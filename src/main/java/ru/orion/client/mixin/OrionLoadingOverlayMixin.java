package ru.orion.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.gui.OrionTitleMenu;

@Mixin(LoadingOverlay.class)
abstract class OrionLoadingOverlayMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ReloadInstance reload;
    @Shadow @Final private boolean fadeIn;
    @Shadow private long fadeOutStart;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void orion$renderSplash(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (fadeIn || minecraft.isDemo() || minecraft.options.narrator().get() != NarratorStatus.OFF) return;
        var menu = OrionTitleMenu.INSTANCE;
        if (fadeOutStart < 0) {
            if (menu.renderStartup(graphics, reload.getActualProgress())) ci.cancel();
        } else if (menu.startupDrawn()) {
            if (minecraft.gui.screen() instanceof TitleScreen && menu.renderStartup(graphics, 1F)) {
                // tick() уже завершил reload, проверил ошибки и вызвал штатный callback.
                minecraft.gui.setOverlay(null);
                ci.cancel();
            } else {
                menu.release();
            }
        }
    }
}
