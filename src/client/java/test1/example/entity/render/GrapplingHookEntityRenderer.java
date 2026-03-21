package test1.example.entity.render;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import test1.example.ModGrapplingHookTiers;
import test1.example.entity.GrapplingHookEntity;

/**
 * 勾爪实体渲染器。
 *
 * 目标效果：在“玩家手部位置”与“勾爪实体位置”之间绘制细长柱体，
 * 模拟泰拉瑞亚风格的链条/绳索延伸感。
 */
public class GrapplingHookEntityRenderer
        extends EntityRenderer<GrapplingHookEntity, GrapplingHookEntityRenderer.GrapplingHookRenderState> {

    /** 柱体半径（越小越细）。 */
    private static final float CABLE_RADIUS = 0.035F;

    public GrapplingHookEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public GrapplingHookRenderState createRenderState() {
        return new GrapplingHookRenderState();
    }

    @Override
    public void extractRenderState(GrapplingHookEntity entity, GrapplingHookRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);

        state.tier = entity.getTier();

        // lineStart: 玩家主手近似位置（用于让绳索从“手里”发出，而不是从眼睛）。
        if (entity.getOwner() instanceof Player player) {
            state.lineStart = calculateMainHandAnchor(player, partialTick);
        } else if (entity.getOwner() != null) {
            state.lineStart = entity.getOwner().getEyePosition(partialTick);
        } else {
            state.lineStart = new Vec3(state.x, state.y, state.z);
        }

        // lineEnd: 勾爪实体当前世界坐标。
        state.lineEnd = new Vec3(state.x, state.y, state.z);
    }

    /**
     * 计算玩家主手的近似锚点。
     *
     * 说明：
     * 1. 以玩家眼睛位置稍微下移作为基点（近似肩部/手部高度）。
     * 2. 根据视角偏航角计算水平面的 right 向量与 forward 向量。
     * 3. 按主手方向沿 right 偏移，再沿 forward 轻微前推，得到“手部发射点”。
     */
    private Vec3 calculateMainHandAnchor(Player player, float partialTick) {
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 base = eye.add(0.0D, -0.25D, 0.0D);

        float yawRad = player.getViewYRot(partialTick) * ((float) Math.PI / 180.0F);
        Vec3 right = new Vec3(Math.cos(yawRad), 0.0D, Math.sin(yawRad));
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));

        double side = player.getMainArm() == HumanoidArm.RIGHT ? -0.28D : 0.28D;
        return base.add(right.scale(side)).add(forward.scale(0.18D));
    }

    @Override
    public void submit(GrapplingHookRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            net.minecraft.client.renderer.state.CameraRenderState cameraRenderState) {
        if (state.lineStart == null || state.lineEnd == null) {
            return;
        }

        // 线段方向：从玩家手部锚点指向勾爪实体。
        Vec3 startWorld = state.lineStart;
        Vec3 endWorld = state.lineEnd;
        Vec3 startToEndWorld = endWorld.subtract(startWorld);
        double dist = startToEndWorld.length();
        if (dist < 1.0E-6D) {
            return;
        }

        Vec3 direction = startToEndWorld.scale(1.0D / dist);

        // 当前提交阶段的局部原点在“实体位置”，
        // 因此把手部锚点换算到以实体为原点的局部坐标中。
        Vec3 startLocal = startWorld.subtract(endWorld);

        Identifier texture = resolveTextureByTier(state.tier);
        RenderType renderType = RenderTypes.entityCutoutNoCull(texture);

        // 不走任何默认模型绘制，只提交自定义几何体，避免出现默认调试样式重叠。
        collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> {
            drawRectangularCable(vertexConsumer, pose, startLocal, direction, (float) dist, state.lightCoords);
        });
    }

    /**
     * 根据勾爪材质选择柱体贴图。
     */
    private Identifier resolveTextureByTier(ModGrapplingHookTiers tier) {
        if (tier == null) {
            return Identifier.parse("minecraft:textures/block/cobblestone.png");
        }

        return switch (tier) {
            case STONE -> Identifier.parse("minecraft:textures/block/cobblestone.png");
            case IRON -> Identifier.parse("minecraft:textures/block/iron_block.png");
            case GOLD -> Identifier.parse("minecraft:textures/block/gold_block.png");
            case DIAMOND -> Identifier.parse("minecraft:textures/block/diamond_block.png");
            case NETHERITE -> Identifier.parse("minecraft:textures/block/netherite_block.png");
        };
    }

    /**
     * 手动构建长方形柱体顶点。
     *
     * 向量与旋转解释：
     * 1. cableDir = 归一化(终点 - 起点)
     *    表示柱体延展方向。
     * 2. 选一个不平行的参考向量 referenceUp（通常是世界上方向）。
     * 3. right = normalize(cableDir x referenceUp)
     *    得到与延展方向垂直的“右方向”。
     * 4. up = normalize(right x cableDir)
     *    得到与 right/cableDir 都正交的“上方向”。
     * 5. 用 right/up 在起点和终点各偏移出 4 个角点，拼成矩形柱体 4 个侧面。
     *
    * 这样就完成了“任意两点之间柱体”的方向对齐与长度伸展。
    *
    * 可以把这个过程理解为：
    * 我们不是直接旋转模型，而是先在数学上构造一个局部坐标系
    * (dir/right/up)，然后在该坐标系里生成顶点，最终自然对齐到目标方向。
     */
    private void drawRectangularCable(VertexConsumer vertexConsumer, PoseStack.Pose pose,
            Vec3 startLocal, Vec3 directionWorld, float distance, int light) {
        Vector3f anchor = new Vector3f((float) startLocal.x, (float) startLocal.y, (float) startLocal.z);

        // 利用 atan2 计算指向勾爪方向的偏航角和俯仰角：
        // yaw   = atan2(x, z)               -> 绕 Y 轴旋转（水平朝向）
        // pitch = -atan2(y, sqrt(x^2+z^2))  -> 绕 X 轴旋转（抬头/低头）
        // 然后把“本地 +Z 方向的柱体”旋转到真实连线方向。
        float yaw = (float) Mth.atan2(directionWorld.x, directionWorld.z);
        float horizontal = Mth.sqrt((float) (directionWorld.x * directionWorld.x + directionWorld.z * directionWorld.z));
        float pitch = (float) -Mth.atan2(directionWorld.y, horizontal);

        Quaternionf rotation = new Quaternionf().rotateY(yaw).rotateX(pitch);

        // 在本地坐标中构造“沿 Z 轴拉伸”的细长柱体：z=0 到 z=distance。
        Vector3f s1 = transform(new Vector3f(CABLE_RADIUS, CABLE_RADIUS, 0.0F), rotation, anchor);
        Vector3f s2 = transform(new Vector3f(-CABLE_RADIUS, CABLE_RADIUS, 0.0F), rotation, anchor);
        Vector3f s3 = transform(new Vector3f(-CABLE_RADIUS, -CABLE_RADIUS, 0.0F), rotation, anchor);
        Vector3f s4 = transform(new Vector3f(CABLE_RADIUS, -CABLE_RADIUS, 0.0F), rotation, anchor);

        Vector3f e1 = transform(new Vector3f(CABLE_RADIUS, CABLE_RADIUS, distance), rotation, anchor);
        Vector3f e2 = transform(new Vector3f(-CABLE_RADIUS, CABLE_RADIUS, distance), rotation, anchor);
        Vector3f e3 = transform(new Vector3f(-CABLE_RADIUS, -CABLE_RADIUS, distance), rotation, anchor);
        Vector3f e4 = transform(new Vector3f(CABLE_RADIUS, -CABLE_RADIUS, distance), rotation, anchor);

        Vector3f normalUp = rotateNormal(new Vector3f(0.0F, 1.0F, 0.0F), rotation);
        Vector3f normalLeft = rotateNormal(new Vector3f(-1.0F, 0.0F, 0.0F), rotation);
        Vector3f normalDown = rotateNormal(new Vector3f(0.0F, -1.0F, 0.0F), rotation);
        Vector3f normalRight = rotateNormal(new Vector3f(1.0F, 0.0F, 0.0F), rotation);

        // V 方向按照距离拉伸，用于形成“贴图沿线重复/延展”的观感。
        float uvV = Math.max(1.0F, distance * 3.0F);

        emitQuad(vertexConsumer, pose, s1, s2, e2, e1, normalUp, light, uvV);
        emitQuad(vertexConsumer, pose, s2, s3, e3, e2, normalLeft, light, uvV);
        emitQuad(vertexConsumer, pose, s3, s4, e4, e3, normalDown, light, uvV);
        emitQuad(vertexConsumer, pose, s4, s1, e1, e4, normalRight, light, uvV);
    }

    private Vector3f transform(Vector3f local, Quaternionf rotation, Vector3f anchor) {
        return local.rotate(rotation, new Vector3f()).add(anchor);
    }

    private Vector3f rotateNormal(Vector3f normal, Quaternionf rotation) {
        return normal.rotate(rotation, new Vector3f()).normalize();
    }

    private void emitQuad(VertexConsumer vertexConsumer, PoseStack.Pose pose,
            Vector3f a, Vector3f b, Vector3f c, Vector3f d,
            Vector3f normal, int light, float uvV) {
        putVertex(vertexConsumer, pose, a, 0.0F, 0.0F, normal, light);
        putVertex(vertexConsumer, pose, b, 1.0F, 0.0F, normal, light);
        putVertex(vertexConsumer, pose, c, 1.0F, uvV, normal, light);
        putVertex(vertexConsumer, pose, d, 0.0F, uvV, normal, light);
    }

    private void putVertex(VertexConsumer vertexConsumer, PoseStack.Pose pose,
            Vector3f pos, float u, float v, Vector3f normal, int light) {
        vertexConsumer
                .addVertex(pose, pos.x(), pos.y(), pos.z())
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, normal.x(), normal.y(), normal.z());
    }

    /**
     * 渲染状态：保存 submit 阶段所需的线段信息。
     */
    public static class GrapplingHookRenderState extends EntityRenderState {
        public Vec3 lineStart;
        public Vec3 lineEnd;
        public ModGrapplingHookTiers tier;
    }
}
