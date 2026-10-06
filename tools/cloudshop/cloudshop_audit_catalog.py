#!/usr/bin/env python3
"""Audit catalog data through GET only; save counts and limited examples."""

import argparse
from collections import Counter
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
import getpass
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time

STORES = {
    '64feaaa51c9799143b532fcf': 'Car City',
    '698c479d4bb8342bd30e3c33': 'БАКОРДА',
    '64feaa7d401f7a28d35cda23': 'жибек жолы АВТО',
    '6967a39f62918c7e760f3747': 'СКЛАД',
}


def number(value):
    if value is None or isinstance(value, bool):
        return None
    try:
        result = Decimal(str(value))
        return result if result.is_finite() else None
    except (InvalidOperation, ValueError):
        return None


class Audit:
    def __init__(self):
        self.ids = set()
        self.skus = Counter()
        self.counts = Counter()
        self.examples = {}
        self.stores = {name: Counter() for name in STORES.values()}
        self.units = Counter()

    def issue(self, key, product):
        self.counts[key] += 1
        samples = self.examples.setdefault(key, [])
        if len(samples) < 5:
            samples.append({'id': product['id'], 'sku': product.get('sku')})

    def add_page(self, rows):
        # Validate the page before changing counters; malformed pages invalidate the traversal.
        if not isinstance(rows, list):
            raise ValueError('Ожидался массив товаров.')
        if any(not isinstance(p, dict) or not isinstance(p.get('id'), str) or not p['id'] for p in rows):
            raise ValueError('Есть запись без строкового ID товара.')
        new = 0
        for p in rows:
            self.counts['rows_received'] += 1
            if p['id'] in self.ids:
                self.counts['repeated_id_rows'] += 1
                continue
            self.ids.add(p['id'])
            new += 1
            self.counts['unique_products'] += 1
            sku = p.get('sku')
            if isinstance(sku, str) and sku.strip():
                self.skus[sku.strip()] += 1
            else:
                self.issue('missing_sku', p)
            unit = p.get('unit')
            if isinstance(unit, str) and unit.strip():
                self.units[unit.strip()] += 1
            else:
                self.issue('missing_unit', p)
            images = p.get('img')
            if isinstance(images, list) and any(isinstance(v, str) and v.strip() for v in images):
                self.counts['with_image_links'] += 1
            else:
                self.issue('without_image_links', p)
            if images is not None and not isinstance(images, list):
                self.issue('invalid_img_shape', p)
            if not p.get('category_id'):
                self.counts['without_category_id'] += 1
            if not p.get('tags'):
                self.counts['without_tags'] += 1
            stock = p.get('stock')
            prices = p.get('store_prices')
            # CloudShop emits [] for empty maps (confirmed by targeted GETs).
            # Accept only empty arrays; nonempty arrays remain schema anomalies.
            if isinstance(stock, list) and not stock:
                self.counts['empty_stock_array'] += 1
                stock = {}
            if isinstance(prices, list) and not prices:
                self.counts['empty_store_prices_array'] += 1
                prices = {}
            invalid_stock = not isinstance(stock, dict)
            invalid_prices = prices is not None and not isinstance(prices, dict)
            if invalid_stock:
                self.issue('invalid_stock_shape', p)
            if invalid_prices:
                self.issue('invalid_store_prices_shape', p)
            stock = stock if isinstance(stock, dict) else {}
            prices = prices if isinstance(prices, dict) else {}
            any_stock = False
            for store_id, name in STORES.items():
                stats = self.stores[name]
                # A missing store key means 0 by the documented stock convention.
                qty = None if invalid_stock else number(stock.get(store_id, 0))
                if qty is None:
                    stats['invalid_stock'] += 1
                elif qty > 0:
                    stats['products_with_positive_stock'] += 1
                    any_stock = True
                elif qty < 0:
                    stats['products_with_negative_stock'] += 1
                else:
                    stats['products_with_zero_stock'] += 1
                specific = store_id in prices
                price = None if invalid_prices else number(prices[store_id] if specific else p.get('price'))
                stats['store_price_used' if specific else 'catalog_price_used'] += 1
                if price is None:
                    stats['invalid_price'] += 1
                elif price == 0:
                    stats['zero_price'] += 1
                    if qty is not None and qty > 0:
                        stats['positive_stock_with_zero_price'] += 1
                elif price < 0:
                    stats['negative_price'] += 1
                else:
                    stats['positive_price'] += 1
                if qty is not None and qty > 0 and price is not None and price > 0:
                    stats['positive_stock_and_price'] += 1
                    if isinstance(unit, str) and unit.strip():
                        stats['positive_stock_price_and_unit'] += 1
            if any_stock:
                self.counts['products_available_in_selected_places'] += 1
        return new

    def summary(self):
        duplicates = {sku: n for sku, n in self.skus.items() if n > 1}
        return {
            'counts': dict(self.counts),
            'units': dict(self.units),
            'stores': {name: dict(values) for name, values in self.stores.items()},
            'duplicate_sku_groups': len(duplicates),
            'duplicate_sku_product_count': sum(duplicates.values()),
            'duplicate_sku_samples': dict(list(duplicates.items())[:5]),
            'issue_samples': self.examples,
        }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--max-pages', type=int, default=100, help='Защитный предел запросов, по умолчанию 100')
    args = parser.parse_args()
    if not 1 <= args.max_pages <= 1000:
        raise SystemExit('max-pages должен быть от 1 до 1000.')
    cli = shutil.which('postman')
    if not cli:
        raise SystemExit('Postman CLI не найден.')
    root = Path(__file__).resolve().parents[2]
    env = json.loads((root / 'postman' / 'assets' / 'CloudShop_Local.postman_environment.json').read_text())
    print('Аудит GET /product без фильтра магазина; анализ четырёх согласованных мест.')
    print('Полный экспорт не сохраняется. Фото не скачиваются, товары не меняются.', flush=True)
    token = getpass.getpass('Токен CloudShop (ввод скрыт): ').strip()
    if not token or any(c.isspace() for c in token) or token.lower().startswith('bearer'):
        raise SystemExit('Введите только токен без Bearer и пробелов.')
    for v in env['values']:
        if v['key'] == 'access_token':
            v['value'] = token
    audit = Audit()
    started = datetime.now(timezone.utc).isoformat()
    reached_empty = False
    reason = 'Достигнут предел страниц.'
    offset = 0
    pages = 0
    with tempfile.TemporaryDirectory(prefix='cloudshop-audit-') as directory:
        env_path = Path(directory) / 'environment.json'
        env_path.write_text(json.dumps(env, ensure_ascii=False))
        os.chmod(env_path, 0o600)
        response_path = Path(directory) / 'response.json'
        for page in range(args.max_pages):
            print(f'GET страница {page + 1}, offset={offset}…', flush=True)
            try:
                # request --output works locally (unlike collection run --output).
                # Its file contains headers/token: keep it private and delete with the temp directory.
                response_path.write_text('')
                os.chmod(response_path, 0o600)
                result = subprocess.run([
                    cli, 'request', 'GET', '{{base_url}}/product?limit=1000&offset=' + str(offset),
                    '-e', str(env_path), '-H', 'Accept:application/json',
                    '-H', 'X-CloudShop-API-Access-Token:{{access_token}}',
                    '--response-only', '--redirects-ignore', '--timeout', '30000', '--no-report-events',
                    '--output', str(response_path),
                ], capture_output=True, text=True, timeout=90)
                response = json.loads(response_path.read_text()).get('response', {})
                status = response.get('code')
                print('HTTP: ' + str(status), flush=True)
                if status != 200:
                    reason = f'Ожидался HTTP 200, получен {status}; обход остановлен.'
                    break
                if result.returncode:
                    reason = f'CLI завершился с кодом {result.returncode}; повторов нет.'
                    break
                rows = json.loads(response['body'])
                new = audit.add_page(rows)
            except subprocess.TimeoutExpired:
                reason = 'Таймаут чтения; повторов нет.'
                break
            except (ValueError, KeyError, TypeError, AttributeError):
                reason = 'Ответ не является ожидаемым JSON-массивом товаров.'
                break
            except OSError:
                reason = 'Ошибка запуска CLI или чтения временного ответа.'
                break
            except KeyboardInterrupt:
                reason = 'Обход отменён пользователем; сохранены только прочитанные страницы.'
                break
            pages += 1
            print(f'Получено {len(rows)}, новых ID {new}, всего уникальных {len(audit.ids)}.', flush=True)
            if not rows:
                reached_empty = True
                reason = 'Достигнута пустая страница.'
                break
            if not new:
                reason = 'Непустая страница не содержит новых ID: возможна неработающая пагинация.'
                break
            # Advance by actual size, not requested limit; an undocumented cap must not skip rows.
            offset += len(rows)
            time.sleep(1)
    report = {
        'started_at_utc': started,
        'finished_at_utc': datetime.now(timezone.utc).isoformat(),
        'reached_empty_page': reached_empty,
        'termination_reason': reason,
        'successful_pages_including_empty': pages,
        'scope': 'Все товары API, цены/остатки четырёх согласованных мест',
        'limitations': [
            'Обход offset не является атомарным снимком: каталог может меняться между запросами.',
            'Повторяющиеся ID учитываются один раз, по первому полученному состоянию.',
            'Ключ остатка, отсутствующий в корректном stock, считается нулём по документации.',
            'Нулевая цена места не заменяется ценой каталога.',
            'Положительные цена/остаток и непустой unit не подтверждают публичность и возможность выдачи.',
            'Наличие ссылки на фото не доказывает доступность изображения.',
        ],
        **audit.summary(),
    }
    folder = root / '.cloudshop-audit'
    folder.mkdir(mode=0o700, exist_ok=True)
    path = folder / ('catalog-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ') + '.json')
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2))
    os.chmod(path, 0o600)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    print('Сводка сохранена: ' + str(path))
    if not reached_empty:
        print('АУДИТ НЕПОЛНЫЙ. Итоги относятся только к прочитанным страницам.')
    return 0 if reached_empty else 1


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise SystemExit('\nАудит отменён; полный результат не заявляется.')
