"""Orion cosmetics protocol v1: shared badges, halos and wings over HTTPS."""
import argparse
import asyncio
import base64
import hashlib
import ipaddress
import json
import logging
import re
import secrets
import time
import uuid
from collections import defaultdict, deque
from pathlib import Path

import aiosqlite
from aiohttp import web
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.serialization import load_der_public_key
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey

LOG = logging.getLogger("orion.cosmetics")
MAX_PLAYERS = 100

def compact_uuid(value):
    if not isinstance(value, str) or not re.fullmatch(r"[0-9a-fA-F-]{32,36}", value):
        raise web.HTTPBadRequest(reason="Invalid UUID")
    try:
        return uuid.UUID(value).hex
    except ValueError:
        raise web.HTTPBadRequest(reason="Invalid UUID") from None

def decode(value, limit):
    if not isinstance(value, str) or len(value) > limit:
        raise web.HTTPBadRequest(reason="Invalid encoding")
    try:
        return base64.b64decode(value + "=" * (-len(value) % 4), altchars=b"-_", validate=True)
    except (ValueError, TypeError):
        raise web.HTTPBadRequest(reason="Invalid encoding") from None

class Store:
    def __init__(self, path, clock=time.time, presence_ttl=90, token_ttl=86400):
        self.path = path
        self.clock = clock
        self.presence_ttl = presence_ttl
        self.token_ttl = token_ttl
        self.lock = asyncio.Lock()
        self.changed = asyncio.Condition()
        self.db = None
        self.revision = 0

    async def open(self):
        Path(self.path).parent.mkdir(parents=True, exist_ok=True)
        self.db = await aiosqlite.connect(self.path)
        self.db.row_factory = aiosqlite.Row
        await self.db.executescript('''
            PRAGMA journal_mode=WAL;
            PRAGMA foreign_keys=ON;
            PRAGMA busy_timeout=5000;
            CREATE TABLE IF NOT EXISTS players (
                uuid TEXT PRIMARY KEY, name TEXT NOT NULL, halo INTEGER NOT NULL DEFAULT 0,
                wings INTEGER NOT NULL DEFAULT 0, online INTEGER NOT NULL DEFAULT 0,
                online_until REAL NOT NULL DEFAULT 0, updated_at REAL NOT NULL
            );
            CREATE TABLE IF NOT EXISTS challenges (
                challenge TEXT PRIMARY KEY, uuid TEXT NOT NULL, name TEXT NOT NULL,
                public_key BLOB NOT NULL, expires REAL NOT NULL
            );
            CREATE TABLE IF NOT EXISTS sessions (
                token_hash TEXT PRIMARY KEY, uuid TEXT NOT NULL REFERENCES players(uuid), expires REAL NOT NULL
            );
            CREATE INDEX IF NOT EXISTS sessions_expires ON sessions(expires);
            CREATE INDEX IF NOT EXISTS challenges_expires ON challenges(expires);
            CREATE TABLE IF NOT EXISTS meta (id INTEGER PRIMARY KEY CHECK (id=1), revision INTEGER NOT NULL);
            INSERT OR IGNORE INTO meta(id, revision) VALUES (1, 0);
            CREATE TABLE IF NOT EXISTS shared_configs (
                id INTEGER PRIMARY KEY, owner TEXT NOT NULL REFERENCES players(uuid),
                author TEXT NOT NULL, name TEXT NOT NULL, settings TEXT NOT NULL, created_at REAL NOT NULL
            );
            CREATE INDEX IF NOT EXISTS shared_configs_owner ON shared_configs(owner);
        ''')
        async with self.db.execute("SELECT revision FROM meta WHERE id=1") as cursor:
            self.revision = (await cursor.fetchone())[0]
        await self.db.commit()

    async def close(self):
        if self.db:
            await self.db.close()

    async def notify(self):
        async with self.changed:
            self.changed.notify_all()

    async def bump(self):
        self.revision += 1
        await self.db.execute("UPDATE meta SET revision=? WHERE id=1", (self.revision,))

    async def challenge(self, payload):
        player = compact_uuid(payload.get("uuid"))
        name = payload.get("name")
        if not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9_]{1,16}", name):
            raise web.HTTPBadRequest(reason="Invalid player name")
        key = decode(payload.get("publicKey"), 256)
        try:
            public = load_der_public_key(key)
            if not isinstance(public, Ed25519PublicKey):
                raise ValueError()
        except (ValueError, TypeError):
            raise web.HTTPBadRequest(reason="Ed25519 public key required") from None
        challenge = "orion-cosmetics:v1:" + player + ":" + secrets.token_urlsafe(32)
        async with self.lock:
            await self.db.execute("INSERT INTO challenges VALUES (?, ?, ?, ?, ?)",
                                  (challenge, player, name, key, self.clock() + 60))
            await self.db.commit()
        return {"challenge": challenge, "expiresIn": 60}

    async def verify(self, payload):
        challenge = payload.get("challenge")
        if not isinstance(challenge, str) or len(challenge) > 256:
            raise web.HTTPBadRequest(reason="Invalid challenge")
        signature = decode(payload.get("signature"), 128)
        if len(signature) != 64:
            raise web.HTTPBadRequest(reason="Invalid signature")
        now = self.clock()
        async with self.lock:
            async with self.db.execute("SELECT * FROM challenges WHERE challenge=?", (challenge,)) as cursor:
                row = await cursor.fetchone()
            if row is None or row["expires"] < now:
                raise web.HTTPUnauthorized(reason="Challenge expired or already used")
            try:
                load_der_public_key(row["public_key"]).verify(signature, challenge.encode("utf-8"))
            except InvalidSignature:
                raise web.HTTPUnauthorized(reason="Invalid signature") from None
            token = secrets.token_urlsafe(36)
            digest = hashlib.sha256(token.encode()).hexdigest()
            try:
                await self.db.execute("DELETE FROM challenges WHERE challenge=?", (challenge,))
                await self.db.execute("INSERT INTO players(uuid,name,updated_at) VALUES (?,?,?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name", (row["uuid"], row["name"], now))
                await self.db.execute("INSERT INTO sessions VALUES(?,?,?)", (digest, row["uuid"], now + self.token_ttl))
                await self.db.commit()
            except Exception:
                await self.db.rollback()
                raise
        return {"token": token, "expiresIn": self.token_ttl}

    async def authenticate(self, header):
        if not header.startswith("Bearer ") or len(header) > 256:
            raise web.HTTPUnauthorized(reason="Bearer token required")
        digest = hashlib.sha256(header[7:].encode()).hexdigest()
        async with self.lock:
            async with self.db.execute("SELECT uuid FROM sessions WHERE token_hash=? AND expires>?", (digest, self.clock())) as cursor:
                row = await cursor.fetchone()
        if row is None:
            raise web.HTTPUnauthorized(reason="Session expired")
        return row["uuid"]

    @staticmethod
    def players(payload):
        players = payload.get("players", [])
        if not isinstance(players, list) or len(players) > MAX_PLAYERS:
            raise web.HTTPBadRequest(reason="At most 100 players per request")
        return list(dict.fromkeys(compact_uuid(value) for value in players))

    async def presence(self, player, payload):
        ids = self.players(payload)
        halo, wings = payload.get("halo", False), payload.get("wings", 0)
        if not isinstance(halo, bool) or isinstance(wings, bool) or not isinstance(wings, int) or not 0 <= wings <= 2:
            raise web.HTTPBadRequest(reason="Invalid cosmetics")
        now = self.clock()
        async with self.lock:
            async with self.db.execute("SELECT * FROM players WHERE uuid=?", (player,)) as cursor:
                old = await cursor.fetchone()
            if old is None:
                raise web.HTTPUnauthorized(reason="Unknown player")
            updated = not old["online"] or old["online_until"] <= now or bool(old["halo"]) != halo or old["wings"] != wings
            try:
                await self.db.execute("UPDATE players SET halo=?, wings=?, online=1, online_until=?, updated_at=? WHERE uuid=?", (int(halo), wings, now + self.presence_ttl, now, player))
                if updated:
                    await self.bump()
                rows = []
                if ids:
                    placeholders = ",".join("?" for _ in ids)
                    async with self.db.execute("SELECT uuid,halo,wings FROM players WHERE uuid IN (" + placeholders + ") AND online=1 AND online_until>?", (*ids, now)) as cursor:
                        rows = await cursor.fetchall()
                response = {"online": [r["uuid"] for r in rows], "halos": [r["uuid"] for r in rows if r["halo"]],
                            "wings": {r["uuid"]: r["wings"] for r in rows if r["wings"]}, "revision": self.revision}
                await self.db.commit()
            except Exception:
                await self.db.rollback()
                async with self.db.execute("SELECT revision FROM meta WHERE id=1") as cursor:
                    self.revision = (await cursor.fetchone())[0]
                raise
        if updated:
            await self.notify()
        return response

    async def cleanup(self):
        now = self.clock()
        async with self.lock:
            try:
                cursor = await self.db.execute("UPDATE players SET online=0 WHERE online=1 AND online_until<=?", (now,))
                updated = cursor.rowcount > 0
                await cursor.close()
                await self.db.execute("DELETE FROM challenges WHERE expires<=?", (now,))
                await self.db.execute("DELETE FROM sessions WHERE expires<=?", (now,))
                if updated:
                    await self.bump()
                await self.db.commit()
            except Exception:
                await self.db.rollback()
                raise
        if updated:
            await self.notify()

    async def watch(self, payload):
        self.players(payload)
        revision = payload.get("revision")
        if isinstance(revision, bool) or not isinstance(revision, int) or not 0 <= revision < 2**63:
            raise web.HTTPBadRequest(reason="Invalid revision")
        async with self.changed:
            if revision == self.revision:
                try:
                    await asyncio.wait_for(self.changed.wait_for(lambda: revision != self.revision), timeout=20)
                except asyncio.TimeoutError:
                    pass
        return {"revision": self.revision}

