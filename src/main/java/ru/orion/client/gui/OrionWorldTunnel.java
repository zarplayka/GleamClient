package ru.orion.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.spec.ECGenParameterSpec;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

final class OrionWorldTunnel implements java.io.Closeable {
    static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();
    private static final int DEFAULT_PORT = 25566;
    private static final int HANDSHAKE_MS = 3500;
    private final SSLContext tls;
    private final SSLServerSocket listener;
    private final String pin;
    private final boolean host;
    private final Handler handler;
    private final Set<String> allowedPeers = ConcurrentHashMap.newKeySet();
    private final Set<String> dialing = ConcurrentHashMap.newKeySet();
    private final Set<Socket> remoteSockets = ConcurrentHashMap.newKeySet();
    private final Semaphore inboundSlots = new Semaphore(24);
    private final AtomicBoolean closed = new AtomicBoolean();

    interface Handler {
        void connected(String id, String peerPin, SSLSocket socket) throws IOException;
    }

    OrionWorldTunnel(boolean host, Handler handler) throws IOException, GeneralSecurityException {
        this.host = host;
        this.handler = handler;
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var keys = generator.generateKeyPair();
        X509Certificate certificate = temporaryCertificate(keys);
        pin = fingerprint(certificate);
        var store = KeyStore.getInstance("PKCS12");
        store.load(null);
        char[] password = new char[0];
        store.setKeyEntry("gleam", keys.getPrivate(), password, new X509Certificate[]{certificate});
        var managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(store, password);
        tls = SSLContext.getInstance("TLSv1.3");
        tls.init(managers.getKeyManagers(), new X509TrustManager[]{new PinnedPeerTrust()}, null);
        listener = (SSLServerSocket) tls.getServerSocketFactory().createServerSocket();
        try {
            listener.setEnabledProtocols(new String[]{"TLSv1.3"});
            listener.setNeedClientAuth(true);
            try { listener.bind(new InetSocketAddress(DEFAULT_PORT), 16); }
            catch (java.net.BindException occupied) { listener.bind(new InetSocketAddress(0), 16); }
        } catch (IOException | RuntimeException failure) {
            listener.close();
            throw failure;
        }
        Thread.startVirtualThread(this::listen);
    }

    int port() { return listener.getLocalPort(); }

    JsonObject endpoint(JsonObject request) throws IOException {
        request.addProperty("port", listener.getLocalPort());
        request.addProperty("pin", pin);
        var addresses = new JsonArray();
        for (InetAddress address : NetworkInterface.networkInterfaces()
                .flatMap(NetworkInterface::inetAddresses).filter(a -> !a.isLoopbackAddress()
                        && !a.isLinkLocalAddress() && !a.isMulticastAddress()).limit(8).toList()) {
            addresses.add(address.getHostAddress().split("%", 2)[0]);
        }
        request.add("addresses", addresses);
        return request;
    }

    String pin() { return pin; }

    void allowPeer(String peerPin) { allowedPeers.add(peerPin); }

    void forgetPeer(String peerPin) { allowedPeers.remove(peerPin); }

    void attachIce(String id, String expectedPin, OrionWorldIce ice) throws IOException, GeneralSecurityException {
        try (var bridge = ice.connect();
             var socket = (SSLSocket) tls.getSocketFactory().createSocket(bridge, "localhost", bridge.getPort(), true)) {
            if (!track(socket)) return;
            try {
                socket.setUseClientMode(!host);
                if (host) socket.setNeedClientAuth(true);
                prepare(socket);
                String peer = fingerprint((X509Certificate) socket.getSession().getPeerCertificates()[0]);
                if (!expectedPin.equals(peer)) throw new IOException("Unexpected direct UDP peer");
                handler.connected(id, peer, socket);
            } finally {
                remoteSockets.remove(socket);
            }
        }
    }

    void dial(String id, JsonObject endpoint) {
        int port = endpoint.get("port").getAsInt();
        String expectedPin = endpoint.get("pin").getAsString();
        for (var entry : endpoint.getAsJsonArray("addresses")) {
            String address = entry.getAsString();
            if (address.length() > 45 || !address.matches("[0-9A-Fa-f:.]+")
                    || !address.contains(":") && !address.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) continue;
            String attempt = id + '/' + address;
            if (!dialing.add(attempt)) continue;
            Thread.startVirtualThread(() -> {
                try (var socket = (SSLSocket) tls.getSocketFactory().createSocket()) {
                    if (!track(socket)) return;
                    try {
                        socket.connect(new InetSocketAddress(InetAddress.getByName(address), port), HANDSHAKE_MS);
                        prepare(socket);
                        String remote = fingerprint((X509Certificate) socket.getSession().getPeerCertificates()[0]);
                        if (!expectedPin.equals(remote)) return;
                        var out = new DataOutputStream(socket.getOutputStream());
                        out.writeUTF("gleam-world/1");
                        out.writeBoolean(host);
                        out.writeUTF(id);
                        out.flush();
                        if (new DataInputStream(socket.getInputStream()).readUnsignedByte() != 1) return;
                        handler.connected(id, remote, socket);
                    } finally {
                        remoteSockets.remove(socket);
                    }
                } catch (IOException | GeneralSecurityException | IllegalArgumentException ignored) {
                    // The other endpoint or another address can still establish the session.
                } finally {
                    dialing.remove(attempt);
                }
            });
        }
    }

