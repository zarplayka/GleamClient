package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.renderer.rendertype.OrionEspRenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.effect.kill.OrionArcaneKillModels;
import ru.orion.client.effect.kill.OrionSoulKillModels;
import ru.orion.client.effect.kill.OrionKillAudio;
import ru.orion.client.effect.kill.OrionKillMaterial;
import ru.orion.client.effect.kill.OrionRagdoll;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.lang.ref.WeakReference;

/** Клиентская косметика по серверным сведениям об уроне и смерти. */
public final class OrionKillEffects {
    private static final long CONFIRM_WINDOW = 3_000_000_000L;
    private static final long DAMAGE_WINDOW = 500_000_000L;
    private final OrionConfigManager manager;
    private final Map<Integer, Definition> registry = new LinkedHashMap<>();
    private final List<ActiveEffect> active = new ArrayList<>();
    private final List<OrionRagdoll> ragdolls = new ArrayList<>();
    private final Map<UUID, Death> deaths = new HashMap<>();
    private final Map<UUID, Confirmation> confirmations = new HashMap<>();
    private final Map<UUID, Emission> emitted = new HashMap<>();
    private final Map<UUID, DamageEvidence> damage = new HashMap<>();
    private Object level;
    private boolean previouslyEnabled;
    private long clientTick;

    public OrionKillEffects(OrionConfigManager manager) {
        this.manager = manager;
        for (int i = 0; i < 9; i++) {
            if (i == 7) continue;
            final int type = i;
            boolean organic = i == 0 || i == 5 || i == 7 || i == 8;
            registerEffect(i, new Definition(5F, (stack, vertices, age, quality, particles, seed) -> {
                if (organic) OrionSoulKillModels.render(type, stack, vertices, age, quality, particles, seed);
                else OrionArcaneKillModels.render(type, stack, vertices, age, quality, particles, seed);
            }));
        }
    }

