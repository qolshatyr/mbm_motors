#!/usr/bin/env python3
"""Check staff delivery messages using synthetic states ONLY in mbm_test."""
import os
import uuid
from urllib.parse import urlparse

import psycopg
from playwright.sync_api import sync_playwright, expect

url = os.environ['MBM_STAFF_TEST_URL'].rstrip('/')
if urlparse(url).hostname not in ('localhost', '127.0.0.1'):
    raise SystemExit('Local test site required.')
oid = uuid.uuid5(uuid.NAMESPACE_URL, 'mbm-staff-fixture-24')
states = {
    'READY': 'Заявка подготовлена к передаче.',
    'CUSTOMER_IN_FLIGHT': 'Выполняется создание покупателя.',
    'CUSTOMER_CREATED': 'Покупатель создан.',
    'SALE_IN_FLIGHT': 'Выполняется создание непроведённой продажи.',
    'SENT': 'Это не подтверждает резерв или оплату.',
    'UNKNOWN': 'Нужна ручная сверка; автоматический повтор запрещён.',
}
with psycopg.connect(os.environ['MBM_TEST_DATABASE_DSN'], autocommit=True) as db:
    if db.info.dbname != 'mbm_test':
        raise SystemExit('Dedicated mbm_test database required.')
    number = db.execute('select number from local_orders where id=%s', (oid,)).fetchone()[0]
    original = db.execute('select state,customer_id,sale_id,sale_number from delivery_outbox where order_id=%s', (oid,)).fetchone()
    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page()
            errors = []
            page.on('pageerror', lambda error: errors.append(str(error)))
            page.goto(url + '/admin/login')
            page.locator('input[name="username"]').fill(os.environ['MBM_STAFF_TEST_USERNAME'])
            page.locator('input[name="password"]').fill(os.environ['MBM_STAFF_TEST_PASSWORD'])
            page.get_by_role('button', name='Войти', exact=True).click()
            page.wait_for_load_state('networkidle')
            for state, message in states.items():
                customer = 'b' * 24 if state in ('CUSTOMER_CREATED', 'SALE_IN_FLIGHT', 'SENT') else None
                db.execute('update delivery_outbox set state=%s,customer_id=%s,sale_id=%s,sale_number=%s where order_id=%s',
                           (state, customer, 'c' * 24 if state == 'SENT' else None, 123 if state == 'SENT' else None, oid))
                page.goto(url + '/admin/requests/' + str(number))
                page.wait_for_load_state('networkidle')
                expect(page.get_by_role('region', name='Сохранена на сайте').locator('.staff-facts')).to_contain_text(message)
            assert not errors, errors
            browser.close()
    finally:
        db.execute('update delivery_outbox set state=%s,customer_id=%s,sale_id=%s,sale_number=%s where order_id=%s', (*original, oid))
print('Delivery browser: six synthetic state messages passed; original state restored; JS errors 0')
