package ru.orion.client.football;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.basketball.OrionBasketballBox;
import ru.orion.client.basketball.OrionBasketballVector;
import ru.orion.client.effect.OrionBadgePresence.FootballConnection;
import ru.orion.client.gui.OrionGuiLanguage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Комната, виртуальное поле и обмен кадрами между участниками. */
final class OrionFootballMatch {
    static final int HALF_WIDTH = 10, HALF_LENGTH = 14;
    private static final OrionBasketballVector CENTER = new OrionBasketballVector(0, .25, 0);
    private static final List<OrionFootballGoal> GOALS = List.of(
            new OrionFootballGoal(new OrionBasketballVector(0, 0, HALF_LENGTH), 0, 1),
            new OrionFootballGoal(new OrionBasketballVector(0, 0, -HALF_LENGTH), Math.PI, 1));
    private static final List<OrionBasketballBox> SURFACES = List.of(
            new OrionBasketballBox(-13, -1, -18, 13, 0, 18),
            new OrionBasketballBox(-13, 7, -18, 13, 8, 18));
    private static final OrionFootballPhysics.World PITCH = new OrionFootballPhysics.World() {
        @Override public List<OrionBasketballBox> obstacles(OrionBasketballVector from, double radius, OrionBasketballVector delta) {
            return SURFACES;
        }
        @Override public double groundFriction(OrionBasketballVector position) { return .09; }
    };
    private record Room(String code, String key, boolean host, int size, int count, int slot,
                        boolean ready, String phase, int round, int red, int blue,
                        OrionBasketballVector origin, OrionBasketballVector spawn) { }

    private final OrionFootballPhysics ball = new OrionFootballPhysics();
    private final List<OrionGoalkeeperAI> keepers = List.of(new OrionGoalkeeperAI(0), new OrionGoalkeeperAI(1));
    private Room room;
    private ClientLevel level;
    private String scope, status = "";
    private FootballConnection connection;
    private CompletableFuture<JsonObject> pending;
    private Boolean readyWanted;
    private final ArrayDeque<JsonObject> kicks = new ArrayDeque<>();
    private final ArrayDeque<OrionBasketballVector> recentBall = new ArrayDeque<>();
    private boolean pendingOut;
    private int kickId, pendingGoal = -1;
    private long ack, nextPoll, lastPacket, changedAt;
    private long outAt, lastLocalKick;
    private double blendNanos = 100_000_000D;
    private OrionBasketballVector previous = CENTER, current = CENTER;
    private OrionBasketballVector authoritative = CENTER;
    private float previousRoll, currentRoll;

    OrionFootballMatch() {
        keepers.forEach(keeper -> keeper.setEnabled(false));
    }

    boolean active() { return room != null; }
    boolean playable() { return room != null && "play".equals(room.phase()); }
    String code() { return room == null ? "" : room.code(); }
    int red() { return room == null ? 0 : room.red(); }
    int blue() { return room == null ? 0 : room.blue(); }
    boolean canReach(Vec3 feet) {
        if (!playable()) return false;
        var point = vector(feet).subtract(room.origin());
        if (!insidePitch(point)) return false;
        double reachSquared = 2.75 * 2.75;
        return point.subtract(renderPosition(blend(System.nanoTime()))).lengthSquared() <= reachSquared
                || point.subtract(authoritative).lengthSquared() <= reachSquared;
    }

    String status() {
        if (!status.isEmpty()) return status;
        if (room == null) return pending == null ? "Комнаты нет" : "Подключаемся к матчу…";
        return switch (room.phase()) {
            case "lobby" -> room.ready() ? "Ждём остальных" : "Нажми «Готов»";
            case "kickoff" -> "Займи стартовую точку";
            case "finished" -> room.red() >= 5 ? "Красные победили" : "Синие победили";
            default -> outAt != 0 && System.nanoTime() - outAt < 1_200_000_000L ? "Аут · мяч в центре" : "Матч идёт";
        };
    }

    String info() {
        if (room == null) return "—";
        return room.code() + " · " + room.count() + "/" + (room.size() * 2) + " · "
                + OrionGuiLanguage.translate(room.slot() % 2 == 0 ? "Красные" : "Синие");
    }

    boolean create(Minecraft client, int size) {
        if (!canOpen(client)) return false;
        BlockPos center = BlockPos.containing(client.player.position());
        if (!flat(client.level, center)) {
            status = "Нужна ровная площадка 21 × 35";
            return false;
        }
        var request = new JsonObject();
        request.addProperty("action", "create");
        request.addProperty("size", size);
        request.add("origin", array(new OrionBasketballVector(center.getX() + .5, center.getY(), center.getZ() + .5)));
        open(client, request);
        return true;
    }

