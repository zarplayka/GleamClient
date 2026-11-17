package ru.orion.client.hud;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.effect.OrionNameProtect;

import java.util.Locale;
import java.util.Objects;

public final class OrionHitDistanceHud {
    public static final String ID = "hit_distance";
    private static final int WIDTH = 127;
    private static final int HEADER_HEIGHT = 22;
    private static final int ROW_HEIGHT = 17;
    private static final int HEIGHT = HEADER_HEIGHT + ROW_HEIGHT + 5;
    private static final int PAD_X = 7;
    private static final long HISTORY_NS = 600_000_000L;
    private static final int MAX_SAMPLES = 32;

    private final OrionConfigManager manager;
    private final OrionHudView view = new OrionHudView(ID);
    private final OrionHudDrag drag = new OrionHudDrag(ID);
    private LocalPlayer victim;
    private Level level;
    private final ArrayDeque<Snapshot> movement = new ArrayDeque<>();
    private Hit lastHit;
    private Hit shownHit;
    private String shownName;
    private boolean shownCompact;
    private int sceneWidth;
    private int sceneHeight;
    private OrionHudView.Scene scene;

    private record Hit(String attacker, String distance) {}
    private record Snapshot(long time, AABB box) {}

    public OrionHitDistanceHud(OrionConfigManager manager) {
        this.manager = manager;
    }

