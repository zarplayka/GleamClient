package ru.orion.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.gui.GleamAresCommandWheel;

@Mixin(KeyboardInput.class)
abstract class GleamAresMovementMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
    private boolean gleam$readWheelMovement(KeyMapping mapping, Operation<Boolean> original) {
        Minecraft client = Minecraft.getInstance();
        if (!(GleamMinecraft.screen(client) instanceof GleamAresCommandWheel)) return original.call(mapping);
        if (!client.isWindowActive() || GleamMinecraft.overlay(client) != null || mapping.isUnbound()) return false;

        var key = KeyMappingHelper.getBoundKeyOf(mapping);
        int code = key.getValue();
        if (key.getType() == InputConstants.Type.MOUSE) {
            //? if >=26.3 {
            return code > 0 && code <= Integer.SIZE
                    && (org.lwjgl.sdl.SDLMouse.SDL_GetMouseState(null, null) & (1 << (code - 1))) != 0;
            //?} else {
            /*return code >= 0 && code <= org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LAST
                    && org.lwjgl.glfw.GLFW.glfwGetMouseButton(client.getWindow().handle(), code) == InputConstants.PRESS;
            *///?}
        }
        //? if >=26.3 {
        return InputConstants.isKeyDown(code);
        //?} else {
        /*return key.getType() == InputConstants.Type.KEYSYM
                ? InputConstants.isKeyDown(client.getWindow(), code) : original.call(mapping);
        *///?}
    }
}
