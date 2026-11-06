package ru.orion.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageManager;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.config.OrionConfigLibrary;
import sun.misc.Unsafe;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.List;

/** Рендер страниц профилей в обеих темах и CRUD локальной библиотеки. */
public final class OrionProfilesUiTest {
    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void set(Object target, String name, Object value) throws Exception {
        field(target.getClass(), name).set(target, value);
    }
    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true);
        return method.invoke(target, args);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    public static void main(String[] args) throws Exception {
        Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        Minecraft client = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        field(Minecraft.class, "instance").set(null, client);
        var language = new LanguageManager(client, "ru_ru", ignored -> {});
        field(Minecraft.class, "languageManager").set(client, language);
        var manager = (OrionConfigManager) unsafe.allocateInstance(OrionConfigManager.class);
        var config = OrionConfig.createDefault();
        set(manager, "config", config);
        Path file = Path.of(args[0], "settings.json");
        set(manager, "configPath", file);

        var library = manager.getConfigLibrary();
        var original = library.local(manager.exportProfile()).get();
        check(original.size() == 1 && original.getFirst().entry().id() == 0, "original snapshot");
        var created = library.saveLocal("Local setup", manager.exportProfile()).get();
        int id = created.get(1).entry().id();
        check(id < 0, "local ID must not collide with catalog");
        var renamed = library.renameLocal(id, "Renamed local").get();
        check(renamed.get(1).entry().name().equals("Renamed local"), "rename");
        config.setZoomEnabled(true);
        library.updateLocal(id, manager.exportProfile()).get();
        var reopened = (OrionConfigManager) unsafe.allocateInstance(OrionConfigManager.class);
        set(reopened, "config", OrionConfig.createDefault()); set(reopened, "configPath", file);
        var saved = reopened.getConfigLibrary().local(reopened.exportProfile()).get();
        check(saved.get(1).entry().name().equals("Renamed local") && saved.get(1).settings().get("zoomEnabled").getAsBoolean(), "CRUD persists");
        check(library.removeLocal(id).get().size() == 1, "remove");
        try { library.removeLocal(0).get(); throw new AssertionError("original must be protected"); }
        catch (java.util.concurrent.ExecutionException expected) {}

        var entry = new OrionConfigLibrary.Entry(42, "BEST CONFIG — Long profile title for layout review", "LayOffick", "owner", 123456, 1793770000, 1793773600, 2);
        var local = List.of(new OrionConfigLibrary.Profile(new OrionConfigLibrary.Entry(0, "Изначальный конфиг", ""), manager.exportProfile()),
                new OrionConfigLibrary.Profile(entry, manager.exportProfile()));
        for (int theme : new int[]{3, 4}) for (String code : new String[]{"ru_ru", "en_us"}) {
            config.setClickGuiTheme(theme); config.setClickGuiLastSection("ALL");
            language.setSelected(code);
            var screen = new OrionClickGuiScreen(manager);
            screen.width = 1280; screen.height = 720;
            set(screen, "pageAnimation", 1F); set(screen, "cardsAnimation", 1F);
            set(screen, "presetsPage", true); set(screen, "paradoxAll", false); set(screen, "selectedPreset", 3);
            var image = (BufferedImage) call(screen, "renderImage", new Class<?>[]{int.class}, 2);
            ImageIO.write(image, "png", Path.of(args[0], "presets-" + theme + "-" + code + ".png").toFile());
            set(screen, "presetsPage", false); set(screen, "configsPage", true);
            set(screen, "catalogEntries", List.of(entry)); set(screen, "catalogTotal", 1); set(screen, "configSelected", 0); set(screen, "localConfigs", local);
            image = (BufferedImage) call(screen, "renderImage", new Class<?>[]{int.class}, 2);
            ImageIO.write(image, "png", Path.of(args[0], "catalog-" + theme + "-" + code + ".png").toFile());
            set(screen, "configsLocal", true); set(screen, "configSelected", 1); set(screen, "configEditor", 2);
            set(screen, "configTitle", "Renamed local"); set(screen, "configTitleFocused", true); set(screen, "textCaret", 13);
            image = (BufferedImage) call(screen, "renderImage", new Class<?>[]{int.class}, 2);
            ImageIO.write(image, "png", Path.of(args[0], "rename-" + theme + "-" + code + ".png").toFile());
        }
        System.out.println("Local save/rename/update/remove/reopen and RU/EN profile page renders passed");
    }
}
