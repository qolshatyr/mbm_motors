#!/usr/bin/env python3
"""React product/cart integration against an imported, recently synced catalog.

Requires Playwright Chromium. Uses only its own sessions; no orders, reservations,
CloudShop writes or database edits. MBM_FRONTEND_URL can point at a test instance.
"""
import os
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('MBM_FRONTEND_URL', 'http://127.0.0.1:5173')
STORE = '64feaaa51c9799143b532fcf'

def passed(message):
    print('PASS', message, flush=True)

with sync_playwright() as playwright:
    browser = playwright.chromium.launch()
    context = browser.new_context(viewport={'width': 1440, 'height': 1000})
    page = context.new_page()
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    candidate = None
    for number in range(20):
        response = context.request.get(f'{BASE}/api/catalog/products?store={STORE}&available=true&page={number}')
        assert response.ok
        catalog = response.json()
        assert not catalog['stale'], 'A genuine successful sync is required before this test'
        for product in catalog['products']:
            if product['unit'] not in ('шт', 'шт.'):
                continue
            detail = context.request.get(f"{BASE}/api/catalog/products/{product['id']}?store={STORE}").json()
            offer = next(offer for offer in detail['offers'] if offer['storeId'] == STORE)
            if offer['canAdd']:
                candidate = product
                break
        if candidate:
            break
    assert candidate, 'No eligible piece-based product found'
    product_id = candidate['id']
    page.goto(f'{BASE}/products/{product_id}?store={STORE}')
    expect(page.locator('#product-title')).to_have_text(candidate['name'])
    page.reload()
    expect(page.locator('#product-title')).to_be_visible()
    page.get_by_role('button', name='Добавить из Car City в корзину', exact=True).click()
    expect(page.get_by_role('status').filter(has_text='Товар добавлен')).to_be_visible()
    expect(page.locator('.cart-count')).to_have_text('1')
    page.get_by_role('link', name='Открыть корзину', exact=True).click()
    expect(page.locator('.cart-line')).to_have_count(1)
    snapshot = context.request.get(BASE+'/api/cart').json()
    assert snapshot['itemCount'] == 1 and snapshot['cart']['items'][0]['id'] == product_id
    passed('Карточка, deep link, добавление и счётчик используют настоящий сервер')
    quantity = page.get_by_label('Количество', exact=True)
    quantity.fill('1.5')
    page.get_by_role('button', name='Обновить', exact=True).click()
    assert context.request.get(BASE+'/api/cart').json()['itemCount'] == 1
    quantity.fill('1')
    csrf = context.request.get(BASE+'/api/csrf').json()
    headers = {csrf['headerName']: csrf['token']}
    invalid = context.request.post(BASE+'/api/cart/update', headers=headers, form={'productId':product_id,'quantity':'0'})
    assert invalid.status == 409
    assert context.request.post(BASE+'/api/cart/clear', form={}).status == 403
    assert context.request.get(BASE+'/api/cart').json()['itemCount'] == 1
    other = browser.new_context()
    assert other.request.get(BASE+'/api/cart').json()['itemCount'] == 0
    other.close()
    passed('Дробное количество, серверная валидация, CSRF и независимые сессии')
    page.set_viewport_size({'width':375,'height':812})
    assert not page.evaluate('document.documentElement.scrollWidth > innerWidth')
    page.screenshot(path='/tmp/mbm-react-basket-live.png', full_page=True)
    clear = page.get_by_role('button', name='Очистить корзину', exact=True)
    clear.click()
    expect(page.get_by_role('dialog')).to_be_visible()
    expect(page.get_by_role('button', name='Отмена', exact=True)).to_be_focused()
    page.keyboard.press('Escape')
    expect(page.get_by_role('dialog')).not_to_be_visible()
    expect(clear).to_be_focused()
    expect(page.locator('.cart-line')).to_have_count(1)
    passed('Мобильная корзина, отмена очистки и возврат фокуса')
    remove = page.get_by_role('button', name=f"Удалить {candidate['name']} из корзины", exact=True)
    remove.click()
    page.get_by_role('dialog').get_by_role('button', name='Удалить', exact=True).click()
    expect(page.get_by_role('heading', name='Корзина пока пуста', exact=True)).to_be_visible()
    expect(page.locator('#basket-title')).to_be_focused()
    assert context.request.get(BASE+'/api/cart').json()['itemCount'] == 0
    passed('Удаление последней позиции и восстановление фокуса')
    page.goto(f'{BASE}/products/{product_id}?store={STORE}')
    page.get_by_role('button', name='Добавить из Car City в корзину', exact=True).click()
    expect(page.locator('.cart-count')).to_have_text('1')
    page.get_by_role('link', name='Открыть корзину', exact=True).click()
    page.get_by_role('button', name='Очистить корзину', exact=True).click()
    page.get_by_role('dialog').get_by_role('button', name='Очистить', exact=True).click()
    expect(page.get_by_role('heading', name='Корзина пока пуста', exact=True)).to_be_visible()
    expect(page.locator('#basket-title')).to_be_focused()
    assert context.request.get(BASE+'/api/cart').json()['itemCount'] == 0
    passed('Очистка подтверждается отдельно и меняет только собственную сессию')
    page.goto(BASE+'/products/not-an-id')
    expect(page.get_by_role('alert')).to_be_visible()
    assert not errors, errors
    passed('Ошибка неизвестного товара и отсутствие ошибок JavaScript')
    browser.close()
