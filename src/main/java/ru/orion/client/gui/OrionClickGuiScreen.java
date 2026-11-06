package ru.orion.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.config.OrionConfigLibrary;
import ru.orion.client.config.OrionAccounts;
import ru.orion.client.config.OrionVisualPresets.Profile;
import ru.orion.client.hud.OrionGlassCompositor;
import ru.orion.client.effect.OrionHitSound;
import ru.orion.client.effect.OrionSkinLayers;
import ru.orion.client.effect.OrionItemScroller;
import ru.orion.client.effect.OrionZoom;
import ru.orion.client.effect.OrionTargetEsp;
import ru.orion.client.keybind.OrionKeybinds;
import net.minecraft.client.KeyMapping;

import java.awt.BasicStroke;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Arc2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.net.URI;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class OrionClickGuiScreen extends Screen {
    private static final Component TITLE = Component.literal(OrionClient.VERSION_TEXT);
    private static final Logger LOGGER = LoggerFactory.getLogger(OrionClient.MOD_ID);
    private static final Identifier GUI_TEXTURE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "click_gui_dynamic");

    // Макет из Figma (окно 770x850, коэффициент 0.588 -> gui-юниты 453x501).
    private static final int GUI_WIDTH = 453;
    private static final int GUI_HEIGHT = 501;
    private static final int HEADER_HEIGHT = 33; // 56px в макете
    private static final int WINDOW_RADIUS = 11;
    private static final int SIDEBAR_WIDTH = 126;
    private static final int SIDEBAR_ITEM_X = 8;
    private static final int SIDEBAR_ITEM_Y = 47;
    private static final int SIDEBAR_ITEM_WIDTH = 110;
    private static final int SIDEBAR_ITEM_HEIGHT = 34;
    private static final int SIDEBAR_ITEM_STEP = 38;
    private static final int CONTENT_X = 140;
    private static final int MODULE_X = CONTENT_X;
    private static final int MODULE_Y = 84;
    private static final int MODULE_WIDTH = 301;
    private static final int MODULE_HEIGHT = 52;
    private static final int MODULE_STEP = 59; // 52 карточка + 7 отступ (12px макета)
    private static final int ICON_TEXT_GAP = 7; // 12px макета
    private static final int TOGGLE_WIDTH = 31;
    private static final int TOGGLE_HEIGHT = 19;
    private static final int TOGGLE_ANIMATION_MS = 180;
    private static final int HOVER_ANIMATION_MS = 160;
    private static final int MAX_TEXTURE_SCALE = 6;
    private static final long TEXTURE_UPDATE_INTERVAL_NANOS = 16_666_667L;
    private static final float ORION_TARGET_SCALE = 1.3F;
    private static final float ORION2_TARGET_SCALE = 0.8F;
    private static final float ORION3_TARGET_SCALE = 1.15F;
    private static final int ORION3_LOGO_AREA_HEIGHT = 62;
    private static final int SCREEN_FIT_MARGIN = 12;
    private static final int GEAR_SIZE = 25;
    private static final int BACK_BUTTON_Y = 45;
    private static final int ARROW_SIZE = 18;
    private static final int CHOICE_VALUE_WIDTH = 64;
    private static final int SLIDER_TRACK_WIDTH = 80;
    private static final int SWATCH_SIZE = 14;
    private static final int SWATCH_STEP = 18;

    // Геометрия, зависящая от темы: классические темы делят один макет 453x501,
    // ORION3 — компактное окно 210x300 из своей Figma (фрейм 513x741, множитель ~0.41).
    private record Layout(
            int guiW, int guiH, int windowRadius, int sidebarW,
            int sideX, int sideY, int sideW, int sideH, int sideStep,
            int moduleX, int moduleY, int moduleW, int moduleH, int moduleStep,
            int toggleW, int toggleH, int gearSize, int backY, int arrowSize,
            int choiceValueW, int sliderTrackW, int swatchSize, int swatchStep
    ) {
    }

    private static final Layout CLASSIC_LAYOUT = new Layout(
            GUI_WIDTH, GUI_HEIGHT, WINDOW_RADIUS, SIDEBAR_WIDTH,
            SIDEBAR_ITEM_X, SIDEBAR_ITEM_Y, SIDEBAR_ITEM_WIDTH, SIDEBAR_ITEM_HEIGHT, SIDEBAR_ITEM_STEP,
            MODULE_X, MODULE_Y, MODULE_WIDTH, MODULE_HEIGHT, MODULE_STEP,
            TOGGLE_WIDTH, TOGGLE_HEIGHT, GEAR_SIZE, BACK_BUTTON_Y, ARROW_SIZE,
            CHOICE_VALUE_WIDTH, SLIDER_TRACK_WIDTH, SWATCH_SIZE, SWATCH_STEP
    );

    private static final Layout ORION3_LAYOUT = new Layout(
            360, 300, 10, 84,
            6, 73, 72, 23, 27,
            96, 61, 252, 33, 38,
            27, 14, 18, 17, 16,
            76, 96, 10, 13
    );

    private static final Layout PARADOX_LAYOUT = new Layout(
            660, 416, 10, 126,
            8, 79, 110, 24, 29,
            248, 80, 394, 40, 44,
            26, 14, 18, 18, 20,
            110, 100, 10, 13
    );
    private static final Layout PARADOX_MODULE_LAYOUT = new Layout(
            660, 416, 8, 124,
            8, 190, 110, 18, 19,
            138, 64, 506, 30, 34,
            18, 11, 16, 10, 14,
            82, 112, 10, 13
    );

    private static final String[] COLOR_PRESETS = {
            "#F59E0B", "#FF7C00", "#EF4444", "#22C55E", "#3B82F6", "#64D9FF", "#A855F7", "#F5F5FF"
    };
    private static final String[] FOG_COLOR_PRESETS = {
            "#14111F", "#1E1F24", "#EF4444", "#22C55E", "#3B82F6", "#64D9FF", "#A855F7", "#F5F5FF"
    };

    // Палитры тем. ORION — из первой Figma (янтарная), PRIZRAK — из второй (тёмная, красно-оранжевая).
    private record Palette(
            boolean classic,
            Color accent, Color accentDark,
            Color bg, Color sidebar, Color surface, Color surfaceHover,
            Color border, Color text, Color muted, Color inset, Color placeholder
    ) {
    }

    private static final Palette ORION_PALETTE = new Palette(
            true,
            new Color(0xF5, 0x9E, 0x0B), new Color(0xD9, 0x77, 0x06),
            new Color(0x1E, 0x1F, 0x24), new Color(0x19, 0x1A, 0x1F),
            new Color(0x23, 0x26, 0x2D), new Color(0x2C, 0x30, 0x38),
            new Color(0x3A, 0x40, 0x49), new Color(0xE5, 0xE7, 0xEB),
            new Color(0x9C, 0xA3, 0xAF), new Color(0x17, 0x18, 0x1D),
            new Color(0x6F, 0x77, 0x82)
    );

    private static final Palette PRIZRAK_PALETTE = new Palette(
            false,
            new Color(0xFF, 0x6A, 0x00), new Color(0xC5, 0x55, 0x07),
            new Color(0x05, 0x05, 0x05), new Color(0x0B, 0x0B, 0x0D),
            new Color(0x17, 0x17, 0x1B), new Color(0x20, 0x20, 0x27),
            new Color(0x2A, 0x2A, 0x31), new Color(0xEC, 0xEC, 0xEE),
            new Color(0x8A, 0x8A, 0x92), new Color(0x15, 0x15, 0x18),
            new Color(0x6A, 0x6A, 0x72)
    );

    // ORION3 — из третьей Figma (Untitled.fig): сплошной чёрный фон, логотип в сайдбаре,
    // крупный поиск сверху, карточки без рамок и контурные тумблеры.
    private static final Palette ORION3_PALETTE = new Palette(
            false,
            new Color(0xFF, 0x6A, 0x00), new Color(0xC5, 0x55, 0x07),
            new Color(0x0B, 0x0B, 0x0D), new Color(0x0B, 0x0B, 0x0D),
            new Color(0x17, 0x17, 0x1B), new Color(0x20, 0x20, 0x27),
            new Color(0x2A, 0x2A, 0x31), new Color(0xEC, 0xEC, 0xEE),
            new Color(0x8A, 0x8A, 0x92), new Color(0x15, 0x15, 0x18),
            new Color(0x6A, 0x6A, 0x72)
    );

    private static final Palette PARADOX_PALETTE = new Palette(
            false, new Color(0xF0, 0x00, 0xD7), new Color(0xBA, 0x00, 0xAD),
            new Color(0x1E, 0x1E, 0x1C), new Color(0x16, 0x16, 0x15),
            new Color(0x24, 0x24, 0x22), new Color(0x2A, 0x2A, 0x28),
            new Color(0x35, 0x35, 0x35), new Color(0xEB, 0xEB, 0xEB),
            new Color(0xB8, 0xB8, 0xB6), new Color(0x26, 0x26, 0x26),
            new Color(0xA3, 0xA3, 0xA0));

    private static final Palette PARADOX_LIGHT_PALETTE = new Palette(
            false, new Color(0x34, 0x7F, 0xF5), new Color(0x2B, 0x6D, 0xDB),
            new Color(0xFA, 0xFA, 0xF8), new Color(0xF1, 0xF1, 0xEF),
            Color.WHITE, new Color(0xF5, 0xF7, 0xFC),
            new Color(0xE2, 0xE2, 0xDF), new Color(0x1B, 0x1B, 0x19),
            new Color(0x79, 0x79, 0x77), new Color(0xEE, 0xEE, 0xEC),
            new Color(0x9A, 0x9A, 0x97));
    private static final String[] THEME_OPTIONS = {"Dark", "Light"};
    private static final int[] THEME_IDS = {3, 4};
    private static final int GRID_COLUMNS = 3, GRID_GAP = 6;
    private static final int GRID_HEIGHT = 72, GRID_STEP = 78;


    private final OrionConfigManager configManager;
    private int selectedCategory;
    private boolean paradoxAll;
    private boolean paradoxFavoritesOnly;
    private boolean waypointPage;
    private boolean presetsPage;
    private boolean configsPage, configsLocal, configTitleFocused, configBusy, catalogMore;
    private int configOffset;
    private boolean configsPublished, configDeleteConfirm;
    private int configSelected = -1, configEditor, catalogTotal;
    private static final int ACCOUNT_X = 8, ACCOUNT_W = 184, ACCOUNT_PAD = 9;
    private static final int ACCOUNT_FOOTER_H = 26, ACCOUNT_ROW_H = 32, ACCOUNT_VISIBLE_ROWS = 4;
    private static final int ACCOUNT_FORM_H = 177, ACCOUNT_LIST_Y = 30;
    private static final int ACCOUNT_BUTTON_H = 23, ACCOUNT_LOGOUT_W = 55;
    private static final URI ACCOUNT_PROFILE_PAGE = URI.create("https://orionclient.51-250-32-198.sslip.io/auth.html");
    private final OrionAccounts accounts = OrionAccounts.INSTANCE;
    private boolean accountsOpen, accountForm;
    private boolean accountRefreshing;
    private int accountField, accountOffset;
    private int footballTeamSize;
    private int basketballTeamSize;
    private String basketballInviteCode = "";
    private String footballInviteCode = "";
    private String accountLogin = "", accountPassword = "", accountStatus = "";
    private int pagePointerX = -1, pagePointerY = -1, pageHover = -1;
    private String configTitle = "", configStatus = "";
    private List<OrionConfigLibrary.Entry> catalogEntries = List.of();
    private static final long CATALOG_REFRESH_NANOS = java.time.Duration.ofSeconds(30).toNanos();
    private record CatalogSnapshot(int offset, OrionConfigLibrary.Page page, long fetchedAt) { }
    private final CatalogSnapshot[] catalogCache = new CatalogSnapshot[2];
    private final boolean[] catalogPending = new boolean[2];
    private final boolean[] catalogStale = new boolean[2];
    private boolean localConfigsPending;
    private List<OrionConfigLibrary.Profile> localConfigs = List.of();
    private int selectedPreset;
    private static final int PRESETS_NAV_Y = 132;
    private static final int CONFIGS_NAV_Y = 154;
    private static final int PARADOX_NAV_ROW_HEIGHT = 18;
    private static final int PARADOX_NAV_DIVIDER_Y = CONFIGS_NAV_Y + PARADOX_NAV_ROW_HEIGHT + 6;
    private static final int PARADOX_CATEGORY_START_Y = PARADOX_NAV_DIVIDER_Y + 7;
    private int waypointListScroll;
    private static final int WAYPOINT_NAV_Y = 110, WAYPOINT_LIST_Y = 137, WAYPOINT_LIST_STEP = 29;
    private final java.util.Set<Action> paradoxFavorites = java.util.EnumSet.noneOf(Action.class);
    private int hoveredCategory = -1;
    private int hoveredModule = -1;
    private int settingsScroll;
    private int moduleScroll;
    private boolean draggingSettingsScroll;
    private boolean draggingModuleScroll;
    private ModuleRow settingsFor;
    private Setting[] settingsRows = new Setting[0];
    private final Setting[] globalSettings;
    private int draggingSlider = -1;
    private int editingText = -1;
    private int editingBind = -1;
    private String hitSoundFolderStatus = "Папка";
    private String waypointName = "Метка";
    private String waypointX = "0", waypointY = "64", waypointZ = "0";
    private String waypointColor = "#FF9B38";
    private String waypointStatus = "Укажите название";
    private int waypointSelected;
    private long bindAcceptedAt;
    private int textCaret;
    private int textAnchor;
    private float textScrollX;
    // Раскрытая палитра цвета: индекс ColorSetting и текущее HSV-состояние пикера.
    private int expandedColor = -1;
    private float pickerHue;
    private float pickerSat;
    private float pickerVal;
    private boolean draggingSV;
    private boolean draggingHue;
    private String searchQuery = "";
    private boolean searchFocused;
    private boolean orion3;
    private boolean paradox;
    private Layout L = CLASSIC_LAYOUT;
    private float sc = 1.0F; // множитель внутренних отступов/шрифтов карточек (ORION3 мельче)
    private Palette pal = ORION_PALETTE;
    private Color PRIMARY = ORION_PALETTE.accent;
    private Color PRIMARY_DARK = ORION_PALETTE.accentDark;
    private Color BG = ORION_PALETTE.bg;
    private Color SIDEBAR_BG = ORION_PALETTE.sidebar;
    private Color SURFACE = ORION_PALETTE.surface;
    private Color SURFACE_HOVER = ORION_PALETTE.surfaceHover;
    private Color BORDER = ORION_PALETTE.border;
    private Color TEXT = ORION_PALETTE.text;
    private Color MUTED = ORION_PALETTE.muted;
    private Color SEARCH_BG = ORION_PALETTE.inset;
    private Color PLACEHOLDER = ORION_PALETTE.placeholder;
    private static final Identifier SETTINGS_TEXTURE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "click_gui_settings");
    private static final Identifier SCROLL_THUMB_TEXTURE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "click_gui_scroll_thumb");
    private DynamicTexture settingsTexture, scrollThumbTexture;
    private BufferedImage settingsCanvas;
    private int[] settingsUploadPixels;
    private List<Integer> lastSettingsFingerprint;
    private List<Integer> lastThumbFingerprint;
    private int settingsAtlasStart, settingsAtlasHeight, settingsAtlasGuiWidth, settingsAtlasGuiHeight;
    private int thumbGuiWidth, thumbGuiHeight;
    private boolean drawingSettingsLayer;
    private DynamicTexture dynamicTexture;
    private BufferedImage canvas;
    private int[] uploadPixels;
    private long lastTextureUpdateNanos;
    private int textureWidth;
    private int textureHeight;
    private List<Integer> lastFingerprint;
    private long lastFrameNanos;
    private final float[] toggleAnim = new float[Action.VALUES.length];
    private final float[] categoryHoverAnim = new float[Category.VALUES.length];
    private final float[] moduleHoverAnim = new float[24];
    private long pageStartedAt;
    private List<Object> pageIdentity;
    private float pageAnimation = 1F;
    private float cardsAnimation = 1F;
    private float scrollAnimation;
    private float[] settingToggleAnimation = new float[0];

    /** Подготавливает Java2D и шрифты вне игрового потока до первого открытия меню. */
    public static void prepareInBackground() {
        Thread worker = new Thread(() -> {
            BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                setupGraphics(graphics);
                for (Font font : new Font[]{OrionInterfaceFonts.face(0, 8.5F), OrionInterfaceFonts.face(2, 10F)}) {
                    graphics.setFont(font);
                    graphics.drawString("Orion Настройки ё", 0.0F, 16.0F);
                    textWidth(graphics, font, "Orion Настройки ё");
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("Не удалось заранее подготовить интерфейс Orion", exception);
            } finally {
                graphics.dispose();
            }
        }, "Orion GUI preparation");
        worker.setDaemon(true);
        worker.start();
    }

    public OrionClickGuiScreen(OrionConfigManager configManager) {
        super(TITLE);
        this.configManager = configManager;
        selectedPreset = configManager.getVisualPresets().selectedProfile().ordinal();
        applyPalette();
        paradoxAll = true;
        OrionConfig config = configManager.getConfig();
        for (String favorite : config.getFavoriteModules()) {
            try { paradoxFavorites.add(Action.valueOf(favorite)); } catch (IllegalArgumentException ignored) { }
        }
        globalSettings = new Setting[]{
                new ChoiceSetting("Тема", THEME_OPTIONS, () -> themeOption(configManager.getConfig().getClickGuiTheme()), value -> configManager.setClickGuiTheme(THEME_IDS[value]))
        };
        selectedCategory = 0;
        for (Action action : Action.VALUES) {
            toggleAnim[action.ordinal()] = isActive(action) ? 1.0F : 0.0F;
        }
        restoreLastSection(config.getClickGuiLastSection());
    }

    /** Opens the ClickGUI already focused on the MediaPlayer module settings (used by the HUD menu chip). */
    public static OrionClickGuiScreen withMediaPlayerSettings(OrionConfigManager configManager) {
        OrionClickGuiScreen screen = new OrionClickGuiScreen(configManager);
        for (ModuleRow row : ModuleRow.MISC) {
            if (row.action == Action.MEDIA_PLAYER) {
                screen.closeSettings();
                screen.presetsPage = screen.paradoxFavoritesOnly = false;
                screen.selectedCategory = Category.MISC.ordinal();
                screen.paradoxAll = false;
                screen.openSettings(row);
                break;
            }
        }
        return screen;
    }

    public static OrionClickGuiScreen withWaypointSettings(OrionConfigManager manager, OrionConfig.Waypoint point) {
        OrionClickGuiScreen screen = new OrionClickGuiScreen(manager);
        screen.openWaypointPage();
        int index = screen.currentWaypoints().indexOf(point);
        if (index >= 0) {
            screen.selectWaypoint(index + 1);
            screen.waypointListScroll = Math.max(0, index + 1 - screen.waypointListVisible());
            screen.focusWaypointEditor();
        }
        return screen;
    }

    private void restoreLastSection(String section) {
        paradoxAll = "ALL".equals(section);
        paradoxFavoritesOnly = "FAVORITES".equals(section);
        if ("WAYPOINTS".equals(section)) { openWaypointPage(); return; }
        if ("PRESETS".equals(section)) { openPresetsPage(); return; }
        if ("CONFIGS".equals(section)) { openConfigsPage(); return; }
        if (paradoxAll || paradoxFavoritesOnly) return;
        try { selectedCategory = Category.valueOf(section).ordinal(); }
        catch (IllegalArgumentException exception) { paradoxAll = true; selectedCategory = 0; }
    }

    private String selectedSection() {
        return waypointPage ? "WAYPOINTS" : presetsPage ? "PRESETS" : configsPage ? "CONFIGS" : paradoxAll ? "ALL"
                : paradoxFavoritesOnly ? "FAVORITES" : Category.VALUES[selectedCategory].name();
    }

    private void openPresetsPage() {
        closeSettings(); selectedPreset = configManager.getVisualPresets().selectedProfile().ordinal(); presetsPage = true; paradoxAll = paradoxFavoritesOnly = false;
        selectedCategory = 0; moduleScroll = 0; searchQuery = ""; lastFingerprint = null;
    }

    private void openWaypointPage() {
        closeSettings();
        for (ModuleRow row : ModuleRow.ALL) if (row.action == Action.WAYPOINTS) { openSettings(row); break; }
        waypointPage = true; paradoxAll = paradoxFavoritesOnly = false;
        waypointListScroll = 0; searchQuery = "";
        refreshWaypointSettings();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void added() {
        super.added();
        Minecraft.getInstance().onTextInputFocusChange(this, true);
    }

    @Override
    public void removed() {
        closeAccounts();
        Minecraft.getInstance().onTextInputFocusChange(this, false);
        configManager.getConfig().setClickGuiLastSection(selectedSection());
        configManager.save();
        Minecraft.getInstance().getTextureManager().release(GUI_TEXTURE);
        OrionGlassCompositor.INSTANCE.releaseMask(GUI_TEXTURE);
        releaseSettingsTextures();
        dynamicTexture = null;
        canvas = null;
        uploadPixels = null;
        lastTextureUpdateNanos = 0L;
        textureWidth = 0;
        textureHeight = 0;
        lastFingerprint = null;
        super.removed();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, width, height, 0x66000000);
        minecraft.gui.hud.extractDeferredSubtitles();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);

        applyPalette();
        updateAnimations();
        updateHover(toLocalX(mouseX), toLocalY(mouseY));
        updateSettingsTexture();
        updateTextureIfNeeded();

        int x = guiX();
        int y = guiY();
        if(dynamicTexture!=null && !OrionGlassCompositor.INSTANCE.submit(GUI_TEXTURE,context,dynamicTexture,x,y,guiDisplayWidth(),guiDisplayHeight(),true))
            context.blit(GUI_TEXTURE, x, y, x + guiDisplayWidth(), y + guiDisplayHeight(), 0.0F, 1.0F, 0.0F, 1.0F);
        drawCachedSettings(context);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int localX = toLocalX(event.x());
        int localY = toLocalY(event.y());
        if (accountsOpen) {
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) accountClick(localX, localY);
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && accountFooterHit(localX, localY)) {
            accountsOpen = true;
            accountForm = accounts.usernames().isEmpty();
            accountOffset = 0;
            accountStatus = "";
            accountLogin = "";
            searchFocused = configTitleFocused = false;
            editingText = editingBind = -1;
            lastFingerprint = null;
            if (accountForm && !accounts.busy()) focusAccountField(1);
            refreshAccountInBackground();
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            searchFocused = false;
            configTitleFocused = false;
            if (settingsFor == null && !presetsPage && !configsPage) {
                int index = moduleAt(localX, localY);
                ModuleRow[] rows = rowsForSelectedCategory();
                if (index >= 0 && index < rows.length && hasSettings(rows[index].action)) { openSettings(rows[index]); return true; }
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        if (isInside(localX, localY, searchX(), searchY(), searchW(), searchH()) && (paradox || settingsFor == null)) {
            closeSettings(); editingText = -1; searchFocused = true;
            textCaret = textAnchor = searchQuery.length(); textScrollX = 0; lastFingerprint = null; return true;
        }
        searchFocused = false;
        configTitleFocused = false;

        if (paradox) {
            if (isInside(localX, localY, 8, CONFIGS_NAV_Y, 110, PARADOX_NAV_ROW_HEIGHT)) { openConfigsPage(); return true; }
            if (isInside(localX, localY, 8, PRESETS_NAV_Y, 110, 18)) { openPresetsPage(); return true; }
            if (isInside(localX, localY, 8, WAYPOINT_NAV_Y, 110, 18)) { openWaypointPage(); return true; }
            if (isInside(localX, localY, L.guiW - 22, 7, 16, 16)) { onClose(); return true; }
            if (isInside(localX, localY, 8, 69, 110, 18) || isInside(localX, localY, 8, 88, 110, 18)) {
                boolean favorites = localY >= 88;
                closeSettings(); paradoxAll = !favorites; paradoxFavoritesOnly = favorites;
                selectedCategory = 0; moduleScroll = 0; searchQuery = ""; lastFingerprint = null; return true;
            }
        }

        if (!presetsPage && !configsPage && scrollBarHit(localX, localY)) {
            if (settingsFor != null && settingsMaxScroll(settingsRows) > 0) {
                draggingSettingsScroll = true;
                applyScrollBar(localY, true);
                return true;
            }
            int maximum = moduleMaxScroll(rowsForSelectedCategory().length);
            if (settingsFor == null && maximum > 0 && (Category.VALUES[selectedCategory] != Category.SETTINGS || !searchQuery.isBlank())) {
                draggingModuleScroll = true;
                applyScrollBar(localY, false);
                return true;
            }
        }

        if (waypointPage && localX >= L.sidebarW + 10 && localX < L.moduleX - 8) {
            if (isInside(localX, localY, L.sidebarW + 12, 104, L.moduleX - L.sidebarW - 24, 24)) {
                selectWaypoint(0); waypointStatus = "Новая метка"; focusWaypointEditor(); return true;
            }
            List<OrionConfig.Waypoint> points = currentWaypoints();
            int row = (localY - WAYPOINT_LIST_Y) / WAYPOINT_LIST_STEP;
            if (localY >= WAYPOINT_LIST_Y && row < waypointListVisible() && waypointListScroll + row < points.size()) {
                selectWaypoint(waypointListScroll + row + 1); focusWaypointEditor(); return true;
            }
        }
        int category = categoryAt(localX, localY);
        if (category >= 0) {
            paradoxAll = false;
            paradoxFavoritesOnly = false;
            selectedCategory = category;
            searchQuery = "";
            moduleScroll = 0;
            closeSettings();
            Arrays.fill(moduleHoverAnim, 0.0F);
            lastFingerprint = null;
            return true;
        }

        if (presetsPage) return presetsClick(localX, localY);
        if (configsPage) return configsClick(localX, localY);

        if (settingsFor != null) {
            if (paradox && isInside(localX, localY, L.guiW - 110, 43, 92, 24)) {
                toggle(settingsFor.action);
                lastFingerprint = null;
                return true;
            }
            if (settingsClick(localX, localY)) {
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        if (Category.VALUES[selectedCategory] == Category.SETTINGS && searchQuery.isBlank()) {
            if (settingsClick(localX, localY, globalSettings)) {
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        int module = moduleAt(localX, localY);
        ModuleRow[] rows = rowsForSelectedCategory();
        if (module >= 0 && module < rows.length) {
            ModuleRow row = rows[module];
            if (paradox) {
                int index = module - moduleScroll;
                int cx = gridCardX(index), cy = gridCardY(index);
                if (isInside(localX, localY, cx + gridCardWidth() - 49, cy + 8, 16, 16)) {
                    if (!paradoxFavorites.add(row.action)) paradoxFavorites.remove(row.action);
                    configManager.getConfig().setFavoriteModules(paradoxFavorites.stream().map(Enum::name).toList());
                    configManager.save(); lastFingerprint = null;
                } else {
                    toggle(row.action);
                }
                return true;
            }
            toggle(row.action);
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (accountsOpen) return true;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseDragged(event, dragX, dragY);
        }
        if (draggingSettingsScroll || draggingModuleScroll) {
            applyScrollBar(toLocalY(event.y()), draggingSettingsScroll);
            return true;
        }
        Setting[] rows = editableRows();
        if ((draggingSV || draggingHue) && expandedColor >= 0 && expandedColor < rows.length
                && rows[expandedColor] instanceof ColorSetting color) {
            if (draggingSV) {
                applySvPick(color, toLocalX(event.x()), toLocalY(event.y()));
            } else {
                applyHuePick(color, toLocalY(event.y()));
            }
            return true;
        }
        if (draggingSlider >= 0 && draggingSlider < settingsRows.length
                && settingsRows[draggingSlider] instanceof SliderSetting slider) {
            applySlider(slider, toLocalX(event.x()));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (accountsOpen) {
            if (!accountForm && vertical != 0 && accountPanelHit(toLocalX(mouseX), toLocalY(mouseY))) {
                accountOffset = Math.clamp(accountOffset + (vertical > 0 ? -1 : 1), 0,
                        Math.max(0, accounts.usernames().size() - ACCOUNT_VISIBLE_ROWS));
            }
            return true;
        }
        if (presetsPage || configsPage) return true;
        TextSetting activeText = editingTextSetting();
        int localX = toLocalX(mouseX), localY = toLocalY(mouseY);
        if (activeText != null && isInside(localX, localY, textBoxX(), settingRowY(editingText) + arrowOffsetY(), textBoxW(), L.arrowSize)) {
            String value = activeText.get.get();
            int steps = (int) Math.signum(vertical) * 5;
            while (steps > 0 && textCaret > 0) { textCaret = value.offsetByCodePoints(textCaret, -1); steps--; }
            while (steps < 0 && textCaret < value.length()) { textCaret = value.offsetByCodePoints(textCaret, 1); steps++; }
            textAnchor = textCaret;
            lastFingerprint = null;
            return true;
        }
        if (waypointPage && localX >= L.sidebarW && localX < L.moduleX && localY >= WAYPOINT_LIST_Y) {
            waypointListScroll = Math.max(0, Math.min(Math.max(0, currentWaypoints().size() - waypointListVisible()),
                    waypointListScroll - (int) Math.signum(vertical)));
            lastFingerprint = null; return true;
        }
        if (settingsFor != null && localX >= L.moduleX && localY >= L.moduleY) {
            int maxScroll = settingsMaxScroll(settingsRows);
            settingsScroll = Math.max(0, Math.min(maxScroll, settingsScroll - (int) Math.signum(vertical) * L.moduleStep));
            return true;
        }
        if (settingsFor == null && (Category.VALUES[selectedCategory] != Category.SETTINGS || !searchQuery.isBlank())
                && toLocalX(mouseX) >= L.moduleX && toLocalX(mouseX) < L.moduleX + L.moduleW
                && toLocalY(mouseY) >= L.moduleY && toLocalY(mouseY) < L.guiH - 8) {
            int maxScroll = moduleMaxScroll(rowsForSelectedCategory().length);
            moduleScroll = Math.max(0, Math.min(maxScroll, moduleScroll - (int) Math.signum(vertical) * (paradox ? GRID_COLUMNS : 1)));
            Arrays.fill(moduleHoverAnim, 0.0F);
            lastFingerprint = null;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (accountsOpen) return true;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseReleased(event);
        }
        if (draggingSettingsScroll || draggingModuleScroll) {
            draggingSettingsScroll = false;
            draggingModuleScroll = false;
            return true;
        }
        if (draggingSlider >= 0 || draggingSV || draggingHue) {
            draggingSlider = -1;
            draggingSV = false;
            draggingHue = false;
            configManager.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    /** Строки настроек, доступные для взаимодействия прямо сейчас. */
    private Setting[] editableRows() {
        if (settingsFor != null) {
            return settingsRows;
        }
        if (searchQuery.isBlank() && Category.VALUES[selectedCategory] == Category.SETTINGS) {
            return globalSettings;
        }
        return new Setting[0];
    }

    private TextSetting editingTextSetting() {
        if (accountsOpen && accountForm) {
            return switch (accountField) {
                case 1 -> new TextSetting("Логин", () -> accountLogin, value -> accountLogin = value, 24);
                case 2 -> new TextSetting("Пароль", () -> accountPassword, value -> accountPassword = value, 256);
                default -> null;
            };
        }
        if (configsPage && configTitleFocused) return new TextSetting("Название конфига", () -> configTitle,
                value -> configTitle = value, 48);
        if (searchFocused) return new TextSetting("Поиск", () -> searchQuery, value -> {
            searchQuery = value; moduleScroll = 0; lastFingerprint = null;
        }, 96);
        Setting[] rows = editableRows();
        if (editingText >= 0 && editingText < rows.length && rows[editingText] instanceof TextSetting text) {
            return text;
        }
        return null;
    }

    private void replaceTextSelection(TextSetting setting, String inserted) {
        String current = setting.get.get();
        int start = Math.min(textCaret, textAnchor);
        int end = Math.max(textCaret, textAnchor);
        int available = Math.max(0, setting.maxLength() - (current.length() - (end - start)));
        int length = Math.min(available, inserted.length());
        if (length > 0 && length < inserted.length() && Character.isHighSurrogate(inserted.charAt(length - 1))
                && Character.isLowSurrogate(inserted.charAt(length))) length--;
        String replacement = inserted.substring(0, length);
        setting.set.accept(current.substring(0, start) + replacement + current.substring(end));
        textCaret = start + length;
        textAnchor = textCaret;
        lastFingerprint = null;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (bindAcceptedAt != 0 && System.nanoTime() - bindAcceptedAt < 150_000_000L) { bindAcceptedAt = 0; return true; }
        bindAcceptedAt = 0;
        TextSetting editing = editingTextSetting();
        if (editing != null) {
            if (event.isAllowedChatCharacter()) replaceTextSelection(editing, event.codepointAsString());
            return true;
        }
        return accountsOpen || super.charTyped(event);
    }

    private static boolean editShortcut(KeyEvent event, int key) {
        return event.hasControlDownWithQuirk() && !event.hasAltDown() && event.key() == key;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (accountsOpen) {
            if (event.key() == InputConstants.KEY_ESCAPE) { closeAccounts(); return true; }
            if (accounts.busy() || !accountForm) return true;
            if (event.key() == InputConstants.KEY_TAB) { focusAccountField(accountField == 1 ? 2 : 1); return true; }
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                if (accountField == 1) focusAccountField(2);
                else loginAccount();
                return true;
            }
            if (accountField == 0) return true;
        }
        Setting[] bindRows = editableRows();
        if (editingBind >= 0 && editingBind < bindRows.length && bindRows[editingBind] instanceof KeybindSetting bind) {
            KeyMapping key = bind.get.get();
            if (key != null && event.key() != InputConstants.KEY_ESCAPE) {
                key.setKey(event.key() == InputConstants.KEY_DELETE || event.key() == InputConstants.KEY_BACKSPACE
                        ? InputConstants.UNKNOWN : InputConstants.getKey(event));
                KeyMapping.resetMapping();
                minecraft.options.save();
                bindAcceptedAt = System.nanoTime();
            }
            editingBind = -1;
            lastFingerprint = null;
            return true;
        }
        TextSetting editing = editingTextSetting();
        if (editing != null) {
            String value = editing.get.get();
            textCaret = Math.max(0, Math.min(value.length(), textCaret));
            textAnchor = Math.max(0, Math.min(value.length(), textAnchor));
            if ((event.isSelectAll() || editShortcut(event, InputConstants.KEY_A))) {
                textAnchor = 0;
                textCaret = value.length();
            } else if ((event.isCopy() || editShortcut(event, InputConstants.KEY_C)) || (event.isCut() || editShortcut(event, InputConstants.KEY_X))) {
                int start = Math.min(textCaret, textAnchor), end = Math.max(textCaret, textAnchor);
                if (start != end && !(accountsOpen && accountField == 2)) {
                    minecraft.keyboardHandler.setClipboard(value.substring(start, end));
                    if ((event.isCut() || editShortcut(event, InputConstants.KEY_X))) replaceTextSelection(editing, "");
                }
            } else if ((event.isPaste() || editShortcut(event, InputConstants.KEY_V))) {
                String clipboard = minecraft.keyboardHandler.getClipboard().codePoints()
                        .filter(codepoint -> codepoint >= 32 && codepoint != 127)
                        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                        .toString();
                replaceTextSelection(editing, clipboard);
            } else if (event.key() == InputConstants.KEY_LEFT || event.key() == InputConstants.KEY_RIGHT
                    || event.key() == InputConstants.KEY_HOME || event.key() == InputConstants.KEY_END) {
                if (!event.hasShiftDown() && textCaret != textAnchor && event.key() == InputConstants.KEY_LEFT) {
                    textCaret = Math.min(textCaret, textAnchor);
                } else if (!event.hasShiftDown() && textCaret != textAnchor && event.key() == InputConstants.KEY_RIGHT) {
                    textCaret = Math.max(textCaret, textAnchor);
                } else if (event.key() == InputConstants.KEY_HOME) textCaret = 0;
                else if (event.key() == InputConstants.KEY_END) textCaret = value.length();
                else if (event.key() == InputConstants.KEY_LEFT && textCaret > 0) textCaret = value.offsetByCodePoints(textCaret, -1);
                else if (event.key() == InputConstants.KEY_RIGHT && textCaret < value.length()) textCaret = value.offsetByCodePoints(textCaret, 1);
                if (!event.hasShiftDown()) textAnchor = textCaret;
            } else if (event.key() == InputConstants.KEY_BACKSPACE || event.key() == InputConstants.KEY_DELETE) {
                if (textCaret == textAnchor) {
                    if (event.key() == InputConstants.KEY_BACKSPACE && textCaret > 0) textAnchor = value.offsetByCodePoints(textCaret, -1);
                    if (event.key() == InputConstants.KEY_DELETE && textCaret < value.length()) textAnchor = value.offsetByCodePoints(textCaret, 1);
                }
                if (textCaret != textAnchor) replaceTextSelection(editing, "");
            } else if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER
                    || event.key() == InputConstants.KEY_ESCAPE) {
                editingText = -1;
                searchFocused = false;
                configTitleFocused = false;
                configManager.save();
            }
            lastFingerprint = null;
            return true;
        }
        return super.keyPressed(event);
    }

    private void openSettings(ModuleRow row) {
        if (paradox) {
            ModuleRow[][] categories = {ModuleRow.COMBAT, ModuleRow.PLAYER, ModuleRow.MOVEMENT,
                    ModuleRow.RENDER, ModuleRow.WORLD, ModuleRow.MISC, ModuleRow.SETTINGS};
            for (int i = 0; i < categories.length; i++) {
                if (Arrays.asList(categories[i]).contains(row)) selectedCategory = i;
            }
        }
        settingsScroll = 0;
        settingsFor = row;
        if (row.action == Action.WAYPOINTS) {
            waypointSelected = 0;
            waypointName = newWaypointName();
            waypointColor = configManager.getConfig().getWaypointQuickColor();
            waypointStatus = "Укажите название";
            waypointCurrentPosition();
        }
        settingsRows = buildSettings(row.action);
        draggingSlider = -1;
        editingBind = -1;
        expandedColor = -1;
        draggingSV = false;
        draggingHue = false;
        lastFingerprint = null;
    }

    private void closeSettings() {
        waypointPage = presetsPage = configsPage = configTitleFocused = false;
        settingsScroll = 0;
        draggingSettingsScroll = false;
        draggingModuleScroll = false;
        expandedColor = -1;
        draggingSV = false;
        draggingHue = false;
        if (settingsFor != null) {
            settingsFor = null;
            settingsRows = new Setting[0];
            draggingSlider = -1;
            editingText = -1;
            editingBind = -1;
            lastFingerprint = null;
        }
    }

    private boolean settingsClick(int localX, int localY) {
        if (isInside(localX, localY, paradox ? L.sidebarW + 12 : L.moduleX, L.backY, L.gearSize, L.gearSize)) {
            boolean wasWaypointPage = waypointPage;
            closeSettings();
            if (wasWaypointPage) paradoxAll = true;
            return true;
        }

        if (paradox && !waypointPage && localX >= L.sidebarW + 10 && localX < L.moduleX - 8) {
            int[] starts = paradoxGroupStarts(settingsRows);
            for (int group = 0; group < starts.length; group++) {
                if (isInside(localX, localY, L.sidebarW + 10, 99 + group * 18, L.moduleX - L.sidebarW - 20, 18)) {
                    settingsScroll = Math.max(0, Math.min(settingsMaxScroll(settingsRows),
                            settingRowY(starts[group]) + currentSettingsScroll() - L.moduleY - 20));
                    lastFingerprint = null;
                    return true;
                }
            }
        }

        return settingsClick(localX, localY, settingsRows);
    }

    private boolean settingsClick(int localX, int localY, Setting[] rows) {
        if (localY < L.moduleY || localY >= L.guiH - 8) return false;
        int previousEditingText = editingText;
        editingText = -1;
        editingBind = -1;

        if (expandedColor >= 0 && expandedColor < rows.length
                && rows[expandedColor] instanceof ColorSetting expanded
                && pickerClick(expanded, localX, localY, expandedPanelY())) {
            return true;
        }

        for (int i = 0; i < rows.length; i++) {
            int y = settingRowY(i);
            Setting setting = rows[i];
            if (setting instanceof ChoiceSetting choice) {
                if (isInside(localX, localY, choiceLeftArrowX(), y + arrowOffsetY(), L.arrowSize, L.arrowSize)) {
                    cycleChoice(choice, -1);
                    return true;
                }
                if (isInside(localX, localY, choiceRightArrowX(), y + arrowOffsetY(), L.arrowSize, L.arrowSize)) {
                    cycleChoice(choice, 1);
                    return true;
                }
            } else if (setting instanceof SliderSetting slider) {
                if (isInside(localX, localY, sliderTrackX() - 4, y + L.moduleH / 2 - 8, L.sliderTrackW + 8, 16)) {
                    draggingSlider = i;
                    applySlider(slider, localX);
                    return true;
                }
            } else if (setting instanceof ColorSetting color) {
                if (isInside(localX, localY, L.moduleX, y, L.moduleW, L.moduleH)) {
                    toggleColorExpand(i, color);
                    return true;
                }
            } else if (setting instanceof ToggleSetting toggle) {
                if (isInside(localX, localY, L.moduleX, y, L.moduleW, L.moduleH)) {
                    toggle.set.accept(!toggle.get.get());
                    lastFingerprint = null;
                    return true;
                }
            } else if (setting instanceof KeybindSetting) {
                if (isInside(localX, localY, textBoxX(), y + arrowOffsetY(), textBoxW(), L.arrowSize)) {
                    editingBind = i;
                    lastFingerprint = null;
                    return true;
                }
            } else if (setting instanceof TextSetting text) {
                if (isInside(localX, localY, textBoxX(), y + arrowOffsetY(), textBoxW(), L.arrowSize)) {
                    if (previousEditingText != i) textScrollX = 0.0F;
                    editingText = i;
                    textCaret = textIndexAt(text.get.get(), localX - textBoxX() - 6 * sc + textScrollX);
                    textAnchor = textCaret;
                    lastFingerprint = null;
                    return true;
                }
            } else if (setting instanceof ButtonSetting button) {
                if (isInside(localX, localY, textBoxX(), y + arrowOffsetY(), textBoxW(), L.arrowSize)) {
                    button.onClick.run();
                    lastFingerprint = null;
                    return true;
                }
            }
        }
        return false;
    }

    private int textIndexAt(String value, float x) {
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = probe.createGraphics();
        try {
            setupGraphics(g);
            Font font = bodyFont(8.5F * sc);
            int previous = 0;
            for (int next = 0; next < value.length();) {
                next = value.offsetByCodePoints(next, 1);
                float left = textWidth(g, font, value.substring(0, previous));
                float right = textWidth(g, font, value.substring(0, next));
                if (x < (left + right) * 0.5F) return previous;
                previous = next;
            }
            return value.length();
        } finally {
            g.dispose();
        }
    }

    private void cycleChoice(ChoiceSetting choice, int direction) {
        int count = choice.options.length;
        int next = ((choice.get.getAsInt() + direction) % count + count) % count;
        choice.set.accept(next);
        lastFingerprint = null;
    }

    private static int timePreset(int ticks) {
        return switch(ticks) { case 0 -> 0; case 6000 -> 1; case 12000 -> 2; case 15000 -> 3; case 18000 -> 4; default -> 5; };
    }

    private void applySlider(SliderSetting slider, float localX) {
        float t = (localX - sliderTrackX()) / L.sliderTrackW;
        t = Math.max(0.0F, Math.min(1.0F, t));
        float value = slider.min + (slider.max - slider.min) * t;
        slider.set.accept(slider.integer ? Math.round(value) : value);
        lastFingerprint = null;
    }

    private void toggleColorExpand(int index, ColorSetting color) {
        if (expandedColor == index) {
            expandedColor = -1;
            settingsScroll = 0;
            configManager.save();
        } else {
            expandedColor = index;
            syncPickerFromHex(color.get.get());
        }
        draggingSV = false;
        draggingHue = false;
        lastFingerprint = null;
    }

    private void syncPickerFromHex(String hex) {
        Color current = parseColor(hex);
        float[] hsb = Color.RGBtoHSB(current.getRed(), current.getGreen(), current.getBlue(), null);
        pickerHue = hsb[0];
        pickerSat = hsb[1];
        pickerVal = hsb[2];
    }

    private boolean pickerClick(ColorSetting color, int localX, int localY, int panelY) {
        PickerLayout p = pickerLayout(panelY);
        if (isInside(localX, localY, p.svX() - 2, p.svY() - 2, p.svS() + 4, p.svS() + 4)) {
            draggingSV = true;
            applySvPick(color, localX, localY);
            return true;
        }
        if (isInside(localX, localY, p.hueX() - 3, p.svY() - 2, p.hueW() + 6, p.svS() + 4)) {
            draggingHue = true;
            applyHuePick(color, localY);
            return true;
        }
        for (int s = 0; s < color.presets.length; s++) {
            int dx = p.rightX() + (s % 4) * p.dotStep();
            int dy = p.dotsY() + (s / 4) * p.dotStep();
            if (isInside(localX, localY, dx, dy, p.dotSize(), p.dotSize())) {
                color.set.accept(color.presets[s]);
                syncPickerFromHex(color.presets[s]);
                configManager.save();
                lastFingerprint = null;
                return true;
            }
        }
        // Клик по фону панели не должен проваливаться в строки под ней.
        return isInside(localX, localY, L.moduleX, panelY, L.moduleW, pickerPanelHeight());
    }

    private void applySvPick(ColorSetting color, int localX, int localY) {
        PickerLayout p = pickerLayout(expandedPanelY());
        pickerSat = clamp01((localX - p.svX()) / (float) p.svS());
        pickerVal = 1.0F - clamp01((localY - p.svY()) / (float) p.svS());
        commitPicker(color);
    }

    private void applyHuePick(ColorSetting color, int localY) {
        PickerLayout p = pickerLayout(expandedPanelY());
        pickerHue = clamp01((localY - p.svY()) / (float) p.svS());
        commitPicker(color);
    }

    private void commitPicker(ColorSetting color) {
        Color current = Color.getHSBColor(pickerHue, pickerSat, pickerVal);
        color.set.accept(String.format(Locale.ROOT, "#%02X%02X%02X",
                current.getRed(), current.getGreen(), current.getBlue()));
        lastFingerprint = null;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    /** Y строки настроек с учётом раскрытой палитры: строки ниже неё сдвигаются вниз. */
    private int settingRowY(int index) {
        int y = L.moduleY + index * L.moduleStep - currentSettingsScroll();
        if (paradox && settingsFor != null) {
            for (int start : paradoxGroupStarts(settingsRows)) {
                if (start > 0 && start <= index) y += 24;
            }
        }
        if (expandedColor >= 0 && index > expandedColor) {
            y += pickerPanelHeight() + (L.moduleStep - L.moduleH);
        }
        return y;
    }

    private int currentSettingsScroll() { return drawingSettingsLayer ? settingsAtlasStart : paradox ? Math.round(scrollAnimation) : settingsScroll; }

    private int expandedPanelY() {
        return settingRowY(expandedColor) + L.moduleH + (L.moduleStep - L.moduleH);
    }

    /** Геометрия раскрытой палитры: SV-квадрат, полоса тона и правая колонка с превью и пресетами. */
    private record PickerLayout(int svX, int svY, int svS, int hueX, int hueW,
                                int rightX, int previewW, int previewH,
                                int dotSize, int dotStep, int dotsY) {
    }

    private PickerLayout pickerLayout(int panelY) {
        int pad = pickerPad();
        int svS = svSize();
        int svX = L.moduleX + pad;
        int svY = panelY + pad;
        int hueX = svX + svS + Math.round(8 * sc);
        int hueW = Math.round(10 * sc);
        int rightX = hueX + hueW + Math.round(12 * sc);
        int previewW = Math.round(34 * sc);
        int previewH = Math.round(14 * sc);
        int dotSize = Math.round(12 * sc);
        int dotStep = Math.round(16 * sc);
        int dotsY = svY + svS - dotStep - dotSize;
        return new PickerLayout(svX, svY, svS, hueX, hueW, rightX, previewW, previewH, dotSize, dotStep, dotsY);
    }

    private int pickerPad() {
        return Math.round(10 * sc);
    }

    private int svSize() {
        return Math.round(66 * sc);
    }

    private int pickerPanelHeight() {
        return svSize() + 2 * pickerPad();
    }

    private boolean hasSettings(Action action) {
        return switch (action) {
            case RAGDOLL, FRESH_ANIMATIONS -> true;
            case SKIN_LAYERS, ITEM_SCROLLER, EMOTES, CHAT_ANIMATION, MIRAGE, KILL_EFFECTS, WINGS, WAYPOINTS, ZOOM, CHAT_FILTER, CHAT_HIGHLIGHT, FOOTBALL, BASKETBALL, LOCK_SLOT, ANIMATIONS, ENCHANT_GLINT, PVP_SAFE, CUSTOM_TIME, ITEM_SCALING, SMALL_SHIELD, VIEW_MODEL, TARGET_ESP, JUMP_CIRCLE, BREADCRUMBS, PROJECTILE_TRAIL, DAMAGE_INDICATORS, MOTION_BLUR, BLOCK_HIGHLIGHT, HIT_PARTICLES, NO_PARTICLES, ASPECT, HUD, WORLD_PARTICLES, WORLD_CHANGER, MEDIA_PLAYER, PROTECT_NAME,
                 FAKE_PREFIX,
                 HIT_BOX, HIT_COLOR, HIT_SOUND, CROSSHAIR, BETTER_NEAR, RAINBOW -> true;
            default -> false;
        };
    }

    private ChoiceSetting hudStyleSetting(String label, String id) {
        return new ChoiceSetting(label, new String[]{"Обычный", "Glass"},
                () -> configManager.getConfig().isHudElementGlass(id) ? 1 : 0,
                value -> configManager.setHudElementGlass(id, value == 1));
    }

    private void resetBasketballSettings() {
        OrionConfig defaults = OrionConfig.createDefault();
        OrionConfig config = configManager.getConfig();
        config.setBasketballThrowPower(defaults.getBasketballThrowPower());
        config.setBasketballChargeSpeed(defaults.getBasketballChargeSpeed());
        config.setBasketballBounce(defaults.getBasketballBounce());
        config.setBasketballGravity(defaults.getBasketballGravity());
        config.setBasketballDribbleSpeed(defaults.getBasketballDribbleSpeed());
        config.setBasketballBallScale(defaults.getBasketballBallScale());
        config.setBasketballVolume(defaults.getBasketballVolume());
        config.setBasketballHud(defaults.isBasketballHud());
        config.setBasketballPassPower(defaults.getBasketballPassPower());
        config.setBasketballSpin(defaults.getBasketballSpin());
        config.setBasketballAutoCatch(defaults.isBasketballAutoCatch());
        config.setBasketballAutoDribble(defaults.isBasketballAutoDribble());
        config.setBasketballHands(defaults.isBasketballHands());
        config.setBasketballCourtLines(defaults.isBasketballCourtLines());
        config.setBasketballTrajectory(defaults.isBasketballTrajectory());
        configManager.save();
    }

    private Setting[] buildSettings(Action action) {
        OrionConfig config = configManager.getConfig();
        return switch (action) {
            case FRESH_ANIMATIONS -> new Setting[]{
                    new ChoiceSetting("Походка", new String[]{"Размашистая", "Ветряк", "Пингвин", "Робот"},
                            config::getFreshAnimationsStyle, configManager::setFreshAnimationsStyle)
            };
            case RAGDOLL -> new Setting[]{
                    new SliderSetting("Перед погружением", 0F, 10F, config::getRagdollHold, config::setRagdollHold, "с"),
                    new ButtonSetting("Предпросмотр", "Показать", () -> {
                        var effects = OrionClient.getKillEffects();
                        if (effects != null && effects.previewRagdoll()) onClose();
                    })
            };
            case KILL_EFFECTS -> new Setting[]{
                    new ChoiceSetting("Эффект", new String[]{"Призрак", "Надгробие", "Дождь мечей", "Раскол кристалла",
                            "Чёрная дыра", "Ангельские крылья", "Кристальная тюрьма", "Душа дракона"},
                            () -> config.getKillEffectsStyle() == 8 ? 7 : config.getKillEffectsStyle(), value -> configManager.setKillEffectsStyle(value == 7 ? 8 : value)),
                    new ChoiceSetting("Цели", new String[]{"Только игроки", "Все сущности", "Только мобы"}, config::getKillEffectsTargets, configManager::setKillEffectsTargets),
                    new SliderSetting("Высота на игроках", 0F, 2F, config::getKillEffectsPlayerLift, configManager::setKillEffectsPlayerLift, "м"),
                    new SliderSetting("Размер", 0.5F, 2.0F, config::getKillEffectsSize, config::setKillEffectsSize, "×"),
                    new SliderSetting("Скорость анимации", 0.5F, 2.0F, config::getKillEffectsSpeed, config::setKillEffectsSpeed, "×"),
                    new SliderSetting("Частицы", 0.0F, 1.0F, config::getKillEffectsParticles, config::setKillEffectsParticles, ""),
                    new SliderSetting("Дальность", 16F, 128F, () -> (float) config.getKillEffectsDistance(),
                            value -> config.setKillEffectsDistance(Math.round(value)), "м", true),
                    new ChoiceSetting("Качество", new String[]{"Низкое", "Среднее", "Высокое"},
                            config::getKillEffectsQuality, configManager::setKillEffectsQuality),
                    new SliderSetting("Лимит эффектов", 1F, 8F, () -> (float) config.getKillEffectsLimit(),
                            value -> config.setKillEffectsLimit(Math.round(value)), "", true),
                    new SliderSetting("Громкость", 0F, 100F, () -> (float) config.getKillEffectsVolume(),
                            value -> config.setKillEffectsVolume(Math.round(value)), "%", true),
                    new ButtonSetting("Предпросмотр", "Показать", () -> {
                        var effects = OrionClient.getKillEffects();
                        if (effects != null && effects.preview()) onClose();
                    })
            };
            case SKIN_LAYERS -> new Setting[]{
                    new ValueSetting("Обработчик скина", () -> OrionSkinLayers.EXTERNAL ? tr("Установлен внешний 3D Skin Layers") : tr("Встроенный")),
                    new SliderSetting("Толщина слоя", .1F, 1.5F, config::getSkinLayersThickness, config::setSkinLayersThickness, "px"),
                    new SliderSetting("Дальность объёмных слоёв", 4F, 32F, config::getSkinLayersDistance, config::setSkinLayersDistance, "м"),
                    new ToggleSetting("Рука от первого лица", config::isSkinLayersHands, config::setSkinLayersHands)
            };
            case ITEM_SCROLLER -> new Setting[]{
                    new ValueSetting("Обработчик переноса", () -> OrionItemScroller.EXTERNAL ? tr("Установлен внешний Item Scroller") : tr("Встроенный")),
                    new ValueSetting("Колесо вниз / вверх", () -> tr("Отдать / забрать один предмет")),
                    new ToggleSetting("Shift — переносить стек", config::isItemScrollerStacks, config::setItemScrollerStacks),
                    new ToggleSetting("Ctrl — все такие предметы", config::isItemScrollerMatching, config::setItemScrollerMatching),
                    new SliderSetting("Стеков за действие", 1F, 16F, config::getItemScrollerLimit, config::setItemScrollerLimit, "", true),
                    new ToggleSetting("Обратная прокрутка", config::isItemScrollerReverse, config::setItemScrollerReverse)
            };
            case EMOTES -> new Setting[]{
                    new KeybindSetting("Колесо танцев", OrionKeybinds::getEmoteKey),
                    new ValueSetting("Управление танцами", () -> tr("Выбери мышью или клавишами 1–6")),
                    new ButtonSetting("Текущий танец", "Остановить", ru.orion.client.effect.OrionEmotes::stop)
            };
            case WINGS -> new Setting[]{new ChoiceSetting("Вид крыльев", new String[]{"Ангельские", "Драконьи"}, config::getWingsStyle, configManager::setWingsStyle)};
            case WAYPOINTS -> new Setting[]{
                    new ToggleSetting("Указатели за экраном", config::isWaypointEdgeIndicators, config::setWaypointEdgeIndicators),
                    new ToggleSetting("Координаты меток", config::isWaypointShowCoordinates, config::setWaypointShowCoordinates),
                    new ToggleSetting("Раскрывать при наведении", config::isWaypointSmartLabels, config::setWaypointSmartLabels),
                    new SliderSetting("Меток на экране", 1, 64, () -> (float) config.getWaypointVisibleLimit(),
                            value -> config.setWaypointVisibleLimit(Math.round(value)), "", true),
                    new KeybindSetting("Клавиша быстрой метки", OrionKeybinds::getWaypointKey),
                    new TextSetting("Имя быстрой метки", config::getWaypointQuickPrefix, config::setWaypointQuickPrefix, 24),
                    new ColorSetting("Цвет быстрой метки", COLOR_PRESETS, config::getWaypointQuickColor, config::setWaypointQuickColor),
                    new ToggleSetting("Открывать редактор после создания", config::isWaypointEditAfterCreate, config::setWaypointEditAfterCreate),
                    new ValueSetting("Статус", () -> waypointStatus),
                    new ChoiceSetting("Сохранённые метки", waypointOptions(), () -> waypointSelected, this::selectWaypoint),
                    new TextSetting("Название", () -> waypointName, value -> waypointName = value, 32),
                    new TextSetting("Координата X", () -> waypointX, value -> waypointX = value, 10),
                    new TextSetting("Координата Y", () -> waypointY, value -> waypointY = value, 6),
                    new TextSetting("Координата Z", () -> waypointZ, value -> waypointZ = value, 10),
                    new ColorSetting("Цвет метки", COLOR_PRESETS, () -> waypointColor, value -> waypointColor = value),
                    new ButtonSetting("Ваши координаты", "Взять", this::waypointCurrentPosition),
                    new ButtonSetting("Новая метка", "Добавить", () -> saveWaypoint(false)),
                    new ButtonSetting("Выбранная метка", "Сохранить", () -> saveWaypoint(true)),
                    new ButtonSetting("Удалить метку", "Удалить", this::deleteWaypoint)
            };
            case ZOOM -> new Setting[]{
                    new SliderSetting("Приближение", 1.5F, 12F, config::getZoomFactor, config::setZoomFactor, "x"),
                    new KeybindSetting("Клавиша приближения", OrionKeybinds::getZoomKey),
                    new ToggleSetting("Переключение по нажатию", config::isZoomToggle, config::setZoomToggle),
                    new ChoiceSetting("Переход приближения", OrionZoom.CURVES, config::getZoomInCurve, config::setZoomInCurve),
                    new SliderSetting("Время приближения", 0F, 3F, config::getZoomInTime, config::setZoomInTime, " с"),
                    new ChoiceSetting("Переход отдаления", OrionZoom.CURVES, config::getZoomOutCurve, config::setZoomOutCurve),
                    new SliderSetting("Время отдаления", 0F, 3F, config::getZoomOutTime, config::setZoomOutTime, " с"),
                    new ToggleSetting("Приближение колесом", config::isZoomScroll, config::setZoomScroll),
                    new SliderSetting("Множитель за шаг", 1.05F, 3F, config::getZoomScrollStep, config::setZoomScrollStep, "x"),
                    new SliderSetting("Количество шагов", 1F, 20F, config::getZoomScrollTiers, config::setZoomScrollTiers, ""),
                    new SliderSetting("Плавность колеса", 0F, 1F, config::getZoomScrollSmoothing, config::setZoomScrollSmoothing, ""),
                    new ToggleSetting("Запоминать шаги колеса", config::isZoomRetainScroll, config::setZoomRetainScroll),
                    new SliderSetting("Снижение чувствительности", 0F, 1F, config::getZoomRelativeSensitivity, config::setZoomRelativeSensitivity, ""),
                    new SliderSetting("Кинематографическая камера", 0F, 1F, config::getZoomCinematic, config::setZoomCinematic, ""),
                    new ToggleSetting("Уменьшать покачивание", config::isZoomRelativeBobbing, config::setZoomRelativeBobbing),
                    new ToggleSetting("Приближать руку", config::isZoomHand, config::setZoomHand),
                    new ChoiceSetting("Подзорная труба", OrionZoom.SPYGLASS_MODES, config::getZoomSpyglassMode, config::setZoomSpyglassMode),
                    new KeybindSetting("Клавиша второго зума", OrionKeybinds::getSecondaryZoomKey),
                    new SliderSetting("Приближение второго зума", 1.5F, 12F, config::getSecondaryZoomFactor, config::setSecondaryZoomFactor, "x"),
                    new SliderSetting("Время второго приближения", 0F, 15F, config::getSecondaryZoomInTime, config::setSecondaryZoomInTime, " с"),
                    new SliderSetting("Время второго отдаления", 0F, 5F, config::getSecondaryZoomOutTime, config::setSecondaryZoomOutTime, " с"),
                    new ToggleSetting("Скрывать HUD при втором зуме", config::isSecondaryZoomHideHud, config::setSecondaryZoomHideHud)
            };
            case CHAT_FILTER -> new Setting[]{ new TextSetting("Слова через запятую", config::getChatFilterWords, configManager::setChatFilterWords, 512) };
            case CHAT_ANIMATION -> new Setting[]{
                    new ToggleSetting("Сдвиг сообщений", config::isChatAnimationMessages, config::setChatAnimationMessages),
                    new ToggleSetting("Плавное появление текста", config::isChatAnimationFade, config::setChatAnimationFade),
                    new SliderSetting("Время анимации сообщений", 10F, 800F, config::getChatAnimationMessageTime, config::setChatAnimationMessageTime, " мс"),
                    new ToggleSetting("Анимация поля ввода", config::isChatAnimationInput, config::setChatAnimationInput),
                    new SliderSetting("Время анимации поля ввода", 10F, 800F, config::getChatAnimationInputTime, config::setChatAnimationInputTime, " мс")
            };
            case CHAT_HIGHLIGHT -> new Setting[]{
                    new TextSetting("Слова через запятую", config::getChatHighlightWords, configManager::setChatHighlightWords, 512),
                    new ColorSetting("Цвет", COLOR_PRESETS, config::getChatHighlightColor, configManager::setChatHighlightColor)
            };
            case TARGET_ESP -> new Setting[]{
                    new ChoiceSetting("Текстура", OrionTargetEsp.TEXTURES,
                            config::getTargetEspTexture, configManager::setTargetEspTexture),
                    new ColorSetting("Цвет цепи", COLOR_PRESETS,
                            config::getTargetEspChainColor, config::setTargetEspChainColor)
            };
            case JUMP_CIRCLE -> new Setting[]{
                    new ColorSetting("Цвет кольца", COLOR_PRESETS,
                            config::getJumpCircleColor, config::setJumpCircleColor),
                    new SliderSetting("Размер", 0.25F, 3.0F,
                            config::getJumpCircleSize, config::setJumpCircleSize, "x")
            };
            case ASPECT -> new Setting[]{
                    new SliderSetting("Растяжение", 0.05F, 2.0F,
                            config::getAspectStretch, config::setAspectStretch, "x")
            };
            case HUD -> new Setting[]{
                    hudStyleSetting("Водяной знак", "hud_watermark"),
                    hudStyleSetting("Клавиши", "hud_status_panels"),
                    hudStyleSetting("Инвентарь", "inventory_hud"),
                    hudStyleSetting("Игроки рядом", "hud_better_near"),
                    hudStyleSetting("Медиаплеер", "media_player_hud"),
                    hudStyleSetting("Баскетбол", "basketball_hud"),
                    hudStyleSetting("Футбол", "football_hud"),
                    new ChoiceSetting("Скорборд", new String[]{"Обычный", "Glass"},
                            () -> config.isScoreboardGlassEnabled() ? 1 : 0, value -> configManager.setScoreboardGlassEnabled(value == 1)),
                    new SliderSetting("Масштаб", 0.5F, 4.0F,
                            config::getHudScale, config::setHudScale, "x")
            };
            case MIRAGE -> new Setting[]{
                    new ValueSetting("Отражения", () -> ru.orion.client.effect.OrionMirage.externalShadersActive() ? "Используются отражения активного шейдерпака" : "Видимые детали мира"),
                    new ChoiceSetting("Качество", new String[]{"Лёгкое", "Высокое", "Максимум"}, config::getMirageQuality, config::setMirageQuality),
                    new SliderSetting("Сила отражений", 0, 40, () -> config.getMirageStrength() * 100, value -> config.setMirageStrength(value / 100), "%"),
                    new SliderSetting("Матовость", 0, 100, () -> config.getMirageRoughness() * 100, value -> config.setMirageRoughness(value / 100), "%"),
                    new SliderSetting("Мягкое свечение", 0, 35, () -> config.getMirageBloom() * 100, value -> config.setMirageBloom(value / 100), "%"),
                    new SliderSetting("Цветокоррекция", 0, 100, () -> config.getMirageColorGrade() * 100, value -> config.setMirageColorGrade(value / 100), "%")
            };
            case MOTION_BLUR -> new Setting[]{
                    new SliderSetting("Сила", 0.0F, 1.0F, config::getMotionBlurStrength, config::setMotionBlurStrength, "x")
            };
            case BLOCK_HIGHLIGHT -> new Setting[]{
                    new ColorSetting("Цвет контура", COLOR_PRESETS, config::getBlockHighlightColor, config::setBlockHighlightColor),
                    new SliderSetting("Скорость перехода", 1.0F, 40.0F, config::getBlockHighlightMoveSpeed, config::setBlockHighlightMoveSpeed, ""),
                    new SliderSetting("Скорость появления", 1.0F, 40.0F, config::getBlockHighlightFadeSpeed, config::setBlockHighlightFadeSpeed, ""),
                    new SliderSetting("Толщина контура", 0.5F, 5.0F, config::getBlockHighlightLineWidth, config::setBlockHighlightLineWidth, ""),
                    new SliderSetting("Непрозрачность", 5.0F, 100.0F, () -> (float) config.getBlockHighlightOpacity(),
                            value -> config.setBlockHighlightOpacity(Math.round(value)), "%", true),
                    new ToggleSetting("Свечение", config::isBlockHighlightGlow, configManager::setBlockHighlightGlow),
                    new SliderSetting("Ширина свечения", 1.0F, 16.0F, config::getBlockHighlightGlowWidth, config::setBlockHighlightGlowWidth, ""),
                    new SliderSetting("Яркость свечения", 0.0F, 100.0F, () -> (float) config.getBlockHighlightGlowOpacity(),
                            value -> config.setBlockHighlightGlowOpacity(Math.round(value)), "%", true),
                    new SliderSetting("Отступ от блока", 0.0F, 0.1F, config::getBlockHighlightOffset, config::setBlockHighlightOffset, "м")
            };
            case FOOTBALL -> {
                var football = OrionClient.getFootballOrNull();
                yield new Setting[]{
                        new ChoiceSetting("Формат матча", new String[]{"1×1", "2×2"}, () -> footballTeamSize, value -> footballTeamSize = value),
                        new ButtonSetting("Арена вокруг тебя", "Создать матч", () -> { if (football != null) football.createMatch(footballTeamSize + 1); }),
                        new TextSetting("Код приглашения", () -> footballInviteCode, value -> footballInviteCode = value, 6),
                        new ButtonSetting("Войти по коду", "Войти в матч", () -> { if (football != null) football.joinMatch(footballInviteCode); }),
                        new ValueSetting("Комната", () -> football == null ? "—" : football.matchInfo()),
                        new ValueSetting("Статус матча", () -> football == null ? "—" : football.matchStatus()),
                        new ButtonSetting("Пригласить друзей", "Копировать код", () -> {
                            if (football != null && !football.matchCode().isEmpty()) minecraft.keyboardHandler.setClipboard(football.matchCode());
                        }),
                        new ButtonSetting("Подтвердить готовность", "Готов", () -> { if (football != null) football.readyMatch(); }),
                        new ButtonSetting("Выйти из комнаты", "Выйти", () -> { if (football != null) football.leaveMatch(); }),
                        new KeybindSetting("Установить ворота", () -> football == null ? null : football.getGoalKey()),
                        new KeybindSetting("Создать или вернуть мяч", () -> football == null ? null : football.getBallKey()),
                        new ValueSetting("Быстрый удар", () -> InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_LEFT).getDisplayName().getString()),
                        new ValueSetting("Зарядить удар", () -> InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_RIGHT).getDisplayName().getString()),
                        new ValueSetting("Подкрутить мяч", () -> "Shift + " + InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_RIGHT).getDisplayName().getString()),
                        new KeybindSetting("Переместить ворота", () -> football == null ? null : football.getMoveKey()),
                        new KeybindSetting("Удалить ворота", () -> football == null ? null : football.getRemoveKey()),
                        new KeybindSetting("Поворот ворот влево", () -> football == null ? null : football.getRotateLeftKey()),
                        new KeybindSetting("Поворот ворот вправо", () -> football == null ? null : football.getRotateRightKey()),
                        new KeybindSetting("Переключить вратаря", () -> football == null ? null : football.getKeeperKey()),
                        new KeybindSetting("Переключить пенальти", () -> football == null ? null : football.getPenaltyKey()),
                        new SliderSetting("Размер ворот", .5F, 2F, config::getFootballGoalScale, config::setFootballGoalScale, "x"),
                        new ChoiceSetting("Сложность вратаря", new String[]{"Easy", "Normal", "Hard"}, config::getFootballGoalkeeperDifficulty, configManager::setFootballGoalkeeperDifficulty),
                        new ToggleSetting("Вратарь", config::isFootballGoalkeeperEnabled, configManager::setFootballGoalkeeperEnabled),
                        new ToggleSetting("Пенальти", config::isFootballPenaltyMode, configManager::setFootballPenaltyMode),
                        new SliderSetting("Сила удара", .3F, 2F, config::getFootballKickPower, config::setFootballKickPower, "x"),
                        new SliderSetting("Скорость зарядки", .3F, 3F, config::getFootballChargeSpeed, config::setFootballChargeSpeed, "x")
                };
            }
            case BASKETBALL -> {
                var basketball = OrionClient.getBasketballOrNull();
                yield new Setting[]{
                        new ChoiceSetting("Формат матча", new String[]{"1×1", "2×2"}, () -> basketballTeamSize, value -> basketballTeamSize = value),
                        new ButtonSetting("Арена вокруг тебя", "Создать матч", () -> { if (basketball != null) basketball.createMatch(basketballTeamSize+1); }),
                        new TextSetting("Код приглашения", () -> basketballInviteCode, value -> basketballInviteCode = value, 6),
                        new ButtonSetting("Войти по коду", "Войти в матч", () -> { if (basketball != null) basketball.joinMatch(basketballInviteCode); }),
                        new ValueSetting("Комната", () -> basketball == null ? "—" : basketball.match().info()),
                        new ValueSetting("Статус матча", () -> basketball == null ? "—" : basketball.match().status()),
                        new ButtonSetting("Пригласить друзей", "Копировать код", () -> {
                            if (basketball != null && basketball.match().active()) minecraft.keyboardHandler.setClipboard(basketball.match().code());
                        }),
                        new ButtonSetting("Подтвердить готовность", "Готов", () -> { if (basketball != null) basketball.match().ready(); }),
                        new ButtonSetting("Выйти из комнаты", "Выйти", () -> { if (basketball != null) basketball.match().leave(); }),
                        new KeybindSetting("Установить кольцо", () -> basketball == null ? null : basketball.getHoopKey()),
                        new KeybindSetting("Взять или создать мяч", () -> basketball == null ? null : basketball.getBallKey()),
                        new ValueSetting("Зарядить бросок", () -> keybindLabel(minecraft.options == null ? null : minecraft.options.keyUse)),
                        new ValueSetting("Пас", () -> keybindLabel(minecraft.options == null ? null : minecraft.options.keyAttack)),
                        new KeybindSetting("Переместить кольцо", () -> basketball == null ? null : basketball.getMoveKey()),
                        new KeybindSetting("Удалить кольцо", () -> basketball == null ? null : basketball.getRemoveKey()),
                        new KeybindSetting("Повернуть кольцо влево", () -> basketball == null ? null : basketball.getRotateLeftKey()),
                        new KeybindSetting("Повернуть кольцо вправо", () -> basketball == null ? null : basketball.getRotateRightKey()),
                        new KeybindSetting("Подобрать мяч", () -> basketball == null ? null : basketball.getPickupKey()),
                        new KeybindSetting("Сбросить счёт клавишей", () -> basketball == null ? null : basketball.getResetKey()),
                        new ValueSetting("Колец в этом мире", () -> basketball == null ? "0" : Integer.toString(basketball.hoopCount())),
                        new ButtonSetting("Снять все кольца здесь", "Снять кольца", () -> { if (basketball != null) basketball.clearHoopsInCurrentWorld(); }),
                        new ButtonSetting("Обнулить счёт", "Обнулить счёт", () -> { if (basketball != null) basketball.resetScore(); }),
                        new ButtonSetting("Убрать мяч", "Убрать мяч", () -> { if (basketball != null) basketball.removeBall(); }),
                        new ButtonSetting("Настройки баскетбола", "Сбросить настройки", this::resetBasketballSettings),
                        new SliderSetting("Сила броска", 0.3F, 2F, config::getBasketballThrowPower, config::setBasketballThrowPower, "x"),
                        new SliderSetting("Скорость зарядки", 0.3F, 3F, config::getBasketballChargeSpeed, config::setBasketballChargeSpeed, "x"),
                        new SliderSetting("Сила паса", 6F, 30F, config::getBasketballPassPower, configManager::setBasketballPassPower, " м/с"),
                        new SliderSetting("Вращение мяча", 0F, 3F, config::getBasketballSpin, configManager::setBasketballSpin, "x"),
                        new ToggleSetting("Показывать дугу", config::isBasketballTrajectory, configManager::setBasketballTrajectory),
                        new ToggleSetting("Ловить самому", config::isBasketballAutoCatch, configManager::setBasketballAutoCatch),
                        new SliderSetting("Сила отскока", 0.1F, 2F, config::getBasketballBounce, config::setBasketballBounce, "x"),
                        new SliderSetting("Гравитация", 2F, 25F, config::getBasketballGravity, config::setBasketballGravity, ""),
                        new SliderSetting("Скорость дриблинга", 0.3F, 2F, config::getBasketballDribbleSpeed, config::setBasketballDribbleSpeed, "x"),
                        new ToggleSetting("Ведение при ходьбе", config::isBasketballAutoDribble, configManager::setBasketballAutoDribble),
                        new SliderSetting("Размер мяча", 0.5F, 1.5F, config::getBasketballBallScale, config::setBasketballBallScale, "x"),
                        new SliderSetting("Громкость", 0F, 1F, config::getBasketballVolume, config::setBasketballVolume, "x"),
                        new ToggleSetting("Показывать HUD", config::isBasketballHud, configManager::setBasketballHud),
                        new ToggleSetting("Показывать руки", config::isBasketballHands, configManager::setBasketballHands),
                        new ToggleSetting("Разметка площадки", config::isBasketballCourtLines, configManager::setBasketballCourtLines)
                };
            }
            case LOCK_SLOT -> new Setting[]{new KeybindSetting("Клавиша блокировки", OrionKeybinds::getLockSlotKey)};
            case ANIMATIONS -> new Setting[]{
                    new SliderSetting("Плавность", 50F, 600F, () -> (float) config.getAnimationsDuration(),
                            value -> config.setAnimationsDuration(Math.round(value)), " мс", true)
            };
            case ENCHANT_GLINT -> new Setting[]{
                    new ChoiceSetting("Эффект", new String[]{"Orion", "Выкл", "Azure", "Rose", "Gold"}, config::getEnchantGlintMode, configManager::setEnchantGlintMode)
            };
            case PVP_SAFE -> new Setting[]{
                    new SliderSetting("Время после удара", 1F, 300F, () -> (float) config.getPvpSafeSeconds(),
                            value -> config.setPvpSafeSeconds(Math.round(value)), " с", true)
            };
            case CUSTOM_TIME -> new Setting[]{
                    new ChoiceSetting("Пресет времени", new String[]{"Рассвет", "День", "Закат", "Ночь", "Лунная ночь", "Своё"},
                            () -> timePreset(config.getCustomTimeTicks()), index -> {
                                int[] values={0,6000,12000,15000,18000};
                                if(index<values.length) configManager.setCustomTimeTicks(values[index]);
                                else if(timePreset(config.getCustomTimeTicks())!=5)
                                    configManager.setCustomTimeTicks((config.getCustomTimeTicks()+1)%24000);
                            }),
                    new SliderSetting("Время суток", 0F, 23999F, () -> (float) config.getCustomTimeTicks(), value -> config.setCustomTimeTicks(Math.round(value)), " тиков", true)
            };
            case ITEM_SCALING -> new Setting[]{
                    new SliderSetting("Масштаб предмета", 0.2F, 2F, config::getItemScaling, config::setItemScaling, "x")
            };
            case SMALL_SHIELD -> new Setting[]{
                    new SliderSetting("Размер щита", 0.2F, 1F, config::getSmallShieldScale, config::setSmallShieldScale, "x")
            };
            case VIEW_MODEL -> new Setting[]{
                    new SliderSetting("Положение X", -1F, 1F, config::getViewModelX, config::setViewModelX, ""),
                    new SliderSetting("Положение Y", -1F, 1F, config::getViewModelY, config::setViewModelY, ""),
                    new SliderSetting("Положение Z", -1F, 1F, config::getViewModelZ, config::setViewModelZ, "")
            };
            case NO_PARTICLES -> new Setting[]{
                    new ToggleSetting("Убирать взрывы", config::isNoParticlesExplosions, configManager::setNoParticlesExplosions),
                    new ToggleSetting("Убирать фоновые", config::isNoParticlesAmbient, configManager::setNoParticlesAmbient),
                    new ToggleSetting("Убирать боевые", config::isNoParticlesCombat, configManager::setNoParticlesCombat)
            };
            case HIT_PARTICLES -> new Setting[]{
                    new ColorSetting("Цвет частиц", COLOR_PRESETS, config::getHitParticlesColor, config::setHitParticlesColor),
                    new SliderSetting("Количество", 1.0F, 64.0F, () -> (float) config.getHitParticlesCount(),
                            value -> config.setHitParticlesCount(Math.round(value)), "", true),
                    new SliderSetting("Размер", 0.03F, 0.3F, config::getHitParticlesSize, config::setHitParticlesSize, "м"),
                    new ChoiceSetting("Форма", new String[]{"Искра", "Ромб", "Круг"}, config::getHitParticlesShape, config::setHitParticlesShape)
            };
            case PROJECTILE_TRAIL -> new Setting[]{
                    new ColorSetting("Цвет следа", COLOR_PRESETS, config::getProjectileTrailColor, config::setProjectileTrailColor),
                    new SliderSetting("Длина", 0.25F, 5.0F, config::getProjectileTrailDuration, config::setProjectileTrailDuration, "с"),
                    new ToggleSetting("Свечение", config::isProjectileTrailGlow, configManager::setProjectileTrailGlow)
            };
            case DAMAGE_INDICATORS -> new Setting[]{
                    new ToggleSetting("Только свои удары", config::isDamageIndicatorsOwnHits, config::setDamageIndicatorsOwnHits),
                    new ToggleSetting("Показывать лечение", config::isDamageIndicatorsHealing, config::setDamageIndicatorsHealing),
                    new SliderSetting("Время на экране", .5F, 5F, config::getDamageIndicatorsLifetime, config::setDamageIndicatorsLifetime, " с"),
                    new SliderSetting("Размер чисел", .5F, 2F, config::getDamageIndicatorsScale, config::setDamageIndicatorsScale, "x"),
                    new ColorSetting("Малый урон", COLOR_PRESETS, config::getDamageIndicatorsLowColor, config::setDamageIndicatorsLowColor),
                    new ColorSetting("Большой урон", COLOR_PRESETS, config::getDamageIndicatorsHighColor, config::setDamageIndicatorsHighColor),
                    new SliderSetting("Большой урон от", 1.0F, 40.0F, config::getDamageIndicatorsThreshold, config::setDamageIndicatorsThreshold, "")
            };
            case BREADCRUMBS -> new Setting[]{
                    new ToggleSetting("Акцентный цвет", config::isBreadcrumbsUseAccent, config::setBreadcrumbsUseAccent),
                    new ColorSetting("Свой цвет", COLOR_PRESETS, config::getBreadcrumbsColor, config::setBreadcrumbsColor),
                    new SliderSetting("Длина", 0.5F, 8.0F, config::getBreadcrumbsDuration, config::setBreadcrumbsDuration, "с"),
                    new SliderSetting("Толщина", 0.02F, 0.3F, config::getBreadcrumbsWidth, config::setBreadcrumbsWidth, "м"),
                    new SliderSetting("Непрозрачность", 5.0F, 100.0F, () -> (float) config.getBreadcrumbsOpacity(),
                            value -> config.setBreadcrumbsOpacity(Math.round(value)), "%", true),
                    new ToggleSetting("Свечение", config::isBreadcrumbsGlow, config::setBreadcrumbsGlow),
                    new ToggleSetting("Сквозь блоки", config::isBreadcrumbsThroughWalls, config::setBreadcrumbsThroughWalls)
            };
            case WORLD_PARTICLES -> new Setting[]{
                    new ColorSetting("Цвет частиц", COLOR_PRESETS,
                            config::getWorldParticlesColor, config::setWorldParticlesColor),
                    new SliderSetting("Количество", 1.0F, 2500.0F,
                            () -> (float) config.getWorldParticlesCount(),
                            value -> config.setWorldParticlesCount(Math.round(value)),
                            "", true)
            };
            case WORLD_CHANGER -> new Setting[]{
                    new ColorSetting("Цвет неба", COLOR_PRESETS,
                            config::getWorldChangerSkyColor, config::setWorldChangerSkyColor),
                    new ColorSetting("Цвет тумана", FOG_COLOR_PRESETS,
                            config::getWorldChangerFogColor, config::setWorldChangerFogColor)
            };
            case PROTECT_NAME -> new Setting[]{
                    new TextSetting("Ник", config::getProtectName, configManager::setProtectName, 16)
            };
            case FAKE_PREFIX -> new Setting[]{
                    new TextSetting("Префикс / JSON", config::getFakePrefix, configManager::setFakePrefix, 2048),
                    new TextSetting("Доп. ники через запятую", config::getFakePrefixAdditionalNames,
                            configManager::setFakePrefixAdditionalNames, 512)
            };
            case HIT_BOX -> new Setting[]{
                    new ColorSetting("Цвет хитбоксов", COLOR_PRESETS,
                            config::getHitBoxColor, config::setHitBoxColor)
            };
            case HIT_COLOR -> new Setting[]{
                    new ColorSetting("Цвет заливки", COLOR_PRESETS,
                            config::getHitColorColor, config::setHitColorColor),
                    new SliderSetting("Прозрачность", 5.0F, 100.0F,
                            () -> (float) config.getHitColorOpacity(),
                            value -> config.setHitColorOpacity(Math.round(value)),
                            "%", true)
            };
            case HIT_SOUND -> new Setting[]{
                    new ChoiceSetting("Звук", OrionHitSound.SOUND_NAMES,
                            config::getHitSoundIndex, configManager::setHitSoundIndex),
                    new SliderSetting("Громкость", 0.0F, 100.0F,
                            () -> (float) config.getHitSoundVolume(),
                            value -> config.setHitSoundVolume(Math.round(value)),
                            "%", true),
                    new SliderSetting("Высота звука", .5F, 2F, config::getHitSoundPitch, config::setHitSoundPitch, "x"),
                    new ToggleSetting("Варьировать высоту", config::isHitSoundVaryPitch, config::setHitSoundVaryPitch),
                    new ToggleSetting("Только по игрокам", config::isHitSoundPlayersOnly, config::setHitSoundPlayersOnly),
                    new ButtonSetting("Проверка звука", "Прослушать",
                            () -> OrionClient.getInstance().getHitSound().preview()),
                    new ButtonSetting("Свои звуки (.wav, .aiff, .au)", "Папка",
                            this::openHitSoundFolder)
            };
            case CROSSHAIR -> new Setting[]{
                    new ColorSetting("Цвет прицела", COLOR_PRESETS,
                            config::getCrosshairColor, config::setCrosshairColor),
                    new ButtonSetting("Прицел", "Редактор", () -> {
                        Minecraft client = Minecraft.getInstance();
                        client.gui.setScreen(new OrionCrosshairEditorScreen(configManager, this));
                    })
            };
            case BETTER_NEAR -> new Setting[]{
                    new SliderSetting("Радиус", 10.0F, 200.0F,
                            () -> (float) config.getBetterNearRadius(),
                            value -> config.setBetterNearRadius(Math.round(value)),
                            "м", true)
            };
            case RAINBOW -> new Setting[]{
                    new SliderSetting("Скорость", 1.0F, 10.0F,
                            () -> (float) config.getRainbowSpeed(),
                            value -> config.setRainbowSpeed(Math.round(value)),
                            "x", true)
            };
            case MEDIA_PLAYER -> new Setting[]{
                    new ChoiceSetting("Позиция", new String[]{"TOP_LEFT", "TOP_RIGHT"},
                            () -> "TOP_RIGHT".equals(config.getMediaPlayerCorner()) ? 1 : 0,
                            index -> configManager.setMediaPlayerCorner(index == 1 ? "TOP_RIGHT" : "TOP_LEFT")),
                    new ChoiceSetting("Анимация", new String[]{"Вкл", "Выкл"},
                            () -> config.isMediaPlayerAnimated() ? 0 : 1,
                            index -> configManager.setMediaPlayerAnimated(index == 0))
            };
            default -> new Setting[0];
        };
    }

    private int textureScale() {
        int scale = Minecraft.getInstance().getWindow().getGuiScale();
        return Math.max(1, Math.min(MAX_TEXTURE_SCALE, scale));
    }

    private void openHitSoundFolder() {
        hitSoundFolderStatus = "Открываем…";
        lastFingerprint = null;
        OrionClient.getInstance().getHitSound().openCustomFolder(success -> Minecraft.getInstance().execute(() -> {
            hitSoundFolderStatus = success ? "Открыто" : "Ошибка — см. лог";
            lastFingerprint = null;
        }));
    }

    private boolean usesSettingsLayer() { return paradox && settingsFor != null; }

    private int selectedSettingsGroup() {
        int selected = 0;
        int[] starts = paradoxGroupStarts(settingsRows);
        for (int i = 0; i < starts.length; i++) if (settingRowY(starts[i]) <= L.moduleY + 36) selected = i;
        return selected;
    }

    /** Три окна настроек кешируются одним Java2D-слоем; скролл меняет только UV. */
    private void updateSettingsTexture() {
        if (!usesSettingsLayer()) { releaseSettingsTextures(); return; }
        int scale = textureScale();
        int viewport = L.guiH - L.moduleY - 8;
        int content = viewport + settingsMaxScroll(settingsRows);
        int atlasHeight = Math.min(content, viewport * 3);
        int start = Math.max(0, Math.min(content - atlasHeight, (currentSettingsScroll() / viewport - 1) * viewport));
        List<Integer> next = new ArrayList<>(fingerprint(scale, true));
        next.add(start); next.add(atlasHeight);
        if (next.equals(lastSettingsFingerprint)) return;
        settingsAtlasStart = start; settingsAtlasHeight = atlasHeight;
        float sx = guiDisplayWidth() / (float) L.guiW, sy = guiDisplayHeight() / (float) L.guiH;
        settingsAtlasGuiWidth = Math.max(1, Math.round((L.moduleX + L.moduleW) * sx) - Math.round(L.moduleX * sx));
        settingsAtlasGuiHeight = Math.max(1, (int) Math.ceil(atlasHeight * sy));
        int pw = settingsAtlasGuiWidth * scale, ph = settingsAtlasGuiHeight * scale;
        renderSettingsImage(pw, ph);
        var client = Minecraft.getInstance();
        if (settingsTexture == null || settingsTexture.getPixels() == null || settingsTexture.getPixels().isClosed() || settingsTexture.getPixels().getWidth() != pw || settingsTexture.getPixels().getHeight() != ph) {
            client.getTextureManager().release(SETTINGS_TEXTURE);
            NativeImage pixels = new NativeImage(pw, ph, true);
            settingsUploadPixels = copyPixels(settingsCanvas, pixels, settingsUploadPixels);
            settingsTexture = new DynamicTexture(() -> "orion_settings", pixels);
            client.getTextureManager().register(SETTINGS_TEXTURE, settingsTexture);
        } else {
            settingsUploadPixels = copyPixels(settingsCanvas, settingsTexture.getPixels(), settingsUploadPixels);
            settingsTexture.upload();
        }
        lastSettingsFingerprint = next;
        updateScrollThumb(scale, sx, sy);
    }

    private BufferedImage renderSettingsImage(int pw, int ph) {
        if (settingsCanvas == null || settingsCanvas.getWidth() != pw || settingsCanvas.getHeight() != ph)
            settingsCanvas = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = settingsCanvas.createGraphics();
        try {
            g.setComposite(AlphaComposite.Clear); g.fill(new Rectangle2D.Float(0, 0, pw, ph));
            g.setComposite(AlphaComposite.SrcOver); setupGraphics(g);
            g.scale(pw / (double) L.moduleW, ph / (double) settingsAtlasHeight);
            g.translate(-L.moduleX, -L.moduleY);
            drawingSettingsLayer = true;
            drawSettingRows(g, settingsRows);
        } finally { drawingSettingsLayer = false; g.dispose(); }
        return settingsCanvas;
    }

    private void updateScrollThumb(int scale, float sx, float sy) {
        int maximum = settingsMaxScroll(settingsRows);
        if (maximum <= 0) return;
        float track = L.guiH - L.moduleY - 10;
        thumbGuiWidth = Math.max(1, Math.round(2 * sx));
        thumbGuiHeight = Math.max(1, Math.round(Math.max(16, track * track / (track + maximum)) * sy));
        List<Integer> next = List.of(scale, thumbGuiWidth, thumbGuiHeight, PRIMARY.getRGB());
        if (next.equals(lastThumbFingerprint)) return;
        int pw = thumbGuiWidth * scale, ph = thumbGuiHeight * scale;
        BufferedImage image = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { setupGraphics(g); g.setColor(withAlpha(PRIMARY, 190)); g.fill(new RoundRectangle2D.Float(0, 0, pw, ph, pw, pw)); }
        finally { g.dispose(); }
        var client = Minecraft.getInstance();
        client.getTextureManager().release(SCROLL_THUMB_TEXTURE);
        NativeImage pixels = new NativeImage(pw, ph, true);
        settingsUploadPixels = copyPixels(image, pixels, settingsUploadPixels);
        scrollThumbTexture = new DynamicTexture(() -> "orion_scroll_thumb", pixels);
        client.getTextureManager().register(SCROLL_THUMB_TEXTURE, scrollThumbTexture);
        lastThumbFingerprint = next;
    }

    private void drawCachedSettings(GuiGraphicsExtractor context) {
        if (!usesSettingsLayer() || settingsTexture == null) return;
        if (!accountsOpen || L.moduleX >= ACCOUNT_X + ACCOUNT_W) {
            blitCachedSettings(context);
            return;
        }
        float sx = guiDisplayWidth() / (float) L.guiW, sy = guiDisplayHeight() / (float) L.guiH;
        int left = guiX(), top = guiY(), right = left + guiDisplayWidth(), bottom = top + guiDisplayHeight();
        int panelTop = top + (int) Math.floor(accountY() * sy);
        int panelBottom = top + (int) Math.ceil((accountY() + accountHeight()) * sy);
        int panelRight = left + (int) Math.ceil((ACCOUNT_X + ACCOUNT_W) * sx);
        blitCachedSettingsClipped(context, left, top, right, panelTop);
        blitCachedSettingsClipped(context, panelRight, panelTop, right, panelBottom);
        blitCachedSettingsClipped(context, left, panelBottom, right, bottom);
    }

    private void blitCachedSettingsClipped(GuiGraphicsExtractor context, int x, int y, int right, int bottom) {
        context.enableScissor(x, y, right, bottom);
        try {
            blitCachedSettings(context);
        } finally {
            context.disableScissor();
        }
    }

    private void blitCachedSettings(GuiGraphicsExtractor context) {
        float sx = guiDisplayWidth() / (float) L.guiW, sy = guiDisplayHeight() / (float) L.guiH;
        int x = guiX() + Math.round(L.moduleX * sx), y = guiY() + Math.round(L.moduleY * sy);
        int height = Math.round((L.guiH - 8) * sy) - Math.round(L.moduleY * sy);
        int offset = Math.max(0, Math.min(settingsAtlasGuiHeight - height, Math.round((currentSettingsScroll() - settingsAtlasStart) * sy)));
        context.blit(SETTINGS_TEXTURE, x, y, x + settingsAtlasGuiWidth, y + height, 0, 1,
                offset / (float) settingsAtlasGuiHeight, (offset + height) / (float) settingsAtlasGuiHeight);
        int maximum = settingsMaxScroll(settingsRows);
        if (maximum > 0 && scrollThumbTexture != null) {
            float track = L.guiH - L.moduleY - 10;
            float thumb = Math.max(16, track * track / (track + maximum));
            int tx = guiX() + Math.round((L.moduleX + L.moduleW + 3) * sx);
            int ty = guiY() + Math.round((L.moduleY + (track - thumb) * currentSettingsScroll() / maximum) * sy);
            context.blit(SCROLL_THUMB_TEXTURE, tx, ty, tx + thumbGuiWidth, ty + thumbGuiHeight, 0, 1, 0, 1);
        }
    }

    private void releaseSettingsTextures() {
        var client = Minecraft.getInstance();
        if (settingsTexture != null) client.getTextureManager().release(SETTINGS_TEXTURE);
        if (scrollThumbTexture != null) client.getTextureManager().release(SCROLL_THUMB_TEXTURE);
        settingsTexture = scrollThumbTexture = null;
        settingsCanvas = null; settingsUploadPixels = null;
        lastSettingsFingerprint = lastThumbFingerprint = null;
    }

    private void updateTextureIfNeeded() {
        applyPalette();
        int scale = textureScale();
        List<Integer> fingerprint = fingerprint(scale);
        if (fingerprint.equals(lastFingerprint)) {
            return;
        }

        int imageWidth = guiDisplayWidth() * scale;
        int imageHeight = guiDisplayHeight() * scale;
        long now = System.nanoTime();
        if (dynamicTexture != null && textureWidth == imageWidth && textureHeight == imageHeight
                && now - lastTextureUpdateNanos < TEXTURE_UPDATE_INTERVAL_NANOS) {
            return;
        }
        long renderStart = System.nanoTime();
        BufferedImage image = renderImage(scale);
        OrionGlassCompositor.INSTANCE.updateMask(GUI_TEXTURE,new OrionGlassCompositor.MaskLayout(imageWidth,imageHeight,L.guiW,L.guiH,
                List.of(new OrionGlassCompositor.Panel(0,0,L.guiW,L.guiH,L.windowRadius*2F))));
        long renderElapsed = System.nanoTime() - renderStart;

        if (dynamicTexture == null || textureWidth != imageWidth || textureHeight != imageHeight) {
            Minecraft.getInstance().getTextureManager().release(GUI_TEXTURE);
            NativeImage nativeImage = new NativeImage(imageWidth, imageHeight, true);
            copyPixels(image, nativeImage);
            dynamicTexture = new DynamicTexture(() -> "orion_click_gui", nativeImage);
            Minecraft.getInstance().getTextureManager().register(GUI_TEXTURE, dynamicTexture);
            textureWidth = imageWidth;
            textureHeight = imageHeight;
        } else {
            NativeImage pixels = dynamicTexture.getPixels();
            if (pixels == null || pixels.isClosed()) {
                lastFingerprint = null;
                return;
            }
            copyPixels(image, pixels);
            dynamicTexture.upload();
        }
        lastFingerprint = fingerprint;
        lastTextureUpdateNanos = System.nanoTime();
        long totalElapsed = lastTextureUpdateNanos - renderStart;
        if (totalElapsed > 250_000_000L) {
            LOGGER.info("Подготовка меню Orion: Java2D {} мс, загрузка текстуры {} мс, размер {}x{}",
                    renderElapsed / 1_000_000L, (totalElapsed - renderElapsed) / 1_000_000L,
                    imageWidth, imageHeight);
        }
    }

    private void copyPixels(BufferedImage source, NativeImage target) {
        uploadPixels = copyPixels(source, target, uploadPixels);
    }

    private static int[] copyPixels(BufferedImage source, NativeImage target, int[] buffer) {
        int[] argb = ((DataBufferInt) source.getRaster().getDataBuffer()).getData();
        if (buffer == null || buffer.length != argb.length) buffer = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            int color = argb[i];
            buffer[i] = (color & 0xFF00FF00) | ((color & 0x00FF0000) >>> 16) | ((color & 0x000000FF) << 16);
        }
        target.getPixelBytes().order(ByteOrder.nativeOrder()).asIntBuffer().put(buffer);
        return buffer;
    }

    private BufferedImage renderImage(int scale) {
        int imageWidth = guiDisplayWidth() * scale;
        int imageHeight = guiDisplayHeight() * scale;
        if (canvas == null || canvas.getWidth() != imageWidth || canvas.getHeight() != imageHeight) {
            canvas = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        }
        BufferedImage image = canvas;
        Graphics2D g = image.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fill(new Rectangle2D.Float(0, 0, imageWidth, imageHeight));
        g.setComposite(AlphaComposite.SrcOver);
        setupGraphics(g);
        // Fractional content scale keeps texture pixels 1:1 with physical pixels at any theme size.
        g.scale(imageWidth / (double) L.guiW, imageHeight / (double) L.guiH);

        Shape window = new RoundRectangle2D.Float(0, 0, L.guiW, L.guiH, L.windowRadius * 2, L.windowRadius * 2);
        Composite windowComposite=g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.78F));
        drawWindow(g, window);
        g.setComposite(windowComposite);
        drawHeader(g, window);
        drawSidebar(g);
        Composite contentComposite = g.getComposite();
        if (paradox && settingsFor != null) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                (float)Math.sin(pageAnimation * Math.PI / 2)));
        drawContent(g);
        g.setComposite(contentComposite);
        if (accountsOpen) drawAccounts(g);

        g.dispose();
        return image;
    }

    private void drawWindow(Graphics2D g, Shape window) {
        if (paradox) {
            g.setColor(BG);
            g.fill(window);
            Shape clip = g.getClip();
            g.clip(window);
            g.setColor(SIDEBAR_BG);
            g.fill(new Rectangle2D.Float(0, 0, L.sidebarW, L.guiH));
            g.setColor(withAlpha(BORDER, 150));
            g.setStroke(new BasicStroke(1));
            g.draw(new Line2D.Float(L.sidebarW + .5F, 0, L.sidebarW + .5F, L.guiH));
            g.setClip(clip);
            g.setColor(withAlpha(BORDER, 200));
            g.draw(new RoundRectangle2D.Float(.5F, .5F, L.guiW - 1, L.guiH - 1, 19, 19));
            return;
        }
        if (orion3) {
            g.setColor(BG);
            g.fill(window);

            Shape oldClip = g.getClip();
            g.setClip(window);
            g.setColor(withAlpha(BORDER, 140));
            g.setStroke(new BasicStroke(1.0F));
            g.draw(new Line2D.Float(L.sidebarW, 0, L.sidebarW, L.guiH));
            g.draw(new Line2D.Float(0, ORION3_LOGO_AREA_HEIGHT, L.sidebarW, ORION3_LOGO_AREA_HEIGHT));
            g.setClip(oldClip);

            g.setColor(withAlpha(BORDER, 170));
            g.setStroke(new BasicStroke(1.0F));
            g.draw(new RoundRectangle2D.Float(0.5F, 0.5F, L.guiW - 1, L.guiH - 1, L.windowRadius * 2 - 1, L.windowRadius * 2 - 1));
            return;
        }

        g.setPaint(new GradientPaint(0, HEADER_HEIGHT, lighten(BG, 5), 0, GUI_HEIGHT, darken(BG, 6)));
        g.fill(window);

        Shape oldClip = g.getClip();
        g.setClip(window);
        g.setColor(SIDEBAR_BG);
        g.fillRect(0, HEADER_HEIGHT, SIDEBAR_WIDTH, GUI_HEIGHT - HEADER_HEIGHT);
        g.setColor(withAlpha(BORDER, 110));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new Line2D.Float(SIDEBAR_WIDTH, HEADER_HEIGHT, SIDEBAR_WIDTH, GUI_HEIGHT));
        g.setClip(oldClip);

        g.setColor(withAlpha(BORDER, 170));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new RoundRectangle2D.Float(0.5F, 0.5F, GUI_WIDTH - 1, GUI_HEIGHT - 1, WINDOW_RADIUS * 2 - 1, WINDOW_RADIUS * 2 - 1));
    }

    private void drawHeader(Graphics2D g, Shape window) {
        if (paradox) {
            Font brand = OrionInterfaceFonts.face(3, 10F);
            OrionBrandMark.drawBesideText(g, 16, 19, 9.0F, brand, "Orion Client", TEXT);
            drawText(g, "Orion Client", 27, 19, brand, TEXT, false);
            drawSearchInput(g);
            drawText(g, tr("Разделы"), 10, 62, OrionInterfaceFonts.face(1,8F), MUTED, false);
            if (paradoxAll || paradoxFavoritesOnly) {
                g.setColor(SURFACE); g.fill(new RoundRectangle2D.Float(7, paradoxAll ? 69 : 88, 111, 18, 6, 6));
            }
            drawText(g, tr("Все"), 27, 82, OrionInterfaceFonts.face(2,9.5F), TEXT, false);
            drawText(g, tr("Избранное"), 27, 101, OrionInterfaceFonts.face(2,9.5F), TEXT, false);
            drawIcon(g, Icon.GRID, 15, 78, TEXT, .9F, .32F);
            drawFavoriteStar(g, 15, 97.5F, paradoxFavoritesOnly);
            if (waypointPage) {
                g.setColor(SURFACE); g.fill(new RoundRectangle2D.Float(7, WAYPOINT_NAV_Y, 111, 18, 6, 6));
            }
            drawIcon(g, Icon.PIN, 15, WAYPOINT_NAV_Y + 9, TEXT, .9F, .3F);
            drawText(g, tr("Метки"), 27, WAYPOINT_NAV_Y + 13, OrionInterfaceFonts.face(2, 9.5F), TEXT, false);
            g.setColor(BORDER);
            g.setStroke(new BasicStroke(0.75F));
            if (presetsPage) { g.setColor(SURFACE); g.fill(new RoundRectangle2D.Float(7, PRESETS_NAV_Y, 111, 18, 6, 6)); }
            drawIcon(g, Icon.MONITOR, 15, PRESETS_NAV_Y + 9, TEXT, .9F, .3F);
            drawText(g, tr("Пресеты"), 27, PRESETS_NAV_Y + 13, OrionInterfaceFonts.face(2, 9.5F), TEXT, false);
            if (configsPage) { g.setColor(SURFACE); g.fill(new RoundRectangle2D.Float(7, CONFIGS_NAV_Y, 111, PARADOX_NAV_ROW_HEIGHT, 6, 6)); }
            drawIcon(g, Icon.FOLDER, 15, CONFIGS_NAV_Y + 9, TEXT, .9F, .3F);
            drawText(g, tr("Конфиги"), 27, CONFIGS_NAV_Y + 13, OrionInterfaceFonts.face(2, 9.5F), TEXT, false);
            g.setColor(BORDER);
            float dividerY = PARADOX_NAV_DIVIDER_Y + 0.5F;
            g.draw(new Line2D.Float(8, dividerY, L.sidebarW - 8, dividerY));
            g.setColor(MUTED);
            g.setStroke(new BasicStroke(1F));
            g.draw(new Line2D.Float(L.guiW - 15, 11, L.guiW - 10, 16));
            g.draw(new Line2D.Float(L.guiW - 10, 11, L.guiW - 15, 16));
            return;
        }
        if (orion3) {
            drawOrion3Brand(g);
            return;
        }

        Shape oldClip = g.getClip();
        g.setClip(window);
        g.setPaint(new GradientPaint(0, 0, PRIMARY, GUI_WIDTH, HEADER_HEIGHT, PRIMARY_DARK));
        g.fillRect(0, 0, GUI_WIDTH, HEADER_HEIGHT);
        g.setColor(new Color(255, 255, 255, 36));
        g.draw(new Line2D.Float(1, 0.8F, GUI_WIDTH - 1, 0.8F));
        g.setClip(oldClip);

        Font brand = headingFont(13.0F);
        OrionBrandMark.drawBesideText(g, 19, 21.5F, 22.0F, brand, "OrionClient", Color.WHITE);
        drawText(g, "OrionClient", 35, 21.5F, brand, Color.WHITE, true);

        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.5F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(GUI_WIDTH - 53, 16.5F, GUI_WIDTH - 44, 16.5F));
        g.draw(new Line2D.Float(GUI_WIDTH - 26.5F, 12, GUI_WIDTH - 17.5F, 21));
        g.draw(new Line2D.Float(GUI_WIDTH - 17.5F, 12, GUI_WIDTH - 26.5F, 21));
    }

    private void drawOrion3Brand(Graphics2D g) {
        float cx = L.sidebarW / 2.0F;
        OrionBrandMark.draw(g, cx, 24, 26.0F, PRIMARY);
        Font brand = headingFont(7.5F);
        float orionWidth = (float) g.getFontMetrics(brand).getStringBounds("Orion ", g).getWidth();
        float clientWidth = (float) g.getFontMetrics(brand).getStringBounds("Client", g).getWidth();
        float start = cx - (orionWidth + clientWidth) / 2.0F;
        drawText(g, "Orion ", start, 48, brand, PRIMARY, true);
        drawText(g, "Client", start + orionWidth, 48, brand, TEXT, true);
    }

    private void drawSidebar(Graphics2D g) {
        for (int i = 0; i < Category.VALUES.length; i++) {
            Category category = Category.VALUES[i];
            int y = categoryRowY(i);
            boolean active = i == selectedCategory;
            float hover = categoryHoverAnim[i];

            if (paradox) {
                boolean selected = active && !paradoxAll && !paradoxFavoritesOnly && !waypointPage && !presetsPage && !configsPage;
                if (selected || hover > .01F) {
                    g.setColor(selected ? SEARCH_BG : withAlpha(SURFACE_HOVER, Math.round(150 * hover)));
                    g.fill(new RoundRectangle2D.Float(L.sideX, y, L.sideW, 18, 5, 5));
                }
                Color color = TEXT;
                drawIcon(g, category.icon, L.sideX + 7, y + 9, color, .9F, .25F);
                drawText(g, tr(category.title), L.sideX + 20, y + 12.5F, OrionInterfaceFonts.face(1,9.5F), color, false);
            } else if (orion3) {
                if (active || hover > 0.01F) {
                    g.setColor(active ? withAlpha(PRIMARY, 28) : withAlpha(SURFACE_HOVER, Math.round(120 * hover)));
                    g.fill(new RoundRectangle2D.Float(L.sideX, y, L.sideW, L.sideH, 8, 8));
                }
                if (active) {
                    g.setColor(PRIMARY);
                    g.fill(new RoundRectangle2D.Float(L.sideX, y + 5, 2.5F, L.sideH - 10, 2.5F, 2.5F));
                }
                Color color = active ? PRIMARY : mix(TEXT, Color.WHITE, hover * 0.4F);
                drawIcon(g, category.icon, L.sideX + 13, y + L.sideH / 2.0F, color, 1.3F, 0.45F);
                drawText(g, tr(category.title), L.sideX + 24, y + L.sideH / 2.0F + 2.8F, bodyFont(7.5F), color, true);
            } else {
                if (active) {
                    g.setColor(SURFACE);
                    g.fill(new RoundRectangle2D.Float(L.sideX, y, L.sideW, L.sideH, 6, 6));
                } else if (hover > 0.01F) {
                    g.setColor(withAlpha(SURFACE, Math.round(140 * hover)));
                    g.fill(new RoundRectangle2D.Float(L.sideX, y, L.sideW, L.sideH, 6, 6));
                }

                Color color = active ? PRIMARY : mix(TEXT, Color.WHITE, hover * 0.4F);
                drawIcon(g, category.icon, 27, y + 17, color, 1.6F, 0.75F);
                drawText(g, tr(category.title), 27 + 12 + ICON_TEXT_GAP, y + 21, bodyFont(10.0F), color, true);
            }
        }

        int footerY = L.guiH - ACCOUNT_FOOTER_H, footerW = L.sidebarW - 16;
        if (accountsOpen || accountFooterHit(pagePointerX, pagePointerY)) {
            g.setColor(accountsOpen ? SURFACE_HOVER : SEARCH_BG);
            g.fill(new RoundRectangle2D.Float(ACCOUNT_X, footerY, footerW, 20, 6, 6));
        }
        String username = accounts.username();
        Font font = bodyFont(orion3 ? 7.5F : 9F);
        Color color = username.isEmpty() && !accountsOpen ? MUTED : TEXT;
        String label = username.isEmpty() ? tr("Аккаунт Orion") : username;
        if (username.isEmpty() && textWidth(g, font, label) > footerW - 32) label = "Orion";
        drawAccountAvatar(g, accounts.avatar(), ACCOUNT_X + 3, footerY + 2, 16, color);
        drawText(g, ellipsize(g, font, label, footerW - 35),
                ACCOUNT_X + 23, footerY + 13, font, color, false);
        int arrowX = ACCOUNT_X + footerW - 8;
        int arrowY = footerY + 10;
        Path2D.Float chevron = new Path2D.Float();
        chevron.moveTo(arrowX - 2.5F, arrowY + 1.5F);
        chevron.lineTo(arrowX, arrowY - 1);
        chevron.lineTo(arrowX + 2.5F, arrowY + 1.5F);
        g.setColor(color);
        g.setStroke(new BasicStroke(.9F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(chevron);
    }

    private int accountRows() { return Math.min(ACCOUNT_VISIBLE_ROWS, accounts.usernames().size()); }
    private int accountActionsY() { return ACCOUNT_LIST_Y + Math.max(1, accountRows()) * ACCOUNT_ROW_H + 7; }
    private int accountSiteY() { return accountForm ? 123 : accountActionsY() + ACCOUNT_BUTTON_H + 4; }
    private int accountAddWidth() {
        int width = ACCOUNT_W - 2 * ACCOUNT_PAD;
        return accounts.username().isEmpty() ? width : width - ACCOUNT_LOGOUT_W - 5;
    }
    private int accountHeight() {
        if (accountForm) return ACCOUNT_FORM_H;
        return accountSiteY() + ACCOUNT_BUTTON_H + (accounts.busy() || !accountStatus.isEmpty() ? 28 : 9);
    }
    private int accountY() { return L.guiH - ACCOUNT_FOOTER_H - accountHeight() - 6; }

    private boolean accountFooterHit(int x, int y) {
        return isInside(x, y, ACCOUNT_X, L.guiH - ACCOUNT_FOOTER_H, L.sidebarW - 16, 20);
    }

    private boolean accountPanelHit(int x, int y) {
        return isInside(x, y, ACCOUNT_X, accountY(), ACCOUNT_W, accountHeight());
    }

    private int accountTarget(int x, int y) {
        x -= ACCOUNT_X;
        y -= accountY();
        if (isInside(x, y, ACCOUNT_W - 26, 6, 18, 18)) return 1;
        if (isInside(x, y, ACCOUNT_PAD, accountSiteY(), ACCOUNT_W - 2 * ACCOUNT_PAD, ACCOUNT_BUTTON_H)) return 7;
        if (accountForm) {
            if (isInside(x, y, ACCOUNT_PAD, 31, ACCOUNT_W - 2 * ACCOUNT_PAD, 27)) return 2;
            if (isInside(x, y, ACCOUNT_PAD, 63, ACCOUNT_W - 2 * ACCOUNT_PAD, 27)) return 3;
            return isInside(x, y, ACCOUNT_PAD, 96, ACCOUNT_W - 2 * ACCOUNT_PAD, 22) ? 4 : 0;
        }
        if (isInside(x, y, ACCOUNT_PAD, ACCOUNT_LIST_Y, ACCOUNT_W - 2 * ACCOUNT_PAD, accountRows() * ACCOUNT_ROW_H)) {
            return 10 + (y - ACCOUNT_LIST_Y) / ACCOUNT_ROW_H;
        }
        if (isInside(x, y, ACCOUNT_PAD, accountActionsY(), accountAddWidth(), ACCOUNT_BUTTON_H)) return 5;
        return isInside(x, y, ACCOUNT_W - ACCOUNT_PAD - ACCOUNT_LOGOUT_W, accountActionsY(), ACCOUNT_LOGOUT_W, ACCOUNT_BUTTON_H) ? 6 : 0;
    }

    private void closeAccounts() {
        accountsOpen = false;
        accountForm = false;
        accountPassword = "";
        accountField = 0;
        lastFingerprint = null;
    }

    private void focusAccountField(int field) {
        accountField = field;
        textCaret = textAnchor = field == 1 ? accountLogin.length() : field == 2 ? accountPassword.length() : 0;
        lastFingerprint = null;
    }

    private void loginAccount() {
        if (accountLogin.isBlank() || accountPassword.isEmpty()) {
            accountStatus = "Введи логин и пароль";
            return;
        }
        if (accountPassword.codePointCount(0, accountPassword.length()) > 128) {
            accountStatus = "Пароль слишком длинный";
            return;
        }
        runAccountTask(accounts.login(accountLogin.strip(), accountPassword), () -> {
            accountForm = false;
            accountOffset = Math.max(0, accounts.usernames().size() - ACCOUNT_VISIBLE_ROWS);
        });
        accountPassword = "";
        focusAccountField(0);
    }

    private void runAccountTask(java.util.concurrent.CompletableFuture<Runnable> task) {
        runAccountTask(task, () -> { });
    }

    private void refreshAccountInBackground() {
        if (accountRefreshing || accounts.busy() || accounts.username().isEmpty()) return;
        accountRefreshing = true;
        accounts.refresh().whenComplete((commit, failure) -> Minecraft.getInstance().execute(() -> {
            accountRefreshing = false;
            if (failure == null) {
                commit.run();
                accountOffset = Math.min(accountOffset, Math.max(0, accounts.usernames().size() - ACCOUNT_VISIBLE_ROWS));
            }
        }));
    }

    private void runAccountTask(java.util.concurrent.CompletableFuture<Runnable> task, Runnable onSuccess) {
        accounts.setBusy(true);
        accountStatus = "";
        focusAccountField(0);
        task.whenComplete((commit, failure) -> Minecraft.getInstance().execute(() -> {
            accounts.setBusy(false);
            if (failure == null) {
                commit.run();
                accountOffset = Math.min(accountOffset, Math.max(0, accounts.usernames().size() - ACCOUNT_VISIBLE_ROWS));
                onSuccess.run();
            } else {
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                accountStatus = cause instanceof IllegalStateException ? cause.getMessage() : "Сайт Orion недоступен";
            }
            lastFingerprint = null;
        }));
    }

    private void accountClick(int x, int y) {
        if (!accountPanelHit(x, y)) { closeAccounts(); return; }
        int target = accountTarget(x, y);
        if (target == 1) { closeAccounts(); return; }
        if (accounts.busy()) return;
        if (target >= 10) {
            String name = accounts.usernames().get(accountOffset + target - 10);
            if (!name.equals(accounts.username())) runAccountTask(accounts.select(name));
        } else if (target == 5) {
            accountForm = true;
            accountStatus = "";
            accountLogin = "";
            focusAccountField(1);
        } else if (target == 6 && !accounts.username().isEmpty()) runAccountTask(accounts.logout());
        else if (target == 7) ConfirmLinkScreen.confirmLinkNow(this, ACCOUNT_PROFILE_PAGE, true);
        else if (target == 2 || target == 3) focusAccountField(target - 1);
        else if (target == 4) loginAccount();
        else focusAccountField(0);
    }

    private void drawAccountAvatar(Graphics2D g, BufferedImage image, int x, int y, int size, Color color) {
        Shape frame = new RoundRectangle2D.Float(x, y, size, size, 6, 6);
        if (image == null) {
            g.setColor(SEARCH_BG);
            g.fill(frame);
            drawIcon(g, Icon.PLAYER, x + size * .5F, y + size * .5F, color, size / 19F, .55F);
            return;
        }
        Shape clip = g.getClip();
        g.clip(frame);
        g.drawImage(image, x, y, size, size, null);
        g.setClip(clip);
    }

    private void drawAccounts(Graphics2D g) {
        int y = accountY(), h = accountHeight();
        int x = ACCOUNT_X + ACCOUNT_PAD, w = ACCOUNT_W - 2 * ACCOUNT_PAD;
        Font body = bodyFont(9.5F);
        g.setColor(BG);
        g.fill(new RoundRectangle2D.Float(ACCOUNT_X, y, ACCOUNT_W, h, 9, 9));
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(1F));
        g.draw(new RoundRectangle2D.Float(ACCOUNT_X + .5F, y + .5F, ACCOUNT_W - 1, h - 1, 9, 9));
        drawText(g, tr(accountForm ? "Вход в Orion" : "Аккаунты Orion"), x + 3, y + 19, bodyFont(11F), TEXT, false);
        drawConfigButton(g, ACCOUNT_X + ACCOUNT_W - 26, y + 6, 18, 18, "×", false, body);
        if (accountForm) {
            drawPageInput(g, x, y + 31, w, body, accountLogin, "Логин", accountField == 1);
            drawPageInput(g, x, y + 63, w, body, "•".repeat(accountPassword.length()), "Пароль", accountField == 2);
            drawConfigButton(g, x, y + 96, w, 22, "Войти в Orion", true, body);
        } else {
            List<String> names = accounts.usernames();
            for (int row = 0; row < accountRows(); row++) {
                String name = names.get(accountOffset + row);
                int rowY = y + ACCOUNT_LIST_Y + row * ACCOUNT_ROW_H;
                boolean active = name.equals(accounts.username());
                boolean hover = !accounts.busy() && isInside(pagePointerX, pagePointerY, x, rowY, w, ACCOUNT_ROW_H);
                if (active || hover) {
                    g.setColor(SURFACE_HOVER);
                    g.fill(new RoundRectangle2D.Float(x, rowY, w, ACCOUNT_ROW_H - 3, 7, 7));
                }
                drawAccountAvatar(g, active ? accounts.avatar() : null, x + 4, rowY + 3, 23, MUTED);
                String badge = active ? accounts.badge() : "";
                Font badgeFont = bodyFont(8F);
                int nameWidth = w - (active ? 54 : 40);
                drawText(g, ellipsize(g, body, name, nameWidth), x + 34, rowY + (active ? 12 : 18), body, TEXT, false);
                if (active) {
                    String detail = badge.isEmpty() ? tr("Активный аккаунт") : badge;
                    if (!badge.isEmpty() && !badge.equals("Plus") && accounts.plusActive()) detail += " · Plus";
                    drawText(g, ellipsize(g, badgeFont, detail, nameWidth), x + 34, rowY + 24, badgeFont, MUTED, false);
                    Path2D.Float check = new Path2D.Float();
                    check.moveTo(x + w - 15, rowY + 15);
                    check.lineTo(x + w - 12, rowY + 18);
                    check.lineTo(x + w - 7, rowY + 12);
                    g.setColor(PRIMARY);
                    g.setStroke(new BasicStroke(1F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(check);
                }
            }
            if (names.isEmpty()) drawText(g, tr("Ты ещё не вошёл в Orion"), x + 3, y + ACCOUNT_LIST_Y + 19, body, MUTED, false);
            if (names.size() > ACCOUNT_VISIBLE_ROWS) {
                float track = ACCOUNT_VISIBLE_ROWS * ACCOUNT_ROW_H - 2;
                float thumb = track * ACCOUNT_VISIBLE_ROWS / names.size();
                float scroll = (track - thumb) * accountOffset / (names.size() - ACCOUNT_VISIBLE_ROWS);
                g.setColor(BORDER);
                g.fill(new RoundRectangle2D.Float(x + w + 3, y + ACCOUNT_LIST_Y + scroll, 2, thumb, 2, 2));
            }
            int actionsY = y + accountActionsY();
            g.setColor(BORDER);
            g.draw(new Line2D.Float(x, actionsY - 3.5F, x + w, actionsY - 3.5F));
            Font actionFont = bodyFont(8.5F);
            drawConfigButton(g, x, actionsY, accountAddWidth(), ACCOUNT_BUTTON_H, "Добавить аккаунт", false, actionFont);
            if (!accounts.username().isEmpty()) drawConfigButton(g, x + w - ACCOUNT_LOGOUT_W, actionsY, ACCOUNT_LOGOUT_W, ACCOUNT_BUTTON_H, "Выйти", false, actionFont);
        }
        int siteY = y + accountSiteY();
        boolean siteHover = isInside(pagePointerX, pagePointerY, x, siteY, w, ACCOUNT_BUTTON_H);
        if (siteHover) {
            g.setColor(SURFACE_HOVER);
            g.fill(new RoundRectangle2D.Float(x, siteY, w, ACCOUNT_BUTTON_H, 5, 5));
        }
        Color linkColor = siteHover ? TEXT : MUTED;
        drawText(g, tr(accounts.username().isEmpty() ? "Открыть сайт Orion" : "Профиль на сайте"),
                x + 5, siteY + 14, bodyFont(8.5F), linkColor, false);
        drawText(g, "↗", x + w - 15, siteY + 14, bodyFont(9F), linkColor, false);
        String status = tr(accounts.busy() ? "Подключаемся к Orion…" : accountStatus);
        int split = status.length();
        while (split > 0 && textWidth(g, body, status.substring(0, split)) > w - 4) {
            split = status.lastIndexOf(' ', split - 1);
        }
        if (split > 0 && split < status.length()) {
            drawText(g, status.substring(0, split), x + 2, y + h - 18, body, MUTED, false);
            status = status.substring(split + 1);
        }
        drawText(g, ellipsize(g, body, status, w - 4), x + 2, y + h - 7, body, MUTED, false);
    }

    private void drawContent(Graphics2D g) {
        if (presetsPage) { drawPresets(g); return; }
        if (configsPage) { drawConfigs(g); return; }
        if (settingsFor != null) {
            drawSettings(g);
            return;
        }

        if (Category.VALUES[selectedCategory] == Category.SETTINGS && searchQuery.isBlank()) {
            drawGlobalSettings(g);
            return;
        }

        boolean searching = !searchQuery.isBlank();
        String title = tr(searching ? "Поиск" : paradox && paradoxAll ? "Все модули" : paradox && paradoxFavoritesOnly ? "Избранное" : Category.VALUES[selectedCategory].title);
        if (paradox) {
            drawText(g, tr("Модули") + " / " + title, L.moduleX, 18, OrionInterfaceFonts.face(1,9F), MUTED, false);
            drawText(g, title, L.moduleX, 39, OrionInterfaceFonts.face(3,15F), TEXT, false);
            Font subtitle=OrionInterfaceFonts.face(1,9F);
            drawText(g, ellipsize(g,subtitle,tr("Выберите модуль, чтобы изменить его настройки"),L.moduleW), L.moduleX, 55, subtitle, MUTED, false);
        } else if (orion3) {
            drawText(g, title, L.moduleX, 24, headingFont(11.0F), TEXT, true);
            drawText(g, tr(searching ? "Результаты поиска" : "Модули и настройки"), L.moduleX, 37, bodyFont(6.8F), MUTED, false);
        } else {
            drawText(g, title, CONTENT_X, 62, headingFont(12.5F), PRIMARY, true);
        }
        if (!paradox) drawSearch(g);

        ModuleRow[] rows = rowsForSelectedCategory();
        if (rows.length == 0) {
            String empty = tr(searching ? "Ничего не найдено" : "В этой категории пока пусто");
            drawText(g, empty, L.moduleX, L.moduleY + 18 * sc, bodyFont(8.5F * sc), MUTED, false);
            return;
        }
        moduleScroll = Math.max(0, Math.min(moduleScroll, moduleMaxScroll(rows.length)));
        int visible = Math.min(rows.length - moduleScroll, maxVisibleRows());
        for (int i = 0; i < visible; i++) {
            float hover = i < moduleHoverAnim.length ? moduleHoverAnim[i] : 0.0F;
            if (paradox) drawGridModule(g, rows[i + moduleScroll], i, hover);
            else drawModule(g, rows[i + moduleScroll], L.moduleY + i * L.moduleStep, hover);
        }
        if (rows.length > visible) drawScrollIndicator(g, paradox ? moduleScroll / GRID_COLUMNS * GRID_STEP : moduleScroll * L.moduleStep,
                paradox ? moduleMaxScroll(rows.length) / GRID_COLUMNS * GRID_STEP : (rows.length - visible) * L.moduleStep);
    }

    private int maxVisibleRows() {
        if (paradox) return GRID_COLUMNS * Math.max(1, (L.guiH - L.moduleY - 8 + 6) / GRID_STEP);
        return (L.guiH - L.moduleY - 8) / L.moduleStep;
    }

    private int moduleMaxScroll(int count) {
        int remaining = Math.max(0, count - maxVisibleRows());
        return paradox ? (remaining + GRID_COLUMNS - 1) / GRID_COLUMNS * GRID_COLUMNS : remaining;
    }

    private int gridCardWidth() { return (L.moduleW - GRID_GAP * (GRID_COLUMNS - 1)) / GRID_COLUMNS; }
    private int gridCardX(int index) { return L.moduleX + index % GRID_COLUMNS * (gridCardWidth() + GRID_GAP); }
    private int gridCardY(int index) { return L.moduleY + index / GRID_COLUMNS * GRID_STEP; }

    private void drawGridModule(Graphics2D g, ModuleRow row, int index, float hover) {
        float progress = Math.max(0F, Math.min(1F, (cardsAnimation * 600F - Math.min(index,18) * 22F) / 200F));
        progress=1F-(1F-progress)*(1F-progress)*(1F-progress);
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, progress));
        int x = gridCardX(index), y = gridCardY(index), w = gridCardWidth();
        g.setColor(mix(SURFACE, SURFACE_HOVER, hover));
        g.fill(new RoundRectangle2D.Float(x, y, w, GRID_HEIGHT, 7.5F, 7.5F));
        g.setColor(mix(BORDER, PRIMARY, hover * .35F));
        g.setStroke(new BasicStroke(.65F));
        g.draw(new RoundRectangle2D.Float(x + .5F, y + .5F, w - 1, GRID_HEIGHT - 1, 7.5F, 7.5F));
        float active = toggleAnim[row.action.ordinal()];
        g.setColor(mix(SEARCH_BG, SURFACE_HOVER, active));
        g.fill(new RoundRectangle2D.Float(x + 8, y + 7, 20, 20, 6, 6));
        drawModuleSymbol(g, row, x + 18, y + 17, mix(MUTED, PRIMARY, active));
        drawToggle(g, x + w - 26, y + 11, active);
        if (hover > .01F || paradoxFavorites.contains(row.action)) drawFavoriteStar(g, x + w - 41, y + 16,
                paradoxFavorites.contains(row.action));
        Font titleFont=OrionInterfaceFonts.face(2,11F);
        drawText(g, ellipsize(g, titleFont, tr(row.title), w - 16), x + 8, y + 37,
                titleFont, TEXT, false);
        Font font = OrionInterfaceFonts.face(1,9F);
        String remaining = tr(row.description);
        for (int line = 0; line < 2 && !remaining.isEmpty(); line++) {
            int end = remaining.length();
            while (end > 1 && textWidth(g, font, remaining.substring(0, end)) > w - 16) end--;
            if (end < remaining.length()) {
                int space = remaining.lastIndexOf(' ', end);
                if (space > end / 2) end = space;
            }
            String text = remaining.substring(0, end);
            if (line == 1 && end < remaining.length()) text = ellipsize(g, font, remaining, w - 16);
            drawText(g, text, x + 8, y + 51 + line * 12, font, MUTED, false);
            remaining = remaining.substring(end).stripLeading();
        }
        g.setComposite(old);
    }

    private static final int PROFILE_TOP = 64;
    private static final int PROFILE_GAP = 10;
    private static final int PAGE_BUTTON_H = 24;

    private int presetCardWidth() { return (L.moduleW - PROFILE_GAP) * 46 / 100; }
    private int presetCardHeight() { return (L.guiH - PROFILE_TOP - 16 - 24) / 4; }
    private int presetCardX(int index) { return L.moduleX; }
    private int presetCardY(int index) { return PROFILE_TOP + index * (presetCardHeight() + 8); }
    private int presetInfoX() { return L.moduleX + presetCardWidth() + PROFILE_GAP; }
    private int presetInfoWidth() { return L.moduleW - presetCardWidth() - PROFILE_GAP; }
    private int presetButtonY() { return L.guiH - 78; }
    private int presetApplyX() { return presetInfoX() + 12; }
    private int presetApplyWidth() { return presetInfoWidth() - 24; }
    private int presetRestoreX() { return presetApplyX(); }
    private int presetRestoreWidth() { return presetApplyWidth(); }

    private boolean presetsClick(int x, int y) {
        var presets = configManager.getVisualPresets();
        Profile[] profiles = Profile.values();
        for (int i = 0; i < profiles.length; i++) {
            if (isInside(x, y, presetCardX(i), presetCardY(i), presetCardWidth(), presetCardHeight())) {
                selectedPreset = i;
                presets.select(profiles[i]);
                lastFingerprint = null;
                return true;
            }
        }
        if (isInside(x, y, presetApplyX(), presetButtonY(), presetApplyWidth(), PAGE_BUTTON_H)) {
            presets.apply(profiles[selectedPreset], Minecraft.getInstance());
            lastFingerprint = null;
            return true;
        }
        if (presets.canRestore() && isInside(x, y, presetRestoreX(), presetButtonY() + 32, presetRestoreWidth(), PAGE_BUTTON_H)) {
            presets.restore(Minecraft.getInstance());
            selectedPreset = presets.selectedProfile().ordinal();
            lastFingerprint = null;
            return true;
        }
        return false;
    }

    private void drawPresets(Graphics2D g) {
        var presets = configManager.getVisualPresets();
        Profile[] profiles = Profile.values();
        Font title = OrionInterfaceFonts.face(3, 15);
        Font body = OrionInterfaceFonts.face(0, 8);
        Font name = OrionInterfaceFonts.face(2, 10);
        drawText(g, tr("Пресеты"), L.moduleX, 29, title, TEXT, false);
        drawText(g, tr("Выбери характер изображения"), L.moduleX, 45, body, MUTED, false);
        for (int i = 0; i < profiles.length; i++) {
            int x = presetCardX(i), y = presetCardY(i), w = presetCardWidth(), h = presetCardHeight();
            boolean selected = i == selectedPreset;
            pageSurface(g, x, y, w, h, selected);
            if (selected) {
                g.setColor(PRIMARY);
                g.fill(new RoundRectangle2D.Float(x + 1, y + 10, 2, h - 20, 2, 2));
            }
            g.setColor(SEARCH_BG);
            g.fill(new RoundRectangle2D.Float(x + 12, y + (h - 25) / 2F - 6, 25, 25, 7, 7));
            Icon icon = switch (profiles[i]) {
                case QUALITY -> Icon.EYE;
                case PERFORMANCE -> Icon.MOVEMENT;
                case BALANCE -> Icon.GRID;
                case BEAUTY -> Icon.SUN;
            };
            drawIcon(g, icon, x + 24.5F, y + h / 2F - 6, selected ? PRIMARY : MUTED, 1.1F, .38F);
            drawText(g, ellipsize(g, name, tr(profiles[i].title), w - 59), x + 47, y + h / 2F - 14, name, TEXT, false);
            drawPageParagraph(g, tr(profiles[i].description), x + 47, y + h / 2F, w - 59, body, MUTED, 2);
            String detail = profiles[i].renderDistance + " " + tr("чанков") + " · "
                    + tr(presets.wasApplied(profiles[i]) ? "Применён" : profiles[i] == Profile.PERFORMANCE ? "Минимум частиц"
                    : profiles[i] == Profile.BALANCE ? "Меньше частиц" : "Все частицы");
            drawText(g, ellipsize(g, body, detail, w - 59), x + 47, y + h - 11, body,
                    presets.wasApplied(profiles[i]) ? PRIMARY : MUTED, false);
        }
        Profile chosen = profiles[selectedPreset];
        int x = presetInfoX(), w = presetInfoWidth();
        pageSurface(g, x, PROFILE_TOP, w, L.guiH - PROFILE_TOP - 16, false);
        drawText(g, tr("Выбранный режим"), x + 14, 84, body, MUTED, false);
        drawText(g, ellipsize(g, title, tr(chosen.title), w - 28), x + 14, 107, title, TEXT, false);
        drawPageParagraph(g, tr(chosen.detail), x + 14, 126, w - 28, body, MUTED, 2);
        pageMetric(g, x + 14, 169, w - 28, "Прорисовка", chosen.renderDistance + " " + tr("чанков"));
        pageMetric(g, x + 14, 194, w - 28, "Симуляция", chosen.simulationDistance + " " + tr("чанков"));
        pageMetric(g, x + 14, 219, w - 28, "Частицы", tr(chosen == Profile.PERFORMANCE ? "Минимум" : chosen == Profile.BALANCE ? "Уменьшены" : "Все"));
        pageMetric(g, x + 14, 244, w - 28, "Нагрузка", tr(chosen == Profile.PERFORMANCE ? "Низкая" : chosen == Profile.BALANCE ? "Умеренная" : "Высокая"));
        drawPageParagraph(g, tr(chosen.effectsSummary()), x + 14, 278, w - 28, body, MUTED, 3);
        drawConfigButton(g, presetApplyX(), presetButtonY(), presetApplyWidth(), PAGE_BUTTON_H,
                presets.wasApplied(chosen) ? "Применить заново" : "Применить пресет", true, body);
        if (presets.canRestore()) drawConfigButton(g, presetRestoreX(), presetButtonY() + 32,
                presetRestoreWidth(), PAGE_BUTTON_H, "Вернуть свои настройки", false, body);
    }

    private static final int CONFIG_ROWS_Y = 112;
    private int configInfoWidth() { return Math.min(192, L.moduleW / 2); }
    private int configListWidth() { return L.moduleW - configInfoWidth() - 12; }
    private int configInfoX() { return L.moduleX + configListWidth() + 12; }
    private int configActionX() { return configInfoX() + 12; }
    private int configActionWidth() { return configInfoWidth() - 24; }
    private int configFooterY() { return L.guiH - 31; }
    private int configRowHeight() { return Math.min(48, (L.guiH - CONFIG_ROWS_Y - 48) / OrionConfigLibrary.PAGE_SIZE); }
    private int configMainY() {
        int actionsHeight = configEditor != 0 ? 54 : configsLocal ? 114 : configsPublished ? 84 : 24;
        return L.guiH - 28 - actionsHeight;
    }
    private int configHalfWidth() { return (configActionWidth() - 6) / 2; }

    private void openConfigsPage() {
        if (configBusy) return;
        closeSettings();
        configsPage = true;
        paradoxAll = paradoxFavoritesOnly = false;
        selectedCategory = moduleScroll = 0;
        searchQuery = "";
        if (localConfigs.isEmpty()) loadLocalConfigs();
        selectConfigTab(0);
        if (catalogCache[1] == null) loadCatalog(true, 0);
    }

    private void selectConfigTab(int tab) {
        configsLocal = tab == 1;
        configsPublished = tab == 2;
        configOffset = configEditor = 0;
        configSelected = -1;
        configTitleFocused = configDeleteConfirm = false;
        configStatus = "";
        if (configsLocal) {
            configSelected = localConfigs.isEmpty() ? -1 : 0;
            if (localConfigs.isEmpty()) loadLocalConfigs();
        } else {
            int index = configsPublished ? 1 : 0;
            CatalogSnapshot cached = catalogCache[index];
            if (cached == null) {
                catalogEntries = List.of();
                catalogMore = false;
                catalogTotal = 0;
            } else showCatalog(cached);
            if (cached == null || catalogStale[index] || System.nanoTime() - cached.fetchedAt() >= CATALOG_REFRESH_NANOS) {
                loadCatalog(configsPublished, configOffset);
            }
        }
        lastFingerprint = null;
    }

    private void loadLocalConfigs() {
        if (localConfigsPending) return;
        localConfigsPending = true;
        configManager.getConfigLibrary().local(configManager.exportProfile()).whenComplete((profiles, failure) ->
                Minecraft.getInstance().execute(() -> {
                    localConfigsPending = false;
                    if (failure == null) {
                        localConfigs = profiles;
                        if (configsPage && configsLocal && configEditor == 0) acceptLocalConfigs(profiles);
                    } else if (configsPage && configsLocal) reportConfigFailure(failure);
                }));
    }

    private void showCatalog(CatalogSnapshot snapshot) {
        var selected = selectedConfig();
        configOffset = snapshot.offset();
        catalogEntries = snapshot.page().entries();
        catalogMore = snapshot.page().more();
        catalogTotal = snapshot.page().total();
        configSelected = catalogEntries.isEmpty() ? -1 : 0;
        if (selected != null) {
            for (int i = 0; i < catalogEntries.size(); i++) {
                if (catalogEntries.get(i).id() == selected.id()) {
                    configSelected = i;
                    break;
                }
            }
        }
        var current = selectedConfig();
        if (selected == null || current == null || selected.id() != current.id() || selected.version() != current.version()) {
            configDeleteConfirm = false;
        }
        lastFingerprint = null;
    }

    private void loadCatalog(int offset) {
        loadCatalog(configsPublished, offset);
    }

    private void loadCatalog(boolean published, int offset) {
        int index = published ? 1 : 0;
        if (catalogPending[index]) return;
        catalogPending[index] = true;
        var user = Minecraft.getInstance().getUser();
        configManager.getConfigLibrary().catalog(offset,
                published ? user.getProfileId() : null, published ? user.getName() : null).whenComplete((page, failure) -> {
            Minecraft.getInstance().execute(() -> {
                catalogPending[index] = false;
                boolean visible = configsPage && !configsLocal && configsPublished == published;
                if (failure == null) {
                    catalogCache[index] = new CatalogSnapshot(offset, page, System.nanoTime());
                    catalogStale[index] = false;
                    if (visible && !configBusy && configEditor == 0) showCatalog(catalogCache[index]);
                } else if (visible) reportConfigFailure(failure);
            });
        });
    }

    private void reportConfigFailure(Throwable failure) {
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        configStatus = cause instanceof IllegalStateException ? cause.getMessage() : "Не удалось выполнить действие";
        LOGGER.warn("Операция каталога конфигов завершилась ошибкой", cause);
        lastFingerprint = null;
    }

    private void invalidateCatalogs() {
        catalogStale[0] = catalogStale[1] = true;
    }

    private <T> void runConfigTask(java.util.concurrent.CompletableFuture<T> task, Consumer<T> success) {
        configBusy = true;
        configStatus = "";
        configDeleteConfirm = false;
        lastFingerprint = null;
        task.whenComplete((result, failure) -> Minecraft.getInstance().execute(() -> {
            configBusy = false;
            lastFingerprint = null;
            if (failure == null) success.accept(result);
            else reportConfigFailure(failure);
        }));
    }

    private OrionConfigLibrary.Entry selectedConfig() {
        if (configSelected < 0 || configSelected >= configRowCount()) return null;
        return configsLocal ? localConfigs.get(configOffset + configSelected).entry() : catalogEntries.get(configSelected);
    }

    private void beginConfigEditor(int mode) {
        configEditor = mode;
        configDeleteConfirm = false;
        var entry = selectedConfig();
        configTitle = mode == 2 || mode == 3 ? entry.name() : "";
        configTitleFocused = true;
        textCaret = configTitle.length();
        textAnchor = 0;
        configStatus = "";
        lastFingerprint = null;
    }

    private void acceptLocalConfigs(List<OrionConfigLibrary.Profile> profiles) {
        localConfigs = profiles;
        configOffset = Math.max(0, Math.min(configOffset, Math.max(0, (profiles.size() - 1) / OrionConfigLibrary.PAGE_SIZE * OrionConfigLibrary.PAGE_SIZE)));
        configSelected = profiles.isEmpty() ? -1 : Math.max(0, Math.min(configSelected, configRowCount() - 1));
        configEditor = 0;
        lastFingerprint = null;
    }

    private void saveConfigEditor() {
        var library = configManager.getConfigLibrary();
        var user = minecraft.getUser();
        var selected = selectedConfig();
        String title = configTitle.strip();
        if (title.isEmpty()) { configStatus = "Введи название конфига"; return; }
        if (configEditor == 1) {
            runConfigTask(library.saveLocal(title, configManager.exportProfile()), profiles -> {
                configsLocal = true; configsPublished = false; configOffset = 0; configSelected = 1;
                acceptLocalConfigs(profiles); configStatus = "Конфиг сохранён";
            });
        } else if (configEditor == 2 && selected != null && configsLocal) {
            int id = selected.id();
            runConfigTask(library.renameLocal(id, title), profiles -> {
                acceptLocalConfigs(profiles); configOffset = 0; configSelected = 1;
                configStatus = "Конфиг переименован";
            });
        } else if (configEditor == 2 && selected != null && configsPublished) {
            var snapshot = configManager.exportProfile();
            runConfigTask(library.editPublication(selected, title, null, user.getProfileId(), user.getName())
                    .thenCompose(profile -> library.local(snapshot)), profiles -> {
                invalidateCatalogs();
                localConfigs = profiles; configEditor = 0; loadCatalog(configOffset);
                configStatus = "Публикация обновлена";
            });
        } else {
            var settings = configEditor == 3 && selected != null && configsLocal
                    ? localConfigs.get(configOffset + configSelected).settings() : configManager.exportProfile();
            runConfigTask(library.publish(title, user.getProfileId(), user.getName(), settings), profiles -> {
                invalidateCatalogs();
                configsLocal = true; configsPublished = false; configOffset = 0; configSelected = 1;
                acceptLocalConfigs(profiles); configTitle = ""; configStatus = "Конфиг опубликован";
            });
        }
        configTitleFocused = false;
    }

    private boolean configsClick(int x, int y) {
        if (configBusy) return true;
        for (int tab = 0; tab < 3; tab++) {
            if (isInside(x, y, L.moduleX + tab * 99, 48, 93, 24)) {
                selectConfigTab(tab);
                return true;
            }
        }
        if (isInside(x, y, L.moduleX + L.moduleW - 76, 48, 76, 24)) {
            configStatus = "";
            if (configsLocal) loadLocalConfigs();
            else loadCatalog(configOffset);
            return true;
        }
        if (isInside(x, y, L.moduleX + L.moduleW - 166, 80, 80, 22)) { beginConfigEditor(1); return true; }
        if (isInside(x, y, L.moduleX + L.moduleW - 80, 80, 80, 22)) { beginConfigEditor(4); return true; }
        int actionX = configActionX(), w = configActionWidth(), mainY = configMainY();
        if (configEditor != 0) {
            if (isInside(x, y, actionX, CONFIG_ROWS_Y + 68, w, 27)) {
                configTitleFocused = true; textCaret = configTitle.length(); textAnchor = 0; lastFingerprint = null; return true;
            }
            if (isInside(x, y, actionX, mainY, w, PAGE_BUTTON_H)) { saveConfigEditor(); lastFingerprint = null; return true; }
            if (isInside(x, y, actionX, mainY + 30, w, PAGE_BUTTON_H)) {
                configEditor = 0; configTitle = ""; lastFingerprint = null; return true;
            }
        }
        for (int row = 0; row < configRowCount(); row++) {
            if (isInside(x, y, L.moduleX, CONFIG_ROWS_Y + row * configRowHeight(), configListWidth(), configRowHeight() - 5)) {
                configSelected = row; configEditor = 0; configDeleteConfirm = false; configStatus = ""; lastFingerprint = null; return true;
            }
        }
        if (configOffset > 0 && isInside(x, y, L.moduleX, configFooterY(), 62, 20)) {
            configEditor = 0; configDeleteConfirm = false;
            if (configsLocal) { configOffset -= OrionConfigLibrary.PAGE_SIZE; configSelected = 0; }
            else loadCatalog(configOffset - OrionConfigLibrary.PAGE_SIZE);
            lastFingerprint = null; return true;
        }
        if (configHasNext() && isInside(x, y, L.moduleX + configListWidth() - 62, configFooterY(), 62, 20)) {
            configEditor = 0; configDeleteConfirm = false;
            if (configsLocal) { configOffset += OrionConfigLibrary.PAGE_SIZE; configSelected = 0; }
            else loadCatalog(configOffset + OrionConfigLibrary.PAGE_SIZE);
            lastFingerprint = null; return true;
        }
        var entry = selectedConfig();
        if (entry == null || configEditor != 0) return true;
        var library = configManager.getConfigLibrary();
        var user = minecraft.getUser();
        if (isInside(x, y, actionX, mainY, w, PAGE_BUTTON_H)) {
            configDeleteConfirm = false;
            if (configsLocal) {
                try {
                    configManager.applyProfile(localConfigs.get(configOffset + configSelected).settings());
                    ru.orion.client.effect.OrionBadgePresence.requestRefresh();
                    configStatus = "Конфиг применён";
                } catch (java.io.IOException | RuntimeException exception) {
                    configStatus = "Не удалось применить конфиг";
                    LOGGER.warn("Не удалось применить конфиг", exception);
                }
            } else {
                runConfigTask(library.download(entry.id(), user.getProfileId(), user.getName()), profiles -> {
                    localConfigs = profiles;
                    var updated = profiles.stream().filter(profile -> profile.entry().id() == entry.id()).findFirst().orElseThrow().entry();
                    var entries = new ArrayList<>(catalogEntries); entries.set(configSelected, updated); catalogEntries = List.copyOf(entries);
                    int index = configsPublished ? 1 : 0;
                    var cached = catalogCache[index];
                    if (cached != null && cached.offset() == configOffset) {
                        catalogCache[index] = new CatalogSnapshot(configOffset,
                                new OrionConfigLibrary.Page(catalogEntries, catalogMore, catalogTotal), cached.fetchedAt());
                    }
                    configStatus = "Конфиг добавлен в «Мои конфиги»";
                });
            }
            lastFingerprint = null; return true;
        }
        if ((configsLocal && entry.id() != 0) || configsPublished) {
            if (isInside(x, y, actionX, mainY + 30, configHalfWidth(), 24)) { beginConfigEditor(2); return true; }
            if (isInside(x, y, actionX + configHalfWidth() + 6, mainY + 30, configHalfWidth(), 24)) {
                if (!configDeleteConfirm) { configDeleteConfirm = true; lastFingerprint = null; return true; }
                if (configsLocal) runConfigTask(library.removeLocal(entry.id()), profiles -> {
                    acceptLocalConfigs(profiles); configStatus = "Локальная копия удалена";
                });
                else runConfigTask(library.removePublication(entry, user.getProfileId(), user.getName()), ignored -> {
                    invalidateCatalogs();
                    loadCatalog(Math.max(0, configOffset - (catalogEntries.size() == 1 ? OrionConfigLibrary.PAGE_SIZE : 0)));
                    configStatus = "Публикация удалена";
                });
                return true;
            }
            if (isInside(x, y, actionX, mainY + 60, w, 24)) {
                var settings = configManager.exportProfile();
                if (configsLocal) runConfigTask(library.updateLocal(entry.id(), settings), this::acceptLocalConfigs);
                else runConfigTask(library.editPublication(entry, entry.name(), settings, user.getProfileId(), user.getName())
                        .thenCompose(profile -> library.local(settings)), profiles -> {
                    invalidateCatalogs();
                    localConfigs = profiles; loadCatalog(configOffset); configStatus = "Публикация обновлена";
                });
                return true;
            }
        }
        if (configsLocal && isInside(x, y, actionX, mainY + 90, w, 24)) { beginConfigEditor(3); return true; }
        return true;
    }

    private boolean configHasNext() {
        return configsLocal ? configOffset + OrionConfigLibrary.PAGE_SIZE < localConfigs.size() : catalogMore;
    }

    private int configRowCount() {
        return configsLocal ? Math.max(0, Math.min(OrionConfigLibrary.PAGE_SIZE, localConfigs.size() - configOffset)) : catalogEntries.size();
    }

    private void drawConfigs(Graphics2D g) {
        int x = L.moduleX;
        Font title = OrionInterfaceFonts.face(3, 15F), body = OrionInterfaceFonts.face(0, 8F), name = OrionInterfaceFonts.face(2, 9F);
        drawText(g, tr("Конфиги"), x, 29, title, TEXT, false);
        String[] tabs = {"Каталог", "Мои конфиги", "Публикации"};
        int activeTab = configsLocal ? 1 : configsPublished ? 2 : 0;
        for (int tab = 0; tab < 3; tab++) drawConfigButton(g, x + tab * 99, 48, 93, 24, tabs[tab], activeTab == tab, body);
        drawConfigButton(g, x + L.moduleW - 76, 48, 76, 24, "Обновить", false, body);
        int total = configsLocal ? localConfigs.size() : catalogTotal;
        drawText(g, tr(configsLocal ? "В библиотеке" : configsPublished ? "Опубликовано" : "В каталоге") + " · " + total, x, 94, body, MUTED, false);
        drawConfigButton(g, x + L.moduleW - 166, 80, 80, 22, "Сохранить", false, body);
        drawConfigButton(g, x + L.moduleW - 80, 80, 80, 22, "Опубликовать", true, body);
        int h = configRowHeight();
        for (int row = 0; row < configRowCount(); row++) {
            var entry = configsLocal ? localConfigs.get(configOffset + row).entry() : catalogEntries.get(row);
            int y = CONFIG_ROWS_Y + row * h;
            boolean selected = configSelected == row;
            pageSurface(g, x, y, configListWidth(), h - 5, selected);
            g.setColor(selected ? PRIMARY : MUTED);
            drawIcon(g, entry.id() <= 0 ? Icon.FOLDER : Icon.MONITOR, x + 17, y + 17, g.getColor(), .9F, .29F);
            String label = entry.id() == 0 ? tr(entry.name()) : entry.name();
            drawText(g, ellipsize(g, name, label, configListWidth() - 46), x + 33, y + 17, name, TEXT, false);
            String meta = entry.id() <= 0 ? tr(entry.id() == 0 ? "Исходные настройки" : "Локальный конфиг")
                    : entry.author() + " · " + entry.downloads() + " " + tr("скачиваний");
            drawText(g, ellipsize(g, body, meta, configListWidth() - 46), x + 33, y + 31, body, MUTED, false);
        }
        if (configRowCount() == 0) {
            pageSurface(g, x, CONFIG_ROWS_Y, configListWidth(), 100, false);
            drawIcon(g, Icon.FOLDER, x + 20, CONFIG_ROWS_Y + 24, MUTED, 1F, .4F);
            boolean loaded = configsLocal ? !localConfigs.isEmpty() : catalogCache[configsPublished ? 1 : 0] != null;
            drawText(g, tr(!loaded ? "Список ещё не загружен" : configsLocal ? "Нет сохранённых конфигов" : configsPublished ? "Пока нет публикаций" : "Каталог пока пуст"),
                    x + 14, CONFIG_ROWS_Y + 50, name, TEXT, false);
            if (loaded) drawPageParagraph(g, tr("Сохрани текущие настройки или выбери конфиг в каталоге"),
                    x + 14, CONFIG_ROWS_Y + 68, configListWidth() - 28, body, MUTED, 2);
        }
        int panelX = configInfoX(), panelW = configInfoWidth(), actionX = configActionX(), actionW = configActionWidth();
        pageSurface(g, panelX, CONFIG_ROWS_Y, panelW, L.guiH - CONFIG_ROWS_Y - 16, false);
        var selected = selectedConfig();
        if (configEditor != 0) {
            String heading = configEditor == 1 ? "Новый конфиг" : configEditor == 2 ? "Переименовать" : "Публикация конфига";
            drawText(g, tr(heading), actionX, CONFIG_ROWS_Y + 23, name, TEXT, false);
            drawPageParagraph(g, tr(configEditor <= 2 ? "Название поможет найти конфиг в списке" : "Этот конфиг увидят все пользователи Orion"),
                    actionX, CONFIG_ROWS_Y + 42, actionW, body, MUTED, 2);
            drawConfigNameInput(g, actionX, CONFIG_ROWS_Y + 68, actionW, body);
            drawConfigButton(g, actionX, configMainY(), actionW, PAGE_BUTTON_H, configEditor <= 2 ? "Сохранить" : "Опубликовать", true, body);
            drawConfigButton(g, actionX, configMainY() + 30, actionW, PAGE_BUTTON_H, "Отмена", false, body);
        } else if (selected != null) {
            drawPageParagraph(g, selected.id() == 0 ? tr(selected.name()) : selected.name(),
                    actionX, CONFIG_ROWS_Y + 22, actionW, name, TEXT, 2);
            drawText(g, ellipsize(g, body, selected.author().isEmpty() ? tr("Локальная библиотека") : selected.author(), actionW),
                    actionX, CONFIG_ROWS_Y + 56, body, MUTED, false);
            pageMetric(g, actionX, CONFIG_ROWS_Y + 80, actionW, "Скачивания", selected.id() > 0 ? Long.toString(selected.downloads()) : "—");
            pageMetric(g, actionX, CONFIG_ROWS_Y + 103, actionW, "Обновлён", configDate(selected.updatedAt()));
            pageMetric(g, actionX, CONFIG_ROWS_Y + 126, actionW, "Создан", configDate(selected.createdAt()));
            if (selected.id() > 0) pageMetric(g, actionX, CONFIG_ROWS_Y + 149, actionW, "Версия", Integer.toString(selected.version()));
            boolean downloaded = selected.id() > 0 && localConfigs.stream().anyMatch(profile -> profile.entry().id() == selected.id());
            drawConfigButton(g, actionX, configMainY(), actionW, PAGE_BUTTON_H,
                    configsLocal ? "Применить" : downloaded ? "Обновить копию" : "Скачать", true, body);
            if ((configsLocal && selected.id() != 0) || configsPublished) {
                drawConfigButton(g, actionX, configMainY() + 30, configHalfWidth(), 24, "Название", false, body);
                drawConfigButton(g, actionX + configHalfWidth() + 6, configMainY() + 30, configHalfWidth(), 24,
                        configDeleteConfirm ? "Подтвердить" : "Удалить", configDeleteConfirm, body);
                drawConfigButton(g, actionX, configMainY() + 60, actionW, 24, "Обновить текущими", false, body);
            }
            if (configsLocal) drawConfigButton(g, actionX, configMainY() + 90, actionW, 24, "Опубликовать копию", false, body);
            if (!configsLocal && !configsPublished) {
                drawPageParagraph(g, tr("Скачай конфиг в библиотеку, чтобы применить его к клиенту"),
                        actionX, configMainY() - 40, actionW, body, MUTED, 2);
            }
        } else drawPageParagraph(g, tr("Выбери конфиг, чтобы посмотреть подробности"),
                actionX, CONFIG_ROWS_Y + 30, actionW, body, MUTED, 3);
        if (configOffset > 0) drawConfigButton(g, x, configFooterY(), 62, 20, "Назад", false, body);
        if (configHasNext()) drawConfigButton(g, x + configListWidth() - 62, configFooterY(), 62, 20, "Дальше", false, body);
        String status = tr(configStatus);
        drawText(g, ellipsize(g, body, status, configListWidth()), x, configFooterY() - 8, body, MUTED, false);
    }

    private static String configDate(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0) return "—";
        return java.time.format.DateTimeFormatter.ofPattern("dd.MM.yy").withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.ofEpochSecond((long) seconds));
    }

    private void drawConfigNameInput(Graphics2D g, int x, int y, int w, Font font) {
        drawPageInput(g, x, y, w, font, configTitle, "Название конфига", configTitleFocused);
    }

    private void drawPageInput(Graphics2D g, int x, int y, int w, Font font, String value, String placeholder, boolean focused) {
        g.setColor(SEARCH_BG);
        g.fill(new RoundRectangle2D.Float(x, y, w, 27, 5, 5));
        if (focused) {
            g.setColor(PRIMARY); g.setStroke(new BasicStroke(.8F));
            g.draw(new RoundRectangle2D.Float(x + .5F, y + .5F, w - 1, 26, 5, 5));
        }
        int caret = Math.min(textCaret, value.length());
        int anchor = Math.min(textAnchor, value.length());
        float caretWidth = textWidth(g, font, value.substring(0, caret));
        float offset = focused ? Math.max(0, caretWidth - w + 16) : 0;
        Shape oldClip = g.getClip();
        g.clip(new Rectangle2D.Float(x + 5, y + 2, w - 10, 23));
        if (focused && caret != anchor) {
            float start = textWidth(g, font, value.substring(0, Math.min(caret, anchor)));
            float end = textWidth(g, font, value.substring(0, Math.max(caret, anchor)));
            g.setColor(withAlpha(PRIMARY, 70));
            g.fill(new Rectangle2D.Float(x + 7 + start - offset, y + 5, end - start, 16));
        }
        drawText(g, value.isEmpty() ? tr(placeholder) : value, x + 7 - offset, y + 18, font,
                value.isEmpty() ? MUTED : TEXT, false);
        if (focused && System.currentTimeMillis() / 500L % 2L == 0) {
            g.setColor(TEXT); g.draw(new Line2D.Float(x + 7 + caretWidth - offset, y + 6, x + 7 + caretWidth - offset, y + 21));
        }
        g.setClip(oldClip);
    }

    private void pageSurface(Graphics2D g, int x, int y, int w, int h, boolean selected) {
        g.setColor(selected ? mix(SURFACE, PRIMARY, .045F) : SURFACE);
        g.fill(new RoundRectangle2D.Float(x, y, w, h, 8, 8));
        if (selected) {
            g.setColor(withAlpha(PRIMARY, 150)); g.setStroke(new BasicStroke(.7F));
            g.draw(new RoundRectangle2D.Float(x + .5F, y + .5F, w - 1, h - 1, 8, 8));
        }
    }

    private void pageMetric(Graphics2D g, int x, int baseline, int width, String label, String value) {
        Font font = OrionInterfaceFonts.face(1, 8F);
        drawText(g, tr(label), x, baseline, font, MUTED, false);
        String displayed = ellipsize(g, font, value, width / 2F);
        drawText(g, displayed, x + width - textWidth(g, font, displayed), baseline, font, TEXT, false);
        g.setColor(withAlpha(BORDER, 90)); g.setStroke(new BasicStroke(.6F));
        g.draw(new Line2D.Float(x, baseline + 8, x + width, baseline + 8));
    }

    private void drawPageParagraph(Graphics2D g, String value, float x, float baseline, float width, Font font, Color color, int lines) {
        String remaining = value;
        for (int line = 0; line < lines && !remaining.isEmpty(); line++) {
            int end = remaining.length();
            while (end > 1 && textWidth(g, font, remaining.substring(0, end)) > width) end--;
            if (end < remaining.length()) {
                int space = remaining.lastIndexOf(' ', end);
                if (space > end / 2) end = space;
            }
            String part = line == lines - 1 ? ellipsize(g, font, remaining, width) : remaining.substring(0, end);
            drawText(g, part, x, baseline + line * 12, font, color, false);
            remaining = remaining.substring(end).stripLeading();
        }
    }

    private void drawConfigButton(Graphics2D g, int x, int y, int w, int h, String label, boolean accent, Font font) {
        boolean hover = isInside(pagePointerX, pagePointerY, x, y, w, h);
        g.setColor(accent ? mix(SURFACE, PRIMARY, hover ? .32F : .19F) : hover ? SURFACE_HOVER : SEARCH_BG);
        g.fill(new RoundRectangle2D.Float(x, y, w, h, 5, 5));
        drawCenteredText(g, ellipsize(g, font, tr(label), w - 10), x + w / 2F, y + h / 2F + 3F,
                font, TEXT);
    }

    private int profilePageHover(int x, int y) {
        if (presetsPage) {
            for (int row = 0; row < 4; row++) if (isInside(x, y, presetCardX(row), presetCardY(row), presetCardWidth(), presetCardHeight())) return row;
            if (isInside(x, y, presetApplyX(), presetButtonY(), presetApplyWidth(), PAGE_BUTTON_H)) return 10;
            if (isInside(x, y, presetRestoreX(), presetButtonY() + 32, presetRestoreWidth(), PAGE_BUTTON_H)) return 11;
        }
        if (configsPage) {
            for (int tab = 0; tab < 3; tab++) if (isInside(x, y, L.moduleX + tab * 99, 48, 93, 24)) return 20 + tab;
            if (isInside(x, y, L.moduleX + L.moduleW - 76, 48, 76, 24)) return 23;
            if (isInside(x, y, L.moduleX + L.moduleW - 166, 80, 80, 22)) return 24;
            if (isInside(x, y, L.moduleX + L.moduleW - 80, 80, 80, 22)) return 25;
            if (isInside(x, y, configActionX(), configMainY(), configActionWidth(), PAGE_BUTTON_H)) return 26;
            if (configEditor != 0) {
                if (isInside(x, y, configActionX(), configMainY() + 30, configActionWidth(), PAGE_BUTTON_H)) return 27;
            } else {
                for (int i = 0; i < 2; i++) if (isInside(x, y, configActionX() + i * (configHalfWidth() + 6),
                        configMainY() + 30, configHalfWidth(), 24)) return 28 + i;
                for (int i = 0; i < 2; i++) if (isInside(x, y, configActionX(), configMainY() + 60 + i * 30,
                        configActionWidth(), 24)) return 30 + i;
            }
            if (isInside(x, y, L.moduleX, configFooterY(), 62, 20)) return 32;
            if (isInside(x, y, L.moduleX + configListWidth() - 62, configFooterY(), 62, 20)) return 33;
        }
        return -1;
    }


    private int gearOffsetY() {
        return (L.moduleH - L.gearSize) / 2;
    }

    private int arrowOffsetY() {
        return (L.moduleH - L.arrowSize) / 2;
    }

    /** Тумблер: в ORION3 прижат к правому краю карточки, в классике левее шестерёнки. */
    private int toggleX() {
        return orion3 ? L.moduleX + L.moduleW - 8 - L.toggleW : L.moduleX + L.moduleW - 85;
    }

    private void drawGlobalSettings(Graphics2D g) {
        if (orion3) {
            drawText(g, tr("Настройки"), L.moduleX, 28, headingFont(9.0F), PRIMARY, true);
            drawText(g, tr("Настройки интерфейса"), L.moduleX, 40, bodyFont(7.0F), MUTED, false);
        } else {
            drawText(g, "Settings", CONTENT_X, 62, headingFont(12.5F), PRIMARY, true);
            drawText(g, tr("Настройки клиента"), CONTENT_X, 73, bodyFont(7.0F), MUTED, false);
        }
        drawSettingRows(g, globalSettings);
    }

    private void drawSettingRows(Graphics2D g, Setting[] rows) {
        if (usesSettingsLayer() && !drawingSettingsLayer) {
            int maximum = settingsMaxScroll(rows);
            if (maximum > 0) drawScrollIndicator(g, 0, maximum);
            return;
        }
        Shape oldClip = g.getClip();
        g.clip(new Rectangle2D.Float(L.moduleX, L.moduleY, L.moduleW, drawingSettingsLayer ? settingsAtlasHeight : L.guiH - L.moduleY - 8));
        if (paradox && settingsFor != null) drawParadoxGroups(g, rows);
        for (int i = 0; i < rows.length; i++) {
            int y = settingRowY(i);
            int rowBottom = y + L.moduleH + (i == expandedColor ? pickerPanelHeight() + L.moduleStep - L.moduleH : 0);
            int bottom = L.moduleY + (drawingSettingsLayer ? settingsAtlasHeight : L.guiH - L.moduleY - 8);
            if (rowBottom < L.moduleY || y > bottom) continue;
            drawSettingRow(g, rows[i], y);
            if (i == expandedColor && rows[i] instanceof ColorSetting color) {
                drawColorPicker(g, color, y + L.moduleH + (L.moduleStep - L.moduleH));
            }
        }
        g.setClip(oldClip);
        int maxScroll = settingsMaxScroll(rows);
        if (maxScroll > 0 && !drawingSettingsLayer) drawScrollIndicator(g, currentSettingsScroll(), maxScroll);
    }

    private int settingsMaxScroll(Setting[] rows) {
        int height = rows.length == 0 ? 0 : (rows.length - 1) * L.moduleStep + L.moduleH;
        if (paradox && settingsFor != null && rows.length > 0) {
            height += Math.max(0, paradoxGroupStarts(rows).length - 1) * 24;
        }
        if (expandedColor >= 0) height += pickerPanelHeight() + (L.moduleStep - L.moduleH);
        return Math.max(0, height - (L.guiH - L.moduleY - 8));
    }

    private boolean scrollBarHit(int x, int y) {
        return isInside(x, y, L.moduleX + L.moduleW, L.moduleY, 10, L.guiH - L.moduleY - 10);
    }

    private void applyScrollBar(int y, boolean settings) {
        int moduleMaximum = moduleMaxScroll(rowsForSelectedCategory().length);
        int maximum = settings ? settingsMaxScroll(settingsRows)
                : (paradox ? moduleMaximum / GRID_COLUMNS * GRID_STEP : moduleMaximum * L.moduleStep);
        float trackHeight = L.guiH - L.moduleY - 10;
        float thumbHeight = Math.max(16, trackHeight * trackHeight / (trackHeight + maximum));
        float available = trackHeight - thumbHeight;
        float progress = available <= 0 ? 0 : Math.max(0, Math.min(1, (y - L.moduleY - thumbHeight / 2) / available));
        if (settings) { settingsScroll = Math.round(progress * maximum); if (paradox) scrollAnimation = settingsScroll; }
        else moduleScroll = paradox ? Math.min(moduleMaximum,Math.round(progress * moduleMaximum / GRID_COLUMNS) * GRID_COLUMNS)
                : Math.round(progress * maximum / L.moduleStep);
        lastFingerprint = null;
    }

    private void drawScrollIndicator(Graphics2D g, int scroll, int maximum) {
        float x = L.moduleX + L.moduleW + 3;
        float y = L.moduleY;
        float h = L.guiH - L.moduleY - 10;
        float thumb = Math.max(16, h * h / (h + maximum));
        g.setColor(withAlpha(BORDER, 100));
        g.fill(new RoundRectangle2D.Float(x, y, 2, h, 2, 2));
        if (usesSettingsLayer() && !drawingSettingsLayer) return;
        g.setColor(withAlpha(PRIMARY, 190));
        g.fill(new RoundRectangle2D.Float(x, y + (h - thumb) * scroll / Math.max(1, maximum), 2, thumb, 2, 2));
    }

    private void drawSettings(Graphics2D g) {
        if (paradox) {
            drawParadoxSettings(g);
            return;
        }
        g.setColor(SEARCH_BG);
        g.fill(new RoundRectangle2D.Float(L.moduleX, L.backY, L.gearSize, L.gearSize, 5 * sc, 5 * sc));
        g.setColor(withAlpha(BORDER, 150));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new RoundRectangle2D.Float(L.moduleX + 0.5F, L.backY + 0.5F, L.gearSize - 1, L.gearSize - 1, 5 * sc, 5 * sc));
        float acx = L.moduleX + L.gearSize / 2.0F;
        float acy = L.backY + L.gearSize / 2.0F;
        g.setColor(TEXT);
        g.setStroke(new BasicStroke(Math.max(1.1F, 1.5F * sc), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(acx + 4 * sc, acy, acx - 4 * sc, acy));
        g.draw(new Line2D.Float(acx - 4 * sc, acy, acx - 0.5F * sc, acy - 3.5F * sc));
        g.draw(new Line2D.Float(acx - 4 * sc, acy, acx - 0.5F * sc, acy + 3.5F * sc));

        if (orion3) {
            drawText(g, tr(settingsFor.title), L.moduleX + L.gearSize + 6, L.backY + L.gearSize - 4, headingFont(9.0F), PRIMARY, true);
            drawText(g, tr("Настройки модуля · прокрутка колёсиком"), L.moduleX + L.gearSize + 6, L.backY + L.gearSize + 6, bodyFont(6.2F), MUTED, false);
        } else {
            drawText(g, tr(settingsFor.title), CONTENT_X + GEAR_SIZE + 9, 62, headingFont(12.5F), PRIMARY, true);
            drawText(g, tr("Настройки"), CONTENT_X + GEAR_SIZE + 9, 73, bodyFont(7.0F), MUTED, false);
        }

        drawSettingRows(g, settingsRows);
    }

    private void drawParadoxSettings(Graphics2D g) {
        float backX = L.sidebarW + 12;
        float backY = L.backY;
        float cy = backY + L.gearSize / 2F;
        g.setColor(MUTED);
        g.setStroke(new BasicStroke(1.2F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(backX + 5, cy, backX + 12, cy));
        g.draw(new Line2D.Float(backX + 5, cy, backX + 8, cy - 3));
        g.draw(new Line2D.Float(backX + 5, cy, backX + 8, cy + 3));
        Font crumb = OrionInterfaceFonts.face(1, 8F);
        String moduleTitle = waypointPage ? "Метки" : settingsFor.action == Action.BASKETBALL ? "Баскетбол" : settingsFor.action == Action.FOOTBALL ? "Футбол" : settingsFor.title;
        String section = tr(settingsFor.action == Action.BASKETBALL || settingsFor.action == Action.FOOTBALL ? "Games" : Category.VALUES[selectedCategory].title);
        String breadcrumb = waypointPage ? tr("Метки") + " / " + tr(waypointSelected == 0 ? "Новая метка" : waypointName)
                : tr("Модули") + " / " + section + " / " + tr(moduleTitle);
        drawText(g, ellipsize(g, crumb, breadcrumb, 320), backX + 23, 23, crumb, MUTED, false);

        float iconX = L.sidebarW + 13;
        g.setColor(PRIMARY);
        g.fill(new RoundRectangle2D.Float(iconX, 43, 26, 26, 7, 7));
        drawModuleSymbol(g, settingsFor, iconX + 13, 56, TEXT);

        Font moduleTitleFont=OrionInterfaceFonts.face(3, 14F);
        drawText(g, ellipsize(g,moduleTitleFont,tr(moduleTitle),185), iconX + 36, 51, moduleTitleFont, TEXT, false);
        Font description = OrionInterfaceFonts.face(1, 8F);
        drawText(g, ellipsize(g, description, tr(settingsFor.description), 185), iconX + 36, 64, description, MUTED, false);

        float switchX = L.guiW - 110;
        if (settingsFor.action == Action.BASKETBALL || settingsFor.action == Action.FOOTBALL) {
            Font hint = bodyFont(6.5F);
            drawText(g, tr("Клик по клавише — изменить"), 367, 52, hint, MUTED, false);
            drawText(g, tr("Esc — отмена · Delete — снять"), 367, 63, hint, MUTED, false);
        }
        g.setColor(SEARCH_BG);
        g.fill(new RoundRectangle2D.Float(switchX, 43, 92, 24, 8, 8));
        Font stateFont=bodyFont(6.4F);
        String state=tr(isActive(settingsFor.action) ? "Включён" : "Выключен");
        drawText(g, ellipsize(g,stateFont,state,51), switchX + 5, 58, stateFont, TEXT, false);
        drawToggle(g, switchX + 61, 48, toggleAnim[settingsFor.action.ordinal()]);

        g.setColor(withAlpha(BORDER, 110));
        g.draw(new Line2D.Float(L.sidebarW, 80.5F, L.guiW - 10, 80.5F));
        if (waypointPage) {
            drawWaypointList(g);
        } else {
            drawText(g, tr("На этой странице"), L.sidebarW + 13, 92, bodyFont(6.2F), MUTED, false);
            int[] starts = paradoxGroupStarts(settingsRows);
            int selectedGroup = 0;
            for (int group = 0; group < starts.length; group++) {
                if (settingRowY(starts[group]) <= L.moduleY + 36) selectedGroup = group;
            }
            for (int group = 0; group < starts.length; group++) {
                int y = 99 + group * 18;
                if (group == selectedGroup) {
                    g.setColor(PRIMARY);
                    g.fill(new RoundRectangle2D.Float(L.sidebarW + 12, y, 2, 16, 2, 2));
                }
                drawText(g, tr(paradoxGroupName(group)), L.sidebarW + 20, y + 11, bodyFont(7F),
                        group == selectedGroup ? TEXT : MUTED, false);
                int end = group + 1 < starts.length ? starts[group + 1] : settingsRows.length;
                drawText(g, Integer.toString(end - starts[group]), L.moduleX - 22, y + 11, bodyFont(6F), MUTED, false);
            }
        }
        drawSettingRows(g, settingsRows);
    }

    private int waypointListVisible() { return Math.max(1, (L.guiH - WAYPOINT_LIST_Y - 12) / WAYPOINT_LIST_STEP); }

    private void drawWaypointList(Graphics2D g) {
        List<OrionConfig.Waypoint> points = currentWaypoints();
        waypointListScroll = Math.max(0, Math.min(waypointListScroll, Math.max(0, points.size() - waypointListVisible())));
        float x = L.sidebarW + 12, w = L.moduleX - L.sidebarW - 24;
        drawText(g, tr("Метки мира") + " · " + points.size(), x, 94, bodyFont(6.2F), MUTED, false);
        g.setColor(waypointSelected == 0 ? SURFACE_HOVER : SEARCH_BG);
        g.fill(new RoundRectangle2D.Float(x, 104, w, 24, 6, 6));
        drawCenteredText(g, tr("+ Новая метка"), x + w / 2, 119, bodyFont(7F), TEXT);
        Font title = OrionInterfaceFonts.face(2, 8F), detail = OrionInterfaceFonts.face(0, 6.5F);
        for (int i = 0; i < Math.min(waypointListVisible(), points.size() - waypointListScroll); i++) {
            int index = waypointListScroll + i;
            var point = points.get(index);
            float y = WAYPOINT_LIST_Y + i * WAYPOINT_LIST_STEP;
            if (waypointSelected == index + 1) { g.setColor(SURFACE); g.fill(new RoundRectangle2D.Float(x, y, w, 26, 5, 5)); }
            g.setColor(parseColor(point.color));
            g.fill(new Ellipse2D.Float(x + 4, y + 7, 4, 4));
            drawText(g, ellipsize(g, title, point.name, w - 17), x + 13, y + 11, title, TEXT, false);
            drawText(g, ellipsize(g, detail, point.x + " · " + point.y + " · " + point.z, w - 17), x + 13, y + 22, detail, MUTED, false);
        }
        if (points.isEmpty()) drawText(g, tr("Пока нет меток"), x + 3, WAYPOINT_LIST_Y + 12, detail, MUTED, false);
    }

    private void focusWaypointEditor() {
        editingBind = -1; expandedColor = -1; draggingSV = draggingHue = false;
        for (int i = 0; i < settingsRows.length; i++) if (settingsRows[i] instanceof TextSetting text && text.label.equals("Название")) {
            settingsScroll = Math.min(settingsMaxScroll(settingsRows), Math.max(0, (i - 1) * L.moduleStep));
            scrollAnimation = settingsScroll; break;
        }
        lastFingerprint = null;
    }

    private int[] paradoxGroupStarts(Setting[] rows) {
        if (rows.length == 0) return new int[0];
        if (settingsFor != null && settingsFor.action == Action.BASKETBALL && rows == settingsRows) return new int[]{0, 9, 13, 19, 24, 30, 34};
        if (settingsFor != null && settingsFor.action == Action.FOOTBALL && rows == settingsRows) return new int[]{0, 9, 14, 20, 24};
        return new int[]{0};
    }

    private String paradoxGroupName(int group) {
        if (settingsFor != null && settingsFor.action == Action.BASKETBALL) {
            return new String[]{"Сетевой матч", "Как играть", "Площадка", "Текущая игра", "Бросок", "Физика", "Вид и звук"}[group];
        }
        if (settingsFor != null && settingsFor.action == Action.FOOTBALL) {
            return new String[]{"Сетевой матч", "Как играть", "Поле", "Правила игры", "Удар"}[group];
        }
        return "Настройки";
    }

    private void drawParadoxGroups(Graphics2D g, Setting[] rows) {
        int[] starts = paradoxGroupStarts(rows);
        for (int group = 0; group < starts.length; group++) {
            int first = starts[group];
            int last = (group + 1 < starts.length ? starts[group + 1] : rows.length) - 1;
            int top = settingRowY(first);
            int bottom = settingRowY(last) + L.moduleH;
            if (expandedColor == last) bottom += pickerPanelHeight() + L.moduleStep - L.moduleH;
            if (group > 0) drawText(g, tr(paradoxGroupName(group)), L.moduleX + 6, top - 8, headingFont(7F), MUTED, false);
            g.setColor(SURFACE);
            g.fill(new RoundRectangle2D.Float(L.moduleX, top - 3, L.moduleW, bottom - top + 6, 8, 8));
            g.setColor(BORDER);
            g.setStroke(new BasicStroke(.65F));
            g.draw(new RoundRectangle2D.Float(L.moduleX + .5F, top - 2.5F, L.moduleW - 1, bottom - top + 5, 8, 8));
        }
    }

    private void drawSettingRow(Graphics2D g, Setting setting, int y) {
        int radius = orion3 ? 8 : 6;
        if (!paradox || settingsFor == null) {
            g.setColor(SURFACE);
            g.fill(new RoundRectangle2D.Float(L.moduleX, y, L.moduleW, L.moduleH, radius, radius));
        }
        if (paradox && settingsFor != null) {
            int index = Arrays.asList(settingsRows).indexOf(setting);
            boolean last = index == settingsRows.length - 1;
            for (int start : paradoxGroupStarts(settingsRows)) if (start == index + 1) last = true;
            if (!last) {
                g.setColor(BORDER); g.setStroke(new BasicStroke(.55F));
                g.draw(new Line2D.Float(L.moduleX + .5F, y + L.moduleStep - 2.5F,
                        L.moduleX + L.moduleW - .5F, y + L.moduleStep - 2.5F));
            }
        }
        if (!orion3) {
            g.setColor(withAlpha(BORDER, 150));
            g.setStroke(new BasicStroke(1.0F));
            g.draw(new RoundRectangle2D.Float(L.moduleX + 0.5F, y + 0.5F, L.moduleW - 1, L.moduleH - 1, 6, 6));
        }

        float labelX = L.moduleX + 14 * sc;
        Font labelFont = bodyFont((paradox ? 9.2F : 9.5F) * sc);
        String label = tr(setting.label());
        if (paradox || setting instanceof SliderSetting) {
            float right = setting instanceof SliderSetting ? sliderTrackX() : setting instanceof ToggleSetting ? toggleX() : textBoxX();
            label = ellipsize(g, labelFont, label, Math.max(0, right - labelX - 8 * sc));
        }
        String description = tr(sportSettingDescription(setting));
        drawText(g, label, labelX, description.isEmpty() ? y + L.moduleH / 2.0F + 3.5F * sc : y + (paradox ? 16 : 14),
                labelFont, setting instanceof ValueSetting ? MUTED : TEXT, !paradox);
        if (!description.isEmpty()) {
            Font detail = bodyFont(6F);
            float detailRight = setting instanceof SliderSetting ? sliderTrackX() : textBoxX();
            drawText(g, ellipsize(g, detail, description, Math.max(0, detailRight - labelX - 8)),
                    labelX, y + (paradox ? 31 : 23), detail, MUTED, false);
        }

        if (setting instanceof ValueSetting value) {
            Font valueFont = bodyFont(6.5F);
            drawCenteredText(g, ellipsize(g, valueFont, tr(value.get.get()), textBoxW() - 8), textBoxX() + textBoxW() / 2F,
                    y + L.moduleH / 2F + 2, valueFont, MUTED);
        } else if (setting instanceof ChoiceSetting choice) {
            drawArrowBox(g, choiceLeftArrowX(), y + arrowOffsetY(), true);
            drawArrowBox(g, choiceRightArrowX(), y + arrowOffsetY(), false);
            String value = tr(choice.options[Math.floorMod(choice.get.getAsInt(), choice.options.length)]);
            float centerX = choiceLeftArrowX() + L.arrowSize + L.choiceValueW / 2.0F;
            Font valueFont=bodyFont(8.5F * sc);
            drawCenteredText(g, ellipsize(g,valueFont,value,L.choiceValueW-4), centerX, y + L.moduleH / 2.0F + 3.0F * sc, valueFont, PRIMARY);
        } else if (setting instanceof SliderSetting slider) {
            float t = (slider.get.get() - slider.min) / (slider.max - slider.min);
            t = Math.max(0.0F, Math.min(1.0F, t));
            float tx = sliderTrackX();
            float ty = y + L.moduleH / 2.0F;
            float trackH = Math.max(2.5F, 4 * sc);
            float knobR = 5 * sc;
            g.setColor(withAlpha(BORDER, 220));
            g.fill(new RoundRectangle2D.Float(tx, ty - trackH / 2, L.sliderTrackW, trackH, trackH, trackH));
            Shape oldClip = g.getClip();
            g.clip(new RoundRectangle2D.Float(tx, ty - trackH / 2, L.sliderTrackW, trackH, trackH, trackH));
            g.setPaint(new GradientPaint(tx, ty, PRIMARY_DARK, tx + L.sliderTrackW, ty, PRIMARY));
            g.fill(new Rectangle2D.Float(tx, ty - trackH / 2, L.sliderTrackW * t, trackH));
            g.setClip(oldClip);
            float kx = tx + L.sliderTrackW * t;
            g.setColor(new Color(0, 0, 0, 60));
            g.fill(new Ellipse2D.Float(kx - knobR, ty - knobR + 0.8F, knobR * 2, knobR * 2));
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Float(kx - knobR, ty - knobR, knobR * 2, knobR * 2));
            String value = slider.integer
                    ? String.format(Locale.ROOT, "%d%s", Math.round(slider.get.get()), tr(slider.suffix))
                    : String.format(Locale.ROOT, "%.2f%s", slider.get.get(), tr(slider.suffix));
            Font numberFont=bodyFont(8.0F * sc);
            float numberX=tx+L.sliderTrackW+8*sc;
            drawText(g, ellipsize(g,numberFont,value,L.moduleX+L.moduleW-numberX-5), numberX, ty + 3.0F * sc, numberFont, MUTED, false);
        } else if (setting instanceof ColorSetting color) {
            Setting[] rows = editableRows();
            boolean expanded = expandedColor >= 0 && expandedColor < rows.length && rows[expandedColor] == setting;
            Color current = parseColor(color.get.get());
            float chipW = 30 * sc;
            float chipH = Math.max(10 * sc, L.swatchSize);
            float chipX = L.moduleX + L.moduleW - Math.round(14 * sc) - chipW;
            float chipY = y + (L.moduleH - chipH) / 2.0F;
            float chipRadius = Math.max(2.0F, 4 * sc);
            g.setColor(current);
            g.fill(new RoundRectangle2D.Float(chipX, chipY, chipW, chipH, chipRadius, chipRadius));
            g.setColor(expanded ? Color.WHITE : withAlpha(BORDER, 180));
            g.setStroke(new BasicStroke(expanded ? 1.6F : 1.0F));
            g.draw(new RoundRectangle2D.Float(chipX + 0.5F, chipY + 0.5F, chipW - 1, chipH - 1, chipRadius, chipRadius));

            String hex = color.get.get().toUpperCase(Locale.ROOT);
            Font hexFont = bodyFont(8.0F * sc);
            float hexWidth = (float) g.getFontMetrics(hexFont).getStringBounds(hex, g).getWidth();
            drawText(g, hex, chipX - 8 * sc - hexWidth, y + L.moduleH / 2.0F + 3.0F * sc, hexFont, MUTED, false);
        } else if (setting instanceof ToggleSetting toggle) {
            int index = Arrays.asList(settingsRows).indexOf(setting);
            float animation = paradox && index >= 0 && index < settingToggleAnimation.length
                    ? settingToggleAnimation[index] : toggle.get.get() ? 1F : 0F;
            drawToggle(g, toggleX(), y + (L.moduleH - L.toggleH) / 2.0F,
                    animation);
        } else if (setting instanceof KeybindSetting bind) {
            float bx=textBoxX(), by=y+arrowOffsetY(), bw=textBoxW();
            g.setColor(SEARCH_BG);g.fill(new RoundRectangle2D.Float(bx,by,bw,L.arrowSize,5*sc,5*sc));
            g.setColor(editingBind>=0 && editableRows()[editingBind]==setting ? PRIMARY : withAlpha(BORDER,150));
            g.setStroke(new BasicStroke(1F));g.draw(new RoundRectangle2D.Float(bx+.5F,by+.5F,bw-1,L.arrowSize-1,5*sc,5*sc));
            KeyMapping key=bind.get.get();
            String text=editingBind>=0 && editableRows()[editingBind]==setting ? tr("Нажмите клавишу…") : keybindLabel(key);
            Font f=bodyFont(8F*sc);
            drawCenteredText(g,ellipsize(g,f,text,bw-8*sc),bx+bw/2,by+L.arrowSize/2F+3*sc,f,TEXT);
        } else if (setting instanceof TextSetting text) {
            boolean editing = editingTextSetting() == setting;
            float bx = textBoxX();
            float by = y + arrowOffsetY();
            float bw = textBoxW();
            float cornerRadius = 5 * sc;
            g.setColor(SEARCH_BG);
            g.fill(new RoundRectangle2D.Float(bx, by, bw, L.arrowSize, cornerRadius, cornerRadius));
            g.setColor(editing ? PRIMARY : withAlpha(BORDER, 150));
            g.setStroke(new BasicStroke(editing ? 1.4F : 1.0F));
            g.draw(new RoundRectangle2D.Float(bx + 0.5F, by + 0.5F, bw - 1, L.arrowSize - 1, cornerRadius, cornerRadius));

            String value = text.get.get();
            Font valueFont = bodyFont(8.5F * sc);
            float baseline = by + L.arrowSize / 2.0F + 3.0F * sc;
            if (editing) {
                textCaret = Math.max(0, Math.min(value.length(), textCaret));
                textAnchor = Math.max(0, Math.min(value.length(), textAnchor));
                float innerWidth = Math.max(1, bw - 12 * sc);
                float caretWidth = textWidth(g, valueFont, value.substring(0, textCaret));
                float fullWidth = textWidth(g, valueFont, value);
                if (caretWidth - textScrollX > innerWidth - 2) textScrollX = caretWidth - innerWidth + 2;
                if (caretWidth < textScrollX) textScrollX = caretWidth;
                textScrollX = Math.max(0, Math.min(textScrollX, Math.max(0, fullWidth - innerWidth + 2)));
                float textX = bx + 6 * sc - textScrollX;
                Shape oldClip = g.getClip();
                g.clip(new Rectangle2D.Float(bx + 4 * sc, by + 1, bw - 8 * sc, L.arrowSize - 2));
                int selectionStart = Math.min(textCaret, textAnchor), selectionEnd = Math.max(textCaret, textAnchor);
                if (selectionStart != selectionEnd) {
                    float x0 = textX + textWidth(g, valueFont, value.substring(0, selectionStart));
                    float x1 = textX + textWidth(g, valueFont, value.substring(0, selectionEnd));
                    g.setColor(withAlpha(PRIMARY, 90));
                    g.fill(new Rectangle2D.Float(x0, by + 2 * sc, Math.max(0, x1 - x0), L.arrowSize - 4 * sc));
                }
                drawText(g, value, textX, baseline, valueFont, TEXT, false);
                if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
                    g.setColor(PRIMARY);
                    g.setStroke(new BasicStroke(1.0F));
                    float caretX = textX + caretWidth;
                    g.draw(new Line2D.Float(caretX, by + 3 * sc, caretX, by + L.arrowSize - 3 * sc));
                }
                g.setClip(oldClip);
            } else {
                String displayedValue = ellipsize(g, valueFont, value, bw - 12 * sc);
                drawText(g, displayedValue, bx + 6 * sc, baseline, valueFont, TEXT, false);
            }
        } else if (setting instanceof ButtonSetting button) {
            float bx = textBoxX();
            float by = y + arrowOffsetY();
            float bw = textBoxW();
            float cornerRadius = 5 * sc;
            g.setColor(SEARCH_BG);
            g.fill(new RoundRectangle2D.Float(bx, by, bw, L.arrowSize, cornerRadius, cornerRadius));
            if (!paradox) {
                g.setColor(withAlpha(PRIMARY, 190));
                g.setStroke(new BasicStroke(1.2F));
                g.draw(new RoundRectangle2D.Float(bx + 0.5F, by + 0.5F, bw - 1, L.arrowSize - 1, cornerRadius, cornerRadius));
            }
            String buttonText = tr("Свои звуки (.wav, .aiff, .au)".equals(button.label) ? hitSoundFolderStatus : button.buttonText);
            drawCenteredText(g, buttonText, bx + bw / 2.0F,
                    by + L.arrowSize / 2.0F + 3.0F * sc, bodyFont((paradox ? 7F : 8.5F) * sc), paradox ? TEXT : PRIMARY);
        }
    }

    private String keybindLabel(KeyMapping key) {
        return key == null || key.isUnbound() ? tr("Не назначена") : key.getTranslatedKeyMessage().getString();
    }

    private String sportSettingDescription(Setting setting) {
        if (settingsFor == null || (settingsFor.action != Action.BASKETBALL && settingsFor.action != Action.FOOTBALL)) return "";
        return switch (setting.label()) {
            case "Установить ворота" -> "Нажми, наведи на землю и нажми снова";
            case "Создать или вернуть мяч" -> "Мяч появится впереди или на точке пенальти";
            case "Установить кольцо" -> "Наведи на блок и нажми клавишу";
            case "Взять или создать мяч" -> "Возьми лежащий мяч или создай новый";
            case "Зарядить бросок" -> "Удерживай, затем отпусти для броска";
            case "Пас" -> "Нажми кнопку атаки, когда держишь мяч";
            case "Быстрый удар" -> "Нажми рядом с мячом для лёгкого удара";
            case "Зарядить удар" -> "Рядом с мячом удерживай и отпусти";
            case "Подкрутить мяч" -> "Зажми Shift перед началом зарядки удара";
            case "Переместить ворота" -> "Подтверди клавишей установки ворот";
            case "Переместить кольцо" -> "Нажми у кольца, наведи на блок и повтори";
            case "Сила броска" -> "Скорость мяча при броске";
            case "Скорость зарядки" -> "Как быстро набирается сила броска";
            case "Сила отскока" -> "Потеря энергии при ударе о поверхность";
            case "Гравитация" -> "Сила притяжения мяча";
            case "Скорость дриблинга" -> "Частота ударов мяча о землю";
            case "Размер мяча" -> "Масштаб баскетбольного мяча";
            case "Громкость" -> "Громкость звуков мяча";
            default -> "";
        };
    }

    /** Раскрытая палитра: SV-квадрат, вертикальная полоса тона, превью с hex и быстрые пресеты. */
    private void drawColorPicker(Graphics2D g, ColorSetting color, int panelY) {
        int radius = orion3 ? 8 : 6;
        int panelH = pickerPanelHeight();
        g.setColor(SEARCH_BG);
        g.fill(new RoundRectangle2D.Float(L.moduleX, panelY, L.moduleW, panelH, radius, radius));
        if (!orion3) {
            g.setColor(withAlpha(BORDER, 150));
            g.setStroke(new BasicStroke(1.0F));
            g.draw(new RoundRectangle2D.Float(L.moduleX + 0.5F, panelY + 0.5F, L.moduleW - 1, panelH - 1, radius, radius));
        }

        PickerLayout p = pickerLayout(panelY);

        // SV-квадрат: слева направо — насыщенность, сверху вниз — яркость.
        g.setPaint(new GradientPaint(p.svX(), p.svY(), Color.WHITE,
                p.svX() + p.svS(), p.svY(), Color.getHSBColor(pickerHue, 1.0F, 1.0F)));
        g.fill(new Rectangle2D.Float(p.svX(), p.svY(), p.svS(), p.svS()));
        g.setPaint(new GradientPaint(p.svX(), p.svY(), new Color(0, 0, 0, 0),
                p.svX(), p.svY() + p.svS(), Color.BLACK));
        g.fill(new Rectangle2D.Float(p.svX(), p.svY(), p.svS(), p.svS()));
        g.setColor(withAlpha(BORDER, 180));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new Rectangle2D.Float(p.svX() - 0.5F, p.svY() - 0.5F, p.svS() + 1, p.svS() + 1));

        float knobX = p.svX() + pickerSat * p.svS();
        float knobY = p.svY() + (1.0F - pickerVal) * p.svS();
        float knobR = Math.max(2.5F, 3.5F * sc);
        g.setColor(new Color(0, 0, 0, 120));
        g.setStroke(new BasicStroke(2.5F));
        g.draw(new Ellipse2D.Float(knobX - knobR, knobY - knobR, knobR * 2, knobR * 2));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.4F));
        g.draw(new Ellipse2D.Float(knobX - knobR, knobY - knobR, knobR * 2, knobR * 2));

        // Полоса тона.
        for (int i = 0; i < p.svS(); i++) {
            g.setColor(Color.getHSBColor(i / (float) p.svS(), 1.0F, 1.0F));
            g.fill(new Rectangle2D.Float(p.hueX(), p.svY() + i, p.hueW(), 1.25F));
        }
        g.setColor(withAlpha(BORDER, 180));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new Rectangle2D.Float(p.hueX() - 0.5F, p.svY() - 0.5F, p.hueW() + 1, p.svS() + 1));
        float hueY = p.svY() + pickerHue * p.svS();
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(Math.max(1.5F, 2.0F * sc)));
        g.draw(new Line2D.Float(p.hueX() - 1.5F, hueY, p.hueX() + p.hueW() + 1.5F, hueY));

        // Превью текущего цвета и hex.
        Color current = Color.getHSBColor(pickerHue, pickerSat, pickerVal);
        float previewRadius = Math.max(2.0F, 4 * sc);
        g.setColor(current);
        g.fill(new RoundRectangle2D.Float(p.rightX(), p.svY(), p.previewW(), p.previewH(), previewRadius, previewRadius));
        g.setColor(withAlpha(BORDER, 180));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new RoundRectangle2D.Float(p.rightX() + 0.5F, p.svY() + 0.5F,
                p.previewW() - 1, p.previewH() - 1, previewRadius, previewRadius));
        String hex = String.format(Locale.ROOT, "#%02X%02X%02X",
                current.getRed(), current.getGreen(), current.getBlue());
        drawText(g, hex, p.rightX() + p.previewW() + 7 * sc,
                p.svY() + p.previewH() / 2.0F + 3.0F * sc, bodyFont(8.5F * sc), TEXT, false);

        // Быстрые пресеты.
        float dotRadius = Math.max(2.0F, 3 * sc);
        for (int s = 0; s < color.presets.length; s++) {
            float dx = p.rightX() + (s % 4) * p.dotStep();
            float dy = p.dotsY() + (s / 4) * p.dotStep();
            g.setColor(parseColor(color.presets[s]));
            g.fill(new RoundRectangle2D.Float(dx, dy, p.dotSize(), p.dotSize(), dotRadius, dotRadius));
            g.setColor(withAlpha(BORDER, 180));
            g.setStroke(new BasicStroke(1.0F));
            g.draw(new RoundRectangle2D.Float(dx + 0.5F, dy + 0.5F, p.dotSize() - 1, p.dotSize() - 1, dotRadius, dotRadius));
        }
    }

    private int textBoxX() {
        return choiceLeftArrowX();
    }

    private int textBoxW() {
        return L.arrowSize * 2 + L.choiceValueW;
    }

    private void drawArrowBox(Graphics2D g, float x, float y, boolean left) {
        g.setColor(SEARCH_BG);
        g.fill(new RoundRectangle2D.Float(x, y, L.arrowSize, L.arrowSize, 5 * sc, 5 * sc));
        g.setColor(withAlpha(BORDER, 150));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new RoundRectangle2D.Float(x + 0.5F, y + 0.5F, L.arrowSize - 1, L.arrowSize - 1, 5 * sc, 5 * sc));
        float cx = x + L.arrowSize / 2.0F;
        float cy = y + L.arrowSize / 2.0F;
        float d = (left ? 1.8F : -1.8F) * sc;
        g.setColor(TEXT);
        g.setStroke(new BasicStroke(Math.max(1.1F, 1.5F * sc), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(cx + d, cy - 3.5F * sc, cx - d, cy));
        g.draw(new Line2D.Float(cx - d, cy, cx + d, cy + 3.5F * sc));
    }

    private static void drawCenteredText(Graphics2D g, String text, float centerX, float baseline, Font font, Color color) {
        g.setFont(font);
        float width = (float) g.getFontMetrics(font).getStringBounds(text, g).getWidth();
        drawText(g, text, centerX - width / 2.0F, baseline, font, color, false);
    }

    private static Color parseColor(String hex) {
        try {
            return new Color(Integer.parseInt(hex.substring(1), 16));
        } catch (RuntimeException exception) {
            return ORION_PALETTE.accent;
        }
    }

    private int gearX() {
        return orion3 ? toggleX() - 6 - L.gearSize : L.moduleX + L.moduleW - 38;
    }

    private int choiceRightArrowX() {
        return L.moduleX + L.moduleW - Math.round(14 * sc) - L.arrowSize;
    }

    private int choiceLeftArrowX() {
        return choiceRightArrowX() - L.choiceValueW - L.arrowSize;
    }

    private int sliderTrackX() {
        return L.moduleX + L.moduleW - Math.round(14 * sc) - Math.round((paradox ? 75 : 38) * sc) - L.sliderTrackW;
    }

    private int searchX() { return paradox ? 8 : orion3 ? L.moduleX + L.moduleW - 126 : 303; }
    private int searchY() { return paradox ? 30 : orion3 ? 13 : 44; }
    private int searchW() { return paradox ? 110 : orion3 ? 126 : 138; }
    private int searchH() { return paradox ? 19 : orion3 ? 25 : 27; }
    private void drawSearch(Graphics2D g) { drawSearchInput(g); }
    private void drawSearchInput(Graphics2D g) {
        float x=searchX(),y=searchY(),w=searchW(),h=searchH();
        g.setColor(SEARCH_BG); g.fill(new RoundRectangle2D.Float(x,y,w,h,6,6));
        g.setColor(searchFocused ? PRIMARY : BORDER); g.setStroke(new BasicStroke(.8F));
        g.draw(new RoundRectangle2D.Float(x+.5F,y+.5F,w-1,h-1,6,6));
        drawMagnifier(g,x+7,y+h/2,2.6F,MUTED);
        Font font=paradox ? OrionInterfaceFonts.face(1,9F) : bodyFont(8F);
        float tx=x+20,baseline=y+h/2+(g.getFontMetrics(font).getAscent()-g.getFontMetrics(font).getDescent())/2F;
        Shape clip=g.getClip();g.clip(new Rectangle2D.Float(tx,y+2,w-24,h-4));
        if(searchQuery.isEmpty() && !searchFocused) drawText(g,tr("Поиск"),tx,baseline,font,PLACEHOLDER,false);
        else {
            int caret=Math.max(0,Math.min(textCaret,searchQuery.length())),anchor=Math.max(0,Math.min(textAnchor,searchQuery.length()));
            float cw=textWidth(g,font,searchQuery.substring(0,caret));
            if(searchFocused) textScrollX=Math.max(0,Math.max(cw-(w-28),Math.min(textScrollX,cw)));
            float start=tx-textScrollX;
            if(searchFocused && caret!=anchor){float left=textWidth(g,font,searchQuery.substring(0,Math.min(caret,anchor)));float right=textWidth(g,font,searchQuery.substring(0,Math.max(caret,anchor)));g.setColor(withAlpha(PRIMARY,85));g.fill(new Rectangle2D.Float(start+left,y+3,right-left,h-6));}
            drawText(g,searchQuery,start,baseline,font,TEXT,false);
            if(searchFocused && (System.currentTimeMillis()/500L)%2L==0){g.setColor(TEXT);g.draw(new Line2D.Float(start+cw+.5F,y+4,start+cw+.5F,y+h-4));}
        }
        g.setClip(clip);
    }

    private static void drawMagnifier(Graphics2D g, float x, float centerY, float radius, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(Math.max(1.1F, radius * 0.29F), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float offset = radius * 0.27F;
        g.draw(new Ellipse2D.Float(x, centerY - radius - offset, radius * 2, radius * 2));
        float hx = x + radius + radius * 0.72F;
        float hy = centerY - offset + radius * 0.72F;
        g.draw(new Line2D.Float(hx, hy, hx + radius * 0.62F, hy + radius * 0.62F));
    }

    private static String spaced(String text) {
        StringBuilder builder = new StringBuilder(text.length() * 2);
        for (int i = 0; i < text.length(); i++) {
            builder.append(text.charAt(i));
            if (i < text.length() - 1) {
                builder.append(' ');
            }
        }
        return builder.toString();
    }

    private void drawModule(Graphics2D g, ModuleRow row, int y, float hover) {
        boolean active = isActive(row.action);
        float animation = toggleAnim[row.action.ordinal()];

        if (paradox) {
            g.setColor(mix(SURFACE, SURFACE_HOVER, hover));
            g.fill(new RoundRectangle2D.Float(L.moduleX, y, L.moduleW, L.moduleH, 6, 6));
            g.setColor(mix(SEARCH_BG, withAlpha(PRIMARY, 40), animation));
            g.fill(new RoundRectangle2D.Float(L.moduleX + 7, y + 5, 20, 20, 5, 5));
            drawIcon(g, row.icon, L.moduleX + 17, y + 15, active ? PRIMARY : MUTED, 1F, .32F);
            Font name = headingFont(7.3F), detail = bodyFont(5.8F);
            float max = gearX() - L.moduleX - 63;
            drawText(g, ellipsize(g, name, tr(row.title), max), L.moduleX + 35, y + 12, name, TEXT, false);
            drawText(g, ellipsize(g, detail, tr(row.description), max), L.moduleX + 35, y + 22, detail, MUTED, false);
            drawFavoriteStar(g, gearX() - 11, y + 15, paradoxFavorites.contains(row.action));
            drawToggle(g, toggleX(), y + (L.moduleH - L.toggleH) / 2F, animation);
            if (hasSettings(row.action)) drawGearButton(g, gearX(), y + gearOffsetY(), hover);
            return;
        }

        int radius = orion3 ? 10 : 6;
        g.setColor(mix(SURFACE, SURFACE_HOVER, hover));
        g.fill(new RoundRectangle2D.Float(L.moduleX, y, L.moduleW, L.moduleH, radius, radius));

        if (orion3) {
            // Карточки в макете без рамок; при ховере проступает едва заметный контур.
            if (hover > 0.01F) {
                g.setColor(withAlpha(BORDER, Math.round(120 * hover)));
                g.setStroke(new BasicStroke(1.0F));
                g.draw(new RoundRectangle2D.Float(L.moduleX + 0.5F, y + 0.5F, L.moduleW - 1, L.moduleH - 1, radius, radius));
            }
            Color iconColor = mix(TEXT, PRIMARY, animation);
            g.setColor(withAlpha(active ? PRIMARY : BORDER, active ? 35 : 50));
            g.fill(new RoundRectangle2D.Float(L.moduleX + 7, y + 5, 23, 23, 7, 7));
            drawIcon(g, row.icon, L.moduleX + 18.5F, y + L.moduleH / 2.0F, iconColor, 1.4F, 0.48F);
            float textX = L.moduleX + 37;
            float textW = Math.max(10, gearX() - textX - 7);
            Font titleFont = headingFont(8.2F);
            Font descriptionFont = bodyFont(6.2F);
            drawText(g, ellipsize(g, titleFont, tr(row.title), textW), textX, y + 14, titleFont, TEXT, true);
            drawText(g, ellipsize(g, descriptionFont, tr(row.description), textW), textX, y + 24, descriptionFont, MUTED, false);
        } else {
            if (active) {
                g.setColor(PRIMARY);
                g.setStroke(new BasicStroke(1.3F));
            } else {
                g.setColor(withAlpha(BORDER, Math.round(150 + 60 * hover)));
                g.setStroke(new BasicStroke(1.0F));
            }
            g.draw(new RoundRectangle2D.Float(L.moduleX + 0.5F, y + 0.5F, L.moduleW - 1, L.moduleH - 1, radius, radius));

            Color iconColor = mix(TEXT, PRIMARY, animation);
            drawIcon(g, row.icon, L.moduleX + 23, y + 26, iconColor, 1.8F, 1.0F);
            int textX = L.moduleX + 23 + 12 + ICON_TEXT_GAP;
            drawText(g, tr(row.title), textX, y + 22.5F, headingFont(10.8F), TEXT, true);
            // Описание всегда нейтрально-серое: акцентный оранжевый на тёмном фоне при таком кегле нечитаем.
            drawText(g, tr(row.description), textX, y + 36.5F, bodyFont(8.2F), mix(MUTED, TEXT, animation * 0.35F), false);
        }

        drawToggle(g, toggleX(), y + (L.moduleH - L.toggleH) / 2.0F, animation);
        if (hasSettings(row.action)) {
            drawGearButton(g, gearX(), y + gearOffsetY(), hover);
        }
    }

    private void drawToggle(Graphics2D g, float x, float y, float animation) {
        float eased = easeInOutCubic(animation);
        if (paradox) {
            g.setColor(mix(BORDER, PRIMARY, eased));
            g.fill(new RoundRectangle2D.Float(x, y, L.toggleW, L.toggleH, L.toggleH, L.toggleH));
            float radius = (L.toggleH - 4) / 2F;
            float cx = x + 2 + radius + (L.toggleW - 4 - radius * 2) * eased;
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Float(cx - radius, y + 2, radius * 2, radius * 2));
            return;
        }
        if (orion3) {
            // Контурный тумблер из макета: тёмная пилюля, обводка и круглый бегунок
            // перекрашиваются из серого в акцентный.
            g.setColor(SEARCH_BG);
            g.fill(new RoundRectangle2D.Float(x, y, L.toggleW, L.toggleH, L.toggleH, L.toggleH));
            Color rim = mix(new Color(0xD8, 0xD8, 0xDE), PRIMARY, eased);
            g.setColor(rim);
            g.setStroke(new BasicStroke(1.4F));
            g.draw(new RoundRectangle2D.Float(x + 0.7F, y + 0.7F, L.toggleW - 1.4F, L.toggleH - 1.4F, L.toggleH - 1.4F, L.toggleH - 1.4F));

            float radius = (L.toggleH - 6) / 2.0F + 0.5F;
            float inset = 3.2F;
            float cx = x + inset + radius + (L.toggleW - radius * 2 - inset * 2) * eased;
            float cy = y + L.toggleH / 2.0F;
            g.setColor(rim);
            g.fill(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2));
            return;
        }
        g.setPaint(new GradientPaint(x, y, mix(BORDER, PRIMARY, eased), x + L.toggleW, y + L.toggleH, mix(darken(BORDER, 8), PRIMARY_DARK, eased)));
        g.fill(new RoundRectangle2D.Float(x, y, L.toggleW, L.toggleH, L.toggleH, L.toggleH));
        g.setColor(new Color(255, 255, 255, Math.round(20 + 20 * eased)));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new RoundRectangle2D.Float(x + 0.5F, y + 0.5F, L.toggleW - 1, L.toggleH - 1, L.toggleH - 1, L.toggleH - 1));

        float radius = 6.5F;
        float cx = x + 3 + radius + (L.toggleW - radius * 2 - 6) * eased;
        float cy = y + L.toggleH / 2.0F;
        g.setColor(new Color(0, 0, 0, 60));
        g.fill(new Ellipse2D.Float(cx - radius, cy - radius + 0.8F, radius * 2, radius * 2));
        g.setColor(mix(TEXT, Color.WHITE, eased));
        g.fill(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2));
    }

    private static int themeOption(int stored) { for(int i=0;i<THEME_IDS.length;i++) if(THEME_IDS[i]==stored)return i;return 0; }
    private void drawModuleSymbol(Graphics2D g, ModuleRow row, float x, float y, Color color) {
        Graphics2D icon = (Graphics2D) g.create();
        try {
            icon.translate(x,y); icon.setColor(color);
            icon.setStroke(new BasicStroke(1.15F,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            switch(row.action) {
                case CHAT_FILTER -> { Path2D.Float p=new Path2D.Float();p.moveTo(-5,-4);p.lineTo(5,-4);p.lineTo(1,0);p.lineTo(1,5);p.lineTo(-1,4);p.lineTo(-1,0);p.closePath();icon.draw(p); }
                case CHAT_HIGHLIGHT -> {
                    Path2D.Float marker = new Path2D.Float();
                    marker.moveTo(-4, 1); marker.lineTo(2, -5); marker.quadTo(3, -6, 4, -5);
                    marker.lineTo(6, -3); marker.lineTo(-1, 4); marker.closePath(); icon.draw(marker);
                    icon.draw(new Line2D.Float(-4, 1, -5, 5));
                    icon.draw(new Line2D.Float(-5, 5, -1, 4));
                    icon.draw(new Line2D.Float(-6, 7, 5, 7));
                    icon.draw(new Line2D.Float(1, -4, 4, -1));
                }
                case CUSTOM_TIME, CHAT_TIME -> { icon.draw(new Ellipse2D.Float(-5,-5,10,10));icon.draw(new Line2D.Float(0,-3,0,0));icon.draw(new Line2D.Float(0,0,3,2)); }
                case WAYPOINTS -> { Path2D.Float p=new Path2D.Float();p.moveTo(0,6);p.curveTo(-9,-2,-3,-7,0,-6);p.curveTo(3,-7,9,-2,0,6);icon.draw(p);icon.draw(new Ellipse2D.Float(-1.5F,-3.5F,3,3)); }
                case DEATH_POINT -> { icon.draw(new RoundRectangle2D.Float(-4,-5,8,8,4,4));icon.fill(new Ellipse2D.Float(-2.5F,-2,2,2));icon.fill(new Ellipse2D.Float(.5F,-2,2,2));icon.draw(new Line2D.Float(-2,3,-2,5));icon.draw(new Line2D.Float(2,3,2,5)); }
                case ANIMATIONS -> {
                    icon.draw(new RoundRectangle2D.Float(-5, -5, 10, 10, 3, 3));
                    Path2D.Float play = new Path2D.Float();
                    play.moveTo(-1.5F, -2.5F); play.lineTo(2, 0); play.lineTo(-1.5F, 2.5F); play.closePath();
                    icon.fill(play);
                }
                case ARMOR_INDICATOR -> {
                    Path2D.Float armor = new Path2D.Float();
                    armor.moveTo(-3, -5); armor.lineTo(-6, -2); armor.lineTo(-4, 0); armor.lineTo(-3, -1);
                    armor.lineTo(-3, 5); armor.lineTo(3, 5); armor.lineTo(3, -1); armor.lineTo(4, 0);
                    armor.lineTo(6, -2); armor.lineTo(3, -5); armor.quadTo(0, -1, -3, -5); armor.closePath();
                    icon.draw(armor);
                }
                case BASKETBALL, FOOTBALL -> {
                    icon.draw(new Ellipse2D.Float(-5.5F, -5.5F, 11, 11));
                    icon.draw(new Line2D.Float(-5.5F, 0, 5.5F, 0));
                    icon.draw(new Line2D.Float(0, -5.5F, 0, 5.5F));
                    Path2D.Float seam = new Path2D.Float();
                    seam.moveTo(-4, -4); seam.curveTo(1, -2, 1, 2, -4, 4);
                    seam.moveTo(4, -4); seam.curveTo(-1, -2, -1, 2, 4, 4); icon.draw(seam);
                }
                case MIRAGE -> {
                    icon.draw(new RoundRectangle2D.Float(-5, -6, 10, 12, 3, 3));
                    icon.draw(new Line2D.Float(-2, 2, 2, -3));
                    icon.draw(new Line2D.Float(0, 3, 3, 0));
                }
                case TARGET_ESP -> {
                    Path2D.Float corners = new Path2D.Float();
                    for (int side : new int[]{-1, 1}) {
                        corners.moveTo(side * 2, -6); corners.lineTo(side * 6, -6); corners.lineTo(side * 6, -2);
                        corners.moveTo(side * 6, 2); corners.lineTo(side * 6, 6); corners.lineTo(side * 2, 6);
                    }
                    icon.draw(corners);
                    icon.draw(new Ellipse2D.Float(-2, -2, 4, 4));
                }
                case HIT_COLOR -> {
                    Path2D.Float drop = new Path2D.Float();
                    drop.moveTo(0, -6); drop.curveTo(-2, -3, -5, -1, -5, 2);
                    drop.curveTo(-5, 8, 5, 8, 5, 2); drop.curveTo(5, -1, 2, -3, 0, -6); drop.closePath();
                    icon.draw(drop);
                    Shape clip = icon.getClip();
                    icon.clip(drop); icon.fill(new Rectangle2D.Float(-5, 2, 10, 5)); icon.setClip(clip);
                }
                case BETTER_NEAR -> {
                    icon.draw(new Ellipse2D.Float(-5, -5, 4, 4));
                    icon.draw(new Arc2D.Float(-7, 1, 8, 8, 0, 180, Arc2D.OPEN));
                    icon.draw(new Ellipse2D.Float(2, -4, 3, 3));
                    icon.draw(new Arc2D.Float(1, 1, 6, 6, 0, 140, Arc2D.OPEN));
                }
                case SMALL_SHIELD -> {
                    Path2D.Float shield = new Path2D.Float();
                    shield.moveTo(-2, -4); shield.lineTo(3, -2); shield.lineTo(3, 2);
                    shield.quadTo(2, 5, -2, 6); shield.quadTo(-6, 5, -7, 2);
                    shield.lineTo(-7, -2); shield.closePath(); icon.draw(shield);
                    icon.draw(new Line2D.Float(1, -6, 6, -6));
                    icon.draw(new Line2D.Float(6, -6, 6, -1));
                    icon.draw(new Line2D.Float(6, -6, 2, -2));
                }
                case VIEW_MODEL -> {
                    Path2D.Float hand = new Path2D.Float();
                    hand.moveTo(-4, 6); hand.lineTo(-7, 1); hand.quadTo(-7, -1, -5, 0);
                    hand.lineTo(-3, 2); hand.lineTo(-3, -4); hand.quadTo(-2, -6, -1, -4);
                    hand.lineTo(-1, -1); hand.lineTo(-1, -5); hand.quadTo(0, -7, 1, -5);
                    hand.lineTo(1, -1); hand.lineTo(1, -4); hand.quadTo(2, -6, 3, -4);
                    hand.lineTo(3, 0); hand.lineTo(3, -2); hand.quadTo(5, -4, 5, -1);
                    hand.lineTo(5, 3); hand.lineTo(3, 6); hand.closePath();
                    icon.draw(hand);
                }
                case ITEM_PHYSICS -> {
                    Path2D.Float item = new Path2D.Float();
                    item.moveTo(-4, -4); item.lineTo(1, -6); item.lineTo(5, -2);
                    item.lineTo(0, 0); item.closePath();
                    item.moveTo(-4, -4); item.lineTo(-4, 1); item.lineTo(0, 5);
                    item.lineTo(5, 3); item.lineTo(5, -2);
                    item.moveTo(0, 0); item.lineTo(0, 5); icon.draw(item);
                    icon.draw(new Line2D.Float(-6, 7, 6, 7));
                    icon.draw(new Line2D.Float(-7, -3, -7, 2));
                }
                case PVP_SAFE -> {
                    Path2D.Float shield = new Path2D.Float();
                    shield.moveTo(0, -6); shield.lineTo(5, -4); shield.lineTo(4, 2);
                    shield.quadTo(3, 4, 0, 6); shield.quadTo(-3, 4, -4, 2);
                    shield.lineTo(-5, -4); shield.closePath(); icon.draw(shield);
                    icon.draw(new Line2D.Float(-2, 0, -.5F, 2));
                    icon.draw(new Line2D.Float(-.5F, 2, 2.5F, -2));
                }
                case HEALTH_TAGS, DAMAGE_INDICATORS -> {
                    Path2D.Float heart = new Path2D.Float();
                    heart.moveTo(0, 5); heart.curveTo(-9, 0, -6, -7, 0, -3);
                    heart.curveTo(6, -7, 9, 0, 0, 5); heart.closePath(); icon.draw(heart);
                }
                case NO_FIRE -> {
                    Path2D.Float flame = new Path2D.Float();
                    flame.moveTo(0, -6); flame.curveTo(1, -2, 6, 0, 4, 4);
                    flame.curveTo(1, 8, -7, 4, -4, 0); flame.quadTo(-3, 2, -2, 1);
                    flame.quadTo(-3, -2, 0, -6); flame.closePath(); icon.draw(flame);
                    icon.draw(new Line2D.Float(-6, 6, 6, -6));
                }
                case NO_WEATHER -> {
                    Path2D.Float cloud = new Path2D.Float();
                    cloud.moveTo(-3, 2); cloud.curveTo(-8, 2, -7, -3, -3, -3);
                    cloud.curveTo(-2, -7, 4, -7, 4, -2); cloud.curveTo(8, -2, 8, 2, 4, 2);
                    cloud.closePath(); icon.draw(cloud);
                    icon.draw(new Line2D.Float(-3, 4, -4, 6));
                    icon.draw(new Line2D.Float(1, 4, 0, 6));
                }
                case WORLD_CHANGER -> {
                    icon.draw(new Ellipse2D.Float(-6, -6, 12, 12));
                    icon.draw(new Ellipse2D.Float(-2.5F, -6, 5, 12));
                    icon.draw(new Line2D.Float(-6, 0, 6, 0));
                }
                case MOTION_BLUR -> {
                    icon.draw(new RoundRectangle2D.Float(-1, -4, 7, 8, 2, 2));
                    icon.draw(new Line2D.Float(-6, -3, -3, -3));
                    icon.draw(new Line2D.Float(-7, 0, -3, 0));
                    icon.draw(new Line2D.Float(-6, 3, -3, 3));
                }
                case ZOOM -> {
                    icon.draw(new Ellipse2D.Float(-5, -5, 8, 8));
                    icon.draw(new Line2D.Float(2, 2, 6, 6));
                    icon.draw(new Line2D.Float(-3, -1, 1, -1));
                    icon.draw(new Line2D.Float(-1, -3, -1, 1));
                }
                case INVENTORY_HUD -> { icon.draw(new RoundRectangle2D.Float(-5,-4,10,8,2,2));icon.draw(new Line2D.Float(-2,-4,-2,4));icon.draw(new Line2D.Float(2,-4,2,4));icon.draw(new Line2D.Float(-5,0,5,0)); }
                case PROJECTILE_TRAIL -> { icon.draw(new Line2D.Float(-5,4,4,-4));icon.draw(new Line2D.Float(0,-4,4,-4));icon.draw(new Line2D.Float(4,-4,4,0));icon.draw(new Line2D.Float(-5,0,-2,-3)); }
                case BREADCRUMBS -> {
                    AffineTransform transform = icon.getTransform();
                    icon.rotate(-.35);
                    icon.fill(new RoundRectangle2D.Float(-5, -2, 3.5F, 6, 3, 3));
                    icon.fill(new Ellipse2D.Float(-5, 5, 3.5F, 2));
                    icon.fill(new RoundRectangle2D.Float(1.5F, -6, 3.5F, 6, 3, 3));
                    icon.fill(new Ellipse2D.Float(1.5F, 1, 3.5F, 2));
                    icon.setTransform(transform);
                }
                case WORLD_PARTICLES, HIT_PARTICLES -> { drawSparkle(icon,0,0,5,color,.8F);icon.fill(new Ellipse2D.Float(4,-5,2,2)); }
                case NO_PARTICLES -> { drawSparkle(icon,0,0,4,color,.8F);icon.draw(new Line2D.Float(-5,5,5,-5)); }
                case HALO -> {
                    icon.draw(new Ellipse2D.Float(-6, -6, 12, 3));
                    icon.draw(new Ellipse2D.Float(-3, -1, 6, 6));
                    icon.draw(new Arc2D.Float(-5, 6, 10, 6, 20, 140, Arc2D.OPEN));
                }
                case WINGS -> { for(int side : new int[]{-1,1}) { Path2D.Float wing=new Path2D.Float();wing.moveTo(0,3);wing.curveTo(side*2,-1,side*5,-4,side*6,-4);wing.quadTo(side*6,1,side*2,4);wing.closePath();icon.draw(wing); } }
                case LOCK_SLOT -> { icon.draw(new RoundRectangle2D.Float(-4,-1,8,6,2,2));icon.draw(new Arc2D.Float(-2.5F,-5,5,7,0,180,Arc2D.OPEN)); }
                case PROTECT_NAME -> { icon.draw(new Ellipse2D.Float(-5,-3,10,6));icon.draw(new Line2D.Float(-5,5,5,-5)); }
                case FAKE_PREFIX -> { Path2D.Float tag=new Path2D.Float();tag.moveTo(-5,-3);tag.lineTo(2,-3);tag.lineTo(5,0);tag.lineTo(2,3);tag.lineTo(-5,3);tag.closePath();icon.draw(tag);icon.fill(new Ellipse2D.Float(1,-1,2,2)); }
                case SELF_TAG -> { icon.draw(new Ellipse2D.Float(-2,-5,4,4));icon.draw(new Arc2D.Float(-4,0,8,7,0,180,Arc2D.OPEN));icon.draw(new Line2D.Float(-4,-7,4,-7)); }
                case ENCHANT_GLINT, RAINBOW -> drawFavoriteStar(icon,0,0,true);
                case KILL_EFFECTS -> {
                    Path2D.Float skull = new Path2D.Float();
                    skull.moveTo(-3, 5); skull.lineTo(-3, 2); skull.curveTo(-8, 0, -6, -6, -1, -6);
                    skull.curveTo(4, -6, 6, 0, 1, 2); skull.lineTo(1, 5); skull.closePath(); icon.draw(skull);
                    icon.fill(new Ellipse2D.Float(-4, -1, 2, 2));
                    icon.fill(new Ellipse2D.Float(0, -1, 2, 2));
                    icon.draw(new Line2D.Float(-1, 3, -1, 5));
                    icon.draw(new Line2D.Float(6, 2, 6, 6));
                    icon.draw(new Line2D.Float(4, 4, 8, 4));
                }
                case SATURATION -> {
                    Path2D.Float food = new Path2D.Float();
                    food.moveTo(-2, 2); food.curveTo(-8, 0, -3, -8, 3, -5);
                    food.curveTo(9, -2, 3, 5, 0, 3); food.closePath(); icon.draw(food);
                    Path2D.Float bone = new Path2D.Float();
                    bone.moveTo(-2, 2); bone.lineTo(-4, 4); bone.curveTo(-7, 2, -8, 6, -5, 6);
                    bone.curveTo(-5, 9, -1, 7, -3, 5); bone.lineTo(0, 3); icon.draw(bone);
                }
                default -> drawIcon(icon,row.icon,0,0,color,1.15F,.44F);
            }
        } finally { icon.dispose(); }
    }

    private void drawFavoriteStar(Graphics2D g, float cx, float cy, boolean active) {
        Path2D.Float star = new Path2D.Float();
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            float radius = i % 2 == 0 ? 4.3F : 1.95F;
            float x = cx + (float)Math.cos(angle) * radius, y = cy + .4F + (float)Math.sin(angle) * radius;
            if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
        }
        star.closePath();
        g.setColor(active ? PRIMARY : MUTED);
        g.setStroke(new BasicStroke(1F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (active) g.fill(star); else g.draw(star);
    }

    private void drawGearButton(Graphics2D g, float x, float y, float hover) {
        float size = L.gearSize;
        float k = size / 25.0F;
        g.setColor(mix(SEARCH_BG, lighten(SEARCH_BG, 12), hover));
        g.fill(new RoundRectangle2D.Float(x, y, size, size, 5 * k, 5 * k));
        g.setColor(withAlpha(BORDER, 150));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new RoundRectangle2D.Float(x + 0.5F, y + 0.5F, size - 1, size - 1, 5 * k, 5 * k));

        float cx = x + size / 2;
        float cy = y + size / 2;
        g.setColor(mix(MUTED, TEXT, hover));
        g.setStroke(new BasicStroke(Math.max(1.1F, 1.5F * k), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Float(cx - 3.2F * k, cy - 3.2F * k, 6.4F * k, 6.4F * k));
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI / 4 * i;
            float sin = (float) Math.sin(angle);
            float cos = (float) Math.cos(angle);
            g.draw(new Line2D.Float(cx + cos * 5.2F * k, cy + sin * 5.2F * k, cx + cos * 7.4F * k, cy + sin * 7.4F * k));
        }
    }

    private void drawIcon(Graphics2D g, Icon icon, float cx, float cy, Color color, float stroke, float size) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(cx, cy);
        g2.scale(size, size);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(stroke / size, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        drawIconShape(g2, icon);
        g2.dispose();
    }

    private static void drawIconShape(Graphics2D g, Icon icon) {
        switch (icon) {
            case PIN -> {
                Path2D.Float pin = new Path2D.Float(); pin.moveTo(0, 13); pin.curveTo(-19, -3, -8, -13, 0, -13);
                pin.curveTo(8, -13, 19, -3, 0, 13); pin.closePath(); g.draw(pin);
                g.draw(new Ellipse2D.Float(-4, -8, 8, 8));
            }
            case CROSSHAIR -> drawCrosshair(g, 11);
            case PLAYER -> {
                g.draw(new Ellipse2D.Float(-5, -11, 10, 10));
                Path2D body = new Path2D.Float();
                body.moveTo(-12, 12);
                body.curveTo(-9, 3, 9, 3, 12, 12);
                g.draw(body);
            }
            case MOVEMENT -> {
                Path2D.Float arrow = new Path2D.Float();
                arrow.moveTo(-10, 8); arrow.lineTo(10, -8);
                arrow.moveTo(-1, -8); arrow.lineTo(10, -8); arrow.lineTo(10, 3);
                g.draw(arrow);
                g.draw(new Line2D.Float(-11, 1, -5, -4));
                g.draw(new Line2D.Float(-4, 10, 2, 5));
            }
            case EYE -> {
                Path2D path = new Path2D.Float();
                path.moveTo(-13, 0);
                path.curveTo(-6, -9, 6, -9, 13, 0);
                path.curveTo(6, 9, -6, 9, -13, 0);
                g.draw(path);
                g.fill(new Ellipse2D.Float(-4, -4, 8, 8));
            }
            case CUBE -> {
                Path2D path = new Path2D.Float();
                path.moveTo(0, -13);
                path.lineTo(11, -7);
                path.lineTo(11, 7);
                path.lineTo(0, 13);
                path.lineTo(-11, 7);
                path.lineTo(-11, -7);
                path.closePath();
                g.draw(path);
                g.draw(new Line2D.Float(0, 0, 0, 13));
                g.draw(new Line2D.Float(0, 0, 11, -7));
                g.draw(new Line2D.Float(0, 0, -11, -7));
            }
            case GRID -> {
                for (int row = 0; row < 2; row++) {
                    for (int col = 0; col < 2; col++) {
                        g.draw(new RoundRectangle2D.Float(-11 + col * 14, -11 + row * 14, 8, 8, 2, 2));
                    }
                }
            }
            case FOLDER -> {
                Path2D path = new Path2D.Float();
                path.moveTo(-12, -8);
                path.lineTo(-3, -8);
                path.lineTo(1, -4);
                path.lineTo(12, -4);
                path.lineTo(12, 9);
                path.lineTo(-12, 9);
                path.closePath();
                g.draw(path);
            }
            case SETTINGS -> {
                g.draw(new Ellipse2D.Float(-6, -6, 12, 12));
                for (int i = 0; i < 8; i++) {
                    double angle = Math.PI / 4 * i;
                    float sin = (float) Math.sin(angle);
                    float cos = (float) Math.cos(angle);
                    g.draw(new Line2D.Float(cos * 9, sin * 9, cos * 13, sin * 13));
                }
            }
            case HAT -> {
                Path2D cone = new Path2D.Float();
                cone.moveTo(0, -13);
                cone.lineTo(10, 6);
                cone.lineTo(-10, 6);
                cone.closePath();
                g.draw(cone);
                g.draw(new Line2D.Float(-13, 10, 13, 10));
            }
            case RINGS -> {
                g.draw(new Ellipse2D.Float(-12, -2, 24, 11));
                g.draw(new Ellipse2D.Float(-6, 0.5F, 12, 6));
                g.draw(new Line2D.Float(0, -12, 0, -6));
            }
            case EXPAND -> {
                g.draw(new Line2D.Float(-10, 10, -3, 3));
                g.draw(new Line2D.Float(-11, 4, -11, 11));
                g.draw(new Line2D.Float(-4, 11, -11, 11));
                g.draw(new Line2D.Float(10, -10, 3, -3));
                g.draw(new Line2D.Float(11, -4, 11, -11));
                g.draw(new Line2D.Float(4, -11, 11, -11));
            }
            case MUSIC -> {
                g.draw(new Line2D.Float(-3, 7, -3, -11));
                g.draw(new Line2D.Float(-3, -11, 10, -8));
                g.draw(new Line2D.Float(10, -8, 10, 5));
                g.fill(new Ellipse2D.Float(-9.5F, 4, 7, 6));
                g.fill(new Ellipse2D.Float(3.5F, 2, 7, 6));
            }
            case MONITOR -> {
                g.draw(new RoundRectangle2D.Float(-12, -10, 24, 16, 4, 4));
                g.draw(new Line2D.Float(-5, 11, 5, 11));
                g.draw(new Line2D.Float(0, 6, 0, 11));
            }
            case SUN -> {
                g.fill(new Ellipse2D.Float(-4.5F, -4.5F, 9, 9));
                for (int i = 0; i < 8; i++) {
                    double angle = Math.PI / 4 * i;
                    float sin = (float) Math.sin(angle);
                    float cos = (float) Math.cos(angle);
                    g.draw(new Line2D.Float(cos * 8, sin * 8, cos * 12, sin * 12));
                }
            }
            case CHAT -> {
                Path2D bubble = new Path2D.Float();
                bubble.moveTo(-12, -9);
                bubble.lineTo(12, -9);
                bubble.lineTo(12, 6);
                bubble.lineTo(-2, 6);
                bubble.lineTo(-8, 12);
                bubble.lineTo(-8, 6);
                bubble.lineTo(-12, 6);
                bubble.closePath();
                g.draw(bubble);
                g.fill(new Ellipse2D.Float(-6.5F, -3.5F, 4, 4));
                g.fill(new Ellipse2D.Float(2.5F, -3.5F, 4, 4));
            }
        }
    }

    private static void drawCrosshair(Graphics2D g, float radius) {
        g.draw(new Ellipse2D.Float(-radius / 2, -radius / 2, radius, radius));
        g.draw(new Line2D.Float(0, -radius - 5, 0, -radius / 2));
        g.draw(new Line2D.Float(0, radius / 2, 0, radius + 5));
        g.draw(new Line2D.Float(-radius - 5, 0, -radius / 2, 0));
        g.draw(new Line2D.Float(radius / 2, 0, radius + 5, 0));
    }

    private static void drawSparkle(Graphics2D g, float x, float y, float size, Color color, float stroke) {
        g.setColor(color);
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float arm = size * 0.28F;
        Path2D path = new Path2D.Float();
        path.moveTo(x, y - size);
        path.lineTo(x + arm, y - arm);
        path.lineTo(x + size, y);
        path.lineTo(x + arm, y + arm);
        path.lineTo(x, y + size);
        path.lineTo(x - arm, y + arm);
        path.lineTo(x - size, y);
        path.lineTo(x - arm, y - arm);
        path.closePath();
        g.draw(path);
        g.fill(new Ellipse2D.Float(x - 1.5F, y - 1.5F, 3, 3));
    }

    private void updateHover(int localX, int localY) {
        pagePointerX = localX; pagePointerY = localY;
        pageHover = profilePageHover(localX, localY);
        hoveredCategory = categoryAt(localX, localY);
        int module = settingsFor == null && !presetsPage && !configsPage ? moduleAt(localX, localY) : -1;
        hoveredModule = module < 0 ? -1 : module - moduleScroll;
    }

    private int categoryAt(int localX, int localY) {
        for (int i = 0; i < Category.VALUES.length; i++) {
            int y = categoryRowY(i);
            if (isInside(localX, localY, L.sideX, y, L.sideW, paradox ? 18 : L.sideH)) {
                return i;
            }
        }
        return -1;
    }

    private int categoryRowY(int index) {
        return paradox ? PARADOX_CATEGORY_START_Y + index * 22 : L.sideY + index * L.sideStep;
    }

    private int moduleAt(int localX, int localY) {
        ModuleRow[] rows = rowsForSelectedCategory();
        int visible = Math.min(Math.max(0, rows.length - moduleScroll), maxVisibleRows());
        for (int i = 0; i < visible; i++) {
            if (paradox) {
                if (isInside(localX, localY, gridCardX(i), gridCardY(i), gridCardWidth(), GRID_HEIGHT)) return i + moduleScroll;
                continue;
            }
            int y = L.moduleY + i * L.moduleStep;
            if (isInside(localX, localY, L.moduleX, y, L.moduleW, L.moduleH)) {
                return i + moduleScroll;
            }
        }
        return -1;
    }

    private List<OrionConfig.Waypoint> currentWaypoints() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return List.of();
        String world = ru.orion.client.effect.OrionWaypoints.worldKey(client);
        return configManager.getConfig().getWaypoints().stream().filter(point -> world.equals(point.world)).toList();
    }

    private String[] waypointOptions() {
        List<OrionConfig.Waypoint> points = currentWaypoints();
        waypointSelected = Math.max(0, Math.min(waypointSelected, points.size()));
        List<String> labels = new ArrayList<>();
        labels.add("Новая метка");
        points.forEach(point -> labels.add(point.name));
        return labels.toArray(String[]::new);
    }

    private String newWaypointName() {
        var client = Minecraft.getInstance();
        return ru.orion.client.effect.OrionWaypoints.nextName(configManager.getConfig(),
                client.level == null ? "" : ru.orion.client.effect.OrionWaypoints.worldKey(client));
    }

    private void selectWaypoint(int index) {
        List<OrionConfig.Waypoint> points = currentWaypoints();
        waypointSelected = Math.floorMod(index, points.size() + 1);
        if (waypointSelected == 0) {
            waypointName = newWaypointName();
            waypointColor = configManager.getConfig().getWaypointQuickColor();
            waypointCurrentPosition();
            editingText = -1;
            return;
        }
        OrionConfig.Waypoint point = points.get(waypointSelected - 1);
        waypointName = point.name;
        waypointX = Integer.toString(point.x); waypointY = Integer.toString(point.y); waypointZ = Integer.toString(point.z);
        waypointColor = point.color;
        waypointStatus = "Выбрана";
        editingText = -1;
    }

    private void waypointCurrentPosition() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) { waypointStatus = "Войдите в мир"; return; }
        var pos = client.player.blockPosition();
        waypointX = Integer.toString(pos.getX()); waypointY = Integer.toString(pos.getY()); waypointZ = Integer.toString(pos.getZ());
        waypointStatus = "Координаты взяты";
        lastFingerprint = null;
    }

    private void saveWaypoint(boolean replace) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) { waypointStatus = "Войдите в мир"; return; }
        String name = waypointName.trim();
        if (name.isEmpty() || name.length() > 32) { waypointStatus = "Введите название"; return; }
        List<OrionConfig.Waypoint> points = currentWaypoints();
        OrionConfig.Waypoint selected = replace && waypointSelected > 0 && waypointSelected <= points.size() ? points.get(waypointSelected - 1) : null;
        if (replace && selected == null) { waypointStatus = "Нет меток"; return; }
        for (OrionConfig.Waypoint point : points) {
            if (point != selected && point.name.equalsIgnoreCase(name)) { waypointStatus = "Название занято"; return; }
        }
        if (!replace && configManager.getConfig().getWaypoints().size() >= 512) { waypointStatus = "Слишком много меток"; return; }
        int x, y, z;
        try {
            x = Integer.parseInt(waypointX.trim()); y = Integer.parseInt(waypointY.trim()); z = Integer.parseInt(waypointZ.trim());
            if (Math.abs((long) x) > 30000000L || Math.abs((long) z) > 30000000L || y < -2048 || y > 2048) throw new NumberFormatException();
        } catch (NumberFormatException ignored) { waypointStatus = "Неверные координаты"; return; }
        if (selected == null) {
            selected = new OrionConfig.Waypoint(name, ru.orion.client.effect.OrionWaypoints.worldKey(client), x, y, z);
            configManager.getConfig().getWaypoints().add(selected);
            waypointSelected = points.size() + 1;
        } else { selected.name = name; selected.x = x; selected.y = y; selected.z = z; }
        selected.color = waypointColor;
        configManager.getConfig().setWaypointsEnabled(true);
        configManager.save();
        waypointStatus = replace ? "Сохранено" : "Добавлено";
        refreshWaypointSettings();
    }

    private void deleteWaypoint() {
        List<OrionConfig.Waypoint> points = currentWaypoints();
        if (waypointSelected <= 0 || waypointSelected > points.size()) { waypointStatus = "Выберите метку"; return; }
        configManager.getConfig().getWaypoints().remove(points.get(waypointSelected - 1));
        configManager.save();
        waypointSelected = 0;
        waypointStatus = "Удалено";
        refreshWaypointSettings();
    }

    private void refreshWaypointSettings() {
        Setting[] rows = buildSettings(Action.WAYPOINTS);
        settingsRows = waypointPage ? Arrays.stream(rows).filter(row -> !row.label().equals("Сохранённые метки")).toArray(Setting[]::new) : rows;
        if (waypointPage && waypointSelected > 0) {
            waypointListScroll = Math.min(waypointListScroll, waypointSelected - 1);
            waypointListScroll = Math.max(waypointListScroll, waypointSelected - waypointListVisible());
        }
        editingText = -1;
        expandedColor = -1;
        lastFingerprint = null;
    }

    private void toggle(Action action) {
        OrionConfig config = configManager.getConfig();
        switch (action) {
            case WINGS -> configManager.setWingsEnabled(!config.isWingsEnabled());
            case HALO -> configManager.setHaloEnabled(!config.isHaloEnabled());
            case ZOOM -> configManager.setZoomEnabled(!config.isZoomEnabled());
            case CHAT_FILTER -> configManager.setChatFilterEnabled(!config.isChatFilterEnabled());
            case CHAT_ANIMATION -> { config.setChatAnimationEnabled(!config.isChatAnimationEnabled()); configManager.save(); }
            case CHAT_HIGHLIGHT -> configManager.setChatHighlightEnabled(!config.isChatHighlightEnabled());
            case WAYPOINTS -> configManager.setWaypointsEnabled(!config.isWaypointsEnabled());
            case DEATH_POINT -> configManager.setDeathPointEnabled(!config.isDeathPointEnabled());
            case TARGET_ESP -> configManager.setTargetEspEnabled(!config.isTargetEspEnabled());
            case CHINA_HEAD -> configManager.setChinaHeadEnabled(!config.isChinaHeadEnabled());
            case MIRAGE -> configManager.setMirageEnabled(!config.isMirageEnabled());
            case MOTION_BLUR -> configManager.setMotionBlurEnabled(!config.isMotionBlurEnabled());
            case BLOCK_HIGHLIGHT -> configManager.setBlockHighlightEnabled(!config.isBlockHighlightEnabled());
            case ITEM_PHYSICS -> configManager.setItemPhysicsEnabled(!config.isItemPhysicsEnabled());
            case KILL_EFFECTS -> configManager.setKillEffectsEnabled(!config.isKillEffectsEnabled());
            case RAGDOLL -> configManager.setRagdollEnabled(!config.isRagdollEnabled());
            case FRESH_ANIMATIONS -> configManager.setFreshAnimationsEnabled(!config.isFreshAnimationsEnabled());
            case HIT_PARTICLES -> configManager.setHitParticlesEnabled(!config.isHitParticlesEnabled());
            case PROJECTILE_TRAIL -> configManager.setProjectileTrailEnabled(!config.isProjectileTrailEnabled());
            case DAMAGE_INDICATORS -> configManager.setDamageIndicatorsEnabled(!config.isDamageIndicatorsEnabled());
            case BREADCRUMBS -> configManager.setBreadcrumbsEnabled(!config.isBreadcrumbsEnabled());
            case JUMP_CIRCLE -> configManager.setJumpCircleEnabled(!config.isJumpCircleEnabled());
            case ASPECT -> configManager.setAspectEnabled(!config.isAspectEnabled());
            case MEDIA_PLAYER -> configManager.setMediaPlayerEnabled(!config.isMediaPlayerEnabled());
            case HUD -> configManager.setHudEnabled(!config.isHudEnabled());
            case DISCORD -> {
                configManager.setDiscordRpcEnabled(!config.isDiscordRpcEnabled());
                OrionClient.getInstance().getDiscordPresenceService().applyConfigNow();
            }
            case FULL_BRIGHT -> configManager.setFullBrightEnabled(!config.isFullBrightEnabled());
            case WORLD_PARTICLES -> configManager.setWorldParticlesEnabled(!config.isWorldParticlesEnabled());
            case ANIMATIONS -> configManager.setAnimationsEnabled(!config.isAnimationsEnabled());
            case ENCHANT_GLINT -> configManager.setEnchantGlintEnabled(!config.isEnchantGlintEnabled());
            case INVENTORY_HUD -> configManager.setInventoryHudEnabled(!config.isInventoryHudEnabled());
            case SELF_TAG -> configManager.setSelfTagEnabled(!config.isSelfTagEnabled());
            case ARMOR_INDICATOR -> configManager.setArmorIndicatorEnabled(!config.isArmorIndicatorEnabled());
            case FOOTBALL -> configManager.setFootballEnabled(!config.isFootballEnabled());
            case BASKETBALL -> configManager.setBasketballEnabled(!config.isBasketballEnabled());
            case LOCK_SLOT -> configManager.setLockSlotEnabled(!config.isLockSlotEnabled());
            case PVP_SAFE -> configManager.setPvpSafeEnabled(!config.isPvpSafeEnabled());
            case KEYSTROKES -> configManager.setKeystrokesEnabled(!config.isKeystrokesEnabled());
            case NO_ANIMATIONS -> { config.setNoAnimationsEnabled(!config.isNoAnimationsEnabled()); configManager.save(); }
            case SATURATION -> configManager.setSaturationEnabled(!config.isSaturationEnabled());
            case CHAT_HISTORY -> configManager.setChatHistoryEnabled(!config.isChatHistoryEnabled());
            case CHAT_TIME -> configManager.setChatTimeEnabled(!config.isChatTimeEnabled());
            case CUSTOM_TIME -> configManager.setCustomTimeEnabled(!config.isCustomTimeEnabled());
            case ITEM_SCALING -> configManager.setItemScalingEnabled(!config.isItemScalingEnabled());
            case SMALL_SHIELD -> configManager.setSmallShieldEnabled(!config.isSmallShieldEnabled());
            case VIEW_MODEL -> configManager.setViewModelEnabled(!config.isViewModelEnabled());
            case NO_PARTICLES -> configManager.setNoParticlesEnabled(!config.isNoParticlesEnabled());
            case NO_WEATHER -> configManager.setNoWeatherEnabled(!config.isNoWeatherEnabled());
            case NO_FIRE -> configManager.setNoFireEnabled(!config.isNoFireEnabled());
            case WORLD_CHANGER -> configManager.setWorldChangerEnabled(!config.isWorldChangerEnabled());
            case HEALTH_TAGS -> configManager.setHealthTagsEnabled(!config.isHealthTagsEnabled());
            case PROTECT_NAME -> configManager.setProtectNameEnabled(!config.isProtectNameEnabled());
            case FAKE_PREFIX -> configManager.setFakePrefixEnabled(!config.isFakePrefixEnabled());
            case HIT_BOX -> configManager.setHitBoxEnabled(!config.isHitBoxEnabled());
            case HIT_COLOR -> configManager.setHitColorEnabled(!config.isHitColorEnabled());
            case SKIN_LAYERS -> { config.setSkinLayersEnabled(!config.isSkinLayersEnabled()); configManager.save(); }
            case ITEM_SCROLLER -> { config.setItemScrollerEnabled(!config.isItemScrollerEnabled()); configManager.save(); }
            case EMOTES -> { config.setEmotesEnabled(!config.isEmotesEnabled()); configManager.save(); }
            case HIT_SOUND -> configManager.setHitSoundEnabled(!config.isHitSoundEnabled());
            case CROSSHAIR -> configManager.setCrosshairEnabled(!config.isCrosshairEnabled());
            case BETTER_NEAR -> configManager.setBetterNearEnabled(!config.isBetterNearEnabled());
            case RAINBOW -> configManager.setRainbowEnabled(!config.isRainbowEnabled());
        }
        lastFingerprint = null;
    }

    private boolean isActive(Action action) {
        OrionConfig config = configManager.getConfig();
        return switch (action) {
            case WINGS -> config.isWingsEnabled();
            case HALO -> config.isHaloEnabled();
            case ZOOM -> config.isZoomEnabled();
            case CHAT_FILTER -> config.isChatFilterEnabled();
            case CHAT_ANIMATION -> config.isChatAnimationEnabled();
            case CHAT_HIGHLIGHT -> config.isChatHighlightEnabled();
            case WAYPOINTS -> config.isWaypointsEnabled();
            case DEATH_POINT -> config.isDeathPointEnabled();
            case TARGET_ESP -> config.isTargetEspEnabled();
            case CHINA_HEAD -> config.isChinaHeadEnabled();
            case JUMP_CIRCLE -> config.isJumpCircleEnabled();
            case PROJECTILE_TRAIL -> config.isProjectileTrailEnabled();
            case DAMAGE_INDICATORS -> config.isDamageIndicatorsEnabled();
            case BREADCRUMBS -> config.isBreadcrumbsEnabled();
            case MIRAGE -> config.isMirageEnabled();
            case MOTION_BLUR -> config.isMotionBlurEnabled();
            case BLOCK_HIGHLIGHT -> config.isBlockHighlightEnabled();
            case ITEM_PHYSICS -> config.isItemPhysicsEnabled();
            case KILL_EFFECTS -> config.isKillEffectsEnabled();
            case RAGDOLL -> config.isRagdollEnabled();
            case FRESH_ANIMATIONS -> config.isFreshAnimationsEnabled();
            case HIT_PARTICLES -> config.isHitParticlesEnabled();
            case ASPECT -> config.isAspectEnabled();
            case MEDIA_PLAYER -> config.isMediaPlayerEnabled();
            case HUD -> config.isHudEnabled();
            case DISCORD -> config.isDiscordRpcEnabled();
            case FULL_BRIGHT -> config.isFullBrightEnabled();
            case WORLD_PARTICLES -> config.isWorldParticlesEnabled();
            case ANIMATIONS -> config.isAnimationsEnabled();
            case ENCHANT_GLINT -> config.isEnchantGlintEnabled();
            case INVENTORY_HUD -> config.isInventoryHudEnabled();
            case SELF_TAG -> config.isSelfTagEnabled();
            case ARMOR_INDICATOR -> config.isArmorIndicatorEnabled();
            case FOOTBALL -> config.isFootballEnabled();
            case BASKETBALL -> config.isBasketballEnabled();
            case LOCK_SLOT -> config.isLockSlotEnabled();
            case PVP_SAFE -> config.isPvpSafeEnabled();
            case KEYSTROKES -> config.isKeystrokesEnabled();
            case NO_ANIMATIONS -> config.isNoAnimationsEnabled();
            case SATURATION -> config.isSaturationEnabled();
            case CHAT_HISTORY -> config.isChatHistoryEnabled();
            case CHAT_TIME -> config.isChatTimeEnabled();
            case CUSTOM_TIME -> config.isCustomTimeEnabled();
            case ITEM_SCALING -> config.isItemScalingEnabled();
            case SMALL_SHIELD -> config.isSmallShieldEnabled();
            case VIEW_MODEL -> config.isViewModelEnabled();
            case NO_PARTICLES -> config.isNoParticlesEnabled();
            case NO_WEATHER -> config.isNoWeatherEnabled();
            case NO_FIRE -> config.isNoFireEnabled();
            case WORLD_CHANGER -> config.isWorldChangerEnabled();
            case HEALTH_TAGS -> config.isHealthTagsEnabled();
            case PROTECT_NAME -> config.isProtectNameEnabled();
            case FAKE_PREFIX -> config.isFakePrefixEnabled();
            case HIT_BOX -> config.isHitBoxEnabled();
            case HIT_COLOR -> config.isHitColorEnabled();
            case SKIN_LAYERS -> config.isSkinLayersEnabled();
            case ITEM_SCROLLER -> config.isItemScrollerEnabled();
            case EMOTES -> config.isEmotesEnabled();
            case HIT_SOUND -> config.isHitSoundEnabled();
            case CROSSHAIR -> config.isCrosshairEnabled();
            case BETTER_NEAR -> config.isBetterNearEnabled();
            case RAINBOW -> config.isRainbowEnabled();
        };
    }

    private ModuleRow[] rowsForSelectedCategory() {
        if (!searchQuery.isBlank()) {
            String query = searchQuery.toLowerCase(Locale.ROOT).trim();
            return Arrays.stream(ModuleRow.ALL)
                    .filter(row -> row.title.toLowerCase(Locale.ROOT).contains(query)
                            || tr(row.title).toLowerCase(Locale.ROOT).contains(query)
                            || (row.description.toLowerCase(Locale.ROOT).contains(query) || tr(row.description).toLowerCase(Locale.ROOT).contains(query)))
                    .toArray(ModuleRow[]::new);
        }
        if (paradox && paradoxFavoritesOnly) return sortedThemeRows(Arrays.stream(ModuleRow.ALL).filter(row -> paradoxFavorites.contains(row.action)).toArray(ModuleRow[]::new));
        if (paradox && paradoxAll) return sortedThemeRows(ModuleRow.ALL);
        ModuleRow[] rows = switch (Category.VALUES[selectedCategory]) {
            case COMBAT -> ModuleRow.COMBAT;
            case PLAYER -> ModuleRow.PLAYER;
            case MOVEMENT -> ModuleRow.MOVEMENT;
            case RENDER -> ModuleRow.RENDER;
            case WORLD -> ModuleRow.WORLD;
            case MISC -> ModuleRow.MISC;
            case SETTINGS -> ModuleRow.SETTINGS;
        };
        return sortedThemeRows(rows);
    }

    private ModuleRow[] sortedThemeRows(ModuleRow[] rows) {
        if (!paradox) return rows;
        return Arrays.stream(rows).sorted(java.util.Comparator.comparing(row -> row.title.toLowerCase(Locale.ROOT))).toArray(ModuleRow[]::new);
    }

    private void updateAnimations() {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0.0F : (now - lastFrameNanos) / 1_000_000_000.0F;
        lastFrameNanos = now;
        if (paradox) {
            List<Object> identity = List.of(configManager.getConfig().getClickGuiTheme(), selectedCategory, paradoxAll, paradoxFavoritesOnly, waypointPage, presetsPage, configsPage, searchQuery,
                    settingsFor == null ? "" : settingsFor.action.name());
            if (!identity.equals(pageIdentity)) {
                pageIdentity = identity; pageStartedAt = now; pageAnimation = 0F; scrollAnimation = settingsScroll;
                settingToggleAnimation = new float[0];
            }
            pageAnimation = Math.min(1F, (now - pageStartedAt) / 200_000_000F);
            cardsAnimation = Math.min(1F, (now - pageStartedAt) / 600_000_000F);
            float weight = 1F - (float)Math.exp(-20F * Math.min(dt, .1F));
            scrollAnimation += (settingsScroll - scrollAnimation) * weight;
            if (Math.abs(settingsScroll - scrollAnimation) < .3F) scrollAnimation = settingsScroll;
            if (settingToggleAnimation.length != settingsRows.length) {
                settingToggleAnimation = new float[settingsRows.length];
                for (int i = 0; i < settingsRows.length; i++) {
                    if (settingsRows[i] instanceof ToggleSetting toggle) settingToggleAnimation[i] = toggle.get.get() ? 1F : 0F;
                }
            }
            for (int i = 0; i < settingsRows.length; i++) {
                if (settingsRows[i] instanceof ToggleSetting toggle) settingToggleAnimation[i] = approach(settingToggleAnimation[i], toggle.get.get() ? 1F : 0F, dt * 7F);
            }
        }
        float toggleStep = Math.min(1.0F, dt * 1000.0F / (paradox ? 280 : TOGGLE_ANIMATION_MS));
        float hoverStep = Math.min(1.0F, dt * 1000.0F / HOVER_ANIMATION_MS);

        for (Action action : Action.VALUES) {
            int i = action.ordinal();
            toggleAnim[i] = approach(toggleAnim[i], isActive(action) ? 1.0F : 0.0F, toggleStep);
        }
        for (int i = 0; i < categoryHoverAnim.length; i++) {
            categoryHoverAnim[i] = approach(categoryHoverAnim[i], i == hoveredCategory ? 1.0F : 0.0F, hoverStep);
        }
        for (int i = 0; i < moduleHoverAnim.length; i++) {
            moduleHoverAnim[i] = approach(moduleHoverAnim[i], i == hoveredModule ? 1.0F : 0.0F, hoverStep);
        }
    }

    private List<Integer> fingerprint(int scale) { return fingerprint(scale, false); }

    private List<Integer> fingerprint(int scale, boolean contentOnly) {
        List<Integer> values = new ArrayList<>(32);
        values.add(scale);
        values.add(OrionGuiLanguage.english() ? 1 : 0);
        values.add(guiDisplayWidth());
        values.add(guiDisplayHeight());
        values.add(configManager.getConfig().getClickGuiTheme());
        values.add(selectedCategory);
        if (!contentOnly) {
            values.add(java.util.Objects.hash(accounts.username(), accounts.avatar(), accountsOpen, accountFooterHit(pagePointerX, pagePointerY)));
            if (accountsOpen) values.add(java.util.Objects.hash(accountLogin, accountPassword.length(), accountField,
                    textCaret, textAnchor, accountStatus, accounts.badge(), accounts.plusActive(), accounts.busy(), accounts.usernames(),
                    accountForm, accountOffset, accountTarget(pagePointerX, pagePointerY),
                    accountField != 0 && System.currentTimeMillis() / 500L % 2L == 0));
        }
        if (paradox) {
            values.add(contentOnly ? 0 : Math.round(pageAnimation * 255));
            values.add(contentOnly ? 0 : Math.round(cardsAnimation * 255));
            values.add(contentOnly ? 0 : usesSettingsLayer() ? selectedSettingsGroup() : Math.round(scrollAnimation));
            for (float value : settingToggleAnimation) values.add(Math.round(value * 255));
        }
        values.add(paradoxAll ? 1 : 0);
        values.add(paradoxFavoritesOnly ? 1 : 0);
        values.add(waypointPage ? 1 : 0);
        values.add(presetsPage ? 1 : 0);
        values.add(configsPage ? 1 : 0);
        if (configsPage) values.add(java.util.Objects.hash(configsLocal, configBusy, configOffset, configStatus, configTitle,
                configTitleFocused, configsPublished, configSelected, configEditor, configDeleteConfirm, catalogTotal, pageHover, textCaret, textAnchor, configTitleFocused && System.currentTimeMillis() / 500L % 2L == 0));
        values.add(selectedPreset);
        if (presetsPage) values.add(pageHover);
        if (presetsPage) {
            values.add(configManager.getConfig().getAppliedVisualPreset().hashCode());
            values.add(configManager.getVisualPresets().canRestore() ? 1 : 0);
        }
        values.add(waypointListScroll);
        values.add(waypointSelected);
        if (waypointPage) for (var point : currentWaypoints()) {
            values.add(java.util.Objects.hash(point.name, point.x, point.y, point.z, point.color));
        }
        values.add(paradoxFavorites.hashCode());
        values.add(hoveredCategory);
        values.add(hoveredModule);
        for (Action action : Action.VALUES) {
            values.add(isActive(action) ? 1 : 0);
            values.add(Math.round(toggleAnim[action.ordinal()] * 255));
        }
        for (float hover : categoryHoverAnim) {
            values.add(Math.round(hover * 255));
        }
        for (float hover : moduleHoverAnim) {
            values.add(Math.round(hover * 255));
        }
        values.add(settingsFor == null ? -1 : settingsFor.action.ordinal());
        values.add(usesSettingsLayer() ? 0 : settingsScroll);
        values.add(moduleScroll);
        values.add(searchQuery.hashCode());
        values.add(searchFocused ? 1 : 0);
        values.add(searchFocused ? (int) ((System.currentTimeMillis() / 500L) % 2L) : 0);
        for (Setting setting : settingsRows) {
            if (setting instanceof ChoiceSetting choice) {
                values.add(choice.get.getAsInt());
            } else if (setting instanceof SliderSetting slider) {
                values.add(Math.round(slider.get.get() * 1000));
            } else if (setting instanceof KeybindSetting bind) {
                KeyMapping key = bind.get.get();
                values.add(key == null ? 0 : key.saveString().hashCode());
            } else if (setting instanceof ColorSetting color) {
                values.add(color.get.get().hashCode());
            } else if (setting instanceof TextSetting text) {
                values.add(text.get.get().hashCode());
            } else if (setting instanceof ToggleSetting toggle) {
                values.add(toggle.get.get() ? 1 : 0);
            } else if (setting instanceof ValueSetting value) {
                values.add(value.get.get().hashCode());
            }
        }
        values.add(editingText);
        values.add(editingBind);
        values.add(textCaret);
        values.add(textAnchor);
        values.add(Math.round(textScrollX * 10));
        values.add(editingText >= 0 ? (int) ((System.currentTimeMillis() / 500L) % 2L) : -1);
        values.add(expandedColor);
        values.add(Math.round(pickerHue * 1000.0F));
        values.add(Math.round(pickerSat * 1000.0F));
        values.add(Math.round(pickerVal * 1000.0F));
        return values;
    }

    private void applyPalette() {
        int theme = configManager.getConfig().getClickGuiTheme();
        paradox = theme == 3 || theme == 4;
        orion3 = theme == 2 || paradox;
        L = paradox ? (settingsFor != null ? PARADOX_LAYOUT : PARADOX_MODULE_LAYOUT) : orion3 ? ORION3_LAYOUT : CLASSIC_LAYOUT;
        sc = orion3 ? 0.85F : 1.0F;
        pal = switch (theme) {
            case 3 -> PARADOX_PALETTE;
            case 4 -> PARADOX_LIGHT_PALETTE;
            case 1 -> PRIZRAK_PALETTE;
            case 2 -> ORION3_PALETTE;
            default -> ORION_PALETTE;
        };
        PRIMARY = pal.accent;
        PRIMARY_DARK = pal.accentDark;
        BG = pal.bg;
        SIDEBAR_BG = pal.sidebar;
        SURFACE = pal.surface;
        SURFACE_HOVER = pal.surfaceHover;
        BORDER = pal.border;
        TEXT = pal.text;
        MUTED = pal.muted;
        SEARCH_BG = pal.inset;
        PLACEHOLDER = pal.placeholder;
    }

    private int guiX() {
        return Math.max(0, (width - guiDisplayWidth()) / 2);
    }

    private int guiY() {
        return Math.max(0, (height - guiDisplayHeight()) / 2);
    }

    private float displayFactor() {
        float factor = switch (configManager.getConfig().getClickGuiTheme()) {
            case 1 -> ORION2_TARGET_SCALE;
            case 2 -> ORION3_TARGET_SCALE;
            case 3 -> 0.94F;
            case 4 -> 0.94F;
            default -> ORION_TARGET_SCALE;
        };
        if (width > 0 && height > 0) {
            // Не выпускаем окно за экран (при крупном gui scale места меньше).
            factor = Math.min(factor, (width - SCREEN_FIT_MARGIN) / (float) L.guiW);
            factor = Math.min(factor, (height - SCREEN_FIT_MARGIN) / (float) L.guiH);
        }
        return Math.max(paradox ? 0.1F : 0.4F, factor);
    }

    private int guiDisplayWidth() {
        return Math.max(1, Math.round(L.guiW * displayFactor()));
    }

    private int guiDisplayHeight() {
        return Math.max(1, Math.round(L.guiH * displayFactor()));
    }

    private int toLocalX(double screenX) {
        return (int) Math.floor((screenX - guiX()) * L.guiW / (double) guiDisplayWidth());
    }

    private int toLocalY(double screenY) {
        return (int) Math.floor((screenY - guiY()) * L.guiH / (double) guiDisplayHeight());
    }

    private static void setupGraphics(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private Font headingFont(float size) {
        if (paradox) return OrionInterfaceFonts.face(3,Math.max(10F,size*1.25F));
        Font font = pickFont(size, Font.PLAIN, "Inter SemiBold", "Inter Semi Bold", "Segoe UI Semibold");
        if (font != null) {
            return font;
        }
        font = pickFont(size, Font.BOLD, "Inter", "SF Pro Display", "Segoe UI");
        return font != null ? font : new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size)).deriveFont(Font.BOLD, size);
    }

    private Font bodyFont(float size) {
        if (paradox) return OrionInterfaceFonts.face(1,Math.max(9F,size*1.3F));
        Font font = pickFont(size, Font.PLAIN, "Inter", "SF Pro Display", "Segoe UI");
        return font != null ? font : new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(size)).deriveFont(Font.PLAIN, size);
    }

    private static Font pickFont(float size, int style, String... families) {
        for (String family : families) {
            Font font = new Font(family, style, Math.round(size));
            if (font.getFamily().equalsIgnoreCase(family) && font.canDisplay('Я') && font.canDisplay('ё')) {
                return font.deriveFont(style, size);
            }
        }
        return null;
    }

    private static String tr(String text) { return OrionGuiLanguage.translate(text); }

    private static void drawText(Graphics2D g, String text, float x, float baseline, Font font, Color color, boolean shadow) {
        g.setFont(font);
        if (shadow) {
            g.setColor(new Color(0, 0, 0, 90));
            g.drawString(text, x + 0.7F, baseline + 0.9F);
        }
        g.setColor(color);
        g.drawString(text, x, baseline);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static Color lighten(Color color, int amount) {
        return new Color(Math.min(255, color.getRed() + amount), Math.min(255, color.getGreen() + amount),
                Math.min(255, color.getBlue() + amount), color.getAlpha());
    }

    private static Color darken(Color color, int amount) {
        return new Color(Math.max(0, color.getRed() - amount), Math.max(0, color.getGreen() - amount),
                Math.max(0, color.getBlue() - amount), color.getAlpha());
    }

    private static Color mix(Color from, Color to, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return new Color(Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t),
                Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * t));
    }

    private static float approach(float current, float target, float step) {
        return current < target ? Math.min(target, current + step) : Math.max(target, current - step);
    }

    private static float easeInOutCubic(float value) {
        if (value < 0.5F) {
            return 4.0F * value * value * value;
        }
        float f = -2.0F * value + 2.0F;
        return 1.0F - f * f * f / 2.0F;
    }

    private static boolean isInside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static String ellipsize(Graphics2D g, Font font, String text, float maxWidth) {
        if (textWidth(g, font, text) <= maxWidth) return text;
        String ellipsis = "…";
        int low = 0, high = text.codePointCount(0, text.length());
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            int end = text.offsetByCodePoints(0, middle);
            if (textWidth(g, font, text.substring(0, end) + ellipsis) <= maxWidth) low = middle;
            else high = middle - 1;
        }
        return text.substring(0, text.offsetByCodePoints(0, low)) + ellipsis;
    }

    private record TextMeasure(Font font, String text, java.awt.font.FontRenderContext context) { }
    private static final java.util.concurrent.ConcurrentHashMap<TextMeasure, Float> TEXT_WIDTHS = new java.util.concurrent.ConcurrentHashMap<>();

    private static float textWidth(Graphics2D g, Font font, String text) {
        TextMeasure key = new TextMeasure(font, text, g.getFontRenderContext());
        Float cached = TEXT_WIDTHS.get(key);
        if (cached != null) return cached;
        Font measuring = font.deriveFont(font.getSize2D() * 16F);
        float value = (float) g.getFontMetrics(measuring).getStringBounds(text, g).getWidth() / 16F;
        if (TEXT_WIDTHS.size() > 8192) TEXT_WIDTHS.clear();
        TEXT_WIDTHS.put(key, value);
        return value;
    }

    private enum Category {
        COMBAT("Бой", Icon.CROSSHAIR),
        PLAYER("Игрок", Icon.PLAYER),
        MOVEMENT("Движение", Icon.MOVEMENT),
        RENDER("Визуал", Icon.EYE),
        WORLD("Мир", Icon.CUBE),
        MISC("Разное", Icon.GRID),
        SETTINGS("Настройки", Icon.SETTINGS);

        private static final Category[] VALUES = values();
        private final String title;
        private final Icon icon;

        Category(String title, Icon icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private enum Icon {
        CROSSHAIR, PLAYER, MOVEMENT, EYE, CUBE, GRID, PIN, FOLDER, SETTINGS, HAT, RINGS, EXPAND, MUSIC, MONITOR, SUN, CHAT
    }

    private sealed interface Setting permits ChoiceSetting, SliderSetting, ColorSetting, ToggleSetting, TextSetting, KeybindSetting,
            ButtonSetting, ValueSetting {
        String label();
    }

    private record ButtonSetting(String label, String buttonText, Runnable onClick) implements Setting {
    }
    private record ValueSetting(String label, Supplier<String> get) implements Setting { }

    private record TextSetting(String label, Supplier<String> get, Consumer<String> set, int maxLength)
            implements Setting {
    }

    private record KeybindSetting(String label, Supplier<KeyMapping> get) implements Setting { }

    private record ChoiceSetting(String label, String[] options, IntSupplier get, IntConsumer set) implements Setting {
    }

    private record SliderSetting(String label, float min, float max, Supplier<Float> get, Consumer<Float> set,
                                 String suffix, boolean integer) implements Setting {
        private SliderSetting(String label, float min, float max, Supplier<Float> get, Consumer<Float> set,
                              String suffix) {
            this(label, min, max, get, set, suffix, false);
        }
    }

    private record ColorSetting(String label, String[] presets, Supplier<String> get,
                                Consumer<String> set) implements Setting {
    }

    private record ToggleSetting(String label, Supplier<Boolean> get, Consumer<Boolean> set) implements Setting {
    }

    private enum Action {
        MIRAGE, WINGS, HALO, SKIN_LAYERS, ITEM_SCROLLER, EMOTES, ZOOM, CHAT_FILTER, CHAT_ANIMATION, CHAT_HIGHLIGHT, WAYPOINTS, DEATH_POINT, TARGET_ESP, CHINA_HEAD, JUMP_CIRCLE, BREADCRUMBS, PROJECTILE_TRAIL, DAMAGE_INDICATORS, MOTION_BLUR, BLOCK_HIGHLIGHT, ITEM_PHYSICS, HIT_PARTICLES, ASPECT, MEDIA_PLAYER, HUD, DISCORD, FULL_BRIGHT,
        WORLD_PARTICLES, FOOTBALL, BASKETBALL, ANIMATIONS, ENCHANT_GLINT, INVENTORY_HUD, SELF_TAG, ARMOR_INDICATOR, LOCK_SLOT, PVP_SAFE, KEYSTROKES, NO_ANIMATIONS, SATURATION, CHAT_HISTORY, CHAT_TIME, CUSTOM_TIME, ITEM_SCALING, SMALL_SHIELD, VIEW_MODEL, NO_PARTICLES, NO_WEATHER, NO_FIRE, WORLD_CHANGER, HEALTH_TAGS, PROTECT_NAME, FAKE_PREFIX,
        HIT_BOX, HIT_COLOR, HIT_SOUND, CROSSHAIR, BETTER_NEAR, RAINBOW, KILL_EFFECTS, RAGDOLL, FRESH_ANIMATIONS;

        private static final Action[] VALUES = values();
    }

    private record ModuleRow(String title, String description, Icon icon, Action action) {
        private static final ModuleRow[] COMBAT = {
                new ModuleRow("TargetEsp", "Эффект на цели под прицелом", Icon.CROSSHAIR, Action.TARGET_ESP),
                new ModuleRow("HitBox", "Перекрашивает хитбоксы (F3+B)", Icon.CUBE, Action.HIT_BOX),
                new ModuleRow("HitColor", "Заливка хитбокса при ударе", Icon.CROSSHAIR, Action.HIT_COLOR),
                new ModuleRow("HitSound", "Звук подтверждённого попадания", Icon.MUSIC, Action.HIT_SOUND),
                new ModuleRow("BetterNear", "Игроки рядом: ник, дистанция, стрелка", Icon.PLAYER, Action.BETTER_NEAR),
                new ModuleRow("ArmorIndicator", "Броня игроков над головой", Icon.PLAYER, Action.ARMOR_INDICATOR),
                new ModuleRow("HitParticles", "Искры при ударе", Icon.GRID, Action.HIT_PARTICLES),
                new ModuleRow("KillEffect", "Объёмные эффекты и звуки после убийства", Icon.GRID, Action.KILL_EFFECTS),
                new ModuleRow("Ragdoll", "Игрок падает после убийства и уходит под землю", Icon.PLAYER, Action.RAGDOLL),
                new ModuleRow("DamageIndicators", "Числа урона и лечения над целями", Icon.CROSSHAIR, Action.DAMAGE_INDICATORS),
                new ModuleRow("PvpSafe", "Предупреждает о выходе во время боя", Icon.CROSSHAIR, Action.PVP_SAFE)
        };
        private static final ModuleRow[] PLAYER = {
                new ModuleRow("Fresh Animations", "Выразительные взмахи рук и более живой шаг", Icon.PLAYER, Action.FRESH_ANIMATIONS),
                new ModuleRow("3D Skin Layers", "Объёмный внешний слой скина", Icon.PLAYER, Action.SKIN_LAYERS),
                new ModuleRow("Item Scroller", "Перенос предметов колесом мыши", Icon.GRID, Action.ITEM_SCROLLER),
                new ModuleRow("Танцы", "Синхронные танцы для пользователей Orion", Icon.PLAYER, Action.EMOTES),
                new ModuleRow("Wings", "Объёмные крылья для пользователей Orion", Icon.PLAYER, Action.WINGS),
                new ModuleRow("Halo", "Золотой нимб для пользователей Orion", Icon.RINGS, Action.HALO),
                new ModuleRow("ChinaHead", "Рисует шляпу над головой", Icon.HAT, Action.CHINA_HEAD),
                new ModuleRow("Aspect", "Растягивает обзор (stretch)", Icon.EXPAND, Action.ASPECT),
                new ModuleRow("ProtectName", "Прячет ваш ник за псевдонимом", Icon.PLAYER, Action.PROTECT_NAME),
                new ModuleRow("FakePrefix", "Заменяет ваш донат-префикс", Icon.PLAYER, Action.FAKE_PREFIX),
                new ModuleRow("LockSlot", "Защищает выбранные слоты", Icon.GRID, Action.LOCK_SLOT),
                new ModuleRow("SelfTag", "Ник над вами в третьем лице", Icon.PLAYER, Action.SELF_TAG)
        };
        private static final ModuleRow[] MOVEMENT = {
                new ModuleRow("JumpCircle", "Кольцо на земле при прыжке", Icon.RINGS, Action.JUMP_CIRCLE),
                new ModuleRow("Breadcrumbs", "Светящийся след за игроком", Icon.RINGS, Action.BREADCRUMBS)
        };
        private static final ModuleRow[] RENDER = {
                new ModuleRow("Zoom", "Управляемое приближение камеры", Icon.EYE, Action.ZOOM),
                new ModuleRow("RainbowColor", "Все цвета переливаются радугой", Icon.SUN, Action.RAINBOW),
                new ModuleRow("Crosshair", "Свой прицел: рисуется в редакторе", Icon.CROSSHAIR, Action.CROSSHAIR),
                new ModuleRow("FullBright", "Повышает яркость мира", Icon.SUN, Action.FULL_BRIGHT),
                new ModuleRow("HUD", "Отображает интерфейс клиента", Icon.MONITOR, Action.HUD),
                new ModuleRow("Keystrokes", "Показывает нажатые клавиши", Icon.GRID, Action.KEYSTROKES),
                new ModuleRow("InventoryHUD", "Содержимое инвентаря на экране", Icon.GRID, Action.INVENTORY_HUD),
                new ModuleRow("Saturation", "Показывает насыщение и эффект еды", Icon.SUN, Action.SATURATION),
                new ModuleRow("NoAnimations", "Фиксирует позы других игроков", Icon.PLAYER, Action.NO_ANIMATIONS),
                new ModuleRow("Health Indicator", "Показывает сердечки игроков", Icon.PLAYER, Action.HEALTH_TAGS),
                new ModuleRow("NoFire", "Убирает огонь с экрана полностью", Icon.EYE, Action.NO_FIRE),
                new ModuleRow("NoParticles", "Скрывает лишние частицы", Icon.GRID, Action.NO_PARTICLES),
                new ModuleRow("Animations", "Плавность хотбара и инвентаря", Icon.GRID, Action.ANIMATIONS),
                new ModuleRow("ItemScaling", "Размер предмета в руке", Icon.CUBE, Action.ITEM_SCALING),
                new ModuleRow("SmallShield", "Уменьшает щит", Icon.CUBE, Action.SMALL_SHIELD),
                new ModuleRow("EnchantGlint", "Переливание зачарованных вещей", Icon.SUN, Action.ENCHANT_GLINT),
                new ModuleRow("ViewModel", "Положение рук", Icon.PLAYER, Action.VIEW_MODEL)
        };
        private static final ModuleRow[] WORLD = {
                new ModuleRow("Waypoints", "Метки мира и управление ими", Icon.RINGS, Action.WAYPOINTS),
                new ModuleRow("DeathPoint", "Метка последнего места смерти", Icon.RINGS, Action.DEATH_POINT),
                new ModuleRow("NoWeather", "Отключает дождь и снег", Icon.CUBE, Action.NO_WEATHER),
                new ModuleRow("WorldChanger", "Меняет цвет неба и тумана", Icon.EYE, Action.WORLD_CHANGER),
                new ModuleRow("CustomTime", "Свое время суток", Icon.SUN, Action.CUSTOM_TIME),
                new ModuleRow("WorldParticles", "Добавляет частицы в мире", Icon.GRID, Action.WORLD_PARTICLES),
                new ModuleRow("ProjectileTrail", "След стрел, перлов и трезубцев", Icon.RINGS, Action.PROJECTILE_TRAIL),
                new ModuleRow("BlockHighlight", "Плавный контур блока", Icon.CUBE, Action.BLOCK_HIGHLIGHT),
                new ModuleRow("ItemPhysics", "Предметы лежат на земле", Icon.CUBE, Action.ITEM_PHYSICS)
        };
        private static final ModuleRow[] MISC = {
                new ModuleRow("ChatFilter", "Скрывает сообщения по словам", Icon.CHAT, Action.CHAT_FILTER),
                new ModuleRow("ChatHighlight", "Подсвечивает слова в чате", Icon.CHAT, Action.CHAT_HIGHLIGHT),
                new ModuleRow("Basketball", "Клиентский баскетбол", Icon.RINGS, Action.BASKETBALL),
                new ModuleRow("Football", "Клиентский футбол", Icon.RINGS, Action.FOOTBALL),
                new ModuleRow("Discord", "Показывает статус в Discord", Icon.CHAT, Action.DISCORD),
                new ModuleRow("ChatAnimation", "Анимирует сообщения и поле ввода", Icon.CHAT, Action.CHAT_ANIMATION),
                new ModuleRow("ChatTime", "Время перед сообщениями", Icon.CHAT, Action.CHAT_TIME),
                new ModuleRow("ChatHistory", "Сохраняет чат при переподключении", Icon.CHAT, Action.CHAT_HISTORY),
                new ModuleRow("MediaPlayer", "Виджет проигрываемой музыки", Icon.MUSIC, Action.MEDIA_PLAYER),
                new ModuleRow("Mirage", "Мягкие отражения и выразительный свет", Icon.EYE, Action.MIRAGE),
                new ModuleRow("MotionBlur", "Размытие при движении камеры", Icon.EYE, Action.MOTION_BLUR)
        };
        private static final ModuleRow[] SETTINGS = {};

        private static final ModuleRow[] ALL;

        static {
            List<ModuleRow> all = new ArrayList<>();
            for (ModuleRow[] rows : new ModuleRow[][]{COMBAT, PLAYER, MOVEMENT, RENDER, WORLD, MISC}) {
                all.addAll(Arrays.asList(rows));
            }
            ALL = all.toArray(ModuleRow[]::new);
        }
    }
}
