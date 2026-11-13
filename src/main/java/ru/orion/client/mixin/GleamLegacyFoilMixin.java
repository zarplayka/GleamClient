//? if <26.1 {
/*package ru.orion.client.mixin;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.orion.client.OrionClient;
// Не меняем кеш слоя предмета: режим None применяется только к текущей отрисовке.
@Mixin(ItemRenderer.class)
public abstract class GleamLegacyFoilMixin {
    private static boolean gleam$disabled() {
        var config = OrionClient.getConfigOrNull();
        return config != null && config.isEnchantGlintEnabled() && config.getEnchantGlintMode() == 1;
    }
    @ModifyVariable(method="renderItem(Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II[ILjava/util/List;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V", at=@At("HEAD"),argsOnly=true)
    private static ItemStackRenderState.FoilType gleam$foil(ItemStackRenderState.FoilType original) {
        return gleam$disabled() ? ItemStackRenderState.FoilType.NONE : original;
    }
    @ModifyVariable(method="getFoilBuffer",at=@At("HEAD"),argsOnly=true,ordinal=1)
    private static boolean gleam$foilBuffer(boolean original) { return original && !gleam$disabled(); }
}
*///?}
