#!/usr/bin/env python3
"""Build the isolated client prototype without reading server credentials."""
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def main():
    frontend = ROOT / 'frontend'
    if not (frontend / 'node_modules').is_dir():
        subprocess.run(['npm', 'ci'], cwd=frontend, check=True)
    subprocess.run(['npm', 'run', 'build:demo'], cwd=frontend, check=True)
    source = frontend / 'dist-demo'
    destination = ROOT / 'prototype' / 'dist'
    if destination.exists():
        shutil.rmtree(destination)
    shutil.copytree(source, destination)
    (destination / 'robots.txt').write_text('User-agent: *\nDisallow: /\n')
    with zipfile.ZipFile(ROOT / 'prototype' / 'mbm-motors-demo.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(destination.rglob('*')):
            if file.is_file():
                archive.write(file, file.relative_to(destination))
    print('Demo: prototype/dist; portable archive: prototype/mbm-motors-demo.zip')

if __name__ == '__main__':
    main()
