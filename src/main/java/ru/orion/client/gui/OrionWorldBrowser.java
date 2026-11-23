//~ legacy_gui
package ru.orion.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.OrionClient;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** A Lumen view of the active vanilla browser. Vanilla owns world loading, pings and connections. */
public final class OrionWorldBrowser {
    private static final long UPDATE_NANOS = 100_000_000L;
    private static final String CREATE = "+";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault());
    private static OrionWorldBrowser current;
    private static int nextBrowser;

    private final Minecraft client;
    private final Screen screen;
    private final boolean single;
    private final int browser = ++nextBrowser;
    private final OrionLumenView view = new OrionLumenView("world-browser.html", "world_browser",
            "/assets/orion-client/fonts/paradox/ParadoxSans-Regular.ttf");
    private final Map<Path, CompletableFuture<byte[]>> worldIcons = new HashMap<>();
    private final Map<String, Icon> icons = new HashMap<>();
    private final JsonObject labels = labels();
    private List<Row> rows = List.of();
    private JsonObject previous;
    private boolean active, attempted, initialized, dirty = true;
    private float scale = 1;
    private long nextUpdate;
    private int nextIcon;
    private String selected = "", status = "", form = "", formTarget = "";

    private record Row(String id, JsonObject data, Runnable play, WorldSelectionList.WorldListEntry world,
                       ServerSelectionList.OnlineServerEntry server) { }
    private record Icon(int fingerprint, String asset) { }

    private OrionWorldBrowser(Minecraft client, Screen screen) {
        this.client = client;
        this.screen = screen;
        single = screen instanceof SelectWorldScreen;
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof SelectWorldScreen || screen instanceof JoinMultiplayerScreen)) return;
            String selection = current != null && current.screen == screen ? current.selected : "";
            if (current != null) current.dispose();
            var menu = new OrionWorldBrowser(client, screen);
            current = menu;
            menu.selected = selection;
            ScreenEvents.remove(screen).register(owner -> {
                if (current == menu) { current = null; menu.dispose(); }
            });
            ScreenMouseEvents.allowMouseClick(screen).register((owner, event) -> current != menu || !menu.pointer(event, true));
            ScreenMouseEvents.allowMouseRelease(screen).register((owner, event) -> current != menu || !menu.pointer(event, false));
            ScreenMouseEvents.allowMouseDrag(screen).register((owner, event, dx, dy) -> current != menu || !menu.active);
            ScreenMouseEvents.allowMouseScroll(screen).register((owner, x, y, dx, dy) -> current != menu || !menu.wheel(x, y, dx, dy));
            ScreenKeyboardEvents.allowKeyPress(screen).register((owner, key) -> current != menu || !menu.key(key));
        });
    }

    public static boolean active(Screen screen) { return current != null && current.screen == screen && current.active; }

    public static boolean type(CharacterEvent event) {
        var menu = current;
        if (menu == null || !menu.active || GleamMinecraft.screen(menu.client) != menu.screen
                || GleamMinecraft.overlay(menu.client) != null) return false;
        if (event.isAllowedChatCharacter()) menu.view.text(event.codepointAsString());
        return true;
    }

    public static boolean drawBrowser(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        var menu = current;
        return menu != null && menu.screen == screen && menu.draw(graphics, mouseX, mouseY, delta);
    }

    private boolean draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (GleamMinecraft.screen(client) != screen || GleamMinecraft.overlay(client) != null
                || client.options.narrator().get() != NarratorStatus.OFF) {
            active = false;
            return false;
        }
        if (!attempted) { attempted = true; initialized = view.ready(); }
        if (!initialized) return false;
        int width = screen.width, height = screen.height;
        if (width < 1 || height < 1) return false;
        long now = System.nanoTime();
        if (now >= nextUpdate) {
            pollVanillaList(graphics, delta);
            if (GleamMinecraft.screen(client) != screen) return true;
            updateRows();
            nextUpdate = now + UPDATE_NANOS;
        }
        var window = client.getWindow();
        float ratio = Math.clamp(Math.min(window.getWidth() / 1440F, window.getHeight() / 900F), .6F, 2F);
        scale = ratio / window.getGuiScale();
        view.resize((int) Math.ceil(width / scale), (int) Math.ceil(height / scale), ratio);
        if (dirty) {
            JsonObject state = state();
            if (!state.equals(previous)) { view.update(state); previous = state; }
            dirty = false;
        }
        OrionTitleMenu.drawBackdrop(graphics, width, height);
        view.move(mouseX / scale, mouseY / scale);
        view.frame();
        active = view.draw(graphics, 0, 0, width, height, scale, scale);
        if (active) receive();
        return active;
    }

    private void pollVanillaList(GuiGraphicsExtractor graphics, float delta) {
        // Its draw pass also polls the world-load future or queues the native server pings.
        // Run that pass on its usual widget, with an empty clip so it contributes no visible UI.
        graphics.enableScissor(0, 0, 0, 0);
        try {
            var worlds = worldList();
            var servers = serverList();
            if (worlds != null) extractList(graphics, worlds, delta);
            if (servers != null) extractList(graphics, servers, delta);
        } finally { graphics.disableScissor(); }
    }

    private static void extractList(GuiGraphicsExtractor graphics, net.minecraft.client.gui.components.AbstractWidget list, float delta) {
        //? if >=26.1 {
        list.extractRenderState(graphics, -1, -1, delta);
        //?} else {
        /*list.render(graphics, -1, -1, delta);
        *///?}
    }

    private WorldSelectionList worldList() {
        if (!single) return null;
        return screen.children().stream().filter(WorldSelectionList.class::isInstance).map(WorldSelectionList.class::cast).findFirst().orElse(null);
    }

    private ServerSelectionList serverList() {
        if (single) return null;
        return screen.children().stream().filter(ServerSelectionList.class::isInstance).map(ServerSelectionList.class::cast).findFirst().orElse(null);
    }

    private void updateRows() {
        List<Row> next = new ArrayList<>();
        int activeIndex = 0;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).id().equals(selected)) activeIndex = i;
        if (single) {
            var list = worldList();
            if (list != null) for (var element : list.children()) {
                if (element instanceof WorldSelectionList.WorldListEntry world) {
                    var level = world.getLevelSummary();
                    String id = "world:" + level.getLevelId();
                    JsonObject entry = entry(id, level.getLevelName(), level.isHardcore() ? t("Хардкор") : level.getGameMode().getLongDisplayName().getString(),
                            level.getInfo().getString(), "world");
                    entry.addProperty("address", "");
                    entry.addProperty("version", level.getWorldVersionName().getString());
                    entry.addProperty("when", level.getLastPlayed() <= 0 ? "" : DATE.format(Instant.ofEpochMilli(level.getLastPlayed())));
                    entry.addProperty("play", level.primaryActionMessage().getString());
                    entry.addProperty("canPlay", level.primaryActionActive());
                    entry.addProperty("canEdit", level.canEdit());
                    entry.addProperty("canDelete", level.canDelete());
                    entry.addProperty("canRecreate", level.canRecreate());
                    entry.addProperty("icon", Math.abs(next.size() - activeIndex) <= 6 ? worldIcon(id, level.getIcon()) : cachedIcon(id));
                    next.add(new Row(id, entry, () -> { if (level.primaryActionActive()) world.joinWorld(); }, world, null));
                }
            }
        } else {
            var list = serverList();
            if (list != null) for (var element : list.children()) {
                if (element instanceof ServerSelectionList.OnlineServerEntry server) {
                    var data = server.getServerData();
                    String id = "server:" + next.size() + ":" + data.ip;
                    String status = switch (data.state()) {
                        case INITIAL, PINGING -> t("Проверяем сервер…");
                        case SUCCESSFUL -> t("В сети");
                        case INCOMPATIBLE -> t("Другая версия Minecraft");
                        case UNREACHABLE -> t("Нет соединения");
                    };
                    JsonObject entry = entry(id, data.name, status, data.motd == null ? "" : data.motd.getString(), "server");
                    entry.addProperty("address", data.ip);
                    entry.addProperty("version", data.version == null ? "" : data.version.getString());
                    entry.addProperty("online", data.players == null ? "" : data.players.online() + " / " + data.players.max());
                    entry.addProperty("ping", data.state() == ServerData.State.SUCCESSFUL && data.ping >= 0 ? data.ping + " " + t("мс") : "");
                    entry.addProperty("canPlay", true);
                    entry.addProperty("icon", Math.abs(next.size() - activeIndex) <= 6 ? registerIcon(id, data.getIconBytes()) : cachedIcon(id));
                    next.add(new Row(id, entry, server::join, null, server));
                } else if (element instanceof ServerSelectionList.NetworkServerEntry lan) {
                    String id = "lan:" + next.size();
                    JsonObject entry = entry(id, lan.getServerNarration().getString(), t("Локальная сеть"), "", "lan");
                    entry.addProperty("canPlay", true);
                    next.add(new Row(id, entry, lan::join, null, null));
                }
            }
            for (var value : OrionFriendWorlds.state(client).getAsJsonArray("invites")) {
                var invite = value.getAsJsonObject();
                if (!invite.get("incoming").getAsBoolean() || !invite.get("state").getAsString().equals("pending")) continue;
                String key = invite.get("id").getAsString();
                String id = "invite:" + key;
                JsonObject entry = entry(id, invite.get("username").getAsString(), t("Миры друзей"), "", "invite");
                entry.addProperty("canPlay", true);
                next.add(new Row(id, entry, () -> OrionFriendWorlds.accept(client, key), null, null));
            }
        }
        rows = next;
        if (selected.isEmpty() || !selected.equals(CREATE) && find(selected) == null) selected = rows.isEmpty() ? "" : rows.getFirst().id();
        dirty = true;
    }

    private static JsonObject entry(String id, String name, String tag, String description, String kind) {
        JsonObject entry = new JsonObject();
        entry.addProperty("id", id);
        entry.addProperty("name", name);
        entry.addProperty("tag", tag);
        entry.addProperty("description", description);
        entry.addProperty("kind", kind);
        return entry;
    }

    private String worldIcon(String id, Path path) {
        if (path == null) return "";
        var task = worldIcons.computeIfAbsent(path, icon -> CompletableFuture.supplyAsync(() -> {
            try (var stream = Files.newInputStream(icon)) { return stream.readNBytes(262145); }
            catch (IOException failure) { return null; }
        }, Util.nonCriticalIoPool()));
        return registerIcon(id, task.getNow(null));
    }

    private String cachedIcon(String id) {
        var icon = icons.get(id);
        return icon == null ? "" : icon.asset();
    }

    private String registerIcon(String id, byte[] bytes) {
        if (bytes == null || bytes.length < 24 || bytes.length > 262144) return "";
        var header = ByteBuffer.wrap(bytes);
        if (header.getLong(0) != 0x89504E470D0A1A0AL || header.getInt(12) != 0x49484452) return "";
        int width = header.getInt(16), height = header.getInt(20);
        if (width < 1 || height < 1 || width > 256 || height > 256) return "";
        int fingerprint = Arrays.hashCode(bytes);
        var previous = icons.get(id);
        if (previous != null && previous.fingerprint() == fingerprint) return previous.asset();
        if (nextIcon >= 128) return "";
        String name = "assets/world-browser-" + browser + "-" + ++nextIcon + ".png";
        view.registerImage(name, bytes);
        icons.put(id, new Icon(fingerprint, name));
        return name;
    }

    private JsonObject state() {
        var config = OrionClient.getConfigOrNull();
        var state = new JsonObject();
        state.addProperty("theme", config == null ? "dark" : switch (config.getClickGuiTheme()) { case 4 -> "light"; case 5 -> "glass"; default -> "dark"; });
        state.addProperty("accent", config == null ? "" : config.getClickGuiAccent());
        state.addProperty("single", single);
        state.addProperty("multiplayer", client.allowsMultiplayer());
        state.addProperty("account", client.getUser().getName());
        state.addProperty("selected", selected);
        state.addProperty("hasProtocolButton", protocolButton() != null);
        state.addProperty("form", form);
        state.addProperty("status", status);
        Row edited = find(formTarget);
        state.addProperty("formName", edited == null ? "" : edited.data().get("name").getAsString());
        state.addProperty("formAddress", edited == null || !edited.data().has("address") ? "" : edited.data().get("address").getAsString());
        state.add("labels", labels);
        var entries = new JsonArray();
        for (Row row : rows) entries.add(row.data());
        state.add("entries", entries);
        return state;
    }

    private Row find(String id) { return rows.stream().filter(row -> row.id().equals(id)).findFirst().orElse(null); }

    private boolean pointer(MouseButtonEvent event, boolean down) {
        if (!active) return false;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            float x = (float) event.x() / scale, y = (float) event.y() / scale;
            view.move(x, y);
            if (down) view.press(x, y); else { view.release(x, y); receive(); }
        }
        return true;
    }

    private boolean wheel(double x, double y, double dx, double dy) {
        if (!active) return false;
        view.wheel((float) x / scale, (float) y / scale, (float) dx, (float) dy);
        receive();
        return true;
    }

    private boolean key(KeyEvent event) {
        if (!active) return false;
        if (event.hasControlDownWithQuirk() && event.key() == InputConstants.KEY_V) {
            String clipboard = client.keyboardHandler.getClipboard();
            JsonObject shortcut = new JsonObject();
            shortcut.addProperty("command", "paste");
            shortcut.addProperty("text", clipboard.substring(0, Math.min(clipboard.length(), 255)));
            view.editShortcut(shortcut);
            return true;
        }
        if (event.hasControlDownWithQuirk() && event.key() == InputConstants.KEY_A) {
            view.editShortcut(JsonParser.parseString("{\"command\":\"select\"}").getAsJsonObject());
            return true;
        }
        String key = switch (event.key()) {
            case InputConstants.KEY_TAB -> "Tab";
            case InputConstants.KEY_ESCAPE -> "Escape";
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> "Enter";
            case InputConstants.KEY_SPACE -> " ";
            case InputConstants.KEY_LEFT -> "ArrowLeft";
            case InputConstants.KEY_RIGHT -> "ArrowRight";
            case InputConstants.KEY_UP -> "ArrowUp";
            case InputConstants.KEY_DOWN -> "ArrowDown";
            case InputConstants.KEY_BACKSPACE -> "Backspace";
            case InputConstants.KEY_DELETE -> "Delete";
            case InputConstants.KEY_HOME -> "Home";
            case InputConstants.KEY_END -> "End";
            case InputConstants.KEY_F5 -> "F5";
            case InputConstants.KEY_Q -> "q";
            case InputConstants.KEY_E -> "e";
            case InputConstants.KEY_SLASH -> "/";
            default -> "";
        };
        if (!key.isEmpty()) {
            int modifiers = (event.hasShiftDown() ? 1 : 0) | (event.hasControlDownWithQuirk() ? 2 : 0);
            view.key(key, key, modifiers, true);
            view.key(key, key, modifiers, false);
            receive();
        }
        return true;
    }

    private void receive() {
        byte[] payload = view.messages();
        if (payload == null || payload.length > 8192) return;
        for (String line : new String(payload, StandardCharsets.UTF_8).lines().toList()) {
            try {
                var item = JsonParser.parseString(line);
                if (item.isJsonArray() && !item.getAsJsonArray().isEmpty()) item = item.getAsJsonArray().get(0);
                if (item.isJsonPrimitive()) item = JsonParser.parseString(item.getAsString());
                if (item.isJsonObject()) handle(item.getAsJsonObject());
            } catch (RuntimeException invalid) { /* Ignore values outside the view contract. */ }
            if (current != this) break;
        }
    }

    private void handle(JsonObject message) {
        if (!message.has("action")) return;
        String action = message.get("action").getAsString();
        if (!form.isEmpty() && !action.equals("back") && !action.equals("save-server")) return;
        String id = message.has("id") ? message.get("id").getAsString() : selected;
        Row row = find(id);
        if (action.equals("select")) {
            if (id.equals(CREATE) || row != null) {
                selected = id;
                if (row != null && row.server() != null) {
                    var list = serverList();
                    if (list != null) list.setScrollAmount(Math.max(0, list.children().indexOf(row.server()) - 2) * 36.0);
                }
                nextUpdate = 0;
                OrionClickGuiScreen.playInterfaceSound(client, "click");
            }
            return;
        }
        Runnable command = switch (action) {
            case "back" -> form.isEmpty() ? screen::onClose : () -> showForm("", "");
            case "single" -> single ? null : () -> switchMode(true);
            case "multi" -> !single || !client.allowsMultiplayer() ? null : () -> switchMode(false);
            case "account" -> () -> OrionTitleMenu.INSTANCE.openMinecraftAccounts(screen, "minecraft-accounts");
            case "protocol" -> protocolButton() == null ? null : () -> press(protocolButton());
            case "play" -> row != null ? row.play() : id.equals(CREATE) ? this::create : null;
            case "create" -> this::create;
            case "direct" -> single ? null : () -> showForm("direct", "");
            case "refresh" -> single ? null : () -> press(vanillaButton(screen, "selectServer.refresh"));
            case "edit" -> row == null ? null : row.world() != null ? () -> worldAction(row, "selectWorld.edit")
                    : row.server() != null ? () -> showForm("edit", id) : null;
            case "delete" -> row == null ? null : row.world() != null ? () -> worldAction(row, "selectWorld.delete")
                    : row.server() != null ? () -> showForm("delete", id) : null;
            case "recreate" -> row != null && row.world() != null ? () -> worldAction(row, "selectWorld.recreate") : null;
            case "save-server" -> single || form.isEmpty() ? null : () -> saveServer(message);
            default -> null;
        };
        if (command != null) {
            OrionClickGuiScreen.playInterfaceSound(client, "click");
            command.run();
        }
    }

    private void worldAction(Row row, String key) {
        var list = worldList();
        if (list == null || !list.children().contains(row.world())) return;
        list.setSelected(row.world());
        press(vanillaButton(screen, key));
    }

    private void create() {
        if (single) press(vanillaButton(screen, "selectWorld.create"));
        else showForm("add", "");
    }

    private void switchMode(boolean worlds) {
        screen.onClose();
        Screen parent = GleamMinecraft.screen(client);
        if (parent instanceof TitleScreen) press(vanillaButton(parent, worlds ? "menu.singleplayer" : "menu.multiplayer"));
        else if (parent != null) GleamMinecraft.setScreen(client, worlds ? new SelectWorldScreen(parent) : new JoinMultiplayerScreen(parent));
    }

    private void showForm(String kind, String id) { form = kind; formTarget = id; status = ""; dirty = true; }

    private void saveServer(JsonObject message) {
        var multiplayer = (JoinMultiplayerScreen) screen;
        var servers = multiplayer.getServers();
        var list = serverList();
        if (list == null) return;
        Row editing = find(formTarget);
        ServerData existing = editing == null || editing.server() == null ? null : editing.server().getServerData();
        if (form.equals("delete")) {
            if (existing != null) servers.remove(existing);
            finishServerChange(servers, list);
            return;
        }
        String address = message.has("address") ? message.get("address").getAsString().trim() : "";
        String name = message.has("name") ? message.get("name").getAsString().trim() : "";
        if (address.length() > 255 || !ServerAddress.isValidAddress(address)) { status = t("Введите корректный адрес сервера"); dirty = true; return; }
        if (form.equals("direct")) { multiplayer.join(new ServerData(address, address, ServerData.Type.OTHER)); return; }
        if (name.isEmpty() || name.length() > 128) { status = t("Введите название сервера"); dirty = true; return; }
        if (existing != null && existing.ip.equals(address)) {
            existing.name = name;
        } else {
            var server = new ServerData(name, address, ServerData.Type.OTHER);
            if (existing == null) servers.add(server, false);
            else {
                server.setResourcePackStatus(existing.getResourcePackStatus());
                for (int i = 0; i < servers.size(); i++) if (servers.get(i) == existing) { servers.replace(i, server); break; }
            }
        }
        finishServerChange(servers, list);
    }

    private void finishServerChange(net.minecraft.client.multiplayer.ServerList servers, ServerSelectionList list) {
        servers.save();
        list.updateOnlineServers(servers);
        nextUpdate = 0;
        showForm("", "");
    }

    private Button protocolButton() {
        return !single ? screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals("ViaFabricPlus")).findFirst().orElse(null) : null;
    }

    private static Button vanillaButton(Screen screen, String key) {
        Component label = Component.translatable(key);
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().equals(label)).findFirst().orElse(null);
    }

    private static void press(Button button) {
        if (button != null && button.active) button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
    }

    private void dispose() {
        active = false;
        worldIcons.clear();
        icons.clear();
        view.dispose();
    }

    private static JsonObject labels() {
        var labels = new JsonObject();
        for (String label : List.of("Одиночная игра", "Сетевая игра", "Поиск", "Аккаунты Minecraft", "Назад", "Создать мир",
                "Добавить сервер", "Играть", "Подключиться", "Изменить", "Удалить", "Пересоздать", "По адресу", "Обновить",
                "Игроки", "Пинг", "Последняя игра", "Выбрать", "Запустить", "Раздел", "Изменить сервер", "Прямое подключение",
                "Удалить сервер", "Сохранить", "Название", "Адрес", "Отмена")) labels.addProperty(label, t(label));
        return labels;
    }

    private static String t(String value) { return OrionGuiLanguage.translate(value); }
}
