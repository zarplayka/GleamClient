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
from aiohttp import ClientError, ClientSession, ClientTimeout, web
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.serialization import load_der_public_key
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
from football import FootballRooms
from basketball import BasketballRooms

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
            CREATE TABLE IF NOT EXISTS config_downloads (
                config_id INTEGER NOT NULL REFERENCES shared_configs(id) ON DELETE CASCADE,
                player TEXT NOT NULL, PRIMARY KEY(config_id, player)
            );
            CREATE TABLE IF NOT EXISTS config_sequence (
                singleton INTEGER PRIMARY KEY CHECK(singleton=1), next_id INTEGER NOT NULL
            );
            INSERT OR IGNORE INTO config_sequence SELECT 1, COALESCE(MAX(id),0)+1 FROM shared_configs;
            UPDATE config_sequence SET next_id=MAX(next_id,(SELECT COALESCE(MAX(id),0)+1 FROM shared_configs));
        ''')
        async with self.db.execute("PRAGMA table_info(players)") as cursor:
            columns = {row[1] for row in await cursor.fetchall()}
        for column, definition in (("emote", "INTEGER NOT NULL DEFAULT 0"),
                                   ("emote_started", "REAL NOT NULL DEFAULT 0")):
            if column not in columns:
                await self.db.execute("ALTER TABLE players ADD COLUMN " + column + " " + definition)
        async with self.db.execute("PRAGMA table_info(shared_configs)") as cursor:
            columns = {row[1] for row in await cursor.fetchall()}
        for column, definition in (("downloads", "INTEGER NOT NULL DEFAULT 0"),
                                   ("updated_at", "REAL NOT NULL DEFAULT 0"),
                                   ("version", "INTEGER NOT NULL DEFAULT 1"),
                                   ("orion_account_id", "INTEGER")):
            if column not in columns:
                await self.db.execute("ALTER TABLE shared_configs ADD COLUMN " + column + " " + definition)
        await self.db.execute("CREATE INDEX IF NOT EXISTS shared_configs_orion_account ON shared_configs(orion_account_id)")
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
                    async with self.db.execute("SELECT uuid,halo,wings,emote,emote_started FROM players WHERE uuid IN (" + placeholders + ") AND online=1 AND online_until>?", (*ids, now)) as cursor:
                        rows = await cursor.fetchall()
                response = {"online": [r["uuid"] for r in rows], "halos": [r["uuid"] for r in rows if r["halo"]],
                            "wings": {r["uuid"]: r["wings"] for r in rows if r["wings"]},
                            "emotes": {r["uuid"]: {"id": r["emote"], "started": r["emote_started"],
                                                   "elapsed": max(0, now - r["emote_started"])}
                                       for r in rows if r["emote"] and 0 <= now - r["emote_started"] < 12},
                            "revision": self.revision}
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
FOOTBALL = web.AppKey("football", FootballRooms)
BASKETBALL = web.AppKey("basketball", BasketballRooms)

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
        limit = 7200 if route in ("/v1/football", "/v1/basketball") else 12 if route == "/v1/configs" and request.method == "POST" else 60 if route == "/v1/challenge" else 240
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

async def football(request):
    player = await request.app[STORE].authenticate(request.headers.get("Authorization", ""))
    return web.json_response(request.app[FOOTBALL].handle(player, await body(request)))

async def basketball(request):
    player = await request.app[STORE].authenticate(request.headers.get("Authorization", ""))
    return web.json_response(request.app[BASKETBALL].handle(player, await body(request)))

async def watch(request):
    store = request.app[STORE]
    await store.authenticate(request.headers.get("Authorization", ""))
    result = await store.watch(await body(request))
    await store.authenticate(request.headers.get("Authorization", ""))
    return web.json_response(result)


async def emote(request):
    store = request.app[STORE]
    player = await store.authenticate(request.headers.get("Authorization", ""))
    value = (await body(request)).get("emote")
    if type(value) is not int or not 0 <= value <= 6:
        raise web.HTTPBadRequest(reason="Invalid emote")
    async with store.lock:
        try:
            await store.db.execute("UPDATE players SET emote=?, emote_started=? WHERE uuid=?",
                                   (value, store.clock(), player))
            await store.bump()
            await store.db.commit()
        except Exception:
            await store.db.rollback()
            async with store.db.execute("SELECT revision FROM meta WHERE id=1") as cursor:
                store.revision = (await cursor.fetchone())[0]
            raise
    await store.notify()
    return web.json_response({"ok": True})


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
    limit = int(request.query.get("limit", CONFIG_PAGE_SIZE))
    owner = await store.authenticate(request.headers.get("Authorization", "")) if request.path.endswith("/mine") else None
    if offset < 0 or offset > 2000 or not 1 <= limit <= 20:
        raise web.HTTPBadRequest(reason="Invalid page")
    where = " WHERE owner=?" if owner else ""
    params = (owner,) if owner else ()
    async with store.lock:
        async with store.db.execute(
                "SELECT * FROM shared_configs" + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
                (*params, limit + 1, offset)) as cursor:
            rows = await cursor.fetchall()
        async with store.db.execute("SELECT COUNT(*) FROM shared_configs" + where, params) as cursor:
            total = (await cursor.fetchone())[0]
    return web.json_response({"items": [config_metadata(row) for row in rows[:limit]],
                              "more": len(rows) > limit, "total": total})


def config_metadata(row):
    return {"id": row["id"], "author": row["author"], "name": row["name"], "owner": row["owner"],
            "downloads": row["downloads"], "createdAt": row["created_at"],
            "updatedAt": row["updated_at"] or row["created_at"], "version": row["version"]}


async def get_config(request):
    store = request.app[STORE]
    async with store.lock:
        async with store.db.execute("SELECT * FROM shared_configs WHERE id=?",
                                    (int(request.match_info["id"]),)) as cursor:
            row = await cursor.fetchone()
    if row is None:
        raise web.HTTPNotFound(reason="Config not found")
    return web.json_response(config_metadata(row) | {"schema": 1, "settings": json.loads(row["settings"])})


async def catalog_access(request):
    cookie = request.cookies.get("__Host-orion_session", "")
    if not cookie:
        return None, 3
    if not re.fullmatch(r"[A-Za-z0-9_-]{40,100}", cookie):
        raise web.HTTPUnauthorized(reason="Invalid Orion session")
    try:
        async with ClientSession(timeout=ClientTimeout(total=3)) as session:
            async with session.get("http://127.0.0.1:8088/api/catalog-access",
                                   cookies={"__Host-orion_session": cookie}, allow_redirects=False) as response:
                if response.status == 401:
                    raise web.HTTPUnauthorized(reason="Orion session expired")
                if response.status == 200:
                    data = await response.json()
                    return data["account_id"], data["limit"]
    except (ClientError, asyncio.TimeoutError):
        pass
    raise web.HTTPServiceUnavailable(reason="Orion accounts unavailable")


async def publish_config(request):
    store = request.app[STORE]
    owner = await store.authenticate(request.headers.get("Authorization", ""))
    name, settings = validate_config(await body(request))
    account_id, max_owned = await catalog_access(request)
    now = store.clock()
    async with store.lock:
        async with store.db.execute("""SELECT COUNT(*), COUNT(CASE WHEN owner=? THEN 1 END),
            COUNT(CASE WHEN orion_account_id=? THEN 1 END) FROM shared_configs""", (owner, account_id)) as cursor:
            total, owned, account_owned = await cursor.fetchone()
        if total >= 2000 or max(owned, account_owned) >= max_owned:
            raise web.HTTPTooManyRequests(reason="Config limit reached")
        async with store.db.execute("SELECT name FROM players WHERE uuid=?", (owner,)) as cursor:
            author = (await cursor.fetchone())[0]
        try:
            async with store.db.execute("SELECT next_id FROM config_sequence WHERE singleton=1") as cursor:
                config_id = (await cursor.fetchone())[0]
            await store.db.execute("UPDATE config_sequence SET next_id=next_id+1 WHERE singleton=1")
            await store.db.execute(
                """INSERT INTO shared_configs(id, owner, author, name, settings, created_at, updated_at, orion_account_id)
                   VALUES (?,?,?,?,?,?,?,?)""",
                (config_id, owner, author, name, settings, now, now, account_id))
            await store.db.commit()
        except Exception:
            await store.db.rollback()
            raise
        async with store.db.execute("SELECT * FROM shared_configs WHERE id=?", (config_id,)) as cursor:
            row = await cursor.fetchone()
    return web.json_response(config_metadata(row), status=201)


async def download_config(request):
    store = request.app[STORE]
    player = await store.authenticate(request.headers.get("Authorization", ""))
    config_id = int(request.match_info["id"])
    async with store.lock:
        try:
            async with store.db.execute("SELECT * FROM shared_configs WHERE id=?", (config_id,)) as cursor:
                row = await cursor.fetchone()
            if row is None:
                raise web.HTTPNotFound(reason="Config not found")
            if row["owner"] != player:
                cursor = await store.db.execute("INSERT OR IGNORE INTO config_downloads VALUES (?,?)", (config_id, player))
                if cursor.rowcount:
                    await store.db.execute("UPDATE shared_configs SET downloads=downloads+1 WHERE id=?", (config_id,))
                await cursor.close()
            await store.db.commit()
            async with store.db.execute("SELECT * FROM shared_configs WHERE id=?", (config_id,)) as cursor:
                row = await cursor.fetchone()
        except Exception:
            await store.db.rollback()
            raise
    return web.json_response(config_metadata(row) | {"schema": 1, "settings": json.loads(row["settings"])})


async def verify_config_owner(request, owner):
    nonce = request.headers.get("X-Orion-Profile-Proof", "")
    if not re.fullmatch(r"[0-9a-f]{32}", nonce):
        raise web.HTTPForbidden(reason="Minecraft account verification required")
    store = request.app[STORE]
    async with store.lock:
        async with store.db.execute("SELECT name FROM players WHERE uuid=?", (owner,)) as cursor:
            player = await cursor.fetchone()
    binding = "\n".join((request.headers["Authorization"], request.method, request.path, nonce))
    server_id = hashlib.sha256(binding.encode()).hexdigest()[:40]
    try:
        async with ClientSession(timeout=ClientTimeout(total=5)) as session:
            async with session.get("https://sessionserver.mojang.com/session/minecraft/hasJoined",
                                   params={"username": player["name"], "serverId": server_id}) as response:
                if response.status == 200 and (await response.json()).get("id") == owner:
                    return
                if response.status not in (200, 204, 404):
                    raise web.HTTPServiceUnavailable(reason="Minecraft verification unavailable")
    except (ClientError, asyncio.TimeoutError):
        raise web.HTTPServiceUnavailable(reason="Minecraft verification unavailable") from None
    raise web.HTTPForbidden(reason="Minecraft account verification required")


async def manage_config(request):
    store = request.app[STORE]
    owner = await store.authenticate(request.headers.get("Authorization", ""))
    data = await body(request)
    config_id = int(request.match_info["id"])
    await verify_config_owner(request, owner)
    async with store.lock:
        async with store.db.execute("SELECT * FROM shared_configs WHERE id=?", (config_id,)) as cursor:
            row = await cursor.fetchone()
        if row is None:
            if request.method == "DELETE":
                return web.json_response({"deleted": True})
            raise web.HTTPNotFound(reason="Config not found")
        if row["owner"] != owner:
            raise web.HTTPForbidden(reason="Config belongs to another author")
        if type(data.get("version")) is not int or data["version"] != row["version"]:
            raise web.HTTPConflict(reason="Config has changed; refresh the catalog")
        try:
            if request.method == "DELETE":
                await store.db.execute("DELETE FROM shared_configs WHERE id=?", (config_id,))
                result = {"deleted": True}
            else:
                name, settings = validate_config({
                    "name": data.get("name", row["name"]), "schema": data.get("schema", 1),
                    "settings": data.get("settings", json.loads(row["settings"]))})
                await store.db.execute("UPDATE shared_configs SET name=?, settings=?, updated_at=?, version=version+1 WHERE id=?",
                                       (name, settings, store.clock(), config_id))
                async with store.db.execute("SELECT * FROM shared_configs WHERE id=?", (config_id,)) as cursor:
                    updated = await cursor.fetchone()
                result = config_metadata(updated) | {"schema": 1, "settings": json.loads(updated["settings"])}
            await store.db.commit()
        except Exception:
            await store.db.rollback()
            raise
    return web.json_response(result)

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
    app[FOOTBALL] = FootballRooms()
    app[BASKETBALL] = BasketballRooms()
    app.cleanup_ctx.append(lifetime)
    app.add_routes([web.get("/", health), web.get("/healthz", health), web.post("/v1/challenge", challenge),
                    web.post("/v1/verify", verify), web.post("/v1/presence", presence), web.post("/v1/watch", watch),
                    web.get("/v1/configs", list_configs), web.post("/v1/configs", publish_config),
                    web.get("/v1/configs/mine", list_configs),
                    web.get(r"/v1/configs/{id:\d{1,9}}", get_config),
                    web.post(r"/v1/configs/{id:\d{1,9}}/download", download_config),
                    web.patch(r"/v1/configs/{id:\d{1,9}}", manage_config),
                    web.delete(r"/v1/configs/{id:\d{1,9}}", manage_config), web.post("/v1/emote", emote),
                    web.post("/v1/football", football), web.post("/v1/basketball", basketball)])
    return app

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--database", default="/var/lib/orion-cosmetics/state.db")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    web.run_app(create_app(args.database), host=args.host, port=args.port, access_log=None, shutdown_timeout=5)
