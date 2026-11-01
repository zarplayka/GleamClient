package ru.orion.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface OrionContainerGeometryAccessor {
    @Accessor("leftPos") int orion$getLeftPos();
    @Accessor("topPos") int orion$getTopPos();
    @Accessor("imageWidth") int orion$getImageWidth();
    @Accessor("imageHeight") int orion$getImageHeight();
}
