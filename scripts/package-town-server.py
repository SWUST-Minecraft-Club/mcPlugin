#!/usr/bin/env python3
"""复用已交付服务端的依赖 jar，仅更新 Town 和干净默认配置；不带世界、玩家数据或客户端。"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile
from io import BytesIO


def main():
    root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path, required=True, help="上一版仅服务端 ZIP")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    version = re.search(r"version = '([^']+)'", (root / "Minecraft-26_3-plugin/Town/build.gradle").read_text()).group(1)
    name = f"Town-Server-mc26_3-{version}"
    output = args.output or root.parent / f"{name}.zip"
    with zipfile.ZipFile(args.base) as archive:
        files = {entry.filename.split("/", 1)[1]: archive.read(entry) for entry in archive.infolist()
                 if not entry.is_dir() and "/" in entry.filename}
    files = {path: data for path, data in files.items()
             if not path.startswith("plugins/Town-mc26_3-plugin-") and path not in ("README.md", "SHA256SUMS.json")}
    # 只接受已知的最小包，不把来源 ZIP 内的其它数据夹带发布。
    expected = {"folia-26.3-5.jar", "start.sh", "start.bat", "eula.txt", "server.properties",
                "plugins/Town/config.yml", "plugins/Wallet/config.yml", "plugins/CraftEngine-26.9.2.jar",
                "plugins/ArcMenu-3.2.0.jar", "plugins/MainMenu-mc26_3-plugin-Beta_1_5_1.jar",
                "plugins/Wallet-mc26_3-plugin-Beta_0_5_1.jar"}
    if set(files) != expected:
        raise ValueError(f"来源包文件集合不符：{set(files) ^ expected}")
    jar = f"Town-mc26_3-plugin-{version}.jar"
    files[f"plugins/{jar}"] = (root / "release/Town" / jar).read_bytes()
    files["plugins/Town/config.yml"] = (root / "Minecraft-26_3-plugin/Town/src/main/resources/config.yml").read_bytes().replace(
        b'currency: ""', b'currency: "TEST"')
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
    files["README.md"] = f"""# Town 服务端 {version}

仅服务端：Folia 26.3 build 5 / Java 25+，含 Town、MainMenu、Wallet、CraftEngine 26.9.2、ArcMenu 3.2.0。
不含客户端、测试 Mod、测试夹具、世界或玩家资料。

## 首次运行

1. 安装 Java 25 或更新版本。阅读 Minecraft EULA，同意后将 eula.txt 改为 eula=true。
2. Windows 运行 start.bat，Linux 运行 ./start.sh。默认正版验证，端口 25565，白名单关闭。
3. 首次联网会下载 Folia/CE 所需依赖。等待 Done 和 CE 资源包上传成功；Town 会自动生成、合并并发送资源包。
4. 客户端添加服务器时将“服务器资源包”设为“启用”。本机连接 127.0.0.1:25565。
5. /town 开始小镇。Wallet 是本地测试模式，初始 100000 TEST，方便测试成立和购买。

资源包未加载时，地图显示可操作的区块网格；正确加载后自动显示地形。移动地图到未缓存位置会生成新图片，资源包更新有 30 秒节流。

## 下载地址

CE 默认 resource-pack.self-host.ip 和 port 都为 auto，资源包 HTTP 下载与游戏共用 TCP 25565。
本机连接由 CE 使用回环下载地址；公网连接使用自动检测的公网地址。
若用局域网、端口映射或反向代理，请在 plugins/CraftEngine/config.yml 设置客户端可访问的
resource-pack.self-host.ip，或 resource-pack.self-host.url（带 http:// 或 https:// 和实际外部端口）。
修改后执行 /ce reload host，再执行 /ce reload pack 并重进服务器。
无需把生成的资源包手动放到客户端。

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
