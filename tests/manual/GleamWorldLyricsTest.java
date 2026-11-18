package ru.orion.client.media;

import java.util.List;

/** Проверяет настоящие контуры, отверстия, переносы и переходы по меткам. */
public final class GleamWorldLyricsTest {
    private static int checks;
    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
        checks++;
    }
    public static void main(String[] args) {
        var first = new GleamLyrics.Line(1000, 2700, "В небе", List.of(
                new GleamLyrics.Word(1000, 1350, "В "), new GleamLyrics.Word(1500, -1, "небе")));
        var marker = new GleamLyrics.Line(2700, -1, "", List.of());
        var second = new GleamLyrics.Line(5000, -1, "Above the valley there are a thousand small lanterns", List.of());
        var timeline = new GleamLyricsTimeline(List.of(first, marker, second));
        check(timeline.find(999) == -1 && timeline.find(1000) == 0, "Cue begins on its exact start");
        check(timeline.find(2699) == 0 && timeline.find(2700) == -1, "An explicit silence cue clears the scene");
        check(timeline.find(12001) == -1 && timeline.nextAfter(2800) == 2, "Visual hold and next-cue prefetch");
        check(timeline.find(5200) == 2 && timeline.find(1200) == 0, "Backward seek resolves the earlier cue");
        check(GleamLyricsTimeline.wordEnd(first, 0, 2700) == 1350 && GleamLyricsTimeline.wordEnd(first, 1, 2700) == 2700,
                "Word boundaries retain explicit pauses");
        var seek = new GleamLyricsTimeline.Seek("track", 50, 10_000_000_000L);
        check(!seek.settled("track", 14, true, 11_000_000_000L), "Stale seek snapshot is suppressed");
        check(seek.settled("track", 50.7, true, 11_000_000_000L), "Seek clears after an authoritative snapshot");
        check(!seek.blocks("other", 11_000_000_000L) && !seek.blocks("track", 18_000_000_000L), "Track switch and seek timeout clear hold");
        check(GleamLyricsTimeline.smoothPosition(1600, 1480, 16, true) >= 1600, "Small media jitter cannot reveal a word twice");
        check(GleamLyricsTimeline.smoothPosition(1600, 200, 16, true) == 200, "External seek is still applied");
        check(GleamLyricsTimeline.smoothPosition(1600, 1650, 16, false) == 1650, "Paused media uses the authoritative position");

        var mesh = new GleamLyricsMesh();
        var glyph = mesh.prepare(new GleamLyrics.Line(0, -1, "O", List.of()), "Poppins").pieces().getFirst().glyph();
        float[] faces = glyph.faces();
        check(faces.length >= 16 && faces.length % 8 == 0, "Front is filled with complete quads");
        check(glyph.contour().length >= 16 && glyph.contour().length % 4 == 0, "Contour supports full 3D side walls");
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, maxX = -minX, maxY = -minY;
        for (int i = 0; i < faces.length; i += 2) {
            minX = Math.min(minX, faces[i]); maxX = Math.max(maxX, faces[i]);
            minY = Math.min(minY, faces[i + 1]); maxY = Math.max(maxY, faces[i + 1]);
        }
        float midX = (minX + maxX) / 2, midY = (minY + maxY) / 2;
        for (int i = 0; i < faces.length; i += 8) if (midY >= faces[i + 1] && midY <= faces[i + 5]) {
            float mix = (midY - faces[i + 1]) / (faces[i + 5] - faces[i + 1]);
            float left = faces[i] + (faces[i + 6] - faces[i]) * mix, right = faces[i + 2] + (faces[i + 4] - faces[i + 2]) * mix;
            if (midX >= left && midX <= right) throw new AssertionError("The letter O has a filled hole");
        }
        checks++;
        var cyrillic = mesh.prepare(first, "Poppins");
        check(cyrillic.pieces().size() == 5 && cyrillic.pieces().getFirst().word() == 0
                && cyrillic.pieces().getLast().word() == 1, "Cyrillic fallback preserves timed word membership");
        var wrapped = mesh.prepare(second, "Poppins");
        check(wrapped.height() > 2 && wrapped.width() < 15 && wrapped.pieces().size() > 35, "Long phrases wrap without dropping glyphs");
        System.out.println("World lyrics geometry and timeline checks passed: " + checks);
    }
}
