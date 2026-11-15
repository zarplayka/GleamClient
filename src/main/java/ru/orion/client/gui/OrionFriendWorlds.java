package ru.orion.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.config.OrionAccounts;

import javax.net.ssl.SSLSocket;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class OrionFriendWorlds {
    private static final Logger LOGGER = LoggerFactory.getLogger("GleamClient Friends");
    private static final OrionAccounts ACCOUNTS = OrionAccounts.INSTANCE;
    private static final String VERSION = SharedConstants.getCurrentVersion().name();
    private static final Map<String, HostPeer> PEERS = new ConcurrentHashMap<>();
    private static final Map<SocketAddress, HostPeer> LAN_LOGINS = new ConcurrentHashMap<>();
    private static final long JOIN_TIMEOUT = 18_000_000_000L;
    private static final ThreadLocal<Boolean> PRIVATE_LAN = new ThreadLocal<>();
    private static String account = "", status = "";
    private static volatile Host hosting;
    private static volatile boolean offlineAllowed;
    private static volatile Guest joining;
    private static JsonObject feed = new JsonObject();
    private static JsonArray visibleInvites = new JsonArray();
    private static String autojoinAttempt = "";
    private static boolean busy, polling, startupChecked, closingLan;
    private static long nextPoll, nextPresence;
    private static final HashSet<String> FINISHED = new HashSet<>();
    private static final LinkedHashSet<String> REPORTED = new LinkedHashSet<>();

    private record Host(IntegratedServer server, boolean ownLan, int lanPort, OrionWorldTunnel tunnel, JsonObject presence) {}
    private record HostPeer(Host world, long guestId, String pin, String name, boolean offline, long expires,
                            AtomicBoolean claimed, AtomicBoolean ready, CompletableFuture<Socket> socket, JsonObject endpoint,
                            AtomicReference<CompletableFuture<OrionWorldIce>> ice) {}
    private record Guest(String id, String username, OrionWorldTunnel tunnel, ServerSocket local, String account,
                         long deadline, AtomicBoolean claimed, CompletableFuture<Socket> socket,
                         OrionWorldIce ice, AtomicBoolean answered) {}

    private OrionFriendWorlds() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(OrionFriendWorlds::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> reset());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var peer = LAN_LOGINS.get(handler.getRemoteAddress());
            if (peer != null && peer.world().server() == server) peer.ready().set(true);
        });
        OrionWorldInvitation.register();
    }

    static JsonObject state(Minecraft client) {
        var state = new JsonObject();
        state.addProperty("canHost", client.getSingleplayerServer() != null && client.allowsMultiplayer());
        state.addProperty("hosting", hosting != null);
        if (hosting != null) state.addProperty("port", hosting.tunnel().port());
        state.addProperty("offline", hosting != null && offlineAllowed);
        state.addProperty("busy", busy || closingLan);
        state.addProperty("status", OrionGuiLanguage.translate(status));
        state.addProperty("version", VERSION);
        state.add("people", feed.has("people") ? feed.get("people") : new JsonObject());
        state.add("invites", visibleInvites);
        return state;
    }

    static void action(Minecraft client, String action, String target, boolean allowOffline) {
        if (busy || closingLan || !account.equals(ACCOUNTS.sessionCookieHeader()) || account.isEmpty()) return;
        switch (action) {
            case "open" -> open(client, allowOffline);
            case "close" -> {
                closeHost();
                request(presence(), "Доступ к миру закрыт");
            }
            case "invite" -> {
                if (hosting == null || !target.matches("[A-Za-z0-9_]{3,24}")) return;
                var body = command("invite", "");
                body.addProperty("username", target);
                request(body, "Приглашение отправлено");
            }
            case "accept" -> accept(client, target);
            case "cancel", "decline" -> {
                if (target.matches("[A-Za-z0-9_-]{32}")) request(command(action, target), "");
            }
        }
    }

    private static void tick(Minecraft client) {
        String session = ACCOUNTS.sessionCookieHeader();
        if (!session.equals(account)) {
            reset();
            account = session;
        }
        if (session.isEmpty()) return;
        if (!startupChecked) {
            startupChecked = true;
            String id = System.getenv("GLEAM_WORLD_INVITE");
            if (id != null && id.matches("[A-Za-z0-9_-]{32}")) {
                request(null, "", () -> accept(client, id));
                return;
            }
        }
        if (hosting != null && (client.getSingleplayerServer() != hosting.server() || !hosting.server().isPublished())) {
            closeHost();
            nextPresence = 0;
        }
        long now = System.nanoTime();
        var pending = joining;
        if (pending != null && now >= pending.deadline() && pending.claimed().compareAndSet(false, true)) {
            closeGuest(pending);
            LOGGER.warn("No direct route to the invited world before timeout (UDP attempted: {})", pending.ice() != null);
            showRouteFailure(client, pending);
        }
        PEERS.entrySet().removeIf(e -> {
            var peer = e.getValue();
            if (peer.claimed().get() || now < peer.expires()) return false;
            closePeer(peer);
            FINISHED.add(e.getKey());
            return true;
        });
        if (!polling && !busy && now >= nextPoll) {
            boolean heartbeat = now >= nextPresence;
            if (heartbeat) nextPresence = now + 5_000_000_000L;
            nextPoll = now + (hosting != null || joining != null ? 900_000_000L : 1_600_000_000L);
            poll(client, heartbeat ? presence() : null);
        }
        OrionWorldInvitation.tick(client);
    }

    private static void poll(Minecraft client, JsonObject body) {
        String session = account;
        polling = true;
        ACCOUNTS.friendWorlds(body).whenComplete((data, failure) -> client.execute(() -> {
            if (!session.equals(account) || !session.equals(ACCOUNTS.sessionCookieHeader())) return;
            polling = false;
            if (data != null && !busy) update(client, data);
            if (failure != null) nextPoll = System.nanoTime() + 5_000_000_000L;
        }));
    }

    private static void update(Minecraft client, JsonObject data) {
        feed = data;
        var list = new JsonArray();
        var acceptedIds = new HashSet<String>();
        for (var element : data.getAsJsonArray("items")) {
            var entry = element.getAsJsonObject();
            var visible = entry.deepCopy();
            for (String key : new String[]{"endpoint", "player", "offline", "guestId", "remaining", "iceOffer", "iceAnswer"}) visible.remove(key);
            list.add(visible);
            if (entry.get("incoming").getAsBoolean()) continue;
            if (hosting == null) continue;
            String id = entry.get("id").getAsString();
            String state = entry.get("state").getAsString();
            if ("connected".equals(state)) {
                acceptedIds.add(id);
                REPORTED.remove(id);
                continue;
            }
            if (!"accepted".equals(state) || !entry.has("endpoint")) continue;
            acceptedIds.add(id);
            if (FINISHED.contains(id)) continue;
            var endpoint = entry.getAsJsonObject("endpoint");
            String name = entry.get("player").getAsString();
            if (name.equalsIgnoreCase(client.getUser().getName())) continue;
            if (entry.get("offline").getAsBoolean() && !offlineAllowed) continue;
            var peer = PEERS.computeIfAbsent(id, key -> new HostPeer(hosting, entry.get("guestId").getAsLong(),
                    endpoint.get("pin").getAsString(), name, entry.get("offline").getAsBoolean(),
                    System.nanoTime() + JOIN_TIMEOUT, new AtomicBoolean(), new AtomicBoolean(), new CompletableFuture<>(), endpoint,
                    new AtomicReference<>()));
            if (!peer.claimed().get() && System.nanoTime() < peer.expires()) {
                hosting.tunnel().allowPeer(peer.pin());
                hosting.tunnel().dial(id, peer.endpoint());
                if (entry.has("iceOffer") && OrionWorldIce.available()) answerIce(client, id, peer, entry.get("iceOffer").getAsString());
            } else if (peer.ready().get() && REPORTED.add(id)) {
                ACCOUNTS.friendWorlds(command("connected", id)).exceptionally(failure -> {
                    client.execute(() -> REPORTED.remove(id));
                    return null;
                });
            }
        }
        FINISHED.retainAll(acceptedIds);
        for (var entry : PEERS.entrySet()) {
            var peer = entry.getValue();
            if (!acceptedIds.contains(entry.getKey())) {
                if (PEERS.remove(entry.getKey(), peer)) closePeer(peer);
            }
        }
        visibleInvites = list;
        OrionWorldInvitation.sync(client, data.getAsJsonArray("items"));
        var pendingGuest = joining;
        if (pendingGuest != null && !pendingGuest.claimed().get()) {
            var accepted = find(pendingGuest.id());
            if (accepted == null && pendingGuest.claimed().compareAndSet(false, true)) {
                closeGuest(pendingGuest);
                showJoinFailure(client, "Друг закрыл мир или приглашение истекло");
            } else if (accepted != null && accepted.has("endpoint")) {
                var endpoint = accepted.getAsJsonObject("endpoint");
                String pin = endpoint.get("pin").getAsString();
                pendingGuest.tunnel().allowPeer(pin);
                pendingGuest.tunnel().dial(pendingGuest.id(), endpoint);
                if (pendingGuest.ice() != null && accepted.has("iceAnswer") && pendingGuest.answered().compareAndSet(false, true)) {
                    String answer = accepted.get("iceAnswer").getAsString();
                    Thread.startVirtualThread(() -> {
                        try {
                            pendingGuest.ice().answer(answer);
                            pendingGuest.tunnel().attachIce(pendingGuest.id(), pin, pendingGuest.ice());
                        } catch (IOException | GeneralSecurityException failure) {
                            LOGGER.debug("Direct UDP guest connection did not complete", failure);
                        } finally {
                            OrionWorldTunnel.closeSocket(pendingGuest.ice());
                        }
                    });
                }
            }
        }
        if (!busy && joining == null && !account.isEmpty()) {
            for (var element : data.getAsJsonArray("items")) {
                var entry = element.getAsJsonObject();
                if (!entry.has("autojoin") || !entry.get("autojoin").getAsBoolean() || !VERSION.equals(entry.get("version").getAsString())) continue;
                String id = entry.get("id").getAsString();
                if (!id.equals(autojoinAttempt)) {
                    autojoinAttempt = id;
                    accept(client, id);
                }
                break;
            }
        }
    }

    private static void request(JsonObject body, String success) {
        request(body, success, () -> {});
    }

    private static void answerIce(Minecraft client, String id, HostPeer peer, String offer) {
        if (peer.ice().get() != null) return;
        String session = account;
        CompletableFuture<OrionWorldIce> startup = new CompletableFuture<>();
        if (!peer.ice().compareAndSet(null, startup)) return;
        Thread.startVirtualThread(() -> startup.complete(startIce(offer)));
        startup.thenAccept(ice -> client.execute(() -> {
            if (ice == null) return;
            if (PEERS.get(id) != peer || peer.claimed().get() || !session.equals(ACCOUNTS.sessionCookieHeader())) {
                OrionWorldTunnel.closeSocket(ice);
                return;
            }
            var message = command("ice-answer", id);
            message.addProperty("iceAnswer", ice.description());
            ACCOUNTS.friendWorlds(message).whenComplete((reply, failure) -> {
                if (failure != null) {
                    OrionWorldTunnel.closeSocket(ice);
                    return;
                }
                Thread.startVirtualThread(() -> {
                    try (ice) {
                        peer.world().tunnel().attachIce(id, peer.pin(), ice);
                    } catch (IOException | GeneralSecurityException refused) {
                        LOGGER.debug("Direct UDP host connection did not complete", refused);
                    }
                });
            });
        }));
    }

    private static OrionWorldIce startIce(String offer) {
        try {
            return OrionWorldIce.start(offer);
        } catch (IOException | RuntimeException failure) {
            LOGGER.debug("Direct UDP helper did not start", failure);
            return null;
        }
    }

    private static void request(JsonObject body, String success, Runnable after) {
        var client = Minecraft.getInstance();
        String session = account;
        nextPoll = System.nanoTime() + 750_000_000L;
        busy = true;
        ACCOUNTS.friendWorlds(body).whenComplete((data, failure) -> client.execute(() -> {
            if (!session.equals(account) || !session.equals(ACCOUNTS.sessionCookieHeader())) return;
            busy = false;
            if (failure != null) {
                report(failure);
                return;
            }
            status = success;
            update(client, data);
            after.run();
        }));
    }

    private static void open(Minecraft client, boolean offline) {
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null || !client.allowsMultiplayer()) {
            announce(client, "Сначала открой одиночный мир в Minecraft");
            return;
        }
        if (hosting != null) {
            offlineAllowed = offline;
            request(presence(), "Доступ к миру настроен");
            return;
        }
        String world = server.getWorldData().getLevelName().replaceAll("[\\p{C}\\p{Zl}\\p{Zp}]", "").strip();
        if (world.isEmpty()) world = "Minecraft";
        var body = presence();
        body.addProperty("world", world.substring(0, world.offsetByCodePoints(0, Math.min(80, world.codePointCount(0, world.length())))));
        body.addProperty("version", VERSION);
        body.addProperty("offline", offline);
        body.addProperty("directIce", OrionWorldIce.available());
        String session = account;
        busy = true;
        CompletableFuture.supplyAsync(() -> {
            OrionWorldTunnel tunnel = null;
            try {
                tunnel = new OrionWorldTunnel(true, OrionFriendWorlds::connectHost);
                tunnel.endpoint(body);
                return tunnel;
            } catch (Exception failure) {
                OrionWorldTunnel.closeSocket(tunnel);
                throw new IllegalStateException("Не удалось открыть порт для друзей", failure);
            }
        }).whenComplete((tunnel, failure) -> client.execute(() -> {
            if (!session.equals(account) || !session.equals(ACCOUNTS.sessionCookieHeader())) {
                OrionWorldTunnel.closeSocket(tunnel);
                return;
            }
            busy = false;
            if (failure != null) { report(failure); return; }
            if (client.getSingleplayerServer() != server) { tunnel.close(); return; }
            int port = server.getPort();
            boolean ownLan = !server.isPublished();
            if (ownLan) {
                try (var socket = new ServerSocket(0, 1, OrionWorldTunnel.LOOPBACK)) {
                    port = socket.getLocalPort();
                } catch (IOException unavailable) {
                    tunnel.close();
                    announce(client, "Не удалось открыть порт для друзей");
                    return;
                }
                boolean published = publishPrivateLan(server, port);
                if (!published) {
                    tunnel.close();
                    announce(client, "Не удалось открыть порт для друзей");
                    return;
                }
            }
            offlineAllowed = offline;
            hosting = new Host(server, ownLan, port, tunnel, body);
            request(body, "Мир открыт для друзей");
        }));
    }

    private static boolean publishPrivateLan(IntegratedServer server, int port) {
        PRIVATE_LAN.set(true);
        try {
            //? if >=26.2 {
            return server.publishServer(MinecraftServer.MultiplayerScope.LAN, port);
            //?} else {
            /*return server.publishServer(null, false, port);
            *///?}
        } finally {
            PRIVATE_LAN.remove();
        }
    }

    public static InetAddress lanBindAddress(InetAddress vanilla) {
        return Boolean.TRUE.equals(PRIVATE_LAN.get()) ? OrionWorldTunnel.LOOPBACK : vanilla;
    }

    static boolean accept(Minecraft client, String id) {
        if (busy || joining != null) {
            announce(client, "Подожди завершения текущего подключения");
            return false;
        }
        if (!client.allowsMultiplayer()) {
            announce(client, "Сетевая игра недоступна для этого аккаунта");
            return false;
        }
        if (!id.matches("[A-Za-z0-9_-]{32}")) return false;
        var entry = find(id);
        if (entry == null || !entry.get("incoming").getAsBoolean()) {
            announce(client, "Приглашение больше не действует");
            return false;
        }
        if (!VERSION.equals(entry.get("version").getAsString())) {
            announce(client, "Открой нужную версию Minecraft в лаунчере");
            return false;
        }
        if (isOffline(client) && !entry.get("allowOffline").getAsBoolean()) {
            announce(client, "Друг разрешил вход только с лицензией Minecraft");
            return false;
        }
        String state = entry.get("state").getAsString();
        if ("pending".equals(state)) {
            request(command("reserve", id), "", () -> confirmJoin(client, id));
        } else if ("reserved".equals(state)) {
            confirmJoin(client, id);
        } else {
            announce(client, "Приглашение больше не действует");
            return false;
        }
        return true;
    }

    private static void confirmJoin(Minecraft client, String id) {
        if (client.getSingleplayerServer() != null || client.level != null) {
            var previous = GleamMinecraft.screen(client);
            var confirmation = new net.minecraft.client.gui.screens.ConfirmScreen(accepted -> {
                GleamMinecraft.setScreen(client, previous);
                if (accepted) connect(client, id);
            }, Component.literal(OrionGuiLanguage.translate("Зайти к другу")),
                    Component.literal(OrionGuiLanguage.translate("Текущий мир будет закрыт")));
            GleamMinecraft.setScreen(client, confirmation);
        } else {
            connect(client, id);
        }
    }

    private static boolean isOffline(Minecraft client) {
        String token = client.getUser().getAccessToken();
        return token.isEmpty() || token.equals("0");
    }

    private static void connect(Minecraft client, String id) {
        if (busy || joining != null) {
            announce(client, "Подожди завершения текущего подключения");
            return;
        }
        var entry = find(id);
        if (account.isEmpty() || !account.equals(ACCOUNTS.sessionCookieHeader()) || entry == null
                || !entry.get("incoming").getAsBoolean() || !"reserved".equals(entry.get("state").getAsString())) {
            showJoinFailure(client, "Друг закрыл мир или приглашение истекло");
            return;
        }
        boolean offline = isOffline(client);
        if (offline && !entry.get("allowOffline").getAsBoolean()) {
            announce(client, "Друг разрешил вход только с лицензией Minecraft");
            return;
        }
        String session = account;
        var body = command("accept", id);
        body.addProperty("name", client.getUser().getName());
        body.addProperty("offline", offline);
        boolean useIce = entry.has("directIce") && entry.get("directIce").getAsBoolean() && OrionWorldIce.available();
        busy = true;
        announce(client, "Подключение к миру друга…");
        LOGGER.info("Accepting a reserved friend world invitation");
        CompletableFuture.supplyAsync(() -> {
            OrionWorldTunnel tunnel = null;
            OrionWorldIce ice = null;
            try {
                tunnel = new OrionWorldTunnel(false, OrionFriendWorlds::connectGuest);
                tunnel.endpoint(body);
                if (useIce) {
                    ice = startIce(null);
                    if (ice != null) body.addProperty("iceOffer", ice.description());
                }
                return new Guest(id, entry.get("username").getAsString(), tunnel, new ServerSocket(0, 1, OrionWorldTunnel.LOOPBACK),
                        session, System.nanoTime() + JOIN_TIMEOUT, new AtomicBoolean(), new CompletableFuture<>(), ice, new AtomicBoolean());
            } catch (Exception failure) {
                OrionWorldTunnel.closeSocket(tunnel);
                OrionWorldTunnel.closeSocket(ice);
                throw new IllegalStateException("Не удалось открыть порт для друзей", failure);
            }
        }).whenComplete((guest, failure) -> client.execute(() -> {
            if (!session.equals(account) || !session.equals(ACCOUNTS.sessionCookieHeader())) {
                if (guest != null) closeGuest(guest);
                return;
            }
            if (failure != null) { busy = false; showJoinFailure(client, failureText(failure)); return; }
            joining = guest;
            ACCOUNTS.friendWorlds(body).whenComplete((data, error) -> client.execute(() -> {
                if (joining != guest || !session.equals(ACCOUNTS.sessionCookieHeader())) return;
                busy = false;
                if (error != null) { closeGuest(guest); showJoinFailure(client, failureText(error)); return; }
                update(client, data);
            }));
        }));
    }

    private static void connectHost(String id, String pin, SSLSocket remote) throws IOException {
        var peer = PEERS.get(id);
        if (peer == null || peer.world() != hosting || System.nanoTime() >= peer.expires() || !peer.pin().equals(pin)
                || peer.offline() && !offlineAllowed
                || !peer.claimed().compareAndSet(false, true))
            return;
        Host world = peer.world();
        var local = new Socket();
        try (local) {
            if (!peer.socket().complete(local)) return;
            local.bind(new InetSocketAddress(OrionWorldTunnel.LOOPBACK, 0));
            LAN_LOGINS.put(local.getLocalSocketAddress(), peer);
            local.connect(new InetSocketAddress(OrionWorldTunnel.LOOPBACK, world.lanPort()), 2000);
            var out = new DataOutputStream(remote.getOutputStream());
            out.writeByte(1);
            out.flush();
            OrionWorldTunnel.pipe(local, remote);
        } finally {
            LAN_LOGINS.values().removeIf(value -> value == peer);
            world.tunnel().forgetPeer(pin);
            Minecraft.getInstance().execute(() -> {
                PEERS.remove(id, peer);
                closePeer(peer);
                if (hosting == world) {
                    FINISHED.add(id);
                    ACCOUNTS.friendWorlds(command("finish", id));
                }
            });
        }
    }

    private static void connectGuest(String id, String pin, SSLSocket remote) throws IOException {
        Guest guest = joining;
        if (guest == null || !guest.id().equals(id) || new DataInputStream(remote.getInputStream()).readUnsignedByte() != 1
                || !guest.claimed().compareAndSet(false, true)) return;
        LOGGER.info("Direct connection established, starting Minecraft login");
        var client = Minecraft.getInstance();
        client.execute(() -> {
            if (joining != guest || !guest.account().equals(ACCOUNTS.sessionCookieHeader())) return;
            var address = new InetSocketAddress(OrionWorldTunnel.LOOPBACK, guest.local().getLocalPort());
            String host = address.getAddress().getHostAddress();
            String target = (host.contains(":") ? "[" + host + "]" : host) + ":" + address.getPort();
            var previous = GleamMinecraft.screen(client);
            if (client.level != null || client.getSingleplayerServer() != null) {
                client.disconnect(new TitleScreen(), false);
                previous = GleamMinecraft.screen(client);
            }
            ConnectScreen.startConnecting(previous == null ? new TitleScreen() : previous, client,
                    ServerAddress.parseString(target), new ServerData(guest.username(), target, ServerData.Type.OTHER), false, null);
        });
        guest.local().setSoTimeout(15_000);
        try (var local = guest.local().accept()) {
            if (guest.socket().complete(local)) OrionWorldTunnel.pipe(local, remote);
        } finally {
            client.execute(() -> closeGuest(guest));
        }
    }

    public static boolean rejectLogin(MinecraftServer server, SocketAddress address, String name) {
        var peer = LAN_LOGINS.get(address);
        return peer != null && (peer.world() != hosting || peer.world().server() != server || !peer.name().equals(name)
                || peer.offline() && !offlineAllowed);
    }

    public static boolean allowsOffline(MinecraftServer server, SocketAddress address) {
        var peer = LAN_LOGINS.get(address);
        return peer != null && peer.world().server() == server && peer.offline();
    }

    public static GameProfile offlineProfile(MinecraftServer server, SocketAddress address, String name) {
        var peer = LAN_LOGINS.get(address);
        if (peer == null || peer.world().server() != server || !peer.offline() || !peer.name().equals(name)) return null;
        var id = UUID.nameUUIDFromBytes(("GleamWorld:" + peer.guestId()).getBytes(StandardCharsets.UTF_8));
        return new GameProfile(id, name);
    }

    private static JsonObject find(String id) {
        if (!feed.has("items")) return null;
        for (var item : feed.getAsJsonArray("items")) {
            var entry = item.getAsJsonObject();
            if (id.equals(entry.get("id").getAsString())) return entry;
        }
        return null;
    }

    private static JsonObject presence() {
        if (hosting != null) {
            var body = hosting.presence().deepCopy();
            body.addProperty("offline", offlineAllowed);
            return body;
        }
        var body = command("presence", "");
        body.addProperty("device", "game");
        return body;
    }

    private static JsonObject command(String action, String id) {
        var body = new JsonObject();
        body.addProperty("action", action);
        if (!id.isEmpty()) body.addProperty("id", id);
        return body;
    }

    private static void report(Throwable failure) {
        announce(Minecraft.getInstance(), failureText(failure));
    }

    private static String failureText(Throwable failure) {
        while (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null)
            failure = failure.getCause();
        return failure instanceof IllegalStateException ? failure.getMessage() : "Сайт GleamClient недоступен";
    }

    private static void showJoinFailure(Minecraft client, String reason) {
        announce(client, reason);
        var previous = GleamMinecraft.screen(client);
        GleamMinecraft.setScreen(client, new net.minecraft.client.gui.screens.AlertScreen(
                () -> GleamMinecraft.setScreen(client, previous),
                Component.literal(OrionGuiLanguage.translate("Не удалось подключиться к миру друга")),
                Component.literal(OrionGuiLanguage.translate(reason))));
    }

    private static void showRouteFailure(Minecraft client, Guest guest) {
        announce(client, "Прямое подключение недоступно");
        var previous = GleamMinecraft.screen(client);
        GleamMinecraft.setScreen(client, new net.minecraft.client.gui.screens.ConfirmScreen(retry -> {
            GleamMinecraft.setScreen(client, previous);
            if (!guest.account().equals(ACCOUNTS.sessionCookieHeader())) return;
            String action = retry ? "retry" : "finish";
            ACCOUNTS.friendWorlds(command(action, guest.id())).whenComplete((data, failure) -> client.execute(() -> {
                if (!retry || !guest.account().equals(ACCOUNTS.sessionCookieHeader())) return;
                if (failure != null) { showJoinFailure(client, failureText(failure)); return; }
                if (busy || joining != null) return;
                update(client, data);
                if (data.has("retryId")) connect(client, data.get("retryId").getAsString());
            }));
        }, Component.literal(OrionGuiLanguage.translate("Не удалось подключиться к миру друга")),
                Component.literal(OrionGuiLanguage.translate("Gleam не смог установить прямое соединение. Причиной могут быть настройки роутера, провайдера или брандмауэра.")),
                Component.literal(OrionGuiLanguage.translate("Повторить")), Component.literal(OrionGuiLanguage.translate("Закрыть"))));
    }

    private static void announce(Minecraft client, String message) {
        status = message;
        GleamMinecraft.setOverlayMessage(client, Component.literal(OrionGuiLanguage.translate(message)), false);
    }

    private static void closeHost() {
        Host world = hosting;
        if (world == null) return;
        hosting = null;
        offlineAllowed = false;
        world.tunnel().close();
        PEERS.values().forEach(OrionFriendWorlds::closePeer);
        PEERS.clear();
        LAN_LOGINS.clear();
        FINISHED.clear();
        REPORTED.clear();
        if (world.ownLan() && world.server() == Minecraft.getInstance().getSingleplayerServer()
                && world.server().isPublished() && world.server().getPort() == world.lanPort()) closeOwnedLan(world.server());
    }

    private static void closeOwnedLan(IntegratedServer server) {
        closingLan = true;
        server.execute(() -> {
            try {
        //? if >=26.2 {
                server.unpublishServer();
        //?} else {
        /*        var listeners = ((ru.orion.client.mixin.OrionLanListenersAccess) server.getConnection()).gleam$listeners();
                synchronized (listeners) {
                    listeners.removeIf(future -> {
                        if (!(future.channel().localAddress() instanceof InetSocketAddress)) return false;
                        future.channel().close();
                        return true;
                    });
                }
                var access = (ru.orion.client.mixin.OrionLegacyLanAccess) server;
                if (access.gleam$lanPinger() != null) access.gleam$lanPinger().interrupt();
                access.gleam$lanPinger(null);
                access.gleam$publishedPort(-1);
        *///?}
            } finally {
                Minecraft.getInstance().execute(() -> closingLan = false);
            }
        });
    }

    private static void closePeer(HostPeer peer) {
        peer.world().tunnel().forgetPeer(peer.pin());
        var ice = peer.ice().get();
        if (ice != null) ice.thenAccept(OrionWorldTunnel::closeSocket);
        if (!peer.socket().complete(null)) OrionWorldTunnel.closeSocket(peer.socket().getNow(null));
    }

    private static void closeGuest(Guest guest) {
        if (joining == guest) joining = null;
        guest.tunnel().close();
        OrionWorldTunnel.closeSocket(guest.ice());
        OrionWorldTunnel.closeSocket(guest.local());
        if (!guest.socket().complete(null)) OrionWorldTunnel.closeSocket(guest.socket().getNow(null));
    }

    private static void reset() {
        closeHost();
        if (joining != null) closeGuest(joining);
        feed = new JsonObject();
        visibleInvites = new JsonArray();
        nextPoll = nextPresence = 0;
        status = "";
        autojoinAttempt = "";
        busy = polling = false;
        OrionWorldInvitation.clear();
    }
}
