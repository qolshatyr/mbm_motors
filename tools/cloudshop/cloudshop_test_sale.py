#!/usr/bin/env python3
"""Preview or send one unposted test sale; never retry an attempted submission."""

import argparse
from datetime import datetime
import getpass
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from zoneinfo import ZoneInfo

MARKER = 'TEST-SITE-CAR-CITY-105427-001'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--send', action='store_true', help='Отправить один POST; без флага только просмотр')
    parser.add_argument('--time-test', action='store_true', help='Отдельный однократный тест даты UTC с новой фиксированной меткой')
    parser.add_argument('--date', help='Дата документа YYYY-MM-DD HH:MM:SS в UTC (по наблюдению теста #35236)')
    parser.add_argument('--customer-id', help='API ID согласованного тестового покупателя')
    parser.add_argument('--unit', help='Проверенная непустая единица товара из CloudShop')
    args = parser.parse_args()
    marker = 'TEST-SITE-CAR-CITY-105427-TIME-002' if args.time_test else MARKER
    if args.time_test and args.date:
        raise SystemExit('Для теста времени дата формируется автоматически в UTC; --date не используйте.')
    if args.customer_id and not re.fullmatch(r'[0-9a-fA-F]{24}', args.customer_id):
        raise SystemExit('customer-id должен быть API ID: 24 шестнадцатеричных символа.')
    if args.send and (not args.customer_id or not args.unit or not args.unit.strip()):
        raise SystemExit('API требует покупателя и единицу: укажите --customer-id и --unit. Запрос не отправлен.')
    date = args.date or datetime.now(ZoneInfo('UTC')).strftime('%Y-%m-%d %H:%M:%S')
    datetime.strptime(date, '%Y-%m-%d %H:%M:%S')
    payload = {
        'status': False,
        'store_id': '64feaaa51c9799143b532fcf',
        'date': date,
        'discount_percent': 0,
        'discount_sum': 0,
        'products': [{
            'product_id': '6a6e12abb0b00726916dde8b',
            'quantity': 1,
            'discount_percent': 0,
            'unit': args.unit.strip() if args.unit else '',
            'price': 3000,
        }],
        'comment': marker + ' — тест интеграции, не выдавать товар',
    }
    if args.customer_id:
        payload['customer_id'] = args.customer_id
    print(json.dumps(payload, ensure_ascii=False, indent=2), flush=True)
    print('Магазин Car City; непроведённая продажа; 1 товар × 3000 ₸.', flush=True)
    expected_local = datetime.strptime(date, '%Y-%m-%d %H:%M:%S').replace(tzinfo=ZoneInfo('UTC')).astimezone(ZoneInfo('Asia/Almaty'))
    print('Ожидаемая дата в интерфейсе Алматы: ' + expected_local.strftime('%Y-%m-%d %H:%M:%S'), flush=True)
    if not args.unit:
        print('Единица не задана: API отклоняет пустой unit. Для отправки нужен --unit.', flush=True)
    if not args.customer_id:
        print('Покупатель не задан: API требует customer_id. Для отправки нужен --customer-id.', flush=True)
    if not args.date:
        print('Дата сформирована в UTC: в тесте #35236 API интерпретировал строку даты как UTC.', flush=True)
    if not args.send:
        print('Только просмотр. Для однократной отправки используйте --send.')
        return 0

    root = Path(__file__).resolve().parents[2]
    ledger_dir = root / '.cloudshop-test-sales'
    ledger_path = ledger_dir / (marker + '.json')
    if ledger_path.exists():
        raise SystemExit('Эта тестовая отправка уже отмечена. Не повторяйте её: сначала сверка в CloudShop.')
    cli = shutil.which('postman')
    if not cli:
        raise SystemExit('Postman CLI не найден.')
    print('Тест создаст реальный документ. Его влияние на резерв ещё не проверено.')
    print('Продолжайте только с разрешения владельца; проверьте дату и остаток до/после.')
    token = getpass.getpass('Токен RO + Orders (ввод скрыт): ').strip()
    if not token or any(c.isspace() for c in token) or token.lower().startswith('bearer'):
        raise SystemExit('Введите только токен, без Bearer, пробелов и переносов.')
    environment = json.loads((root / 'postman' / 'assets' / 'CloudShop_Local.postman_environment.json').read_text())
    for value in environment['values']:
        if value['key'] == 'access_token':
            value['value'] = token
    # Persist the attempt BEFORE invoking the HTTP client. Concurrent starts cannot both send.
    ledger_dir.mkdir(mode=0o700, exist_ok=True)
    record = {'marker': marker, 'payload': payload, 'state': 'UNKNOWN'}
    with tempfile.TemporaryDirectory(prefix='cloudshop-sale-') as directory:
        env_path = Path(directory) / 'environment.json'
        body_path = Path(directory) / 'body.json'
        for path, data in [(env_path, environment), (body_path, payload)]:
            path.write_text(json.dumps(data, ensure_ascii=False))
            os.chmod(path, 0o600)
        try:
            with ledger_path.open('x') as file:
                os.chmod(ledger_path, 0o600)
                json.dump(record, file, ensure_ascii=False, indent=2)
        except FileExistsError:
            raise SystemExit('Другой запуск уже отметил отправку. Запрос отменён.')
        try:
            result = subprocess.run([
                cli, 'request', 'POST', '{{base_url}}/sales',
                '-e', str(env_path),
                '-H', 'X-CloudShop-API-Access-Token:{{access_token}}',
                '-H', 'Content-Type:application/json',
                '-H', 'Accept:application/json',
                # In this CLI, @file is uploaded as multipart instead of raw JSON.
                # This test payload contains no credentials or customer data.
                '--body', body_path.read_text(),
                # CLI rejects explicit --retry 0 despite documenting 0 as the default.
                # Omit --retry: its default is no retries.
                '--timeout', '30000', '--redirects-ignore',
                '--no-report-events',
                '--script-post-request',
                'console.log("SALE_HTTP_STATUS:" + pm.response.code); console.log("SALE_RESPONSE:" + pm.response.text());',
            ], capture_output=True, text=True, timeout=90)
        except (subprocess.TimeoutExpired, KeyboardInterrupt, OSError):
            raise SystemExit('Результат UNKNOWN. Не повторяйте POST; ищите документ по метке ' + marker)
        safe_stdout = (result.stdout or '').replace(token, '[TOKEN HIDDEN]')
        print(safe_stdout, flush=True)
        safe_stderr = (result.stderr or '').replace(token, '[TOKEN HIDDEN]')
        if safe_stderr:
            print(safe_stderr, flush=True)
        match = re.search(r'SALE_HTTP_STATUS:(\d{3})', result.stdout)
        status = int(match.group(1)) if match else None
        record['http_status'] = status
        record['cli_exit_code'] = result.returncode
        record['response_output'] = safe_stdout
        record['cli_error'] = safe_stderr
        response_match = re.search(r'SALE_RESPONSE:(\{[^\n]*\})', result.stdout)
        response_data = None
        if response_match:
            try:
                response_data = json.loads(response_match.group(1))
            except json.JSONDecodeError:
                pass
        created = isinstance(response_data, dict) and bool(
            re.fullmatch(r'[0-9a-fA-F]{24}', str(response_data.get('id', '')))
        ) and isinstance(response_data.get('number'), int)
        if status == 201 or (status == 200 and created):
            record['state'] = 'SENT'
            if created:
                record['external_id'] = response_data['id']
                record['external_number'] = response_data['number']
        else:
            record['state'] = 'REJECTED' if status in (400, 401, 403, 404, 422) else 'UNKNOWN'
        ledger_path.write_text(json.dumps(record, ensure_ascii=False, indent=2))
        print('Результат: ' + record['state'])
        print('Метка для поиска: ' + marker)
        print('Повторный запуск заблокирован. Проверьте документ, цену, статус и остаток/резерв в CloudShop.')
        return 0 if record['state'] == 'SENT' else 1


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise SystemExit('\nОтменено.')
