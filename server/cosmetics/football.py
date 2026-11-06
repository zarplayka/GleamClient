"""Private Orion football rooms. The host simulates the ball on the fixed virtual pitch."""
from collections import deque
from dataclasses import dataclass, field
import math
import re
import secrets
import time

from aiohttp import web


HALF_WIDTH = 10
HALF_LENGTH = 14
WIN_SCORE = 5
ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
CODE = re.compile(r"[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}")
MAX_ROOMS = 256
TIMEOUT = 15


def number(value, low, high):
    if type(value) not in (int, float) or not low <= value <= high:
        raise ValueError("number")
    return float(value)


def vector(value, limits):
    if not isinstance(value, list) or len(value) != 3:
        raise ValueError("vector")
    return [number(v, lo, hi) for v, (lo, hi) in zip(value, limits)]


@dataclass
class Member:
    slot: int
    seen: float
    key: str = field(default_factory=lambda: secrets.token_urlsafe(24))
    ready: bool = False
    position: list | None = None
    last_kick: float = -10
    kick_sequence: int = 0


@dataclass
class Room:
    host: str
    scope: str
    origin: list
    size: int
    players: dict
    phase: str = "lobby"
    score: list = field(default_factory=lambda: [0, 0])
    ball: list = field(default_factory=lambda: [0, .25, 0])
    roll: float = 0
    velocity: list = field(default_factory=lambda: [0, 0, 0])
    recent_ball: deque = field(default_factory=lambda: deque(maxlen=8))
    round: int = 1
    kickoff_at: float = 0
    sequence: int = 0
    kicks: deque = field(default_factory=lambda: deque(maxlen=64))

    def spawn(self, member):
        x = 0 if self.size == 1 else (-3 if member.slot < 2 else 3)
        return [x, 0, -6 if member.slot % 2 == 0 else 6]

    def reset_ball(self):
        self.round += 1
        self.ball, self.roll, self.velocity = [0, .25, 0], 0, [0, 0, 0]
        self.kicks.clear()
        self.recent_ball.clear()


