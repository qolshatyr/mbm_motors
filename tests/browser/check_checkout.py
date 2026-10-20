#!/usr/bin/env python3
"""Verify local checkout using a disposable seeded database only.

Set MBM_CHECKOUT_TEST_URL to the isolated server. It must have fresh synthetic
products (IDs 000..001), pickup Car City enabled and never contain real customers.
This test creates local synthetic orders. It does not call CloudShop.
"""
import os
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('MBM_CHECKOUT_TEST_URL')
if not BASE:
    raise SystemExit('Set MBM_CHECKOUT_TEST_URL for an isolated seeded test server; never use the working catalog.')
STORE = '64feaaa51c9799143b532fcf'
PRODUCT = '000000000000000000000001'


def passed(message):
    print('PASS', message, flush=True)


def fill(page):
    page.get_by_label('Имя', exact=True).fill('Тестовый покупатель')
    page.get_by_label('Телефон', exact=True).fill('+77000000000')
    page.get_by_label('Комментарий', exact=False).fill('Синтетическая проверка оформления')
    page.locator('#consent').check()


def checkout(page):
    page.goto(f'{BASE}/products/{PRODUCT}?store={STORE}')
    page.get_by_role('button', name='Добавить из Car City в корзину', exact=True).click()
    expect(page.locator('.cart-count')).to_have_text('1')
    page.get_by_role('link', name='Открыть корзину', exact=True).click()
    page.get_by_role('link', name='Оформить заявку', exact=True).click()
    expect(page.get_by_role('heading', name='Оформление заявки', exact=True)).to_be_visible()
    expect(page.get_by_role('button', name='Сохранить заявку', exact=True)).to_be_enabled()


with sync_playwright() as playwright:
    browser = playwright.chromium.launch()
    context = browser.new_context(viewport={'width':1440,'height':1000})
    page = context.new_page()
    errors = []
    page.on('pageerror', lambda error:errors.append(str(error)))
    checkout(page)
    fill(page)
    page.get_by_label('Телефон', exact=True).fill('letters')
    page.get_by_role('button', name='Сохранить заявку', exact=True).click()
    expect(page.locator('#phone-error')).to_be_visible()
    expect(page.locator('#phone')).to_be_focused()
    expect(page.locator('#customerName')).to_have_value('Тестовый покупатель')
    passed('Ошибки контактов отображаются у поля, фокус и остальные данные сохранены')
    page.get_by_label('Телефон', exact=True).fill('+77000000000')
    for width in [360,375,768,1440]:
        page.set_viewport_size({'width':width,'height':900})
        assert not page.evaluate('document.documentElement.scrollWidth > innerWidth')
    page.set_viewport_size({'width':375,'height':812})
    page.screenshot(path='/tmp/mbm-checkout-mobile.png', full_page=True)
    passed('Оформление на360/375/768/1440px без горизонтальной прокрутки')
    posts=[]
    page.on('request',lambda request:posts.append(request) if request.method=='POST' and request.url.endswith('/api/orders') else None)
    page.locator('.checkout-form').evaluate('(form)=>{form.requestSubmit();form.requestSubmit();}')
    expect(page).to_have_url(__import__('re').compile(r'/checkout/success\?key='))
    expect(page.get_by_text('RECEIVED',exact=True)).to_have_count(0)
    assert len(posts)==1, 'Double submit issued more than one POST'
    key=page.url.split('key=')[1]
    receipt=context.request.get(f'{BASE}/api/orders/by-key/{key}').json()
    assert receipt['status']=='RECEIVED' and receipt['itemCount']==1
    assert not any(field in receipt for field in ['phone','customerName','comment'])
    assert context.request.get(BASE+'/api/cart').json()['itemCount']==0
    page.reload()
    expect(page.get_by_text(receipt['number'],exact=False).first).to_be_visible()
    other=browser.new_context()
    assert other.request.get(f'{BASE}/api/orders/by-key/{key}').status==404
    other.close()
    passed('Один POST при двойном submit, номер заявки, пустая корзина, приватность и reload результата')
    checkout(page)
    fill(page)
    unknown=[]
    def lose_response(route):
        unknown.append(1)
        route.fetch()
        route.abort('failed')
    page.route('**/api/orders',lose_response)
    page.get_by_role('button',name='Сохранить заявку',exact=True).click()
    expect(page).to_have_url(__import__('re').compile(r'/checkout/success\?key='))
    assert len(unknown)==1
    page.unroute('**/api/orders')
    passed('Потеря ответа после commit восстанавливается GET без повторного POST')
    checkout(page)
    fill(page)
    never_sent=[]
    def before_send(route):
        never_sent.append(1)
        route.abort('failed')
    page.route('**/api/orders',before_send)
    page.get_by_role('button',name='Сохранить заявку',exact=True).click()
    expect(page.get_by_role('button',name='Повторить ту же отправку',exact=True)).to_be_visible()
    assert len(never_sent)==1
    assert list(page.evaluate('Object.keys(sessionStorage)'))==['mbm.pendingOrderKey']
    assert 'Тестовый' not in page.evaluate('JSON.stringify(sessionStorage)')
    page.unroute('**/api/orders')
    page.get_by_role('button',name='Повторить ту же отправку',exact=True).click()
    expect(page).to_have_url(__import__('re').compile(r'/checkout/success\?key='))
    passed('Ошибка до отправки не теряет форму; ручной повтор использует тот же ключ, контакты не сохраняются в storage')
    # Another store exists in the catalog but is not an approved pickup point.
    page.goto(f'{BASE}/products/{PRODUCT}?store=698c479d4bb8342bd30e3c33')
    page.get_by_role('button',name='Добавить из БАКОРДА в корзину',exact=True).click()
    expect(page.locator('.cart-count')).to_have_text('1')
    page.get_by_role('link',name='Открыть корзину',exact=True).click()
    page.get_by_role('link',name='Оформить заявку',exact=True).click()
    expect(page.get_by_role('button',name='Сохранить заявку',exact=True)).to_be_disabled()
    passed('Место хранения не превращается автоматически в точку самовывоза')
    abandoned = browser.new_context()
    recovered = abandoned.new_page()
    key = '00000000-0000-4000-8000-000000000099'
    recovered.add_init_script(f"sessionStorage.setItem('mbm.pendingOrderKey', '{key}')")
    restart_posts = []
    recovered.on('request', lambda request: restart_posts.append(request) if request.method == 'POST' else None)
    recovered.goto(BASE+'/checkout')
    restart = recovered.get_by_role('button', name='Начать новое оформление', exact=True)
    expect(restart).to_be_visible()
    restart.click()
    expect(recovered.get_by_role('dialog')).to_be_visible()
    expect(recovered.get_by_role('button', name='Отмена', exact=True)).to_be_focused()
    recovered.keyboard.press('Escape')
    expect(restart).to_be_focused()
    assert recovered.evaluate("sessionStorage.getItem('mbm.pendingOrderKey')") == key
    restart.click()
    recovered.get_by_role('dialog').get_by_role('button', name='Начать заново', exact=True).click()
    expect(recovered.locator('.checkout-form')).to_be_visible()
    assert recovered.evaluate("sessionStorage.getItem('mbm.pendingOrderKey')") is None
    assert not restart_posts
    abandoned.close()
    passed('Истёкшая сессия не блокирует оформление навсегда: подтверждённый выход из восстановления без POST')
    assert not errors, errors
    passed('Ошибок JavaScript нет; CloudShop записи не создавались')
    browser.close()
