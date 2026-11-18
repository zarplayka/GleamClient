"""Basketball rooms using the existing Orion Java physics on the room host."""
import math
from collections import deque
from dataclasses import dataclass, field
from football import FootballRooms, number, vector

RADIUS = .121 * 2.1
RIM_Y = 3.05
RIM_Z = 12
WIN_SCORE = 11
PICKUP_REACH = 2.25
RIM_PROTECTION = 1.5


@dataclass
class BallSounds:
    sequence: int = 0
    host_sequence: int = 0
    events: deque = field(default_factory=lambda: deque(maxlen=24))

    def play(self, now, kind, point, volume, pitch):
        self.sequence += 1
        self.events.append((now, {"id": self.sequence, "kind": kind, "point": point,
                                  "volume": volume, "pitch": pitch}))


@dataclass
class Ball:
    point: list = field(default_factory=lambda: [0, RADIUS, 0])
    velocity: list = field(default_factory=lambda: [0, 0, 0])
    spin: list = field(default_factory=lambda: [0, 0, 0])
    turn: list = field(default_factory=lambda: [0, 0, 0, 1])
    owner: str = ""
    released_at: float = 0
    shooter: str = ""
    points: int = 2
    live_shot: bool = False
    revision: int = 0
    dribble: bool = False
    dribble_low: bool = False
    dribble_sound_at: float = -math.inf
    commands: dict = field(default_factory=dict)
    pickup_attempts: dict = field(default_factory=dict)
    sounds: BallSounds = field(default_factory=BallSounds)


