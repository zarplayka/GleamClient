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
    private static final String BASE = "https://orion-client-cosmetics.candytulip7.chatgpt.site";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Orion Badge Sync");
        thread.setDaemon(true);
        return thread;
    });
    private static final ExecutorService WATCHER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Orion Cosmetics Watch");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile Set<UUID> online = Set.of();
    private static volatile Set<UUID> halos = Set.of();
    private static volatile Map<UUID, Integer> wings = Map.of();
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
                    KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
                    JsonObject request = new JsonObject();
                    request.addProperty("uuid", uuid.toString());
                    request.addProperty("name", name);
                    request.addProperty("publicKey", Base64.getUrlEncoder().withoutPadding().encodeToString(keyPair.getPublic().getEncoded()));
                    JsonObject challenge = post("/v1/challenge", request, null);
                    String challengeText = challenge.get("challenge").getAsString();
                    Signature signature = Signature.getInstance("Ed25519");
                    signature.initSign(keyPair.getPrivate());
                    signature.update(challengeText.getBytes(StandardCharsets.UTF_8));
                    JsonObject verification = new JsonObject();
                    verification.addProperty("challenge", challengeText);
                    verification.addProperty("signature", Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign()));
                    activeToken = post("/v1/verify", verification, null).get("token").getAsString();
                }
                if (requestGeneration != generation) return;
                Set<UUID> found = new HashSet<>(), foundHalos = new HashSet<>();
                Map<UUID, Integer> foundWings = new HashMap<>();
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
                }
                if (requestGeneration == generation) {
                    token = activeToken;
                    online = Set.copyOf(found);
                    halos = Set.copyOf(foundHalos);
                    wings = Map.copyOf(foundWings);
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

    private static JsonObject post(String path, JsonObject payload, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json");
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        HttpResponse<String> response = HTTP.send(builder.POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || response.body().length() > 65536) throw new IllegalStateException("Сервис значков недоступен");
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
}
