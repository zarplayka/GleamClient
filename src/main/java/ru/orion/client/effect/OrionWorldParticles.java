package ru.orion.client.effect;

import ru.orion.client.GleamMinecraft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class OrionWorldParticles {
    private static final long LIFE_MS = 4_200L;
    private static final long SPAWN_DELAY_MS = 70L;
    private static final float SIZE = 0.11F;
    private static final double MIN_SPAWN_RADIUS = 4.0D;
    private static final double MAX_SPAWN_RADIUS = 14.0D;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final Identifier GLOW_TEXTURE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "textures/targetesp/glow.png");

    private final OrionConfigManager configManager;
    private final List<Particle> particles = new ArrayList<>();
    private long lastSpawnMs;

    public OrionWorldParticles(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
    }

    private void tick(Minecraft client) {
        if (!configManager.getConfig().isWorldParticlesEnabled()) {
            particles.clear();
            return;
        }

        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            particles.clear();
            return;
        }

        long now = System.currentTimeMillis();
        Vec3 current = player.position();

        updateParticles(now);

        int targetCount = configManager.getConfig().getWorldParticlesCount();
        if (particles.size() > targetCount) {
            // Настройку уменьшили — лишние частицы убираем сразу, не дожидаясь их смерти.
            particles.subList(targetCount, particles.size()).clear();
        }
        if (particles.size() >= targetCount || now - lastSpawnMs < SPAWN_DELAY_MS) {
            return;
        }

        // Скорость спавна пропорциональна цели, иначе большие значения не набираются:
        // частицы живут ~4.2 с, и фиксированные 5 штук за тик спавна дают потолок ~300.
        int spawnCount = Math.min(Math.max(1, targetCount / 50), targetCount - particles.size());
        int color = OrionRainbow.orElse(parseColor(configManager.getConfig().getWorldParticlesColor()));
        for (int i = 0; i < spawnCount; i++) {
            particles.add(createParticle(current, now, color));
        }
        lastSpawnMs = now;
    }

    private void updateParticles(long now) {
        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle particle = iterator.next();
            if (now - particle.createdAtMs > LIFE_MS) {
                iterator.remove();
                continue;
            }

            particle.velocity = particle.velocity.add(0.0D, 0.0005D, 0.0D).scale(0.992D);
            particle.position = particle.position.add(particle.velocity);
            particle.rotation += particle.rotationSpeed;
        }
    }

    private static Particle createParticle(Vec3 playerPosition, long now, int color) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble(Math.PI * 2.0D);
        double radius = random.nextDouble(MIN_SPAWN_RADIUS, MAX_SPAWN_RADIUS);
        Vec3 offset = new Vec3(
                Math.cos(angle) * radius,
                random.nextDouble(-1.0D, 4.5D),
                Math.sin(angle) * radius
        );
        Vec3 velocity = new Vec3(
                random.nextDouble(-0.006D, 0.006D),
                random.nextDouble(-0.003D, 0.012D),
                random.nextDouble(-0.006D, 0.006D)
        );
        int particleColor = random.nextBoolean() ? color : ARGB.color(255, 255, 255, 255);
        return new Particle(playerPosition.add(offset), velocity, particleColor, random.nextFloat(-0.08F, 0.08F), now);
    }

    private void render(LevelRenderContext context) {
        if (particles.isEmpty() || !configManager.getConfig().isWorldParticlesEnabled()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Camera camera = GleamMinecraft.camera(client);
        Vec3 cameraPosition = camera.position();
        Quaternionf cameraRotation = new Quaternionf(camera.rotation());
        RenderType renderType = RenderTypes.entityTranslucentEmissive(GLOW_TEXTURE);
        long now = System.currentTimeMillis();

        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), renderType,
                (pose, vertices) -> buildBatch(pose, vertices, cameraPosition, cameraRotation, now));
    }

    private void buildBatch(PoseStack.Pose pose, VertexConsumer vertices, Vec3 cameraPosition,
                            Quaternionf cameraRotation, long now) {
        var offset = new Vector3f();
        var normal = cameraRotation.transform(0F, 0F, 1F, new Vector3f());
        for (Particle particle : particles) {
            float age = (now - particle.createdAtMs) / (float) LIFE_MS;
            float alpha = fade(age);
            if (alpha <= 0.01F || particle.position.distanceToSqr(cameraPosition) > 48 * 48) {
                continue;
            }

            int color = withAlpha(particle.color, Math.round(180.0F * alpha));
            float size = SIZE * (0.7F + alpha * 0.3F);

            float x = (float) (particle.position.x - cameraPosition.x);
            float y = (float) (particle.position.y - cameraPosition.y);
            float z = (float) (particle.position.z - cameraPosition.z);
            float cos = Mth.cos(particle.rotation) * size, sin = Mth.sin(particle.rotation) * size;
            for (int corner = 0; corner < 4; corner++) {
                float u = corner == 1 || corner == 2 ? 1F : 0F;
                float v = corner < 2 ? 1F : 0F;
                float a = u * 2 - 1, b = 1 - v * 2;
                cameraRotation.transform(a * cos - b * sin, a * sin + b * cos, 0F, offset);
                vertices.addVertex(pose, x + offset.x, y + offset.y, z + offset.z)
                        .setUv(u, v).setColor(color).setOverlay(0).setLight(FULL_BRIGHT)
                        .setNormal(pose, normal.x, normal.y, normal.z);
            }
        }
    }

    private static float fade(float age) {
        if (age < 0.12F) {
            return age / 0.12F;
        }
        return Math.max(0.0F, 1.0F - age);
    }

    private static int parseColor(String hex) {
        try {
            return ARGB.opaque(Integer.parseInt(hex.substring(1), 16));
        } catch (RuntimeException ignored) {
            return ARGB.color(255, 255, 124, 0);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return ARGB.color(Mth.clamp(alpha, 0, 255), ARGB.red(color), ARGB.green(color), ARGB.blue(color));
    }

    private static final class Particle {
        private Vec3 position;
        private Vec3 velocity;
        private final int color;
        private final float rotationSpeed;
        private final long createdAtMs;
        private float rotation;

        private Particle(Vec3 position, Vec3 velocity, int color, float rotationSpeed, long createdAtMs) {
            this.position = position;
            this.velocity = velocity;
            this.color = color;
            this.rotationSpeed = rotationSpeed;
            this.createdAtMs = createdAtMs;
        }
    }
}
