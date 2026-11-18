package ru.orion.client.gui;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageManager;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigLibrary;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.config.OrionVisualPresets;
import sun.misc.Unsafe;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exports the real ClickGUI window, navigation and module states for the website. */
public final class OrionInterfaceCapture {
    private static final Class<?> SCREEN = OrionClickGuiScreen.class;
    private static final int SCALE = 2;

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) throws ReflectiveOperationException {
        Method method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static int number(Object object, String name) throws ReflectiveOperationException {
        return field(object.getClass(), name).getInt(object);
    }

    private static BufferedImage image(int width, int height) {
        return new BufferedImage(width * SCALE, height * SCALE, BufferedImage.TYPE_INT_ARGB);
    }

    private static Graphics2D graphics(BufferedImage image) throws ReflectiveOperationException {
        Graphics2D graphics = image.createGraphics();
        method(SCREEN, "setupGraphics", Graphics2D.class).invoke(null, graphics);
        graphics.scale(SCALE, SCALE);
        return graphics;
    }

    private static void copy(Graphics2D target, BufferedImage image, int x, int y, int w, int h, int toX, int toY) {
        target.drawImage(image, toX, toY, toX + w, toY + h, x * SCALE, y * SCALE,
                (x + w) * SCALE, (y + h) * SCALE, null);
    }

    private static String color(OrionClickGuiScreen screen, String name) throws ReflectiveOperationException {
        Color color = (Color) field(SCREEN, name).get(screen);
        return String.format("#%06x", color.getRGB() & 0xffffff);
    }

    private static Map<String, Object> capture(OrionClickGuiScreen screen, Path output) throws Exception {
        Object layout = field(SCREEN, "L").get(screen);
        int width = number(layout, "guiW"), height = number(layout, "guiH");
        int sidebar = number(layout, "sidebarW") + 1;
        int x = number(layout, "moduleX"), y = number(layout, "moduleY");
        int cardWidth = (int) method(SCREEN, "gridCardWidth").invoke(screen);
        int cardHeight = field(SCREEN, "GRID_HEIGHT").getInt(null);
        int step = field(SCREEN, "GRID_STEP").getInt(null);
        int gap = field(SCREEN, "GRID_GAP").getInt(null);
        int columns = field(SCREEN, "GRID_COLUMNS").getInt(null);
        int visible = (int) method(SCREEN, "maxVisibleRows").invoke(screen);
        int slotHeight = y + 27;
        Class<?> rowType = Class.forName(SCREEN.getName() + "$ModuleRow");
        Object[] rows = (Object[]) field(rowType, "ALL").get(null);
        Method title = method(rowType, "title"), description = method(rowType, "description"), action = method(rowType, "action");
        Method drawCard = method(SCREEN, "drawGridModule", Graphics2D.class, rowType, int.class, float.class);
        var rowSections = new LinkedHashMap<Object, String>();
        List<String> sections = List.of("COMBAT", "PLAYER", "MOVEMENT", "RENDER", "WORLD", "MISC");
        for (String section : sections) {
            for (Object row : (Object[]) field(rowType, section).get(null)) rowSections.put(row, section);
        }

        List<String> views = new ArrayList<>(List.of("ALL", "FAVORITES"));
        views.addAll(sections);
        views.add("SEARCH");
        int cardsY = height + views.size() * slotHeight;
        int packedColumns = width / cardWidth;
        int cardRows = (rows.length * 2 + packedColumns - 1) / packedColumns;
        BufferedImage atlas = image(width, cardsY + cardRows * cardHeight + 12);
        Graphics2D target = graphics(atlas);
        Method window = method(SCREEN, "drawWindow", Graphics2D.class, Shape.class);
        Method header = method(SCREEN, "drawHeader", Graphics2D.class, Shape.class);
        Method drawSidebar = method(SCREEN, "drawSidebar", Graphics2D.class);
        Method content = method(SCREEN, "drawContent", Graphics2D.class);
        Method restore = method(SCREEN, "restoreLastSection", String.class);
        Shape shape = new RoundRectangle2D.Float(0, 0, width, height, number(layout, "windowRadius") * 2, number(layout, "windowRadius") * 2);
        Field selected = field(SCREEN, "selectedCategory"), all = field(SCREEN, "paradoxAll");
        Field search = field(SCREEN, "searchQuery");

        all.setBoolean(screen, false);
        selected.setInt(screen, -1);
        target.setComposite(AlphaComposite.SrcOver.derive(.78F));
        window.invoke(screen, target, shape);
        target.setComposite(AlphaComposite.SrcOver);
        header.invoke(screen, target, shape);
        drawSidebar.invoke(screen, target);

        var viewData = new LinkedHashMap<String, Object>();
        for (int index = 0; index < views.size(); index++) {
            String view = views.get(index);
            boolean searching = view.equals("SEARCH");
            selected.setInt(screen, 0);
            restore.invoke(screen, searching ? "ALL" : view);
            search.set(screen, searching ? "___orion_capture_no_matches___" : "");
            int slotY = height + index * slotHeight;
            BufferedImage layer = image(width, height);
            Graphics2D layerGraphics = graphics(layer);
            header.invoke(screen, layerGraphics, shape);
            drawSidebar.invoke(screen, layerGraphics);
            content.invoke(screen, layerGraphics);
            layerGraphics.dispose();
            copy(target, layer, sidebar, 0, width - sidebar, y, sidebar, slotY);
            if (searching || view.equals("FAVORITES")) {
                copy(target, layer, sidebar, y, width - sidebar, slotHeight - y, sidebar, slotY + y);
            }
            int navY = switch (view) {
                case "ALL" -> 69;
                case "FAVORITES" -> 88;
                case "SEARCH" -> -1;
                default -> (int) method(SCREEN, "categoryRowY", int.class).invoke(screen, selected.getInt(screen));
            };
            if (navY >= 0) copy(target, layer, 0, navY, sidebar, 18, 0, slotY);
            viewData.put(view, new int[]{slotY, navY});
        }

        search.set(screen, "");
        float[] toggles = (float[]) field(SCREEN, "toggleAnim").get(screen);
        var cardData = new ArrayList<Object>();
        for (int index = 0; index < rows.length; index++) {
            Object row = rows[index];
            int ordinal = ((Enum<?>) action.invoke(row)).ordinal();
            for (int active = 0; active < 2; active++) {
                int slot = index * 2 + active;
                Graphics2D card = (Graphics2D) target.create();
                card.translate((slot % packedColumns) * cardWidth - x, cardsY + (slot / packedColumns) * cardHeight - y);
                toggles[ordinal] = active;
                drawCard.invoke(screen, card, row, 0, 0F);
                card.dispose();
            }
            String name = (String) title.invoke(row);
            String details = (String) description.invoke(row);
            cardData.add(List.of(name, OrionGuiLanguage.translate(name), rowSections.get(row), details, OrionGuiLanguage.translate(details)));
        }
        int starsY = cardsY + cardRows * cardHeight;
        Method star = method(SCREEN, "drawFavoriteStar", Graphics2D.class, float.class, float.class, boolean.class);
        star.invoke(screen, target, 6F, starsY + 6F, false);
        star.invoke(screen, target, 18F, starsY + 6F, true);
        target.dispose();
        ImageIO.write(atlas, "png", output.toFile());

        var manifest = new LinkedHashMap<String, Object>();
        manifest.put("size", new int[]{width, height});
        manifest.put("scale", SCALE);
        manifest.put("sidebar", sidebar);
        manifest.put("grid", new int[]{x, y, cardWidth, cardHeight, gap, step, columns, visible, cardsY, packedColumns});
        manifest.put("search", new int[]{
                (int) method(SCREEN, "searchX").invoke(screen), (int) method(SCREEN, "searchY").invoke(screen),
                (int) method(SCREEN, "searchW").invoke(screen), (int) method(SCREEN, "searchH").invoke(screen)});
        manifest.put("scrollX", x + number(layout, "moduleW") + 3);
        manifest.put("views", viewData);
        manifest.put("cards", cardData);
        manifest.put("starsY", starsY);
        manifest.put("ink", color(screen, "TEXT"));
        manifest.put("input", color(screen, "SEARCH_BG"));
        manifest.put("accent", color(screen, "PRIMARY"));
        manifest.put("border", color(screen, "BORDER"));
        return manifest;
    }

    private static int callInt(OrionClickGuiScreen screen, String name) throws ReflectiveOperationException {
        return (int) method(SCREEN, name).invoke(screen);
    }

    private static Map<String, Object> capturePages(OrionClickGuiScreen screen, OrionConfigManager manager, Path output) throws Exception {
        OrionConfig config = manager.getConfig();
        boolean initialWaypoints = config.isWaypointsEnabled();
        Method close = method(SCREEN, "closeSettings");
        Method applyPalette = method(SCREEN, "applyPalette");
        Method restore = method(SCREEN, "restoreLastSection", String.class);
        Method window = method(SCREEN, "drawWindow", Graphics2D.class, Shape.class);
        Method header = method(SCREEN, "drawHeader", Graphics2D.class, Shape.class);
        Method sidebar = method(SCREEN, "drawSidebar", Graphics2D.class);
        Method content = method(SCREEN, "drawContent", Graphics2D.class);
        close.invoke(screen);
        applyPalette.invoke(screen);
        Object layout = field(SCREEN, "L").get(screen);
        int width = number(layout, "guiW"), height = number(layout, "guiH");
        int profileCount = OrionVisualPresets.Profile.values().length;
        var pageCounts = new LinkedHashMap<String, Integer>();
        pageCounts.put("WAYPOINTS", 2);
        pageCounts.put("PRESETS", profileCount);
        pageCounts.put("CONFIGS", 3);
        pageCounts.put("SETTINGS", 1);
        int frames = pageCounts.values().stream().mapToInt(Integer::intValue).sum();
        int columns = 3;
        BufferedImage pages = image(width * columns, height * ((frames + columns - 1) / columns));
        Graphics2D target = graphics(pages);
        var positions = new LinkedHashMap<String, List<int[]>>();
        var nav = new LinkedHashMap<String, Integer>();
        var actions = new LinkedHashMap<String, List<int[]>>();
        Class<?> catalog = Class.forName(SCREEN.getName() + "$CatalogSnapshot");
        var snapshotConstructor = catalog.getDeclaredConstructor(int.class, OrionConfigLibrary.Page.class, long.class);
        snapshotConstructor.setAccessible(true);
        Object[] snapshots = (Object[]) field(SCREEN, "catalogCache").get(screen);
        for (int i = 0; i < snapshots.length; i++) {
            snapshots[i] = snapshotConstructor.newInstance(0, new OrionConfigLibrary.Page(List.of(), false), System.nanoTime());
        }
        field(SCREEN, "localConfigs").set(screen, manager.getConfigLibrary().local(manager.exportProfile()).get());
        int slot = 0;
        for (var entry : pageCounts.entrySet()) {
            String page = entry.getKey();
            var pageFrames = new ArrayList<int[]>();
            positions.put(page, pageFrames);
            for (int variant = 0; variant < entry.getValue(); variant++) {
                close.invoke(screen);
                applyPalette.invoke(screen);
                if (page.equals("CONFIGS")) {
                    // Use the actual config page with a sandbox library and empty catalog, without connecting to Orion.
                    field(SCREEN, "configsPage").setBoolean(screen, true);
                    field(SCREEN, "paradoxAll").setBoolean(screen, false);
                    field(SCREEN, "paradoxFavoritesOnly").setBoolean(screen, false);
                    method(SCREEN, "selectConfigTab", int.class).invoke(screen, variant);
                } else {
                    restore.invoke(screen, page);
                }
                applyPalette.invoke(screen);
                Object pageLayout = field(SCREEN, "L").get(screen);
                if (page.equals("WAYPOINTS")) {
                    boolean active = variant == 1;
                    config.setWaypointsEnabled(active);
                    Class<?> action = Class.forName(SCREEN.getName() + "$Action");
                    for (Object constant : action.getEnumConstants()) {
                        Enum<?> value = (Enum<?>) constant;
                        if (value.name().equals("WAYPOINTS")) {
                            ((float[]) field(SCREEN, "toggleAnim").get(screen))[value.ordinal()] = active ? 1F : 0F;
                            break;
                        }
                    }
                    field(SCREEN, "drawingSettingsLayer").setBoolean(screen, true);
                    field(SCREEN, "settingsAtlasHeight").setInt(screen, height - number(pageLayout, "moduleY") - 8);
                } else if (page.equals("PRESETS")) {
                    field(SCREEN, "selectedPreset").setInt(screen, variant);
                }
                if (variant == 0) {
                    int rowY = switch (page) {
                        case "WAYPOINTS", "PRESETS", "CONFIGS" -> field(SCREEN, page.replace("WAYPOINTS", "WAYPOINT") + "_NAV_Y").getInt(null);
                        default -> (int) method(SCREEN, "categoryRowY", int.class).invoke(screen, field(SCREEN, "selectedCategory").getInt(screen));
                    };
                    nav.put(page, rowY);
                    actions.put(page, pageActions(screen, pageLayout, page, profileCount, width));
                }
                int x = slot % columns * width, y = slot / columns * height;
                pageFrames.add(new int[]{x, y});
                slot++;
                Graphics2D layer = (Graphics2D) target.create();
                layer.translate(x, y);
                layer.clipRect(0, 0, width, height);
                int radius = number(pageLayout, "windowRadius") * 2;
                Shape shape = new RoundRectangle2D.Float(0, 0, width, height, radius, radius);
                layer.setComposite(AlphaComposite.SrcOver.derive(.78F));
                window.invoke(screen, layer, shape);
                layer.setComposite(AlphaComposite.SrcOver);
                header.invoke(screen, layer, shape);
                sidebar.invoke(screen, layer);
                content.invoke(screen, layer);
                layer.dispose();
                field(SCREEN, "drawingSettingsLayer").setBoolean(screen, false);
            }
        }
        config.setWaypointsEnabled(initialWaypoints);
        target.dispose();
        ImageIO.write(pages, "png", output.toFile());
        return Map.of("frames", positions, "nav", nav, "actions", actions,
                "defaultPreset", manager.getVisualPresets().selectedProfile().ordinal());
    }

    private static List<int[]> pageActions(OrionClickGuiScreen screen, Object layout, String page, int profileCount, int width)
            throws ReflectiveOperationException {
        var actions = new ArrayList<int[]>();
        switch (page) {
            case "WAYPOINTS" -> {
                int size = number(layout, "gearSize");
                actions.add(new int[]{number(layout, "sidebarW") + 12, number(layout, "backY"), size, size});
                actions.add(new int[]{width - 110, 43, 92, 24});
            }
            case "PRESETS" -> {
                for (int i = 0; i < profileCount; i++) {
                    int x = (int) method(SCREEN, "presetCardX", int.class).invoke(screen, i);
                    int y = (int) method(SCREEN, "presetCardY", int.class).invoke(screen, i);
                    actions.add(new int[]{x, y, callInt(screen, "presetCardWidth"), callInt(screen, "presetCardHeight")});
                }
            }
            case "CONFIGS" -> {
                for (int i = 0; i < 3; i++) actions.add(new int[]{number(layout, "moduleX") + i * 99, 48, 93, 24});
            }
            case "SETTINGS" -> {
                int size = number(layout, "arrowSize");
                int y = (int) method(SCREEN, "settingRowY", int.class).invoke(screen, 0) + callInt(screen, "arrowOffsetY");
                actions.add(new int[]{callInt(screen, "choiceLeftArrowX"), y, size, size});
                actions.add(new int[]{callInt(screen, "choiceRightArrowX"), y, size, size});
            }
        }
        return actions;
    }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        Minecraft client = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        field(Minecraft.class, "instance").set(null, client);
        var manager = (OrionConfigManager) unsafe.allocateInstance(OrionConfigManager.class);
        var config = OrionConfig.createDefault();
        field(OrionConfigManager.class, "config").set(manager, config);
        field(OrionConfigManager.class, "configPath").set(manager, Files.createTempDirectory("orion-interface-capture").resolve("config.json"));
        config.setClickGuiLastSection("ALL");
        config.setFavoriteModules(List.of());
        var manifest = new LinkedHashMap<String, Object>();
        for (String language : List.of("ru", "en")) {
            field(Minecraft.class, "languageManager").set(client, new LanguageManager(client, language.equals("ru") ? "ru_ru" : "en_us", ignored -> {}));
            for (String theme : List.of("dark", "light")) {
                config.setClickGuiTheme(theme.equals("dark") ? 3 : 4);
                var screen = new OrionClickGuiScreen(manager);
                screen.width = 1600;
                screen.height = 1000;
                String key = language + "-" + theme;
                var capture = capture(screen, output.resolve(key + ".png"));
                capture.put("pages", capturePages(screen, manager, output.resolve(key + "-pages.png")));
                manifest.put(key, capture);
            }
        }
        Files.writeString(output.resolve("manifest.json"), new Gson().toJson(manifest));
    }
}
