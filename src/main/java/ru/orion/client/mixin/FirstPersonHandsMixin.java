package ru.orion.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.HumanoidArm;
//? if >=26.3 {
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
//?} else {
/*import net.minecraft.client.renderer.ItemInHandRenderer;
*///?}

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
//? if >=26.3 {
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
//?}

//? if >=26.3 {
import net.minecraft.client.renderer.state.level.PlayerRenderState;
//?}

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;
import ru.orion.client.hud.OrionHudEditorScreen;
import net.minecraft.world.phys.AABB;

//? if >=26.3 {
@Mixin(FirstPersonHandsAndItemsRenderer.class)
//?} else {
/*@Mixin(ItemInHandRenderer.class)
*///?}

public abstract class FirstPersonHandsMixin {
    private static final AABB GLEAM_RIGHT_ARM = new AABB(-.54, -.18, -.19, -.04, .69, .19);
    private static final AABB GLEAM_LEFT_ARM = new AABB(.04, -.18, -.19, .54, .69, .19);
    //? if <26.2 {
    /*private static final String LEGACY_ARM_METHOD = "renderArmWithItem";
    *///?} elif <26.3 {
    /*private static final String LEGACY_ARM_METHOD = "submitArmWithItem";
    *///?}

    //? if >=26.3 {
    @Inject(method = "renderPlayerArm", at = @At("RETURN"))
    private void gleam$recordArm(PoseStack pose, SubmitNodeCollector collector, int light, float equipped,
                                 float swing, HumanoidArm arm, PlayerRenderState player, CallbackInfo callback) {
        boolean right = arm == HumanoidArm.RIGHT;
        OrionHudEditorScreen.recordHand(right, pose, right ? GLEAM_RIGHT_ARM : GLEAM_LEFT_ARM);
    }

    @Inject(method = "submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
    private void orion$viewModel(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float partial,
                                  float pitch, InteractionHand hand, float swing, ItemStack item, float equipped,
                                  PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo callback) {
        var config = OrionClient.getConfigOrNull();
        if (config != null && config.isViewModelEnabled()) {
            var local = Minecraft.getInstance().player;
            boolean right = (hand == InteractionHand.MAIN_HAND) == (local == null || local.getMainArm() == HumanoidArm.RIGHT);
            pose.translate(config.getViewModelOffset(right, 0), config.getViewModelOffset(right, 1),
                    config.getViewModelOffset(right, 2));
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
        var local = Minecraft.getInstance().player;
        if (config != null && config.isViewModelEnabled() && local != null && OrionHudEditorScreen.editing(Minecraft.getInstance())) {
            boolean right = (hand == InteractionHand.MAIN_HAND) == (local.getMainArm() == HumanoidArm.RIGHT);
            OrionHudEditorScreen.recordHand(right, pose, itemState.getModelBoundingBox());
        }
        itemState.submit(pose, collector, light, overlay, outline);
        pose.popPose();
    }
    //?} else {
    /*@Inject(method = "renderPlayerArm", at = @At("RETURN"))
    private void gleam$recordArm(PoseStack pose, SubmitNodeCollector collector, int light, float equipped,
                                 float swing, HumanoidArm arm, CallbackInfo callback) {
        boolean right = arm == HumanoidArm.RIGHT;
        OrionHudEditorScreen.recordHand(right, pose, right ? GLEAM_RIGHT_ARM : GLEAM_LEFT_ARM);
    }

    @Inject(method = LEGACY_ARM_METHOD,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
    private void orion$viewModel(net.minecraft.client.player.AbstractClientPlayer player, float partial,
                                  float pitch, InteractionHand hand, float swing, ItemStack item, float equipped,
                                  PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo callback) {
        var config = OrionClient.getConfigOrNull();
        if (config != null && config.isViewModelEnabled()) {
            boolean right = (hand == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.RIGHT);
            pose.translate(config.getViewModelOffset(right, 0), config.getViewModelOffset(right, 1),
                    config.getViewModelOffset(right, 2));
        }
    }

    @Redirect(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void orion$scaleItem(ItemStackRenderState itemState, PoseStack pose, SubmitNodeCollector collector, int light,
                                 int overlay, int outline, net.minecraft.world.entity.LivingEntity player, ItemStack item,
                                 net.minecraft.world.item.ItemDisplayContext displayContext,
                                 PoseStack originalPose, SubmitNodeCollector originalCollector, int originalLight) {
        var config = OrionClient.getConfigOrNull();
        float scale = 1F;
        if (config != null && config.isItemScalingEnabled()) scale *= config.getItemScaling();
        if (config != null && config.isSmallShieldEnabled() && item.is(Items.SHIELD)) scale *= config.getSmallShieldScale();
        pose.pushPose();
        pose.scale(scale, scale, scale);
        if (config != null && config.isViewModelEnabled() && player == Minecraft.getInstance().player
                && (displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                && OrionHudEditorScreen.editing(Minecraft.getInstance())) {
            OrionHudEditorScreen.recordHand(displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, pose, itemState.getModelBoundingBox());
        }
        itemState.submit(pose, collector, light, overlay, outline);
        pose.popPose();
    }
    *///?}
}
