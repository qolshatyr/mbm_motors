#!/usr/bin/env python3
"""Save a private catalog snapshot and compare with the previous complete snapshot."""

import argparse
from datetime import datetime, timedelta, timezone
import getpass
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
from urllib.parse import urlencode

if __package__:
    from .cloudshop_check_updated_completeness import records
else:
    from cloudshop_check_updated_completeness import records


def normalized(product):
    result = dict(product)
    for key in ('stock', 'store_prices'):
        if isinstance(result.get(key), list) and not result[key]:
            result[key] = {}
    return result


def differences(previous, current, updated_ids=None):
    old, new = previous['products'], current['products']
    shared = set(old) & set(new)
    changed, stock_changed, stock_only, unchanged_stamp = [], [], [], []
    samples = []
    for identifier in sorted(shared):
        a, b = normalized(old[identifier]), normalized(new[identifier])
        keys = sorted(k for k in set(a) | set(b)
                      if (k in a) != (k in b) or a.get(k) != b.get(k))
        if not keys:
            continue
        changed.append(identifier)
        if 'stock' in keys:
            stock_changed.append(identifier)
            if set(keys) <= {'stock', 'in_stock', 'updated_at'}:
                stock_only.append(identifier)
            if a.get('updated_at') == b.get('updated_at'):
                unchanged_stamp.append(identifier)
        if len(samples) < 5:
            samples.append({'id': identifier, 'changed_fields': keys,
                            'old_updated_at': a.get('updated_at'), 'new_updated_at': b.get('updated_at'),
                            'old_stock': a.get('stock'), 'new_stock': b.get('stock')})
    missing = None if updated_ids is None else sorted(set(stock_changed) - updated_ids)
    return {
        'previous_count': len(old), 'current_count': len(new),
        'new_ids': sorted(set(new) - set(old)),
        'absent_ids': sorted(set(old) - set(new)),
        'changed_existing_count': len(changed),
        'stock_changed_count': len(stock_changed),
        'stock_changed_without_other_fields_count': len(stock_only),
        'stock_changed_with_same_updated_at_count': len(unchanged_stamp),
        'stock_changed_ids_missing_from_updated': missing,
        'changed_samples': samples,
    }


def capture(get, max_pages):
    products, offset, duplicates = {}, 0, 0
    for page in range(max_pages):
        print(f'Каталог: страница {page + 1}, offset={offset}', flush=True)
        rows = get('/product?' + urlencode({'limit': 1000, 'offset': offset}))
        batch = records(rows)
        if not rows:
            return products, duplicates
        if not set(batch) - set(products):
            raise ValueError('Повторная страница: полный снимок не сохраняется.')
        duplicates += len(rows) - len(set(batch) - set(products))
        for identifier, product in batch.items():
            products.setdefault(identifier, product)
        offset += len(rows)
    raise ValueError('Предел страниц: полный снимок не сохраняется.')


