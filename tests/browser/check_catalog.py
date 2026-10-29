#!/usr/bin/env python3
"""Partner sign-in, home page, catalog, product page and sign-out on synthetic fixtures. Read-only."""
import json
import re

from playwright.sync_api import expect, sync_playwright

import fixtures
from common import BASE, PASSWORD, api, no_overflow, passed, watch_errors

ONE, NO_PRICE, NO_STOCK = (fixtures.product_id(i) for i in (1, 2, 3))

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    context = browser.new_context(viewport={'width': 1440, 'height': 1000})
    page = context.new_page()
    errors = watch_errors(page)

    page.goto(BASE + '/search?q=MBM-TEST-005')
    expect(page).to_have_url(re.compile(r'/login\?from=%2Fsearch%3Fq%3DMBM-TEST-005$'))
    anonymous = context.request.get(BASE + '/api/catalog/products?q=&available=false&page=0')
    assert anonymous.status == 401, anonymous.status
    page.get_by_label('Логин', exact=True).fill(fixtures.ALFA)
    page.get_by_label('Пароль', exact=True).fill('wrong-synthetic-password')
    page.get_by_role('button', name='Войти', exact=True).click()
    expect(page.get_by_role('alert')).to_have_text('Неверный логин или пароль.')
    expect(page.get_by_role('alert')).to_be_focused()
    page.get_by_label('Логин', exact=True).fill(fixtures.BLOCKED)
    page.get_by_label('Пароль', exact=True).fill(PASSWORD)
    page.get_by_role('button', name='Войти', exact=True).click()
    expect(page.get_by_role('alert')).to_have_text('Неверный логин или пароль.')
    page.get_by_label('Логин', exact=True).fill(fixtures.ALFA)
    page.get_by_role('button', name='Войти', exact=True).click()
    expect(page).to_have_url(BASE + '/search?q=MBM-TEST-005')
    expect(page.get_by_role('link', name=f'Кабинет: {fixtures.ALFA_COMPANY}')).to_be_visible()
    expect(page.locator('#results-title')).to_contain_text('Найдено: 1')
    passed('Без входа каталог закрыт; неверный пароль и заблокированный партнёр отклонены; после входа — возврат на исходный адрес')

    page.goto(BASE + '/')
    expect(page.get_by_role('heading', name='Нужная деталь со склада в Алматы')).to_be_visible()
    expect(page.locator('.make-button')).to_have_count(2)
    expect(page.locator('.category-tile')).to_have_count(2)
    expect(page.locator('.product-tile')).to_have_count(8)
    page.get_by_label('Артикул, код или название').fill('mbmtest007')
    page.get_by_role('search', name='По артикулу').get_by_role('button', name='Найти').click()
    expect(page).to_have_url(BASE + '/search?q=mbmtest007')
    expect(page.locator('.product-row')).to_have_count(1)
    expect(page.locator('.product-row').first).to_contain_text(fixtures.sku(7))
    page.reload()
    expect(page.locator('#header-query')).to_have_value('mbmtest007')
    passed('Главная: марки, категории и товары склада; поиск без дефисов находит артикул и переживает перезагрузку')

    page.goto(BASE + '/search')
    expect(page.locator('#results-title')).to_have_text(f'Найдено: {fixtures.PRODUCTS}')
    expect(page.locator('.product-row')).to_have_count(24)
    expect(page.get_by_text('Страница 1 из 2')).to_be_visible()
    listing = api(context, f'/api/catalog/products?q=&available=false&page=0&store={fixtures.SKLAD}')
    first = next(item for item in listing['products'] if item['id'] == ONE)
    row = page.locator('.product-row').filter(has_text=fixtures.sku(1))
    expect(row.locator('.price')).to_contain_text(first['priceText'])
    # Car City sells every fixture for 99 999 ₸; that price must never appear.
    expect(page.get_by_text(re.compile('99[\\s\xa0]?999'))).to_have_count(0)
    blocked = page.locator('.product-row').filter(has_text=fixtures.sku(2))
    expect(blocked.locator('.price')).to_have_text('Цена уточняется')
    expect(blocked.get_by_role('link', name=re.compile('цена уточняется$'))).to_be_visible()
    expect(page.locator('.product-row').filter(has_text=fixtures.sku(3)).get_by_role('link', name=re.compile('нет на складе$'))).to_be_visible()
    expect(page.get_by_role('button', name=f'В корзину: {fixtures.name(1)}')).to_be_enabled()
    page.get_by_role('link', name='Далее', exact=True).click()
    expect(page).to_have_url(re.compile(r'page=1'))
    expect(page.locator('.product-row')).to_have_count(fixtures.PRODUCTS - 24)
    expect(page.locator('#results-title')).to_be_focused()
    page.go_back()
    expect(page.locator('.product-row')).to_have_count(24)
    passed('Каталог: только цены СКЛАДА, «Цена уточняется» и «нет на складе» не добавляются, пагинация 24 + 6 с фокусом и Back')

    page.get_by_role('switch', name='Только в наличии').click()
    expect(page).to_have_url(re.compile('available=true'))
    in_stock = api(context, f'/api/catalog/products?q=&available=true&page=0&store={fixtures.SKLAD}')['totalProducts']
    assert in_stock < fixtures.PRODUCTS
    expect(page.locator('#results-title')).to_have_text(f'Найдено: {in_stock}')
    expect(page.locator('.product-row').filter(has_text=fixtures.sku(3))).to_have_count(0)
    page.get_by_role('button', name='Тормозная система', exact=True).click()
    expect(page.get_by_role('heading', level=1)).to_have_text('Тормозная система')
    expect(page.get_by_role('navigation', name='Подразделы: Тормозная система')).to_be_visible()
    page.get_by_label('МАРКА АВТО').select_option(json.dumps(['Hyundai', None], separators=(',', ':')))
    expect(page.get_by_label('Выбранный автомобиль')).to_contain_text('Hyundai')
    for text in page.locator('.product-row h3').all_inner_texts():
        assert text.startswith('Колодки'), text
    page.get_by_role('button', name='Сбросить автомобиль').click()
    expect(page.get_by_label('Выбранный автомобиль')).to_have_count(0)
    page.reload()
    expect(page.get_by_role('switch', name='Только в наличии')).to_have_attribute('aria-checked', 'true')
    expect(page.get_by_role('heading', level=1)).to_have_text('Тормозная система')
    page.get_by_role('button', name='Сбросить фильтры').click()
    expect(page.locator('#results-title')).to_have_text(f'Найдено: {fixtures.PRODUCTS}')
    passed('Наличие, раздел, подраздел и марка фильтруют выдачу, сохраняются в адресе и сбрасываются')

    page.goto(BASE + '/search?q=zzzz-mbm-impossible')
    expect(page.get_by_role('heading', name='Товары не найдены')).to_be_visible()
    page.get_by_role('button', name='Показать весь каталог').click()
    expect(page.locator('.product-row')).to_have_count(24)
    page.goto(BASE + '/search?page=1.5')
    expect(page.get_by_role('heading', name='Проверьте параметры поиска')).to_be_visible()
    # Unknown URLs get the static error page from the server, never the SPA.
    unknown = page.goto(BASE + '/no-such-page')
    assert unknown.status in (403, 404), unknown.status
    expect(page.locator('#main')).to_have_count(0)
    page.route('**/api/catalog/products?**', lambda route: route.fulfill(
        status=503, content_type='application/problem+json',
        body=json.dumps({'detail': 'Каталог временно недоступен. Попробуйте ещё раз.'})))
    page.goto(BASE + '/search')
    expect(page.get_by_role('alert')).to_contain_text('временно недоступен')
    page.unroute('**/api/catalog/products?**')
    page.get_by_role('button', name='Повторить', exact=True).click()
    expect(page.locator('.product-row')).to_have_count(24)

    def stale(route):
        response = route.fetch()
        body = response.json()
        body['stale'] = True
        route.fulfill(response=response, json=body)
    page.route('**/api/catalog/products?**', stale)
    page.reload()
    expect(page.get_by_text('Данные могут быть устаревшими', exact=False)).to_be_visible()
    page.unroute('**/api/catalog/products?**')
    passed('Пустая выдача, неверные параметры, неизвестная страница, имитация HTTP 503 с повтором и устаревшего каталога')

    page.goto(BASE + f'/products/{ONE}')
    expect(page.locator('#product-title')).to_have_text(fixtures.name(1))
    expect(page.locator('.product-facts')).to_contain_text(fixtures.sku(1))
    expect(page.locator('.buy-price')).to_have_text(first['priceText'])
    expect(page.get_by_role('button', name='В корзину', exact=True)).to_be_enabled()
    expect(page.get_by_role('heading', name='Применимость')).to_be_visible()
    expect(page.get_by_role('heading', name=re.compile('^Ещё в категории'))).to_be_visible()
    page.goto(BASE + f'/products/{NO_PRICE}')
    expect(page.locator('.buy-blocked')).to_contain_text('Цена этой позиции уточняется')
    expect(page.get_by_role('button', name='В корзину', exact=True)).to_have_count(0)
    page.goto(BASE + f'/products/{NO_STOCK}')
    expect(page.locator('.buy-blocked')).to_be_visible()
    page.goto(BASE + '/products/not-an-id')
    expect(page.get_by_role('alert')).to_be_visible()
    passed('Карточка товара: цена СКЛАДА, применимость, похожие товары; без цены и без остатка — объяснение вместо кнопки')

    page.goto(BASE + '/search')
    expect(page.locator('.product-row')).to_have_count(24)
    no_overflow(page, 'catalog')
    page.set_viewport_size({'width': 375, 'height': 900})
    toggle = page.get_by_role('button', name='Фильтры', exact=True)
    toggle.click()
    expect(page.get_by_role('button', name='Скрыть фильтры')).to_have_attribute('aria-expanded', 'true')
    expect(page.get_by_role('switch', name='Только в наличии')).to_be_visible()
    page.set_viewport_size({'width': 1440, 'height': 1000})
    page.goto(BASE + f'/products/{ONE}')
    expect(page.locator('#product-title')).to_be_visible()
    no_overflow(page, 'product')
    page.goto(BASE + '/')
    expect(page.locator('.product-tile')).to_have_count(8)
    no_overflow(page, 'home')
    page.keyboard.press('Tab')
    expect(page.get_by_role('link', name='Перейти к содержимому')).to_be_focused()
    passed('360/375/768/1440 px без горизонтальной прокрутки; мобильная панель фильтров; ссылка к содержимому')

    page.goto(BASE + '/account')
    page.get_by_role('button', name='Выйти', exact=True).click()
    expect(page.get_by_role('heading', name='Вход для оптовых клиентов')).to_be_visible()
    page.goto(BASE + '/search')
    expect(page).to_have_url(re.compile(r'/login\?from='))
    assert context.request.get(BASE + '/api/cart').status == 401
    assert not errors, errors
    passed('Выход закрывает каталог и API; ошибок JavaScript нет')
    browser.close()
