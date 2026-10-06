#!/usr/bin/env python3
"""Compare updated results with a paginated catalog, using GET only."""

import argparse
from datetime import datetime, timezone
import getpass
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
from urllib.parse import urlencode


def records(rows):
    if not isinstance(rows, list) or any(
            not isinstance(p, dict) or not isinstance(p.get('id'), str) or not p['id']
            for p in rows):
        raise ValueError('Ожидался массив товаров со строковыми ID.')
    return {p['id']: p for p in rows}


def compare(catalog, before, after, start):
    expected, boundary, unknown = set(), set(), 0
    for identifier, product in catalog.items():
        try:
            stamp = datetime.strptime(product.get('updated_at'), '%Y-%m-%d %H:%M:%S')
        except (ValueError, TypeError):
            unknown += 1
            continue
        if stamp > start:
            expected.add(identifier)
        elif stamp == start:
            boundary.add(identifier)
    missing = expected - set(before) - set(after)
    return {
        'catalog_unique_products': len(catalog),
        'updated_before_count': len(before), 'updated_after_count': len(after),
        'updated_id_sets_equal': set(before) == set(after),
        'catalog_dates_strictly_after_start': len(expected),
        'catalog_dates_equal_start': len(boundary),
        'catalog_dates_unreadable': unknown,
        'missing_from_both_updated_reads': len(missing),
        'missing_samples': [{'id': i, 'updated_at': catalog[i]['updated_at']}
                            for i in sorted(missing)[:5]],
    }


def read_catalog(get, max_pages):
    catalog, offset = {}, 0
    for page in range(max_pages):
        print(f'Каталог: страница {page + 1}, offset={offset}', flush=True)
        rows = get('/product?' + urlencode({'limit': 1000, 'offset': offset}))
        batch = records(rows)
        if not rows:
            return catalog
        if not set(batch) - set(catalog):
            raise ValueError('Страница не содержит новых ID; полнота не подтверждена.')
        for identifier, product in batch.items():
            catalog.setdefault(identifier, {'updated_at': product.get('updated_at')})
        offset += len(rows)
    raise ValueError('Достигнут предел страниц; полный результат не заявляется.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--start-time', required=True, help='YYYY-MM-DD HH:MM:SS, рабочая гипотеза UTC')
    parser.add_argument('--max-pages', type=int, default=100)
    args = parser.parse_args()
    start = datetime.strptime(args.start_time, '%Y-%m-%d %H:%M:%S')
    if not 1 <= args.max_pages <= 1000:
        raise ValueError('max-pages должен быть от 1 до 1000.')
    cli = shutil.which('postman')
    if not cli:
        raise ValueError('Postman CLI не найден.')
    root = Path(__file__).resolve().parents[2]
    env = json.loads((root / 'postman' / 'assets' / 'CloudShop_Local.postman_environment.json').read_text())
    print('Только GET: updated → весь каталог → updated. Товары и продажи не меняются.')
    print('Время сравниваем как строки UTC; зона и граница start_time ещё не доказаны.', flush=True)
    token = getpass.getpass('Токен CloudShop (ввод скрыт): ').strip()
    if not token or any(c.isspace() for c in token) or token.lower().startswith('bearer'):
        raise ValueError('Введите только токен без Bearer и пробелов.')
    for entry in env['values']:
        if entry['key'] == 'access_token':
            entry['value'] = token
    with tempfile.TemporaryDirectory(prefix='cloudshop-updated-') as folder:
        env_path, response_path = Path(folder) / 'env.json', Path(folder) / 'response.json'
        env_path.touch(mode=0o600)
        env_path.write_text(json.dumps(env, ensure_ascii=False))

        def get(path):
            response_path.touch(mode=0o600, exist_ok=True)
            response_path.write_text('')
            result = subprocess.run([
                cli, 'request', 'GET', '{{base_url}}' + path, '-e', str(env_path),
                '-H', 'Accept:application/json', '-H', 'X-CloudShop-API-Access-Token:{{access_token}}',
                '--response-only', '--redirects-ignore', '--timeout', '30000',
                '--output', str(response_path), '--no-report-events',
            ], capture_output=True, text=True, timeout=90)
            response = json.loads(response_path.read_text())['response']
            print('HTTP: ' + str(response.get('code')), flush=True)
            if result.returncode or response.get('code') != 200:
                raise ValueError('Ошибка чтения; повторов нет, сверка остановлена.')
            rows = json.loads(response['body'])
            records(rows)
            time.sleep(1)
            return rows

        url = '/product/updated?' + urlencode({'start_time': args.start_time})
        before = records(get(url))
        catalog = read_catalog(get, args.max_pages)
        after = records(get(url))
    report = {
        'finished_at_utc': datetime.now(timezone.utc).isoformat(),
        'start_time': args.start_time,
        **compare(catalog, before, after, start),
        'limitations': [
            'Обход неатомарный: каталог и выдача могут меняться между запросами.',
            'Сравнение предполагает общую зону updated_at/start_time; она пока не подтверждена.',
            'Равные ID до/после не доказывают неизменность содержимого.',
            'Совпадение списков не доказывает отражение изменений только остатка.',
            'Пропуски являются наблюдением; жёсткий лимит 300 не считается доказанным.',
        ],
    }
    folder = root / '.cloudshop-audit'
    folder.mkdir(mode=0o700, exist_ok=True)
    path = folder / ('updated-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ') + '.json')
    path.touch(mode=0o600)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print(json.dumps(report, ensure_ascii=False, indent=2))
    print('Сводка сохранена: ' + str(path))
    print('Временное окружение и ответы удалены. Только чтение.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, TypeError, OSError, subprocess.TimeoutExpired) as error:
        # Do not print raw CLI output or metadata: they may contain authentication headers.
        raise SystemExit('Проверка остановлена: ' + (str(error) if isinstance(error, ValueError) else type(error).__name__))
    except KeyboardInterrupt:
        raise SystemExit('\nОтменено; полный результат не заявляется.')
