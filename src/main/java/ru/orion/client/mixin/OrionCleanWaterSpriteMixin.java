package ru.orion.client.mixin;

import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import ru.orion.client.effect.OrionCleanWater;

@Mixin(SpriteResourceLoader.class)
public interface OrionCleanWaterSpriteMixin {
    @ModifyArgs(
            //? if >=26.1 {
            method = "lambda$create$0",
            //?} else {
            /*method = "method_52851",
            *///?}
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/SpriteContents;<init>(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/resources/metadata/animation/FrameSize;Lcom/mojang/blaze3d/platform/NativeImage;Ljava/util/Optional;Ljava/util/List;Ljava/util/Optional;)V"))
    private static void orion$adjustWater(Args args) {
        OrionCleanWater.adjustSprite(args.get(0), args.get(2));
    }
}
