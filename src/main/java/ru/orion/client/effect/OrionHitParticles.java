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
import ru.orion.client.config.OrionConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Искры с мягким свечением, затуханием и отскоком от поверхности мира. */
public final class OrionHitParticles {
    private static final int LIMIT = 256;
    private final OrionConfigManager configManager;
    private final List<Particle> particles = new ArrayList<>();
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
        if (level != client.level) { particles.clear(); level = client.level; }
        var random = ThreadLocalRandom.current();
        long now = System.nanoTime();
        Vec3 center = client.hitResult instanceof EntityHitResult hit && hit.getEntity() == target
                ? hit.getLocation() : target.position().add(0, target.getBbHeight() * 0.6D, 0);
        Vec3 outward = client.player.getEyePosition().subtract(center).normalize();
        Vec3 origin = center.add(outward.scale(.13));
        for (int i = 0; i < config.getHitParticlesCount(); i++) {
            Vec3 direction = new Vec3(random.nextDouble(-1, 1), random.nextDouble(-.3, 1), random.nextDouble(-1, 1)).normalize();
            if (direction.dot(outward) < 0) direction = direction.scale(-1);
            Vec3 velocity = direction.scale(random.nextDouble(2.1, 4.4)).add(0, .7, 0);
            particles.add(new Particle(origin, velocity, now,
                    random.nextFloat(0.9F, 1.35F), random.nextFloat(0.55F, 1F), random.nextFloat(0F, 6.2832F), random.nextFloat(-2F, 2F)));
        }
        if (particles.size() > LIMIT) particles.subList(0, particles.size() - LIMIT).clear();
    }

    private void tick(Minecraft client) {
        long now = System.nanoTime();
        float dt = lastTick == 0 ? .05F : Math.min(.1F, (now - lastTick) / 1_000_000_000F);
        lastTick = now;
        if (!configManager.getConfig().isHitParticlesEnabled() || client.level == null || client.player == null || client.level != level) {
            particles.clear(); level = client.level; return;
        }
        particles.removeIf(p -> (now - p.created) / 1_000_000_000F >= p.life);
        for (Particle p : particles) {
            p.previous = p.position;
            p.velocity = p.velocity.scale(Math.exp(-.65 * dt)).add(0, -4.1 * dt, 0);
            Vec3 next = p.position.add(p.velocity.scale(dt));
            var hit = client.level.clip(new ClipContext(p.position, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
            if (hit.getType() == HitResult.Type.BLOCK) {
                Vec3 normal = new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
                p.position = hit.getLocation().add(normal.scale(.015));
                double incoming = p.velocity.dot(normal);
                if (incoming < 0) p.velocity = p.velocity.subtract(normal.scale(1.52 * incoming)).scale(.86);
            } else p.position = next;
        }
    }

    private void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        var config = configManager.getConfig();
        if (!config.isHitParticlesEnabled() || client.level == null || client.level != level || particles.isEmpty()) return;
        var camera = context.levelState().cameraRenderState;
        if (camera == null || !camera.initialized || camera.orientation == null) return;
        long now = System.nanoTime();
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        int rgb = OrionRainbow.orElse(OrionWorldChanger.parseOpaqueColor(config.getHitParticlesColor()));
        int shape = config.getHitParticlesShape();
        List<RenderedParticle> visible = new ArrayList<>(particles.size());
        for (Particle particle : particles) {
            float age = Math.max(0, (now - particle.created) / 1_000_000_000F);
            float t = age / particle.life;
            if (t >= 1 || particle.position.distanceToSqr(camera.pos) > 4096D) continue;
            float fade = Math.min(1F, age / .06F) * (1 - t) * (1 - t);
            float size = config.getHitParticlesSize() * particle.scale * (0.65F + .35F * (1 - t));
            Vec3 position = particle.previous.lerp(particle.position, partial).subtract(camera.pos);
            float angle = particle.angle + particle.spin * age;
            PoseStack stack = context.poseStack();
            stack.pushPose();
            stack.translate(position.x, position.y, position.z);
            stack.rotate(camera.orientation);
            visible.add(new RenderedParticle(stack.last().copy(), size, fade, angle));
            stack.popPose();
        }
        if (!visible.isEmpty()) context.submitNodeCollector().submitCustomGeometry(context.poseStack(),
                OrionEspRenderTypes.colorQuadsDepthTested(), (ignored, vertices) -> {
                    for (var particle : visible) draw(particle.pose(), vertices, particle.size(), rgb,
                            particle.fade(), shape, particle.angle());
                });
    }

    private record RenderedParticle(PoseStack.Pose pose, float size, float fade, float angle) { }
    private static void draw(PoseStack.Pose pose, VertexConsumer vc, float size, int rgb, float fade, int shape, float angle) {
        int glow = alpha(rgb, Math.round(fade * 36)), clear = alpha(rgb, 0);
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5, b = (i + 1) * Math.PI / 5;
            triangle(pose, vc, 0, 0, glow, (float) Math.cos(a) * size * 2, (float) Math.sin(a) * size * 2, clear,
                    (float) Math.cos(b) * size * 2, (float) Math.sin(b) * size * 2, clear);
        }
        int points = shape == 0 ? 8 : shape == 1 ? 4 : 12;
        int core = ARGB.color(Math.round(fade * 235), (ARGB.red(rgb) + 510) / 3, (ARGB.green(rgb) + 510) / 3, (ARGB.blue(rgb) + 510) / 3);
        int edge = alpha(rgb, Math.round(fade * 170));
        for (int i = 0; i < points; i++) {
            double a = angle + i * Math.PI * 2 / points, b = angle + (i + 1) * Math.PI * 2 / points;
            float ra = shape == 0 && (i & 1) != 0 ? size * .23F : size;
            float rb = shape == 0 && (i & 1) == 0 ? size * .23F : size;
            float ax = (float) Math.cos(a) * ra, ay = (float) Math.sin(a) * ra;
            float bx = (float) Math.cos(b) * rb, by = (float) Math.sin(b) * rb;
            triangle(pose, vc, 0, 0, core, ax, ay, edge, bx, by, edge);
            vertex(pose, vc, ax, ay, edge); vertex(pose, vc, bx, by, edge);
            vertex(pose, vc, bx * 1.22F, by * 1.22F, clear); vertex(pose, vc, ax * 1.22F, ay * 1.22F, clear);
        }
    }

    private static int alpha(int rgb, int alpha) { return ARGB.color(alpha, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb)); }
    private static void triangle(PoseStack.Pose pose, VertexConsumer vc, float x, float y, int c, float ax, float ay, int ac, float bx, float by, int bc) {
        vertex(pose, vc, x, y, c); vertex(pose, vc, ax, ay, ac); vertex(pose, vc, bx, by, bc); vertex(pose, vc, x, y, c);
    }
    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, int color) { vc.addVertex(pose, x, y, 0).setColor(color); }

    private static final class Particle {
        Vec3 position, previous, velocity;
        final long created;
        final float life, scale, angle, spin;
        Particle(Vec3 position, Vec3 velocity, long created, float life, float scale, float angle, float spin) {
            this.position = this.previous = position; this.velocity = velocity; this.created = created;
            this.life = life; this.scale = scale; this.angle = angle; this.spin = spin;
        }
    }
}
