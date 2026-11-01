package ru.orion.client.mixin;

import net.minecraft.client.renderer.rendertype.OrionGlintRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.orion.client.OrionClient;

@Mixin(ShieldSpecialRenderer.class)
public abstract class EnchantGlintShieldMixin {
    @Redirect(method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V", at = @At(value="INVOKE",target="Lnet/minecraft/client/renderer/rendertype/RenderTypes;entitySolidGlint(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType orion$shieldType(Identifier texture) {
        var config=OrionClient.getConfigOrNull();
        return config!=null && config.isEnchantGlintEnabled() && config.getEnchantGlintMode()!=1 ? OrionGlintRenderTypes.entity(texture) : RenderTypes.entitySolidGlint(texture);
    }
    @Redirect(method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V", at = @At(value="INVOKE",target="Lnet/minecraft/client/renderer/rendertype/RenderTypes;patternedShieldGlint()Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType orion$patternType() {
        var config=OrionClient.getConfigOrNull();
        return config!=null && config.isEnchantGlintEnabled() && config.getEnchantGlintMode()!=1 ? OrionGlintRenderTypes.shield() : RenderTypes.patternedShieldGlint();
    }
}
