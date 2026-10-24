"""Shared helpers for the browser scenarios. Settings come from tests/browser/run_checks.py."""
import os
import re
from pathlib import Path
from urllib.parse import urlparse

from playwright.sync_api import expect

import fixtures

BASE = os.environ.get('MBM_BROWSER_URL', '').rstrip('/')
if urlparse(BASE).hostname not in ('127.0.0.1', 'localhost'):
    raise SystemExit('Run through tests/browser/run_checks.py: only a local test server on mbm_test is allowed.')
PASSWORD = os.environ['MBM_BROWSER_PARTNER_PASSWORD']
STAFF_USERNAME = os.environ['MBM_BROWSER_STAFF_USERNAME']
STAFF_PASSWORD = os.environ['MBM_BROWSER_STAFF_PASSWORD']
DSN = os.environ['MBM_TEST_DATABASE_DSN']
OUT = Path(os.environ.get('MBM_BROWSER_OUT', '/tmp/mbm-browser'))
OUT.mkdir(parents=True, exist_ok=True)
WIDTHS = (360, 375, 768, 1440)


def passed(message):
    print('PASS', message, flush=True)


def watch_errors(page):
    """Collects uncaught JavaScript errors of the page."""
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    return errors


def sign_in(page, login=fixtures.ALFA, password=None, path='/search'):
    page.goto(BASE + path)
    expect(page.get_by_role('heading', name='Вход для оптовых клиентов')).to_be_visible()
    page.get_by_label('Логин', exact=True).fill(login)
    page.get_by_label('Пароль', exact=True).fill(password or PASSWORD)
    page.get_by_role('button', name='Войти', exact=True).click()
    expect(page.get_by_role('link', name=re.compile('^Кабинет: '))).to_be_visible()


def staff_sign_in(page):
    page.goto(BASE + '/admin/login')
    page.get_by_label('Логин').fill(STAFF_USERNAME)
    page.get_by_label('Пароль', exact=True).fill(STAFF_PASSWORD)
    page.get_by_role('button', name='Войти', exact=True).click()
    page.wait_for_url('**/admin/clients')


def no_overflow(page, name):
    """No horizontal scroll at any checked width; saves a 375 px screenshot."""
    for width in WIDTHS:
        page.set_viewport_size({'width': width, 'height': 900})
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), f'{name}: horizontal scroll at {width}px'
    page.set_viewport_size({'width': 375, 'height': 900})
    page.screenshot(path=str(OUT / f'{name}-375.png'), full_page=True)
    page.set_viewport_size({'width': 1440, 'height': 1000})


def api(context, path):
    response = context.request.get(BASE + path)
    assert response.ok, (path, response.status)
    return response.json()


def csrf_headers(context):
    token = api(context, '/api/csrf')
    return {token['headerName']: token['token']}
