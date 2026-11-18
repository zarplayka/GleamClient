"""Orion website: isolated web accounts and payment-intent prototype."""
import asyncio, hashlib, hmac, os, re, secrets, sqlite3, time
import logging
import json
import base64
import binascii
import smtplib
import ssl
import ipaddress
from html import escape
from collections import defaultdict, deque
from contextlib import contextmanager
from pathlib import Path
from urllib.parse import urlencode
from aiohttp import ClientError, web
from account_mail import account_mail
from local_pricing import pricing_for_ip
from profile_oauth import PROVIDERS, authorization_url, configuration, verified_identity
import funpay_sales

BASE = Path(__file__).resolve().parent
DB_PATH = os.environ.get('ORION_SITE_DB', str(BASE / 'site.db'))
ORIGIN = os.environ.get('ORION_SITE_ORIGIN', 'http://127.0.0.1:8088').rstrip('/')
TRUSTED_ORIGINS = frozenset({ORIGIN} | {
    value.strip().rstrip('/')
    for value in os.environ.get('ORION_SITE_ADDITIONAL_ORIGINS', '').split(',')
    if value.strip()
})
SECURE = ORIGIN.startswith('https://')
COOKIE = '__Host-orion_session' if SECURE else 'orion_session'
TTL = 7 * 24 * 3600
buckets = defaultdict(deque)
MAIL_FROM = os.environ.get('ORION_SITE_MAIL_FROM', '')
MAIL_PASSWORD_FILE = os.environ.get('ORION_SITE_MAIL_PASSWORD_FILE', '')
MAIL_ORIGIN = os.environ.get('ORION_SITE_MAIL_ORIGIN', '').rstrip('/')
MAIL_QUEUE = web.AppKey('mail_queue', asyncio.Queue)
FAKE_PUNISHMENTS = web.AppKey('fake_punishments', deque)
FAKE_PUNISHMENT_EPOCH = web.AppKey('fake_punishment_epoch', str)
LAUNCHER_REQUESTS = web.AppKey('launcher_requests', dict)
CHAT_TRANSFERS = web.AppKey('chat_transfers', dict)
WORLD_PRESENCE = web.AppKey('world_presence', dict)
WORLD_INVITES = web.AppKey('world_invites', dict)
WORLD_VERSIONS = frozenset(('1.21.11', '26.1.2', '26.2', '26.3'))
MAIL_TOKEN = re.compile(r'[A-Za-z0-9_-]{43}')
logger = logging.getLogger(__name__)

@contextmanager
def connect():
    db = sqlite3.connect(DB_PATH, timeout=10)
    db.row_factory = sqlite3.Row
    db.execute('PRAGMA foreign_keys=ON')
    try:
        with db: yield db
    finally: db.close()

def init_db():
    with connect() as db:
        db.executescript('''PRAGMA journal_mode=WAL;
        CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY, username TEXT NOT NULL, username_key TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, created_at INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS sessions (token_hash TEXT PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, csrf TEXT NOT NULL, expires_at INTEGER NOT NULL);
        CREATE INDEX IF NOT EXISTS sessions_expiry ON sessions(expires_at);
        CREATE TABLE IF NOT EXISTS orders (id TEXT PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id), amount_kopecks INTEGER NOT NULL CHECK(amount_kopecks=6700), currency TEXT NOT NULL, status TEXT NOT NULL, created_at INTEGER NOT NULL);
        CREATE INDEX IF NOT EXISTS orders_user ON orders(user_id, created_at DESC);
        CREATE TABLE IF NOT EXISTS plus_access (user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, expires_at INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS plus_grants (id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, granted_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, source TEXT NOT NULL);''')
        db.execute('BEGIN IMMEDIATE')
        columns = {column['name'] for column in db.execute('PRAGMA table_info(users)')}
        if 'email' not in columns:
            db.execute('ALTER TABLE users ADD COLUMN email TEXT COLLATE NOCASE')
        if 'email_verified_at' not in columns:
            db.execute('ALTER TABLE users ADD COLUMN email_verified_at INTEGER')
        if 'role' not in columns:
            db.execute("ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT 'member' CHECK (role IN ('member','admin','owner'))")
        if 'is_media' not in columns:
            db.execute('ALTER TABLE users ADD COLUMN is_media INTEGER NOT NULL DEFAULT 0 CHECK (is_media IN (0,1))')
        if 'banned_at' not in columns:
            db.execute('ALTER TABLE users ADD COLUMN banned_at INTEGER')
        db.execute('CREATE TABLE IF NOT EXISTS user_id_sequence (id INTEGER PRIMARY KEY CHECK(id=1), last_id INTEGER NOT NULL)')
        db.execute('INSERT OR IGNORE INTO user_id_sequence SELECT 1, COALESCE(MAX(id),0) FROM users')
        db.execute('CREATE UNIQUE INDEX IF NOT EXISTS users_email_unique ON users(email) WHERE email IS NOT NULL')
        db.execute('''CREATE TABLE IF NOT EXISTS game_settings (
            user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
            revision INTEGER NOT NULL, minecraft TEXT, modules TEXT
        )''')
        db.execute('''CREATE TABLE IF NOT EXISTS cosmetic_loadouts (
            user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
            slots TEXT NOT NULL
        )''')
        db.execute('''CREATE TABLE IF NOT EXISTS friend_appearances (
            user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
            skin TEXT NOT NULL, slim INTEGER NOT NULL
        )''')
        session_columns = {column['name'] for column in db.execute('PRAGMA table_info(sessions)')}
        if 'device' not in session_columns:
            db.execute('ALTER TABLE sessions ADD COLUMN device TEXT')
        db.execute('''CREATE TABLE IF NOT EXISTS profiles (
            user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
            public_id TEXT NOT NULL UNIQUE, published INTEGER NOT NULL DEFAULT 0,
            discord TEXT NOT NULL DEFAULT '', github TEXT NOT NULL DEFAULT '', avatar BLOB
        )''')
        db.execute('''CREATE TABLE IF NOT EXISTS profile_votes (
            profile_id INTEGER NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
            voter_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            value INTEGER NOT NULL CHECK(value IN (-1, 1)),
            PRIMARY KEY (profile_id, voter_id)
        )''')
        db.execute('''CREATE TABLE IF NOT EXISTS profile_comments (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            profile_id INTEGER NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
            author_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            body TEXT NOT NULL CHECK(length(body) BETWEEN 1 AND 500),
            created_at INTEGER NOT NULL
        )''')
        db.execute('CREATE INDEX IF NOT EXISTS profile_comments_recent ON profile_comments(profile_id, id DESC)')
        db.execute('''CREATE TABLE IF NOT EXISTS friendships (
            low_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            high_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            sender_id INTEGER NOT NULL CHECK(sender_id IN (low_id, high_id)),
            accepted INTEGER NOT NULL DEFAULT 0 CHECK(accepted IN (0,1)),
            PRIMARY KEY (low_id, high_id), CHECK(low_id < high_id)
        )''')
        db.execute('CREATE INDEX IF NOT EXISTS friendships_high ON friendships(high_id)')
        db.execute('''CREATE TABLE IF NOT EXISTS friend_messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            low_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            high_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            sender_id INTEGER NOT NULL CHECK(sender_id IN (low_id, high_id)),
            body TEXT NOT NULL CHECK(length(body) BETWEEN 1 AND 500),
            sent_at INTEGER NOT NULL,
            CHECK(low_id < high_id)
        )''')
        db.execute('CREATE INDEX IF NOT EXISTS friend_messages_pair ON friend_messages(low_id,high_id,id DESC)')
        message_columns = {column['name'] for column in db.execute('PRAGMA table_info(friend_messages)')}
        if 'envelope' not in message_columns:
            db.execute('ALTER TABLE friend_messages ADD COLUMN envelope TEXT')
        db.execute('''CREATE TABLE IF NOT EXISTS chat_keys (
            user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
            encryption TEXT NOT NULL, signing TEXT NOT NULL
        )''')
        if 'recovery' not in {column['name'] for column in db.execute('PRAGMA table_info(chat_keys)')}:
            db.execute('ALTER TABLE chat_keys ADD COLUMN recovery TEXT')
        db.execute('''CREATE TABLE IF NOT EXISTS friend_message_reactions (
            message_id INTEGER NOT NULL REFERENCES friend_messages(id) ON DELETE CASCADE,
            user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            reaction TEXT NOT NULL CHECK(reaction IN ('👍','❤️','😂','😮','😢')),
            created_at INTEGER NOT NULL,
            PRIMARY KEY(message_id,user_id,reaction)
        )''')
        profile_columns = {column['name'] for column in db.execute('PRAGMA table_info(profiles)')}
        for provider in PROVIDERS:
            column = provider + '_id'
            if column not in profile_columns:
                db.execute(f'ALTER TABLE profiles ADD COLUMN {column} TEXT')
            db.execute(f'CREATE UNIQUE INDEX IF NOT EXISTS profiles_{column}_unique ON profiles({column})')
        db.execute('''CREATE TABLE IF NOT EXISTS profile_oauth (
            session_hash TEXT NOT NULL REFERENCES sessions(token_hash) ON DELETE CASCADE,
            provider TEXT NOT NULL, state_hash TEXT NOT NULL, verifier TEXT NOT NULL,
            expires_at INTEGER NOT NULL, claimed INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY (session_hash, provider)
        )''')
        db.execute('''CREATE TABLE IF NOT EXISTS email_tokens (
            user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            purpose TEXT NOT NULL CHECK (purpose IN ('verify', 'reset')),
            token_hash TEXT NOT NULL UNIQUE,
            email TEXT NOT NULL,
            expires_at INTEGER NOT NULL,
            created_at INTEGER NOT NULL,
            PRIMARY KEY (user_id, purpose)
        )''')

def hash_password(password, salt=None):
    salt = salt or secrets.token_bytes(16)
    value = hashlib.scrypt(password.encode('utf-8'), salt=salt, n=16384, r=8, p=1, dklen=32)
    return 'scrypt$16384$8$1$' + salt.hex() + '$' + value.hex()

def verify(password, encoded):
    try:
        algorithm, n, r, p, salt, expected = encoded.split('$')
        if (algorithm, n, r, p) != ('scrypt', '16384', '8', '1'): return False
        return hmac.compare_digest(hash_password(password, bytes.fromhex(salt)), encoded)
    except (ValueError, TypeError): return False

DUMMY_HASH = hash_password('not-a-real-user-password')
def digest(token): return hashlib.sha256(token.encode()).hexdigest()
def error(code, status=400): return web.json_response({'error':code}, status=status, headers={'Cache-Control':'no-store'})
def limit(request, kind, max_count, seconds):
    now=time.monotonic(); ip=request.headers.get('X-Real-IP', request.remote or 'unknown'); key=(kind,ip)
    q=buckets[key]
    while q and q[0] <= now-seconds: q.popleft()
    if len(q)>=max_count: return False
    q.append(now)
    if len(buckets)>10000:
        for k in list(buckets):
            if not buckets[k] or buckets[k][-1] < now-3600: del buckets[k]
    return True

@web.middleware
async def security(request, handler):
    if request.method in ('POST','PUT','DELETE','PATCH'):
        if request.headers.get('Origin','').rstrip('/') not in TRUSTED_ORIGINS or request.headers.get('X-Orion-Request') != '1':
            return error('origin',403)
    try: response=await handler(request)
    except web.HTTPException as exc: response=exc
    response.headers['X-Content-Type-Options']='nosniff'
    response.headers['Referrer-Policy']='same-origin'
    response.headers['X-Frame-Options']='DENY'
    response.headers['Content-Security-Policy']="default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'"
    if request.path.startswith('/api/'): response.headers['Cache-Control']='no-store'
    return response

