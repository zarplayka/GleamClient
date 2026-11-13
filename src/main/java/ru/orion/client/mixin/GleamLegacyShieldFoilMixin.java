//? if <26.1 {
/*package ru.orion.client.mixin;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.orion.client.OrionClient;
@Mixin(ShieldSpecialRenderer.class)
public abstract class GleamLegacyShieldFoilMixin {
    @ModifyVariable(method="submit(Lnet/minecraft/core/component/DataComponentMap;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",at=@At("HEAD"),argsOnly=true)
    private boolean gleam$foil(boolean original) {
        var config = OrionClient.getConfigOrNull();
        return original && !(config != null && config.isEnchantGlintEnabled() && config.getEnchantGlintMode() == 1);
    }
}
*///?}
