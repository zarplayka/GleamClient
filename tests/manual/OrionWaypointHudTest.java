package ru.orion.client.hud;

import org.joml.Vector4f;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Проекция, размещение и Java2D-макет меток без запуска Minecraft. */
public final class OrionWaypointHudTest {
    public static void main(String[] args) throws Exception {
        int checked = 0;
        for (int width : new int[]{96, 160, 320, 960, 1920}) for (int height : new int[]{64, 120, 240, 540, 1080}) {
            var center = OrionWaypointHud.project(new Vector4f(0, 0, 0, 1), width, height);
            check(!center.edge() && center.x() == width / 2F && center.y() == height / 2F, "center projection");
            for (float w : new float[]{-10, -1, 0, .001F, 1, 100}) for (float x : new float[]{-100, -1, 0, 1, 100}) {
                var projected = OrionWaypointHud.project(new Vector4f(x, x / 2, 0, w), width, height);
                check(projected != null && Float.isFinite(projected.x()) && Float.isFinite(projected.y()), "finite edge projection");
                check(projected.x() >= 0 && projected.x() <= width && projected.y() >= 0 && projected.y() <= height, "inside viewport");
                if (w <= 0) check(projected.edge(), "behind camera uses arrow");
                checked++;
            }
            var placed = new ArrayList<OrionWaypointHud.Bounds>();
            for (int i = 0; i < 30; i++) {
                var result = OrionWaypointHud.place(width / 2, height / 2, 48, 24, width, height, placed);
                if (result == null) break;
                check(result.x() >= 2 && result.y() >= 2 && result.x() + result.width() <= width - 2 && result.y() + result.height() <= height - 2, "placement bounds");
                check(placed.stream().noneMatch(result::intersects), "no label overlap");
                placed.add(result);
            }
        }
        check(OrionWaypointHud.directlyAimed(new OrionWaypointHud.Projection(160, 120, 0, false), 320, 240), "direct aim expands label");
        check(!OrionWaypointHud.directlyAimed(new OrionWaypointHud.Projection(168, 120, 0, false), 320, 240), "indirect aim keeps label compact");
        check(!OrionWaypointHud.directlyAimed(new OrionWaypointHud.Projection(160, 120, 0, true), 320, 240), "edge indicators stay compact");
        check(OrionWaypointHud.project(new Vector4f(Float.NaN, 0, 0, 1), 320, 240) == null, "NaN rejected");
        check(OrionWaypointHud.place(0, 0, 200, 24, 96, 64, List.of()) == null, "oversized label hidden");
        check(OrionWaypointHud.formatDistance(0).equals("0 м"), "arrival distance");
        check(OrionWaypointHud.formatDistance(12345).equals("12.3 км"), "kilometres");
        var preview = new BufferedImage(740, 460, BufferedImage.TYPE_INT_ARGB);
        var g = preview.createGraphics();
        g.setColor(new java.awt.Color(46, 49, 58)); g.fill(new java.awt.geom.Rectangle2D.Float(0, 0, 740, 460));
        int row = 0;
        for (boolean detailed : new boolean[]{false, true}) for (boolean death : new boolean[]{false, true}) {
            for (int scale = 1; scale <= 6; scale++) {
                var image = OrionWaypointHud.drawMarker("ОченьДлиннаяМеткаЯё😀Название", "12.3 км · -2048 м", "-30000000 · -2048 · 30000000", "#FF9B38", death, scale, detailed,
                        detailed ? Integer.MIN_VALUE : 8, 180);
                check(image.getWidth() <= 180 * scale && image.getWidth() % scale == 0 && image.getHeight() % scale == 0, "texture and blit alignment");
                if (scale == 2) g.drawImage(image, 20 + (row % 2) * 360, 20 + (row / 2) * 110, null);
            }
            row++;
        }
        // Полный и компактный режим на узком окне.
        for (int scale = 1; scale <= 6; scale++) {
            var narrow = OrionWaypointHud.drawMarker("Яё", "30000000 м", "0 · 64 · 0", null, false, scale, true, Integer.MIN_VALUE, 48);
            check(narrow.getWidth() == 48 * scale, "narrow viewport");
        }
        g.dispose();
        ImageIO.write(preview, "png", Path.of(args[0]).toFile());
        System.out.println("Waypoint checks passed: " + checked + " projections, overlap checks and scales 1–6");
    }
    private static void check(boolean valid, String label) { if (!valid) throw new AssertionError(label); }
}
