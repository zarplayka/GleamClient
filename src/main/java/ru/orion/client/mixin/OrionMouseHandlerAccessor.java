package ru.orion.client.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface OrionMouseHandlerAccessor {
    @Accessor("activeButton") MouseButtonInfo orion$getActiveButton();
}
