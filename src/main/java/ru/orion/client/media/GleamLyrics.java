package ru.orion.client.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Не придумывает пословные тайминги. end=-1 означает, что источник не указал конец. */
public record GleamLyrics(String status, List<Line> lines, String source) {
    public record Word(long start, long end, String text) { }
    public record Line(long start, long end, String text, List<Word> words) { }
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):([0-5]\\d)(?:[.:](\\d{1,3}))?]");
    private static final Pattern WORD = Pattern.compile("<(\\d{1,3}):([0-5]\\d)(?:[.:](\\d{1,3}))?>");
    private static final Pattern OFFSET = Pattern.compile("(?im)^\\[offset:([+-]?\\d{1,7})]\\s*$");
    private static final long MAX_TIME = 86_400_000;

    public static GleamLyrics empty(String status) { return new GleamLyrics(status, List.of(), "LRCLIB"); }

    public static GleamLyrics parse(JsonObject record) {
        if (record.has("instrumental") && record.get("instrumental").getAsBoolean()) return empty("instrumental");
        String yaml = string(record, "lyricsfile");
        List<Line> parsed = List.of();
        if (!yaml.isBlank()) {
            try {
                parsed = fromYaml(yaml);
                if (parsed.stream().anyMatch(line -> !line.words().isEmpty()))
                    return new GleamLyrics("ready", parsed, "LRCLIB · Lyricsfile");
            } catch (RuntimeException invalid) { /* Валидный LRC остаётся доступен при неподдерживаемом YAML. */ }
        }
        var lrc = fromLrc(string(record, "syncedLyrics"));
        if (lrc.stream().anyMatch(line -> !line.words().isEmpty())) return new GleamLyrics("ready", lrc, "LRCLIB · LRC");
        if (parsed.stream().anyMatch(line -> !line.text().isBlank())) return new GleamLyrics("ready", parsed, "LRCLIB · Lyricsfile");
        if (lrc.stream().anyMatch(line -> !line.text().isBlank())) return new GleamLyrics("ready", lrc, "LRCLIB · LRC");
        return empty(string(record, "plainLyrics").isBlank() ? "missing" : "unsynced");
    }

    static List<Line> fromYaml(String text) {
        if (text.length() > 512_000) throw new IllegalArgumentException("Lyricsfile too large");
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(12);
        options.setCodePointLimit(512_000);
        Object value = new Yaml(new SafeConstructor(options)).load(text);
        if (!(value instanceof Map<?, ?> root) || !"1.0".equals(root.get("version"))) return List.of();
        if (!(root.get("lines") instanceof List<?> rows) || rows.size() > 2000) return List.of();
        var lines = new ArrayList<Line>();
        int count = 0;
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> line)) continue;
            long start = milliseconds(line.get("start_ms")), end = milliseconds(line.get("end_ms"));
            if (start < 0 || end >= 0 && end < start) continue;
            String content = content(line.get("text"));
            var words = new ArrayList<Word>();
            if (line.get("words") instanceof List<?> segments && segments.size() <= 128) {
                for (Object segment : segments) {
                    if (!(segment instanceof Map<?, ?> word)) { words.clear(); break; }
                    long ws = milliseconds(word.get("start_ms")), we = milliseconds(word.get("end_ms"));
                    String wordText = content(word.get("text"));
                    if (ws < start || we >= 0 && we < ws || end >= 0 && (ws > end || we > end)
                            || !words.isEmpty() && ws < words.getLast().start() || wordText.isEmpty()) {
                        words.clear(); break;
                    }
                    words.add(new Word(ws, we, wordText));
                }
                // Не размечаем слова, не соответствующие настоящему тексту строки.
                StringBuilder joined = new StringBuilder();
                for (Word word : words) joined.append(word.text());
                if (!joined.toString().equals(content)) words.clear();
            }
            count += words.size();
            if (count > 20_000) throw new IllegalArgumentException("Too many words");
            lines.add(new Line(start, end, content, List.copyOf(words)));
        }
        lines.sort(Comparator.comparingLong(Line::start));
        return List.copyOf(lines);
    }

    static List<Line> fromLrc(String text) {
        if (text.length() > 512_000) return List.of();
        var offset = OFFSET.matcher(text);
        long shift = offset.find() ? Long.parseLong(offset.group(1)) : 0;
        var result = new ArrayList<Line>();
        for (String row : text.split("\\R")) {
            var times = TIME.matcher(row);
            var starts = new ArrayList<Long>();
            int end = 0;
            // Несколько временных меток в начале строки обозначают повторы.
            while (times.find() && row.substring(end, times.start()).isBlank()) {
                starts.add(timestamp(times) + shift); end = times.end();
            }
            if (starts.isEmpty()) continue;
            String body = row.substring(end).stripLeading();
            var matcher = WORD.matcher(body);
            var words = new ArrayList<Word>();
            int previous = -1; long wordStart = -1;
            while (matcher.find()) {
                if (words.size() >= 128) { words.clear(); previous = -1; break; }
                if (previous >= 0 && matcher.start() > previous)
                    words.add(new Word(wordStart, timestamp(matcher) + shift, content(body.substring(previous, matcher.start()))));
                wordStart = timestamp(matcher) + shift; previous = matcher.end();
            }
            if (previous >= 0 && previous < body.length()) words.add(new Word(wordStart, -1, content(body.substring(previous))));
            String content = content(WORD.matcher(body).replaceAll(""));
            if (!String.join("", words.stream().map(Word::text).toList()).equals(content)) words.clear();
            for (long start : starts) {
                if (start < 0 || start > MAX_TIME) continue;
                long delta = start - starts.getFirst();
                var shifted = new ArrayList<Word>();
                long last = start;
                for (Word word : words) {
                    if (word.start() + delta < last || word.end() >= 0 && word.end() < word.start()) { shifted.clear(); break; }
                    shifted.add(new Word(word.start() + delta, word.end() < 0 ? -1 : word.end() + delta, word.text()));
                    last = word.start() + delta;
                }
                long lineEnd = shifted.isEmpty() ? -1 : shifted.getLast().end();
                result.add(new Line(start, lineEnd, content, List.copyOf(shifted)));
                if (result.size() >= 2000) break;
            }
            if (result.size() >= 2000) break;
        }
        result.sort(Comparator.comparingLong(Line::start));
        return List.copyOf(result);
    }

    public JsonObject json() {
        var result = new JsonObject();
        result.addProperty("status", status); result.addProperty("source", source);
        var rows = new JsonArray();
        boolean wordSync = false;
        for (Line line : lines) {
            var row = new JsonObject();
            row.addProperty("start", line.start()); row.addProperty("end", line.end()); row.addProperty("text", line.text());
            var words = new JsonArray();
            for (Word word : line.words()) {
                var item = new JsonObject();
                item.addProperty("start", word.start()); item.addProperty("end", word.end()); item.addProperty("text", word.text());
                words.add(item);
            }
            wordSync |= !words.isEmpty(); row.add("words", words); rows.add(row);
        }
        result.addProperty("wordSync", wordSync); result.add("lines", rows);
        return result;
    }

    private static long timestamp(java.util.regex.Matcher matcher) {
        String fraction = matcher.group(3);
        return Long.parseLong(matcher.group(1)) * 60000 + Long.parseLong(matcher.group(2)) * 1000
                + (fraction == null ? 0 : Long.parseLong((fraction + "000").substring(0, 3)));
    }
    private static long milliseconds(Object value) {
        if (!(value instanceof Number n)) return -1;
        double d = n.doubleValue();
        return Double.isFinite(d) && d >= 0 && d <= MAX_TIME && d == Math.floor(d) ? n.longValue() : -1;
    }
    private static String content(Object value) {
        if (!(value instanceof String text) || text.length() > 512) return "";
        return text.replaceAll("[\\p{Cc}&&[^\\t]]", "");
    }
    private static String string(JsonObject value, String key) {
        return value.has(key) && value.get(key).isJsonPrimitive() ? value.get(key).getAsString() : "";
    }
}
