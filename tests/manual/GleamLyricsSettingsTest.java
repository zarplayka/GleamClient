package ru.orion.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageManager;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import sun.misc.Unsafe;
import java.nio.file.Files;
import java.nio.file.Path;

/** Реальные настройки MediaPlayer, HTML-каталог, обработчики и сохранение конфига. */
public final class GleamLyricsSettingsTest {
    private static int checks;
    private static java.lang.reflect.Field field(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static Object call(Object object,String name,Class<?>[] types,Object...args)throws Exception{var m=object.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(object,args);}
    private static void check(boolean result,String label){if(!result)throw new AssertionError(label);checks++;}
    public static void main(String[]args)throws Exception {
        Path output=Path.of(args[0]);Files.createDirectories(output);
        var unsafe=(Unsafe)field(Unsafe.class,"theUnsafe").get(null);
        var client=(Minecraft)unsafe.allocateInstance(Minecraft.class);field(Minecraft.class,"instance").set(null,client);
        var language=new LanguageManager(client,"ru_ru",v->{});field(Minecraft.class,"languageManager").set(client,language);
        var manager=(OrionConfigManager)unsafe.allocateInstance(OrionConfigManager.class);var config=OrionConfig.createDefault();
        field(OrionConfigManager.class,"config").set(manager,config);field(OrionConfigManager.class,"configPath").set(manager,output.resolve("settings.json"));
        Class<?> actionType=Class.forName(OrionClickGuiScreen.class.getName()+"$Action"),rowType=Class.forName(OrionClickGuiScreen.class.getName()+"$ModuleRow"),settingType=Class.forName(OrionClickGuiScreen.class.getName()+"$Setting");
        Object action=Enum.valueOf((Class)actionType,"MEDIA_PLAYER"),mediaRow=null;
        for(var f:rowType.getDeclaredFields())if(f.getType().isArray()&&f.getType().getComponentType()==rowType){f.setAccessible(true);for(Object row:(Object[])f.get(null))if(call(row,"action",new Class<?>[]{}).equals(action))mediaRow=row;}
        check(mediaRow!=null,"existing media module found");
        for(int theme:new int[]{0,1,2,3,4})for(String code:new String[]{"ru_ru","en_us","de_de","es_es"}){
            config.setClickGuiTheme(theme);language.setSelected(code);var screen=new OrionClickGuiScreen(manager);
            call(screen,"openSettings",new Class<?>[]{rowType},mediaRow);
            var rows=(Object[])call(screen,"buildSettings",new Class<?>[]{actionType},action);check(rows.length==23,"all settings exposed");
            field(OrionClickGuiScreen.class,"settingsRows").set(screen,rows);
            var groups=(int[])call(screen,"paradoxGroupStarts",new Class<?>[]{rows.getClass()},(Object)rows);
            check(groups.length==2&&groups[1]==6,"separate Animated Lyrics section");
            for(int i=6;i<19;i++){
                var item=(JsonObject)call(screen,"siteSetting",new Class<?>[]{settingType},rows[i]);check(item.has("kind")&&item.has("label"),"HTML setting exported");
            }
            JsonObject event=new JsonObject();event.addProperty("id","MEDIA_PLAYER");event.addProperty("index",6);event.addProperty("value",true);
            call(screen,"applySiteSetting",new Class<?>[]{String.class,JsonObject.class},"setting",event);
            check(config.getAnimatedLyrics().isEnabled(),"enable through actual UI action");
            event.addProperty("index",11);event.addProperty("value",230.4);
            call(screen,"applySiteSetting",new Class<?>[]{String.class,JsonObject.class},"setting",event);
            check(config.getAnimatedLyrics().getOffset()==230,"offset action rounds milliseconds");
            event.addProperty("index",21);event.addProperty("value",1);
            call(screen,"applySiteSetting",new Class<?>[]{String.class,JsonObject.class},"setting",event);
            event.addProperty("index",22);event.addProperty("value",true);
            call(screen,"applySiteSetting",new Class<?>[]{String.class,JsonObject.class},"setting",event);
            var saved=new Gson().fromJson(Files.readString(output.resolve("settings.json")),OrionConfig.class);
            check(saved.getAnimatedLyrics().isEnabled()&&saved.getAnimatedLyrics().getOffset()==230,"UI saves nested settings");
            check(saved.getAnimatedLyrics().getPlacement().equals("Floating")&&saved.getAnimatedLyrics().isFollowView(),"Placement and following save through ClickGUI");
            call(screen,"closeSettings",new Class<?>[]{});
        }
        System.out.println("Lyrics settings checks passed: "+checks);
    }
}