def session(request):
    token=request.cookies.get(COOKIE,'')
    if not re.fullmatch(r'[A-Za-z0-9_-]{40,100}',token): return None
    with connect() as db:
        return db.execute('SELECT u.id,u.username,u.email,u.email_verified_at,u.role,u.is_media,s.csrf FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>? AND u.banned_at IS NULL',(digest(token),int(time.time()))).fetchone()

def auth_csrf(request):
    user=session(request)
    if not user: return None
    if not hmac.compare_digest(user['csrf'], request.headers.get('X-CSRF-Token','')): return None
    return user

def session_device(request):
    agent = request.headers.get('User-Agent', '').lower()
    for marker, name in (
        ('gleam-launcher/', 'Gleam Launcher'),
        ('java-http-client/', 'Orion Client'), ('yabrowser/', 'Яндекс Браузер'),
        ('edg', 'Microsoft Edge'), ('opr/', 'Opera'), ('firefox/', 'Firefox'),
        ('chrome/', 'Chrome'), ('safari/', 'Safari'),
    ):
        if marker in agent:
            return name
    return None

def create_session(user_id, password_hash, request):
    token=secrets.token_urlsafe(32); csrf=secrets.token_urlsafe(32); now=int(time.time())
    with connect() as db:
        db.execute('DELETE FROM sessions WHERE expires_at<=?',(now,))
        inserted = db.execute('''INSERT INTO sessions (token_hash, user_id, csrf, expires_at, device)
            SELECT ?, id, ?, ?, ? FROM users WHERE id=? AND password_hash=? AND banned_at IS NULL''',
            (digest(token), csrf, now+TTL, session_device(request), user_id, password_hash))
        if inserted.rowcount != 1: return None
    return token,csrf

def set_cookie(response, token):
    response.set_cookie(COOKIE, token, max_age=TTL, httponly=True, secure=SECURE, samesite='Strict', path='/')

async def json_body(request):
    if request.content_type!='application/json': raise web.HTTPUnsupportedMediaType()
    try:
        data=await request.json()
        if not isinstance(data,dict): raise ValueError()
        return data
    except (ValueError,TypeError): raise web.HTTPBadRequest()

def validate(data):
    name=data.get('username',''); password=data.get('password','')
    if not valid_username(name): return None,'username'
    if not valid_password(password): return None,'password'
    return (name,password),None

def valid_username(name):
    return isinstance(name, str) and re.fullmatch(r'[A-Za-z0-9_]{3,24}', name) is not None

def valid_password(password):
    try:
        return isinstance(password, str) and 12 <= len(password) <= 128 and len(password.encode('utf8')) <= 256
    except UnicodeEncodeError:
        return False

def public_user(user):
    return {'username': user['username'], 'email': user['email'],
            'email_verified': user['email_verified_at'] is not None}

def normalize_email(value):
    if not isinstance(value, str):
        return None
    value = value.strip()
    local, separator, domain = value.rpartition('@')
    if not separator or len(value) > 254 or not 1 <= len(local) <= 64:
        return None
    atom = r"[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+"
    label = r'[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?'
    if not re.fullmatch(rf'{atom}(?:\.{atom})*', local):
        return None
    if not re.fullmatch(rf'{label}(?:\.{label})+', domain):
        return None
    return local + '@' + domain.lower()

async def registration_availability(request):
    if not limit(request, 'registration_availability', 30, 60): return error('rate', 429)
    data = await json_body(request)
    if data.keys() == {'username'}:
        name = data['username']
        if not valid_username(name): return error('username')
        query, value = 'SELECT 1 FROM users WHERE username_key=?', name.lower()
    elif data.keys() == {'email'}:
        email = normalize_email(data['email'])
        if email is None: return error('email')
        query, value = 'SELECT 1 FROM users WHERE email=?', email
    else:
        return error('invalid')
    with connect() as db:
        available = db.execute(query, (value,)).fetchone() is None
    return web.json_response({'available': available})

async def register(request):
    if not limit(request,'register',5,3600): return error('rate',429)
    data=await json_body(request); values,problem=validate(data)
    if problem: return error(problem)
    email = normalize_email(data['email']) if 'email' in data else None
    if 'email' in data and email is None: return error('email')
    name,password=values; hashed=await asyncio.to_thread(hash_password,password)
    try:
        with connect() as db:
            uid = db.execute('UPDATE user_id_sequence SET last_id=MAX(last_id,(SELECT COALESCE(MAX(id),0) FROM users))+1 RETURNING last_id').fetchone()[0]
            db.execute('INSERT INTO users(id,username,username_key,password_hash,created_at,email) VALUES(?,?,?,?,?,?)',
                       (uid, name, name.lower(), hashed, int(time.time()), email))
    except sqlite3.IntegrityError as exc:
        if str(exc) == 'UNIQUE constraint failed: users.email': return error('email_taken',409)
        if str(exc) == 'UNIQUE constraint failed: users.username_key': return error('taken',409)
        raise
    if email: queue_mail(request, 'verify', email, uid)
    token,csrf=create_session(uid,hashed,request); response=web.json_response({'user':{'username':name,'email':email,'email_verified':False},'csrf':csrf},status=201);set_cookie(response,token);return response

async def login(request):
    if not limit(request,'login',12,900): return error('rate',429)
    data=await json_body(request); name=data.get('username',''); password=data.get('password','')
    if not isinstance(name,str) or not isinstance(password,str) or len(name)>24 or len(password)>128: return error('credentials',401)
    with connect() as db: user=db.execute('SELECT id,username,password_hash,email,email_verified_at,banned_at FROM users WHERE username_key=?',(name.lower(),)).fetchone()
    try: valid=await asyncio.to_thread(verify,password,user['password_hash'] if user else DUMMY_HASH)
    except (ValueError,UnicodeEncodeError): valid=False
    if not user or not valid: return error('credentials',401)
    if user['banned_at'] is not None: return error('banned',403)
    old=request.cookies.get(COOKIE,'')
    if old:
        with connect() as db: db.execute('DELETE FROM sessions WHERE token_hash=?',(digest(old),))
    created = create_session(user['id'], user['password_hash'], request)
    if not created: return error('credentials',401)
    token,csrf=created;response=web.json_response({'user':public_user(user),'csrf':csrf});set_cookie(response,token);return response

def display_role(user):
    return 'media' if user['role'] == 'member' and user['is_media'] else user['role']

def session_payload(request):
    user=session(request)
    if not user: return {'user':None}
    with connect() as db:
        orders=[dict(r) for r in db.execute('SELECT id,amount_kopecks,currency,status,created_at FROM orders WHERE user_id=? ORDER BY created_at DESC LIMIT 10',(user['id'],)).fetchall()]
        plus_active, plus_expires_at = plus_status(db, user)
        avatar = db.execute('SELECT public_id, avatar FROM profiles WHERE user_id=? AND avatar IS NOT NULL',
                            (user['id'],)).fetchone()
    avatar_url = None
    if avatar:
        version = hashlib.sha256(avatar['avatar']).hexdigest()[:16]
        avatar_url = f"/api/profile/{avatar['public_id']}/avatar?v={version}"
    return {'user':public_user(user) | {'role': display_role(user)},'account_id':user['id'],'csrf':user['csrf'],'orders':orders,'plus_active':plus_active,'plus_expires_at':plus_expires_at,'avatar_url':avatar_url}

async def game_settings(request):
    writing = request.method == 'POST'
    user = auth_csrf(request) if writing else session(request)
    if not user: return error('auth', 401)
    if not limit(request, 'game-settings', 120, 60): return error('rate', 429)
    if writing:
        if request.content_type != 'application/json': return error('invalid', 415)
        body = bytearray()
        async for chunk in request.content.iter_chunked(16384):
            body.extend(chunk)
            if len(body) > 512 * 1024: return error('too_large', 413)
        try:
            data = json.loads(body)
            if not isinstance(data, dict) or set(data) != {'revision', 'minecraft', 'modules'}:
                return error('invalid')
            revision = data['revision']
            minecraft, modules = data['minecraft'], data['modules']
            if type(revision) is not int or not 0 <= revision < 2**63 - 1:
                return error('invalid')
            if minecraft is not None and (not isinstance(minecraft, str) or '\0' in minecraft or len(minecraft.encode()) > 128 * 1024):
                return error('invalid')
            if modules is not None and (not isinstance(modules, str) or len(modules.encode()) > 256 * 1024
                                        or not isinstance(json.loads(modules), dict)):
                return error('invalid')
        except (ValueError, UnicodeError, RecursionError):
            return error('invalid')
    with connect() as db:
        if writing: db.execute('BEGIN IMMEDIATE')
        row = db.execute('SELECT revision,minecraft,modules FROM game_settings WHERE user_id=?', (user['id'],)).fetchone()
        current = dict(row) if row else {'revision': 0, 'minecraft': None, 'modules': None}
        if writing and (minecraft, modules) != (current['minecraft'], current['modules']):
            if revision != current['revision']: return error('revision_conflict', 409)
            current = {'revision': revision + 1, 'minecraft': minecraft, 'modules': modules}
            db.execute('''INSERT INTO game_settings (user_id,revision,minecraft,modules) VALUES (?,?,?,?)
                ON CONFLICT(user_id) DO UPDATE SET revision=excluded.revision,minecraft=excluded.minecraft,modules=excluded.modules''',
                (user['id'], current['revision'], minecraft, modules))
    return web.json_response({'account_id': user['id'], **current}, dumps=lambda value: json.dumps(value, ensure_ascii=False))

COSMETIC_PETS = {'', 'ember_fox', 'moon_moth', 'moss_turtle', 'rhm_tarnkappe',
                 'snow_ferret', 'space_slime', 'tracked_scout'}

def valid_cosmetic_slots(slots):
    if not isinstance(slots, dict): return False
    for key, value in slots.items():
        if key in ('halo', 'hat', 'dance') and type(value) is bool: continue
        if key == 'wings' and value in ('', 'angel', 'dragon', 'midnight', 'evergreen', 'first_salvo', 'ash_phoenix', 'lunar_moth'): continue
        if key == 'pet' and isinstance(value, str) and value in COSMETIC_PETS: continue
        return False
    return True

async def cosmetic_loadout(request):
    writing = request.method == 'POST'
    user = auth_csrf(request) if writing else session(request)
    if not user: return error('auth', 401)
    if not limit(request, ('cosmetic-loadout', user['id']), 120, 60): return error('rate', 429)
    if writing:
        data = await json_body(request)
        changes, defaults = data.get('changes', {}), data.get('defaults', {})
        if (set(data) - {'changes', 'defaults'} or not (changes or defaults)
                or not valid_cosmetic_slots(changes) or not valid_cosmetic_slots(defaults)):
            return error('invalid')
    with connect() as db:
        if writing: db.execute('BEGIN IMMEDIATE')
        row = db.execute('SELECT slots FROM cosmetic_loadouts WHERE user_id=?', (user['id'],)).fetchone()
        slots = json.loads(row['slots']) if row else {}
        if writing:
            updated = defaults | slots | changes
            if updated != slots:
                db.execute('''INSERT INTO cosmetic_loadouts (user_id,slots) VALUES (?,?)
                    ON CONFLICT(user_id) DO UPDATE SET slots=excluded.slots''', (user['id'], json.dumps(updated)))
            slots = updated
    return web.json_response({'slots': slots})

