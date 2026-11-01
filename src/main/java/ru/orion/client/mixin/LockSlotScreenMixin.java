package ru.orion.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.OrionClient;
import ru.orion.client.keybind.OrionKeybinds;
import ru.orion.client.effect.OrionLockSlot;
import ru.orion.client.effect.OrionLockSlotOverlay;
import ru.orion.client.effect.OrionInventoryAnimation;

@Mixin(AbstractContainerScreen.class)
public abstract class LockSlotScreenMixin {
    @Shadow protected Slot hoveredSlot;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow @Final protected AbstractContainerMenu menu;
    @Unique private final OrionInventoryAnimation orion$inventoryAnimation = new OrionInventoryAnimation();
    @Unique private final OrionLockSlotOverlay orion$lockOverlay = new OrionLockSlotOverlay();

    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void orion$toggleLock(KeyEvent event, CallbackInfoReturnable<Boolean> callback) {
        var config = OrionClient.getConfigOrNull();
        if (config != null && config.isLockSlotEnabled() && OrionKeybinds.isLockSlotKey(event)
                && OrionLockSlot.inventoryIndex(hoveredSlot) >= 0) {
            OrionLockSlot.toggle(hoveredSlot);
            callback.setReturnValue(true);
        }
    }
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"))
    private void orion$drawLocks(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        orion$lockOverlay.draw(context,menu,leftPos,topPos);
        orion$inventoryAnimation.draw(context,hoveredSlot,leftPos,topPos);
    }
    @Inject(method = "removed()V", at = @At("HEAD"))
    private void orion$releaseLocks(CallbackInfo callback) { orion$lockOverlay.release(); orion$inventoryAnimation.release(); }
}
