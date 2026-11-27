package ru.orion.client.replay;

import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/** One bounded texture slot. Decoding, audio, scheduling and native memory writes stay off the render thread. */
final class ReplayPlayback implements AutoCloseable {
    private static final int MAGIC = 0x47525031;
    private static final int MAX_WIDTH = 960, MAX_HEIGHT = 540, DISPLAY_FPS = 48;
    record Frame(NativeImage pixels, double time) implements AutoCloseable {
        @Override public void close() { pixels.close(); }
    }

    private final AtomicReference<Frame> ready = new AtomicReference<>();
    private final Thread thread;
    private volatile Process process;
    private volatile boolean closed, finished, failed;
    private volatile int gain;

    ReplayPlayback(GleamReplay replay, Path file, double position, boolean still, double rate, boolean muted) {
        this(replay, file, position, still, rate, muted ? 0 : 1.0);
    }

    ReplayPlayback(GleamReplay replay, Path file, double position, boolean still, double rate, double volume) {
        volume(volume);
        thread = Thread.ofPlatform().daemon().name("gleam-replay-player").start(() -> decode(replay, file, position, still, rate));
    }

    void volume(double volume) { gain = (int) Math.round(volume * 256); }
    Frame frame() { return ready.get() == null ? null : ready.getAndSet(null); }
    boolean finished() { return finished; }
    boolean failed() { return failed; }

    private void decode(GleamReplay replay, Path file, double from, boolean still, double rate) {
        try {
            Path helper = replay.binary("gleam-replay.exe");
            if (closed) return;
            process = new ProcessBuilder(helper.toString(), "preview", file.toString(),
                    Long.toString(Math.max(0, Math.round(from * 1000))), still ? "1" : "0",
                    Integer.toString(MAX_WIDTH), Integer.toString(Math.min(60, (int) (DISPLAY_FPS / rate))))
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (closed) return;
            try (var input = new DataInputStream(new BufferedInputStream(process.getInputStream(), 128 * 1024))) {
                if (integer(input) != MAGIC) throw new IOException("Invalid replay preview header");
                int width = integer(input), height = integer(input);
                boolean hasAudio = integer(input) != 0;
                if (width < 2 || width > MAX_WIDTH || height < 2 || height > MAX_HEIGHT) throw new IOException("Invalid preview dimensions");
                var frameData = new byte[width * height * 4];
                var audioData = new byte[64 * 1024];
                try (SourceDataLine output = hasAudio && !still ? audio(rate) : null) {
                    long origin = 0;
                    boolean videoFrame = false;
                    while (!closed) {
                        int kind;
                        try { kind = integer(input); } catch (EOFException end) { break; }
                        long time = Long.reverseBytes(input.readLong());
                        int size = integer(input);
                        double pts = time / 10_000_000.0;
                        if (kind == 1 && size == frameData.length) {
                            input.readFully(frameData);
                            if (still || await(origin, pts, from, rate)) {
                                var image = new NativeImage(width, height, false);
                                MemoryUtil.memByteBuffer(image.getPointer(), frameData.length).put(frameData);
                                publish(new Frame(image, pts));
                                videoFrame = true;
                            }
                        } else if (kind == 2 && size > 0 && size <= audioData.length && size % 4 == 0) {
                            input.readFully(audioData, 0, size);
                            if (output != null && await(origin, pts - .045, from, rate)) {
                                int volume = gain;
                                if (volume == 0) java.util.Arrays.fill(audioData, 0, size, (byte) 0);
                                else if (volume < 256) for (int i = 0; i < size; i += 2) {
                                    int sample = ((audioData[i] & 255) | audioData[i + 1] << 8) * volume / 256;
                                    audioData[i] = (byte) sample;
                                    audioData[i + 1] = (byte) (sample >> 8);
                                }
                                output.write(audioData, 0, size);
                            }
                        } else throw new IOException("Invalid replay preview packet");
                        if (origin == 0) origin = System.nanoTime() - (long) ((pts - from) / rate * 1e9);
                    }
                    if (!closed && !videoFrame) failed = true;
                }
            }
            if (!closed && process.waitFor() != 0) failed = true;
        } catch (IOException | RuntimeException error) {
            if (!closed) {
                failed = true;
                LoggerFactory.getLogger("GleamReplay").debug("Replay preview stopped", error);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            Process current = process;
            if (current != null && current.isAlive()) current.destroy();
            finished = true;
        }
    }

    private static SourceDataLine audio(double rate) {
        SourceDataLine line = null;
        try {
            var format = new AudioFormat((float) (48_000 * rate), 16, 2, true, false);
            line = AudioSystem.getSourceDataLine(format);
            line.open(format, Math.max(4096, Math.round(192_000 * (float) rate / 8)));
            line.start();
            return line;
        } catch (Exception unavailable) {
            if (line != null) line.close();
            return null;
        }
    }

    private boolean await(long origin, double pts, double from, double rate) {
        if (origin != 0) {
            long deadline = origin + (long) ((pts - from) / rate * 1e9);
            for (long remaining; !closed && (remaining = deadline - System.nanoTime()) > 0;)
                LockSupport.parkNanos(Math.min(remaining, 20_000_000));
        }
        return !closed;
    }

    private static int integer(DataInputStream input) throws IOException { return Integer.reverseBytes(input.readInt()); }

    private void publish(Frame frame) {
        Frame previous = ready.getAndSet(frame);
        if (previous != null) previous.close();
        if (closed) discard();
    }

    private void discard() {
        Frame frame = ready.getAndSet(null);
        if (frame != null) frame.close();
    }

    @Override public void close() {
        closed = true;
        thread.interrupt();
        Process current = process;
        if (current != null) current.destroy();
        discard();
    }
}
