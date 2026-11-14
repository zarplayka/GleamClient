package ru.orion.client.config;

import ru.orion.client.GleamMinecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Crypt32Util;
import com.sun.jna.platform.win32.WinCrypt;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import ru.orion.client.OrionClient;
import ru.orion.client.command.OrionFakeBanCommand;
import ru.orion.client.effect.OrionBadgePresence;
import ru.orion.client.gui.OrionClickGuiScreen;
import ru.orion.client.gui.OrionChatNotifications;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private String cosmeticOwner = "", cosmeticCookie = "";
    private JsonObject cosmeticBaseline, cosmeticSeed;
    private long nextCosmeticPoll, nextCosmeticSample, cosmeticRetry;
    private boolean cosmeticLoading;
    private final LinkedHashMap<String, Profile> profiles = new LinkedHashMap<>();
    private final LinkedHashMap<String, Session> pendingProfiles = new LinkedHashMap<>();
    private long profileRevision;
    private Session registeredChatSession, notificationSession;
    private long lastNotificationMessage;
    private CompletableFuture<OrionChatCrypto.Identity> registeredChatIdentity;
    private long notificationCursor = -1, nextNotificationPoll, nextChatRegistration;
    private boolean notificationLoading;
    private FakePunishmentFeed fakePunishmentFeed;
    private final LinkedHashMap<String, Integer> unreadChats = new LinkedHashMap<>();
    // Кэш читается только в очереди хранения и сбрасывается при смене пары.
    private String decodedChatPair = "";
    private final LinkedHashMap<String, String> decodedMessages = new LinkedHashMap<>(128, .75f, true);

    public record Profile(String id, long createdAt, boolean published, BufferedImage avatar) { }
    public record ChatReply(long id, boolean own, String body) { }
    public record ChatTransfer(String code, long expiresAt) { }
    private Session transferSession;
    private ChatTransfer chatTransfer;
    public static final class ChatRecoveryRequired extends IllegalStateException {
        private final boolean backupAvailable;

        private ChatRecoveryRequired(boolean backupAvailable) {
            super(backupAvailable ? "Восстанови ключ чата" : "Сохрани ключ чата там, где открывается переписка");
            this.backupAvailable = backupAvailable;
        }

        public boolean backupAvailable() { return backupAvailable; }
    }
    private boolean busy;

    private OrionAccounts() { }

    private record Session(String username, String cookie, String csrf, long plusExpiresAt, String role) { }
    private record StoredSessions(List<Session> sessions, String active) { }
    private record StartupSessions(StoredSessions saved, Session launcher) { }

    private static final class FakePunishmentFeed {
        private final Session session;
        private final ClientPacketListener connection;
        private final String scope;
        private String cursor = "";
        private long nextPoll;
        private boolean loading;

        private FakePunishmentFeed(Session session, ClientPacketListener connection, String scope) {
            this.session = session;
            this.connection = connection;
            this.scope = scope;
        }
    }

    public void register() {
        if (!Platform.isWindows()) return;
        sessionFile = FabricLoader.getInstance().getConfigDir().resolve("orion-account-sessions.json");
        ClientLifecycleEvents.CLIENT_STARTED.register(this::restore);
        ClientTickEvents.END_CLIENT_TICK.register(this::pollChatNotifications);
        ClientTickEvents.END_CLIENT_TICK.register(this::pollFakePunishments);
        ClientTickEvents.END_CLIENT_TICK.register(this::syncCosmetics);
        OrionChatNotifications.register();
        ru.orion.client.gui.OrionFriendWorlds.register();
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
        CompletableFuture.supplyAsync(() -> {
            Session launcher = readLauncherSession();
            StoredSessions saved;
            try {
                saved = readSessions();
            } catch (IllegalStateException exception) {
                LoggerFactory.getLogger("orion-client").warn("Не удалось восстановить сессии GleamClient");
                saved = new StoredSessions(List.of(), "");
            }
            return new StartupSessions(saved, launcher);
        }, storage).whenComplete((startup, failure) ->
                client.execute(() -> {
                    if (failure != null) {
                        busy = false;
                        LoggerFactory.getLogger("orion-client").warn("Не удалось восстановить сессии GleamClient");
                        return;
                    }
                    StoredSessions stored = startup.saved();
                    for (Session session : stored.sessions()) sessions.put(session.username(), session);
                    active = sessions.get(stored.active());
                    if (startup.launcher() == null) {
                        finishRestore(client);
                        return;
                    }
                    verify(startup.launcher(), true).whenComplete((update, error) -> client.execute(() -> {
                        if (error == null) {
                            busy = false;
                            update.run();
                        } else {
                            LoggerFactory.getLogger("orion-client").warn("Не удалось подтвердить сессию Gleam из лаунчера");
                            finishRestore(client);
                        }
                    }));
                }));
    }

    private void finishRestore(Minecraft client) {
        busy = false;
        if (active != null) refresh().thenAccept(update -> client.execute(update));
    }

    private static Session readLauncherSession() {
        String handoff = System.getenv("GLEAM_LAUNCH_SESSION");
        if (handoff == null || handoff.isBlank()) return null;
        try {
            Path path = Path.of(handoff).toAbsolutePath().normalize();
            Path directory = path.getParent();
            Path gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
            if (directory == null || !gameDir.equals(directory.getParent())
                    || !directory.getFileName().toString().startsWith(".gleam-login-")
                    || !Path.of("session.bin").equals(path.getFileName())
                    || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return null;
            try {
                byte[] encrypted;
                try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                    encrypted = input.readNBytes(4097);
                }
                if (encrypted.length == 0 || encrypted.length > 4096) return null;
                byte[] plain = Crypt32Util.cryptUnprotectData(encrypted, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN);
                try {
                    String cookie = new String(plain, StandardCharsets.US_ASCII);
                    return cookie.matches("[A-Za-z0-9_-]{43}") ? new Session("", cookie, "", 0, null) : null;
                } finally {
                    Arrays.fill(plain, (byte) 0);
                }
            } finally {
                Files.deleteIfExists(path);
            }
        } catch (Exception exception) {
            LoggerFactory.getLogger("orion-client").warn("Не удалось прочитать сессию Gleam из лаунчера");
            return null;
        }
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

    public static String fakePunishmentScope(Minecraft client) {
        var server = client.getCurrentServer();
        if (server == null || server.ip == null || server.ip.isBlank() || client.hasSingleplayerServer()) return "";
        String address = server.ip.strip().toLowerCase(Locale.ROOT);
        if (address.endsWith(":25565")) address = address.substring(0, address.length() - 6);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(address.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public CompletableFuture<Boolean> publishFakePunishment(JsonObject event) {
        Session session = active;
        if (session == null || !plusActive()) return CompletableFuture.completedFuture(false);
        return send("/api/fake-punishments", event, session, true).thenApplyAsync(response -> {
            if (active != session) return false;
            return switch (response.statusCode()) {
                case 200 -> true;
                case 401, 403 -> false;
                case 429 -> throw new IllegalStateException("Подожди перед следующей командой");
                default -> throw new IllegalStateException("Сервис розыгрышей недоступен");
            };
        }, Minecraft.getInstance());
    }

    private void pollFakePunishments(Minecraft client) {
        var config = OrionClient.getConfigOrNull();
        var connection = client.getConnection();
        if (active == null || client.level == null || connection == null || client.getCurrentServer() == null
                || client.hasSingleplayerServer() || config == null || !config.isShowFakePunishments()) {
            fakePunishmentFeed = null;
            return;
        }
        if (fakePunishmentFeed == null || fakePunishmentFeed.session != active || fakePunishmentFeed.connection != connection) {
            fakePunishmentFeed = new FakePunishmentFeed(active, connection, fakePunishmentScope(client));
        }
        FakePunishmentFeed feed = fakePunishmentFeed;
        long now = System.nanoTime();
        if (feed.scope.isEmpty() || feed.loading || now < feed.nextPoll) return;
        feed.loading = true;
        feed.nextPoll = now + TimeUnit.SECONDS.toNanos(6);
        String path = "/api/fake-punishments?scope=" + feed.scope + (feed.cursor.isEmpty() ? "" : "&after=" + feed.cursor);
        send(path, null, feed.session, false).thenApply(OrionAccounts::friendResponse).whenComplete((result, failure) ->
                client.execute(() -> {
                    var current = OrionClient.getConfigOrNull();
                    if (fakePunishmentFeed != feed || active != feed.session || client.getConnection() != feed.connection
                            || current == null || !current.isShowFakePunishments()) return;
                    feed.loading = false;
                    if (failure != null) {
                        feed.nextPoll = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
                        return;
                    }
                    var cursorValue = result.get("cursor");
                    if (cursorValue == null || !cursorValue.isJsonPrimitive() || !cursorValue.getAsJsonPrimitive().isString()
                            || !result.has("items") || !result.get("items").isJsonArray()) return;
                    String cursor = cursorValue.getAsString();
                    if (!cursor.matches("[a-f0-9]{16}-[0-9]{1,18}")) return;
                    feed.cursor = cursor;
                    for (var item : result.getAsJsonArray("items")) {
                        if (item.isJsonObject()) OrionFakeBanCommand.showRemote(client, item.getAsJsonObject());
                    }
                }));
    }
    public boolean plusActive(String username) {
        Session session = sessions.get(username);
        return session != null && session.plusExpiresAt() > Instant.now().getEpochSecond();
    }
    public long plusExpiresAt(String username) {
        Session session = sessions.get(username);
        return session == null ? 0 : session.plusExpiresAt();
    }

    public String badge() { return badge(username()); }
    public String badge(String username) {
        Session session = sessions.get(username);
        if (session == null) return "";
        if ("owner".equals(session.role())) return "Owner";
        if ("admin".equals(session.role())) return "Admin";
        if ("media".equals(session.role())) return "Media";
        return session.plusExpiresAt() > Instant.now().getEpochSecond() ? "Plus" : "";
    }
    public String sessionCookieHeader() { return active == null ? "" : COOKIE + "=" + active.cookie(); }

    private static JsonObject cosmeticSlots(OrionConfig config) {
        var slots = new JsonObject();
        slots.addProperty("wings", !config.isWingsEnabled() ? "" : config.getWingsStyle() == 0 ? "angel" : "dragon");
        slots.addProperty("halo", config.isHaloEnabled());
        slots.addProperty("hat", config.isChinaHeadEnabled());
        slots.addProperty("dance", config.isEmotesEnabled());
        slots.addProperty("pet", config.getCosmeticPet());
        return slots;
    }

    private static void applyCosmeticSlots(OrionConfigManager manager, JsonObject slots) {
        OrionConfig config = manager.getConfig();
        if (cosmeticSlots(config).equals(slots)) return;
        String wings = slots.get("wings").getAsString();
        config.setWingsEnabled(!wings.isEmpty());
        if (!wings.isEmpty()) config.setWingsStyle("dragon".equals(wings) ? 1 : 0);
        config.setHaloEnabled(slots.get("halo").getAsBoolean());
        config.setChinaHeadEnabled(slots.get("hat").getAsBoolean());
        config.setEmotesEnabled(slots.get("dance").getAsBoolean());
        config.setCosmeticPet(slots.get("pet").getAsString());
        manager.save();
        OrionBadgePresence.requestRefresh();
    }

    private void syncCosmetics(Minecraft client) {
        if (busy || active == null || OrionClient.getConfigOrNull() == null) return;
        Session session = active;
        OrionConfigManager manager = OrionClient.getInstance().getConfigManager();
        if (!cosmeticCookie.equals(session.cookie())) {
            if (!cosmeticOwner.isEmpty() && !cosmeticOwner.equals(session.username())) {
                applyCosmeticSlots(manager, cosmeticSlots(OrionConfig.createDefault()));
            }
            cosmeticOwner = session.username();
            cosmeticCookie = session.cookie();
            cosmeticBaseline = cosmeticSeed = cosmeticSlots(manager.getConfig());
            cosmeticLoading = false;
            nextCosmeticPoll = nextCosmeticSample = cosmeticRetry = 0;
        }
        if (cosmeticLoading) return;
        long now = System.nanoTime();
        if (now < cosmeticRetry || now < nextCosmeticSample) return;
        nextCosmeticSample = now + Duration.ofMillis(250).toNanos();
        JsonObject snapshot = cosmeticSlots(manager.getConfig());
        JsonObject changes = new JsonObject();
        for (var entry : snapshot.entrySet()) {
            if (!entry.getValue().equals(cosmeticBaseline.get(entry.getKey()))) changes.add(entry.getKey(), entry.getValue());
        }
        if (now < nextCosmeticPoll && changes.isEmpty()) return;
        JsonObject body = changes.isEmpty() && cosmeticSeed == null ? null : new JsonObject();
        if (body != null) {
            body.add("changes", changes);
            if (cosmeticSeed != null) body.add("defaults", cosmeticSeed);
        }
        cosmeticLoading = true;
        send("/api/cosmetic-loadout", body, session, body != null).thenApply(response -> {
            requireSuccess(response.statusCode());
            return JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("slots");
        }).whenComplete((slots, failure) -> client.execute(() -> {
            if (!cosmeticCookie.equals(session.cookie())) return;
            cosmeticLoading = false;
            if (active == null || !active.cookie().equals(session.cookie())) return;
            long finished = System.nanoTime();
            if (failure != null || slots == null) {
                cosmeticRetry = finished + Duration.ofSeconds(15).toNanos();
                return;
            }
            nextCosmeticPoll = finished + Duration.ofSeconds(5).toNanos();
            JsonObject current = cosmeticSlots(manager.getConfig());
            JsonObject merged = current.deepCopy();
            for (var entry : snapshot.entrySet()) {
                String key = entry.getKey();
                if (slots.has(key) && current.get(key).equals(entry.getValue())) merged.add(key, slots.get(key));
            }
            // Keep edits made while the request was in flight queued for the next write.
            cosmeticSeed = null;
            cosmeticBaseline = snapshot.deepCopy();
            for (String key : snapshot.keySet()) if (slots.has(key)) cosmeticBaseline.add(key, slots.get(key));
            applyCosmeticSlots(manager, merged);
        }));
    }

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

    public void publishFriendAppearance(String hash, boolean slim) {
        if (active == null) return;
        var body = new JsonObject();
        body.addProperty("action", "appearance");
        body.addProperty("skin", hash);
        body.addProperty("slim", slim);
        send("/api/friends", body, active, true);
    }

    public CompletableFuture<JsonObject> friendWorlds(JsonObject action) {
        Session session = active;
        if (session == null) return CompletableFuture.failedFuture(new IllegalStateException("Войди в аккаунт GleamClient"));
        return send("/api/friend-worlds?transport=1", action, session, action != null).thenApply(OrionAccounts::friendResponse);
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

    public CompletableFuture<JsonObject> chat(String username, long before, String message, ChatReply reply) {
        Session session = active;
        if (session == null || !username.matches("[A-Za-z0-9_]{3,24}"))
            return CompletableFuture.failedFuture(new IllegalStateException("Чат недоступен"));
        String path = "/api/friends/" + username + "/chat?secure=1&reactions=0" + (before > 0 ? "&before=" + before : "");
        if (message == null) {
            var history = send(path, null, session, false).thenApply(OrionAccounts::chatResponse);
            var identity = chatIdentity(session);
            var peer = chatPeerKey(session, username);
            return history.thenCompose(result -> identity.thenCompose(ownKey ->
                    peer.thenCompose(peerKey -> decodeChat(result, ownKey, peerKey, session, username))));
        }
        String plaintext = reply == null ? message : replyMessage(message, reply);
        if (plaintext.getBytes(StandardCharsets.UTF_8).length > 2000)
            return CompletableFuture.failedFuture(new IllegalStateException("Сообщение слишком длинное"));
        var peerKey = chatPeerKey(session, username);
        return chatIdentity(session).thenCompose(identity -> peerKey.thenCompose(peer -> {
            if (peer == null) return CompletableFuture.failedFuture(new IllegalStateException("Друг ещё не настроил защищённый чат"));
            return CompletableFuture.supplyAsync(() -> {
                var body = new JsonObject();
                body.addProperty("envelope", chatCrypto(() -> OrionChatCrypto.seal(plaintext, identity, peer, session.username(), username)));
                return body;
            }, storage).thenCompose(body -> send(path, body, session, true))
                    .thenApply(OrionAccounts::chatResponse)
                    .thenCompose(result -> decodeChat(result, identity, peer, session, username));
        }));
    }

    private static String replyMessage(String message, ChatReply reply) {
        // NUL запрещён во вводе. Тело остаётся первым, а ссылка и цитата шифруются вместе с ним.
        String header = message + '\0' + reply.id() + '\0' + (reply.own() ? '1' : '0');
        int remaining = 2000 - header.getBytes(StandardCharsets.UTF_8).length;
        var excerpt = new StringBuilder();
        var points = reply.body().codePoints().limit(96).iterator();
        while (points.hasNext() && remaining > 0) {
            int point = points.nextInt();
            int bytes = point < 0x80 ? 1 : point < 0x800 ? 2 : point < 0x10000 ? 3 : 4;
            if (bytes > remaining) break;
            excerpt.appendCodePoint(point);
            remaining -= bytes;
        }
        return header + excerpt;
    }

    private static String messageBody(String plain) {
        int separator = plain.indexOf(0);
        return separator < 0 ? plain : plain.substring(0, separator);
    }

    private static void unpackReply(JsonObject item, String plain, boolean own) {
        int start = plain.indexOf(0);
        item.addProperty("body", messageBody(plain));
        if (start < 0) return;
        int end = plain.indexOf(0, start + 1);
        if (end <= start + 1 || end + 1 >= plain.length()) return;
        char author = plain.charAt(end + 1);
        if (author != '0' && author != '1') return;
        try {
            long id = Long.parseLong(plain, start + 1, end, 10);
            if (id <= 0) return;
            item.addProperty("reply_to", id);
            item.addProperty("reply_own", own == (author == '1'));
            item.addProperty("reply_preview", plain.substring(end + 2));
        } catch (NumberFormatException ignored) { }
    }

    private CompletableFuture<OrionChatCrypto.Peer> chatPeerKey(Session session, String username) {
        return send("/api/chat/keys/" + username, null, session, false).thenApplyAsync(keys -> {
            if (keys.statusCode() == 404) return null;
            return chatCrypto(() -> OrionChatCrypto.pin(session.username(), username, friendResponse(keys)));
        }, storage);
    }

    private static JsonObject chatResponse(HttpResponse<String> response) {
        if (response.statusCode() == 403) throw new IllegalStateException("Чат доступен только друзьям");
        var result = friendResponse(response);
        if (!result.has("items") || !result.get("items").isJsonArray() || !result.has("older"))
            throw new IllegalStateException("Чат недоступен");
        return result;
    }

    private CompletableFuture<OrionChatCrypto.Identity> chatIdentity(Session session) {
        if (!Platform.isWindows()) return CompletableFuture.failedFuture(new IllegalStateException("Защищённый чат доступен на Windows"));
        if (registeredChatSession == session && registeredChatIdentity != null && !registeredChatIdentity.isCompletedExceptionally())
            return registeredChatIdentity;
        registeredChatSession = session;
        registeredChatIdentity = chatRecoveryState(session).thenCompose(keys ->
                CompletableFuture.supplyAsync(() -> chatCrypto(() -> {
                    var local = OrionChatCrypto.localIdentity(session.username(), keys);
                    if (local != null) return local;
                    if (keys.has("encryption")) throw new ChatRecoveryRequired(hasChatBackup(keys));
                    return OrionChatCrypto.newIdentity();
                }), storage).thenCompose(identity -> CompletableFuture.runAsync(
                        () -> chatCrypto(() -> { OrionChatCrypto.saveIdentity(session.username(), identity); return null; }), storage)
                        .thenCompose(ignored -> keys.has("encryption") ? CompletableFuture.completedFuture(identity)
                                : send("/api/chat/keys", identity.publicKeys(), session, true).thenApply(response -> {
                                    friendResponse(response);
                                    return identity;
                                }))));
        return registeredChatIdentity;
    }

    public CompletableFuture<String> copyChatRecoveryCode() {
        var session = active;
        if (session == null) return CompletableFuture.failedFuture(new IllegalStateException("Войди в аккаунт GleamClient"));
        return chatIdentity(session).thenCompose(identity -> chatRecoveryState(session).thenCompose(keys ->
                CompletableFuture.supplyAsync(() -> chatCrypto(() -> {
                    if (hasChatBackup(keys)) {
                        var recovery = OrionChatCrypto.localRecovery(session.username(), keys.get("backup").getAsString());
                        if (recovery != null) return recovery;
                        throw new IllegalStateException("Код уже был создан. Используй сохранённый код восстановления");
                    }
                    var recovery = OrionChatCrypto.pendingRecovery(session.username());
                    if (recovery != null) {
                        try {
                            OrionChatCrypto.recover(session.username(), recovery, keys);
                            return recovery;
                        } catch (java.security.GeneralSecurityException | IllegalArgumentException invalid) {
                            // The staged copy may be from a prior, unpublished key pair.
                        }
                    }
                    recovery = OrionChatCrypto.createRecovery(session.username(), identity);
                    OrionChatCrypto.stageRecovery(session.username(), recovery);
                    return recovery;
                }), storage).thenCompose(recovery -> {
                    if (hasChatBackup(keys)) return CompletableFuture.completedFuture(recovery.code());
                    var body = new JsonObject();
                    body.addProperty("backup", recovery.backup());
                    return send("/api/chat/recovery", body, session, true).thenApply(response -> {
                        friendResponse(response);
                        return recovery.code();
                    });
                })));
    }

    public CompletableFuture<Runnable> restoreChatKey(String code) {
        var session = active;
        if (session == null) return CompletableFuture.failedFuture(new IllegalStateException("Войди в аккаунт GleamClient"));
        if (!code.matches("[A-Za-z0-9_-]{43}"))
            return CompletableFuture.failedFuture(new IllegalStateException("Неверный код восстановления"));
        return chatRecoveryState(session).thenApplyAsync(keys -> {
            if (!hasChatBackup(keys)) throw new ChatRecoveryRequired(false);
            var recovery = new OrionChatCrypto.Recovery(code, keys.get("backup").getAsString());
            OrionChatCrypto.Identity identity;
            try {
                identity = OrionChatCrypto.recover(session.username(), recovery, keys);
            } catch (java.security.GeneralSecurityException | IllegalArgumentException failure) {
                throw new IllegalStateException("Неверный код восстановления");
            }
            chatCrypto(() -> {
                OrionChatCrypto.saveIdentity(session.username(), identity);
                OrionChatCrypto.saveRecovery(session.username(), recovery);
                return null;
            });
            return () -> useChatIdentity(session, identity);
        }, storage);
    }

    public ChatTransfer chatTransfer() {
        return active != null && active == transferSession ? chatTransfer : null;
    }

    public CompletableFuture<Runnable> startChatTransfer() {
        Session session = active;
        if (session == null) return CompletableFuture.failedFuture(new IllegalStateException("Войди в аккаунт GleamClient"));
        if (chatTransfer() != null) return CompletableFuture.completedFuture(() -> { });
        String code = OrionChatCrypto.transferCode();
        return transferRequest(session, "start", code, null).thenApply(ignored -> () -> {
            if (active != session) return;
            transferSession = session;
            chatTransfer = new ChatTransfer(code, System.nanoTime() + TimeUnit.SECONDS.toNanos(295));
        });
    }

    public CompletableFuture<Runnable> pollChatTransfer() {
        Session session = active;
        ChatTransfer current = chatTransfer();
        if (current == null) return CompletableFuture.completedFuture(null);
        if (System.nanoTime() >= current.expiresAt()) {
            chatTransfer = null;
            return CompletableFuture.failedFuture(new IllegalStateException("Срок действия кода истёк"));
        }
        return transferRequest(session, "poll", current.code(), null).thenCompose(result -> {
            if (result == null) return CompletableFuture.completedFuture(() -> {
                if (chatTransfer == current) chatTransfer = null;
                throw new IllegalStateException("Срок действия кода истёк");
            });
            if (!hasChatBackup(result)) return CompletableFuture.completedFuture(null);
            return chatRecoveryState(session).thenApplyAsync(keys -> {
                var identity = chatCrypto(() -> {
                    var restored = OrionChatCrypto.openTransfer(session.username(), current.code(),
                            result.get("backup").getAsString(), keys);
                    OrionChatCrypto.saveIdentity(session.username(), restored);
                    return restored;
                });
                return () -> {
                    if (active != session || chatTransfer != current) return;
                    chatTransfer = null;
                    transferRequest(session, "cancel", current.code(), null);
                    useChatIdentity(session, identity);
                };
            }, storage);
        });
    }

    public void cancelChatTransfer() {
        ChatTransfer current = chatTransfer();
        if (current == null) return;
        chatTransfer = null;
        transferRequest(active, "cancel", current.code(), null);
    }

    public CompletableFuture<Void> approveChatTransfer(String code) {
        Session session = active;
        if (session == null) return CompletableFuture.failedFuture(new IllegalStateException("Войди в аккаунт GleamClient"));
        if (!code.matches("[A-Za-z0-9_-]{43}"))
            return CompletableFuture.failedFuture(new IllegalStateException("Неверный код подключения"));
        return chatIdentity(session).thenApplyAsync(identity -> chatCrypto(
                () -> OrionChatCrypto.encryptTransfer(session.username(), identity, code)), storage)
                .thenCompose(backup -> transferRequest(session, "approve", code, backup))
                .thenAccept(ignored -> { });
    }

    private static CompletableFuture<JsonObject> transferRequest(Session session, String action, String code, String backup) {
        JsonObject body = new JsonObject();
        body.addProperty("action", action);
        body.addProperty("id", OrionChatCrypto.transferId(code));
        if (backup != null) body.addProperty("backup", backup);
        return send("/api/chat/transfer", body, session, true).thenApply(response ->
                action.equals("poll") && response.statusCode() == 404 ? null : friendResponse(response));
    }

    private void useChatIdentity(Session session, OrionChatCrypto.Identity identity) {
        if (active != session) return;
        registeredChatSession = session;
        registeredChatIdentity = CompletableFuture.completedFuture(identity);
    }

    private static CompletableFuture<JsonObject> chatRecoveryState(Session session) {
        return send("/api/chat/recovery", null, session, false).thenApply(OrionAccounts::friendResponse);
    }

    private static boolean hasChatBackup(JsonObject keys) {
        return keys.has("backup") && !keys.get("backup").isJsonNull();
    }

    private CompletableFuture<JsonObject> decodeChat(JsonObject result, OrionChatCrypto.Identity identity,
                                                     OrionChatCrypto.Peer peer, Session session, String username) {
        return CompletableFuture.supplyAsync(() -> {
            String pair = session.username() + "/" + username;
            if (!pair.equals(decodedChatPair)) {
                decodedChatPair = pair;
                decodedMessages.clear();
            }
            if (peer != null) result.addProperty("verification", chatCrypto(
                    () -> OrionChatCrypto.verificationCode(identity, peer, session.username(), username)));
            for (var element : result.getAsJsonArray("items")) {
                var item = element.getAsJsonObject();
                item.remove("reactions");
                var envelope = item.remove("envelope");
                if (envelope == null) {
                    item.addProperty("legacy", true);
                    continue;
                }
                try {
                    if (peer == null) throw new IllegalStateException("Missing chat peer key");
                    boolean own = item.get("own").getAsBoolean();
                    String packet = envelope.getAsString(), key = (own ? "o" : "i") + packet;
                    String plain = decodedMessages.get(key);
                    if (plain == null) {
                        plain = OrionChatCrypto.open(packet, identity, peer, session.username(), username, own);
                        decodedMessages.put(key, plain);
                        if (decodedMessages.size() > 128) decodedMessages.pollFirstEntry();
                    }
                    unpackReply(item, plain, own);
                } catch (java.security.GeneralSecurityException | RuntimeException failure) {
                    item.addProperty("unreadable", true);
                    item.addProperty("body", "");
                }
            }
            return result;
        }, storage);
    }

    private static <T> T chatCrypto(java.util.concurrent.Callable<T> operation) {
        try {
            return operation.call();
        } catch (IllegalStateException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Ключи защищённого чата недоступны", failure);
        }
    }

    private CompletableFuture<String> chatNotificationPreview(Session session, String username, JsonObject item) {
        if (item.has("body")) return CompletableFuture.completedFuture(item.get("body").getAsString());
        if (!item.has("envelope")) return CompletableFuture.completedFuture("");
        String envelope = item.get("envelope").getAsString();
        return chatIdentity(session).thenCombineAsync(chatPeerKey(session, username), (identity, peer) ->
                peer == null ? "" : chatCrypto(() -> messageBody(OrionChatCrypto.open(envelope, identity, peer, session.username(), username, false))), storage);
    }

    private CompletableFuture<BufferedImage> notificationAvatar(Session session, String sender) {
        return loadFriends(session).thenCompose(friends -> {
            for (var entry : friends) {
                var friend = entry.getAsJsonObject();
                var id = friend.get("public_id");
                if (sender.equalsIgnoreCase(friend.get("username").getAsString()) && id != null && !id.isJsonNull())
                    return publicAvatar(id.getAsString());
            }
            return CompletableFuture.completedFuture(null);
        }).exceptionally(failure -> null);
    }

    private void pollChatNotifications(Minecraft client) {
        var config = OrionClient.getConfigOrNull();
        if (notificationSession != active) {
            OrionChatNotifications.clear();
            notificationSession = active;
            notificationCursor = -1;
            unreadChats.clear();
            notificationLoading = false;
            nextNotificationPoll = 0;
            nextChatRegistration = 0;
        }
        if (active == null || busy || config == null) return;
        long now = System.nanoTime();
        if (now >= nextChatRegistration) {
            nextChatRegistration = now + TimeUnit.MINUTES.toNanos(2);
            chatIdentity(active);
        }
        if (notificationLoading || now < nextNotificationPoll) return;
        notificationLoading = true;
        nextNotificationPoll = now + TimeUnit.SECONDS.toNanos(6);
        var session = active;
        String path = "/api/chat/updates?preview=" + (config.isChatNotifications() ? "1" : "0")
                + (notificationCursor < 0 ? "" : "&after=" + notificationCursor);
        send(path, null, session, false).thenApply(OrionAccounts::friendResponse).whenComplete((result, failure) ->
            client.execute(() -> {
                if (active != session || notificationSession != session) return;
                notificationLoading = false;
                if (failure != null) {
                    nextNotificationPoll = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                    return;
                }
                notificationCursor = result.get("cursor").getAsLong();
                JsonObject latest = null;
                for (var entry : result.getAsJsonArray("items")) {
                    var item = entry.getAsJsonObject();
                    String username = item.get("username").getAsString();
                    if (GleamMinecraft.screen(client) instanceof OrionClickGuiScreen screen && screen.isViewingChat(username)) continue;
                    latest = item;
                    unreadChats.merge(username, 1, Integer::sum);
                }
                if (latest == null) return;
                if (config.isChatNotifications()) {
                    long id = latest.get("id").getAsLong();
                    lastNotificationMessage = id;
                    String sender = latest.get("username").getAsString();
                    var avatar = notificationAvatar(session, sender);
                    chatNotificationPreview(session, sender, latest).whenComplete((preview, error) -> client.execute(() -> {
                        if (active != session || notificationSession != session || lastNotificationMessage != id) return;
                        if (GleamMinecraft.screen(client) instanceof OrionClickGuiScreen screen && screen.isViewingChat(sender)) return;
                        var settings = OrionClient.getConfigOrNull();
                        if (settings == null || !settings.isChatNotifications()) return;
                        String fallback = ru.orion.client.gui.OrionGuiLanguage.translate("Новое сообщение");
                        OrionChatNotifications.show(id, sender, error == null && !preview.isBlank() ? preview : fallback);
                        avatar.thenAccept(image -> client.execute(() -> {
                            if (active == session && lastNotificationMessage == id) OrionChatNotifications.setAvatar(id, image);
                        }));
                    }));
                }
                if (config.isChatNotificationSound())
                    client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.15F, .75F));
            }));
    }

    public JsonObject chatUnread() {
        var result = new JsonObject();
        if (notificationSession == active) unreadChats.forEach(result::addProperty);
        return result;
    }

    public void markChatRead(String username) {
        if (notificationSession == active) unreadChats.remove(username);
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
        return send("/api/friends?appearance=1", null, session, false)
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
            case "world_unavailable" -> "Мир друга сейчас недоступен";
            case "world_expired" -> "Приглашение больше не действует";
            case "world_license" -> "Друг разрешил вход только с лицензией Minecraft";
            case "chat_device" -> "Синхронизируй ключ чата и попробуй снова";
            case "chat_recovery_exists" -> "Код уже был создан. Используй сохранённый код восстановления";
            case "chat_transfer_busy" -> "Запрос на подключение уже обработан или достигнут лимит";
            case "chat_transfer_expired" -> "Срок действия кода истёк";
            case "chat_transfer_same_session" -> "Подтверди подключение на другом устройстве";
            case "chat_key_missing" -> "Друг ещё не настроил защищённый чат";
            case "chat_encryption_required" -> "Чат требует обновления клиента";
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
