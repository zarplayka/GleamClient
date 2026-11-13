package ru.orion.client.mixin;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
/** Пакет Minecraft меняется при remap; package-private фабрику вызываем через invoker. */
@Mixin(RenderType.class)
public interface GleamRenderTypeAccess {
    @Invoker("create") static RenderType gleam$create(String name, RenderSetup setup) { throw new AssertionError(); }
}
