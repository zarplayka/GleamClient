//~ legacy_gui
package ru.orion.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.paradox.lumen.LumenNative;
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
import net.minecraft.network.chat.Style;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelStorageSource;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.OrionClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.Font;
import java.awt.FontFormatException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;

/** A Lumen view of the active vanilla browser. Vanilla owns world loading, pings and connections. */
public final class OrionWorldBrowser {
    private static final long UPDATE_NANOS = 100_000_000L;
    private static final String CREATE = "+";
    private static final String ROOT = "/assets/orion-client/";
    private static final String PREVIEW_ROOT = "../../src/main/resources/assets/orion-client/";
    private static final String MOTD_SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘꞯʀꜱᴛᴜᴠᴡꭓʏᴢ";
    private static final String[] ARTWORK = {
            "launcher-background-26-3.webp", "launcher-background-26-2.webp", "launcher-background-26-1-2.webp",
            "launcher-background-1-21-11.webp", "launcher-background-1-21-4.webp", "launcher-background-1-16-5.webp",
            "../assets/gameplay-baseline.webp", "../assets/gameplay-orion.webp"
    };
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static OrionWorldBrowser current;
    private static BrowserPage switchingPage;
    private static int nextPage;
    private static Font motdFont;

    private final Minecraft client;
    private final Screen screen;
    private final boolean single;
    private final BrowserPage page;
    private final OrionLumenView view;
    private final Map<Path, CompletableFuture<byte[]>> worldIcons = new HashMap<>();
    private final Map<Path, CompletableFuture<WorldStats>> worldStats = new HashMap<>();
    private final Map<Component, JsonArray> motdCache = new IdentityHashMap<>();
    private final JsonObject labels = labels();
    private final DateTimeFormatter dates = DATE.withLocale(Locale.forLanguageTag(OrionGuiLanguage.language()));
    private List<Row> rows = List.of();
    private JsonObject previous;
    private boolean active, attempted, initialized, dirty = true, searchShortcut;
    private float scale = 1;
    private long nextUpdate;
    private long clockMinute = -1;
    private float pointerX = Float.NaN, pointerY = Float.NaN;
    private int viewportWidth, viewportHeight, serverResponse;
    private String selected = "", status = "";
    private JsonObject feedback;
    private JsonArray profiles = new JsonArray();
    private OrionMinecraftAccountsScreen.Worker accountWorker;
    private boolean accountBusy, makingBackup;

    private record Row(String id, JsonObject data, Runnable play, WorldSelectionList.WorldListEntry world,
                       ServerSelectionList.OnlineServerEntry server) { }
    private record Icon(int fingerprint, String asset) { }
    private static final class BrowserPage {
        private final int id = ++nextPage;
        private final OrionLumenView view = new OrionLumenView("world-browser.html", "world_browser",
                ROOT + "fonts/paradox/ParadoxSans-Regular.ttf", OrionWorldBrowser::loadDocument, true);
        private final Map<String, Icon> icons = new HashMap<>();
        private int nextIcon;
        private String world = "", server = "";
    }

    private record WorldStats(String size, String time) {
        private static final WorldStats UNAVAILABLE = new WorldStats("—", "—");
    }

    private OrionWorldBrowser(Minecraft client, Screen screen) {
        this.client = client;
        this.screen = screen;
        single = screen instanceof SelectWorldScreen;
        page = switchingPage == null ? new BrowserPage() : switchingPage;
        switchingPage = null;
        view = page.view;
        selected = single ? page.world : page.server;
    }

