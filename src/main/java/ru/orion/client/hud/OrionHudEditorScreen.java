package ru.orion.client.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.gui.OrionGuiLanguage;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public final class OrionHudEditorScreen extends Screen {
    private static final String PANEL = "#0f1216eb", BORDER = "#edf7f226", TEXT = "#eef6f1";
    private static final String MUTED = "#aab7b0", ACCENT = "#65d7a0", LINE = "#65d7a0b3";
    private static final int BAR_Y = 10, BAR_H = 19, ROW = 17, HANDLE = 8, PANEL_WIDTH = 136;
    private static final int PANEL_Y = 35, INSPECTOR_W = 156;
    private static final int[] STEPS = {8, 16, 24, 32};

    private record Widget(String id, String name, Predicate<OrionConfig> enabled,
                          BiConsumer<OrionConfig, Boolean> toggle) {
        String regionId() { return id.equals("media_player") ? "island" : id; }
    }
    private static final List<Widget> WIDGETS = List.of(
            new Widget("island", "HUD", OrionConfig::isHudEnabled, OrionConfig::setHudEnabled),
            new Widget("media_player", "MediaPlayer", OrionConfig::isMediaPlayerEnabled, OrionConfig::setMediaPlayerEnabled),
            new Widget("keystrokes", "Keystrokes", OrionConfig::isKeystrokesEnabled, OrionConfig::setKeystrokesEnabled),
            new Widget(OrionPotionsHud.ID, "Potions", OrionConfig::isPotionsEnabled, OrionConfig::setPotionsEnabled),
            new Widget(OrionTargetHud.ID, "TargetHud", OrionConfig::isTargetHudEnabled, OrionConfig::setTargetHudEnabled),
            new Widget("nearby", "BetterNear", OrionConfig::isBetterNearEnabled, OrionConfig::setBetterNearEnabled),
            new Widget("inventory", "InventoryHUD", OrionConfig::isInventoryHudEnabled, OrionConfig::setInventoryHudEnabled),
            new Widget("football_hud", "Football", OrionConfig::isFootballEnabled, OrionConfig::setFootballEnabled),
            new Widget("basketball_hud", "Basketball", c -> c.isBasketballEnabled() && c.isBasketballHud(),
                    (c, enabled) -> {
                        c.setBasketballEnabled(enabled);
                        if (enabled) c.setBasketballHud(true);
                    })
    );

    private record Snap(int position, int line, OrionHudDrag.Region peer) {}
    private record Gesture(OrionHudDrag.Region region, double x, double y, float scale, boolean resize) {}
    private record Inspector(OrionHudDrag.Region widget, int x, int y, int width, int rows) {
        int height() { return rows == 5 ? 119 : 48; }
        boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height();
        }
    }

    private final OrionConfigManager manager;
    private final OrionConfig config;
    private final ChatScreen chat;
    private final OrionHudView overlay = new OrionHudView("hud_editor");
    private List<OrionHudDrag.Region> regions = List.of();
    private String selected;
    private Gesture gesture;
    private Snap guideX, guideY;
    private boolean preview, catalog, inspector, changed;
    private boolean moving, freeGesture;
    private int handDrag;
    private double handStartX, handStartY;
    private float handOffsetX, handOffsetY;
    private int sliderRow = -1;
    private int inspectorX = -1, inspectorY;
    private Inspector closingInspector;
    private final float[] inspectorToggles = new float[3];
    private float catalogProgress, inspectorProgress, gridProgress;
    private long animationAt;
    private long openedAt;
    private long gridUntil;

    private OrionHudEditorScreen(OrionConfigManager manager) {
        this(manager, null);
    }

    private OrionHudEditorScreen(OrionConfigManager manager, ChatScreen chat) {
        super(Component.literal(tr("Редактор HUD")));
        this.manager = manager;
        this.config = manager.getConfig();
        this.chat = chat;
    }

    private static OrionHudEditorScreen chatEditor;

    public static void registerChat(OrionConfigManager manager) {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof ChatScreen chat) || client.player == null || client.level == null) return;
            if (chatEditor != null) chatEditor.removed();
            var editor = new OrionHudEditorScreen(manager, chat);
            chatEditor = editor;
            editor.width = w;
            editor.height = h;
            ScreenEvents.afterExtract(screen).register((current, graphics, x, y, delta) ->
                    { if (chatEditor == editor) editor.drawEditor(graphics, x, y); });
            ScreenEvents.remove(screen).register(current -> {
                if (chatEditor == editor) {
                    chatEditor = null;
                    editor.removed();
                }
            });
            ScreenMouseEvents.allowMouseClick(screen).register((current, event) ->
                    chatEditor != editor || !editor.mouseClicked(event, false));
            ScreenMouseEvents.allowMouseDrag(screen).register((current, event, dx, dy) ->
                    chatEditor != editor || !editor.pointerCaptured() || !editor.mouseDragged(event, dx, dy));
            ScreenMouseEvents.allowMouseRelease(screen).register((current, event) ->
                    chatEditor != editor || !editor.pointerCaptured() || !editor.mouseReleased(event));
            ScreenMouseEvents.allowMouseScroll(screen).register((current, x, y, dx, dy) ->
                    chatEditor != editor || !editor.mouseScrolled(x, y, dx, dy));
        });
    }

    public static void open(OrionConfigManager manager) {
        var client = Minecraft.getInstance();
        if (client.player != null && client.level != null) {
            GleamMinecraft.setScreen(client, new OrionHudEditorScreen(manager));
        }
    }

    public static boolean editing(Minecraft client) {
        var screen = GleamMinecraft.screen(client);
        return screen instanceof OrionHudEditorScreen editor ? !editor.preview
                : chatEditor != null && screen == chatEditor.chat && !chatEditor.preview;
    }

    public static boolean editingIsland() {
        return chatEditor != null && !chatEditor.preview && "island".equals(chatEditor.selected);
    }

    public static boolean claimsIslandPointer(double x, double y) {
        if (chatEditor == null || chatEditor.preview) return false;
        var editor = chatEditor;
        var panel = editor.inspectorLayout();
        if (editor.inspector && panel != null && panel.contains(x, y)) return true;
        if (editor.catalog && x >= 9 && x < 9 + PANEL_WIDTH && y >= PANEL_Y
                && y < PANEL_Y + 19 + editor.catalogRows() * ROW) return true;
        var region = editor.region("island");
        return region != null && (editor.onHandle(region, x, y) || editor.onTag(region, x, y));
    }

    public static boolean islandHeader(double y, double top, double width, double height) {
        return y >= top && y < top + Math.min(height, width * .09);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        openedAt = System.nanoTime();
        finishGesture();
        inspectorX = -1;
        closingInspector = null;
        gridUntil = 0;
    }

    @Override public void removed() {
        overlay.dispose();
        saveChanges();
        super.removed();
    }

    private void saveChanges() {
        if (changed) {
            manager.save();
            changed = false;
        }
    }

    @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!preview) {
            double elapsed = Math.clamp((System.nanoTime() - openedAt) / 180_000_000.0, 0, 1);
            int alpha = (int) Math.round(77 * (1 - Math.pow(1 - elapsed, 3)));
            context.fill(0, 0, width, height, alpha << 24 | 0x101819);
        }
        GleamMinecraft.extractDeferredSubtitles(minecraft);
    }

    @Override
    //? if >=26.1 {
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    //?} else {
    /*public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    *///?}
        drawEditor(context, mouseX, mouseY);
    }

    private void drawEditor(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null || width <= 0 || height <= 0) return;
        regions = OrionHudDrag.regions().stream().filter(region -> !region.movable() || enabled(region.id())).toList();
        animate();
        var scene = new OrionHudView.Scene(width, height);
        if (!preview) {
            if (gridProgress > 0) grid(context);
            guides(context);
            for (var region : regions) {
                if (region.movable()) drawWidget(scene, region, mouseX, mouseY);
            }
            for (var region : regions) {
                if (!region.movable()) continue;
                int y = tagY(region);
                if (y >= 0) boxButton(scene, region.x() + tagWidth(region) - 12, y, 12, 12, "×", false);
            }
            scene.box(9, BAR_Y, 81, BAR_H, 9, PANEL, BORDER);
            button(scene, 11, BAR_Y + 2, 33, "Сетка", config.isHudEditorSnap());
            button(scene, 45, BAR_Y + 2, 43, "Свободно", !config.isHudEditorSnap());
            button(scene, 95, BAR_Y + 2, 15, catalog ? "×" : "+", catalog);
            button(scene, 115, BAR_Y + 2, 24, config.getHudEditorGridStep() + " px", false);
            text(scene, tr("Alt — без привязки"), 0, height - (chat == null ? 18 : 34), width, 12, 7, MUTED, "center");
            if (catalogProgress > 0) {
                int start = scene.mark();
                drawCatalog(scene);
                float progress = ease(catalogProgress);
                scene.animateParts(start, progress, -5 * (1 - progress));
            }
            if (inspectorProgress > 0) {
                int start = scene.mark();
                drawInspector(scene);
                float progress = ease(inspectorProgress);
                scene.animateParts(start, progress, -4 * (1 - progress));
            }
            if (canEditHands()) {
                for (int hand = -1; hand <= 1; hand += 2) {
                    var box = handHandle(hand);
                    boxButton(scene, box.x(), box.y(), box.width(), 16, tr(hand > 0 ? "Правая рука" : "Левая рука"),
                            handDrag == hand || box.contains(mouseX, mouseY));
                }
            }
        }
        button(scene, width - 81, BAR_Y, 72, preview ? "Редактировать" : "Просмотр", preview);
        if (chat == null) button(scene, width - 104, BAR_Y, 20, "×", false);
        context.nextStratum();
        overlay.draw(context, scene, 0, 0, width, height);
    }

    private void animate() {
        long now = System.nanoTime();
        float delta = animationAt == 0 ? 1F : Math.clamp((now - animationAt) / 150_000_000F, 0F, 1F);
        animationAt = now;
        if (!config.isAnimationsEnabled()) delta = 1F;
        catalogProgress = approach(catalogProgress, catalog, delta);
        inspectorProgress = approach(inspectorProgress, inspector, delta);
        boolean showGrid = gesture != null && freeGesture ? false
                : now < gridUntil || gesture != null && config.isHudEditorSnap();
        gridProgress = approach(gridProgress, showGrid, delta);
        if (inspectorProgress == 0) closingInspector = null;
        inspectorToggles[0] = approach(inspectorToggles[0], config.isTargetHudEquipment(), delta);
        inspectorToggles[1] = approach(inspectorToggles[1], config.isTargetHudPing(), delta);
        inspectorToggles[2] = approach(inspectorToggles[2], config.isTargetHudAttackOnly(), delta);
    }

    private static float approach(float value, boolean show, float step) {
        return Math.clamp(value + (show ? step : -step), 0F, 1F);
    }

    private static float ease(float value) {
        return value * value * (3F - 2F * value);
    }

    private void grid(GuiGraphicsExtractor context) {
        int step = config.getHudEditorGridStep();
        int color = Math.round(25 * gridProgress) << 24 | 0xcce3da;
        for (int x = 0; x < width; x += step) context.fill(x, 0, x + 1, height, color);
        for (int y = 0; y < height; y += step) context.fill(0, y, width, y + 1, color);
        int center = Math.round(78 * gridProgress) << 24 | 0xa8f0d1;
        context.fill(width / 2, 0, width / 2 + 1, height, center);
        context.fill(0, height / 2, width, height / 2 + 1, center);
    }

    private void guides(GuiGraphicsExtractor context) {
        var moving = region(selected);
        if (moving == null) return;
        if (guideX != null) {
            var peer = guideX.peer();
            int top = peer == null ? 0 : Math.max(0, Math.min(moving.y(), peer.y()) - 7);
            int bottom = peer == null ? height : Math.min(height, Math.max(moving.y() + moving.height(), peer.y() + peer.height()) + 7);
            int line = Math.clamp(guideX.line(), 0, width - 1);
            context.fill(line, top, line + 1, bottom, 0xd865d7a0);
        }
        if (guideY != null) {
            var peer = guideY.peer();
            int left = peer == null ? 0 : Math.max(0, Math.min(moving.x(), peer.x()) - 7);
            int right = peer == null ? width : Math.min(width, Math.max(moving.x() + moving.width(), peer.x() + peer.width()) + 7);
            int line = Math.clamp(guideY.line(), 0, height - 1);
            context.fill(left, line, right, line + 1, 0xd865d7a0);
        }
    }

    private void drawWidget(OrionHudView.Scene scene, OrionHudDrag.Region region, int mouseX, int mouseY) {
        boolean active = region.id().equals(selected), hovered = region.contains(mouseX, mouseY);
        int x = region.x(), y = region.y(), w = region.width(), h = region.height();
        int tagY = tagY(region);
        if (tagY >= 0) {
            String name = WIDGETS.stream().filter(item -> item.regionId().equals(region.id()) && item.enabled().test(config)).findFirst()
                    .map(Widget::name).orElse(region.id());
            String glyph = switch (region.id()) {
                case "keystrokes" -> "M7 7h6v6H7zM7 7H5a2 2 0 1 1 2-2zm6 0V5a2 2 0 1 1 2 2zm0 6h2a2 2 0 1 1-2 2zm-6 0v2a2 2 0 1 1-2-2z";
                case "potions_hud" -> "M10 3c3 4 5 6.5 5 9a5 5 0 1 1-10 0c0-2.5 2-5 5-9z";
                case "target_hud" -> "M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0zM12.5 10a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0z";
                case "nearby" -> "M12 6a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0zM4 16v-1a5.5 5.5 0 0 1 11 0v1m0-10a2 2 0 0 1 0 4m1 2a3 3 0 0 1 2 3";
                case "inventory" -> "M4 8h12l1 8H3zm3 0V6a3 3 0 0 1 6 0v2";
                case "football_hud" -> "M3 16V5h14v11M3 11h14";
                case "basketball_hud" -> "M5 4h10M3 8h14m-12 0 2 8h6l2-8M10 8v8";
                default -> "";
            };
            if (!glyph.isEmpty()) scene.icon(glyph, x, tagY + 2, 8, active ? ACCENT : TEXT, false);
            int inset = glyph.isEmpty() ? 0 : 10;
            text(scene, name, x + inset, tagY, tagWidth(region) - inset - 13, 12, 7, active ? ACCENT : TEXT, "left");
        }
        if (active || hovered) {
            scene.box(x, y, w, h, 3, "#00000000", LINE);
            scene.box(handleX(region), handleY(region), HANDLE, HANDLE, 2, ACCENT, PANEL);
        }
    }

    private int tagY(OrionHudDrag.Region region) {
        int y = region.y() >= BAR_Y + BAR_H + 14 ? region.y() - 13 : region.y() + region.height() + 1;
        return y + 12 <= height - (chat == null ? 0 : 14) ? y : -1;
    }

    private int tagWidth(OrionHudDrag.Region region) {
        return Math.min(width - region.x(), Math.max(79, region.width()));
    }

    private boolean enabled(String id) {
        return WIDGETS.stream().anyMatch(widget -> widget.regionId().equals(id) && widget.enabled().test(config));
    }

    private void toggle(Widget widget) {
        boolean before = enabled(widget.regionId());
        widget.toggle().accept(config, !widget.enabled().test(config));
        boolean after = enabled(widget.regionId());
        if (before != after) OrionHudDrag.transition(widget.regionId(), after);
        if (!after && widget.regionId().equals(selected)) {
            inspector = false;
            selected = null;
            finishGesture();
        }
        changed = true;
    }

    private void hide(String id) {
        for (var widget : WIDGETS) {
            if (widget.regionId().equals(id) && widget.enabled().test(config)) toggle(widget);
        }
    }

    private void drawCatalog(OrionHudView.Scene scene) {
        int rows = catalogRows();
        scene.box(9, PANEL_Y, PANEL_WIDTH, 19 + rows * ROW, 7, PANEL, BORDER);
        text(scene, tr("Виджеты"), 16, PANEL_Y + 2, PANEL_WIDTH - 14, 15, 8, TEXT, "left");
        for (int i = 0; i < rows; i++) {
            int first = scene.mark();
            var widget = WIDGETS.get(catalogOffset + i);
            int y = PANEL_Y + 17 + i * ROW;
            scene.box(13, y, PANEL_WIDTH - 8, ROW - 2, 4, "#edf7f20a", BORDER);
            text(scene, widget.name(), 19, y, PANEL_WIDTH - 29, ROW - 2, 8, TEXT, "left");
            text(scene, widget.enabled().test(config) ? "−" : "+", PANEL_WIDTH - 9, y, 17, ROW - 2,
                    9, ACCENT, "center");
            float appear = Math.clamp((catalogProgress - i * .025F) * 1.28F, 0F, 1F);
            scene.animateParts(first, appear, 3 * (1 - appear));
        }
    }

    private void drawInspector(OrionHudView.Scene scene) {
        var panel = inspectorLayout();
        if (panel == null) return;
        int x = panel.x(), y = panel.y(), w = panel.width();
        var region = panel.widget();
        scene.box(x, y, w, panel.height(), 7, "#11181dfc", "#6d8a7d50");
        String title = WIDGETS.stream().filter(widget -> region.id().equals(widget.regionId()) && widget.enabled().test(config))
                .map(Widget::name).findFirst().orElse(region.id());
        scene.icon("M4 5h12M4 10h12M4 15h12M8 3v4m4 1v4m-5 1v4", x + 10, y + 7, 9, ACCENT, false);
        text(scene, title + " · " + tr("Настройки"), x + 24, y + 5, w - 31, 13, 7, TEXT, "left");
        if (panel.rows() == 5) {
            toggleRow(scene, panel, 21, "Показывать снаряжение", inspectorToggles[0]);
            toggleRow(scene, panel, 36, "Показывать пинг", inspectorToggles[1]);
            toggleRow(scene, panel, 51, "Показывать только после атаки", inspectorToggles[2]);
        }
        float scale = config.getHudElementScale(region.scaleId());
        drawSlider(scene, panel, 0, "Масштаб", Math.round(scale * 100) + "%", (scale - .35F) / 3.65F);
        if (panel.rows() == 5) {
            drawSlider(scene, panel, 1, "Удерживать цель", config.getTargetHudHoldSeconds() + " s",
                    config.getTargetHudHoldSeconds() / 10F);
        }
    }

    private Inspector inspectorLayout() {
        if (!inspector) return closingInspector;
        var region = region(selected);
        if (region == null) return closingInspector;
        int panelWidth = Math.min(INSPECTOR_W, Math.max(1, width - 12));
        int rows = OrionTargetHud.ID.equals(region.id()) ? 5 : 1;
        if (inspectorX < 0) {
            int x = region.x() + region.width() + 10;
            if (x + panelWidth > width - 6) x = region.x() - panelWidth - 10;
            inspectorX = Math.clamp(x, 6, Math.max(6, width - panelWidth - 6));
            inspectorY = Math.clamp(region.y(), PANEL_Y, Math.max(PANEL_Y, height - (rows == 5 ? 119 : 48) - 17));
        }
        closingInspector = new Inspector(region, inspectorX, inspectorY, panelWidth, rows);
        return closingInspector;
    }

    private static void toggleRow(OrionHudView.Scene scene, Inspector panel, int offset, String label, float progress) {
        int x = panel.x(), y = panel.y() + offset, w = panel.width();
        text(scene, tr(label), x + 10, y, w - 34, 13, 7, MUTED, "left");
        int left = x + w - 28;
        scene.box(left, y + 2, 18, 9, 4.5F, "#21292f", "#46544f");
        int first = scene.mark();
        if (progress > 0) {
            scene.box(left, y + 2, 18, 9, 4.5F, "#295840", "#65d7a098");
            scene.animateParts(first, progress, 0);
        }
        scene.box(left + 3 + 7 * progress, y + 4, 5, 5, 2.5F, progress > .5F ? ACCENT : "#899992", null);
    }

    private static int sliderTop(Inspector panel, int row) {
        return panel.y() + (panel.rows() == 5 ? 67 : 20) + row * 24;
    }

    private static void drawSlider(OrionHudView.Scene scene, Inspector panel, int row, String name, String value, float fraction) {
        int x = panel.x() + 10, w = panel.width() - 20, y = sliderTop(panel, row);
        text(scene, tr(name), x, y, w - 42, 12, 7, MUTED, "left");
        text(scene, value, x + w - 40, y, 40, 12, 7, ACCENT, "right");
        float fill = Math.round(w * fraction);
        scene.box(x, y + 16, w, 2, 1, "#30413a", null);
        if (fill > 0) scene.box(x, y + 16, fill, 2, 1, ACCENT, null);
        scene.box(x + fill - 3, y + 14, 6, 6, 3, PANEL, ACCENT);
    }

    private void button(OrionHudView.Scene scene, int x, int y, int w, String title, boolean active) {
        int h = y == BAR_Y ? BAR_H : BAR_H - 4;
        scene.box(x, y, w, h, h / 2F, active ? ACCENT : PANEL, active ? null : BORDER);
        text(scene, tr(title), x + 1, y, w - 2, h, 7, active ? "#101817" : TEXT, "center");
    }

    private static void boxButton(OrionHudView.Scene scene, int x, int y, int w, int h, String title, boolean active) {
        scene.box(x, y, w, h, 5, active ? ACCENT : PANEL, active ? null : BORDER);
        text(scene, title, x + 1, y, w - 2, h, 7, active ? "#101817" : TEXT, "center");
    }

    private static void text(OrionHudView.Scene scene, String text, int x, int y, int w, int h,
                             int size, String color, String align) {
        if (w > 0) scene.label(text, x, y, w, h, size, color, align, true);
    }

    private static String tr(String label) { return OrionGuiLanguage.translate(label); }

    private OrionHudDrag.Region region(String id) {
        for (var region : regions) if (region.id().equals(id)) return region;
        return null;
    }

    private int catalogOffset;
    private int catalogRows() {
        return Math.min(WIDGETS.size() - catalogOffset, Math.max(1, (height - PANEL_Y - 21) / ROW));
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (catalog && x >= 9 && x < 9 + PANEL_WIDTH && y >= PANEL_Y && y < PANEL_Y + 19 + catalogRows() * ROW) {
            int capacity = Math.max(1, (height - PANEL_Y - 21) / ROW);
            catalogOffset = Math.clamp(catalogOffset + (int) Math.signum(-vertical), 0, Math.max(0, WIDGETS.size() - capacity));
            return true;
        }
        var panel = inspectorLayout();
        int hand = handAt(x, y);
        if (hand != 0 && vertical != 0 && !(inspector && panel != null && panel.contains(x, y))) {
            boolean right = hand > 0;
            config.setViewModelOffset(right, 2, config.getViewModelOffset(right, 2) + (float) Math.signum(vertical) * .06F);
            changed = true;
            return true;
        }
        return chat == null;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x(), y = event.y();
        if (chat != null && y >= height - 14) return false;
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && !preview) {
            var panel = inspectorLayout();
            if (inspector && panel != null && panel.contains(x, y)) {
                inspector = false;
                return true;
            }
            var top = hit(x, y);
            if (top != null) {
                showGridAfterClick(event);
                boolean close = inspector && top.id().equals(selected);
                if (!close) inspectorX = -1;
                selected = top.id(); catalog = false; inspector = !close;
                return true;
            }
            return chat == null;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return chat == null;
        if (y >= BAR_Y && y < BAR_Y + BAR_H) {
            if (chat == null && x >= width - 104 && x < width - 84) { onClose(); return true; }
            if (x >= width - 81 && x < width - 9) { togglePreview(); return true; }
            if (!preview && x >= 9 && x < 139) {
                if ((x >= 11 && x < 44) || (x >= 45 && x < 88)) {
                    config.setHudEditorSnap(x < 44);
                    changed = true;
                } else if (x >= 115) {
                    int step = config.getHudEditorGridStep();
                    for (int i = 0; i < STEPS.length; i++) {
                        if (STEPS[i] == step) { config.setHudEditorGridStep(STEPS[(i + 1) % STEPS.length]); break; }
                    }
                    changed = true;
                } else if (x >= 95 && x < 110) { catalog = !catalog; inspector = false; catalogOffset = 0; }
                return true;
            }
        }
        if (preview) return chat == null;
        var panel = inspectorLayout();
        if (inspector && panel != null && panel.contains(x, y)) {
            if (panel.rows() == 5 && y >= panel.y() + 21 && y < panel.y() + 66) {
                switch ((int) (y - panel.y() - 21) / 15) {
                    case 0 -> config.setTargetHudEquipment(!config.isTargetHudEquipment());
                    case 1 -> config.setTargetHudPing(!config.isTargetHudPing());
                    case 2 -> config.setTargetHudAttackOnly(!config.isTargetHudAttackOnly());
                }
                changed = true;
            } else {
                int row = y >= sliderTop(panel, 1) && panel.rows() == 5 ? 1 : 0;
                if (y >= sliderTop(panel, row) + 10 && y < sliderTop(panel, row) + 23) {
                    sliderRow = row;
                    updateSlider(panel, x);
                }
            }
            return true;
        }
        if (x >= 9 && x < 9 + PANEL_WIDTH && y >= PANEL_Y) {
            if (catalog && y < PANEL_Y + 19 + catalogRows() * ROW) {
                int row = (int) (y - PANEL_Y - 17);
                if (row >= 0 && row / ROW < catalogRows()) {
                    toggle(WIDGETS.get(catalogOffset + row / ROW));
                }
                return true;
            }
        }
        int hand = handAt(x, y);
        if (hand != 0) {
            handDrag = hand;
            handStartX = x;
            handStartY = y;
            handOffsetX = config.getViewModelOffset(hand > 0, 0);
            handOffsetY = config.getViewModelOffset(hand > 0, 1);
            return true;
        }
        var top = hit(x, y);
        if (top == null) {
            catalog = inspector = false; selected = null;
            return chat == null;
        } else {
            if (onHideButton(top, x, y)) {
                hide(top.id());
                return true;
            }
            boolean resize = onHandle(top, x, y);
            showGridAfterClick(event);
            if (!top.id().equals(selected)) inspector = false;
            catalog = false; selected = top.id();
            gesture = new Gesture(top, x, y, config.getHudElementScale(top.scaleId()), resize);
            freeGesture = event.hasAltDown();
        }
        return true;
    }

    private boolean canEditHands() {
        return chat != null && !preview && config.isViewModelEnabled()
                && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    private record HandHandle(int x, int y, int width) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + width && my >= y && my < y + 16;
        }
    }

    private HandHandle handHandle(int hand) {
        int w = Math.min(87, Math.max(30, width / 4));
        float unit = Math.min(width, height) * .52F;
        int x = Math.round(width * (hand > 0 ? .79F : .21F) - w / 2F + config.getViewModelOffset(hand > 0, 0) * unit);
        int y = Math.round(height * .76F - config.getViewModelOffset(hand > 0, 1) * unit);
        return new HandHandle(Math.clamp(x, 3, Math.max(3, width - w - 3)),
                Math.clamp(y, BAR_Y + BAR_H + 4, Math.max(BAR_Y + BAR_H + 4, height - 45)), w);
    }

    private int handAt(double x, double y) {
        if (!canEditHands()) return 0;
        if (handHandle(1).contains(x, y)) return 1;
        return handHandle(-1).contains(x, y) ? -1 : 0;
    }

    private void showGridAfterClick(MouseButtonEvent event) {
        gridUntil = event.hasAltDown() ? 0 : System.nanoTime() + 300_000_000L;
    }

    private OrionHudDrag.Region hit(double x, double y) {
        var current = region(selected);
        if (current != null && current.movable() && onHandle(current, x, y)) return current;
        var island = chat == null ? null : region("island");
        if (island != null && enabled("island") && island.contains(x, y)
                && islandHeader(y, island.y(), island.width(), island.height())) return island;
        for (int i = regions.size() - 1; i >= 0; i--) {
            var region = regions.get(i);
            if (region.movable() && enabled(region.id()) && onHideButton(region, x, y)) return region;
        }
        for (int i = regions.size() - 1; i >= 0; i--) {
            var region = regions.get(i);
            if (region.movable() && enabled(region.id()) && onTag(region, x, y)) return region;
        }
        for (int i = regions.size() - 1; i >= 0; i--) {
            var region = regions.get(i);
            if (!region.movable() || !enabled(region.id())) continue;
            if (region.contains(x, y) && (chat == null || !region.id().equals("island") || editingIsland())) return region;
        }
        return null;
    }

    private int handleX(OrionHudDrag.Region region) {
        return Math.clamp(region.x() + region.width() - 2, 0, Math.max(0, width - HANDLE));
    }

    private int handleY(OrionHudDrag.Region region) {
        return Math.clamp(region.y() + region.height() - 2, 0, Math.max(0, height - HANDLE - (chat == null ? 0 : 14)));
    }

    private boolean onHandle(OrionHudDrag.Region region, double x, double y) {
        return x >= handleX(region) && x < handleX(region) + HANDLE && y >= handleY(region) && y < handleY(region) + HANDLE;
    }

    private boolean pointerCaptured() { return gesture != null || sliderRow >= 0 || handDrag != 0; }

    private boolean onTag(OrionHudDrag.Region region, double x, double y) {
        int top = tagY(region);
        return top >= 0 && y >= top && y < top + 12 && x >= region.x() && x < region.x() + tagWidth(region);
    }

    private boolean onHideButton(OrionHudDrag.Region region, double x, double y) {
        return x >= region.x() + tagWidth(region) - 12 && onTag(region, x, y);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (preview || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return true;
        if (handDrag != 0) {
            boolean right = handDrag > 0;
            float unit = Math.max(1F, Math.min(width, height) * .52F);
            config.setViewModelOffsets(right, handOffsetX + (float) (event.x() - handStartX) / unit,
                    handOffsetY - (float) (event.y() - handStartY) / unit, config.getViewModelOffset(right, 2));
            changed = true;
            return true;
        }
        if (sliderRow >= 0) {
            var panel = inspectorLayout();
            if (panel != null) updateSlider(panel, event.x());
            return true;
        }
        if (gesture == null) return true;
        var start = gesture.region();
        double offsetX = event.x() - gesture.x(), offsetY = event.y() - gesture.y();
        if (gesture.resize()) {
            float scale = OrionHudResize.scale(gesture.scale(), start.width(), start.height(), offsetX, offsetY,
                    width - start.x(), height - start.y());
            config.setHudElementScale(start.scaleId(), scale);
            start.setPosition().accept(start.x(), start.y());
        } else {
            if (!moving && Math.hypot(offsetX, offsetY) < 2) return true;
            moving = true;
            freeGesture = event.hasAltDown();
            var live = region(start.id());
            if (live == null) return true;
            int maxX = Math.max(0, width - live.width()), maxY = Math.max(0, height - live.height());
            int left = (int) Math.round(start.x() + offsetX), top = (int) Math.round(start.y() + offsetY);
            if (config.isHudEditorSnap() && !event.hasAltDown()) {
                guideX = align(left, live.width(), maxX, regions, start.id(), true);
                guideY = align(top, live.height(), maxY, regions, start.id(), false);
                int step = config.getHudEditorGridStep();
                left = guideX == null ? gridPosition(left, step) : guideX.position();
                top = guideY == null ? gridPosition(top, step) : guideY.position();
            } else { guideX = guideY = null; }
            start.setPosition().accept(Math.clamp(left, 0, maxX), Math.clamp(top, 0, maxY));
        }
        changed = true;
        return true;
    }

    private static int gridPosition(int value, int step) { return Math.round(value / (float) step) * step; }

    private static Snap align(int position, int size, int limit, List<OrionHudDrag.Region> regions, String id, boolean horizontal) {
        Snap best = null;
        int nearest = 6;
        boolean same = false;
        int[] pivots = {0, size / 2, size};
        int[] screen = {0, (limit + size) / 2, limit + size};
        for (int i = 0; i < 3; i++) {
            int candidate = screen[i] - pivots[i];
            int distance = Math.abs(candidate - position);
            if (candidate >= 0 && candidate <= limit && distance <= nearest) {
                best = new Snap(candidate, screen[i], null);
                nearest = distance;
                same = true;
            }
        }
        for (var peer : regions) {
            if (peer.id().equals(id)) continue;
            int start = horizontal ? peer.x() : peer.y();
            int length = horizontal ? peer.width() : peer.height();
            int[] anchors = {start, start + length / 2, start + length};
            for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
                if (i != j && (i == 1 || j == 1)) continue;
                int candidate = anchors[j] - pivots[i];
                if (candidate < 0 || candidate > limit) continue;
                int distance = Math.abs(candidate - position);
                if (distance > nearest || best != null && distance == nearest && (same || i != j)) continue;
                best = new Snap(candidate, anchors[j], peer);
                nearest = distance; same = i == j;
            }
        }
        return best;
    }

    private void updateSlider(Inspector panel, double x) {
        float fraction = (float) Math.clamp((x - panel.x() - 10) / Math.max(1, panel.width() - 20), 0, 1);
        if (sliderRow == 0) {
            var region = panel.widget();
            config.setHudElementScale(region.scaleId(), Math.round((.35F + 3.65F * fraction) * 100) / 100F);
            region.setPosition().accept(region.x(), region.y());
        } else {
            config.setTargetHudHoldSeconds(Math.round(10 * fraction));
        }
        changed = true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) finishGesture();
        return true;
    }

    private void finishGesture() {
        handDrag = 0;
        gesture = null; guideX = guideY = null; moving = false; freeGesture = false; sliderRow = -1;
    }

    private void togglePreview() {
        preview = !preview; finishGesture(); catalog = inspector = false;
        gridUntil = 0;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            if (preview) togglePreview();
            else if (catalog || inspector) catalog = inspector = false;
            else onClose();
            return true;
        }
        var current = region(selected);
        if (!preview && current != null && current.movable()) {
            int dx = event.key() == InputConstants.KEY_RIGHT ? 1 : event.key() == InputConstants.KEY_LEFT ? -1 : 0;
            int dy = event.key() == InputConstants.KEY_DOWN ? 1 : event.key() == InputConstants.KEY_UP ? -1 : 0;
            if (dx != 0 || dy != 0) {
                current.setPosition().accept(Math.clamp(current.x() + dx, 0, Math.max(0, width - current.width())),
                        Math.clamp(current.y() + dy, 0, Math.max(0, height - current.height())));
                changed = true;
                return true;
            }
        }
        return super.keyPressed(event);
    }
}
