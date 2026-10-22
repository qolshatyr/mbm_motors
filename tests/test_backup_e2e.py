"""End-to-end backup through the production compose database and an encrypted rclone remote.

Needs Docker Compose and rclone; runs in CI with MBM_BACKUP_E2E=1 and is skipped elsewhere.
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import uuid

ROOT = Path(__file__).parents[1]
DEPLOY = ROOT / 'deploy/production'


@unittest.skipUnless(os.environ.get('MBM_BACKUP_E2E') == '1', 'set MBM_BACKUP_E2E=1 (Docker and rclone required)')
class BackupEndToEndTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.dir, True)
        self.site = self.dir / 'site'
        self.site.mkdir()
        for name in ('compose.yml', 'backup.sh', 'restore_check.sh'):
            shutil.copy(DEPLOY / name, self.site / name)
        (self.site / '.env').write_text(
            'SITE_ADDRESS=localhost\nDB_PASSWORD=e2e-test-password\n'
            'MBM_ADMIN_PASSWORD_HASH=$2a$12$abcdef\nBACKUP_REMOTE=mbm-backup:mbm-motors\n')
        self.raw_remote = self.dir / 'remote'
        password = subprocess.run(['rclone', 'obscure', 'e2e-crypt-password'],
                                  capture_output=True, text=True, check=True).stdout.strip()
        config = self.dir / 'rclone.conf'
        config.write_text(
            f'[mbm-backup]\ntype = crypt\nremote = {self.raw_remote}\npassword = {password}\n')
        self.env = {**os.environ, 'RCLONE_CONFIG': str(config),
                    'COMPOSE_PROJECT_NAME': f'mbm-e2e-{uuid.uuid4().hex[:8]}'}
        self.addCleanup(self.compose, 'down', '-v', check=False)
        self.compose('up', '-d', '--wait', 'db')
        self.load_schema()

    def compose(self, *args, check=True, **kwargs):
        return subprocess.run(['docker', 'compose', *args], cwd=self.site, env=self.env,
                              check=check, timeout=180, **kwargs)

    def psql(self, sql):
        self.compose('exec', '-T', 'db', 'psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-U', 'mbm', '-d', 'mbm_motors',
                     input=sql, text=True, capture_output=True)

    def load_schema(self):
        migrations = sorted((ROOT / 'src/main/resources/db/migration').glob('V*.sql'),
                            key=lambda path: int(path.name[1:].split('__')[0]))
        self.psql('\n'.join(path.read_text() for path in migrations))
        rows = ', '.join(f"({rank}, '{path.name[1:].split('__')[0]}', true)"
                         for rank, path in enumerate(migrations, 1))
        self.psql('create table flyway_schema_history (installed_rank int primary key, version varchar(50), '
                  f'success boolean not null); insert into flyway_schema_history values {rows};')
        self.latest = migrations[-1].name[1:].split('__')[0]

    def run_script(self, *args):
        return subprocess.run(['bash', str(self.site / args[0]), *args[1:]], env=self.env,
                              capture_output=True, text=True, timeout=300)

    def test_encrypted_off_server_copy_restores(self):
        backup = self.run_script('backup.sh')
        self.assertEqual(0, backup.returncode, backup.stderr)
        [local] = (self.site / 'backups').glob('mbm_motors-*.sql.gz')
        [stored] = [path for path in self.raw_remote.rglob('*') if path.is_file()]
        self.assertNotIn('mbm_motors', stored.name, 'file name must be encrypted on the remote')
        self.assertNotEqual(local.read_bytes()[:2], stored.read_bytes()[:2], 'content must be encrypted')

        local.unlink()
        check = self.run_script('restore_check.sh', '--remote')
        self.assertEqual(0, check.returncode, check.stdout + check.stderr)
        self.assertIn('Restore check passed', check.stdout)
        self.assertRegex(check.stdout, rf'migration\s+{self.latest}\s+{self.latest}')
        databases = self.compose('exec', '-T', 'db', 'psql', '-At', '-U', 'mbm', '-d', 'postgres',
                                 '-c', 'select datname from pg_database', capture_output=True, text=True).stdout
        self.assertNotIn('restore_check', databases)


if __name__ == '__main__':
    unittest.main()