class FootballRooms:
    def __init__(self, clock=time.monotonic):
        self.rooms = {}
        self.clock = clock

    def handle(self, actor, payload):
        now = self.clock()
        for code, room in list(self.rooms.items()):
            stale = [uuid for uuid, member in room.players.items() if now - member.seen > TIMEOUT]
            if room.host in stale or (stale and room.phase != "lobby"):
                del self.rooms[code]
            else:
                for uuid in stale:
                    del room.players[uuid]

        action = payload.get("action")
        scope = payload.get("scope")
        if not isinstance(scope, str) or not re.fullmatch(r"[0-9a-f]{64}", scope):
            raise ValueError("scope")
        code = payload.get("code", "")
        if action == "create":
            if len(self.rooms) >= MAX_ROOMS:
                raise web.HTTPServiceUnavailable(reason="football_full")
            size = payload.get("size")
            if type(size) is not int or size not in (1, 2):
                raise ValueError("size")
            origin = vector(payload.get("origin"), ((-30_000_000, 30_000_000), (-2048, 2048), (-30_000_000, 30_000_000)))
            code = "".join(secrets.choice(ALPHABET) for _ in range(6))
            while code in self.rooms:
                code = "".join(secrets.choice(ALPHABET) for _ in range(6))
            member = Member(0, now)
            room = self.rooms[code] = Room(actor, scope, origin, size, {actor: member})
            return self.snapshot(code, room, actor, member)

        if not isinstance(code, str) or not CODE.fullmatch(code):
            raise web.HTTPNotFound(reason="football_missing")
        room = self.rooms.get(code)
        if room is None:
            raise web.HTTPNotFound(reason="football_missing")
        if room.scope != scope:
            raise web.HTTPForbidden(reason="football_world")
        if action == "join":
            if room.phase != "lobby" or actor in room.players or len(room.players) >= 2 * room.size:
                raise web.HTTPConflict(reason="football_locked")
            position = vector(payload.get("position"), ((-30_000_000, 30_000_000), (-2048, 2048), (-30_000_000, 30_000_000)))
            if math.dist(position, room.origin) > 42:
                raise web.HTTPForbidden(reason="football_near")
            slots = {member.slot for member in room.players.values()}
            slot = next(slot for slot in range(2 * room.size) if slot not in slots)
            member = room.players[actor] = Member(slot, now)
            return self.snapshot(code, room, actor, member)

        member = room.players.get(actor)
        key = payload.get("key")
        if member is None or not isinstance(key, str) or not secrets.compare_digest(key, member.key):
            raise web.HTTPForbidden(reason="football_member")
        if action == "leave":
            if actor == room.host or room.phase != "lobby":
                del self.rooms[code]
            else:
                del room.players[actor]
            return {}
        if action != "sync":
            raise ValueError("action")

        member.position = vector(payload.get("position"), ((-512, 512), (-512, 512), (-512, 512)))
        member.seen = now
        if room.phase == "lobby":
            ready = payload.get("ready")
            if type(ready) is bool:
                member.ready = ready
            if len(room.players) == 2 * room.size and all(p.ready for p in room.players.values()):
                room.phase = "kickoff"
        if room.phase == "kickoff":
            all_in_place = all(p.position is not None and now - p.seen < 2
                               and math.dist(p.position, room.spawn(p)) <= 2.5 for p in room.players.values())
            if not all_in_place:
                room.kickoff_at = 0
            elif not room.kickoff_at:
                room.kickoff_at = now + .8
            elif now >= room.kickoff_at:
                room.phase = "play"

        if actor == room.host:
            ack = payload.get("ack", 0)
            if type(ack) is not int or not 0 <= ack <= room.sequence:
                raise ValueError("ack")
            while room.kicks and room.kicks[0]["seq"] <= ack:
                room.kicks.popleft()
            if room.phase == "play" and payload.get("round") == room.round and "frame" in payload:
                frame = payload.get("frame")
                if not isinstance(frame, dict):
                    raise ValueError("frame")
                ball = vector(frame.get("ball"), ((-13, 13), (-1, 10), (-18, 18)))
                roll = number(frame.get("roll"), -360, 360)
                velocity = vector(frame.get("velocity", [0, 0, 0]), ((-64, 64),) * 3)
                goal = frame.get("goal")
                out = frame.get("out", False)
                if type(out) is not bool or (out and goal is not None):
                    raise ValueError("event")
                if goal is not None:
                    if (type(goal) is not int or goal not in (0, 1)
                            or (ball[2] if goal == 0 else -ball[2]) < HALF_LENGTH - .4
                            or abs(ball[0]) > 4.3 or not 0 <= ball[1] <= 3.1):
                        raise ValueError("goal")
                    room.score[goal] += 1
                    room.phase = "finished" if room.score[goal] >= WIN_SCORE else "kickoff"
                    room.kickoff_at = 0
                    room.reset_ball()
                elif out:
                    if abs(ball[0]) < HALF_WIDTH + .2 and abs(ball[2]) < HALF_LENGTH + .2:
                        raise ValueError("out")
                    room.reset_ball()
                else:
                    room.recent_ball.append((now, room.ball))
                    room.ball, room.roll, room.velocity = ball, roll, velocity

        kick = payload.get("kick")
        if kick is not None and room.phase == "play" and payload.get("round") == room.round:
            self.kick(room, actor, member, kick, now)
        return self.snapshot(code, room, actor, member)

    @staticmethod
    def kick(room, actor, member, kick, now):
        if not isinstance(kick, dict):
            raise ValueError("kick")
        sequence = kick.get("id")
        if type(sequence) is not int or not 0 < sequence <= 2_147_483_647:
            raise ValueError("kick sequence")
        if sequence <= member.kick_sequence:
            return
        power = number(kick.get("power"), 0, 1)
        look = vector(kick.get("look"), ((-1, 1), (-1, 1), (-1, 1)))
        position = vector(kick.get("position", member.position), ((-512, 512),) * 3)
        if not .97 <= sum(v * v for v in look) <= 1.03 or type(kick.get("curve")) is not bool:
            raise ValueError("kick direction")
        member.kick_sequence = sequence
        if now - member.last_kick < .20 or math.dist(position, member.position) > 3:
            return
        if abs(position[0]) > HALF_WIDTH + .5 or abs(position[2]) > HALF_LENGTH + 1:
            return
        if math.dist(position, room.ball) > 2.75 and not any(
                now - seen < .32 and math.dist(position, old) <= 2.75 for seen, old in room.recent_ball):
            return
        member.last_kick = now
        room.sequence += 1
        room.kicks.append({"seq": room.sequence, "actor": actor, "power": power, "look": look,
                           "position": position, "curve": kick["curve"]})

    @staticmethod
    def snapshot(code, room, actor, member):
        return {"code": code, "key": member.key, "host": actor == room.host, "size": room.size,
                "count": len(room.players), "ready": member.ready, "slot": member.slot,
                "origin": room.origin, "spawn": room.spawn(member), "phase": room.phase,
                "score": room.score, "round": room.round, "ball": room.ball, "roll": room.roll,
                "velocity": room.velocity,
                "kicks": list(room.kicks) if actor == room.host and room.phase == "play" else []}
