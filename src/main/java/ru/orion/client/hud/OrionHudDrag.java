package ru.orion.client.hud;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    static {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(REGIONS::clear));
    }
    private final String id;

    public OrionHudDrag(String id) { this.id = id; }

    public record Position(int x, int y) {}
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

    Position placeWidget(GuiGraphicsExtractor context, int width, int height, int defaultX, int defaultY,
                         OrionConfigManager manager) {
        var config = manager.getConfig();
        int savedX = config.getExtraHudPosition(id, 0);
        int savedY = config.getExtraHudPosition(id, 1);
        int maxX = Math.max(0, context.guiWidth() - width);
        int maxY = Math.max(0, context.guiHeight() - height);
        int x = Math.clamp(savedX < 0 ? defaultX : savedX, 0, maxX);
        int y = Math.clamp(savedY < 0 ? defaultY : savedY, 0, maxY);
        // Auto-place unused defaults. Explicit positions belong to the user.
        Position position = savedX >= 0 && savedY >= 0
                ? new Position(x, y) : available(x, y, width, height, maxX, maxY);
        if (position == null) {
            forget(id);
            return null;
        }
        return update(position.x(), position.y(), width, height, (left, top) -> config.setExtraHudPosition(id, left, top));
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
