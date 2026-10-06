#!/usr/bin/env python3
"""Read the one authorized UTC time test, without creating or changing documents."""

from datetime import datetime, timedelta
import getpass
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
from urllib.parse import urlencode
from zoneinfo import ZoneInfo

MARKER = 'TEST-SITE-CAR-CITY-105427-TIME-002'


def compare_time(raw, submitted):
    parsed = datetime.fromisoformat(raw.replace('Z', '+00:00'))
    if parsed.tzinfo is None:
        raise ValueError('Ответ не содержит смещения времени; вывод о зоне невозможен.')
    return {
        'api_raw': raw,
        'api_as_almaty': parsed.astimezone(ZoneInfo('Asia/Almaty')).isoformat(),
        'difference_seconds': (parsed - submitted).total_seconds(),
    }


def main():
    root = Path(__file__).resolve().parents[2]
    ledger = root / '.cloudshop-test-sales' / (MARKER + '.json')
    if not ledger.exists():
        raise ValueError('Тест времени ещё не отправлен.')
    record = json.loads(ledger.read_text())
    if record.get('state') != 'SENT' or not record.get('external_id') or not record.get('external_number'):
        raise ValueError('Нет подтверждённого ID/номера. Проверьте документ по метке в CloudShop; POST не повторяйте.')
    submitted = datetime.strptime(record['payload']['date'], '%Y-%m-%d %H:%M:%S').replace(tzinfo=ZoneInfo('UTC'))
    query = urlencode({
        'number': record['external_number'], 'limit': 100, 'offset': 0,
        'time_start': (submitted - timedelta(days=2)).strftime('%Y-%m-%d 00:00:00'),
        'time_end': (submitted + timedelta(days=2)).strftime('%Y-%m-%d 23:59:59'),
    })
    cli = shutil.which('postman')
    if not cli:
        raise ValueError('Postman CLI не найден.')
    env = json.loads((root / 'postman' / 'assets' / 'CloudShop_Local.postman_environment.json').read_text())
    print(f'Только GET продажи №{record["external_number"]}; состояние не меняем.', flush=True)
    token = getpass.getpass('Токен CloudShop (ввод скрыт): ').strip()
    if not token or any(c.isspace() for c in token) or token.lower().startswith('bearer'):
        raise ValueError('Введите только токен без Bearer и пробелов.')
    for entry in env['values']:
        if entry['key'] == 'access_token':
            entry['value'] = token
    with tempfile.TemporaryDirectory(prefix='cloudshop-time-verify-') as folder:
        env_path, response_path = Path(folder) / 'env.json', Path(folder) / 'response.json'
        env_path.touch(mode=0o600)
        env_path.write_text(json.dumps(env, ensure_ascii=False))
        response_path.touch(mode=0o600)
        result = subprocess.run([
            cli, 'request', 'GET', '{{base_url}}/documents/sales?' + query,
            '-e', str(env_path), '-H', 'Accept:application/json',
            '-H', 'X-CloudShop-API-Access-Token:{{access_token}}',
            '--response-only', '--redirects-ignore', '--timeout', '30000',
            '--output', str(response_path), '--no-report-events',
        ], capture_output=True, text=True, timeout=90)
        response = json.loads(response_path.read_text())['response']
        if result.returncode or response.get('code') != 200:
            raise ValueError('GET не завершился HTTP 200; автоматических повторов нет.')
        rows = json.loads(response['body'])
    if not isinstance(rows, list):
        raise ValueError('Ожидался список документов.')
    matches = [d for d in rows if isinstance(d, dict) and d.get('id') == record['external_id']]
    if len(matches) != 1:
        raise ValueError('Созданный ID не найден однозначно. POST не повторяйте.')
    document = matches[0]
    output = {
        'id': document['id'], 'number': document.get('order_number'),
        'submitted_utc': submitted.isoformat(),
        'expected_ui_almaty': submitted.astimezone(ZoneInfo('Asia/Almaty')).isoformat(),
        'document_flags': document.get('document_flags'),
        'processed_at_comparison': compare_time(document['processed_at'], submitted),
        'created_at_comparison': compare_time(document['created_at'], submitted),
    }
    print(json.dumps(output, ensure_ascii=False, indent=2))
    print('Сравните дату в UI с expected_ui_almaty. Для processed_at ожидаем difference_seconds=0.')
    print('created_at — время создания: несколько секунд разницы допустимы. Выполнено только чтение.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, TypeError, OSError, subprocess.TimeoutExpired) as error:
        raise SystemExit(str(error) if isinstance(error, ValueError) else 'Проверка остановлена: ' + type(error).__name__)
    except KeyboardInterrupt:
        raise SystemExit('\nОтменено.')
