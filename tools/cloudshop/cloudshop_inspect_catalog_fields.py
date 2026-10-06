#!/usr/bin/env python3
"""Inspect up to five ambiguous catalog records from an audit summary via GET."""

import argparse
import getpass
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path, help='Сводка аудита; по умолчанию последняя локальная')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    reports = sorted((root / '.cloudshop-audit').glob('catalog-*.json'))
    path = args.report or (reports[-1] if reports else None)
    if path is None:
        raise SystemExit('Сводка аудита не найдена.')
    report = json.loads(path.read_text())
    samples = report.get('issue_samples', {})
    ids = []
    for key, limit in [('invalid_stock_shape', 3), ('invalid_store_prices_shape', 2)]:
        for sample in samples.get(key, [])[:limit]:
            identifier = sample.get('id', '')
            if re.fullmatch(r'[0-9a-fA-F]{24}', identifier) and identifier not in ids:
                ids.append(identifier)
    if not ids:
        raise SystemExit('Нет примеров неоднозначных stock/store_prices.')
    cli = shutil.which('postman')
    if not cli:
        raise SystemExit('Postman CLI не найден.')
    env = json.loads((root / 'postman' / 'assets' / 'CloudShop_Local.postman_environment.json').read_text())
    print(f'Проверяем {len(ids)} товаров, только GET. Ничего не исправляем в CloudShop.', flush=True)
    token = getpass.getpass('Токен CloudShop (ввод скрыт): ').strip()
    if not token or any(c.isspace() for c in token) or token.lower().startswith('bearer'):
        raise SystemExit('Введите только токен без Bearer и пробелов.')
    for v in env['values']:
        if v['key'] == 'access_token':
            v['value'] = token
    with tempfile.TemporaryDirectory(prefix='cloudshop-fields-inspect-') as directory:
        env_path = Path(directory) / 'environment.json'
        response_path = Path(directory) / 'response.json'
        env_path.write_text(json.dumps(env, ensure_ascii=False))
        os.chmod(env_path, 0o600)
        for identifier in ids:
            response_path.write_text('')
            os.chmod(response_path, 0o600)
            result = subprocess.run([
                cli, 'request', 'GET', '{{base_url}}/product/' + identifier,
                '-e', str(env_path), '-H', 'Accept:application/json',
                '-H', 'X-CloudShop-API-Access-Token:{{access_token}}',
                '--response-only', '--redirects-ignore', '--timeout', '30000',
                '--output', str(response_path), '--no-report-events',
            ], capture_output=True, text=True, timeout=90)
            try:
                response = json.loads(response_path.read_text()).get('response', {})
                print('HTTP: ' + str(response.get('code')), flush=True)
                if result.returncode or response.get('code') != 200:
                    raise SystemExit('Ошибка чтения; автоматического повтора нет.')
                product = json.loads(response['body'])
            except (ValueError, KeyError, AttributeError):
                raise SystemExit('Ответ не удалось разобрать; остановлено.')
            if not isinstance(product, dict) or product.get('id') != identifier:
                raise SystemExit('Ответ не соответствует запрошенному товару.')
            safe = {'id': identifier, 'sku': product.get('sku')}
            for key in ['price', 'store_prices', 'stock', 'unit', 'in_stock']:
                safe[key] = {'present': key in product, 'type': type(product.get(key)).__name__, 'value': product.get(key)}
            print(json.dumps(safe, ensure_ascii=False, indent=2), flush=True)
            time.sleep(1)
    print('Временные ответы и окружение удалены. Только чтение.')


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        raise SystemExit('\nОтменено.')
    except subprocess.TimeoutExpired:
        raise SystemExit('Таймаут чтения; автоматического повтора нет.')
