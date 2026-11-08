package ru.orion.client.config;

import com.google.gson.JsonArray;
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
    public static final String ORIGIN = "https://gleamclient.xyz";
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
    private final LinkedHashMap<String, Profile> profiles = new LinkedHashMap<>();
    private final LinkedHashMap<String, Session> pendingProfiles = new LinkedHashMap<>();
    private long profileRevision;

    public record Profile(String id, long createdAt, boolean published, BufferedImage avatar) { }
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
                LoggerFactory.getLogger("orion-client").warn("Не удалось дождаться сохранения сессий GleamClient");
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
                        LoggerFactory.getLogger("orion-client").warn("Не удалось восстановить сессии GleamClient");
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
            throw new IllegalStateException("Could not read GleamClient sessions", exception);
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
                LoggerFactory.getLogger("orion-client").warn("Не удалось сохранить сессии GleamClient");
            } finally {
                Arrays.fill(plain, (byte) 0);
            }
        }, storage);
    }

    public String username() { return active == null ? "" : active.username(); }
    public BufferedImage avatar() { return avatar(username()); }
    public BufferedImage avatar(String username) {
        Profile profile = profiles.get(username);
        return profile == null ? null : profile.avatar();
    }
    public Profile profile() { return profile(username()); }
    public Profile profile(String username) { return profiles.get(username); }
    public long profileRevision() { return profileRevision; }
    public boolean profileLoading() { return pendingProfiles.containsKey(username()); }

    public void loadSavedProfiles() {
        for (Session session : sessions.values()) {
            if (!profiles.containsKey(session.username())) loadProfile(session);
        }
    }
    public boolean plusActive() { return active != null && active.plusExpiresAt() > Instant.now().getEpochSecond(); }
    public boolean plusActive(String username) {
        Session session = sessions.get(username);
        return session != null && session.plusExpiresAt() > Instant.now().getEpochSecond();
    }

    public String badge() { return badge(username()); }
    public String badge(String username) {
        Session session = sessions.get(username);
        if (session == null) return "";
        if ("owner".equals(session.role())) return "Owner";
        if ("admin".equals(session.role())) return "Admin";
        return session.plusExpiresAt() > Instant.now().getEpochSecond() ? "Plus" : "";
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
                    .findFirst().orElseThrow(() -> new IllegalStateException("Сайт не вернул сессию GleamClient")).getValue();
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
                if (fresh) throw new IllegalStateException("Не удалось подтвердить вход в GleamClient");
                return () -> forget(session);
            }
            var expiry = body.get("plus_expires_at");
            long plusExpiresAt = expiry.isJsonNull()
                    ? (body.get("plus_active").getAsBoolean() ? Long.MAX_VALUE : 0)
                    : expiry.getAsLong();
            JsonObject user = body.getAsJsonObject("user");
            var role = user.get("role");
            Session verified = new Session(user.get("username").getAsString(), session.cookie(),
                    body.get("csrf").getAsString(), plusExpiresAt, role == null || role.isJsonNull() ? null : role.getAsString());
            return () -> {
                boolean changed = !verified.equals(sessions.put(verified.username(), verified)) || !verified.equals(active);
                active = verified;
                loadProfile(verified);
                if (changed) persist();
            };
        });
    }

    private void forget(Session session) {
        boolean removed = sessions.remove(session.username(), session);
        if (active == session) active = null;
        if (removed) {
            pendingProfiles.remove(session.username());
            profiles.remove(session.username());
            profileRevision++;
            persist();
        }
    }

    public CompletableFuture<Runnable> setPublished(boolean published) {
        Session session = active;
        JsonObject body = new JsonObject();
        body.addProperty("published", published);
        return send("/api/profile", body, session, true).thenApply(response -> {
            requireSuccess(response.statusCode());
            JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject();
            return () -> {
                if (!session.equals(active)) return;
                pendingProfiles.remove(session.username());
                putProfile(session.username(), decodeProfile(data, avatar()));
            };
        });
    }

    public CompletableFuture<JsonArray> friends() {
        return loadFriends(active);
    }

    public CompletableFuture<JsonObject> publicProfile(String id) {
        if (!id.matches("[a-f0-9]{16}")) return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid profile ID"));
        return send("/api/profile/" + id, null, null, false).thenApply(response -> {
            if (response.statusCode() == 404) throw new IllegalStateException("Игрок скрыл свой профиль");
            if (response.statusCode() != 200) throw new IllegalStateException("Сайт GleamClient недоступен");
            return JsonParser.parseString(response.body()).getAsJsonObject();
        });
    }

    public CompletableFuture<BufferedImage> publicAvatar(String id) {
        if (!id.matches("[a-f0-9]{16}")) return CompletableFuture.completedFuture(null);
        return downloadAvatar(id, null);
    }

    public CompletableFuture<JsonObject> chat(String username, long before, String message) {
        Session session = active;
        if (session == null || !username.matches("[A-Za-z0-9_]{3,24}"))
            return CompletableFuture.failedFuture(new IllegalStateException("Чат недоступен"));
        JsonObject body = null;
        if (message != null) {
            body = new JsonObject();
            body.addProperty("body", message);
        }
        String path = "/api/friends/" + username + "/chat" + (before > 0 ? "?before=" + before : "");
        return send(path, body, session, message != null).thenApply(response -> {
            if (response.statusCode() == 403) throw new IllegalStateException("Чат доступен только друзьям");
            return friendResponse(response);
        });
    }

    public CompletableFuture<JsonArray> changeFriend(String username, String action) {
        Session session = active;
        if (session == null) return CompletableFuture.failedFuture(new IllegalStateException("Войди в аккаунт GleamClient"));
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("action", action);
        return send("/api/friends", body, session, true).thenCompose(response -> {
            friendResponse(response);
            return loadFriends(session);
        });
    }

    private static CompletableFuture<JsonArray> loadFriends(Session session) {
        return send("/api/friends", null, session, false)
                .thenApply(response -> friendResponse(response).getAsJsonArray("items"));
    }

    private static JsonObject friendResponse(HttpResponse<String> response) {
        if (response.statusCode() == 401) throw new IllegalStateException("Войди в аккаунт GleamClient");
        if (response.statusCode() == 429) throw new IllegalStateException("Слишком много попыток, попробуй позже");
        if (response.statusCode() >= 500) throw new IllegalStateException("Сайт GleamClient недоступен");
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        if (response.statusCode() == 200) return body;
        var error = body.get("error");
        throw new IllegalStateException(switch (error == null ? "" : error.getAsString()) {
            case "friend_missing" -> "Игрок с таким ником не найден";
            case "friend_self" -> "Нельзя добавить себя в друзья";
            case "friend_state" -> "Статус заявки уже изменился";
            case "friend_limit" -> "Достигнут лимит друзей или заявок";
            case "friend_unavailable" -> "Этот аккаунт заблокирован";
            default -> "Не удалось выполнить действие";
        });
    }

    private void loadProfile(Session session) {
        String name = session.username();
        if (session.equals(pendingProfiles.get(name))) return;
        pendingProfiles.put(name, session);
        BufferedImage previousAvatar = avatar(name);
        send("/api/profile", null, session, false).thenCompose(response -> {
            requireSuccess(response.statusCode());
            JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject();
            Profile profile = decodeProfile(data, null);
            if (!data.get("has_avatar").getAsBoolean()) return CompletableFuture.completedFuture(profile);
            return downloadAvatar(profile.id(), session).handle((image, failure) ->
                    new Profile(profile.id(), profile.createdAt(), profile.published(), image == null ? previousAvatar : image));
        }).whenComplete((profile, failure) -> Minecraft.getInstance().execute(() -> {
            if (pendingProfiles.get(name) != session) return;
            pendingProfiles.remove(name);
            if (failure == null && session.equals(sessions.get(name))) putProfile(name, profile);
            else profileRevision++;
        }));
    }

    private void putProfile(String name, Profile profile) {
        profiles.put(name, profile);
        profileRevision++;
    }

    private static Profile decodeProfile(JsonObject data, BufferedImage avatar) {
        String id = data.get("id").getAsString();
        if (!id.matches("[a-f0-9]{16}")) throw new IllegalStateException("Сайт GleamClient недоступен");
        return new Profile(id, data.get("created_at").getAsLong(), data.get("published").getAsBoolean(), avatar);
    }

    private static CompletableFuture<BufferedImage> downloadAvatar(String id, Session session) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(ORIGIN + "/api/profile/" + id + "/avatar"))
                .timeout(Duration.ofSeconds(4));
        if (session != null) request.header("Cookie", COOKIE + "=" + session.cookie());
        HttpRequest built = request.build();
        return HTTP.thenCompose(client -> client.sendAsync(built, HttpResponse.BodyHandlers.ofByteArray()))
                .thenApplyAsync(response -> {
                    if (response.statusCode() != 200 || response.body().length > 16_384) return null;
                    try {
                        BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
                        return image != null && image.getWidth() == 64 && image.getHeight() == 64 ? image : null;
                    } catch (IOException ignored) {
                        return null;
                    }
                });
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
            default -> "Сайт GleamClient недоступен";
        });
    }
}
