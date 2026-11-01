package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.OrionEspRenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class OrionHitParticles {
    private static final float LIFE = 0.75F;
    private final OrionConfigManager configManager;
    private final List<Particle> particles = new ArrayList<>();
    private Object level;
    public OrionHitParticles(OrionConfigManager configManager) { this.configManager = configManager; }
    public void register() { LevelRenderEvents.COLLECT_SUBMITS.register(this::render); }
    public void onHit(Entity target) {
        OrionConfig config = configManager.getConfig();
        Minecraft client = Minecraft.getInstance();
        if (!config.isHitParticlesEnabled() || client.level == null) return;
        if (level != client.level) { particles.clear(); level = client.level; }
        var random = ThreadLocalRandom.current();
        long now = System.nanoTime();
        Vec3 center = target.position().add(0, target.getBbHeight() * 0.55D, 0);
        for (int i=0; i<config.getHitParticlesCount(); i++) {
            Vec3 velocity = new Vec3(random.nextDouble(-1.5,1.5), random.nextDouble(0.2,2.3), random.nextDouble(-1.5,1.5));
            particles.add(new Particle(center.add(random.nextDouble(-0.2,0.2),random.nextDouble(-0.2,0.2),random.nextDouble(-0.2,0.2)), velocity, now, random.nextFloat(0.65F,1.2F)));
        }
        if (particles.size() > 256) particles.subList(0, particles.size()-256).clear();
    }
    private void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        OrionConfig config = configManager.getConfig();
        if (!config.isHitParticlesEnabled() || client.level == null || client.level != level) { particles.clear(); level = client.level; return; }
        long now = System.nanoTime();
        particles.removeIf(p -> (now-p.created()) / 1_000_000_000F >= LIFE);
        if (particles.isEmpty()) return;
        Vec3 camera = client.gameRenderer.mainCamera().position();
        int rgb = OrionRainbow.orElse(OrionWorldChanger.parseOpaqueColor(config.getHitParticlesColor()));
        var rotation = client.gameRenderer.mainCamera().rotation();
        for (Particle p : particles) {
            float age = Math.max(0, (now-p.created()) / 1_000_000_000F);
            float fade = (1-age/LIFE); fade *= fade;
            Vec3 position = p.position().add(p.velocity().scale(age)).add(0,-1.7*age*age,0).subtract(camera);
            int color = ARGB.color(Math.round(fade*220), ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
            float size = config.getHitParticlesSize()*p.scale();
            PoseStack stack = context.poseStack(); stack.pushPose();
            stack.translate(position.x,position.y,position.z); stack.rotate(rotation);
            int shape = config.getHitParticlesShape();
            context.submitNodeCollector().submitCustomGeometry(stack, OrionEspRenderTypes.colorQuadsDepthTested(),
                    (pose, vc) -> draw(pose, vc, size, color, shape));
            stack.popPose();
        }
    }
    private static void draw(PoseStack.Pose p, VertexConsumer vc, float size, int color, int shape) {
        if (shape == 2) {
            for (int i=0;i<12;i++) {
                double a=i*Math.PI/6, b=(i+1)*Math.PI/6;
                vertex(p,vc,0,0,color); vertex(p,vc,(float)Math.cos(a)*size,(float)Math.sin(a)*size,color);
                vertex(p,vc,(float)Math.cos(b)*size,(float)Math.sin(b)*size,color); vertex(p,vc,0,0,color);
            }
        } else {
            float x = shape==0 ? size*0.28F : size;
            float y = shape==0 ? size*1.5F : size;
            vertex(p,vc,0,-y,color); vertex(p,vc,x,0,color); vertex(p,vc,0,y,color); vertex(p,vc,-x,0,color);
        }
    }
    private static void vertex(PoseStack.Pose p, VertexConsumer vc, float x,float y,int color) { vc.addVertex(p,x,y,0).setColor(color); }
    private record Particle(Vec3 position, Vec3 velocity, long created, float scale) { }
}
