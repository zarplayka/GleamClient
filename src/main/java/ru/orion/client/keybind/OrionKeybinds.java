package ru.orion.client.keybind;

import ru.orion.client.GleamMinecraft;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import ru.orion.client.effect.OrionLockSlot;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.gui.OrionClickGuiScreen;
import ru.orion.client.gui.OrionEmoteWheelScreen;
import ru.orion.client.effect.OrionEmotes;

import java.util.Locale;

public final class OrionKeybinds {
    private static final String OPEN_CLICK_GUI_TRANSLATION_KEY = "key.orion-client.open_click_gui";
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "orion_client")
    );

    private final OrionConfigManager configManager;
    private KeyMapping openClickGuiKey;
    private static KeyMapping lockSlotKey;
    private static KeyMapping zoomKey;
    private static KeyMapping freeLookKey;
    private static KeyMapping secondaryZoomKey;
    private static KeyMapping waypointKey;
    private static KeyMapping emoteKey;

    public OrionKeybinds(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        openClickGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                OPEN_CLICK_GUI_TRANSLATION_KEY,
                //? if >=26.3 {
                InputConstants.Type.KEYBOARD,
                //?} else {
                                /*InputConstants.Type.KEYSYM,
                *///?}
                InputConstants.KEY_RSHIFT,
                CATEGORY
        ));

        lockSlotKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                //? if >=26.3 {
                "key.orion-client.lock_slot", InputConstants.Type.KEYBOARD, InputConstants.KEY_L, CATEGORY));
                //?} else {
                                /*"key.orion-client.lock_slot", InputConstants.Type.KEYSYM, InputConstants.KEY_L, CATEGORY));
                *///?}
        zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                //? if >=26.3 {
                "key.orion-client.zoom", InputConstants.Type.KEYBOARD, InputConstants.KEY_C, CATEGORY));
                //?} else {
                                /*"key.orion-client.zoom", InputConstants.Type.KEYSYM, InputConstants.KEY_C, CATEGORY));
                *///?}

        freeLookKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                //? if >=26.3 {
                "key.orion-client.freelook", InputConstants.Type.KEYBOARD, InputConstants.KEY_LALT, CATEGORY));
                //?} else {
                /*"key.orion-client.freelook", InputConstants.Type.KEYSYM, InputConstants.KEY_LALT, CATEGORY));
                *///?}
        secondaryZoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                //? if >=26.3 {
                "key.orion-client.secondary_zoom", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
                //?} else {
                                /*"key.orion-client.secondary_zoom", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
                *///?}
        waypointKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                //? if >=26.3 {
                "key.orion-client.add_waypoint", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
                //?} else {
                                /*"key.orion-client.add_waypoint", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
                *///?}
        emoteKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                //? if >=26.3 {
                "key.orion-client.emotes", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, CATEGORY));
                //?} else {
                                /*"key.orion-client.emotes", InputConstants.Type.KEYSYM, InputConstants.KEY_V, CATEGORY));
                *///?}
        ClientTickEvents.END_CLIENT_TICK.register(this::handleClientTick);
    }

    public static KeyMapping getLockSlotKey() { return lockSlotKey; }
    public static KeyMapping getWaypointKey() { return waypointKey; }
    public static KeyMapping getEmoteKey() { return emoteKey; }
    public static KeyMapping getZoomKey() { return zoomKey; }
    public static KeyMapping getFreeLookKey() { return freeLookKey; }
    public static boolean isZoomDown() { return zoomKey != null && zoomKey.isDown(); }
    public static KeyMapping getSecondaryZoomKey() { return secondaryZoomKey; }
    public static boolean isSecondaryZoomDown() { return secondaryZoomKey != null && secondaryZoomKey.isDown(); }

    public static String keyLabel(KeyMapping key) {
        return key == null || key.isUnbound() ? "" : keyLabel(key.saveString());
    }

    public static String keyLabel(String key) {
        return switch (key) {
            case "key.mouse.left" -> "LMB";
            case "key.mouse.right" -> "RMB";
            case "key.mouse.middle" -> "MMB";
            case "key.keyboard.unknown" -> "";
            case "key.keyboard.left.shift" -> "L Shift";
            case "key.keyboard.right.shift" -> "R Shift";
            case "key.keyboard.left.control" -> "L Ctrl";
            case "key.keyboard.right.control" -> "R Ctrl";
            case "key.keyboard.left.alt" -> "L Alt";
            case "key.keyboard.right.alt" -> "R Alt";
            case "key.keyboard.page.up" -> "Page Up";
            case "key.keyboard.page.down" -> "Page Down";
            default -> {
                if (key.startsWith("key.mouse.")) yield "Mouse " + key.substring("key.mouse.".length());
                if (key.startsWith("scancode.")) yield "Scan " + key.substring("scancode.".length());
                String keyboard = key.replaceFirst("^key\\.keyboard\\.", "").replace(".", " ");
                yield keyboard.isBlank() ? "" : keyboard.substring(0, 1).toUpperCase(Locale.ROOT) + keyboard.substring(1);
            }
        };
    }

    public static boolean isLockSlotKey(KeyEvent event) {
        return lockSlotKey != null && lockSlotKey.matches(event);
    }

    private void handleClientTick(Minecraft client) {
        while (freeLookKey.consumeClick()) { /* FreeLook reads the held state. */ }
        while (emoteKey.consumeClick()) {
            if (configManager.getConfig().isEmotesEnabled() && GleamMinecraft.screen(client) == null
                    && OrionEmotes.canDance(client.player)) GleamMinecraft.setScreen(client, new OrionEmoteWheelScreen());
        }
        while (lockSlotKey.consumeClick()) {
            if (client.player != null && GleamMinecraft.screen(client) == null && configManager.getConfig().isLockSlotEnabled()) {
                OrionLockSlot.toggleIndex(client.player.getInventory().getSelectedSlot());
            }
        }
        while (waypointKey.consumeClick()) {
            if (client.player == null || client.level == null || GleamMinecraft.screen(client) != null) continue;
            var point = ru.orion.client.effect.OrionWaypoints.addAtPlayer(configManager, client);
            if (point == null) {
                GleamMinecraft.setOverlayMessage(client, net.minecraft.network.chat.Component.literal("Ƴ Достигнут предел меток")
                        .withStyle(net.minecraft.ChatFormatting.WHITE), false);
            } else if (configManager.getConfig().isWaypointEditAfterCreate()) {
                GleamMinecraft.setScreen(client, OrionClickGuiScreen.withWaypointSettings(configManager, point));
            } else {
                GleamMinecraft.setOverlayMessage(client, net.minecraft.network.chat.Component.literal("Ʋ Метка ")
                        .withStyle(net.minecraft.ChatFormatting.WHITE)
                        .append(net.minecraft.network.chat.Component.literal(point.name).withStyle(net.minecraft.ChatFormatting.GOLD))
                        .append(net.minecraft.network.chat.Component.literal(" добавлена").withStyle(net.minecraft.ChatFormatting.WHITE)), false);
            }
        }
        while (openClickGuiKey.consumeClick()) {
            GleamMinecraft.setScreen(client, new OrionClickGuiScreen(configManager));
        }
    }
}
