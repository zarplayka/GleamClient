"""Проверки аккаунтов в отдельной временной базе, без production-данных."""
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from aiohttp.test_utils import TestClient, TestServer
import app


OLD = 'https://orion.51-250-32-198.sslip.io'
NEW = 'https://orionclient.51-250-32-198.sslip.io'
PASSWORD = 'IsolatedTestPassword-123'


class AccountTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.directory = tempfile.TemporaryDirectory()
        app.DB_PATH = str(Path(self.directory.name) / 'test.db')
        app.TRUSTED_ORIGINS = frozenset({OLD, NEW})
        app.SECURE = True
        app.COOKIE = '__Host-orion_session'
        app.buckets.clear()
        (app.BASE / 'static').mkdir(exist_ok=True)
        with app.connect() as db:
            db.execute('''CREATE TABLE users (
                id INTEGER PRIMARY KEY, username TEXT NOT NULL, username_key TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL, created_at INTEGER NOT NULL
            )''')
            db.execute('INSERT INTO users VALUES (?,?,?,?,?)', (1, 'LegacyUser', 'legacyuser', app.hash_password(PASSWORD), 1))
        self.client = TestClient(TestServer(app.create_app()))
        await self.client.start_server()

    async def asyncTearDown(self):
        await self.client.close()
        self.directory.cleanup()

    async def register(self, name='OrionTest', origin=NEW):
        return await self.client.post('/api/register', json={'username': name, 'password': PASSWORD}, headers={'Origin': origin, 'X-Orion-Request': '1'})

    async def account_headers(self, name):
        response = await self.register(name)
        self.assertEqual(response.status, 201)
        return {'Origin': NEW, 'X-Orion-Request': '1',
                'Cookie': app.COOKIE + '=' + response.cookies[app.COOKIE].value,
                'X-CSRF-Token': (await response.json())['csrf']}

    async def test_launcher_browser_approval_is_csrf_protected_and_single_use(self):
        headers = await self.account_headers('LauncherUser')
        native = {'Origin': NEW, 'X-Orion-Request': '1', 'User-Agent': 'Gleam-Launcher/0.1.0'}
        started = await self.client.post('/api/launcher-auth/start', json={}, headers=native)
        self.assertEqual(started.status, 200)
        device = await started.json()
        self.assertNotIn(device['secret'], device['url'])
        pending = await self.client.post('/api/launcher-auth/poll', json={'secret': device['secret']}, headers=native)
        self.assertEqual((await pending.json())['status'], 'pending')
        approve = '/api/launcher-auth/approve'
        unsafe = {key: value for key, value in headers.items() if key != 'X-CSRF-Token'}
        self.assertEqual((await self.client.post(approve, json={'code': device['code']}, headers=unsafe)).status, 401)
        self.assertEqual((await self.client.post(approve, json={'code': device['code']}, headers=headers)).status, 200)
        self.assertEqual((await self.client.post(approve, json={'code': device['code']}, headers=headers)).status, 409)
        self.client.server.app[app.LAUNCHER_REQUESTS][app.digest(device['secret'])]['poll'] = 0
        received = await self.client.post('/api/launcher-auth/poll', json={'secret': device['secret']}, headers=native)
        self.assertEqual(received.status, 200)
        data = await received.json()
        self.assertEqual(data['status'], 'approved')
        self.assertNotEqual(data['token'], headers['Cookie'].split('=', 1)[1])
        account = await self.client.get('/api/me', headers={'Cookie': app.COOKIE+'='+data['token']})
        self.assertEqual((await account.json())['user']['username'], 'LauncherUser')
        with app.connect() as db:
            device_name = db.execute('SELECT device FROM sessions WHERE token_hash=?', (app.digest(data['token']),)).fetchone()[0]
        self.assertEqual(device_name, 'Gleam Launcher')
        self.assertEqual((await self.client.post('/api/launcher-auth/poll', json={'secret': device['secret']}, headers=native)).status, 410)

    async def test_launcher_expiration_and_revoked_approval(self):
        headers = await self.account_headers('LauncherRevoked')
        started = await (await self.client.post('/api/launcher-auth/start', json={}, headers=headers)).json()
        approved = await self.client.post('/api/launcher-auth/approve', json={'code': started['code']}, headers=headers)
        self.assertEqual(approved.status, 200)
        await self.client.post('/api/logout', json={}, headers=headers)
        self.assertEqual((await self.client.post('/api/launcher-auth/poll', json={'secret': started['secret']}, headers=headers)).status, 401)
        started = await (await self.client.post('/api/launcher-auth/start', json={}, headers=headers)).json()
        self.client.server.app[app.LAUNCHER_REQUESTS][app.digest(started['secret'])]['expires'] = 0
        self.assertEqual((await self.client.post('/api/launcher-auth/poll', json={'secret': started['secret']}, headers=headers)).status, 410)
        self.assertEqual((await self.client.post('/api/launcher-auth/poll', json={'secret': 'invalid'}, headers=headers)).status, 410)

    async def test_fake_punishments_require_plus_and_share_only_in_scope(self):
        sender = await self.account_headers('PrankAuthor')
        viewer = await self.account_headers('PrankViewer')
        path = '/api/fake-punishments'
        scope = 'a' * 64
        event = {'scope': scope, 'kind': 'mute', 'moderator': 'Moderator', 'target': 'Player',
                 'duration': '5m', 'reason': 'Проверка'}
        initial = await (await self.client.get(path, params={'scope': scope}, headers=viewer)).json()
        self.assertEqual(initial['items'], [])
        self.assertEqual((await self.client.post(path, headers=sender, json=event)).status, 403)
        with app.connect() as db:
            db.execute('''INSERT INTO plus_access (user_id, expires_at)
                        SELECT id, ? FROM users WHERE username=?''', (int(app.time.time()) + 60, 'PrankAuthor'))
        no_csrf = {key: value for key, value in sender.items() if key != 'X-CSRF-Token'}
        self.assertEqual((await self.client.post(path, headers=no_csrf, json=event)).status, 401)
        self.assertEqual((await self.client.post(path, headers=sender, json=event | {'target': 'Bad name'})).status, 400)
        self.assertEqual((await self.client.post(path, headers=sender, json=event)).status, 200)
        after = {'scope': scope, 'after': initial['cursor']}
        received = await (await self.client.get(path, params=after, headers=viewer)).json()
        self.assertEqual([(item['kind'], item['author'], item['target']) for item in received['items']],
                         [('mute', 'PrankAuthor', 'Player')])
        for headers, params in ((sender, after), (viewer, after | {'scope': 'b' * 64}),
                                (viewer, {'scope': scope}), (viewer, after | {'after': received['cursor']})):
            result = await (await self.client.get(path, params=params, headers=headers)).json()
            self.assertEqual(result['items'], [])
        with app.connect() as db:
            db.execute('DELETE FROM plus_access')
        self.assertEqual((await self.client.post(path, headers=sender, json=event)).status, 403)

    async def test_friends_require_recipient_consent_and_protect_private_profiles(self):
        alice = await self.account_headers('AlicePlayer')
        bob = await self.account_headers('BobPlayer')
        await self.client.get('/api/profile', headers=bob)

        async def action(headers, username, name):
            return (await self.client.post('/api/friends', headers=headers, json={'username': username, 'action': name})).status

        self.assertEqual(await action(alice, 'AlicePlayer', 'request'), 400)
        self.assertEqual(await action(alice, 'bobplayer', 'request'), 200)
        self.assertEqual(await action(alice, 'BobPlayer', 'accept'), 409)
        self.assertEqual(await action(bob, 'AlicePlayer', 'request'), 409)
        self.assertEqual(await action(bob, 'AlicePlayer', 'accept'), 200)
        friends = await (await self.client.get('/api/friends', headers=alice)).json()
        self.assertEqual(friends['items'], [{'username': 'BobPlayer', 'role': 'member', 'public_id': None, 'status': 'friend'}])
        self.assertEqual(await action(alice, 'BobPlayer', 'remove'), 200)
        self.assertEqual((await (await self.client.get('/api/friends', headers=bob)).json())['items'], [])
        self.assertEqual(await action(alice, 'BobPlayer', 'request'), 200)
        self.assertEqual(await action(bob, 'AlicePlayer', 'decline'), 200)
        self.assertEqual(await action(alice, 'BobPlayer', 'request'), 200)
        self.assertEqual(await action(alice, 'BobPlayer', 'cancel'), 200)

    async def test_private_chat_requires_accepted_friendship_and_csrf(self):
        alice = await self.account_headers('AlicePlayer')
        bob = await self.account_headers('BobPlayer')
        carol = await self.account_headers('CarolPlayer')
        path = '/api/friends/BobPlayer/chat'
        message = {'body': 'Привет 👋'}
        self.assertEqual((await self.client.get(path, headers=alice)).status, 403)
        response = await self.client.post('/api/friends', headers=alice, json={'username': 'BobPlayer', 'action': 'request'})
        self.assertEqual(response.status, 200)
        self.assertEqual((await self.client.post(path, headers=alice, json=message)).status, 403)
        response = await self.client.post('/api/friends', headers=bob, json={'username': 'AlicePlayer', 'action': 'accept'})
        self.assertEqual(response.status, 200)
        self.assertEqual((await self.client.get(path, headers=carol)).status, 403)
        no_csrf = {key: value for key, value in alice.items() if key != 'X-CSRF-Token'}
        self.assertEqual((await self.client.post(path, headers=no_csrf, json=message)).status, 401)
        self.assertEqual((await self.client.post(path, headers=alice, json={'body': 'a' * 501})).status, 400)
        response = await self.client.post(path, headers=alice, json=message)
        self.assertEqual(response.status, 200)
        written = (await response.json())['items'][0]
        self.assertEqual((written['body'], written['own']), (message['body'], 1))
        received = await self.client.get('/api/friends/AlicePlayer/chat', headers=bob)
        self.assertEqual((await received.json())['items'][0]['own'], 0)
        older = await self.client.get(path + '?before=' + str(written['id']), headers=alice)
        self.assertEqual((await older.json())['items'], [])
        response = await self.client.post('/api/friends', headers=bob, json={'username': 'AlicePlayer', 'action': 'remove'})
        self.assertEqual(response.status, 200)
        self.assertEqual((await self.client.get(path, headers=alice)).status, 403)

    async def test_game_settings_are_private_and_revisioned(self):
        first = await self.account_headers('SettingsFirst')
        second = await self.account_headers('SettingsSecond')
        path = '/api/game-settings'
        initial = await (await self.client.get(path, headers=first)).json()
        self.assertEqual(initial['revision'], 0)
        settings = {'revision': 0, 'minecraft': 'lang:ru_ru\nkey_key.jump:key.keyboard.space\n',
                    'modules': app.json.dumps({'zoomEnabled': True, 'chatFilterWords': 'слово ' * 2000})}
        self.assertGreater(len(settings['modules']), 4096)
        without_csrf = {key:value for key,value in first.items() if key != 'X-CSRF-Token'}
        self.assertEqual((await self.client.post(path, headers=without_csrf, json=settings)).status, 401)
        saved = await (await self.client.post(path, headers=first, json=settings)).json()
        self.assertEqual(saved['revision'], 1)
        self.assertEqual(saved['account_id'], initial['account_id'])
        self.assertEqual(saved['modules'], settings['modules'])
        other = await (await self.client.get(path, headers=second)).json()
        self.assertNotEqual(other['account_id'], saved['account_id'])
        self.assertIsNone(other['modules'])
        conflict = await self.client.post(path, headers=first, json={**settings, 'minecraft': 'lang:de_de\n'})
        self.assertEqual(conflict.status, 409)
        duplicate = await (await self.client.post(path, headers=first, json=settings)).json()
        self.assertEqual(duplicate['revision'], 1)
        login = await self.client.post('/api/login', headers={'Origin': NEW, 'X-Orion-Request': '1'},
                                       json={'username': 'SettingsFirst', 'password': PASSWORD})
        self.assertEqual(login.status, 200)
        second_device = {'Cookie': app.COOKIE + '=' + login.cookies[app.COOKIE].value}
        restored = await (await self.client.get(path, headers=second_device)).json()
        self.assertEqual(restored, saved)

    async def test_admin_permissions_and_role_changes_apply_to_existing_sessions(self):
        owner = await self.account_headers('FirstOwner')
        admin = await self.account_headers('FirstAdmin')
        player = await self.account_headers('OtherPlayer')
        with app.connect() as db:
            db.execute("UPDATE users SET role='owner' WHERE username_key='firstowner'")
            db.execute("UPDATE users SET role='admin' WHERE username_key='firstadmin'")
        route = '/api/admin/accounts'
        self.assertEqual((await self.client.get(route, headers=player)).status, 403)
        accounts = await (await self.client.get(route + '?q=other', headers=admin)).json()
        self.assertEqual([item['username'] for item in accounts['items']], ['OtherPlayer'])

        async def action(headers, username, name):
            return (await self.client.post(route, headers=headers, json={'username': username, 'action': name})).status

        self.assertEqual(await action(admin, 'OtherPlayer', 'grant_admin'), 403)
        self.assertEqual(await action(admin, 'FirstOwner', 'hide'), 403)
        await self.client.post('/api/profile', headers=player, json={'published': True})
        self.assertEqual(await action(admin, 'OtherPlayer', 'hide'), 200)
        self.assertFalse((await (await self.client.get('/api/profile', headers=player)).json())['published'])
        self.assertEqual(await action(owner, 'OtherPlayer', 'grant_admin'), 200)
        self.assertEqual((await self.client.get(route, headers=player)).status, 200)
        self.assertEqual(await action(admin, 'OtherPlayer', 'hide'), 403)
        self.assertEqual(await action(owner, 'OtherPlayer', 'revoke_admin'), 200)
        self.assertEqual((await self.client.get(route, headers=player)).status, 403)
        self.assertEqual(await action(admin, 'OtherPlayer', 'grant_media'), 403)
        self.assertEqual(await action(owner, 'OtherPlayer', 'grant_media'), 200)
        for path in ('/api/me', '/api/profile', route + '?q=other'):
            headers = owner if path.startswith(route) else player
            payload = await (await self.client.get(path, headers=headers)).json()
            account = payload.get('user') or (payload['items'][0] if 'items' in payload else payload)
            self.assertEqual(account['role'], 'media')
        self.assertEqual((await self.client.get(route, headers=player)).status, 403)
        self.assertEqual(await action(owner, 'OtherPlayer', 'revoke_media'), 200)
        self.assertEqual((await (await self.client.get('/api/me', headers=player)).json())['user']['role'], 'member')

    async def test_moderation_ban_revokes_sessions_and_delete_does_not_reuse_id(self):
        admin = await self.account_headers('ModerationAdmin')
        player = await self.account_headers('ModerationPlayer')
        with app.connect() as db:
            db.execute("UPDATE users SET role='admin' WHERE username_key='moderationadmin'")
        profile = await (await self.client.post('/api/profile', headers=player, json={'published': True})).json()
        route = '/api/profile/' + profile['id']
        access = await (await self.client.get('/api/catalog-access', headers=player)).json()
        self.assertEqual((await self.client.post('/api/orders', headers=player, json={})).status, 200)

        async def moderate(action, **fields):
            return (await self.client.post('/api/admin/accounts', headers=admin,
                    json={'username': 'ModerationPlayer', 'action': action, **fields})).status

        async def login():
            return await self.client.post('/api/login', headers={'Origin': NEW, 'X-Orion-Request': '1'},
                    json={'username': 'ModerationPlayer', 'password': PASSWORD})

        self.assertEqual(await moderate('ban'), 200)
        self.assertIsNone((await (await self.client.get('/api/me', headers=player)).json())['user'])
        self.assertIsNotNone((await (await self.client.get(route)).json())['banned_at'])
        denied = await login()
        self.assertEqual(denied.status, 403)
        self.assertEqual((await denied.json())['error'], 'banned')
        self.assertEqual(await moderate('unban'), 200)
        self.assertIsNone((await (await self.client.get(route)).json())['banned_at'])
        self.assertIsNone((await (await self.client.get('/api/me', headers=player)).json())['user'])
        self.assertEqual((await login()).status, 200)
        self.assertEqual(await moderate('delete', confirmation='wrong'), 400)
        self.assertEqual(await moderate('delete', confirmation='ModerationPlayer'), 200)
        self.assertEqual((await self.client.get(route)).status, 404)
        self.assertEqual((await login()).status, 401)
        next_player = await self.account_headers('NextPlayer')
        next_access = await (await self.client.get('/api/catalog-access', headers=next_player)).json()
        self.assertGreater(next_access['account_id'], access['account_id'])

    async def test_profile_document_starts_with_profile_for_signed_in_user(self):
        headers = await self.account_headers('FirstPaint')
        template = Path(__file__).resolve().parents[2] / 'website' / 'auth.html'
        with patch.object(app, 'Path', return_value=template):
            response = await self.client.get('/auth.html', headers=headers)
            self.assertEqual(response.status, 200)
            html = await response.text()
        self.assertIn('<body class="auth-page profile-page">', html)
        self.assertIn('<section class="auth-card" hidden', html)
        self.assertIn('<h1 id="profile-name">FirstPaint</h1>', html)

    async def test_register_login_restore_logout(self):
        response = await self.register()
        self.assertEqual(response.status, 201)
        data = await response.json()
        cookie = response.cookies[app.COOKIE]
        self.assertTrue(cookie['secure'])
        self.assertTrue(cookie['httponly'])
        self.assertEqual(cookie['samesite'], 'Strict')
        self.assertEqual(cookie['path'], '/')
        headers = {'Cookie': app.COOKIE + '=' + cookie.value}
        me = await self.client.get('/api/me', headers=headers)
        self.assertEqual((await me.json())['user']['username'], 'OrionTest')
        protected = {'Origin': NEW, 'X-Orion-Request': '1', **headers}
        denied = await self.client.post('/api/logout', json={}, headers=protected)
        self.assertEqual(denied.status, 401)
        protected['X-CSRF-Token'] = data['csrf']
        ended = await self.client.post('/api/logout', json={}, headers=protected)
        self.assertEqual(ended.status, 200)
        after = await self.client.get('/api/me', headers=headers)
        self.assertIsNone((await after.json())['user'])
        login = await self.client.post('/api/login', json={'username': 'oriontest', 'password': PASSWORD}, headers={'Origin': NEW, 'X-Orion-Request': '1'})
        self.assertEqual(login.status, 200)

    async def test_existing_origin_still_works(self):
        response = await self.register(origin=OLD)
        self.assertEqual(response.status, 201)

    async def test_untrusted_origin_rejected(self):
        for origin in ['https://example.com', NEW + '.attacker.test', '', 'null']:
            response = await self.register(origin=origin)
            self.assertEqual(response.status, 403)
        response = await self.client.post('/api/register', json={'username': 'NoHeader', 'password': PASSWORD}, headers={'Origin': NEW})
        self.assertEqual(response.status, 403)

    async def test_invalid_duplicate_and_wrong_password(self):
        response = await self.register(name='!')
        self.assertEqual(response.status, 400)
        response = await self.register()
        self.assertEqual(response.status, 201)
        response = await self.register(name='oriontest')
        self.assertEqual(response.status, 409)
        response = await self.client.post('/api/login', json={'username': 'OrionTest', 'password': 'IncorrectPassword'}, headers={'Origin': NEW, 'X-Orion-Request': '1'})
        self.assertEqual(response.status, 401)

    async def test_registration_rate_limit(self):
        for i in range(5):
            response = await self.register(name='RateProbe' + str(i))
            self.assertEqual(response.status, 201)
        response = await self.register(name='RateProbeExtra')
        self.assertEqual(response.status, 429)

    async def test_email_registration_and_legacy_account_update(self):
        headers = {'Origin': NEW, 'X-Orion-Request': '1'}
        payload = {'username': 'NewUser', 'password': PASSWORD, 'email': 'New@EXAMPLE.COM'}
        registered = await self.client.post('/api/register', json=payload, headers=headers)
        self.assertEqual(registered.status, 201)
        self.assertEqual((await registered.json())['user']['email'], 'New@example.com')
        duplicate = await self.client.post('/api/register', json={**payload, 'username': 'OtherUser', 'email': 'new@example.com'}, headers=headers)
        self.assertEqual((duplicate.status, (await duplicate.json())['error']), (409, 'email_taken'))
        legacy = await self.client.post('/api/login', json={'username': 'LegacyUser', 'password': PASSWORD}, headers=headers)
        data = await legacy.json()
        self.assertIsNone(data['user']['email'])
        headers['Cookie'] = app.COOKIE + '=' + legacy.cookies[app.COOKIE].value
        update = {'email': 'legacy@example.com', 'current_password': PASSWORD}
        no_csrf = await self.client.post('/api/email', json=update, headers=headers)
        self.assertEqual(no_csrf.status, 401)
        headers['X-CSRF-Token'] = data['csrf']
        wrong_password = await self.client.post('/api/email', json={**update, 'current_password': 'wrong'}, headers=headers)
        self.assertEqual(wrong_password.status, 401)
        invalid = await self.client.post('/api/email', json={**update, 'email': 'bad..email@example.com'}, headers=headers)
        self.assertEqual(invalid.status, 400)
        saved = await self.client.post('/api/email', json=update, headers=headers)
        self.assertEqual(saved.status, 200)
        me = await self.client.get('/api/me', headers=headers)
        self.assertEqual((await me.json())['user']['email'], update['email'])
        app.init_db()
        with app.connect() as db:
            row = db.execute('SELECT email, email_verified_at FROM users WHERE id=1').fetchone()
            self.assertEqual(tuple(row), (update['email'], None))

    async def test_verified_email_can_reset_password_once(self):
        config = patch.multiple(app, MAIL_FROM='sender@example.com', MAIL_PASSWORD_FILE='unused', MAIL_ORIGIN=NEW)
        headers = {'Origin': NEW, 'X-Orion-Request': '1'}
        email = 'recovery@example.com'
        new_password = 'NewIsolatedPassword-456'
        with config, patch.object(app, 'deliver_mail') as delivered:
            application = app.create_app()
            async with TestClient(TestServer(application)) as client:
                registered = await client.post('/api/register', json={'username': 'RecoveryUser', 'password': PASSWORD, 'email': email}, headers=headers)
                self.assertEqual(registered.status, 201)
                queue = application[app.MAIL_QUEUE]
                await queue.join()
                verification_message = delivered.call_args.args[1]
                verification = verification_message.get_body(('plain',)).get_content().split('#verify=')[1].splitlines()[0]
                verification_html = verification_message.get_body(('html',)).get_content()
                self.assertIn(f'href="{NEW}/auth.html#verify={verification}"', verification_html)
                self.assertIn('Подтвердить почту', verification_html)
                self.assertIn('RecoveryUser', verification_html)
                self.assertIn(f'{NEW}/assets/orion-badge.png', verification_html)
                self.assertIn('24 часа', verification_html)
                self.assertFalse((await registered.json())['user']['email_verified'])

                reset_request = '/api/password/reset/request'
                unknown = await client.post(reset_request, json={'email': 'unknown@example.com'}, headers=headers)
                unverified = await client.post(reset_request, json={'email': email}, headers=headers)
                self.assertEqual((unknown.status, await unknown.json()), (unverified.status, await unverified.json()))
                await queue.join()
                self.assertEqual(delivered.call_count, 1)

                confirm = await client.post('/api/email/verify/confirm', json={'token': verification}, headers=headers)
                self.assertEqual(confirm.status, 200)
                repeated = await client.post('/api/email/verify/confirm', json={'token': verification}, headers=headers)
                self.assertEqual(repeated.status, 400)
                session_headers = {'Cookie': app.COOKIE + '=' + registered.cookies[app.COOKIE].value}
                me = await client.get('/api/me', headers=session_headers)
                self.assertTrue((await me.json())['user']['email_verified'])

                accepted = await client.post(reset_request, json={'email': email.upper()}, headers={**headers, 'Accept-Language': 'en-GB'})
                self.assertEqual(accepted.status, 202)
                await queue.join()
                reset_message = delivered.call_args.args[1]
                token = reset_message.get_body(('plain',)).get_content().split('#reset=')[1].splitlines()[0]
                reset_html = reset_message.get_body(('html',)).get_content()
                self.assertIn(f'href="{NEW}/auth.html#reset={token}"', reset_html)
                self.assertIn('Set a new password', reset_html)
                self.assertIn('30 minutes', reset_html)
                payload = {'token': token, 'password': new_password}
                confirm_reset = '/api/password/reset/confirm'
                reset = await client.post(confirm_reset, json=payload, headers=headers)
                self.assertEqual(reset.status, 200)
                reused = await client.post(confirm_reset, json=payload, headers=headers)
                self.assertEqual(reused.status, 400)
                await queue.join()
                changed = delivered.call_args.args[1]
                self.assertEqual(changed['Subject'], 'Пароль изменён')
                self.assertIn('RecoveryUser', changed.get_body(('plain',)).get_content())
                self.assertIn('поддержку Orion', changed.get_body(('html',)).get_content())
                self.assertNotIn('#reset=', changed.get_body(('plain',)).get_content())

                me = await client.get('/api/me', headers=session_headers)
                self.assertIsNone((await me.json())['user'])
                old = await client.post('/api/login', json={'username': 'RecoveryUser', 'password': PASSWORD}, headers=headers)
                new = await client.post('/api/login', json={'username': 'RecoveryUser', 'password': new_password}, headers=headers)
                self.assertEqual((old.status, new.status), (401, 200))

    async def test_registration_availability_ignores_case(self):
        headers = {'Origin': NEW, 'X-Orion-Request': '1'}
        payload = {'username': 'MixedCase', 'email': 'Mixed@Example.COM', 'password': PASSWORD}
        self.assertEqual((await self.client.post('/api/register', json=payload, headers=headers)).status, 201)
        path = '/api/register/availability'
        for fields, expected in [
            ({'username': 'mIXEDcASE'}, False),
            ({'username': 'Unclaimed'}, True),
            ({'email': 'mIXED@eXAMPLE.cOM'}, False),
            ({'email': 'unclaimed@example.com'}, True),
        ]:
            response = await self.client.post(path, json=fields, headers=headers)
            self.assertEqual((response.status, await response.json()), (200, {'available': expected}))
        invalid = await self.client.post(path, json={'email': 'bad..email@example.com'}, headers=headers)
        self.assertEqual(invalid.status, 400)
        duplicate = await self.client.post('/api/register', json={**payload, 'username': 'mIXEDcASE', 'email': 'other@example.com'}, headers=headers)
        self.assertEqual((duplicate.status, (await duplicate.json())['error']), (409, 'taken'))
        duplicate = await self.client.post('/api/register', json={**payload, 'username': 'OtherName', 'email': 'mixed@example.com'}, headers=headers)
        self.assertEqual((duplicate.status, (await duplicate.json())['error']), (409, 'email_taken'))

    async def test_profile_is_private_until_published(self):
        headers = {'Origin': NEW, 'X-Orion-Request': '1'}
        registered = await self.register()
        owner = {**headers, 'Cookie': app.COOKIE + '=' + registered.cookies[app.COOKIE].value,
                 'X-CSRF-Token': (await registered.json())['csrf']}
        profile = await (await self.client.get('/api/profile', headers=owner)).json()
        self.assertEqual(profile['role'], 'member')
        path = '/api/profile/' + profile['id']
        self.assertEqual((await self.client.get(path, headers={'Cookie': ''})).status, 404)
        data = {'discord': 'orion_test', 'github': 'orion-test', 'published': True}
        self.assertEqual((await self.client.post('/api/profile', json=data, headers=headers)).status, 401)
        saved = await self.client.post('/api/profile', json={**data, 'role': 'owner'}, headers=owner)
        self.assertEqual(saved.status, 200)
        self.assertEqual((await saved.json())['role'], 'member')
        public = await (await self.client.get(path, headers={'Cookie': ''})).json()
        self.assertEqual(public['discord'], '')
        self.assertEqual(public['github'], '')
        self.assertTrue(public['published'])
        with app.connect() as db:
            db.execute("UPDATE users SET role='admin' WHERE username=?", (profile['username'],))
        public = await (await self.client.get(path, headers={'Cookie': ''})).json()
        self.assertEqual(public['role'], 'admin')
        self.assertEqual((await self.client.post('/api/profile/avatar', json={'avatar': 'bad'}, headers=owner)).status, 400)
        self.assertEqual((await self.client.post('/api/profile', json={**data, 'published': False}, headers=owner)).status, 200)
        self.assertEqual((await self.client.get(path, headers={'Cookie': ''})).status, 404)

    async def test_profile_feedback_enforces_session_plus_and_comment_ownership(self):
        async def register_as(name):
            response = await self.register(name)
            return {'Origin': NEW, 'X-Orion-Request': '1',
                    'Cookie': app.COOKIE + '=' + response.cookies[app.COOKIE].value,
                    'X-CSRF-Token': (await response.json())['csrf']}

        owner = await register_as('ProfileOwner')
        voter = await register_as('ProfileVoter')
        stranger = await register_as('Stranger')
        saved = await self.client.post('/api/profile', json={'published': True}, headers=owner)
        path = '/api/profile/' + (await saved.json())['id']
        matches = await (await self.client.get('/api/profile-search?q=pRoFiLe')).json()
        self.assertEqual([item['username'] for item in matches['items']], ['ProfileOwner'])
        self.assertEqual((await self.client.post(path + '/vote', json={'value': 1}, headers=owner)).status, 403)
        self.assertEqual((await self.client.post(path + '/vote', json={'value': 1}, headers=voter)).status, 200)
        self.assertEqual((await self.client.post(path + '/vote', json={'value': -1}, headers=voter)).status, 200)
        feedback = await (await self.client.get(path + '/feedback', headers=voter)).json()
        self.assertEqual((feedback['likes'], feedback['dislikes'], feedback['vote']), (0, 1, -1))
        self.assertEqual((await self.client.post(path + '/vote', json={'value': 0}, headers=voter)).status, 200)
        comment = {'body': '<b>Plain text</b>'}
        self.assertEqual((await self.client.post(path + '/comments', json=comment, headers=voter)).status, 403)
        with app.connect() as db:
            db.execute("INSERT INTO plus_access SELECT id, ? FROM users WHERE username_key='profilevoter'",
                       (int(app.time.time()) + 3600,))
        self.assertEqual((await self.client.post(path + '/comments', json=comment, headers=voter)).status, 201)
        feedback = await (await self.client.get(path + '/feedback', headers={'Cookie': ''})).json()
        self.assertIsNone(feedback['csrf'])
        self.assertEqual(feedback['comments'][0]['body'], comment['body'])
        delete = path + '/comments/' + feedback['comments'][0]['id'] + '/delete'
        self.assertEqual((await self.client.post(delete, json={}, headers=stranger)).status, 404)
        self.assertEqual((await self.client.post(delete, json={}, headers=owner)).status, 200)
        with app.connect() as db:
            db.execute("UPDATE users SET role='owner' WHERE username_key='stranger'")
        status = await (await self.client.get('/api/me', headers=stranger)).json()
        self.assertTrue(status['plus_active'])
        self.assertIsNone(status['plus_expires_at'])
        access = await (await self.client.get('/api/catalog-access', headers=stranger)).json()
        self.assertEqual(access['limit'], 10)
        access = await (await self.client.get('/api/catalog-access', headers=voter)).json()
        self.assertEqual(access['limit'], 10)
        access = await (await self.client.get('/api/catalog-access', headers=owner)).json()
        self.assertEqual(access['limit'], 3)
        feedback = await (await self.client.get(path + '/feedback', headers=stranger)).json()
        self.assertTrue(feedback['can_comment'])
        self.assertEqual((await self.client.post(path + '/comments', json=comment, headers=stranger)).status, 201)
        order = await self.client.post('/api/orders', json={}, headers=stranger)
        self.assertEqual(order.status, 409)
        self.assertEqual((await order.json())['error'], 'plus_active')
        with app.connect() as db:
            db.execute("UPDATE users SET role='admin' WHERE username_key='stranger'")
        status = await (await self.client.get('/api/me', headers=stranger)).json()
        self.assertEqual(status['user']['role'], 'admin')
        self.assertTrue(status['plus_active'])
        self.assertIsNone(status['plus_expires_at'])
        access = await (await self.client.get('/api/catalog-access', headers=stranger)).json()
        self.assertEqual(access['limit'], 10)
        self.assertEqual((await self.client.post(path + '/comments', json=comment, headers=stranger)).status, 201)
        await self.client.post('/api/profile', json={'published': False}, headers=owner)
        self.assertEqual((await self.client.get(path + '/feedback', headers=voter)).status, 404)
        self.assertEqual(await (await self.client.get('/api/profile-search?q=Profile')).json(), {'items': []})

    async def test_oauth_contact_requires_matching_session_and_single_use_state(self):
        from urllib.parse import parse_qs, urlsplit

        async def register_as(name):
            response = await self.register(name)
            return {'Origin': NEW, 'X-Orion-Request': '1',
                    'Cookie': app.COOKIE + '=' + response.cookies[app.COOKIE].value,
                    'X-CSRF-Token': (await response.json())['csrf']}

        owner = await register_as('OwnerTest')
        other = await register_as('OtherTest')
        for provider in ('discord', 'github'):
            prefix = '/api/profile/oauth/' + provider
            config = ('test-client', None, NEW + prefix + '/callback')
            with patch.object(app, 'configuration', return_value=config), patch.object(
                    app, 'verified_identity', return_value=('123456', 'verified-user')) as exchange:
                start = await self.client.post(prefix + '/start', json={}, headers=owner)
                self.assertEqual(start.status, 200)
                params = parse_qs(urlsplit((await start.json())['url']).query)
                state = params['state'][0]
                if provider == 'github':
                    self.assertEqual(params['code_challenge_method'], ['S256'])
                else:
                    self.assertEqual(params['scope'], ['identify'])
                callback = await self.client.get(prefix + '/callback', params={'code': 'test-code', 'state': state}, allow_redirects=False)
                self.assertEqual(callback.status, 303)
                self.assertIn('#social=' + provider, callback.headers['Location'])
                payload = {'state': state, 'code': 'test-code'}
                denied = await self.client.post(prefix + '/finish', json=payload, headers=other)
                self.assertEqual(denied.status, 409)
                saved = await self.client.post(prefix + '/finish', json=payload, headers=owner)
                self.assertEqual(saved.status, 200)
                self.assertTrue((await saved.json())['connections'][provider]['verified'])
                replay = await self.client.post(prefix + '/finish', json=payload, headers=owner)
                self.assertEqual(replay.status, 409)
                exchange.assert_awaited_once()
                unlinked = await self.client.post(prefix + '/unlink', json={}, headers=owner)
                self.assertFalse((await unlinked.json())['connections'][provider]['verified'])

    async def test_session_management_preserves_current_session_and_isolates_accounts(self):
        headers = {'Origin': NEW, 'X-Orion-Request': '1'}

        async def login_as(username, agent):
            response = await self.client.post('/api/login', json={'username': username, 'password': PASSWORD},
                                              headers={**headers, 'User-Agent': agent})
            self.assertEqual(response.status, 200)
            return {**headers, 'Cookie': app.COOKIE + '=' + response.cookies[app.COOKIE].value,
                    'X-CSRF-Token': (await response.json())['csrf']}

        async def list_as(session_headers):
            response = await self.client.get('/api/sessions', headers=session_headers)
            self.assertEqual(response.status, 200)
            return (await response.json())['sessions']

        current = await login_as('LegacyUser', 'Mozilla/5.0 Chrome/130.0')
        client = await login_as('LegacyUser', 'Java-http-client/25')
        browser = await login_as('LegacyUser', 'Firefox/130.0')
        self.assertEqual((await self.register('OtherUser')).status, 201)
        other = await login_as('OtherUser', 'Firefox/130.0')
        sessions = await list_as(current)
        self.assertEqual(len(sessions), 3)
        self.assertEqual((sessions[0]['device'], sessions[0]['current']), ('Chrome', True))
        client_id = next(item['id'] for item in sessions if item['device'] == 'Orion Client')
        revoke = '/api/sessions/revoke'
        denied = await self.client.post(revoke, json={'id': client_id}, headers={**current, 'X-CSRF-Token': ''})
        self.assertEqual(denied.status, 401)
        foreign = await self.client.post(revoke, json={'id': (await list_as(other))[0]['id']}, headers=current)
        self.assertEqual(foreign.status, 200)
        self.assertEqual(len(await list_as(other)), 2)
        revoked = await self.client.post(revoke, json={'id': client_id}, headers=current)
        self.assertEqual(revoked.status, 200)
        self.assertEqual((await self.client.get('/api/sessions', headers=client)).status, 401)
        remaining = await self.client.post(revoke, json={'id': 'others'}, headers=current)
        self.assertEqual(remaining.status, 200)
        self.assertEqual((await self.client.get('/api/sessions', headers=browser)).status, 401)
        self.assertEqual(len(await list_as(current)), 1)


if __name__ == '__main__':
    unittest.main()
