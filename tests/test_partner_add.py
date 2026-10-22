import importlib.util
from pathlib import Path
import unittest
from unittest import mock

SPEC = importlib.util.spec_from_file_location(
    'partner_add', Path(__file__).resolve().parent.parent / 'scripts' / 'partner_add.py')
partner_add = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(partner_add)


class CollectTest(unittest.TestCase):
    def test_keeps_existing_environment_without_prompting(self):
        env = {
            'MBM_PARTNER_LOGIN': 'turbo', 'MBM_PARTNER_COMPANY': 'СТО', 'MBM_PARTNER_CONTACT': 'Ерлан',
            'MBM_PARTNER_PHONE': '87011234567', 'MBM_PARTNER_PASSWORD': 'synthetic-pass', 'DB_PASSWORD': 'x',
        }
        with mock.patch('builtins.input') as asked, mock.patch('getpass.getpass') as hidden:
            self.assertEqual(partner_add.collect(dict(env)), env)
        asked.assert_not_called()
        hidden.assert_not_called()

    def test_password_is_hidden_and_confirmed(self):
        env = {'MBM_PARTNER_LOGIN': 'turbo', 'MBM_PARTNER_COMPANY': 'СТО', 'MBM_PARTNER_CONTACT': 'Ерлан',
               'MBM_PARTNER_PHONE': '87011234567', 'DB_PASSWORD': 'x'}
        with mock.patch('getpass.getpass', side_effect=['first-password', 'other-password']):
            with self.assertRaises(SystemExit):
                partner_add.collect(dict(env))
        with mock.patch('getpass.getpass', side_effect=['same-password', 'same-password']):
            self.assertEqual(partner_add.collect(dict(env))['MBM_PARTNER_PASSWORD'], 'same-password')

    def test_empty_answer_stops(self):
        with mock.patch('builtins.input', return_value='  '):
            with self.assertRaises(SystemExit):
                partner_add.collect({})


if __name__ == '__main__':
    unittest.main()
