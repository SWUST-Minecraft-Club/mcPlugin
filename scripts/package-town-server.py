#!/usr/bin/env python3
"""复用已交付服务端的依赖 jar，仅更新 Town 和干净默认配置；不带世界、玩家数据或客户端。"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile
from io import BytesIO
from urllib.parse import urlsplit


def distribution(files, root, host, gitlab_url, project_id):
    """仅调整分发配置与启动时的凭据读取；凭据不参与打包。"""
    for path, marker in (("start.sh", "#"), ("start.bat", "rem")):
        script = files[path].decode()
        script = re.sub(rf"(?ms)^{marker} BEGIN CE_GITLAB_TOKEN\n.*?^{marker} END CE_GITLAB_TOKEN\n", "", script)
        files[path] = script.encode()
    if host == "self":
        return """CE 使用内置 HTTP 托管，资源包下载与游戏共用 TCP 25565。
局域网、端口映射或代理环境需设置客户端能访问的 resource-pack.self-host.ip / url。
修改后执行 /ce reload host、/ce reload pack 并重连。
"""
    parsed = urlsplit(gitlab_url)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("GitLab 地址必须为无凭据、查询参数或片段的 HTTPS 地址")
    if not re.fullmatch(r"[0-9]+", project_id):
        raise ValueError("GitLab 项目 ID 必须为数字")
    gitlab_url = gitlab_url.rstrip("/")
    fragment = (root / "Minecraft-26_3-plugin/Town/deploy/craftengine-gitlab.yml").read_text()
    fragment = fragment.replace('gitlab_url: "https://gitlab.yudream.online"', f'gitlab_url: {json.dumps(gitlab_url)}').replace(
        'project_id: "51"', f'project_id: "{project_id}"')
    block = fragment.split("resource-pack:\n", 1)[1].rstrip()
    ce = files["plugins/CraftEngine/config.yml"].decode()
    start = ce.index("  packs:\n")
    end = ce.index("\n  # Players apply", start)
    files["plugins/CraftEngine/config.yml"] = (ce[:start] + block + "\n" + ce[end:]).encode()
    files["craftengine-gitlab.yml"] = fragment.encode()
    files["gitlab-token.txt.example"] = b"REPLACE_WITH_PROJECT_ACCESS_TOKEN\n"
    shell = """# BEGIN CE_GITLAB_TOKEN
if [ -z "${CE_GITLAB_ACCESS_TOKEN:-}" ]; then
  token_file=${CE_GITLAB_TOKEN_FILE:-./gitlab-token.txt}
  if [ -f "$token_file" ]; then
    IFS= read -r CE_GITLAB_ACCESS_TOKEN < "$token_file" || :
    CE_GITLAB_ACCESS_TOKEN=$(printf '%s' "$CE_GITLAB_ACCESS_TOKEN" | tr -d '\\r')
  fi
fi
case ${CE_GITLAB_ACCESS_TOKEN:-} in
  ''|REPLACE_WITH_*)
    echo "请设置 CE_GITLAB_ACCESS_TOKEN，或将项目上传令牌单独保存到 gitlab-token.txt。"
    exit 1 ;;
esac
export CE_GITLAB_ACCESS_TOKEN
# END CE_GITLAB_TOKEN
"""
    windows = """rem BEGIN CE_GITLAB_TOKEN
if not defined CE_GITLAB_TOKEN_FILE set "CE_GITLAB_TOKEN_FILE=%~dp0gitlab-token.txt"
if not defined CE_GITLAB_ACCESS_TOKEN if exist "%CE_GITLAB_TOKEN_FILE%" set /p "CE_GITLAB_ACCESS_TOKEN="<"%CE_GITLAB_TOKEN_FILE%"
if not defined CE_GITLAB_ACCESS_TOKEN (
  echo Set CE_GITLAB_ACCESS_TOKEN or put the project upload token in gitlab-token.txt.
  pause
  exit /b 1
)
if "%CE_GITLAB_ACCESS_TOKEN%"=="REPLACE_WITH_PROJECT_ACCESS_TOKEN" (
  echo Replace the example value in gitlab-token.txt with a project upload token.
  pause
  exit /b 1
)
rem END CE_GITLAB_TOKEN
"""
    for path, anchor, addition in (("start.sh", 'exec "$java_bin"', shell),
                                   ("start.bat", '"%JAVA_CMD%" -Xms', windows)):
        script = files[path].decode()
        if script.count(anchor) != 1:
            raise ValueError(f"启动脚本缺少唯一启动位置：{path}")
        files[path] = script.replace(anchor, addition + anchor, 1).encode()
    return f"""本包通过 GitLab 实例 {gitlab_url} 的项目 {project_id} 分发，使用 CE 原生 GitLab host。
