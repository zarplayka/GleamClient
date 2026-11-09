package ru.orion.client.mixin;

import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.orion.client.OrionClient;
import ru.orion.client.effect.OrionWorldChanger;

@Mixin(CloudRenderer.class)
public abstract class OrionCloudColorMixin {
    @ModifyVariable(method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int orion$applyCloudColor(int vanilla) {
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isWorldChangerEnabled() || !config.isWorldChangerCloudsEnabled()) return vanilla;
        int tint = OrionWorldChanger.parseOpaqueColor(config.getWorldChangerCloudColor());
        int color = ARGB.multiply(vanilla, tint);
        return ARGB.color(Math.round(ARGB.alpha(color) * config.getWorldChangerCloudOpacity() / 100F),
                ARGB.red(color), ARGB.green(color), ARGB.blue(color));
    }
}
