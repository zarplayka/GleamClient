package ru.orion.client.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

/** Общий силуэт эмблемы Orion для ClickGUI и HUD. */
public final class OrionBrandMark {
    private static final float DESIGN_SIZE = 64.0F;
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
        Area ring = new Area(new Ellipse2D.Float(8, 8, 48, 48));
        ring.subtract(new Area(new Ellipse2D.Float(19, 19, 26, 26)));

        Line2D orbit = new Line2D.Float(8, 52, 56, 12);
        BasicStroke clearance = new BasicStroke(18, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        BasicStroke slash = new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        ring.subtract(new Area(clearance.createStrokedShape(orbit)));
        ring.add(new Area(slash.createStrokedShape(orbit)));
        return ring;
    }
}
