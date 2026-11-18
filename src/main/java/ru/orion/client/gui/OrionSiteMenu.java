package ru.orion.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.paradox.lumen.LumenNative;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class OrionSiteMenu {
    private static final String ROOT = "/assets/orion-client/";
    private static final String MENU = ROOT + "html/menu/";
    private static final long STATE_REFRESH_NANOS = 50_000_000L;
    private record Avatar(String name, byte[] png) { }
    private static final Map<BufferedImage,Avatar> AVATARS = new java.util.WeakHashMap<>();
    private static Map<String,byte[]> menuImages;
    private static byte[] document;
    private static int avatarSerial;

    private final Consumer<JsonObject> actions;
    private final OrionLumenView view;
    private JsonObject previous;
    private long nextStateAt;
    private float pointerX = Float.NaN, pointerY = Float.NaN;
    private long openedAt;
    private boolean openingFinished;
    private float sx = 1F, sy = 1F;

    OrionSiteMenu(String[] modules, Consumer<JsonObject> actions) {
        this.actions = actions;
        view = new OrionLumenView("menu/index.html","site_click_gui",
                ROOT + "fonts/paradox/ParadoxSans-Regular.ttf",() -> loadDocument(modules));
    }

    boolean ready() { return view.ready(); }

    void revealTheme(JsonObject origin) {
        view.revealTheme(origin.get("x").getAsFloat(),origin.get("y").getAsFloat(),origin.get("radius").getAsFloat());
    }

    void invalidateState() { nextStateAt = 0; }

    boolean render(GuiGraphicsExtractor graphics, int width, int height, int mouseX, int mouseY, Supplier<JsonObject> snapshot) {
        var window = Minecraft.getInstance().getWindow();
        int pixelsX = window.getWidth(), pixelsY = window.getHeight();
        if (width < 1 || height < 1 || pixelsX < 1 || pixelsY < 1) return false;
        sx = width/(float)pixelsX;
        sy = height/(float)pixelsY;
        if (view.resize(pixelsX,pixelsY,1F)) pointerX = pointerY = Float.NaN;
        long now = System.nanoTime();
        if (nextStateAt == 0 || now - nextStateAt >= 0) {
            nextStateAt = now + STATE_REFRESH_NANOS;
            JsonObject state = snapshot.get();
            if (!state.equals(previous)) {
                view.update(state);
                previous = state;
                pointerX = pointerY = Float.NaN;
            }
        }
        if (!openingFinished) {
            float progress = openedAt == 0 ? 0F : Math.min(1F, (System.nanoTime() - openedAt) / 205_000_000F);
            view.opening(progress);
            openingFinished = progress == 1F;
        }
        movePointer(mouseX,mouseY);
        view.frame();
        byte[] logs = view.logs();
        if (logs != null && logs.length != 0) {
            String errors = new String(logs,StandardCharsets.UTF_8);
            for (String line : errors.lines().toList())
                if (line.startsWith("2\t")) LoggerFactory.getLogger("GleamClient ClickGUI").warn("{}",line.substring(2));
        }
        // Проекция GUI делит framebuffer на guiScale без округления размеров экрана.
        float drawScale = 1F / window.getGuiScale();
        boolean drawn = view.draw(graphics,0,0,width,height,drawScale,drawScale);
        if (drawn && openedAt == 0) openedAt = System.nanoTime();
        receive();
        return drawn;
    }

    void press(double x,double y) {
        press(x, y, 0);
    }

    void press(double x, double y, int button) {
        press(x, y, button, false);
    }

    void press(double x, double y, int button, boolean shift) {
        view.pointerModifiers(shift);
        movePointer(x,y);
        view.press((float)x/sx,(float)y/sy,button);
        receive();
    }

    void release(double x,double y) {
        release(x, y, 0);
    }

    void release(double x, double y, int button) {
        movePointer(x,y);
        view.release((float)x/sx,(float)y/sy,button);
        receive();
    }

    void move(double x,double y) {
        movePointer(x,y);
        receive();
    }

    private void movePointer(double x, double y) {
        float px = (float)x/sx, py = (float)y/sy;
        if (pointerX == px && pointerY == py) return;
        pointerX = px;
        pointerY = py;
        view.move(px,py);
    }

    void chatContext(double x, double y) {
        var point = new JsonObject();
        point.addProperty("x", x / sx);
        point.addProperty("y", y / sy);
        view.chatContext(point);
        receive();
    }

    void scroll(double x,double y,double horizontal,double vertical) {
        view.wheel((float)x/sx,(float)y/sy,(float)horizontal,(float)vertical);
        receive();
    }

    boolean key(KeyEvent event,boolean down) {
        String shortcut = editingShortcut(event);
        if (shortcut != null) {
            if (down) {
                var payload = new JsonObject();
                payload.addProperty("command", shortcut);
                if ("paste".equals(shortcut))
                    payload.addProperty("text", Minecraft.getInstance().keyboardHandler.getClipboard());
                view.editShortcut(payload);
                receive();
            }
            return true;
        }
        int value = event.key();
        String key = switch (value) {
            case InputConstants.KEY_ESCAPE -> "Escape";
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> "Enter";
            case InputConstants.KEY_TAB -> "Tab";
            case InputConstants.KEY_BACKSPACE -> "Backspace";
            case InputConstants.KEY_DELETE -> "Delete";
            case InputConstants.KEY_HOME -> "Home";
            case InputConstants.KEY_END -> "End";
            case InputConstants.KEY_LEFT -> "ArrowLeft";
            case InputConstants.KEY_RIGHT -> "ArrowRight";
            case InputConstants.KEY_UP -> "ArrowUp";
            case InputConstants.KEY_DOWN -> "ArrowDown";
            case InputConstants.KEY_SPACE -> " ";
            //? if >=26.3 {
            default -> event.keycode() >= 32 && event.keycode() < 127 ? Character.toString(event.keycode()) : "";
            //?} else {
                        /*default -> event.key() >= 32 && event.key() < 127 ? Character.toString(event.key()) : "";
            *///?}
        };
        if (key.isEmpty()) return false;
        String code = value >= InputConstants.KEY_A && value <= InputConstants.KEY_Z
                ? "Key" + (char)('A' + value - InputConstants.KEY_A) : key;
        int modifiers = (event.hasShiftDown()?1:0) | (event.hasControlDownWithQuirk()?2:0) | (event.hasAltDown()?4:0);
        boolean consumed = view.key(key,code,modifiers,down);
        receive();
        return consumed;
    }

    private static String editingShortcut(KeyEvent event) {
        if (event.hasShiftDown() && !event.hasControlDownWithQuirk() && !event.hasAltDown()
                && event.key() == InputConstants.KEY_INSERT) return "paste";
        if (!event.hasControlDownWithQuirk() || event.hasAltDown()) return null;
        return switch (event.key()) {
            case InputConstants.KEY_BACKSPACE -> "delete-word";
            case InputConstants.KEY_A -> "select";
            case InputConstants.KEY_C, InputConstants.KEY_INSERT -> "copy";
            case InputConstants.KEY_X -> "cut";
            case InputConstants.KEY_V -> "paste";
            case InputConstants.KEY_Z -> event.hasShiftDown() ? "redo" : "undo";
            case InputConstants.KEY_Y -> "redo";
            default -> null;
        };
    }

    void text(String text) {
        view.text(text);
        receive();
    }

    void dispose() {
        view.dispose();
        previous = null;
        invalidateState();
        pointerX = pointerY = Float.NaN;
        openedAt = 0;
        openingFinished = false;
    }

    private void receive() {
        byte[] messages = view.messages();
        if (messages == null || messages.length == 0 || messages.length > 32_768) return;
        for (String line : new String(messages, StandardCharsets.UTF_8).replace('\0', '\n').lines().toList()) {
            if (line.isBlank()) continue;
            try {
                var event = JsonParser.parseString(line);
                if (event.isJsonObject()) dispatch(event.getAsJsonObject());
                else if (event.isJsonArray()) {
                    for (var item : event.getAsJsonArray()) {
                        var value = item.isJsonPrimitive() ? JsonParser.parseString(item.getAsString()) : item;
                        if (value.isJsonObject()) dispatch(value.getAsJsonObject());
                    }
                }
            } catch (RuntimeException failure) {
                LoggerFactory.getLogger("GleamClient ClickGUI").warn("Не удалось обработать действие меню",failure);
            }
        }
    }

    private void dispatch(JsonObject event) {
        var type = event.get("action");
        if (type != null && type.isJsonPrimitive()
                && !"cosmetic-preview".equals(type.getAsString()) && !"ui-sound".equals(type.getAsString())) invalidateState();
        actions.accept(event);
    }

    private static byte[] loadDocument(String[] modules) {
        try {
            for (String face : new String[]{"Regular","Medium","SemiBold","Bold"})
                LumenNative.registerFont(bytes("assets/orion-"+face.toLowerCase(Locale.ROOT)+".ttf"),
                        read(ROOT+"fonts/paradox/ParadoxSans-"+face+".ttf"));
            // Lumen очищает реестр ресурсов после закрытия последнего экземпляра движка.
            if (menuImages == null) {
                var loaded = new LinkedHashMap<String,byte[]>();
                loaded.put("orion-badge",read(ROOT+"textures/cosmetics/orion_badge.png"));
                try (var stream = OrionSiteMenu.class.getResourceAsStream(ROOT+"textures/gui/click_gui_modules.png")) {
                    if (stream == null) throw new IOException("Атлас иконок GleamClient отсутствует");
                    BufferedImage atlas = ImageIO.read(stream);
                    int size = OrionClickGuiScreen.MODULE_ICON_CELL;
                    int columns = OrionClickGuiScreen.MODULE_ICON_COLUMNS;
                    for (int i=0; i<modules.length; i++) {
                        int cell=i+1;
                        loaded.put(modules[i].toLowerCase(Locale.ROOT),
                                png(atlas.getSubimage(cell % columns * size, cell / columns * size, size, size)));
                    }
                }
                try (var stream = OrionSiteMenu.class.getResourceAsStream(ROOT+"textures/gui/click_gui_cosmetics.png")) {
                    if (stream == null) throw new IOException("Атлас предпросмотра косметики отсутствует");
                    BufferedImage atlas = ImageIO.read(stream);
                    String[] names = {"angel","dragon","halo","hat","dance"};
                    int width = atlas.getWidth() / names.length, height = atlas.getHeight() / 2;
                    for (int i = 0; i < names.length; i++) {
                        loaded.put("cosmetic-"+names[i]+"-item", png(atlas.getSubimage(i * width, 0, width, height)));
                        loaded.put("cosmetic-"+names[i]+"-preview", png(atlas.getSubimage(i * width, height, width, height)));
                    }
                }
                for (String id : ru.orion.client.effect.GleamWingModel.IDS.subList(2, ru.orion.client.effect.GleamWingModel.IDS.size())) {
                    String name = "cosmetic-" + id + "-item";
                    loaded.put(name, read(MENU + "assets/" + name + ".png"));
                }
                for (String id : ru.orion.client.cosmetic.GleamHatModel.IDS) {
                    String name = "cosmetic-" + id + "-item";
                    loaded.put(name, read(MENU + "assets/" + name + ".png"));
                }
                for (var pet : ru.orion.client.cosmetic.GleamPet.values()) {
                    String name = "cosmetic-" + pet.id() + "-item";
                    loaded.put(name, read(MENU + "assets/" + name + ".png"));
                }
                menuImages = loaded;
            }
            menuImages.forEach(OrionSiteMenu::registerPng);
            registerSettingIcons();
            for (Avatar avatar : AVATARS.values()) registerPng(avatar.name(),avatar.png());
            if (document != null) return document;
            // Lumen не применяет селекторы вида :root[data-theme] и :root[data-density].
            String css = (string(MENU+"style.css") + "\n" + string(MENU+"lumen.css"))
                    .replace("100dvh", "100vh").replace(":root[", "html[");
            String index = string(MENU+"index.html")
                    .replace("<link rel=\"stylesheet\" href=\"style.css\">","<style>"+css+"</style>")
                    .replace("<script defer src=\"catalog.js\"></script>","")
                    .replace("<script defer src=\"preview.js\"></script>","");
            StringBuilder translations = new StringBuilder("window.orionTranslations = {};\n");
            for (String language : new String[]{"en", "de", "es"})
                translations.append("window.orionTranslations.").append(language).append(" = ")
                        .append(string(ROOT+"lang/click_gui_"+language+".json").replace("</", "<\\/")).append(";\n");
            document = bytes(index.replace("</body>","<script>" + translations + string(MENU+"preview.js")+"\n"
                    + string(MENU+"game.js")+"</script></body>"));
            return document;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String avatar(BufferedImage image) {
        if (image == null) return "orion-badge";
        Avatar avatar = AVATARS.computeIfAbsent(image,source -> {
            String name = "account-"+avatarSerial++;
            try {
                byte[] data = png(source);
                registerPng(name,data);
                return new Avatar(name,data);
            } catch (IOException e) {
                return null;
            }
        });
        return avatar == null ? "orion-badge" : avatar.name();
    }

    static String registerAvatar(BufferedImage image) {
        Avatar cached = AVATARS.get(image);
        if (cached == null) return avatar(image);
        registerPng(cached.name(),cached.png());
        return cached.name();
    }

    private static void registerSettingIcons() {
        var resources = Minecraft.getInstance().getResourceManager();
        for (String path : OrionClickGuiScreen.settingIcons()) {
            var resource = resources.getResource(Identifier.withDefaultNamespace("textures/" + path + ".png"));
            if (resource.isEmpty()) continue;
            try (var stream = resource.get().open()) {
                byte[] data;
                if (path.equals("entity/player/wide/steve")) {
                    var skin = ImageIO.read(stream);
                    if (skin == null || skin.getWidth() < 16 || skin.getHeight() < 16) continue;
                    int unit = Math.max(1, skin.getWidth() / 8);
                    if (skin.getHeight() < unit * 2) continue;
                    data = png(skin.getSubimage(unit, unit, unit, unit));
                } else {
                    data = stream.readAllBytes();
                }
                registerPng(OrionClickGuiScreen.settingIconName(path), data);
            } catch (IOException exception) {
                LoggerFactory.getLogger("GleamClient ClickGUI").debug("Could not load setting icon {}", path, exception);
            }
        }
    }

    private static byte[] png(BufferedImage image) throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(image,"PNG",output);
        return output.toByteArray();
    }

    private static void registerPng(String name,byte[] png) {
        LumenNative.registerImage(bytes("assets/"+name+".png"),png);
    }
    private static byte[] read(String path) throws IOException {
        try (var stream = OrionSiteMenu.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Отсутствует ресурс "+path);
            return stream.readAllBytes();
        }
    }

    private static String string(String path) throws IOException { return new String(read(path),StandardCharsets.UTF_8); }
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
}
