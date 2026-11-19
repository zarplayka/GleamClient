package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.render.GleamPose;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Числа наблюдаемых изменений здоровья и поглощения. */
public final class OrionDamageIndicators {
    private static final long WAIT_NANOS = 900_000_000L;
    private static final long MERGE_NANOS = 300_000_000L;
    private static final double TRACK_RANGE_SQUARED = 32D * 32D;
    private static final int HEAL_COLOR = 0x66EF9E;
    private static final int MAX_INDICATORS = 32;
    private final OrionConfigManager configManager;
    private final Map<LivingEntity, Pending> pending = new IdentityHashMap<>();
    private final Map<LivingEntity, Sample> health = new IdentityHashMap<>();
    private int scan;
    private int lane;
    private final List<Indicator> indicators = new ArrayList<>();
    private ClientLevel level;

    public OrionDamageIndicators(OrionConfigManager configManager) { this.configManager = configManager; }
    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
    }
    public void onAttack(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        var config = configManager.getConfig();
        if (!config.isDamageIndicatorsEnabled() || !config.isDamageIndicatorsOwnHits() || client.level == null
                || !(entity instanceof LivingEntity target) || target == client.player) return;
        if (client.level != level) { clear(); level = client.level; }
        float health = effectiveHealth(target);
        Pending previous = pending.get(target);
        long now = System.nanoTime();
        if (previous != null && now - previous.created() < WAIT_NANOS && previous.health() - health > .05F) {
            addIndicator(target, previous.health() - health, false, now);
        }
        pending.put(target, new Pending(health, now));
    }
    private void tick(Minecraft client) {
        OrionConfig config = configManager.getConfig();
        if (!config.isDamageIndicatorsEnabled() || client.level == null || client.player == null) {
            clear(); level = null; return;
        }
        if (level != client.level) { clear(); level = client.level; }
        long now = System.nanoTime();
        indicators.removeIf(indicator -> now - indicator.updated() >= indicator.lifetime());
        if (!config.isDamageIndicatorsOwnHits()) pending.clear();
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            LivingEntity target = entry.getKey();
            Pending attack = entry.getValue();
            float damage = attack.health() - effectiveHealth(target);
            if (now - attack.created() >= WAIT_NANOS || target.isRemoved()) {
                iterator.remove();
            } else if (damage > .05F && Float.isFinite(damage)) {
                addIndicator(target, damage, false, now);
                iterator.remove();
            }
        }
        if (!config.isDamageIndicatorsOwnHits() || config.isDamageIndicatorsHealing()) {
            trackNearby(client, config, now);
        } else {
            health.clear();
        }
    }

    private void trackNearby(Minecraft client, OrionConfig config, long now) {
        scan++;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living == client.player || living.isRemoved()
                    || living.distanceToSqr(client.player) > TRACK_RANGE_SQUARED) continue;
            float current = effectiveHealth(living);
            if (!Float.isFinite(current)) continue;
            Sample previous = health.put(living, new Sample(current, scan));
            if (previous == null) continue;
            float delta = current - previous.health();
            if (delta < -.05F && !config.isDamageIndicatorsOwnHits()) {
                addIndicator(living, -delta, false, now);
            } else if (delta > .05F && config.isDamageIndicatorsHealing()) {
                addIndicator(living, delta, true, now);
            }
        }
        health.values().removeIf(sample -> sample.scan() != scan);
    }

    private static float effectiveHealth(LivingEntity target) {
        return target.getHealth() + target.getAbsorptionAmount();
    }

    private void addIndicator(LivingEntity target, float amount, boolean healing, long now) {
        if (!Float.isFinite(amount) || amount <= .05F) return;
        var config = configManager.getConfig();
        boolean lethal = !healing && target.getHealth() <= 0F;
        long lifetime = (long) (config.getDamageIndicatorsLifetime() * 1_000_000_000D);
        int entityId = target.getId();
        if (config.isDamageIndicatorsMerge()) {
            for (int i = indicators.size() - 1; i >= 0; i--) {
                var previous = indicators.get(i);
                if (previous.entityId() != entityId || previous.healing() != healing
                        || now - previous.updated() > MERGE_NANOS || now - previous.created() > lifetime) continue;
                float total = previous.amount() + amount;
                indicators.set(i, new Indicator(entityId, previous.position(), total, healing, lethal,
                        previous.lane(), previous.created(), now, lifetime, label(total, healing, lethal)));
                return;
            }
        }
        if (indicators.size() >= MAX_INDICATORS) indicators.removeFirst();
        Vec3 origin = target.position().add(0, target.getBbHeight() * .64D, 0);
        // Spawn slightly in front of the hitbox so the target doesn't cover the number.
        Vec3 towardCamera = GleamMinecraft.camera(Minecraft.getInstance()).position().subtract(origin);
        Vec3 front = new Vec3(towardCamera.x, 0, towardCamera.z).normalize().scale(target.getBbWidth() * .62F);
        float side = (lane++ % 2 == 0 ? 1F : -1F) * (.29F + (lane % 3) * .08F);
        indicators.add(new Indicator(entityId, origin.add(front), amount, healing, lethal,
                side, now, now, lifetime, label(amount, healing, lethal)));
    }

    private static FormattedCharSequence label(float amount, boolean healing, boolean lethal) {
        int tenths = Math.round(Math.min(amount, 999.9F) * 10F);
        String value = tenths >= 9999 ? "999+" : tenths % 10 == 0
                ? Integer.toString(tenths / 10) : (tenths / 10) + "." + (tenths % 10);
        return Component.literal((healing ? "+" : "−") + value + (lethal ? "!" : "")).getVisualOrderText();
    }

    private void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        OrionConfig config = configManager.getConfig();
        if (!config.isDamageIndicatorsEnabled() || client.level == null || client.level != level || indicators.isEmpty()) return;
        var camera = context.levelState().cameraRenderState;
        if (camera == null || !camera.initialized || camera.orientation == null) return;
        int low = OrionWorldChanger.parseOpaqueColor(config.getDamageIndicatorsLowColor());
        int high = OrionWorldChanger.parseOpaqueColor(config.getDamageIndicatorsHighColor());
        long now = System.nanoTime();
        PoseStack stack = context.poseStack();
        for (Indicator indicator : indicators) {
            float life = Math.clamp((now - indicator.updated()) / (float) indicator.lifetime(), 0F, 1F);
            if (life >= 1F) continue;
            float age = Math.clamp((now - indicator.created()) / (float) indicator.lifetime(), 0F, 1F);
            float launch = 1F - (1F - age) * (1F - age) * (1F - age);
            float fade = Math.clamp((1F - life) / .28F, 0F, 1F);
            int alpha = Math.round(fade * fade * 255F);
            if (alpha < 8) continue;
            float mix = indicator.lethal() ? 1F : Math.clamp(indicator.amount() / config.getDamageIndicatorsThreshold(), 0F, 1F);
            int color = indicator.healing() ? ARGB.color(alpha, HEAL_COLOR) : ARGB.color(alpha,
                    Math.round(ARGB.red(low) + (ARGB.red(high) - ARGB.red(low)) * mix),
                    Math.round(ARGB.green(low) + (ARGB.green(high) - ARGB.green(low)) * mix),
                    Math.round(ARGB.blue(low) + (ARGB.blue(high) - ARGB.blue(low)) * mix));

            float entry = Math.clamp((now - indicator.created()) / 145_000_000F, 0F, 1F);
            float recoil = 1F - Math.clamp((now - indicator.updated()) / 190_000_000F, 0F, 1F);
            float size = .022F * config.getDamageIndicatorsScale() * (.72F + .28F * entry + .22F * recoil * recoil);
            if (indicator.lethal()) size *= 1.14F;
            Vec3 pos = indicator.position().subtract(camera.pos);
            stack.pushPose();
            stack.translate(pos.x, pos.y, pos.z);
            GleamPose.rotate(stack, camera.orientation);
            stack.translate(indicator.lane() * (.7F + .75F * launch), .52F * launch + .18F * age, 0);
            stack.scale(size, -size, size);
            var text = indicator.label();
            context.submitNodeCollector().submitText(stack, -client.font.width(text) / 2F, -4F, text, true,
                    Font.DisplayMode.NORMAL, 0xF000F0, color, 0, 0);
            stack.popPose();
        }
    }

    private void clear() {
        pending.clear();
        health.clear();
        indicators.clear();
        lane = scan = 0;
    }

    private record Pending(float health, long created) { }
    private record Sample(float health, int scan) { }
    private record Indicator(int entityId, Vec3 position, float amount, boolean healing, boolean lethal,
                             float lane, long created, long updated, long lifetime, FormattedCharSequence label) { }
}
