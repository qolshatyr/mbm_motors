#!/usr/bin/env python3
"""Build the React + Spring Boot release JAR. Does not deploy or contact CloudShop."""
import argparse
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--skip-install', action='store_true', help='Use existing frontend node_modules')
    arguments = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    if not arguments.skip_install:
        subprocess.run(['npm', 'ci'], cwd=root / 'frontend', check=True)
    subprocess.run(['npm', 'run', 'build', '--', '--base=/frontend/'], cwd=root / 'frontend', check=True)
    subprocess.run([str(root / 'mvnw'), 'clean', 'verify', '-Prelease-site'], cwd=root, check=True)
    print('Release JAR: target/mbm-motors-0.0.1-SNAPSHOT.jar')
    print('Run with local DB configuration and --frontend.enabled=true; see docs/Development_Guide.md.')


if __name__ == '__main__':
    main()
