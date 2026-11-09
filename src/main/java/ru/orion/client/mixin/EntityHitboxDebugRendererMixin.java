package ru.orion.client.mixin;

import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.effect.OrionRainbow;

/**
 * HitBox: когда модуль включён, заменяет ванильный хитбокс (F3+B) на чистый цветной бокс —
 * без красной планки глаз, синего вектора взгляда и серверного дубля.
 */
@Mixin(EntityHitboxDebugRenderer.class)
public abstract class EntityHitboxDebugRendererMixin {
    @Inject(
            method = "showHitboxes(Lnet/minecraft/world/entity/Entity;FZ)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void orion$cleanColoredHitbox(Entity entity, float partialTick, boolean serverBox, CallbackInfo ci) {
        OrionConfig config = OrionClient.getConfigOrNull();
        if (config == null || !config.isHitBoxEnabled()) {
            return;
        }

        ci.cancel();
        if (serverBox) {
            return; // серверный дубль из дебага не рисуем вовсе
        }

        Minecraft client = Minecraft.getInstance();
        boolean target = client.crosshairPickEntity == entity;
        if (config.isHitBoxPlayersOnly() && !(entity instanceof Player) || config.isHitBoxTargetOnly() && !target) return;
        int color = OrionRainbow.orElse(orion$parseColor(target ? config.getHitBoxTargetColor() : config.getHitBoxColor()));
        Vec3 offset = entity.getPosition(partialTick).subtract(entity.position());
        AABB box = entity.getBoundingBox().move(offset);
        float width = config.getHitBoxWidth();
        if (config.getHitBoxStyle() == 0) {
            Gizmos.cuboid(box, GizmoStyle.stroke(color, width));
        } else {
            double part = Math.min(box.getXsize(), Math.min(box.getYsize(), box.getZsize())) * .27;
            for (int i = 0; i < 8; i++) {
                double x = (i & 1) == 0 ? box.minX : box.maxX;
                double y = (i & 2) == 0 ? box.minY : box.maxY;
                double z = (i & 4) == 0 ? box.minZ : box.maxZ;
                Vec3 corner = new Vec3(x, y, z);
                Gizmos.line(corner, corner.add((i & 1) == 0 ? part : -part, 0, 0), color, width);
                Gizmos.line(corner, corner.add(0, (i & 2) == 0 ? part : -part, 0), color, width);
                Gizmos.line(corner, corner.add(0, 0, (i & 4) == 0 ? part : -part), color, width);
            }
        }
    }

    private static int orion$parseColor(String hex) {
        try {
            return ARGB.opaque(Integer.parseInt(hex.substring(1), 16));
        } catch (RuntimeException ignored) {
            return ARGB.color(255, 255, 124, 0);
        }
    }
}
