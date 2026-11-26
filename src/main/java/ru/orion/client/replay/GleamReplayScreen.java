//~ legacy_gui
package ru.orion.client.replay;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.OrionClient;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Replay library and editor in Minecraft's screen stack. */
final class GleamReplayScreen extends Screen {
    private static final int BG = 0xff15161e, LINE = 0xff30313e, TEXT = 0xfff1f1f7, MUTED = 0xffaeb0c1;
    private static final int ACCENT = 0xff927cf7;
    private static final Identifier VIDEO = asset("replay_video");
    private static final String[] FILTERS = {"Все", "Избранное", "Откаты", "Записи"};
    private static final double[] SPEEDS = {.25, .5, 1, 2};
    private static final String[] TIMES = new String[3600];
    static {
        String[] digits = new String[60];
        for (int n = 0; n < digits.length; n++) digits[n] = n < 10 ? "0" + n : Integer.toString(n);
        for (int second = 0; second < TIMES.length; second++)
            TIMES[second] = digits[second / 60] + ":" + digits[second % 60];
    }
    private final Screen parent;
    private final GleamReplay replay;
    private final Map<String, Identifier> thumbnails = new HashMap<>();
    private final Set<String> requested = new HashSet<>();
    private List<GleamReplay.Clip> previous = List.of();
    private List<GleamReplay.Clip> cards = List.of();
    private GleamReplay.Clip clip;
    private GleamReplay.Preferences prefs;
    private ReplayPlayback playback;
    private DynamicTexture video;
    private Button record, buffer, play, reset, copy, apply;
    private int left, top, wide, tall, columns, rows, cardWidth, cardHeight, page, filter, settings = -1;
    private int videoX, videoY, videoW, videoH, sidebar, trackY, drag;
    private long statusVersion, statusUntil, thumbnailGeneration;
    private String status = "", listening;
    private double position, from, to, speed = 1;
    private boolean playing, mute, zoomed;
    private boolean worldControls;

    GleamReplayScreen(Screen parent, GleamReplay replay) {
        super(Component.translatable(GleamReplay.LABEL));
        this.parent = parent;
        this.replay = replay;
        this.prefs = replay.preferences();
    }

    @Override public boolean isPauseScreen() { return true; }
    @Override protected void init() { rebuild(); }

    private void rebuild() {
        clearWidgets();
        clearFocus();
        wide = Math.min(width - 12, Math.max(370, width * 3 / 4));
        tall = Math.min(height - 12, Math.max(235, height * 3 / 4));
        left = (width - wide) / 2;
        top = (height - tall) / 2;
        record = buffer = play = reset = copy = apply = null;
        if (clip != null) { editorControls(); return; }

        int x = left + 12, y = top + 31;
        button(left + wide - 38, top + 7, 26, "Esc", this::onClose, false);
        button(left + wide / 2 - 79, top + 7, 74, "Клипы", () -> showSettings(-1), settings < 0);
        button(left + wide / 2, top + 7, 83, "Настройки", () -> showSettings(0), settings >= 0);
        if (settings >= 0) { settingsControls(x, y); return; }
        int tabWidth = Math.min(60, (wide - 24) / 4);
        for (int i = 0; i < FILTERS.length; i++) {
            int next = i;
            button(x + i * tabWidth, y, tabWidth - 3, FILTERS[i], () -> {
                filter = next;
                page = 0;
                rebuild();
            }, i == filter);
        }
        buffer = button(x, y + 21, 110, bufferLabel(), () -> {
            JsonObject values = new JsonObject();
            values.addProperty("enabled", !replay.buffered());
            replay.submit("buffer", values);
        }, false);
        button(left + wide - 72, y + 21, 60, "Папка", () -> send("folder"), false);
        worldControls = replay.inWorld();
        if (worldControls) {
            record = button(x + 116, y + 21, Math.min(134, wide - 212), recordLabel(), () -> send("record"), false);
        }
        libraryControls(x, y + 44);
    }

