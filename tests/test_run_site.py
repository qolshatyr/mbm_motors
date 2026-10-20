"""Private staff credential preparation must never put a password in process arguments."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location('run_site', Path(__file__).parents[1] / 'scripts/run_site.py')
launcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(launcher)


class StaffLauncherTest(unittest.TestCase):
    def test_existing_hash_avoids_password_prompt(self):
        env = {'MBM_ADMIN_USERNAME': 'staff', 'MBM_ADMIN_PASSWORD_HASH': 'configured-hash'}
        with patch.object(launcher.getpass, 'getpass') as prompt:
            launcher.configure_admin(env, Path('site.jar'), Path('.'))
        prompt.assert_not_called()
        self.assertEqual('true', env['MBM_ADMIN_ENABLED'])

    def test_invalid_username_is_rejected(self):
        with self.assertRaises(SystemExit):
            launcher.configure_admin({'MBM_ADMIN_USERNAME': 'two words'}, Path('site.jar'), Path('.'))

    def test_short_password_is_rejected(self):
        with patch.object(launcher.getpass, 'getpass', return_value='short'), self.assertRaises(SystemExit):
            launcher.configure_admin({'MBM_ADMIN_USERNAME': 'staff'}, Path('site.jar'), Path('.'))

    def test_unicode_password_over_bcrypt_limit_is_rejected(self):
        with patch.object(launcher.getpass, 'getpass', return_value='я' * 37), self.assertRaises(SystemExit):
            launcher.configure_admin({'MBM_ADMIN_USERNAME': 'staff'}, Path('site.jar'), Path('.'))

    def test_confirmation_must_match(self):
        with patch.object(launcher.getpass, 'getpass', side_effect=['synthetic-password', 'different-password']), self.assertRaises(SystemExit):
            launcher.configure_admin({'MBM_ADMIN_USERNAME': 'staff'}, Path('site.jar'), Path('.'))

    def test_password_uses_only_stdin_and_hash_uses_environment(self):
        password = 'synthetic-password'
        password_hash = '$2a$12$' + 'a' * 53
        archive = Mock()
        archive.namelist.return_value = ['BOOT-INF/lib/spring-security-crypto-test.jar']
        archive.read.return_value = b'fixture'
        env = {'MBM_ADMIN_USERNAME': 'staff'}
        with patch.object(launcher.getpass, 'getpass', return_value=password), patch.object(launcher.zipfile, 'ZipFile') as jar, patch.object(launcher.subprocess, 'run', return_value=Mock(returncode=0, stdout=password_hash)) as run:
            jar.return_value.__enter__.return_value = archive
            launcher.configure_admin(env, Path('site.jar'), Path('.'))
        args, kwargs = run.call_args
        self.assertNotIn(password, str(args))
        self.assertEqual(password, kwargs['input'])
        self.assertEqual(password_hash, env['MBM_ADMIN_PASSWORD_HASH'])
        self.assertNotIn('MBM_ADMIN_PASSWORD', env)


if __name__ == '__main__':
    unittest.main()
