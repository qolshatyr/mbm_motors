#!/usr/bin/env python3
"""Build the browser-only demo as one HTML fragment for a claude.ai artifact.

The artifact host wraps the page in its own document and blocks other hosts, so the script,
stylesheet and fonts are inlined; demo images stay as files next to the page.
"""
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent.parent
FRONTEND = ROOT / 'frontend'
OUT = ROOT / 'prototype' / 'artifact'


def main():
    subprocess.run(['npx', 'tsc', '--noEmit'], cwd=FRONTEND, check=True)
    subprocess.run(['npx', 'vite', 'build', '--mode', 'demo', '--config', 'vite.artifact.config.ts'],
                   cwd=FRONTEND, check=True)
    dist = FRONTEND / 'dist-artifact'
    html = (dist / 'index.html').read_text(encoding='utf-8')
    css_paths = re.findall(r'<link rel="stylesheet"[^>]*href="\./([^"]+)"[^>]*>', html)
    js_paths = re.findall(r'<script type="module"[^>]*src="\./([^"]+)"[^>]*></script>', html)
    if len(css_paths) != 1 or len(js_paths) != 1:
        raise SystemExit(f'Expected one stylesheet and one script, got {css_paths} {js_paths}')
    css = (dist / css_paths[0]).read_text(encoding='utf-8')
    js = (dist / js_paths[0]).read_text(encoding='utf-8').replace('</script', '<\\/script')
    if re.search(r'url\((?!data:)', css):
        raise SystemExit('Stylesheet still references external files')
    # Gallery name of the artifact; the app still sets the tab title per screen.
    page = ('<title>Демо MBM Automotors</title>\n'
            f'<style>\n{css}\n</style>\n'
            '<div id="root"></div>\n'
            f'<script type="module">\n{js}\n</script>\n')
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    (OUT / 'index.html').write_text(page, encoding='utf-8')
    for images in ('demo-images', 'photos'):
        shutil.copytree(dist / images, OUT / images)
    size = sum(p.stat().st_size for p in OUT.rglob('*') if p.is_file())
    print(f'Artifact demo: {OUT / "index.html"} ({size // 1024} KB with images)')


if __name__ == '__main__':
    main()
