#!/usr/bin/env python3
"""Run the packaged React + Spring Boot site with private local credentials."""
import argparse
import getpass
import os
import re
from pathlib import Path
import subprocess
import tempfile
import zipfile


def configure_admin(env, jar, root):
    """Hash privately entered credentials using the packaged Spring Security library."""
    username = env.get('MBM_ADMIN_USERNAME') or input('Логин сотрудника: ').strip()
    if not re.fullmatch(r'[A-Za-z0-9._-]{1,64}', username):
        raise SystemExit('Укажите логин из латинских букв, цифр, точки, дефиса или подчёркивания, не более 64 символов.')
    password_hash = env.get('MBM_ADMIN_PASSWORD_HASH')
    if not password_hash:
        password = getpass.getpass('Пароль сотрудника (минимум 12 символов, ввод скрыт): ')
        if len(password) < 12 or len(password.encode('utf-8')) > 72:
            raise SystemExit('Пароль должен содержать минимум 12 символов и не более 72 байт UTF-8.')
        if password != getpass.getpass('Повторите пароль сотрудника: '):
            raise SystemExit('Пароли не совпадают.')
        with tempfile.TemporaryDirectory(prefix='mbm-admin-hash-') as directory:
            with zipfile.ZipFile(jar) as archive:
                crypto = next((name for name in archive.namelist()
                               if name.startswith('BOOT-INF/lib/spring-security-crypto-') and name.endswith('.jar')), None)
                if not crypto:
                    raise SystemExit('В JAR нет Spring Security. Пересоберите сайт.')
                library = Path(directory) / 'security-crypto.jar'
                library.write_bytes(archive.read(crypto))
            result = subprocess.run(['java', '--class-path', str(library),
                                     str(root / 'tools/admin/HashPassword.java')],
                                    input=password, text=True, capture_output=True)
            if result.returncode:
                raise SystemExit('Не удалось подготовить пароль. Проверьте Java 21 и пересоберите сайт.')
            password_hash = result.stdout.strip()
        if not re.fullmatch(r'\$2[aby]\$12\$[./A-Za-z0-9]{53}', password_hash):
            raise SystemExit('Не удалось подготовить BCrypt-пароль.')
    env.update(MBM_ADMIN_ENABLED='true', MBM_ADMIN_USERNAME=username,
               MBM_ADMIN_PASSWORD_HASH=password_hash)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--admin', action='store_true', help='Enable staff area with privately entered local credentials')
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
    if args.admin:
        configure_admin(env, jar, root)
        print(f'Служебный раздел: http://127.0.0.1:{args.port}/admin/requests', flush=True)
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