    private void libraryControls(int x, int y) {
        previous = replay.clips();
        List<GleamReplay.Clip> filtered = previous.stream().filter(item -> switch (filter) {
            case 1 -> item.favorite();
            case 2 -> item.kind().equals("replay");
            case 3 -> item.kind().equals("recording");
            default -> true;
        }).toList();
        columns = Math.clamp((wide - 18) / 122, 1, 4);
        cardWidth = (wide - 24 - (columns - 1) * 8) / columns;
        cardHeight = Math.min(76, cardWidth * 9 / 16);
        rows = Math.clamp((tall - 101) / (cardHeight + 26), 1, 3);
        int capacity = columns * rows;
        page = Math.clamp(page, 0, Math.max(0, (filtered.size() - 1) / capacity));
        cards = filtered.subList(page * capacity, Math.min(filtered.size(), (page + 1) * capacity));
        Set<String> visible = new HashSet<>();
        for (int i = 0; i < cards.size(); i++) {
            var item = cards.get(i);
            visible.add(thumbnailKey(item));
            int bx = x + i % columns * (cardWidth + 8), by = y + i / columns * (cardHeight + 26);
            button(bx, by + cardHeight + 2, cardWidth - 23, item.title(), () -> openClip(item), false);
            button(bx + cardWidth - 20, by + cardHeight + 2, 20, item.favorite() ? "★" : "☆", () -> favorite(item), false);
        }
        thumbnails.entrySet().removeIf(entry -> {
            if (visible.contains(entry.getKey())) return false;
            minecraft.getTextureManager().release(entry.getValue());
            requested.remove(entry.getKey());
            return true;
        });
        if (filtered.size() > capacity) {
            button(x, top + tall - 24, 24, "‹", () -> changePage(-1), false).active = page > 0;
            button(x + 28, top + tall - 24, 24, "›", () -> changePage(1), false).active = (page + 1) * capacity < filtered.size();
        }
        loadThumbnails();
    }

    private void loadThumbnails() {
        var missing = cards.stream().filter(item -> requested.add(thumbnailKey(item))).toList();
        if (missing.isEmpty()) return;
        long generation = thumbnailGeneration;
        Minecraft client = minecraft;
        Thread.startVirtualThread(() -> {
            for (var item : missing) {
                var path = replay.thumbnail(item.id());
                if (path == null) continue;
                try {
                    if (!Files.isRegularFile(path) || Files.size(path) > 1024 * 1024) continue;
                    NativeImage image = NativeImage.read(Files.readAllBytes(path));
                    client.execute(() -> {
                        String key = thumbnailKey(item);
                        if (generation != thumbnailGeneration || GleamMinecraft.screen(client) != this ||
                                cards.stream().noneMatch(card -> thumbnailKey(card).equals(key))) {
                            requested.remove(key);
                            image.close();
                            return;
                        }
                        Identifier id = asset("replay_" + item.id().toLowerCase(Locale.ROOT));
                        client.getTextureManager().register(id, new DynamicTexture(() -> "replay thumbnail", image));
                        thumbnails.put(key, id);
                    });
                } catch (java.io.IOException | IllegalArgumentException ignored) { }
            }
        });
    }

