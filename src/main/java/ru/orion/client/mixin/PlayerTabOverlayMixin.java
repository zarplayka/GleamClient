package ru.orion.client.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.effect.OrionBadge;
import ru.orion.client.effect.OrionBadgePresence;
import net.minecraft.network.chat.Component;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void orion$badgeLocalPlayer(PlayerInfo info, CallbackInfoReturnable<Component> callback) {
        if (OrionBadgePresence.isOrion(info.getProfile().id())) {
            callback.setReturnValue(OrionBadge.prepend(callback.getReturnValue()));
        }
    }

}
