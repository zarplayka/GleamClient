package ru.orion.client.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.orion.client.OrionClient;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public abstract class EnchantGlintLayerMixin {
    @ModifyVariable(method = "setFoilType(Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V", at = @At("HEAD"), argsOnly = true)
    private ItemStackRenderState.FoilType orion$disableGlint(ItemStackRenderState.FoilType original) {
        var config = OrionClient.getConfigOrNull();
        return config != null && config.isEnchantGlintEnabled() && config.getEnchantGlintMode() == 1
                ? ItemStackRenderState.FoilType.NONE : original;
    }
}
