#!/usr/bin/env python3
"""Read-only staff browser checks on an explicitly configured local test server."""
import os
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

base = os.environ['MBM_STAFF_TEST_URL'].rstrip('/')
if urlparse(base).hostname not in ('127.0.0.1', 'localhost'):
    raise SystemExit('Use a dedicated local test server.')
username = os.environ['MBM_STAFF_TEST_USERNAME']
password = os.environ['MBM_STAFF_TEST_PASSWORD']

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    context = browser.new_context(viewport={'width': 1440, 'height': 1000})
    page = context.new_page()
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.goto(base + '/admin/requests')
    page.wait_for_load_state('networkidle')
    # Vite serves the SPA route; the packaged server redirects before rendering it.
    login_link = page.get_by_role('link', name='Войти в служебный раздел')
    if login_link.is_visible():
        login_link.click()
        page.wait_for_load_state('networkidle')
    expect(page.get_by_role('heading', name='Вход сотрудника')).to_be_visible()
    page.get_by_label('Логин').fill(username)
    page.get_by_label('Пароль', exact=True).fill('wrong-synthetic-password')
    page.get_by_role('button', name='Войти', exact=True).click()
    page.wait_for_load_state('networkidle')
    expect(page.get_by_role('alert')).to_be_visible()
    page.get_by_label('Логин').fill(username)
    page.get_by_label('Пароль', exact=True).fill(password)
    page.get_by_role('button', name='Войти', exact=True).click()
    page.wait_for_load_state('networkidle')
    expect(page.get_by_role('heading', name='Заявки', exact=True)).to_be_visible()
    expect(page.locator('.staff-request-list > li')).to_have_count(20)
    expect(page.get_by_text('Страница 1 из 2')).to_be_visible()
    page.get_by_role('button', name='Следующая страница заявок').click()
    expect(page.locator('.staff-request-list > li')).to_have_count(5)
    assert '?page=1' in page.url
    page.reload()
    expect(page.locator('.staff-request-list > li')).to_have_count(5)
    page.get_by_role('button', name='Предыдущая страница заявок').click()
    expect(page.locator('.staff-request-list > li')).to_have_count(20)
    page.goto(base + '/admin/requests?page=100000')
    expect(page.get_by_role('heading', name='На этой странице нет заявок')).to_be_visible()
    page.goto(base + '/admin/requests?page=-1')
    expect(page.get_by_text('Номер страницы должен быть от 0 до 100000.')).to_be_visible()
    page.goto(base + '/admin/requests/9223372036854775807')
    expect(page.get_by_text('Заявка не найдена. Проверьте номер или вернитесь к списку заявок.')).to_be_visible()
    page.goto(base + '/admin/requests')
    expect(page.locator('.staff-request-list > li')).to_have_count(20)
    detail_link = page.locator('.staff-number').first
    number = detail_link.get_attribute('href').split('/')[-1]
    detail_link.click()
    expect(page.get_by_role('heading', name='Покупатель', exact=True)).to_be_visible()
    expect(page.get_by_text('Передача ещё не подключена. Ожидает настройки.',exact=True)).to_be_visible()
    expect(page.get_by_text('+70000000000', exact=True)).to_be_visible()
    expect(page.get_by_text('5 000,02 ₸', exact=True).first).to_be_visible()
    assert page.locator('.staff-comment img').count() == 0
    assert page.evaluate('window.staffXss !== true')
    assert page.locator('nav[aria-label="Основная навигация"]').count() == 0
    assert page.evaluate('localStorage.length') == 0
    assert page.evaluate('sessionStorage.length') == 0
    for width in (360, 375, 768, 1440):
        page.set_viewport_size({'width': width, 'height': 1000})
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), width
    page.set_viewport_size({'width': 375, 'height': 1000})
    page.screenshot(path='/tmp/mbm-staff-mobile.png', full_page=True)
    page.reload()
    expect(page.get_by_role('heading', name='Покупатель', exact=True)).to_be_visible()
    # API loss of authentication must hide contacts even though query data existed.
    page.route('**/admin/api/requests/' + number, lambda r: r.fulfill(status=401, content_type='application/problem+json', body='{"detail":"Войдите"}'))
    page.get_by_role('button', name='Обновить', exact=True).click()
    expect(page.get_by_role('link', name='Войти в служебный раздел')).to_be_visible()
    expect(page.get_by_text('+70000000000', exact=True)).to_have_count(0)
    page.unroute('**/admin/api/requests/' + number)
    page.reload()
    expect(page.get_by_role('heading', name='Покупатель', exact=True)).to_be_visible()
    page.get_by_role('button', name='Выйти', exact=True).click()
    page.wait_for_url('**/admin/login?logout')
    page.go_back()
    page.wait_for_load_state('networkidle')
    expect(page.get_by_text('+70000000000', exact=True)).to_have_count(0)
    # Sign in again, keep detail open in A, and log out in B with the shared session.
    page.goto(base + '/admin/login')
    page.get_by_label('Логин').fill(username)
    page.get_by_label('Пароль', exact=True).fill(password)
    page.get_by_role('button', name='Войти', exact=True).click()
    page.wait_for_load_state('networkidle')
    page.goto(base + '/admin/requests/' + number)
    expect(page.get_by_text('+70000000000', exact=True)).to_be_visible()
    other = context.new_page()
    other.goto(base + '/admin/requests')
    other.wait_for_load_state('networkidle')
    other.get_by_role('button', name='Выйти', exact=True).click()
    other.wait_for_url('**/admin/login?logout')
    page.bring_to_front()
    page.evaluate("window.dispatchEvent(new Event('focus'))")
    expect(page.get_by_role('link', name='Войти в служебный раздел')).to_be_visible()
    expect(page.get_by_text('+70000000000', exact=True)).to_have_count(0)
    other.close()
    page.goto(base + '/admin/api/requests')
    assert '401' in page.content()
    assert errors == [], errors
    context.close()
    browser.close()
print('Staff browser: login/error, list/pagination/reload, private detail/XSS, four widths, 401 recovery, logout/back and two-tab logout/focus — passed; JS errors 0')
