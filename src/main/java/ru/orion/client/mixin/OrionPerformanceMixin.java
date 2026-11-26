package ru.orion.client.mixin;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.orion.client.hud.OrionPerformanceHud;

@Mixin(DebugScreenOverlay.class)
abstract class OrionPerformanceMixin {
    @ModifyVariable(method = "logFrameDuration(J)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private long gleam$collectFrameDuration(long duration) {
        return OrionPerformanceHud.frame(duration);
    }
}
