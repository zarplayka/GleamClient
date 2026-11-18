package ru.orion.client.legacy.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.orion.client.effect.OrionLegacyFakePrefix;
import ru.orion.client.effect.OrionLegacyNameProtect;

/**
 * ProtectName: основные пути отрисовки текста (drawInBatch и GUI) проходят через
 * drawInternal, а контурный текст — через drawInBatch8xOutline.
 */
@Mixin(Font.class)
public abstract class OrionLegacyFontMixin {
    @ModifyVariable(
            method = "drawInternal(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I",
            at = @At("HEAD"),
            argsOnly = true
    )
    private String orion$protectPlainText(String text) {
        return OrionLegacyNameProtect.replace(OrionLegacyFakePrefix.replace(text));
    }

    @ModifyVariable(
            method = "drawInternal(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I",
            at = @At("HEAD"),
            argsOnly = true
    )
    private FormattedCharSequence orion$protectFormattedText(FormattedCharSequence text) {
        return OrionLegacyNameProtect.replace(OrionLegacyFakePrefix.replace(text));
    }

    @ModifyVariable(method = "drawInBatch8xOutline", at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence orion$protectOutlineText(FormattedCharSequence text) {
        return OrionLegacyNameProtect.replace(OrionLegacyFakePrefix.replace(text));
    }

    // Ширина меряется до подмены в drawInternal, поэтому скорборд/таб верстались под
    // реальный ник, и псевдоним вылезал за границы. Подменяем и в width(...).
    @ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
    private String orion$protectStringWidth(String text) {
        return OrionLegacyNameProtect.replace(OrionLegacyFakePrefix.replace(text));
    }

    @ModifyVariable(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), argsOnly = true)
    private FormattedText orion$protectTextWidth(FormattedText text) {
        return OrionLegacyNameProtect.replace(OrionLegacyFakePrefix.replace(text));
    }

    @ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence orion$protectSequenceWidth(FormattedCharSequence text) {
        return OrionLegacyNameProtect.replace(OrionLegacyFakePrefix.replace(text));
    }
}
