package ru.orion.client.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;

/** Общий силуэт эмблемы Gleam для ClickGUI и колеса эмоций. */
public final class OrionBrandMark {
    private static final float DESIGN_SIZE = 1024.0F;
    private static final Shape MARK = createMark();

    private OrionBrandMark() {
    }

    public static void drawBesideText(Graphics2D graphics, float centerX, float baseline, float size,
                                      Font font, String text, Color color) {
        double centerY = font.createGlyphVector(graphics.getFontRenderContext(), text)
                .getVisualBounds().getCenterY();
        draw(graphics, centerX, baseline + (float) centerY, size, color);
    }

    public static void draw(Graphics2D graphics, float centerX, float centerY, float size, Color color) {
        float scale = size / DESIGN_SIZE;
        AffineTransform transform = new AffineTransform(scale, 0, 0, scale,
                centerX - size / 2.0F, centerY - size / 2.0F);
        graphics.setColor(color);
        graphics.fill(transform.createTransformedShape(MARK));
    }

    private static Shape createMark() {
        // Coordinates match the launcher SVG and its 115 126 1024 1024 viewBox.
        Path2D.Float eye = new Path2D.Float();
        eye.moveTo(147, 695);
        eye.curveTo(195, 579, 448, 450, 730, 454);
        eye.lineTo(598, 692);
        eye.lineTo(650, 543);
        eye.curveTo(574, 548, 502, 563, 438, 581);
        eye.curveTo(413, 634, 413, 698, 441, 748);
        eye.curveTo(475, 806, 536, 840, 610, 840);
        eye.curveTo(714, 840, 799, 755, 799, 650);
        eye.curveTo(799, 612, 788, 580, 768, 550);
        eye.lineTo(643, 661);
        eye.lineTo(768, 455);
        eye.curveTo(899, 461, 1026, 448, 1111, 386);
        eye.curveTo(1095, 452, 1054, 505, 1010, 535);
        eye.curveTo(954, 725, 794, 890, 581, 890);
        eye.curveTo(430, 890, 304, 814, 203, 713);
        eye.curveTo(182, 711, 166, 717, 154, 717);
        eye.curveTo(143, 718, 140, 711, 147, 695);
        eye.closePath();
        return AffineTransform.getTranslateInstance(-115, -126).createTransformedShape(eye);
    }
}
