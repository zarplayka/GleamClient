//~ legacy_gui
package ru.orion.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.OrionClient;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class OrionMinecraftAccountsScreen extends Screen {
    private static final int ROW_HEIGHT = 25;
    private final OrionLumenView view = new OrionLumenView("minecraft-accounts.html", "minecraft_accounts",
            "/assets/orion-client/fonts/paradox/ParadoxSans-Regular.ttf");
    private final Screen parent;
    private final List<Profile> profiles = new ArrayList<>();
    private final Path executable = helperExecutable();
    private Worker worker;
    private Page page;
    private boolean started, busy, disposed;
    private boolean html, htmlDirty = true;
    private float htmlScale = 1F;
    private String name = "", code = "", status = "";
    private int firstProfile;
    private long pollAt = Long.MAX_VALUE;

    private enum Page { LIST, OFFLINE, MICROSOFT }
    private record Profile(String id, String name, boolean offline) { }

    OrionMinecraftAccountsScreen(Screen parent, String action) {
        super(text("Аккаунты Minecraft"));
        this.parent = parent;
        this.page = switch (action) {
            case "add-offline" -> Page.OFFLINE;
            case "add-microsoft" -> Page.MICROSOFT;
            default -> Page.LIST;
        };
    }

    @Override
    protected void init() {
        if (disposed) return;
        if (!started) html = view.ready();
        rebuild();
        if (started) return;
        started = true;
        if (executable == null) {
            status = "Запустите Minecraft из обновлённого Gleam Launcher";
            htmlDirty = true;
            return;
        }
        worker = new Worker(executable);
        if (page == Page.MICROSOFT) startMicrosoft();
        if (page == Page.LIST) loadProfiles();
    }

    private void drawControls() {
        int left = panelLeft() + 14, right = panelLeft() + panelWidth() - 14;
        int usable = right - left, top = panelTop(), bottom = panelBottom();
        if (page == Page.LIST) {
            int visible = visibleProfiles();
            firstProfile = Math.clamp(firstProfile, 0, Math.max(0, profiles.size() - visible));
            int rowY = top + 51;
            for (Profile profile : profiles.subList(firstProfile, Math.min(profiles.size(), firstProfile + visible))) {
                boolean active = profile.id().equals(minecraft.getUser().getProfileId().toString().replace("-", ""));
                String type = profile.offline() ? OrionGuiLanguage.translate("Офлайн-профиль") : "Microsoft";
                button(left, rowY, usable, Component.literal((active ? "✓  " : "") + profile.name() + " · " + type),
                        () -> selectProfile(profile.id()), !busy && !active);
                rowY += ROW_HEIGHT;
            }
            if (profiles.size() > visible) {
                button(left, bottom - 55, 23, Component.literal("↑"), () -> scrollProfiles(-1), firstProfile > 0);
                button(left + 28, bottom - 55, 23, Component.literal("↓"), () -> scrollProfiles(1), firstProfile + visible < profiles.size());
            }
            int width = (usable - 12) / 3;
            button(left, bottom - 29, width, text("Microsoft"), this::showMicrosoft, ready());
            button(left + width + 6, bottom - 29, width, text("Офлайн-профиль"),
                    this::showOffline, ready());
            button(left + (width + 6) * 2, bottom - 29, width, text("Назад"), this::onClose, true);
        } else if (page == Page.OFFLINE) {
            EditBox input = new EditBox(font, left, top + 68, usable, 22, text("Ник Minecraft"));
            input.setMaxLength(16);
            input.setValue(name);
            input.setResponder(value -> name = value);
            input.setEditable(!busy && executable != null);
            addRenderableWidget(input);
            if (!busy && executable != null) setInitialFocus(input);
            button(left, bottom - 29, usable / 2 - 3, text("Сохранить"), this::addOffline, ready());
            button(left + usable / 2 + 3, bottom - 29, usable / 2 - 3, text("Назад"), this::onClose, true);
        } else {
            int half = usable / 2 - 3;
            if (code.isEmpty()) {
                button(left, top + 80, usable, text("Запросить код"), this::startMicrosoft, ready());
            } else {
                button(left, top + 80, half, text("Открыть браузер"), () -> request("open", null, null, ignored -> {}), ready());
                button(left + half + 6, top + 80, half, text("Скопировать код"),
                        this::copyCode, true);
            }
            button(left, bottom - 29, usable, text("Назад"), this::onClose, true);
        }
    }

    private void button(int x, int y, int width, Component label, Runnable action, boolean enabled) {
        var widget = new Button.Plain(x, y, width, 21, label, button -> action.run(), narration -> narration.get()) {
            @Override
            public void playDownSound(SoundManager sounds) {
                OrionClickGuiScreen.playInterfaceSound(minecraft, "click");
            }
        };
        widget.active = enabled;
        addRenderableWidget(widget);
    }

    private boolean ready() { return executable != null && !busy; }
    private int panelWidth() { return Math.min(392, width - 22); }
    private int panelLeft() { return (width - panelWidth()) / 2; }
    private int panelTop() { return Math.max(6, (height - (page == Page.LIST ? 246 : 166)) / 2); }
    private int panelBottom() { return Math.min(height - 6, height - panelTop()); }
    private int visibleProfiles() { return Math.max(0, (panelBottom() - panelTop() - 109) / ROW_HEIGHT); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        OrionTitleMenu.drawBackdrop(graphics, width, height);
        if (html) {
            var window = minecraft.getWindow();
            float ratio = Math.clamp(Math.min(window.getWidth() / 1440F, window.getHeight() / 900F), .6F, 2F);
            htmlScale = ratio / window.getGuiScale();
            view.resize((int) Math.ceil(width / htmlScale), (int) Math.ceil(height / htmlScale), ratio);
            if (htmlDirty) {
                view.update(htmlState());
                htmlDirty = false;
            }
            view.move(mouseX / htmlScale, mouseY / htmlScale);
            view.frame();
            view.draw(graphics, 0, 0, width, height, htmlScale, htmlScale);
            return;
        }
        var config = OrionClient.getConfigOrNull();
        int theme = config == null ? 3 : config.getClickGuiTheme();
        boolean light = theme == 4;
        int panel = light ? 0xFFF3F5F9 : theme == 5 ? 0xD91B252D : 0xFF18191C;
        int line = light ? 0xFFC4CCDA : 0xFF3B3C41;
        int ink = light ? 0xFF22283A : 0xFFE5E5E7;
        int muted = light ? 0xFF546077 : 0xFFB0B1B6;
        int x = panelLeft(), y = panelTop(), right = x + panelWidth(), bottom = panelBottom();
        graphics.fill(0, 0, width, height, 0x6507090D);
        graphics.fill(x, y, right, bottom, line);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, panel);
        String heading = switch (page) {
            case LIST -> "Аккаунты Minecraft";
            case OFFLINE -> "Добавить офлайн-профиль";
            case MICROSOFT -> "Вход через Microsoft";
        };
        line(graphics, heading, x + 14, y + 13, ink);
        if (page == Page.LIST) {
            line(graphics, OrionGuiLanguage.translate("Активный профиль") + " · " + minecraft.getUser().getName(), x + 14, y + 31, muted, false);
        } else if (page == Page.OFFLINE) {
            line(graphics, "Ник Minecraft", x + 14, y + 51, muted);
        } else if (!code.isEmpty()) {
            line(graphics, "Подтвердите вход Microsoft в браузере", x + 14, y + 32, muted);
            graphics.fill(x + 14, y + 49, right - 14, y + 71, light ? 0xFFE2E7F0 : 0xFF2C3039);
            drawText(graphics, code, (width - font.width(code)) / 2, y + 56, ink);
        }
        if (!status.isBlank()) line(graphics, status, x + 14, bottom - (page == Page.LIST && profiles.size() > visibleProfiles() ? 69 : 44), muted);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void line(GuiGraphicsExtractor graphics, String label, int x, int y, int color) {
        line(graphics, label, x, y, color, true);
    }

    private void line(GuiGraphicsExtractor graphics, String label, int x, int y, int color, boolean translate) {
        String value = translate ? OrionGuiLanguage.translate(label) : label;
        drawText(graphics, font.plainSubstrByWidth(value, panelWidth() - 28), x, y, color);
    }

    private void drawText(GuiGraphicsExtractor graphics, String value, int x, int y, int color) {
        //? if >=26.1 {
        graphics.text(font, value, x, y, color, false);
        //?} else {
        /*graphics.drawString(font, value, x, y, color, false);
        *///?}
    }

    private JsonObject htmlState() {
        JsonObject state = new JsonObject();
        var config = OrionClient.getConfigOrNull();
        int theme = config == null ? 3 : config.getClickGuiTheme();
        state.addProperty("theme", switch (theme) { case 4 -> "light"; case 5 -> "glass"; default -> "dark"; });
        String accent = config == null ? "" : config.getClickGuiAccent();
        if (accent != null && accent.matches("#[0-9a-fA-F]{6}")) {
            int color = Integer.parseInt(accent.substring(1), 16);
            double luminance = luminance(color >> 16 & 255) * .2126 + luminance(color >> 8 & 255) * .7152 + luminance(color & 255) * .0722;
            state.addProperty("accent", accent);
            state.addProperty("accentInk", luminance > .179 ? "#101219" : "#ffffff");
        } else {
            state.addProperty("accent", "");
            state.addProperty("accentInk", "");
        }
        state.addProperty("page", page.name().toLowerCase(Locale.ROOT));
        state.addProperty("heading", translated(switch (page) {
            case LIST -> "Аккаунты Minecraft";
            case OFFLINE -> "Добавить офлайн-профиль";
            case MICROSOFT -> "Вход через Microsoft";
        }));
        User user = minecraft.getUser();
        state.addProperty("caption", page == Page.LIST ? translated("Активный профиль") + " · " + user.getName() : "");
        state.addProperty("active", user.getProfileId().toString().replace("-", ""));
        state.addProperty("name", name);
        state.addProperty("code", code);
        state.addProperty("status", status.isBlank() ? "" : translated(status));
        state.addProperty("busy", busy);
        state.addProperty("ready", ready());
        state.addProperty("offlineType", translated("Офлайн-профиль"));
        JsonArray list = new JsonArray();
        for (Profile profile : profiles) {
            var entry = new JsonObject();
            entry.addProperty("id", profile.id());
            entry.addProperty("name", profile.name());
            entry.addProperty("offline", profile.offline());
            list.add(entry);
        }
        state.add("profiles", list);
        JsonObject labels = new JsonObject();
        labels.addProperty("nickname-label", translated("Ник Minecraft"));
        labels.addProperty("offline", translated("Добавить офлайн-профиль"));
        labels.addProperty("save", translated("Сохранить"));
        labels.addProperty("instructions", translated("Подтвердите вход Microsoft в браузере"));
        labels.addProperty("open", translated("Открыть браузер"));
        labels.addProperty("copy", translated("Скопировать код"));
        labels.addProperty("retry", translated("Запросить код"));
        labels.addProperty("back", translated("Назад"));
        state.add("labels", labels);
        return state;
    }

    private static double luminance(int channel) {
        double value = channel / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!html) return super.mouseClicked(event, doubleClick);
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            view.move((float) event.x() / htmlScale, (float) event.y() / htmlScale);
            view.press((float) event.x() / htmlScale, (float) event.y() / htmlScale);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!html) return super.mouseReleased(event);
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            view.release((float) event.x() / htmlScale, (float) event.y() / htmlScale);
            receiveHtml();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!html) return super.mouseScrolled(x, y, horizontal, vertical);
        return view.wheel((float) x / htmlScale, (float) y / htmlScale, (float) horizontal, (float) vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!html || event.key() == InputConstants.KEY_ESCAPE) return super.keyPressed(event);
        if (page == Page.OFFLINE && event.hasControlDownWithQuirk() && !event.hasAltDown()) {
            String shortcut = switch (event.key()) {
                case InputConstants.KEY_A -> "select";
                case InputConstants.KEY_V -> "paste";
                default -> "";
            };
            if (!shortcut.isEmpty()) {
                JsonObject payload = new JsonObject();
                payload.addProperty("command", shortcut);
                if (shortcut.equals("paste")) {
                    String clipboard = minecraft.keyboardHandler.getClipboard();
                    payload.addProperty("text", clipboard.substring(0, Math.min(clipboard.length(), 16)));
                }
                view.editShortcut(payload);
                return true;
            }
        }
        String key = switch (event.key()) {
            case InputConstants.KEY_TAB -> "Tab";
            case InputConstants.KEY_UP -> "ArrowUp";
            case InputConstants.KEY_DOWN -> "ArrowDown";
            case InputConstants.KEY_LEFT -> "ArrowLeft";
            case InputConstants.KEY_RIGHT -> "ArrowRight";
            case InputConstants.KEY_HOME -> "Home";
            case InputConstants.KEY_END -> "End";
            case InputConstants.KEY_BACKSPACE -> "Backspace";
            case InputConstants.KEY_DELETE -> "Delete";
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> "Enter";
            case InputConstants.KEY_SPACE -> " ";
            default -> "";
        };
        if (!key.isEmpty()) {
            int modifiers = (event.hasShiftDown() ? 1 : 0) | (event.hasControlDownWithQuirk() ? 2 : 0);
            view.key(key, key, modifiers, true);
            view.key(key, key, modifiers, false);
            receiveHtml();
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!html) return super.charTyped(event);
        if (page == Page.OFFLINE && event.isAllowedChatCharacter()) view.text(event.codepointAsString());
        return true;
    }

    private void receiveHtml() {
        byte[] bytes = view.messages();
        if (bytes == null || bytes.length == 0 || bytes.length > 8192) return;
        for (String line : new String(bytes, StandardCharsets.UTF_8).lines().toList()) {
            try {
                JsonElement item = JsonParser.parseString(line);
                if (item.isJsonArray() && !item.getAsJsonArray().isEmpty()) item = item.getAsJsonArray().get(0);
                if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) item = JsonParser.parseString(item.getAsString());
                if (item.isJsonObject() && handleHtmlAction(item.getAsJsonObject())) return;
            } catch (RuntimeException invalid) {
                // Messages outside the account page contract are ignored.
            }
        }
    }

    private boolean handleHtmlAction(JsonObject message) {
        if (disposed || !message.has("action") || !message.has("value")) return false;
        String action = message.get("action").getAsString();
        String value = message.get("value").getAsString();
        Runnable command = switch (action) {
            case "back" -> this::onClose;
            case "offline" -> page == Page.LIST && ready() ? this::showOffline : null;
            case "microsoft" -> page == Page.LIST && ready() ? this::showMicrosoft : null;
            case "retry" -> page == Page.MICROSOFT && code.isEmpty() && ready() ? this::startMicrosoft : null;
            case "open" -> page == Page.MICROSOFT && !code.isEmpty() && ready() ? () -> request("open", null, null, ignored -> {}) : null;
            case "copy" -> page == Page.MICROSOFT && !code.isEmpty() ? this::copyCode : null;
            case "save" -> page == Page.OFFLINE && ready() && value.length() <= 16 ? () -> { name = value; addOffline(); } : null;
            case "select" -> page == Page.LIST && ready() && profiles.stream().anyMatch(p -> p.id().equals(value)) ? () -> selectProfile(value) : null;
            default -> null;
        };
        if (command == null) return false;
        OrionClickGuiScreen.playInterfaceSound(minecraft, "click");
        command.run();
        return true;
    }

    @Override
    public void tick() {
        if (html) receiveHtml();
        if (disposed) return;
        if (page == Page.MICROSOFT && ready() && System.nanoTime() >= pollAt) {
            pollAt = Long.MAX_VALUE;
            request("poll", null, null, reply -> {
                if (reply.has("pending")) { schedulePoll(reply.get("interval").getAsInt()); return; }
                useNewProfile(reply);
            });
        }
    }

    private void rebuild() {
        htmlDirty = true;
        if (!html) { clearWidgets(); drawControls(); }
    }
    private void scrollProfiles(int direction) { firstProfile += direction; rebuild(); }
    private void loadProfiles() { request("list", null, null, this::replaceProfiles); }

    private void showMicrosoft() { page = Page.MICROSOFT; startMicrosoft(); }
    private void showOffline() { page = Page.OFFLINE; status = ""; rebuild(); }

    private void copyCode() {
        minecraft.keyboardHandler.setClipboard(code);
        status = "Код скопирован";
        htmlDirty = true;
    }

    private void startMicrosoft() {
        code = "";
        status = "Запрашиваем код входа…";
        request("start", null, null, reply -> {
            code = reply.get("code").getAsString();
            status = "Ожидаем подтверждения…";
            schedulePoll(reply.get("interval").getAsInt());
        });
    }

    private void schedulePoll(int seconds) { pollAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.clamp(seconds, 1, 60)); }

    private void addOffline() {
        if (!name.trim().matches("[a-zA-Z0-9_]{3,16}")) {
            status = "Ник должен содержать 3–16 латинских букв, цифр или _";
            htmlDirty = true;
            return;
        }
        request("offline", null, name.trim(), this::useNewProfile);
    }

    private void replaceProfiles(JsonObject reply) {
        profiles.clear();
        for (var value : reply.getAsJsonArray("accounts")) {
            JsonObject entry = value.getAsJsonObject();
            profiles.add(new Profile(entry.get("id").getAsString(), entry.get("name").getAsString(), entry.get("offline").getAsBoolean()));
        }
        status = profiles.isEmpty() ? "Нет сохранённых профилей" : "";
    }

    private void useNewProfile(JsonObject reply) {
        replaceProfiles(reply);
        page = Page.LIST;
        code = "";
        selectProfile(reply.get("selected").getAsString());
    }

    private void selectProfile(String id) {
        status = "Переключаем профиль…";
        request("session", id, null, reply -> {
            if (!applySession(minecraft, reply)) {
                status = "Профиль можно сменить только до входа в мир";
                return;
            }
            request("select", id, null, this::replaceProfiles);
        });
    }

    static boolean applySession(Minecraft client, JsonObject reply) {
        var uuid = UUID.fromString(reply.get("uuid").getAsString().replaceFirst(
                "^([0-9a-f]{8})([0-9a-f]{4})([0-9a-f]{4})([0-9a-f]{4})([0-9a-f]{12})$", "$1-$2-$3-$4-$5"));
        String token = reply.get("token").getAsString();
        User user = new User(reply.get("name").getAsString(), uuid, token, Optional.empty(), Optional.empty());
        return ((OrionMinecraftSession) (Object) client).gleam$useSession(user, token.equals("0"));
    }

    private void request(String action, String id, String name, Consumer<JsonObject> success) {
        if (!ready() || worker == null) return;
        busy = true;
        rebuild();
        Worker source = worker;
        Minecraft client = minecraft;
        source.request(action, id, name).whenComplete((reply, failure) -> client.execute(() -> {
            if (disposed || worker != source || GleamMinecraft.screen(client) != this) return;
            busy = false;
            if (failure == null) {
                try { success.accept(reply); }
                catch (RuntimeException malformed) { status = "Не удалось обработать данные профиля"; }
            } else {
                String error = failure.getCause() == null ? failure.getMessage() : failure.getCause().getMessage();
                status = switch (error == null ? "" : error.split(":", 2)[0]) {
                    case "mc_reauth", "mc_expired" -> "Войдите в Microsoft заново";
                    case "mc_denied", "mc_cancelled" -> "Вход Microsoft не подтверждён";
                    case "mc_network", "mc_timeout" -> "Нет ответа от службы Microsoft";
                    case "mc_offline_name" -> "Некорректный ник Minecraft";
                    case "browser_open" -> "Не удалось открыть браузер";
                    case "storage" -> "Не удалось открыть хранилище аккаунтов";
                    default -> "Не удалось выполнить действие с аккаунтом";
                };
                if (action.equals("start") || action.equals("poll")) code = "";
            }
            if (worker == source) rebuild();
        }));
    }

    @Override
    public void onClose() {
        if (page == Page.LIST) { GleamMinecraft.setScreen(minecraft, parent); return; }
        if (worker != null) worker.close();
        worker = executable == null ? null : new Worker(executable);
        page = Page.LIST;
        busy = false;
        code = "";
        pollAt = Long.MAX_VALUE;
        if (worker == null) rebuild(); else loadProfiles();
    }

    @Override
    public void removed() {
        disposed = true;
        if (worker != null) worker.close();
        view.dispose();
        super.removed();
    }

    private static String translated(String value) { return OrionGuiLanguage.translate(value); }
    private static Component text(String value) { return Component.literal(translated(value)); }

    static Path helperExecutable() {
        String value = System.getenv("GLEAM_MC_ACCOUNT_HELPER");
        if (value == null || System.getenv("GLEAM_MC_ACCOUNT_DATA") == null) return null;
        try {
            Path path = Path.of(value);
            return path.isAbsolute() && Files.isRegularFile(path) ? path : null;
        } catch (RuntimeException invalid) { return null; }
    }

    /** The launcher worker is private to this screen. Only selected sessions are returned to Java. */
    static final class Worker {
        private final Path executable;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
        private Process process;
        private BufferedReader input;
        private BufferedWriter output;
        private boolean closed;

        Worker(Path executable) { this.executable = executable; }

        CompletableFuture<JsonObject> request(String action, String id, String name) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    connect();
                    JsonObject request = new JsonObject();
                    request.addProperty("action", action);
                    if (id != null) request.addProperty("id", id);
                    if (name != null) request.addProperty("name", name);
                    output.write(request.toString()); output.newLine(); output.flush();
                    String line = input.readLine();
                    if (line == null || line.length() > 1_048_576) throw new IOException("mc_service");
                    JsonObject result = JsonParser.parseString(line).getAsJsonObject();
                    if (result.has("error")) throw new IOException(result.get("error").getAsString());
                    return result.get("ok").isJsonObject() ? result.getAsJsonObject("ok") : new JsonObject();
                } catch (IOException failure) {
                    throw new java.util.concurrent.CompletionException(failure);
                }
            }, executor);
        }

        private synchronized void connect() throws IOException {
            if (closed) throw new IOException("mc_cancelled");
            if (process != null && process.isAlive()) return;
            process = new ProcessBuilder(executable.toString(), "--minecraft-account-worker")
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            input = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            output = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        }

        synchronized void close() {
            closed = true;
            if (process != null) process.destroyForcibly();
            executor.shutdownNow();
        }
    }
}
