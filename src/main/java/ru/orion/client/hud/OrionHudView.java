package ru.orion.client.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import ru.orion.client.gui.OrionLumenView;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Lumen display list for the in-game HUD. Positions are in the widget's design units. */
public final class OrionHudView {
    private static final Set<OrionHudView> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());
    static {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(() -> {
                    for (OrionHudView widget : List.copyOf(ACTIVE)) widget.dispose();
                }));
    }

    private final OrionLumenView view;
    private final Identifier id;
    private JsonObject previous;
    private int lastWidth, lastHeight, lastDrawWidth, lastDrawHeight, lastScale;
    private boolean lastLight, lastGlass;
    private String lastAccent;
    private long animateUntil;

    public OrionHudView(String id) {
        this.id = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, id);
        view = new OrionLumenView("hud-widgets.html", id,
                "/assets/orion-client/fonts/paradox/ParadoxSans-Regular.ttf");
    }

    public void dispose() {
        if (previous == null) return;
        view.dispose();
        OrionGlassCompositor.INSTANCE.releaseMask(id);
        previous = null;
        lastWidth = lastHeight = lastDrawWidth = lastDrawHeight = lastScale = 0;
        ACTIVE.remove(this);
    }

    public boolean draw(GuiGraphicsExtractor context, Scene scene, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return false;
        boolean gleamHud = !"scoreboard_glass".equals(id.getPath());
        if (!view.ready()) {
            scene.fallback(context, x, y, width, height, gleamHud);
            return true;
        }
        int scale = Minecraft.getInstance().getWindow().getGuiScale();
        view.resize(scene.width, scene.height, Math.max(1F, scale * Math.max(
                width / (float) scene.width, height / (float) scene.height)));
        var config = OrionClient.getConfigOrNull();
        boolean light = config != null && config.getClickGuiTheme() == 4;
        boolean glass = config != null && config.isHudElementGlass(id.getPath()) && !scene.panels.isEmpty();
        String accent = gleamHud && !light ? "#65d7a0" : config == null ? "#c2d2f7" : config.getClickGuiAccent();
        if (!scene.state.equals(previous) || light != lastLight || glass != lastGlass
                || !java.util.Objects.equals(accent, lastAccent)) {
            var state = new JsonObject();
            state.add("parts", scene.parts);
            state.addProperty("light", light);
            state.addProperty("gleamHud", gleamHud);
            state.addProperty("glass", glass);
            state.addProperty("accent", accent);
            view.update(state);
            previous = scene.state;
            lastLight = light;
            lastGlass = glass;
            lastAccent = accent;
            ACTIVE.add(this);
            if (scene.animated) animateUntil = System.nanoTime() + 180_000_000L;
            view.frame();
        } else if (scene.width != lastWidth || scene.height != lastHeight || width != lastDrawWidth
                || height != lastDrawHeight || scale != lastScale || System.nanoTime() < animateUntil) {
            view.frame();
        }
        lastWidth = scene.width;
        lastHeight = scene.height;
        lastDrawWidth = width;
        lastDrawHeight = height;
        lastScale = scale;
        if (glass) {
            context.nextStratum();
            OrionGlassCompositor.INSTANCE.submitBackground(id, context, x, y, width, height,
                    scene.width, scene.height, scene.panels);
        }
        if (!view.draw(context, x, y, width, height, width / (float) scene.width, height / (float) scene.height)) {
            scene.fallback(context, x, y, width, height, gleamHud);
        }
        return true;
    }

    public static float fit(float scale, int width, int height, int screenWidth, int screenHeight) {
        return Math.min(scale, Math.min(Math.max(1, screenWidth - 8) / (float) width,
                Math.max(1, screenHeight - 8) / (float) height));
    }

    public static String color(int argb) {
        return String.format(Locale.ROOT, "#%06x%02x", argb & 0xffffff, argb >>> 24);
    }

    public static String arrow(float degrees) {
        double angle = Math.toRadians(degrees);
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return String.format(Locale.ROOT, "M%.3f %.3fL%.3f %.3fL%.3f %.3fL%.3f %.3fZ",
                10 + 8*sin, 10 - 8*cos, 10 + 6*cos - 8*sin, 10 + 6*sin + 8*cos,
                10 - 3*sin, 10 + 3*cos, 10 - 6*cos - 8*sin, 10 - 6*sin + 8*cos);
    }

    public static Scene charge(int size, int percent, int alpha) {
        percent = Math.clamp(percent, 0, 100);
        alpha = Math.clamp(alpha, 0, 255);
        var scene = new Scene(size, size);
        String circle = "M10 1.4A8.6 8.6 0 1 1 10 18.6A8.6 8.6 0 1 1 10 1.4";
        scene.icon(circle, 0, 0, size, color(Math.round(alpha * .53F) << 24 | 0x0a0a0c), false);
        if (percent > 0) {
            double angle = Math.toRadians(percent * 3.6);
            String arc = percent == 100 ? circle : String.format(Locale.ROOT, "M10 1.4A8.6 8.6 0 %d 1 %.3f %.3f",
                    percent > 50 ? 1 : 0, 10 + 8.6 * Math.sin(angle), 10 - 8.6 * Math.cos(angle));
            int tint = Math.round(alpha * .94F) << 24 | 0xff0000 | Math.round(185 - percent * .8F) << 8 | 35;
            scene.icon(arc, 0, 0, size, color(tint), false);
        }
        return scene;
    }

    public static final class Scene {
        private final int width, height;
        private final JsonObject state = new JsonObject();
        private final JsonArray parts = new JsonArray();
        private final List<OrionGlassCompositor.Panel> panels = new ArrayList<>();
        private boolean animated;

        public Scene(int width, int height) {
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
            state.add("parts", parts);
        }

        private JsonObject part(String kind, float x, float y, float w, float h) {
            JsonObject part = new JsonObject();
            part.addProperty("kind", kind);
            part.addProperty("x", x);
            part.addProperty("y", y);
            part.addProperty("w", w);
            part.addProperty("h", h);
            parts.add(part);
            return part;
        }

        public void panel(float x, float y, float w, float h, float radius) {
            surface(x, y, w, h, radius);
            panels.add(new OrionGlassCompositor.Panel(x, y, w, h, radius * 2));
        }

        void surface(float x, float y, float w, float h, float radius) {
            part("panel", x, y, w, h).addProperty("radius", radius);
        }

        public void box(float x, float y, float w, float h, float radius, String color, String border) {
            JsonObject part = part("box", x, y, w, h);
            part.addProperty("radius", radius);
            part.addProperty("color", color);
            if (border != null) part.addProperty("border", border);
        }

        void well(float x, float y, float w, float h, float radius) {
            part("well", x, y, w, h).addProperty("radius", radius);
        }

        void divider(float x, float y, float width) {
            divider(x, y, width, 1);
        }

        void divider(float x, float y, float width, float height) {
            part("divider", x, y, width, height);
        }

        void key(float x, float y, float w, float h, String text, String mouse, boolean pressed) {
            var part = part("key", x, y, w, h);
            part.addProperty("radius", 6);
            part.addProperty("text", text);
            part.addProperty("mouse", mouse);
            part.addProperty("pressed", pressed);
            animated = true;
        }

        void timer(String text, int x, int y, int width, int height, boolean expiring) {
            var part = part("timer", x, y, width, height);
            part.addProperty("text", text);
            part.addProperty("expiring", expiring);
        }

        public void label(String text, float x, float y, float w, float h, float size,
                          String color, String align, boolean bold) {
            JsonObject part = part("label", x, y, w, h);
            part.addProperty("text", text);
            part.addProperty("size", size);
            part.addProperty("color", color);
            part.addProperty("align", align);
            part.addProperty("bold", bold);
        }

        public void icon(String path, float x, float y, float size, String color, boolean fill) {
            JsonObject part = part("icon", x, y, size, size);
            part.addProperty("path", path);
            part.addProperty("color", color);
            part.addProperty("fill", fill);
        }

        public void lock(int x, int y) {
            box(x, y, 16, 16, 2, "#dc2d2d55", "#ff5a50be");
            box(x + 9, y + 1, 6, 7, 1.5F, "#140f12d2", null);
            icon("M5 9V6a5 5 0 0 1 10 0v3 M4 9h12v8H4Z", x + 9, y + 2, 6, "#ffffff", false);
        }

        private void fallback(GuiGraphicsExtractor context, int x, int y, int w, int h, boolean gleamHud) {
            var pose = context.pose();
            pose.pushMatrix();
            try {
                pose.translate(x, y).scale(w / (float) width, h / (float) height);
                var font = Minecraft.getInstance().font;
                var config = OrionClient.getConfigOrNull();
                boolean light = config != null && config.getClickGuiTheme() == 4;
                String customAccent = config == null ? "" : config.getClickGuiAccent();
                boolean darkGleam = gleamHud && !light;
                int accent = darkGleam ? 0xff65d7a0 : customAccent.isEmpty()
                        ? (light ? 0xff4257b2 : 0xffc2d2f7) : argb(customAccent);
                for (var value : parts) {
                    var part = value.getAsJsonObject();
                    int left = Math.round(part.get("x").getAsFloat()), top = Math.round(part.get("y").getAsFloat());
                    int partWidth = Math.round(part.get("w").getAsFloat()), partHeight = Math.round(part.get("h").getAsFloat());
                    String kind = part.get("kind").getAsString();
                    if (kind.equals("key")) {
                        boolean pressed = part.get("pressed").getAsBoolean();
                        int fill = darkGleam ? (pressed ? 0xeb1b2b29 : 0xeb0f1216)
                                : pressed ? (light ? 0xffdce3f7 : 0xff292e41) : (light ? 0xf2e7ecf7 : 0xf217181f);
                        int ink = pressed ? accent : (light ? 0xff242735 : 0xfff5f5f8);
                        fillTile(context, left, top, partWidth, partHeight, fill);
                        String mouse = part.get("mouse").getAsString();
                        String label = part.get("text").getAsString();
                        if (!mouse.isEmpty() && !label.isEmpty()) {
                            int half = partHeight / 2;
                            drawKeyText(context, font, mouse, left, top, partWidth, half, ink);
                            drawKeyText(context, font, label, left, top + half, partWidth, partHeight - half, ink);
                        } else if (!mouse.isEmpty()) {
                            drawKeyText(context, font, mouse, left, top, partWidth, partHeight, ink);
                        } else if (label.isEmpty()) {
                            int inset = partWidth / 4, center = top + partHeight / 2;
                            context.fill(left + inset, center, left + partWidth - inset, center + 1, ink);
                        } else {
                            drawKeyText(context, font, label, left, top, partWidth, partHeight, ink);
                        }
                    } else if (kind.equals("panel") || kind.equals("box") || kind.equals("well") || kind.equals("divider")) {
                        int color = switch (kind) {
                            case "panel" -> darkGleam ? 0xeb0f1216 : 0xf517181f;
                            case "well" -> 0x0dffffff;
                            case "divider" -> 0x1cffffff;
                            default -> argb(part.get("color").getAsString());
                        };
                        context.fill(left, top, left + partWidth, top + partHeight, color);
                    } else if (kind.equals("label") || kind.equals("timer")) {
                        boolean timer = kind.equals("timer");
                        boolean expiring = timer && part.get("expiring").getAsBoolean();
                        if (timer) fillTile(context, left, top, partWidth, partHeight,
                                expiring ? 0xfff2a2a6 : darkGleam ? 0xff20332c : accent);
                        int color;
                        if (timer) color = expiring ? 0xff17181f : darkGleam ? accent : light ? 0xfff5f5f8 : 0xff17181f;
                        else {
                            String ink = part.get("color").getAsString();
                            color = ink.equals("var(--accent)") ? accent : argb(ink);
                        }
                        float scale = (timer ? 5.8F : part.get("size").getAsFloat()) / font.lineHeight;
                        String label = font.plainSubstrByWidth(part.get("text").getAsString(), Math.max(0, (int) (partWidth / scale)));
                        float textWidth = font.width(label) * scale;
                        float offset = switch (timer ? "center" : part.get("align").getAsString()) {
                            case "center" -> (partWidth - textWidth) / 2;
                            case "right" -> partWidth - textWidth;
                            default -> 0;
                        };
                        pose.pushMatrix();
                        try {
                            pose.translate(left + offset, top + (partHeight - font.lineHeight * scale) / 2);
                            pose.scale(scale, scale);
                            //? if >=26.1 {
                            context.text(font, label, 0, 0, color);
                            //?} else {
                            /*context.drawString(font, label, 0, 0, color);
                            *///?}
                        } finally {
                            pose.popMatrix();
                        }
                    }
                }
            } finally {
                pose.popMatrix();
            }
        }

        private static void fillTile(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
            context.fill(x + 2, y, x + width - 2, y + 2, color);
            context.fill(x, y + 2, x + width, y + height - 2, color);
            context.fill(x + 2, y + height - 2, x + width - 2, y + height, color);
        }

        private static void drawKeyText(GuiGraphicsExtractor context, net.minecraft.client.gui.Font font,
                                        String label, int x, int y, int width, int height, int color) {
            String fitted = font.plainSubstrByWidth(label, width - 2);
            int left = x + (width - font.width(fitted)) / 2, top = y + (height - font.lineHeight) / 2;
            //? if >=26.1 {
            context.text(font, fitted, left, top, color);
            //?} else {
            /*context.drawString(font, fitted, left, top, color);
            *///?}
        }

        private static int argb(String color) {
            if (color.equals("var(--text)")) return 0xfff5f5f8;
            if (color.equals("var(--muted)")) return 0xffa9aab8;
            long rgba = Long.parseLong(color.substring(1), 16);
            return color.length() == 7 ? 0xff000000 | (int) rgba : (int) (rgba >>> 8 | (rgba & 255) << 24);
        }
    }
}
