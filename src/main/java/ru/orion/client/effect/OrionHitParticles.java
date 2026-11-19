package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.OrionEspRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.render.GleamPose;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class OrionHitParticles {
    public static final String[] STYLES = {"Криты", "Искры", "Сердца", "Осколки", "Лепестки", "Ударная волна"};
    private static final int CRIT = 0, SPARK = 1, HEART = 2, SHARD = 3, PETAL = 4, WAVE = 5;
    private static final int LIMIT = 180;
    private static final Identifier ATLAS = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "textures/hit/particles.png");
    private static final float[][] SHARD_VERTICES = {
            {.11F, .91F, -.12F}, {-.54F, -.35F, -.27F}, {.45F, -.52F, -.3F}, {.08F, -.61F, .48F}
    };
    private static final int[][] SHARD_FACES = {{0, 1, 2}, {0, 3, 1}, {0, 2, 3}, {1, 3, 2}};
    private static final float[] SHARD_SHADES = {.91F, .63F, 1F, .44F};

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
        if (level != client.level) {
            particles.clear();
            level = client.level;
        }
        var random = ThreadLocalRandom.current();
        long now = System.nanoTime();
        Vec3 eye = client.player.getEyePosition();
        Vec3 center = target.position().add(0, target.getBbHeight() * .66D, 0);
        Vec3 hitPoint = client.hitResult instanceof EntityHitResult hit && hit.getEntity() == target
                ? hit.getLocation() : target.getBoundingBox().clip(eye, center).orElse(center);
        Vec3 direction = eye.subtract(hitPoint).normalize();
        if (direction.lengthSqr() < .001) direction = new Vec3(0, 0, 1);
        Vec3 origin = hitPoint.add(direction.scale(.17));
        int style = config.getHitParticlesShape();
        int count = config.getHitParticlesCount();
        count = switch (style) {
            case SPARK -> Math.min(24, count);
            case HEART -> Math.clamp((count + 2) / 3, 1, 12);
            case SHARD -> Math.clamp((count + 1) / 2, 1, 18);
            case PETAL -> Math.clamp((count + 1) / 2, 1, 16);
            case WAVE -> Math.clamp((count + 11) / 12, 1, 3);
            default -> Math.min(32, count);
        };
        for (int i = 0; i < count; i++) {
            Vec3 scatter = new Vec3(random.nextDouble(-1, 1), random.nextDouble(-.75, 1), random.nextDouble(-1, 1)).normalize();
            double along = scatter.dot(direction);
            Vec3 tangent = scatter.subtract(direction.scale(along));
            Vec3 velocity;
            float lifetime;
            switch (style) {
                case SPARK -> {
                    double speed = random.nextDouble(2.7, 5.1);
                    velocity = direction.scale(speed * (.25 + .28 * Math.abs(along))).add(tangent.scale(speed * .8));
                    lifetime = random.nextFloat(.31F, .49F);
                }
                case HEART -> {
                    velocity = tangent.scale(.66).add(direction.scale(.28)).add(0, random.nextDouble(.67, 1.05), 0);
                    lifetime = random.nextFloat(.68F, 1.04F);
                }
                case SHARD -> {
                    velocity = tangent.scale(random.nextDouble(1.0, 2.6)).add(0, 1.2, 0)
                            .add(direction.scale(.73 + Math.abs(along)));
                    lifetime = random.nextFloat(.57F, .91F);
                }
                case PETAL -> {
                    velocity = tangent.scale(random.nextDouble(.58, 1.28)).add(direction.scale(.38))
                            .add(0, random.nextDouble(.45, .92), 0);
                    lifetime = random.nextFloat(.83F, 1.2F);
                }
                case WAVE -> {
                    velocity = Vec3.ZERO;
                    lifetime = .48F;
                }
                default -> {
                    velocity = tangent.scale(random.nextDouble(1.6, 3)).add(direction.scale(.73 + Math.abs(along)))
                            .add(0, .35, 0);
                    lifetime = random.nextFloat(.43F, .66F);
                }
            }
            double spread = style == HEART || style == PETAL ? .17 : .07;
            float angle = random.nextFloat(0F, (float)Math.TAU);
            int tile = switch (style) {
                case HEART -> 2;
                case SPARK -> 1;
                case PETAL -> 4;
                case WAVE -> 5;
                default -> random.nextBoolean() ? 0 : 3;
            };
            Vec3 spawn = style == WAVE ? origin : origin.add(tangent.scale(random.nextDouble(.01, spread)));
            long start = style == WAVE ? now + i * 65_000_000L : now;
            float size = style == WAVE ? 1F - i * .12F : random.nextFloat(.78F, 1.22F);
            particles.add(new Particle(style, tile, spawn, velocity, start, lifetime, size, angle, random.nextFloat(-6F, 6F)));
        }
        if (particles.size() > LIMIT) particles.subList(0, particles.size() - LIMIT).clear();
    }

    private void tick(Minecraft client) {
        long now = System.nanoTime();
        float delta = lastTick == 0L ? .05F : Math.clamp((now - lastTick) / 1_000_000_000F, 0F, .075F);
        lastTick = now;
        if (!configManager.getConfig().isHitParticlesEnabled() || client.level == null || client.player == null || client.level != level) {
            particles.clear();
            level = client.level;
            return;
        }
        particles.removeIf(particle -> age(now, particle) >= particle.lifetime);
        for (Particle p : particles) {
            p.previous = p.position;
            if (p.style == WAVE) continue;
            if (p.style == HEART) {
                double sway = Math.sin(age(now, p) * 9 + p.angle) * .29;
                p.position = p.position.add(p.velocity.add(sway, 0, 0).scale(delta));
                continue;
            }
            if (p.style == PETAL) {
                double flutter = age(now, p) * 10 + p.angle;
                p.velocity = p.velocity.scale(Math.exp(-1.9 * delta)).add(0, -1.9 * delta, 0);
                p.position = p.position.add(p.velocity.add(Math.sin(flutter) * .36, 0, Math.cos(flutter) * .36).scale(delta));
                continue;
            }
            float gravity = p.style == SHARD ? 8F : p.style == SPARK ? 4.1F : 2.6F;
            p.velocity = p.velocity.scale(Math.exp(-(p.style == CRIT ? 1.65 : .8) * delta)).add(0, -gravity * delta, 0);
            Vec3 next = p.position.add(p.velocity.scale(delta));
            if (p.style == SHARD) {
                var hit = client.level.clip(new ClipContext(p.position, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
                if (hit.getType() == HitResult.Type.BLOCK) {
                    Vec3 n = new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
                    p.position = hit.getLocation().add(n.scale(.012));
                    double impact = p.velocity.dot(n);
                    if (impact < 0) p.velocity = p.velocity.subtract(n.scale(1.36 * impact)).scale(.68);
                    continue;
                }
            }
            p.position = next;
        }
    }

    private void render(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        var config = configManager.getConfig();
        if (!config.isHitParticlesEnabled() || mc.level != level || particles.isEmpty()) return;
        var camera = context.levelState().cameraRenderState;
        if (camera == null || !camera.initialized || camera.orientation == null) return;
        int rgb = OrionWorldChanger.parseOpaqueColor(config.getHitParticlesColor());
        float scale = config.getHitParticlesSize();
        long now = System.nanoTime();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Quaternionf inverseView = new Quaternionf(camera.orientation).conjugate();
        List<Sprite> sprites = new ArrayList<>();
        List<Chip> chips = new ArrayList<>();
        PoseStack stack = context.poseStack();
        for (Particle p : particles) {
            float seconds = age(now, p);
            float progress = seconds / p.lifetime;
            if (seconds <= 0F || progress >= 1F || p.position.distanceToSqr(camera.pos) > 4096D) continue;
            Vec3 pos = p.previous.lerp(p.position, partial).subtract(camera.pos);
            float fade = Math.min(1F, seconds / .032F) * Math.min(1F, (1F - progress) / .6F);
            int color = ARGB.color(Math.round(fade * 255), rgb);
            float size = scale * p.scale;
            stack.pushPose();
            stack.translate(pos.x, pos.y, pos.z);
            if (p.style == SHARD) {
                GleamPose.rotate(stack, new Quaternionf().rotateXYZ(p.angle + p.spin * seconds * .7F,
                        p.angle * .44F - p.spin * seconds, .5F + seconds * 5.5F));
                chips.add(new Chip(stack.last().copy(), size * .54F * (1F - progress * .25F), color));
            } else {
                GleamPose.rotate(stack, camera.orientation);
                float angle = 0, width, height;
                if (p.style == SPARK) {
                    var view = inverseView.transform(new Vector3f((float)p.velocity.x, (float)p.velocity.y, (float)p.velocity.z));
                    float projected = Math.clamp((float)Math.hypot(view.x, view.y) / Math.max(.01F, view.length()), .23F, 1F);
                    angle = (float)Math.atan2(view.y, view.x);
                    width = size * (1.24F - .54F * progress) * projected;
                    height = size * .72F;
                } else if (p.style == HEART) {
                    angle = .13F * (float)Math.sin(p.angle + seconds * 7F);
                    width = height = size * (.52F + .12F * (1F - (float)Math.exp(-12F * seconds)));
                } else if (p.style == PETAL) {
                    float flutter = (float)Math.sin(seconds * 12F + p.angle);
                    angle = p.angle + p.spin * seconds * .18F + flutter * .55F;
                    height = size * .67F;
                    width = height * (.4F + .6F * Math.abs(flutter));
                } else if (p.style == WAVE) {
                    float expansion = 1F - (1F - progress) * (1F - progress);
                    angle = p.angle;
                    width = height = size * (1.2F + 5F * expansion);
                } else {
                    angle = p.angle + p.spin * seconds * .42F;
                    width = height = size * (.54F - .17F * progress);
                }
                sprites.add(new Sprite(stack.last().copy(), p.tile, width, height, angle, color));
            }
            stack.popPose();
        }
        if (!sprites.isEmpty()) {
            context.submitNodeCollector().submitCustomGeometry(stack, RenderTypes.text(ATLAS), (pose, vc) -> {
                for (Sprite sprite : sprites) drawSprite(vc, sprite);
            });
        }
        if (!chips.isEmpty()) {
            context.submitNodeCollector().submitCustomGeometry(stack, OrionEspRenderTypes.translucentColorQuadsDepthTested(), (pose, vc) -> {
                for (Chip chip : chips) drawChip(vc, chip);
            });
        }
    }

    private static void drawSprite(VertexConsumer vc, Sprite sprite) {
        float u = (sprite.tile % 3) / 3F, v = (sprite.tile / 3) * .5F;
        float cos = (float)Math.cos(sprite.angle), sin = (float)Math.sin(sprite.angle);
        spriteVertex(vc, sprite, -sprite.width, sprite.height, cos, sin, u, v);
        spriteVertex(vc, sprite, -sprite.width, -sprite.height, cos, sin, u, v + .5F);
        spriteVertex(vc, sprite, sprite.width, -sprite.height, cos, sin, u + 1F / 3F, v + .5F);
        spriteVertex(vc, sprite, sprite.width, sprite.height, cos, sin, u + 1F / 3F, v);
    }

    private static void spriteVertex(VertexConsumer vc, Sprite sprite, float x, float y, float cos, float sin, float u, float v) {
        vc.addVertex(sprite.pose, x * cos - y * sin, x * sin + y * cos, 0)
                .setUv(u, v).setLight(0xF000F0).setColor(sprite.color);
    }

    private static void drawChip(VertexConsumer vc, Chip chip) {
        for (int i = 0; i < SHARD_FACES.length; i++) {
            int[] face = SHARD_FACES[i];
            int color = ARGB.scaleRGB(chip.color, SHARD_SHADES[i]);
            for (int vertex = 0; vertex < 4; vertex++) {
                float[] p = SHARD_VERTICES[face[Math.min(vertex, 2)]];
                vc.addVertex(chip.pose, p[0] * chip.size, p[1] * chip.size, p[2] * chip.size).setColor(color);
            }
        }
    }

    private static float age(long now, Particle particle) {
        return Math.max(0F, (now - particle.created) / 1_000_000_000F);
    }

    private record Sprite(PoseStack.Pose pose, int tile, float width, float height, float angle, int color) {}
    private record Chip(PoseStack.Pose pose, float size, int color) {}

    private static final class Particle {
        Vec3 position, previous, velocity;
        final int style, tile;
        final long created;
        final float lifetime, scale, angle, spin;

        Particle(int style, int tile, Vec3 position, Vec3 velocity, long created, float lifetime, float scale, float angle, float spin) {
            this.style = style;
            this.tile = tile;
            this.position = this.previous = position;
            this.velocity = velocity;
            this.created = created;
            this.lifetime = lifetime;
            this.scale = scale;
            this.angle = angle;
            this.spin = spin;
        }
    }
}
