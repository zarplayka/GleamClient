package ru.orion.client.hud;

import org.joml.Matrix3x2f;

/** Проверяет GPU-области без запуска окна и инициализации render pipeline. */
public final class OrionGlassCaptureTest {
    public static void main(String[] args) {
        Matrix3x2f identity = new Matrix3x2f();
        var top = area(identity, 10, 20, 100, 50, false);
        check(top.equals(new OrionGlassCompositor.CaptureArea(14, 34, 212, 112)), "Масштаб и запас размытия");
        var bottom = area(identity, 10, 20, 100, 50, true);
        check(bottom.equals(new OrionGlassCompositor.CaptureArea(14, 934, 212, 112)), "Начало координат OpenGL");
        check((long) top.width() * top.height() < 1920L * 1080 / 80, "Маленькая панель не копирует весь экран");
        check(area(identity, -5, -5, 10, 10, false).equals(new OrionGlassCompositor.CaptureArea(0, 0, 16, 16)), "Левый верхний край");
        check(area(identity, 1000, 0, 20, 20, false) == null, "За правым краем");
        check(area(identity, 0, 600, 20, 20, true) == null, "За нижним краем");
        check(area(identity, 0, 0, 0, 20, false) == null, "Пустая панель");
        check(area(new Matrix3x2f().translate(-6, 3), 10, 20, 100, 50, false)
                .equals(new OrionGlassCompositor.CaptureArea(2, 40, 212, 112)), "Смещение scoreboard");
        check(area(new Matrix3x2f().set(0, 1, -1, 0, 100, 0), 10, 20, 100, 50, false)
                .equals(new OrionGlassCompositor.CaptureArea(54, 14, 112, 212)), "Поворот панели");
        check(area(new Matrix3x2f().set(-1, 0, 0, 1, 100, 0), 10, 20, 20, 50, false)
                .equals(new OrionGlassCompositor.CaptureArea(134, 34, 52, 112)), "Отражение панели");
        int samples = 0;
        for (int scale = 1; scale <= 6; scale++) for (int x = -100; x <= 1100; x += 37) {
            var result = OrionGlassCompositor.CaptureArea.from(identity, x, x / 2, 120, 40,
                    960, 540, 960 * scale, 540 * scale, (x & 1) == 0);
            if (result != null) {
                check(result.x() >= 0 && result.y() >= 0 && result.width() > 0 && result.height() > 0
                        && result.x() + result.width() <= 960 * scale
                        && result.y() + result.height() <= 540 * scale, "Выход за framebuffer");
            }
            samples++;
        }
        System.out.println("Glass capture checks passed: 10 scenarios, " + samples + " edge/scale samples");
    }

    private static OrionGlassCompositor.CaptureArea area(Matrix3x2f pose, int x, int y, int w, int h, boolean bottom) {
        return OrionGlassCompositor.CaptureArea.from(pose, x, y, w, h, 960, 540, 1920, 1080, bottom);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
