//? if <26.1 {
/*package ru.orion.client.basketball.engine;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.mixin.GleamLegacyGameRendererAccessor;
import ru.orion.client.render.GleamCameraMatrices;

// Настоящие части модели игрока: корректные UV, slim-руки и наружный слой, без растяжения скина.
public final class GleamBasketballHands {
    static final double ARM_LENGTH = 12D / 16D;
    private static final Vec3 RIGHT_SHOULDER = new Vec3(.52, -.66, -.08);
    private static final Vec3 LEFT_SHOULDER = new Vec3(-.52, -.66, -.08);
    private static final Vec3 REST_WRIST = new Vec3(.39, -.38, -.70);
    private static final Arms[] MODELS = new Arms[2];
    private record Arms(ModelPart right, ModelPart left) {}
    record Segment(Vec3 shoulder, Vec3 wrist) {}
    private GleamBasketballHands() {}

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            public Identifier getFabricId() { return Identifier.fromNamespaceAndPath("orion-client", "basketball_hands"); }
            public void onResourceManagerReload(ResourceManager resources) { MODELS[0] = MODELS[1] = null; }
        });
    }

    // То же смещение использует физическое удержание мяча: при броске не меняется точка появления.
    public static Vec3 heldOffset(float charge) {
        float t = Math.clamp(charge, 0F, 1F);
        return new Vec3(.08 - .04 * t, -.27 + .20 * t, .95 - .12 * t);
    }

    static Segment fixedArm(Vec3 shoulder, Vec3 target) {
        Vec3 delta = target.subtract(shoulder);
        double distance = delta.length();
        Vec3 direction = distance < 1E-6 ? new Vec3(0, 0, -1) : delta.scale(1D / distance);
        Vec3 wrist = distance > ARM_LENGTH ? shoulder.add(direction.scale(ARM_LENGTH)) : target;
        return new Segment(wrist.subtract(direction.scale(ARM_LENGTH)), wrist);
    }

    public static boolean render(float partial, PoseStack pose, SubmitNodeCollector collector, LocalPlayer player, int light) {
        var settings = OrionBasketballSettings.get();
        var client = Minecraft.getInstance();
        if (!settings.basket || !settings.basketHands || player == null || !OrionBasketballController.localHolding()
                || !client.options.getCameraType().isFirstPerson()) return false;
        boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
        Arms arms = models(client, slim);
        Identifier skin = player.getSkin().body().texturePath();
        arms.right.getChild("right_sleeve").visible = player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        arms.left.getChild("left_sleeve").visible = player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        if (OrionBasketballController.mode() == OrionBasketballController.Mode.HELD) {
            float charge = OrionBasketballController.charge(partial);
            Vec3 offset = heldOffset(charge);
            Vec3 center = new Vec3(offset.x, offset.y, -offset.z);
            double radius = OrionBasketballController.ball().radius;
            Vec3 right = center.add(radius * .86, -radius * .18, radius * .38);
            Vec3 left = center.add(-radius * .86, -radius * .18, radius * .38);
            arm(pose, collector, arms.right, slim, true, skin, light, RIGHT_SHOULDER, right);
            arm(pose, collector, arms.left, slim, false, skin, light, LEFT_SHOULDER, left);
            ball(pose, collector, light, center, radius);
        } else {
            Vec3 target = dribbleTarget(client, pose, partial);
            arm(pose, collector, arms.right, slim, true, skin, light, RIGHT_SHOULDER, target);
        }
        return true;
    }

    private static Arms models(Minecraft client, boolean slim) {
        int index = slim ? 1 : 0;
        if (MODELS[index] == null) {
            ModelPart root = client.getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER);
            ModelPart right = root.getChild("right_arm"), left = root.getChild("left_arm");
            right.setPos(0, 0, 0); left.setPos(0, 0, 0);
            MODELS[index] = new Arms(right, left);
        }
        return MODELS[index];
    }

    private static Vec3 dribbleTarget(Minecraft client, PoseStack pose, float partial) {
        var camera = GleamMinecraft.camera(client);
        var state = GleamMinecraft.levelState(client).cameraRenderState;
        var ball = OrionBasketballController.ball();
        Vec3 relative = new Vec3(Mth.lerp(partial, ball.prevX, ball.x),
                Mth.lerp(partial, ball.prevY, ball.y) + ball.radius + .06,
                Mth.lerp(partial, ball.prevZ, ball.z)).subtract(camera.position());
        // Перенос точки из мирового прохода в проход рук учитывает FOV спринта и покачивание камеры.
        Matrix4f world = new Matrix4f(GleamCameraMatrices.projection(state)).mul(GleamCameraMatrices.view(state));
        Vector4f clip = world.transform(new Vector4f((float)relative.x, (float)relative.y, (float)relative.z, 1F));
        if (clip.w <= .05F || !Float.isFinite(clip.w) || clip.y / clip.w < -1.25F) return REST_WRIST;
        float fov = ((GleamLegacyGameRendererAccessor)client.gameRenderer).gleam$fov(camera, partial, false);
        Matrix4f hands = client.gameRenderer.getProjectionMatrix(fov)
                .mul(RenderSystem.getModelViewStack()).mul(pose.last().pose());
        Vector4f local = hands.invert().transform(clip);
        if (Math.abs(local.w) < 1E-6F || !Float.isFinite(local.w)) return REST_WRIST;
        float x = local.x / local.w, y = local.y / local.w, z = local.z / local.w;
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return REST_WRIST;
        return new Vec3(Math.clamp(x, -.32F, .68F), Math.clamp(y, -.58F, .12F), Math.clamp(z, -1.10F, -.42F));
    }

    private static void arm(PoseStack pose, SubmitNodeCollector collector, ModelPart arm, boolean slim, boolean right,
                            Identifier skin, int light, Vec3 shoulder, Vec3 target) {
        Segment segment = fixedArm(shoulder, target);
        Vec3 direction = segment.wrist.subtract(segment.shoulder).normalize();
        pose.pushPose();
        pose.translate(segment.shoulder.x, segment.shoulder.y, segment.shoulder.z);
        pose.mulPose(new Quaternionf().rotationTo(0F, 1F, 0F, (float)direction.x, (float)direction.y, (float)direction.z));
        // Убираем асимметричный сдвиг куба vanilla; UV и длина 12 пикселей остаются исходными.
        double center = (slim ? .5D : 1D) / 16D;
        pose.translate(right ? center : -center, 2D / 16D, 0);
        collector.submitModelPart(arm, pose, RenderTypes.entityCutoutNoCull(skin), light, OverlayTexture.NO_OVERLAY, null);
        pose.popPose();
    }

    private static void ball(PoseStack pose, SubmitNodeCollector collector, int light, Vec3 center, double radius) {
        pose.pushPose();
        pose.translate(center.x, center.y, center.z);
        pose.mulPose(Axis.YP.rotationDegrees(28F)); pose.mulPose(Axis.XP.rotationDegrees(-16F));
        pose.scale((float)radius, (float)radius, (float)radius);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(OrionBasketballScene.helper118()),
                (transform, vertices) -> OrionBasketballMesh.VDVIWDOMTOCVRHWDIMTOCVRHWDIMTO(transform, vertices, light, -1));
        pose.popPose();
    }
}
*///?}
