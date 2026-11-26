package ru.orion.client.replay;

import java.io.IOException;

final class ReplayFrameCapture {
    private ReplayFrameCapture() {}

    static native void open(long window, int height, int fps, String channel) throws IOException;
    static native boolean frame(int width, int height) throws IOException;
    static native void stop();
}