    public void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, ID), this::render);
        ClientTickEvents.END_CLIENT_TICK.register(this::captureMovement);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            clearHit();
            drag.cancel(manager);
            view.dispose();
        }));
    }

    public void onDamage(ClientboundDamageEventPacket packet) {
        var client = Minecraft.getInstance();
        if (!manager.getConfig().isHitDistanceEnabled() || client.level == null
                || client.player == null || client.player.getId() != packet.entityId()) return;
        var source = packet.getSource(client.level);
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker
                || attacker == client.player || source.is(DamageTypes.THORNS)) return;

        syncWorld(client);
        long now = System.nanoTime();
        var connection = client.getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(victim.getUUID());
        long roundTrip = info == null ? 0 : Math.clamp(info.getLatency(), 0, 400) * 1_000_000L;
        var current = new Snapshot(now, victim.getBoundingBox());
        var hitBox = positionAt(now - roundTrip, current);

        // Remote entities are interpolated for rendering. The position codec stores the latest server snapshot.
        var eye = attacker.getEyePosition();
        var from = attacker.position();
        var tracked = attacker.getPositionCodec().getBase();
        if (!attacker.isPassenger() && tracked.distanceToSqr(from) < 16D) {
            eye = eye.add(tracked.subtract(from));
        }
        double distance = Math.sqrt(hitBox.distanceToSqr(eye));
        if (Double.isFinite(distance)) {
            lastHit = new Hit(attacker.getName().getString(), String.format(Locale.ROOT, "%.1f", distance));
        }
    }

    private void captureMovement(Minecraft client) {
        if (!manager.getConfig().isHitDistanceEnabled() || client.player == null || client.level == null) {
            clearHit();
            return;
        }
        syncWorld(client);
        var sample = new Snapshot(System.nanoTime(), victim.getBoundingBox());
        Snapshot previous = movement.peekLast();
        if (previous != null && discontinuity(previous, sample)) movement.clear();
        while (movement.size() >= MAX_SAMPLES || !movement.isEmpty()
                && sample.time - movement.getFirst().time > HISTORY_NS) movement.removeFirst();
        movement.addLast(sample);
    }

    private void syncWorld(Minecraft client) {
        if (client.player == victim && client.level == level) return;
        clearHit();
        victim = client.player;
        level = client.level;
    }

    private AABB positionAt(long estimatedTime, Snapshot current) {
        Snapshot previous = movement.peekLast();
        if (previous == null || discontinuity(previous, current)) return current.box;
        previous = null;
        for (var sample : movement) {
            if (sample.time >= estimatedTime) {
                return previous == null ? sample.box : interpolate(previous, sample, estimatedTime);
            }
            previous = sample;
        }
        return interpolate(previous, current, estimatedTime);
    }

    private static boolean discontinuity(Snapshot first, Snapshot next) {
        return next.time - first.time > 250_000_000L || first.box.getCenter().distanceToSqr(next.box.getCenter()) > 16D;
    }

    private static AABB interpolate(Snapshot first, Snapshot second, long time) {
        long span = second.time - first.time;
        if (span <= 0) return second.box;
        double fraction = Math.clamp((time - first.time) / (double) span, 0, 1);
        AABB a = first.box, b = second.box;
        return new AABB(Mth.lerp(fraction, a.minX, b.minX), Mth.lerp(fraction, a.minY, b.minY),
                Mth.lerp(fraction, a.minZ, b.minZ), Mth.lerp(fraction, a.maxX, b.maxX),
                Mth.lerp(fraction, a.maxY, b.maxY), Mth.lerp(fraction, a.maxZ, b.maxZ));
    }

    private void clearHit() {
        victim = null;
        level = null;
        lastHit = null;
        movement.clear();
    }

    private void render(GuiGraphicsExtractor context, DeltaTracker ticks) {
        var client = Minecraft.getInstance();
        var config = manager.getConfig();
        if (client.player == null || client.level == null || !config.isHitDistanceEnabled()) {
            clearHit();
            drag.cancel(manager);
            view.dispose();
            return;
        }
        syncWorld(client);
        Hit hit = lastHit;
        boolean compact = config.isHitDistanceCompact();
        String name = hit == null || compact ? null : OrionNameProtect.replace(hit.attacker());
        if (scene == null || compact != shownCompact || !Objects.equals(hit, shownHit) || !Objects.equals(name, shownName)) {
            sceneWidth = compact ? 42 : WIDTH;
            sceneHeight = compact ? 17 : HEIGHT;
            scene = compact ? compactScene(hit) : cardScene(hit, name);
            shownHit = hit;
            shownName = name;
            shownCompact = compact;
        }

        float scale = config.getHudScale() * config.getHudElementScale(ID);
        float fit = OrionHudView.fit(scale, sceneWidth, sceneHeight, context.guiWidth(), context.guiHeight());
        int w = Math.max(1, Math.round(sceneWidth * fit));
        int h = Math.max(1, Math.round(sceneHeight * fit));
        int x = Math.clamp(config.getHitDistanceX() < 0 ? context.guiWidth() - w - 8 : config.getHitDistanceX(),
                0, context.guiWidth() - w);
        int y = Math.clamp(config.getHitDistanceY() < 0 ? 22 : config.getHitDistanceY(),
                0, context.guiHeight() - h);
        var available = drag.available(context, x, y, w, h);
        if (available == null) {
            drag.cancel(manager);
            return;
        }
        var requested = drag.update(context, available.x(), available.y(), w, h, manager, (nx, ny) -> {
            var safe = drag.available(context, nx, ny, w, h);
            if (safe == null) return;
            config.setHitDistanceX(safe.x());
            config.setHitDistanceY(safe.y());
        });
        var position = drag.available(context, requested.x(), requested.y(), w, h);
        if (position == null) position = available;
        drag.trackPosition(position, w, h);
        view.draw(context, scene, position.x(), position.y(), w, h);
    }

    private OrionHudView.Scene compactScene(Hit hit) {
        var compact = new OrionHudView.Scene(sceneWidth, sceneHeight);
        String value = hit == null ? "—" : hit.distance();
        compact.label(value, 1, 1, sceneWidth, sceneHeight, 11F, "#000000b8", "center", true);
        compact.label(value, 0, 0, sceneWidth, sceneHeight, 11F, "#ffffff", "center", true);
        return compact;
    }

    private static OrionHudView.Scene cardScene(Hit hit, String name) {
            var scene = new OrionHudView.Scene(WIDTH, HEIGHT);
            scene.panel(0, 0, WIDTH, HEIGHT, 7);
            scene.icon("M3 10h14M3 7v6m14-6v6M6 8v4m8-4v4", PAD_X, 5, 11, "var(--accent)", false);
            scene.label("HitDistance", PAD_X + 14, 2, 78, 17, 8.5F, "var(--text)", "left", true);
            scene.divider(PAD_X, HEADER_HEIGHT - 2, WIDTH - PAD_X * 2);
            int distanceX = WIDTH - PAD_X - 40;
            scene.label(name == null ? "—" : name, PAD_X, HEADER_HEIGHT,
                    distanceX - PAD_X - 3, ROW_HEIGHT - 2, 8F,
                    hit == null ? "var(--muted)" : "var(--text)", "left", false);
            if (hit != null) {
                scene.label("≈" + hit.distance(), distanceX, HEADER_HEIGHT, 28, ROW_HEIGHT - 2, 8F, "var(--text)", "right", true);
                scene.label("m", WIDTH - PAD_X - 10, HEADER_HEIGHT, 7, ROW_HEIGHT - 2, 6.5F, "var(--muted)", "left", false);
            }
            return scene;
    }
}
