"""Установка отображаемой роли Orion из консоли сервера."""

import argparse
import sqlite3
from contextlib import closing
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--db', type=Path, required=True)
    parser.add_argument('username')
    parser.add_argument('role', choices=('member', 'admin', 'owner'))
    args = parser.parse_args()
    uri = args.db.resolve().as_uri() + '?mode=rw'
    with closing(sqlite3.connect(uri, uri=True, timeout=10)) as db, db:
        result = db.execute('UPDATE users SET role=? WHERE username_key=?',
                            (args.role, args.username.lower()))
        if result.rowcount != 1:
            parser.exit(1, 'Аккаунт Orion не найден\n')
    print(f'{args.username}: {args.role}')


if __name__ == '__main__':
    main()
