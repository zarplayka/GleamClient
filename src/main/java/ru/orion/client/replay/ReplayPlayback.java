package ru.orion.client.replay;

import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.windows.Kernel32;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/** One bounded texture slot. Decoding, audio, scheduling and native memory writes stay off the render thread. */
final class ReplayPlayback implements AutoCloseable {
    private static final int MAGIC = 0x47525031;
    private static final int FILE_MAP_READ = 4;
    static final int MAX_WIDTH = 3840, MAX_HEIGHT = 2160;
    private static final int DISPLAY_FPS = 60;
    record Frame(NativeImage pixels, double time) implements AutoCloseable {
        @Override public void close() { pixels.close(); }
    }

    private final AtomicReference<Frame> ready = new AtomicReference<>();
    private final Thread thread;
    private volatile Process process;
    private volatile boolean closed, finished, failed;
    private volatile int gain;

    ReplayPlayback(GleamReplay replay, Path file, double position, boolean still, double rate, double volume,
                   int width, int height) {
        volume(volume);
        thread = Thread.ofPlatform().daemon().name("gleam-replay-player")
                .start(() -> decode(replay, file, position, still, rate, width, height));
    }

    void volume(double volume) { gain = (int) Math.round(volume * 256); }
    Frame frame() { return ready.get() == null ? null : ready.getAndSet(null); }
    boolean finished() { return finished; }
    boolean failed() { return failed; }

    private void decode(GleamReplay replay, Path file, double from, boolean still, double rate, int maxWidth, int maxHeight) {
        long pixels = 0;
        try {
            Path helper = replay.binary("gleam-replay.exe");
            if (closed) return;
            String token = UUID.randomUUID().toString();
            process = new ProcessBuilder(helper.toString(), "preview", file.toString(),
                    Long.toString(Math.max(0, Math.round(from * 1000))), still ? "1" : "0",
                    Integer.toString(maxWidth), Integer.toString(maxHeight),
                    Integer.toString(Math.min(DISPLAY_FPS, (int) (DISPLAY_FPS / rate))), token)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (closed) return;
            try (var input = new DataInputStream(new BufferedInputStream(process.getInputStream(), 128 * 1024))) {
                if (integer(input) != MAGIC) throw new IOException("Invalid replay preview header");
                int width = integer(input), height = integer(input);
                boolean hasAudio = integer(input) != 0;
                if (width < 2 || width > maxWidth || height < 2 || height > maxHeight) throw new IOException("Invalid preview dimensions");
                int frameBytes = width * height * 4;
                pixels = mapPixels(token, frameBytes);
                var ack = process.getOutputStream();
                try (var sound = hasAudio && !still ? new PlaybackAudio(rate) : null) {
                    long origin = 0;
                    boolean videoFrame = false;
                    while (!closed) {
                        int kind;
                        try { kind = integer(input); } catch (EOFException end) { break; }
                        long time = Long.reverseBytes(input.readLong());
                        int size = integer(input);
                        double pts = time / 10_000_000.0;
                        long elapsed = (long) ((pts - from) / rate * 1e9);
                        if (origin == 0) origin = System.nanoTime() - elapsed;
                        long deadline = origin + elapsed;
                        if (kind == 1 && size == 0) {
                            var image = new NativeImage(width, height, false);
                            try {
                                MemoryUtil.memCopy(pixels, image.getPointer(), frameBytes);
                                ack.write(1);
                                ack.flush();
                                if (still || await(deadline)) {
                                    publish(new Frame(image, pts));
                                    image = null;
                                    videoFrame = true;
                                }
                            } finally {
                                if (image != null) image.close();
                            }
                        } else if (kind == 2 && size > 0 && size <= 64 * 1024 && size % 4 == 0) {
                            if (sound == null) input.skipNBytes(size);
                            else {
                                byte[] pcm = new byte[size];
                                input.readFully(pcm);
                                sound.enqueue(new AudioPacket(pcm, deadline - (long) (45_000_000 / rate)));
                            }
                        } else throw new IOException("Invalid replay preview packet");
                    }
                    if (!closed && !videoFrame) failed = true;
                    if (!closed && sound != null) sound.finish();
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
            if (pixels != 0) JNI.invokePI(pixels, Kernel32.getLibrary().getFunctionAddress("UnmapViewOfFile"));
            Process current = process;
            if (current != null && current.isAlive()) current.destroy();
            finished = true;
        }
    }

    private static long mapPixels(String token, int bytes) throws IOException {
        var kernel = Kernel32.getLibrary();
        try (var stack = MemoryStack.stackPush()) {
            long name = MemoryUtil.memAddress(stack.UTF16("Local\\GleamReplayPreview-" + token));
            long mapping = JNI.invokePP(FILE_MAP_READ, 0, name, kernel.getFunctionAddress("OpenFileMappingW"));
            if (mapping == 0) throw new IOException("Cannot open replay frame mapping");
            try {
                long view = JNI.invokePPP(mapping, FILE_MAP_READ, 0, 0, (long) bytes, kernel.getFunctionAddress("MapViewOfFile"));
                if (view == 0) throw new IOException("Cannot map replay frames");
                return view;
            } finally {
                JNI.invokePI(mapping, kernel.getFunctionAddress("CloseHandle"));
            }
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

    private record AudioPacket(byte[] samples, long deadline) { }

    private final class PlaybackAudio implements AutoCloseable {
        private static final AudioPacket END = new AudioPacket(null, 0);
        private final ArrayBlockingQueue<AudioPacket> pending = new ArrayBlockingQueue<>(12);
        private final Thread worker;
        private volatile SourceDataLine line;
        private volatile boolean stopped;

        PlaybackAudio(double rate) {
            worker = Thread.ofPlatform().daemon().name("gleam-replay-audio").start(() -> play(rate));
        }

        void enqueue(AudioPacket packet) {
            // A stalled output device may drop old PCM, but never holds up the next video frame.
            if (!pending.offer(packet)) {
                pending.poll();
                pending.offer(packet);
            }
        }

        private void play(double rate) {
            try (SourceDataLine output = audio(rate)) {
                if (output == null) return;
                line = output;
                while (!stopped && !closed) {
                    var packet = pending.take();
                    if (packet == END) {
                        output.drain();
                        return;
                    }
                    if (!await(packet.deadline())) return;
                    byte[] pcm = packet.samples();
                    int volume = gain;
                    if (volume == 0) Arrays.fill(pcm, (byte) 0);
                    else if (volume < 256) for (int i = 0; i < pcm.length; i += 2) {
                        int sample = ((pcm[i] & 255) | pcm[i + 1] << 8) * volume / 256;
                        pcm[i] = (byte) sample;
                        pcm[i + 1] = (byte) (sample >> 8);
                    }
                    output.write(pcm, 0, pcm.length);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException error) {
                if (!stopped && !closed) LoggerFactory.getLogger("GleamReplay").debug("Replay audio stopped", error);
            } finally {
                line = null;
            }
        }

        void finish() throws InterruptedException {
            enqueue(END);
            worker.join(200);
        }

        @Override public void close() {
            stopped = true;
            worker.interrupt();
            SourceDataLine output = line;
            if (output != null) output.close();
        }
    }

    private boolean await(long deadline) {
        Thread current = Thread.currentThread();
        for (long remaining; !closed && !current.isInterrupted() && (remaining = deadline - System.nanoTime()) > 0;)
            LockSupport.parkNanos(Math.min(remaining, 20_000_000));
        return !closed && !current.isInterrupted();
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
