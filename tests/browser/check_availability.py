#!/usr/bin/env python3
"""Explain blocked synthetic offers; no orders or catalog edits."""
import os
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

base = os.environ['MBM_AVAILABILITY_TEST_URL'].rstrip('/')
if urlparse(base).hostname not in ('127.0.0.1', 'localhost'):
    raise SystemExit('Use a dedicated local fixture server.')
with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    context = browser.new_context(viewport={'width':375,'height':900})
    page = context.new_page()
    errors=[]
    page.on('pageerror',lambda e:errors.append(str(e)))
    page.goto(base+'/products/000000000000000000000002')
    expect(page.get_by_text('Для товара нужно уточнить единицу измерения. Сейчас доступны только штуки.')).to_have_count(4)
    assert page.get_by_role('button',name='Добавить из Car City в корзину').count()==0
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
    page.goto(base+'/products/000000000000000000000003')
    expect(page.get_by_text('Цену товара нужно уточнить.',exact=True)).to_have_count(2)
    page.goto(base+'/products/000000000000000000000001')
    expect(page.get_by_role('button',name='Добавить из Car City в корзину')).to_be_enabled()
    payload=context.request.get(base+'/api/catalog/products/000000000000000000000001').json()
    payload['stale']=True
    for offer in payload['offers']:
        offer.update(canAdd=False,stockText='Наличие уточняется',unavailabilityReason='Данные о наличии устарели. Попробуйте позже или уточните наличие в магазине.')
    page.route('**/api/catalog/products/000000000000000000000001',lambda r:r.fulfill(json=payload))
    page.reload()
    expect(page.get_by_text('Данные о наличии устарели. Попробуйте позже или уточните наличие в магазине.')).to_have_count(4)
    assert page.get_by_role('button',name='Добавить из Car City в корзину').count()==0
    assert errors==[],errors
    browser.close()
print('Availability browser: missing unit, zero price, valid pieces, stale explanation and mobile layout — passed')
