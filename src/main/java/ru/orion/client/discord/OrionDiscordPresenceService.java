package ru.orion.client.discord;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

public final class OrionDiscordPresenceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrionClient.MOD_ID);
    private static final Gson GSON = new Gson();
    private static final String APPLICATION = "1520703983163932832";
    private static final String SITE = "https://gleamclient.xyz/";
    private static final long UPDATE_INTERVAL_NANOS = 4_200_000_000L;
    private final OrionConfigManager configManager;

    private volatile Snapshot requested;
    private volatile boolean stopping;
    private final Relay relay = Relay.fromEnvironment();
    private Thread worker;

    public OrionDiscordPresenceService(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::capture);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            stopping = true;
            if (worker != null) worker.interrupt();
        });
        capture(Minecraft.getInstance());
        worker = Thread.ofPlatform().daemon().name("GleamClient Discord RPC").start(this::run);
    }

    public void applyConfigNow() {
        capture(Minecraft.getInstance());
    }

    private void capture(Minecraft client) {
        var config = configManager.getConfig();
        Mode mode = client == null || client.level == null ? Mode.MENU
                : client.getSingleplayerServer() == null ? Mode.MULTIPLAYER : Mode.SINGLEPLAYER;
        requested = new Snapshot(config.isDiscordRpcEnabled(), mode, config.getDiscordApplicationId().trim(),
                config.getDiscordLargeImageKey(), config.getDiscordLargeImageText());
    }

    private void run() {
        long started = relay == null ? System.currentTimeMillis() / 1000L : relay.started();
        long updateAt = 0;
        Snapshot published = null;
        RelayState relayed = null;
        DiscordIpcConnection connection = null;
        String application = "";
        try {
            while (!stopping) {
                Snapshot current = requested;
                boolean launcherAlive = relay != null && relay.ownerAlive();
                if (launcherAlive) {
                    boolean coordinated = APPLICATION.equals(current.application());
                    var state = new RelayState(current.enabled() && coordinated, current.mode().wire);
                    if (!state.equals(relayed)) {
                        try {
                            relay.publish(state);
                            relayed = state;
                        } catch (IOException exception) {
                            launcherAlive = false;
                        }
                    }
                    launcherAlive &= coordinated;
                }
                if (launcherAlive || !current.enabled() || !current.application().equals(application)) {
                    if (connection != null) {
                        if (!current.enabled()) {
                            try { sendPresence(connection, null, started); } catch (IOException ignored) {}
                        }
                        close(connection);
                        connection = null;
                        published = null;
                    }
                }
                long now = System.nanoTime();
                if (!launcherAlive && current.enabled() && (updateAt == 0 || now - updateAt >= 0)
                        && (connection == null || !current.equals(published) || now - updateAt > 35_000_000_000L)) {
                    try {
                        if (connection == null) {
                            connection = DiscordIpcConnection.open();
                            sendHandshake(connection, current.application());
                            application = current.application();
                        }
                        sendPresence(connection, current, started);
                        published = current;
                        updateAt = now + UPDATE_INTERVAL_NANOS;
                    } catch (IOException | RuntimeException exception) {
                        close(connection);
                        connection = null;
                        updateAt = now + 8_000_000_000L;
                        LOGGER.debug("Discord activity could not be updated", exception);
                    }
                }
                Thread.sleep(250);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            close(connection);
        }
    }

    private static void sendHandshake(DiscordIpcConnection connection, String applicationId) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("v", 1);
        payload.addProperty("client_id", applicationId);
        connection.write(0, payload);
        waitFor(connection, response -> "READY".equals(string(response, "evt")));
    }

    private static void sendPresence(DiscordIpcConnection connection, Snapshot snapshot, long started) throws IOException {
        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        if (snapshot == null) {
            args.add("activity", JsonNull.INSTANCE);
        } else {
            JsonObject timestamps = new JsonObject();
            timestamps.addProperty("start", started);
            JsonObject assets = new JsonObject();
            String image = APPLICATION.equals(snapshot.application()) && "orion_icon".equals(snapshot.image())
                    ? SITE + "assets/orion-badge.png" : snapshot.image();
            assets.addProperty("large_image", image);
            assets.addProperty("large_text", snapshot.tooltip());
            assets.addProperty("large_url", SITE);
            JsonObject site = new JsonObject();
            site.addProperty("label", "Сайт GleamClient");
            site.addProperty("url", SITE);
            JsonArray buttons = new JsonArray();
            buttons.add(site);

            JsonObject activity = new JsonObject();
            activity.addProperty("type", 0);
            activity.addProperty("details", snapshot.mode().label);
            activity.addProperty("state", "Minecraft " + SharedConstants.getCurrentVersion().name());
            activity.add("timestamps", timestamps);
            activity.add("assets", assets);
            activity.add("buttons", buttons);
            args.add("activity", activity);
        }
        String nonce = UUID.randomUUID().toString();
        JsonObject payload = new JsonObject();
        payload.addProperty("cmd", "SET_ACTIVITY");
        payload.addProperty("nonce", nonce);
        payload.add("args", args);
        connection.write(1, payload);
        waitFor(connection, response -> nonce.equals(string(response, "nonce")));
    }

    private static void waitFor(DiscordIpcConnection connection,
                                java.util.function.Predicate<JsonObject> matches) throws IOException {
        for (int i = 0; i < 24; i++) {
            Frame frame = connection.read();
            if (frame.opcode() == 2) throw new EOFException("Discord disconnected");
            if (frame.opcode() == 3) {
                connection.write(4, frame.payload());
                continue;
            }
            if ("ERROR".equals(string(frame.payload(), "evt"))) throw new IOException("Discord rejected activity");
            if (matches.test(frame.payload())) return;
        }
        throw new IOException("Discord activity response missing");
    }

    private static String string(JsonObject object, String member) {
        var value = object.get(member);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static void close(DiscordIpcConnection channel) {
        if (channel == null) return;
        try { channel.close(); } catch (IOException ignored) {}
    }

    private enum Mode {
        MENU("menu", "Главное меню Minecraft"),
        SINGLEPLAYER("singleplayer", "Одиночная игра"),
        MULTIPLAYER("multiplayer", "Сетевая игра");

        final String wire;
        final String label;

        Mode(String wire, String label) {
            this.wire = wire;
            this.label = label;
        }
    }

    private record Snapshot(boolean enabled, Mode mode, String application, String image, String tooltip) {}
    private record RelayState(boolean enabled, String mode) {}

    private record Relay(long pid, Path file, long started) {
        static Relay fromEnvironment() {
            String location = System.getenv("GLEAM_DISCORD_STATE");
            String owner = System.getenv("GLEAM_DISCORD_OWNER");
            if (owner == null || location == null) return null;
            try {
                Path path = Path.of(location).normalize();
                if (!path.isAbsolute() || path.getParent() == null || path.getParent().getFileName() == null
                        || !"state.json".equals(path.getFileName().toString())
                        || !path.getParent().getFileName().toString().startsWith("gleam-discord-")) return null;
                long now = System.currentTimeMillis() / 1000L;
                String timestamp = System.getenv("GLEAM_DISCORD_STARTED");
                long start = timestamp != null && timestamp.matches("[0-9]{1,11}") ? Long.parseLong(timestamp) : now;
                return new Relay(Long.parseLong(owner), path, Math.min(now, Math.max(0, start)));
            } catch (InvalidPathException | NumberFormatException exception) {
                return null;
            }
        }

        boolean ownerAlive() {
            return pid > 0 && ProcessHandle.of(pid).filter(ProcessHandle::isAlive).isPresent();
        }

        void publish(RelayState status) throws IOException {
            Path temp = Files.createTempFile(file.getParent(), "state-", ".json");
            try {
                Files.writeString(temp, GSON.toJson(status), StandardCharsets.UTF_8);
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        }
    }

    private record Frame(int opcode, JsonObject payload) {}

    private record DiscordIpcConnection(ByteChannel channel) implements Closeable {
        static DiscordIpcConnection open() throws IOException {
            IOException last = new IOException("Discord IPC unavailable");
            for (int i = 0; i < 10; i++) {
                try {
                    if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                        return new DiscordIpcConnection(new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw").getChannel());
                    }
                    String name = "discord-ipc-" + i;
                    String runtime = System.getenv("XDG_RUNTIME_DIR");
                    Path socket = Path.of(runtime == null || runtime.isBlank() ? System.getProperty("java.io.tmpdir") : runtime, name);
                    if (!Files.exists(socket)) socket = Path.of(System.getProperty("java.io.tmpdir"), name);
                    return new DiscordIpcConnection(SocketChannel.open(UnixDomainSocketAddress.of(socket)));
                } catch (IOException exception) {
                    last = exception;
                }
            }
            throw last;
        }

        void write(int opcode, JsonObject payload) throws IOException {
            byte[] bytes = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
            ByteBuffer frame = ByteBuffer.allocate(8 + bytes.length).order(ByteOrder.LITTLE_ENDIAN);
            frame.putInt(opcode).putInt(bytes.length).put(bytes).flip();
            while (frame.hasRemaining()) channel.write(frame);
        }

        Frame read() throws IOException {
            ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            readFully(header);
            int opcode = header.getInt(), size = header.getInt();
            if (size < 0 || size > 65_536) throw new IOException("Invalid Discord IPC frame size");
            ByteBuffer payload = ByteBuffer.allocate(size);
            readFully(payload);
            return new Frame(opcode, size == 0 ? new JsonObject()
                    : GSON.fromJson(StandardCharsets.UTF_8.decode(payload).toString(), JsonObject.class));
        }

        private void readFully(ByteBuffer buffer) throws IOException {
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) throw new EOFException("Discord IPC disconnected");
            }
            buffer.flip();
        }

        @Override
        public void close() throws IOException { channel.close(); }
    }
}
