#!/usr/bin/env python3
"""Package the verified Town and RptStardew update, SDK, sources and configuration notes."""
import argparse
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

ROOT = Path(__file__).resolve().parents[1]
TOWN = ROOT / "Minecraft-26_3-plugin/Town"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def portable(value):
    if isinstance(value, dict):
        return {key: portable(item) for key, item in value.items()}
    if isinstance(value, list):
        return [portable(item) for item in value]
    if isinstance(value, str) and value.startswith(str(ROOT) + "/"):
        return "./" + value[len(str(ROOT)) + 1:]
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stardew-project", type=Path, default=ROOT.parent / "rptstardew")
    args = parser.parse_args()
    town_version = re.search(r"version = '([^']+)'", (TOWN / "build.gradle").read_text()).group(1)
    core_version = re.search(r"version = '([^']+)'", (args.stardew_project / "build.gradle").read_text()).group(1)
    town_jar = ROOT / f"release/Town/Town-mc26_3-plugin-{town_version}.jar"
    core = args.stardew_project / "release" / core_version
    core_jar = core / f"RptStardew-{core_version}.jar"
    evidence = {}
    for label in ("stardew", "no-stardew"):
        report = json.loads((TOWN / f"build/reports/research-{label}.json").read_text())
        if report["townSha256"] != sha(town_jar) or report["townVersion"] != town_version:
            raise RuntimeError("Re-run Town integration against this exact release JAR")
        if label == "stardew" and (report["stardewSha256"] != sha(core_jar) or report["stardewVersion"] != core_version):
            raise RuntimeError("Town integration used a different Stardew JAR")
        evidence[label] = portable(report)
    suites = [ET.parse(p).getroot() for p in (TOWN / "build/test-results/test").glob("TEST-*.xml")]
    if not suites or any(int(s.get("failures", 0)) or int(s.get("errors", 0)) for s in suites):
        raise RuntimeError("Town unit tests must pass before packaging")
    evidence["townUnitTests"] = sum(int(s.get("tests", 0)) for s in suites)
    files = {
        f"plugins/{town_jar.name}": town_jar.read_bytes(),
        f"plugins/{core_jar.name}": core_jar.read_bytes(),
        f"sdk/RptStardew-{core_version}-api.jar": (core / f"RptStardew-{core_version}-api.jar").read_bytes(),
        f"sources/RptStardew-{core_version}-source.zip": (core / f"RptStardew-{core_version}-source.zip").read_bytes(),
        "docs/科研与星露联动.md": (TOWN / "RESEARCH.md").read_bytes(),
        "docs/经验加成接口.md": (core / "经验加成接口.md").read_bytes(),
        "docs/小镇玩家指南.md": (TOWN / "PLAYER_GUIDE.md").read_bytes(),
        "verification/科研联动.json": json.dumps(evidence, ensure_ascii=False, indent=2).encode(),
        "verification/星露谷验证.json": (core / "验证记录.json").read_bytes(),
    }
    for folder in (core / "verification").iterdir():
        if folder.is_dir():
            for file in folder.iterdir():
                if file.is_file():
                    files[f"verification/stardew/{folder.name}/{file.name}"] = file.read_bytes()
    files["安装说明.md"] = f"""# 小镇科研与星露研习更新

Town {town_version} / RptStardew {core_version}；Folia 26.3 / Java 25。

1. 完整停止服务器，备份 Town 与 RptStardew 数据目录。
2. 用 plugins/ 下两个 JAR 替换对应旧 JAR，每个插件只保留一个版本。
3. 保留已有 config.yml、towns.yml 和星露谷数据库；启动后会保留旧科技等级并一次性补入四项新政策。
4. 原有 CraftEngine、ArcMenu 与经济等依赖沿用原服务器安装。未安装星露谷也可单独更新 Town，技能政策会暂停采购。
5. /town civic 查看科技研究与政策，/farm 查看当前位置经验加成。

默认研究耗时30分钟乘目标等级，每镇同时一项，离线及停服继续；科研协作持续24小时、速度+25%，全程加速时一级需24分钟。
农艺/渔业/矿业研习默认持续24小时、经验+25%，仅本镇镇域内成员受益。政策占用槽位、续购延时。
价格、时长、范围、叠加上限可配置，详见 docs/。本次没有新增资源包素材，沿用现有星露谷内容包。

sdk/ 只供其他插件编译，不安装到 plugins/。sources/ 是星露谷可构建源码；Town源码在 mcPlugin 仓库。
本包不包含测试夹具。首次与重启验证记录在 verification/；本轮没有新的真实客户端画面验收。
""".encode()
    checksums = "\n".join(f"{hashlib.sha256(data).hexdigest()}  {name}" for name, data in sorted(files.items())) + "\n"
    files["SHA256SUMS.txt"] = checksums.encode()
    output = ROOT / f"release/Town/Town-Stardew-Research-mc26_3-{town_version}.zip"
    with ZipFile(output, "w", ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(files.items()):
            info = ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data)
    with ZipFile(output) as archive:
        if archive.testzip() is not None or any("TestPlugin" in name for name in archive.namelist()):
            raise RuntimeError("Invalid distribution")
    output.with_suffix(".zip.sha256").write_text(f"{sha(output)}  {output.name}\n")
    print(output)
    print(sha(output))


if __name__ == "__main__":
    main()
