package ru.orion.client.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.effect.OrionBadge;
import ru.orion.client.effect.OrionBadgePresence;
import ru.orion.client.effect.OrionFakePrefix;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;

import java.util.Comparator;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void orion$badgeLocalPlayer(PlayerInfo info, CallbackInfoReturnable<Component> callback) {
        Component name = callback.getReturnValue();
        var local = Minecraft.getInstance().player;
        if (local != null && local.getUUID().equals(info.getProfile().id())) {
            var prefix = OrionFakePrefix.configuredPrefixComponent();
            if (!prefix.getString().isBlank() && name.getString().equalsIgnoreCase(info.getProfile().name())) {
                var formatted = prefix.copy();
                if (!Character.isWhitespace(prefix.getString().codePointBefore(prefix.getString().length()))) formatted.append(" ");
                name = formatted.append(name);
            }
        }
        if (OrionBadgePresence.isOrion(info.getProfile().id())) {
            name = OrionBadge.prepend(name);
        }
        if (name != callback.getReturnValue()) callback.setReturnValue(name);
    }

    @ModifyArg(method = "getPlayerInfos", at = @At(value = "INVOKE",
            target = "Ljava/util/stream/Stream;sorted(Ljava/util/Comparator;)Ljava/util/stream/Stream;"))
    private Comparator<PlayerInfo> gleam$sortFakeRank(Comparator<PlayerInfo> vanilla) {
        return OrionFakePrefix.tabComparator(vanilla);
    }
}