STORE = web.AppKey("store", Store)

@web.middleware
async def errors(request, handler):
    try:
        response = await handler(request)
    except web.HTTPException as error:
        response = web.json_response({"error": error.reason}, status=error.status, headers={"Retry-After": "5"} if error.status == 429 else None)
    except (json.JSONDecodeError, UnicodeDecodeError, ValueError, TypeError):
        response = web.json_response({"error": "Invalid request"}, status=400)
    except Exception:
        LOG.exception("Unhandled API error on %s", request.path)
        response = web.json_response({"error": "Service unavailable"}, status=503)
    response.headers["Cache-Control"] = "no-store"
    response.headers["X-Content-Type-Options"] = "nosniff"
    return response

class RateLimiter:
    def __init__(self):
        self.buckets = defaultdict(deque)

    @web.middleware
    async def middleware(self, request, handler):
        if request.path in ("/", "/healthz"):
            return await handler(request)
        remote = request.remote or "unknown"
        if remote in ("127.0.0.1", "::1"):
            remote = request.headers.get("X-Real-IP", remote)
        try:
            remote = str(ipaddress.ip_address(remote))
        except ValueError:
            remote = "unknown"
        route = "/v1/configs/item" if request.path.startswith("/v1/configs/") else request.path
        key = (remote, route, request.method)
        now = time.monotonic()
        queue = self.buckets[key]
        while queue and queue[0] < now - 60:
            queue.popleft()
        limit = 12 if route == "/v1/configs" and request.method == "POST" else 60 if route == "/v1/challenge" else 240
        if len(queue) >= limit:
            raise web.HTTPTooManyRequests(reason="Rate limit exceeded")
        queue.append(now)
        if len(self.buckets) > 10000:
            self.buckets = defaultdict(deque, {k: v for k, v in self.buckets.items() if v and v[-1] > now - 60})
        return await handler(request)

