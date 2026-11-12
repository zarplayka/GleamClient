"""Установка отображаемой роли Orion из консоли сервера."""

import argparse
import sqlite3
from contextlib import closing
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--db', type=Path, required=True)
    parser.add_argument('username')
    parser.add_argument('role', choices=('member', 'admin', 'owner', 'media'))
    args = parser.parse_args()
    uri = args.db.resolve().as_uri() + '?mode=rw'
    with closing(sqlite3.connect(uri, uri=True, timeout=10)) as db, db:
        columns = {column[1] for column in db.execute('PRAGMA table_info(users)')}
        if 'is_media' in columns:
            result = db.execute('UPDATE users SET role=?,is_media=? WHERE username_key=?',
                                ('member' if args.role == 'media' else args.role, args.role == 'media',
                                 args.username.lower()))
        elif args.role == 'media':
            parser.exit(1, 'Сначала запусти обновлённый API для миграции базы\n')
        else:
            result = db.execute('UPDATE users SET role=? WHERE username_key=?',
                                (args.role, args.username.lower()))
        if result.rowcount != 1:
            parser.exit(1, 'Аккаунт Orion не найден\n')
    print(f'{args.username}: {args.role}')


if __name__ == '__main__':
    main()
