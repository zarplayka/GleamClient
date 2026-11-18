"""FunPay stock, order reconciliation and creator payouts."""
import calendar
import hashlib
import hmac
import os
import re
import secrets
import time
from datetime import datetime, timezone
from pathlib import Path

from aiohttp import web


PLANS = {
    'month': {'months': 1, 'amount_kopecks': 2900},
    'quarter': {'months': 3, 'amount_kopecks': 6700},
    'year': {'months': 12, 'amount_kopecks': 19900},
    'lifetime': {'months': 0, 'amount_kopecks': 39900},
}
PERMANENT = 253402300799
MIN_LOT_PRICE = 100
KEY = re.compile(r'GLP-[A-F0-9]{32}')
CODE = re.compile(r'[A-Z0-9][A-Z0-9_-]{2,23}')
ORDER = re.compile(r'[A-Z0-9]{1,32}')


def initialize(db):
    db.executescript('''
        CREATE TABLE IF NOT EXISTS funpay_promos (
            code TEXT PRIMARY KEY, creator_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
            creator_name TEXT NOT NULL, share_percent INTEGER NOT NULL CHECK(share_percent BETWEEN 1 AND 100),
            discount_percent INTEGER NOT NULL DEFAULT 0 CHECK(discount_percent BETWEEN 0 AND 99)
        );
        CREATE TABLE IF NOT EXISTS funpay_lots (
            id TEXT PRIMARY KEY, promo TEXT NOT NULL REFERENCES funpay_promos(code), plan TEXT NOT NULL,
            price INTEGER NOT NULL, quantity INTEGER NOT NULL, state TEXT NOT NULL DEFAULT 'queued',
            offer_id INTEGER UNIQUE, UNIQUE(promo, plan)
        );
        CREATE TABLE IF NOT EXISTS funpay_sales (
            id TEXT PRIMARY KEY, lot_id TEXT NOT NULL REFERENCES funpay_lots(id), status TEXT NOT NULL,
            amount INTEGER NOT NULL, share INTEGER NOT NULL, created_at INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS funpay_keys (
            hash TEXT PRIMARY KEY, lot_id TEXT NOT NULL REFERENCES funpay_lots(id), stock_secret TEXT,
            order_id TEXT REFERENCES funpay_sales(id), redeemed_by INTEGER REFERENCES users(id) ON DELETE SET NULL,
            redeemed_at INTEGER, base_at_redemption INTEGER
        );
        CREATE INDEX IF NOT EXISTS funpay_keys_lot ON funpay_keys(lot_id);
        CREATE INDEX IF NOT EXISTS funpay_keys_order ON funpay_keys(order_id);
        CREATE INDEX IF NOT EXISTS funpay_keys_buyer ON funpay_keys(redeemed_by, redeemed_at);
        CREATE INDEX IF NOT EXISTS funpay_sales_lot ON funpay_sales(lot_id);
        CREATE INDEX IF NOT EXISTS funpay_promos_creator ON funpay_promos(creator_id);
        CREATE TABLE IF NOT EXISTS funpay_payouts (
            id INTEGER PRIMARY KEY, promo TEXT NOT NULL REFERENCES funpay_promos(code), amount INTEGER NOT NULL,
            created_at INTEGER NOT NULL, recorded_by INTEGER REFERENCES users(id) ON DELETE SET NULL
        );
        CREATE INDEX IF NOT EXISTS funpay_payouts_promo ON funpay_payouts(promo);
    ''')
    db.execute('BEGIN IMMEDIATE')
    if not any(column['name'] == 'discount_percent' for column in db.execute('PRAGMA table_info(funpay_promos)')):
        db.execute('''ALTER TABLE funpay_promos ADD COLUMN discount_percent INTEGER NOT NULL DEFAULT 0
                      CHECK(discount_percent BETWEEN 0 AND 99)''')


def key_hash(value):
    return hashlib.sha256(value.encode('ascii')).hexdigest()


