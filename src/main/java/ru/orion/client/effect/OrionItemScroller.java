package ru.orion.client.effect;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

import java.util.function.IntPredicate;

/** Перенос предметов штатными кликами контейнера с возвратом остатка в исходный слот. */
public final class OrionItemScroller {
    public static final boolean EXTERNAL = FabricLoader.getInstance().isModLoaded("itemscroller");
    @FunctionalInterface public interface Click { void click(Slot slot, int button); }
    private OrionItemScroller() {}

    public static boolean supported(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu || menu instanceof HopperMenu || menu instanceof DispenserMenu
                || menu instanceof ShulkerBoxMenu || menu instanceof AbstractFurnaceMenu || menu instanceof InventoryMenu;
    }

    public static boolean eligible(AbstractContainerMenu menu, Slot hovered, Player player) {
        return supported(menu) && hovered != null && hovered.isActive() && hovered.hasItem()
                && !hovered.getItem().has(DataComponents.BUNDLE_CONTENTS) && menu.getCarried().isEmpty()
                && menu.stillValid(player) && side(menu, hovered, player) >= 0;
    }

    public static int transfer(AbstractContainerMenu menu, Slot hovered, Player player, boolean pull, int mode,
                               int limit, IntPredicate locked, Click click) {
        if (!eligible(menu, hovered, player)) return 0;
        ItemStack sample = hovered.getItem().copy();
        int destinationSide = side(menu, hovered, player) ^ (pull ? 0 : 1);
        int sourceSide = destinationSide ^ 1;
        int moved = 0;
        for (int step = 0; step < Math.clamp(limit, 1, 16) && menu.getCarried().isEmpty(); step++) {
            Slot source = null;
            if (!pull && mode < 2) source = hovered;
            else for (Slot candidate : menu.slots) {
                if (side(menu, candidate, player) == sourceSide && usableSource(candidate, player, sample, locked)) {
                    source = candidate;
                    break;
                }
            }
            if (source == null || !usableSource(source, player, sample, locked)) break;
            Slot destination = destination(menu, player, destinationSide, sample, pull ? hovered : null, locked);
            if (destination == null) break;
            int before = source.getItem().getCount();
            click.click(source, 0);
            if (menu.getCarried().isEmpty()) break;
            if (!ItemStack.isSameItemSameComponents(menu.getCarried(), sample)) {
                click.click(source, 0);
                break;
            }
            click.click(destination, mode == 0 ? 1 : 0);
            if (!menu.getCarried().isEmpty()) click.click(source, 0);
            int amount = before - source.getItem().getCount();
            if (amount <= 0 || !menu.getCarried().isEmpty()) break;
            moved += amount;
            if (mode == 1) break;
        }
        return moved;
    }

    private static boolean usableSource(Slot slot, Player player, ItemStack sample, IntPredicate locked) {
        return slot.isActive() && !locked.test(slot.index) && slot.hasItem()
                && ItemStack.isSameItemSameComponents(slot.getItem(), sample)
                && slot.mayPickup(player) && slot.mayPlace(slot.getItem());
    }

    private static Slot destination(AbstractContainerMenu menu, Player player, int side, ItemStack sample,
                                    Slot preferred, IntPredicate locked) {
        if (preferred != null) return accepts(preferred, sample, locked) ? preferred : null;
        Slot empty = null;
        for (Slot slot : menu.slots) {
            if (side(menu, slot, player) != side || !accepts(slot, sample, locked)) continue;
            if (slot.hasItem()) return slot;
            if (empty == null) empty = slot;
        }
        return empty;
    }

    private static boolean accepts(Slot slot, ItemStack sample, IntPredicate locked) {
        ItemStack current = slot.getItem();
        return slot.isActive() && !locked.test(slot.index) && slot.mayPlace(sample)
                && (current.isEmpty() || ItemStack.isSameItemSameComponents(current, sample))
                && current.getCount() < Math.min(slot.getMaxStackSize(sample), sample.getMaxStackSize());
    }

    private static int side(AbstractContainerMenu menu, Slot slot, Player player) {
        if (slot.container == player.getInventory()) {
            int index = slot.getContainerSlot();
            if (index < 0 || index >= 36) return -1;
            return menu instanceof InventoryMenu ? (index < 9 ? 0 : 1) : 0;
        }
        return menu instanceof InventoryMenu ? -1 : 1;
    }
}
