package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.OrionEspRenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.config.OrionConfigManager;

/** Нимб локального игрока и тех пользователей Orion, которые включили его у себя. */
public final class OrionHalo {
    private static final int SEGMENTS = 64;
    private static final float[] COS = new float[SEGMENTS + 1];
    private static final float[] SIN = new float[SEGMENTS + 1];
    private static final float HEIGHT_ABOVE_HEAD = 0.21F;
    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = 2D * Math.PI * i / SEGMENTS;
            COS[i] = (float) Math.cos(angle);
            SIN[i] = (float) Math.sin(angle);
        }
    }

    private final OrionConfigManager manager;
    public OrionHalo(OrionConfigManager manager) { this.manager = manager; }
    public void register() { LevelRenderEvents.COLLECT_SUBMITS.register(this::render); }

    private void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        var camera = context.levelState().cameraRenderState;
        if (camera == null || !camera.initialized || camera.pos == null) return;
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Player player) || !player.isAlive() || player.isInvisibleTo(client.player)) continue;
            boolean local = player == client.player;
            if (local ? !manager.getConfig().isHaloEnabled() || client.options.getCameraType().isFirstPerson()
                    : !OrionBadgePresence.hasHalo(player.getUUID())) continue;
            Vec3 position = player.getPosition(partial).subtract(camera.pos).add(0D, player.getBbHeight() + HEIGHT_ABOVE_HEAD, 0D);
            PoseStack pose = context.poseStack();
            pose.pushPose();
            pose.translate(position.x, position.y, position.z);
            context.submitNodeCollector().submitCustomGeometry(pose, OrionEspRenderTypes.colorQuadsDepthTested(), OrionHalo::build);
            pose.popPose();
        }
    }

    private static void build(PoseStack.Pose pose, VertexConsumer vertices) {
        ring(pose, vertices, .31F, .35F, ARGB.color(35, 255, 180, 35), ARGB.color(145, 255, 198, 57));
        ring(pose, vertices, .35F, .41F, ARGB.color(230, 255, 224, 115), ARGB.color(220, 255, 183, 35));
        ring(pose, vertices, .41F, .46F, ARGB.color(145, 255, 175, 35), ARGB.color(20, 255, 145, 20));
    }

    public static void submitPreview(PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector, float height) {
        pose.translate(0F, height + HEIGHT_ABOVE_HEAD, 0F);
        collector.submitCustomGeometry(pose, OrionEspRenderTypes.colorQuadsDepthTested(), OrionHalo::build);
    }

    private static void ring(PoseStack.Pose pose, VertexConsumer vc, float inner, float outer, int innerColor, int outerColor) {
        for (int i = 0; i < SEGMENTS; i++) {
            vc.addVertex(pose, inner * COS[i], 0, inner * SIN[i]).setColor(innerColor);
            vc.addVertex(pose, outer * COS[i], 0, outer * SIN[i]).setColor(outerColor);
            vc.addVertex(pose, outer * COS[i + 1], 0, outer * SIN[i + 1]).setColor(outerColor);
            vc.addVertex(pose, inner * COS[i + 1], 0, inner * SIN[i + 1]).setColor(innerColor);
        }
    }
}
