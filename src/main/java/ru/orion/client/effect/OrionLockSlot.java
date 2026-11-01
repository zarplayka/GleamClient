package ru.orion.client.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.Slot;
import ru.orion.client.OrionClient;
import ru.orion.client.mixin.OrionCreativeSlotAccessor;

public final class OrionLockSlot {
    private OrionLockSlot() { }
    public static int inventoryIndex(Slot slot) {
        if (slot instanceof OrionCreativeSlotAccessor wrapper) slot = wrapper.orion$getTargetSlot();
        Minecraft client = Minecraft.getInstance();
        if (slot == null || client.player == null || slot.container != client.player.getInventory()) return -1;
        int index = slot.getContainerSlot();
        return index >= 0 && index < 41 ? index : -1;
    }
    public static boolean isLocked(int index) {
        var config = OrionClient.getConfigOrNull();
        return config != null && config.isLockSlotEnabled() && index >= 0 && index < 41
                && (config.getLockSlotMask() & (1L << index)) != 0;
    }
    public static boolean isLocked(Slot slot) { return isLocked(inventoryIndex(slot)); }
    public static void toggle(Slot slot) {
        int index = inventoryIndex(slot);
        toggleIndex(index);
    }
    public static void toggleIndex(int index) {
        if (index < 0 || index >= 41) return;
        var manager = OrionClient.getInstance().getConfigManager();
        manager.setLockSlotMask(manager.getConfig().getLockSlotMask() ^ (1L << index));
    }
}
