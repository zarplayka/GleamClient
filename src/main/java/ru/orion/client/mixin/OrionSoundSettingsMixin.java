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
    @WrapOperation(method = {"play", "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
    private float orion$eventVolume(SoundEngine engine, float volume, SoundSource source, Operation<Float> original,
                                    SoundInstance sound) {
        var config = OrionClient.getConfigOrNull();
        if (config != null && config.isHitSoundEnabled() && config.isHitSoundMuteVanilla()
                && source == SoundSource.PLAYERS && sound.getIdentifier().getNamespace().equals("minecraft")) {
            String path = sound.getIdentifier().getPath();
            if (path.startsWith("entity.player.attack.") || path.equals("item.spear.attack")
                    || path.equals("item.spear.hit") || path.equals("item.spear_wood.attack")
                    || path.equals("item.spear_wood.hit") || path.startsWith("item.mace.smash_")) {
                return 0F;
            }
        }
        if (config != null && config.isSoundSettingsEnabled()) {
            volume *= config.getSoundVolume(sound.getIdentifier().toString()) / 100F;
        }
        return original.call(engine, volume, source);
    }
}
