package ru.orion.client.replay;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

final class GleamReplayYouTube {
    static final String DEFAULT_TEMPLATE = "{link}";
    private static final String CLIENT_FILE = "gleam-youtube-client.json";
    private static final Gson JSON = new Gson();
    private static final URI TOKEN = URI.create("https://oauth2.googleapis.com/token");
    private static final URI UPLOAD = URI.create("https://www.googleapis.com/upload/youtube/v3/videos"
            + "?uploadType=resumable&part=snippet,status&notifySubscribers=false");
    private static final String SCOPE = "https://www.googleapis.com/auth/youtube.upload";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CHUNK_SIZE = 8 * 1024 * 1024;

    private final Path directory;
    private Credentials credentials;
    private String accessToken;
    private long expiresAt;
    private volatile boolean connected;

    static final class Failure extends IOException {
        Failure(String message) { super(message); }
    }

    private record Credentials(String clientId, String secret) {}

    GleamReplayYouTube(Path directory) { this.directory = directory; }

    record Publication(String id, String privacy) {}

    boolean connected() { return connected; }

    void load() { connected = Files.isRegularFile(tokenFile()); }

    void openConfig() throws IOException {
        Files.createDirectories(directory);
        //? if >=26.3 {
        com.mojang.blaze3d.Blaze3D.openPath(directory);
        //?} else {
        /*net.minecraft.util.Util.getPlatform().openPath(directory);
        *///?}
    }

    void disconnect() throws IOException {
        Files.deleteIfExists(tokenFile());
        accessToken = null;
        credentials = null;
        connected = false;
    }

    void connect() throws Exception {
        Credentials client = readCredentials();
        String state = randomUrlToken();
        String verifier = randomUrlToken();
        String challenge = base64(sha256(verifier));
        var completed = new CompletableFuture<String>();
        HttpServer server;
        try { server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); }
        catch (IOException error) { throw new Failure("Не удалось запустить локальное подключение Google"); }
        String redirect = "http://127.0.0.1:" + server.getAddress().getPort() + "/oauth2callback";
        server.createContext("/oauth2callback", exchange -> {
            String code = null;
            try (exchange) {
                var params = query(exchange.getRequestURI().getRawQuery());
                boolean valid = "GET".equals(exchange.getRequestMethod())
                        && "/oauth2callback".equals(exchange.getRequestURI().getPath())
                        && state.equals(params.get("state"));
                String response;
                if (!valid) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                code = params.getOrDefault("code", "");
                response = code.isEmpty() ? "Authorization declined. Return to Minecraft."
                        : "Google responded. Return to Minecraft to check the connection.";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally {
                if (code != null) completed.complete(code);
            }
        });
        try {
            server.start();
            URI authorization = URI.create("https://accounts.google.com/o/oauth2/v2/auth?" + encode(Map.of(
                    "client_id", client.clientId(), "redirect_uri", redirect, "response_type", "code", "scope", SCOPE,
                    "code_challenge", challenge, "code_challenge_method", "S256", "access_type", "offline",
                    "state", state, "prompt", "consent select_account")));
            //? if >=26.3 {
            com.mojang.blaze3d.Blaze3D.openUri(authorization);
            //?} else {
            /*net.minecraft.util.Util.getPlatform().openUri(authorization);
            *///?}
            String code;
            try { code = completed.get(3, TimeUnit.MINUTES); }
            catch (TimeoutException timeout) { throw new Failure("Google не завершил вход за 3 минуты. Повтори подключение"); }
            if (code.isEmpty()) throw new Failure("Вход в Google отменён");
            JsonObject tokens = tokenRequest(client, Map.of("grant_type", "authorization_code", "code", code,
                    "redirect_uri", redirect, "code_verifier", verifier));
            String refresh = string(tokens, "refresh_token");
            if (refresh.isEmpty()) throw new Failure("Google не выдал токен для фоновой загрузки");
            var stored = new JsonObject();
            stored.addProperty("clientId", client.clientId());
            stored.addProperty("refresh", refresh);
            stored.addProperty("connection", randomUrlToken());
            writeJson(tokenFile(), stored);
            cacheToken(client, tokens);
            connected = true;
        } finally {
            server.stop(0);
        }
    }

