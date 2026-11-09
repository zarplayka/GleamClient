package ru.orion.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.paradox.lumen.LumenNative;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.UUIDUtil;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionAccounts;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Одна страница на заставку и первое появление меню, затем по странице на открытие меню. */
public final class OrionTitleMenu {
    public static final OrionTitleMenu INSTANCE = new OrionTitleMenu();
    private static final String ROOT = "/assets/orion-client/";
    private static final String PANORAMA_PATH = "textures/gui/gleam_title_panorama.png";
    private static final Identifier PANORAMA = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, PANORAMA_PATH);
    private static final String IAS_SCREEN = "ru.vidtu.ias.screen.AccountScreen";
    private static final long REVEAL_NANOS = 620_000_000L;
    private final OrionLumenView view = new OrionLumenView("title-menu.html", "title_menu",
            ROOT + "fonts/paradox/ParadoxSans-Regular.ttf", OrionTitleMenu::loadPage);
    private JsonObject previous;
    private Screen owner;
    private IasAdd pendingIasAdd;
    private BufferedImage registeredAvatar;
    private String avatarAsset = "";
    private boolean artwork, startupDrawn, titleDrawn;
    private float scale = 1F;
    private long startupAt, revealAt;

    private record IasAdd(Screen accounts, String choice) { }

    private OrionTitleMenu() { }

    public static void register() {
        OrionLumenView.registerPipelines();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> INSTANCE.release());
        if (FabricLoader.getInstance().isModLoaded("ias")) ClientTickEvents.END_CLIENT_TICK.register(INSTANCE::openIasChoice);
    }

    // На первом кадре ShaderManager ещё использует кэш, в котором есть только ресурсы Minecraft.
    public static ShaderSource startupShaders(ShaderSource vanilla) {
        return new ShaderSource() {
            @Override
            public String getShader(Identifier id, ShaderType type) {
                if (!OrionClient.MOD_ID.equals(id.getNamespace()) || !"core/lumen_paint".equals(id.getPath()))
                    return vanilla.getShader(id, type);
                try {
                    return new String(read(ROOT + type.idConverter().idToFile(id).getPath()), StandardCharsets.UTF_8);
                } catch (IOException failure) {
                    LoggerFactory.getLogger("GleamClient").warn("Не удалось загрузить шейдер заставки", failure);
                    return null;
                }
            }

            @Override public CachedIncludeSource getInclude(Identifier id) { return vanilla.getInclude(id); }
            @Override public void close() { vanilla.close(); }
        };
    }

    public boolean startupDrawn() { return startupDrawn; }

    public boolean renderStartup(GuiGraphicsExtractor graphics, float progress) {
        if (!view.ready()) return false;
        long now = System.nanoTime();
        if (startupAt == 0) startupAt = now;
        JsonObject state = new JsonObject();
        state.addProperty("loading", true);
        state.addProperty("progress", Math.round(Math.clamp(progress, 0F, 1F) * 100F));
        state.addProperty("intro", Math.clamp((now - startupAt) / 700_000_000F, 0F, 1F));
        state.addProperty("loadingLabel", OrionGuiLanguage.translate("Загрузка ресурсов"));
        addAccent(state);
        boolean drawn = render(graphics, state);
        startupDrawn |= drawn;
        return drawn;
    }

    public boolean renderTitle(Screen title, GuiGraphicsExtractor graphics, int mouseX, int mouseY, String multiplayerReason) {
        if (!view.ready()) return false;
        if (owner != title) {
            owner = title;
            revealAt = System.nanoTime();
        }
        if (!artwork) {
            try {
                registerImage("gleam-title-panorama.png", ROOT + PANORAMA_PATH);
            } catch (IOException failure) {
                LoggerFactory.getLogger("GleamClient").warn("Не удалось загрузить панораму главного меню", failure);
                release();
                return false;
            }
            artwork = true;
        }
        var accounts = OrionAccounts.INSTANCE;
        var user = Minecraft.getInstance().getUser();
        boolean offline = UUIDUtil.createOfflinePlayerUUID(user.getName()).equals(user.getProfileId())
                || user.getAccessToken().isBlank() || "0".equals(user.getAccessToken());
        var avatar = accounts.avatar();
        if (avatar != registeredAvatar) {
            registeredAvatar = avatar;
            avatarAsset = avatar == null ? "" : OrionSiteMenu.registerAvatar(avatar);
        }
        var state = new JsonObject();
        state.addProperty("reveal", startupDrawn ? Math.min(1F, (System.nanoTime() - revealAt) / (float)REVEAL_NANOS) : 1F);
        state.addProperty("account", user.getName());
        state.addProperty("accountType", offline ? OrionGuiLanguage.translate("Офлайн-профиль") : "Microsoft");
        state.addProperty("gleamProfile", OrionGuiLanguage.translate("Аккаунт GleamClient"));
        state.addProperty("avatar", avatarAsset);
        state.addProperty("minecraftAccounts", OrionGuiLanguage.translate("Аккаунты Minecraft"));
        state.addProperty("addMicrosoft", OrionGuiLanguage.translate("Добавить аккаунт Microsoft"));
        state.addProperty("addOffline", OrionGuiLanguage.translate("Добавить офлайн-профиль"));
        state.addProperty("iasMissing", OrionGuiLanguage.translate("Для аккаунтов Minecraft нужен In-Game Account Switcher"));
        state.addProperty("iasAvailable", FabricLoader.getInstance().isModLoaded("ias"));
        state.addProperty("single", OrionGuiLanguage.translate("Одиночная игра"));
        state.addProperty("multi", OrionGuiLanguage.translate("Сетевая игра"));
        state.addProperty("multiReason", multiplayerReason);
        state.addProperty("modules", OrionGuiLanguage.translate("Модули"));
        state.addProperty("settings", OrionGuiLanguage.translate("Настройки Minecraft"));
        state.addProperty("clientSettings", OrionGuiLanguage.translate("Настройки GleamClient"));
        state.addProperty("quit", OrionGuiLanguage.translate("Выйти из игры"));
        state.addProperty("version", "Minecraft " + SharedConstants.getCurrentVersion().name());
        addAccent(state);
        titleDrawn = render(graphics, state);
        if (titleDrawn) view.move(mouseX / scale, mouseY / scale);
        return titleDrawn;
    }

    private static void addAccent(JsonObject state) {
        var config = OrionClient.getConfigOrNull();
        state.addProperty("accent", config == null ? "" : config.getClickGuiAccent());
        boolean light = config != null && config.getClickGuiTheme() == 4;
        state.addProperty("light", light);
        state.addProperty("themeAction", OrionGuiLanguage.translate(light ? "Тёмная тема" : "Светлая тема"));
    }

    public void openMinecraftAccounts(Screen title, String action) {
        var client = Minecraft.getInstance();
        if (!FabricLoader.getInstance().isModLoaded("ias")) return;
        try {
            Screen accounts = Class.forName(IAS_SCREEN).asSubclass(Screen.class).getConstructor(Screen.class).newInstance(title);
            pendingIasAdd = switch (action) {
                case "add-microsoft" -> new IasAdd(accounts, "ias.add.microsoft");
                case "add-offline" -> new IasAdd(accounts, "ias.add.offline");
                default -> null;
            };
            client.gui.setScreen(accounts);
        } catch (ReflectiveOperationException | LinkageError failure) {
            LoggerFactory.getLogger("GleamClient").warn("Не удалось открыть экран аккаунтов IAS", failure);
            client.gui.setScreen(new AlertScreen(() -> client.gui.setScreen(title),
                    Component.literal(OrionGuiLanguage.translate("Аккаунты Minecraft")),
                    Component.literal(OrionGuiLanguage.translate("Не удалось открыть аккаунты Minecraft"))));
        }
    }

    private void openIasChoice(Minecraft client) {
        IasAdd request = pendingIasAdd;
        if (request == null) return;
        Screen current = client.gui.screen();
        // IAS может сначала показать свой экран подтверждения, затем вернуть тот же экземпляр списка.
        if (current instanceof AlertScreen) return;
        pendingIasAdd = null;
        if (current != request.accounts()) return;
        if (pressIasButton(current, "ias.accounts.add") && client.gui.screen() != current)
            pressIasButton(client.gui.screen(), request.choice());
    }

    private static boolean pressIasButton(Screen screen, String key) {
        if (screen == null) return false;
        Component label = Component.translatable(key);
        for (var listener : screen.children()) {
            if (listener instanceof Button button && button.active && label.equals(button.getMessage())) {
                button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                return true;
            }
        }
        return false;
    }

    static void drawBackdrop(GuiGraphicsExtractor graphics, int width, int height) {
        if (width < 1 || height < 1) return;
        float zoom = Math.max(width / 1920F, height / 1080F) * 1.38F;
        float v = (1F - height / (1080F * zoom)) / 2F;
        graphics.blit(PANORAMA, 0, 0, width, height, 0F, width / (1920F * zoom), v, 1F - v);
    }

    private boolean render(GuiGraphicsExtractor graphics, JsonObject state) {
        var window = Minecraft.getInstance().getWindow();
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        if (width < 1 || height < 1) return false;
        float ratio = Math.clamp(Math.min(window.getWidth() / 1440F, window.getHeight() / 900F), .6F, 2F);
        scale = ratio / window.getGuiScale();
        int layoutWidth = (int)Math.ceil(width / scale), layoutHeight = (int)Math.ceil(height / scale);
        view.resize(layoutWidth, layoutHeight, ratio);
        state.addProperty("width", layoutWidth);
        state.addProperty("height", layoutHeight);
        if (!state.equals(previous)) {
            view.update(state);
            previous = state;
        }
        view.frame();
        return view.draw(graphics, 0, 0, width, height, scale, scale);
    }

    public boolean active(Screen title) { return owner == title && titleDrawn; }

    public void mouse(double x, double y, boolean pressed, Consumer<String> action) {
        float px = (float)x / scale, py = (float)y / scale;
        view.move(px, py);
        if (pressed) view.press(px, py); else view.release(px, py);
        receive(action);
    }

    public boolean key(KeyEvent event, Consumer<String> action) {
        String key = switch (event.key()) {
            case InputConstants.KEY_TAB -> "Tab";
            case InputConstants.KEY_UP -> "ArrowUp";
            case InputConstants.KEY_DOWN -> "ArrowDown";
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> "Enter";
            case InputConstants.KEY_SPACE -> " ";
            case InputConstants.KEY_ESCAPE -> "Escape";
            default -> "";
        };
        if (key.isEmpty()) return false;
        view.key(key, key, event.hasShiftDown() ? 1 : 0, true);
        view.key(key, key, event.hasShiftDown() ? 1 : 0, false);
        receive(action);
        return true;
    }

    public void receive(Consumer<String> action) {
        byte[] messages = view.messages();
        if (messages == null || messages.length == 0 || messages.length > 8192) return;
        for (String line : new String(messages, StandardCharsets.UTF_8).lines().toList()) {
            var value = JsonParser.parseString(line);
            if (value.isJsonArray() && !value.getAsJsonArray().isEmpty()) {
                value = value.getAsJsonArray().get(0);
                if (value.isJsonPrimitive()) value = JsonParser.parseString(value.getAsString());
            }
            if (value.isJsonObject() && value.getAsJsonObject().has("action")) {
                action.accept(value.getAsJsonObject().get("action").getAsString());
                return;
            }
        }
    }

    public void release() {
        view.dispose();
        previous = null;
        owner = null;
        registeredAvatar = null;
        avatarAsset = "";
        startupDrawn = titleDrawn = artwork = false;
        startupAt = revealAt = 0;
    }

    private static byte[] loadPage() {
        try {
            registerImage("gleam-title-badge.png", ROOT + "textures/cosmetics/orion_badge.png");
            return read(ROOT + "html/title-menu.html");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void registerImage(String name, String path) throws IOException {
        LumenNative.registerImage(("assets/" + name).getBytes(StandardCharsets.UTF_8), read(path));
    }

    private static byte[] read(String path) throws IOException {
        try (var stream = OrionTitleMenu.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException(path);
            return stream.readAllBytes();
        }
    }
}
