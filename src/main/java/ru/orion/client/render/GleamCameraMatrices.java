package ru.orion.client.render;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
public final class GleamCameraMatrices {
    private GleamCameraMatrices() {}
    //? if <26.1 {
    /*private static final Matrix4f worldProjection = new Matrix4f(), worldView = new Matrix4f();
    private static final org.joml.FrustumIntersection frustum = new org.joml.FrustumIntersection();
    public static void capture(Matrix4f view, Matrix4f projection) {
        worldProjection.set(projection);
        worldView.set(view);
        frustum.set(new Matrix4f(projection).mul(view));
    }
    *///?}
    public static Matrix4f projection(CameraRenderState camera) {
        //? if >=26.1 {
        return camera.projectionMatrix;
        //?} else {
        /*return worldProjection;
        *///?}
    }
    public static Matrix4f view(CameraRenderState camera) {
        //? if >=26.1 {
        return camera.viewRotationMatrix;
        //?} else {
        /*return worldView;
        *///?}
    }
    public static boolean visible(CameraRenderState camera, AABB bounds) {
        //? if >=26.1 {
        return camera.cullFrustum == null || camera.cullFrustum.isVisible(bounds);
        //?} else {
        /*return frustum.testAab((float)(bounds.minX-camera.pos.x), (float)(bounds.minY-camera.pos.y), (float)(bounds.minZ-camera.pos.z),
                     (float)(bounds.maxX-camera.pos.x), (float)(bounds.maxY-camera.pos.y), (float)(bounds.maxZ-camera.pos.z));
        *///?}
    }
    public static float fov(Camera camera) {
        //? if >=26.1 {
        return camera.getFov();
        //?} else {
        /*var client = Minecraft.getInstance();
        return ((ru.orion.client.mixin.GleamLegacyGameRendererAccessor)client.gameRenderer)
            .gleam$fov(camera, client.getDeltaTracker().getGameTimeDeltaPartialTick(false), true);
        *///?}
    }
}
