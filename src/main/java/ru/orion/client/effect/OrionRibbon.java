package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Камеро-ориентированная лента с общими вершинами на стыках и мягкими краями. */
final class OrionRibbon {
    record Point(Vec3 position, int color) { }
    private record Section(Vec3 center, Vec3 side, int color) { }

    static List<Point> smooth(List<Point> points, int subdivisions) {
        if (points.size() < 2 || subdivisions < 2) return points;
        List<Point> result = new ArrayList<>((points.size() - 1) * subdivisions + 1);
        for (int i = 0; i < points.size() - 1; i++) {
            Point a = points.get(i), b = points.get(i + 1);
            Vec3 p0 = points.get(Math.max(0, i - 1)).position();
            Vec3 p3 = points.get(Math.min(points.size() - 1, i + 2)).position();
            for (int j = 0; j < subdivisions; j++) {
                float t = j / (float) subdivisions;
                result.add(new Point(catmull(p0, a.position(), b.position(), p3, t), mix(a.color(), b.color(), t)));
            }
        }
        result.add(points.get(points.size() - 1));
        return result;
    }

    private static Vec3 catmull(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float t) {
        double t2 = t * t, t3 = t2 * t;
        return p1.scale(2).add(p2.subtract(p0).scale(t))
                .add(p0.scale(2).subtract(p1.scale(5)).add(p2.scale(4)).subtract(p3).scale(t2))
                .add(p0.scale(-1).add(p1.scale(3)).subtract(p2.scale(3)).add(p3).scale(t3)).scale(0.5);
    }

    static void draw(PoseStack.Pose pose, VertexConsumer vc, List<Point> points, Vec3 camera, float width, boolean glow) {
        if (points.size() < 2) return;
        List<Section> sections = new ArrayList<>(points.size());
        Vec3 previousSide = null;
        for (int i = 0; i < points.size(); i++) {
            Vec3 center = points.get(i).position();
            Vec3 tangent = points.get(Math.min(points.size() - 1, i + 1)).position()
                    .subtract(points.get(Math.max(0, i - 1)).position());
            Vec3 side = tangent.cross(camera.subtract(center));
            if (side.lengthSqr() < 1.0E-10) side = previousSide != null ? previousSide : tangent.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1.0E-10) side = new Vec3(1, 0, 0);
            side = side.normalize();
            if (previousSide != null && side.dot(previousSide) < 0) side = side.scale(-1);
            sections.add(new Section(center, side, points.get(i).color()));
            previousSide = side;
        }
        float core = width * 0.5F;
        float edge = width * (glow ? 2.0F : 0.8F);
        for (int i = 1; i < sections.size(); i++) {
            Section a = sections.get(i - 1), b = sections.get(i);
            band(pose, vc, a, b, -edge, -core, true, false);
            band(pose, vc, a, b, -core, core, false, false);
            band(pose, vc, a, b, core, edge, false, true);
        }
    }

    private static void band(PoseStack.Pose pose, VertexConsumer vc, Section a, Section b,
                             float left, float right, boolean fadeLeft, boolean fadeRight) {
        vertex(pose, vc, a.center().add(a.side().scale(left)), faded(a.color(), fadeLeft));
        vertex(pose, vc, b.center().add(b.side().scale(left)), faded(b.color(), fadeLeft));
        vertex(pose, vc, b.center().add(b.side().scale(right)), faded(b.color(), fadeRight));
        vertex(pose, vc, a.center().add(a.side().scale(right)), faded(a.color(), fadeRight));
    }
    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, Vec3 p, int color) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(color);
    }
    private static int faded(int color, boolean fade) {
        return fade ? ARGB.color(0, ARGB.red(color), ARGB.green(color), ARGB.blue(color)) : color;
    }
    private static int mix(int a, int b, float t) {
        return ARGB.color(Math.round(ARGB.alpha(a) + (ARGB.alpha(b) - ARGB.alpha(a)) * t),
                Math.round(ARGB.red(a) + (ARGB.red(b) - ARGB.red(a)) * t),
                Math.round(ARGB.green(a) + (ARGB.green(b) - ARGB.green(a)) * t),
                Math.round(ARGB.blue(a) + (ARGB.blue(b) - ARGB.blue(a)) * t));
    }
    private OrionRibbon() { }
}
