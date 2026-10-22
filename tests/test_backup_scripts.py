"""Backup and restore-check scripts against stand-in docker and rclone commands."""
import gzip
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

DEPLOY = Path(__file__).parents[1] / 'deploy/production'

DOCKER_STUB = r'''#!/usr/bin/env bash
echo "docker $*" >> "$STUB_LOG"
case " $* " in
  *" pg_dump "*)
    echo '-- PostgreSQL database dump'
    echo 'create table products (id int);'
    [ -n "${STUB_DUMP_FAIL:-}" ] && exit 1
    [ -z "${STUB_DUMP_INCOMPLETE:-}" ] && echo '-- PostgreSQL database dump complete'
    exit 0 ;;
  *" -At "*)
    case " $* " in *" -d restore_check "*) echo "$STUB_RESTORED_ROW" ;; *) echo "$STUB_LIVE_ROW" ;; esac
    exit 0 ;;
  *" -d restore_check "*)
    cat > "$STUB_DIR/restored.sql"
    [ -n "${STUB_RESTORE_FAIL:-}" ] && exit 3
    exit 0 ;;
esac
'''

RCLONE_STUB = r'''#!/usr/bin/env bash
echo "rclone $*" >> "$STUB_LOG"
remote_dir="$STUB_DIR/remote"
mkdir -p "$remote_dir"
case "$1" in
  listremotes) echo "mbm-backup: $STUB_REMOTE_TYPE" ;;
  copy)
    [ -n "${STUB_COPY_FAIL:-}" ] && exit 1
    case "$2" in
      mbm-backup:*) cp "$remote_dir/${2##*/}" "$3/" ;;
      *) cp "$2" "$remote_dir/" ;;
    esac ;;
  lsf)
    target="${!#}"
    case "$target" in
      *.sql.gz) [ -f "$remote_dir/${target##*/}" ] && echo "${target##*/}" ;;
      *) ls "$remote_dir" ;;
    esac ;;
esac
exit 0
'''

CURL_STUB = r'''#!/usr/bin/env bash
echo "curl ${!#}" >> "$STUB_LOG"
exit 0
'''

COUNTS = '|'.join(['9'] + ['3'] * 8)


class BackupScriptsTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.dir)
        self.site = self.dir / 'site'
        self.site.mkdir()
        for name in ('backup.sh', 'restore_check.sh'):
            shutil.copy(DEPLOY / name, self.site / name)
        bin_dir = self.dir / 'bin'
        bin_dir.mkdir()
        for name, body in (('docker', DOCKER_STUB), ('rclone', RCLONE_STUB), ('curl', CURL_STUB)):
            path = bin_dir / name
            path.write_text(body)
            path.chmod(0o755)
        self.log = self.dir / 'calls.log'
        self.env = {
            'PATH': f'{bin_dir}:{os.environ["PATH"]}',
            'STUB_LOG': str(self.log),
            'STUB_DIR': str(self.dir),
            'STUB_REMOTE_TYPE': 'crypt',
            'STUB_RESTORED_ROW': COUNTS,
            'STUB_LIVE_ROW': COUNTS,
        }

    def run_script(self, *args, **env):
        return subprocess.run(
            ['bash', str(self.site / args[0]), *args[1:]],
            env={**self.env, **env}, capture_output=True, text=True, timeout=30)

    def calls(self):
        return self.log.read_text() if self.log.exists() else ''

    def backups(self):
        return sorted((self.site / 'backups').glob('mbm_motors-*'))

    def test_local_backup_is_complete_and_private(self):
        result = self.run_script('backup.sh')
        self.assertEqual(0, result.returncode, result.stderr)
        [backup] = self.backups()
        self.assertIn('dump complete', gzip.decompress(backup.read_bytes()).decode())
        self.assertEqual(0o600, backup.stat().st_mode & 0o777)
        self.assertNotIn('rclone', self.calls())

    def test_incomplete_dump_is_rejected_and_removed(self):
        result = self.run_script('backup.sh', STUB_DUMP_INCOMPLETE='1')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('incomplete', result.stderr)
        self.assertEqual([], list((self.site / 'backups').iterdir()))

    def test_failed_dump_is_rejected_and_removed(self):
        result = self.run_script('backup.sh', STUB_DUMP_FAIL='1')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual([], list((self.site / 'backups').iterdir()))

    def test_remote_copy_uploads_verifies_and_prunes(self):
        result = self.run_script('backup.sh', BACKUP_REMOTE='mbm-backup:mbm')
        self.assertEqual(0, result.returncode, result.stderr)
        [backup] = self.backups()
        self.assertTrue((self.dir / 'remote' / backup.name).exists())
        self.assertIn('rclone delete --min-age 30d', self.calls())
        self.assertIn(backup.name, (self.site / 'backups/.last-offsite').read_text())

    def test_remote_settings_are_read_from_env_file_with_dollar_signs(self):
        (self.site / '.env').write_text(
            'MBM_ADMIN_PASSWORD_HASH=$2a$12$abcdef\nBACKUP_REMOTE=mbm-backup:mbm\nBACKUP_REMOTE_KEEP_DAYS=45\n')
        result = self.run_script('backup.sh')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('rclone delete --min-age 45d', self.calls())

    def test_unencrypted_remote_is_refused_but_local_copy_kept(self):
        result = self.run_script('backup.sh', BACKUP_REMOTE='mbm-backup:mbm', STUB_REMOTE_TYPE='s3')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('not crypt', result.stderr)
        self.assertEqual(1, len(self.backups()))
        self.assertNotIn('rclone copy', self.calls())

    def test_unencrypted_remote_can_be_accepted_explicitly(self):
        result = self.run_script(
            'backup.sh', BACKUP_REMOTE='mbm-backup:mbm', STUB_REMOTE_TYPE='s3', BACKUP_REMOTE_ALLOW_PLAIN='true')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('rclone copy', self.calls())

    def test_failed_upload_does_not_prune_remote(self):
        result = self.run_script('backup.sh', BACKUP_REMOTE='mbm-backup:mbm', STUB_COPY_FAIL='1')
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn('rclone delete', self.calls())
        self.assertFalse((self.site / 'backups/.last-offsite').exists())

    def test_short_remote_retention_is_rejected(self):
        result = self.run_script('backup.sh', BACKUP_REMOTE='mbm-backup:mbm', BACKUP_REMOTE_KEEP_DAYS='2')
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn('rclone copy', self.calls())

    def test_heartbeat_reports_success(self):
        result = self.run_script('backup.sh', BACKUP_HEARTBEAT_URL='https://hc-ping.com/abc')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('curl https://hc-ping.com/abc\n', self.calls())

    def test_heartbeat_reports_failure(self):
        result = self.run_script(
            'backup.sh', BACKUP_HEARTBEAT_URL='https://hc-ping.com/abc', STUB_DUMP_INCOMPLETE='1')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('curl https://hc-ping.com/abc/fail', self.calls())

    def test_heartbeat_reports_failed_off_server_copy(self):
        result = self.run_script('backup.sh', BACKUP_HEARTBEAT_URL='https://hc-ping.com/abc',
                                 BACKUP_REMOTE='mbm-backup:mbm', STUB_REMOTE_TYPE='s3')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('curl https://hc-ping.com/abc/fail', self.calls())

    def test_plain_http_heartbeat_is_rejected(self):
        result = self.run_script('backup.sh', BACKUP_HEARTBEAT_URL='http://hc-ping.com/abc')
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn('pg_dump', self.calls())

    def test_restore_check_uses_newest_local_copy_and_drops_database(self):
        backups = self.site / 'backups'
        backups.mkdir()
        for stamp, body in (('20261020-0330', b'old'), ('20261022-0330', b'newest')):
            (backups / f'mbm_motors-{stamp}.sql.gz').write_bytes(gzip.compress(body))
        result = self.run_script('restore_check.sh')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('newest', (self.dir / 'restored.sql').read_text())
        self.assertIn('ON_ERROR_STOP=1', self.calls())
        self.assertIn('drop database if exists restore_check', self.calls().splitlines()[-1])
        self.assertIn('Restore check passed', result.stdout)

    def test_restore_check_from_remote_downloads_newest(self):
        remote = self.dir / 'remote'
        remote.mkdir()
        (remote / 'mbm_motors-20261021-0330.sql.gz').write_bytes(gzip.compress(b'old'))
        (remote / 'mbm_motors-20261022-0330.sql.gz').write_bytes(gzip.compress(b'remote newest'))
        result = self.run_script('restore_check.sh', '--remote', BACKUP_REMOTE='mbm-backup:mbm')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('remote newest', (self.dir / 'restored.sql').read_text())

    def test_failed_restore_fails_and_still_drops_database(self):
        backup = self.dir / 'mbm_motors-20261022-0330.sql.gz'
        backup.write_bytes(gzip.compress(b'broken'))
        result = self.run_script('restore_check.sh', str(backup), STUB_RESTORE_FAIL='1')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('drop database if exists restore_check', self.calls().splitlines()[-1])

    def test_backup_without_migrations_fails(self):
        backup = self.dir / 'mbm_motors-20261022-0330.sql.gz'
        backup.write_bytes(gzip.compress(b'empty'))
        result = self.run_script('restore_check.sh', str(backup), STUB_RESTORED_ROW='|0|0|0|0|0|0|0|0')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('no applied migrations', result.stderr)


if __name__ == '__main__':
    unittest.main()
