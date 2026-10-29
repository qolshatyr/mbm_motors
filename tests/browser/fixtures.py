"""Synthetic catalog, partners and staff requests for the browser scenarios.

Writes ONLY to a database named mbm_test and replaces its catalog, partners and orders.
"""
import uuid

import psycopg

SKLAD = '6967a39f62918c7e760f3747'
CAR_CITY = '64feaaa51c9799143b532fcf'
PRODUCTS = 30
ALFA, BETA, BLOCKED = 'sto-alfa', 'sto-beta', 'sto-blocked'
ALFA_COMPANY = 'СТО «Альфа-Тест»'
ALFA_CONTACT, ALFA_PHONE = 'Тестов Альфа', '+77010000001'
STAFF_ORDERS = 22
XSS_ORDER, UNKNOWN_ORDER, SENT_ORDER = 1, 2, 3   # positions among the seeded staff orders


def product_id(i):
    return f'{i:024x}'


def sku(i):
    return f'MBM-TEST-{i:03}'


def price(i):
    """СКЛАД price. Product 2 has none: «Цена уточняется»."""
    return 0 if i == 2 else 4500 + i * 100


def stock(i):
    """Product 3 is out of stock, product 4 has only two pieces."""
    return {3: 0, 4: 2}.get(i, 10)


def name(i):
    return (f'Колодки тормозные передние, комплект {i:02}' if i <= 15
            else f'Фильтр масляный двигателя {i:02}')


def tags(i):
    brakes = i <= 15
    make, model = ('Hyundai', 'Solaris') if i % 2 else ('Kia', 'Rio')
    return [('Тормозная система' if brakes else 'Фильтры и обслуживание', 'SECTION', None),
            ('Колодки' if brakes else 'Масляные фильтры', 'SUBSECTION', 'Тормозная система' if brakes else 'Фильтры и обслуживание'),
            (make, 'MAKE', None), (model, 'MODEL', make),
            ('Mobis' if i % 3 else 'Hi-Q', 'BRAND', None)]


def connect(dsn):
    connection = psycopg.connect(dsn)
    if connection.info.dbname != 'mbm_test':
        connection.close()
        raise SystemExit('Refusing to modify a database other than mbm_test.')
    return connection


def seed(dsn, password_hash):
    with connect(dsn) as c:
        c.execute('truncate delivery_outbox, local_order_items, local_orders, partners, product_tags, '
                  'product_images, product_offers, products, sync_runs')
        for i in range(1, PRODUCTS + 1):
            pid = product_id(i)
            c.execute("insert into products(id,name,sku,code,unit,imported_at) values (%s,%s,%s,%s,'шт',now())",
                      (pid, name(i), sku(i), f'{900000 + i}'))
            c.execute('insert into product_offers(product_id,store_id,quantity,price) values (%s,%s,%s,%s)',
                      (pid, SKLAD, stock(i), price(i)))
            # Another place with its own price and stock: the portal must ignore it.
            c.execute('insert into product_offers(product_id,store_id,quantity,price) values (%s,%s,50,99999)',
                      (pid, CAR_CITY))
            for position, (tag, kind, parent) in enumerate(tags(i)):
                c.execute('insert into product_tags(product_id,position,name,kind,parent) values (%s,%s,%s,%s,%s)',
                          (pid, position, tag, kind, parent))
        c.execute("insert into sync_runs(id,mode,status,started_at,finished_at,product_count) "
                  "values (%s,'FULL','SUCCESS',now(),now(),%s)", (uuid.uuid4(), PRODUCTS))
        partners = {}
        for login, company, contact, phone, enabled in [
                (ALFA, ALFA_COMPANY, ALFA_CONTACT, ALFA_PHONE, True),
                (BETA, 'ТОО «Бета-Тест»', 'Тестов Бета', '+77010000002', True),
                (BLOCKED, 'ИП «Заблокирован-Тест»', 'Тестов Блок', '+77010000003', False)]:
            partners[login] = uuid.uuid4()
            c.execute('insert into partners(id,login,password_hash,company,contact_name,phone,enabled,created_at) '
                      'values (%s,%s,%s,%s,%s,%s,%s,now())',
                      (partners[login], login, password_hash, company, contact, phone, enabled))
        for n in range(1, STAFF_ORDERS + 1):
            order = uuid.uuid4()
            comment = ('Синтетический комментарий\n<img src=x onerror="window.staffXss=true">\n' + 'ДлинныйТекст' * 60
                       if n == XSS_ORDER else '')
            c.execute('''insert into local_orders(id,idempotency_key,owner_id,request_hash,revision,status,store_id,
                store_name,customer_name,phone,comment,consent_at,created_at,total,total_text,item_count,partner_id,
                announced_at) values (%s,%s,%s,%s,%s,'RECEIVED',%s,'СКЛАД','Тестов Бета','+77010000002',%s,now(),
                now() - %s * interval '1 hour',5000.02,'5 000,02 ₸',2,%s,now())''',
                      (order, uuid.uuid4(), uuid.uuid4(), '0' * 64, '0' * 64, SKLAD, comment, n, partners[BETA]))
            c.execute('''insert into local_order_items(order_id,position,product_id,product_name,sku,unit,quantity,
                price,line_total) values (%s,0,%s,%s,%s,'шт',2,2500.01,5000.02)''',
                      (order, product_id(1), 'Синтетический комплект ' + 'НазваниеБезПробела' * 20, sku(1)))
            if n == UNKNOWN_ORDER:
                c.execute("insert into delivery_outbox(order_id,state,created_at,failure_reason) "
                          "values (%s,'UNKNOWN',now(),'Синтетический таймаут ответа CloudShop')", (order,))
            elif n == SENT_ORDER:
                c.execute("insert into delivery_outbox(order_id,state,created_at,customer_id,sale_id,sale_number) "
                          "values (%s,'SENT',now(),%s,%s,123)", (order, 'b' * 24, 'c' * 24))
            else:
                c.execute("insert into delivery_outbox(order_id,state,created_at) values (%s,'WAITING_CONFIGURATION',now())",
                          (order,))


def staff_order_numbers(dsn):
    """Numbers of the seeded staff orders by their position (1 is the newest)."""
    with connect(dsn) as c:
        rows = c.execute("select number from local_orders where customer_name='Тестов Бета' "
                         "order by created_at desc").fetchall()
    return {position: row[0] for position, row in enumerate(rows, start=1)}
