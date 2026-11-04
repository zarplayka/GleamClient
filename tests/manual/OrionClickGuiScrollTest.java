package ru.orion.client.gui;

import net.minecraft.client.Minecraft;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import sun.misc.Unsafe;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.List;

/** Реальный Java2D-рендер и стабильность кеша прокрутки без запуска игры. */
public final class OrionClickGuiScrollTest {
    private static Field field(Class<?> type, String name) throws Exception { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
    private static void set(Object object, String name, Object value) throws Exception { field(object.getClass(), name).set(object, value); }
    private static Object call(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = object.getClass().getDeclaredMethod(name, types); m.setAccessible(true);
        try { return m.invoke(object, args); } catch (InvocationTargetException e) { throw new RuntimeException(name, e.getCause()); }
    }
    private static Object call(Object object, String name) throws Exception { return call(object, name, new Class<?>[0]); }
    private static Object module(String action) throws Exception {
        Class<?> row = Class.forName(OrionClickGuiScreen.class.getName() + "$ModuleRow");
        for (Object value : (Object[]) field(row, "ALL").get(null)) if (((Enum<?>) field(row, "action").get(value)).name().equals(action)) return value;
        throw new AssertionError(action);
    }
    public static void main(String[] args) throws Exception {
        Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        field(Minecraft.class, "instance").set(null, unsafe.allocateInstance(Minecraft.class));
        var manager = (OrionConfigManager) unsafe.allocateInstance(OrionConfigManager.class);
        var config = OrionConfig.createDefault(); set(manager, "config", config);
        set(manager, "configPath", Path.of(System.getProperty("java.io.tmpdir"), "orion-click-gui-test.json"));
        for (int theme : new int[]{3, 4}) {
            config.setClickGuiTheme(theme);
            config.setClickGuiLastSection("COMBAT");
            var screen = new OrionClickGuiScreen(manager);
            check(call(screen, "selectedSection").equals("COMBAT"), "last combat category restored");
            screen.width = 1280; screen.height = 720;
            Object row = module("BASKETBALL");
            call(screen, "openSettings", new Class<?>[]{row.getClass()}, row);
            call(screen, "applyPalette");
            set(screen, "pageAnimation", 1F); set(screen, "cardsAnimation", 1F);
            set(screen, "scrollAnimation", 10F); set(screen, "settingsScroll", 10);
            List<?> first = (List<?>) call(screen, "fingerprint", new Class<?>[]{int.class}, 2);
            int beforeY = (Integer) call(screen, "settingRowY", new Class<?>[]{int.class}, 1);
            set(screen, "scrollAnimation", 20F); set(screen, "settingsScroll", 20);
            List<?> second = (List<?>) call(screen, "fingerprint", new Class<?>[]{int.class}, 2);
            int afterY = (Integer) call(screen, "settingRowY", new Class<?>[]{int.class}, 1);
            check(first.equals(second), "scroll must reuse the cached texture");
            check(afterY == beforeY - 10, "click coordinates follow visible scroll");
            config.setBasketballVolume(theme == 3 ? .2F : .8F);
            List<?> changed = (List<?>) call(screen, "fingerprint", new Class<?>[]{int.class}, 2);
            check(!second.equals(changed), "changed setting invalidates content");
            List<?> content = (List<?>) call(screen, "fingerprint", new Class<?>[]{int.class, boolean.class}, 2, true);
            int maximum = (Integer) call(screen, "settingsMaxScroll", new Class<?>[]{field(screen.getClass(), "settingsRows").get(screen).getClass()}, field(screen.getClass(), "settingsRows").get(screen));
            for (int offset = 0; offset <= maximum; offset += 7) {
                set(screen, "scrollAnimation", (float) offset); set(screen, "settingsScroll", offset);
                check(content.equals(call(screen, "fingerprint", new Class<?>[]{int.class, boolean.class}, 2, true)), "content cache remains stable across every group");
            }
            set(screen, "scrollAnimation", 20F); set(screen, "settingsScroll", 20);
            Object layout = field(OrionClickGuiScreen.class, "L").get(screen);
            Method accessor = layout.getClass().getDeclaredMethod("moduleW"); accessor.setAccessible(true);
            int designWidth = (Integer) accessor.invoke(layout);
            set(screen, "settingsAtlasStart", 0); set(screen, "settingsAtlasHeight", 328 * 3);
            int guiWidth = (Integer) call(screen, "guiDisplayWidth"), guiHeight = (Integer) call(screen, "guiDisplayHeight");
            Method mxMethod = layout.getClass().getDeclaredMethod("moduleX"); mxMethod.setAccessible(true);
            Method myMethod = layout.getClass().getDeclaredMethod("moduleY"); myMethod.setAccessible(true);
            int mx = (Integer) mxMethod.invoke(layout), my = (Integer) myMethod.invoke(layout);
            float sx = guiWidth / 660F, sy = guiHeight / 416F;
            int atlasW = (Math.round((mx + designWidth) * sx) - Math.round(mx * sx)) * 2;
            int atlasH = (int) Math.ceil(328 * 3 * sy) * 2;
            long start = System.nanoTime();
            BufferedImage strip = null;
            for (int i = 0; i < 8; i++) strip = (BufferedImage) call(screen, "renderSettingsImage", new Class<?>[]{int.class, int.class}, atlasW, atlasH);
            double rasterMillis = (System.nanoTime() - start) / 8_000_000D;
            BufferedImage frame = (BufferedImage) call(screen, "renderImage", new Class<?>[]{int.class}, 2);
            var composite = frame.createGraphics();
            int offset = Math.round(20 * sy) * 2, viewport = (Math.round(408 * sy) - Math.round(my * sy)) * 2;
            composite.drawImage(strip, Math.round(mx * sx) * 2, Math.round(my * sy) * 2,
                    Math.round(mx * sx) * 2 + atlasW, Math.round(my * sy) * 2 + viewport,
                    0, offset, atlasW, offset + viewport, null); composite.dispose();
            ImageIO.write(frame, "png", Path.of(args[0] + "-frame-" + theme + ".png").toFile());
            ImageIO.write(strip, "png", Path.of(args[0] + "-settings-" + theme + ".png").toFile());
            long hitStart = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                set(screen, "scrollAnimation", (float) (10 + i % 20));
                List<?> current = (List<?>) call(screen, "fingerprint", new Class<?>[]{int.class}, 2);
                check(current.equals(changed), "no raster invalidation while scrolling within a group");
            }
            double cacheMicros = (System.nanoTime() - hitStart) / 1_000_000D;
            System.out.println("Theme " + theme + ": cached scroll checks passed; strip raster avg " + rasterMillis + " ms; 1000 fingerprints " + cacheMicros + " ms");
            if (Minecraft.getInstance().player == null) {
                net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
                var level = (net.minecraft.client.multiplayer.ClientLevel) unsafe.allocateInstance(net.minecraft.client.multiplayer.ClientLevel.class);
                field(net.minecraft.world.level.Level.class, "dimension").set(level, net.minecraft.world.level.Level.OVERWORLD);
                var player = (net.minecraft.client.player.LocalPlayer) unsafe.allocateInstance(net.minecraft.client.player.LocalPlayer.class);
                field(net.minecraft.world.entity.Entity.class, "position").set(player, new net.minecraft.world.phys.Vec3(100, 64, 200));
                field(net.minecraft.world.entity.Entity.class, "blockPosition").set(player, new net.minecraft.core.BlockPos(100, 64, 200));
                Minecraft.getInstance().level = level; Minecraft.getInstance().player = player;
            }
            String world = ru.orion.client.effect.OrionWaypoints.worldKey(Minecraft.getInstance());
            if (config.getWaypoints().isEmpty()) for (int i = 0; i < 15; i++) {
                var point = new OrionConfig.Waypoint(i == 0 ? "Очень длинное название метки" : "Метка " + (i + 1), world, i * 100, 64 + i, -i * 50);
                config.getWaypoints().add(point);
            }
            var quick = ru.orion.client.effect.OrionWaypoints.addAtPlayer(manager, Minecraft.getInstance());
            check(quick != null && quick.x == 100 && quick.y == 64 && quick.z == 200 && config.isWaypointsEnabled(), "quick creation uses player location");
            call(screen, "openWaypointPage"); call(screen, "applyPalette");
            check((Boolean) field(screen.getClass(), "waypointPage").get(screen), "dedicated waypoint section");
            Object[] settings = (Object[]) field(screen.getClass(), "settingsRows").get(screen);
            check(java.util.Arrays.stream(settings).anyMatch(setting -> setting.getClass().getSimpleName().equals("KeybindSetting")), "waypoint keybind editor");
            set(screen, "settingsAtlasStart", 0); set(screen, "settingsAtlasHeight", 328 * 3);
            BufferedImage waypointStrip = (BufferedImage) call(screen, "renderSettingsImage", new Class<?>[]{int.class, int.class}, atlasW, atlasH);
            BufferedImage waypointFrame = (BufferedImage) call(screen, "renderImage", new Class<?>[]{int.class}, 2);
            var waypointGraphics = waypointFrame.createGraphics();
            waypointGraphics.drawImage(waypointStrip, Math.round(mx * sx) * 2, Math.round(my * sy) * 2,
                    Math.round(mx * sx) * 2 + atlasW, Math.round(my * sy) * 2 + viewport, 0, 0, atlasW, viewport, null);
            waypointGraphics.dispose();
            ImageIO.write(waypointFrame, "png", Path.of(args[0] + "-waypoints-" + theme + ".png").toFile());
            Object edited = config.getWaypoints().get(1);
            call(screen, "selectWaypoint", new Class<?>[]{int.class}, 2);
            call(screen, "focusWaypointEditor");
            check(field(screen.getClass(), "waypointName").get(screen).equals(config.getWaypoints().get(1).name), "selected waypoint loads editor");
            set(screen, "waypointName", "Изменённая метка"); set(screen, "waypointX", "12345");
            call(screen, "saveWaypoint", new Class<?>[]{boolean.class}, true);
            check(config.getWaypoints().get(1) == edited && config.getWaypoints().get(1).x == 12345 && config.getWaypoints().get(1).name.equals("Изменённая метка"), "editor updates selected waypoint");
            int count = config.getWaypoints().size(); call(screen, "deleteWaypoint");
            check(config.getWaypoints().size() == count - 1 && !config.getWaypoints().contains(edited), "delete removes selected waypoint");
        }
        config.setClickGuiLastSection("PRESETS");
        config.setVisualPresetSelection("BEAUTY"); config.setAppliedVisualPreset("BEAUTY");
        for (int theme : new int[]{3, 4}) {
            config.setClickGuiTheme(theme);
            var presets = new OrionClickGuiScreen(manager); presets.width = 1280; presets.height = 720;
            check(call(presets, "selectedSection").equals("PRESETS"), "preset section restored");
            check((Integer) field(presets.getClass(), "selectedPreset").get(presets) == 3, "preset highlight restored");
            ImageIO.write((BufferedImage) call(presets, "renderImage", new Class<?>[]{int.class}, 2), "png", Path.of(args[0] + "-presets-" + theme + ".png").toFile());
        }
        System.out.println("ClickGUI cached scroll and waypoint section: OK");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
