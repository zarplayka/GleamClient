package ru.orion.client.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FoodData.class)
public interface OrionFoodDataAccessor {
    @Accessor("exhaustionLevel") float orion$getExhaustion();
    @Accessor("exhaustionLevel") void orion$setExhaustion(float value);
}
