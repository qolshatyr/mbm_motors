#!/usr/bin/env python3
"""Staff section: sign-in, «Клиенты» and «Заявки» on synthetic fixtures.

Creates a synthetic partner and records a manual reconciliation in mbm_test only; CloudShop
customer cards are off on the test server.
"""
import re

from playwright.sync_api import expect, sync_playwright

import fixtures
from common import BASE, DSN, STAFF_USERNAME, no_overflow, passed, sign_in, staff_sign_in, watch_errors

GAMMA, GAMMA_COMPANY = 'sto-gamma', 'СТО «Гамма-Тест»'


def credentials(page):
    """Reads the one-time password from the dialog and closes it."""
    dialog = page.get_by_role('dialog')
    expect(dialog.get_by_role('heading', name=f'Доступ: {GAMMA_COMPANY}')).to_be_visible()
    password = dialog.locator('.credentials-list dd').nth(2).inner_text()
    note = dialog.locator('.credentials-note').inner_text()
    dialog.get_by_role('button', name='Готово').click()
    expect(dialog).not_to_be_visible()
    return password, note


def partner_can_sign_in(browser, password):
    context = browser.new_context()
    page = context.new_page()
    page.goto(BASE + '/login')
    page.get_by_label('Логин', exact=True).fill(GAMMA)
    page.get_by_label('Пароль', exact=True).fill(password)
    page.get_by_role('button', name='Войти', exact=True).click()
    signed_in = page.get_by_role('link', name=f'Кабинет: {GAMMA_COMPANY}').or_(page.get_by_role('alert'))
    expect(signed_in).to_be_visible()
    result = page.get_by_role('alert').count() == 0
    context.close()
    return result


