"""Basketball rooms using the existing Orion Java physics on the room host."""
import math
from dataclasses import dataclass, field
from football import FootballRooms, number, vector

RADIUS = .121 * 2.1
RIM_Y = 3.05
RIM_Z = 12
WIN_SCORE = 11


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
    commands: dict = field(default_factory=dict)


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
            frame = payload.get("frame")
            if (actor == room.host and isinstance(frame, dict) and not ball.owner
                    and payload.get("round") == room.round and frame.get("revision") == ball.revision):
                self.frame(code, room, ball, frame)
                ball = self.balls[code]
            command = payload.get("command")
            if command is not None and payload.get("round") == room.round and room.phase == "play":
                self.command(room, ball, actor, command, now)

        result = self.snapshot(code, room, actor, room.players[actor])
        result.update({"owner": ball.owner, "ball": ball.point, "velocity": ball.velocity,
                       "spin": ball.spin, "turn": ball.turn, "ballRevision": ball.revision,
                       "liveShot": ball.live_shot, "dribble": ball.dribble, "shooter": ball.shooter,
                       "sport": "basketball"})
        return result

    def frame(self, code, room, ball, frame):
        point = vector(frame.get("ball"), ((-13, 13), (-1, 25), (-18, 18)))
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

    def command(self, room, ball, actor, command, now):
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
            if (ball.owner and ball.owner != actor) or now-ball.released_at < .35:
                return
            if not ball.owner and math.dist(member.position, ball.point) > 2.5:
                return
            if abs(member.position[0]) > 10 or abs(member.position[2]) > 14:
                return
            ball.owner, ball.dribble, ball.live_shot = actor, False, False
            ball.velocity, ball.spin = [0, 0, 0], [0, 0, 0]
            ball.revision += 1
        elif ball.owner == actor and kind == "dribble":
            ball.dribble = not ball.dribble
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

    def reset(self, code, room, basket=False):
        previous = self.balls[code]
        room.reset_ball()
        room.phase = "finished" if max(room.score) >= WIN_SCORE else ("kickoff" if basket else "play")
        room.kickoff_at = 0
        self.balls[code] = Ball(revision=previous.revision+1, commands=previous.commands)