async def me(request):
    return web.json_response(session_payload(request))

async def launcher_start(request):
    if not limit(request, 'launcher-start', 10, 900): return error('rate', 429)
    now = time.monotonic()
    pending = request.app[LAUNCHER_REQUESTS]
    for key in list(pending):
        if pending[key]['expires'] <= now: del pending[key]
    if len(pending) >= 1024: return error('rate', 429)
    secret = secrets.token_urlsafe(32)
    code = secrets.token_hex(4).upper()
    while any(item['code'] == code for item in pending.values()): code = secrets.token_hex(4).upper()
    pending[digest(secret)] = {'code': code, 'expires': now + 300, 'session': None, 'poll': 0}
    return web.json_response({'secret': secret, 'code': code, 'expires_in': 300,
                              'url': ORIGIN + '/auth.html?launcher=' + code})

async def launcher_approve(request):
    user = auth_csrf(request)
    if not user: return error('auth', 401)
    data = await json_body(request)
    code = data.get('code')
    if not isinstance(code, str) or not re.fullmatch(r'[A-F0-9]{8}', code): return error('launcher_expired', 410)
    if not limit(request, 'launcher-approve', 12, 900): return error('rate', 429)
    item = next((item for item in request.app[LAUNCHER_REQUESTS].values()
                 if hmac.compare_digest(item['code'], code) and item['expires'] > time.monotonic()), None)
    if not item: return error('launcher_expired', 410)
    if item['session'] is not None: return error('launcher_used', 409)
    item['session'] = digest(request.cookies[COOKIE])
    return web.json_response({'ok': True})

async def launcher_poll(request):
    data = await json_body(request)
    secret = data.get('secret')
    if not isinstance(secret, str) or not re.fullmatch(r'[A-Za-z0-9_-]{43}', secret): return error('launcher_expired', 410)
    pending = request.app[LAUNCHER_REQUESTS]
    key = digest(secret)
    item = pending.get(key)
    if not item or item['expires'] <= time.monotonic():
        pending.pop(key, None)
        return error('launcher_expired', 410)
    now = time.monotonic()
    if now - item['poll'] < 1: return error('rate', 429)
    item['poll'] = now
    if not item['session']: return web.json_response({'status': 'pending'})
    # Consume once and create a distinct launcher session. Revoking the browser
    # session or changing the password before polling cancels the approval.
    del pending[key]
    token = secrets.token_urlsafe(32)
    csrf = secrets.token_urlsafe(32)
    with connect() as db:
        inserted = db.execute('''INSERT INTO sessions (token_hash,user_id,csrf,expires_at,device)
            SELECT ?,s.user_id,?,?,? FROM sessions s JOIN users u ON u.id=s.user_id
            WHERE s.token_hash=? AND s.expires_at>? AND u.banned_at IS NULL''',
            (digest(token), csrf, int(time.time())+TTL, 'Gleam Launcher', item['session'], int(time.time())))
        if inserted.rowcount != 1: return error('auth', 401)
    return web.json_response({'status': 'approved', 'token': token, 'csrf': csrf})

async def profile_page(request):
    payload = session_payload(request)
    public_id = request.query.get('profile')
    if public_id is not None:
        row = visible_profile(request, public_id)
        public = profile_payload(row) if row else None
        payload = {'public_profile': public}
    else:
        public = None
    template = await asyncio.to_thread(Path('/var/www/orion-client/auth.html').read_text, encoding='utf-8')
    html = re.sub(r'(src|href)="(assets/|(?:style\.css|app\.js|auth\.js|i18n\.js)(?=[?"\s]))', r'\1="/\2', template)
    identity = public if public_id is not None else payload['user']
    if identity:
        html = html.replace('<body class="auth-page">', '<body class="auth-page profile-page">', 1)
        html = html.replace('<section class="auth-card"', '<section class="auth-card" hidden', 1)
        section_id, name_id, avatar_id = ('public-profile', 'public-profile-name', 'public-avatar') if public else (
            'auth-profile', 'profile-name', 'profile-avatar-image')
        section = f'id="{section_id}" aria-labelledby="{name_id}"'
        html = html.replace(section + ' hidden', section, 1)
        name = escape(identity['username'])
        html = html.replace(f'<h1 id="{name_id}"></h1>', f'<h1 id="{name_id}">{name}</h1>', 1)
        avatar_url = (public or payload).get('avatar_url')
        if avatar_url:
            avatar = escape(avatar_url, quote=True)
            html = html.replace(f'id="{avatar_id}" src="/assets/orion-badge.png"',
                                f'id="{avatar_id}" class="custom-avatar" src="{avatar}"', 1)
    state = json.dumps(payload, ensure_ascii=True).replace('<', '\\u003c')
    html = html.replace('</body>', f'<script type="application/json" id="orion-session">{state}</script>\n</body>', 1)
    return web.Response(text=html, content_type='text/html', headers={'Cache-Control': 'no-store'})

async def pricing(request):
    return web.json_response(await pricing_for_ip(request.headers.get('X-Real-IP', request.remote or '')))

def profile_payload(row, private=False):
    payload = {'id': row['public_id'], 'username': row['username'], 'created_at': row['created_at'], 'role': display_role(row),
               'has_avatar': bool(row['avatar']), 'published': bool(row['published']), 'banned_at': row['banned_at']}
    if row['avatar']:
        version = hashlib.sha256(row['avatar']).hexdigest()[:16]
        payload['avatar_url'] = f"/api/profile/{row['public_id']}/avatar?v={version}"
    for provider in PROVIDERS:
        payload[provider] = row[provider] if private or row[provider + '_id'] else ''
    if private:
        payload['connections'] = {
            provider: {'verified': bool(row[provider + '_id']), 'enabled': configuration(provider, TRUSTED_ORIGINS) is not None}
            for provider in PROVIDERS
        }
    return payload

async def own_profile(request):
    user = session(request)
    if not user: return error('auth', 401)
    with connect() as db:
        row = db.execute('SELECT p.*,u.username,u.created_at,u.role,u.is_media,u.banned_at FROM profiles p JOIN users u ON u.id=p.user_id WHERE u.id=?',
                         (user['id'],)).fetchone()
        if not row:
            db.execute('INSERT INTO profiles (user_id, public_id) VALUES (?,?) ON CONFLICT(user_id) DO NOTHING',
                       (user['id'], secrets.token_hex(8)))
            row = db.execute('SELECT p.*,u.username,u.created_at,u.role,u.is_media,u.banned_at FROM profiles p JOIN users u ON u.id=p.user_id WHERE u.id=?',
                             (user['id'],)).fetchone()
    return web.json_response(profile_payload(row, private=True))

async def save_profile(request):
    user = auth_csrf(request)
    if not user: return error('auth', 401)
    if not limit(request, 'profile', 20, 300): return error('rate', 429)
    data = await json_body(request)
    published = data.get('published')
    if type(published) is not bool: return error('invalid')
    with connect() as db:
        db.execute('''INSERT INTO profiles (user_id,public_id,published) VALUES (?,?,?)
            ON CONFLICT(user_id) DO UPDATE SET published=excluded.published''',
            (user['id'], secrets.token_hex(8), published))
    return await own_profile(request)

def oauth_provider(request):
    provider = request.match_info['provider']
    if provider not in PROVIDERS:
        raise web.HTTPNotFound()
    return provider

async def oauth_start(request):
    provider = oauth_provider(request)
    user = auth_csrf(request)
    if not user: return error('auth', 401)
    if not limit(request, 'profile_oauth_start', 12, 300): return error('rate', 429)
    config = configuration(provider, TRUSTED_ORIGINS)
    if not config: return error('oauth_disabled', 503)
    state, verifier = secrets.token_urlsafe(32), secrets.token_urlsafe(32)
    now = int(time.time())
    with connect() as db:
        db.execute('DELETE FROM profile_oauth WHERE expires_at<=?', (now,))
        db.execute('INSERT OR REPLACE INTO profile_oauth VALUES (?,?,?,?,?,0)',
                   (digest(request.cookies[COOKIE]), provider, digest(state), verifier, now + 600))
    return web.json_response({'url': authorization_url(provider, config, state, verifier)})

async def oauth_callback(request):
    provider = oauth_provider(request)
    state, code = request.query.get('state', ''), request.query.get('code', '')
    values = {'social': provider}
    if re.fullmatch(r'[A-Za-z0-9_-]{43}', state) and 1 <= len(code) <= 512:
        values.update(state=state, code=code)
    raise web.HTTPSeeOther('/auth.html#' + urlencode(values))

async def oauth_finish(request):
    provider = oauth_provider(request)
    user = auth_csrf(request)
    if not user: return error('auth', 401)
    if not limit(request, 'profile_oauth_finish', 15, 300): return error('rate', 429)
    config = configuration(provider, TRUSTED_ORIGINS)
    if not config: return error('oauth_disabled', 503)
    data = await json_body(request)
    state, code = data.get('state'), data.get('code')
    if not isinstance(state, str) or not re.fullmatch(r'[A-Za-z0-9_-]{43}', state):
        return error('oauth_state', 409)
    if not isinstance(code, str) or not 1 <= len(code) <= 512:
        return error('oauth_state', 409)
    current, state_hash = digest(request.cookies[COOKIE]), digest(state)
    with connect() as db:
        row = db.execute('''UPDATE profile_oauth SET claimed=1
            WHERE session_hash=? AND provider=? AND state_hash=? AND expires_at>? AND claimed=0
            RETURNING verifier''', (current, provider, state_hash, int(time.time()))).fetchone()
    if not row: return error('oauth_state', 409)
    try:
        identity = await verified_identity(provider, config, code, row['verifier'])
    except (ClientError, asyncio.TimeoutError, OSError, ValueError):
        return error('oauth_unavailable', 503)
    if not identity: return error('oauth_state', 409)
    provider_id, name = identity
    try:
        with connect() as db:
            now = int(time.time())
            deleted = db.execute('''DELETE FROM profile_oauth
                WHERE session_hash=? AND provider=? AND state_hash=? AND expires_at>?
                AND EXISTS (SELECT 1 FROM sessions WHERE token_hash=? AND expires_at>?)''',
                (current, provider, state_hash, now, current, now))
            if not deleted.rowcount: return error('oauth_state', 409)
            db.execute(f'''INSERT INTO profiles (user_id,public_id,{provider},{provider}_id) VALUES (?,?,?,?)
                ON CONFLICT(user_id) DO UPDATE SET {provider}=excluded.{provider}, {provider}_id=excluded.{provider}_id''',
                (user['id'], secrets.token_hex(8), name, provider_id))
    except sqlite3.IntegrityError:
        return error('oauth_taken', 409)
    return await own_profile(request)

async def oauth_unlink(request):
    provider = oauth_provider(request)
    user = auth_csrf(request)
    if not user: return error('auth', 401)
    with connect() as db:
        db.execute('''DELETE FROM profile_oauth WHERE provider=? AND session_hash IN
            (SELECT token_hash FROM sessions WHERE user_id=?)''', (provider, user['id']))
        db.execute(f"UPDATE profiles SET {provider}='', {provider}_id=NULL WHERE user_id=?", (user['id'],))
    return await own_profile(request)

