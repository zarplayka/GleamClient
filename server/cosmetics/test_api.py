import asyncio
import base64
import tempfile
import time
import unittest
import uuid
from pathlib import Path
from aiohttp.test_utils import TestClient, TestServer
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat
from app import create_app, BASKETBALL, FOOTBALL, STORE, Store

def encode(value):
    return base64.urlsafe_b64encode(value).decode().rstrip("=")

class ApiTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.now = time.time()
        self.app = create_app(str(Path(self.directory.name) / "state.db"))
        self.app[STORE].clock = lambda: self.now
        self.client = TestClient(TestServer(self.app))
        await self.client.start_server()

    async def asyncTearDown(self):
        await self.client.close()
        self.directory.cleanup()

    async def login(self, name="OrionProbe", player=None):
        player = player or str(uuid.uuid4())
        private = Ed25519PrivateKey.generate()
        response = await self.client.post("/v1/challenge", json={"uuid": player, "name": name,
                    "publicKey": encode(private.public_key().public_bytes(Encoding.DER, PublicFormat.SubjectPublicKeyInfo))})
        self.assertEqual(response.status, 200)
        challenge = (await response.json())["challenge"]
        payload = {"challenge": challenge, "signature": encode(private.sign(challenge.encode()))}
        response = await self.client.post("/v1/verify", json=payload)
        self.assertEqual(response.status, 200)
        return player, {"Authorization": "Bearer " + (await response.json())["token"]}, payload

    async def football(self, headers, payload, status=200):
        response = await self.client.post("/v1/football", headers=headers, json={"scope": "a" * 64} | payload)
        result = await response.json()
        self.assertEqual(response.status, status, result)
        return result

    async def basketball(self, headers, payload, status=200):
        response = await self.client.post("/v1/basketball", headers=headers, json={"scope": "a" * 64} | payload)
        result = await response.json()
        self.assertEqual(response.status, status, result)
        return result

    async def test_basketball_guest_pickup_pass_and_owner_exclusion(self):
        service = self.app[BASKETBALL]
        service.clock = lambda: self.now
        host_id, host, _ = await self.login("BasketHost")
        guest_id, guest, _ = await self.login("BasketGuest")
        room = await self.basketball(host, {"action": "create", "origin": [0, 64, 0], "size": 1})
        joined = await self.basketball(guest, {"action": "join", "code": room["code"], "position": [0, 64, 0]})
        red = {"action": "sync", "code": room["code"], "key": room["key"], "position": room["spawn"]}
        blue = {"action": "sync", "code": room["code"], "key": joined["key"], "position": joined["spawn"]}
        await self.basketball(host, red | {"ready": True})
        await self.basketball(guest, blue | {"ready": True})
        self.now += 1
        self.assertEqual((await self.basketball(host, red))["phase"], "play")
        blue["position"] = [0, 0, 1]
        blue["round"] = 1
        pickup = {"id": 1, "kind": "pickup"}
        result = await self.basketball(guest, blue | {"command": pickup})
        self.assertEqual(result["owner"], uuid.UUID(guest_id).hex)
        red["position"] = [0, 0, -.5]
        red["round"] = 1
        self.assertEqual((await self.basketball(host, red | {"command": pickup}))["owner"], uuid.UUID(guest_id).hex)
        shot = {"id": 2, "kind": "shoot", "position": [0, 1.3, .4],
                "velocity": [0, .2, -13], "spin": [6, 0, 0]}
        self.assertEqual((await self.basketball(host, red | {"command": shot}))["velocity"], [0, 0, 0])
        shot["kind"] = "pass"
        passed = await self.basketball(guest, blue | {"command": shot, "look": [0, 0, -1]})
        self.assertEqual(passed["owner"], "")
        self.assertEqual(passed["velocity"], [0, .2, -13])
        self.assertEqual((await self.basketball(guest, blue | {"command": shot}))["velocity"], [0, .2, -13])
        self.now += .4
        frame = {"revision": passed["ballRevision"], "ball": [0, .5, -2], "velocity": [0, -2, -12],
                 "spin": [5, 0, 0], "turn": [0, 0, 0, 1]}
        await self.basketball(guest, blue | {"frame": frame})
        self.assertEqual(service.balls[room["code"]].point, [0, 1.3, .4])
        free = await self.basketball(host, red | {"frame": frame})
        self.assertEqual(free["ball"], [0, .5, -2])
        self.assertEqual(free["sport"], "basketball")
        self.assertEqual(free["score"], [0, 0])
        await self.basketball(host, red | {"action": "leave"})
        await self.basketball(guest, blue, 404)

    async def test_basketball_four_players_score_and_out(self):
        service = self.app[BASKETBALL]
        service.clock = lambda: self.now
        players = [await self.login("Basket" + str(i)) for i in range(4)]
        room = await self.basketball(players[0][1], {"action": "create", "origin": [0, 64, 0], "size": 2})
        participants = [room]
        for _, headers, _ in players[1:]:
            participants.append(await self.basketball(headers, {"action": "join", "code": room["code"], "position": [0, 64, 0]}))
        payloads = []
        for player, participant in zip(players, participants):
            payload = {"action": "sync", "code": room["code"], "key": participant["key"], "position": participant["spawn"]}
            payloads.append(payload)
            await self.basketball(player[1], payload | {"ready": True})
        self.now += 1
        await self.basketball(players[0][1], payloads[0])
        ball = service.balls[room["code"]]
        ball.point, ball.velocity = [0, 3.2, 12], [0, -4, 0]
        ball.live_shot, ball.shooter, ball.points = True, uuid.UUID(players[2][0]).hex, 3
        self.now += .05
        frame = {"revision": ball.revision, "ball": [0, 3.05, 12], "velocity": [0, -4, 0],
                 "spin": [0, 0, 0], "turn": [0, 0, 0, 1], "goal": 0}
        result = await self.basketball(players[0][1], payloads[0] | {"round": 1, "frame": frame})
        self.assertEqual((result["phase"], result["score"], result["round"]), ("kickoff", [3, 0], 2))
        self.assertEqual(result["ball"], [0, .121*2.1, 0])
        self.now += 1
        for player, payload in zip(players, payloads):
            await self.basketball(player[1], payload)
        self.now += 1
        self.assertEqual((await self.basketball(players[0][1], payloads[0]))["phase"], "play")
        ball = service.balls[room["code"]]
        ball.point, ball.velocity = [10.1, 1, 0], [9, 0, 0]
        self.now += .05
        frame = {"revision": ball.revision, "ball": [10.3, 1, 0], "velocity": [9, 0, 0],
                 "spin": [0, 0, 0], "turn": [0, 0, 0, 1], "out": True}
        result = await self.basketball(players[0][1], payloads[0] | {"round": 2, "frame": frame})
        self.assertEqual((result["phase"], result["score"], result["round"]), ("play", [3, 0], 3))
        self.assertEqual((result["owner"], result["ball"]), ("", [0, .121*2.1, 0]))
        self.now += 16
        await self.basketball(players[0][1], payloads[0], 404)

    async def test_football_duel_shared_ball_score_and_leave(self):
        self.app[FOOTBALL].clock = lambda: self.now
        _, host, _ = await self.login("Red")
        _, guest, _ = await self.login("Blue")
        await self.football({}, {"action": "create", "origin": [0, 64, 0], "size": 1}, 401)
        room = await self.football(host, {"action": "create", "origin": [0, 64, 0], "size": 1})
        code = room["code"]
        await self.football(guest, {"action": "join", "code": code, "scope": "b" * 64, "position": [0, 64, 0]}, 403)
        visitor = await self.football(guest, {"action": "join", "code": code, "position": [0, 64, 0]})
        red = {"action": "sync", "code": code, "key": room["key"], "position": room["spawn"]}
        blue = {"action": "sync", "code": code, "key": visitor["key"], "position": visitor["spawn"]}
        self.assertEqual((await self.football(host, red | {"ready": True}))["phase"], "lobby")
        self.assertEqual((await self.football(guest, blue | {"ready": True}))["phase"], "kickoff")
        self.now += 1
        self.assertEqual((await self.football(host, red))["phase"], "play")
        frame = {"ball": [0, .25, 0], "roll": .5}
        await self.football(host, red | {"round": 1, "frame": frame})
        shot = {"id": 1, "power": .7, "look": [0, 0, -1], "curve": False}
        await self.football(guest, blue | {"round": 1, "kick": shot})
        self.assertEqual((await self.football(host, red))["kicks"], [])
        moving = {"ball": [0, .25, 4.5], "roll": .5, "velocity": [0, 0, 15]}
        await self.football(host, red | {"round": 1, "frame": moving})
        shot["id"] = 2
        near = blue | {"position": [0, 0, 1], "round": 1, "kick": shot}
        await self.football(guest, near)
        await self.football(guest, near)
        result = await self.football(host, red)
        self.assertEqual(len(result["kicks"]), 1)
        self.assertEqual(result["kicks"][0]["power"], .7)
        observed = await self.football(guest, blue)
        self.assertEqual((observed["roll"], observed["velocity"]), (.5, [0, 0, 15]))
        await self.football(guest, red, 403)
        goal = red | {"round": 1, "ack": result["kicks"][0]["seq"],
                      "frame": {"ball": [0, .35, 14.3], "roll": 1, "goal": 0}}
        scored = await self.football(host, goal)
        self.assertEqual((scored["score"], scored["phase"], scored["ball"]), ([1, 0], "kickoff", [0, .25, 0]))
        self.assertEqual((await self.football(host, goal))["score"], [1, 0])
        self.assertEqual((await self.football(guest, blue))["score"], [1, 0])
        self.now += 1
        self.assertEqual((await self.football(host, red))["phase"], "play")
        fake_out = red | {"round": 2, "frame": {"ball": [0, .25, 0], "roll": 0, "out": True}}
        await self.football(host, fake_out, 400)
        out = red | {"round": 2, "frame": {"ball": [10.3, .25, 0], "roll": 0, "out": True}}
        reset = await self.football(host, out)
        self.assertEqual((reset["round"], reset["phase"], reset["score"], reset["ball"]),
                         (3, "play", [1, 0], [0, .25, 0]))
        self.assertEqual((await self.football(host, out))["round"], 3)
        self.assertEqual((await self.football(guest, blue))["ball"], [0, .25, 0])
        await self.football(host, red | {"action": "leave"})
        await self.football(guest, blue, 404)

    async def test_football_four_players_take_distinct_team_positions(self):
        self.app[FOOTBALL].clock = lambda: self.now
        headers = [(await self.login("Player" + str(i)))[1] for i in range(5)]
        room = await self.football(headers[0], {"action": "create", "origin": [0, 64, 0], "size": 2})
        join = {"action": "join", "code": room["code"], "position": [0, 64, 0]}
        players = [room] + [await self.football(header, join) for header in headers[1:4]]
        await self.football(headers[4], join, 409)
        self.assertEqual([p["slot"] for p in players], [0, 1, 2, 3])
        self.assertEqual(len({tuple(p["spawn"]) for p in players}), 4)
        for header, player in zip(headers, players):
            result = await self.football(header, {"action": "sync", "code": room["code"], "key": player["key"],
                                                   "position": player["spawn"], "ready": True})
        self.assertEqual(result["phase"], "kickoff")
        self.now += 1
        host = {"action": "sync", "code": room["code"], "key": room["key"], "position": room["spawn"]}
        self.assertEqual((await self.football(headers[0], host))["phase"], "play")
        self.now += 16
        await self.football(headers[0], host, 404)

    async def test_cosmetics_and_java_uuid_contract(self):
        player, headers, _ = await self.login()
        response = await self.client.post("/v1/presence", headers=headers, json={"players": [player], "halo": True, "wings": 2})
        self.assertEqual(response.status, 200)
        result = await response.json()
        compact = uuid.UUID(player).hex
        self.assertEqual(result["online"], [compact])
        self.assertEqual(result["halos"], [compact])
        self.assertEqual(result["wings"], {compact: 2})
        self.assertGreater(result["revision"], 0)
        _, other, _ = await self.login("OtherProbe")
        response = await self.client.post("/v1/presence", headers=other, json={"players": [player], "halo": False, "wings": 0})
        self.assertEqual((await response.json())["wings"], {compact: 2})

    async def test_signed_challenge_is_single_use(self):
        _, _, payload = await self.login()
        response = await self.client.post("/v1/verify", json=payload)
        self.assertEqual(response.status, 401)
        response = await self.client.post("/v1/presence", json={"players": []})
        self.assertEqual(response.status, 401)

    async def test_config_publication_and_download(self):
        payload = {"name": "PvP", "schema": 1, "settings": {"zoomEnabled": True, "accentColor": "#FF9B38"}}
        self.assertEqual((await self.client.post("/v1/configs", json=payload)).status, 401)
        _, headers, _ = await self.login()
        for private in ({"waypoints": []}, {"discordApplicationId": 42}, {"unknown": "secret"}):
            rejected = await self.client.post("/v1/configs", headers=headers, json=payload | {"settings": private})
            self.assertEqual(rejected.status, 400)
        created = await self.client.post("/v1/configs", headers=headers, json=payload)
        self.assertEqual(created.status, 201)
        metadata = await created.json()
        listing = await (await self.client.get("/v1/configs")).json()
        self.assertEqual(listing, {"items": [metadata], "more": False, "total": 1})
        saved = await (await self.client.get("/v1/configs/" + str(metadata["id"]))).json()
        self.assertEqual(saved, metadata | payload)
        self.assertEqual((await self.client.get("/v1/configs/999")).status, 404)

    async def test_config_publication_limits(self):
        from unittest.mock import AsyncMock, patch

        _, free, _ = await self.login("FreeAuthor")
        _, plus, _ = await self.login("PlusAuthor")
        _, alternate, _ = await self.login("Alternate")
        payload = {"name": "PvP", "schema": 1, "settings": {"zoomEnabled": True}}
        for _ in range(3):
            self.assertEqual((await self.client.post("/v1/configs", headers=free, json=payload)).status, 201)
        self.assertEqual((await self.client.post("/v1/configs", headers=free, json=payload)).status, 429)
        # Bypass only the IP rate limit so these requests exercise the publication quota.
        with patch("app.catalog_access", AsyncMock(return_value=(123, 10))):
            for _ in range(10):
                response = await self.client.post("/v1/configs", headers=plus | {"X-Real-IP": "192.0.2.10"}, json=payload)
                self.assertEqual(response.status, 201)
            response = await self.client.post("/v1/configs", headers=alternate | {"X-Real-IP": "192.0.2.10"}, json=payload)
            self.assertEqual(response.status, 429)

    async def test_config_owner_edits_unique_downloads_and_delete(self):
        from unittest.mock import AsyncMock, patch
        _, owner, _ = await self.login("Author")
        _, visitor, _ = await self.login("Reader")
        payload = {"name": "Original", "schema": 1, "settings": {"zoomEnabled": True}}
        entry = await (await self.client.post("/v1/configs", headers=owner, json=payload)).json()
        path = "/v1/configs/" + str(entry["id"])
        for headers in (owner, visitor, visitor):
            self.assertEqual((await self.client.post(path + "/download", headers=headers, json={})).status, 200)
        saved = await (await self.client.get(path)).json()
        self.assertEqual(saved["downloads"], 1)
        self.assertEqual((await self.client.get("/v1/configs/mine")).status, 401)
        mine = await (await self.client.get("/v1/configs/mine", headers=visitor)).json()
        self.assertEqual(mine["total"], 0)
        self.assertEqual((await self.client.delete(path, headers=owner, json={"version": 1})).status, 403)
        verifier = patch("app.verify_config_owner", new_callable=AsyncMock)
        verifier.start()
        self.addCleanup(verifier.stop)
        for method in (self.client.patch, self.client.delete):
            self.assertEqual((await method(path, headers=visitor, json={"version": 1, "name": "Wrong"})).status, 403)
        changed = await self.client.patch(path, headers=owner, json={"version": 1, "name": "Renamed"})
        self.assertEqual(changed.status, 200)
        updated = await changed.json()
        self.assertEqual(updated["name"], "Renamed")
        self.assertEqual(updated["settings"], payload["settings"])
        self.assertEqual(updated["downloads"], 1)
        self.assertEqual(updated["version"], 2)
        self.assertEqual((await self.client.patch(path, headers=owner, json={"version": 1, "name": "Stale"})).status, 409)
        replacement = await self.client.patch(path, headers=owner, json={"version": 2, "schema": 1, "settings": {"zoomEnabled": False}})
        self.assertEqual(replacement.status, 200)
        self.assertEqual((await replacement.json())["settings"], {"zoomEnabled": False})
        self.assertEqual((await self.client.delete(path, headers=owner, json={"version": 3})).status, 200)
        self.assertEqual((await self.client.get(path)).status, 404)
        self.assertEqual((await self.client.delete(path, headers=owner, json={"version": 3})).status, 200)
        next_entry = await (await self.client.post("/v1/configs", headers=owner, json=payload)).json()
        self.assertGreater(next_entry["id"], entry["id"])

    async def test_legacy_catalog_migrates_without_data_loss(self):
        import sqlite3
        path = str(Path(self.directory.name) / "legacy.db")
        with sqlite3.connect(path) as db:
            db.execute("CREATE TABLE shared_configs(id INTEGER PRIMARY KEY, owner TEXT, author TEXT, name TEXT, settings TEXT, created_at REAL)")
            db.execute("INSERT INTO shared_configs VALUES (1, 'owner', 'Author', 'Legacy', '{}', 100)")
        store = Store(path)
        await store.open()
        try:
            async with store.db.execute("SELECT * FROM shared_configs WHERE id=1") as cursor:
                row = await cursor.fetchone()
            self.assertEqual(row["name"], "Legacy")
            self.assertEqual(row["created_at"], 100)
            self.assertEqual(row["downloads"], 0)
            self.assertEqual(row["version"], 1)
        finally:
            await store.close()

    async def test_emote_lifecycle_and_legacy_presence(self):
        player, headers, _ = await self.login()
        await self.client.post("/v1/presence", headers=headers, json={"players": []})
        for invalid in (-1, 7, True, "1"):
            response = await self.client.post("/v1/emote", headers=headers, json={"emote": invalid})
            self.assertEqual(response.status, 400)
        response = await self.client.post("/v1/emote", json={"emote": 1})
        self.assertEqual(response.status, 401)
        revision = self.app[STORE].revision
        response = await self.client.post("/v1/emote", headers=headers, json={"emote": 4})
        self.assertEqual(response.status, 200)
        self.assertGreater(self.app[STORE].revision, revision)
        self.now += 2
        state = await (await self.client.post("/v1/presence", headers=headers, json={"players": [player]})).json()
        self.assertEqual(state["emotes"][uuid.UUID(player).hex]["id"], 4)
        self.assertEqual(state["emotes"][uuid.UUID(player).hex]["elapsed"], 2)
        self.now += 11
        state = await (await self.client.post("/v1/presence", headers=headers, json={"players": [player]})).json()
        self.assertEqual(state["emotes"], {})
        await self.client.post("/v1/emote", headers=headers, json={"emote": 2})
        await self.client.post("/v1/emote", headers=headers, json={"emote": 0})
        state = await (await self.client.post("/v1/presence", headers=headers, json={"players": [player]})).json()
        self.assertEqual(state["emotes"], {})

    async def test_invalid_signature_and_expiry(self):
        private = Ed25519PrivateKey.generate()
        response = await self.client.post("/v1/challenge", json={"uuid": str(uuid.uuid4()), "name": "Probe", "publicKey": encode(private.public_key().public_bytes(Encoding.DER, PublicFormat.SubjectPublicKeyInfo))})
        challenge = (await response.json())["challenge"]
        response = await self.client.post("/v1/verify", json={"challenge": challenge, "signature": encode(Ed25519PrivateKey.generate().sign(challenge.encode()))})
        self.assertEqual(response.status, 401)
        self.now += 61
        response = await self.client.post("/v1/verify", json={"challenge": challenge, "signature": encode(private.sign(challenge.encode()))})
        self.assertEqual(response.status, 401)

    async def test_watch_receives_changes(self):
        player, headers, _ = await self.login()
        response = await self.client.post("/v1/presence", headers=headers, json={"players": [player], "halo": False, "wings": 0})
        revision = (await response.json())["revision"]
        watcher = asyncio.create_task(self.client.post("/v1/watch", headers=headers, json={"players": [], "revision": revision}))
        await asyncio.sleep(.05)
        response = await self.client.post("/v1/presence", headers=headers, json={"players": [], "halo": True, "wings": 1})
        self.assertEqual(response.status, 200)
        notification = await asyncio.wait_for(watcher, 2)
        self.assertGreater((await notification.json())["revision"], revision)

    async def test_presence_expiry_and_session_expiry(self):
        player, headers, _ = await self.login()
        await self.client.post("/v1/presence", headers=headers, json={"players": [], "halo": True, "wings": 2})
        self.now += 91
        await self.app[STORE].cleanup()
        _, other, _ = await self.login("OtherProbe")
        response = await self.client.post("/v1/presence", headers=other, json={"players": [player], "halo": False, "wings": 0})
        self.assertEqual((await response.json())["online"], [])
        self.now += 86401
        response = await self.client.post("/v1/presence", headers=headers, json={"players": []})
        self.assertEqual(response.status, 401)

    async def test_input_limits_and_health(self):
        response = await self.client.get("/healthz")
        self.assertEqual((await response.json())["protocol"], 1)
        _, headers, _ = await self.login()
        for payload in ({"players": ["bad"]}, {"players": [str(uuid.uuid4())] * 101}, {"players": [], "wings": 3}, {"players": [], "halo": "yes"}):
            response = await self.client.post("/v1/presence", headers=headers, json=payload)
            self.assertEqual(response.status, 400)
        response = await self.client.post("/v1/presence", headers=headers, json={"padding": "x" * 70000})
        self.assertEqual(response.status, 413)

    async def test_database_survives_reopen(self):
        path = str(Path(self.directory.name) / "reopen.db")
        store = Store(path, clock=lambda: self.now)
        await store.open()
        private = Ed25519PrivateKey.generate()
        player = str(uuid.uuid4())
        challenge = (await store.challenge({"uuid": player, "name": "PersistProbe", "publicKey": encode(private.public_key().public_bytes(Encoding.DER, PublicFormat.SubjectPublicKeyInfo))}))["challenge"]
        token = (await store.verify({"challenge": challenge, "signature": encode(private.sign(challenge.encode()))}))["token"]
        identity = await store.authenticate("Bearer " + token)
        before = await store.presence(identity, {"players": [player], "halo": True, "wings": 2})
        await store.close()
        store = Store(path, clock=lambda: self.now)
        await store.open()
        try:
            identity = await store.authenticate("Bearer " + token)
            after = await store.presence(identity, {"players": [player], "halo": True, "wings": 2})
            self.assertEqual(before, after)
        finally:
            await store.close()

if __name__ == "__main__":
    unittest.main()
