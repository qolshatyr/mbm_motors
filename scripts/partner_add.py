#!/usr/bin/env python3
"""Create a partner account; contacts and passwords are passed only through the environment."""

import getpass
import os
from pathlib import Path
import subprocess


def ask(prompt):
    value = input(prompt).strip()
    if not value:
        raise SystemExit('Поле не может быть пустым.')
    return value


def collect(env):
    """Fill MBM_PARTNER_* from the existing environment or interactive prompts."""
    for key, prompt in [
        ('MBM_PARTNER_LOGIN', 'Логин (латиница, цифры, точка, дефис): '),
        ('MBM_PARTNER_COMPANY', 'Компания: '),
        ('MBM_PARTNER_CONTACT', 'Контактное лицо: '),
        ('MBM_PARTNER_PHONE', 'Телефон с кодом страны: '),
    ]:
        if not env.get(key):
            env[key] = ask(prompt)
    if not env.get('MBM_PARTNER_PASSWORD'):
        password = getpass.getpass('Пароль партнёра, не короче 10 символов (ввод скрыт): ')
        if password != getpass.getpass('Повторите пароль: '):
            raise SystemExit('Пароли не совпадают.')
        env['MBM_PARTNER_PASSWORD'] = password
    if 'DB_PASSWORD' not in env:
        env['DB_PASSWORD'] = getpass.getpass('Пароль PostgreSQL (ввод скрыт): ')
    return env


def main():
    root = Path(__file__).resolve().parent.parent
    env = collect(dict(os.environ))
    print('Создание учётной записи партнёра. CloudShop не используется.', flush=True)
    result = subprocess.run([
        str(root / 'mvnw'), '-q', 'spring-boot:run',
        '-Dspring-boot.run.arguments=--spring.main.web-application-type=none --partner-add',
    ], cwd=root, env=env)
    return result.returncode


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise SystemExit('\nОтменено.')
