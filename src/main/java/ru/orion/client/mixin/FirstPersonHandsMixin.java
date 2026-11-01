package ru.orion.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsMixin {
    @Inject(method = "submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
    private void orion$viewModel(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float partial,
                                  float pitch, InteractionHand hand, float swing, ItemStack item, float equipped,
                                  PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo callback) {
        var config = OrionClient.getConfigOrNull();
        if (config != null && config.isViewModelEnabled()) {
            pose.translate(config.getViewModelX() * (hand == InteractionHand.MAIN_HAND ? 1 : -1), config.getViewModelY(), config.getViewModelZ());
        }
    }

    @Redirect(method = "submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void orion$scaleItem(ItemStackRenderState itemState, PoseStack pose, SubmitNodeCollector collector, int light,
                                 int overlay, int outline, PlayerRenderState player, FirstPersonHandsAndItemsRenderState state,
                                 float partial, float pitch, InteractionHand hand, float swing, ItemStack item, float equipped,
                                 PoseStack originalPose, SubmitNodeCollector originalCollector, int originalLight) {
        var config = OrionClient.getConfigOrNull();
        float scale = 1F;
        if (config != null && config.isItemScalingEnabled()) scale *= config.getItemScaling();
        if (config != null && config.isSmallShieldEnabled() && item.is(Items.SHIELD)) scale *= config.getSmallShieldScale();
        pose.pushPose();
        pose.scale(scale, scale, scale);
        itemState.submit(pose, collector, light, overlay, outline);
        pose.popPose();
    }
}
