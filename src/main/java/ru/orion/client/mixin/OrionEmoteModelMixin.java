package ru.orion.client.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.effect.OrionEmotes;
import ru.orion.client.effect.OrionFreshAnimations;

@Mixin(HumanoidModel.class)
public abstract class OrionEmoteModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void orion$dance(HumanoidRenderState state, CallbackInfo callback) {
        if (state instanceof AvatarRenderState player) {
            HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
            OrionFreshAnimations.apply(model, player);
            OrionEmotes.apply(model, player);
        }
    }
}
