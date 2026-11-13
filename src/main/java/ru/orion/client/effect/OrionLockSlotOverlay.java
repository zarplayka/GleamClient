package ru.orion.client.effect;


import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import ru.orion.client.OrionClient;
import ru.orion.client.hud.OrionHudView;




/** Marks locked slots above the open container. */
public final class OrionLockSlotOverlay {
    private final OrionHudView view = new OrionHudView("lock_slot_overlay");

    public void draw(GuiGraphicsExtractor context, AbstractContainerMenu menu, int left, int top) {
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isLockSlotEnabled() || config.getLockSlotMask() == 0) {
            release();
            return;
        }
        var scene = new OrionHudView.Scene(context.guiWidth(), context.guiHeight());
        for (Slot slot : menu.slots) {
            if (slot.isActive() && OrionLockSlot.isLocked(slot)) scene.lock(left + slot.x, top + slot.y);
        }
        view.draw(context, scene, 0, 0, context.guiWidth(), context.guiHeight());
    }

    public void release() {
        view.dispose();
    }
}
