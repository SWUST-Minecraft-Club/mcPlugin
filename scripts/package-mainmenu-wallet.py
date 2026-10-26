#!/usr/bin/env python3
"""从构建产物或留档和干净模板打包手机/钱包更新，不读取服务端数据或凭据。"""
import argparse
import hashlib
from io import BytesIO
import json
from pathlib import Path
import re
from urllib.request import Request, urlopen
import zipfile

ROOT = Path(__file__).resolve().parent.parent
PLUGINS = ("MainMenu", "Wallet", "Town", "RealTime", "YudreamPlaytime", "ConcertoMenu", "JoinAnnounce")
VAULT_LOCK = ROOT / "scripts/dependencies/vault-folia.json"


def vault_bytes(path, lock):
    if path:
        data = path.read_bytes()
    else:
        print("下载已锁定的 Folia Vault：", lock["url"], flush=True)
        request = Request(lock["url"], headers={"User-Agent": "mcPlugin-packager"})
        with urlopen(request, timeout=60) as response:
            data = response.read()
    if hashlib.sha256(data).hexdigest() != lock["sha256"]:
        raise ValueError("Vault SHA-256 与依赖锁不符；拒绝打包未经验证的版本")
    with zipfile.ZipFile(BytesIO(data)) as archive:
        descriptor = archive.read("plugin.yml").decode()
        if not re.search(r"(?m)^name: Vault\s*$", descriptor) or not re.search(r"(?m)^folia-supported: true\s*$", descriptor):
            raise ValueError("提供的 JAR 不是声明 Folia 支持的 Vault")
        if "net/milkbowl/vault/economy/Economy.class" not in archive.namelist():
            raise ValueError("Vault 缺少 Economy API")
        match = re.search(r"(?m)^version:\s*([^\r\n]+)", descriptor)
        if not match or match.group(1).strip().strip("\"'") != lock["version"]:
            raise ValueError("Vault 版本与依赖锁不符")
    return data


def built_jar(name, version, game_tag, kind, artifacts):
    filename = f"{name}-{game_tag}-{kind}-{version}.jar"
    if artifacts == "build":
        directory = "fabric-26_3-mod" if kind == "fabric" else "Minecraft-26_3-plugin"
        path = ROOT / directory / name / "build/libs" / filename
    else:
        path = ROOT / "release" / name / filename
    if not path.is_file():
        raise ValueError(f"缺少 {artifacts} 产物：./{path.relative_to(ROOT)}；请先完成对应构建")
    data = path.read_bytes()
    with zipfile.ZipFile(BytesIO(data)) as archive:
        if kind == "fabric":
            actual = json.loads(archive.read("fabric.mod.json"))["version"]
        else:
            descriptor = archive.read("paper-plugin.yml").decode()
            actual = re.search(r"(?m)^version:\s*([^\r\n]+)", descriptor).group(1).strip().strip("\"'")
        if actual != version:
            raise ValueError(f"{filename} 内的版本 {actual} 与源码 {version} 不符")
    return filename, data


