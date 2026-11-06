package ru.orion.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.OrionClient;
import ru.orion.client.effect.OrionItemScroller;
import ru.orion.client.effect.OrionLockSlot;

@Mixin(AbstractContainerScreen.class)
public abstract class OrionItemScrollerMixin {
    @Shadow @Final protected AbstractContainerMenu menu;
    @Shadow private Slot getHoveredSlot(double x, double y) { throw new AssertionError(); }
    @Shadow protected abstract void slotClicked(Slot slot, int index, int button, ContainerInput action);
    @Unique private double orion$scrollRemainder;

    @Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), cancellable = true)
    private void orion$scrollItems(double x, double y, double horizontal, double vertical,
                                   CallbackInfoReturnable<Boolean> callback) {
        Minecraft client = Minecraft.getInstance();
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isItemScrollerEnabled() || OrionItemScroller.EXTERNAL || client.player == null
                || client.player.containerMenu != menu || !Double.isFinite(vertical) || vertical == 0
                || (Object) this instanceof CreativeModeInventoryScreen) return;
        Slot hovered = getHoveredSlot(x, y);
        if (!OrionItemScroller.eligible(menu, hovered, client.player)) { orion$scrollRemainder = 0; return; }
        if (Math.signum(orion$scrollRemainder) != Math.signum(vertical)) orion$scrollRemainder = 0;
        orion$scrollRemainder += Math.clamp(vertical, -16D, 16D);
        int steps = (int) orion$scrollRemainder;
        if (steps == 0) { callback.setReturnValue(true); return; }
        orion$scrollRemainder -= steps;
        boolean shift = InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
        boolean control = InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
        int mode = control && config.isItemScrollerMatching() ? 2 : shift && config.isItemScrollerStacks() ? 1 : 0;
        boolean pull = (steps > 0) != config.isItemScrollerReverse();
        OrionItemScroller.transfer(menu, hovered, client.player, pull, mode,
                mode == 2 ? Math.round(config.getItemScrollerLimit()) : Math.min(Math.abs(steps), 16),
                index -> index >= 0 && index < menu.slots.size() && OrionLockSlot.isLocked(menu.slots.get(index)),
                (slot, button) -> slotClicked(slot, slot.index, button, ContainerInput.PICKUP));
        callback.setReturnValue(true);
    }
}
