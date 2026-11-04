package ru.orion.client.gui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Перевод подписей интерфейса по выбранному языку Minecraft. */
public final class OrionGuiLanguage {
    private static final Map<String,String> ENGLISH = load();
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
    private static Map<String,String> load() {
        try (var stream=OrionGuiLanguage.class.getResourceAsStream("/assets/orion-client/lang/click_gui_en.json")) {
            if(stream==null)return Map.of();
            Map<String,String> result=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),
                    new TypeToken<Map<String,String>>(){}.getType());
            return result==null?Map.of():Map.copyOf(result);
        } catch (Exception exception) {
            org.slf4j.LoggerFactory.getLogger("orion-client").warn("Не удалось загрузить перевод интерфейса",exception);
            return Map.of();
        }
    }
    public static boolean english() {
        Minecraft client=Minecraft.getInstance();
        return client!=null && client.getLanguageManager()!=null
                && client.getLanguageManager().getSelected()!=null
                && client.getLanguageManager().getSelected().startsWith("en_");
    }
    public static String translate(String value) {
        return english() ? ENGLISH.getOrDefault(value, value) : RUSSIAN.getOrDefault(value, value);
    }
}
