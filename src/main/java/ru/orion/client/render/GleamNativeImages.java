package ru.orion.client.render;
import com.mojang.blaze3d.platform.NativeImage;
public final class GleamNativeImages {
    private GleamNativeImages() {}
    public static boolean isClosed(NativeImage image) {
        //? if >=26.1 {
        return image.isClosed();
        //?} else {
        /*return ((ru.orion.client.mixin.GleamNativeImageAccessor)(Object)image).gleam$pixels() == 0L;
        *///?}
    }
}
