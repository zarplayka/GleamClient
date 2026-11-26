package ru.orion.client.replay;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/** Serves the owned WebView only while its window is open. Clip paths never cross the browser boundary. */
final class ReplayWindow implements AutoCloseable {
    private static final String ASSETS = "/assets/orion-client/";
    private static final String HTML = "pause-preview.html";
    private static final Pattern FILE = Pattern.compile("media/((?:Replay|Recording)-[a-f0-9-]{8,48})[.](mp4|jpg)");
    private static final Pattern RANGE = Pattern.compile("bytes=([0-9]*)-([0-9]*)");
    private static final Set<String> ACTIONS = Set.of("buffer", "save", "record", "settings", "bindings", "favorite", "remove", "trim", "folder", "close");
    private static final Map<String, String> STATIC = Map.of(
            "replay-preview.js", "replay/replay-preview.js",
            "fonts/paradox/ParadoxSans-Regular.ttf", "fonts/paradox/ParadoxSans-Regular.ttf",
            "fonts/paradox/ParadoxSans-SemiBold.ttf", "fonts/paradox/ParadoxSans-SemiBold.ttf",
            "fonts/paradox/ParadoxSans-Bold.ttf", "fonts/paradox/ParadoxSans-Bold.ttf",
            "textures/cosmetics/orion_badge.png", "textures/cosmetics/orion_badge.png");
    private final GleamReplay controller;
    private final HttpServer server;
    private final ExecutorService requests = Executors.newVirtualThreadPerTaskExecutor();
    private final String prefix;
    private final String origin;
    private final byte[] document;

    ReplayWindow(GleamReplay controller) throws IOException {
        this.controller = controller;
        var nonce = new byte[24];
        new java.security.SecureRandom().nextBytes(nonce);
        prefix = "/" + HexFormat.of().formatHex(nonce) + "/";
        document = new String(resource("replay/" + HTML), StandardCharsets.UTF_8)
                .replace("/src/main/resources/assets/orion-client/", "")
                .getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 16);
        origin = "http://127.0.0.1:" + server.getAddress().getPort();
        server.setExecutor(requests);
        server.createContext(prefix, this::serve);
        server.start();
    }

    String url() { return origin + prefix + HTML + "?panel=replays&client=1"; }

    private void serve(HttpExchange exchange) throws IOException {
        try (exchange) {
            var headers = exchange.getResponseHeaders();
            headers.set("Cache-Control", "no-store");
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("Referrer-Policy", "no-referrer");
            headers.set("Cross-Origin-Resource-Policy", "same-origin");
            headers.set("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
                    "connect-src 'self'; img-src 'self' data:; font-src 'self'; media-src 'self'; frame-ancestors 'none'; base-uri 'none'");
            String host = exchange.getRequestHeaders().getFirst("Host");
            if (!URI.create(origin).getRawAuthority().equals(host)) { empty(exchange, 403); return; }
            String path = exchange.getRequestURI().getPath().substring(prefix.length());
            String method = exchange.getRequestMethod();
            if ("POST".equals(method)) {
                if (!path.equals("api/action") || !origin.equals(exchange.getRequestHeaders().getFirst("Origin")) ||
                        !"application/json".equals(exchange.getRequestHeaders().getFirst("Content-Type"))) {
                    empty(exchange, 403);
                    return;
                }
                var body = exchange.getRequestBody().readNBytes(8193);
                if (body.length > 8192) { empty(exchange, 413); return; }
                try {
                    var json = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
                    String command = json.get("action").getAsString();
                    if (!ACTIONS.contains(command)) { empty(exchange, 400); return; }
                    controller.submit(command, json.has("values") ? json.getAsJsonObject("values") : new com.google.gson.JsonObject());
                    empty(exchange, 204);
                } catch (RuntimeException invalid) { empty(exchange, 400); }
                return;
            }
            if (!"GET".equals(method) && !"HEAD".equals(method)) { empty(exchange, 405); return; }
            if (path.equals(HTML)) { bytes(exchange, "text/html; charset=utf-8", document); return; }
            if (path.equals("api/state")) {
                bytes(exchange, "application/json; charset=utf-8", controller.snapshot().toString().getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (STATIC.containsKey(path)) {
                String type = path.endsWith(".js") ? "application/javascript; charset=utf-8" :
                        path.endsWith(".ttf") ? "font/ttf" : "image/png";
                bytes(exchange, type, resource(STATIC.get(path)));
                return;
            }
            var file = FILE.matcher(path);
            if (file.matches()) {
                boolean video = file.group(2).equals("mp4");
                Path media = video ? controller.media(file.group(1)) : controller.thumbnail(file.group(1));
                if (media == null || !Files.isRegularFile(media, LinkOption.NOFOLLOW_LINKS)) { empty(exchange, 404); return; }
                stream(exchange, media, video ? "video/mp4" : "image/jpeg");
                return;
            }
            empty(exchange, 404);
        }
    }

    private static void stream(HttpExchange exchange, Path path, String contentType) throws IOException {
        try (var file = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = file.size(), from = 0, to = size - 1;
            int status = 200;
            var headers = exchange.getResponseHeaders();
            headers.set("Accept-Ranges", "bytes");
            headers.set("Content-Type", contentType);
            String requested = exchange.getRequestHeaders().getFirst("Range");
            if (requested != null) {
                var parsed = RANGE.matcher(requested);
                try {
                    if (!parsed.matches()) throw new IllegalArgumentException();
                    if (parsed.group(1).isEmpty()) from = Math.max(0, size - Long.parseLong(parsed.group(2)));
                    else {
                        from = Long.parseLong(parsed.group(1));
                        if (!parsed.group(2).isEmpty()) to = Math.min(to, Long.parseLong(parsed.group(2)));
                    }
                    if (from > to) throw new IllegalArgumentException();
                } catch (IllegalArgumentException invalid) {
                    headers.set("Content-Range", "bytes */" + size);
                    empty(exchange, 416);
                    return;
                }
                status = 206;
                headers.set("Content-Range", "bytes " + from + "-" + to + "/" + size);
            }
            long length = to - from + 1;
            if ("HEAD".equals(exchange.getRequestMethod()) || length == 0) {
                headers.set("Content-Length", Long.toString(length));
                empty(exchange, status);
                return;
            }
            exchange.sendResponseHeaders(status, length);
            file.position(from);
            ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(64 * 1024, length));
            var output = exchange.getResponseBody();
            while (length > 0) {
                buffer.clear().limit((int) Math.min(buffer.capacity(), length));
                int count = file.read(buffer);
                if (count < 0) throw new EOFException("Clip changed during playback");
                output.write(buffer.array(), 0, count);
                length -= count;
            }
        }
    }

    private static byte[] resource(String name) throws IOException {
        try (var stream = ReplayWindow.class.getResourceAsStream(ASSETS + name)) {
            if (stream == null) throw new IOException("Missing replay resource " + name);
            return stream.readAllBytes();
        }
    }

    private static void bytes(HttpExchange exchange, String type, byte[] contents) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        if ("HEAD".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Content-Length", Integer.toString(contents.length));
            empty(exchange, 200);
        } else {
            exchange.sendResponseHeaders(200, contents.length);
            exchange.getResponseBody().write(contents);
        }
    }

    private static void empty(HttpExchange exchange, int status) throws IOException { exchange.sendResponseHeaders(status, -1); }

    @Override public void close() {
        server.stop(0);
        requests.shutdownNow();
    }
}
