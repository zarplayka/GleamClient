package ru.orion.client.media;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.OrionEspRenderTypes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import ru.orion.client.config.GleamLyricsSettings;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.effect.OrionWorldChanger;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Фразы состоят из объёмных глифов, которые Minecraft рисует в координатах мира. */
public final class GleamWorldLyrics {
    private static final float[] SKY_X = {-.21F, .19F, -.08F, .10F, 0F};
    private static final float[] SKY_Y = {.35F, .29F, .42F, .37F, .24F};
    private static final float[] BACKDROP = {-1F, -.46F, .46F, 1F};
    private final OrionConfigManager manager;
    private final OrionMediaController controller;
    private final GleamLyricsMesh mesh = new GleamLyricsMesh();
    private final ThreadPoolExecutor preparation = new ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(4), Thread.ofPlatform().daemon().name("gleam-lyric-mesh").factory());
    private ClientLevel level;
    private GleamLyricsService.Snapshot document;
    private GleamLyricsTimeline timeline;
    private String family;
    private Work current, warm;
    private Future<?> cleanup;
    private Anchor anchor, followTarget;
    private long lastFrame, lastPositionAt, lastOnset, nextAnchorAttempt;
    private float pulse;
    private double lastPosition = -1;

    private record Work(int index, Future<GleamLyricsMesh.Phrase> future) { }
    private record Anchor(Vec3 position, Quaternionf rotation, float distance, float halfWidth, float halfHeight,
                          float fractionX, float fractionY) { }
    private record Animation(double millis, long end, float intensity, float alpha, float bass, float glow,
                             int text, int accent, int variant, boolean wordHighlight, boolean shadow, boolean edit) { }

    public GleamWorldLyrics(OrionConfigManager manager, OrionMediaController controller) {
        this.manager = manager;
        this.controller = controller;
        preparation.allowCoreThreadTimeOut(true);
    }

    public void register() {
        OrionEspRenderTypes.worldLyrics();
        LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (document != null && (client.level != level || !enabled())) reset();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> preparation.shutdownNow());
    }

    private boolean enabled() {
        var config = manager.getConfig();
        return config.isMediaPlayerEnabled() && config.getAnimatedLyrics().isEnabled();
    }

    private void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!enabled() || client.level == null || client.player == null) { reset(); return; }
        var snapshot = controller.lyricsSnapshot();
        if (!snapshot.lyrics().status().equals("ready") || !controller.matchesLyrics(snapshot)) { reset(); return; }
        var settings = manager.getConfig().getAnimatedLyrics();
        if (snapshot != document || client.level != level || !settings.getFont().equals(family)) {
            reset();
            level = client.level;
            document = snapshot;
            family = settings.getFont();
            timeline = new GleamLyricsTimeline(snapshot.lyrics().lines());
        }
        long now = System.nanoTime();
        double position = controller.lyricsMillis(snapshot, now);
        if (position < 0) {
            anchor = followTarget = null;
            lastPosition = -1;
            pulse = 0;
            return;
        }
        boolean playing = controller.isPlaying();
        position = GleamLyricsTimeline.smoothPosition(lastPosition, position, (now - lastPositionAt) / 1e6, playing);
        double millis = position - settings.getOffset();
        boolean jumped = lastPosition >= 0 && (position < lastPosition - 200 || position - lastPosition > 1100);
        lastPosition = position;
        lastPositionAt = now;
        int index = timeline.find(millis);
        if (index < 0) {
            anchor = followTarget = null;
            prefetch(timeline.nextAfter(millis));
            return;
        }
        if (current == null || current.index() != index) {
            cancel(current);
            current = warm != null && warm.index() == index ? warm : prepare(index);
            if (current == warm) warm = null;
            anchor = followTarget = null;
            nextAnchorAttempt = 0;
        }
        prefetch(timeline.next(index));
        if (client.gui.screen() != null || client.gui.overlay() != null || !current.future().isDone()) return;
        GleamLyricsMesh.Phrase phrase;
        try { phrase = current.future().get(); }
        catch (Exception invalid) { return; }
        if (phrase.pieces().isEmpty()) return;
        Camera camera = client.gameRenderer.mainCamera();
        boolean sky = !settings.getPlacement().equals("Floating");
        boolean minimal = settings.getStyle().equals("Minimal");
        int variant = settings.isRandomizedTransitions() && !minimal
                ? Math.floorMod(document.track().hashCode() + index * 3, SKY_X.length) : 4;
        if (jumped) { anchor = followTarget = null; nextAnchorAttempt = 0; pulse = 0; }
        float delta = Math.clamp((now - lastFrame) / 1e9F, 0, .1F);
        lastFrame = now;
        if (anchor == null || settings.isFollowView() && now >= nextAnchorAttempt) {
            if (now < nextAnchorAttempt) return;
            nextAnchorAttempt = now + (settings.isFollowView() ? 45_000_000L : 160_000_000L);
            Anchor target = place(client, camera, sky, variant);
            followTarget = target;
            if (anchor == null) anchor = target;
            else if (target == null && settings.isFollowView()) anchor = null;
        }
        if (anchor == null) return;
        if (settings.isFollowView() && followTarget != null && followTarget != anchor) {
            float amount = 1F - (float) Math.exp(-delta * 19F);
            anchor = new Anchor(anchor.position().lerp(followTarget.position(), amount),
                    new Quaternionf(anchor.rotation()).slerp(followTarget.rotation(), amount),
                    mix(anchor.distance(), followTarget.distance(), amount), mix(anchor.halfWidth(), followTarget.halfWidth(), amount),
                    mix(anchor.halfHeight(), followTarget.halfHeight(), amount), followTarget.fractionX(), followTarget.fractionY());
        }
        var beat = controller.beatSample(now);
        if (playing && beat.at() != 0 && beat.onset() > lastOnset) pulse = 1;
        lastOnset = beat.onset();
        pulse = playing ? pulse * (float) Math.exp(-delta * 8.5F) : 0;
        float sensitivity = settings.getBeatSensitivity() / 100F;
        float bass = playing ? beat.bass() * sensitivity : 0;
        float beatScale = playing ? (pulse * .8F + beat.level() * .14F + beat.bass() * .2F) * sensitivity : 0;
        draw(context, camera, phrase, timeline.line(index), timeline.end(index), millis, variant,
                settings, sky, minimal, bass, beatScale);
    }

    private Work prepare(int index) {
        preparation.purge();
        var line = timeline.line(index);
        var font = family;
        return new Work(index, preparation.submit(() -> mesh.prepare(line, font)));
    }

    private void prefetch(int index) {
        if (warm != null && warm.index() == index) return;
        cancel(warm);
        warm = index < 0 || current != null && current.index() == index ? null : prepare(index);
    }

    private static void cancel(Work work) { if (work != null) work.future().cancel(false); }

    private void reset() {
        if (document == null) return;
        cancel(current); cancel(warm);
        if (cleanup != null) cleanup.cancel(false);
        preparation.purge();
        if (!preparation.isShutdown()) cleanup = preparation.submit(mesh::clear);
        document = null;
        level = null;
        timeline = null;
        current = warm = null;
        anchor = followTarget = null;
        nextAnchorAttempt = 0;
        lastPosition = -1;
        lastPositionAt = 0;
        lastOnset = 0;
        pulse = 0;
    }

    private static Anchor place(Minecraft client, Camera camera, boolean sky, int variant) {
        Vec3 eye = camera.position();
        float distance = sky ? 20F : 8F;
        float tangent = (float) Math.tan(Math.toRadians(Math.clamp(camera.getFov(), 12F, 140F) / 2));
        float aspect = Math.clamp(client.getWindow().getWidth() / (float) Math.max(1, client.getWindow().getHeight()), .45F, 3.5F);
        float fractionX = SKY_X[variant] * (sky ? 1 : .60F);
        float fractionY = sky ? SKY_Y[variant] : .12F;
        Vec3 offset = new Vec3(camera.forwardVector()).scale(distance)
                .add(new Vec3(camera.leftVector()).scale(-distance * tangent * aspect * fractionX))
                .add(new Vec3(camera.upVector()).scale(distance * tangent * fractionY));
        Vec3 target = eye.add(offset);
        var hit = client.level.clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, client.player));
        if (hit.getType() != HitResult.Type.MISS) {
            float fraction = (float) ((eye.distanceTo(hit.getLocation()) - .28) / offset.length());
            if (fraction * distance < .75F) return null;
            distance *= fraction;
            target = eye.add(offset.scale(fraction));
        }
        return new Anchor(target, new Quaternionf(camera.rotation()), distance, distance * tangent * aspect,
                distance * tangent, fractionX, fractionY);
    }

    private void draw(LevelRenderContext context, Camera camera, GleamLyricsMesh.Phrase phrase, GleamLyrics.Line line,
                      long end, double millis, int variant, GleamLyricsSettings settings, boolean sky,
                      boolean minimal, float bass, float beat) {
        boolean edit = settings.getStyle().equals("Edit");
        boolean dynamic = settings.getStyle().equals("Dynamic");
        float strength = settings.getIntensity() / 100F;
        float motion = strength * (minimal ? .12F : sky ? 1F : .46F);
        float sign = variant % 2 == 0 ? 1F : -1F;
        double duration = Math.max(1, end - line.start());
        float progress = unit((millis - line.start()) / duration);
        float in = ease(unit((millis - line.start()) / Math.min(duration * .45, dynamic ? 260 : 390)));
        long lastWord = line.words().isEmpty() ? line.start() : line.words().getLast().start();
        double exitStart = Math.min(end - 65, Math.max(end - (sky ? 790 : 460), lastWord + 105));
        float out = unit((millis - exitStart) / (end - exitStart));
        float sweep = out * out * (3 - 2 * out);
        float alpha = strength == 0 ? 1F : in * (1F - out * out * out);
        if (alpha < .012F) return;

        float size = anchor.distance() * (sky ? .15F : .072F) * settings.getTextScale();
        float fitX = 2 * anchor.halfWidth() * (1 - Math.abs(anchor.fractionX())) * .84F / phrase.width();
        float fitY = 2 * anchor.halfHeight() * (1 - Math.abs(anchor.fractionY())) * .54F / phrase.height();
        size = Math.min(size, Math.min(fitX, fitY));
        float zoom = 1 + motion * ((in - 1) * .31F + progress * .17F + sweep * (sky ? .52F : .21F))
                + strength * beat * (dynamic ? .15F : .09F);
        float yaw = sign * motion * ((1 - in) * -32F + 14F * (progress - .32F) + sweep * (variant == 0 ? 22F : 96F));
        float roll = motion * sign * ((1 - in) * -10F + 7F * (float) Math.sin(progress * Math.PI) + sweep * 13F);
        int color = OrionWorldChanger.parseOpaqueColor(settings.getColor());
        int accent = OrionWorldChanger.parseOpaqueColor(manager.getConfig().getClickGuiAccent());
        var animation = new Animation(millis, end, strength, alpha, bass, settings.isGlow() ? .11F : 0,
                color, accent, variant, settings.isWordHighlight(), settings.isShadow(), edit);

        PoseStack stack = context.poseStack();
        Vec3 relative = anchor.position().subtract(camera.position());
        stack.pushPose();
        try {
            stack.translate(relative);
            stack.rotate(anchor.rotation());
            stack.translate(sign * anchor.halfWidth() * motion * ((1 - in) * -.24F + sweep * .16F),
                    anchor.halfHeight() * motion * ((1 - in) * -.10F + sweep * .44F),
                    anchor.distance() * motion * (progress * .10F + sweep * .22F));
            stack.rotateDegrees(Axis.ZP, roll);
            stack.rotateDegrees(Axis.YP, yaw);
            stack.rotateDegrees(Axis.XP, -motion * sweep * 9F);
            stack.scale(size * zoom, size * zoom, size * zoom);
            float dim = settings.getBackgroundDim() / 100F * alpha;
            if (alpha >= .998F) context.submitNodeCollector().submitCustomGeometry(stack, OrionEspRenderTypes.worldLyricsSolid(),
                    (pose, vertices) -> emit(pose, vertices, phrase, line, animation, 0, true));
            context.submitNodeCollector().submitCustomGeometry(stack, OrionEspRenderTypes.worldLyrics(),
                    (pose, vertices) -> emit(pose, vertices, phrase, line, animation, dim, false));
        } finally { stack.popPose(); }
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer vertices, GleamLyricsMesh.Phrase phrase,
                             GleamLyrics.Line line, Animation animation, float dim, boolean solid) {
        if (dim > .01F) backdrop(pose, vertices, phrase.width(), phrase.height(), dim);
        var words = line.words();
        for (var piece : phrase.pieces()) {
            var word = words.isEmpty() ? null : words.get(piece.word());
            double start = word == null ? line.start() : word.start();
            double age = animation.millis() - start;
            if (age < 0) continue;
            boolean active = word != null && animation.millis() < GleamLyricsTimeline.wordEnd(line, piece.word(), animation.end());
            boolean letters = animation.variant() == 3;
            double delay = letters ? piece.letter() * (word == null ? 19 : 9) : 0;
            float reveal = animation.intensity() == 0 ? 1 : ease(unit((age - delay) / (letters ? 185 : 155)));
            float alpha = animation.alpha() * reveal;
            if (alpha < .012F) continue;
            float size = 1 + animation.intensity() * ((reveal - 1) * .30F + (active && animation.edit() ? .18F : 0));
            float drift = animation.bass() * animation.intensity() * (active ? .033F : .01F);
            float shake = drift * (float) Math.sin(animation.millis() * .062 + piece.letter() * 1.7);
            float x = piece.wordCenter() + (piece.x() - piece.wordCenter()) * size + shake;
            float y = piece.y() - (1 - reveal) * animation.intensity() * .20F;
            float depth = .075F;
            int rgb = active && animation.wordHighlight() ? blend(animation.text(), animation.accent(), .22F) : animation.text();
            float[] faces = piece.glyph().faces();
            if (!solid) {
                if (animation.shadow()) face(pose, vertices, faces, x + .036F, y - .047F, -depth - .023F, size, argb(0x141122, alpha * .48F), false);
                if (animation.glow() > 0) face(pose, vertices, faces, x - .022F, y - .021F, -depth - .008F,
                        size * 1.10F, argb(animation.accent(), alpha * animation.glow()), false);
            }
            if (solid != (alpha >= .998F)) continue;
            face(pose, vertices, faces, x, y, -depth, size, argb(shade(rgb, .48F), alpha), true);
            float[] edges = piece.glyph().contour();
            for (int i = 0; i < edges.length; i += 4) {
                float ax = edges[i] * size + x, ay = edges[i + 1] * size + y;
                float bx = edges[i + 2] * size + x, by = edges[i + 3] * size + y;
                float nx = edges[i + 2] - edges[i], ny = edges[i + 3] - edges[i + 1];
                float direction = (nx * .62F + ny * .38F) / Math.max(.001F, Math.abs(nx) + Math.abs(ny));
                int side = argb(shade(rgb, .54F + direction * .19F), alpha);
                vertex(pose, vertices, ax, ay, 0, side); vertex(pose, vertices, bx, by, 0, side);
                vertex(pose, vertices, bx, by, -depth, side); vertex(pose, vertices, ax, ay, -depth, side);
            }
            face(pose, vertices, faces, x, y, 0, size, argb(rgb, alpha), false);
        }
    }

    private static void face(PoseStack.Pose pose, VertexConsumer vertices, float[] face,
                             float x, float y, float z, float scale, int color, boolean reverse) {
        for (int i = 0; i < face.length; i += 8) for (int j = 0; j < 4; j++) {
            int at = i + (reverse ? 3 - j : j) * 2;
            vertex(pose, vertices, x + face[at] * scale, y + face[at + 1] * scale, z, color);
        }
    }

    private static void backdrop(PoseStack.Pose pose, VertexConsumer vertices, float width, float height, float dim) {
        float halfWidth = width * .63F + .25F, halfHeight = height * .75F + .22F;
        int ink = argb(0x0D0816, dim);
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) {
            backdropPoint(pose, vertices, x, y, halfWidth, halfHeight, ink);
            backdropPoint(pose, vertices, x + 1, y, halfWidth, halfHeight, ink);
            backdropPoint(pose, vertices, x + 1, y + 1, halfWidth, halfHeight, ink);
            backdropPoint(pose, vertices, x, y + 1, halfWidth, halfHeight, ink);
        }
    }

    private static void backdropPoint(PoseStack.Pose pose, VertexConsumer vertices, int x, int y, float w, float h, int ink) {
        int color = x == 0 || x == 3 || y == 0 || y == 3 ? ink & 0xFFFFFF : ink;
        vertex(pose, vertices, BACKDROP[x] * w, BACKDROP[y] * h, -.20F, color);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices, float x, float y, float z, int color) {
        vertices.addVertex(pose, x, y, z).setColor(color);
    }

    private static int argb(int rgb, float alpha) { return (Math.round(Math.clamp(alpha, 0, 1) * 255) << 24) | rgb & 0xFFFFFF; }
    private static int shade(int rgb, float f) {
        return (Math.round((rgb >> 16 & 255) * f) << 16) | (Math.round((rgb >> 8 & 255) * f) << 8) | Math.round((rgb & 255) * f);
    }
    private static int blend(int a, int b, float t) {
        int r = Math.round((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
        int g = Math.round((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
        int blue = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | blue;
    }
    private static float unit(double n) { return (float) Math.clamp(n, 0, 1); }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
    private static float ease(float n) { float inverse = 1 - n; return 1 - inverse * inverse * inverse; }
}
