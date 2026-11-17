package ru.orion.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import ru.orion.client.OrionClient;
//? if >=26.3 {
import net.minecraft.client.renderer.SubmitNodeCollection;
//?} else {
/*import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
*///?}

//? if >=26.3 {
@Mixin(SubmitNodeCollection.class)
//?} else {
/*@Mixin(NameTagFeatureRenderer.class)
*///?}
public abstract class OrionNametagsMixin {
    @ModifyArgs(
            //? if >=26.3 {
            method = "nameTag", at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Content$Text;<init>(FFLnet/minecraft/util/FormattedCharSequence;ZIII)V"))
    private static void orion$nametagStyle(Args args) {
        final int shadow = 3, background = 5;
            //?} else if >=26.2 {
            /*method = "prepareText", at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"))
    private static void orion$nametagStyle(Args args) {
        final int shadow = 4, background = 6;
            *///?} else if >=26.1 {
            /*method = "renderTranslucent", at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"))
    private void orion$nametagStyle(Args args) {
        final int shadow = 4, background = 8;
            *///?} else {
            /*method = "render", at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"))
    private void orion$nametagStyle(Args args) {
        final int shadow = 4, background = 8;
            *///?}
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isNametagsEnabled()) return;
        args.set(shadow, config.isNametagsShadow());
        if (!config.isNametagsBackground()) args.set(background, 0);
    }
}
