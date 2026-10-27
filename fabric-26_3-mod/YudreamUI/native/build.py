"""Build the game-independent JNI engine from a pinned upstream commit."""
import argparse
import os
from pathlib import Path
import platform
import shutil
import subprocess

MOD = Path(__file__).resolve().parent.parent
REPO = MOD.parents[1]
COMMIT = 'ba95ffe8bfb6370efb2cdcca927eaad4710c5413'


def run(*args, **kwargs):
    subprocess.run([str(a) for a in args], check=True, **kwargs)


def main():
    args = argparse.ArgumentParser()
    args.add_argument('--java-home', required=True)
    options = args.parse_args()
    cache = REPO / '.gradle/yudream-ui-rmlui'
    tools = REPO / '.gradle/yudream-ui-tools'
    env = os.environ.copy()
    env['JAVA_HOME'] = options.java_home
    env['GIT_CONFIG_GLOBAL'] = os.devnull
    if not cache.exists():
        run('git', 'clone', '--depth', '1', '--filter=blob:none', '--no-checkout', '--branch', '6.3',
            'https://github.com/mikke89/RmlUi.git', cache, env=env)
    revision = subprocess.check_output(['git', '-C', str(cache), 'rev-parse', 'HEAD'], env=env, text=True).strip()
    if revision != COMMIT:
        raise RuntimeError('Unexpected RmlUi source revision')
    run('git', '-C', cache, '-c', 'http.version=HTTP/1.1', 'sparse-checkout', 'set', '--no-cone', '/*', '!/*/', '/Include/', '/Source/', '/CMake/', '/Samples/CMakeLists.txt', env=env)
    run('git', '-C', cache, 'checkout', COMMIT, env=env)
    cmake = shutil.which('cmake') or str(tools / 'cmake/data/bin/cmake')
    ninja = shutil.which('ninja') or str(tools / 'bin/ninja')
    if not Path(cmake).exists() or not Path(ninja).exists():
        raise RuntimeError('Install CMake and Ninja to build the native UI engine')
    build = MOD / 'runtime/build/native'
    run(cmake, '-S', MOD / 'native', '-B', build, '-G', 'Ninja', '-DCMAKE_BUILD_TYPE=Release',
        f'-DCMAKE_MAKE_PROGRAM={ninja}', f'-DRMLUI_SOURCE={cache}', env=env)
    run(cmake, '--build', build, '--target', 'yudreamui', '--parallel', str(min(6, os.cpu_count() or 2)), env=env)
    system = {'Linux': 'linux', 'Darwin': 'macos', 'Windows': 'windows'}[platform.system()]
    arch = {'x86_64': 'x86_64', 'AMD64': 'x86_64', 'aarch64': 'aarch64', 'arm64': 'aarch64'}[platform.machine()]
    library = {'linux': 'libyudreamui.so', 'macos': 'libyudreamui.dylib', 'windows': 'yudreamui.dll'}[system]
    destination = MOD / f'runtime/build/native-resources/natives/{system}-{arch}'
    destination.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(next(build.rglob(library)), destination / library)
    notice = MOD / 'runtime/build/native-resources/licenses'
    notice.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(cache / 'LICENSE.txt', notice / 'RmlUi-MIT.txt')
    print(f'Built RmlUi 6.3 JNI runtime for {system}-{arch}')


if __name__ == '__main__':
    main()
