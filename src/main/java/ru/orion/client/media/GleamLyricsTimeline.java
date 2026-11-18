package ru.orion.client.media;

import java.util.List;

/** Навигация по исходным меткам без оценки времени произнесения слов. */
final class GleamLyricsTimeline {
    private final List<GleamLyrics.Line> lines;
    private final long[] ends;
    private final boolean[] blank;

    GleamLyricsTimeline(List<GleamLyrics.Line> lines) {
        this.lines = lines;
        ends = new long[lines.size()];
        blank = new boolean[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            blank[i] = line.text().isBlank();
            long next = i + 1 < lines.size() ? lines.get(i + 1).start() : Long.MAX_VALUE;
            ends[i] = line.end() >= line.start() ? line.end() : Math.min(next, line.start() + 7000);
        }
    }

    int find(double millis) {
        int lo = 0;
        int hi = lines.size();
        while (lo < hi) {
            int middle = (lo + hi) >>> 1;
            if (lines.get(middle).start() <= millis) lo = middle + 1;
            else hi = middle;
        }
        for (int i = lo - 1; i >= 0; i--) {
            if (blank[i]) break;
            if (millis < ends[i]) return i;
            if (millis - lines.get(i).start() > 30_000) break;
        }
        return -1;
    }

    int next(int after) {
        for (int i = after + 1; i < lines.size(); i++) if (!blank[i]) return i;
        return -1;
    }

    int nextAfter(double millis) {
        int lo = 0;
        int hi = lines.size();
        while (lo < hi) {
            int middle = (lo + hi) >>> 1;
            if (lines.get(middle).start() <= millis) lo = middle + 1;
            else hi = middle;
        }
        return next(lo - 1);
    }

    GleamLyrics.Line line(int i) { return lines.get(i); }
    long end(int i) { return ends[i]; }

    static long wordEnd(GleamLyrics.Line line, int index, long lineEnd) {
        var words = line.words();
        long explicit = words.get(index).end();
        return explicit >= 0 ? explicit : index + 1 < words.size() ? words.get(index + 1).start() : lineEnd;
    }

    static double smoothPosition(double last, double observed, double elapsed, boolean playing) {
        if (last < 0 || !playing || elapsed > 400) return observed;
        double expected = last + elapsed;
        double error = observed - expected;
        if (Math.abs(error) > 260) return observed;
        // Не даём небольшому дрожанию снимка вновь спрятать уже появившееся слово.
        return Math.max(last, expected + error * Math.min(.25, elapsed / 135));
    }

    record Seek(String track, double seconds, long started) {
        boolean blocks(String current, long now) { return track.equals(current) && now - started < 8_000_000_000L; }
        boolean settled(String current, double observed, boolean playing, long now) {
            double expected = seconds + (playing ? Math.max(0, now - started) / 1e9 : 0);
            return !blocks(current, now) || Math.abs(observed - expected) <= 1.5 || Math.abs(observed - seconds) <= .65;
        }
    }
}
