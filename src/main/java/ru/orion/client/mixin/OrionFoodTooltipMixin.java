package ru.orion.client.mixin;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.hud.OrionFoodTooltip;

@Mixin(ClientTooltipComponent.class)
public interface OrionFoodTooltipMixin {
    @Inject(method = "create(Lnet/minecraft/util/FormattedCharSequence;)Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;",
            at = @At("HEAD"), cancellable = true)
    private static void orion$foodTooltip(FormattedCharSequence line, CallbackInfoReturnable<ClientTooltipComponent> callback) {
        if (line instanceof OrionFoodTooltip tooltip) callback.setReturnValue(tooltip);
    }
}