    Publication upload(Path video, GleamReplay.Clip clip, String privacy) throws IOException, InterruptedException {
        Path receipt = receipt(video);
        JsonObject saved = readReceipt(video, clip.revision());
        Publication published = publication(saved);
        if (published != null) return published;

        Credentials client = readCredentials();
        if (!Files.isRegularFile(tokenFile())) throw new Failure("Подключи YouTube в настройках реплеев");
        JsonObject stored = readJson(tokenFile());
        String connection = string(stored, "connection");
        if (!client.clientId().equals(string(stored, "clientId")) || connection.isEmpty())
            throw new Failure("Подключи YouTube заново");
        String bearer = accessToken(client, stored);
        long size = Files.size(video);
        if (size <= 0) throw new Failure("Видеофайл пуст");

        String previousSession = connection.equals(string(saved, "connection")) ? string(saved, "session") : "";
        URI session = previousSession.isEmpty() ? null : sessionUri(previousSession);
        long offset = 0;
        if (session != null) {
            var status = HTTP.send(authenticated(session, bearer)
                    .header("Content-Range", "bytes */" + size)
                    .PUT(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            if (success(status)) return finish(receipt, clip.revision(), status);
            if (status.statusCode() == 308) offset = nextOffset(status, size);
            else if (status.statusCode() == 404 || status.statusCode() == 410) session = null;
            else throw apiError("YouTube", status);
        }
        if (session == null) {
            session = startSession(clip.title(), privacy, size, bearer);
            var active = new JsonObject();
            active.addProperty("revision", clip.revision());
            active.addProperty("session", session.toString());
            active.addProperty("connection", connection);
            writeJson(receipt, active);
        }

        try (var file = new java.io.RandomAccessFile(video.toFile(), "r")) {
            while (offset < size) {
                var chunk = new byte[(int) Math.min(CHUNK_SIZE, size - offset)];
                file.seek(offset);
                file.readFully(chunk);
                var response = HTTP.send(authenticated(session, bearer).timeout(Duration.ofMinutes(2))
                        .header("Content-Type", "video/mp4")
                        .header("Content-Range", "bytes " + offset + "-" + (offset + chunk.length - 1) + "/" + size)
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(chunk)).build(), HttpResponse.BodyHandlers.ofString());
                if (success(response)) return finish(receipt, clip.revision(), response);
                if (response.statusCode() != 308) throw apiError("YouTube", response);
                long next = nextOffset(response, size);
                if (next <= offset || next > offset + chunk.length) break;
                offset = next;
            }
        }
        throw new Failure("YouTube не подтвердил загрузку. Нажми «Опубликовать», чтобы продолжить");
    }

    private URI startSession(String title, String privacy, long size, String token) throws IOException, InterruptedException {
        var body = new JsonObject();
        var snippet = new JsonObject();
        snippet.addProperty("title", title);
        snippet.addProperty("categoryId", "20");
        var status = new JsonObject();
        status.addProperty("privacyStatus", privacy);
        body.add("snippet", snippet);
        body.add("status", status);
        var response = HTTP.send(authenticated(UPLOAD, token)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("X-Upload-Content-Type", "video/mp4")
                .header("X-Upload-Content-Length", Long.toString(size))
                .POST(HttpRequest.BodyPublishers.ofString(JSON.toJson(body))).build(), HttpResponse.BodyHandlers.ofString());
        if (!success(response)) throw apiError("YouTube", response);
        return sessionUri(response.headers().firstValue("Location").orElse(""));
    }

    static JsonObject readReceipt(Path video, long revision) {
        try {
            var saved = readJson(receipt(video));
            if (saved.has("revision") && saved.get("revision").getAsLong() == revision) return saved;
        } catch (IOException | RuntimeException ignored) { }
        return new JsonObject();
    }

    static Publication publication(JsonObject saved) {
        String id = string(saved, "id");
        return id.matches("[A-Za-z0-9_-]{11}") ? new Publication(id, string(saved, "privacy")) : null;
    }

    static Path receipt(Path video) { return video.resolveSibling(video.getFileName() + ".youtube.json"); }

    static String template(String value) {
        String trimmed = value.strip();
        return trimmed.length() <= 240 && (trimmed.contains("{link}") || trimmed.contains("{url}")
                || trimmed.contains("{short}") || trimmed.contains("{id}")) ? trimmed : DEFAULT_TEMPLATE;
    }

    static String format(String template, String id) {
        String link = "https://www.youtube.com/watch?v=" + id;
        return template.replace("{link}", link).replace("{url}", link)
                .replace("{short}", "https://youtu.be/" + id).replace("{id}", id);
    }

    private Publication finish(Path receipt, long revision, HttpResponse<String> response) throws IOException {
        var data = responseJson(response);
        var saved = new JsonObject();
        saved.addProperty("revision", revision);
        saved.addProperty("id", string(data, "id"));
        if (data.has("status") && data.get("status").isJsonObject())
            saved.addProperty("privacy", string(data.getAsJsonObject("status"), "privacyStatus"));
        var result = publication(saved);
        if (result == null) throw new Failure("YouTube не вернул ID видео");
        try { writeJson(receipt, saved); }
        catch (IOException ignored) { /* The stored session can recover the completed video on retry. */ }
        return result;
    }

    private String accessToken(Credentials client, JsonObject stored) throws IOException, InterruptedException {
        if (client.equals(credentials) && accessToken != null && System.nanoTime() < expiresAt) return accessToken;
        String refresh = string(stored, "refresh");
        if (refresh.isEmpty()) throw new Failure("Подключи YouTube заново");
        var tokens = tokenRequest(client, Map.of("grant_type", "refresh_token", "refresh_token", refresh));
        cacheToken(client, tokens);
        return accessToken;
    }

    private void cacheToken(Credentials client, JsonObject tokens) throws IOException {
        String token = string(tokens, "access_token");
        if (token.isEmpty()) throw new Failure("Google не вернул токен доступа");
        credentials = client;
        accessToken = token;
        int seconds = tokens.has("expires_in") ? tokens.get("expires_in").getAsInt() : 0;
        expiresAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.clamp(seconds - 60, 0, 3600));
    }