async def save_avatar(request):
    user = auth_csrf(request)
    if not user: return error('auth', 401)
    if not limit(request, 'avatar', 12, 300): return error('rate', 429)
    data = await json_body(request)
    encoded = data.get('avatar')
    avatar = None
    if encoded is not None:
        if not isinstance(encoded, str) or len(encoded) > 3600: return error('avatar')
        try:
            avatar = base64.b64decode(encoded, validate=True)
        except (ValueError, binascii.Error):
            return error('avatar')
        if not valid_avatar(avatar):
            return error('avatar')
    with connect() as db:
        db.execute('''INSERT INTO profiles (user_id,public_id,avatar) VALUES (?,?,?)
            ON CONFLICT(user_id) DO UPDATE SET avatar=excluded.avatar''',
            (user['id'], secrets.token_hex(8), avatar))
    return await own_profile(request)

def valid_avatar(avatar):
    if not avatar.startswith(b'\xff\xd8') or not avatar.endswith(b'\xff\xd9'):
        return False
    offset = 2
    while offset + 9 < len(avatar):
        if avatar[offset] != 255:
            return False
        marker = avatar[offset + 1]
        length = int.from_bytes(avatar[offset + 2:offset + 4], 'big')
        if length < 2 or offset + 2 + length > len(avatar):
            return False
        if marker in (192, 194):
            return length >= 8 and avatar[offset + 5:offset + 9] == b'\x00\x40\x00\x40'
        if marker == 218:
            return False
        offset += 2 + length
    return False

def visible_profile(request, public_id=None):
    if public_id is None:
        public_id = request.match_info['profile_id']
    if not re.fullmatch(r'[a-f0-9]{16}', public_id): return None
    user = session(request)
    with connect() as db:
        return db.execute('''SELECT p.*,u.username,u.created_at,u.role,u.is_media,u.banned_at FROM profiles p JOIN users u ON u.id=p.user_id
            WHERE p.public_id=? AND (p.published=1 OR p.user_id=?)''',
            (public_id, user['id'] if user else None)).fetchone()

async def public_profile(request):
    row = visible_profile(request)
    if not row: return error('profile_missing', 404)
    return web.json_response(profile_payload(row))

async def profile_avatar(request):
    row = visible_profile(request)
    if not row or not row['avatar']: raise web.HTTPNotFound()
    return web.Response(body=row['avatar'], content_type='image/jpeg')

def published_profile(db, request):
    identifier = request.match_info['profile_id']
    if not re.fullmatch(r'[a-f0-9]{16}', identifier):
        raise web.HTTPNotFound()
    row = db.execute('SELECT user_id FROM profiles WHERE public_id=? AND published=1', (identifier,)).fetchone()
    if not row:
        raise web.HTTPNotFound()
    return row['user_id']

def plus_status(db, user):
    if user['role'] in ('owner', 'admin'):
        return True, None
    entitlement = db.execute('SELECT expires_at FROM plus_access WHERE user_id=?', (user['id'],)).fetchone()
    expires_at = funpay_sales.plus_expiry(db, user['id'], entitlement['expires_at'] if entitlement else None)
    if expires_at is not None and expires_at >= funpay_sales.PERMANENT:
        return True, None
    return expires_at is not None and expires_at > int(time.time()), expires_at

async def catalog_access(request):
    user = session(request)
    if not user:
        return error('auth', 401)
    with connect() as db:
        active, _ = plus_status(db, user)
    return web.json_response({'account_id': user['id'], 'limit': 10 if active else 3})

async def fake_punishments(request):
    publish = request.method == 'POST'
    data = await json_body(request) if publish else request.query
    user = auth_csrf(request) if publish else session(request)
    if not user:
        return error('auth', 401)
    scope = data.get('scope')
    if not isinstance(scope, str) or not re.fullmatch(r'[a-f0-9]{64}', scope):
        return error('invalid')
    events = request.app[FAKE_PUNISHMENTS]
    epoch = request.app[FAKE_PUNISHMENT_EPOCH]
    cursor = events[-1][3]['id'] if events else 0
    now = time.monotonic()
    if not publish:
        if not limit(request, 'fake-punishment-feed', 180, 60):
            return error('rate', 429)
        after = data.get('after', '')
        match = re.fullmatch(r'([a-f0-9]{16})-([0-9]{1,18})', after)
        items = []
        if match and match[1] == epoch:
            sequence = int(match[2])
            items = [event for created, server, owner, event in events
                     if event['id'] > sequence and now - created <= 25 and server == scope and owner != user['id']]
        return web.json_response({'cursor': f'{epoch}-{cursor}', 'items': items[-24:]})

    with connect() as db:
        active, _ = plus_status(db, user)
    if not active:
        return error('plus_required', 403)
    kind, duration, reason = data.get('kind'), data.get('duration'), data.get('reason')
    moderator, target = data.get('moderator'), data.get('target')
    if kind not in ('ban', 'mute') or not all(isinstance(name, str) and re.fullmatch(r'[A-Za-z0-9_]{1,16}', name)
                                            for name in (moderator, target)):
        return error('invalid')
    if not isinstance(reason, str) or not 1 <= len(reason.strip()) <= 256 or not reason.isprintable():
        return error('invalid')
    if duration is None:
        if kind != 'ban':
            return error('invalid')
    elif not isinstance(duration, str) or not 1 <= len(duration) <= 16 or not duration.isprintable() or any(c.isspace() for c in duration):
        return error('invalid')
    if (not limit(request, 'fake-punishment-publish', 20, 60)
            or not limit(request, f'fake-punishment-user-{user["id"]}', 3, 15)):
        return error('rate', 429)
    event = {'id': cursor + 1, 'author': user['username'], 'kind': kind, 'duration': duration,
             'moderator': moderator, 'target': target, 'reason': reason.strip()}
    events.append((now, scope, user['id'], event))
    return web.json_response({'ok': True})

async def search_profiles(request):
    query = request.query.get('q', '').strip()
    if not re.fullmatch(r'[A-Za-z0-9_]{2,24}', query):
        return web.json_response({'items': []})
    if not limit(request, 'profile-search', 90, 60):
        return error('rate', 429)
    key = query.lower()
    with connect() as db:
        rows = db.execute('''SELECT p.public_id, u.username,
            CASE WHEN u.role='member' AND u.is_media=1 THEN 'media' ELSE u.role END AS role,
            p.avatar IS NOT NULL AS has_avatar
            FROM users u JOIN profiles p ON p.user_id=u.id
            WHERE p.published=1 AND u.username_key>=? AND u.username_key<?
            ORDER BY u.username_key LIMIT 12''', (key, key + '~')).fetchall()
    return web.json_response({'items': [dict(row) for row in rows]})

async def friends_list(request):
    user = session(request)
    if not user: return error('auth', 401)
    uid = user['id']
    with connect() as db:
        columns = {column['name'] for column in db.execute('PRAGMA table_info(users)')}
        role = "CASE WHEN u.role='member' AND u.is_media=1 THEN 'media' ELSE u.role END" if 'is_media' in columns else 'u.role'
        rows = db.execute(f'''SELECT u.username, {role} AS role,
            CASE WHEN p.published=1 THEN p.public_id END AS public_id,
            CASE WHEN f.accepted=1 THEN 'friend' WHEN f.sender_id=:uid THEN 'outgoing' ELSE 'incoming' END AS status
            FROM friendships f JOIN users u ON u.id=CASE WHEN f.low_id=:uid THEN f.high_id ELSE f.low_id END
            LEFT JOIN profiles p ON p.user_id=u.id
            WHERE f.low_id=:uid OR f.high_id=:uid ORDER BY u.username_key''', {'uid': uid}).fetchall()
        items = [dict(row) for row in rows]
        if request.query.get('appearance') == '1':
            appearances = db.execute('''SELECT u.username,a.skin,a.slim,c.slots,p.avatar IS NOT NULL AS has_avatar
                FROM friendships f JOIN users u ON u.id=CASE WHEN f.low_id=:uid THEN f.high_id ELSE f.low_id END
                JOIN profiles p ON p.user_id=u.id AND p.published=1
                LEFT JOIN friend_appearances a ON a.user_id=u.id
                LEFT JOIN cosmetic_loadouts c ON c.user_id=u.id
                WHERE f.accepted=1 AND (f.low_id=:uid OR f.high_id=:uid) AND u.banned_at IS NULL''', {'uid': uid}).fetchall()
            by_name = {row['username']: row for row in appearances}
            for item in items:
                row = by_name.get(item['username'])
                if row:
                    item['appearance'] = {'skin': row['skin'], 'slim': bool(row['slim']), 'avatar': bool(row['has_avatar']),
                                          'slots': json.loads(row['slots']) if row['slots'] else {}}
    return web.json_response({'items': items}, dumps=lambda value: json.dumps(value, ensure_ascii=False))

async def friends_update(request):
    user = auth_csrf(request)
    if not user: return error('auth', 401)
    if not limit(request, 'friends-update', 50, 60): return error('rate', 429)
    data = await json_body(request)
    username, action = data.get('username'), data.get('action')
    if action == 'appearance':
        skin, slim = data.get('skin'), data.get('slim')
        if not isinstance(skin, str) or not re.fullmatch(r'(?:[a-f0-9]{1,64})?', skin) or type(slim) is not bool:
            return error('invalid')
        with connect() as db:
            db.execute('''INSERT INTO friend_appearances(user_id,skin,slim) VALUES (?,?,?)
                ON CONFLICT(user_id) DO UPDATE SET skin=excluded.skin,slim=excluded.slim
                WHERE skin<>excluded.skin OR slim<>excluded.slim''', (user['id'], skin, int(slim)))
        return web.json_response({'ok': True})
    if not valid_username(username) or action not in ('request', 'accept', 'decline', 'cancel', 'remove'):
        return error('invalid')
    uid = user['id']
    with connect() as db:
        db.execute('BEGIN IMMEDIATE')
        other = db.execute('SELECT id,banned_at FROM users WHERE username_key=?', (username.lower(),)).fetchone()
        if not other: return error('friend_missing', 404)
        if other['id'] == uid: return error('friend_self')
        pair = tuple(sorted((uid, other['id'])))
        row = db.execute('SELECT sender_id,accepted FROM friendships WHERE low_id=? AND high_id=?', pair).fetchone()
        state = ('none' if row is None else 'friend' if row['accepted']
                 else 'outgoing' if row['sender_id'] == uid else 'incoming')
        if state != {'request': 'none', 'accept': 'incoming', 'decline': 'incoming',
                     'cancel': 'outgoing', 'remove': 'friend'}[action]:
            return error('friend_state', 409)
        if action in ('request', 'accept'):
            if other['banned_at'] is not None: return error('friend_unavailable', 409)
            count = db.execute('''SELECT COUNT(*) FROM friendships
                WHERE (low_id=? OR high_id=?) AND accepted=1''', (uid, uid)).fetchone()[0]
            other_count = db.execute('''SELECT COUNT(*) FROM friendships
                WHERE (low_id=? OR high_id=?) AND accepted=1''', (other['id'], other['id'])).fetchone()[0]
            if max(count, other_count) >= 200: return error('friend_limit', 409)
        if action == 'request':
            outgoing = db.execute('SELECT COUNT(*) FROM friendships WHERE sender_id=? AND accepted=0', (uid,)).fetchone()[0]
            incoming = db.execute('''SELECT COUNT(*) FROM friendships
                WHERE (low_id=? OR high_id=?) AND sender_id<>? AND accepted=0''', (other['id'], other['id'], other['id'])).fetchone()[0]
            if outgoing >= 20 or incoming >= 100: return error('friend_limit', 409)
            db.execute('INSERT INTO friendships (low_id,high_id,sender_id) VALUES (?,?,?)', (*pair, uid))
        elif action == 'accept':
            db.execute('UPDATE friendships SET accepted=1 WHERE low_id=? AND high_id=?', pair)
        else:
            db.execute('DELETE FROM friendships WHERE low_id=? AND high_id=?', pair)
    return web.json_response({'ok': True})