async def body(request):
    if request.content_type != "application/json":
        raise web.HTTPUnsupportedMediaType(reason="JSON required")
    value = await request.json()
    if not isinstance(value, dict):
        raise web.HTTPBadRequest(reason="JSON object required")
    return value

async def health(request):
    return web.json_response({"status": "ok", "service": "orion-cosmetics", "protocol": 1})

async def challenge(request):
    return web.json_response(await request.app[STORE].challenge(await body(request)))

async def verify(request):
    return web.json_response(await request.app[STORE].verify(await body(request)))

async def presence(request):
    store = request.app[STORE]
    player = await store.authenticate(request.headers.get("Authorization", ""))
    return web.json_response(await store.presence(player, await body(request)))

async def watch(request):
    store = request.app[STORE]
    await store.authenticate(request.headers.get("Authorization", ""))
    result = await store.watch(await body(request))
    await store.authenticate(request.headers.get("Authorization", ""))
    return web.json_response(result)


CONFIG_PAGE_SIZE = 7
CONFIG_ENUMS = {"TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT"}
CONFIG_PRIVATE = {"waypoints", "favoriteModules", "clickGuiLastSection", "visualPresetSelection",
                  "appliedVisualPreset", "chatFilterWords", "chatHighlightWords", "protectName",
                  "fakePrefix", "fakePrefixAdditionalNames"}