    public void registerEffect(int id, Definition definition) {
        if (id < 0 || definition == null || definition.renderer() == null || !Float.isFinite(definition.duration()) || definition.duration() <= 0F)
            throw new IllegalArgumentException("Invalid kill effect definition");
        if (registry.putIfAbsent(id, definition) != null) throw new IllegalArgumentException("Duplicate kill effect id: " + id);
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            clientTick++;
            update();
            if (client.level != null && !client.isPaused()) {
                for (OrionRagdoll body : ragdolls) body.tick(client.level);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
    }

    public void clear() {
        active.forEach(effect -> OrionKillAudio.stop(effect.sound()));
        active.clear(); ragdolls.clear(); deaths.clear(); confirmations.clear(); emitted.clear(); damage.clear(); level = null;
    }

    private boolean ready() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != level) { clear(); level = client.level; }
        return client.level != null && client.player != null;
    }

    private void update() {
        if (!ready()) return;
        long now = System.nanoTime();
        boolean enabled = manager.getConfig().isKillEffectsEnabled();
        if (previouslyEnabled && !enabled) {
            active.forEach(effect -> OrionKillAudio.stop(effect.sound()));
            active.clear();
        }
        previouslyEnabled = enabled;
        boolean ragdollEnabled = manager.getConfig().isRagdollEnabled();
        ragdolls.removeIf(body -> body.expired() || !ragdollEnabled && !body.preview());
        if (!enabled && !ragdollEnabled) {
            deaths.clear(); confirmations.clear(); emitted.clear(); damage.clear();
        }
        active.removeIf(effect -> {
            if ((!effect.preview() && !enabled) || effect.age(now) >= effect.definition().duration()) {
                OrionKillAudio.stop(effect.sound());
                return true;
            }
            return false;
        });
        int limit = manager.getConfig().getKillEffectsLimit();
        while (active.size() > limit) OrionKillAudio.stop(active.removeFirst().sound());
        deaths.values().removeIf(death -> now - death.time() > CONFIRM_WINDOW);
        confirmations.values().removeIf(confirmation -> now - confirmation.time() > CONFIRM_WINDOW);
        emitted.values().removeIf(emission -> now - emission.time() > 30_000_000_000L);
        var iterator = damage.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            DamageEvidence evidence = entry.getValue();
            var entity = Minecraft.getInstance().level.getEntity(evidence.entityId());
            if (!evidence.matches(evidence.entityId(), clientTick, now) || entity == null
                    || !entry.getKey().equals(entity.getUUID())) {
                iterator.remove();
            } else if (entity instanceof LivingEntity victim && victim.getHealth() <= 0F) {
                // Некоторые серверы передают нулевое здоровье без отдельного статуса 3.
                iterator.remove();
                recordDeath(victim, evidence, now);
            }
        }
    }

    public void onDamage(ClientboundDamageEventPacket packet) {
        if (!ready() || !tracksKills()) return;
        Minecraft client = Minecraft.getInstance();
        if (!(client.level.getEntity(packet.entityId()) instanceof LivingEntity victim) || victim == client.player || !accepts(victim)) return;
        // Любой последующий чужой или природный урон отменяет наше свидетельство.
        // Локальный клик/взмах оружием сам по себе сюда не попадает.
        damage.remove(victim.getUUID());
        if (packet.getSource(client.level).getEntity() != client.player) return;
        damage.put(victim.getUUID(), new DamageEvidence(victim.getId(), clientTick, System.nanoTime()));
        trim(damage);
    }

    public void onDeath(ClientboundEntityEventPacket packet) {
        if (!ready() || !tracksKills() || packet.getEventId() != 3) return;
        Minecraft client = Minecraft.getInstance();
        if (!(packet.getEntity(client.level) instanceof LivingEntity victim) || victim == client.player || !accepts(victim)) return;
        recordDeath(victim, damage.remove(victim.getUUID()), System.nanoTime());
    }

    private boolean accepts(LivingEntity victim) {
        return manager.getConfig().isRagdollEnabled() && victim instanceof AbstractClientPlayer
                || acceptsKillEffect(victim);
    }

    private boolean tracksKills() {
        return manager.getConfig().isKillEffectsEnabled() || manager.getConfig().isRagdollEnabled();
    }

    private boolean acceptsKillEffect(LivingEntity victim) {
        int mode = manager.getConfig().getKillEffectsTargets();
        return manager.getConfig().isKillEffectsEnabled()
                && (mode == 1 || (mode == 0 ? victim instanceof Player : victim instanceof Mob));
    }

    private void recordDeath(LivingEntity victim, DamageEvidence evidence, long now) {
        if (!accepts(victim)) return;
        UUID id = victim.getUUID();
        Emission previous = emitted.get(id);
        if (previous != null && previous.sameEntity(victim)) return;
        deaths.put(id, new Death(victim.position(), victim.getYRot(), victim.getId(), victim, now));
        trim(deaths);
        if (evidence != null && evidence.matches(victim.getId(), clientTick, now)) {
            confirmations.put(id, new Confirmation(victim.getId(), now));
            trim(confirmations);
        }
        confirm(id);
    }

    public void onDeathMessage(Component message) {
        if (!ready() || !tracksKills()) return;
        UUID victim = confirmedVictim(message, Minecraft.getInstance().player.getUUID());
        if (victim == null) return;
        Death death = deaths.get(victim);
        var player = Minecraft.getInstance().level.getPlayerByUUID(victim);
        if (player == null && death == null) return;
        int entityId = player != null ? player.getId() : death.entityId();
        Emission previous = emitted.get(victim);
        if (previous != null && previous.sameEntity(player != null ? player : death.entity())) return;
        confirmations.put(victim, new Confirmation(entityId, System.nanoTime()));
        trim(confirmations);
        confirm(victim);
    }

    /** Не разбираем обычный текст: имена и сообщения сторонних плагинов неоднозначны. */
    public static UUID confirmedVictim(Component message, UUID localPlayer) {
        if (message.getContents() instanceof TranslatableContents contents) {
            String key = contents.getKey();
            Object[] args = contents.getArgs();
            if ((key.startsWith("death.attack.") || key.equals("death.fell.assist") || key.equals("death.fell.assist.item")
                    || key.equals("death.fell.finish") || key.equals("death.fell.finish.item"))
                    && args.length >= 2 && args[0] instanceof Component victim && args[1] instanceof Component killer) {
                UUID victimId = entityId(victim), killerId = entityId(killer);
                if (localPlayer.equals(killerId) && victimId != null && !localPlayer.equals(victimId)) return victimId;
            }
        }
        // Обёртки серверного сообщения могут добавлять стиль и префикс через siblings.
        for (Component sibling : message.getSiblings()) {
            UUID found = confirmedVictim(sibling, localPlayer);
            if (found != null) return found;
        }
        return null;
    }

    private static UUID entityId(Component component) {
        return component.getStyle().getHoverEvent() instanceof HoverEvent.ShowEntity entity ? entity.entity().uuid : null;
    }

    private void confirm(UUID victim) {
        Death death = deaths.get(victim);
        Confirmation confirmation = confirmations.get(victim);
        long now = System.nanoTime();
        if (death == null || confirmation == null || death.entityId() != confirmation.entityId()
                || now - death.time() > CONFIRM_WINDOW || now - confirmation.time() > CONFIRM_WINDOW) return;
        Emission previous = emitted.get(victim);
        if (previous == null || !previous.sameEntity(death.entity())) {
            emitted.put(victim, new Emission(new WeakReference<>(death.entity()), now));
            trim(emitted);
            if (acceptsKillEffect(death.entity())) spawn(death.position(), death.yaw(), false, death.entity() instanceof Player);
            if (manager.getConfig().isRagdollEnabled() && death.entity() instanceof AbstractClientPlayer player
                    && !player.isInvisible()) {
                spawnRagdoll(player, death.position(), player.yBodyRot, false);
            }
        }
        deaths.remove(victim); confirmations.remove(victim);
    }

    private static <T> void trim(Map<UUID, T> map) {
        // Ограничение относится и к серверам с очень большим числом сущностей/сообщений.
        if (map.size() > 256) map.remove(map.keySet().iterator().next());
    }

    public boolean preview() {
        if (!ready()) return false;
        Minecraft client = Minecraft.getInstance();
        spawn(previewTarget(client), client.player.getYRot() + 180F, true, true);
        return true;
    }

    public boolean previewRagdoll() {
        if (!ready()) return false;
        Minecraft client = Minecraft.getInstance();
        spawnRagdoll(client.player, previewTarget(client), client.player.getYRot() + 180F, true);
        return true;
    }

    private static Vec3 previewTarget(Minecraft client) {
        Vec3 forward = client.player.getLookAngle();
        double length = Math.hypot(forward.x, forward.z);
        Vec3 direction = length > .01 ? new Vec3(forward.x / length, 0, forward.z / length) : new Vec3(0, 0, 1);
        Vec3 target = client.player.position().add(direction.scale(4.5));
        // Не переносим демонстрацию за стену.
        var sight = client.level.clip(new ClipContext(client.player.getEyePosition(), target.add(0, 1, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
        if (sight.getType() != HitResult.Type.MISS) target = sight.getLocation().subtract(direction.scale(.8));
        return target;
    }

    private void spawnRagdoll(AbstractClientPlayer player, Vec3 position, float yaw, boolean preview) {
        Minecraft client = Minecraft.getInstance();
        if (position.distanceToSqr(client.player.position()) > OrionRagdoll.RANGE_SQUARED) return;
        Vec3 impulse = preview ? Vec3.ZERO : player.getDeltaMovement();
        if (Math.hypot(impulse.x, impulse.z) < .06) impulse = position.subtract(client.player.position());
        double length = Math.hypot(impulse.x, impulse.z);
        Vec3 direction = length > .01 ? new Vec3(impulse.x / length, 0, impulse.z / length) : new Vec3(0, 0, 1);
        if (ragdolls.size() >= 8) ragdolls.removeFirst();
        ragdolls.add(new OrionRagdoll(client, player, position, direction, yaw, manager.getConfig().getRagdollHold(), preview));
    }

    public boolean replacesPlayerModel(AbstractClientPlayer player) {
        if (player.level() != level || !manager.getConfig().isRagdollEnabled() || !player.isDeadOrDying()) return false;
        for (OrionRagdoll body : ragdolls) if (body.replaces(player)) return true;
        return false;
    }

    private void spawn(Vec3 position, float yaw, boolean preview, boolean playerVictim) {
        Minecraft client = Minecraft.getInstance();
        var config = manager.getConfig();
        Definition definition = registry.get(config.getKillEffectsStyle());
        if (definition == null) return;
        double distance = config.getKillEffectsDistance();
        if (position.distanceToSqr(client.player.position()) > distance * distance) return;
        var ground = client.level.clip(new ClipContext(position.add(0, .5, 0), position.add(0, -6, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
        if (!playerVictim && ground.getType() != HitResult.Type.MISS) position = ground.getLocation().add(0, .025, 0);
        if (playerVictim) position = position.add(0, config.getKillEffectsPlayerLift(), 0);
        Vec3 viewer = client.gameRenderer.mainCamera().position();
        yaw = (float) Math.toDegrees(Math.atan2(viewer.x - position.x, position.z - viewer.z));
        while (active.size() >= config.getKillEffectsLimit()) OrionKillAudio.stop(active.removeFirst().sound());
        float size = config.getKillEffectsSize();
        SoundInstance sound = OrionKillAudio.play(config.getKillEffectsStyle(), position,
                config.getKillEffectsSpeed(), config.getKillEffectsVolume() / 100F);
        active.add(new ActiveEffect(definition, config.getKillEffectsStyle(), position, yaw, size,
                config.getKillEffectsSpeed(), config.getKillEffectsQuality(), config.getKillEffectsParticles(), System.nanoTime(), preview, sound,
                new AABB(position.x - 5 * size, position.y - size, position.z - 5 * size,
                        position.x + 5 * size, position.y + 10 * size, position.z + 5 * size)));
    }

    private void render(LevelRenderContext context) {
        update();
        if (active.isEmpty() && ragdolls.isEmpty()) return;
        var camera = context.levelState().cameraRenderState;
        if (camera == null || !camera.initialized || camera.pos == null) return;
        long now = System.nanoTime();
        for (OrionRagdoll body : ragdolls) body.render(context, camera.pos, now);
        double distance = manager.getConfig().getKillEffectsDistance();
        for (ActiveEffect effect : active) {
            if (effect.position().distanceToSqr(camera.pos) > distance * distance
                    || camera.cullFrustum != null && !camera.cullFrustum.isVisible(effect.bounds())) continue;
            if (!effect.preview() && !manager.getConfig().isKillEffectsEnabled()) continue;
            float age = effect.age(now);
            if (age >= effect.definition().duration()) continue;
            Vec3 relative = effect.position().subtract(camera.pos);
            PoseStack stack = context.poseStack();
            stack.pushPose();
            stack.translate(relative.x, relative.y, relative.z);
            stack.rotate(Axis.YP.rotationDegrees(-effect.yaw()));
            stack.scale(effect.size(), effect.size(), effect.size());
            // Данные захвачены неизменяемым снимком, callback не читает мир/игроков.
            int quality = Math.min(effect.quality(), effect.position().distanceToSqr(camera.pos) > 2304 ? 0 : effect.quality());
            boolean translucentModel = effect.type() == 0 || effect.type() == 5 || effect.type() == 8;
            context.submitNodeCollector().submitCustomGeometry(stack,
                    translucentModel ? OrionEspRenderTypes.killEffects() : OrionEspRenderTypes.killModels(), (pose, vertices) -> {
                PoseStack model = new PoseStack();
                model.last().pose().set(pose.pose());
                effect.definition().renderer().render(model, OrionKillMaterial.textured(vertices, effect.type()),
                        age, quality, effect.particles(), effect.created());
            });
            stack.popPose();
        }
    }

    @FunctionalInterface
    public interface ModelRenderer {
        void render(PoseStack pose, VertexConsumer vertices, float age, int quality, float particles, long seed);
    }
    public record Definition(float duration, ModelRenderer renderer) { }
    private record Death(Vec3 position, float yaw, int entityId, LivingEntity entity, long time) { }
    private record Emission(WeakReference<LivingEntity> entity, long time) {
        boolean sameEntity(LivingEntity other) { return other != null && entity.get() == other; }
    }
    private record Confirmation(int entityId, long time) { }
    record DamageEvidence(int entityId, long tick, long time) {
        boolean matches(int deadEntityId, long currentTick, long now) {
            return entityId == deadEntityId && currentTick >= tick && currentTick - tick <= 2
                    && now >= time && now - time <= DAMAGE_WINDOW;
        }
    }
    private record ActiveEffect(Definition definition, int type, Vec3 position, float yaw, float size, float speed,
                                int quality, float particles, long created, boolean preview, SoundInstance sound, AABB bounds) {
        float age(long now) { return Math.max(0F, (now - created) / 1_000_000_000F * speed); }
    }
}
