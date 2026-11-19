package ru.orion.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.config.OrionConfig.IndicatorOptions;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Vanilla-style pixel hearts above players and mobs, rendered the same way as the
 * HealthIndicators mod (https://github.com/AdyTech99/HealthIndicators): text render type
 * (crisp vanilla colors, depth-tested), 0.025 world-per-pixel name-tag scale, 8px heart step,
 * container under every slot with the fill pushed toward the camera by a distance-scaled
 * z offset, rows of ten hearts stacking upwards, absorption hearts appended in gold.
 */
public final class OrionHealthTags {
    private static final WeakHashMap<Entity, Boolean> ATTACKED = new WeakHashMap<>();
    private static WeakReference<ClientLevel> attackLevel = new WeakReference<>(null);
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float SCALE = 0.025F;
    private static final float HEART_SIZE = 9.0F;
    private static final int HEART_STEP = 8;
    private static final int HEARTS_PER_ROW = 10;
    private static final float NAME_LINE_LIFT = 9.0F * 1.15F * SCALE;

    private static final Identifier HEART_CONTAINER = Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/container.png");
    private static final Identifier HEART_FULL = Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/full.png");
    private static final Identifier HEART_HALF = Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/half.png");
    private static final Identifier HEART_ABSORBING_FULL = Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/absorbing_full.png");
    private static final Identifier HEART_ABSORBING_HALF = Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/absorbing_half.png");

    private final OrionConfigManager configManager;

    public OrionHealthTags(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
    }

    private void render(LevelRenderContext context) {
        var config = configManager.getConfig();
        if (!config.isHealthTagsEnabled()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }

        CameraRenderState cameraState = context.levelState().cameraRenderState;
        if (cameraState == null || !cameraState.initialized || cameraState.orientation == null) {
            return;
        }

        Vec3 cameraPos = cameraState.pos;
        float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        IndicatorOptions options = config.getHealthIndicatorOptions();
        var labels = overheadLabels(context, client, cameraState, tickDelta);
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !visible(client, living, options)
                    || options.changedOnly && living.getHealth() >= living.getMaxHealth()) continue;

            Vec3 position = living.getPosition(tickDelta);
            double distanceSq = cameraPos.distanceToSqr(position);
            if (distanceSq > options.distance * options.distance) {
                continue;
            }

            Vec3 anchor = labels.anchor(living, position).add(labels.up().scale(options.offset + HEART_SIZE * SCALE * options.scale)).subtract(cameraPos);
            if (options.mode == 0) {
                submitHearts(context, living, anchor, labels.up(), cameraState, distanceSq, options);
            } else {
                int health = options.mode == 1 ? Mth.ceil(living.getHealth())
                        : Math.round(100 * living.getHealth() / Math.max(1F, living.getMaxHealth()));
                String value = health + (options.mode == 2 ? "%" : "");
                int absorption = Mth.ceil(living.getAbsorptionAmount());
                if (absorption > 0) value += " +" + absorption;
                submitNumber(context, client.font, value, options.getValueArgb(), anchor.x, anchor.y, anchor.z, cameraState, options.scale);
            }
        }
    }

    public static void onAttack(Entity entity) {
        var config = OrionClient.getConfigOrNull();
        if (config == null || !(config.getHealthIndicatorOptions().attackedOnly || config.getArmorIndicatorOptions().attackedOnly)) return;
        var level = Minecraft.getInstance().level;
        if (attackLevel.get() != level) {
            ATTACKED.clear();
            attackLevel = new WeakReference<>(level);
        }
        ATTACKED.put(entity, Boolean.TRUE);
    }

    static boolean visible(Minecraft client, LivingEntity living, IndicatorOptions options) {
        if (!living.isAlive() || living instanceof ArmorStand || living.isInvisibleTo(client.player)) return false;
        if (living == client.player) {
            if (!options.self || client.options.getCameraType().isFirstPerson()) return false;
        } else if (living instanceof Player ? !options.players : living instanceof Enemy ? !options.hostile : !options.passive) {
            return false;
        }
        if (options.targetOnly && (!(client.hitResult instanceof EntityHitResult hit) || hit.getEntity() != living)) return false;
        if (options.attackedOnly && (attackLevel.get() != client.level || !ATTACKED.containsKey(living))) return false;
        if (options.hidden.isBlank()) return true;
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).toString();
        for (String hidden : options.hidden.split("[,;\\s]+")) {
            if (hidden.equalsIgnoreCase(entityId) || hidden.equalsIgnoreCase(living.getScoreboardName())) return false;
        }
        return true;
    }

    static void submitNumber(LevelRenderContext context, Font font, String value, int color, double x, double y, double z,
                             CameraRenderState camera, float scale) {
        PoseStack stack = context.poseStack();
        stack.pushPose();
        stack.translate(x, y, z);
        ru.orion.client.render.GleamPose.rotate(stack, camera.orientation);
        stack.scale(SCALE * scale, -SCALE * scale, SCALE * scale);
        var text = Component.literal(value).getVisualOrderText();
        context.submitNodeCollector().submitText(stack, -font.width(text) / 2F, 0, text, true,
                Font.DisplayMode.NORMAL, FULL_BRIGHT, color, 0, 0);
        stack.popPose();
    }

    private void submitHearts(LevelRenderContext context, LivingEntity living,
                              Vec3 anchor, Vec3 up, CameraRenderState cameraState, double distanceSq,
                              IndicatorOptions options) {
        int healthRed = Mth.ceil(living.getHealth());
        int maxHealth = Mth.ceil(living.getMaxHealth());
        int healthYellow = Mth.ceil(living.getAbsorptionAmount());

        int heartsRed = Mth.ceil(healthRed / 2.0F);
        boolean lastRedHalf = (healthRed & 1) == 1;
        int heartsNormal = Math.max(1, Mth.ceil(maxHealth / 2.0F));
        int heartsYellow = Mth.ceil(healthYellow / 2.0F);
        boolean lastYellowHalf = (healthYellow & 1) == 1;
        int heartsTotal = heartsNormal + heartsYellow;

        int pixelsTotal = Math.min(heartsTotal, HEARTS_PER_ROW) * HEART_STEP + 1;
        float maxX = pixelsTotal / 2.0F;
        double heartDensity = 50.0D - Math.max(4.0D - Math.ceil((double) heartsTotal / HEARTS_PER_ROW), -3.0D) * 5.0D;

        // Same trick as HealthIndicators: the fill goes toward the camera and the container away
        // from it, with the gap growing with distance so depth precision never lets the dark
        // container swallow the red fill (containers and fills are separate RenderType batches
        // whose draw order is not the submit order).
        float zOffset = 0.01F * (1.0F + (float) Math.sqrt(distanceSq));

        PoseStack poseStack = context.poseStack();
        double rowLift = 0.0D;
        for (int heart = 0; heart < heartsTotal; heart++) {
            if (heart % HEARTS_PER_ROW == 0) {
                rowLift = heart / heartDensity;
            }

            Identifier fill;
            if (heart < heartsRed) {
                fill = (heart == heartsRed - 1 && lastRedHalf) ? HEART_HALF : HEART_FULL;
            } else if (heart < heartsNormal) {
                fill = null;
            } else {
                fill = (heart == heartsTotal - 1 && lastYellowHalf) ? HEART_ABSORBING_HALF : HEART_ABSORBING_FULL;
            }

            float x = maxX - (heart % HEARTS_PER_ROW) * HEART_STEP;

            poseStack.pushPose();
            Vec3 rowPosition = anchor.add(up.scale(rowLift * options.scale));
            poseStack.translate(rowPosition.x, rowPosition.y, rowPosition.z);
            ru.orion.client.render.GleamPose.rotate(poseStack, cameraState.orientation);
            poseStack.scale(-SCALE * options.scale, SCALE * options.scale, SCALE * options.scale);

            submitHeart(context, poseStack, HEART_CONTAINER, x, -zOffset);
            if (fill != null) {
                submitHeart(context, poseStack, fill, x, zOffset);
            }
            poseStack.popPose();
        }
    }

    static double rowStackHeight(LivingEntity living, IndicatorOptions options) {
        if (options.mode != 0) return HEART_SIZE * SCALE * options.scale;
        int count = Math.max(1, Mth.ceil(Mth.ceil(living.getMaxHealth()) / 2F))
                + Mth.ceil(Mth.ceil(living.getAbsorptionAmount()) / 2F);
        double density = 50D - Math.max(4D - Math.ceil((double) count / HEARTS_PER_ROW), -3D) * 5D;
        return (((count - 1) / HEARTS_PER_ROW * HEARTS_PER_ROW) / density + HEART_SIZE * SCALE) * options.scale;
    }

    static OverheadLabels overheadLabels(LevelRenderContext context, Minecraft client, CameraRenderState camera, float partial) {
        Vector3f vertical = camera.orientation.transform(new Vector3f(0, 1, 0));
        Vec3 up = new Vec3(vertical.x, vertical.y, vertical.z);
        List<Label> labels = new ArrayList<>();
        for (var state : context.levelState().entityRenderStates) {
            if (state instanceof TextDisplayEntityRenderState display && display.cachedInfo != null
                    && !display.cachedInfo.lines().isEmpty() && display.renderState != null) {
                var transform = display.renderState.transformation().get(display.interpolationProgress);
                var matrix = transform.getMatrix();
                float displayScale = matrix.transformDirection(new Vector3f(0, 1, 0)).length();
                double height = display.cachedInfo.lines().size() * 10D * SCALE * displayScale;
                labels.add(new Label(new Vec3(state.x + matrix.m30(), state.y + matrix.m31(), state.z + matrix.m32()),
                        height, -1, true, state.entityType));
                continue;
            }
            // For players on 1.21.11 the below-name score lives on AvatarRenderState.
            Component score;
            //? if >=26.1 {
            score = state.scoreText;
            //?} else {
            /*score = state instanceof AvatarRenderState avatar ? avatar.scoreText : null;
            *///?}
            if (state.nameTagAttachment == null || state.nameTag == null && score == null) continue;
            int id = state instanceof AvatarRenderState avatar ? avatar.id : -1;
            if (id == client.player.getId()) continue;
            double lift = state.nameTag != null && score != null ? NAME_LINE_LIFT : 0;
            if (state instanceof AvatarRenderState avatar && avatar.showExtraEars) lift += 10 * SCALE;
            Vec3 attachment = state.nameTagAttachment.add(0, .5F + lift, 0);
            if (state.passengerOffset != null) attachment = attachment.add(state.passengerOffset);
            labels.add(new Label(new Vec3(state.x, state.y, state.z).add(attachment), .07D, id,
                    state instanceof ArmorStandRenderState, state.entityType));
        }
        var config = OrionClient.getConfigOrNull();
        if (config != null && config.isSelfTagEnabled()) {
            var self = client.player;
            Vec3 position = self.getPosition(partial);
            Vec3 attachment = self.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, self.getYRot(partial));
            if (attachment != null) {
                double scoreLift = self.belowNameDisplay() != null && position.distanceToSqr(camera.pos) < 100D ? NAME_LINE_LIFT : 0;
                labels.add(new Label(position.add(attachment).add(0, .5F + scoreLift, 0), .07D, self.getId(), false, self.getType()));
            }
        }
        return new OverheadLabels(up, labels);
    }

    record OverheadLabels(Vec3 up, List<Label> labels) {
        Vec3 anchor(LivingEntity living, Vec3 position) {
            Vec3 baseline = position.add(0, living.getBbHeight() + .46F, 0);
            double lift = 0;
            for (Label label : labels) {
                Vec3 delta = label.position().subtract(baseline);
                double flatDistance = delta.horizontalDistanceSqr();
                boolean own = living instanceof Player ? label.entityId() == living.getId()
                        : label.type() == living.getType() && flatDistance < .04;
                // Server holograms are independent entities. Only reserve labels in this entity's head column.
                boolean overhead = label.hologram() && flatDistance < .64 && delta.y > -.42 && delta.y < 3.2;
                if (own || overhead) lift = Math.max(lift, delta.dot(up) + label.extraHeight() + .12);
            }
            return baseline.add(up.scale(lift));
        }
    }

    private record Label(Vec3 position, double extraHeight, int entityId, boolean hologram, EntityType<?> type) { }

    private static void submitHeart(LevelRenderContext context, PoseStack poseStack, Identifier texture, float x, float z) {
        context.submitNodeCollector().submitCustomGeometry(
                poseStack,
                RenderTypes.text(texture),
                (pose, vertexConsumer) -> drawHeart(pose.pose(), vertexConsumer, x, z)
        );
    }

    private static void drawHeart(Matrix4f model, VertexConsumer vc, float x, float z) {
        vc.addVertex(model, x, -HEART_SIZE, z).setUv(0.0F, 1.0F).setLight(FULL_BRIGHT).setColor(1.0F, 1.0F, 1.0F, 1.0F);
        vc.addVertex(model, x - HEART_SIZE, -HEART_SIZE, z).setUv(1.0F, 1.0F).setLight(FULL_BRIGHT).setColor(1.0F, 1.0F, 1.0F, 1.0F);
        vc.addVertex(model, x - HEART_SIZE, 0.0F, z).setUv(1.0F, 0.0F).setLight(FULL_BRIGHT).setColor(1.0F, 1.0F, 1.0F, 1.0F);
        vc.addVertex(model, x, 0.0F, z).setUv(0.0F, 0.0F).setLight(FULL_BRIGHT).setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
