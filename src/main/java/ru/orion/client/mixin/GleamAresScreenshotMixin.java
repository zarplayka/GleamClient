package ru.orion.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.ares.GleamAresKPHelper;

import java.io.File;
import java.util.function.Consumer;

@Mixin(Screenshot.class)
abstract class GleamAresScreenshotMixin {
    @Inject(
            //? if >=26.2 {
            method = "lambda$grab$3",
            //?} elif >=26.1 {
            /*method = "lambda$grab$1",
            *///?} else {
            /*method = "method_22691",
            *///?}
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/NativeImage;writeToFile(Ljava/io/File;)V",
                    shift = At.Shift.AFTER))
    private static void gleam$afterScreenshotSaved(NativeImage image, File destination, Consumer<Component> result,
                                                   CallbackInfo ci) {
        GleamAresKPHelper.screenshotSaved(destination.toPath());
    }
}
