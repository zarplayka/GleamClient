package ru.orion.client.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    private static final int PANEL_Y = 35;
    private static final int[] STEPS = {8, 16, 24, 32};

    private record Widget(String id, String name, Predicate<OrionConfig> enabled,
                          BiConsumer<OrionConfig, Boolean> toggle) {}
    private static final List<Widget> WIDGETS = List.of(
            new Widget("island", "HUD / MediaPlayer", c -> c.isHudEnabled() || c.isMediaPlayerEnabled(), (c, enabled) -> {
                c.setHudEnabled(enabled);
                if (!enabled) c.setMediaPlayerEnabled(false);
            }),
            new Widget("keystrokes", "Keystrokes", OrionConfig::isKeystrokesEnabled, OrionConfig::setKeystrokesEnabled),
            new Widget(OrionPotionsHud.ID, "Potions", OrionConfig::isPotionsEnabled, OrionConfig::setPotionsEnabled),
            new Widget(OrionTargetHud.ID, "TargetHud", OrionConfig::isTargetHudEnabled, OrionConfig::setTargetHudEnabled),
            new Widget("nearby", "BetterNear", OrionConfig::isBetterNearEnabled, OrionConfig::setBetterNearEnabled),
            new Widget("inventory", "InventoryHUD", OrionConfig::isInventoryHudEnabled, OrionConfig::setInventoryHudEnabled),
            new Widget("football_hud", "Football", OrionConfig::isFootballEnabled, OrionConfig::setFootballEnabled),
            new Widget("basketball_hud", "Basketball", c -> c.isBasketballEnabled() && c.isBasketballHud(),
                    (c, enabled) -> {
                        if (enabled) c.setBasketballEnabled(true);
                        c.setBasketballHud(enabled);
                    })
    );

    private record Snap(int position, int line, OrionHudDrag.Region peer) {}
    private record Gesture(OrionHudDrag.Region region, double x, double y, float scale, boolean resize) {}

    private final OrionConfigManager manager;
    private final OrionConfig config;
    private final OrionHudView overlay = new OrionHudView("hud_editor");
    private List<OrionHudDrag.Region> regions = List.of();
    private String selected;
    private Gesture gesture;
    private Snap guideX, guideY;
    private boolean preview, catalog, inspector, changed;
    private long openedAt;

    private OrionHudEditorScreen(OrionConfigManager manager) {
        super(Component.literal(tr("Редактор HUD")));
        this.manager = manager;
        this.config = manager.getConfig();
    }

    public static void open(OrionConfigManager manager) {
        var client = Minecraft.getInstance();
        if (client.player != null && client.level != null) {
            GleamMinecraft.setScreen(client, new OrionHudEditorScreen(manager));
        }
    }

    public static boolean editing(Minecraft client) {
        return GleamMinecraft.screen(client) instanceof OrionHudEditorScreen;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        openedAt = System.nanoTime();
        finishGesture();
    }

    @Override public void removed() {
        overlay.dispose();
        if (changed) manager.save();
        super.removed();
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
        if (minecraft.player == null || minecraft.level == null || width <= 0 || height <= 0) return;
        regions = OrionHudDrag.regions();
        var scene = new OrionHudView.Scene(width, height);
        if (!preview) {
            if (config.isHudEditorSnap()) grid(context);
            guides(context);
            for (var region : regions) {
                if (region.movable()) drawWidget(scene, region, mouseX, mouseY);
            }
            scene.box(9, BAR_Y, 81, BAR_H, 9, PANEL, BORDER);
            button(scene, 11, BAR_Y + 2, 33, "Сетка", config.isHudEditorSnap());
            button(scene, 45, BAR_Y + 2, 43, "Свободно", !config.isHudEditorSnap());
            button(scene, 95, BAR_Y + 2, 15, catalog ? "×" : "+", catalog);
            button(scene, 115, BAR_Y + 2, 24, config.getHudEditorGridStep() + " px", false);
            text(scene, tr("Alt — без привязки"), 0, height - 18, width, 12, 7, MUTED, "center");
            if (catalog) drawCatalog(scene);
            else if (inspector) drawInspector(scene);
        }
        button(scene, width - 81, BAR_Y, 72, preview ? "Редактировать" : "Просмотр", preview);
        button(scene, width - 104, BAR_Y, 20, "×", false);
        context.nextStratum();
        overlay.draw(context, scene, 0, 0, width, height);
    }

    private void grid(GuiGraphicsExtractor context) {
        int step = config.getHudEditorGridStep();
        for (int x = 0; x < width; x += step) context.fill(x, 0, x + 1, height, 0x14cce3da);
        for (int y = 0; y < height; y += step) context.fill(0, y, width, y + 1, 0x14cce3da);
    }

    private void guides(GuiGraphicsExtractor context) {
        var moving = region(selected);
        if (moving == null) return;
        if (guideX != null) {
            var peer = guideX.peer();
            int top = Math.max(0, Math.min(moving.y(), peer.y()) - 7);
            int bottom = Math.min(height, Math.max(moving.y() + moving.height(), peer.y() + peer.height()) + 7);
            context.fill(guideX.line(), top, guideX.line() + 1, bottom, 0xb365d7a0);
        }
        if (guideY != null) {
            var peer = guideY.peer();
            int left = Math.max(0, Math.min(moving.x(), peer.x()) - 7);
            int right = Math.min(width, Math.max(moving.x() + moving.width(), peer.x() + peer.width()) + 7);
            context.fill(left, guideY.line(), right, guideY.line() + 1, 0xb365d7a0);
        }
    }

    private void drawWidget(OrionHudView.Scene scene, OrionHudDrag.Region region, int mouseX, int mouseY) {
        boolean active = region.id().equals(selected), hovered = region.contains(mouseX, mouseY);
        int x = region.x(), y = region.y(), w = region.width(), h = region.height();
        if (y >= 34) {
            String name = WIDGETS.stream().filter(item -> item.id().equals(region.id())).findFirst()
                    .map(Widget::name).orElse(region.id());
            String glyph = switch (region.id()) {
                case "island" -> "M2 10h4l2-6 4 12 2-6h4";
                case "keystrokes" -> "M7 7h6v6H7zM7 7H5a2 2 0 1 1 2-2zm6 0V5a2 2 0 1 1 2 2zm0 6h2a2 2 0 1 1-2 2zm-6 0v2a2 2 0 1 1-2-2z";
                case "potions_hud" -> "M10 3c3 4 5 6.5 5 9a5 5 0 1 1-10 0c0-2.5 2-5 5-9z";
                case "target_hud" -> "M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0zM12.5 10a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0z";
                case "nearby" -> "M12 6a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0zM4 16v-1a5.5 5.5 0 0 1 11 0v1m0-10a2 2 0 0 1 0 4m1 2a3 3 0 0 1 2 3";
                case "inventory" -> "M4 8h12l1 8H3zm3 0V6a3 3 0 0 1 6 0v2";
                case "football_hud" -> "M3 16V5h14v11M3 11h14";
                case "basketball_hud" -> "M5 4h10M3 8h14m-12 0 2 8h6l2-8M10 8v8";
                default -> "";
            };
            if (!glyph.isEmpty()) scene.icon(glyph, x, y - 11, 8, active ? ACCENT : TEXT, false);
            text(scene, name, x + 10, y - 13, Math.min(width - x - 10, Math.max(52, w)), 12, 7, active ? ACCENT : TEXT, "left");
        }
        if (active || hovered) {
            scene.box(x, y, w, h, 3, "#00000000", LINE);
            scene.box(x + Math.max(0, w - HANDLE), y + Math.max(0, h - HANDLE), Math.min(w, HANDLE),
                    Math.min(h, HANDLE), 2, ACCENT, PANEL);
        }
    }

    private void drawCatalog(OrionHudView.Scene scene) {
        int rows = catalogRows();
        scene.box(9, PANEL_Y, PANEL_WIDTH, 19 + rows * ROW, 7, PANEL, BORDER);
        text(scene, tr("Виджеты"), 16, PANEL_Y + 2, PANEL_WIDTH - 14, 15, 8, TEXT, "left");
        for (int i = 0; i < rows; i++) {
            var widget = WIDGETS.get(catalogOffset + i);
            int y = PANEL_Y + 17 + i * ROW;
            scene.box(13, y, PANEL_WIDTH - 8, ROW - 2, 4, "#edf7f20a", BORDER);
            text(scene, widget.name(), 19, y, PANEL_WIDTH - 29, ROW - 2, 8, TEXT, "left");
            text(scene, widget.enabled().test(config) ? "−" : "+", PANEL_WIDTH - 9, y, 17, ROW - 2,
                    9, ACCENT, "center");
        }
    }

    private void drawInspector(OrionHudView.Scene scene) {
        var region = region(selected);
        if (region == null) return;
        scene.box(9, PANEL_Y, PANEL_WIDTH, 61, 7, PANEL, BORDER);
        text(scene, "X " + region.x() + "   Y " + region.y(), 17, PANEL_Y + 3, 107, 14, 8, TEXT, "left");
        int scale = Math.round(config.getHudElementScale(region.scaleId()) * 100);
        text(scene, tr("Масштаб") + "  " + scale + "%", 17, PANEL_Y + 21, 107, 12, 8, TEXT, "left");
        boxButton(scene, 17, PANEL_Y + 36, 22, 17, "−", false);
        boxButton(scene, 43, PANEL_Y + 36, 22, 17, "+", false);
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
        if (catalog && x >= 9 && x < 9 + PANEL_WIDTH && y >= PANEL_Y) {
            int capacity = Math.max(1, (height - PANEL_Y - 21) / ROW);
            catalogOffset = Math.clamp(catalogOffset + (int) Math.signum(-vertical), 0, Math.max(0, WIDGETS.size() - capacity));
        }
        return true;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x(), y = event.y();
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && !preview) {
            var top = hit(x, y);
            if (top != null) {
                selected = top.id(); catalog = false; inspector = true;
            }
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return true;
        if (y >= BAR_Y && y < BAR_Y + BAR_H) {
            if (x >= width - 104 && x < width - 84) { onClose(); return true; }
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
        if (preview) return true;
        if (x >= 9 && x < 9 + PANEL_WIDTH && y >= PANEL_Y) {
            if (catalog && y < PANEL_Y + 19 + catalogRows() * ROW) {
                int row = (int) (y - PANEL_Y - 17);
                if (row >= 0 && row / ROW < catalogRows()) {
                    var widget = WIDGETS.get(catalogOffset + row / ROW);
                    boolean enabled = !widget.enabled().test(config);
                    widget.toggle().accept(config, enabled);
                    if (!enabled) OrionHudDrag.forget(widget.id());
                    changed = true;
                }
                return true;
            }
            if (inspector && y < PANEL_Y + 61) {
                if (y >= PANEL_Y + 36 && y < PANEL_Y + 53 && x >= 17 && x < 65) {
                    var region = region(selected);
                    if (region != null) scale(region, x < 39 ? -.05F : x >= 43 ? .05F : 0);
                }
                return true;
            }
        }
        var top = hit(x, y);
        if (top == null) {
            catalog = inspector = false; selected = null;
        } else {
            boolean resize = x >= top.x() + top.width() - HANDLE && y >= top.y() + top.height() - HANDLE;
            if (!top.id().equals(selected)) inspector = false;
            catalog = false; selected = top.id();
            gesture = new Gesture(top, x, y, config.getHudElementScale(top.scaleId()), resize);
        }
        return true;
    }

    private OrionHudDrag.Region hit(double x, double y) {
        for (int i = regions.size() - 1; i >= 0; i--) {
            var region = regions.get(i);
            if (region.movable() && region.contains(x, y)) return region;
        }
        return null;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (gesture == null || preview || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return true;
        var start = gesture.region();
        double offsetX = event.x() - gesture.x(), offsetY = event.y() - gesture.y();
        if (gesture.resize()) {
            float scale = OrionHudResize.scale(gesture.scale(), start.width(), start.height(), offsetX, offsetY,
                    width - start.x(), height - start.y());
            config.setHudElementScale(start.scaleId(), scale);
            start.setPosition().accept(start.x(), start.y());
        } else {
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
        int nearest = 5;
        boolean same = false;
        int[] pivots = {0, size / 2, size};
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

    private void scale(OrionHudDrag.Region region, float delta) {
        config.setHudElementScale(region.scaleId(), config.getHudElementScale(region.scaleId()) + delta);
        region.setPosition().accept(region.x(), region.y());
        changed = true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) finishGesture();
        return true;
    }

    private void finishGesture() {
        gesture = null; guideX = guideY = null;
    }

    private void togglePreview() {
        preview = !preview; finishGesture(); catalog = inspector = false;
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
