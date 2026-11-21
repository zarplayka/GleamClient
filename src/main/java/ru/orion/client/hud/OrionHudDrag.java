package ru.orion.client.hud;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Bounds reported by the live HUD renderers and shared with the layout editor. */
public final class OrionHudDrag {
    private static final long MAX_AGE = 500_000_000L;
    private static final Map<String, Region> REGIONS = new LinkedHashMap<>();
    private static final Map<String, Visibility> TRANSITIONS = new LinkedHashMap<>();
    private static float opacity = 1F;
    static {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            REGIONS.clear();
            TRANSITIONS.clear();
        }));
    }
    private final String id;

    public OrionHudDrag(String id) { this.id = id; }

    public record Position(int x, int y) {}
    private record Visibility(float from, boolean enabled, long started) {
        float opacity(long now) {
            float t = Math.clamp((now - started) / 210_000_000F, 0F, 1F);
            float eased = enabled ? 1F - (1F - t) * (1F - t) : t * t;
            return from + ((enabled ? 1F : 0F) - from) * eased;
        }
    }
    public record Region(String id, int x, int y, int width, int height, long seen,
                         BiConsumer<Integer, Integer> setPosition, String scaleId) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }

        boolean overlaps(int left, int top, int w, int h, int gap) {
            return left < x + width + gap && x - gap < left + w
                    && top < y + height + gap && y - gap < top + h;
        }

        public boolean movable() { return setPosition != null; }
    }

    public static List<Region> regions() {
        long cutoff = System.nanoTime() - MAX_AGE;
        REGIONS.values().removeIf(region -> !region.movable() && region.seen() < cutoff);
        return List.copyOf(REGIONS.values());
    }

    public static void observe(String id, int x, int y, int width, int height) {
        observe(id, x, y, width, height, null, null);
    }

    public static void observe(String id, int x, int y, int width, int height,
                               BiConsumer<Integer, Integer> setPosition, String scaleId) {
        if (width > 0 && height > 0) {
            REGIONS.put(id, new Region(id, x, y, width, height, System.nanoTime(), setPosition, scaleId));
        }
    }

    public static void forget(String id) { REGIONS.remove(id); }

    static void transition(String id, boolean enabled) {
        long now = System.nanoTime();
        var previous = TRANSITIONS.get(id);
        float from = previous == null ? (enabled ? 0F : 1F) : previous.opacity(now);
        TRANSITIONS.put(id, new Visibility(from, enabled, now));
    }

    public static boolean fadingOut(String id) {
        var transition = TRANSITIONS.get(id);
        return transition != null && !transition.enabled() && transition.opacity(System.nanoTime()) > 0F;
    }

    public boolean fadingOut() { return fadingOut(id); }

    public static float opacity() { return opacity; }

    public static int tint(int color) {
        return color & 0xffffff | Math.round((color >>> 24) * opacity) << 24;
    }

    public void draw(GuiGraphicsExtractor context, Runnable render) { draw(id, context, render); }

    public static void draw(String id, GuiGraphicsExtractor context, Runnable render) {
        var transition = TRANSITIONS.get(id);
        if (transition == null) {
            render.run();
            return;
        }
        long now = System.nanoTime();
        float alpha = transition.opacity(now);
        if (now - transition.started() >= 210_000_000L) {
            TRANSITIONS.remove(id);
            render.run();
            return;
        }
        float previous = opacity;
        opacity *= alpha;
        var region = REGIONS.get(id);
        var pose = context.pose();
        pose.pushMatrix();
        try {
            if (region != null) {
                float pivot = region.x() + region.width() / 2F;
                float scale = .8F + .2F * alpha;
                pose.translate(pivot, region.y() - 5F * (1F - alpha))
                        .scale(scale).translate(-pivot, -region.y());
            }
            render.run();
        } finally {
            pose.popMatrix();
            opacity = previous;
        }
    }

    Position placeWidget(GuiGraphicsExtractor context, int width, int height, int defaultX, int defaultY,
                         OrionConfigManager manager) {
        var config = manager.getConfig();
        int savedX = config.getExtraHudPosition(id, 0);
        int savedY = config.getExtraHudPosition(id, 1);
        int maxX = Math.max(0, context.guiWidth() - width);
        int maxY = Math.max(0, context.guiHeight() - height);
        Position scaled = restore(config, context.guiWidth(), context.guiHeight(), savedX, savedY, defaultX, defaultY);
        int x = Math.clamp(scaled.x(), 0, maxX);
        int y = Math.clamp(scaled.y(), 0, maxY);
        // Auto-place unused defaults. Explicit positions belong to the user.
        Position position = savedX >= 0 && savedY >= 0
                ? new Position(x, y) : available(x, y, width, height, maxX, maxY);
        if (position == null) {
            forget(id);
            return null;
        }
        return update(position.x(), position.y(), width, height, tracked(config, context.guiWidth(), context.guiHeight(),
                (left, top) -> config.setExtraHudPosition(id, left, top)));
    }

    public Position restore(OrionConfig config, int width, int height, int savedX, int savedY, int defaultX, int defaultY) {
        int baseWidth = config.getHudPositionViewport(id, 0);
        int baseHeight = config.getHudPositionViewport(id, 1);
        if ((savedX >= 0 || savedY >= 0) && (baseWidth <= 0 || baseHeight <= 0) && width > 0 && height > 0) {
            config.setHudPositionViewport(id, width, height);
            baseWidth = width;
            baseHeight = height;
        }
        return new Position(savedX < 0 ? defaultX : Math.round(savedX * (width / (float) Math.max(1, baseWidth))),
                savedY < 0 ? defaultY : Math.round(savedY * (height / (float) Math.max(1, baseHeight))));
    }

    public BiConsumer<Integer, Integer> tracked(OrionConfig config, int width, int height,
                                                 BiConsumer<Integer, Integer> write) {
        return (x, y) -> {
            write.accept(x, y);
            config.setHudPositionViewport(id, width, height);
        };
    }

    public Position place(GuiGraphicsExtractor context, OrionConfig config, int w, int h,
                          int savedX, int savedY, int defaultX, int defaultY, BiConsumer<Integer, Integer> write) {
        int width = context.guiWidth(), height = context.guiHeight();
        Position pos = restore(config, width, height, savedX, savedY, defaultX, defaultY);
        return update(Math.clamp(pos.x(), 0, Math.max(0, width - w)), Math.clamp(pos.y(), 0, Math.max(0, height - h)),
                w, h, tracked(config, width, height, write));
    }

    private Position available(int x, int y, int w, int h, int maxX, int maxY) {
        var occupied = regions().stream().filter(region -> !region.id().equals(id)).toList();
        int gap = 4;
        if (occupied.stream().noneMatch(region -> region.overlaps(x, y, w, h, gap))) return new Position(x, y);
        var xs = new ArrayList<>(List.of(x, 0, maxX));
        var ys = new ArrayList<>(List.of(y, 0, maxY));
        for (var region : occupied) {
            xs.add(Math.clamp(region.x() - w - gap, 0, maxX));
            xs.add(Math.clamp(region.x() + region.width() + gap, 0, maxX));
            ys.add(Math.clamp(region.y() - h - gap, 0, maxY));
            ys.add(Math.clamp(region.y() + region.height() + gap, 0, maxY));
        }
        Position nearest = null;
        int shortest = Integer.MAX_VALUE;
        for (int left : xs) {
            for (int top : ys) {
                int distance = Math.abs(left - x) + Math.abs(top - y);
                if (distance >= shortest) continue;
                if (occupied.stream().noneMatch(region -> region.overlaps(left, top, w, h, gap))) {
                    nearest = new Position(left, top);
                    shortest = distance;
                }
            }
        }
        return nearest;
    }

    public Position update(int x, int y, int w, int h, BiConsumer<Integer, Integer> setPosition) {
        observe(id, x, y, w, h, setPosition, id);
        return new Position(x, y);
    }

    public void cancel() { forget(id); }
}