    private void settingsControls(int x, int y) {
        int w = Math.max(80, (wide - 30) / 3);
        button(x, y, w, "Запись", () -> showSettings(0), settings == 0);
        button(x + w + 3, y, w, "Звук", () -> showSettings(1), settings == 1);
        button(x + (w + 3) * 2, y, w, "Горячие клавиши", () -> showSettings(2), settings == 2);
        y += 25;
        int step = Math.clamp((tall - 72) / 6, 20, 25);
        if (settings == 0) {
            cycle(x, y, "Длина отката", "duration", prefs.duration(), "30", "60", "120", "custom");
            if (prefs.duration().equals("custom")) number(x, y += step, "Своя длина в секундах", "customSeconds", prefs.customSeconds(), 5, 300);
            cycle(x, y += step, "Разрешение", "resolution", prefs.resolution(), "720", "1080", "1440", "source");
            cycle(x, y += step, "FPS", "fps", Integer.toString(prefs.fps()), "30", "60");
            cycle(x, y += step, "Качество", "quality", prefs.quality(), "performance", "balanced", "quality");
            cycle(x, y += step, "Битрейт", "bitrateMode", prefs.bitrateMode(), "auto", "manual");
            if (prefs.bitrateMode().equals("manual")) number(x + (wide - 28) / 2 + 4, y, "Мбит/с", "bitrate", prefs.bitrate(), 2, 60);
        } else if (settings == 1) {
            cycle(x, y, "Источник", "audio", prefs.audio(), "game", "system", "none");
            cycle(x, y + step, "Микрофон", "mic", Boolean.toString(prefs.mic()), "false", "true");
        } else {
            bind(x, y, "save", "Сохранить откат");
            bind(x, y + step, "toggle", "Переключить буфер");
            bind(x, y + 2 * step, "record", "Начать / остановить запись");
            button(x, y + 3 * step, 145, "Сбросить клавиши", () -> {
                replay.submit("bindings", new JsonObject());
                listening = null;
            }, false);
        }
    }

    private void cycle(int x, int y, String label, String key, String current, String... choices) {
        int size = key.equals("bitrateMode") && current.equals("manual") ? (wide - 28) / 2 : wide - 24;
        button(x, y, size, label + " · " + option(current), () -> {
            int next = (List.of(choices).indexOf(current) + 1) % choices.length;
            JsonObject values = settingsValues();
            if (key.equals("mic")) values.addProperty(key, Boolean.parseBoolean(choices[next]));
            else values.addProperty(key, choices[next]);
            replay.submit("settings", values);
        }, false);
    }

    private void number(int x, int y, String label, String key, int value, int min, int max) {
        int size = key.equals("bitrate") ? Math.min(64, (wide - 40) / 4) : 62;
        EditBox field = new EditBox(font, x, y, size, 18, Component.literal(label));
        field.setMaxLength(3);
        field.setValue(Integer.toString(value));
        addRenderableWidget(field);
        button(x + size + 4, y, key.equals("bitrate") ? size + 8 : 132, "Сохранить " + label, () -> {
            try {
                JsonObject values = settingsValues();
                values.addProperty(key, Math.clamp(Integer.parseInt(field.getValue()), min, max));
                replay.submit("settings", values);
            } catch (NumberFormatException invalid) { message("Введи число от " + min + " до " + max); }
        }, false);
    }

    private JsonObject settingsValues() {
        var json = new JsonObject();
        json.addProperty("duration", prefs.duration()); json.addProperty("customSeconds", prefs.customSeconds());
        json.addProperty("resolution", prefs.resolution()); json.addProperty("fps", prefs.fps());
        json.addProperty("quality", prefs.quality()); json.addProperty("bitrateMode", prefs.bitrateMode());
        json.addProperty("bitrate", prefs.bitrate()); json.addProperty("audio", prefs.audio()); json.addProperty("mic", prefs.mic());
        return json;
    }

    private void bind(int x, int y, String key, String label) {
        String caption = key.equals(listening) ? "Нажми сочетание" : keyLabel(prefs.keys().get(key));
        button(x, y, wide - 24, label + " · " + caption, () -> { listening = key; rebuild(); }, key.equals(listening));
    }

