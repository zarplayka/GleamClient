package ru.orion.client.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Один отменяемый запрос, ограниченные RAM/дисковый кеши, никаких сетевых операций в HUD. */
public final class GleamLyricsService implements AutoCloseable {
    public record Track(String source, String title, String artist, String album, double duration) {
        String signature() { return title + '\0' + artist + '\0' + album + '\0' + Math.round(duration); }
        String identity() { return source + '\0' + signature(); }
    }
    public record Snapshot(long revision, String track, GleamLyrics lyrics) { }
    private record Cached(long expires, GleamLyrics lyrics) { }
    private final Path directory;
    private final URI endpoint;
    private volatile HttpClient http;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("gleam-lyrics").factory());
    private final LinkedHashMap<String, Cached> memory = new LinkedHashMap<>(32, .75F, true);
    private volatile Snapshot snapshot = new Snapshot(0, "", GleamLyrics.empty("off"));
    private String selected = "";
    private long generation, revision, retryAfter;
    private ScheduledFuture<?> pending;
    private boolean closed;

    public GleamLyricsService(Path directory) { this(directory, URI.create("https://lrclib.net/api/get")); }
    GleamLyricsService(Path directory, URI endpoint) { this.directory = directory; this.endpoint = endpoint; }
    public Snapshot snapshot() { return snapshot; }

    public synchronized void select(Track track, boolean enabled) {
        if (closed) return;
        String identity = enabled && track != null ? track.identity() : "";
        if (identity.equals(selected)) return;
        selected = identity; long token = ++generation;
        if (pending != null) pending.cancel(true);
        if (identity.isEmpty()) { publish(identity, GleamLyrics.empty("off")); return; }
        if (track.title().isBlank() || track.artist().isBlank() || track.title().length() > 512 || track.artist().length() > 512 || track.album().length() > 512) {
            publish(identity, GleamLyrics.empty("metadata")); return;
        }
        publish(identity, GleamLyrics.empty("loading"));
        pending = worker.schedule(() -> load(track, token), Math.max(0, retryAfter - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
    }

    private void load(Track track, long token) {
        try {
            String key = hash(track.signature());
            Cached cached = memory.get(key);
            if (cached == null) cached = read(key);
            if (cached != null && cached.expires() > System.currentTimeMillis()) {
                put(key, cached); complete(token, track, cached.lyrics()); return;
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + "?" + query(track))).timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "GleamClient/0.2.3 (https://gleamclient.xyz)")
                    .header("Accept", "application/json").GET().build();
            var download = client().sendAsync(request, HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), 1_000_000));
            HttpResponse<byte[]> response;
            try { response = download.get(10, TimeUnit.SECONDS); }
            finally { if (!download.isDone()) download.cancel(true); }
            GleamLyrics lyrics;
            if (response.statusCode() == 429 || response.statusCode() == 503) {
                long delay = retryDelay(response.headers().firstValue("Retry-After").orElse("60"));
                synchronized (this) {
                    retryAfter = System.currentTimeMillis() + delay;
                    if (token == generation && !closed) {
                        publish(track.identity(), GleamLyrics.empty("rate_limited"));
                        pending = worker.schedule(() -> load(track, token), delay, TimeUnit.MILLISECONDS);
                    }
                }
                return;
            }
            if (response.statusCode() == 404) lyrics = GleamLyrics.empty("missing");
            else {
                if (response.statusCode() != 200) throw new IOException("Lyrics HTTP " + response.statusCode());
                if (!response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT).contains("json"))
                    throw new IOException("Unexpected lyrics response");
                lyrics = GleamLyrics.parse(JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject());
            }
            if (Thread.currentThread().isInterrupted()) return;
            long ttl = lyrics.status().equals("ready") || lyrics.status().equals("instrumental") ? 30L * 86400000 : 6L * 3600000;
            Cached entry = new Cached(System.currentTimeMillis() + ttl, lyrics);
            put(key, entry);
            try { write(key, entry); } catch (IOException ignored) { /* Кеш необязателен для воспроизведения. */ }
            complete(token, track, lyrics);
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        catch (Exception failure) {
            synchronized (this) {
                if (token == generation && !closed) {
                    publish(track.identity(), GleamLyrics.empty("offline"));
                    pending = worker.schedule(() -> load(track, token), 60, TimeUnit.SECONDS);
                }
            }
        }
    }

    private synchronized void complete(long token, Track track, GleamLyrics lyrics) {
        if (!closed && token == generation && selected.equals(track.identity())) publish(track.identity(), lyrics);
    }
    private HttpClient client() throws InterruptedException {
        if (http != null) return http;
        var created = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        synchronized (this) {
            if (closed) { created.shutdownNow(); throw new InterruptedException(); }
            return http = created;
        }
    }
    private void publish(String track, GleamLyrics lyrics) { snapshot = new Snapshot(++revision, track, lyrics); }
    private void put(String key, Cached value) {
        memory.put(key, value);
        while (memory.size() > 32) memory.remove(memory.keySet().iterator().next());
    }
    private Cached read(String key) {
        try {
            Path path = directory.resolve(key + ".json");
            if (Files.size(path) > 1_000_000) return null;
            var root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1 || root.get("expires").getAsLong() <= System.currentTimeMillis()) return null;
            var lyrics = new com.google.gson.Gson().fromJson(root.get("lyrics"), GleamLyrics.class);
            if (lyrics == null || lyrics.lines() == null || lyrics.lines().size() > 2000) return null;
            lyrics.json(); // Проверяет структуру локального кеша до передачи в HUD.
            return new Cached(root.get("expires").getAsLong(), lyrics);
        } catch (Exception invalid) { return null; }
    }
    private void write(String key, Cached entry) throws IOException {
        Files.createDirectories(directory);
        var root = new JsonObject(); root.addProperty("schema", 1); root.addProperty("expires", entry.expires());
        root.add("lyrics", new com.google.gson.Gson().toJsonTree(entry.lyrics()));
        Path temp = Files.createTempFile(directory, "lyrics-", ".tmp");
        try {
            Files.writeString(temp, root.toString());
            Files.move(temp, directory.resolve(key + ".json"), StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
        try (var paths = Files.list(directory)) {
            List<Path> files = paths.filter(p -> p.getFileName().toString().matches("[a-f0-9]{64}\\.json"))
                    .sorted(java.util.Comparator.comparingLong(p -> p.toFile().lastModified())).toList();
            for (int i = 0; i < files.size() - 128; i++) Files.deleteIfExists(files.get(i));
        }
    }
    static String url(Track track) {
        return "https://lrclib.net/api/get?" + query(track);
    }
    private static String query(Track track) {
        String query = "track_name=" + encode(track.title()) + "&artist_name=" + encode(track.artist());
        if (!track.album().isBlank()) query += "&album_name=" + encode(track.album());
        if (Double.isFinite(track.duration()) && track.duration() >= 1 && track.duration() <= 3600)
            query += "&duration=" + Math.round(track.duration());
        return query;
    }
    static long retryDelay(String value) {
        long seconds;
        try { seconds = Math.max(1, Long.parseLong(value)); }
        catch (NumberFormatException invalid) {
            try { seconds = Math.max(1, java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toEpochSecond() - java.time.Instant.now().getEpochSecond()); }
            catch (RuntimeException badDate) { seconds = 60; }
        }
        return Math.min(seconds, 86400) * 1000 + java.util.concurrent.ThreadLocalRandom.current().nextLong(301);
    }
    private static String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    @Override public synchronized void close() {
        closed = true; generation++; if (pending != null) pending.cancel(true); worker.shutdownNow();
        if (http != null) http.shutdownNow();
    }
}
