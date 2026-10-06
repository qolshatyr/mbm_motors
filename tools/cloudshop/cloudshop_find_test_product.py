#!/usr/bin/env python3
"""Find the selected test product using only the saved GET request."""

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
    collection = json.loads((root / 'postman' / 'assets' / 'CloudShop_ReadOnly.postman_collection.json').read_text())
    environment = json.loads((root / 'postman' / 'assets' / 'CloudShop_Local.postman_environment.json').read_text())
    token = getpass.getpass('Токен CloudShop (ввод скрыт): ').strip()
    if not token or any(c.isspace() for c in token):
        raise SystemExit('Введите непустой токен без пробелов и переносов строк.')
    for value in environment['values']:
        if value['key'] == 'access_token':
            value['value'] = token
        elif value['key'] == 'store_id':
            value['value'] = '64feaaa51c9799143b532fcf'
    item = next(i for i in collection['item'] if i['name'] == '03 — Первая страница товаров')
    if item['request']['method'] != 'GET':
        raise SystemExit('Ожидался GET-запрос.')
    collection['item'] = [item]
    item['request']['auth'] = {'type': 'noauth'}
    item['request']['header'] = [
        {'key': 'Accept', 'value': 'application/json'},
        {'key': 'X-CloudShop-API-Access-Token', 'value': '{{access_token}}'},
    ]
    item.setdefault('event', []).append({
        'listen': 'test',
        'script': {'type': 'text/javascript', 'exec': [
            'if (pm.response.code === 200) {',
            '  const rows = pm.response.json();',
            '  if (!Array.isArray(rows)) throw new Error("Ожидался массив товаров");',
            '  console.log("PAGE_COUNT:" + rows.length);',
            '  for (const p of rows) {',
            '    if (String(p.sku).trim() === "54610-2T000") {',
            '      console.log("MATCH_FOUND");',
            '      console.log("ID: " + p.id);',
            '      console.log("Название: " + (p.options || {}).name);',
            '      console.log("Артикул: " + p.sku);',
            '      console.log("Код: " + p.code);',
            '      console.log("Единица: " + JSON.stringify(p.unit));',
            '      console.log("Цена каталога: " + p.price);',
            '      console.log("Цены магазинов: " + JSON.stringify(p.store_prices));',
            '      console.log("Остатки: " + JSON.stringify(p.stock));',
            '    }',
            '  }',
            '}',
        ]},
    })
    with tempfile.TemporaryDirectory(prefix='cloudshop-product-') as directory:
        env_path = Path(directory) / 'environment.json'
        col_path = Path(directory) / 'collection.json'
        env_path.write_text(json.dumps(environment, ensure_ascii=False))
        os.chmod(env_path, 0o600)
        for page in range(50):
            item['request']['url'] = (
                '{{base_url}}/product?limit=1000&offset=' + str(page * 1000)
                + '&store_id={{store_id}}'
            )
            col_path.write_text(json.dumps(collection, ensure_ascii=False))
            os.chmod(col_path, 0o600)
            print(f'Читаю страницу {page + 1} для Car City…', flush=True)
            result = subprocess.run([
                cli, 'collection', 'run', str(col_path), '-e', str(env_path),
                '--timeout-request', '30000', '--no-report-events',
            ], capture_output=True, text=True)
            # Never print stderr/debug output: it is unnecessary for product discovery.
            print(result.stdout, flush=True)
            if result.returncode:
                raise SystemExit('Чтение завершилось ошибкой. Автоматического повтора нет.')
            count = re.search(r'PAGE_COUNT:(\d+)', result.stdout)
            if not count:
                raise SystemExit('Не удалось определить размер страницы. Поиск остановлен.')
            if 'MATCH_FOUND' in result.stdout:
                print('Совпадения по артикулу найдены. Продажу скрипт НЕ создаёт.')
                return
            if int(count.group(1)) < 1000:
                raise SystemExit('Артикул не найден в полученном каталоге Car City.')
            time.sleep(1)
        raise SystemExit('Достигнут лимит 50 страниц. Поиск остановлен.')


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        raise SystemExit('\nПоиск отменён.')
