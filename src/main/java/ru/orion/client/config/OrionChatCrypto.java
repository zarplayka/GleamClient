package ru.orion.client.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.sun.jna.platform.win32.Crypt32Util;
import com.sun.jna.platform.win32.WinCrypt;
import net.fabricmc.loader.api.FabricLoader;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/** Используется только из очереди хранения аккаунтов. */
final class OrionChatCrypto {
    private static final int WRAPPED = 384, NONCE = 12, SIGNATURE = 64;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final OAEPParameterSpec OAEP = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    record Identity(KeyPair encryption, KeyPair signing) {
        JsonObject publicKeys() {
            var keys = new JsonObject();
            keys.addProperty("encryption", encode(encryption.getPublic().getEncoded()));
            keys.addProperty("signing", encode(signing.getPublic().getEncoded()));
            return keys;
        }
    }
    record Peer(PublicKey encryption, PublicKey signing) { }

    static Identity identity(String account) throws IOException, GeneralSecurityException {
        Path file = file(account, "-identity.json");
        JsonObject keys = read(file);
        if (keys == null) {
            var rsa = KeyPairGenerator.getInstance("RSA");
            rsa.initialize(3072);
            var identity = new Identity(rsa.generateKeyPair(), KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
            keys = identity.publicKeys();
            keys.addProperty("decrypt", encode(identity.encryption().getPrivate().getEncoded()));
            keys.addProperty("sign", encode(identity.signing().getPrivate().getEncoded()));
            write(file, keys);
            return identity;
        }
        var peer = decodePeer(keys);
        return new Identity(
                new KeyPair(peer.encryption(), KeyFactory.getInstance("RSA").generatePrivate(
                        new PKCS8EncodedKeySpec(decode(keys, "decrypt")))),
                new KeyPair(peer.signing(), KeyFactory.getInstance("Ed25519").generatePrivate(
                        new PKCS8EncodedKeySpec(decode(keys, "sign")))));
    }

    static Peer pin(String account, String username, JsonObject keys) throws IOException, GeneralSecurityException {
        var peer = decodePeer(keys);
        Path file = file(account, "-" + username.toLowerCase(Locale.ROOT) + "-peer.json");
        JsonObject previous = read(file);
        if (previous == null) write(file, keys);
        else if (!previous.equals(keys)) throw new IllegalStateException("Ключ друга изменился");
        return peer;
    }

    static String verificationCode(Identity own, Peer peer, String account, String username) throws GeneralSecurityException {
        var hash = MessageDigest.getInstance("SHA-256");
        boolean first = account.compareToIgnoreCase(username) < 0;
        hash.update(context(first ? account : username, first ? username : account));
        hash.update((first ? own.encryption().getPublic() : peer.encryption()).getEncoded());
        hash.update((first ? own.signing().getPublic() : peer.signing()).getEncoded());
        hash.update((first ? peer.encryption() : own.encryption().getPublic()).getEncoded());
        hash.update((first ? peer.signing() : own.signing().getPublic()).getEncoded());
        return HexFormat.of().withUpperCase().formatHex(hash.digest(), 0, 16);
    }

    static String seal(String message, Identity own, Peer peer, String sender, String recipient) throws GeneralSecurityException {
        var generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        var key = generator.generateKey();
        byte[] nonce = new byte[NONCE];
        RANDOM.nextBytes(nonce);
        byte[] aad = context(sender, recipient);
        var aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        aes.updateAAD(aad);
        byte[] ciphertext = aes.doFinal(message.getBytes(StandardCharsets.UTF_8));
        var rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.WRAP_MODE, own.encryption().getPublic(), OAEP);
        byte[] senderKey = rsa.wrap(key);
        rsa.init(Cipher.WRAP_MODE, peer.encryption(), OAEP);
        byte[] recipientKey = rsa.wrap(key);
        var packet = ByteBuffer.allocate(NONCE + WRAPPED * 2 + ciphertext.length + SIGNATURE);
        packet.put(nonce).put(senderKey).put(recipientKey).put(ciphertext);
        var signature = Signature.getInstance("Ed25519");
        signature.initSign(own.signing().getPrivate());
        signature.update(aad);
        signature.update(packet.array(), 0, packet.position());
        packet.put(signature.sign());
        return encode(packet.array());
    }

    static String open(String envelope, Identity own, Peer peer, String account, String username, boolean sent)
            throws GeneralSecurityException {
        byte[] packet = Base64.getDecoder().decode(envelope);
        if (packet.length < 861 || packet.length > 2860) throw new GeneralSecurityException("Invalid envelope");
        byte[] aad = sent ? context(account, username) : context(username, account);
        var signature = Signature.getInstance("Ed25519");
        signature.initVerify(sent ? own.signing().getPublic() : peer.signing());
        signature.update(aad);
        int signedLength = packet.length - SIGNATURE;
        signature.update(packet, 0, signedLength);
        if (!signature.verify(packet, signedLength, SIGNATURE)) throw new GeneralSecurityException("Invalid signature");
        var rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.DECRYPT_MODE, own.encryption().getPrivate(), OAEP);
        byte[] rawKey = rsa.doFinal(packet, NONCE + (sent ? 0 : WRAPPED), WRAPPED);
        try {
            var aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(rawKey, "AES"), new GCMParameterSpec(128, packet, 0, NONCE));
            aes.updateAAD(aad);
            int start = NONCE + WRAPPED * 2;
            return new String(aes.doFinal(packet, start, signedLength - start), StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(rawKey, (byte) 0);
        }
    }

    private static byte[] context(String sender, String recipient) {
        return ("GleamChat/1\0" + sender.toLowerCase(Locale.ROOT) + "\0" + recipient.toLowerCase(Locale.ROOT))
                .getBytes(StandardCharsets.UTF_8);
    }

    private static Peer decodePeer(JsonObject keys) throws GeneralSecurityException {
        var encryption = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(decode(keys, "encryption")));
        if (encryption.getModulus().bitLength() != 3072) throw new GeneralSecurityException("Invalid RSA key");
        return new Peer(encryption, KeyFactory.getInstance("Ed25519").generatePublic(
                new X509EncodedKeySpec(decode(keys, "signing"))));
    }

    private static Path file(String account, String suffix) {
        if (!account.matches("[A-Za-z0-9_]{3,24}")) throw new IllegalArgumentException("Invalid account");
        return FabricLoader.getInstance().getConfigDir().resolve("gleam-chat-" + account.toLowerCase(Locale.ROOT) + suffix);
    }

    private static JsonObject read(Path file) throws IOException {
        if (Files.notExists(file)) return null;
        if (Files.size(file) > 16_384) throw new IOException("Chat key file too large");
        byte[] plain = Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(
                JsonParser.parseString(Files.readString(file)).getAsString()), WinCrypt.CRYPTPROTECT_UI_FORBIDDEN);
        try {
            return JsonParser.parseString(new String(plain, StandardCharsets.UTF_8)).getAsJsonObject();
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    private static void write(Path file, JsonObject keys) throws IOException {
        byte[] plain = keys.toString().getBytes(StandardCharsets.UTF_8);
        try {
            OrionConfigManager.writeAtomically(file, new JsonPrimitive(
                    encode(Crypt32Util.cryptProtectData(plain, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN))));
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    private static String encode(byte[] bytes) { return Base64.getEncoder().encodeToString(bytes); }
    private static byte[] decode(JsonObject keys, String key) {
        return Base64.getDecoder().decode(keys.get(key).getAsString());
    }

    private OrionChatCrypto() { }
}