默认项目为 https://gitlab.yudream.online/yudream/mc-texture。
启动前设置环境变量 CE_GITLAB_ACCESS_TOKEN，或复制 gitlab-token.txt.example 为 gitlab-token.txt，
将项目访问令牌单独写入其中（单行 UTF-8、无 BOM）。也可用 CE_GITLAB_TOKEN_FILE 指定令牌文件。
需要项目 Developer 角色及 api scope；仅有 write_repository 或包仓库 Deploy Token 不适用于 CE 上传接口。
令牌只用于服务端上传，玩家无需登录 GitLab。请在令牌到期前更新文件并重启服务端。

Town 自动执行生成、合包、GitLab 上传和发送。CE 使用项目 Uploads API 存储 ZIP，
下载地址由每次上传返回，不是仓库网页或分支 raw 地址，也不会把每张地图的 ZIP 提交进 Git 历史。
公开项目必须允许匿名下载上传附件。无需为资源包另开游戏服下载端口。

已有服：将 craftengine-gitlab.yml 的 packs.default 合并到 plugins/CraftEngine/config.yml，
保留已有工作流的 generate / zip / upload / send_pack；upload 和 send_pack 的 pack 均应为 default。
让 Java 进程获得 CE_GITLAB_ACCESS_TOKEN 后重启；后续配置修改可 /ce reload host、/ce reload pack。
本包和配置片段不包含真实令牌。
"""


def main():
    root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path, required=True, help="上一版仅服务端 ZIP")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--host", choices=("gitlab", "self"), default="gitlab")
    parser.add_argument("--gitlab-url", default="https://gitlab.yudream.online")
    parser.add_argument("--gitlab-project-id", default="51")
    parser.add_argument("--vault", type=Path, help="额外包含用户提供的 Folia 版 Vault jar")
    parser.add_argument("--economy", choices=("wallet", "vault"), default="wallet", help="Town 使用的经济来源")
    args = parser.parse_args()
    version = re.search(r"version = '([^']+)'", (root / "Minecraft-26_3-plugin/Town/build.gradle").read_text()).group(1)
    name = f"Town-Server-mc26_3-{version}" + ("-GitLab" if args.host == "gitlab" else "")
    output = args.output or root.parent / f"{name}.zip"
    with zipfile.ZipFile(args.base) as archive:
        files = {entry.filename.split("/", 1)[1]: archive.read(entry) for entry in archive.infolist()
                 if not entry.is_dir() and "/" in entry.filename}
    files = {path: data for path, data in files.items()
             if not path.startswith("plugins/Town-mc26_3-plugin-") and path not in (
                 "README.md", "SHA256SUMS.json", "plugins/CraftEngine/config.yml", "plugins/ArcMenu/config.yml",
                 "craftengine-gitlab.yml", "gitlab-token.txt.example")}
    vault = files.pop("plugins/Vault-1.7.3-folia.jar", None)
    if args.vault:
        vault = args.vault.read_bytes()
    if args.economy == "vault" and vault is None:
        raise ValueError("Vault 经济配置需要 --vault 指定插件，或从已有 Vault 包继承插件")
    # 只接受已知的最小包，不把来源 ZIP 内的其它数据夹带发布。
    expected = {"folia-26.3-5.jar", "start.sh", "start.bat", "eula.txt", "server.properties",
                "plugins/Town/config.yml", "plugins/Wallet/config.yml", "plugins/CraftEngine-26.9.2.jar",
                "plugins/ArcMenu-3.2.0.jar", "plugins/MainMenu-mc26_3-plugin-Beta_1_5_1.jar",
                "plugins/Wallet-mc26_3-plugin-Beta_0_5_1.jar"}
    if set(files) != expected:
        raise ValueError(f"来源包文件集合不符：{set(files) ^ expected}")
    if vault is not None:
        with zipfile.ZipFile(BytesIO(vault)) as archive:
            descriptor = archive.read("plugin.yml").decode()
            if not re.search(r"(?m)^name: Vault\s*$", descriptor) or not re.search(
                    r"(?m)^folia-supported: true\s*$", descriptor) or "net/milkbowl/vault/economy/Economy.class" not in archive.namelist():
                raise ValueError("指定的 jar 不是带 Economy API 的 Folia 版 Vault")
        files["plugins/Vault-1.7.3-folia.jar"] = vault
    jar = f"Town-mc26_3-plugin-{version}.jar"
    files[f"plugins/{jar}"] = (root / "release/Town" / jar).read_bytes()
    files["plugins/Town/config.yml"] = (root / "Minecraft-26_3-plugin/Town/src/main/resources/config.yml").read_bytes().replace(
        b'provider: "wallet"', f'provider: "{args.economy}"'.encode())
    if args.economy == "wallet":
        files["plugins/Town/config.yml"] = files["plugins/Town/config.yml"].replace(b'currency: ""', b'currency: "TEST"')
    files["server.properties"] = ("motd=Town Server 26.3\nmax-players=20\nonline-mode=true\n"
            "server-port=25565\nview-distance=8\nsimulation-distance=6\nspawn-protection=0\nwhite-list=false\n").encode()
    with zipfile.ZipFile(BytesIO(files["plugins/CraftEngine-26.9.2.jar"])) as archive:
        ce = archive.read("config.yml").decode()
    ce = ce.replace("kick-if-declined: true", "kick-if-declined: false").replace(
        "<yellow>To fully experience our server,<newline>please accept our custom resource pack.</yellow>",
        "<yellow>请接受小镇资源包，以显示政厅材质和领地地形。</yellow>")
    files["plugins/CraftEngine/config.yml"] = ce.encode()
    with zipfile.ZipFile(BytesIO(files["plugins/ArcMenu-3.2.0.jar"])) as archive:
        arc = archive.read("config.yml").decode()
    arc = arc.replace("assume-resource-pack-loaded: true", "assume-resource-pack-loaded: false")
    files["plugins/ArcMenu/config.yml"] = arc.encode()
    hosting = distribution(files, root, args.host, args.gitlab_url, args.gitlab_project_id)
    economy_note = ("Wallet 是本地测试模式，初始 100000 TEST，方便测试成立和购买。" if args.economy == "wallet" else
                    "Town 已选择 Vault。另需安装兼容本版 Folia 的 Economy 提供者；Vault 本身不保存余额。")
    vault_note = ("已包含用户提供的 Folia 版 Vault。" if vault is not None else "未包含 Vault；使用前另行安装兼容本版 Folia 的 Vault。")
    files["README.md"] = f"""# Town 服务端 {version}