def verify_bundle(path):
    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise ValueError("ZIP CRC 校验失败")
        sums = archive.read("SHA256SUMS.txt").decode().splitlines()
        recorded = set()
        for line in sums:
            digest, name = line.split("  ", 1)
            if name in recorded or hashlib.sha256(archive.read(name)).hexdigest() != digest:
                raise ValueError(f"ZIP 文件校验失败：{name}")
            recorded.add(name)
        if recorded != set(archive.namelist()) - {"SHA256SUMS.txt"}:
            raise ValueError("ZIP 清单与实际文件不一致")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--vault-jar", type=Path, help="离线使用与依赖锁哈希一致的 Folia Vault JAR；省略时从上游下载")
    parser.add_argument("--artifacts", choices=("build", "archive"), default="build",
                        help="默认仅取本次 build/libs 输出；archive 显式使用 release 留档")
    parser.add_argument("--output", type=Path,
                        default=ROOT / "Minecraft-26_3-plugin/MainMenu/build/delivery/MainMenu-Wallet-Vault-mc26_3.zip")
    args = parser.parse_args()
    lock = json.loads(VAULT_LOCK.read_text())
    vault = vault_bytes(args.vault_jar, lock)
    game_version = re.search(r"(?m)^minecraftVersion=(.+)$", (ROOT / "gradle.properties").read_text()).group(1).strip()
    game_tag = "mc" + game_version.replace(".", "_")
    files, versions = {}, {}
    for name in PLUGINS:
        text = (ROOT / "Minecraft-26_3-plugin" / name / "build.gradle").read_text()
        version = re.search(r"(?m)^version\s*=\s*'([^']+)'", text).group(1)
        filename, data = built_jar(name, version, game_tag, "plugin", args.artifacts)
        files["server/plugins/" + filename] = data
        versions[name] = version
    text = (ROOT / "fabric-26_3-mod/MainMenuRepair/gradle.properties").read_text()
    version = re.search(r"(?m)^version=(.+)$", text).group(1).strip()
    filename, data = built_jar("MainMenuRepair", version, game_tag, "fabric", args.artifacts)
    files["client/mods/" + filename] = data
    versions["MainMenuRepair"] = version
    files["server/plugins/Vault-" + lock["version"] + ".jar"] = vault
    versions["Vault"] = lock["version"]
    files["third-party/vault-folia.json"] = VAULT_LOCK.read_bytes()
    files["third-party/Vault-LICENSE.txt"] = (ROOT / "scripts/dependencies/Vault-LICENSE.txt").read_bytes()
    files["third-party/Vault-GPL-3.0.txt"] = (ROOT / "scripts/dependencies/Vault-GPL-3.0.txt").read_bytes()
    files["third-party/README.md"] = (ROOT / "scripts/dependencies/README.md").read_bytes()
    menus = ROOT / "Minecraft-26_3-plugin/MainMenu/src/main/resources/arcmenu"
    for path in sorted(menus.glob("*.yml")):
        files["templates/ArcMenu/menus/mainmenu/" + path.name] = path.read_bytes()
    if not any(name.startswith("templates/ArcMenu/") for name in files):
        raise ValueError("未找到手机菜单 YAML，拒绝生成不完整更新包")
    for source, target in (
        ("Minecraft-26_3-plugin/MainMenu/deploy/安装说明.md", "安装说明.md"),
        ("Minecraft-26_3-plugin/MainMenu/deploy/Wallet-local.yml", "config-merge/Wallet-local.yml"),
        ("Minecraft-26_3-plugin/MainMenu/deploy/Town-vault.yml", "config-merge/Town-vault.yml"),
        ("Minecraft-26_3-plugin/Wallet/README.md", "docs/Wallet-README.md"),
        ("Minecraft-26_3-plugin/Wallet/VALIDATION.md", "docs/Wallet-VALIDATION.md"),
        ("Minecraft-26_3-plugin/MainMenu/assets/VALIDATION.md", "docs/MainMenu-VALIDATION.md"),
        ("Minecraft-26_3-plugin/MainMenu/assets/vault-validation-results.json", "docs/vault-validation-results.json"),
        ("docs/exp/arcmenu-item-angles.md", "docs/arcmenu-item-angles.md"),
    ):
        text = (ROOT / source).read_text()
        if target == "docs/Wallet-README.md":
            text = text.replace("(./VALIDATION.md)", "(./Wallet-VALIDATION.md)")
        elif target == "docs/Wallet-VALIDATION.md":
            text = text.replace("(../MainMenu/assets/VALIDATION.md)", "(./MainMenu-VALIDATION.md)")
            text = text.replace("(../MainMenu/assets/vault-validation-results.json)", "(./vault-validation-results.json)")
        elif target == "docs/MainMenu-VALIDATION.md":
            text = text.split("以下是 Beta_1_6_0", 1)[0].replace("(../../Wallet/VALIDATION.md)", "(./Wallet-VALIDATION.md)")
        files[target] = text.encode()
    for name in ("vault-home", "vault-economy"):
        files[f"docs/screenshots/{name}.png"] = (ROOT / f"Minecraft-26_3-plugin/MainMenu/assets/screenshots/{name}.png").read_bytes()
    files["versions.json"] = (json.dumps(versions, ensure_ascii=False, indent=2) + "\n").encode()
    files["SHA256SUMS.txt"] = "".join(f"{hashlib.sha256(data).hexdigest()}  {name}\n"
                                           for name, data in sorted(files.items())).encode()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(files.items()):
            entry = zipfile.ZipInfo(name, date_time=(2026, 10, 26, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, data)
    verify_bundle(args.output)
    digest = hashlib.sha256(args.output.read_bytes()).hexdigest()
    args.output.with_suffix(".zip.sha256").write_text(f"{digest}  {args.output.name}\n", encoding="utf-8")
    print(args.output)
    print("SHA-256:", digest)


if __name__ == "__main__":
    main()