    private void editorControls() {
        int x = left + 12, y = top + 8;
        button(x, y, 52, "‹ Назад", this::backToLibrary, false);
        button(left + wide - 37, y, 25, "Esc", this::onClose, false);
        videoX = x;
        videoY = top + 32;
        int rail = zoomed ? 0 : Math.clamp(wide / 4, 102, 176) + 10;
        videoW = wide - 24 - rail;
        videoH = Math.max(28, Math.min(videoW * 9 / 16, tall - 91));
        trackY = videoY + videoH + 9;
        sidebar = videoX + videoW + 10;
        play = button(x, trackY + 12, 32, playing ? "Ⅱ" : "▶", this::togglePlayback, true);
        button(x + 36, trackY + 12, 27, "−1", () -> seek(position - 1), false);
        button(x + 67, trackY + 12, 27, "+1", () -> seek(position + 1), false);
        int space = Math.max(17, (videoW - 125) / 2);
        button(x + 98, trackY + 12, Math.min(41, space), speed + "×", this::cycleSpeed, false);
        button(x + 102 + Math.min(41, space), trackY + 12, Math.min(30, space), mute ? "♪ ×" : "♪", () -> {
            boolean resume = playing;
            mute = !mute;
            if (resume) startPlayback(false);
            rebuild();
        }, false);
        button(x + videoW - 21, trackY + 12, 21, "⤢", () -> { zoomed = !zoomed; rebuild(); }, zoomed);
        if (zoomed) return;
        int side = left + wide - 12 - sidebar;
        button(sidebar, videoY + 50, side, clip.favorite() ? "★ В избранном" : "☆ В избранное", () -> favorite(clip), false);
        int half = (side - 3) / 2;
        button(sidebar, videoY + 70, half, "Имя", () -> minecraft.keyboardHandler.setClipboard(clip.title()), false);
        button(sidebar + half + 3, videoY + 70, side - half - 3, "Кадр", this::saveFrame, false);
        button(sidebar, videoY + 90, side, "Удалить", this::confirmRemove, false);
        copy = button(sidebar, videoY + 139, side, "Сохранить копию", () -> trim(false), true);
        apply = button(sidebar, videoY + 159, half, "Обрезать", () -> trim(true), false);
        reset = button(sidebar + half + 3, videoY + 159, side - half - 3, "Сбросить", () -> { from = 0; to = clip.seconds(); }, false);
    }

    private void showSettings(int next) { settings = next; prefs = replay.preferences(); listening = null; rebuild(); }
    private void changePage(int by) { page = Math.max(0, page + by); rebuild(); }
    private void send(String action) { replay.submit(action, new JsonObject()); }
    private String recordLabel() {
        if (replay.pending()) return "Подождите…";
        return replay.recording() ? "■ Стоп · " + time(replay.recordingSeconds()) : "● Начать запись";
    }
    private String bufferLabel() { return replay.buffered() ? "Буфер включён" : "Буфер выключен"; }

    private void openClip(GleamReplay.Clip item) {
        clip = item;
        position = from = 0;
        to = item.seconds();
        rebuild();
        startPlayback(true);
    }

    private void togglePlayback() {
        if (playing) stopPlayback();
        else {
            if (position >= clip.seconds() - .05) position = 0;
            startPlayback(false);
        }
    }

    private void seek(double time) {
        boolean resume = playing;
        position = Math.clamp(time, 0, Math.max(0, clip.seconds() - .075));
        startPlayback(!resume);
    }

    private void cycleSpeed() {
        int i = 0;
        while (SPEEDS[i] != speed) i++;
        speed = SPEEDS[(i + 1) % SPEEDS.length];
        if (playing) startPlayback(false);
        rebuild();
    }

    private void startPlayback(boolean still) {
        stopPlayback();
        var file = replay.media(clip.id());
        if (file == null) return;
        playback = new ReplayPlayback(replay, file, position, still, speed, mute);
        playing = !still;
    }

    private void stopPlayback() {
        if (playback != null) playback.close();
        playback = null;
        playing = false;
    }

    private void favorite(GleamReplay.Clip item) {
        JsonObject values = values(item);
        values.addProperty("favorite", !item.favorite());
        replay.submit("favorite", values);
    }