def validate_config(data):
    name = data.get("name")
    settings = data.get("settings")
    if (type(name) is not str or not 1 <= len(name.strip()) <= 48 or not name.isprintable()
            or data.get("schema") != 1 or type(settings) is not dict or not 1 <= len(settings) <= 512):
        raise web.HTTPBadRequest(reason="Invalid config")
    for key, value in settings.items():
        if (not re.fullmatch(r"[A-Za-z][A-Za-z0-9]{0,63}", key) or key in CONFIG_PRIVATE
                or key.startswith("discord")):
            raise web.HTTPBadRequest(reason="Invalid setting")
        if type(value) is bool:
            continue
        if type(value) in (int, float) and -1e9 <= value <= 1e9:
            continue
        if type(value) is str and (re.fullmatch(r"#[0-9a-fA-F]{6}", value) or value in CONFIG_ENUMS
                                   or (key == "crosshairPixels" and re.fullmatch(r"[01]{1,1024}", value))):
            continue
        raise web.HTTPBadRequest(reason="Invalid setting value")
    return name.strip(), json.dumps(settings, ensure_ascii=False, separators=(",", ":"))


async def list_configs(request):
    store = request.app[STORE]
    offset = int(request.query.get("offset", 0))
    if offset < 0 or offset > 2000:
        raise web.HTTPBadRequest(reason="Invalid page")
    async with store.lock:
        async with store.db.execute(
                "SELECT id, author, name FROM shared_configs ORDER BY id DESC LIMIT ? OFFSET ?",
                (CONFIG_PAGE_SIZE + 1, offset)) as cursor:
            rows = await cursor.fetchall()
    return web.json_response({"items": [dict(row) for row in rows[:CONFIG_PAGE_SIZE]],
                              "more": len(rows) > CONFIG_PAGE_SIZE})


async def get_config(request):
    store = request.app[STORE]
    async with store.lock:
        async with store.db.execute("SELECT id, author, name, settings FROM shared_configs WHERE id=?",
                                    (int(request.match_info["id"]),)) as cursor:
            row = await cursor.fetchone()
    if row is None:
        raise web.HTTPNotFound(reason="Config not found")
    return web.json_response({"id": row["id"], "author": row["author"], "name": row["name"],
                              "schema": 1, "settings": json.loads(row["settings"])})


async def publish_config(request):
    store = request.app[STORE]
    owner = await store.authenticate(request.headers.get("Authorization", ""))
    name, settings = validate_config(await body(request))
    async with store.lock:
        async with store.db.execute("SELECT COUNT(*), COUNT(CASE WHEN owner=? THEN 1 END) FROM shared_configs",
                                    (owner,)) as cursor:
            total, owned = await cursor.fetchone()
        if total >= 2000 or owned >= 20:
            raise web.HTTPTooManyRequests(reason="Config limit reached")
        async with store.db.execute("SELECT name FROM players WHERE uuid=?", (owner,)) as cursor:
            author = (await cursor.fetchone())[0]
        async with store.db.execute(
                "INSERT INTO shared_configs(owner, author, name, settings, created_at) VALUES (?,?,?,?,?)",
                (owner, author, name, settings, store.clock())) as cursor:
            config_id = cursor.lastrowid
        await store.db.commit()
    return web.json_response({"id": config_id, "author": author, "name": name}, status=201)

async def lifetime(app):
    await app[STORE].open()
    async def janitor():
        while True:
            await asyncio.sleep(5)
            try:
                await app[STORE].cleanup()
            except Exception:
                LOG.exception("Database cleanup failed")
    task = asyncio.create_task(janitor())
    yield
    task.cancel()
    try:
        await task
    except asyncio.CancelledError:
        pass
    await app[STORE].close()

def create_app(database):
    app = web.Application(client_max_size=65536, middlewares=[errors, RateLimiter().middleware])
    app[STORE] = Store(database)
    app.cleanup_ctx.append(lifetime)
    app.add_routes([web.get("/", health), web.get("/healthz", health), web.post("/v1/challenge", challenge),
                    web.post("/v1/verify", verify), web.post("/v1/presence", presence), web.post("/v1/watch", watch),
                    web.get("/v1/configs", list_configs), web.post("/v1/configs", publish_config),
                    web.get(r"/v1/configs/{id:\d{1,9}}", get_config)])
    return app

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--database", default="/var/lib/orion-cosmetics/state.db")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    web.run_app(create_app(args.database), host=args.host, port=args.port, access_log=None, shutdown_timeout=5)
