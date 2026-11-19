//? if <26.1 {
/*package ru.orion.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
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

/^* Анимация строк на основе ChatAnimation от Ezzenix (MIT). ^/
@Mixin(ChatComponent.class)
public abstract class GleamLegacyChatAnimationMixin {
    @Shadow private int chatScrollbarPos;
    @Shadow private int getLineHeight() { throw new AssertionError(); }

    @Unique private final java.util.HashMap<Integer, Long> orion$messageTimes = new java.util.HashMap<>();
    @Unique private long orion$lastMessageTime;
    @Unique private boolean orion$drawing;

    @Inject(method = "clearMessages(Z)V", at = @At("HEAD"))
    private void orion$clearAnimation(boolean clearHistory, CallbackInfo callback) {
        orion$messageTimes.clear();
        orion$lastMessageTime = 0L;
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("TAIL"))
    private void orion$recordMessage(Component content, MessageSignature signature,
                                     GuiMessageTag tag, CallbackInfo callback) {
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isAnimationsEnabled() || !config.isChatAnimationEnabled()) return;
        var messages = ((OrionChatHistoryAccessor) this).orion$getMessages();
        if (messages.isEmpty()) return;
        if (orion$messageTimes.size() >= 512) orion$messageTimes.clear();
        orion$lastMessageTime = System.nanoTime();
        orion$messageTimes.put(messages.getFirst().addedTime(), orion$lastMessageTime);
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V"))
    private void orion$slideChat(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY,
                                 boolean focused, boolean insertion, CallbackInfo callback) {
        var config = OrionClient.getConfigOrNull();
        orion$drawing = config != null && config.isAnimationsEnabled() && config.isChatAnimationEnabled()
                && chatScrollbarPos == 0;
        if (!orion$drawing || !config.isChatAnimationMessages() || orion$lastMessageTime == 0L) return;
        float progress = Math.clamp((System.nanoTime() - orion$lastMessageTime)
                / (config.getChatAnimationMessageTime() * 1_000_000F), 0F, 1F);
        if (progress < 1F) graphics.pose().translate(0F, getLineHeight() * config.getChatAnimationSlideDistance() * (1F - progress) * (1F - progress));
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At("RETURN"))
    private void orion$endDraw(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY,
                               boolean focused, boolean insertion, CallbackInfo callback) {
        orion$drawing = false;
    }

    @ModifyArgs(method = "forEachLine", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;accept(Lnet/minecraft/client/multiplayer/chat/GuiMessage$Line;IF)V"))
    private void orion$fadeLine(Args args) {
        if (!orion$drawing) return;
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isChatAnimationFade()) return;
        GuiMessage.Line line = args.get(0);
        Long added = orion$messageTimes.get(line.addedTime());
        if (added == null) return;
        float progress = Math.clamp((System.nanoTime() - added)
                / (config.getChatAnimationFadeTime() * 1_000_000F), 0F, 1F);
        if (progress < 1F) args.set(2, args.<Float>get(2) * (float) Math.sin(progress * Math.PI / 2D));
    }
}
*///?}