    boolean join(Minecraft client, String code) {
        if (!canOpen(client)) return false;
        code = code.strip().toUpperCase(Locale.ROOT);
        if (!code.matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}")) {
            status = "Введи код из 6 символов";
            return false;
        }
        var request = new JsonObject();
        request.addProperty("action", "join");
        request.addProperty("code", code);
        request.add("position", array(vector(client.player.position())));
        open(client, request);
        return true;
    }

    private boolean canOpen(Minecraft client) {
        if (room != null || pending != null) return false;
        if (client.player == null || client.level == null || client.getConnection() == null || client.isLocalServer()) {
            status = "Зайди на Minecraft-сервер";
            return false;
        }
        return true;
    }

    private void open(Minecraft client, JsonObject request) {
        level = client.level;
        var server = client.getCurrentServer();
        String endpoint = server == null ? client.getConnection().getConnection().getRemoteAddress().toString() : server.ip;
        scope = scope(endpoint.strip().toLowerCase(Locale.ROOT) + "|" + level.dimension().identifier());
        request.addProperty("scope", scope);
        var user = client.getUser();
        connection = new FootballConnection(client.player.getUUID(), user.getName());
        status = "";
        submit(client, request, null);
    }

    private static String scope(String world) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(world.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean flat(ClientLevel level, BlockPos center) {
        var pos = new BlockPos.MutableBlockPos();
        for (int x = -HALF_WIDTH; x <= HALF_WIDTH; x++) {
            for (int z = -HALF_LENGTH - 3; z <= HALF_LENGTH + 3; z++) {
                pos.set(center.getX() + x, center.getY() - 1, center.getZ() + z);
                if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isCollisionShapeFullBlock(level, pos)) return false;
                for (int y = 0; y < 7; y++) {
                    pos.setY(center.getY() + y);
                    var state = level.getBlockState(pos);
                    if (!state.getFluidState().isEmpty() || !state.getCollisionShape(level, pos).isEmpty()) return false;
                }
            }
        }
        return true;
    }

    void ready() {
        if (room != null && "lobby".equals(room.phase())) {
            readyWanted = !(readyWanted == null ? room.ready() : readyWanted);
            nextPoll = 0;
        }
    }

    void leave() { close("Ты вышел из комнаты", true); }

    void close(String reason, boolean notifyServer) {
        if (notifyServer && room != null && connection != null) {
            var request = request("leave");
            var transport = connection;
            if (pending == null) transport.request(request);
            else pending.handle((response, error) -> null).thenCompose(ignored -> transport.request(request));
        }
        room = null;
        pending = null;
        connection = null;
        level = null;
        readyWanted = null;
        kicks.clear();
        recentBall.clear();
        kickId = 0;
        ack = 0;
        pendingGoal = -1;
        pendingOut = false;
        outAt = lastLocalKick = 0;
        nextPoll = 0;
        status = reason;
    }

    void tick(Minecraft client, boolean enabled) {
        if (level == null) return;
        if (!enabled || client.level != level || client.player == null) {
            close("Матч завершён", true);
            return;
        }
        if (room == null) return;
        long now = System.nanoTime();
        if (now - lastPacket > 12_000_000_000L) {
            close("Связь с матчем потеряна", false);
            return;
        }
        var position = vector(client.player.position()).subtract(room.origin());
        if (position.lengthSquared() > 100 * 100) {
            close("Ты покинул площадку", true);
            return;
        }
        if (room.host() && playable() && pendingGoal < 0 && !pendingOut) {
            if (recentBall.size() >= 6) recentBall.removeFirst();
            recentBall.addLast(ball.position());
            var result = ball.advance(.05, PITCH, GOALS, keepers, (kind, point, speed) -> { });
            if (result.goals() > 0) pendingGoal = ball.goalIndex();
            else pendingOut = outside(ball.position());
            if (pendingGoal >= 0 || pendingOut) nextPoll = 0;
            authoritative = ball.position();
            updateBall(authoritative, roll(ball.roll()), 50_000_000D);
        }
        if (pending != null || now < nextPoll) return;
        nextPoll = now + ("lobby".equals(room.phase()) || "finished".equals(room.phase()) ? 400_000_000L : 45_000_000L);
        var request = request("sync");
        request.addProperty("round", room.round());
        request.add("position", array(position));
        if (readyWanted != null) request.addProperty("ready", readyWanted);
        if (room.host()) {
            request.addProperty("ack", ack);
            if (playable()) {
                var frame = new JsonObject();
                frame.add("ball", array(pendingGoal >= 0 ? ball.goalPoint() : ball.position()));
                frame.addProperty("roll", roll(ball.roll()));
                frame.add("velocity", array(ball.velocity()));
                if (pendingGoal >= 0) frame.addProperty("goal", pendingGoal);
                if (pendingOut) frame.addProperty("out", true);
                request.add("frame", frame);
            }
        }
        var nextKick = kicks.peekFirst();
        if (nextKick != null) request.add("kick", nextKick);
        submit(client, request, nextKick);
    }

    boolean kick(Vec3 feet, Vec3 direction, float power, boolean curve) {
        if (!playable() || pendingGoal >= 0 || pendingOut) return false;
        var position = vector(feet).subtract(room.origin());
        if (!insidePitch(position)) return false;
        if (room.host()) {
            long now = System.nanoTime();
            if (now - lastLocalKick >= 200_000_000L && nearBall(position)) {
                lastLocalKick = now;
                applyKick(vector(direction).normalize(), power, curve);
                nextPoll = 0;
                return true;
            }
            return false;
        }
        if (kicks.size() >= 4) return false;
        var next = new JsonObject();
        next.addProperty("id", ++kickId);
        next.addProperty("power", Math.clamp(power, 0F, 1F));
        next.addProperty("curve", curve);
        next.add("look", array(vector(direction).normalize()));
        next.add("position", array(position));
        kicks.addLast(next);
        nextPoll = 0;
        return true;
    }

    private JsonObject request(String action) {
        var request = new JsonObject();
        request.addProperty("action", action);
        request.addProperty("scope", scope);
        request.addProperty("code", room.code());
        request.addProperty("key", room.key());
        return request;
    }

    private void submit(Minecraft client, JsonObject request, JsonObject sentKick) {
        var transport = connection;
        long sentAt = System.nanoTime();
        var future = transport.request(request);
        pending = future;
        future.whenComplete((response, failure) -> client.execute(() -> {
            if (pending != future) {
                // Если клиент ушёл во время создания комнаты, удаляем комнату и на хосте.
                if (failure == null && request.has("scope") && !request.has("key") && response.has("key")) {
                    var leave = new JsonObject();
                    leave.addProperty("action", "leave");
                    leave.add("scope", request.get("scope"));
                    leave.add("key", response.get("key"));
                    leave.add("code", response.get("code"));
                    transport.request(leave);
                }
                return;
            }
            pending = null;
            if (failure == null) {
                try {
                    accept(response, System.nanoTime() - sentAt);
                    if (sentKick != null && kicks.peekFirst() == sentKick) kicks.removeFirst();
                    status = "";
                } catch (RuntimeException invalid) {
                    close("Некорректный ответ матча", true);
                }
            } else {
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                String reason = switch (cause.getMessage() == null ? "" : cause.getMessage()) {
                    case "football_missing", "football_member" -> "Комната закрыта или не найдена";
                    case "football_locked" -> "Комната заполнена или матч уже начался";
                    case "football_world" -> "Ты в другом мире или на другом сервере";
                    case "football_near" -> "Подойди ближе к площадке";
                    default -> "Сервис матчей недоступен";
                };
                if (room == null || "football_missing".equals(cause.getMessage()) || "football_member".equals(cause.getMessage())) {
                    close(reason, false);
                } else {
                    status = reason;
                    nextPoll = System.nanoTime() + 1_000_000_000L;
                }
            }
        }));
    }

    private void accept(JsonObject reply, long roundTrip) {
        var score = reply.getAsJsonArray("score");
        Room next = new Room(reply.get("code").getAsString(), reply.get("key").getAsString(), reply.get("host").getAsBoolean(),
                reply.get("size").getAsInt(), reply.get("count").getAsInt(), reply.get("slot").getAsInt(),
                reply.get("ready").getAsBoolean(), reply.get("phase").getAsString(), reply.get("round").getAsInt(),
                score.get(0).getAsInt(), score.get(1).getAsInt(),
                vector(reply.getAsJsonArray("origin")), vector(reply.getAsJsonArray("spawn")));
        if (room == null || room.round() != next.round()) {
            if (room != null && playable() && "play".equals(next.phase())) outAt = System.nanoTime();
            else outAt = 0;
            ball.reset(CENTER, OrionBasketballVector.ZERO);
            previous = current = authoritative = CENTER;
            previousRoll = currentRoll = 0;
            changedAt = System.nanoTime();
            pendingGoal = -1;
            pendingOut = false;
            kicks.clear();
            recentBall.clear();
        }
        room = next;
        lastPacket = System.nanoTime();
        if (readyWanted != null && readyWanted == next.ready()) readyWanted = null;
        if (!next.host()) {
            authoritative = vector(reply.getAsJsonArray("ball"));
            var point = authoritative;
            if (playable() && reply.has("velocity")) {
                double lead = Math.clamp(roundTrip / 2_000_000_000D + .018, 0, .07);
                var estimate = point.add(vector(reply.getAsJsonArray("velocity")).scale(lead));
                if (!outside(estimate)) point = new OrionBasketballVector(estimate.x(),
                        Math.clamp(estimate.y(), OrionFootballPhysics.RADIUS, 7 - OrionFootballPhysics.RADIUS), estimate.z());
            }
            updateBall(point, reply.get("roll").getAsFloat(), 50_000_000D);
        }
        if (next.host() && playable() && pendingGoal < 0 && !pendingOut) {
            applyRelayedKicks(reply.getAsJsonArray("kicks"));
        }
    }

    private void applyRelayedKicks(JsonArray accepted) {
        // Дальность и темп проверены сервисом по позиции в момент удара и кадрам мяча.
        for (var item : accepted) {
            var event = item.getAsJsonObject();
            long seq = event.get("seq").getAsLong();
            if (seq <= ack) continue;
            var look = vector(event.getAsJsonArray("look")).normalize();
            applyKick(look, event.get("power").getAsDouble(), event.get("curve").getAsBoolean());
            ack = seq;
        }
    }

    private void applyKick(OrionBasketballVector look, double power, boolean curve) {
        var impulse = look.scale(3 + power * 15).add(new OrionBasketballVector(0, .4, 0));
        var velocity = ball.velocity().add(impulse);
        if (velocity.lengthSquared() > 30 * 30) impulse = velocity.normalize().scale(30).subtract(ball.velocity());
        ball.kick(impulse, new OrionBasketballVector(0, curve ? 25 : 0, 0));
    }

    private boolean nearBall(OrionBasketballVector position) {
        if (ball.position().subtract(position).lengthSquared() <= 2.75 * 2.75) return true;
        if (ball.position().subtract(position).lengthSquared() > 5 * 5) return false;
        for (var frame : recentBall) if (frame.subtract(position).lengthSquared() <= 2.75 * 2.75) return true;
        return false;
    }

    private static boolean insidePitch(OrionBasketballVector point) {
        return Math.abs(point.x()) <= HALF_WIDTH + .5 && Math.abs(point.z()) <= HALF_LENGTH + 1;
    }

    private static boolean outside(OrionBasketballVector point) {
        return Math.abs(point.x()) >= HALF_WIDTH + OrionFootballPhysics.RADIUS
                || Math.abs(point.z()) >= HALF_LENGTH + OrionFootballPhysics.RADIUS;
    }

    private void updateBall(OrionBasketballVector point, float roll, double duration) {
        long now = System.nanoTime();
        double part = blend(now);
        previous = renderPosition(part);
        previousRoll = (float) (previousRoll + Math.IEEEremainder(currentRoll - previousRoll, Math.TAU) * part);
        current = point;
        currentRoll = roll;
        changedAt = now;
        blendNanos = duration;
    }

    private double blend(long now) {
        return Math.clamp((now - changedAt) / blendNanos, 0, 1);
    }

    private OrionBasketballVector renderPosition(double part) {
        return previous.add(current.subtract(previous).scale(part));
    }

    void render(LevelRenderContext context) {
        if (room == null) return;
        OrionFootballRenderer.pitch(context, room.origin(), room.spawn(), room.slot() % 2, "kickoff".equals(room.phase()));
        for (var goal : GOALS) {
            var placed = new OrionFootballGoal(room.origin().add(goal.base()), goal.yaw(), 1);
            OrionFootballRenderer.goal(context, placed, null, false, 0, 0, null);
        }
        double part = blend(System.nanoTime());
        var point = room.origin().add(renderPosition(part));
        float angle = (float) (previousRoll + Math.IEEEremainder(currentRoll - previousRoll, Math.TAU) * part);
        OrionFootballRenderer.ball(context, point, angle);
    }

    private static float roll(double radians) { return (float) Math.IEEEremainder(radians, Math.TAU); }
    private static OrionBasketballVector vector(Vec3 point) { return new OrionBasketballVector(point.x, point.y, point.z); }
    private static OrionBasketballVector vector(JsonArray array) {
        return new OrionBasketballVector(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
    }
    private static JsonArray array(OrionBasketballVector point) {
        var result = new JsonArray();
        result.add(point.x());
        result.add(point.y());
        result.add(point.z());
        return result;
    }
}