with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    context = browser.new_context(viewport={'width': 1440, 'height': 1000})
    page = context.new_page()
    errors = watch_errors(page)
    numbers = fixtures.staff_order_numbers(DSN)

    page.goto(BASE + '/admin/requests')
    expect(page).to_have_url(re.compile('/admin/login'))
    assert context.request.get(BASE + '/admin/api/requests').status == 401
    page.get_by_label('Логин').fill(STAFF_USERNAME)
    page.get_by_label('Пароль', exact=True).fill('wrong-synthetic-password')
    page.get_by_role('button', name='Войти', exact=True).click()
    expect(page).to_have_url(re.compile(r'/admin/login\?error'))
    expect(page.get_by_role('alert')).to_have_text('Не удалось войти. Проверьте логин и пароль.')
    partner = browser.new_context()
    sign_in(partner.new_page())
    assert partner.request.get(BASE + '/admin/api/requests').status in (401, 403)
    partner.close()
    staff_sign_in(page)
    expect(page.get_by_role('heading', name='Клиенты', level=1)).to_be_visible()
    passed('Служебный раздел закрыт без входа и для партнёра; неверный пароль отклонён; вход ведёт в «Клиенты»')

    clients = page.locator('.client-row')
    expect(clients).to_have_count(3)
    expect(clients.filter(has_text=fixtures.BLOCKED)).to_contain_text('Заблокирован')
    expect(clients.filter(has_text=fixtures.ALFA)).to_contain_text('Карточки CloudShop нет')
    page.get_by_label('Поиск по компании или логину').fill('бета')
    expect(clients).to_have_count(1)
    page.get_by_label('Поиск по компании или логину').fill('')
    expect(clients).to_have_count(3)

    form = page.locator('form.client-form')
    form.get_by_label('Компания').fill(GAMMA_COMPANY)
    expect(form.get_by_label('Логин')).to_have_value('gammatest')
    form.get_by_label('Телефон').fill('+77010000004')
    form.get_by_label('Контактное лицо').fill('Тестов Гамма')
    form.get_by_label('Логин').fill(fixtures.ALFA)
    form.get_by_role('button', name='Создать учётную запись').click()
    expect(form.get_by_text('Такой логин уже занят')).to_be_visible()
    form.get_by_label('Логин').fill(GAMMA)
    form.get_by_role('button', name='Создать учётную запись').click()
    password, note = credentials(page)
    assert 'Карточка CloudShop не создавалась' in note, note
    expect(clients).to_have_count(4)
    gamma = clients.filter(has_text=GAMMA)
    expect(gamma).to_contain_text('Не входил')
    assert partner_can_sign_in(browser, password)
    page.reload()
    expect(gamma).to_contain_text('Активен')
    passed('«Клиенты»: поиск, логин из названия, занятый логин, создание без карточки CloudShop; новый партнёр входит с выданным паролем')

    gamma.get_by_role('button', name='Заблокировать').click()
    dialog = page.get_by_role('dialog')
    expect(dialog.get_by_role('button', name='Отмена')).to_be_focused()
    dialog.get_by_role('button', name='Заблокировать').click()
    expect(gamma).to_contain_text('Заблокирован')
    assert not partner_can_sign_in(browser, password)
    gamma.get_by_role('button', name='Разблокировать').click()
    expect(gamma).not_to_contain_text('Заблокирован')
    gamma.get_by_role('button', name='Новый пароль').click()
    page.get_by_role('dialog').get_by_role('button', name='Выдать новый пароль').click()
    new_password, note = credentials(page)
    assert 'Старый пароль больше не действует' in note, note
    assert not partner_can_sign_in(browser, password)
    assert partner_can_sign_in(browser, new_password)
    no_overflow(page, 'staff-clients')
    passed('Блокировка закрывает вход, разблокировка возвращает; новый пароль отменяет старый')

    page.get_by_role('navigation', name='Служебная навигация').get_by_role('link', name='Заявки').click()
    expect(page.get_by_role('heading', name='Заявки', level=1)).to_be_visible()
    expect(page.locator('.staff-request-list > li')).to_have_count(20)
    expect(page.get_by_text(f'Всего заявок: {fixtures.STAFF_ORDERS}')).to_be_visible()
    expect(page.get_by_text('Страница 1 из 2')).to_be_visible()
    page.get_by_role('button', name='Следующая страница заявок').click()
    expect(page.locator('.staff-request-list > li')).to_have_count(fixtures.STAFF_ORDERS - 20)
    assert '?page=1' in page.url
    page.reload()
    expect(page.locator('.staff-request-list > li')).to_have_count(fixtures.STAFF_ORDERS - 20)
    page.goto(BASE + '/admin/requests?page=100000')
    expect(page.get_by_role('heading', name='На этой странице нет заявок')).to_be_visible()
    page.goto(BASE + '/admin/requests?page=-1')
    expect(page.get_by_text('Номер страницы должен быть от 0 до 100000.')).to_be_visible()
    page.goto(BASE + '/admin/requests/9223372036854775807')
    expect(page.get_by_text('Заявка не найдена. Проверьте номер или вернитесь к списку заявок.')).to_be_visible()
    passed('«Заявки»: 20 + 2 на страницах, адрес и перезагрузка, пустая и неверная страница, несуществующий номер')

    page.goto(BASE + f'/admin/requests/{numbers[fixtures.XSS_ORDER]}')
    expect(page.get_by_role('heading', name='Покупатель')).to_be_visible()
    expect(page.get_by_text('+77010000002', exact=True)).to_be_visible()
    expect(page.get_by_text('5 000,02 ₸', exact=True).first).to_be_visible()
    assert page.locator('.staff-comment img').count() == 0
    assert page.evaluate('window.staffXss !== true')
    assert page.evaluate('localStorage.length + sessionStorage.length') == 0
    no_overflow(page, 'staff-request')
    page.goto(BASE + f'/admin/requests/{numbers[fixtures.SENT_ORDER]}')
    expect(page.locator('.staff-overview')).to_contain_text('Это не подтверждает резерв или оплату.')
    page.goto(BASE + f'/admin/requests/{numbers[fixtures.UNKNOWN_ORDER]}')
    overview = page.locator('.staff-overview')
    expect(overview).to_contain_text('Синтетический таймаут ответа CloudShop')
    reconcile = page.get_by_role('form', name='Ручная сверка')
    reconcile.get_by_label('Продажа найдена в CloudShop').check()
    expect(reconcile.get_by_role('button', name='Записать сверку')).to_be_disabled()
    reconcile.get_by_label('Номер продажи').fill('0')
    expect(reconcile.get_by_role('button', name='Записать сверку')).to_be_disabled()
    reconcile.get_by_label('Продажи в CloudShop нет').check()
    reconcile.get_by_role('button', name='Записать сверку').click()
    expect(overview.get_by_text('Сверка', exact=True)).to_be_visible()
    expect(overview).to_contain_text(STAFF_USERNAME)
    expect(page.get_by_role('form', name='Ручная сверка')).to_have_count(0)
    page.reload()
    expect(page.get_by_role('form', name='Ручная сверка')).to_have_count(0)
    passed('Подробности: контакты, безопасный HTML-комментарий, состояния SENT и UNKNOWN; ручная сверка записывается один раз')

    number = numbers[fixtures.XSS_ORDER]
    page.goto(BASE + f'/admin/requests/{number}')
    expect(page.get_by_text('+77010000002', exact=True)).to_be_visible()
    page.route(f'**/admin/api/requests/{number}', lambda route: route.fulfill(
        status=401, content_type='application/problem+json', body='{"detail":"Войдите"}'))
    page.get_by_role('button', name='Обновить', exact=True).click()
    expect(page.get_by_role('link', name='Войти в служебный раздел')).to_be_visible()
    expect(page.get_by_text('+77010000002', exact=True)).to_have_count(0)
    page.unroute(f'**/admin/api/requests/{number}')
    page.reload()
    expect(page.get_by_text('+77010000002', exact=True)).to_be_visible()
    page.get_by_role('button', name='Выйти', exact=True).click()
    page.wait_for_url('**/admin/login?logout')
    expect(page.get_by_text('Вы вышли из служебного раздела.')).to_be_visible()
    page.go_back()
    expect(page.get_by_text('+77010000002', exact=True)).to_have_count(0)
    staff_sign_in(page)
    page.goto(BASE + f'/admin/requests/{number}')
    expect(page.get_by_text('+77010000002', exact=True)).to_be_visible()
    second = context.new_page()
    second.goto(BASE + '/admin/requests')
    second.get_by_role('button', name='Выйти', exact=True).click()
    second.wait_for_url('**/admin/login?logout')
    second.close()
    page.bring_to_front()
    page.evaluate("window.dispatchEvent(new Event('focus'))")
    expect(page.get_by_role('link', name='Войти в служебный раздел')).to_be_visible()
    expect(page.get_by_text('+77010000002', exact=True)).to_have_count(0)
    assert context.request.get(BASE + '/admin/api/requests').status == 401
    assert not errors, errors
    passed('Ответ 401 скрывает контакты; выход, возврат назад и выход в другой вкладке не оставляют контактов; ошибок JavaScript нет')
    browser.close()
