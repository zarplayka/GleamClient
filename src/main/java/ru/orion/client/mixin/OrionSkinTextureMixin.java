package ru.orion.client.mixin;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.effect.OrionSkinLayers;

@Mixin(TextureManager.class)
public abstract class OrionSkinTextureMixin {
    @Inject(method = "register(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/renderer/texture/AbstractTexture;)V", at = @At("TAIL"))
    private void orion$captureSkin(Identifier id, AbstractTexture texture, CallbackInfo callback) {
        if (texture instanceof DynamicTexture dynamic) OrionSkinLayers.capture(id, dynamic.getPixels());
    }
    @Inject(method = "release", at = @At("HEAD"))
    private void orion$releaseSkin(Identifier id, CallbackInfo callback) { OrionSkinLayers.release(id); }
}
