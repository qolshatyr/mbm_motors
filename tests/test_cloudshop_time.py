import contextlib
from datetime import datetime
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from zoneinfo import ZoneInfo

from tools.cloudshop import cloudshop_test_sale as sale
from tools.cloudshop.cloudshop_verify_time_test import compare_time, MARKER


class TimeTests(unittest.TestCase):
    def test_utc_date_displays_same_instant_in_almaty(self):
        submitted = datetime(2026, 10, 3, 14, 35, 33, tzinfo=ZoneInfo('UTC'))
        result = compare_time('2026-10-03T14:35:33+00:00', submitted)
        self.assertEqual(result['difference_seconds'], 0)
        self.assertEqual(result['api_as_almaty'], '2026-10-03T19:35:33+05:00')
        with self.assertRaises(ValueError):
            compare_time('2026-10-03T14:35:33', submitted)

    def test_one_new_marker_leaves_original_and_blocks_repeat(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'postman' / 'assets').mkdir(parents=True)
            (root / 'postman' / 'assets' / 'CloudShop_Local.postman_environment.json').write_text(json.dumps({
                'values': [{'key': 'access_token', 'value': ''}],
            }))
            ledgers = root / '.cloudshop-test-sales'
            ledgers.mkdir()
            original = ledgers / (sale.MARKER + '.json')
            original.write_text('{"state":"SENT"}')
            calls = []
            def run(command, **kwargs):
                calls.append(command)
                payload = json.loads(command[command.index('--body') + 1])
                self.assertFalse(payload['status'])
                self.assertTrue(payload['comment'].startswith(MARKER))
                self.assertNotIn('--retry', command)
                self.assertNotIn('dummy-token', command)
                record_path = ledgers / (MARKER + '.json')
                self.assertEqual(json.loads(record_path.read_text())['state'], 'UNKNOWN')
                self.assertEqual(record_path.stat().st_mode & 0o777, 0o600)
                return subprocess.CompletedProcess(command, 0,
                    'SALE_HTTP_STATUS:200\nSALE_RESPONSE:{"id":"6ac112bf4584ee042a3a7173","number":35237}\n', '')
            argv = ['sale.py', '--send', '--time-test', '--customer-id', '6ac110842dd87801040fed88', '--unit', 'шт.']
            with patch.object(sale, '__file__', str(root / 'tools' / 'cloudshop' / 'sale.py')), patch('sys.argv', argv), \
                    patch.object(sale.shutil, 'which', return_value='/mock/postman'), \
                    patch.object(sale.getpass, 'getpass', return_value='dummy-token'), \
                    patch.object(sale.subprocess, 'run', side_effect=run), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(sale.main(), 0)
                with self.assertRaises(SystemExit):
                    sale.main()
            self.assertEqual(len(calls), 1)
            self.assertEqual(original.read_text(), '{"state":"SENT"}')
            self.assertEqual(json.loads((ledgers / (MARKER + '.json')).read_text())['state'], 'SENT')