    private static byte[] loadDocument() {
        try {
            String html = new String(resource("html/world-browser.html"), StandardCharsets.UTF_8);
            for (String weight : List.of("Regular", "Medium", "SemiBold", "Bold")) {
                String file = "fonts/paradox/ParadoxSans-" + weight + ".ttf";
                String alias = "assets/worlds-" + weight + ".ttf";
                byte[] font = resource(file);
                LumenNative.registerFont(bytes(alias), font);
                if (weight.equals("Regular") && motdFont == null) {
                    try { motdFont = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(font)); }
                    catch (FontFormatException ignored) { }
                }
                html = html.replace(PREVIEW_ROOT + file, alias);
            }
            for (int index = 0; index < ARTWORK.length; index++) {
                String file = index + ".jpg", alias = "assets/worlds-" + file;
                registerAsset(alias, "textures/gui/worlds/" + file);
                html = html.replace(ARTWORK[index], alias);
            }
            String badge = "textures/cosmetics/orion_badge.png";
            String panorama = "textures/gui/gleam_title_panorama.png";
            registerAsset("assets/worlds-badge.png", badge);
            registerAsset("assets/worlds-panorama.png", panorama);
            registerProfileFace();
            return bytes(html.replace(PREVIEW_ROOT + badge, "assets/worlds-badge.png")
                    .replace(PREVIEW_ROOT + panorama, "assets/worlds-panorama.png")
                    .replace("<!-- gleam-worlds-game-config -->", "<script>window.gleamWorldsGame = {};</script>")
                    .replace("</style>", new String(resource("html/worlds-lumen.css"), StandardCharsets.UTF_8) + "\n</style>")
                    .replace("<!-- gleam-worlds-game-bridge -->", "<script>\n"
                            + new String(resource("html/worlds-game.js"), StandardCharsets.UTF_8) + "\n</script>"));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void registerProfileFace() throws IOException {
        byte[] skin = resource("textures/gui/worlds/launcher-default-skin.png");
        var image = ImageIO.read(new ByteArrayInputStream(skin));
        var png = new ByteArrayOutputStream();
        ImageIO.write(image.getSubimage(8, 8, 8, 8), "png", png);
        if (LumenNative.registerImage(bytes("assets/worlds-face.png"), png.toByteArray()) < 0) {
            throw new IOException("Invalid world browser avatar");
        }
    }

    private static void registerAsset(String alias, String resource) throws IOException {
        if (LumenNative.registerImage(bytes(alias), resource(resource)) < 0) throw new IOException("Invalid image " + resource);
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    private static byte[] resource(String name) throws IOException {
        try (var stream = OrionWorldBrowser.class.getResourceAsStream(ROOT + name)) {
            if (stream == null) throw new IOException("Missing world browser resource " + name);
            return stream.readAllBytes();
        }
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof SelectWorldScreen || screen instanceof JoinMultiplayerScreen)) return;
            String selection = current != null && current.screen == screen ? current.selected : null;
            if (current != null) current.dispose();
            var menu = new OrionWorldBrowser(client, screen);
            current = menu;
            if (selection != null) menu.selected = selection;
            ScreenEvents.remove(screen).register(owner -> {
                if (current == menu) { current = null; menu.dispose(); }
            });
            ScreenMouseEvents.allowMouseClick(screen).register((owner, event) -> current != menu || !menu.pointer(event, true));
            ScreenMouseEvents.allowMouseRelease(screen).register((owner, event) -> current != menu || !menu.pointer(event, false));
            ScreenMouseEvents.allowMouseDrag(screen).register((owner, event, dx, dy) -> current != menu || !menu.active);
            ScreenMouseEvents.allowMouseScroll(screen).register((owner, x, y, dx, dy) -> current != menu || !menu.wheel(x, y, dx, dy));
            ScreenKeyboardEvents.allowKeyPress(screen).register((owner, key) -> current != menu || !menu.key(key));
            //? if >=26.1 {
            ScreenKeyboardEvents.allowCharType(screen).register((owner, event) -> current != menu || !type(event));
            //?}
        });
    }

    public static boolean active(Screen screen) { return current != null && current.screen == screen && current.active; }

    public static boolean type(CharacterEvent event) {
        var menu = current;
        if (menu == null || !menu.active || GleamMinecraft.screen(menu.client) != menu.screen
                || GleamMinecraft.overlay(menu.client) != null) return false;
        boolean openingSlash = menu.searchShortcut && event.codepoint() == '/';
        menu.searchShortcut = false;
        if (!openingSlash && event.isAllowedChatCharacter()) menu.view.text(event.codepointAsString());
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
        float ratio = Math.clamp(Math.min(window.getWidth() / 1280F, window.getHeight() / 720F), .6F, 2F);
        scale = ratio / window.getGuiScale();
        viewportWidth = (int) Math.ceil(width / scale);
        viewportHeight = (int) Math.ceil(height / scale);
        boolean resized = view.resize(viewportWidth, viewportHeight, ratio);
        dirty |= resized;
        long minute = System.currentTimeMillis() / 60_000;
        if (clockMinute != minute) {
            clockMinute = minute;
            dirty = true;
        }
        if (dirty) {
            JsonObject state = state();
            if (!state.equals(previous)) { view.update(state); previous = state; }
            dirty = false;
        }
        float x = mouseX / scale, y = mouseY / scale;
        if (resized || x != pointerX || y != pointerY) {
            view.move(x, y);
            pointerX = x;
            pointerY = y;
        }
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
                    var settings = level.getSettings();
                    boolean hasSettings = settings != null;
                    boolean hardcore = hasSettings && level.isHardcore();
                    boolean upgrade = hasSettings && level.shouldBackup();
                    String mode = !hasSettings ? t("Мир") : hardcore ? t("Хардкор") : level.getGameMode().getLongDisplayName().getString();
                    JsonObject entry = entry(id, level.getLevelName(), mode,
                            level.getInfo().getString(), "world");
                    String version = hasSettings ? level.getWorldVersionName().getString() : "";
                    entry.addProperty("version", version);
                    entry.addProperty("hardcore", hardcore);
                    entry.addProperty("cheats", hasSettings && level.hasCommands());
                    entry.addProperty("upgrade", upgrade);
                    entry.addProperty("played", level.getLastPlayed() <= 0 ? "" : dates.format(Instant.ofEpochMilli(level.getLastPlayed())));
                    entry.addProperty("diff", hasSettings ? difficulty(settings) : "");
                    entry.addProperty("notice", upgrade || level.isDisabled() ? level.getInfo().getString() : "");
                    entry.addProperty("folder", "saves/" + level.getLevelId());
                    entry.addProperty("play", level.primaryActionMessage().getString());
                    entry.addProperty("canPlay", level.primaryActionActive());
                    entry.addProperty("canEdit", level.canEdit());
                    entry.addProperty("canDelete", level.canDelete());
                    entry.addProperty("canRecreate", level.canRecreate());
                    entry.addProperty("iconUrl", Math.abs(next.size() - activeIndex) <= 6 ? worldIcon(id, level.getIcon()) : cachedIcon(id));
                    WorldStats stats = hasSettings ? worldStats(level.getLevelId(), selected.equals(id) || selected.isEmpty() && next.isEmpty())
                            : WorldStats.UNAVAILABLE;
                    entry.addProperty("size", stats.size());
                    entry.addProperty("time", stats.time());
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
                    JsonObject entry = entry(id, data.name, status, "", "server");
                    if (data.motd != null) entry.add("motdRuns", motdCache.computeIfAbsent(data.motd, OrionWorldBrowser::motdRuns));
                    entry.addProperty("address", data.ip);
                    entry.addProperty("version", data.version == null ? "" : data.version.getString());
                    entry.addProperty("pending", data.state() == ServerData.State.INITIAL || data.state() == ServerData.State.PINGING);
                    entry.addProperty("offline", data.state() == ServerData.State.UNREACHABLE);
                    entry.addProperty("incompatible", data.state() == ServerData.State.INCOMPATIBLE);
                    entry.addProperty("notice", data.state() == ServerData.State.INCOMPATIBLE ? status : "");
                    if (data.players != null) {
                        entry.addProperty("online", data.players.online());
                        entry.addProperty("max", data.players.max());
                    }
                    entry.addProperty("ping", data.state() == ServerData.State.SUCCESSFUL ? data.ping : -1);
                    entry.addProperty("canPlay", true);
                    entry.addProperty("shot", Math.floorMod(data.ip.hashCode(), ARTWORK.length));
                    entry.addProperty("iconUrl", Math.abs(next.size() - activeIndex) <= 6 ? registerIcon(id, data.getIconBytes()) : cachedIcon(id));
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
        boolean changed = rows.size() != next.size();
        for (int i = 0; !changed && i < next.size(); i++) {
            changed = !rows.get(i).data().equals(next.get(i).data());
        }
        rows = next;
        if (!rows.isEmpty() && (selected.isEmpty() || !selected.equals(CREATE) && find(selected) == null)) {
            selected = rows.getFirst().id();
            changed = true;
        }
        dirty |= changed;
    }

    private static JsonObject entry(String id, String name, String tag, String description, String kind) {
        JsonObject entry = new JsonObject();
        entry.addProperty("id", id);
        entry.addProperty("name", name);
        entry.addProperty("mode", tag);
        entry.addProperty("status", tag);
        entry.addProperty("sub", tag);
        entry.addProperty("desc", description);
        entry.addProperty("motd", description);
        entry.addProperty("kind", kind);
        entry.addProperty("shot", Math.floorMod(id.hashCode(), ARTWORK.length));
        return entry;
    }

    private static JsonArray motdRuns(Component description) {
        var runs = new JsonArray();
        description.visit((style, text) -> {
            String readable = readableMotd(text, motdFont);
            if (!readable.isEmpty()) {
                var run = new JsonObject();
                run.addProperty("text", readable);
                if (style.getColor() != null) run.addProperty("color", String.format(Locale.ROOT, "#%06x", style.getColor().getValue()));
                if (style.isBold()) run.addProperty("bold", true);
                if (style.isItalic()) run.addProperty("italic", true);
                if (style.isUnderlined()) run.addProperty("underline", true);
                if (style.isStrikethrough()) run.addProperty("strike", true);
                runs.add(run);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return runs;
    }

    private static String readableMotd(String text, Font font) {
        var result = new StringBuilder(text.length());
        Normalizer.normalize(text, Normalizer.Form.NFKC).codePoints().forEach(codepoint -> {
            int smallCap = MOTD_SMALL_CAPS.indexOf(codepoint);
            if (smallCap >= 0) result.append((char) ('A' + smallCap));
            else if (codepoint == '\n' || codepoint == ' ') result.appendCodePoint(codepoint);
            else if (codepoint == '\r') { return; }
            else if (Character.isWhitespace(codepoint)) result.append(' ');
            else if (Character.isISOControl(codepoint) || Character.getType(codepoint) == Character.FORMAT
                    || Character.getType(codepoint) == Character.PRIVATE_USE || codepoint == 0xfffd) { return; }
            else if (font == null || font.canDisplay(codepoint)) result.appendCodePoint(codepoint);
            else if (Character.isLetterOrDigit(codepoint)) result.append('…');
            else if (Character.getType(codepoint) != Character.NON_SPACING_MARK) result.append('·');
        });
        return result.toString().replaceAll("…+", "…");
    }

    private static String difficulty(net.minecraft.world.level.LevelSettings settings) {
        //? if >=26.1 {
        return settings.difficultySettings().difficulty().getDisplayName().getString();
        //?} else {
        /*return settings.difficulty().getDisplayName().getString();
        *///?}
    }

    private WorldStats worldStats(String id, boolean selected) {
        Path folder = client.getLevelSource().getLevelPath(id);
        Path stats = folder.resolve("stats").resolve(client.getUser().getProfileId() + ".json");
        var task = selected ? worldStats.computeIfAbsent(stats, path -> CompletableFuture.supplyAsync(
                () -> readWorldStats(folder, path), Util.nonCriticalIoPool())) : worldStats.get(stats);
        return task == null ? WorldStats.UNAVAILABLE : task.getNow(WorldStats.UNAVAILABLE);
    }

    private static WorldStats readWorldStats(Path folder, Path stats) {
        String size = "—", time = "—";
        try (var files = Files.find(folder, Integer.MAX_VALUE, (path, attributes) -> attributes.isRegularFile())) {
            long bytes = files.mapToLong(path -> {
                try { return Files.size(path); }
                catch (IOException failure) { return 0; }
            }).sum();
            size = bytes >= 1L << 30 ? String.format("%.1f %s", bytes / (double) (1L << 30), t("ГБ"))
                    : bytes >= 1L << 20 ? String.format("%.1f %s", bytes / (double) (1L << 20), t("МБ"))
                    : String.format("%.0f %s", bytes / 1024.0, t("КБ"));
        } catch (IOException | UncheckedIOException ignored) { }
        try (var stream = Files.newInputStream(stats)) {
            byte[] json = stream.readNBytes(1_048_577);
            if (json.length <= 1_048_576) {
                var custom = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject()
                        .getAsJsonObject("stats").getAsJsonObject("minecraft:custom");
                if (custom != null && custom.has("minecraft:play_time")) {
                    long minutes = custom.get("minecraft:play_time").getAsLong() / 1200;
                    time = minutes < 60 ? minutes + " " + t("мин") : minutes / 60 + " " + t("ч");
                }
            }
        } catch (IOException | RuntimeException ignored) { }
        return new WorldStats(size, time);
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
        var icon = page.icons.get(id);
        return icon == null ? "" : icon.asset();
    }

    private String registerIcon(String id, byte[] bytes) {
        if (bytes == null || bytes.length < 24 || bytes.length > 262144) return "";
        var header = ByteBuffer.wrap(bytes);
        if (header.getLong(0) != 0x89504E470D0A1A0AL || header.getInt(12) != 0x49484452) return "";
        int width = header.getInt(16), height = header.getInt(20);
        if (width < 1 || height < 1 || width > 256 || height > 256) return "";
        int fingerprint = Arrays.hashCode(bytes);
        var previous = page.icons.get(id);
        if (previous != null && previous.fingerprint() == fingerprint) return previous.asset();
        if (page.nextIcon >= 128) return "";
        String name = "assets/world-browser-" + page.id + "-" + ++page.nextIcon + ".png";
        if (LumenNative.registerImage(bytes(name), bytes) < 0) return "";
        page.icons.put(id, new Icon(fingerprint, name));
        return name;
    }

    private JsonObject state() {
        var config = OrionClient.getConfigOrNull();
        var state = new JsonObject();
        state.addProperty("theme", config == null ? "dark" : switch (config.getClickGuiTheme()) { case 4 -> "light"; case 5 -> "glass"; default -> "dark"; });
        String accent = config == null ? "" : config.getClickGuiAccent();
        if (accent != null && accent.matches("#[0-9a-fA-F]{6}")) {
            int rgb = Integer.parseInt(accent.substring(1), 16);
            double luminance = luminance(rgb >> 16 & 255) * .2126 + luminance(rgb >> 8 & 255) * .7152 + luminance(rgb & 255) * .0722;
            state.addProperty("accent", accent);
            state.addProperty("accentInk", luminance > .179 ? "#101219" : "#ffffff");
        }
        state.addProperty("single", single);
        state.addProperty("multiplayer", client.allowsMultiplayer());
        String account = client.getUser().getProfileId().toString().replace("-", "");
        JsonArray accounts = profiles.deepCopy();
        if (!hasProfile(account)) {
            var active = new JsonObject();
            active.addProperty("id", account);
            active.addProperty("name", client.getUser().getName());
            active.addProperty("type", client.getUser().getAccessToken().equals("0") ? "offline" : "microsoft");
            active.addProperty("face", "steve");
            accounts.add(active);
        }
        state.add("profiles", accounts);
        state.addProperty("activeAccount", account);
        state.addProperty("clock", CLOCK.format(Instant.now()));
        state.addProperty("width", viewportWidth);
        state.addProperty("height", viewportHeight);
        state.addProperty("selected", selected);
        state.addProperty("hasProtocolButton", protocolButton() != null);
        state.addProperty("serverResponse", serverResponse);
        state.addProperty("status", status);
        if (feedback != null) state.add("feedback", feedback);
        state.add("labels", labels);
        var entries = new JsonArray();
        for (Row row : rows) entries.add(row.data());
        state.add("entries", entries);
        return state;
    }

    private static double luminance(int component) {
        double value = component / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }

    private boolean hasProfile(String id) {
        for (var profile : profiles) if (profile.getAsJsonObject().get("id").getAsString().equals(id)) return true;
        return false;
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
        searchShortcut = false;
        if (event.key() == InputConstants.KEY_TAB && event.hasShiftDown()) {
            JsonObject shortcut = new JsonObject();
            shortcut.addProperty("command", "previous-field");
            view.editShortcut(shortcut);
            return true;
        }
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
        if (action.equals("search-shortcut")) {
            searchShortcut = true;
            return;
        }
        String id = message.has("id") && !message.get("id").getAsString().isEmpty() ? message.get("id").getAsString() : selected;
        Row row = find(id);
        if (action.equals("select")) {
            if (id.equals(CREATE) || row != null) {
                selected = id;
                if (row != null && row.server() != null) {
                    var list = serverList();
                    if (list != null) list.setScrollAmount(Math.max(0, list.children().indexOf(row.server()) - 2) * 36.0);
                }
                OrionClickGuiScreen.playInterfaceSound(client, "click");
            }
            return;
        }
        Runnable command = switch (action) {
            case "back" -> screen::onClose;
            case "single" -> single ? null : () -> keepPage(() -> switchMode(true));
            case "multi" -> !single || !client.allowsMultiplayer() ? null : () -> keepPage(() -> switchMode(false));
            case "account" -> () -> OrionTitleMenu.INSTANCE.openMinecraftAccounts(screen, "minecraft-accounts");
            case "account-microsoft" -> () -> OrionTitleMenu.INSTANCE.openMinecraftAccounts(screen, "add-microsoft");
            case "account-offline" -> () -> OrionTitleMenu.INSTANCE.openMinecraftAccounts(screen, "add-offline");
            case "account-list" -> this::loadAccounts;
            case "account-select" -> hasProfile(id) ? () -> selectAccount(id) : null;
            case "protocol" -> protocolButton() == null ? null : () -> press(protocolButton());
            case "play" -> row != null && (single || client.allowsMultiplayer()) ? row.play() : null;
            case "create" -> single ? () -> press(vanillaButton(screen, "selectWorld.create")) : null;
            case "refresh" -> single ? null : () -> keepPage(() -> press(vanillaButton(screen, "selectServer.refresh")));
            case "edit" -> row != null && row.world() != null ? () -> worldAction(row, "selectWorld.edit") : null;
            case "delete" -> row == null ? null : row.world() != null ? () -> worldAction(row, "selectWorld.delete")
                    : row.server() != null ? () -> deleteServer(row) : null;
            case "recreate" -> row != null && row.world() != null ? () -> worldAction(row, "selectWorld.recreate") : null;
            case "save-server" -> single || !client.allowsMultiplayer() ? null : () -> saveServer(message, row);
            case "move-left", "move-right" -> row == null || row.server() == null ? null
                    : () -> moveServer(row, action.equals("move-left") ? -1 : 1);
            case "folder" -> row != null && row.world() != null ? () -> openWorldFolder(row) : null;
            case "backup" -> row != null && row.world() != null && row.world().getLevelSummary().canEdit()
                    ? () -> backupWorld(row) : null;
            default -> null;
        };
        if (command != null) {
            if (!action.equals("account-list")) OrionClickGuiScreen.playInterfaceSound(client, "click");
            command.run();
        }
    }

    private void worldAction(Row row, String key) {
        var list = worldList();
        if (list == null || !list.children().contains(row.world())) return;
        list.setSelected(row.world());
        press(vanillaButton(screen, key));
    }

    private void switchMode(boolean worlds) {
        screen.onClose();
        Screen parent = GleamMinecraft.screen(client);
        if (parent instanceof TitleScreen) press(vanillaButton(parent, worlds ? "menu.singleplayer" : "menu.multiplayer"));
        else if (parent != null) GleamMinecraft.setScreen(client, worlds ? new SelectWorldScreen(parent) : new JoinMultiplayerScreen(parent));
    }

    private void keepPage(Runnable transition) {
        if (single) page.world = selected;
        else page.server = selected;
        switchingPage = page;
        try {
            transition.run();
        } finally {
            if (switchingPage == page) {
                switchingPage = null;
                if (current != this) view.dispose();
            }
        }
    }

    private void saveServer(JsonObject message, Row editing) {
        var multiplayer = (JoinMultiplayerScreen) screen;
        var servers = multiplayer.getServers();
        var list = serverList();
        if (list == null || !message.has("kind")) return;
        String kind = message.get("kind").getAsString();
        if (!List.of("add", "edit", "direct").contains(kind)) return;
        ServerData existing = editing == null || editing.server() == null ? null : editing.server().getServerData();
        if (kind.equals("edit") && existing == null) { serverResponse("Сервер исчез из списка"); return; }
        String address = message.has("address") ? message.get("address").getAsString().trim() : "";
        String name = message.has("name") ? message.get("name").getAsString().trim() : "";
        if (address.length() > 255 || !ServerAddress.isValidAddress(address)) { serverResponse("Введите корректный адрес сервера"); return; }
        if (kind.equals("direct")) { multiplayer.join(new ServerData(address, address, ServerData.Type.OTHER)); return; }
        if (name.isEmpty() || name.length() > 128) { serverResponse("Введите название сервера"); return; }
        if (kind.equals("edit") && existing.ip.equals(address)) {
            existing.name = name;
        } else {
            var server = new ServerData(name, address, ServerData.Type.OTHER);
            if (kind.equals("add")) {
                servers.add(server, false);
                selected = "server:" + (servers.size() - 1) + ":" + address;
            }
            else {
                server.setResourcePackStatus(existing.getResourcePackStatus());
                for (int i = 0; i < servers.size(); i++) if (servers.get(i) == existing) {
                    servers.replace(i, server);
                    selected = "server:" + i + ":" + address;
                    break;
                }
            }
        }
        finishServerChange(servers, list);
        serverResponse("");
    }

    private void deleteServer(Row row) {
        var servers = ((JoinMultiplayerScreen) screen).getServers();
        var list = serverList();
        if (list == null) return;
        servers.remove(row.server().getServerData());
        finishServerChange(servers, list);
    }

    private void moveServer(Row row, int direction) {
        var servers = ((JoinMultiplayerScreen) screen).getServers();
        var list = serverList();
        if (list == null) return;
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i) != row.server().getServerData()) continue;
            int target = i + direction;
            if (target < 0 || target >= servers.size()) return;
            servers.swap(i, target);
            selected = "server:" + target + ":" + row.server().getServerData().ip;
            finishServerChange(servers, list);
            return;
        }
    }

    private void serverResponse(String message) {
        status = message.isEmpty() ? "" : t(message);
        serverResponse++;
        dirty = true;
    }

    private void finishServerChange(net.minecraft.client.multiplayer.ServerList servers, ServerSelectionList list) {
        servers.save();
        list.updateOnlineServers(servers);
        nextUpdate = 0;
        dirty = true;
    }

    private void openWorldFolder(Row row) {
        Path folder = client.getLevelSource().getLevelPath(row.world().getLevelSummary().getLevelId());
        //? if >=26.3 {
        com.mojang.blaze3d.Blaze3D.openPath(folder);
        //?} else {
        /*Util.getPlatform().openPath(folder);
        *///?}
    }

    private void backupWorld(Row row) {
        if (makingBackup) return;
        makingBackup = true;
        showFeedback("Создаём резервную копию…");
        LevelStorageSource storage = client.getLevelSource();
        String id = row.world().getLevelSummary().getLevelId();
        CompletableFuture.runAsync(() -> {
            try (var access = storage.validateAndCreateAccess(id)) {
                access.makeWorldBackup();
            } catch (IOException | net.minecraft.world.level.validation.ContentValidationException failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        }, Util.nonCriticalIoPool()).whenComplete((unused, failure) -> client.execute(() -> {
            makingBackup = false;
            if (current == this) showFeedback(failure == null ? "Резервная копия сохранена" : "Не удалось сохранить резервную копию");
        }));
    }

    private void loadAccounts() {
        if (accountBusy) return;
        Path executable = OrionMinecraftAccountsScreen.helperExecutable();
        if (executable == null) {
            showFeedback("Запустите Minecraft из обновлённого Gleam Launcher");
            return;
        }
        if (accountWorker == null) accountWorker = new OrionMinecraftAccountsScreen.Worker(executable);
        accountBusy = true;
        accountWorker.request("list", null, null).whenComplete((reply, failure) -> client.execute(() -> {
            if (current != this) return;
            accountBusy = false;
            if (failure == null) replaceProfiles(reply);
            else showFeedback("Не удалось открыть хранилище аккаунтов");
        }));
    }

    private void replaceProfiles(JsonObject reply) {
        var list = new JsonArray();
        for (var value : reply.getAsJsonArray("accounts")) {
            var profile = value.getAsJsonObject();
            var entry = new JsonObject();
            entry.addProperty("id", profile.get("id").getAsString());
            entry.addProperty("name", profile.get("name").getAsString());
            entry.addProperty("type", profile.get("offline").getAsBoolean() ? "offline" : "microsoft");
            entry.addProperty("face", "steve");
            list.add(entry);
        }
        profiles = list;
        dirty = true;
    }

    private void selectAccount(String id) {
        if (accountBusy || accountWorker == null) return;
        accountBusy = true;
        accountWorker.request("session", id, null).whenComplete((reply, failure) -> client.execute(() -> {
            if (current != this) return;
            if (failure != null || !OrionMinecraftAccountsScreen.applySession(client, reply)) {
                accountBusy = false;
                showFeedback("Не удалось сменить профиль Minecraft");
                return;
            }
            dirty = true;
            accountWorker.request("select", id, null).whenComplete((profiles, error) -> client.execute(() -> {
                if (current != this) return;
                accountBusy = false;
                if (error == null) replaceProfiles(profiles);
                else showFeedback("Не удалось сохранить выбранный профиль");
            }));
        }));
    }

    private void showFeedback(String message) {
        int revision = feedback == null ? 1 : feedback.get("revision").getAsInt() + 1;
        feedback = new JsonObject();
        feedback.addProperty("revision", revision);
        feedback.addProperty("message", t(message));
        dirty = true;
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
        worldStats.clear();
        if (accountWorker != null) accountWorker.close();
        if (switchingPage != page) view.dispose();
    }

    private static JsonObject labels() {
        var labels = new JsonObject();
        for (String label : List.of("Одиночная игра", "Сетевая игра", "Поиск", "Аккаунты Minecraft", "Назад", "Создать мир",
                "Добавить сервер", "Играть", "Подключиться", "Изменить", "Удалить", "Пересоздать", "По адресу", "Обновить",
                "Игроки", "Пинг", "Последняя игра", "Выбрать", "Запустить", "Раздел", "Изменить сервер", "Прямое подключение",
                "Удалить сервер", "Сохранить", "Название", "Адрес", "Отмена", "Поиск по названию",
                "Сложность", "Версия", "Хардкор", "игроков", "мс", "Нет соединения", "В сети", "Сервер не ответил на запрос",
                "В локальной сети", "Миры друзей", "О мире", "Удалить мир", "Переместить левее", "Переместить правее",
                "Открыть папку", "Резервная копия", "Время в мире", "Размер на диске", "Сохранить ZIP рядом с миром",
                "Подтвердить", "Выполнить", "Закрыть", "Перейти", "Новый мир с нуля", "По адресу или из списка",
                "Здесь появятся ваши миры", "Добавьте первый сервер", "Создайте мир — он откроется сразу после генерации.",
                "Введите адрес сервера или подключитесь напрямую без сохранения.", "Новый мир", "Новый сервер",
                "Выживание, творческий или хардкор", "Сохраняется в списке серверов",
                "Выберите режим, сид и правила мира. Можно сразу включить команды и импортировать набор данных.",
                "Введите название и адрес. Для разового входа используйте «Прямое подключение».", "Создать", "Добавить",
                "Команды", "Мир из", "Повторить", "Сервер Minecraft", "Сервер не будет сохранён в списке.",
                "Сервер исчезнет из списка. Его можно добавить заново по адресу.", "Пока пусто", "Ещё",
                "Офлайн-профиль", "Добавить аккаунт Microsoft", "Добавить офлайн-профиль", "Управление профилем",
                "Мир будет удалён с диска без возможности восстановления. Сначала можно сделать резервную копию.")) {
            labels.addProperty(label, t(label));
        }
        return labels;
    }

    private static String t(String value) { return OrionGuiLanguage.translate(value); }
}
