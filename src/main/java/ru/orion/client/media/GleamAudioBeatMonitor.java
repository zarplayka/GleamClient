package ru.orion.client.media;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Один долгоживущий WASAPI-процесс, 25 замеров/с. PCM не покидает помощник. */
public final class GleamAudioBeatMonitor implements AutoCloseable {
    @FunctionalInterface public interface Helper { Path path() throws Exception; }
    record Sample(float level, float bass, long onset, long at) { }
    private static final Sample SILENCE = new Sample(0, 0, 0, 0);
    private final Helper helper;
    private final ScheduledExecutorService reader = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("gleam-audio-beat").factory());
    private volatile Sample sample = new Sample(0, 0, 0, 0);
    private Process process;
    private boolean enabled, scheduled, closed;
    private long generation;

    public GleamAudioBeatMonitor(Helper helper) { this.helper = helper; }
    public synchronized void setEnabled(boolean value) {
        enabled = value && !closed;
        if (!enabled) { generation++; if (process != null) process.destroyForcibly(); sample = new Sample(0, 0, 0, 0); }
        else if (!scheduled) schedule(0);
    }
    private void schedule(long seconds) { scheduled = true; reader.schedule(this::capture, seconds, TimeUnit.SECONDS); }
    private void capture() {
        long token;
        synchronized (this) { if (!enabled || closed) { scheduled = false; return; } token = generation; }
        Process current = null;
        try {
            var path = helper.path();
            synchronized (this) {
                if (!enabled || closed || token != generation) return;
                process = current = new ProcessBuilder(path.toString(), "--beat", Long.toString(ProcessHandle.current().pid()))
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            }
            try (var lines = new BufferedReader(new InputStreamReader(current.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = lines.readLine()) != null) {
                    if (line.length() > 128) break;
                    String[] values = line.strip().split(" ");
                    if (values.length != 3) continue;
                    float level = Float.parseFloat(values[0]), bass = Float.parseFloat(values[1]);
                    long onset = Long.parseLong(values[2]);
                    if (!Float.isFinite(level + bass) || level < 0 || level > 1 || bass < 0 || bass > 1 || onset < 0) continue;
                    synchronized (this) {
                        if (!enabled || closed || token != generation) break;
                        sample = new Sample(level, bass, onset, System.nanoTime());
                    }
                }
            }
        } catch (Exception unavailable) { /* Никакого синтетического ритма при недоступном выходе. */ }
        finally {
            if (current != null) current.destroyForcibly();
            synchronized (this) {
                process = null; sample = new Sample(0, 0, 0, 0); scheduled = false;
                if (enabled && !closed) schedule(10);
            }
        }
    }
    Sample sample(long now) {
        Sample value = sample;
        return value.at() != 0 && now - value.at() < 500_000_000 ? value : SILENCE;
    }
    @Override public synchronized void close() {
        closed = true; setEnabled(false); reader.shutdownNow();
    }
}
