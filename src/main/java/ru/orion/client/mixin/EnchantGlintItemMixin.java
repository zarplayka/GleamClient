package ru.orion.client.mixin;

import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.OrionGlintRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.orion.client.OrionClient;

@Mixin(ItemFeatureRenderer.class)
public abstract class EnchantGlintItemMixin {
    @Redirect(method = "prepareMainSubmit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/geometry/BakedQuad$MaterialInfo;itemGlintRenderType()Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType orion$itemGlint(BakedQuad.MaterialInfo material) {
        var config = OrionClient.getConfigOrNull();
        return config != null && config.isEnchantGlintEnabled() && config.getEnchantGlintMode() != 1
                ? OrionGlintRenderTypes.item(material.sprite().atlasLocation(), material.layer().translucent(), false) : material.itemGlintRenderType();
    }
    @Redirect(method = "prepareMainSubmit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/geometry/BakedQuad$MaterialInfo;itemGlintSpecialRenderType()Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType orion$specialItemGlint(BakedQuad.MaterialInfo material) {
        var config = OrionClient.getConfigOrNull();
        return config != null && config.isEnchantGlintEnabled() && config.getEnchantGlintMode() != 1
                ? OrionGlintRenderTypes.item(material.sprite().atlasLocation(), material.layer().translucent(), true) : material.itemGlintSpecialRenderType();
    }
}
