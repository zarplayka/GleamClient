package ru.orion.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import ru.orion.client.OrionClient;

import java.util.WeakHashMap;

/** Анимация строк на основе ChatAnimation от Ezzenix (MIT). */
@Mixin(ChatComponent.class)
public abstract class OrionChatAnimationMixin {
    @Shadow private int chatScrollbarPos;
    @Shadow private int getLineHeight() { throw new AssertionError(); }

    @Unique private final WeakHashMap<GuiMessage, Long> orion$messageTimes = new WeakHashMap<>();
    @Unique private long orion$lastMessageTime;
    @Unique private boolean orion$drawing;

    @Inject(method = "clearMessages(Z)V", at = @At("HEAD"))
    private void orion$clearAnimation(boolean clearHistory, CallbackInfo callback) {
        orion$messageTimes.clear();
        orion$lastMessageTime = 0L;
    }

    @Inject(method = "addMessage", at = @At("TAIL"))
    private void orion$recordMessage(Component content, MessageSignature signature, GuiMessageSource source,
                                     GuiMessageTag tag, CallbackInfo callback) {
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isChatAnimationEnabled() || !config.isChatAnimationMessages()) return;
        var messages = ((OrionChatHistoryAccessor) this).orion$getMessages();
        if (messages.isEmpty()) return;
        orion$lastMessageTime = System.nanoTime();
        orion$messageTimes.put(messages.getFirst(), orion$lastMessageTime);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V"))
    private void orion$slideChat(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY,
                                 ChatComponent.DisplayMode mode, boolean insertion, CallbackInfo callback) {
        var config = OrionClient.getConfigOrNull();
        orion$drawing = config != null && config.isChatAnimationEnabled() && config.isChatAnimationMessages()
                && chatScrollbarPos == 0;
        if (!orion$drawing || orion$lastMessageTime == 0L) return;
        float progress = Math.clamp((System.nanoTime() - orion$lastMessageTime)
                / (config.getChatAnimationMessageTime() * 1_000_000F), 0F, 1F);
        if (progress < 1F) graphics.pose().translate(0F, getLineHeight() * .8F * (1F - progress));
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("RETURN"))
    private void orion$endDraw(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY,
                               ChatComponent.DisplayMode mode, boolean insertion, CallbackInfo callback) {
        orion$drawing = false;
    }

    @ModifyArgs(method = "forEachLine", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;accept(Lnet/minecraft/client/multiplayer/chat/GuiMessage$Line;IF)V"))
    private void orion$fadeLine(Args args) {
        if (!orion$drawing) return;
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isChatAnimationFade()) return;
        GuiMessage.Line line = args.get(0);
        Long added = orion$messageTimes.get(line.parent());
        if (added == null) return;
        float progress = Math.clamp((System.nanoTime() - added)
                / (config.getChatAnimationMessageTime() * 1_000_000F), 0F, 1F);
        if (progress < 1F) args.set(2, args.<Float>get(2) * (float) Math.sin(progress * Math.PI / 2D));
    }
}
