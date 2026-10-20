#!/usr/bin/env python3
"""Give a partner a CloudShop customer card: link an existing card or create one once."""

import argparse
import getpass
import os
from pathlib import Path
import subprocess


def collect(mode, env):
    if not env.get('MBM_PARTNER_LOGIN'):
        env['MBM_PARTNER_LOGIN'] = input('Логин партнёра: ').strip()
    if mode == 'link' and not env.get('MBM_CLOUDSHOP_CUSTOMER_ID'):
        env['MBM_CLOUDSHOP_CUSTOMER_ID'] = input('ID карточки покупателя CloudShop (24 символа): ').strip()
    if mode == 'create' and not env.get('CLOUDSHOP_TOKEN'):
        env['CLOUDSHOP_TOKEN'] = getpass.getpass('Токен CloudShop с правом записи (ввод скрыт): ').strip()
    if 'DB_PASSWORD' not in env:
        env['DB_PASSWORD'] = getpass.getpass('Пароль PostgreSQL (ввод скрыт): ')
    return env


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['link', 'create'],
                        help='link — привязать существующую карточку; create — создать карточку в CloudShop')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    env = collect(args.mode, dict(os.environ))
    if args.mode == 'create':
        print('Будет создан один покупатель в CloudShop. При неясном ответе команду не повторяйте.', flush=True)
        if input('Продолжить? Введите «да»: ').strip().lower() != 'да':
            raise SystemExit('Отменено.')
    option = '--partner-link-customer' if args.mode == 'link' else '--partner-create-customer'
    result = subprocess.run([
        str(root / 'mvnw'), '-q', 'spring-boot:run',
        '-Dspring-boot.run.arguments=--spring.main.web-application-type=none ' + option,
    ], cwd=root, env=env)
    return result.returncode


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise SystemExit('\nОтменено.')
