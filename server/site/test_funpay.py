"""Isolated FunPay API scenarios, with no calls to the marketplace."""
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from aiohttp.test_utils import TestClient, TestServer

import app


class FunpayTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        directory = Path(self.enterContext(tempfile.TemporaryDirectory()))
        token_file = directory / 'worker-token'
        token = 'test-' * 10
        token_file.write_text(token, encoding='utf-8')
        self.enterContext(patch.dict('os.environ', {'GLEAM_FUNPAY_TOKEN_FILE': str(token_file)}))
        self.enterContext(patch.object(app, 'DB_PATH', str(directory / 'test.db')))
        app.buckets.clear()
        self.client = TestClient(TestServer(app.create_app()))
        await self.client.start_server()
        self.addAsyncCleanup(self.client.close)
        self.headers = {'Origin': app.ORIGIN, 'X-Orion-Request': '1'}
        self.worker = self.headers | {'Authorization': 'Bearer ' + token}
        self.owner = await self.account('Operator', 'owner')
        self.creator = await self.account('Creator', 'member')
        self.buyer = await self.account('Buyer', 'member')
        self.staff = await self.account('Staff', 'admin')

    async def account(self, name, role):
        with app.connect() as db:
            db.execute('INSERT INTO users(username,username_key,password_hash,created_at,role) VALUES(?,?,?,1,?)',
                       (name, name.lower(), 'unused', role))
            user_id = db.execute('SELECT id FROM users WHERE username=?', (name,)).fetchone()[0]
        token, csrf = app.create_session(user_id, 'unused', SimpleNamespace(headers={}))
        return self.headers | {'Cookie': app.COOKIE + '=' + token, 'X-CSRF-Token': csrf}

    async def call(self, path, headers, data=None, status=200):
        response = await self.client.request('GET' if data is None else 'POST', '/api/' + path, headers=headers, json=data)
        result = await response.json()
        self.assertEqual(response.status, status, result)
        return result

    async def prepare(self, plan='month', quantity=2):
        code = 'CREATOR'
        await self.call('admin/funpay', self.staff,
                        {'action': 'create', 'code': code, 'creator': 'Creator', 'share_percent': 50})
        queue = {'action': 'queue', 'code': code, 'plan': plan, 'price': 2900, 'quantity': quantity}
        await self.call('admin/funpay', self.staff, queue)
        await self.call('admin/funpay', self.staff, queue, 409)
        await self.call('funpay/worker/jobs', self.headers, status=401)
        job, = (await self.call('funpay/worker/jobs', self.worker))['jobs']
        lot = 'funpay/worker/lots/' + job['id']
        keys = (await self.call(lot, self.worker, {'action': 'claim'}))['keys']
        self.assertEqual(len(keys), quantity)
        await self.call(lot, self.worker, {'action': 'claim'}, 409)
        self.assertEqual((await self.call('funpay/worker/jobs', self.worker))['jobs'][0]['state'], 'creating')
        await self.call('funpay/redeem', self.buyer, {'key': keys[0]}, 409)
        for _ in range(2):
            await self.call(lot, self.worker, {'action': 'link', 'offer_id': 321})
        with app.connect() as db:
            self.assertEqual(db.execute('SELECT COUNT(stock_secret) FROM funpay_keys').fetchone()[0], 0)
        return keys

    async def summary(self, headers=None):
        data = await self.call('funpay/profile', headers or self.creator)
        return data['promos']

    async def test_discount_queues_all_plans_with_stock_and_preserves_price_on_retry(self):
        payload = {'action': 'create', 'code': 'VIDEO', 'creator': 'Creator', 'share_percent': 50,
                   'discount_percent': 10, 'quantity': 2}
        await self.call('admin/funpay', self.staff, payload | {'discount_percent': 99}, 400)
        self.assertEqual((await self.call('funpay/worker/jobs', self.worker))['jobs'], [])
        await self.call('admin/funpay', self.staff, payload)
        await self.call('admin/funpay', self.staff, payload, 409)
        promo, = await self.summary()
        self.assertEqual((promo['discount_percent'], promo['share_percent']), (10, 50))
        expected = {'month': 2610, 'quarter': 6030, 'year': 17910, 'lifetime': 35910}
        self.assertEqual({lot['plan']: lot['price'] for lot in promo['lots']}, expected)
        jobs = (await self.call('funpay/worker/jobs', self.worker))['jobs']
        self.assertEqual(len(jobs), 4)
        month = next(job for job in jobs if job['plan'] == 'month')
        path = 'funpay/worker/lots/' + month['id']
        first = (await self.call(path, self.worker, {'action': 'claim'}))['keys']
        self.assertEqual(len(first), 2)
        await self.call(path, self.worker, {'action': 'rejected'})
        await self.call('admin/funpay', self.staff, {'action': 'retry', 'code': 'VIDEO', 'plan': 'month'})
        second = (await self.call(path, self.worker, {'action': 'claim'}))['keys']
        self.assertEqual(len(second), 2)
        self.assertFalse(set(first) & set(second))
        promo, = await self.summary()
        self.assertEqual({lot['plan']: lot['price'] for lot in promo['lots']}, expected)

    async def test_sale_activation_payout_and_refund(self):
        first, second = await self.prepare()
        self.assertEqual(await self.summary(self.buyer), [])
        await self.call('admin/funpay', self.creator, status=403)
        await self.call('funpay/redeem', self.buyer, {'key': first})
        self.assertTrue((await self.call('me', self.buyer))['plus_active'])
        self.assertEqual((await self.summary())[0]['sales'], 0)
        order = 'funpay/worker/orders/ORDER1'
        paid = {'status': 'paid', 'amount': 5801, 'keys': [first, second]}
        for _ in range(2):
            await self.call(order, self.worker, paid)
        (promo,) = await self.summary()
        self.assertEqual((promo['sales'], promo['pending'], promo['available']), (1, 2901, 0))
        self.assertNotIn(first, str(promo))
        self.assertNotIn('ORDER1', str(promo))
        for _ in range(2):
            await self.call('funpay/redeem', self.buyer, {'key': first})
        await self.call('funpay/redeem', self.creator, {'key': first}, 409)
        access = await self.call('me', self.buyer)
        self.assertTrue(access['plus_active'])
        self.assertIsInstance(access['plus_expires_at'], int)
        await self.call('funpay/worker/orders/ORDER2', self.worker, paid, 409)
        await self.call(order, self.worker, {'status': 'closed'})
        promo, = await self.summary()
        self.assertEqual((promo['pending'], promo['available']), (0, 2901))
        payout = {'action': 'payout', 'code': promo['code'], 'amount': 2901, 'version': promo['payout_version']}
        await self.call('admin/funpay', self.staff, payout, 403)
        await self.call('admin/funpay', self.owner, payout)
        await self.call('admin/funpay', self.owner, payout, 409)
        await self.call(order, self.worker, {'status': 'refunded'})
        await self.call(order, self.worker, paid)
        promo, = await self.summary()
        self.assertEqual((promo['sales'], promo['available'], promo['paid'], promo['adjustment']), (0, 0, 2901, -2901))
        self.assertFalse((await self.call('me', self.buyer))['plus_active'])
        await self.call('funpay/redeem', self.buyer, {'key': second}, 409)

    async def test_lifetime_and_partial_refund_review(self):
        key, = await self.prepare('lifetime', 1)
        order = 'funpay/worker/orders/PARTIAL'
        await self.call(order, self.worker, {'status': 'closed', 'amount': 2900, 'keys': [key]})
        await self.call('funpay/redeem', self.buyer, {'key': key})
        access = await self.call('me', self.buyer)
        self.assertTrue(access['plus_active'])
        self.assertIsNone(access['plus_expires_at'])
        await self.call(order, self.worker, {'status': 'review'})
        await self.call(order, self.worker, {'status': 'closed'})
        promo, = await self.summary()
        self.assertEqual(promo['available'], 0)
        update = {'action': 'resolve', 'code': 'CREATOR', 'order_id': 'PARTIAL', 'amount': 1000}
        await self.call('admin/funpay', self.staff, update, 403)
        await self.call('admin/funpay', self.owner, update)
        await self.call(order, self.worker, {'status': 'review'})
        promo, = await self.summary()
        self.assertEqual(promo['available'], 500)
        self.assertEqual(promo['orders'][0]['status'], 'adjusted')

    async def test_refund_can_be_first_event_after_redemption(self):
        key, = await self.prepare(quantity=1)
        await self.call('funpay/redeem', self.buyer, {'key': key})
        self.assertTrue((await self.call('me', self.buyer))['plus_active'])
        await self.call('funpay/worker/orders/REFUND', self.worker, {'status': 'refunded', 'keys': [key]})
        self.assertFalse((await self.call('me', self.buyer))['plus_active'])