    private static JsonObject tokenRequest(Credentials client, Map<String, String> params) throws IOException, InterruptedException {
        var fields = new HashMap<>(params);
        fields.put("client_id", client.clientId());
        fields.put("client_secret", client.secret());
        var request = HttpRequest.newBuilder(TOKEN).timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encode(fields))).build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (!success(response)) throw apiError("Google OAuth", response);
        return responseJson(response);
    }

    private Credentials readCredentials() throws IOException {
        Path file = directory.resolve(CLIENT_FILE);
        if (!Files.isRegularFile(file)) throw new Failure("Помести OAuth JSON Desktop App в config/" + CLIENT_FILE);
        try {
            JsonObject installed = readJson(file).getAsJsonObject("installed");
            String clientId = string(installed, "client_id"), secret = string(installed, "client_secret");
            if (clientId.endsWith(".apps.googleusercontent.com") && !secret.isEmpty()) return new Credentials(clientId, secret);
        } catch (RuntimeException ignored) { }
        throw new Failure("Требуется OAuth JSON Google Desktop App");
    }

    private Path tokenFile() { return directory.resolve("gleam-youtube-token.json"); }

    private static HttpRequest.Builder authenticated(URI uri, String bearer) {
        return HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).header("Authorization", "Bearer " + bearer);
    }

    private static URI sessionUri(String raw) throws IOException {
        try {
            URI uri = URI.create(raw);
            String host = uri.getHost();
            if ("https".equals(uri.getScheme()) && host != null
                    && (host.equals("googleapis.com") || host.endsWith(".googleapis.com"))
                    && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)) return uri;
        } catch (IllegalArgumentException ignored) { }
        throw new Failure("YouTube вернул недопустимый адрес загрузки");
    }

    private static long nextOffset(HttpResponse<?> response, long size) throws IOException {
        String range = response.headers().firstValue("Range").orElse("");
        if (range.isEmpty()) return 0;
        if (range.matches("bytes=0-[0-9]{1,18}")) {
            long next = Long.parseLong(range.substring(8)) + 1;
            if (next <= size) return next;
        }
        throw new Failure("YouTube вернул недопустимый диапазон загрузки");
    }

    private static boolean success(HttpResponse<?> response) {
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    private static Failure apiError(String service, HttpResponse<String> response) {
        String reason = "";
        try {
            var body = responseJson(response);
            var error = body.get("error");
            if (error != null && error.isJsonPrimitive()) reason = error.getAsString();
            else if (error != null && error.isJsonObject()) {
                var details = error.getAsJsonObject().getAsJsonArray("errors");
                if (details != null && !details.isEmpty()) reason = string(details.get(0).getAsJsonObject(), "reason");
            }
        } catch (IOException | RuntimeException ignored) { }
        return new Failure(service + " · HTTP " + response.statusCode()
                + (reason.matches("[A-Za-z0-9_]{1,60}") ? " · " + reason : ""));
    }

    private static JsonObject responseJson(HttpResponse<String> response) throws IOException {
        try { return JsonParser.parseString(response.body()).getAsJsonObject(); }
        catch (RuntimeException invalid) { throw new Failure("Google вернул неверный ответ"); }
    }

    private static JsonObject readJson(Path file) throws IOException {
        if (Files.size(file) > 16_384) throw new Failure("Слишком большой файл настроек YouTube");
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static void writeJson(Path file, JsonObject object) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "gleam-youtube-", ".tmp");
        try {
            Files.writeString(temp, JSON.toJson(object));
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }

    private static String string(JsonObject object, String key) {
        var value = object == null ? null : object.get(key);
        return value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() ? "" : value.getAsString();
    }

    private static String encode(Map<String, String> fields) {
        return fields.entrySet().stream().map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static Map<String, String> query(String raw) {
        var result = new HashMap<String, String>();
        if (raw != null) for (String entry : raw.split("&")) {
            String[] pair = entry.split("=", 2);
            if (pair.length == 2) result.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return result;
    }

    private static String randomUrlToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return base64(bytes);
    }

    private static String base64(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }

    private static byte[] sha256(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)); }
        catch (NoSuchAlgorithmException error) { throw new AssertionError(error); }
    }
}
