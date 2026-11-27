package ru.orion.client.ares;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.gui.GleamAresCommandWheel;
import ru.orion.client.gui.OrionGuiLanguage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GleamAresKPHelper {
    public static final int COMMAND_SLOTS = 6;
    public static final int COMMAND_LABEL_LENGTH = 32;
    private static final AtomicBoolean SCREENSHOT_PENDING = new AtomicBoolean();
    private static Target selected;
    private static ClientPacketListener selectionConnection;
    private static volatile String freeimageKey = "";

    public record Target(UUID id, String name) {}
    public record WheelAction(String label, String command) {}

    private GleamAresKPHelper() {}

    public static void register() {
        try {
            if (Files.isRegularFile(keyFile()) && Files.size(keyFile()) <= 512)
                freeimageKey = sanitizeKey(Files.readString(keyFile()));
        } catch (IOException ignored) { }
        ClientTickEvents.END_CLIENT_TICK.register(client -> selectedTarget(client));
    }

    public static boolean hotkey(Minecraft client, KeyEvent event) {
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isAresKPHelperEnabled() || client.player == null || client.level == null
                || GleamMinecraft.screen(client) != null || GleamMinecraft.overlay(client) != null
                || event.hasControlDown() || event.hasAltDown() || event.hasShiftDown()
                || (event.modifiers() & InputConstants.MOD_SUPER) != 0) return false;
        if (event.key() == InputConstants.KEY_R) {
            select(client);
            return true;
        }
        return false;
    }

    public static void openWheel(Minecraft client) {
        if (GleamMinecraft.screen(client) != null || GleamMinecraft.overlay(client) != null) return;
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isAresKPHelperEnabled()) return;
        var target = selectedTarget(client);
        if (target == null) {
            notice(client, "Наведи прицел на игрока и нажми R");
            return;
        }
        List<WheelAction> commands = wheelActions(config);
        if (commands.isEmpty()) {
            notice(client, "Добавь команды в настройках AresKPHelper");
            return;
        }
        GleamMinecraft.setScreen(client, new GleamAresCommandWheel(target, commands));
    }

    static List<WheelAction> wheelActions(OrionConfig config) {
        var actions = new ArrayList<WheelAction>(COMMAND_SLOTS);
        for (int slot = 0; slot < COMMAND_SLOTS; slot++) {
            String command = sanitizeCommand(config.getAresCommand(slot));
            if (command.isEmpty()) continue;
            String label = config.getAresCommandLabel(slot).strip();
            if (label.isEmpty()) label = command.substring(0, command.indexOf(' '));
            actions.add(new WheelAction(label, command));
        }
        return actions;
    }

    public static boolean execute(Minecraft client, Target target, String template) {
        var connection = client.getConnection();
        if (connection == null || target != selectedTarget(client) || template.isEmpty()) {
            notice(client, "Выбранный игрок больше недоступен");
            return false;
        }
        String name = target.name();
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            notice(client, "Имя игрока нельзя подставить в команду");
            return false;
        }
        String command = sanitizeCommand(template).replace("{nick}", name);
        if (command.isEmpty()) return false;
        connection.sendCommand(command.substring(1));
        return true;
    }

    public static Target selectedTarget(Minecraft client) {
        if (selected == null) return null;
        OrionConfig config = OrionClient.getConfigOrNull();
        if (config == null || !config.isAresKPHelperEnabled() || client.getConnection() != selectionConnection) {
            selected = null;
            selectionConnection = null;
        }
        return selected;
    }

    public static AbstractClientPlayer markedPlayer(Minecraft client) {
        var target = selectedTarget(client);
        if (target == null || client.level == null || client.player == null) return null;
        for (var player : client.level.players()) {
            if (player.isAlive() && !player.isRemoved() && target.id().equals(player.getUUID())) return player;
        }
        return null;
    }

    private static void select(Minecraft client) {
        var camera = GleamMinecraft.camera(client);
        Vec3 from = camera.position();
        var forward = camera.forwardVector();
        Vec3 direction = new Vec3(forward.x(), forward.y(), forward.z());
        Vec3 end = from.add(direction.scale(64));
        end = client.level.clip(new ClipContext(from, end, ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE, client.player)).getLocation();
        double nearest = from.distanceToSqr(end);
        AbstractClientPlayer picked = null;
        for (var player : client.level.players()) {
            if (player == client.player || !player.isAlive() || player.isSpectator()
                    || player.isInvisibleTo(client.player)) continue;
            var point = player.getBoundingBox().clip(from, end);
            if (point.isEmpty()) continue;
            double distance = from.distanceToSqr(point.get());
            if (distance < nearest) {
                picked = player;
                nearest = distance;
            }
        }
        if (picked == null) {
            notice(client, "Под прицелом нет игрока");
            return;
        }
        var connection = client.getConnection();
        if (connection == null) return;
        selected = new Target(picked.getUUID(), picked.getGameProfile().name());
        selectionConnection = connection;
        GleamMinecraft.setOverlayMessage(client, Component.literal(
                OrionGuiLanguage.translate("Выбран игрок") + " · " + selected.name()), false);
    }

    public static String commandInput(String input) {
        if (input == null || input.length() > 220 || input.codePoints().anyMatch(Character::isISOControl)) return "";
        return input;
    }

    public static String commandLabelInput(String input) {
        if (input == null || input.length() > COMMAND_LABEL_LENGTH || input.codePoints().anyMatch(Character::isISOControl)) return "";
        return input;
    }

    static String sanitizeCommand(String input) {
        String command = commandInput(input).strip();
        if (command.isEmpty() || !command.contains("{nick}")) return "";
        if (command.charAt(0) != '/') command = "/" + command;
        return command.matches("/[a-zA-Z0-9_:-]+ +.+") ? command : "";
    }

    private static Path keyFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("gleam-freeimage-key.txt");
    }

    public static boolean hasKey() { return !freeimageKey.isEmpty(); }

    public static void importKey(Minecraft client) {
        String key = sanitizeKey(client.keyboardHandler.getClipboard());
        if (key.isEmpty()) {
            notice(client, "Скопируй API-ключ Freeimage.host");
            return;
        }
        try {
            Files.createDirectories(keyFile().getParent());
            Files.writeString(keyFile(), key);
            freeimageKey = key;
            notice(client, "API-ключ Freeimage.host сохранён");
        } catch (IOException error) { notice(client, "Не удалось сохранить API-ключ"); }
    }

    public static void clearKey(Minecraft client) {
        try {
            Files.deleteIfExists(keyFile());
            freeimageKey = "";
            notice(client, "API-ключ Freeimage.host удалён");
        } catch (IOException error) { notice(client, "Не удалось удалить API-ключ"); }
    }

    private static String sanitizeKey(String key) {
        String stripped = key.strip();
        return stripped.matches("[a-zA-Z0-9_-]{8,512}") ? stripped : "";
    }

    public static void screenshotSaved(Path screenshot) {
        Path folder = FabricLoader.getInstance().getGameDir().resolve("screenshots").toAbsolutePath().normalize();
        if (!folder.equals(screenshot.toAbsolutePath().normalize().getParent())) return;
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> publishScreenshot(client, screenshot));
    }

    private static void publishScreenshot(Minecraft client, Path screenshot) {
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isAresKPHelperEnabled() || !config.isAresScreenshotEnabled() || client.level == null) return;
        String key = freeimageKey;
        if (key.isEmpty()) {
            notice(client, "Добавь API-ключ Freeimage.host в AresKPHelper");
            return;
        }
        if (!SCREENSHOT_PENDING.compareAndSet(false, true)) {
            notice(client, "Дождись публикации скриншота");
            return;
        }
        notice(client, "Скриншот сохранён · загрузка на Freeimage.host");
        Thread.startVirtualThread(() -> {
            try {
                String command = "/p add id " + GleamFreeimage.upload(key, screenshot) + " false false";
                client.execute(() -> {
                    client.keyboardHandler.setClipboard(command);
                    notice(client, "Команда для скриншота скопирована");
                });
            } catch (GleamFreeimage.Failure error) {
                client.execute(() -> notice(client, error.getMessage()));
            } catch (IOException | RuntimeException error) {
                client.execute(() -> notice(client, "Не удалось опубликовать скриншот"));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                SCREENSHOT_PENDING.set(false);
            }
        });
    }

    public static void notice(Minecraft client, String text) {
        String translated = OrionGuiLanguage.translate(text);
        var component = Component.literal(translated);
        if (GleamMinecraft.screen(client) instanceof ru.orion.client.gui.OrionClickGuiScreen)
            GleamMinecraft.addChatMessage(client, component);
        GleamMinecraft.setOverlayMessage(client, component, false);
    }
}
