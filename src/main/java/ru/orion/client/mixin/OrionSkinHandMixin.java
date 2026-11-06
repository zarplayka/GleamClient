package ru.orion.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.effect.OrionSkinLayers;

@Mixin(AvatarRenderer.class)
public abstract class OrionSkinHandMixin {
    @Inject(method = "renderHand", at = @At("HEAD"))
    private void orion$prepareSleeve(PoseStack pose, SubmitNodeCollector collector, int light, Identifier texture,
                                     ModelPart arm, boolean sleeve, CallbackInfo callback) {
        OrionSkinLayers.prepareHand(((AvatarRenderer<?>) (Object) this).getModel(), texture);
    }
}
