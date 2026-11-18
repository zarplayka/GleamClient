package ru.orion.client.mixin;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.cosmetic.GleamPetPlacement;

@Mixin(BlockItem.class)
abstract class GleamPetBlockPlacementMixin {
    @Inject(method = "canPlace", at = @At("RETURN"), cancellable = true)
    private void gleam$occupiedByPet(BlockPlaceContext context, BlockState state,
                                     CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() && GleamPetPlacement.reject(context, state)) {
            callback.setReturnValue(false);
        }
    }
}
