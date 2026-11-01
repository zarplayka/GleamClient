package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.config.OrionConfigManager;

/** Обычный серверный неймтег локального игрока в камере F5. */
public final class OrionSelfTag {
    private final OrionConfigManager configManager;
    public OrionSelfTag(OrionConfigManager configManager) { this.configManager = configManager; }
    public void register() { LevelRenderEvents.COLLECT_SUBMITS.register(this::render); }

    private void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!configManager.getConfig().isSelfTagEnabled() || client.player == null || client.level == null
                || client.options.getCameraType().isFirstPerson()) return;
        CameraRenderState camera = context.levelState().cameraRenderState;
        if (camera == null || !camera.initialized || camera.pos == null || camera.orientation == null) return;
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 position = client.player.getPosition(partial).subtract(camera.pos);
        Vec3 attachment = client.player.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, client.player.getYRot(partial));
        if (attachment == null) attachment = new Vec3(0, client.player.getBbHeight() + 0.5D, 0);
        Component name = OrionBadge.prepend(client.player.getDisplayName());
        int light = client.getEntityRenderDispatcher().getPackedLightCoords(client.player, partial);
        PoseStack pose = context.poseStack();
        pose.pushPose();
        pose.translate(position.x, position.y, position.z);
        Component score = client.player.belowNameDisplay();
        if (score != null && client.player.getPosition(partial).distanceToSqr(camera.pos) < 100D) {
            context.submitNodeCollector().submitNameTag(pose, attachment, 0, score, !client.player.isDiscrete(), light, camera);
            pose.translate(0F, 9F * 1.15F * 0.025F, 0F);
        }
        context.submitNodeCollector().submitNameTag(pose, attachment, 0, name, !client.player.isDiscrete(), light, camera);
        pose.popPose();
    }
}
