#!/usr/bin/env python3
"""从留档和干净模板打包手机/钱包更新，不读取服务端数据或凭据。"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parent.parent
PLUGINS = ("MainMenu", "Wallet", "Town", "RealTime", "YudreamPlaytime", "ConcertoMenu", "JoinAnnounce")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--vault-jar", required=True, type=Path, help="用户提供的 Folia Vault JAR")
    parser.add_argument("--output", type=Path,
                        default=ROOT / "Minecraft-26_3-plugin/MainMenu/build/delivery/MainMenu-Wallet-Vault-mc26_3.zip")
    args = parser.parse_args()
    with zipfile.ZipFile(args.vault_jar) as archive:
        descriptor = archive.read("plugin.yml").decode()
        if not re.search(r"(?m)^name: Vault\s*$", descriptor) or not re.search(r"(?m)^folia-supported: true\s*$", descriptor):
            parser.error("提供的 JAR 不是声明 Folia 支持的 Vault")
    vault_version = re.search(r"(?m)^version:\s*([^\r\n]+)", descriptor).group(1).strip().strip("\"'")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", vault_version):
        parser.error("Vault 版本号不适用于文件名")
    files, versions = {}, {}
    for name in PLUGINS:
        text = (ROOT / "Minecraft-26_3-plugin" / name / "build.gradle").read_text()
        version = re.search(r"(?m)^version\s*=\s*'([^']+)'", text).group(1)
        jar = ROOT / "release" / name / f"{name}-mc26_3-plugin-{version}.jar"
        files["server/plugins/" + jar.name] = jar.read_bytes()
        versions[name] = version
    text = (ROOT / "fabric-26_3-mod/MainMenuRepair/gradle.properties").read_text()
    version = re.search(r"(?m)^version=(.+)$", text).group(1).strip()
    jar = ROOT / "release/MainMenuRepair" / f"MainMenuRepair-mc26_3-fabric-{version}.jar"
    files["client/mods/" + jar.name] = jar.read_bytes()
    versions["MainMenuRepair"] = version
    files["server/plugins/Vault-" + vault_version + ".jar"] = args.vault_jar.read_bytes()
    versions["Vault"] = vault_version
    menus = ROOT / "Minecraft-26_3-plugin/MainMenu/src/main/resources/arcmenu"
    for path in sorted(menus.glob("*.yml")):
        files["templates/ArcMenu/menus/mainmenu/" + path.name] = path.read_bytes()
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
    print(args.output)
    print("SHA-256:", hashlib.sha256(args.output.read_bytes()).hexdigest())


if __name__ == "__main__":
    main()
