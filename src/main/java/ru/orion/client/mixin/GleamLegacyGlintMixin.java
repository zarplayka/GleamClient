package ru.orion.client.mixin;
import net.minecraft.client.renderer.rendertype.OrionGlintRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.OrionClient;

@Mixin(RenderTypes.class)
public abstract class GleamLegacyGlintMixin {
    private static boolean gleam$custom() {
        var config = OrionClient.getConfigOrNull();
        return config != null && config.isEnchantGlintEnabled() && config.getEnchantGlintMode() != 1;
    }
    @Inject(method = {"glint", "glintTranslucent"}, at = @At("RETURN"), cancellable = true)
    private static void gleam$item(CallbackInfoReturnable<RenderType> callback) {
        if (gleam$custom()) callback.setReturnValue(OrionGlintRenderTypes.item(Identifier.withDefaultNamespace("textures/atlas/blocks.png"), false, false));
    }
    @Inject(method = "armorEntityGlint", at = @At("RETURN"), cancellable = true)
    private static void gleam$armor(CallbackInfoReturnable<RenderType> callback) {
        if (gleam$custom()) callback.setReturnValue(OrionGlintRenderTypes.trim());
    }
    @Inject(method = "entityGlint", at = @At("RETURN"), cancellable = true)
    private static void gleam$entity(CallbackInfoReturnable<RenderType> callback) {
        if (gleam$custom()) callback.setReturnValue(OrionGlintRenderTypes.shield());
    }
}
