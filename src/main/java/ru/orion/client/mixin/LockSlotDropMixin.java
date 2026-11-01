package ru.orion.client.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.effect.OrionLockSlot;

@Mixin(MultiPlayerGameMode.class)
public abstract class LockSlotDropMixin {
    @Inject(method = "dropItem(Lnet/minecraft/client/player/LocalPlayer;Z)V", at = @At("HEAD"), cancellable = true)
    private void orion$protectHotbar(LocalPlayer player, boolean all, CallbackInfo callback) {
        if (OrionLockSlot.isLocked(player.getInventory().getSelectedSlot())) callback.cancel();
    }
    @Inject(method = "handleContainerInput(IIILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"), cancellable = true)
    private void orion$protectInventory(int id, int slot, int button, ContainerInput action, Player player, CallbackInfo callback) {
        if (action == ContainerInput.THROW && player.containerMenu.containerId == id && slot >= 0 && slot < player.containerMenu.slots.size()
                && OrionLockSlot.isLocked(player.containerMenu.getSlot(slot))) callback.cancel();
    }
}
