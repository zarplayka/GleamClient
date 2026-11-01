package ru.orion.client.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.effect.OrionClickCounter;

@Mixin(MouseHandler.class)
public abstract class OrionClickCounterMixin {
    @Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("TAIL"))
    private void orion$countClicks(long window, MouseButtonInfo button, int action, CallbackInfo callback) {
        OrionClickCounter.onButton(button, action);
    }
}
