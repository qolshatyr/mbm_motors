#!/usr/bin/env python3
"""Client prototype (demo build, HashRouter, data in the tab). Run only against the static prototype:

  python3 scripts/build_prototype.py
  python3 -m http.server 5185 --bind 127.0.0.1 --directory prototype/dist
  MBM_PROTOTYPE_URL=http://127.0.0.1:5185 python tests/browser/check_client_prototype.py
"""
import os
import re
import tempfile
from pathlib import Path
from urllib.parse import urlparse

from playwright.sync_api import expect, sync_playwright

base = os.environ.get('MBM_PROTOTYPE_URL', 'http://127.0.0.1:5185').rstrip('/')
assert urlparse(base).hostname in ('127.0.0.1', 'localhost'), 'Use the isolated prototype only'
out = Path(os.environ.get('MBM_PROTOTYPE_SCREENSHOTS', Path(tempfile.gettempdir()) / 'mbm-prototype-shots'))
out.mkdir(parents=True, exist_ok=True)
PRODUCTS = 14

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    for width in (375, 768, 1440):
        page = browser.new_page(viewport={'width': width, 'height': 960})
        errors, api = [], []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.on('request', lambda request: api.append(request.url) if '/api/' in request.url else None)

        def capture(name):
            expect(page.locator('h1').first).to_be_visible()
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), (name, width)
            page.screenshot(path=str(out / f'{name}-{width}.png'), full_page=True)

        page.goto(base + '/#/search')
        expect(page.get_by_text('Прототип MBM AutoMotors')).to_be_visible()
        expect(page.get_by_role('heading', name='Вход для оптовых клиентов')).to_be_visible()
        capture('login')
        page.get_by_label('Логин', exact=True).fill('demo')
        page.get_by_label('Пароль', exact=True).fill('demo')
        page.get_by_role('button', name='Войти', exact=True).click()
        expect(page.locator('#results-title')).to_have_text(f'Найдено: {PRODUCTS}')
        assert page.url.endswith('/#/search'), page.url
        capture('catalog')
        page.get_by_role('button', name='Тормозная система', exact=True).click()
        expect(page.locator('.product-row')).to_have_count(2)
        page.goto(base + '/#/search?q=2630035505')
        expect(page.locator('.product-row')).to_have_count(1)
        expect(page.locator('.product-row')).to_contain_text('26300-35505')
        page.goto(base + '/#/search?q=NOT-EXIST-DEMO')
        expect(page.get_by_role('heading', name='Товары не найдены')).to_be_visible()
        page.get_by_role('button', name='Показать весь каталог').click()
        expect(page.locator('.product-row')).to_have_count(PRODUCTS)
        expect(page.locator('.product-row').filter(has_text='95670-D3000').locator('.price')).to_have_text('Цена уточняется')

        page.goto(base + '/#/products/000000000000000000000005')
        expect(page.locator('#product-title')).to_have_text('Фильтр масляный')
        capture('product')
        page.get_by_role('button', name='В корзину', exact=True).click()
        expect(page.locator('.header-cart-count')).to_have_text('1')
        page.goto(base + '/#/basket')
        expect(page.locator('.cart-line')).to_have_count(1)
        capture('basket')
        page.get_by_label('Согласен на обработку', exact=False).check()
        page.locator('#comment').fill('Проверка прототипа')
        page.get_by_role('button', name='Отправить заявку').click()
        expect(page.get_by_role('heading', name='Демонстрация оформления завершена')).to_be_visible()
        expect(page.locator('.header-cart-count')).to_have_text('0')
        capture('receipt')
        saved = page.evaluate('JSON.stringify(sessionStorage) + JSON.stringify(localStorage)')
        assert '+77000000000' not in saved and 'Проверка прототипа' not in saved and 'Ерлан' not in saved
        page.reload()
        expect(page.get_by_role('heading', name='Демонстрация оформления завершена')).to_be_visible()

        page.goto(base + '/#/account')
        expect(page.locator('.order-row')).to_have_count(1)
        expect(page.locator('.order-row')).to_contain_text(re.compile(r'DEMO-\d+'))
        capture('account')
        page.goto(base + '/#/quick-order')
        page.get_by_label('Список артикулов').fill('54651-2T000 2\nNOPE-1')
        expect(page.get_by_role('status').filter(has_text='Готово к добавлению')).to_have_text('Готово к добавлению: 1 из 2')
        capture('quick-order')
        page.goto(base + '/#/')
        expect(page.locator('.category-tile').first).to_be_visible()
        capture('home')
        assert not errors, errors
        assert not api, api
        page.close()
        print(f'PASS {width}px: login, catalog, search, empty, product, basket, checkout, receipt, account, quick order; no overflow, JS errors or API requests')
    browser.close()
