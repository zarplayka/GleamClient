package ru.orion.client.mixin;

import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.effect.OrionSkinLayers;

@Mixin(ReloadableTexture.class)
public abstract class OrionSkinResourceMixin {
    @Inject(method = "apply", at = @At("HEAD"))
    private void orion$resourceSkin(TextureContents contents, CallbackInfo callback) {
        var self = (ReloadableTexture) (Object) this;
        if (self.resourceId().getPath().contains("entity/player/")) {
            OrionSkinLayers.capture(self.resourceId(), contents.image());
        }
    }
}
