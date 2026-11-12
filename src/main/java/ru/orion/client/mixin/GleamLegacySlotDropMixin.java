package ru.orion.client.mixin;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.effect.OrionLockSlot;
@Mixin(LocalPlayer.class)
public abstract class GleamLegacySlotDropMixin {
    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void gleam$protectHotbar(boolean all, CallbackInfoReturnable<Boolean> callback) {
        var player = (LocalPlayer) (Object) this;
        if (OrionLockSlot.isLocked(player.getInventory().getSelectedSlot())) callback.setReturnValue(false);
    }
}
