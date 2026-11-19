package ru.orion.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;
import ru.orion.client.cosmetic.GleamHatModel;
import ru.orion.client.effect.OrionFreeLook;
import ru.orion.client.gui.OrionCosmeticPreview;

@Mixin(LivingEntityRenderer.class)
abstract class OrionCosmeticPreviewMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void orion$submitPreview(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                     CameraRenderState camera, CallbackInfo callback) {
        if (state instanceof OrionCosmeticPreview preview) {
            preview.submitItem(pose, collector);
        } else if (state instanceof AvatarRenderState avatar && camera != null && camera.pos != null) {
            var player = Minecraft.getInstance().player;
            if (player != null && avatar.id == player.getId() && OrionFreeLook.isActive(player)
                    && camera.pos.distanceToSqr(avatar.x, avatar.y + avatar.eyeHeight, avatar.z) < .85 * .85) {
                callback.cancel();
            }
        }
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V", shift = At.Shift.AFTER))
    private void gleam$submitEquippedHat(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                        CameraRenderState camera, CallbackInfo callback) {
        if (!(state instanceof AvatarRenderState avatar) || avatar.isInvisible || avatar.isSpectator
                || !(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel() instanceof PlayerModel playerModel)) return;

        String id;
        if (avatar instanceof OrionCosmeticPreview preview) {
            id = preview.selectedHat();
        } else {
            var client = Minecraft.getInstance();
            var player = client.player;
            var config = OrionClient.getConfigOrNull();
            if (player == null || avatar.id != player.getId() || config == null) return;
            id = config.getCosmeticHat();
        }
        if (!id.isEmpty()) GleamHatModel.submit(playerModel, pose, collector, id, avatar.lightCoords);
    }
}
