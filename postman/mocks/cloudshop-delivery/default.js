// Generated from cloudshop-delivery.openapi.json, narrowed to synthetic local tests.
// Contract stand-in; not an owner-certified CloudShop implementation.
const http = require('node:http');
const assert = require('node:assert/strict');
const customers = [], sales = [];
let fault = '';
const id = n => n.toString(16).padStart(24, '0');
const reply = (res, code, body) => {
  res.writeHead(code, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(body));
};
http.createServer(async (req, res) => {
  const path = new URL(req.url, 'http://localhost').pathname;
  // Control routes exist only in this local fixture, never in the upstream contract.
  if (req.method === 'GET' && path === '/__test__/state') return reply(res, 200, { customers, sales });
  let raw = '';
  for await (const chunk of req) {
    raw += chunk;
    if (raw.length > 65536) return reply(res, 413, { error: 'Fixture body too large' });
  }
  let body;
  try { body = raw ? JSON.parse(raw) : {}; }
  catch { return reply(res, 400, { error: 'Invalid JSON' }); }
  if (req.method === 'POST' && path === '/__test__/reset') {
    customers.length = 0; sales.length = 0; fault = body.fault || '';
    return reply(res, 200, { reset: true });
  }
  if (req.method !== 'POST' || !['/api/v4/customers', '/api/v4/sales'].includes(path)) return reply(res, 501, { error: 'Unmodelled dependency call' });
  if (req.headers['x-cloudshop-api-access-token'] !== 'synthetic-local-only') return reply(res, 403, { error: 'Synthetic credential required' });
  try {
    if (path.endsWith('/customers')) {
      assert.equal(body.type, 'person'); assert.equal(body.name, 'Synthetic Buyer');
      assert.deepEqual(body.phones, ['+77000000000']);
      assert.deepEqual(Object.keys(body).sort(), ['name', 'phones', 'type']);
      const record = { id: id(100 + customers.length), ...body }; customers.push(record);
      if (fault === 'customer-lost') return req.socket.destroy();
      return reply(res, 201, { id: record.id });
    }
    assert.equal(body.status, false); assert.equal(body.store_id, 'aaaaaaaaaaaaaaaaaaaaaaaa');
    assert(customers.some(x => x.id === body.customer_id));
    assert.equal(body.date, '2026-10-12 00:00:00'); assert.equal(body.discount_percent, 0); assert.equal(body.discount_sum, 0);
    assert.equal('payment_details' in body, false); assert.match(body.comment, /MBM-[0-9a-f-]{36}/);
    assert.deepEqual(body.products, [{ product_id: 'dddddddddddddddddddddddd', quantity: 2, unit: 'шт', price: 2500.01, discount_percent: 0 }]);
    const record = { id: id(200 + sales.length), number: sales.length + 1, ...body }; sales.push(record);
    if (fault === 'sale-lost') return req.socket.destroy();
    return reply(res, 200, { id: record.id, number: record.number });
  } catch { return reply(res, 422, { error: 'Synthetic contract assertion failed' }); }
}).listen(Number(process.env.PORT || 4900), '127.0.0.1');
