package ru.orion.client.mixin;

import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;

@Mixin(AvatarRenderer.class)
public abstract class OrionNoAnimationsMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("RETURN"))
    private void orion$freezePose(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo callback) {
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isNoAnimationsEnabled() || !(avatar instanceof RemotePlayer)) return;
        state.pose = Pose.STANDING;
        state.ageInTicks = 0F;
        state.walkAnimationPos = state.walkAnimationSpeed = 0F;
        state.xRot = state.yRot = 0F;
        state.swingAnimation = state.swimAmount = state.ticksUsingItem = 0F;
        state.deathTime = state.fallFlyingTimeInTicks = 0F;
        state.capeFlap = state.capeLean = state.capeLean2 = 0F;
        state.elytraRotX = state.elytraRotY = state.elytraRotZ = 0F;
        state.isCrouching = state.isFallFlying = state.isVisuallySwimming = false;
        state.isPassenger = state.isUsingItem = state.isAutoSpinAttack = state.shouldApplyFlyingYRot = false;
        state.leftArmPose = state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
    }
}
