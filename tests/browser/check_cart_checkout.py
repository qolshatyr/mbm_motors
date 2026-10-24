#!/usr/bin/env python3
"""Cart, quick order, checkout, receipt and «Мои заявки» on synthetic fixtures.

Creates local orders in mbm_test only: the test server runs without delivery to CloudShop.
"""
import re

from playwright.sync_api import expect, sync_playwright

import fixtures
from common import BASE, DSN, api, csrf_headers, no_overflow, passed, sign_in, watch_errors

ONE, TWO_LEFT = fixtures.product_id(1), fixtures.product_id(4)
SUCCESS = re.compile(r'/checkout/success\?key=')


def cart_count(context):
    return api(context, '/api/cart')['itemCount']


def open_checkout(page):
    page.goto(BASE + '/checkout')
    expect(page.get_by_role('button', name='Отправить заявку')).to_be_visible()


def add_one(page, number=1):
    page.goto(BASE + f'/products/{fixtures.product_id(number)}')
    page.get_by_role('button', name='В корзину', exact=True).click()
    expect(page.get_by_text(f'Добавлено: {fixtures.name(number)}', exact=False)).to_be_visible()


def consent(page):
    page.get_by_label('Согласен на обработку', exact=False).check()


with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    context = browser.new_context(viewport={'width': 1440, 'height': 1000})
    page = context.new_page()
    errors = watch_errors(page)
    sign_in(page)

    page.goto(BASE + '/search')
    page.get_by_role('button', name=f'В корзину: {fixtures.name(1)}').click()
    stepper = page.get_by_role('group', name=f'Количество «{fixtures.name(1)}» в корзине')
    expect(stepper.get_by_label('Количество', exact=True)).to_have_value('1')
    stepper.get_by_role('button', name='Увеличить количество').click()
    expect(stepper.get_by_label('Количество', exact=True)).to_have_value('2')
    expect(page.locator('.header-cart-count')).to_have_text('2')
    page.goto(BASE + f'/products/{TWO_LEFT}')
    buy = page.get_by_role('group', name='Количество для добавления')
    buy.get_by_role('button', name='Увеличить количество').click()
    expect(buy.get_by_role('button', name='Увеличить количество')).to_be_disabled()
    page.get_by_role('button', name='В корзину', exact=True).click()
    expect(page.get_by_text('В корзине: 2 шт.', exact=False)).to_be_visible()
    assert cart_count(context) == 4
    passed('В корзину из списка и карточки; степпер; количество ограничено остатком СКЛАДА (2 шт.)')

    headers = csrf_headers(context)
    assert context.request.post(BASE + '/api/cart/update', headers=headers,
                                form={'productId': ONE, 'quantity': '0'}).status == 409
    assert context.request.post(BASE + '/api/cart/add', headers=headers, form={
        'productId': fixtures.product_id(2), 'storeId': fixtures.SKLAD, 'quantity': '1'}).status == 409
    assert context.request.post(BASE + '/api/cart/clear', form={}).status == 403
    other = browser.new_context()
    other_page = other.new_page()
    sign_in(other_page, fixtures.BETA)
    assert cart_count(other) == 0
    assert cart_count(context) == 4
    passed('Сервер отклоняет количество 0 и товар без цены, POST без CSRF; корзины партнёров независимы')

    page.goto(BASE + '/basket')
    expect(page.locator('.cart-line')).to_have_count(2)
    remove = page.get_by_role('button', name=f'Удалить {fixtures.name(4)} из корзины')
    remove.click()
    expect(page.get_by_role('dialog')).to_be_visible()
    expect(page.get_by_role('dialog').get_by_role('button', name='Отмена')).to_be_focused()
    page.keyboard.press('Escape')
    expect(page.get_by_role('dialog')).not_to_be_visible()
    expect(remove).to_be_focused()
    remove.click()
    page.get_by_role('dialog').get_by_role('button', name='Удалить', exact=True).click()
    expect(page.locator('.cart-line')).to_have_count(1)
    line = page.get_by_role('group', name=f'Количество «{fixtures.name(1)}»')
    line.get_by_label('Количество', exact=True).fill('3')
    line.get_by_label('Количество', exact=True).press('Enter')
    expect(page.locator('#basket-title')).to_contain_text('3 шт.')
    no_overflow(page, 'basket')
    passed('Корзина: удаление с подтверждением, Escape и возврат фокуса, ввод количества; нет горизонтальной прокрутки')

    expect(page.get_by_text(f'Данные заполнены из кабинета {fixtures.ALFA_COMPANY}')).to_be_visible()
    expect(page.locator('#customerName')).to_have_value(fixtures.ALFA_CONTACT)
    expect(page.locator('#phone')).to_have_value(fixtures.ALFA_PHONE)
    page.locator('#phone').fill('letters')
    consent(page)
    page.get_by_role('button', name='Отправить заявку').click()
    expect(page.locator('#phone-error')).to_be_visible()
    expect(page.locator('#phone')).to_be_focused()
    expect(page.locator('#customerName')).to_have_value(fixtures.ALFA_CONTACT)
    page.locator('#phone').fill(fixtures.ALFA_PHONE)
    page.locator('#comment').fill('Синтетическая проверка оформления')
    posts = []
    page.on('request', lambda request: posts.append(request) if request.method == 'POST' and request.url.endswith('/api/orders') else None)
    page.locator('.checkout-form').evaluate('(form) => { form.requestSubmit(); form.requestSubmit(); }')
    expect(page).to_have_url(SUCCESS)
    expect(page.get_by_role('heading', name='Заявка сохранена')).to_be_visible()
    assert len(posts) == 1, f'{len(posts)} POST requests for a double submit'
    key = page.url.split('key=')[1]
    receipt = api(context, f'/api/orders/by-key/{key}')
    assert receipt['itemCount'] == 3, receipt
    assert not any(field in receipt for field in ('phone', 'customerName', 'comment'))
    expect(page.locator('.receipt-details')).to_contain_text(str(receipt['number']))
    expect(page.locator('.header-cart-count')).to_have_text('0')
    assert other.request.get(f'{BASE}/api/orders/by-key/{key}').status == 404
    page.reload()
    expect(page.locator('.receipt-details')).to_contain_text(str(receipt['number']))
    no_overflow(page, 'receipt')
    passed('Оформление: контакты из кабинета, ошибка телефона у поля, один POST при двойной отправке, квитанция без контактов, чужая сессия — 404')

    page.get_by_role('link', name='Мои заявки', exact=True).first.click()
    order = page.locator('.order-row').filter(has_text=str(receipt['number']))
    expect(order).to_be_visible()
    expect(order).to_contain_text(receipt['totalText'])
    other_page.goto(BASE + '/account')
    expect(other_page.locator('.order-row')).to_have_count(fixtures.STAFF_ORDERS)
    expect(other_page.locator('.order-row').filter(has_text=str(receipt['number']))).to_have_count(0)
    order.get_by_role('button', name=f"Повторить заказ {receipt['number']}").click()
    expect(page).to_have_url(BASE + '/basket')
    expect(page.locator('.cart-line')).to_have_count(1)
    assert cart_count(context) == 3
    passed('«Мои заявки»: заявка видна только своему партнёру; «Повторить заказ» возвращает позиции в корзину')

    lost = []

    def lose_response(route):
        lost.append(1)
        route.fetch()
        route.abort('failed')
    page.route('**/api/orders', lose_response)
    open_checkout(page)
    consent(page)
    page.get_by_role('button', name='Отправить заявку').click()
    expect(page).to_have_url(SUCCESS)
    page.unroute('**/api/orders')
    assert len(lost) == 1
    passed('Ответ потерян после сохранения: результат найден GET-запросом, без повторного POST')

    add_one(page)
    never_sent = []

    def fail_before_send(route):
        never_sent.append(1)
        route.abort('failed')
    page.route('**/api/orders', fail_before_send)
    open_checkout(page)
    page.locator('#comment').fill('Секретный комментарий проверки')
    consent(page)
    page.get_by_role('button', name='Отправить заявку').click()
    retry = page.get_by_role('button', name='Повторить ту же отправку')
    expect(retry).to_be_visible()
    assert len(never_sent) == 1
    assert page.evaluate('Object.keys(sessionStorage)') == ['mbm.pendingOrderKey']
    assert 'Секретный' not in page.evaluate('JSON.stringify(sessionStorage) + JSON.stringify(localStorage)')
    page.unroute('**/api/orders')
    retry.click()
    expect(page).to_have_url(SUCCESS)
    passed('Сбой до отправки: ручной повтор с тем же ключом; в браузере хранится только ключ, без контактов')

    abandoned = browser.new_context()
    recovered = abandoned.new_page()
    sign_in(recovered)
    recovered.evaluate("sessionStorage.setItem('mbm.pendingOrderKey', '00000000-0000-4000-8000-000000000099')")
    restart_posts = []
    recovered.on('request', lambda request: restart_posts.append(request) if request.url.endswith('/api/orders') and request.method == 'POST' else None)
    add_one(recovered)
    recovered.goto(BASE + '/checkout')
    restart = recovered.get_by_role('button', name='Начать новое оформление')
    expect(restart).to_be_visible()
    restart.click()
    expect(recovered.get_by_role('dialog')).to_be_visible()
    recovered.keyboard.press('Escape')
    expect(restart).to_be_focused()
    restart.click()
    recovered.get_by_role('dialog').get_by_role('button', name='Начать заново').click()
    expect(recovered.locator('.checkout-form')).to_be_visible()
    assert recovered.evaluate("sessionStorage.getItem('mbm.pendingOrderKey')") is None
    assert not restart_posts
    abandoned.close()
    passed('Неизвестный результат после перезагрузки: новое оформление только после подтверждения, без POST')

    page.goto(BASE + '/quick-order')
    page.get_by_label('Список артикулов').fill(f'{fixtures.sku(6)} 3\n{fixtures.sku(2).lower()}\nNOPE-0001 1')
    rows = page.locator('.quick-row')
    expect(rows).to_have_count(3)
    expect(rows.nth(0)).to_contain_text('Найдено')
    expect(rows.nth(1)).to_contain_text('Цена уточняется')
    expect(rows.nth(2)).to_contain_text('Не найдено')
    expect(page.get_by_role('status').filter(has_text='Готово к добавлению')).to_have_text('Готово к добавлению: 1 из 3')
    page.get_by_role('button', name=re.compile('^Добавить в корзину')).click()
    expect(page).to_have_url(BASE + '/basket')
    expect(page.locator('.cart-line').filter(has_text=fixtures.name(6))).to_be_visible()
    no_overflow(page, 'checkout')
    page.get_by_role('button', name='Очистить корзину').click()
    page.get_by_role('dialog').get_by_role('button', name='Очистить', exact=True).click()
    expect(page.get_by_role('heading', name='Корзина пока пуста')).to_be_visible()
    assert cart_count(context) == 0
    passed('Быстрый заказ: найдено / цена уточняется / не найдено, добавление в корзину; очистка корзины')

    with fixtures.connect(DSN) as db:
        rows = db.execute('''select p.login, d.state, d.customer_id, d.sale_id from local_orders o
            join partners p on p.id = o.partner_id join delivery_outbox d on d.order_id = o.id
            where o.customer_name <> 'Тестов Бета' ''').fetchall()
    assert len(rows) == 3 and all(row == (fixtures.ALFA, 'WAITING_CONFIGURATION', None, None) for row in rows), rows
    other.close()
    assert not errors, errors
    passed('В базе три заявки партнёра, все ждут передачи и не связаны с CloudShop; ошибок JavaScript нет')
    browser.close()
