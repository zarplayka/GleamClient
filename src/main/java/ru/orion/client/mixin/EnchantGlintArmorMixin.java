package ru.orion.client.mixin;

import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.OrionGlintRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.orion.client.OrionClient;

@Mixin(EquipmentLayerRenderer.class)
public abstract class EnchantGlintArmorMixin {
    @Redirect(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V", at = @At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;hasFoil()Z"))
    private boolean orion$armorFoil(ItemStack item) {
        var config=OrionClient.getConfigOrNull();
        return item.hasFoil() && !(config!=null && config.isEnchantGlintEnabled() && config.getEnchantGlintMode()==1);
    }
    @Redirect(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V", at = @At(value="INVOKE",target="Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCullGlint(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType orion$armorType(Identifier texture) {
        var config=OrionClient.getConfigOrNull();
        return config!=null && config.isEnchantGlintEnabled() && config.getEnchantGlintMode()!=1 ? OrionGlintRenderTypes.armor(texture) : RenderTypes.armorCutoutNoCullGlint(texture);
    }
    @Redirect(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V", at = @At(value="INVOKE",target="Lnet/minecraft/client/renderer/rendertype/RenderTypes;trimmedArmorGlint()Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType orion$trimType() {
        var config=OrionClient.getConfigOrNull();
        return config!=null && config.isEnchantGlintEnabled() && config.getEnchantGlintMode()!=1 ? OrionGlintRenderTypes.trim() : RenderTypes.trimmedArmorGlint();
    }
}
