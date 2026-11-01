package ru.orion.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.basketball.engine.OrionBasketballController;

/** Мышь остаётся доступна движку, ванильные атаки и использование блокируются при владении мячом. */
@Mixin(Minecraft.class)
public abstract class BasketballInteractionMixin {
    @Inject(method="startAttack()Z",at=@At("HEAD"),cancellable=true)
    private void orion$basketballAttack(CallbackInfoReturnable<Boolean> callback) {
        if(OrionBasketballController.localHolding())callback.setReturnValue(false);
    }
    @Inject(method="startUseItem()V",at=@At("HEAD"),cancellable=true)
    private void orion$basketballUse(CallbackInfo callback) {
        if(OrionBasketballController.localHolding())callback.cancel();
    }
    @Inject(method="continueAttack(Z)V",at=@At("HEAD"),cancellable=true)
    private void orion$basketballContinue(boolean attack,CallbackInfo callback) {
        if(attack&&OrionBasketballController.localHolding())callback.cancel();
    }
}
