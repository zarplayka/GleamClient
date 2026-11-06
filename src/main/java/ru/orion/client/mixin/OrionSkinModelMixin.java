package ru.orion.client.mixin;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.effect.OrionSkinLayers;

@Mixin(PlayerModel.class)
public abstract class OrionSkinModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void orion$prepareLayers(AvatarRenderState state, CallbackInfo callback) {
        OrionSkinLayers.prepare((PlayerModel) (Object) this, state);
    }
}
