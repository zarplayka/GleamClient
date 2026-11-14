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
import java.security.interfaces.RSAPrivateKey;
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
    record Recovery(String code, String backup) { }

    static Identity localIdentity(String account, JsonObject registered) throws IOException, GeneralSecurityException {
        var shared = identityFile(account, registered);
        var original = file(account, "-identity.json");
        for (Path candidate : new Path[]{shared, original}) {
            JsonObject keys;
            try {
                keys = read(candidate);
                if (keys == null) continue;
                var identity = decodeIdentity(keys);
                if (!registered.has("encryption") || matches(identity, registered)) return identity;
            } catch (IOException | RuntimeException | GeneralSecurityException invalid) {
                if (!registered.has("encryption")) throw invalid;
            }
            if (shared.equals(original)) break;
        }
        return null;
    }

    static Identity newIdentity() throws GeneralSecurityException {
        var rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(3072);
        return new Identity(rsa.generateKeyPair(), KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
    }

    static void saveIdentity(String account, Identity identity) throws IOException {
        var keys = identity.publicKeys();
        keys.addProperty("decrypt", encode(identity.encryption().getPrivate().getEncoded()));
        keys.addProperty("sign", encode(identity.signing().getPrivate().getEncoded()));
        write(identityFile(account, keys), keys);
    }

    private static Identity decodeIdentity(JsonObject keys) throws GeneralSecurityException {
        var peer = decodePeer(keys);
        return new Identity(
                new KeyPair(peer.encryption(), KeyFactory.getInstance("RSA").generatePrivate(
                        new PKCS8EncodedKeySpec(decode(keys, "decrypt")))),
                new KeyPair(peer.signing(), KeyFactory.getInstance("Ed25519").generatePrivate(
                        new PKCS8EncodedKeySpec(decode(keys, "sign")))));
    }

    static Recovery localRecovery(String account, String backup) throws IOException {
        var stored = read(recoveryFile(account, backup));
        return stored == null ? null : new Recovery(stored.get("code").getAsString(), stored.get("backup").getAsString());
    }

    static Recovery pendingRecovery(String account) throws IOException {
        var stored = read(sharedFile(account, "-pending-recovery.json"));
        return stored == null ? null : new Recovery(stored.get("code").getAsString(), stored.get("backup").getAsString());
    }

    static void saveRecovery(String account, Recovery recovery) throws IOException {
        writeRecovery(recoveryFile(account, recovery.backup()), recovery);
    }

    static void stageRecovery(String account, Recovery recovery) throws IOException {
        saveRecovery(account, recovery);
        writeRecovery(sharedFile(account, "-pending-recovery.json"), recovery);
    }

    private static void writeRecovery(Path file, Recovery recovery) throws IOException {
        var data = new JsonObject();
        data.addProperty("code", recovery.code());
        data.addProperty("backup", recovery.backup());
        write(file, data);
    }

    private static Path recoveryFile(String account, String backup) {
        return sharedFile(account, "-" + keyFingerprint(Base64.getDecoder().decode(backup)) + "-recovery.json");
    }

    static Recovery createRecovery(String account, Identity identity) throws GeneralSecurityException {
        byte[] secret = new byte[32], nonce = new byte[NONCE];
        RANDOM.nextBytes(secret);
        RANDOM.nextBytes(nonce);
        byte[] encryption = identity.encryption().getPrivate().getEncoded();
        byte[] signing = identity.signing().getPrivate().getEncoded();
        byte[] plain = ByteBuffer.allocate(4 + encryption.length + signing.length)
                .putInt(encryption.length).put(encryption).put(signing).array();
        try {
            var aes = recoveryCipher(Cipher.ENCRYPT_MODE, secret, nonce, account, identity.publicKeys());
            byte[] sealed = aes.doFinal(plain);
            byte[] packet = ByteBuffer.allocate(1 + NONCE + sealed.length).put((byte) 1).put(nonce).put(sealed).array();
            return new Recovery(Base64.getUrlEncoder().withoutPadding().encodeToString(secret), encode(packet));
        } finally {
            Arrays.fill(plain, (byte) 0);
            Arrays.fill(secret, (byte) 0);
            Arrays.fill(encryption, (byte) 0);
            Arrays.fill(signing, (byte) 0);
        }
    }

    static Identity recover(String account, Recovery recovery, JsonObject keys) throws GeneralSecurityException {
        byte[] packet = Base64.getDecoder().decode(recovery.backup());
        if (packet.length < 1700 || packet.length > 2850 || packet[0] != 1)
            throw new GeneralSecurityException("Invalid key backup");
        byte[] secret = Base64.getUrlDecoder().decode(recovery.code());
        try {
            if (secret.length != 32) throw new GeneralSecurityException("Invalid recovery code");
            byte[] nonce = Arrays.copyOfRange(packet, 1, 1 + NONCE);
            var aes = recoveryCipher(Cipher.DECRYPT_MODE, secret, nonce, account, keys);
            byte[] plain = aes.doFinal(packet, 1 + NONCE, packet.length - 1 - NONCE);
            try {
                var buffer = ByteBuffer.wrap(plain);
                int length = buffer.getInt();
                if (length <= 0 || length >= buffer.remaining()) throw new GeneralSecurityException("Invalid key backup");
                var peer = decodePeer(keys);
                var decrypt = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(
                        new PKCS8EncodedKeySpec(Arrays.copyOfRange(plain, 4, 4 + length)));
                var sign = KeyFactory.getInstance("Ed25519").generatePrivate(
                        new PKCS8EncodedKeySpec(Arrays.copyOfRange(plain, 4 + length, plain.length)));
                var signature = Signature.getInstance("Ed25519");
                signature.initSign(sign);
                signature.update(nonce);
                byte[] proof = signature.sign();
                signature.initVerify(peer.signing());
                signature.update(nonce);
                if (!signature.verify(proof) || !decrypt.getModulus().equals(((RSAPublicKey) peer.encryption()).getModulus()))
                    throw new GeneralSecurityException("Invalid key backup");
                return new Identity(new KeyPair(peer.encryption(), decrypt), new KeyPair(peer.signing(), sign));
            } finally {
                Arrays.fill(plain, (byte) 0);
            }
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    private static Cipher recoveryCipher(int mode, byte[] secret, byte[] nonce, String account, JsonObject keys)
            throws GeneralSecurityException {
        var aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(mode, new SecretKeySpec(secret, "AES"), new GCMParameterSpec(128, nonce));
        aes.updateAAD(("GleamChatRecovery/1\0" + account.toLowerCase(Locale.ROOT) + "\0").getBytes(StandardCharsets.UTF_8));
        aes.updateAAD(decode(keys, "encryption"));
        aes.updateAAD(decode(keys, "signing"));
        return aes;
    }

    private static boolean matches(Identity identity, JsonObject keys) {
        var local = identity.publicKeys();
        return local.get("encryption").equals(keys.get("encryption")) && local.get("signing").equals(keys.get("signing"));
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

    private static Path sharedFile(String account, String suffix) {
        var original = file(account, suffix);
        String directory = System.getenv("LOCALAPPDATA");
        return directory == null || directory.isBlank() ? original
                : Path.of(directory, "GleamClient", "chat").resolve(original.getFileName());
    }

    private static Path identityFile(String account, JsonObject keys) {
        if (!keys.has("encryption")) return sharedFile(account, "-identity.json");
        return sharedFile(account, "-" + keyFingerprint(decode(keys, "encryption")) + "-identity.json");
    }

    private static String keyFingerprint(byte[] key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key));
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException(unavailable);
        }
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
