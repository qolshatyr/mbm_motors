import contextlib
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from tools.cloudshop import cloudshop_audit_catalog as audit


CAR_CITY = '64feaaa51c9799143b532fcf'
WAREHOUSE = '6967a39f62918c7e760f3747'


def product(identifier, **fields):
    return {'id': identifier, 'sku': 'SKU-' + identifier, 'unit': 'шт.',
            'price': 3000, 'stock': {CAR_CITY: 4}, 'img': [], **fields}


class AuditTests(unittest.TestCase):
    def test_empty_stock_array_means_no_store_balances(self):
        state = audit.Audit()
        state.add_page([product('one', stock=[], in_stock=False)])
        self.assertEqual(state.counts['invalid_stock_shape'], 0)
        self.assertEqual(state.counts['empty_stock_array'], 1)
        for stats in state.stores.values():
            self.assertEqual(stats['products_with_zero_stock'], 1)
            self.assertEqual(stats['invalid_stock'], 0)

    def test_empty_store_prices_array_uses_catalog_price_including_zero(self):
        state = audit.Audit()
        state.add_page([product('one', store_prices=[], price=0)])
        self.assertEqual(state.counts['invalid_store_prices_shape'], 0)
        self.assertEqual(state.counts['empty_store_prices_array'], 1)
        for stats in state.stores.values():
            self.assertEqual(stats['catalog_price_used'], 1)
            self.assertEqual(stats['zero_price'], 1)
            self.assertEqual(stats['invalid_price'], 0)

    def test_nonempty_arrays_remain_invalid_and_null_price_is_not_zero(self):
        state = audit.Audit()
        state.add_page([product('one', stock=[1], store_prices=[3000]),
                        product('two', price=None, store_prices={CAR_CITY: 3500})])
        self.assertEqual(state.counts['invalid_stock_shape'], 1)
        self.assertEqual(state.counts['invalid_store_prices_shape'], 1)
        self.assertEqual(state.stores['Car City']['positive_price'], 1)
        self.assertEqual(state.stores['БАКОРДА']['invalid_price'], 2)
        self.assertEqual(state.stores['БАКОРДА']['zero_price'], 0)

    def test_zero_store_price_is_not_replaced_by_catalog_price(self):
        state = audit.Audit()
        state.add_page([product('one', stock={WAREHOUSE: 62}, store_prices={WAREHOUSE: 0})])
        self.assertEqual(state.stores['СКЛАД']['positive_stock_with_zero_price'], 1)
        self.assertEqual(state.stores['СКЛАД']['positive_price'], 0)
        self.assertEqual(state.stores['Car City']['positive_price'], 1)

    def test_duplicate_ids_count_once_but_same_sku_can_have_multiple_products(self):
        state = audit.Audit()
        state.add_page([product('one', sku='same'), product('one', sku='same'), product('two', sku='same')])
        self.assertEqual(state.counts['unique_products'], 2)
        self.assertEqual(state.counts['repeated_id_rows'], 1)
        self.assertEqual(state.summary()['duplicate_sku_groups'], 1)
        self.assertEqual(state.summary()['duplicate_sku_product_count'], 2)

    def test_missing_fields_invalid_numbers_and_image_links(self):
        state = audit.Audit()
        state.add_page([
            product('one', unit='', stock=None, price='NaN', img=None),
            product('two', unit=None, price=True, img=['']),
            product('three', img=['https://example.com/photo']),
        ])
        self.assertEqual(state.counts['missing_unit'], 2)
        self.assertEqual(state.counts['without_image_links'], 2)
        self.assertEqual(state.counts['with_image_links'], 1)
        self.assertEqual(state.stores['Car City']['invalid_stock'], 1)
        self.assertEqual(state.stores['Car City']['invalid_price'], 2)
        self.assertIsNone(audit.number(float('inf')))

    def test_invalid_page_does_not_partially_change_counts(self):
        state = audit.Audit()
        with self.assertRaises(ValueError):
            state.add_page([product('one'), {'id': None}])
        self.assertEqual(len(state.ids), 0)

    def run_fixture(self, pages, statuses=None):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'postman' / 'assets').mkdir(parents=True)
            (root / 'postman' / 'assets' / 'CloudShop_Local.postman_environment.json').write_text(json.dumps({
                'values': [{'key': 'access_token', 'value': ''}],
            }))
            commands = []
            environment_paths = []

            def fake_run(command, **kwargs):
                commands.append(command)
                path = Path(command[command.index('-e') + 1])
                environment_paths.append(path)
                self.assertEqual(path.stat().st_mode & 0o777, 0o600)
                self.assertNotIn('dummy-token', command)
                self.assertEqual(command[2], 'GET')
                response_path = Path(command[command.index('--output') + 1])
                self.assertEqual(response_path.stat().st_mode & 0o777, 0o600)
                body = json.dumps(pages[len(commands) - 1])
                status = statuses[len(commands) - 1] if statuses else 200
                response_path.write_text(json.dumps({
                    'request': {'header': [{'key': 'secret', 'value': 'dummy-token'}]},
                    'response': {'code': status, 'body': body},
                }))
                return subprocess.CompletedProcess(command, 0, json.dumps(pages[len(commands) - 1]), '')

            with patch.object(audit, '__file__', str(root / 'tools' / 'cloudshop' / 'audit.py')), \
                    patch('sys.argv', ['audit.py']), \
                    patch.object(audit.shutil, 'which', return_value='/mock/postman'), \
                    patch.object(audit.getpass, 'getpass', return_value='dummy-token'), \
                    patch.object(audit.subprocess, 'run', side_effect=fake_run), \
                    patch.object(audit.time, 'sleep'), contextlib.redirect_stdout(io.StringIO()):
                code = audit.main()
            report = json.loads(next((root / '.cloudshop-audit').glob('*.json')).read_text())
            self.assertNotIn('dummy-token', json.dumps(report))
            self.assertTrue(all(not p.exists() for p in environment_paths))
            return code, commands, report

    def test_short_pages_continue_and_offsets_use_actual_counts(self):
        code, commands, report = self.run_fixture([[product('one')], [product('two')], []])
        self.assertEqual(code, 0)
        self.assertTrue(report['reached_empty_page'])
        self.assertEqual(report['counts']['unique_products'], 2)
        for command, offset in zip(commands, [0, 1, 2]):
            self.assertTrue(command[3].endswith('offset=' + str(offset)))

    def test_repeated_nonempty_page_stops_as_incomplete(self):
        code, commands, report = self.run_fixture([[product('one')], [product('one')]])
        self.assertEqual(code, 1)
        self.assertEqual(len(commands), 2)
        self.assertFalse(report['reached_empty_page'])
        self.assertEqual(report['counts']['unique_products'], 1)

    def test_http_redirect_with_array_and_zero_exit_is_not_end_of_catalog(self):
        code, commands, report = self.run_fixture([[]], statuses=[302])
        self.assertEqual(code, 1)
        self.assertFalse(report['reached_empty_page'])
        self.assertIn('302', report['termination_reason'])


if __name__ == '__main__':
    unittest.main()
