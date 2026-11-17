package ru.orion.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
//? if >=26.2 {
import net.minecraft.client.gui.Hud;
//?} else {
/*import net.minecraft.client.gui.Gui;
*///?}
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;
import ru.orion.client.effect.OrionNoRender;

import static ru.orion.client.effect.OrionNoRender.Option.*;

//? if >=26.2 {
@Mixin(Hud.class)
//?} else {
/*@Mixin(Gui.class)
*///?}
public abstract class OrionNoRenderHudMixin {
    //? if >=26.1 {
    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    *///?}
    private void orion$effects(CallbackInfo callback) {
        var config = OrionClient.getConfigOrNull();
        if (OrionNoRender.hides(config, VANILLA_EFFECTS)
                || config != null && config.isPotionsEnabled() && config.isPotionsHideVanilla()) callback.cancel();
    }

    //? if >=26.1 {
    @Inject(method = "extractTextureOverlay", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
    *///?}
    private void orion$texture(GuiGraphicsExtractor graphics, Identifier texture, float alpha, CallbackInfo callback) {
        if (texture.equals(Identifier.withDefaultNamespace("textures/misc/powder_snow_outline.png"))
                && OrionNoRender.hides(POWDER_SNOW)) callback.cancel();
        if (texture.equals(Identifier.withDefaultNamespace("textures/misc/pumpkinblur.png"))
                && OrionNoRender.hides(PUMPKIN)) callback.cancel();
    }

    //? if >=26.1 {
    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    *///?}
    private void orion$vignette(CallbackInfo callback) {
        if (OrionNoRender.hides(VIGNETTE)) callback.cancel();
    }

    //? if >=26.1 {
    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    *///?}
    private void orion$portal(CallbackInfo callback) {
        if (OrionNoRender.hides(PORTAL_OVERLAY)) callback.cancel();
    }

    //? if >=26.1 {
    @Inject(method = "extractConfusionOverlay", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderConfusionOverlay", at = @At("HEAD"), cancellable = true)
    *///?}
    private void orion$nausea(CallbackInfo callback) {
        if (OrionNoRender.hides(NAUSEA_OVERLAY)) callback.cancel();
    }

    //? if >=26.1 {
    @Inject(method = "extractSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    *///?}
    private void orion$scope(CallbackInfo callback) {
        if (OrionNoRender.hides(SPYGLASS)) callback.cancel();
    }

    //? if >=26.1 {
    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    *///?}
    private void orion$scoreboard(CallbackInfo callback) {
        if (OrionNoRender.hides(SCOREBOARD)) callback.cancel();
    }

    //? if >=26.1 {
    @Inject(method = "extractBossOverlay", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderBossOverlay", at = @At("HEAD"), cancellable = true)
    *///?}
    private void orion$bossBar(CallbackInfo callback) {
        if (OrionNoRender.hides(BOSS_BAR)) callback.cancel();
    }

    //? if >=26.1 {
    @Inject(method = "extractTitle", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderTitle", at = @At("HEAD"), cancellable = true)
    *///?}
    private void orion$title(CallbackInfo callback) {
        if (OrionNoRender.hides(TITLES)) callback.cancel();
    }

    //? if >=26.1 {
    @Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true)
    *///?}
    private void orion$actionbar(CallbackInfo callback) {
        if (OrionNoRender.hides(ACTION_BAR)) callback.cancel();
    }

    //? if >=26.1 {
    @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true)
    *///?}
    private void orion$selectedItemName(CallbackInfo callback) {
        if (OrionNoRender.hides(TITLED_ITEM)) callback.cancel();
    }
}