仅服务端：Folia 26.3 build 5 / Java 25+，含 Town、MainMenu、Wallet、CraftEngine 26.9.2、ArcMenu 3.2.0。
不含客户端、测试 Mod、测试夹具、世界或玩家资料。

## 首次运行

1. 安装 Java 25 或更新版本。阅读 Minecraft EULA，同意后将 eula.txt 改为 eula=true。
2. GitLab 包先按下方说明配置上传令牌。Windows 运行 start.bat，Linux 运行 ./start.sh。默认正版验证，端口 25565，白名单关闭。
3. 首次联网会下载 Folia/CE 所需依赖。等待 Done 和 CE 资源包上传成功；Town 会自动生成、合并并发送资源包。
4. 客户端添加服务器时将“服务器资源包”设为“启用”。本机连接 127.0.0.1:25565。
5. /town 开始小镇。{economy_note}

资源包未加载时，地图显示可操作的区块网格；正确加载后自动显示地形。移动地图到未缓存位置会生成新图片，资源包更新有 30 秒节流。

## 资源包分发

{hosting}
无需把生成的资源包手动放到客户端。

## Vault 经济

{vault_note}
Town 的 economy.provider 可选 wallet / vault，默认 wallet；修改后重启服务端。
选择 vault 还需要已注册 Economy 服务的经济插件，且该经济插件也必须支持本版 Folia。
Vault 使用单一货币，忽略 economy.currency；/town admin economy 查看实际来源。
来源、镇库币种与交易业务号分别绑定；切换配置不会自动迁移旧余额、商铺、租约或待结算交易。
结果不明时不自动重复扣款，使用 /town admin economy pending 查看，核对外部账本后再按模块 README 处理。

## 已有服升级

停服并备份后，只替换 plugins/ 下旧 Town jar 为 {jar}，保留世界、plugins/Town/ 和原有依赖配置，再启动。
若客户端以前拒绝过资源包，在多人游戏列表编辑本服，将服务器资源包改为启用后重连。
不要用本包的世界/玩家数据或整份配置覆盖已有服；本包本身不包含这些数据。
已有 CE 配置的托管地址、工作流和拒绝踢出策略保持运营者设置。

SHA256SUMS.json 记录各文件校验值。
""".encode()
    files["SHA256SUMS.json"] = (json.dumps({path: hashlib.sha256(data).hexdigest()
                for path, data in sorted(files.items())}, ensure_ascii=False, indent=2) + "\n").encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        for path, data in sorted(files.items()):
            info = zipfile.ZipInfo(f"{name}/{path}", (2026, 10, 26, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (0o100755 if path.endswith(".sh") else 0o100644) << 16
            archive.writestr(info, data)
    print(json.dumps({"file": str(output), "files": len(files), "bytes": output.stat().st_size,
                      "sha256": hashlib.sha256(output.read_bytes()).hexdigest()}, ensure_ascii=False))


if __name__ == "__main__":
    main()
