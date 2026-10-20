#!/usr/bin/env python3
"""Replace fixtures ONLY in a dedicated mbm_test PostgreSQL database.

Requires psycopg and MBM_TEST_DATABASE_DSN. Never use a working customer database.
"""
import os
import psycopg
import uuid

dsn = os.environ.get('MBM_TEST_DATABASE_DSN')
if not dsn:
    raise SystemExit('Set MBM_TEST_DATABASE_DSN to the dedicated mbm_test database.')
with psycopg.connect(dsn) as c:
 if c.info.dbname != 'mbm_test':
  raise SystemExit('Refusing to modify a database other than mbm_test.')
 for table in ['local_order_items','local_orders','product_images','product_offers','products','sync_runs']:c.execute('delete from '+table)
 for i in range(1,31):
  pid=f'{i:024x}'
  c.execute("insert into products(id,name,sku,code,unit,imported_at) values (%s,%s,%s,%s,'шт',now())",(pid,f'Проверка оформления — комплект тормозных колодок {i:02}',f'CHECKOUT-{i:03}',f'{i:06}'))
  for store,price,qty in [('64feaaa51c9799143b532fcf',3000,5),('698c479d4bb8342bd30e3c33',2500,6),('64feaa7d401f7a28d35cda23',3100,3),('6967a39f62918c7e760f3747',0,62)]:
   c.execute('insert into product_offers(product_id,store_id,quantity,price) values (%s,%s,%s,%s)',(pid,store,qty,price))
 c.execute("update products set unit='' where id='000000000000000000000002'")
 c.execute("update product_offers set price=0 where product_id='000000000000000000000003' and store_id='64feaaa51c9799143b532fcf'")
 c.execute("insert into sync_runs(id,mode,status,started_at,finished_at,product_count) values (%s,'FULL','SUCCESS',now(),now(),30)",(uuid.uuid4(),))
print('mbm_test: synthetic catalog ready')