def plus_expiry(db, user_id, base):
    rows = db.execute('''SELECT k.redeemed_at,k.base_at_redemption,l.plan FROM funpay_keys k
        JOIN funpay_lots l ON l.id=k.lot_id LEFT JOIN funpay_sales s ON s.id=k.order_id
        WHERE k.redeemed_by=? AND (s.status IN ('paid','closed','review','adjusted')
            OR (k.order_id IS NULL AND l.state='linked' AND k.stock_secret IS NULL))
        ORDER BY k.redeemed_at,k.rowid''', (user_id,))
    expiry = 0
    for row in rows:
        months = PLANS[row['plan']]['months']
        if not months or (expiry or 0) >= PERMANENT:
            return PERMANENT
        start_at = max(expiry, row['redeemed_at'], row['base_at_redemption'] or 0)
        if start_at >= PERMANENT:
            continue
        start = datetime.fromtimestamp(start_at, timezone.utc)
        year, month = divmod(start.year * 12 + start.month - 1 + months, 12)
        if year > 9999:
            return PERMANENT
        expiry = int(start.replace(year=year, month=month + 1,
                                   day=min(start.day, calendar.monthrange(year, month + 1)[1])).timestamp())
    return max(base or 0, expiry) or None


def promo_summary(db, promo):
    code = promo['code']
    totals = db.execute('''SELECT
        COUNT(CASE WHEN s.status IN ('paid','closed','review','adjusted') THEN 1 END) AS sales,
        COALESCE(SUM(CASE WHEN s.status='paid' THEN s.share END),0) AS pending,
        COALESCE(SUM(CASE WHEN s.status IN ('closed','adjusted') THEN s.share END),0) AS confirmed
        FROM funpay_sales s JOIN funpay_lots l ON l.id=s.lot_id WHERE l.promo=?''', (code,)).fetchone()
    payouts = db.execute('SELECT COALESCE(SUM(amount),0),COALESCE(MAX(id),0) FROM funpay_payouts WHERE promo=?',
                         (code,)).fetchone()
    lots = db.execute('''SELECT plan,price,quantity,state,offer_id,
        (SELECT COUNT(*) FROM funpay_keys k WHERE k.lot_id=l.id AND k.order_id IS NULL) AS remaining
        FROM funpay_lots l WHERE promo=? ORDER BY price''', (code,))
    balance = totals['confirmed'] - payouts[0]
    return {'code': code, 'share_percent': promo['share_percent'], 'discount_percent': promo['discount_percent'],
            'sales': totals['sales'],
            'pending': totals['pending'], 'available': max(0, balance), 'adjustment': min(0, balance),
            'paid': payouts[0], 'payout_version': payouts[1], 'lots': [dict(row) for row in lots]}


def integer(data, name, low, high):
    value = data.get(name)
    return type(value) is int and low <= value <= high


def add_lot(db, code, plan, price, quantity, lot_id=None):
    if lot_id:
        db.execute('DELETE FROM funpay_keys WHERE lot_id=? AND order_id IS NULL', (lot_id,))
        db.execute("UPDATE funpay_lots SET price=?,quantity=?,state='queued' WHERE id=?", (price, quantity, lot_id))
    else:
        lot_id = secrets.token_hex(5)
        db.execute('INSERT INTO funpay_lots(id,promo,plan,price,quantity) VALUES(?,?,?,?,?)',
                   (lot_id, code, plan, price, quantity))
    keys = ['GLP-' + secrets.token_hex(16).upper() for _ in range(quantity)]
    db.executemany('INSERT INTO funpay_keys(hash,lot_id,stock_secret) VALUES(?,?,?)',
                   ((key_hash(key), lot_id, key) for key in keys))


def discounted_price(base, discount):
    return (base * (100 - discount) + 50) // 100


