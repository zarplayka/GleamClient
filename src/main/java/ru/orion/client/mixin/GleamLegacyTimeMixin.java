//? if <26.1 {
/*package ru.orion.client.mixin;
import net.minecraft.world.level.Level;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Level.class)
public abstract class GleamLegacyTimeMixin {
    @Inject(method="getDayTime", at=@At("RETURN"), cancellable=true)
    private void gleam$time(CallbackInfoReturnable<Long> callback) {
        var config = ru.orion.client.OrionClient.getConfigOrNull();
        if ((Object)this instanceof ClientLevel level && level.dimension().equals(Level.OVERWORLD)
                && config != null && config.isCustomTimeEnabled())
            callback.setReturnValue(Math.floorDiv(callback.getReturnValue(),24000L)*24000L+config.getCustomTimeTicks());
    }
}
*///?}
