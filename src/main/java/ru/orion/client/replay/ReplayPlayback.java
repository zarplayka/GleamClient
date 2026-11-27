package ru.orion.client.replay;

import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/** The player and all Media Foundation calls live on the same worker. */
final class ReplayPlayback implements AutoCloseable {
    private static final int FRAME = 1, PLAYING = 2, ENDED = 4, SEEKING = 8;
    static final int MAX_WIDTH = 3840, MAX_HEIGHT = 2160;
    private static final long OPEN_TIMEOUT = 10_000_000_000L;

    record Frame(NativeImage pixels, int seek) implements AutoCloseable {
        @Override public void close() { pixels.close(); }
    }

    private record Controls(boolean playing, double target, int seek, double rate, double volume) {}

    record Status(double position, boolean playing, boolean ended, boolean seeking) {}

    private record Applied(Controls controls, Status status) {}

    private final AtomicReference<Frame> ready = new AtomicReference<>();
    private final Thread worker;
    private volatile Controls controls;
    private volatile Applied applied;
    private volatile boolean closed, failed;
    private boolean producedFrame;

    ReplayPlayback(GleamReplay replay, Path file, double rate, double volume, int width, int height) {
        controls = new Controls(false, 0, 0, rate, volume);
        worker = Thread.ofPlatform().daemon().name("gleam-replay-player")
                .start(() -> open(replay, file, width, height));
    }

    void play() {
        var current = controls;
        update(new Controls(true, current.target(), current.seek(), current.rate(), current.volume()));
    }

    void pause() {
        var current = controls;
        update(new Controls(false, current.target(), current.seek(), current.rate(), current.volume()));
    }

    void seek(double target, boolean playing) {
        var current = controls;
        update(new Controls(playing, target, current.seek() + 1, current.rate(), current.volume()));
    }

    void rate(double rate) {
        var current = controls;
        update(new Controls(current.playing(), current.target(), current.seek(), rate, current.volume()));
    }

    void volume(double volume) {
        var current = controls;
        update(new Controls(current.playing(), current.target(), current.seek(), current.rate(), volume));
    }

    private void update(Controls next) {
        if (next.equals(controls)) return;
        controls = next;
        LockSupport.unpark(worker);
    }

    Frame frame() {
        var frame = ready.getAndSet(null);
        if (frame == null || frame.seek() == controls.seek()) return frame;
        frame.close();
        return null;
    }

    Status status() {
        var current = applied;
        return current != null && current.controls().equals(controls) ? current.status() : null;
    }

    boolean failed() { return failed; }

    private void open(GleamReplay replay, Path file, int width, int height) {
        try {
            replay.loadCaptureLibrary();
            if (closed) return;
            try {
                pump(file, width, height, true);
            } catch (IOException hardware) {
                if (closed || producedFrame) throw hardware;
                try {
                    pump(file, width, height, false);
                } catch (IOException software) {
                    software.addSuppressed(hardware);
                    throw software;
                }
            }
        } catch (IOException | RuntimeException | LinkageError error) {
            if (!closed) {
                LoggerFactory.getLogger("GleamReplay").warn("Replay player stopped", error);
                failed = true;
            }
        }
    }

    private void pump(Path file, int widthLimit, int heightLimit, boolean hardware) throws IOException {
        long player = openPlayer(file.toAbsolutePath().toString(), widthLimit, heightLimit, hardware);
        try {
            long until = System.nanoTime() + OPEN_TIMEOUT;
            long dimensions = 0;
            while (!closed && (dimensions = playerSize(player)) == 0) {
                if (System.nanoTime() >= until) throw new IOException("Replay video tracks did not load");
                LockSupport.parkNanos(5_000_000);
            }
            if (closed) return;
            int width = (int) (dimensions >>> 32), height = (int) dimensions;
            int bytes = Math.multiplyExact(Math.multiplyExact(width, height), 4);
            var active = new Controls(false, 0, 0, 1, 1);
            while (!closed) {
                var wanted = controls;
                if (!active.equals(wanted)) {
                    double seek = wanted.seek() != active.seek() ? wanted.target() : -1;
                    controlPlayer(player, wanted.playing(), seek, wanted.rate(), wanted.volume());
                    active = wanted;
                }
                long state = pollPlayer(player);
                if ((state & FRAME) != 0) {
                    var image = new NativeImage(width, height, false);
                    try {
                        copyPlayerFrame(player, MemoryUtil.memByteBuffer(image.getPointer(), bytes));
                        if ((state & SEEKING) == 0) {
                            publish(new Frame(image, active.seek()));
                            producedFrame = true;
                            image = null;
                        }
                    } finally {
                        if (image != null) image.close();
                    }
                }
                if (!producedFrame && System.nanoTime() >= until)
                    throw new IOException("Replay video did not produce a frame");
                var previous = applied;
                var position = (state >>> 4) / 1000.0;
                boolean playing = (state & PLAYING) != 0;
                boolean ended = (state & ENDED) != 0;
                boolean seeking = (state & SEEKING) != 0;
                if (previous == null || previous.status().position() != position || previous.status().playing() != playing
                        || previous.status().ended() != ended || previous.status().seeking() != seeking || previous.controls() != active)
                    applied = new Applied(active, new Status(position, playing, ended, seeking));
                LockSupport.parkNanos(playing || seeking || !producedFrame ? 3_000_000 : 25_000_000);
            }
        } finally {
            closePlayer(player);
        }
    }

    private void publish(Frame frame) {
        var previous = ready.getAndSet(frame);
        if (previous != null) previous.close();
        if (closed) discard();
    }

    private void discard() {
        var frame = ready.getAndSet(null);
        if (frame != null) frame.close();
    }

    @Override public void close() {
        closed = true;
        LockSupport.unpark(worker);
        discard();
    }

    private static native long openPlayer(String file, int width, int height, boolean hardware) throws IOException;
    private static native long playerSize(long player) throws IOException;
    private static native void controlPlayer(long player, boolean play, double seek, double rate, double volume) throws IOException;
    private static native long pollPlayer(long player) throws IOException;
    private static native void copyPlayerFrame(long player, ByteBuffer pixels) throws IOException;
    private static native void closePlayer(long player);
}
