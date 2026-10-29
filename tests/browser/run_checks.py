#!/usr/bin/env python3
"""Run the browser scenarios against an isolated copy of the portal.

Starts the release JAR on a free local port with the dedicated mbm_test database, without a
CloudShop token, catalog sync, order delivery or CloudShop customer cards, fills the database with
synthetic fixtures before each scenario and stops the server afterwards. Partner and staff
passwords are random for every run and are never printed.

  MBM_TEST_DATABASE_DSN=postgresql://mbm@127.0.0.1:55432/mbm_test \\
    python tests/browser/run_checks.py [catalog] [cart_checkout] [staff]

Needs Python Playwright with Chromium, psycopg and bcrypt (see docs/Browser_Checks.md).
Do not run it together with ./mvnw verify on the same database.
"""
import argparse
import os
import secrets
import socket
import subprocess
import sys
import tempfile
import time
import urllib.request
import zipfile
from pathlib import Path

import bcrypt
import psycopg

import fixtures

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SCENARIOS = ('catalog', 'cart_checkout', 'staff')
STAFF_USERNAME = 'staff-test'


def jdbc(dsn):
    params = psycopg.conninfo.conninfo_to_dict(dsn)
    url = f"jdbc:postgresql://{params.get('host', '127.0.0.1')}:{params.get('port', 5432)}/{params['dbname']}"
    return url, params.get('user', ''), params.get('password', '')


def free_port():
    with socket.socket() as s:
        s.bind(('127.0.0.1', 0))
        return s.getsockname()[1]


def wait_up(url, server, log):
    for _ in range(120):
        if server.poll() is not None:
            raise SystemExit(f'The server stopped during startup, see {log}')
        try:
            with urllib.request.urlopen(url + '/actuator/health', timeout=2) as response:
                if b'"UP"' in response.read():
                    return
        except OSError:
            pass
        time.sleep(1)
    raise SystemExit(f'The server did not start in 2 minutes, see {log}')


def main():
    parser = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    parser.add_argument('scenarios', nargs='*', metavar='scenario', help=', '.join(SCENARIOS) + ' (default: all)')
    parser.add_argument('--jar', type=Path, default=ROOT / 'target/mbm-motors-0.0.1-SNAPSHOT.jar')
    parser.add_argument('--out', type=Path, default=Path(tempfile.gettempdir()) / 'mbm-browser')
    args = parser.parse_args()
    args.scenarios = args.scenarios or list(SCENARIOS)
    unknown = set(args.scenarios) - set(SCENARIOS)
    if unknown:
        parser.error('unknown scenario: ' + ', '.join(sorted(unknown)))
    dsn = os.environ.get('MBM_TEST_DATABASE_DSN')
    if not dsn:
        raise SystemExit('Set MBM_TEST_DATABASE_DSN to the dedicated mbm_test database.')
    fixtures.connect(dsn).close()
    with zipfile.ZipFile(args.jar) as jar:
        if 'BOOT-INF/classes/static/frontend/index.html' not in jar.namelist():
            raise SystemExit('The JAR has no interface: build it with python3 scripts/build_site.py.')
    args.out.mkdir(parents=True, exist_ok=True)
    partner_password, staff_password = secrets.token_urlsafe(18), secrets.token_urlsafe(18)
    partner_hash = bcrypt.hashpw(partner_password.encode(), bcrypt.gensalt(10)).decode()
    db_url, db_user, db_password = jdbc(dsn)
    port = free_port()
    base = f'http://127.0.0.1:{port}'
    # Only what the server needs: no inherited CloudShop token or Telegram settings.
    server_env = {key: os.environ[key] for key in ('PATH', 'HOME', 'JAVA_HOME', 'LANG') if key in os.environ}
    server_env.update(DB_URL=db_url, DB_USER=db_user, DB_PASSWORD=db_password, CLOUDSHOP_TOKEN='',
                      MBM_DELIVERY_ENABLED='false', MBM_ADMIN_ENABLED='true', MBM_ADMIN_USERNAME=STAFF_USERNAME,
                      MBM_ADMIN_PASSWORD_HASH=bcrypt.hashpw(staff_password.encode(), bcrypt.gensalt(10)).decode(),
                      MBM_ADMIN_CREATE_CUSTOMERS='false', MBM_ALERT_TELEGRAM_BOT_TOKEN='', MBM_ALERT_TELEGRAM_CHAT_ID='')
    log = args.out / 'server.log'
    with open(log, 'w') as output:
        server = subprocess.Popen(['java', '-jar', str(args.jar), '--frontend.enabled=true', f'--server.port={port}',
                                   '--catalog.sync.enabled=false', '--delivery.enabled=false'],
                                  env=server_env, stdout=output, stderr=subprocess.STDOUT)
    failed = []
    try:
        wait_up(base, server, log)
        print(f'Test server {base} on mbm_test, log {log}', flush=True)
        scenario_env = dict(os.environ, MBM_BROWSER_URL=base, MBM_BROWSER_OUT=str(args.out),
                            MBM_BROWSER_PARTNER_PASSWORD=partner_password,
                            MBM_BROWSER_STAFF_USERNAME=STAFF_USERNAME, MBM_BROWSER_STAFF_PASSWORD=staff_password)
        for name in args.scenarios:
            fixtures.seed(dsn, partner_hash)
            print(f'--- {name}', flush=True)
            if subprocess.run([sys.executable, str(HERE / f'check_{name}.py')], env=scenario_env).returncode:
                failed.append(name)
    finally:
        server.terminate()
        server.wait(30)
    print(f'Screenshots: {args.out}')
    if failed:
        raise SystemExit('FAILED: ' + ', '.join(failed))
    print('All browser scenarios passed: ' + ', '.join(args.scenarios))


if __name__ == '__main__':
    main()
