package ru.orion.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
//? if >=26.3 {
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
//?} else {
/*import com.mojang.blaze3d.buffers.GpuBufferSlice;
*///?}
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.effect.OrionCleanWater;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(TextureAtlas.class)
public abstract class OrionCleanWaterAtlasMixin {
    @Shadow @Final private Identifier location;
    @Shadow private int maxMipLevel;
    @Shadow private Map<Identifier, TextureAtlasSprite> texturesByName;
    @Shadow private List<SpriteContents.AnimationState> animatedTexturesStates;

    @Unique private int orion$waterOpacity = 100;
    @Unique private int orion$waterUboStride;
    @Unique private final Map<TextureAtlasSprite, byte[][]> orion$waterAlpha = new HashMap<>();
    @Unique private final Map<TextureAtlasSprite, GpuBufferSlice> orion$waterUbos = new HashMap<>();
    @Unique private final Map<TextureAtlasSprite, SpriteContents.AnimationState> orion$waterAnimations = new HashMap<>();

    @Inject(method = "clearTextureData", at = @At("HEAD"))
    private void orion$discardWaterCaches(CallbackInfo ci) {
        orion$waterOpacity = 100;
        orion$waterAlpha.clear();
        orion$waterUbos.clear();
        orion$waterAnimations.clear();
    }

    @WrapOperation(method = "upload", at = @At(value = "INVOKE",
            //? if >=26.3 {
            target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;createAnimationState(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;I)Lnet/minecraft/client/renderer/texture/SpriteContents$AnimationState;"))
            //?} else {
            /*target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;createAnimationState(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;I)Lnet/minecraft/client/renderer/texture/SpriteContents$AnimationState;"))
            *///?}
    private SpriteContents.AnimationState orion$rememberWaterAnimation(TextureAtlasSprite sprite, GpuBufferSlice ubo,
                                                                        int uboStride, Operation<SpriteContents.AnimationState> original) {
        var state = original.call(sprite, ubo, uboStride);
        if (state != null && location.equals(TextureAtlas.LOCATION_BLOCKS)
                && OrionCleanWater.SPRITES.contains(sprite.contents().name())) {
            orion$waterUboStride = uboStride;
            orion$waterUbos.put(sprite, ubo);
            orion$waterAnimations.put(sprite, state);
        }
        return state;
    }

    @Inject(method = "uploadAnimationFrames", at = @At("HEAD"))
    private void orion$updateWaterTextures(CallbackInfo ci) {
        if (!location.equals(TextureAtlas.LOCATION_BLOCKS)) return;
        int opacity = OrionCleanWater.opacity();
        if (opacity == orion$waterOpacity) return;
        var atlas = (TextureAtlas) (Object) this;
        var texture = atlas.getTexture();
        if (texture == null) return;
        List<SpriteContents.AnimationState> updatedAnimations = null;
        for (var id : OrionCleanWater.SPRITES) {
            var sprite = texturesByName.get(id);
            if (sprite == null) continue;
            var mipmaps = ((OrionWaterSpritePixels) (Object) sprite.contents()).orion$waterMipmaps();
            var originalAlpha = orion$waterAlpha.computeIfAbsent(sprite, ignored -> OrionCleanWater.captureAlpha(mipmaps));
            OrionCleanWater.adjustAlpha(mipmaps, originalAlpha, opacity);
            var previous = orion$waterAnimations.get(sprite);
            if (previous == null) {
                orion$uploadWaterRegion(atlas, sprite, mipmaps);
                continue;
            }
            var state = sprite.createAnimationState(orion$waterUbos.get(sprite), orion$waterUboStride);
            if (updatedAnimations == null) updatedAnimations = new ArrayList<>(animatedTexturesStates);
            updatedAnimations.set(updatedAnimations.indexOf(previous), state);
            orion$waterAnimations.put(sprite, state);
            previous.close();
        }
        if (updatedAnimations != null) animatedTexturesStates = updatedAnimations;
        orion$waterOpacity = opacity;
    }

    @Unique
    private void orion$uploadWaterRegion(TextureAtlas atlas, TextureAtlasSprite sprite, NativeImage[] mipmaps) {
        var texture = atlas.getTexture();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        int padding = Math.round(sprite.getU0() * texture.getWidth(0)) - sprite.getX();
        for (int mip = 0; mip <= maxMipLevel; mip++) {
            var source = mipmaps[mip];
            int pad = padding >> mip;
            int width = Math.max(1, sprite.contents().width() >> mip);
            int height = Math.max(1, sprite.contents().height() >> mip);
            try (var padded = new NativeImage(width + 2 * pad, height + 2 * pad, false)) {
                for (int y = 0; y < padded.getHeight(); y++) {
                    int sy = Math.clamp(y - pad, 0, height - 1);
                    for (int x = 0; x < padded.getWidth(); x++) {
                        padded.setPixel(x, y, source.getPixel(Math.clamp(x - pad, 0, width - 1), sy));
                    }
                }
                int x = sprite.getX() >> mip, y = sprite.getY() >> mip;
                //? if >=26.2 {
                encoder.writeToTexture(texture, padded, mip, 0, x, y);
                //?} else {
                /*encoder.writeToTexture(texture, padded, mip, 0, x, y, padded.getWidth(), padded.getHeight(), 0, 0);
                *///?}
            }
        }
    }
}
