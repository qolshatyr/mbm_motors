"""Client demo QA. Run only against the isolated static prototype."""
import os
import re
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

base = os.environ.get('MBM_PROTOTYPE_URL', 'http://127.0.0.1:5185').rstrip('/')
assert urlparse(base).hostname in ('127.0.0.1', 'localhost'), 'Use the isolated prototype only'
out = Path(os.environ.get('MBM_PROTOTYPE_SCREENSHOTS', '/tmp/mbm-prototype-shots'))
out.mkdir(parents=True, exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    for width in (375, 768, 1440):
        page = browser.new_page(viewport={'width': width, 'height': 960})
        errors, api = [], []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.on('request', lambda request: api.append(request.url) if '/api/' in request.url else None)
        def capture(name):
            expect(page.locator('h1').first).to_be_visible()
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), name
            page.screenshot(path=str(out / f'{name}-{width}.png'), full_page=True)
        page.goto(base + '/#/')
        expect(page.locator('.product-card')).to_have_count(12)
        route_before = page.url
        page.keyboard.press('Tab')
        page.get_by_role('link', name='Перейти к содержимому').click()
        expect(page.locator('#main')).to_be_focused()
        assert page.url == route_before, 'Skip link must preserve HashRouter route'
        capture('catalog')
        for category in ('filter', 'brake', 'suspension', 'light', 'oil', 'body'):
            page.goto(base + '/#/?category=' + category)
            expect(page.locator('.product-card')).to_have_count(4)
        page.goto(base + '/#/?category=filter')
        expect(page.locator('.product-card')).to_have_count(4)
        page.locator('#query').fill('NOT-EXIST-DEMO')
        page.get_by_role('button', name='Найти', exact=True).click()
        expect(page.get_by_text('Товары не найдены', exact=True)).to_be_visible()
        page.get_by_role('button', name='Показать весь каталог').click()
        expect(page.locator('.product-card')).to_have_count(12)
        if width == 375:
            page.get_by_role('button', name='Фильтры', exact=True).click()
            expect(page.get_by_role('dialog')).to_be_visible()
            page.locator('#store').select_option('dddddddddddddddddddd0002')
            expect(page.locator('#store')).to_have_value('dddddddddddddddddddd0002')
            page.keyboard.press('Escape')
            expect(page.get_by_role('dialog')).not_to_be_visible()
        page.goto(base + '/#/products/000000000000000000000001')
        expect(page.locator('#product-title')).to_have_text('Фильтр масляный')
        capture('product')
        page.get_by_role('button', name=re.compile('Добавить из')).first.click()
        expect(page.locator('.cart-count')).to_have_text('1')
        page.get_by_role('link', name='Корзина', exact=False).first.click()
        expect(page.get_by_role('heading', name='Корзина', exact=True)).to_be_visible()
        capture('basket')
        page.get_by_role('link', name='Оформить заявку', exact=True).click()
        expect(page.locator('#customerName')).to_be_visible()
        capture('checkout')
        page.locator('#customerName').fill('Пример клиента')
        page.locator('#phone').fill('+77000000000')
        page.locator('#comment').fill('Проверка прототипа')
        page.locator('input[type=checkbox]').check()
        page.get_by_role('button', name='Сохранить заявку').click()
        expect(page.get_by_text('Демонстрация оформления завершена', exact=True)).to_be_visible()
        expect(page.locator('.cart-count')).to_have_text('0')
        capture('receipt')
        saved = page.evaluate('JSON.stringify(sessionStorage)')
        assert 'Пример клиента' not in saved and '+77000000000' not in saved and 'Проверка прототипа' not in saved
        page.reload()
        expect(page.get_by_text('Демонстрация оформления завершена', exact=True)).to_be_visible()
        for route in ('pickup', 'about', 'how-to-order'):
            page.goto(base + '/#/' + route)
            capture(route)
        assert not errors, errors
        assert not api, api
        page.close()
        print(f'PASS {width}px: navigation/search/empty/filter/cart/checkout/receipt/reload/info; no overflow, JS or API requests')
    browser.close()