def world_endpoint(request, data):
    port, pin, addresses = data.get('port'), data.get('pin'), data.get('addresses')
    if type(port) is not int or not 1024 <= port <= 65535 or not isinstance(pin, str) or not re.fullmatch(r'[0-9a-f]{64}', pin):
        return None
    if not isinstance(addresses, list) or len(addresses) > 8:
        return None
    result = []
    local = []
    observed = None
    for index, raw in enumerate([request.headers.get('X-Real-IP', request.remote), *addresses]):
        try:
            if not isinstance(raw, str) or len(raw) > 45 or '%' in raw:
                continue
            ip = ipaddress.ip_address(raw)
        except ValueError:
            continue
        if ip.is_global and not ip.is_multicast and not ip.is_reserved:
            if index == 0:
                observed = str(ip)
            if str(ip) not in result:
                result.append(str(ip))
        elif index and (ip in ipaddress.ip_network('10.0.0.0/8') or ip in ipaddress.ip_network('172.16.0.0/12')
                        or ip in ipaddress.ip_network('192.168.0.0/16')) and str(ip) not in local:
            local.append(str(ip))
    return {'port': port, 'pin': pin, 'addresses': result, 'observed': observed, 'local': local}


def direct_ice_description(value):
    return (isinstance(value, str) and len(value) <= 3000 and value.isascii()
            and value.startswith('v=0\r\n') and '\r\nm=application ' in value
            and '\r\na=fingerprint:' in value
            and not any(line.startswith('a=candidate:') and ' typ relay' in line for line in value.splitlines()))


async def friend_worlds(request):
    writing = request.method == 'POST'
    user = auth_csrf(request) if writing else session(request)
    if not user:
        return error('auth', 401)
    if not limit(request, 'friend-worlds-write' if writing else 'friend-worlds-read', 100 if writing else 140, 60):
        return error('rate', 429)
    data = await json_body(request) if writing else {}
    uid, now = user['id'], time.monotonic()
    presence, invites = request.app[WORLD_PRESENCE], request.app[WORLD_INVITES]
    for key, entry in list(presence.items()):
        if entry['until'] <= now:
            del presence[key]
    with connect() as db:
        rows = db.execute('''SELECT u.id,u.username,CASE WHEN p.published=1 THEN p.public_id END AS public_id
            FROM friendships f JOIN users u ON u.id=CASE WHEN f.low_id=:uid THEN f.high_id ELSE f.low_id END
            LEFT JOIN profiles p ON p.user_id=u.id WHERE (f.low_id=:uid OR f.high_id=:uid)
            AND f.accepted=1 AND u.banned_at IS NULL''', {'uid': uid}).fetchall()
    friends = {row['id']: row for row in rows}
    for key, entry in list(invites.items()):
        open_world = presence.get((entry['host'], 'game'), {}).get('game')
        if entry['until'] <= now or not open_world:
            del invites[key]
        elif uid in (entry['host'], entry['guest']) and (entry['guest'] if uid == entry['host'] else entry['host']) not in friends:
            del invites[key]
    action = data.get('action')
    retry_id = None
    if action == 'presence':
        device = data.get('device')
        if device not in ('launcher', 'game'):
            return error('invalid')
        entry = {'until': now + 15}
        world = data.get('world')
        if device == 'game' and world is not None:
            version, name = data.get('version'), data.get('world')
            endpoint = world_endpoint(request, data)
            if not endpoint or not isinstance(version, str) or version not in WORLD_VERSIONS \
                    or not isinstance(name, str) or not 1 <= len(name) <= 80 or not name.isprintable():
                return error('invalid')
            entry['game'] = {'world': name, 'version': version, 'offline': data.get('offline') is True,
                             'endpoint': endpoint, 'directIce': data.get('directIce') is True}
        previous = presence.get((uid, device), {})
        old = previous.get('game')
        current = entry.get('game')
        if device == 'game' and old and (not current or old['endpoint']['pin'] != current['endpoint']['pin']):
            for key, invite in list(invites.items()):
                if invite['host'] == uid:
                    del invites[key]
        elif old and current and old['offline'] != current['offline']:
            for invite in invites.values():
                if invite['host'] == uid:
                    invite['world'] = current
        presence[uid, device] = entry
        if current:
            for invite in invites.values():
                if invite['host'] == uid and invite['state'] == 'connected':
                    invite['until'] = now + 15
    elif action == 'invite':
        name = data.get('username')
        guest = next((row['id'] for row in friends.values() if row['username'] == name), None)
        world = presence.get((uid, 'game'), {}).get('game')
        if guest is None or not world:
            return error('world_unavailable', 409)
        if not limit(request, 'friend-world-invite', 12, 60):
            return error('rate', 429)
        if len(invites) >= 2000 or sum(entry['host'] == uid for entry in invites.values()) >= 12:
            return error('rate', 429)
        if any(e['host'] == uid and e['guest'] == guest and e['state'] != 'pending' for e in invites.values()):
            return error('world_unavailable', 409)
        for key, entry in list(invites.items()):
            if entry['host'] == uid and entry['guest'] == guest:
                del invites[key]
        invites[secrets.token_urlsafe(24)] = {'host': uid, 'guest': guest, 'world': world,
                                             'state': 'pending', 'until': now + 15}
    elif action in ('reserve', 'accept', 'ice-answer', 'retry', 'connected', 'decline', 'cancel', 'finish'):
        key = data.get('id')
        entry = invites.get(key) if isinstance(key, str) else None
        if not entry:
            return error('world_expired', 409)
        host, guest = entry['host'] == uid, entry['guest'] == uid
        if action == 'reserve' and guest and entry['state'] == 'pending':
            entry.update(state='reserved', until=now + 180, autojoin=data.get('autojoin') is True)
        elif action == 'accept' and guest and entry['state'] in ('pending', 'reserved'):
            endpoint = world_endpoint(request, data)
            name, offline = data.get('name'), data.get('offline') is True
            if not endpoint or not isinstance(name, str) or not re.fullmatch(r'[A-Za-z0-9_]{1,16}', name):
                return error('invalid')
            if offline and not entry['world']['offline']:
                return error('world_license', 409)
            offer = data.get('iceOffer')
            if offer is not None:
                if not direct_ice_description(offer):
                    return error('invalid')
                if entry['world'].get('directIce'):
                    entry['iceOffer'] = offer
            entry.update(state='accepted', until=now + 180, endpoint=endpoint, name=name, offline=offline)
        elif action == 'ice-answer' and host and entry['state'] == 'accepted' and 'iceOffer' in entry:
            answer = data.get('iceAnswer')
            if not direct_ice_description(answer) or entry.get('iceAnswer', answer) != answer:
                return error('invalid')
            entry['iceAnswer'] = answer
        elif action == 'retry' and guest and entry['state'] == 'accepted':
            retry_id = secrets.token_urlsafe(24)
            invites[retry_id] = {'host': entry['host'], 'guest': uid, 'world': entry['world'],
                                 'state': 'reserved', 'until': now + 180, 'autojoin': False}
            del invites[key]
        elif action == 'connected' and host and entry['state'] == 'accepted':
            entry.update(state='connected', until=now + 15)
        elif ((action == 'decline' and guest and entry['state'] in ('pending', 'reserved'))
              or (action == 'cancel' and host) or (action == 'finish' and (host or guest) and entry['state'] in ('accepted', 'connected'))):
            del invites[key]
        else:
            return error('world_expired', 409)
    elif writing:
        return error('invalid')

    people = {}
    for friend_id, row in friends.items():
        game = presence.get((friend_id, 'game'))
        launcher = presence.get((friend_id, 'launcher'))
        world = game.get('game') if game else None
        people[row['username']] = {
            'status': 'world' if world else 'game' if game else 'launcher' if launcher else 'offline',
            'world': world['world'] if world else None,
            'version': world['version'] if world else None,
        }
    items = []
    for key, entry in invites.items():
        host = entry['host'] == uid
        peer = friends.get(entry['guest'] if host else entry['host']) if host or entry['guest'] == uid else None
        if peer is None:
            continue
        world = entry['world']
        item = {'id': key, 'username': peer['username'], 'public_id': peer['public_id'],
                'incoming': not host, 'state': entry['state'], 'remaining': max(0, int((entry['until'] - now) * 1000)),
                'world': world['world'], 'version': world['version'], 'allowOffline': world['offline']}
        if world.get('directIce'):
            item['directIce'] = True
        if not host and entry['state'] == 'reserved':
            item['autojoin'] = entry.get('autojoin', False)
        if entry['state'] == 'accepted' and request.query.get('transport') == '1':
            remote, own = (entry['endpoint'], world['endpoint']) if host else (world['endpoint'], entry['endpoint'])
            same_network = own['observed'] and own['observed'] == remote['observed']
            item['endpoint'] = {'port': remote['port'], 'pin': remote['pin'],
                                'addresses': (remote['local'] if same_network else []) + remote['addresses']}
            item['player'] = entry['name']
            item['offline'] = entry['offline']
            if host:
                item['guestId'] = entry['guest']
                if 'iceOffer' in entry:
                    item['iceOffer'] = entry['iceOffer']
            elif 'iceAnswer' in entry:
                item['iceAnswer'] = entry['iceAnswer']
        items.append(item)
    response = {'items': items, 'people': people, 'hosting': 'game' in presence.get((uid, 'game'), {})}
    if retry_id is not None:
        response['retryId'] = retry_id
    return web.json_response(response)

async def chat_keys(request):
    writing = request.method == 'POST'
    user = auth_csrf(request) if writing else session(request)
    if not user: return error('auth', 401)
    if not limit(request, 'chat-keys', 120, 60): return error('rate', 429)
    uid = user['id']
    if writing:
        data = await json_body(request)
        keys = (data.get('encryption'), data.get('signing'))
        try:
            if any(not isinstance(key, str) or len(key) > 800 for key in keys): return error('invalid')
            encryption, signing = (base64.b64decode(key, validate=True) for key in keys)
            if len(encryption) != 422 or len(signing) != 44: return error('invalid')
        except (ValueError, binascii.Error):
            return error('invalid')
        with connect() as db:
            db.execute('INSERT OR IGNORE INTO chat_keys(user_id,encryption,signing) VALUES (?,?,?)', (uid, *keys))
            row = db.execute('SELECT encryption,signing FROM chat_keys WHERE user_id=?', (uid,)).fetchone()
            if tuple(row) != keys: return error('chat_device', 409)
        return web.json_response({'ok': True})
    peer = request.match_info['username']
    if not valid_username(peer): return error('invalid')
    with connect() as db:
        row = db.execute('''SELECT k.encryption,k.signing FROM users u
            JOIN friendships f ON f.low_id=min(u.id,?) AND f.high_id=max(u.id,?) AND f.accepted=1
            JOIN chat_keys k ON k.user_id=u.id WHERE u.username_key=? AND u.banned_at IS NULL''',
                         (uid, uid, peer.lower())).fetchone()
    if not row: return error('chat_key_missing', 404)
    return web.json_response(dict(row), headers={'Cache-Control': 'no-store'})

