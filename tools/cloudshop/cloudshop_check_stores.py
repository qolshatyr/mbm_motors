#!/usr/bin/env python3
"""Run the saved read-only stores request with a privately entered token."""

import getpass
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    cli = shutil.which('postman')
    if cli is None:
        raise SystemExit('Postman CLI не найден в PATH.')

    root = Path(__file__).resolve().parents[2]
    collection = json.loads(
        (root / 'postman' / 'assets' / 'CloudShop_ReadOnly.postman_collection.json').read_text()
    )
    environment = json.loads(
        (root / 'postman' / 'assets' / 'CloudShop_Local.postman_environment.json').read_text()
    )
    print('Вставьте токен через Ctrl+Shift+V и нажмите Enter.')
    print('Символы токена и звёздочки не отображаются.')
    token = getpass.getpass('Токен CloudShop: ').strip()
    if not token:
        raise SystemExit('Токен пустой. Запустите скрипт снова.')
    if any(character.isspace() for character in token):
        raise SystemExit('Внутри токена есть пробел или перенос строки. Скопируйте только токен.')
    if token.lower().startswith('bearer') or token.startswith(('"', "'")):
        raise SystemExit('Введите только токен, без Bearer и кавычек.')
    for variable in environment['values']:
        if variable['key'] == 'access_token':
            variable['value'] = token

    request_name = '01 — Магазины'
    stores = next(item for item in collection['item'] if item['name'] == request_name)
    if stores['request']['method'] != 'GET':
        raise SystemExit('Запуск отменён: запрос магазинов должен быть GET.')
    # Explicit header avoids relying on the CLI's inherited collection auth.
    stores['request']['auth'] = {'type': 'noauth'}
    headers = stores['request'].setdefault('header', [])
    headers[:] = [
        header for header in headers
        if header['key'].lower() != 'x-cloudshop-api-access-token'
    ]
    headers.append({
        'key': 'X-CloudShop-API-Access-Token',
        'value': '{{access_token}}',
        'type': 'text',
    })
    stores.setdefault('event', []).append({
        'listen': 'test',
        'script': {
            'type': 'text/javascript',
            'exec': ['console.log("Ответ магазинов: " + pm.response.text());'],
        },
    })

    with tempfile.TemporaryDirectory(prefix='cloudshop-cli-') as directory:
        paths = []
        for filename, data in [('environment.json', environment), ('collection.json', collection)]:
            path = Path(directory) / filename
            path.write_text(json.dumps(data, ensure_ascii=False, indent=2))
            os.chmod(path, 0o600)
            paths.append(str(path))
        print('Токен принят. Запускаю только GET-запрос магазинов.', flush=True)
        result = subprocess.run([
            cli, 'collection', 'run', paths[1], '-e', paths[0],
            '-i', request_name, '--timeout-request', '30000',
            '--no-report-events',
        ])
    print('Временные файлы удалены.')
    return result.returncode


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise SystemExit('\nПроверка отменена.')
