package ru.orion.client.gui;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import ru.orion.client.OrionClient;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Перевод подписей интерфейса по выбранному языку Minecraft. */
public final class OrionGuiLanguage {
    private static final Map<String,Map<String,String>> TRANSLATIONS =
            Map.of("en", load("en"), "de", load("de"), "es", load("es"));
    private static final Map<String, String> RUSSIAN = Map.ofEntries(
            Map.entry("Games", "Игры"), Map.entry("Settings", "Настройки"),
            Map.entry("Easy", "Легко"), Map.entry("Normal", "Обычно"), Map.entry("Hard", "Сложно"),
            Map.entry("Dark", "Тёмная"), Map.entry("Light", "Светлая"), Map.entry("Glass", "Стекло"),
            Map.entry("TOP_LEFT", "Слева сверху"), Map.entry("TOP_RIGHT", "Справа сверху"),
            Map.entry("BOTTOM_LEFT", "Слева снизу"), Map.entry("BOTTOM_RIGHT", "Справа снизу"),
            Map.entry("Azure", "Лазурь"), Map.entry("Rose", "Розовый"), Map.entry("Gold", "Золотой"),
            Map.entry("rhomb", "Ромб"), Map.entry("ghost", "Призрак"), Map.entry("chain", "Цепь"),
            Map.entry("crystals", "Кристаллы"), Map.entry("marker", "Маркер"),
            Map.entry("Sine In", "Синус · вход"), Map.entry("Sine Out", "Синус · выход"), Map.entry("Sine In/Out", "Синус · оба"),
            Map.entry("Quad In", "Квадрат · вход"), Map.entry("Quad Out", "Квадрат · выход"), Map.entry("Quad In/Out", "Квадрат · оба"),
            Map.entry("Cubic In", "Куб · вход"), Map.entry("Cubic Out", "Куб · выход"), Map.entry("Cubic In/Out", "Куб · оба"),
            Map.entry("Expo In", "Экспонента · вход"), Map.entry("Expo Out", "Экспонента · выход"), Map.entry("Expo In/Out", "Экспонента · оба"));
    private OrionGuiLanguage() { }
    private static Map<String,String> load(String language) {
        try (var stream=OrionGuiLanguage.class.getResourceAsStream("/assets/orion-client/lang/click_gui_" + language + ".json")) {
            if(stream==null)return Map.of();
            var entries = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> result = new HashMap<>();
            entries.entrySet().forEach(entry -> {
                var value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    result.put(entry.getKey(), value.getAsString());
                }
            });
            return Map.copyOf(result);
        } catch (Exception exception) {
            org.slf4j.LoggerFactory.getLogger("orion-client").warn("Не удалось загрузить перевод интерфейса",exception);
            return Map.of();
        }
    }
    public static boolean english() {
        return !"ru".equals(language());
    }
    public static String language() {
        var config = OrionClient.getConfigOrNull();
        if (config != null && !"auto".equals(config.getClickGuiLanguage())) return config.getClickGuiLanguage();
        Minecraft client = Minecraft.getInstance();
        String selected = client == null || client.getLanguageManager() == null ? null : client.getLanguageManager().getSelected();
        if (selected == null) return "ru";
        String base = selected.split("[_-]", 2)[0];
        return "ru".equals(base) || TRANSLATIONS.containsKey(base) ? base : "en";
    }
    public static String translate(String value) {
        String language = language();
        if ("ru".equals(language)) return RUSSIAN.getOrDefault(value, value);
        return TRANSLATIONS.get(language).getOrDefault(value, TRANSLATIONS.get("en").getOrDefault(value, value));
    }
}
