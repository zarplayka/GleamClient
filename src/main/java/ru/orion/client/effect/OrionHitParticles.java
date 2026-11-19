package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.OrionEspRenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.render.GleamPose;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class OrionHitParticles {
    public static final String[] STYLES = {"Направленные искры", "Ударная волна", "Осколки", "Дым", "Разрез"};
    private static final int SPARKS = 0, WAVE = 1, SHARDS = 2, SMOKE = 3, SWEEP = 4;
    private static final int LIMIT = 256;
    private static final Vec3 VERTICAL = new Vec3(0, 1, 0);
    private final OrionConfigManager configManager;
    private final List<Particle> particles = new ArrayList<>();
    private final List<Pulse> pulses = new ArrayList<>();
    private Object level;
    private long lastTick;

    public OrionHitParticles(OrionConfigManager configManager) { this.configManager = configManager; }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
    }

    public void onHit(Entity target) {
        var config = configManager.getConfig();
        Minecraft client = Minecraft.getInstance();
        if (!config.isHitParticlesEnabled() || client.level == null || client.player == null || !target.isAlive()) return;
        if (level != client.level) reset(client.level);
        var random = ThreadLocalRandom.current();
        long time = System.nanoTime();
        Vec3 point = client.hitResult instanceof EntityHitResult hit && hit.getEntity() == target
                ? hit.getLocation() : target.position().add(0, target.getBbHeight() * .6, 0);
        Vec3 normal = client.player.getEyePosition().subtract(point).normalize();
        if (normal.lengthSqr() < .01) normal = new Vec3(0, 0, 1);
        Vec3 origin = point.add(normal.scale(.14));
        int style = config.getHitParticlesShape();
        int count = config.getHitParticlesCount();
        if (style == WAVE || style == SWEEP) {
            Vec3 right = VERTICAL.cross(normal).normalize();
            if (right.lengthSqr() < .01) right = new Vec3(1, 0, 0);
            Vec3 up = normal.cross(right).normalize();
            double roll = style == SWEEP ? random.nextDouble(-.75, .75) : 0;
            pulses.add(new Pulse(style, origin, right, up, time, roll, Math.clamp(count, 12, 36)));
            if (pulses.size() > 24) pulses.removeFirst();
            return;
        }
        if (style == SMOKE) count = Math.clamp(count / 3, 3, 14);
        for (int i = 0; i < count; i++) {
            Vec3 jitter = new Vec3(random.nextDouble(-1, 1), random.nextDouble(-.5, 1), random.nextDouble(-1, 1)).normalize();
            Vec3 velocity;
            float life;
            if (style == SMOKE) {
                velocity = jitter.scale(.6).add(0, .55, 0);
                life = random.nextFloat(.55F, .9F);
            } else if (style == SHARDS) {
                velocity = jitter.scale(random.nextDouble(1.6, 3.3)).add(normal.scale(.6)).add(0, 1.7, 0);
                life = random.nextFloat(.65F, 1F);
            } else {
                velocity = normal.scale(random.nextDouble(2.4, 4.6)).add(jitter.scale(2.6));
                life = random.nextFloat(.24F, .46F);
            }
            particles.add(new Particle(style, origin.add(jitter.scale(.025)), velocity, time, life,
                    random.nextFloat(.65F, 1.25F), random.nextFloat(0F, 6.2832F), random.nextFloat(-9F, 9F)));
        }
        if (particles.size() > LIMIT) particles.subList(0, particles.size() - LIMIT).clear();
    }

    private void reset(Object newLevel) {
        particles.clear();
        pulses.clear();
        level = newLevel;
    }

    private void tick(Minecraft client) {
        long now = System.nanoTime();
        float dt = lastTick == 0 ? .05F : Math.clamp((now - lastTick) / 1_000_000_000F, 0F, .075F);
        lastTick = now;
        if (!configManager.getConfig().isHitParticlesEnabled() || client.level == null || client.player == null || client.level != level) {
            reset(client.level);
            return;
        }
        pulses.removeIf(p -> seconds(now - p.created) >= p.life());
        particles.removeIf(p -> seconds(now - p.created) >= p.life);
        for (Particle p : particles) {
            p.previous = p.position;
            double gravity = p.style == SHARDS ? -7.5 : p.style == SMOKE ? .3 : -.9;
            p.velocity = p.velocity.scale(Math.exp(-(p.style == SMOKE ? 1.9 : .9) * dt)).add(0, gravity * dt, 0);
            Vec3 next = p.position.add(p.velocity.scale(dt));
            if (p.style != SHARDS) {
                p.position = next;
                continue;
            }
            var hit = client.level.clip(new ClipContext(p.position, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
            if (hit.getType() == HitResult.Type.BLOCK) {
                Vec3 floor = new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
                p.position = hit.getLocation().add(floor.scale(.012));
                double incoming = p.velocity.dot(floor);
                if (incoming < 0) p.velocity = p.velocity.subtract(floor.scale(1.42 * incoming)).scale(.73);
            } else {
                p.position = next;
            }
        }
    }

    private void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        var config = configManager.getConfig();
        if (!config.isHitParticlesEnabled() || client.level == null || client.level != level || particles.isEmpty() && pulses.isEmpty()) return;
        var camera = context.levelState().cameraRenderState;
        if (camera == null || !camera.initialized || camera.orientation == null) return;
        Vec3 eye = camera.pos;
        long now = System.nanoTime();
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        int color = OrionWorldChanger.parseOpaqueColor(config.getHitParticlesColor());
        float scale = config.getHitParticlesSize();
        List<Billboard> visible = new ArrayList<>(particles.size());
        Quaternionf inverseView = new Quaternionf(camera.orientation).conjugate();
        PoseStack stack = context.poseStack();
        for (Particle p : particles) {
            float age = seconds(now - p.created);
            float progress = age / p.life;
            if (progress >= 1F || p.position.distanceToSqr(eye) > 4096D) continue;
            float alpha = Math.min(1F, age / (p.style == SMOKE ? .1F : .025F)) * (1F - progress);
            float angle = p.angle + p.spin * age;
            if (p.style == SPARKS) {
                var velocity = inverseView.transform(new Vector3f((float)p.velocity.x, (float)p.velocity.y, (float)p.velocity.z));
                angle = (float) Math.atan2(velocity.y, velocity.x);
            }
            Vec3 relative = p.previous.lerp(p.position, partial).subtract(eye);
            stack.pushPose();
            stack.translate(relative.x, relative.y, relative.z);
            GleamPose.rotate(stack, camera.orientation);
            visible.add(new Billboard(stack.last().copy(), p.style, scale * p.scale, angle, alpha, progress));
            stack.popPose();
        }
        List<Pulse> bursts = List.copyOf(pulses);
        context.submitNodeCollector().submitCustomGeometry(stack, OrionEspRenderTypes.translucentColorQuadsDepthTested(),
                (pose, vertices) -> {
                    for (Billboard particle : visible) drawParticle(vertices, particle, color);
                    for (Pulse pulse : bursts) drawPulse(pose, vertices, pulse, eye, color, scale, now);
                });
    }

    private static void drawParticle(VertexConsumer vertices, Billboard p, int color) {
        float size = p.size;
        int strong = ARGB.color(Math.round(255 * p.alpha), color);
        int clear = ARGB.color(0, color);
        float x = (float)Math.cos(p.angle), y = (float)Math.sin(p.angle);
        if (p.style == SPARKS) {
            float width = size * .21F, length = size * (2.8F - p.progress);
            vertex(p.pose, vertices, -x * length, -y * length, clear);
            vertex(p.pose, vertices, y * width, -x * width, strong);
            vertex(p.pose, vertices, x * size * .42F, y * size * .42F, strong);
            vertex(p.pose, vertices, -y * width, x * width, strong);
        } else if (p.style == SHARDS) {
            float side = size * .55F;
            vertex(p.pose, vertices, x * size, y * size, strong);
            vertex(p.pose, vertices, -x * side - y * side, -y * side + x * side, strong);
            int shadow = ARGB.color(Math.round(195 * p.alpha), ARGB.scaleRGB(color, .7F));
            vertex(p.pose, vertices, -x * side + y * side, -y * side - x * side, shadow);
            vertex(p.pose, vertices, x * size, y * size, strong);
        } else {
            float radius = size * (1.2F + 3.1F * p.progress);
            int mist = ARGB.color(Math.round(78 * p.alpha), color);
            disc(p.pose, vertices, x * size * .4F, y * size * .4F, radius, mist, clear);
            disc(p.pose, vertices, -x * size * .8F, -y * size * .65F, radius * .67F, mist, clear);
        }
    }

    private static void disc(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float radius, int core, int clear) {
        for (int i = 0; i < 9; i++) {
            double a = i * Math.TAU / 9, b = (i + 1) * Math.TAU / 9;
            vertex(pose, consumer, x, y, core);
            vertex(pose, consumer, x + (float)Math.cos(a) * radius, y + (float)Math.sin(a) * radius, clear);
            vertex(pose, consumer, x + (float)Math.cos(b) * radius, y + (float)Math.sin(b) * radius, clear);
            vertex(pose, consumer, x, y, core);
        }
    }

    private static void drawPulse(PoseStack.Pose pose, VertexConsumer vertices, Pulse pulse, Vec3 camera, int color,
                                  float scale, long now) {
        float t = seconds(now - pulse.created) / pulse.life();
        if (t >= 1F || pulse.origin.distanceToSqr(camera) > 4096D) return;
        t = Math.max(0F, t);
        Vec3 offset = pulse.origin.subtract(camera);
        if (pulse.style == WAVE) {
            arc(pose, vertices, pulse, offset, color, scale, t, 0D, Math.TAU, false);
            if (t > .19F) arc(pose, vertices, pulse, offset, color, scale * .78F, (t - .19F) / .81F, 0D, Math.TAU, false);
        } else {
            arc(pose, vertices, pulse, offset, color, scale, t, pulse.roll - 1.4 + t * 1.7, 2.9, true);
        }
    }

    private static void arc(PoseStack.Pose pose, VertexConsumer vertices, Pulse pulse, Vec3 offset, int color, float scale,
                            float t, double start, double span, boolean tapered) {
        double radius = scale * (tapered ? 2.3 + t * 1.8 : .4 + 6.4 * (1 - (1 - t) * (1 - t)));
        int alpha = Math.round((1F - t) * (1F - t) * (tapered ? 255 : 215));
        int clear = ARGB.color(0, color);
        for (int i = 0; i < pulse.segments; i++) {
            double step = i / (double)pulse.segments, next = (i + 1) / (double)pulse.segments;
            double a = start + step * span, b = start + next * span;
            double widthA = scale * (tapered ? .84 * Math.sin(Math.PI * step) : .34) * (1F - t * .55F);
            double widthB = scale * (tapered ? .84 * Math.sin(Math.PI * next) : .34) * (1F - t * .55F);
            int ink = ARGB.color(alpha, color);
            pulseVertex(pose, vertices, pulse, offset, a, radius - widthA, clear);
            pulseVertex(pose, vertices, pulse, offset, b, radius - widthB, clear);
            pulseVertex(pose, vertices, pulse, offset, b, radius, ink);
            pulseVertex(pose, vertices, pulse, offset, a, radius, ink);
            pulseVertex(pose, vertices, pulse, offset, a, radius, ink);
            pulseVertex(pose, vertices, pulse, offset, b, radius, ink);
            pulseVertex(pose, vertices, pulse, offset, b, radius + widthB * .55, clear);
            pulseVertex(pose, vertices, pulse, offset, a, radius + widthA * .55, clear);
        }
    }

    private static void pulseVertex(PoseStack.Pose pose, VertexConsumer consumer, Pulse pulse, Vec3 origin, double angle,
                                    double radius, int color) {
        Vec3 point = origin.add(pulse.right.scale(Math.cos(angle) * radius)).add(pulse.up.scale(Math.sin(angle) * radius));
        consumer.addVertex(pose, (float)point.x, (float)point.y, (float)point.z).setColor(color);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, int color) {
        consumer.addVertex(pose, x, y, 0).setColor(color);
    }

    private static float seconds(long nanos) { return nanos / 1_000_000_000F; }

    private record Billboard(PoseStack.Pose pose, int style, float size, float angle, float alpha, float progress) {}
    private record Pulse(int style, Vec3 origin, Vec3 right, Vec3 up, long created, double roll, int segments) {
        float life() { return style == SWEEP ? .28F : .42F; }
    }

    private static final class Particle {
        Vec3 position, previous, velocity;
        final int style;
        final long created;
        final float life, scale, angle, spin;

        Particle(int style, Vec3 position, Vec3 velocity, long created, float life, float scale, float angle, float spin) {
            this.style = style;
            this.position = this.previous = position;
            this.velocity = velocity;
            this.created = created;
            this.life = life;
            this.scale = scale;
            this.angle = angle;
            this.spin = spin;
        }
    }
}
