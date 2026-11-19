package ru.orion.client.mixin;

//? if >=26.2 {
import net.minecraft.client.gui.Hud;
//?} else {
/*import net.minecraft.client.gui.Gui;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ru.orion.client.OrionClient;

//? if >=26.2 {
@Mixin(Hud.class)
//?} else {
/*@Mixin(Gui.class)
*///?}
public abstract class AnimatedHotbarMixin {
    @Unique private float orion$selectionX = Float.NaN;
    @Unique private long orion$lastNanos;
    //? if >=26.3 {
    @ModifyArg(method = "extractItemHotbar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",ordinal=1), index=2)
    //?} else {
    /*@ModifyArg(method = "extractItemHotbar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",ordinal=1), index=2)
    *///?}
    private int orion$smoothSelection(int target) {
        var config=OrionClient.getConfigOrNull();
        long now=System.nanoTime();
        if(config==null || !config.isAnimationsEnabled() || !config.isAnimationsHotbar() || !Float.isFinite(orion$selectionX) || orion$lastNanos==0 || Math.abs(target-orion$selectionX)>200) {
            orion$selectionX=target;orion$lastNanos=now;return target;
        }
        float elapsed=Math.max(0,Math.min(0.1F,(now-orion$lastNanos)/1_000_000_000F));
        orion$lastNanos=now;
        float factor=1F-(float)Math.exp(-elapsed*4000F/config.getAnimationsDuration());
        orion$selectionX+=(target-orion$selectionX)*factor;
        if(Math.abs(target-orion$selectionX)<.05F)orion$selectionX=target;
        return Math.round(orion$selectionX);
    }
}