async def chat_recovery(request):
    writing = request.method == 'POST'
    user = auth_csrf(request) if writing else session(request)
    if not user: return error('auth', 401)
    if not limit(request, 'chat-recovery', 30, 60): return error('rate', 429)
    uid = user['id']
    if writing:
        data = await json_body(request)
        backup = data.get('backup')
        if not isinstance(backup, str) or len(backup) > 3800: return error('invalid')
        try:
            packet = base64.b64decode(backup, validate=True)
            if not 1700 <= len(packet) <= 2850 or packet[0] != 1: return error('invalid')
        except (ValueError, binascii.Error):
            return error('invalid')
        with connect() as db:
            db.execute('UPDATE chat_keys SET recovery=? WHERE user_id=? AND recovery IS NULL', (backup, uid))
            stored = db.execute('SELECT recovery FROM chat_keys WHERE user_id=?', (uid,)).fetchone()
            if not stored: return error('chat_key_missing', 409)
            if stored['recovery'] != backup: return error('chat_recovery_exists', 409)
        return web.json_response({'ok': True})
    with connect() as db:
        row = db.execute('SELECT encryption,signing,recovery AS backup FROM chat_keys WHERE user_id=?', (uid,)).fetchone()
    return web.json_response(dict(row) if row else {})

async def chat_transfer(request):
    user = auth_csrf(request)
    if not user: return error('auth', 401)
    data = await json_body(request)
    action, key = data.get('action'), data.get('id')
    if action not in ('start', 'approve', 'poll', 'cancel') or not isinstance(key, str) or not re.fullmatch(r'[0-9a-f]{64}', key):
        return error('invalid')
    if not limit(request, 'chat-transfer-'+action, 75 if action == 'poll' else 12, 60):
        return error('rate', 429)
    transfers, now = request.app[CHAT_TRANSFERS], time.monotonic()
    for stale in [key for key, item in transfers.items() if item['expires'] <= now]:
        del transfers[stale]
    scope = (user['id'], key)
    session_id = digest(request.cookies[COOKIE])
    entry = transfers.get(scope)
    if action == 'start':
        if entry or sum(uid == user['id'] for uid, _ in transfers) >= 5 or len(transfers) >= 1024:
            return error('chat_transfer_busy', 409)
        with connect() as db:
            if not db.execute('SELECT 1 FROM chat_keys WHERE user_id=?', (user['id'],)).fetchone():
                return error('chat_key_missing', 409)
        transfers[scope] = {'expires': now + 300, 'session': session_id, 'backup': None}
        return web.json_response({'ok': True})
    if not entry: return error('chat_transfer_expired', 404)
    if action == 'approve':
        if entry['session'] == session_id: return error('chat_transfer_same_session', 409)
        backup = data.get('backup')
        if not isinstance(backup, str) or len(backup) > 3800: return error('invalid')
        try:
            packet = base64.b64decode(backup, validate=True)
            if not 1700 <= len(packet) <= 2850 or packet[0] != 2: return error('invalid')
        except (ValueError, binascii.Error):
            return error('invalid')
        if entry['backup'] is not None: return error('chat_transfer_busy', 409)
        entry['backup'] = backup
        return web.json_response({'ok': True})
    if entry['session'] != session_id: return error('auth', 401)
    if action == 'cancel':
        del transfers[scope]
        return web.json_response({'ok': True})
    return web.json_response({'backup': entry['backup']})

async def chat_updates(request):
    user = session(request)
    if not user: return error('auth', 401)
    if not limit(request, 'chat-updates', 30, 60): return error('rate', 429)
    after = request.query.get('after')
    preview = request.query.get('preview') == '1'
    if after is not None and not re.fullmatch(r'[0-9]{1,18}', after): return error('invalid')
    uid = user['id']
    with connect() as db:
        db.execute('BEGIN')
        latest = db.execute('SELECT COALESCE(MAX(id),0) FROM friend_messages').fetchone()[0]
        if after is None:
            cursor = latest
            rows = []
        else:
            cursor = int(after)
            fields = 'm.id,u.username,m.body,m.envelope' if preview else 'm.id,u.username'
            rows = db.execute(f'''SELECT {fields} FROM friend_messages m
                JOIN friendships f ON f.low_id=m.low_id AND f.high_id=m.high_id AND f.accepted=1
                JOIN users u ON u.id=m.sender_id AND u.banned_at IS NULL
                WHERE m.id>? AND (m.low_id=? OR m.high_id=?) AND m.sender_id<>?
                ORDER BY m.id LIMIT 100''', (cursor, uid, uid, uid)).fetchall()
            cursor = rows[-1]['id'] if len(rows) == 100 else max(cursor, latest)
        items = [dict(row) for row in rows]
        if preview:
            for item in items:
                del item['envelope' if item['envelope'] is None else 'body']
    return web.json_response({'cursor': cursor, 'items': items},
                             headers={'Cache-Control': 'no-store'})

async def friend_chat(request):
    writing = request.method == 'POST'
    user = auth_csrf(request) if writing else session(request)
    if not user: return error('auth', 401)
    if not limit(request, 'chat-send' if writing else 'chat-read', 30 if writing else 120, 60):
        return error('rate', 429)
    name = request.match_info['username']
    before = request.query.get('before', '')
    if not valid_username(name) or (before and not re.fullmatch(r'[1-9][0-9]{0,17}', before)):
        return error('invalid')
    body = None
    reaction = None
    envelope = None
    secure = request.query.get('secure') == '1'
    selected_reaction = None
    message_id = None
    if writing:
        payload = await json_body(request)
        if 'reaction' in payload:
            reaction = payload.get('reaction')
            message_id = payload.get('message_id')
            selected_reaction = payload.get('selected')
            if (reaction not in ('👍', '❤️', '😂', '😮', '😢') or type(message_id) is not int
                    or not 0 < message_id < 2**63 or (secure and type(selected_reaction) is not bool)):
                return error('invalid')
        else:
            if secure:
                envelope = payload.get('envelope')
                if not isinstance(envelope, str) or len(envelope) > 3820: return error('invalid')
                try:
                    if not 861 <= len(base64.b64decode(envelope, validate=True)) <= 2860: return error('invalid')
                except (ValueError, binascii.Error):
                    return error('invalid')
                body = '[encrypted]'
            else:
                body = payload.get('body')
                if not isinstance(body, str) or not 1 <= len(body.strip()) <= 500 or '\x00' in body:
                    return error('invalid')
                body = body.strip()
    with connect() as db:
        db.execute('BEGIN IMMEDIATE' if writing else 'BEGIN')
        peer = db.execute('SELECT id FROM users WHERE username_key=? AND banned_at IS NULL', (name.lower(),)).fetchone()
        if peer is None or peer['id'] == user['id']: return error('chat_unavailable', 403)
        pair = tuple(sorted((user['id'], peer['id'])))
        if not db.execute('SELECT 1 FROM friendships WHERE low_id=? AND high_id=? AND accepted=1', pair).fetchone():
            return error('chat_unavailable', 403)
        if writing:
            if reaction is not None:
                if not db.execute('SELECT 1 FROM friend_messages WHERE id=? AND low_id=? AND high_id=?',
                                  (message_id, *pair)).fetchone():
                    return error('chat_unavailable', 403)
                values = (message_id, user['id'], reaction)
                if selected_reaction is None:
                    selected_reaction = not db.execute('SELECT 1 FROM friend_message_reactions WHERE message_id=? AND user_id=? AND reaction=?', values).fetchone()
                if selected_reaction:
                    db.execute('DELETE FROM friend_message_reactions WHERE message_id=? AND user_id=?', values[:2])
                    db.execute('INSERT INTO friend_message_reactions VALUES (?,?,?,?)', (*values, int(time.time())))
                else:
                    db.execute('DELETE FROM friend_message_reactions WHERE message_id=? AND user_id=? AND reaction=?', values)
            else:
                key_count = db.execute('SELECT count(*) FROM chat_keys WHERE user_id IN (?,?)', pair).fetchone()[0]
                if envelope is None and key_count:
                    return error('chat_encryption_required', 409)
                if envelope is not None and key_count != 2:
                    return error('chat_key_missing', 409)
                db.execute('INSERT INTO friend_messages(low_id,high_id,sender_id,body,sent_at,envelope) VALUES (?,?,?,?,?,?)',
                           (*pair, user['id'], body, int(time.time()), envelope))
        rows = db.execute('''SELECT id, sender_id=? AS own, body, sent_at, envelope FROM friend_messages
            WHERE low_id=? AND high_id=? AND (? OR envelope IS NULL) AND id < ?
            ORDER BY id DESC LIMIT 51''',
            (user['id'], *pair, secure, int(before) if before and not writing else 9223372036854775807)).fetchall()
        selected = list(reversed(rows[:50]))
        if reaction is not None and secure:
            selected = db.execute('SELECT id FROM friend_messages WHERE id=?', (message_id,)).fetchall()
        reaction_map = {}
        if selected and (reaction is not None or request.query.get('reactions') != '0'):
            ids = [row['id'] for row in selected]
            placeholders = ','.join('?' for _ in ids)
            reaction_rows = db.execute(f'''SELECT message_id,user_id,reaction FROM friend_message_reactions
                WHERE message_id IN ({placeholders}) ORDER BY message_id,reaction''', ids).fetchall()
            for row in reaction_rows:
                groups = reaction_map.setdefault(row['message_id'], {})
                item = groups.setdefault(row['reaction'], {'emoji': row['reaction'], 'count': 0, 'mine': False})
                item['count'] += 1
                item['mine'] = item['mine'] or row['user_id'] == user['id']
        items = []
        for row in selected:
            item = dict(row)
            if reaction is not None and secure:
                return web.json_response({'id': message_id, 'reactions': list(reaction_map.get(message_id, {}).values())},
                                         headers={'Cache-Control': 'no-store'})
            if item['envelope'] is None:
                del item['envelope']
            else:
                del item['body']
            item['own'] = bool(item['own'])
            item['reactions'] = list(reaction_map.get(row['id'], {}).values())
            items.append(item)
    return web.json_response({'items': items, 'older': len(rows) > 50},
                             headers={'Cache-Control': 'no-store'})

async def admin_accounts(request):
    user = session(request)
    if not user: return error('auth', 401)
    if user['role'] not in ('admin', 'owner'): return error('forbidden', 403)
    if not limit(request, 'admin-accounts', 80, 60): return error('rate', 429)
    query = request.query.get('q', '').strip().lower()
    if query and not re.fullmatch(r'[a-z0-9_]{1,24}', query): return error('invalid')
    with connect() as db:
        rows = db.execute('''SELECT u.username,
            CASE WHEN u.role='member' AND u.is_media=1 THEN 'media' ELSE u.role END AS role,
            u.created_at, u.banned_at, COALESCE(p.published,0) AS published
            FROM users u LEFT JOIN profiles p ON p.user_id=u.id
            WHERE u.username_key>=? AND u.username_key<? ORDER BY u.id DESC LIMIT 30''',
            (query, query + '~')).fetchall()
    return web.json_response({'items': [dict(row) for row in rows], 'role': user['role'], 'username': user['username']})

