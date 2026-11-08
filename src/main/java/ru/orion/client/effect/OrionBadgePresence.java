package ru.orion.client.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import ru.orion.client.OrionClient;
import java.net.URI;
import ru.orion.client.config.OrionAccounts;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Проверяет пользователей Orion в фоне, не задерживая игровой поток. */
public final class OrionBadgePresence {
    private static final String BASE = OrionAccounts.ORIGIN;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "GleamClient Badge Sync");
        thread.setDaemon(true);
        return thread;
    });
    private static final ExecutorService WATCHER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "GleamClient Cosmetics Watch");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile Set<UUID> online = Set.of();
    private static volatile Set<UUID> halos = Set.of();
    private static volatile Map<UUID, Integer> wings = Map.of();
    public record Emote(int id, double started, long localStart) {}
    private static volatile Map<UUID, Emote> emotes = Map.of();
    private static volatile boolean pending;
    private static final AtomicBoolean refreshRequested = new AtomicBoolean();
    private static volatile long generation;
    private static volatile String token;
    private static volatile long watchingGeneration = -1;
    private static volatile CompletableFuture<HttpResponse<String>> watchRequest;
    private static ClientPacketListener connection;
    private static Set<UUID> trackedPlayers = Set.of();
    private static volatile long nextAttempt;

    private OrionBadgePresence() { }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(OrionBadgePresence::tick);
    }

    public static boolean isOrion(UUID uuid) {
        Minecraft client = Minecraft.getInstance();
        return uuid != null && ((client != null && client.player != null && client.player.getUUID().equals(uuid)) || online.contains(uuid));
    }

    public static boolean hasHalo(UUID uuid) { return uuid != null && halos.contains(uuid); }
    public static int wingsStyle(UUID uuid) { return wings.getOrDefault(uuid, 0); }
    public static Emote emote(UUID uuid) { return emotes.get(uuid); }

    public static void sendEmote(int id) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.isLocalServer()) return;
        UUID uuid = client.player.getUUID();
        String name = client.getUser().getName();
        long currentGeneration = generation;
        WORKER.execute(() -> {
            if (currentGeneration != generation) return;
            try {
                String bearer = token;
                if (bearer == null) bearer = authenticate(uuid, name);
                JsonObject payload = new JsonObject();
                payload.addProperty("emote", id);
                post("/v1/emote", payload, bearer);
                requestRefresh();
            } catch (Exception exception) {
                org.slf4j.LoggerFactory.getLogger(OrionClient.MOD_ID).warn("Emote synchronization failed", exception);
            }
        });
    }
    public static void requestRefresh() { refreshRequested.set(true); }

    private static void tick(Minecraft client) {
        ClientPacketListener current = client.player == null || client.isLocalServer() ? null : client.getConnection();
        if (connection != current) {
            connection = current;
            generation++;
            CompletableFuture<HttpResponse<String>> watching = watchRequest;
            if (watching != null) watching.cancel(true);
            online = Set.of();
            halos = Set.of();
            wings = Map.of();
            emotes = Map.of();
            trackedPlayers = Set.of();
            token = null;
            nextAttempt = 0L;
        }
        if (current == null) return;
        Set<UUID> present = new HashSet<>();
        for (PlayerInfo info : current.getOnlinePlayers()) present.add(info.getProfile().id());
        if (!present.equals(trackedPlayers)) {
            trackedPlayers = Set.copyOf(present);
            requestRefresh();
        }
        if (pending) return;
        if (!refreshRequested.getAndSet(false) && System.nanoTime() < nextAttempt) return;
        nextAttempt = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        List<UUID> ids = List.copyOf(trackedPlayers);
        UUID uuid = client.player.getUUID();
        String name = client.getUser().getName();
        boolean halo = OrionClient.getConfigOrNull() != null && OrionClient.getConfigOrNull().isHaloEnabled();
        int wingsValue = OrionClient.getConfigOrNull() != null && OrionClient.getConfigOrNull().isWingsEnabled()
                ? OrionClient.getConfigOrNull().getWingsStyle() + 1 : 0;
        long requestGeneration = generation;
        pending = true;
        WORKER.execute(() -> {
            try {
                String activeToken = token;
                if (activeToken == null) {
                    activeToken = authenticate(uuid, name);
                }
                if (requestGeneration != generation) return;
                Set<UUID> found = new HashSet<>(), foundHalos = new HashSet<>();
                Map<UUID, Integer> foundWings = new HashMap<>();
                Map<UUID, Emote> foundEmotes = new HashMap<>();
                long firstRevision = Long.MAX_VALUE;
                for (int start = 0; start < Math.max(1, ids.size()); start += 100) {
                    if (requestGeneration != generation) return;
                    JsonObject request = new JsonObject();
                    JsonArray players = new JsonArray();
                    ids.subList(start, Math.min(start + 100, ids.size())).forEach(id -> players.add(id.toString()));
                    request.add("players", players);
                    request.addProperty("halo", halo);
                    request.addProperty("wings", wingsValue);
                    JsonObject response = post("/v1/presence", request, activeToken);
                    if (response.has("revision")) firstRevision = Math.min(firstRevision, response.get("revision").getAsLong());
                    response.getAsJsonArray("online").forEach(value -> found.add(parseUuid(value.getAsString())));
                    JsonArray haloResult = response.getAsJsonArray("halos");
                    if (haloResult != null) haloResult.forEach(value -> foundHalos.add(parseUuid(value.getAsString())));
                    JsonObject wingResult = response.getAsJsonObject("wings");
                    if (wingResult != null) wingResult.entrySet().forEach(entry -> {
                        int style = entry.getValue().getAsInt();
                        if (style >= 1 && style <= 2) foundWings.put(parseUuid(entry.getKey()), style);
                    });
                    JsonObject emoteResult = response.getAsJsonObject("emotes");
                    if (emoteResult != null) emoteResult.entrySet().forEach(entry -> {
                        JsonObject value = entry.getValue().getAsJsonObject();
                        int id = value.get("id").getAsInt();
                        double started = value.get("started").getAsDouble();
                        double elapsed = value.get("elapsed").getAsDouble();
                        if (id < 1 || id > 6 || !Double.isFinite(started) || !Double.isFinite(elapsed) || elapsed < 0 || elapsed >= 12) return;
                        UUID player = parseUuid(entry.getKey());
                        Emote old = emotes.get(player);
                        foundEmotes.put(player, old != null && old.id() == id && old.started() == started ? old
                                : new Emote(id, started, System.nanoTime() - (long) (elapsed * 1_000_000_000D)));
                    });
                }
                if (requestGeneration == generation) {
                    token = activeToken;
                    online = Set.copyOf(found);
                    halos = Set.copyOf(foundHalos);
                    wings = Map.copyOf(foundWings);
                    emotes = Map.copyOf(foundEmotes);
                    if (firstRevision != Long.MAX_VALUE) startWatcher(requestGeneration, activeToken, firstRevision);
                }
            } catch (Exception ignored) {
                if (requestGeneration == generation) {
                    token = null;
                    nextAttempt = System.nanoTime() + Duration.ofSeconds(3).toNanos();
                }
            } finally {
                pending = false;
            }
        });
    }

    private static synchronized void startWatcher(long requestGeneration, String bearer, long initialRevision) {
        if (watchingGeneration == requestGeneration) return;
        watchingGeneration = requestGeneration;
        WATCHER.execute(() -> {
            long revision = initialRevision;
            try {
                while (generation == requestGeneration) {
                    try {
                        JsonObject payload = new JsonObject();
                        payload.add("players", new JsonArray());
                        payload.addProperty("revision", revision);
                        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/v1/watch"))
                                .timeout(Duration.ofSeconds(25)).header("Content-Type", "application/json")
                                .header("Authorization", "Bearer " + bearer)
                                .POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build();
                        CompletableFuture<HttpResponse<String>> future = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString());
                        watchRequest = future;
                        if (generation != requestGeneration) { future.cancel(true); return; }
                        HttpResponse<String> response = future.join();
                        if (response.statusCode() == 401) {
                            token = null;
                            requestRefresh();
                            return;
                        }
                        if (response.statusCode() != 200 || response.body().length() > 65536) throw new IllegalStateException("Сервис косметики недоступен");
                        long received = JsonParser.parseString(response.body()).getAsJsonObject().get("revision").getAsLong();
                        if (received != revision) {
                            revision = received;
                            requestRefresh();
                        }
                    } catch (Exception ignored) {
                        if (generation != requestGeneration) return;
                        requestRefresh();
                        Thread.sleep(2000L);
                    }
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                if (watchingGeneration == requestGeneration) watchingGeneration = -1;
                watchRequest = null;
            }
        });
    }

    private static UUID parseUuid(String id) {
        if (!id.matches("[0-9a-fA-F]{32}")) throw new IllegalArgumentException("Некорректный UUID");
        return UUID.fromString(id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16)
                + "-" + id.substring(16, 20) + "-" + id.substring(20));
    }

    /** Отдельная фоновая сессия матча. Частые запросы мяча не задерживают косметику. */
    public static final class FootballConnection {
        private static final ExecutorService NETWORK = Executors.newVirtualThreadPerTaskExecutor();
        private final UUID uuid;
        private final String name;
        private final String path;
        private String bearer;

        public FootballConnection(UUID uuid, String name) {
            this(uuid, name, false);
        }

        public FootballConnection(UUID uuid, String name, boolean basketball) {
            this.uuid = uuid;
            this.name = name;
            path = basketball ? "/v1/basketball" : "/v1/football";
        }

        public CompletableFuture<JsonObject> request(JsonObject payload) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    for (int attempt = 0; attempt < 2; attempt++) {
                        if (bearer == null) bearer = authenticate(uuid, name);
                        var request = HttpRequest.newBuilder(URI.create(BASE + path))
                                .timeout(Duration.ofSeconds(4))
                                .header("Content-Type", "application/json").header("Authorization", "Bearer " + bearer)
                                .POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build();
                        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() == 401 && attempt == 0) {
                            bearer = null;
                            continue;
                        }
                        if (response.body().length() > 65_536) throw new IllegalStateException("football_unavailable");
                        var result = JsonParser.parseString(response.body()).getAsJsonObject();
                        if (response.statusCode() != 200) throw new IllegalStateException(result.get("error").getAsString());
                        return result;
                    }
                    throw new IllegalStateException("football_unavailable");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new java.util.concurrent.CompletionException(exception);
                } catch (Exception exception) {
                    throw new java.util.concurrent.CompletionException(exception);
                }
            }, NETWORK);
        }
    }

    private static String authenticate(UUID uuid, String name) throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        JsonObject request = new JsonObject();
        request.addProperty("uuid", uuid.toString());
        request.addProperty("name", name);
        request.addProperty("publicKey", Base64.getUrlEncoder().withoutPadding().encodeToString(keyPair.getPublic().getEncoded()));
        String challenge = post("/v1/challenge", request, null).get("challenge").getAsString();
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(keyPair.getPrivate());
        signature.update(challenge.getBytes(StandardCharsets.UTF_8));
        JsonObject verification = new JsonObject();
        verification.addProperty("challenge", challenge);
        verification.addProperty("signature", Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign()));
        return post("/v1/verify", verification, null).get("token").getAsString();
    }

    /** Сетевой вызов каталога. Выполняется рабочим потоком библиотеки конфигов. */
    public static JsonObject sharedConfigs(String query, JsonObject payload, UUID uuid, String name) throws Exception {
        return sharedConfigs(payload == null ? "GET" : "POST", query, payload, uuid, name);
    }

    public static JsonObject sharedConfigs(String method, String query, JsonObject payload, UUID uuid, String name) throws Exception {
        return sharedConfigs(method, query, payload, uuid, name, "");
    }

    public static JsonObject publishConfig(JsonObject payload, UUID uuid, String name, String orionCookie) throws Exception {
        return sharedConfigs("POST", "", payload, uuid, name, orionCookie);
    }

    private static JsonObject sharedConfigs(String method, String query, JsonObject payload, UUID uuid, String name, String orionCookie) throws Exception {
        String origin = orionCookie.isEmpty() ? BASE : OrionAccounts.ORIGIN;
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(origin + "/v1/configs" + query))
                .timeout(Duration.ofSeconds(8));
        if (!orionCookie.isEmpty()) builder.header("Cookie", orionCookie);
        if (uuid != null) {
            String authorization = "Bearer " + authenticate(uuid, name);
            builder.header("Authorization", authorization);
            if (method.equals("PATCH") || method.equals("DELETE")) {
                String nonce = UUID.randomUUID().toString().replace("-", "");
                String binding = String.join("\n", authorization, method, "/v1/configs" + query, nonce);
                byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(binding.getBytes(StandardCharsets.UTF_8));
                JsonObject join = new JsonObject();
                join.addProperty("accessToken", Minecraft.getInstance().getUser().getAccessToken());
                join.addProperty("selectedProfile", uuid.toString().replace("-", ""));
                join.addProperty("serverId", java.util.HexFormat.of().formatHex(hash, 0, 20));
                var proof = HttpRequest.newBuilder(URI.create("https://sessionserver.mojang.com/session/minecraft/join"))
                        .timeout(Duration.ofSeconds(6)).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(join.toString())).build();
                if (HTTP.send(proof, HttpResponse.BodyHandlers.discarding()).statusCode() != 204) {
                    throw new IllegalStateException("Для изменения публикации войди в аккаунт Minecraft");
                }
                builder.header("X-Orion-Profile-Proof", nonce);
            }
        }
        builder.header("Content-Type", "application/json").method(method, payload == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload.toString()));
        HttpResponse<java.io.InputStream> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (var stream = response.body()) {
            if (response.statusCode() == 429) throw new IllegalStateException("Лимит публикаций исчерпан");
            if (response.statusCode() == 401 && !orionCookie.isEmpty()) throw new IllegalStateException("Войди в аккаунт GleamClient заново");
            if (response.statusCode() == 403) throw new IllegalStateException("Изменять публикацию может только автор");
            if (response.statusCode() == 404) throw new IllegalStateException("Публикация уже удалена");
            if (response.statusCode() == 409) throw new IllegalStateException("Конфиг изменился — обнови список");
            if (response.statusCode() != (method.equals("POST") && query.isEmpty() ? 201 : 200)) {
                throw new IllegalStateException("Каталог недоступен");
            }
            byte[] data = stream.readNBytes(65_537);
            if (data.length > 65_536) throw new IllegalStateException("Слишком большой ответ каталога");
            return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static JsonObject post(String path, JsonObject payload, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json");
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        HttpResponse<String> response = HTTP.send(builder.POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || response.body().length() > 65536) throw new IllegalStateException("Сервис значков недоступен");
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
}
