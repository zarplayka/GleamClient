//~ legacy_gui
package ru.orion.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class OrionMinecraftAccountsScreen extends Screen {
    private static final int ROW_HEIGHT = 25;
    private final Screen parent;
    private final List<Profile> profiles = new ArrayList<>();
    private final Path executable = helperExecutable();
    private Worker worker;
    private Page page;
    private boolean started, busy, disposed;
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
        drawControls();
        if (started) return;
        started = true;
        if (executable == null) {
            status = "Запустите Minecraft из обновлённого Gleam Launcher";
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
            button(left, bottom - 29, width, text("Microsoft"), () -> { page = Page.MICROSOFT; startMicrosoft(); }, ready());
            button(left + width + 6, bottom - 29, width, text("Офлайн-профиль"),
                    () -> { page = Page.OFFLINE; status = ""; rebuild(); }, ready());
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
                        () -> { minecraft.keyboardHandler.setClipboard(code); status = "Код скопирован"; }, true);
            }
            button(left, bottom - 29, usable, text("Назад"), this::onClose, true);
        }
    }

    private void button(int x, int y, int width, Component label, Runnable action, boolean enabled) {
        var widget = Button.builder(label, button -> action.run()).bounds(x, y, width, 21).build();
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

    @Override
    public void tick() {
        if (page == Page.MICROSOFT && ready() && System.nanoTime() >= pollAt) {
            pollAt = Long.MAX_VALUE;
            request("poll", null, null, reply -> {
                if (reply.has("pending")) { schedulePoll(reply.get("interval").getAsInt()); return; }
                useNewProfile(reply);
            });
        }
    }

    private void rebuild() { clearWidgets(); drawControls(); }
    private void scrollProfiles(int direction) { firstProfile += direction; rebuild(); }
    private void loadProfiles() { request("list", null, null, this::replaceProfiles); }

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
        if (!name.trim().matches("[a-zA-Z0-9_]{3,16}")) { status = "Ник должен содержать 3–16 латинских букв, цифр или _"; return; }
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
            var uuid = UUID.fromString(reply.get("uuid").getAsString().replaceFirst(
                    "^([0-9a-f]{8})([0-9a-f]{4})([0-9a-f]{4})([0-9a-f]{4})([0-9a-f]{12})$", "$1-$2-$3-$4-$5"));
            String token = reply.get("token").getAsString();
            User user = new User(reply.get("name").getAsString(), uuid, token, Optional.empty(), Optional.empty());
            if (!((OrionMinecraftSession) (Object) minecraft).gleam$useSession(user, token.equals("0"))) {
                status = "Профиль можно сменить только до входа в мир";
                return;
            }
            request("select", id, null, this::replaceProfiles);
        });
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
        super.removed();
    }

    private static Component text(String value) { return Component.literal(OrionGuiLanguage.translate(value)); }

    private static Path helperExecutable() {
        String value = System.getenv("GLEAM_MC_ACCOUNT_HELPER");
        if (value == null || System.getenv("GLEAM_MC_ACCOUNT_DATA") == null) return null;
        try {
            Path path = Path.of(value);
            return path.isAbsolute() && Files.isRegularFile(path) ? path : null;
        } catch (RuntimeException invalid) { return null; }
    }

    /** The launcher worker is private to this screen. Only selected sessions are returned to Java. */
    private static final class Worker {
        private final Path executable;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
        private Process process;
        private BufferedReader input;
        private BufferedWriter output;
        private boolean closed;

        private Worker(Path executable) { this.executable = executable; }

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
