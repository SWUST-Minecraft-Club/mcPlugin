#!/usr/bin/env python3
"""Package only the declared Vehicles version, model assets and installation notes."""
from pathlib import Path
import hashlib
import json
import re
import zipfile

PROJECT = Path(__file__).resolve().parent.parent
REPO = PROJECT.parent.parent


def main():
    version = re.search(r"version\s*=\s*'([^']+)'", (PROJECT / 'build.gradle').read_text()).group(1)
    tag = re.search(r'^minecraftVersion=(.+)$', (REPO / 'gradle.properties').read_text(), re.M).group(1).replace('.', '_')
    jar = PROJECT / 'build/libs' / f'Vehicles-mc{tag}-plugin-{version}.jar'
    pack = PROJECT / 'build/pack' / f'Vehicles-mc{tag}-resourcepack-{version}.zip'
    with zipfile.ZipFile(jar) as source:
        descriptor = source.read('paper-plugin.yml').decode()
        if f"version: '{version}'" not in descriptor:
            raise RuntimeError('JAR 版本与源码不一致')
        if source.read('vehicles-resourcepack.zip') != pack.read_bytes():
            raise RuntimeError('JAR 内嵌资源包与独立资源包不一致')
        if any('integration/' in name or 'VehicleProbe' in name for name in source.namelist()):
            raise RuntimeError('正式 JAR 混入了测试夹具')
    release = REPO / 'release/Vehicles'
    release.mkdir(parents=True, exist_ok=True)
    path = release / f'Vehicles-mc{tag}-install-{version}.zip'
    content = {
        'plugins/' + jar.name: jar.read_bytes(),
        'resourcepacks/' + pack.name: pack.read_bytes(),
        'README.md': (PROJECT / 'README.md').read_bytes(),
        'Vehicles-mc26_3-plugin-ChangeLog-Beta.md': (PROJECT / 'Vehicles-mc26_3-plugin-ChangeLog-Beta.md').read_bytes(),
        'LICENSE.txt': (REPO / 'LICENSE').read_bytes(),
        'assets/SOURCES.md': (PROJECT / 'assets/SOURCES.md').read_bytes(),
        'assets/VALIDATION.md': (PROJECT / 'assets/VALIDATION.md').read_bytes(),
        'assets/preview.png': (PROJECT / 'assets/preview.png').read_bytes(),
        'assets/source/town_rover.bbmodel': (PROJECT / 'assets/source/town_rover.bbmodel').read_bytes(),
        'plugins/CraftEngine/resources/vehicles/pack.yml':
            f"name: Vehicles\nnamespace: vehicles\nversion: '{version}'\nauthor: zhou-BZDDX\ndescription: 小镇漫游车模型与纹理\n".encode(),
    }
    for file in (PROJECT / 'assets/resourcepack/assets').rglob('*'):
        if file.is_file():
            content['plugins/CraftEngine/resources/vehicles/resourcepack/' + file.relative_to(PROJECT / 'assets/resourcepack').as_posix()] = file.read_bytes()
    for file in (PROJECT / 'assets/screenshots').glob('*.png'):
        content['assets/screenshots/' + file.name] = file.read_bytes()
    content['SHA256SUMS.txt'] = ''.join(hashlib.sha256(data).hexdigest() + '  ' + name + '\n'
                                      for name, data in sorted(content.items())).encode()
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(content.items()):
            entry = zipfile.ZipInfo(name, (2026, 10, 27, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, data)
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    path.with_suffix('.zip.sha256').write_text(digest + '  ' + path.name + '\n')
    print('./' + path.relative_to(REPO).as_posix(), len(content), 'files', digest)


if __name__ == '__main__': main()
