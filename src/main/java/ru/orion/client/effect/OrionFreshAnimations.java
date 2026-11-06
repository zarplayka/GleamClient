package ru.orion.client.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import ru.orion.client.OrionClient;

/** Дополнительная пластика модели игрока поверх обычного шага и поворота головы. */
public final class OrionFreshAnimations {
    private OrionFreshAnimations() { }

    public static void apply(HumanoidModel<?> model, AvatarRenderState state) {
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isFreshAnimationsEnabled() || state.isSpectator || state.deathTime > 0
                || state.isPassenger || state.isFallFlying || state.isVisuallySwimming || state.swimAmount > 0
                || state.isAutoSpinAttack || (!state.hasPose(Pose.STANDING) && !state.hasPose(Pose.CROUCHING))) return;
        if (config.isNoAnimationsEnabled()) {
            var client = Minecraft.getInstance();
            if (client.player != null && state.id != client.player.getId()) return;
        }
        animate(model, state, config.getFreshAnimationsStyle());
    }

    static void animate(HumanoidModel<?> model, AvatarRenderState state, int style) {
        float movement = Math.clamp(state.walkAnimationSpeed, 0F, 1F);
        float weight = movement * (state.isCrouching ? .40F : 1F);
        float step = state.walkAnimationPos * .6662F;
        float stride = Mth.cos(step);
        float breath = Mth.sin(state.ageInTicks * .11F + (state.id & 15) * .4F) * (1F - movement);
        float detent = Math.round(stride * 2F) * .5F;
        float bounce = switch (style) {
            case 1 -> 1.35F * Math.abs(stride);
            case 2 -> .4F * Math.abs(stride);
            case 3 -> .45F * Math.abs(detent);
            default -> .8F * Math.abs(stride);
        };
        float rise = -(bounce * weight + .16F * breath);
        model.head.y += rise;
        model.body.y += rise;
        model.leftArm.y += rise;
        model.rightArm.y += rise;
        model.leftLeg.y += rise;
        model.rightLeg.y += rise;
        model.head.zRot += stride * .085F * weight + breath * .022F;
        model.rightLeg.xRot += stride * .13F * weight;
        model.leftLeg.xRot -= stride * .13F * weight;

        // Анимации оружия и использования предметов имеют приоритет над дополнительным взмахом.
        if (state.isUsingItem || state.swingAnimation > 0F
                || state.rightArmPose.isTwoHanded() || state.leftArmPose.isTwoHanded()) return;
        switch (style) {
            case 1 -> {
                model.body.xRot += .19F * weight;
                model.body.zRot += .13F * stride * weight;
                float flap = Mth.sin(step);
                arm(model.rightArm, state.rightArmPose, -.54F - .63F * stride, .12F * flap, .60F + .43F * flap, weight);
                arm(model.leftArm, state.leftArmPose, -.54F + .63F * stride, .12F * flap, -.60F + .43F * flap, weight);
            }
            case 2 -> {
                float sway = Mth.sin(step);
                model.body.zRot += .22F * sway * weight;
                model.head.zRot -= .19F * sway * weight;
                float shortStep = 1F - .37F * weight;
                model.rightLeg.xRot *= shortStep;
                model.leftLeg.xRot *= shortStep;
                model.rightLeg.yRot += .18F * weight;
                model.leftLeg.yRot -= .18F * weight;
                float flipper = .46F + .19F * Mth.cos(2F * step);
                arm(model.rightArm, state.rightArmPose, .2F - model.rightArm.xRot, 0F, flipper, weight);
                arm(model.leftArm, state.leftArmPose, .2F - model.leftArm.xRot, 0F, -flipper, weight);
            }
            case 3 -> {
                model.body.yRot += .12F * detent * weight;
                model.rightLeg.xRot += (detent * .81F - model.rightLeg.xRot) * weight;
                model.leftLeg.xRot += (-detent * .81F - model.leftLeg.xRot) * weight;
                arm(model.rightArm, state.rightArmPose, -.60F - .90F * detent - model.rightArm.xRot, 0F, .14F, weight);
                arm(model.leftArm, state.leftArmPose, -.60F + .90F * detent - model.leftArm.xRot, 0F, -.14F, weight);
            }
            default -> {
                model.body.xRot += .12F * weight + breath * .018F;
                model.body.zRot -= stride * .055F * weight;
                swing(model.rightArm, state.rightArmPose, step + Mth.PI, breath, weight, 1F);
                swing(model.leftArm, state.leftArmPose, step, -breath, weight, -1F);
            }
        }
    }

    private static void arm(ModelPart arm, HumanoidModel.ArmPose pose, float pitch, float yaw, float roll, float weight) {
        if (pose != HumanoidModel.ArmPose.EMPTY && pose != HumanoidModel.ArmPose.ITEM) return;
        float strength = pose == HumanoidModel.ArmPose.ITEM ? weight * .5F : weight;
        arm.xRot += pitch * strength;
        arm.yRot += yaw * strength;
        arm.zRot += roll * strength;
    }

    private static void swing(ModelPart arm, HumanoidModel.ArmPose pose, float phase,
                              float breath, float weight, float side) {
        if (pose != HumanoidModel.ArmPose.EMPTY && pose != HumanoidModel.ArmPose.ITEM) return;
        float swing = Mth.cos(phase);
        float flourish = Mth.sin(phase + .55F);
        float strength = pose == HumanoidModel.ArmPose.ITEM ? .50F : 1F;
        arm.xRot += (swing * .64F + Mth.sin(phase * 2F) * .10F) * weight * strength + breath * .055F;
        arm.zRot += side * ((.17F + .28F * Math.max(0F, flourish)) * weight * strength + breath * .05F);
        arm.yRot += side * flourish * .16F * weight * strength;
    }
}
