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
    public static String translate(String value) { return english()?ENGLISH.getOrDefault(value,value):value; }
}
