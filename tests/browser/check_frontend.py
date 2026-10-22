#!/usr/bin/env python3
"""Check the local React catalog with an imported catalog (>24 products).

Requires Python Playwright and Chromium; writes screenshots/results under /tmp.
Uses GET and its own browser sessions, without orders or CloudShop writes.
"""
import json
import os
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
checks=[]
def ok(s):checks.append(s);print('PASS',s,flush=True)
base=os.environ.get('MBM_FRONTEND_URL','http://127.0.0.1:5173')
with sync_playwright() as p:
 b=p.chromium.launch(headless=True);ctx=b.new_context(viewport={'width':1440,'height':1000});page=ctx.new_page();errors=[]
 page.on('pageerror',lambda e:errors.append(str(e)))
 page.goto(base);page.wait_for_load_state('networkidle');expect(page.locator('.product-card')).to_have_count(24)
 assert not page.evaluate('document.documentElement.scrollWidth>innerWidth')
 page.keyboard.press('Tab');assert page.locator('.skip-link').evaluate('(e)=>e===document.activeElement')
 assert page.locator('.skip-link').evaluate('(e)=>getComputedStyle(e).outlineStyle')!='none'
 ok('Живой каталог 24 товара, клавиатура и ссылка к содержимому')
 sku=page.locator('.sku').first.inner_text()
 page.get_by_label('Поиск запчастей',exact=True).fill(sku);page.get_by_role('button',name='Найти',exact=True).click()
 expect(page.locator('.sku').filter(has_text=sku).first).to_be_visible();assert 'q=' in page.url
 page.reload();expect(page.get_by_label('Поиск запчастей',exact=True)).to_have_value(sku)
 ok('Поиск и URL сохраняются после перезагрузки')
 page.get_by_role('button',name='Сбросить фильтры',exact=True).click()
 page.get_by_label('Только в наличии',exact=True).click();expect(page.get_by_label('Только в наличии',exact=True)).to_be_checked();page.wait_for_load_state('networkidle')
 page.get_by_role('link',name='Далее',exact=True).click();page.wait_for_load_state('networkidle')
 assert 'page=1' in page.url and 'available=true' in page.url
 expect(page.locator('#results-title')).to_be_focused()
 assert 0<=page.locator('#results-title').bounding_box()['y']<300
 page.get_by_label('Магазин или склад',exact=True).select_option('698c479d4bb8342bd30e3c33');page.wait_for_load_state('networkidle')
 assert 'page=' not in page.url and 'store=698c479d4bb8342bd30e3c33' in page.url
 page.go_back();page.wait_for_load_state('networkidle');assert 'page=1' in page.url
 ok('Пагинация сохраняет фильтр, смена магазина сбрасывает страницу, Back работает')
 page.goto(base+'/?q=zzzz-mbm-impossible');expect(page.get_by_text('Товары не найдены',exact=True)).to_be_visible()
 page.get_by_role('button',name='Показать весь каталог',exact=True).click();expect(page.locator('.product-card')).to_have_count(24)
 ok('Пустая выдача и восстановление каталога')
 page.goto(base+'/?store=unknown');expect(page.get_by_role('alert')).to_be_visible();page.get_by_role('button',name='Сбросить фильтры',exact=True).first.click();expect(page.locator('.product-card')).to_have_count(24)
 page.goto(base+'/?page=1000000');expect(page.get_by_role('alert')).to_be_visible()
 page.goto(base+'/?page=1.5');expect(page.get_by_text('Проверьте параметры поиска',exact=True)).to_be_visible()
 ok('Неверные параметры и HTTP404 не показывают ложные товары')
 page.goto(base);page.wait_for_load_state('networkidle')
 for width in [360,375,768,1440]:
  page.set_viewport_size({'width':width,'height':900});assert not page.evaluate('document.documentElement.scrollWidth>innerWidth')
 page.set_viewport_size({'width':375,'height':812});page.screenshot(path='/tmp/mbm-react-mobile.png',full_page=True)
 assert page.get_by_role('button',name='Найти',exact=True).bounding_box()['height']>=44
 ok('360/375/768/1440px: нет горизонтальной прокрутки, кнопка доступна на мобильном')
 href=page.locator('.product-card h3 a').first.get_attribute('href')
 page.locator('.product-card h3 a').first.click();page.wait_for_load_state('networkidle');expect(page.locator('#product-title')).to_be_visible()
 assert '/products/' in page.url and page.url.startswith(base)
 page.goto(base);page.get_by_role('link',name='Корзина',exact=False).click();expect(page.get_by_role('heading',name='Корзина',exact=True)).to_be_visible()
 assert page.url.startswith(base+'/basket')
 ok('React-карточка и корзина открываются на том же origin')
 page.route('**/api/catalog/products?**',lambda route:route.fulfill(status=503,content_type='application/problem+json',body=json.dumps({'detail':'Каталог временно недоступен. Попробуйте ещё раз.'})))
 page.goto(base);expect(page.get_by_role('alert')).to_contain_text('временно недоступен')
 page.unroute('**/api/catalog/products?**');page.get_by_role('button',name='Повторить',exact=True).click();expect(page.locator('.product-card')).to_have_count(24)
 ok('Имитированный HTTP503: понятная ошибка и успешный повтор')
 def stale(route):
  r=route.fetch();body=r.json();body['stale']=True
  for item in body['products']:item['stockText']='Наличие уточняется'
  route.fulfill(response=r,json=body)
 page.route('**/api/catalog/products?**',stale);page.reload();expect(page.get_by_text('Данные могут быть устаревшими. Уточните цену и наличие перед покупкой.',exact=True)).to_be_visible()
 assert page.locator('.product-card').first.inner_text().find('Наличие уточняется')>=0
 ok('Имитированная устаревшая синхронизация: предупреждение и скрытый точный остаток')
 assert not errors;ok('Ошибок JavaScript нет')
 b.close()
Path('/tmp/mbm-react-smoke-result.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2))
