package ru.orion.client.mixin;

import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(VoxelShape.class)
public interface OrionVoxelShapeAccessor {
    @Accessor("shape") DiscreteVoxelShape gleam$discreteShape();
}
