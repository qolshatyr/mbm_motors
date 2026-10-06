#!/usr/bin/env python3
"""Read sale #35236 and display only selected non-contact fields."""

import getpass
from datetime import datetime
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from zoneinfo import ZoneInfo

SUBMITTED_DATE = '2026-10-03 19:35:33'


def describe_time(value):
    """Interpret an explicit API offset without assigning a zone to a naive value."""
    if not isinstance(value, str):
        return {'raw': value, 'note': 'Нет строковой даты для сравнения.'}
    try:
        parsed = datetime.fromisoformat(value.replace('Z', '+00:00'))
    except ValueError:
        return {'raw': value, 'note': 'Формат даты не распознан; не делаем вывод о часовом поясе.'}
    if parsed.tzinfo is None:
        return {'raw': value, 'note': 'Смещение отсутствует. Часовой пояс по этой строке определить нельзя.'}
    submitted_local = datetime.strptime(SUBMITTED_DATE, '%Y-%m-%d %H:%M:%S').replace(tzinfo=ZoneInfo('Asia/Almaty'))
    return {
        'raw': value,
        'as_utc': parsed.astimezone(ZoneInfo('UTC')).isoformat(),
        'as_almaty': parsed.astimezone(ZoneInfo('Asia/Almaty')).isoformat(),
        'difference_from_submitted_as_almaty_seconds': (parsed - submitted_local).total_seconds(),
        'note': 'Сравнение с гипотезой, что переданная строка была местным временем Алматы; общее правило API ещё не подтверждено.',
    }


def relation_ids(value):
    """Only identity/type fields; never names, phones, emails or addresses."""
    if not isinstance(value, dict):
        return None
    return {key: value[key] for key in ['id', '_id', 'type'] if key in value}


def numeric_flags(value):
    """Expose field names and numeric/boolean values without free text or contacts."""
    if not isinstance(value, dict):
        return None
    return {
        'available_keys': sorted(value.keys()),
        'values': {key: item for key, item in value.items()
                   if key.lower() not in ['phone', 'email', 'address', 'name']
                   and (item is None or isinstance(item, (bool, int, float)))},
    }


def main():
    cli = shutil.which('postman')
    if not cli:
        raise SystemExit('Postman CLI не найден.')
    root = Path(__file__).resolve().parents[2]
    env = json.loads((root / 'postman' / 'assets' / 'CloudShop_Local.postman_environment.json').read_text())
    token = getpass.getpass('Токен CloudShop (ввод скрыт): ').strip()
    if not token or any(c.isspace() for c in token):
        raise SystemExit('Введите непустой токен без пробелов.')
    for v in env['values']:
        if v['key'] == 'access_token':
            v['value'] = token
    period = '&time_start=2026-10-01%2000:00:00&time_end=2026-10-05%2023:59:59'
    queries = [
        ('По номеру, без фильтра состояния', '{{base_url}}/documents/sales?number=35236&limit=100&offset=0' + period),
        ('По тестовой метке, без фильтра состояния', '{{base_url}}/documents/sales?comment=TEST-SITE-CAR-CITY-105427-001&limit=100&offset=0' + period),
    ]
    print('Читаю только продажу №35236; новых документов не создаю.', flush=True)
    with tempfile.TemporaryDirectory(prefix='cloudshop-verify-') as directory:
        path = Path(directory) / 'environment.json'
        path.write_text(json.dumps(env, ensure_ascii=False))
        os.chmod(path, 0o600)
        matches = []
        for label, url in queries:
            print(label + '…', flush=True)
            try:
                result = subprocess.run([
                    cli, 'request', 'GET', url, '-e', str(path),
                    '-H', 'X-CloudShop-API-Access-Token:{{access_token}}',
                    '-H', 'Accept:application/json', '--response-only',
                    '--timeout', '30000', '--no-report-events',
                ], capture_output=True, text=True, timeout=90)
            except subprocess.TimeoutExpired:
                raise SystemExit('Таймаут чтения. Никаких изменений не выполнялось.')
            try:
                data = json.loads(result.stdout)
            except json.JSONDecodeError:
                raise SystemExit('Ответ не удалось разобрать как JSON; никаких изменений не выполнялось.')
            if not isinstance(data, list):
                error = data.get('error') or data.get('message') if isinstance(data, dict) else 'Неожиданный формат'
                raise SystemExit('Чтение не удалось: ' + str(error))
            print('Получено записей: ' + str(len(data)), flush=True)
            matches = [d for d in data if isinstance(d, dict) and d.get('id') == '6ac112bf4584ee042a3a7172']
            if matches:
                break
    if not matches:
        raise SystemExit('Документ не найден ни по номеру, ни по метке без фильтра состояния. Доступность непроведённых документов в GET /documents/sales не подтверждена. Документ существует в интерфейсе; повторный POST запрещён.')
    for document in matches:
        safe = {key: document.get(key) for key in [
            'id', 'order_number', 'created_at', 'updated_at', 'processed_at', 'location_id',
            'client_id', 'total_price', 'financial_status', 'note', 'document_flags',
        ]}
        safe['line_items'] = [
            {key: item.get(key) for key in ['id', 'sku', 'quantity', 'price']}
            for item in (document.get('line_items') or [])
        ]
        safe['relation_ids'] = {
            key: relation_ids(document.get(key))
            for key in ['customer', 'source', 'destination']
        }
        safe['payment_terms_summary'] = numeric_flags(document.get('payment_terms'))
        safe['metrics_summary'] = numeric_flags(document.get('metrics'))
        print(json.dumps(safe, ensure_ascii=False, indent=2))
        comparison = {
            'submitted_date_without_offset': SUBMITTED_DATE,
            'ui_observed_date': '2026-10-04 00:35',
            'processed_at_interpretation': describe_time(document.get('processed_at')),
            'created_at_interpretation': describe_time(document.get('created_at')),
        }
        print('Сравнение времени:')
        print(json.dumps(comparison, ensure_ascii=False, indent=2))
        print('financial_status описывает оплату, document_flags.status — проведение. Не приравниваем partially_paid к факту платежа без проверки payment_terms.')
    print('Выполнено только чтение. Статус документа не изменялся.')


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        raise SystemExit('\nОтменено.')
