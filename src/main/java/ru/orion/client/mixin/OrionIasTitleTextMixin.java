package ru.orion.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.gui.OrionTitleMenu;

/** IAS рисует подпись после извлечения экрана через Fabric ScreenEvents. */
@Pseudo
@Mixin(targets = "ru.vidtu.ias.IASMinecraft", remap = false)
abstract class OrionIasTitleTextMixin {
    @Inject(method = "onDraw(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/Font;Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void orion$hideDuplicateTitleAccount(Screen screen, Font font, GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (OrionTitleMenu.INSTANCE.active(screen)) ci.cancel();
    }
}
