#!/usr/bin/env python3
"""Run the packaged React + Spring Boot site with private local credentials."""
import argparse
import getpass
import os
import re
from pathlib import Path
import subprocess
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sync', action='store_true', help='Enable read-only CloudShop updates every five minutes')
    parser.add_argument('--port', type=int, default=8080)
    parser.add_argument('--pickup-store', action='append', default=[], help='CloudShop ID согласованной точки самовывоза; можно повторить')
    args = parser.parse_args()
    if any(not re.fullmatch('[a-fA-F0-9]{24}', value) for value in args.pickup_store):
        parser.error('Pickup store ID must have 24 hexadecimal characters')
    if not 1 <= args.port <= 65535:
        parser.error('Port must be between 1 and 65535')
    root = Path(__file__).resolve().parent.parent
    jar = root / 'target/mbm-motors-0.0.1-SNAPSHOT.jar'
    if not jar.is_file():
        raise SystemExit('Build the site first: python3 scripts/build_site.py')
    with zipfile.ZipFile(jar) as archive:
        if 'BOOT-INF/classes/static/frontend/index.html' not in archive.namelist():
            raise SystemExit('This JAR has no React build. Run: python3 scripts/build_site.py')
    env = dict(os.environ)
    if 'DB_PASSWORD' not in env:
        env['DB_PASSWORD'] = getpass.getpass('Пароль PostgreSQL (ввод скрыт): ')
    if args.sync:
        if not env.get('CLOUDSHOP_TOKEN'):
            env['CLOUDSHOP_TOKEN'] = getpass.getpass('Токен CloudShop (только чтение, ввод скрыт): ').strip()
        if not env['CLOUDSHOP_TOKEN'] or any(char.isspace() for char in env['CLOUDSHOP_TOKEN']):
            raise SystemExit('Введите непустой токен без пробелов.')
    print(f'Сайт: http://127.0.0.1:{args.port}/. Остановка: Ctrl+C.', flush=True)
    command = ['java', '-jar', str(jar), '--frontend.enabled=true',
                           f'--server.port={args.port}', '--catalog.sync.startup-mode=NONE',
                           '--catalog.sync.enabled=' + str(args.sync).lower()]
    command.extend(f'--checkout.pickup-store-ids[{index}]={value}' for index, value in enumerate(args.pickup_store))
    return subprocess.run(command, cwd=root, env=env).returncode


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise SystemExit('\nОстановлено.')
