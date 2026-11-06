package ru.orion.client.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Crypt32Util;
import com.sun.jna.platform.win32.WinCrypt;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Сессии сайта Orion, на Windows сохранённые средствами защиты профиля ОС. */
public final class OrionAccounts {
    public static final String ORIGIN = "https://orion.51-250-32-198.sslip.io";
    private static final String COOKIE = "__Host-orion_session";
    private static final CompletableFuture<HttpClient> HTTP = CompletableFuture.supplyAsync(
            () -> HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    public static final OrionAccounts INSTANCE = new OrionAccounts();

    // Список и выбранная сессия меняются только в игровом потоке.
    private final LinkedHashMap<String, Session> sessions = new LinkedHashMap<>();
    private final ExecutorService storage = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("orion-account-storage").factory());
    private CompletableFuture<Void> lastWrite = CompletableFuture.completedFuture(null);
    private Path sessionFile;
    private Session active;
    private String avatarPath;
    private BufferedImage avatar;
    private boolean avatarLoading;
    private long avatarRevision;
    private boolean busy;

    private OrionAccounts() { }

    private record Session(String username, String cookie, String csrf, long plusExpiresAt, String role) { }
    private record StoredSessions(List<Session> sessions, String active) { }

    public void register() {
        if (!Platform.isWindows()) return;
        sessionFile = FabricLoader.getInstance().getConfigDir().resolve("orion-account-sessions.json");
        ClientLifecycleEvents.CLIENT_STARTED.register(this::restore);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            try {
                lastWrite.get(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                LoggerFactory.getLogger("orion-client").warn("Не удалось дождаться сохранения сессий Orion");
            } finally {
                storage.shutdown();
            }
        });
    }

    private void restore(Minecraft client) {
        busy = true;
        CompletableFuture.supplyAsync(this::readSessions, storage).whenComplete((stored, failure) ->
                client.execute(() -> {
                    busy = false;
                    if (failure != null) {
                        LoggerFactory.getLogger("orion-client").warn("Не удалось восстановить сессии Orion");
                        return;
                    }
                    for (Session session : stored.sessions()) sessions.put(session.username(), session);
                    active = sessions.get(stored.active());
                    if (active != null) refresh().thenAccept(update -> client.execute(update));
                }));
    }

    private StoredSessions readSessions() {
        if (!Files.isRegularFile(sessionFile)) return new StoredSessions(List.of(), "");
        try {
            if (Files.size(sessionFile) > 256_000L) throw new IllegalStateException("Session file too large");
            byte[] encoded = Base64.getDecoder().decode(JsonParser.parseString(Files.readString(sessionFile)).getAsString());
            byte[] plain = Crypt32Util.cryptUnprotectData(encoded, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN);
            try {
                var stored = OrionConfigManager.GSON.fromJson(new String(plain, StandardCharsets.UTF_8), StoredSessions.class);
                List<Session> valid = stored.sessions().stream().filter(Objects::nonNull)
                        .filter(session -> session.username() != null && !session.username().isBlank()
                                && session.cookie() != null && session.cookie().matches("[A-Za-z0-9_-]{40,100}")
                                && session.csrf() != null && session.csrf().matches("[A-Za-z0-9_-]{20,100}"))
                        .toList();
                return new StoredSessions(valid, stored.active());
            } finally {
                Arrays.fill(plain, (byte) 0);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read Orion sessions", exception);
        }
    }

    private void persist() {
        if (sessionFile == null || storage.isShutdown()) return;
        StoredSessions snapshot = new StoredSessions(List.copyOf(sessions.values()), username());
        lastWrite = CompletableFuture.runAsync(() -> {
            byte[] plain = OrionConfigManager.GSON.toJson(snapshot).getBytes(StandardCharsets.UTF_8);
            try {
                byte[] encrypted = Crypt32Util.cryptProtectData(plain, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN);
                OrionConfigManager.writeAtomically(sessionFile, new JsonPrimitive(Base64.getEncoder().encodeToString(encrypted)));
            } catch (Exception exception) {
                LoggerFactory.getLogger("orion-client").warn("Не удалось сохранить сессии Orion");
            } finally {
                Arrays.fill(plain, (byte) 0);
            }
        }, storage);
    }

    public String username() { return active == null ? "" : active.username(); }
    public BufferedImage avatar() { return avatar; }
    public boolean plusActive() { return active != null && active.plusExpiresAt() > Instant.now().getEpochSecond(); }
    public String badge() {
        if (active == null) return "";
        if ("owner".equals(active.role())) return "Owner";
        if ("admin".equals(active.role())) return "Admin";
        return plusActive() ? "Plus" : "";
    }
    public String sessionCookieHeader() { return active == null ? "" : COOKIE + "=" + active.cookie(); }
    public List<String> usernames() { return List.copyOf(sessions.keySet()); }
    public boolean busy() { return busy; }
    public void setBusy(boolean busy) { this.busy = busy; }

    public CompletableFuture<Runnable> login(String username, String password) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        Session previous = sessions.values().stream().filter(session -> session.username().equalsIgnoreCase(username))
                .findFirst().orElse(null);
        return send("/api/login", body, previous, false).thenCompose(response -> {
            requireSuccess(response.statusCode());
            String cookie = response.headers().allValues("Set-Cookie").stream()
                    .flatMap(header -> HttpCookie.parse(header).stream())
                    .filter(value -> COOKIE.equals(value.getName()) && value.getValue().matches("[A-Za-z0-9_-]{40,100}"))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Сайт не вернул сессию Orion")).getValue();
            return verify(new Session(username, cookie, "", 0, null), true);
        });
    }

    public CompletableFuture<Runnable> refresh() {
        Session session = active;
        return verify(session, false).thenApply(update -> () -> {
            if (active == session && !busy) update.run();
        });
    }

    public CompletableFuture<Runnable> select(String username) {
        return verify(sessions.get(username), false);
    }

    public CompletableFuture<Runnable> logout() {
        Session session = active;
        return send("/api/logout", new JsonObject(), session, true).thenApply(response -> {
            if (response.statusCode() != 401) requireSuccess(response.statusCode());
            return () -> forget(session);
        });
    }

    private CompletableFuture<Runnable> verify(Session session, boolean fresh) {
        return send("/api/me", null, session, false).thenApply(response -> {
            requireSuccess(response.statusCode());
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            if (body.get("user").isJsonNull()) {
                if (fresh) throw new IllegalStateException("Не удалось подтвердить вход в Orion");
                return () -> forget(session);
            }
            var expiry = body.get("plus_expires_at");
            long plusExpiresAt = expiry.isJsonNull()
                    ? (body.get("plus_active").getAsBoolean() ? Long.MAX_VALUE : 0)
                    : expiry.getAsLong();
            JsonObject user = body.getAsJsonObject("user");
            var role = user.get("role");
            var avatarUrl = body.get("avatar_url");
            String path = avatarUrl == null || avatarUrl.isJsonNull() ? null : avatarUrl.getAsString();
            Session verified = new Session(user.get("username").getAsString(), session.cookie(),
                    body.get("csrf").getAsString(), plusExpiresAt, role == null || role.isJsonNull() ? null : role.getAsString());
            return () -> {
                boolean changed = !verified.equals(sessions.put(verified.username(), verified)) || !verified.equals(active);
                active = verified;
                updateAvatar(path, verified);
                if (changed) persist();
            };
        });
    }

    private void forget(Session session) {
        boolean removed = sessions.remove(session.username(), session);
        if (active == session) {
            active = null;
            updateAvatar(null, null);
        }
        if (removed) persist();
    }

    private void updateAvatar(String path, Session session) {
        if (path != null && !path.matches("/api/profile/[a-f0-9]{16}/avatar\\?v=[a-f0-9]{16}")) path = null;
        if (!Objects.equals(path, avatarPath)) {
            avatarPath = path;
            avatar = null;
            avatarLoading = false;
            avatarRevision++;
        }
        if (path == null || avatarLoading || avatar != null) return;
        avatarLoading = true;
        long revision = avatarRevision;
        HttpRequest request = HttpRequest.newBuilder(URI.create(ORIGIN + path))
                .timeout(Duration.ofSeconds(4)).header("Cookie", COOKIE + "=" + session.cookie()).build();
        HTTP.thenCompose(client -> client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()))
                .thenApplyAsync(response -> {
                    if (response.statusCode() != 200 || response.body().length > 16_384) return null;
                    try {
                        BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
                        return image != null && image.getWidth() == 64 && image.getHeight() == 64 ? image : null;
                    } catch (IOException ignored) {
                        return null;
                    }
                })
                .whenComplete((image, failure) -> Minecraft.getInstance().execute(() -> {
                    if (avatarRevision != revision) return;
                    avatarLoading = false;
                    if (failure == null) avatar = image;
                }));
    }

    private static CompletableFuture<HttpResponse<String>> send(String path, JsonObject body, Session session, boolean csrf) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(ORIGIN + path)).timeout(Duration.ofSeconds(8));
        if (session != null) request.header("Cookie", COOKIE + "=" + session.cookie());
        if (csrf) request.header("X-CSRF-Token", session.csrf());
        if (body != null) request.header("Origin", ORIGIN).header("X-Orion-Request", "1")
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        HttpRequest built = request.build();
        return HTTP.thenCompose(client -> client.sendAsync(built, HttpResponse.BodyHandlers.ofString()));
    }

    private static void requireSuccess(int status) {
        if (status == 200) return;
        throw new IllegalStateException(switch (status) {
            case 401 -> "Неверный логин или пароль";
            case 429 -> "Слишком много попыток, попробуй позже";
            default -> "Сайт Orion недоступен";
        });
    }
}