    private void trim(boolean overwrite) {
        stopPlayback();
        JsonObject values = values(clip);
        values.addProperty("start", Math.round(from * 1000));
        values.addProperty("end", Math.round(to * 1000));
        values.addProperty("replace", overwrite);
        replay.submit("trim", values);
    }

    private void confirmRemove() {
        stopPlayback();
        var item = clip;
        GleamMinecraft.setScreen(minecraft, new ConfirmScreen(remove -> {
            GleamMinecraft.setScreen(minecraft, this);
            if (remove) {
                replay.submit("remove", values(item));
                backToLibrary();
            } else startPlayback(true);
        }, Component.literal("Удалить клип?"), Component.literal(item.title())));
    }

    private void saveFrame() {
        if (video == null || video.getPixels() == null) return;
        NativeImage source = video.getPixels();
        NativeImage result = new NativeImage(source.getWidth(), source.getHeight(), false);
        result.copyFrom(source);
        var file = minecraft.gameDirectory.toPath().resolve("screenshots").resolve("Replay-" + UUID.randomUUID() + ".png");
        var client = minecraft;
        Thread.startVirtualThread(() -> {
            try (result) {
                Files.createDirectories(file.getParent());
                result.writeToFile(file);
                client.execute(() -> message("Кадр сохранён в screenshots"));
            } catch (java.io.IOException error) { client.execute(() -> message("Не удалось сохранить кадр")); }
        });
    }

    private static JsonObject values(GleamReplay.Clip item) {
        JsonObject values = new JsonObject();
        values.addProperty("id", item.id());
        return values;
    }

    private void backToLibrary() {
        stopPlayback();
        releaseVideo();
        clip = null;
        zoomed = false;
        rebuild();
    }

    @Override public void tick() {
        if (statusVersion != replay.messageVersion()) {
            statusVersion = replay.messageVersion();
            message(replay.message());
        }
        if (clip == null) {
            if (settings >= 0 && !prefs.equals(replay.preferences())) {
                prefs = replay.preferences();
                rebuild();
            } else if (settings < 0 && (previous != replay.clips() || worldControls != replay.inWorld())) rebuild();
            if (record != null) {
                record.setMessage(Component.literal(recordLabel()));
                record.active = !replay.pending();
            }
            if (buffer != null) buffer.setMessage(Component.literal(bufferLabel()));
        } else if (previous != replay.clips()) {
            previous = replay.clips();
            GleamReplay.Clip updated = previous.stream().filter(item -> item.id().equals(clip.id())).findFirst().orElse(null);
            if (updated == null) backToLibrary();
            else if (updated.revision() != clip.revision()) openClip(updated);
            else if (updated.favorite() != clip.favorite()) { clip = updated; rebuild(); }
        }
        if (play != null) play.setMessage(Component.literal(playing ? "Ⅱ" : "▶"));
        if (copy != null) copy.active = apply.active = reset.active = from > .001 || to < clip.seconds() - .001;
    }

    private void message(String value) { status = value; statusUntil = System.nanoTime() + 3_000_000_000L; }

