package ru.orion.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.OrionClient;
import ru.orion.client.gui.OrionClickGuiScreen;
import ru.orion.client.gui.OrionTitleMenu;

@Mixin(TitleScreen.class)
abstract class OrionTitleScreenMixin extends Screen {
    protected OrionTitleScreenMixin(Component title) { super(title); }

    @Shadow private Component getMultiplayerDisabledReason() { throw new AssertionError(); }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void orion$renderMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (minecraft.gui.overlay() != null || minecraft.isDemo() || minecraft.options.narrator().get() != NarratorStatus.OFF) return;
        Component reason = getMultiplayerDisabledReason();
        if (OrionTitleMenu.INSTANCE.renderTitle(this, graphics, mouseX, mouseY, reason == null ? "" : reason.getString())) ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void orion$press(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!OrionTitleMenu.INSTANCE.active(this)) return;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT)
            OrionTitleMenu.INSTANCE.mouse(event.x(), event.y(), true, this::orion$runAction);
        cir.setReturnValue(true);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!OrionTitleMenu.INSTANCE.active(this)) return super.mouseReleased(event);
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT)
            OrionTitleMenu.INSTANCE.mouse(event.x(), event.y(), false, this::orion$runAction);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!OrionTitleMenu.INSTANCE.active(this)) return super.keyPressed(event);
        return OrionTitleMenu.INSTANCE.key(event, this::orion$runAction);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void orion$receiveActions(CallbackInfo ci) {
        if (OrionTitleMenu.INSTANCE.active(this)) OrionTitleMenu.INSTANCE.receive(this::orion$runAction);
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void orion$release(CallbackInfo ci) {
        OrionTitleMenu.INSTANCE.release();
    }

    @Unique
    private void orion$runAction(String action) {
        if (minecraft.gui.screen() != this || minecraft.gui.overlay() != null) return;
        if ("minecraft-accounts".equals(action) || "add-microsoft".equals(action) || "add-offline".equals(action)) {
            OrionTitleMenu.INSTANCE.openMinecraftAccounts(this, action);
            return;
        }
        if ("theme".equals(action)) {
            var client = OrionClient.getInstance();
            if (client != null) {
                var manager = client.getConfigManager();
                manager.setClickGuiTheme(manager.getConfig().getClickGuiTheme() == 4 ? 3 : 4);
            }
            return;
        }
        if ("modules".equals(action) || "account".equals(action) || "client-settings".equals(action)) {
            var client = OrionClient.getInstance();
            var page = switch (action) {
                case "account" -> OrionClickGuiScreen.TitlePage.ACCOUNT;
                case "client-settings" -> OrionClickGuiScreen.TitlePage.SETTINGS;
                default -> OrionClickGuiScreen.TitlePage.MODULES;
            };
            if (client != null) minecraft.gui.setScreen(OrionClickGuiScreen.fromTitle(client.getConfigManager(), this, page));
            return;
        }
        String label = switch (action) {
            case "single" -> "menu.singleplayer";
            case "multi" -> "menu.multiplayer";
            case "settings" -> "menu.options";
            case "quit" -> "menu.quit";
            default -> "";
        };
        if (label.isEmpty()) return;
        Component message = Component.translatable(label);
        for (var listener : children()) {
            if (listener instanceof Button button && button.active && message.equals(button.getMessage())) {
                button.playDownSound(minecraft.getSoundManager());
                button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                return;
            }
        }
    }
}
