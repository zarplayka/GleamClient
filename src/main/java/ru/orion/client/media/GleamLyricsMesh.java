package ru.orion.client.media;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.text.Bidi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Геометрия глифов в em. Передние грани заполняются с учётом внутренних отверстий. */
final class GleamLyricsMesh {
    private static final float EM = 64F;
    private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);
    private static final Pattern WORDS = Pattern.compile("\\S+\\s*|\\s+");
    private static final String FONT_ROOT = "/assets/orion-client/fonts/";

    record Glyph(float[] faces, float[] contour) { }
    record Piece(Glyph glyph, float x, float y, float wordCenter, int word, int letter) { }
    record Phrase(List<Piece> pieces, float width, float height) { }
    private record Run(GlyphVector glyphs, Font font, float width) { }
    private record Key(Font font, int glyphCode) { }
    private record Edge(double x0, double y0, double x1, double y1) {
        boolean crosses(double y) { return y > Math.min(y0, y1) && y < Math.max(y0, y1); }
        double xAt(double y) { return x0 + (x1 - x0) * (y - y0) / (y1 - y0); }
    }

    // Эти кеши принадлежат одному рабочему потоку, который готовит соседние фразы.
    private final Map<String, Font> fonts = new HashMap<>();
    private final LinkedHashMap<Key, Glyph> glyphs = new LinkedHashMap<>(64, .75F, true);

    Phrase prepare(GleamLyrics.Line line, String family) {
        var segments = new ArrayList<String>();
        if (line.words().isEmpty()) WORDS.matcher(line.text()).results().forEach(match -> segments.add(match.group()));
        else for (var word : line.words()) segments.add(word.text());
        Font primary = fonts.computeIfAbsent(family, GleamLyricsMesh::loadFont);
        Font fallback = fonts.computeIfAbsent("Paradox", GleamLyricsMesh::loadFont);
        var runs = new ArrayList<Run>(segments.size());
        float total = 0;
        for (String segment : segments) {
            Font font = primary.canDisplayUpTo(segment) < 0 ? primary : fallback;
            if (font.canDisplayUpTo(segment) >= 0) font = fonts.computeIfAbsent("Sans", GleamLyricsMesh::loadFont);
            char[] chars = segment.toCharArray();
            int direction = new Bidi(segment, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()
                    ? Font.LAYOUT_LEFT_TO_RIGHT : Font.LAYOUT_RIGHT_TO_LEFT;
            GlyphVector vector = font.layoutGlyphVector(CONTEXT, chars, 0, chars.length, direction);
            float width = (float) vector.getGlyphPosition(vector.getNumGlyphs()).getX() / EM;
            runs.add(new Run(vector, font, width));
            total += width;
        }
        float wrap = total > 12F ? Math.clamp(total * .58F, 7F, 13F) : total + .01F;
        var pieces = new ArrayList<Piece>();
        float maxWidth = .1F;
        int rows = 0;
        for (int first = 0; first < runs.size();) {
            int end = first;
            float rowWidth = 0;
            do { rowWidth += runs.get(end++).width(); }
            while (end < runs.size() && rowWidth + runs.get(end).width() <= wrap);
            maxWidth = Math.max(maxWidth, rowWidth);
            float x = -rowWidth / 2;
            float baseline = -.26F - rows * 1.16F;
            for (int i = first; i < end; i++) {
                Run run = runs.get(i);
                float center = x + run.width() / 2;
                for (int glyph = 0; glyph < run.glyphs().getNumGlyphs(); glyph++) {
                    var outline = glyph(run.font(), run.glyphs().getGlyphCode(glyph));
                    if (outline.faces().length == 0) continue;
                    var pos = run.glyphs().getGlyphPosition(glyph);
                    pieces.add(new Piece(outline, x + (float) pos.getX() / EM, baseline - (float) pos.getY() / EM,
                            center, i, glyph));
                }
                x += run.width();
            }
            rows++;
            first = end;
        }
        float yShift = Math.max(0, rows - 1) * .58F;
        var centered = pieces.stream().map(p -> new Piece(p.glyph(), p.x(), p.y() + yShift, p.wordCenter(), p.word(), p.letter())).toList();
        return new Phrase(centered, maxWidth, Math.max(1F, rows * 1.16F));
    }

    void clear() { glyphs.clear(); }

    private Glyph glyph(Font font, int code) {
        Key key = new Key(font, code);
        Glyph cached = glyphs.get(key);
        if (cached != null) return cached;
        var shape = new Area(font.createGlyphVector(CONTEXT, new int[]{code}).getGlyphOutline(0));
        var edges = new ArrayList<Edge>();
        var cuts = new TreeSet<Double>();
        var iterator = shape.getPathIterator(null, .42);
        double[] p = new double[6];
        double x = 0, y = 0, startX = 0, startY = 0;
        while (!iterator.isDone()) {
            int type = iterator.currentSegment(p);
            double nextX = type == PathIterator.SEG_CLOSE ? startX : p[0] / EM;
            double nextY = type == PathIterator.SEG_CLOSE ? startY : -p[1] / EM;
            if (type == PathIterator.SEG_MOVETO) { startX = nextX; startY = nextY; }
            else if (x != nextX || y != nextY) {
                edges.add(new Edge(x, y, nextX, nextY));
                cuts.add(y); cuts.add(nextY);
            }
            x = nextX; y = nextY;
            iterator.next();
        }

        // В каждой полосе пересечения прямолинейны. Соседние пары образуют трапеции, в том числе вокруг дырок.
        var face = new ArrayList<Float>();
        var crossing = new ArrayList<Edge>();
        Double previous = null;
        for (double top : cuts) {
            if (previous == null) { previous = top; continue; }
            double bottom = previous;
            previous = top;
            double middle = (bottom + top) * .5;
            if (top - bottom < 1e-7) continue;
            crossing.clear();
            for (var edge : edges) if (edge.crosses(middle)) crossing.add(edge);
            crossing.sort(Comparator.comparingDouble(edge -> edge.xAt(middle)));
            for (int i = 0; i + 1 < crossing.size(); i += 2) {
                Edge left = crossing.get(i), right = crossing.get(i + 1);
                add(face, left.xAt(bottom), bottom); add(face, right.xAt(bottom), bottom);
                add(face, right.xAt(top), top); add(face, left.xAt(top), top);
            }
        }
        float[] sides = new float[edges.size() * 4];
        for (int i = 0; i < edges.size(); i++) {
            var edge = edges.get(i);
            sides[i * 4] = (float) edge.x0(); sides[i * 4 + 1] = (float) edge.y0();
            sides[i * 4 + 2] = (float) edge.x1(); sides[i * 4 + 3] = (float) edge.y1();
        }
        float[] faces = new float[face.size()];
        for (int i = 0; i < faces.length; i++) faces[i] = face.get(i);
        Glyph result = new Glyph(faces, sides);
        glyphs.put(key, result);
        if (glyphs.size() > 384) glyphs.pollFirstEntry();
        return result;
    }

    private static void add(ArrayList<Float> target, double x, double y) { target.add((float) x); target.add((float) y); }

    private static Font loadFont(String family) {
        String file = switch (family) {
            case "Poppins" -> "poppins/Poppins-Bold.ttf";
            case "Paradox" -> "paradox/ParadoxSans-Bold.ttf";
            default -> null;
        };
        if (file != null) try (var stream = GleamLyricsMesh.class.getResourceAsStream(FONT_ROOT + file)) {
            if (stream != null) return Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont(EM);
        } catch (Exception ignored) { /* Логический шрифт сохраняет чтение при отсутствии ресурса. */ }
        String logical = switch (family) { case "Serif" -> Font.SERIF; case "Mono" -> Font.MONOSPACED; default -> Font.SANS_SERIF; };
        return new Font(logical, Font.BOLD, (int) EM);
    }
}