class BasketballRooms(FootballRooms):
    def __init__(self, clock=None):
        if clock is None:
            super().__init__()
        else:
            super().__init__(clock)
        self.balls = {}

    def handle(self, actor, payload):
        common = {key: value for key, value in payload.items() if key not in ("frame", "kick")}
        result = super().handle(actor, common)
        self.balls = {code: ball for code, ball in self.balls.items() if code in self.rooms}
        if not result:
            return result
        code = result["code"]
        room = self.rooms[code]
        ball = self.balls.setdefault(code, Ball())
        now = self.clock()
        if payload.get("action") == "sync" and room.phase == "play":
            member = room.players[actor]
            if ball.owner == actor:
                if abs(member.position[0]) > 10 or abs(member.position[2]) > 14 or not -.2 <= member.position[1] <= 3:
                    self.reset(code, room)
                    ball = self.balls[code]
                else:
                    held = payload.get("held")
                    if held is not None:
                        held = vector(held, ((-512, 512),) * 3)
                        if math.dist(held, member.position) > 3:
                            raise ValueError("held")
                        ball.point = held
                        if ball.dribble:
                            low = held[1] - member.position[1] <= RADIUS + .14
                            if low and not ball.dribble_low and now - ball.dribble_sound_at >= .32:
                                ball.sounds.play(now, 6, held, .5, 1)
                                ball.dribble_sound_at = now
                            ball.dribble_low = low
            frame = payload.get("frame")
            if (actor == room.host and isinstance(frame, dict) and not ball.owner
                    and payload.get("round") == room.round and frame.get("revision") == ball.revision):
                self.frame(code, room, ball, frame, now)
                ball = self.balls[code]
            command = payload.get("command")
            if command is not None and payload.get("round") == room.round and room.phase == "play":
                self.command(room, ball, actor, command, now, payload.get("look"))

        result = self.snapshot(code, room, actor, room.players[actor])
        result.update({"owner": ball.owner, "ball": ball.point, "velocity": ball.velocity,
                       "spin": ball.spin, "turn": ball.turn, "ballRevision": ball.revision,
                       "liveShot": ball.live_shot, "dribble": ball.dribble, "shooter": ball.shooter,
                       "sport": "basketball",
                       "sounds": [event for time, event in ball.sounds.events if now - time <= 1.5]})
        return result

    def frame(self, code, room, ball, frame, now):
        point = vector(frame.get("ball"), ((-13, 13), (-4, 25), (-18, 18)))
        velocity = vector(frame.get("velocity"), ((-45, 45),) * 3)
        spin = vector(frame.get("spin"), ((-65, 65),) * 3)
        turn = frame.get("turn")
        if not isinstance(turn, list) or len(turn) != 4:
            raise ValueError("turn")
        turn = [number(v, -1, 1) for v in turn]
        if not .97 <= sum(v*v for v in turn) <= 1.03:
            raise ValueError("turn")
        goal = frame.get("goal")
        out = frame.get("out", False)
        if type(out) is not bool or (out and goal is not None):
            raise ValueError("event")
        sounds = frame.get("sounds", [])
        if not isinstance(sounds, list) or len(sounds) > 12:
            raise ValueError("sounds")
        collisions = []
        sequence = ball.sounds.host_sequence
        for sound in sounds:
            if not isinstance(sound, dict):
                raise ValueError("sound")
            sound_id, kind = sound.get("id"), sound.get("kind")
            if (type(sound_id) is not int or not 0 < sound_id <= 2_147_483_647
                    or type(kind) is not int or not 0 <= kind <= 2):
                raise ValueError("sound")
            if sound_id > sequence:
                location = vector(sound.get("point"), ((-13, 13), (-2, 25), (-18, 18)))
                speed = number(sound.get("speed"), .9, 100)
                collisions.append((kind, location, min(1, max(.1, speed / 7)), .92 + min(.3, speed / 26)))
                sequence = sound_id
        if goal is not None:
            if type(goal) is not int or goal not in (0, 1) or not ball.live_shot:
                raise ValueError("goal")
            z = RIM_Z if goal == 0 else -RIM_Z
            if abs(point[1]-RIM_Y) > .35 or math.hypot(point[0], point[2]-z) > .55:
                raise ValueError("rim crossing")
            shooter = room.players.get(ball.shooter)
            room.score[goal] += ball.points if shooter is not None and shooter.slot % 2 == goal else 2
            self.reset(code, room, basket=True)
        elif out:
            if abs(point[0]) < 10+RADIUS-.01 and abs(point[2]) < 14+RADIUS-.01 and point[1] >= 0:
                raise ValueError("out")
            self.reset(code, room)
        else:
            ball.point, ball.velocity, ball.spin, ball.turn = point, velocity, spin, turn
        ball.sounds.host_sequence = sequence
        for collision in collisions:
            ball.sounds.play(now, *collision)
        if goal is not None:
            ball.sounds.play(now, 3, point, .85, 1.06)

    def command(self, room, ball, actor, command, now, look):
        if not isinstance(command, dict):
            raise ValueError("command")
        sequence = command.get("id")
        if type(sequence) is not int or not 0 < sequence <= 2_147_483_647:
            raise ValueError("sequence")
        kind = command.get("kind")
        if kind not in ("pickup", "shoot", "pass", "dribble"):
            raise ValueError("kind")
        if sequence <= ball.commands.get(actor, 0):
            return
        member = room.players[actor]
        if kind in ("shoot", "pass"):
            point = vector(command.get("position"), ((-512, 512),) * 3)
            velocity = vector(command.get("velocity"), ((-30, 30),) * 3)
            spin = vector(command.get("spin"), ((-30, 30),) * 3)
            if math.dist(point, member.position) > 3 or math.sqrt(sum(v*v for v in velocity)) > 27:
                raise ValueError("launch")
        ball.commands[actor] = sequence
        if kind == "pickup":
            if "revision" in command and command["revision"] != ball.revision:
                return
            if ball.owner == actor:
                if ball.dribble:
                    ball.dribble = False
                    ball.revision += 1
                return
            if ball.owner or now - ball.pickup_attempts.get(actor, -math.inf) < .25:
                return
            ball.pickup_attempts[actor] = now
            if (now - ball.released_at < .35 or abs(member.position[0]) > 10
                    or abs(member.position[2]) > 14 or not -.2 <= member.position[1] <= 3):
                return
            if (ball.live_shot and ball.point[1] >= RIM_Y - RADIUS
                    and math.hypot(ball.point[0], abs(ball.point[2]) - RIM_Z) <= RIM_PROTECTION):
                return
            if not self.can_reach_ball(member, ball, look):
                return
            ball.owner, ball.dribble, ball.live_shot = actor, False, False
            ball.velocity, ball.spin = [0, 0, 0], [0, 0, 0]
            ball.revision += 1
            ball.sounds.play(now, 4, ball.point, .55, 1)
        elif ball.owner == actor and kind == "dribble":
            ball.dribble = not ball.dribble
            ball.dribble_low = False
            ball.revision += 1
        elif ball.owner == actor:
            ball.owner = ""
            ball.shooter = actor
            ball.released_at = now
            ball.live_shot = True
            ball.dribble = False
            hoop = RIM_Z if member.slot % 2 == 0 else -RIM_Z
            ball.points = 3 if math.hypot(member.position[0], member.position[2]-hoop) >= 6.75 else 2
            ball.point, ball.velocity, ball.spin = point, velocity, spin
            ball.revision += 1
            ball.sounds.play(now, 5, point, .6, 1)

    @staticmethod
    def can_reach_ball(member, ball, look):
        if look is None:
            return False
        direction = vector(look, ((-1, 1),) * 3)
        if not .97 <= sum(v * v for v in direction) <= 1.03:
            return False
        x, y, z = member.position
        offset = [ball.point[0] - x, ball.point[1] - y - 1.62, ball.point[2] - z]
        distance = math.hypot(*offset)
        return distance <= PICKUP_REACH and (distance <= RADIUS or
                sum(a * b for a, b in zip(offset, direction)) >= .5 * distance)

    def reset(self, code, room, basket=False):
        previous = self.balls[code]
        room.reset_ball()
        room.phase = "finished" if max(room.score) >= WIN_SCORE else ("kickoff" if basket else "play")
        if room.phase == "finished":
            for participant in room.players.values():
                participant.ready = False
        room.kickoff_at = 0
        self.balls[code] = Ball(revision=previous.revision+1, commands=previous.commands,
                                pickup_attempts=previous.pickup_attempts, sounds=previous.sounds)

    def restart(self, code, room):
        previous = self.balls[code]
        super().restart(code, room)
        self.balls[code] = Ball(revision=previous.revision + 1, commands=previous.commands, sounds=previous.sounds)