async def admin_update(request):
    user = auth_csrf(request)
    if not user: return error('auth', 401)
    if user['role'] not in ('admin', 'owner'): return error('forbidden', 403)
    if not limit(request, 'admin-update', 30, 60): return error('rate', 429)
    data = await json_body(request)
    username, action = data.get('username'), data.get('action')
    role_changes = {
        'grant_admin': ('member', 'admin'), 'revoke_admin': ('admin', 'member'),
        'grant_media': ('member', 'media'), 'revoke_media': ('media', 'member'),
    }
    if not valid_username(username) or action not in ('hide', 'ban', 'unban', 'delete', *role_changes):
        return error('invalid')
    with connect() as db:
        db.execute('BEGIN IMMEDIATE')
        actor = db.execute('SELECT role FROM users WHERE id=? AND banned_at IS NULL', (user['id'],)).fetchone()
        target = db.execute('SELECT id,username,role,is_media,banned_at FROM users WHERE username_key=?', (username.lower(),)).fetchone()
        if not target: return error('profile_missing', 404)
        if not actor or actor['role'] not in ('admin', 'owner') or target['id'] == user['id'] or target['role'] == 'owner':
            return error('forbidden', 403)
        if action in ('hide', 'ban', 'unban', 'delete'):
            if actor['role'] == 'admin' and target['role'] != 'member': return error('forbidden', 403)
            if action == 'hide':
                db.execute('UPDATE profiles SET published=0 WHERE user_id=?', (target['id'],))
            elif action == 'delete':
                if data.get('confirmation') != target['username']: return error('confirm_username')
                db.execute('DELETE FROM orders WHERE user_id=?', (target['id'],))
                db.execute('DELETE FROM users WHERE id=?', (target['id'],))
            elif action == 'ban':
                db.execute('UPDATE users SET banned_at=COALESCE(banned_at,?) WHERE id=?', (int(time.time()), target['id']))
                db.execute('DELETE FROM sessions WHERE user_id=?', (target['id'],))
            else:
                db.execute('UPDATE users SET banned_at=NULL WHERE id=?', (target['id'],))
        else:
            expected, role = role_changes[action]
            if actor['role'] != 'owner': return error('forbidden', 403)
            if display_role(target) != expected: return error('role_changed', 409)
            db.execute('UPDATE users SET role=?,is_media=? WHERE id=?',
                       ('member' if role == 'media' else role, role == 'media', target['id']))
    logger.info('Account moderation actor=%s target=%s action=%s', user['id'], target['id'], action)
    return web.json_response({'ok': True})

async def profile_feedback(request):
    user = session(request)
    viewer_id = user['id'] if user else None
    before = request.query.get('before')
    if before is not None and not re.fullmatch(r'[1-9][0-9]{0,17}', before):
        return error('invalid')
    with connect() as db:
        profile_id = published_profile(db, request)
        counts = dict(db.execute('SELECT value, COUNT(*) FROM profile_votes WHERE profile_id=? GROUP BY value', (profile_id,)))
        vote = db.execute('SELECT value FROM profile_votes WHERE profile_id=? AND voter_id=?',
                          (profile_id, viewer_id)).fetchone()
        comments = db.execute('''SELECT c.id,c.author_id,c.body,c.created_at,u.username
            FROM profile_comments c JOIN users u ON u.id=c.author_id
            WHERE c.profile_id=? AND (? IS NULL OR c.id<?) ORDER BY c.id DESC LIMIT 21''',
            (profile_id, before, before)).fetchall()
        can_comment = bool(user and plus_status(db, user)[0])
        friendship = None
        target_banned = db.execute('SELECT banned_at FROM users WHERE id=?', (profile_id,)).fetchone()['banned_at']
        if viewer_id is not None and viewer_id != profile_id and target_banned is None:
            pair = tuple(sorted((viewer_id, profile_id)))
            row = db.execute('SELECT sender_id,accepted FROM friendships WHERE low_id=? AND high_id=?', pair).fetchone()
            friendship = ('none' if row is None else 'friend' if row['accepted']
                          else 'outgoing' if row['sender_id'] == viewer_id else 'incoming')
    return web.json_response({
        'likes': counts.get(1, 0), 'dislikes': counts.get(-1, 0), 'vote': vote['value'] if vote else 0,
        'signed_in': user is not None, 'can_vote': user is not None and viewer_id != profile_id,
        'can_comment': can_comment, 'csrf': user['csrf'] if user else None,
        'friendship': friendship,
        'comments': [{'id': str(row['id']), 'author': row['username'], 'body': row['body'],
                      'created_at': row['created_at'], 'can_delete': viewer_id in (profile_id, row['author_id'])}
                     for row in comments[:20]],
        'next': str(comments[19]['id']) if len(comments) > 20 else None
    })

async def profile_vote(request):
    user = auth_csrf(request)
    if not user: return error('auth', 401)
    if not limit(request, 'profile_vote', 60, 60): return error('rate', 429)
    value = (await json_body(request)).get('value')
    if type(value) is not int or value not in (-1, 0, 1): return error('invalid')
    with connect() as db:
        db.execute('BEGIN IMMEDIATE')
        profile_id = published_profile(db, request)
        if profile_id == user['id']: return error('self_vote', 403)
        if value:
            db.execute('''INSERT INTO profile_votes (profile_id,voter_id,value) VALUES (?,?,?)
                ON CONFLICT(profile_id,voter_id) DO UPDATE SET value=excluded.value''', (profile_id, user['id'], value))
        else:
            db.execute('DELETE FROM profile_votes WHERE profile_id=? AND voter_id=?', (profile_id, user['id']))
    return web.json_response({'ok': True})

async def profile_comment_create(request):
    user = auth_csrf(request)
    if not user: return error('auth', 401)
    if not limit(request, 'profile_comment', 10, 300): return error('rate', 429)
    body = (await json_body(request)).get('body')
    if not isinstance(body, str): return error('comment_body')
    body = body.replace('\r\n', '\n').replace('\r', '\n').strip()
    if not 1 <= len(body) <= 500 or re.search(r'[\x00-\x08\x0b-\x1f\x7f\ud800-\udfff]', body):
        return error('comment_body')
    with connect() as db:
        db.execute('BEGIN IMMEDIATE')
        profile_id = published_profile(db, request)
        if not plus_status(db, user)[0]: return error('plus_required', 403)
        db.execute('INSERT INTO profile_comments (profile_id,author_id,body,created_at) VALUES (?,?,?,?)',
                   (profile_id, user['id'], body, int(time.time())))
    return web.json_response({'ok': True}, status=201)

async def profile_comment_delete(request):
    user = auth_csrf(request)
    if not user: return error('auth', 401)
    if not limit(request, 'profile_comment_delete', 30, 60): return error('rate', 429)
    comment_id = request.match_info['comment_id']
    if not re.fullmatch(r'[1-9][0-9]{0,17}', comment_id): return error('invalid')
    with connect() as db:
        db.execute('BEGIN IMMEDIATE')
        profile_id = published_profile(db, request)
        deleted = db.execute('''DELETE FROM profile_comments WHERE id=? AND profile_id=?
            AND (author_id=? OR profile_id=?)''', (comment_id, profile_id, user['id'], user['id']))
        if not deleted.rowcount: return error('comment_missing', 404)
    return web.json_response({'ok': True})

async def update_email(request):
    user = auth_csrf(request)
    if not user: return error('auth',401)
    if not limit(request,'email',8,900): return error('rate',429)
    data = await json_body(request)
    email = normalize_email(data.get('email'))
    if email is None: return error('email')
    password = data.get('current_password')
    if not isinstance(password, str) or not 1 <= len(password) <= 128:
        return error('credentials',401)
    with connect() as db:
        record = db.execute('SELECT password_hash FROM users WHERE id=?', (user['id'],)).fetchone()
    if not record or not await asyncio.to_thread(verify, password, record['password_hash']):
        return error('credentials',401)
    try:
        with connect() as db:
            updated = db.execute('''UPDATE users
                SET email_verified_at=CASE WHEN email COLLATE BINARY=? THEN email_verified_at ELSE NULL END, email=?
                WHERE id=? AND password_hash=? AND EXISTS (
                    SELECT 1 FROM sessions WHERE token_hash=? AND user_id=users.id AND expires_at>?
                )''', (email, email, user['id'], record['password_hash'], digest(request.cookies[COOKIE]), int(time.time())))
            if updated.rowcount != 1: return error('auth',401)
            db.execute('DELETE FROM email_tokens WHERE user_id=? AND email COLLATE BINARY<>?', (user['id'], email))
            verified = db.execute('SELECT email_verified_at FROM users WHERE id=?', (user['id'],)).fetchone()[0] is not None
    except sqlite3.IntegrityError as exc:
        if str(exc) == 'UNIQUE constraint failed: users.email': return error('email_taken',409)
        raise
    if not verified: queue_mail(request, 'verify', email, user['id'])
    return web.json_response({'email': email, 'email_verified': verified})

def queue_mail(request, purpose, email, user_id=None):
    queue = request.app.get(MAIL_QUEUE)
    if queue is None: return False
    preferred = request.headers.get('Accept-Language', '').split(',', 1)[0].split(';', 1)[0].strip()
    language = 'en' if preferred.split('-', 1)[0].lower() == 'en' else 'ru'
    try:
        queue.put_nowait((purpose, email, user_id, language))
        return True
    except asyncio.QueueFull:
        return False

def send_mail(purpose, email, user_id, language):
    if purpose == 'changed':
        with connect() as db:
            user = db.execute('SELECT username FROM users WHERE id=?', (user_id,)).fetchone()
        if user:
            deliver_mail(email, account_mail(purpose, language, MAIL_ORIGIN, user['username']))
        return
    now = int(time.time())
    token = secrets.token_urlsafe(32)
    with connect() as db:
        db.execute('BEGIN IMMEDIATE')
        if purpose == 'reset':
            user = db.execute('SELECT id,email,username FROM users WHERE email=? AND email_verified_at IS NOT NULL', (email,)).fetchone()
        else:
            user = db.execute('SELECT id,email,username FROM users WHERE id=? AND email COLLATE BINARY=? AND email_verified_at IS NULL', (user_id, email)).fetchone()
        if not user: return
        previous = db.execute('SELECT created_at FROM email_tokens WHERE user_id=? AND purpose=?', (user['id'], purpose)).fetchone()
        if previous and previous['created_at'] > now - 120: return
        email = user['email']
        ttl = 1800 if purpose == 'reset' else 86400
        db.execute('INSERT OR REPLACE INTO email_tokens VALUES(?,?,?,?,?,?)',
                   (user['id'], purpose, digest(token), email, now + ttl, now))
    try:
        action_url = f'{MAIL_ORIGIN}/auth.html#{purpose}={token}'
        deliver_mail(email, account_mail(purpose, language, MAIL_ORIGIN, user['username'], action_url))
    except Exception:
        with connect() as db:
            db.execute('DELETE FROM email_tokens WHERE token_hash=?', (digest(token),))
        raise

def deliver_mail(email, message):
    message['From'] = f'Orion Client <{MAIL_FROM}>'
    message['To'] = email
    password = Path(MAIL_PASSWORD_FILE).read_text(encoding='utf-8').strip()
    with smtplib.SMTP_SSL('smtp.yandex.ru', 465, timeout=10, context=ssl.create_default_context()) as smtp:
        smtp.login(MAIL_FROM, password)
        smtp.send_message(message)

async def mail_worker(app):
    if not (normalize_email(MAIL_FROM) and MAIL_PASSWORD_FILE and MAIL_ORIGIN.startswith('https://') and MAIL_ORIGIN in TRUSTED_ORIGINS):
        yield
        return
    app[MAIL_QUEUE] = queue = asyncio.Queue(maxsize=50)

    async def process():
        while True:
            job = await queue.get()
            try:
                await asyncio.to_thread(send_mail, *job)
            except Exception as exc:
                logger.warning('Account email delivery failed (%s)', type(exc).__name__)
            finally:
                queue.task_done()

    worker = asyncio.create_task(process())
    try:
        yield
    finally:
        worker.cancel()
        await asyncio.gather(worker, return_exceptions=True)

