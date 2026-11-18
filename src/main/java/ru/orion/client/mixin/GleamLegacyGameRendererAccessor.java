//? if <26.1 {
/*package ru.orion.client.mixin;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(GameRenderer.class)
public interface GleamLegacyGameRendererAccessor {
    @Invoker("getFov") float gleam$fov(Camera camera, float partialTick, boolean useFovSetting);
}
*///?}
