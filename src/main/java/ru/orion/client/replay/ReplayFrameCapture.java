package ru.orion.client.replay;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
//? if >=26.3 {
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.textures.FilterMode;
//?} else {
/*import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.textures.FilterMode;
*///?}
import net.minecraft.client.renderer.RenderPipelines;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiConsumer;

final class ReplayFrameCapture {
    record Plan(long generation, int width, int height, int fps) {}

    private static final int BUFFER_COUNT = 3;
    private static final String LABEL = "Gleam replay capture";

    private final BiConsumer<Long, Throwable> onError;
    private Capture capture;

    ReplayFrameCapture(BiConsumer<Long, Throwable> onError) {
        this.onError = onError;
    }

    static native void open(int width, int height, long generation, String channel) throws IOException;
    static native void stop();
    private static native long timestamp();
    private static native void submit(long generation, ByteBuffer buffer, int width, int height, long time);

    void frame(Plan plan, RenderTarget source) {
        if (capture != null && !capture.plan.equals(plan)) {
            capture.retire();
            capture = null;
        }
        if (plan == null || source == null || source.width < 2 || source.height < 2) return;
        if (capture == null) capture = new Capture(plan);
        capture.frame(source);
    }

    private static final class Slot {
        GpuBuffer buffer;
        boolean busy;
    }

    private final class Capture {
        final Plan plan;
        final Slot[] slots = {new Slot(), new Slot(), new Slot()};
        final long interval;
        TextureTarget scaled;
        int inFlight;
        long nextFrame;
        boolean retired, failed;

        Capture(Plan plan) {
            this.plan = plan;
            interval = 1_000_000_000L / plan.fps();
        }

        void frame(RenderTarget source) {
            if (failed || inFlight == BUFFER_COUNT) return;
            final long now = System.nanoTime();
            if (nextFrame != 0 && now < nextFrame) return;

            final double factor = Math.min(1, Math.min((double) plan.width() / source.width,
                    (double) plan.height() / source.height));
            final int width = Math.max(2, (int) (source.width * factor) & ~1);
            final int height = Math.max(2, (int) (source.height * factor) & ~1);
            try {
                if (scaled == null || scaled.width != width || scaled.height != height) {
                    if (inFlight != 0) return;
                    if (scaled != null) {
                        scaled.destroyBuffers();
                        scaled = null;
                    }
                    //? if >=26.3 {
                    scaled = new TextureTarget(LABEL, width, height, GpuFormat.RGBA8_UNORM, null);
                    //?} else if >=26.2 {
                    /*scaled = new TextureTarget(LABEL, width, height, false, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM);
                    *///?} else {
                    /*scaled = new TextureTarget(LABEL, width, height, false);
                    *///?}
                }
                Slot free = null;
                for (var slot : slots) {
                    if (!slot.busy) { free = slot; break; }
                }
                if (free == null) return;
                schedule(source, free, width, height);
                nextFrame = Math.max(now + interval / 2, nextFrame == 0 ? now + interval : nextFrame + interval);
            } catch (RuntimeException | LinkageError error) {
                fail(error);
            }
        }

        private void schedule(RenderTarget source, Slot slot, int width, int height) {
            var device = RenderSystem.getDevice();
            if (slot.buffer == null) {
                slot.buffer = device.createBuffer(() -> LABEL, GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ,
                        plan.width() * (long) plan.height() * 4);
            }
            var encoder = device.createCommandEncoder();
            // The same fullscreen pass used by Minecraft's profiler performs the resize on the GPU.
            //? if >=26.2 {
            try (var pass = encoder.createRenderPass(() -> LABEL, scaled.getColorTextureView(), Optional.empty())) {
            //?} else {
            /*try (var pass = encoder.createRenderPass(() -> LABEL, scaled.getColorTextureView(), OptionalInt.empty())) {
            *///?}
                RenderSystem.bindDefaultUniforms(pass);
                var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
                //? if >=26.3 {
                pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.TRACY_BLIT));
                pass.setUniform("InSampler", source.getColorTextureView(), sampler);
                //?} else {
                /*pass.setPipeline(RenderPipelines.TRACY_BLIT);
                pass.bindTexture("InSampler", source.getColorTextureView(), sampler);
                *///?}
                //? if >=26.2 {
                pass.draw(3, 1, 0, 0);
                //?} else {
                /*pass.draw(0, 3);
                *///?}
            }

            long time = timestamp();
            slot.busy = true;
            inFlight++;
            try {
                encoder.copyTextureToBuffer(scaled.getColorTexture(), slot.buffer, 0, () -> {
                    try {
                        if (retired || failed) return;
                        //? if >=26.2 {
                        try (var mapped = slot.buffer.map(true, false)) {
                        //?} else {
                        /*try (var mapped = encoder.mapBuffer(slot.buffer, true, false)) {
                        *///?}
                            submit(plan.generation(), mapped.data(), width, height, time);
                        }
                    } catch (RuntimeException | LinkageError error) {
                        fail(error);
                    } finally {
                        complete(slot);
                    }
                }, 0);
            } catch (RuntimeException | LinkageError error) {
                if (slot.busy) complete(slot);
                throw error;
            }
        }

        private void fail(Throwable error) {
            if (failed) return;
            failed = true;
            onError.accept(plan.generation(), error);
        }

        private void complete(Slot slot) {
            slot.busy = false;
            inFlight--;
            if (retired) {
                closeBuffer(slot);
                if (inFlight == 0) closeTarget();
            }
        }

        void retire() {
            retired = true;
            for (var slot : slots) if (!slot.busy) closeBuffer(slot);
            if (inFlight == 0) closeTarget();
        }

        private void closeTarget() {
            if (scaled != null) scaled.destroyBuffers();
        }

        private void closeBuffer(Slot slot) {
            if (slot.buffer != null) slot.buffer.close();
        }
    }
}
