import contextlib
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from tools.cloudshop import cloudshop_inspect_catalog_fields as inspect


class InspectTests(unittest.TestCase):
    def run_fixture(self, status=200):
        identifier = '6a79a433a99eb8453f176943'
        paths = []
        commands = []
        output = io.StringIO()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reports = root / '.cloudshop-audit'
            reports.mkdir()
            (reports / 'catalog-test.json').write_text(json.dumps({
                'issue_samples': {
                    'invalid_stock_shape': [{'id': identifier}],
                    'invalid_store_prices_shape': [{'id': identifier}],
                },
            }))
            (root / 'postman' / 'assets').mkdir(parents=True)
            (root / 'postman' / 'assets' / 'CloudShop_Local.postman_environment.json').write_text(json.dumps({
                'values': [{'key': 'access_token', 'value': ''}],
            }))

            def fake_run(command, **kwargs):
                commands.append(command)
                self.assertEqual(command[2], 'GET')
                self.assertEqual(command[3], '{{base_url}}/product/' + identifier)
                self.assertNotIn('dummy-token', command)
                env = Path(command[command.index('-e') + 1])
                response = Path(command[command.index('--output') + 1])
                paths.extend([env, response])
                for path in paths:
                    self.assertEqual(path.stat().st_mode & 0o777, 0o600)
                response.write_text(json.dumps({
                    'request': {'token': 'dummy-token'},
                    'response': {'code': status, 'body': json.dumps({
                        'id': identifier, 'sku': 'sample', 'stock': [],
                        'store_prices': [], 'unit': None, 'price': None,
                        'unselected_field': 'private-value',
                    })},
                }))
                return subprocess.CompletedProcess(command, 0, '', '')

            error = None
            with patch.object(inspect, '__file__', str(root / 'tools' / 'cloudshop' / 'inspect.py')), \
                    patch('sys.argv', ['inspect.py']), \
                    patch.object(inspect.shutil, 'which', return_value='/mock/postman'), \
                    patch.object(inspect.getpass, 'getpass', return_value='dummy-token'), \
                    patch.object(inspect.subprocess, 'run', side_effect=fake_run), \
                    patch.object(inspect.time, 'sleep'), contextlib.redirect_stdout(output):
                try:
                    inspect.main()
                except SystemExit as exc:
                    error = str(exc)
            self.assertTrue(all(not path.exists() for path in paths))
        self.assertNotIn('dummy-token', output.getvalue())
        self.assertNotIn('private-value', output.getvalue())
        return commands, output.getvalue(), error

    def test_reports_empty_array_shape_once_and_cleans_private_files(self):
        commands, output, error = self.run_fixture()
        self.assertIsNone(error)
        self.assertEqual(len(commands), 1)
        self.assertIn('"type": "list"', output)
        self.assertIn('"type": "NoneType"', output)

    def test_http_error_with_successful_cli_exit_stops_without_retry(self):
        commands, output, error = self.run_fixture(status=302)
        self.assertEqual(len(commands), 1)
        self.assertIn('Ошибка чтения', error)
        self.assertIn('HTTP: 302', output)
        self.assertNotIn('"sku"', output)
