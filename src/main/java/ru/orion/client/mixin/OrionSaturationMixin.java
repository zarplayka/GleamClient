package ru.orion.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.hud.OrionSaturationOverlay;

@Mixin(Hud.class)
public abstract class OrionSaturationMixin {
    @Unique private final OrionSaturationOverlay orion$saturation = new OrionSaturationOverlay();
    @Inject(method = "tick(Z)V", at = @At("HEAD"))
    private void orion$releaseSaturation(boolean paused, CallbackInfo callback) {
        orion$saturation.tick();
    }
    @Inject(method = "extractFood(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;II)V", at = @At("TAIL"))
    private void orion$drawSaturation(GuiGraphicsExtractor context, Player player, int y, int right, CallbackInfo callback) {
        orion$saturation.render(context,player,y,right);
    }
}
