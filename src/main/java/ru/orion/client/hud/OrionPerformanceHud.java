package ru.orion.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import com.sun.management.OperatingSystemMXBean;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Reuses the frame duration Minecraft already calculates. OS sampling and rasterization run off the game thread. */
public final class OrionPerformanceHud {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "performance_panel");
    private static final long SECOND = 1_000_000_000, BIN_NS = 250_000;
    private static final int WIDTH = 184, HEIGHT = 121, DENSITY = 2;
    private static final double MAX_PERCENTILE_MS = 512 * BIN_NS / 1e6;
    private static final Color SURFACE = new Color(17, 18, 24, 232);
    private static final Color LABEL = new Color(174, 181, 196);
    private static final Color TEXT = new Color(241, 243, 250);
    private static final Color ACCENT = new Color(193, 204, 255);
    private static Session active;
    private static DynamicTexture texture;

    private record Frames(double fps, double average, double p95, double peak) {
        static final Frames EMPTY = new Frames(-1, -1, -1, -1);
    }

    private OrionPerformanceHud() { }

    public static void register() {
        HudElementRegistry.addLast(TEXTURE, (graphics, delta) -> draw(graphics));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> disable(client)));
        ClientLifecycleEvents.CLIENT_STOPPING.register(OrionPerformanceHud::disable);
    }

    public static void toggle(Minecraft client) {
        if (active != null) {
            disable(client);
        } else if (client.level != null) {
            var session = new Session();
            active = session;
            session.worker.start();
        }
    }

    public static long frame(long duration) {
        Session session = active;
        if (session != null) session.record(duration);
        return duration;
    }

    private static void disable(Minecraft client) {
        Session session = active;
        active = null;
        if (session != null) session.close();
        if (texture != null) {
            client.getTextureManager().release(TEXTURE);
            texture = null;
        }
    }

    private static void draw(GuiGraphicsExtractor graphics) {
        Session session = active;
        if (session == null) return;
        var client = Minecraft.getInstance();
        if (client.level == null || client.getDebugOverlay().showDebugScreen()) return;
        NativeImage pixels = session.pending.get() == null ? null : session.pending.getAndSet(null);
        if (pixels != null) {
            if (texture == null) {
                texture = new DynamicTexture(() -> "gleam_performance", pixels);
                client.getTextureManager().register(TEXTURE, texture);
            } else {
                texture.setPixels(pixels);
                texture.upload();
            }
        }
        if (texture == null) return;
        int width = Math.min(WIDTH, Math.min(graphics.guiWidth() - 12, (graphics.guiHeight() - 12) * WIDTH / HEIGHT));
        int height = width * HEIGHT / WIDTH;
        if (width > 0 && height > 0) graphics.blit(TEXTURE, 6, 6, width + 6, height + 6, 0, 1, 0, 1);
    }

    private static final class Session {
        private final int[] histogram = new int[512];
        private final AtomicReference<NativeImage> pending = new AtomicReference<>();
        private final Thread worker = Thread.ofPlatform().daemon().name("gleam-performance")
                .priority(Thread.MIN_PRIORITY).unstarted(this::sample);
        private volatile Frames frames = Frames.EMPTY;
        private volatile long renderThreadId;
        private volatile boolean closed;
        private int frameCount;
        private long elapsed, peak;

        void record(long duration) {
            if (duration <= 0) return;
            if (renderThreadId == 0) renderThreadId = Thread.currentThread().threadId();
            histogram[(int) Math.min(histogram.length - 1, duration / BIN_NS)]++;
            frameCount++;
            elapsed += duration;
            peak = Math.max(peak, duration);
            if (elapsed < SECOND) return;

            int target = (int) ((95L * frameCount + 99) / 100);
            int counted = 0, bucket = 0;
            while (bucket < histogram.length - 1 && (counted += histogram[bucket]) < target) bucket++;
            double percentile = bucket == histogram.length - 1 ? Math.min(MAX_PERCENTILE_MS, peak / 1e6)
                    : Math.min(peak, (bucket + .5) * BIN_NS) / 1e6;
            frames = new Frames(frameCount * (double) SECOND / elapsed, elapsed / (1e6 * frameCount), percentile, peak / 1e6);
            Arrays.fill(histogram, 0);
            frameCount = 0;
            elapsed = peak = 0;
        }

        void close() {
            closed = true;
            worker.interrupt();
            discardPending();
        }

        private void discardPending() {
            NativeImage previous = pending.getAndSet(null);
            if (previous != null) previous.close();
        }

        private void sample() {
            var operatingSystem = ManagementFactory.getOperatingSystemMXBean();
            var os = operatingSystem instanceof OperatingSystemMXBean extended ? extended : null;
            var threads = ManagementFactory.getThreadMXBean();
            boolean threadTime = threads.isThreadCpuTimeSupported() && threads.isThreadCpuTimeEnabled();
            var runtime = Runtime.getRuntime();
            var collectors = ManagementFactory.getGarbageCollectorMXBeans();
            long lastCpu = -1, lastAt = 0, lastGcTime = -1, lastGcCount = -1;
            long nextGpu = 0;
            var gpu = OrionGpuCounters.Load.UNAVAILABLE;
            var image = new BufferedImage(WIDTH * DENSITY, HEIGHT * DENSITY, BufferedImage.TYPE_INT_ARGB);
            int[] scratch = new int[image.getWidth() * image.getHeight()];

            try (OrionGpuCounters counters = OrionGpuCounters.open()) {
                while (!closed) {
                    long now = System.nanoTime();
                    long cpu = threadTime && renderThreadId != 0 ? threads.getThreadCpuTime(renderThreadId) : -1;
                    double render = cpu >= 0 && lastCpu >= 0 && now > lastAt ? (cpu - lastCpu) * 100.0 / (now - lastAt) : -1;
                    long gcTime = 0, gcCount = 0;
                    for (var collector : collectors) {
                        gcTime += Math.max(0, collector.getCollectionTime());
                        gcCount += Math.max(0, collector.getCollectionCount());
                    }
                    if (counters != null && (nextGpu == 0 || now - nextGpu >= 0)) {
                        gpu = counters.read();
                        nextGpu = now + 2 * SECOND;
                    }
                    paint(image, scratch, frames, os == null ? -1 : os.getProcessCpuLoad() * 100,
                            os == null ? -1 : os.getCpuLoad() * 100, render, gpu,
                            runtime.totalMemory() - runtime.freeMemory(), runtime.maxMemory(),
                            os == null ? -1 : os.getTotalMemorySize(), os == null ? -1 : os.getFreeMemorySize(),
                            lastGcTime < 0 ? -1 : gcTime - lastGcTime, lastGcCount < 0 ? -1 : gcCount - lastGcCount);
                    lastAt = now;
                    lastCpu = cpu;
                    lastGcTime = gcTime;
                    lastGcCount = gcCount;
                    TimeUnit.SECONDS.sleep(1);
                }
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            }
        }

        private void paint(BufferedImage image, int[] scratch, Frames f, double gameCpu, double systemCpu, double render, OrionGpuCounters.Load gpu,
                           long heap, long maxHeap, long ram, long freeRam, long gcTime, long gcCount) {
            var canvas = image.createGraphics();
            try {
                canvas.scale(DENSITY, DENSITY);
                canvas.setComposite(AlphaComposite.Clear);
                canvas.fillRect(0, 0, WIDTH, HEIGHT);
                canvas.setComposite(AlphaComposite.SrcOver);
                canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                canvas.setColor(SURFACE);
                canvas.fillRoundRect(0, 0, WIDTH, HEIGHT, 9, 9);
                canvas.setFont(new Font(Font.DIALOG, Font.BOLD, 11));
                canvas.setColor(ACCENT);
                canvas.drawString(number(f.fps(), 0) + " FPS", 8, 15);
                String frame = number(f.average(), 2) + " мс / кадр";
                canvas.setColor(TEXT);
                canvas.drawString(frame, WIDTH - 8 - canvas.getFontMetrics().stringWidth(frame), 15);
                canvas.setColor(new Color(255, 255, 255, 28));
                canvas.drawLine(8, 21, WIDTH - 8, 21);
                canvas.setFont(new Font(Font.DIALOG, Font.PLAIN, 9));
                String p95 = (f.p95() >= MAX_PERCENTILE_MS ? "≥" : "") + number(f.p95(), 2);
                row(canvas, 33, "Кадр p95 / пик", p95 + " / " + number(f.peak(), 2) + " мс");
                row(canvas, 44, "CPU игра / ПК", percentage(gameCpu) + " / " + percentage(systemCpu));
                row(canvas, 55, "Поток рендера", percentage(render) + " ядра");
                row(canvas, 66, "GPU игра / ПК", percentage(gpu.game()) + " / " + percentage(gpu.system()));
                row(canvas, 77, "GPU кодирование", percentage(gpu.encode()));
                row(canvas, 88, "Память Java", gib(heap) + " / " + gib(maxHeap) + " ГиБ");
                row(canvas, 99, "ОЗУ ПК", ram > 0 && freeRam >= 0 ? gib(Math.max(0, ram - freeRam)) + " / " + gib(ram) + " ГиБ" : "—");
                row(canvas, 110, "GC за интервал", gcTime < 0 ? "—" : gcTime + " мс / " + gcCount);
            } finally { canvas.dispose(); }

            if (closed) return;
            var pixels = new NativeImage(image.getWidth(), image.getHeight(), false);
            OrionHudRenderer.copyPixels(image, pixels, scratch);
            NativeImage previous = pending.getAndSet(pixels);
            if (previous != null) previous.close();
            if (closed) discardPending();
        }

        private static void row(java.awt.Graphics2D canvas, int y, String label, String value) {
            canvas.setColor(LABEL);
            canvas.drawString(label, 8, y);
            canvas.setColor(TEXT);
            canvas.drawString(value, WIDTH - 8 - canvas.getFontMetrics().stringWidth(value), y);
        }

        private static String percentage(double value) { return value < 0 || !Double.isFinite(value) ? "—" : Math.round(Math.min(value, 100)) + "%"; }
        private static String gib(long value) { return number(value / (double) (1L << 30), 1); }
        private static String number(double value, int decimals) {
            if (!Double.isFinite(value) || value < 0) return "—";
            return switch (decimals) {
                case 0 -> Long.toString(Math.round(value));
                case 1 -> String.format(Locale.ROOT, "%.1f", value);
                default -> String.format(Locale.ROOT, "%.2f", value);
            };
        }
    }
}
