package ru.orion.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.orion.client.OrionClient;

@Mixin(SoundEngine.class)
public abstract class OrionSoundSettingsMixin {
    @WrapOperation(method = "play",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
    private float orion$playVolume(SoundEngine engine, float volume, SoundSource source, Operation<Float> original,
                                   SoundInstance sound) {
        return orion$eventVolume(engine, volume, source, original, sound);
    }

    @WrapOperation(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
    private float orion$eventVolume(SoundEngine engine, float volume, SoundSource source, Operation<Float> original,
                                    SoundInstance sound) {
        var config = OrionClient.getConfigOrNull();
        if (config != null && config.isSoundSettingsEnabled()) {
            volume *= config.getSoundVolume(sound.getIdentifier().toString()) / 100F;
        }
        return original.call(engine, volume, source);
    }
}
