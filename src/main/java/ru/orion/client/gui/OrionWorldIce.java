package ru.orion.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Platform;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Connects one authenticated invite to the packaged direct WebRTC transport. */
final class OrionWorldIce implements Closeable {
    private static final String RESOURCE = "/assets/orion-client/native/gleam-world-ice.exe";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static Path executable;

    private final ServerSocket bridge;
    private final Process process;
    private final BufferedWriter input;
    private final byte[] secret;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile boolean attached;
    private String description;

    private OrionWorldIce(ServerSocket bridge, Process process, byte[] secret) {
        this.bridge = bridge;
        this.process = process;
        this.secret = secret;
        input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    }

    static boolean available() {
        return Platform.isWindows() && OrionWorldIce.class.getResource(RESOURCE) != null;
    }

    private static synchronized Path executable() throws IOException {
        if (executable != null) return executable;
        try (var resource = OrionWorldIce.class.getResourceAsStream(RESOURCE)) {
            if (resource == null) throw new IOException("Direct UDP helper unavailable");
            var directory = Files.createTempDirectory("gleam-world-ice-");
            directory.toFile().deleteOnExit();
            var file = directory.resolve("gleam-world-ice.exe");
            file.toFile().deleteOnExit();
            Files.copy(resource, file);
            executable = file;
            return file;
        }
    }

    static OrionWorldIce start(String offer) throws IOException {
        var socket = new ServerSocket();
        OrionWorldIce ice = null;
        try {
            socket.bind(new InetSocketAddress("127.0.0.1", 0), 1);
            byte[] nonce = new byte[16];
            RANDOM.nextBytes(nonce);
            String secret = HexFormat.of().formatHex(nonce);
            var child = new ProcessBuilder(executable().toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            ice = new OrionWorldIce(socket, child, secret.getBytes(StandardCharsets.US_ASCII));
            var start = new JsonObject();
            start.addProperty("port", socket.getLocalPort());
            start.addProperty("secret", secret);
            if (offer != null) start.addProperty("offer", offer);
            ice.send(start.toString());
            ice.description = readDescription(child);
            return ice;
        } catch (IOException | RuntimeException failure) {
            OrionWorldTunnel.closeSocket(ice);
            OrionWorldTunnel.closeSocket(socket);
            throw failure;
        }
    }

    private static String readDescription(Process child) throws IOException {
        var reply = new CompletableFuture<String>();
        Thread.startVirtualThread(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line == null || line.length() > 3800) throw new IOException("Invalid ICE helper response");
                reply.complete(JsonParser.parseString(line).getAsString());
            } catch (IOException | RuntimeException failure) {
                reply.completeExceptionally(failure);
            }
        });
        try {
            return reply.get(4, TimeUnit.SECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("ICE gathering interrupted", failure);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IOException("ICE gathering failed", failure);
        }
    }

    String description() { return description; }

    void answer(String sdp) throws IOException {
        send(new com.google.gson.JsonPrimitive(sdp).toString());
    }

    private void send(String line) throws IOException {
        input.write(line);
        input.newLine();
        input.flush();
    }

    Socket connect() throws IOException {
        bridge.setSoTimeout(16_000);
        var socket = bridge.accept();
        try {
            socket.setSoTimeout(2000);
            if (!MessageDigest.isEqual(secret, socket.getInputStream().readNBytes(secret.length)))
                throw new IOException("Invalid ICE bridge credentials");
            attached = true;
            return socket;
        } catch (IOException failure) {
            OrionWorldTunnel.closeSocket(socket);
            throw failure;
        }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        OrionWorldTunnel.closeSocket(bridge);
        if (!attached) {
            stop();
            return;
        }
        // The helper can still have data queued in SCTP after the loopback TLS socket closes.
        Thread.startVirtualThread(() -> {
            try {
                process.waitFor(4, TimeUnit.SECONDS);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
            } finally {
                stop();
            }
        });
    }

    private void stop() {
        process.destroy();
        OrionWorldTunnel.closeSocket(input);
    }
}
