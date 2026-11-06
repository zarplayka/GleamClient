package ru.orion.client.effect;

import net.minecraft.SharedConstants;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.IntPredicate;

/** Геометрия прозрачного слоя и сохранность предметов при реальных кликах контейнера. */
public final class OrionInventorySkinTest {
    private static final class TestPlayer extends Player {
        private TestPlayer() { super(null, null); }
        @Override public GameType gameMode() { return GameType.SURVIVAL; }
        @Override public void updateTutorialInventoryAction(ItemStack slot, ItemStack cursor, ClickAction action) {}
    }
    private static final class TestLevel extends ClientLevel {
        private TestLevel() { super(null, null, null, null, 0, 0, null, false, 0, 0); }
        @Override public FeatureFlagSet enabledFeatures() { return FeatureFlags.DEFAULT_FLAGS; }
    }
    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
    private static int faces(Object mesh) throws Exception {
        var accessor = mesh.getClass().getDeclaredMethod("faces"); accessor.setAccessible(true);
        return ((List<?>) accessor.invoke(mesh)).size();
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var components = DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build();
        for (Item item : new Item[]{Items.AIR, Items.STONE, Items.DIAMOND, Items.APPLE}) {
            item.builtInRegistryHolder().bindComponents(components);
        }
        for (boolean slim : new boolean[]{false, true}) {
            var model = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim);
            int[] pixels = new int[4096];
            check(faces(OrionSkinLayers.build(model.hat, pixels, .5F)) == 0, "transparent skin must stay empty");
            pixels[8 * 64 + 40] = 0xFFFFFFFF;
            check(faces(OrionSkinLayers.build(model.hat, pixels, .5F)) == 6, "one pixel must form a closed voxel");
            pixels[8 * 64 + 41] = 0xFFFFFFFF;
            check(faces(OrionSkinLayers.build(model.hat, pixels, .5F)) == 10, "adjacent sides must be removed");
            pixels[8 * 64 + 41] = 0x40FFFFFF;
            check(faces(OrionSkinLayers.build(model.hat, pixels, .5F)) == 7, "translucent pixel must stay flat");
        }
        Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        var player = (TestPlayer) unsafe.allocateInstance(TestPlayer.class);
        field(Entity.class, "level").set(player, unsafe.allocateInstance(TestLevel.class));
        var inventory = new Inventory(player, new EntityEquipment());
        field(Player.class, "inventory").set(player, inventory);
        field(Player.class, "abilities").set(player, new Abilities());
        var chest = new SimpleContainer(27);
        var menu = new ChestMenu(MenuType.GENERIC_9x3, 1, inventory, chest, 3);
        player.containerMenu = menu;
        Slot source = menu.slots.get(0);
        chest.setItem(0, new ItemStack(Items.STONE, 10));
        OrionItemScroller.Click clicks = (slot, button) -> menu.clicked(slot.index, button, ContainerInput.PICKUP, player);
        IntPredicate unlocked = index -> false;
        check(OrionItemScroller.transfer(menu, source, player, false, 0, 1, unlocked, clicks) == 1, "single transfer");
        check(chest.getItem(0).getCount() == 9 && inventory.countItem(Items.STONE) == 1, "single counts");
        check(menu.getCarried().isEmpty(), "single must return cursor");
        check(OrionItemScroller.transfer(menu, source, player, true, 0, 1, unlocked, clicks) == 1, "single pull");
        check(chest.getItem(0).getCount() == 10 && inventory.countItem(Items.STONE) == 0, "pull counts");
        check(OrionItemScroller.transfer(menu, source, player, false, 1, 1, unlocked, clicks) == 10, "stack transfer");
        check(menu.getCarried().isEmpty(), "stack must return cursor");
        chest.setItem(0, new ItemStack(Items.STONE, 64));
        chest.setItem(1, new ItemStack(Items.STONE, 64));
        check(OrionItemScroller.transfer(menu, source, player, false, 2, 16, unlocked, clicks) == 128, "all matching");
        check(chest.countItem(Items.STONE) == 0 && inventory.countItem(Items.STONE) == 138, "conserve all items");
        chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
        check(OrionItemScroller.transfer(menu, source, player, false, 0, 1, index -> index == 0, clicks) == 0, "locked source");
        menu.setCarried(new ItemStack(Items.APPLE));
        check(OrionItemScroller.transfer(menu, source, player, false, 0, 1, unlocked, clicks) == 0, "occupied cursor");
        menu.setCarried(ItemStack.EMPTY);
        check(inventory.countItem(Items.DIAMOND) == 0 && chest.getItem(0).getCount() == 5, "rejected transfer must not mutate");
        for (int index = 0; index < 36; index++) inventory.setItem(index, new ItemStack(Items.STONE, 64));
        check(OrionItemScroller.transfer(menu, source, player, false, 0, 1, unlocked, clicks) == 0, "full destination");
        check(menu.getCarried().isEmpty() && chest.getItem(0).getCount() == 5, "full destination must leave source intact");
        System.out.println("Skin voxel geometry and real inventory click transfers passed");
    }
}