def routes(connect, session, auth_csrf, limit, json_body, error, plans=PLANS):
    token_path = os.environ.get('GLEAM_FUNPAY_TOKEN_FILE')
    token = Path(token_path).read_text(encoding='utf-8').strip() if token_path else ''
    prices = {plan: plans[plan]['amount_kopecks'] for plan in PLANS}
    cheapest = min(prices.values())
    max_discount = max((percent for percent in range(100)
                        if discounted_price(cheapest, percent) >= MIN_LOT_PRICE), default=0)

    def worker(request):
        provided = request.headers.get('Authorization', '')
        return len(token) >= 32 and hmac.compare_digest(provided.encode(), ('Bearer ' + token).encode())

    async def profile(request):
        user = session(request)
        if not user:
            return error('auth', 401)
        with connect() as db:
            promos = db.execute('SELECT * FROM funpay_promos WHERE creator_id=? ORDER BY code', (user['id'],))
            result = []
            for promo in promos:
                summary = promo_summary(db, promo)
                summary['orders'] = [dict(row) for row in db.execute('''SELECT l.plan,s.status,s.share,s.created_at
                    FROM funpay_sales s JOIN funpay_lots l ON l.id=s.lot_id
                    WHERE l.promo=? ORDER BY s.created_at DESC,s.rowid DESC LIMIT 30''', (promo['code'],))]
                result.append(summary)
        return web.json_response({'promos': result})

    async def redeem(request):
        user = auth_csrf(request)
        if not user:
            return error('auth', 401)
        if not limit(request, 'funpay-redeem', 20, 60):
            return error('rate', 429)
        data = await json_body(request)
        key = str(data.get('key', '')).strip().upper()
        if not KEY.fullmatch(key):
            return error('funpay_key_invalid')
        with connect() as db:
            db.execute('BEGIN IMMEDIATE')
            row = db.execute('''SELECT k.hash,k.redeemed_at,k.redeemed_by,s.status,
                    (l.state='linked' AND k.stock_secret IS NULL) AS published
                FROM funpay_keys k JOIN funpay_lots l ON l.id=k.lot_id
                LEFT JOIN funpay_sales s ON s.id=k.order_id WHERE k.hash=?''',
                (key_hash(key),)).fetchone()
            if not row:
                return error('funpay_key_invalid')
            if row['status'] is None and not row['published']:
                return error('funpay_key_pending', 409)
            if row['status'] is not None and row['status'] not in ('paid', 'closed', 'adjusted'):
                return error('funpay_key_unavailable', 409)
            if row['redeemed_at'] is not None:
                return web.json_response({'ok': True}) if row['redeemed_by'] == user['id'] else error('funpay_key_used', 409)
            if not db.execute('SELECT id FROM users WHERE id=? AND banned_at IS NULL', (user['id'],)).fetchone():
                return error('auth', 401)
            base = db.execute('SELECT expires_at FROM plus_access WHERE user_id=?', (user['id'],)).fetchone()
            db.execute('UPDATE funpay_keys SET redeemed_by=?,redeemed_at=?,base_at_redemption=? WHERE hash=?',
                       (user['id'], int(time.time()), base[0] if base else None, row['hash']))
        return web.json_response({'ok': True})

    async def admin(request):
        write = request.method == 'POST'
        user = auth_csrf(request) if write else session(request)
        if not user:
            return error('auth', 401)
        if user['role'] not in ('admin', 'owner'):
            return error('forbidden', 403)
        if not limit(request, 'funpay-admin', 40, 60):
            return error('rate', 429)
        if not write:
            with connect() as db:
                promos = []
                for row in db.execute('SELECT * FROM funpay_promos ORDER BY code'):
                    summary = promo_summary(db, row)
                    summary['creator'] = row['creator_name']
                    summary['assigned'] = row['creator_id'] is not None
                    summary['reviews'] = [dict(sale) for sale in db.execute('''SELECT s.id,s.amount
                        FROM funpay_sales s JOIN funpay_lots l ON l.id=s.lot_id WHERE l.promo=?
                        AND s.status='review' ORDER BY s.created_at LIMIT 30''', (row['code'],))]
                    promos.append(summary)
                return web.json_response({'promos': promos, 'prices': prices, 'max_discount': max_discount})
        data = await json_body(request)
        code = str(data.get('code', '')).strip().upper()
        action = data.get('action')
        if not CODE.fullmatch(code) or action not in ('create', 'queue', 'retry', 'payout', 'resolve'):
            return error('invalid')
        with connect() as db:
            db.execute('BEGIN IMMEDIATE')
            actor = db.execute('SELECT role FROM users WHERE id=? AND banned_at IS NULL', (user['id'],)).fetchone()
            if not actor or actor['role'] not in ('admin', 'owner'):
                return error('forbidden', 403)
            promo = db.execute('SELECT * FROM funpay_promos WHERE code=?', (code,)).fetchone()
            if action == 'create':
                if promo:
                    return error('funpay_promo_exists', 409)
                if not integer(data, 'share_percent', 1, 100):
                    return error('invalid')
                automatic = 'discount_percent' in data
                if automatic and (not integer(data, 'discount_percent', 0, max_discount)
                                  or not integer(data, 'quantity', 1, 25)):
                    return error('invalid')
                creator = str(data.get('creator', '')).strip().lower()
                target = db.execute('SELECT id,username FROM users WHERE username_key=? AND banned_at IS NULL',
                                    (creator,)).fetchone()
                if not target:
                    return error('profile_missing', 404)
                discount = data.get('discount_percent', 0)
                db.execute('''INSERT INTO funpay_promos(code,creator_id,creator_name,share_percent,discount_percent)
                              VALUES(?,?,?,?,?)''',
                           (code, target['id'], target['username'], data['share_percent'], discount))
                if automatic:
                    for plan, base in prices.items():
                        add_lot(db, code, plan, discounted_price(base, discount), data['quantity'])
            elif not promo:
                return error('funpay_promo_missing', 404)
            elif action in ('queue', 'retry'):
                if not promo['creator_id']:
                    return error('profile_missing', 404)
                plan = data.get('plan')
                if plan not in tuple(PLANS):
                    return error('invalid')
                existing = db.execute('SELECT id,state,price,quantity FROM funpay_lots WHERE promo=? AND plan=?',
                                      (code, plan)).fetchone()
                if existing and existing['state'] != 'rejected':
                    return error('funpay_lot_exists', 409)
                if action == 'retry':
                    if not existing:
                        return error('not_found', 404)
                    price, quantity = existing['price'], existing['quantity']
                else:
                    if not integer(data, 'price', MIN_LOT_PRICE, 100_000_000) or not integer(data, 'quantity', 1, 25):
                        return error('invalid')
                    price, quantity = data['price'], data['quantity']
                add_lot(db, code, plan, price, quantity, existing['id'] if existing else None)
            elif action == 'payout':
                if actor['role'] != 'owner':
                    return error('forbidden', 403)
                summary = promo_summary(db, promo)
                if (not summary['available'] or not integer(data, 'amount', 1, 2**53 - 1)
                        or not integer(data, 'version', 0, 2**53 - 1) or data['amount'] != summary['available']
                        or data['version'] != summary['payout_version']):
                    return error('funpay_balance_changed', 409)
                db.execute('INSERT INTO funpay_payouts(promo,amount,created_at,recorded_by) VALUES(?,?,?,?)',
                           (code, data['amount'], int(time.time()), user['id']))
            else:
                if actor['role'] != 'owner':
                    return error('forbidden', 403)
                order_id = str(data.get('order_id', ''))
                if not ORDER.fullmatch(order_id) or not integer(data, 'amount', 0, 2_500_000_000):
                    return error('invalid')
                sale = db.execute('''SELECT s.status,s.amount FROM funpay_sales s
                    JOIN funpay_lots l ON l.id=s.lot_id WHERE l.promo=? AND s.id=?''', (code, order_id)).fetchone()
                if not sale or sale['status'] != 'review':
                    return error('conflict', 409)
                amount = data['amount']
                if sale['amount'] and amount > sale['amount']:
                    return error('invalid')
                share = (amount * promo['share_percent'] + 50) // 100
                db.execute('UPDATE funpay_sales SET status=?,amount=?,share=? WHERE id=?',
                           ('adjusted' if amount else 'refunded', amount, share, order_id))
        return web.json_response({'ok': True})

    async def jobs(request):
        if not worker(request):
            return error('auth', 401)
        with connect() as db:
            result = [dict(row) for row in db.execute('''SELECT l.id,l.promo,l.plan,l.price,l.state
                FROM funpay_lots l JOIN funpay_promos p ON p.code=l.promo
                WHERE l.state IN ('queued','creating') AND p.creator_id IS NOT NULL ORDER BY l.rowid LIMIT 25''')]
        return web.json_response({'jobs': result})

    async def lot(request):
        if not worker(request):
            return error('auth', 401)
        lot_id = request.match_info['lot_id']
        data = await json_body(request)
        action = data.get('action')
        with connect() as db:
            db.execute('BEGIN IMMEDIATE')
            row = db.execute('SELECT * FROM funpay_lots WHERE id=?', (lot_id,)).fetchone()
            if not row:
                return error('not_found', 404)
            if action == 'claim':
                if row['state'] != 'queued':
                    return error('conflict', 409)
                db.execute("UPDATE funpay_lots SET state='creating' WHERE id=?", (lot_id,))
                keys = [r[0] for r in db.execute('SELECT stock_secret FROM funpay_keys WHERE lot_id=?', (lot_id,))]
                return web.json_response({'keys': keys})
            if action == 'rejected' and row['state'] == 'creating':
                db.execute("UPDATE funpay_lots SET state='rejected' WHERE id=?", (lot_id,))
                db.execute('UPDATE funpay_keys SET stock_secret=NULL WHERE lot_id=?', (lot_id,))
                return web.json_response({'ok': True})
            if action != 'link' or not integer(data, 'offer_id', 1, 2**53 - 1):
                return error('invalid')
            offer_id = data['offer_id']
            if row['state'] == 'linked' and row['offer_id'] == offer_id:
                return web.json_response({'ok': True})
            if row['state'] != 'creating' or db.execute('SELECT 1 FROM funpay_lots WHERE offer_id=?', (offer_id,)).fetchone():
                return error('conflict', 409)
            db.execute("UPDATE funpay_lots SET state='linked',offer_id=? WHERE id=?", (offer_id, lot_id))
            db.execute('UPDATE funpay_keys SET stock_secret=NULL WHERE lot_id=?', (lot_id,))
        return web.json_response({'ok': True})

    async def record(request):
        if not worker(request):
            return error('auth', 401)
        order_id = request.match_info['order_id']
        data = await json_body(request)
        status = data.get('status')
        keys = data.get('keys', [])
        if (not ORDER.fullmatch(order_id) or status not in ('paid', 'closed', 'refunded', 'review')
                or not isinstance(keys, list) or len(keys) > 25
                or any(not isinstance(key, str) or not KEY.fullmatch(key) for key in keys)
                or len(set(keys)) != len(keys)):
            return error('invalid')
        with connect() as db:
            db.execute('BEGIN IMMEDIATE')
            existing = db.execute('SELECT * FROM funpay_sales WHERE id=?', (order_id,)).fetchone()
            if existing:
                if existing['status'] not in ('refunded', 'review', 'adjusted') or status == 'refunded':
                    db.execute('UPDATE funpay_sales SET status=? WHERE id=?', (status, order_id))
                return web.json_response({'tracked': True})
            if not keys:
                return web.json_response({'tracked': False})
            hashes = [key_hash(key) for key in keys]
            marks = ','.join('?' for _ in hashes)
            rows = db.execute(f'SELECT lot_id,order_id FROM funpay_keys WHERE hash IN ({marks})', hashes).fetchall()
            if not rows:
                return web.json_response({'tracked': False})
            if (len(rows) != len(keys) or len({row['lot_id'] for row in rows}) != 1
                    or any(row['order_id'] is not None for row in rows)):
                return error('funpay_order_conflict', 409)
            lot_id = rows[0]['lot_id']
            lot_row = db.execute('''SELECT p.share_percent FROM funpay_lots l
                JOIN funpay_promos p ON p.code=l.promo WHERE l.id=?''', (lot_id,)).fetchone()
            if integer(data, 'amount', 1, 2_500_000_000):
                amount = data['amount']
            elif status in ('refunded', 'review'):
                amount = 0
            else:
                return error('invalid')
            share = (amount * lot_row['share_percent'] + 50) // 100
            db.execute('INSERT INTO funpay_sales VALUES(?,?,?,?,?,?)',
                       (order_id, lot_id, status, amount, share, int(time.time())))
            db.execute(f'UPDATE funpay_keys SET order_id=? WHERE hash IN ({marks})', (order_id, *hashes))
        return web.json_response({'tracked': True})

    return [
        web.get('/api/funpay/profile', profile), web.post('/api/funpay/redeem', redeem),
        web.get('/api/admin/funpay', admin), web.post('/api/admin/funpay', admin),
        web.get('/api/funpay/worker/jobs', jobs), web.post('/api/funpay/worker/lots/{lot_id}', lot),
        web.post('/api/funpay/worker/orders/{order_id}', record),
    ]
