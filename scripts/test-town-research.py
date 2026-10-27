#!/usr/bin/env python3
"""Run the disposable Town research fixture, with and without the Stardew core."""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import time
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
TOWN = ROOT / "Minecraft-26_3-plugin/Town"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepare(args):
    target = args.directory.resolve()
    if target.exists() and any(target.iterdir()):
        raise RuntimeError("Use a new, empty test directory")
    if "eula=true" not in (args.template / "eula.txt").read_text():
        raise RuntimeError("The template must contain an already accepted EULA")
    target.mkdir(parents=True, exist_ok=True)
    plugins = target / "plugins"
    plugins.mkdir()
    for name in ("eula.txt", "folia.jar"):
        shutil.copy2(args.template / name, target / name)
    for name in ("libraries", "versions", "cache"):
        if (args.template / name).is_dir():
            shutil.copytree(args.template / name, target / name)
    town_version = re.search(r"version = '([^']+)'", (TOWN / "build.gradle").read_text()).group(1)
    stardew_version = re.search(r"version = '([^']+)'", (args.stardew_project / "build.gradle").read_text()).group(1)
    shutil.copy2(ROOT / f"release/Town/Town-mc26_3-plugin-{town_version}.jar", plugins / "Town.jar")
    shutil.copy2(TOWN / "build/libs/TestPluginTownResearch.jar", plugins / "TestPluginTownResearch.jar")
    for name in ("CraftEngine.jar", "ArcMenu.jar"):
        shutil.copy2(args.template / "plugins" / name, plugins / name)
    for name in ("resources", "libs", "libraries"):
        source = args.template / "plugins/CraftEngine" / name
        if source.is_dir():
            shutil.copytree(source, plugins / "CraftEngine" / name)
    with ZipFile(plugins / "CraftEngine.jar") as ce:
        config = ce.read("config.yml").decode().replace('    ip: "auto"', '    ip: "127.0.0.1"', 1)
        (plugins / "CraftEngine").mkdir(exist_ok=True)
        (plugins / "CraftEngine/config.yml").write_text(config)
    if not args.without_stardew:
        shutil.copy2(args.stardew_project / f"build/libs/RptStardew-{stardew_version}.jar", plugins / "RptStardew.jar")
        farm_dir = plugins / "RptStardew"
        farm_dir.mkdir()
        (farm_dir / "farm.yml").write_text((args.stardew_project / "src/main/resources/farm.yml").read_text().replace("mode: vault", "mode: internal"))
    town_dir = plugins / "Town"
    town_dir.mkdir()
    config = (TOWN / "src/main/resources/config.yml").read_text().replace("research-base-seconds: 1800", "research-base-seconds: 12")
    config = config.replace("duration-seconds: 86400", "duration-seconds: 3")
    (town_dir / "config.yml").write_text(config)
    (target / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=25641\nonline-mode=false\nlevel-name=world\n"
        "level-type=minecraft:flat\n"
        'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},'
        '{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],'
        '"biome":"minecraft:plains"}\n'
        "view-distance=3\nsimulation-distance=3\nspawn-protection=0\nmax-players=2\npause-when-empty-seconds=-1\n")
    (target / "config").mkdir()
    (target / "config/paper-global.yml").write_text("threaded-regions:\n  threads: 2\n")
    return target, town_version, stardew_version


def run_phase(args, target, phase):
    with (target / f"console-{phase}.log").open("w") as output:
        process = subprocess.Popen([str(args.java), "-Xms256M", "-Xmx1G", "-Dterminal.jline=false", "-Dterminal.ansi=false",
                                    "-jar", "folia.jar", "--nogui"], cwd=target, stdin=subprocess.PIPE,
                                   stdout=output, stderr=subprocess.STDOUT, text=True)
        try:
            until = time.monotonic() + 180
            while time.monotonic() < until:
                if (target / f"research-{phase}.failed.txt").exists():
                    raise RuntimeError((target / f"research-{phase}.failed.txt").read_text())
                passed = target / f"research-{phase}.passed.txt"
                if passed.exists():
                    return {"checks": len(passed.read_text().splitlines()), "console": str(target / f"console-{phase}.log")}
                if process.poll() is not None:
                    raise RuntimeError("Server exited before completing the fixture")
                time.sleep(.5)
            raise TimeoutError("Research fixture timed out; inspect its console")
        finally:
            if process.poll() is None:
                process.stdin.write("stop\n")
                process.stdin.flush()
                try:
                    process.wait(timeout=60)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
                    raise RuntimeError("Disposable server failed to stop cleanly")
            if process.returncode != 0:
                raise RuntimeError(f"Disposable server exited with {process.returncode}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--template", type=Path, required=True)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--stardew-project", type=Path, default=ROOT.parent / "rptstardew")
    parser.add_argument("--without-stardew", action="store_true")
    args = parser.parse_args()
    target, town_version, stardew_version = prepare(args)
    phases = {}
    for phase in ("first", "restart"):
        phases[phase] = run_phase(args, target, phase)
        print(f"{phase}: {phases[phase]['checks']} checks passed; server stopped cleanly", flush=True)
    result = {"townVersion": town_version, "stardewVersion": None if args.without_stardew else stardew_version,
              "townSha256": digest(target / "plugins/Town.jar"), "phases": phases}
    if not args.without_stardew:
        result["stardewSha256"] = digest(target / "plugins/RptStardew.jar")
    folder = TOWN / "build/reports"
    folder.mkdir(parents=True, exist_ok=True)
    output = folder / ("research-no-stardew.json" if args.without_stardew else "research-stardew.json")
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2))
    print(output)


if __name__ == "__main__":
    main()