    private void listen() {
        while (!closed.get()) {
            try {
                var socket = (SSLSocket) listener.accept();
                if (!inboundSlots.tryAcquire()) {
                    socket.close();
                    continue;
                }
                Thread.startVirtualThread(() -> {
                    try (socket) {
                        if (!track(socket)) return;
                        prepare(socket);
                        var in = new DataInputStream(socket.getInputStream());
                        if (!in.readUTF().equals("gleam-world/1") || in.readBoolean() == host) return;
                        String id = in.readUTF();
                        if (!id.matches("[A-Za-z0-9_-]{32}")) return;
                        var peer = fingerprint((X509Certificate) socket.getSession().getPeerCertificates()[0]);
                        if (!allowedPeers.contains(peer)) return;
                        socket.getOutputStream().write(1);
                        socket.getOutputStream().flush();
                        handler.connected(id, peer, socket);
                    } catch (IOException | GeneralSecurityException ignored) {
                    } finally {
                        remoteSockets.remove(socket);
                        inboundSlots.release();
                    }
                });
            } catch (IOException failure) {
                close();
            }
        }
    }

    private boolean track(Socket socket) {
        remoteSockets.add(socket);
        if (!closed.get()) return true;
        remoteSockets.remove(socket);
        return false;
    }

    private static void prepare(SSLSocket socket) throws IOException {
        socket.setEnabledProtocols(new String[]{"TLSv1.3"});
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(HANDSHAKE_MS);
        socket.startHandshake();
    }

    static void pipe(Socket local, SSLSocket remote) throws IOException {
        local.setTcpNoDelay(true);
        remote.setSoTimeout(0);
        Thread upstream = Thread.startVirtualThread(() -> {
            try {
                local.getInputStream().transferTo(remote.getOutputStream());
            } catch (IOException ignored) {
            } finally {
                closeSocket(remote);
            }
        });
        try {
            remote.getInputStream().transferTo(local.getOutputStream());
        } catch (IOException failure) {
            if (!remote.isClosed()) throw failure;
        } finally {
            closeSocket(local);
            upstream.interrupt();
        }
    }

    static void closeSocket(java.io.Closeable socket) {
        if (socket == null) return;
        try { socket.close(); } catch (IOException ignored) { }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        closeSocket(listener);
        remoteSockets.forEach(OrionWorldTunnel::closeSocket);
    }

    private static String fingerprint(X509Certificate cert) throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getPublicKey().getEncoded()));
    }

    private final class PinnedPeerTrust implements X509TrustManager {
        @Override public void checkClientTrusted(X509Certificate[] chain, String auth) throws java.security.cert.CertificateException {
            checkLeaf(chain);
        }
        @Override public void checkServerTrusted(X509Certificate[] chain, String auth) throws java.security.cert.CertificateException {
            checkLeaf(chain);
        }
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }

        private void checkLeaf(X509Certificate[] chain) throws java.security.cert.CertificateException {
            if (chain.length != 1) throw new java.security.cert.CertificateException("Expected ephemeral peer certificate");
            chain[0].checkValidity();
            try {
                if (allowedPeers.contains(fingerprint(chain[0]))) return;
            } catch (GeneralSecurityException failure) {
                throw new java.security.cert.CertificateException(failure);
            }
            throw new java.security.cert.CertificateException("Unexpected Gleam peer");
        }
    }

    private static X509Certificate temporaryCertificate(java.security.KeyPair keys) throws GeneralSecurityException {
        // V3, self-signed ECDSA P-256, with an ephemeral subject public key.
        var now = Instant.now();
        var formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'").withZone(ZoneOffset.UTC);
        var signatureAlgorithm = HexFormat.of().parseHex("300a06082a8648ce3d040302");
        var subject = new javax.security.auth.x500.X500Principal("CN=Gleam World").getEncoded();
        var validity = der(0x30, der(0x18, formatter.format(now.minusSeconds(300)).getBytes(StandardCharsets.US_ASCII)),
                der(0x18, formatter.format(now.plusSeconds(86400)).getBytes(StandardCharsets.US_ASCII)));
        byte[] serial = new byte[17];
        new java.security.SecureRandom().nextBytes(serial);
        serial[0] = 1;
        var body = der(0x30, der(0xa0, der(2, new byte[]{2})), der(2, serial), signatureAlgorithm,
                subject, validity, subject, keys.getPublic().getEncoded());
        var signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keys.getPrivate());
        signer.update(body);
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(
                der(0x30, body, signatureAlgorithm, der(3, new byte[]{0}, signer.sign()))));
    }

    private static byte[] der(int tag, byte[]... parts) {
        int length = Arrays.stream(parts).mapToInt(part -> part.length).sum();
        var bytes = new ByteArrayOutputStream();
        bytes.write(tag);
        if (length < 128) bytes.write(length);
        else if (length < 256) { bytes.write(0x81); bytes.write(length); }
        else { bytes.write(0x82); bytes.write(length >>> 8); bytes.write(length); }
        for (byte[] part : parts) bytes.writeBytes(part);
        return bytes.toByteArray();
    }
}
