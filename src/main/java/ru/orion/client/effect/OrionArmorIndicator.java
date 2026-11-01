package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.config.OrionConfigManager;

/** Экипировка других игроков над моделью, с настоящими моделями предметов. */
public final class OrionArmorIndicator {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int FULL_BRIGHT = 0xF000F0;
    private final OrionConfigManager configManager;
    public OrionArmorIndicator(OrionConfigManager configManager) { this.configManager = configManager; }
    public void register() { LevelRenderEvents.COLLECT_SUBMITS.register(this::render); }
    private void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!configManager.getConfig().isArmorIndicatorEnabled() || client.player == null || client.level == null) return;
        Vec3 camera = client.gameRenderer.mainCamera().position();
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        var orientation = client.gameRenderer.mainCamera().rotation();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Player player) || player == client.player || !player.isAlive()
                    || player.isInvisibleTo(client.player) || player.position().distanceToSqr(camera) > 4096D) continue;
            double lift = configManager.getConfig().isHealthTagsEnabled() ? 1.15D : 0.65D;
            Vec3 position = player.getPosition(partial).subtract(camera).add(0, player.getBbHeight() + lift, 0);
            PoseStack pose = context.poseStack();
            for (int i = 0; i < ARMOR.length; i++) {
                ItemStack item = player.getItemBySlot(ARMOR[i]);
                if (item.isEmpty()) continue;
                ItemStackRenderState state = new ItemStackRenderState();
                client.getItemModelResolver().updateForLiving(state, item, ItemDisplayContext.GUI, player);
                if (state.isEmpty()) continue;
                pose.pushPose();
                pose.translate(position.x, position.y, position.z);
                pose.rotate(orientation);
                pose.translate((i - 1.5F) * 0.23F, 0F, 0F);
                pose.scale(0.20F, 0.20F, 0.20F);
                state.submit(pose, context.submitNodeCollector(), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
            }
        }
    }
}
