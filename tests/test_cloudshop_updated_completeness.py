from datetime import datetime
import unittest

from tools.cloudshop.cloudshop_check_updated_completeness import compare, read_catalog


class UpdatedTests(unittest.TestCase):
    def test_missing_from_both_boundary_and_unreadable_dates(self):
        catalog = {
            'a': {'updated_at': '2026-10-03 01:00:00'},
            'b': {'updated_at': '2026-10-03 02:00:00'},
            'c': {'updated_at': '2026-10-03 03:00:00'},
            'boundary': {'updated_at': '2026-10-03 00:00:00'},
            'unknown': {'updated_at': None},
        }
        result = compare(catalog, {'a': {}}, {'b': {}}, datetime(2026, 10, 3))
        self.assertEqual(result['missing_from_both_updated_reads'], 1)
        self.assertEqual(result['missing_samples'][0]['id'], 'c')
        self.assertFalse(result['updated_id_sets_equal'])
        self.assertEqual(result['catalog_dates_equal_start'], 1)
        self.assertEqual(result['catalog_dates_unreadable'], 1)

    def test_short_pages_continue_to_empty_and_repeated_page_stops(self):
        urls = []
        pages = iter([[{'id': 'a'}], [{'id': 'b'}], []])
        def get(url):
            urls.append(url)
            return next(pages)
        self.assertEqual(set(read_catalog(get, 3)), {'a', 'b'})
        self.assertTrue(urls[1].endswith('offset=1'))
        with self.assertRaises(ValueError):
            read_catalog(lambda url: [{'id': 'a'}], 3)
