package ru.orion.client.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.effect.OrionLockSlot;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeLockSlotMixin {
    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"), cancellable = true)
    private void orion$protectCreativeSlot(Slot slot, int index, int button, ContainerInput action, CallbackInfo callback) {
        if (action == ContainerInput.THROW && OrionLockSlot.isLocked(slot)) callback.cancel();
    }
}
