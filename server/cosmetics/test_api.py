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
from app import create_app, STORE, Store

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
        self.assertEqual(listing, {"items": [metadata], "more": False})
        saved = await (await self.client.get("/v1/configs/" + str(metadata["id"]))).json()
        self.assertEqual(saved, metadata | payload)
        self.assertEqual((await self.client.get("/v1/configs/999")).status, 404)

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
