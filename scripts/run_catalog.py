#!/usr/bin/env python3
"""Start the local catalog, optionally enabling read-only CloudShop synchronization."""

import argparse
import getpass
import os
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sync', action='store_true', help='Обновлять каталог из CloudShop каждые 5 минут')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    env = dict(os.environ)
    if 'DB_PASSWORD' not in env:
        env['DB_PASSWORD'] = getpass.getpass('Пароль локальной PostgreSQL (ввод скрыт): ')
    if args.sync:
        if not env.get('CLOUDSHOP_TOKEN'):
            env['CLOUDSHOP_TOKEN'] = getpass.getpass('Токен CloudShop (только чтение, ввод скрыт): ').strip()
        if not env['CLOUDSHOP_TOKEN'] or any(c.isspace() for c in env['CLOUDSHOP_TOKEN']):
            raise SystemExit('Введите непустой токен без пробелов.')
    print('Каталог: http://localhost:8080/catalog. Остановка: Ctrl+C.', flush=True)
    command = [str(root / 'mvnw'), '-q', 'spring-boot:run',
               '-Dspring-boot.run.arguments=--catalog.sync.startup-mode=NONE --catalog.sync.enabled=' + str(args.sync).lower()]
    return subprocess.run(command, cwd=root, env=env).returncode


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise SystemExit('\nОстановлено.')
