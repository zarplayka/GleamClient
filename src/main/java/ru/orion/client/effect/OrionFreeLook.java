package ru.orion.client.effect;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.OrionClient;
import ru.orion.client.keybind.OrionKeybinds;

public final class OrionFreeLook {
    private static CameraType previousCamera;
    private static boolean wasDown;
    private static boolean blockedUntilRelease;
    private static float yaw;
    private static float pitch;

    private OrionFreeLook() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(OrionFreeLook::update);
    }

    private static void update(Minecraft client) {
        var config = OrionClient.getConfigOrNull();
        var key = OrionKeybinds.getFreeLookKey();
        boolean down = key != null && key.isDown();
        boolean pressed = down && !wasDown;
        wasDown = down;
        if (!down) blockedUntilRelease = false;

        var player = client.player;
        if (config == null || !config.isFreeLookEnabled() || player == null || !player.isAlive()
                || client.getCameraEntity() != player || !client.isWindowActive()
                || GleamMinecraft.screen(client) != null || GleamMinecraft.overlay(client) != null) {
            stop(client);
            blockedUntilRelease = down;
            return;
        }
        if (previousCamera != null) {
            if (client.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
                previousCamera = null;
                blockedUntilRelease = down;
            } else if (config.isFreeLookToggle() ? pressed : !down) {
                stop(client);
            }
        } else if (!blockedUntilRelease && (config.isFreeLookToggle() ? pressed : down)) {
            previousCamera = client.options.getCameraType();
            yaw = player.getYRot();
            pitch = player.getXRot();
            if (previousCamera.isMirrored()) {
                yaw += 180F;
                pitch = -pitch;
            }
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    private static void stop(Minecraft client) {
        if (previousCamera == null) return;
        if (client.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
            client.options.setCameraType(previousCamera);
        }
        previousCamera = null;
    }

    public static boolean turn(Minecraft client, double dx, double dy) {
        update(client);
        if (previousCamera == null) return false;
        yaw = Mth.wrapDegrees(yaw + (float) (dx * 0.15D));
        pitch = Mth.clamp(pitch + (float) (dy * 0.15D), -90F, 90F);
        return true;
    }

    public static boolean isActive(Entity entity) {
        return previousCamera != null && entity != null && entity == Minecraft.getInstance().player;
    }

    public static float yaw() { return yaw; }
    public static float pitch() { return pitch; }
}