async def request_email_link(request):
    purpose = 'verify' if request.path == '/api/email/verify/request' else 'reset'
    if not limit(request, purpose + '_request', 6, 900): return error('rate',429)
    user = auth_csrf(request) if purpose == 'verify' else None
    if purpose == 'verify' and not user: return error('auth',401)
    data = await json_body(request)
    email = user['email'] if user else normalize_email(data.get('email'))
    if not email: return error('email')
    if not queue_mail(request, purpose, email, user['id'] if user else None): return error('unavailable',503)
    return web.json_response({'ok': True}, status=202)

def consume_email_token(purpose, token, password_hash=None):
    now = int(time.time())
    with connect() as db:
        db.execute('BEGIN IMMEDIATE')
        row = db.execute('''SELECT t.user_id,u.email FROM email_tokens t JOIN users u ON u.id=t.user_id
            WHERE t.token_hash=? AND t.purpose=? AND t.expires_at>? AND u.email COLLATE BINARY=t.email
            AND (u.email_verified_at IS NOT NULL) = ?''', (digest(token), purpose, now, purpose == 'reset')).fetchone()
        if not row: return None
        if purpose == 'verify':
            db.execute('UPDATE users SET email_verified_at=? WHERE id=?', (now, row['user_id']))
            db.execute('DELETE FROM email_tokens WHERE user_id=? AND purpose=?', (row['user_id'], purpose))
        else:
            db.execute('UPDATE users SET password_hash=? WHERE id=?', (password_hash, row['user_id']))
            db.execute('DELETE FROM sessions WHERE user_id=?', (row['user_id'],))
            db.execute('DELETE FROM email_tokens WHERE user_id=?', (row['user_id'],))
        return row

async def confirm_email_link(request):
    purpose = 'verify' if request.path == '/api/email/verify/confirm' else 'reset'
    if not limit(request, purpose + '_confirm', 12, 900): return error('rate',429)
    data = await json_body(request)
    token = data.get('token')
    if not isinstance(token, str) or not MAIL_TOKEN.fullmatch(token): return error('mail_token')
    hashed = None
    if purpose == 'reset':
        password = data.get('password')
        if not valid_password(password): return error('password')
        hashed = await asyncio.to_thread(hash_password, password)
    account = await asyncio.to_thread(consume_email_token, purpose, token, hashed)
    if not account: return error('mail_token')
    if purpose == 'reset': queue_mail(request, 'changed', account['email'], account['user_id'])
    return web.json_response({'ok': True})

async def logout(request):
    if not auth_csrf(request): return error('auth',401)
    with connect() as db: db.execute('DELETE FROM sessions WHERE token_hash=?',(digest(request.cookies[COOKIE]),))
    response=web.json_response({'ok':True});response.del_cookie(COOKIE,path='/',secure=SECURE,samesite='Strict');return response

async def list_sessions(request):
    user = session(request)
    if not user: return error('auth',401)
    current = digest(request.cookies[COOKIE])
    with connect() as db:
        rows = db.execute('''SELECT token_hash, expires_at, device FROM sessions
            WHERE user_id=? AND expires_at>? ORDER BY token_hash=? DESC, expires_at DESC''',
            (user['id'], int(time.time()), current)).fetchall()
    return web.json_response({'sessions': [
        {'id': digest(row['token_hash']), 'current': row['token_hash'] == current,
         'signed_in_at': row['expires_at'] - TTL, 'device': row['device']}
        for row in rows
    ]})

async def revoke_sessions(request):
    user = auth_csrf(request)
    if not user: return error('auth',401)
    if not limit(request, 'revoke_sessions', 30, 60): return error('rate',429)
    data = await json_body(request)
    target = data.get('id')
    if target != 'others' and (not isinstance(target, str) or not re.fullmatch(r'[a-f0-9]{64}', target)):
        return error('invalid')
    current = digest(request.cookies[COOKIE])
    with connect() as db:
        db.execute('BEGIN IMMEDIATE')
        if not db.execute('SELECT 1 FROM sessions WHERE token_hash=? AND expires_at>?', (current, int(time.time()))).fetchone():
            return error('auth',401)
        if target == 'others':
            db.execute('DELETE FROM sessions WHERE user_id=? AND token_hash<>?', (user['id'], current))
        else:
            rows = db.execute('SELECT token_hash FROM sessions WHERE user_id=? AND token_hash<>?', (user['id'], current))
            match = next((row['token_hash'] for row in rows if digest(row['token_hash']) == target), None)
            if match:
                db.execute('DELETE FROM sessions WHERE user_id=? AND token_hash=?', (user['id'], match))
    return web.json_response({'ok': True})

async def order(request):
    user=auth_csrf(request)
    if not user: return error('auth',401)
    if not limit(request,'order',10,3600): return error('rate',429)
    with connect() as db:
        if plus_status(db, user)[0]: return error('plus_active',409)
        existing=db.execute("SELECT id,status FROM orders WHERE user_id=? AND status='awaiting_gateway' ORDER BY created_at DESC LIMIT 1",(user['id'],)).fetchone()
        if existing: oid=existing['id']
        else:
            oid=secrets.token_hex(12)
            db.execute('INSERT INTO orders VALUES(?,?,?,?,?,?)',(oid,user['id'],6700,'RUB','awaiting_gateway',int(time.time())))
    return web.json_response({'order':{'id':oid,'amount_kopecks':6700,'currency':'RUB','status':'awaiting_gateway'},'payment_available':False})

async def password_change(request):
    user=auth_csrf(request)
    if not user: return error('auth',401)
    if not limit(request,'password',8,900): return error('rate',429)
    data=await json_body(request); old=data.get('current_password',''); new=data.get('new_password','')
    if not isinstance(old,str) or len(old)>128 or not valid_password(new): return error('password')
    with connect() as db: record=db.execute('SELECT password_hash FROM users WHERE id=?',(user['id'],)).fetchone()
    if not await asyncio.to_thread(verify,old,record['password_hash']): return error('credentials',401)
    hashed=await asyncio.to_thread(hash_password,new)
    with connect() as db:
        updated = db.execute('UPDATE users SET password_hash=? WHERE id=? AND password_hash=?',(hashed,user['id'],record['password_hash']))
        if updated.rowcount != 1: return error('auth',401)
        db.execute('DELETE FROM sessions WHERE user_id=?',(user['id'],))
        db.execute("DELETE FROM email_tokens WHERE user_id=? AND purpose='reset'", (user['id'],))
    created = create_session(user['id'], hashed, request)
    if not created: return error('auth',401)
    token,csrf=created; response=web.json_response({'ok':True,'csrf':csrf});set_cookie(response,token);return response

async def index(request): return web.FileResponse(BASE/'static'/'index.html',headers={'Cache-Control':'no-cache'})
async def account_page(request): return web.FileResponse(BASE/'static'/'account.html',headers={'Cache-Control':'no-cache'})
async def health(request): return web.json_response({'status':'ok','service':'orion-site'})
def create_app():
    init_db(); app=web.Application(middlewares=[security],client_max_size=4096)
    with connect() as db:
        funpay_sales.initialize(db)
    app.add_routes(funpay_sales.routes(connect, session, auth_csrf, limit, json_body, error))
    app[FAKE_PUNISHMENTS] = deque(maxlen=256)
    app[FAKE_PUNISHMENT_EPOCH] = secrets.token_hex(8)
    app[LAUNCHER_REQUESTS] = {}
    app[CHAT_TRANSFERS] = {}
    app[WORLD_PRESENCE] = {}
    app[WORLD_INVITES] = {}
    app.router.add_post('/api/launcher-auth/start', launcher_start)
    app.router.add_post('/api/launcher-auth/approve', launcher_approve)
    app.router.add_post('/api/launcher-auth/poll', launcher_poll)
    app.router.add_get('/api/game-settings', game_settings)
    app.router.add_post('/api/game-settings', game_settings)
    app.router.add_get('/api/cosmetic-loadout', cosmetic_loadout)
    app.router.add_post('/api/cosmetic-loadout', cosmetic_loadout)
    app.add_routes([web.get('/',index),web.get('/account',account_page),web.get('/health',health),web.get('/api/me',me),web.post('/api/register',register),web.post('/api/login',login),web.post('/api/logout',logout),web.post('/api/orders',order),web.post('/api/password',password_change),web.static('/static/',BASE/'static',show_index=False)])
    app.router.add_post('/api/email', update_email)
    app.router.add_get('/api/profile-page', profile_page)
    app.router.add_get('/auth.html', profile_page)
    app.router.add_get('/api/friends', friends_list)
    app.router.add_post('/api/friends', friends_update)
    app.router.add_get('/api/friend-worlds', friend_worlds)
    app.router.add_post('/api/friend-worlds', friend_worlds)
    app.router.add_get('/api/friends/{username}/chat', friend_chat)
    app.router.add_post('/api/friends/{username}/chat', friend_chat)
    app.router.add_post('/api/chat/keys', chat_keys)
    app.router.add_get('/api/chat/keys/{username}', chat_keys)
    app.router.add_route('GET', '/api/chat/recovery', chat_recovery)
    app.router.add_route('POST', '/api/chat/recovery', chat_recovery)
    app.router.add_post('/api/chat/transfer', chat_transfer)
    app.router.add_get('/api/chat/updates', chat_updates)
    app.router.add_get('/api/admin/accounts', admin_accounts)
    app.router.add_post('/api/admin/accounts', admin_update)
    app.router.add_get('/api/pricing', pricing)
    app.router.add_get('/api/profile', own_profile)
    app.router.add_get('/api/catalog-access', catalog_access)
    app.router.add_get('/api/fake-punishments', fake_punishments)
    app.router.add_post('/api/fake-punishments', fake_punishments)
    app.router.add_get('/api/profile-search', search_profiles)
    app.router.add_post('/api/profile', save_profile)
    app.router.add_post('/api/profile/avatar', save_avatar)
    app.router.add_post('/api/profile/oauth/{provider}/start', oauth_start)
    app.router.add_get('/api/profile/oauth/{provider}/callback', oauth_callback)
    app.router.add_post('/api/profile/oauth/{provider}/finish', oauth_finish)
    app.router.add_post('/api/profile/oauth/{provider}/unlink', oauth_unlink)
    app.router.add_get('/api/profile/{profile_id}', public_profile)
    app.router.add_get('/api/profile/{profile_id}/avatar', profile_avatar)
    app.router.add_get('/api/profile/{profile_id}/feedback', profile_feedback)
    app.router.add_post('/api/profile/{profile_id}/vote', profile_vote)
    app.router.add_post('/api/profile/{profile_id}/comments', profile_comment_create)
    app.router.add_post('/api/profile/{profile_id}/comments/{comment_id}/delete', profile_comment_delete)
    app.router.add_get('/api/sessions', list_sessions)
    app.router.add_post('/api/sessions/revoke', revoke_sessions)
    app.router.add_post('/api/register/availability', registration_availability)
    app.router.add_post('/api/email/verify/request', request_email_link)
    app.router.add_post('/api/email/verify/confirm', confirm_email_link)
    app.router.add_post('/api/password/reset/request', request_email_link)
    app.router.add_post('/api/password/reset/confirm', confirm_email_link)
    app.cleanup_ctx.append(mail_worker)
    return app
if __name__=='__main__': web.run_app(create_app(),host='127.0.0.1',port=int(os.environ.get('ORION_SITE_PORT','8088')),access_log=None)