def save_private(path, data):
    # Exclusive creation prevents replacing the baseline even on a name collision.
    with path.open('x') as file:
        path.chmod(0o600)
        json.dump(data, file, ensure_ascii=False, indent=2)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--compare', type=Path, help='Явный исходный снимок; иначе последний полный локальный')
    parser.add_argument('--max-pages', type=int, default=100)
    args = parser.parse_args()
    if not 1 <= args.max_pages <= 1000:
        raise ValueError('max-pages должен быть от 1 до 1000.')
    root = Path(__file__).resolve().parents[2]
    folder = root / '.cloudshop-audit'
    snapshots = sorted(p for p in folder.glob('snapshot-*.json') if not p.name.startswith('snapshot-diff-'))
    baseline_path = args.compare or (snapshots[-1] if snapshots else None)
    previous = json.loads(baseline_path.read_text()) if baseline_path else None
    if previous is not None and (previous.get('schema_version') != 1 or not previous.get('reached_empty_page')
                                 or not isinstance(previous.get('products'), dict)):
        raise ValueError('Исходный файл не является полным снимком версии 1.')
    cli = shutil.which('postman')
    if not cli:
        raise ValueError('Postman CLI не найден.')
    env = json.loads((root / 'postman' / 'assets' / 'CloudShop_Local.postman_environment.json').read_text())
    print('Только GET. Сохраняем все поля товаров, без заголовков и токена.', flush=True)
    print('Исходный снимок: ' + (str(baseline_path) if baseline_path else 'нет — создаём первый'), flush=True)
    token = getpass.getpass('Токен CloudShop (ввод скрыт): ').strip()
    if not token or any(c.isspace() for c in token) or token.lower().startswith('bearer'):
        raise ValueError('Введите только токен без Bearer и пробелов.')
    for entry in env['values']:
        if entry['key'] == 'access_token':
            entry['value'] = token
    started = datetime.now(timezone.utc).isoformat()
    with tempfile.TemporaryDirectory(prefix='cloudshop-snapshot-') as directory:
        env_path, response_path = Path(directory) / 'env.json', Path(directory) / 'response.json'
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
                raise ValueError('Ошибка GET; автоматических повторов нет.')
            rows = json.loads(response['body'])
            records(rows)
            time.sleep(1)
            return rows

        products, duplicates = capture(get, args.max_pages)
        snapshot = {'schema_version': 1, 'started_at_utc': started,
                    'finished_at_utc': datetime.now(timezone.utc).isoformat(),
                    'reached_empty_page': True, 'duplicate_rows': duplicates, 'products': products}
        folder.mkdir(mode=0o700, exist_ok=True)
        stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
        snapshot_path = folder / ('snapshot-' + stamp + '.json')
        save_private(snapshot_path, snapshot)
        print('Снимок сохранён: ' + str(snapshot_path), flush=True)
        print('Уникальных товаров: ' + str(len(products)), flush=True)
        if previous is not None:
            # Overlap covers changes during the baseline traversal; time zone is a working hypothesis.
            start = datetime.fromisoformat(previous['started_at_utc']).astimezone(timezone.utc) - timedelta(minutes=1)
            updated_ids, updated_error = None, None
            try:
                updated_ids = set(records(get('/product/updated?' + urlencode({
                    'start_time': start.strftime('%Y-%m-%d %H:%M:%S')}))))
            except (ValueError, KeyError, TypeError, OSError, subprocess.TimeoutExpired) as error:
                updated_error = type(error).__name__
            report = {'baseline': str(baseline_path), 'snapshot': str(snapshot_path),
                      'updated_start_time_utc_hypothesis': start.isoformat(),
                      'updated_unique_count': None if updated_ids is None else len(updated_ids),
                      'updated_error': updated_error,
                      **differences(previous, snapshot, updated_ids),
                      'limitations': [
                          'Обходы неатомарные; изменения во время чтения и промежуточные операции могут быть не видны.',
                          'Отсутствие ID во втором снимке не доказывает удаление.',
                          'Изменение только stock/in_stock/updated_at — наблюдение полей, не доказательство причины операции.',
                          'Зона start_time пока предполагается UTC; выдача updated может иметь ограничения.',
                          'Отчёт содержит изменения, исходные снимки сохраняются отдельно и не перезаписываются.',
                      ]}
            report_path = folder / ('snapshot-diff-' + stamp + '.json')
            save_private(report_path, report)
            print(json.dumps(report, ensure_ascii=False, indent=2), flush=True)
            print('Сравнение сохранено: ' + str(report_path), flush=True)
    print('Временные ответы и окружение удалены. Никаких изменений в CloudShop.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, TypeError, OSError, subprocess.TimeoutExpired) as error:
        raise SystemExit('Проверка остановлена: ' + type(error).__name__ + '. Сохранённые ранее снимки не изменены.')
    except KeyboardInterrupt:
        raise SystemExit('\nОтменено; сохранённые ранее снимки не изменены.')
