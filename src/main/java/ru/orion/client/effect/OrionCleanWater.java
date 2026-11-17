package ru.orion.client.effect;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;

import java.util.List;

public final class OrionCleanWater {
    public static final List<Identifier> SPRITES = List.of(
            Identifier.withDefaultNamespace("block/water_still"),
            Identifier.withDefaultNamespace("block/water_flow"),
            Identifier.withDefaultNamespace("block/water_overlay"));

    private OrionCleanWater() {}

    public static int opacity() {
        var config = OrionClient.getConfigOrNull();
        return config != null && config.isCleanWaterEnabled() ? config.getCleanWaterOpacity() : 100;
    }

    public static boolean clearsFog() {
        var config = OrionClient.getConfigOrNull();
        return config != null && config.isCleanWaterEnabled() && config.isCleanWaterClearFog();
    }

    public static byte[][] captureAlpha(NativeImage[] mipmaps) {
        byte[][] original = new byte[mipmaps.length][];
        for (int mip = 0; mip < mipmaps.length; mip++) {
            NativeImage frame = mipmaps[mip];
            byte[] alpha = new byte[frame.getWidth() * frame.getHeight()];
            for (int y = 0, index = 0; y < frame.getHeight(); y++) {
                for (int x = 0; x < frame.getWidth(); x++) {
                    alpha[index++] = (byte) (frame.getPixel(x, y) >>> 24);
                }
            }
            original[mip] = alpha;
        }
        return original;
    }

    public static void adjustAlpha(NativeImage[] mipmaps, byte[][] original, int opacity) {
        for (int mip = 0; mip < mipmaps.length; mip++) {
            NativeImage frame = mipmaps[mip];
            byte[] alpha = original[mip];
            for (int y = 0, index = 0; y < frame.getHeight(); y++) {
                for (int x = 0; x < frame.getWidth(); x++) {
                    int base = Byte.toUnsignedInt(alpha[index++]);
                    int adjusted = base == 0 ? 0 : Math.max(1, (base * opacity + 50) / 100);
                    frame.setPixel(x, y, (frame.getPixel(x, y) & 0xFFFFFF) | (adjusted << 24));
                }
            }
        }
    }
}
