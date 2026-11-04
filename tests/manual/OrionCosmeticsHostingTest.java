package ru.orion.client.effect;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Реальный HTTPS-протокол через production-код Java-клиента, без вывода токенов. */
public final class OrionCosmeticsHostingTest {
    private static final Method POST;
    static {
        try { POST = OrionBadgePresence.class.getDeclaredMethod("post", String.class, JsonObject.class, String.class); POST.setAccessible(true); }
        catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    private static JsonObject post(String path, JsonObject payload, String bearer) {
        try { return (JsonObject) POST.invoke(null, path, payload, bearer); }
        catch (InvocationTargetException e) { throw new RuntimeException(e.getCause()); }
        catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    private static String login(UUID uuid, String name) throws Exception {
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var request = new JsonObject(); request.addProperty("uuid", uuid.toString()); request.addProperty("name", name);
        request.addProperty("publicKey", Base64.getUrlEncoder().withoutPadding().encodeToString(keys.getPublic().getEncoded()));
        String challenge = post("/v1/challenge", request, null).get("challenge").getAsString();
        var signer = Signature.getInstance("Ed25519"); signer.initSign(keys.getPrivate()); signer.update(challenge.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        request = new JsonObject(); request.addProperty("challenge", challenge);
        request.addProperty("signature", Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
        return post("/v1/verify", request, null).get("token").getAsString();
    }
    private static JsonObject presence(UUID observed, boolean halo, int wings) {
        var payload = new JsonObject(); var players = new JsonArray(); players.add(observed.toString());
        payload.add("players", players); payload.addProperty("halo", halo); payload.addProperty("wings", wings); return payload;
    }
    public static void main(String[] args) throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        String tokenA = login(a, "OrionProbeA"), tokenB = login(b, "OrionProbeB");
        String compact = a.toString().replace("-", "");
        post("/v1/presence", presence(a, true, 2), tokenA);
        var response = post("/v1/presence", presence(a, false, 0), tokenB);
        check(response.getAsJsonArray("online").asList().stream().anyMatch(value -> compact.equals(value.getAsString())), "shared client badge");
        check(response.getAsJsonArray("halos").asList().stream().anyMatch(value -> compact.equals(value.getAsString())), "shared halo");
        check(response.getAsJsonObject("wings").get(compact).getAsInt() == 2, "shared wings");
        long revision = response.get("revision").getAsLong();
        var watch = new JsonObject(); watch.add("players", new JsonArray()); watch.addProperty("revision", revision);
        var notification = CompletableFuture.supplyAsync(() -> post("/v1/watch", watch, tokenB));
        Thread.sleep(150);
        post("/v1/presence", presence(a, false, 1), tokenA);
        check(notification.get(10, TimeUnit.SECONDS).get("revision").getAsLong() > revision, "live update notification");
        response = post("/v1/presence", presence(a, false, 0), tokenB);
        check(response.getAsJsonArray("halos").asList().stream().noneMatch(value -> compact.equals(value.getAsString())), "halo disabled live");
        check(response.getAsJsonObject("wings").get(compact).getAsInt() == 1, "wing style updated live");
        System.out.println("HTTPS Java cosmetics: Ed25519 handshake, badges, halos, wing styles and live updates passed");
    }
    private static void check(boolean value, String description) { if (!value) throw new AssertionError(description); }
}
