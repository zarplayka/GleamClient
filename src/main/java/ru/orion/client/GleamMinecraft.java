package ru.orion.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
//? if >=26.1 {
import net.minecraft.client.renderer.state.GameRenderState;
//?} else {
/*
*///?}
import net.minecraft.network.chat.Component;

/** Minecraft accessors shared by the version targets. */
public final class GleamMinecraft {
    private GleamMinecraft() {}

    public static Screen screen(Minecraft client) {
        //? if >=26.2 {
        return client.gui.screen();
        //?} else {
        /*return client.screen;
        *///?}
    }

    public static void setScreen(Minecraft client, Screen screen) {
        //? if >=26.2 {
        client.gui.setScreen(screen);
        //?} else {
        /*client.setScreen(screen);
        *///?}
    }

    public static Overlay overlay(Minecraft client) {
        //? if >=26.2 {
        return client.gui.overlay();
        //?} else {
        /*return client.getOverlay();
        *///?}
    }

    public static ChatComponent chat(Minecraft client) {
        //? if >=26.2 {
        return client.gui.hud.getChat();
        //?} else {
        /*return client.gui.getChat();
        *///?}
    }

    public static int guiTicks(Minecraft client) {
        //? if >=26.2 {
        return client.gui.hud.getGuiTicks();
        //?} else {
        /*return client.gui.getGuiTicks();
        *///?}
    }

    public static void setOverlayMessage(Minecraft client, Component message, boolean animate) {
        //? if >=26.2 {
        client.gui.hud.setOverlayMessage(message, animate);
        //?} else {
        /*client.gui.setOverlayMessage(message, animate);
        *///?}
    }

    public static void extractDeferredSubtitles(Minecraft client) {
        //? if >=26.2 {
        client.gui.hud.extractDeferredSubtitles();
        //?} else {
        /*// В 1.21.11 субтитры входят в обычный GUI-проход.
        *///?}
    }

    public static Camera camera(Minecraft client) {
        //? if >=26.2 {
        return client.gameRenderer.mainCamera();
        //?} else {
        /*return client.gameRenderer.getMainCamera();
        *///?}
    }

    //? if >=26.1 {
    public static GameRenderState renderState(Minecraft client) {
        //? if >=26.2 {
        return client.gameRenderer.gameRenderState();
        //?} else {
        /*return client.gameRenderer.getGameRenderState();
        *///?}
    }

    //?}
    public static net.minecraft.client.renderer.state.level.LevelRenderState levelState(Minecraft client) {
        //? if >=26.1 {
        return renderState(client).levelRenderState;
        //?} else {
        /*return client.gameRenderer.getLevelRenderState();
        *///?}
    }
    public static boolean shouldRenderLevel(Minecraft client) {
        //? if >=26.3 {
        return renderState(client).shouldRenderLevel;
        //?} else {
        /*return client.level != null && client.player != null;
        *///?}
    }
    public static double glintSpeed(Minecraft client) {
        //? if >=26.1 {
        return renderState(client).optionsRenderState.glintSpeed;
        //?} else {
        /*return client.options.glintSpeed().get();
        *///?}
    }
    public static RenderTarget mainTarget(Minecraft client) {
        //? if >=26.2 {
        return client.gameRenderer.mainRenderTarget();
        //?} else {
        /*return client.getMainRenderTarget();
        *///?}
    }
    public static void addChatMessage(Minecraft client, Component message) {
        //? if >=26.1 {
        chat(client).addClientSystemMessage(message);
        //?} else {
        /*chat(client).addMessage(message);
        *///?}
    }
}
