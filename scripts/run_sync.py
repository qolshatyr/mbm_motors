#!/usr/bin/env python3
"""Run one local catalog import; credentials are passed only through the environment."""

import argparse
import getpass
import os
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['full', 'incremental'])
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    env = dict(os.environ)
    if not env.get('CLOUDSHOP_TOKEN'):
        env['CLOUDSHOP_TOKEN'] = getpass.getpass('Токен CloudShop (только чтение, ввод скрыт): ').strip()
    if not env['CLOUDSHOP_TOKEN'] or any(c.isspace() for c in env['CLOUDSHOP_TOKEN']):
        raise SystemExit('Введите непустой токен без пробелов.')
    if 'DB_PASSWORD' not in env:
        env['DB_PASSWORD'] = getpass.getpass('Пароль локальной PostgreSQL (ввод скрыт): ')
    print('Импорт в локальную БД. CloudShop: только GET. Продажи не создаются.', flush=True)
    result = subprocess.run([
        str(root / 'mvnw'), '-q', 'spring-boot:run',
        '-Dspring-boot.run.arguments=--spring.main.web-application-type=none --catalog.sync.startup-mode=' + args.mode.upper(),
    ], cwd=root, env=env)
    return result.returncode


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise SystemExit('\nОтменено.')
