package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import ru.orion.client.config.OrionConfigManager;

/** Десять ванильных значков брони над игроком, по два очка на значок. */
public final class OrionArmorIndicator {
    private static final Identifier EMPTY = Identifier.withDefaultNamespace("textures/gui/sprites/hud/armor_empty.png");
    private static final Identifier FULL = Identifier.withDefaultNamespace("textures/gui/sprites/hud/armor_full.png");
    private static final Identifier HALF = Identifier.withDefaultNamespace("textures/gui/sprites/hud/armor_half.png");
    private static final float SCALE = 0.025F;
    private final OrionConfigManager configManager;

    public OrionArmorIndicator(OrionConfigManager configManager) { this.configManager = configManager; }
    public void register() { LevelRenderEvents.COLLECT_SUBMITS.register(this::render); }

    private void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!configManager.getConfig().isArmorIndicatorEnabled() || client.player == null || client.level == null) return;
        var camera = context.levelState().cameraRenderState;
        if (camera == null || !camera.initialized || camera.orientation == null) return;
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Player player) || player == client.player || !player.isAlive()
                    || player.isInvisibleTo(client.player)) continue;
            var position = player.getPosition(partial);
            double distanceSq = position.distanceToSqr(camera.pos);
            if (distanceSq > 4096D) continue;
            int armor = Mth.clamp(player.getArmorValue(), 0, 20);
            double lift = configManager.getConfig().isHealthTagsEnabled()
                    ? OrionHealthTags.topOffset(player) + 0.28D : player.getBbHeight() + 0.76D;
            PoseStack stack = context.poseStack();
            stack.pushPose();
            stack.translate(position.x - camera.pos.x, position.y - camera.pos.y + lift, position.z - camera.pos.z);
            stack.rotate(camera.orientation);
            stack.scale(-SCALE, SCALE, SCALE);
            float depth = 0.01F * (1F + (float) Math.sqrt(distanceSq));
            for (int i = 0; i < 10; i++) {
                float x = 40.5F - i * 8;
                submit(context, stack, EMPTY, x, -depth);
                if (armor > i * 2) submit(context, stack, armor >= i * 2 + 2 ? FULL : HALF, x, depth);
            }
            stack.popPose();
        }
    }

    private static void submit(LevelRenderContext context, PoseStack stack, Identifier texture, float x, float z) {
        context.submitNodeCollector().submitCustomGeometry(stack, RenderTypes.text(texture), (pose, vc) -> {
            vertex(pose, vc, x, -9, z, 0, 1);
            vertex(pose, vc, x - 9, -9, z, 1, 1);
            vertex(pose, vc, x - 9, 0, z, 1, 0);
            vertex(pose, vc, x, 0, z, 0, 0);
        });
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float u, float v) {
        vc.addVertex(pose.pose(), x, y, z).setUv(u, v).setLight(0xF000F0).setColor(-1);
    }
}
