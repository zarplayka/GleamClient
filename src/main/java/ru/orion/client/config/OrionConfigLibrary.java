package ru.orion.client.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.orion.client.effect.OrionBadgePresence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Локальная библиотека и сетевой каталог профилей настроек. */
public final class OrionConfigLibrary {
    public static final int PAGE_SIZE = 5;
    private static final int MAX_LOCAL = 100;
    private static final JsonObject DEFAULTS = OrionConfigManager.GSON.toJsonTree(OrionConfig.createDefault()).getAsJsonObject();
    private static final Set<String> CORNERS = Set.of("TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT");
    private final Path file;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("orion-config-library").factory());
    private List<Profile> local;

    OrionConfigLibrary(Path file) { this.file = file; }

    public record Entry(int id, String name, String author, String owner, long downloads,
                        double createdAt, double updatedAt, int version) {
        public Entry(int id, String name, String author) { this(id, name, author, "", 0, 0, 0, 0); }
        Entry renamed(String title) {
            return new Entry(id, title, author, owner, downloads, createdAt, System.currentTimeMillis() / 1000D, version);
        }
    }
    public record Profile(Entry entry, JsonObject settings) {}
    public record Page(List<Entry> entries, boolean more, int total) {
        public Page(List<Entry> entries, boolean more) { this(entries, more, entries.size()); }
    }

    public CompletableFuture<List<Profile>> local(JsonObject original) {
        return submit(() -> {
            if (local != null) return local;
            List<Profile> loaded = new ArrayList<>();
            if (Files.exists(file)) {
                if (Files.size(file) > 2_000_000L) throw new IOException("Слишком большая библиотека конфигов");
                try (var reader = Files.newBufferedReader(file)) {
                    for (JsonElement item : JsonParser.parseReader(reader).getAsJsonArray()) {
                        if (loaded.size() >= MAX_LOCAL) break;
                        loaded.add(readProfile(item.getAsJsonObject(), true));
                    }
                }
            }
            if (loaded.stream().noneMatch(profile -> profile.entry.id == 0)) {
                if (loaded.size() >= MAX_LOCAL) loaded.removeLast();
                loaded.addFirst(new Profile(new Entry(0, "Изначальный конфиг", ""), original));
                write(loaded);
            }
            local = List.copyOf(loaded);
            return local;
        });
    }

    public CompletableFuture<Page> catalog(int offset) { return catalog(offset, null, null); }

    public CompletableFuture<Page> catalog(int offset, UUID owner, String name) {
        return submit(() -> {
            JsonObject result = OrionBadgePresence.sharedConfigs((owner == null ? "" : "/mine") + "?offset=" + offset
                    + "&limit=" + PAGE_SIZE, null, owner, name);
            List<Entry> entries = new ArrayList<>();
            for (JsonElement item : result.getAsJsonArray("items")) {
                if (entries.size() == PAGE_SIZE) break;
                entries.add(readEntry(item.getAsJsonObject(), false));
            }
            return new Page(List.copyOf(entries), result.get("more").getAsBoolean(), result.has("total") ? result.get("total").getAsInt() : entries.size());
        });
    }

    public CompletableFuture<List<Profile>> download(int id) { return download(id, null, null); }

    public CompletableFuture<List<Profile>> download(int id, UUID uuid, String author) {
        return submit(() -> {
            ensureSpace(id);
            JsonObject result = uuid == null ? OrionBadgePresence.sharedConfigs("/" + id, null, null, null)
                    : OrionBadgePresence.sharedConfigs("POST", "/" + id + "/download", new JsonObject(), uuid, author);
            Profile profile = readProfile(result, false);
            if (profile.entry.id != id) throw new IllegalStateException("Каталог вернул другой конфиг");
            return remember(profile);
        });
    }

    public CompletableFuture<List<Profile>> publish(String title, UUID uuid, String author, JsonObject settings) {
        String orionCookie = OrionAccounts.INSTANCE.sessionCookieHeader();
        return submit(() -> {
            ensureSpace(Integer.MIN_VALUE);
            JsonObject payload = new JsonObject();
            payload.addProperty("name", validateName(title));
            payload.addProperty("schema", 1);
            payload.add("settings", settings);
            Entry entry = readEntry(OrionBadgePresence.publishConfig(payload, uuid, author, orionCookie), false);
            try {
                return remember(new Profile(entry, settings));
            } catch (IOException exception) {
                throw new IllegalStateException("Конфиг опубликован, но локальная копия не сохранена", exception);
            }
        });
    }

    public CompletableFuture<List<Profile>> saveLocal(String title, JsonObject settings) {
        return submit(() -> {
            ensureSpace(Integer.MIN_VALUE);
            int id = local.stream().mapToInt(profile -> profile.entry.id).min().orElse(0) - 1;
            String name = validateName(title);
            return remember(new Profile(new Entry(id, name, "", "", 0, System.currentTimeMillis() / 1000D,
                    System.currentTimeMillis() / 1000D, 0), shareable(settings)));
        });
    }

    public CompletableFuture<List<Profile>> renameLocal(int id, String title) {
        return submit(() -> {
            if (id == 0) throw new IllegalStateException("Изначальный конфиг защищён");
            Profile found = findLocal(id);
            return remember(new Profile(found.entry.renamed(validateName(title)), found.settings));
        });
    }

    public CompletableFuture<List<Profile>> removeLocal(int id) {
        return submit(() -> {
            if (id == 0) throw new IllegalStateException("Изначальный конфиг защищён");
            findLocal(id);
            List<Profile> next = new ArrayList<>(local);
            next.removeIf(profile -> profile.entry.id == id);
            write(next);
            local = List.copyOf(next);
            return local;
        });
    }

    public CompletableFuture<List<Profile>> updateLocal(int id, JsonObject settings) {
        return submit(() -> {
            if (id == 0) throw new IllegalStateException("Изначальный конфиг защищён");
            Profile found = findLocal(id);
            return remember(new Profile(found.entry.renamed(found.entry.name), shareable(settings)));
        });
    }

    public CompletableFuture<Profile> editPublication(Entry entry, String title, JsonObject settings, UUID uuid, String author) {
        return submit(() -> {
            JsonObject payload = new JsonObject();
            payload.addProperty("version", entry.version);
            payload.addProperty("name", validateName(title));
            if (settings != null) {
                payload.addProperty("schema", 1);
                payload.add("settings", settings);
            }
            Profile profile = readProfile(OrionBadgePresence.sharedConfigs("PATCH", "/" + entry.id, payload, uuid, author), false);
            if (local != null && local.stream().anyMatch(saved -> saved.entry.id == entry.id)) {
                try { remember(profile); }
                catch (IOException exception) { throw new IllegalStateException("Публикация обновлена, локальная копия не сохранена", exception); }
            }
            return profile;
        });
    }

    public CompletableFuture<Void> removePublication(Entry entry, UUID uuid, String author) {
        return submit(() -> {
            JsonObject payload = new JsonObject();
            payload.addProperty("version", entry.version);
            OrionBadgePresence.sharedConfigs("DELETE", "/" + entry.id, payload, uuid, author);
            return null;
        });
    }

    private Profile findLocal(int id) {
        if (local == null) throw new IllegalStateException("Библиотека конфигов недоступна");
        return local.stream().filter(profile -> profile.entry.id == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Конфиг не найден"));
    }

    private static String validateName(String title) {
        String name = title.strip();
        if (name.isEmpty() || name.length() > 48 || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException("Название должно содержать от 1 до 48 символов");
        }
        return name;
    }

    private void ensureSpace(int id) {
        if (local == null) throw new IllegalStateException("Библиотека конфигов недоступна");
        if (local.size() >= MAX_LOCAL && local.stream().noneMatch(profile -> profile.entry.id == id)) {
            throw new IllegalStateException("Библиотека заполнена");
        }
    }

    private List<Profile> remember(Profile profile) throws IOException {
        List<Profile> next = new ArrayList<>(local);
        next.removeIf(saved -> saved.entry.id == profile.entry.id);
        next.add(1, profile);
        write(next);
        local = List.copyOf(next);
        return local;
    }

    private void write(List<Profile> profiles) throws IOException {
        JsonArray result = new JsonArray();
        for (Profile profile : profiles) {
            JsonObject item = OrionConfigManager.GSON.toJsonTree(profile.entry).getAsJsonObject();
            item.addProperty("schema", 1);
            item.add("settings", profile.settings);
            result.add(item);
        }
        OrionConfigManager.writeAtomically(file, result);
    }

    private <T> CompletableFuture<T> submit(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try { return task.call(); }
            catch (Exception exception) { throw new CompletionException(exception); }
        }, worker);
    }

    private static Entry readEntry(JsonObject item, boolean allowOriginal) {
        int id = item.get("id").getAsInt();
        String name = item.get("name").getAsString();
        String author = item.get("author").getAsString();
        if ((!allowOriginal && id < 1) || name.isBlank() || name.length() > 96 || author.length() > 32
                || name.codePoints().anyMatch(Character::isISOControl) || author.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Некорректный конфиг");
        }
        return new Entry(id, name, author, item.has("owner") ? item.get("owner").getAsString() : "",
                item.has("downloads") ? Math.max(0, item.get("downloads").getAsLong()) : 0,
                number(item, "createdAt"), number(item, "updatedAt"), item.has("version") ? Math.max(0, item.get("version").getAsInt()) : 0);
    }

    private static double number(JsonObject item, String key) {
        double value = item.has(key) ? item.get(key).getAsDouble() : 0D;
        return Double.isFinite(value) && value >= 0D && value <= 253402300799D ? value : 0D;
    }

    private static Profile readProfile(JsonObject item, boolean allowOriginal) {
        if (item.get("schema").getAsInt() != 1) throw new IllegalArgumentException("Версия конфига не поддерживается");
        JsonObject settings = shareable(item.getAsJsonObject("settings"));
        if (settings.isEmpty()) throw new IllegalArgumentException("Конфиг не содержит настроек");
        return new Profile(readEntry(item, allowOriginal), settings);
    }

    static JsonObject shareable(JsonObject input) {
        JsonObject result = new JsonObject();
        for (var entry : input.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            JsonElement expected = DEFAULTS.get(key);
            if (expected == null || !expected.isJsonPrimitive() || !value.isJsonPrimitive()
                    || key.startsWith("discord") || key.startsWith("fakePrefix") || key.startsWith("protectName")
                    || key.equals("chatFilterEnabled") || key.equals("chatHighlightEnabled")) continue;
            var actual = value.getAsJsonPrimitive();
            var type = expected.getAsJsonPrimitive();
            if (type.isBoolean() && actual.isBoolean()) result.add(key, actual);
            else if (type.isNumber() && actual.isNumber() && Math.abs(actual.getAsDouble()) <= 1e9
                    && (!type.getAsString().matches("-?\\d+") || actual.getAsString().matches("-?\\d+"))) result.add(key, actual);
            else if (type.isString() && actual.isString()) {
                String text = actual.getAsString();
                if ((type.getAsString().matches("#[0-9a-fA-F]{6}") && text.matches("#[0-9a-fA-F]{6}"))
                        || (key.equals("mediaPlayerCorner") && CORNERS.contains(text))
                        || (key.equals("crosshairPixels") && text.matches("[01]{225}"))) result.add(key, actual);
            }
        }
        return result;
    }
}
