package com.paradox.lumen;

import java.nio.ByteBuffer;

// Имя класса входит в JNI-символы локальной библиотеки Lumen.
public final class LumenNative {
    private LumenNative() {}

    public static native long create();
    public static native void destroy(long engine);
    public static native boolean setFontData(long engine, byte[] font);
    public static native void setPixelRatio(long engine, float ratio);
    public static native void setViewport(long engine, float width, float height);
    public static native int frame(long engine, float delta);
    public static native int displaySize(long engine);
    public static native int copyDisplay(long engine, ByteBuffer buffer);
    public static native int atlasRevision(long engine);
    public static native int copyAtlas(long engine, int index, ByteBuffer buffer);
    public static native int registerImage(byte[] name, byte[] png);
    public static native boolean registerFont(byte[] name, byte[] font);
    public static native long imageSize(int image);
    public static native int copyImage(int image, ByteBuffer buffer);
    public static native long pageOpen(long engine, byte[] html, byte[] base);
    public static native void pageClose(long page);
    public static native int pageTick(long page, long engine, float delta);
    public static native int pageAfterFrame(long page, long engine);
    public static native void pageCall(long page, long engine, byte[] name, byte[] json);
    public static native byte[] pageMessages(long page);
    public static native byte[] pageLogs(long page);
    public static native void pageMove(long page, long engine, float x, float y);
    public static native boolean pageDown(long page, long engine, int button, float x, float y);
    public static native boolean pageUp(long page, long engine, int button, float x, float y);
    public static native boolean pageWheel(long page, long engine, float x, float y, float horizontal, float vertical);
    public static native boolean pageKey(long page, long engine, byte[] key, byte[] code, int modifiers, boolean pressed);
    public static native boolean pageText(long page, long engine, byte[] text);
}
