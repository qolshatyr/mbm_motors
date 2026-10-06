import contextlib
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from tools.cloudshop import cloudshop_snapshot_catalog as snapshot


class SnapshotTests(unittest.TestCase):
    def test_stock_only_other_fields_and_updated_coverage(self):
        old = {'products': {
            'a': {'stock': {'store': 4}, 'updated_at': 'old', 'price': 3000},
            'b': {'stock': {'store': 2}, 'updated_at': 'old', 'price': 3000},
            'gone': {},
        }}
        new = {'products': {
            'a': {'stock': {'store': 3}, 'updated_at': 'old', 'price': 3000},
            'b': {'stock': {'store': 1}, 'updated_at': 'new', 'price': 3100},
            'added': {},
        }}
        report = snapshot.differences(old, new, {'b'})
        self.assertEqual(report['stock_changed_count'], 2)
        self.assertEqual(report['stock_changed_without_other_fields_count'], 1)
        self.assertEqual(report['stock_changed_with_same_updated_at_count'], 1)
        self.assertEqual(report['stock_changed_ids_missing_from_updated'], ['a'])
        self.assertEqual(report['new_ids'], ['added'])
        self.assertEqual(report['absent_ids'], ['gone'])
        self.assertIsNone(snapshot.differences(old, new)['stock_changed_ids_missing_from_updated'])

    def test_empty_maps_equivalent_and_missing_null_fields_differ(self):
        old = {'products': {'a': {'stock': [], 'store_prices': []}}}
        new = {'products': {'a': {'stock': {}, 'store_prices': {}}}}
        self.assertEqual(snapshot.differences(old, new)['changed_existing_count'], 0)
        new['products']['a']['unit'] = None
        self.assertEqual(snapshot.differences(old, new)['changed_existing_count'], 1)

    def test_capture_short_pages_and_repeated_page_guard(self):
        pages = iter([[{'id': 'a', 'stock': []}], [{'id': 'b'}], []])
        data, duplicates = snapshot.capture(lambda url: next(pages), 3)
        self.assertEqual(set(data), {'a', 'b'})
        self.assertEqual(duplicates, 0)
        with self.assertRaises(ValueError):
            snapshot.capture(lambda url: [{'id': 'a'}], 3)

    def test_private_snapshot_excludes_request_metadata_and_keeps_baseline(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            folder = root / '.cloudshop-audit'
            folder.mkdir()
            baseline = folder / 'snapshot-20000101.json'
            original = json.dumps({'schema_version': 1, 'started_at_utc': '2000-01-01T00:00:00+00:00',
                                   'reached_empty_page': True, 'products': {'a': {'id': 'a', 'stock': []}}})
            baseline.write_text(original)
            (folder / 'snapshot-diff-999999.json').write_text('{}')
            (root / 'postman' / 'assets').mkdir(parents=True)
            (root / 'postman' / 'assets' / 'CloudShop_Local.postman_environment.json').write_text(json.dumps({
                'values': [{'key': 'access_token', 'value': ''}]}))
            pages = iter([[{'id': 'a', 'stock': {'store': 1}}], [], [{'id': 'a'}]])
            paths = []
            def run(command, **kwargs):
                self.assertEqual(command[2], 'GET')
                self.assertNotIn('dummy-token', command)
                env = Path(command[command.index('-e') + 1])
                response = Path(command[command.index('--output') + 1])
                paths.extend([env, response])
                self.assertEqual(env.stat().st_mode & 0o777, 0o600)
                self.assertEqual(response.stat().st_mode & 0o777, 0o600)
                response.write_text(json.dumps({'request': {'token': 'dummy-token'},
                    'response': {'code': 200, 'body': json.dumps(next(pages))}}))
                return subprocess.CompletedProcess(command, 0, '', '')
            with patch.object(snapshot, '__file__', str(root / 'tools' / 'cloudshop' / 'snapshot.py')), \
                    patch('sys.argv', ['snapshot.py']), \
                    patch.object(snapshot.shutil, 'which', return_value='/mock/postman'), \
                    patch.object(snapshot.getpass, 'getpass', return_value='dummy-token'), \
                    patch.object(snapshot.subprocess, 'run', side_effect=run), \
                    patch.object(snapshot.time, 'sleep'), contextlib.redirect_stdout(io.StringIO()):
                snapshot.main()
            self.assertEqual(baseline.read_text(), original)
            self.assertTrue(all(not path.exists() for path in paths))
            new_files = [p for p in folder.iterdir() if p.name not in (baseline.name, 'snapshot-diff-999999.json')]
            self.assertEqual(len(new_files), 2)
            for path in new_files:
                self.assertEqual(path.stat().st_mode & 0o777, 0o600)
                self.assertNotIn('dummy-token', path.read_text())