    //? if >=26.1 {
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
    //?} else {
    /*@Override public void renderBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
    *///?}
        g.fill(0, 0, width, height, 0x8508090d);
    }

    //? if >=26.1 {
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
    //?} else {
    /*@Override public void render(GuiGraphicsExtractor g, int mx, int my, float delta) {
    *///?}
        g.fill(left - 1, top - 1, left + wide + 1, top + tall + 1, LINE);
        g.fill(left, top, left + wide, top + tall, BG);
        if (clip != null) drawEditor(g);
        else {
            label(g, "↶  Откаты", left + 12, top + 12, TEXT);
            if (settings < 0) drawLibrary(g);
        }
        if (statusUntil - System.nanoTime() > 0)
            label(g, font.plainSubstrByWidth(status, wide - 75), left + 62, top + tall - 17, 0xffa6efbc);
        //? if >=26.1 {
        super.extractRenderState(g, mx, my, delta);
        //?} else {
        /*super.render(g, mx, my, delta);
        *///?}
    }

    private void drawLibrary(GuiGraphicsExtractor g) {
        if (cards.isEmpty()) {
            String heading = filter == 3 ? "Пока нет записей" : filter == 1 ? "Пока нет избранных клипов" : "Пока нет откатов";
            int middle = left + wide / 2;
            int y = top + Math.max(105, tall / 2);
            g.fill(middle - 13, y - 22, middle + 13, y + 2, LINE);
            center(g, filter == 1 ? "☆" : "▶", middle, y - 16, MUTED);
            center(g, heading, middle, y + 11, TEXT);
            center(g, filter == 1 ? "Добавь клип в избранное" : "Сохрани откат или начни запись в мире", middle, y + 25, MUTED);
        }
        for (int i = 0; i < cards.size(); i++) {
            var item = cards.get(i);
            int x = left + 12 + i % columns * (cardWidth + 8);
            int y = top + 75 + i / columns * (cardHeight + 26);
            g.fill(x, y, x + cardWidth, y + cardHeight, 0xff222431);
            Identifier image = thumbnails.get(thumbnailKey(item));
            if (image != null) g.blit(image, x, y, x + cardWidth, y + cardHeight, 0, 1, 0, 1);
            else center(g, "▶", x + cardWidth / 2, y + cardHeight / 2 - 4, MUTED);
            String time = time(item.seconds());
            int tag = font.width(time) + 6;
            g.fill(x + cardWidth - tag - 3, y + cardHeight - 14, x + cardWidth - 3, y + cardHeight - 2, 0xdc10101c);
            label(g, time, x + cardWidth - tag, y + cardHeight - 12, TEXT);
        }
    }

    private void drawEditor(GuiGraphicsExtractor g) {
        updateVideo();
        label(g, font.plainSubstrByWidth(clip.title(), wide - 117), left + 70, top + 13, TEXT);
        g.fill(videoX, videoY, videoX + videoW, videoY + videoH, 0xff090a10);
        if (video != null) {
            NativeImage pixels = video.getPixels();
            double fit = Math.min((double) videoW / pixels.getWidth(), (double) videoH / pixels.getHeight());
            int w = (int) (pixels.getWidth() * fit), h = (int) (pixels.getHeight() * fit);
            int x = videoX + (videoW - w) / 2, y = videoY + (videoH - h) / 2;
            g.blit(VIDEO, x, y, x + w, y + h, 0, 1, 0, 1);
        } else {
            Identifier fallback = thumbnails.get(thumbnailKey(clip));
            if (fallback != null) g.blit(fallback, videoX, videoY, videoX + videoW, videoY + videoH, 0, 1, 0, 1);
        }
        if (!playing) {
            int x = videoX + videoW / 2, y = videoY + videoH / 2;
            g.fill(x - 11, y - 10, x + 11, y + 11, 0xff725bdd);
            center(g, "▶", x, y - 4, TEXT);
        }
        int progress = marker(position), begin = marker(from), end = marker(to);
        g.fill(videoX, trackY, videoX + videoW, trackY + 3, LINE);
        g.fill(begin, trackY, end, trackY + 3, 0xff55458d);
        g.fill(videoX, trackY, progress, trackY + 3, ACCENT);
        g.fill(begin, trackY - 2, begin + 2, trackY + 5, ACCENT);
        g.fill(end - 2, trackY - 2, end, trackY + 5, ACCENT);
        g.fill(progress - 2, trackY - 1, progress + 2, trackY + 4, TEXT);
        label(g, time(position) + " / " + time(clip.seconds()), videoX, trackY + 34, MUTED);
        if (zoomed) return;
        label(g, "О клипе", sidebar, videoY + 2, TEXT);
        label(g, clip.kind().equals("recording") ? "Запись · MP4" : "Откат · MP4", sidebar, videoY + 15, MUTED);
        label(g, time(clip.seconds()), sidebar, videoY + 28, MUTED);
        label(g, "Действия", sidebar, videoY + 39, TEXT);
        label(g, "Обрезка · I / O", sidebar, videoY + 114, TEXT);
        label(g, time(from) + " – " + time(to), sidebar, videoY + 127, MUTED);
    }

    private void updateVideo() {
        if (playback == null) return;
        ReplayPlayback.Frame frame = playback.frame();
        boolean done = playback.finished();
        if (done && frame == null) frame = playback.frame();
        if (frame != null) {
            NativeImage pixels = frame.pixels();
            if (video == null || video.getPixels().getWidth() != pixels.getWidth() || video.getPixels().getHeight() != pixels.getHeight()) {
                releaseVideo();
                video = new DynamicTexture(() -> "replay video", pixels);
                minecraft.getTextureManager().register(VIDEO, video);
            } else { video.setPixels(pixels); video.upload(); }
            if (drag == 0 && playing) position = Math.min(frame.time(), clip.seconds());
        }
        if (done) {
            if (playback.failed()) message("Не удалось прочитать кадры клипа");
            else if (playing && drag == 0) position = clip.seconds();
            stopPlayback();
        }
    }

    private int marker(double time) { return videoX + (int) Math.round(videoW * time / clip.seconds()); }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            double x = event.x(), y = event.y();
            if (clip != null && x >= videoX && x <= videoX + videoW) {
                if (y >= trackY - 6 && y <= trackY + 8) {
                    drag = Math.abs(x - marker(from)) < 5 ? 2 : Math.abs(x - marker(to)) < 5 ? 3 : 1;
                    if (drag == 1) position = point(x);
                    return true;
                }
                if (y >= videoY && y <= videoY + videoH) { togglePlayback(); return true; }
            }
            if (clip == null && settings < 0) {
                for (int i = 0; i < cards.size(); i++) {
                    int leftEdge = left + 12 + i % columns * (cardWidth + 8);
                    int topEdge = top + 75 + i / columns * (cardHeight + 26);
                    if (x >= leftEdge && x < leftEdge + cardWidth && y >= topEdge && y < topEdge + cardHeight) {
                        openClip(cards.get(i));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (drag == 0 || clip == null) return super.mouseDragged(event, dx, dy);
        double time = point(event.x());
        if (drag == 1) position = time;
        else setEdge(drag == 2, time);
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (drag == 0) return super.mouseReleased(event);
        int old = drag;
        drag = 0;
        if (old == 1 && clip != null) seek(position);
        return true;
    }

    private double point(double x) { return Math.clamp((x - videoX) / videoW, 0, 1) * clip.seconds(); }
    private void setEdge(boolean start, double point) {
        double minimum = Math.min(.1, clip.seconds());
        if (start) from = Math.clamp(point, 0, Math.max(0, to - minimum));
        else to = Math.clamp(point, Math.min(clip.seconds(), from + minimum), clip.seconds());
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (listening != null) {
            if (event.key() == InputConstants.KEY_ESCAPE) { listening = null; rebuild(); return true; }
            String code = GleamReplay.browserKey(event.key());
            if (code == null) return true;
            int modifiers = (event.hasControlDown() ? 1 : 0) | (event.hasAltDown() ? 2 : 0) |
                    (event.hasShiftDown() ? 4 : 0) | ((event.modifiers() & InputConstants.MOD_SUPER) != 0 ? 8 : 0);
            String binding = modifiers + ":" + code;
            if (prefs.keys().entrySet().stream().anyMatch(entry -> !entry.getKey().equals(listening) && binding.equals(entry.getValue()))) {
                message("Это сочетание уже занято");
                return true;
            }
            var values = new JsonObject();
            prefs.keys().forEach((key, value) -> values.addProperty(key, key.equals(listening) ? binding : value));
            replay.submit("bindings", values);
            listening = null;
            rebuild();
            return true;
        }
        if (clip != null && !event.hasAltDown() && !event.hasControlDown()) {
            if (event.key() == InputConstants.KEY_I) { setEdge(true, position); return true; }
            if (event.key() == InputConstants.KEY_O) { setEdge(false, position); return true; }
        }
        if (event.key() == InputConstants.KEY_ESCAPE) {
            if (clip != null) backToLibrary();
            else if (settings >= 0) showSettings(-1);
            else onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override public void onClose() { GleamMinecraft.setScreen(minecraft, parent); }

    @Override public void removed() {
        stopPlayback();
        releaseVideo();
        thumbnailGeneration++;
        thumbnails.values().forEach(minecraft.getTextureManager()::release);
        thumbnails.clear();
        requested.clear();
        drag = 0;
        super.removed();
    }

    private void releaseVideo() {
        if (video == null) return;
        minecraft.getTextureManager().release(VIDEO);
        video = null;
    }

    private Button button(int x, int y, int w, String text, Runnable action, boolean selected) {
        return addRenderableWidget(new ReplayButton(x, y, Math.max(1, w), text, action, selected));
    }

    private final class ReplayButton extends Button {
        private final boolean selected;
        ReplayButton(int x, int y, int w, String text, Runnable action, boolean selected) {
            super(x, y, w, 18, Component.literal(text), ignored -> action.run(), DEFAULT_NARRATION);
            this.selected = selected;
        }
        //? if >=26.1 {
        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
        //?} else {
        /*@Override protected void renderContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
        *///?}
            int x = getX(), y = getY(), width = getWidth();
            g.fill(x, y, x + width, y + 18, isFocused() ? ACCENT : LINE);
            int fill = isHoveredOrFocused() ? 0xff39364e : selected ? 0xff493878 : 0xff22232e;
            g.fill(x + 1, y + 1, x + width - 1, y + 17, fill);
            center(g, font.plainSubstrByWidth(getMessage().getString(), Math.max(0, width - 6)), x + width / 2, y + 5, active ? TEXT : MUTED);
        }
    }

    private void center(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        label(g, text, x - font.width(text) / 2, y, color);
    }

    private void label(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        //? if >=26.1 {
        g.text(font, text, x, y, color);
        //?} else {
        /*g.drawString(font, text, x, y, color);
        *///?}
    }

    private static Identifier asset(String name) { return Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, name); }
    private static String thumbnailKey(GleamReplay.Clip item) { return item.id() + "@" + item.revision(); }
    private static String time(double value) {
        long seconds = Math.max(0, (long) value);
        String clock = TIMES[(int) (seconds % 3600)];
        return seconds < 3600 ? clock : seconds / 3600 + ":" + clock;
    }

    private static String option(String value) {
        return switch (value) {
            case "custom" -> "Своя";
            case "source" -> "Исходное";
            case "auto" -> "Авто";
            case "manual" -> "Вручную";
            case "performance" -> "Производительность";
            case "balanced" -> "Баланс";
            case "quality" -> "Качество";
            case "game" -> "Только Minecraft";
            case "system" -> "Вся система";
            case "none" -> "Без системного звука";
            case "true" -> "Вкл.";
            case "false" -> "Выкл.";
            default -> value;
        };
    }

    private static String keyLabel(String key) {
        String[] parts = key.split(":", 2);
        int mask = Integer.parseInt(parts[0]);
        return ((mask & 1) != 0 ? "Ctrl + " : "") + ((mask & 2) != 0 ? "Alt + " : "") +
                ((mask & 4) != 0 ? "Shift + " : "") + ((mask & 8) != 0 ? "Win + " : "") +
                parts[1].replaceFirst("^(Key|Digit)", "");
    }
}
