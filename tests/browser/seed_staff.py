#!/usr/bin/env python3
"""Add synthetic staff fixtures ONLY to the dedicated mbm_test database."""
import os
import uuid
import psycopg

with psycopg.connect(os.environ['MBM_TEST_DATABASE_DSN']) as c:
    if c.info.dbname != 'mbm_test':
        raise SystemExit('Refusing to modify a database other than mbm_test.')
    has_outbox = c.execute("select to_regclass('delivery_outbox')").fetchone()[0] is not None
    for i in range(25):
        oid = uuid.uuid5(uuid.NAMESPACE_URL, f'mbm-staff-fixture-{i}')
        name = 'Синтетический покупатель'
        comment = 'Синтетический комментарий\n<img src=x onerror="window.staffXss=true">\n' + 'ДлинныйТекст' * 60 if i == 24 else ''
        c.execute('''insert into local_orders(id,idempotency_key,owner_id,request_hash,revision,status,
          store_id,store_name,customer_name,phone,comment,consent_at,created_at,total,total_text,item_count)
          values (%s,%s,%s,%s,%s,'RECEIVED',%s,%s,%s,%s,%s,now(),now() + %s * interval '1 second',5000.02,'5 000,02 ₸',2)
          on conflict(id) do nothing''',
          (oid,oid,uuid.uuid5(uuid.NAMESPACE_URL,'mbm-staff-owner'),'0'*64,'0'*64,
           '64feaaa51c9799143b532fcf','Синтетическая точка',name,'+70000000000',comment,i))
        if has_outbox and i != 0:
            c.execute("insert into delivery_outbox(order_id,state,created_at) values (%s,'WAITING_CONFIGURATION',now()) on conflict do nothing",(oid,))
        c.execute('''insert into local_order_items(order_id,position,product_id,product_name,sku,unit,quantity,price,line_total)
          values (%s,0,%s,%s,%s,'шт',2,2500.01,5000.02) on conflict do nothing''',
          (oid,'000000000000000000000001','Синтетический комплект '+ 'НазваниеБезПробела' * 20,'TEST-STAFF'))
print('mbm_test: 25 synthetic staff requests ready')
