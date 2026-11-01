package ru.orion.client.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/** Проверяет пользователей Orion в фоне, не задерживая игровой поток. */
public final class OrionBadgePresence {
    private static final String BASE = "https://orion-client-cosmetics.candytulip7.chatgpt.site";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Orion Badge Sync");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile Set<UUID> online = Set.of();
    private static volatile boolean pending;
    private static volatile long generation;
    private static volatile String token;
    private static ClientPacketListener connection;
    private static long nextAttempt;

    private OrionBadgePresence() { }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(OrionBadgePresence::tick);
    }

    public static boolean isOrion(UUID uuid) {
        Minecraft client = Minecraft.getInstance();
        return uuid != null && ((client != null && client.player != null && client.player.getUUID().equals(uuid)) || online.contains(uuid));
    }

    private static void tick(Minecraft client) {
        ClientPacketListener current = client.player == null || client.isLocalServer() ? null : client.getConnection();
        if (connection != current) {
            connection = current;
            generation++;
            online = Set.of();
            token = null;
            nextAttempt = 0L;
        }
        if (current == null || pending || System.nanoTime() < nextAttempt) return;
        nextAttempt = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        List<UUID> ids = current.getOnlinePlayers().stream().map(PlayerInfo::getProfile)
                .map(profile -> profile.id()).limit(100).toList();
        UUID uuid = client.player.getUUID();
        String name = client.getUser().getName();
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
                JsonObject request = new JsonObject();
                JsonArray players = new JsonArray();
                ids.forEach(id -> players.add(id.toString()));
                request.add("players", players);
                JsonArray result = post("/v1/presence", request, activeToken).getAsJsonArray("online");
                Set<UUID> found = java.util.stream.StreamSupport.stream(result.spliterator(), false)
                        .map(value -> parseUuid(value.getAsString())).collect(Collectors.toUnmodifiableSet());
                if (requestGeneration == generation) {
                    token = activeToken;
                    online = found;
                }
            } catch (Exception ignored) {
                if (requestGeneration == generation) {
                    token = null;
                    online = Set.of();
                }
            } finally {
                pending = false;
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
