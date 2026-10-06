#!/usr/bin/env python3
"""Run the saved four-request read-only catalog/synchronization check."""

import argparse
from datetime import datetime, timezone
import getpass
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--start-time', help='Начало периода YYYY-MM-DD HH:MM:SS; по умолчанию сегодня 00:00 UTC')
    args = parser.parse_args()
    start = args.start_time or datetime.now(timezone.utc).strftime('%Y-%m-%d 00:00:00')
    try:
        datetime.strptime(start, '%Y-%m-%d %H:%M:%S')
    except ValueError:
        raise SystemExit('Нужен формат start-time: YYYY-MM-DD HH:MM:SS.')
    cli = shutil.which('postman')
    if not cli:
        raise SystemExit('Postman CLI не найден.')
    root = Path(__file__).resolve().parents[2]
    path = root / 'postman' / 'assets' / 'CloudShop_Sync_ReadOnly.postman_collection.json'
    collection = json.loads(path.read_text())
    if len(collection['item']) != 4 or any(item['request']['method'] != 'GET' for item in collection['item']):
        raise SystemExit('Проверка отменена: ожидаются четыре GET-запроса.')
    print('Проверяем группы, метки, обновления и удалённые товары. Только чтение.')
    print('Начало периода: ' + start + ' (используем UTC; зона этих методов ещё проверяется).')
    print('Изменять или удалять товары для этого запуска не нужно.', flush=True)
    token = getpass.getpass('Токен CloudShop (ввод скрыт): ').strip()
    if not token or any(c.isspace() for c in token) or token.lower().startswith('bearer'):
        raise SystemExit('Введите только непустой токен без Bearer и пробелов.')
    environment = json.loads((root / 'postman' / 'assets' / 'CloudShop_Local.postman_environment.json').read_text())
    for v in environment['values']:
        if v['key'] == 'access_token':
            v['value'] = token
        elif v['key'] == 'start_time':
            v['value'] = start
    with tempfile.TemporaryDirectory(prefix='cloudshop-sync-') as directory:
        env_path = Path(directory) / 'environment.json'
        env_path.write_text(json.dumps(environment, ensure_ascii=False, indent=2))
        os.chmod(env_path, 0o600)
        try:
            result = subprocess.run([
                cli, 'collection', 'run', str(path), '-e', str(env_path),
                '--delay-request', '1000', '--timeout-request', '30000',
                '--timeout', '130000', '--bail', '--no-report-events',
            ], timeout=150)
        except subprocess.TimeoutExpired:
            raise SystemExit('Истёк срок запуска чтения; временное окружение удаляется.')
    print('Временное окружение удалено. Новых документов и изменений товаров нет.')
    print('Пустые списки не доказывают отсутствие всех изменений/удалений. Фото по ссылкам пока не проверяются.')
    return result.returncode


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise SystemExit('\nПроверка отменена.')
