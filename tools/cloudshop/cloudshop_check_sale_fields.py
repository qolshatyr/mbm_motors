#!/usr/bin/env python3
"""Read the test customer's ID and product unit without showing customer contacts."""

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
        if v['key'] == 'product_id':
            v['value'] = '6a6e12abb0b00726916dde8b'
    headers = [
        {'key': 'Accept', 'value': 'application/json'},
        {'key': 'X-CloudShop-API-Access-Token', 'value': '{{access_token}}'},
    ]

    def run(url, name, script, directory, env_path):
        col = {
            'info': {'name': 'CloudShop — поля тестовой продажи', 'schema': 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},
            'item': [{
                'name': name,
                'request': {'method': 'GET', 'url': url, 'header': headers, 'auth': {'type': 'noauth'}},
                'event': [{'listen': 'test', 'script': {'type': 'text/javascript', 'exec': [
                    'pm.test("HTTP 200", () => pm.response.to.have.status(200));',
                    'if (pm.response.code === 200) { ' + script + ' }',
                ]}}],
            }],
        }
        path = Path(directory) / 'collection.json'
        path.write_text(json.dumps(col, ensure_ascii=False))
        result = subprocess.run([cli, 'collection', 'run', str(path), '-e', str(env_path), '--timeout-request', '30000', '--no-report-events'], capture_output=True, text=True)
        print(result.stdout, flush=True)
        if result.returncode:
            raise SystemExit('Ошибка чтения. Продажа не отправлялась.')
        return result.stdout

    with tempfile.TemporaryDirectory(prefix='cloudshop-fields-') as directory:
        env_path = Path(directory) / 'environment.json'
        env_path.write_text(json.dumps(env, ensure_ascii=False))
        os.chmod(env_path, 0o600)
        for page in range(20):
            output = run(
                '{{base_url}}/customers?limit=1000&offset=' + str(page * 1000),
                'Только ID тестового покупателя',
                'const rows=pm.response.json(); if (!Array.isArray(rows)) throw new Error("Ожидался массив покупателей"); '
                'console.log("PAGE_COUNT:"+rows.length); '
                'for (const c of rows) { if (String(c.name || "").trim().toLowerCase() === "тестовый покупатель") { '
                'console.log("TEST_CUSTOMER_ID:"+c.id); console.log("Имя: "+c.name); } }',
                directory, env_path,
            )
            count = re.search(r'PAGE_COUNT:(\d+)', output)
            if not count:
                raise SystemExit('Не удалось прочитать размер страницы; остановлено.')
            if 'TEST_CUSTOMER_ID:' in output:
                break
            if int(count.group(1)) < 1000:
                raise SystemExit('Покупатель с точным именем «тестовый покупатель» не найден.')
            time.sleep(1)
        else:
            raise SystemExit('Достигнут лимит 20 страниц покупателей.')
        source = json.loads((root / 'postman' / 'assets' / 'CloudShop_ReadOnly.postman_collection.json').read_text())
        product = next(i for i in source['item'] if i['name'] == '04 — Один товар')
        if product['request']['method'] != 'GET':
            raise SystemExit('Карточка товара должна читаться через GET.')
        run(product['request']['url'], 'Единица тестового товара',
            'const p=pm.response.json(); console.log("PRODUCT_ID:"+p.id); '
            'console.log("UNIT:"+JSON.stringify(p.unit)); '
            'console.log("CAR_CITY_STOCK:"+JSON.stringify((p.stock || {})["64feaaa51c9799143b532fcf"]));',
            directory, env_path)
    print('Только чтение. Контакты покупателей не выводились. Продажа не отправлялась.')


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        raise SystemExit('\nОтменено.')
