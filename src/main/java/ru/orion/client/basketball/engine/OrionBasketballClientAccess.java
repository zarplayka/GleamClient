package ru.orion.client.basketball.engine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Общие обращения к клиенту через API целевой версии. */
public final class OrionBasketballClientAccess {
    private OrionBasketballClientAccess() { }
    public static Screen screen(Minecraft client) { return client.gui.screen(); }
    public static void message(LocalPlayer player, Component message) { Minecraft.getInstance().gui.hud.setOverlayMessage(message,false); }
    public static int light(ClientLevel level, BlockPos position) {
        return level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,position)<<4
                | level.getBrightness(net.minecraft.world.level.LightLayer.SKY,position)<<20;
    }
}
