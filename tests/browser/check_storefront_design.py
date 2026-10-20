#!/usr/bin/env python3
"""Visual and mobile-filter checks on a locally seeded synthetic storefront."""
import os
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

base = os.environ['MBM_FRONTEND_URL'].rstrip('/')
if urlparse(base).hostname not in ('127.0.0.1', 'localhost'):
    raise SystemExit('Use the isolated local test site.')
output = Path('/tmp/mbm-storefront-design')
output.mkdir(exist_ok=True)
product = '000000000000000000000001'
store = '64feaaa51c9799143b532fcf'

def capture(page, name, width):
    page.wait_for_load_state('networkidle')
    page.evaluate('''async () => {
      const visibleImages = [...document.images].filter(image => {
        const rect = image.getBoundingClientRect();
        return rect.top < innerHeight && rect.bottom > 0;
      });
      visibleImages.forEach(image => { image.loading = 'eager'; });
      await Promise.all(visibleImages.map(image => image.decode().catch(() => {})));
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
    }''')
    assert not page.evaluate('document.documentElement.scrollWidth > innerWidth'), (name, width)
    page.screenshot(path=str(output / f'{name}-{width}.png'), full_page=False)

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={'width':375,'height':900})
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.goto(base)
    expect(page.locator('.product-card')).to_have_count(24)
    page.get_by_role('button', name='Найти', exact=True).hover()
    expect(page.get_by_role('button', name='Найти', exact=True)).to_have_css('background-color', 'rgb(153, 53, 14)')
    page.get_by_role('button', name='Перейти к поиску запчастей').click()
    expect(page.get_by_label('Поиск запчастей', exact=True)).to_be_focused()
    page.get_by_role('button', name='Фильтры', exact=False).click()
    expect(page.get_by_role('dialog')).to_be_visible()
    page.get_by_label('Магазин или склад').select_option('698c479d4bb8342bd30e3c33')
    page.get_by_label('Только в наличии').click()
    expect(page.get_by_label('Только в наличии')).to_be_checked()
    page.keyboard.press('Escape')
    expect(page.get_by_role('dialog')).not_to_be_visible()
    expect(page.locator('.filter-trigger')).to_be_focused()
    assert page.evaluate('document.body.style.overflow') != 'hidden'
    assert 'available=true' in page.url and 'store=698c479d4bb8342bd30e3c33' in page.url
    page.get_by_role('button', name='Фильтры', exact=False).click()
    expect(page.get_by_label('Только в наличии')).to_be_checked()
    page.set_viewport_size({'width':768,'height':900})
    expect(page.get_by_role('dialog')).not_to_be_visible()
    expect(page.get_by_label('Магазин или склад')).to_be_focused()
    assert page.evaluate('document.body.style.overflow') != 'hidden'
    page.reload()
    expect(page.get_by_label('Только в наличии')).to_be_checked()
    print('PASS Mobile filters: URL persistence, Escape, focus, resize and scroll restoration')
    page.goto(base + f'/products/{product}?store={store}')
    page.get_by_role('button', name='Добавить из Car City в корзину').click()
    expect(page.locator('.cart-count')).to_have_text('1')
    for width in [375,768,1440]:
        page.set_viewport_size({'width':width,'height':1000})
        for name, path in [('catalog','/'), ('product',f'/products/{product}?store={store}'), ('basket','/basket'), ('checkout','/checkout')]:
            page.goto(base + path)
            capture(page, name, width)
    page.goto(base + f'/products/{product}?store={store}')
    page.get_by_label('Поиск запчастей в каталоге').fill('CHECKOUT-001')
    page.get_by_role('button', name='Искать в каталоге').click()
    expect(page.locator('.product-card')).to_have_count(1)
    assert 'q=CHECKOUT-001' in page.url and store in page.url
    page.emulate_media(reduced_motion='reduce')
    assert page.locator('.button').first.evaluate('(e) => getComputedStyle(e).transitionDuration') == '0s'
    page.set_viewport_size({'width':375,'height':900})
    page.route('**/api/catalog/stores', lambda route: route.fulfill(status=503, content_type='application/problem+json', body='{"detail":"Магазины временно недоступны"}'))
    page.goto(base)
    expect(page.get_by_role('alert')).to_contain_text('Не удалось загрузить магазины')
    page.locator('.filter-trigger').click()
    panel = page.get_by_role('dialog')
    expect(panel.get_by_label('Магазин или склад')).to_be_disabled()
    expect(panel.locator('option')).to_have_text('Магазины недоступны')
    page.unroute('**/api/catalog/stores')
    panel.get_by_role('button', name='Повторить').click()
    expect(panel.get_by_label('Магазин или склад')).to_be_enabled()
    page.keyboard.press('Escape')
    print('PASS Store-load error can be recovered inside the mobile panel')
    assert not errors, errors
    browser.close()
print('PASS Four pages at 375/768/1440px: no overflow; header search works; reduced motion honored; JS errors 0')
print('Screenshots:', output)
