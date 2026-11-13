package ru.orion.client.hud;

import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.List;

/** Проекция и размещение меток без запуска Minecraft. */
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
        System.out.println("Waypoint checks passed: " + checked + " projections and overlap checks");
    }
    private static void check(boolean valid, String label) { if (!valid) throw new AssertionError(label); }
}
