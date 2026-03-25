package test1.example.entity;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import test1.example.ModGrapplingHookTiers;

/**
 * 勾爪实体。
 *
 * 在 Mojang 映射命名中，本类继承的是 Projectile；
 * 其角色等价于教程里常说的 ProjectileEntity（投射物实体基类）。
 *
 * 该实体负责维护勾爪飞行、命中、回收全过程。核心由状态机驱动：
 * 1. FLYING：发射后向前飞行。
 * 2. HOOKED：命中方块后固定在命中点。
 * 3. RETRACTING：超过最大距离或失效后，向玩家回收。
 */
public class GrapplingHookEntity extends Projectile {
    private static final EntityDataAccessor<Integer> DATA_TIER_INDEX = SynchedEntityData.defineId(
            GrapplingHookEntity.class,
            EntityDataSerializers.INT
    );

    private static final EntityDataAccessor<Integer> DATA_RANGE = SynchedEntityData.defineId(
            GrapplingHookEntity.class,
            EntityDataSerializers.INT
    );

    private static final EntityDataAccessor<Integer> DATA_SPEED = SynchedEntityData.defineId(
            GrapplingHookEntity.class,
            EntityDataSerializers.INT
    );

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(
            GrapplingHookEntity.class,
            EntityDataSerializers.INT
    );

    private static final ModGrapplingHookTiers[] TIER_VALUES = ModGrapplingHookTiers.values();

    /**
     * 状态机枚举：
     * FLYING（飞行中）, HOOKED（已钩住）, RETRACTING（回收中）。
     */
    public enum HookState {
        FLYING,
        HOOKED,
        RETRACTING
    }

    /** 当前状态，默认飞行中。 */
    private HookState state = HookState.FLYING;

    /** 勾爪最大飞行距离（由材质决定）。 */
    private int range;

    /** 勾爪飞行速度（由材质决定）。 */
    private double speed;

    /** 勾爪材质等级（用于渲染层决定柱体材质）。 */
    private ModGrapplingHookTiers tier = ModGrapplingHookTiers.STONE;

    /** 命中方块时记录锚点位置。 */
    private Vec3 hookedPos;

    /** 命中方块的法线方向（Direction），用于计算玩家最终定位点。 */
    private Direction hitDirection = Direction.UP;

    /** 兼容不同映射：尝试通过反射写入 velocityModified 标志。 */
    private static Field velocityModifiedField;
    private static boolean velocityModifiedFieldResolved = false;

    /** 当前 tick 是否应跳过默认 projectile 物理更新。 */
    private boolean skipDefaultTickThisFrame = false;

    public GrapplingHookEntity(EntityType<? extends GrapplingHookEntity> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * 发射构造：由玩家右键触发。
         *
         * 这里通过 setOwner(owner) 记录“发射者玩家”，
         * 后续距离判断、回收方向与渲染起点都会依赖该 owner。
     */
    public GrapplingHookEntity(EntityType<? extends GrapplingHookEntity> entityType, Level level, Player owner,
            ModGrapplingHookTiers tier) {
        super(entityType, level);
        this.setOwner(owner);
        this.setTierInternal(tier == null ? ModGrapplingHookTiers.STONE : tier);
        this.range = this.tier.getMaxDistance();
        this.speed = this.tier.getSpeed();

        // 同步 range 和 speed 到 SynchedEntityData，确保客户端接收到准确的数据。
        this.entityData.set(DATA_RANGE, this.range);
        // speed 以 100 倍整数存储（0.4 -> 40）。
        this.entityData.set(DATA_SPEED, (int) Math.round(this.speed * 100.0));

        // 根据玩家视角计算初始动量：
        // 1. 读取玩家当前视线单位向量（在 Mojang 映射中对应 getLookAngle，
        //    语义等价于部分教程中的 getRotationVector）。
        // 2. 用视线方向 * 材质速度，作为勾爪初始速度。
        // 3. 初始出生点放在眼睛前方 0.8 格，确保低速材质也能在首帧脱离玩家碰撞箱。
        Vec3 lookDir = owner.getLookAngle().normalize();
        Vec3 spawnPos = owner.getEyePosition().add(lookDir.scale(0.8D));
        Vec3 initialVelocity = lookDir.scale(this.speed);

        this.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        this.setDeltaMovement(initialVelocity);
        this.setRot(owner.getYRot(), owner.getXRot());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // 把材质索引同步到客户端，渲染器才能拿到正确 tier，而不是全部退化到默认材质。
        builder.define(DATA_TIER_INDEX, ModGrapplingHookTiers.STONE.ordinal());

        // 同步 range 和 speed 到客户端：否则客户端默认为 0，会导致"首帧判定超距离"。
        // speed 乘以 100 后存储为整数（0.4 -> 40）。
        builder.define(DATA_RANGE, 12);
        builder.define(DATA_SPEED, 40);

        // 同步状态机到客户端：渲染器和客户端行为都依赖准确的状态。
        // 0=FLYING, 1=HOOKED, 2=RETRACTING
        builder.define(DATA_STATE, HookState.FLYING.ordinal());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> pKey) {
        super.onSyncedDataUpdated(pKey);

        // 当 tier 索引改变时，客户端需要根据 tier 重新读取配置的 range 和 speed。
        // 这是因为 tier 本身是枚举，其 getMaxDistance()/getSpeed() 是固定的。
        if (pKey == DATA_TIER_INDEX) {
            int tierIndex = Mth.clamp(this.entityData.get(DATA_TIER_INDEX), 0, TIER_VALUES.length - 1);
            ModGrapplingHookTiers syncedTier = TIER_VALUES[tierIndex];
            this.range = syncedTier.getMaxDistance();
            this.speed = syncedTier.getSpeed();
        }

        // 直接同步的 range 字段。
        if (pKey == DATA_RANGE) {
            this.range = this.entityData.get(DATA_RANGE);
        }

        // 直接同步的 speed 字段（以整数形式存储，需要转换回 double）。
        if (pKey == DATA_SPEED) {
            int speedInt = this.entityData.get(DATA_SPEED);
            this.speed = (double) speedInt / 100.0;
        }

        // 同步状态：客户端渲染时需知道当前是飞行/钩住/回收状态。
        if (pKey == DATA_STATE) {
            int stateIndex = this.entityData.get(DATA_STATE);
            if (stateIndex >= 0 && stateIndex < HookState.values().length) {
                this.state = HookState.values()[stateIndex];
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("HookState", this.state.name());
        output.putInt("Range", this.range);
        output.putDouble("Speed", this.speed);
        output.putString("Tier", this.tier.name());
        if (this.hookedPos != null) {
            output.putDouble("HookX", this.hookedPos.x);
            output.putDouble("HookY", this.hookedPos.y);
            output.putDouble("HookZ", this.hookedPos.z);
        }
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);

        String stateName = input.getStringOr("HookState", HookState.FLYING.name());
        try {
            this.state = HookState.valueOf(stateName);
        } catch (IllegalArgumentException ignored) {
            this.state = HookState.FLYING;
        }

        String tierName = input.getStringOr("Tier", ModGrapplingHookTiers.STONE.name());
        try {
            this.setTierInternal(ModGrapplingHookTiers.valueOf(tierName));
        } catch (IllegalArgumentException ignored) {
            this.setTierInternal(ModGrapplingHookTiers.STONE);
        }

        this.range = input.getIntOr("Range", this.tier.getMaxDistance());
        this.speed = input.getDoubleOr("Speed", this.tier.getSpeed());

        // ValueInput 没有“字段是否存在”的 double 查询接口，
        // 这里使用默认值回填；若未写入则会保持在 (0,0,0)。
        double hx = input.getDoubleOr("HookX", 0.0D);
        double hy = input.getDoubleOr("HookY", 0.0D);
        double hz = input.getDoubleOr("HookZ", 0.0D);
        this.hookedPos = new Vec3(hx, hy, hz);
    }

    @Override
    public void tick() {
        this.skipDefaultTickThisFrame = false;

        // HOOKED 状态或本帧命中方块后，跳过 Projectile 默认物理，防止边缘弹开。
        if (this.state != HookState.HOOKED && !this.skipDefaultTickThisFrame) {
            super.tick();
        }

        // 勾爪飞行阶段禁用重力：保持近似“绝对直线”弹道，避免下坠影响射程测试。
        this.setNoGravity(true);

        Entity owner = this.getOwner();
        if (!(owner instanceof Player player) || !owner.isAlive()) {
            // 失去合法 owner 时，直接回收。
            this.setStateSync(HookState.RETRACTING);
        }

        // 第三步：Shift 键取消与释放逻辑
        // 检测 Shift 键状态：玩家按下 Shift 时销毁勾爪，解除牵引。
        // 这里选择 isShiftKeyDown() 而不是自定义按键，是因为 Shift 是 Minecraft 的标准控制，
        // 玩家对其有统一预期；且在客户端也容易判定，同步问题少。
        if (owner instanceof Player playerOwner && playerOwner.isShiftKeyDown()) {
            playerOwner.fallDistance = 0.0F;  // 重置跌落距离
            playerOwner.setNoGravity(false);  // 恢复重力，解除牵引状态
            playerOwner.setDeltaMovement(Vec3.ZERO); // 安全释放：清空残余动量，避免 AABB 挤出弹飞
            markVelocityModified(playerOwner);
            this.discard();  // 清除勾爪实体，释放玩家
            return;  // 提前返回，避免继续执行后续状态机
        }

        // 距离判定和状态切换仅在服务端执行。
        // 客户端的状态由服务端同步而来，客户端不应独立做状态判定，避免客户端/服务端状态不一致。
        if (!this.level().isClientSide() && this.state == HookState.FLYING && owner != null) {
            // 射程逻辑：仅在飞行态检查"实体当前位置 -> owner 当前位置"的直线距离。
            // 只有当距离严格大于 range 时，才切到 RETRACTING，避免过早回收导致"射不远"。
            if (this.distanceToSqr(owner) > (double) (this.range * this.range)) {
                System.out.println("[GrapplingHook] Distance Exceeded: distance=" + Math.sqrt(this.distanceToSqr(owner))
                        + ", range=" + this.range + ", owner=" + owner.getName().getString());
                this.setStateSync(HookState.RETRACTING);
            }
        }

        switch (this.state) {
            case FLYING -> tickFlying(owner);
            case HOOKED -> tickHooked(owner);
            case RETRACTING -> tickRetracting(owner);
            default -> {
                // 防御性兜底。
                this.setStateSync(HookState.RETRACTING);
            }
        }
    }

    /**
     * FLYING：
     * 1. 按速度向前推进。
     * 2. 若碰到方块，切到 HOOKED。
     * 3. 若超过 range，切到 RETRACTING。
     */
    private void tickFlying(Entity owner) {
        // 防止首帧因玩家移动造成“看似撞到自己”的问题：
        // 1. canHitEntity 已排除了 owner；
        // 2. onHitEntity 里再次做 owner 兜底忽略；
        // 3. 这里使用投射物“移动路径碰撞检测”而不是只看终点坐标。
        // 注：当前 1.21.11 映射公开的是 getHitResultOnMoveVector，语义等同于按移动向量做路径检测。
        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hitResult.getType() != HitResult.Type.MISS) {
            this.onHit(hitResult);
            // onHit 里可能已切到 HOOKED/RETRACTING，本 tick 不再继续移动。
            if (this.state != HookState.FLYING) {
                return;
            }
        }

        // 飞行阶段锁定速度大小，抵消潜在阻力带来的逐帧衰减。
        Vec3 currentVelocity = this.getDeltaMovement();
        if (currentVelocity.lengthSqr() > 1.0E-8D) {
            this.setDeltaMovement(currentVelocity.normalize().scale(this.speed));
        }

        // 使用实体移动管线推进位置；碰撞由上面的“移动路径检测”先行判定。
        Vec3 velocity = this.getDeltaMovement();
        this.move(MoverType.SELF, velocity);
    }

    /**
     * HOOKED：
     * 勾爪固定在命中点，等待后续拉拽逻辑。
     *
     * 当前阶段先把速度清零并保持在 hookedPos，
     * 这样状态机语义稳定，后续可直接在这里扩展“拉玩家”逻辑。
     */
    private void tickHooked(Entity owner) {
        // 1. 基础锚点维护（保持勾爪实体不动）
        this.noPhysics = true;
        this.setDeltaMovement(Vec3.ZERO);
        if (this.hookedPos != null && this.hitDirection != null) {
            Vec3 hookVisualPos = this.hookedPos.add(new Vec3(
                this.hitDirection.getStepX(), this.hitDirection.getStepY(), this.hitDirection.getStepZ()
            ).scale(0.05D));
            this.setPos(hookVisualPos.x, hookVisualPos.y, hookVisualPos.z);
            markVelocityModified(this);
        }

        if (!(owner instanceof Player player) || this.level().isClientSide()) {
            return;
        }

        // 2. 多勾爪权威判定
        List<GrapplingHookEntity> activeHooks = getActiveHooksFor(player);
        if (activeHooks.isEmpty() || activeHooks.get(0) != this) {
            return;
        }

        // 3. 计算唯一目标：世界坐标质心
        Vec3 worldCentroidSum = Vec3.ZERO;
        for (GrapplingHookEntity hook : activeHooks) {
            worldCentroidSum = worldCentroidSum.add(hook.getPullGoal());
        }
        Vec3 targetCentroid = worldCentroidSum.scale(1.0D / activeHooks.size());

        // 计算玩家眼睛到质心的距离（我们以头部为平衡参考点）
        Vec3 toTarget = targetCentroid.subtract(player.getEyePosition());
        double distance = toTarget.length();
        boolean isBlocked = player.horizontalCollision || player.verticalCollision;

        // 4. 强化停止逻辑（Deadzone & Friction）
        // 速度越高，死区越大，避免高速靠近目标时反复横跳。
        double speedScale = Mth.clamp(this.speed / 0.8D, 1.0D, 2.5D);
        double stopThresholdBase = activeHooks.size() > 1 ? 0.35D : 0.25D;
        double stopThreshold = stopThresholdBase + 0.05D * (speedScale - 1.0D);
        double blockedThreshold = 0.7D + 0.10D * (speedScale - 1.0D);

        if (distance < stopThreshold || (isBlocked && distance < blockedThreshold)) {
            // 强行熄火：彻底清空动量
            player.setDeltaMovement(Vec3.ZERO);
            player.setNoGravity(true);
            player.fallDistance = 0.0F;

            // 微调补丁：如果在死区内仍有微小位移，直接重置位置（防止物理引擎抖动）
            if (distance > 0.05D && distance < stopThreshold) {
                // 仅在服务器做微调，客户端会通过移动数据包同步
                Vec3 currentPos = player.position();
                // 保持脚部坐标正确：目标脚部 = 质心 - 眼睛高度偏移
                player.setPos(currentPos.x, currentPos.y, currentPos.z);
            }

            markVelocityModified(player);
            return;
        }

        // 5. 空间坐标移动逻辑（强化阻尼）
        // 按速度自适应阻尼范围和衰减强度，自动适配更高档位勾爪。
        double dampingRange = 1.5D + 0.5D * (speedScale - 1.0D);
        double baseSpeed = this.speed;
        double finalSpeed;

        if (distance < dampingRange) {
            // 幂次阻尼曲线：速度越高，幂次越大，近距离减速越激进。
            double ratio = distance / dampingRange;
            double dampingPower = 2.0D + 0.8D * (speedScale - 1.0D);
            double dampingFactor = Math.max(0.08D, Math.pow(ratio, dampingPower));
            finalSpeed = baseSpeed * dampingFactor;
        } else {
            finalSpeed = baseSpeed;
        }

        // 限制最大速度，防止多勾爪叠加产生的意外初速度。
        // 允许随档位略增长，但上限控制在稳定区间。
        double maxPullSpeed = 1.2D + 0.15D * (speedScale - 1.0D);
        finalSpeed = Math.min(finalSpeed, maxPullSpeed);

        Vec3 movement = toTarget.normalize().scale(finalSpeed);

        // 6. 执行位移更新
        player.setDeltaMovement(movement);
        player.setNoGravity(true);
        player.fallDistance = 0.0F;
        markVelocityModified(player);
    }

    public Vec3 getPullGoal() {
        if (this.hookedPos == null || this.hitDirection == null) {
            return this.position();
        }

        double safetyOffset = 0.5D;
        return this.hookedPos.add(new Vec3(
            this.hitDirection.getStepX(),
            this.hitDirection.getStepY(),
            this.hitDirection.getStepZ()
        ).scale(safetyOffset));
    }

    public static List<GrapplingHookEntity> getActiveHooksFor(Player player) {
        List<GrapplingHookEntity> hooks = player.level().getEntitiesOfClass(
            GrapplingHookEntity.class,
            player.getBoundingBox().inflate(64.0D),
            e -> e.getOwner() == player && e.getHookState() == HookState.HOOKED
        );

        // 固定顺序：按实体 ID 从小到大，保证“领头勾爪”在多端一致。
        hooks.sort(Comparator.comparingInt(Entity::getId));
        return hooks;
    }

    /**
     * RETRACTING：
     * 向 owner 眼睛位置回收。
     *
     * 状态切换逻辑：
     * 1. 若 owner 不存在，直接销毁实体。
     * 2. 若回收到足够近（< 1.5 格），销毁实体。
     * 3. 否则以固定回收速度沿 owner 方向移动。
     */
    private void tickRetracting(Entity owner) {
        if (owner == null) {
            this.discard();
            return;
        }

        Vec3 target = owner.getEyePosition();
        Vec3 toOwner = target.subtract(this.position());
        double length = toOwner.length();

        if (length < 1.5D) {
            // 回收完成时，恢复玩家的重力，解除牵引状态
            if (owner instanceof Player player) {
                player.setNoGravity(false);
            }
            this.discard();
            return;
        }

        Vec3 retractVelocity = toOwner.normalize().scale(Math.max(0.6D, this.speed));
        this.setDeltaMovement(retractVelocity);

        Vec3 next = this.position().add(retractVelocity);
        this.setPos(next.x, next.y, next.z);
    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        // 先清空速度，立即中断飞行惯性，避免边缘命中时的反射位移。
        this.setDeltaMovement(Vec3.ZERO);
        this.skipDefaultTickThisFrame = true;

        System.out.println("[GrapplingHook] BlockHit at " + blockHitResult.getLocation());

        // ===== 核心修复：精准定位勾爪到方块表面外，彻底断开物理 =====
        // 问题诊断：
        // 1. Projectile 的默认反射/反弹逻辑会推开勾爪（super.onHitBlock 会干扰）
        // 2. 不精确的位置设置导致勾爪在方块内/外反复振荡
        // 3. 玩家被牵引时缺乏彻底的重力禁用
        // 
        // 解决方案：
        // 1. 获取精确交点：使用 getLocation()（浮点精度）而非 getBlockPos()（整数精度）
        // 2. 获取命中面法线：使用 getDirection()，其 getStep* 方向向量就是单位法线
        // 3. 计算最终位置：交点 + 法线*偏移，确保勾爪稳稳停留在方块外表面
        // 4. 立即冻结状态：设置 HOOKED 状态，防止状态被后续逻辑覆盖
        // 5. 不调用 super，避免 Projectile 的反弹/反射逻辑

        Vec3 hitPos = blockHitResult.getLocation();
        Direction dir = blockHitResult.getDirection();
        
        // 法线向量：每个分量为 -1/0/1（由 direction 决定）
        // 乘以 0.05D 让勾爪更贴近墙面，同时仍保留极小安全间隔
        Vec3 normalOffset = new Vec3(
            dir.getStepX() * 0.05D,
            dir.getStepY() * 0.05D,
            dir.getStepZ() * 0.05D
        );
        
        // 最终位置：精确交点 + 法线偏移（确保在方块"外"）
        Vec3 finalPos = hitPos.add(normalOffset);

        // 边缘吸附：若命中点非常接近方块边缘，向方块中心微调 0.02 格，
        // 降低边角浮点误差触发的“弹开”概率。
        Vec3 blockMin = Vec3.atLowerCornerOf(blockHitResult.getBlockPos());
        double localX = hitPos.x - blockMin.x;
        double localY = hitPos.y - blockMin.y;
        double localZ = hitPos.z - blockMin.z;
        double edgeX = Math.min(localX, 1.0D - localX);
        double edgeY = Math.min(localY, 1.0D - localY);
        double edgeZ = Math.min(localZ, 1.0D - localZ);

        // 只检测当前命中面的切向轴，避免把“贴面命中”误判成边缘命中。
        boolean nearEdge = false;
        if (dir.getStepX() == 0 && edgeX < 0.05D) {
            nearEdge = true;
        }
        if (dir.getStepY() == 0 && edgeY < 0.05D) {
            nearEdge = true;
        }
        if (dir.getStepZ() == 0 && edgeZ < 0.05D) {
            nearEdge = true;
        }

        if (nearEdge) {
            Vec3 center = Vec3.atCenterOf(blockHitResult.getBlockPos());
            Vec3 towardCenter = center.subtract(finalPos);
            if (towardCenter.lengthSqr() > 1.0E-8D) {
                finalPos = finalPos.add(towardCenter.normalize().scale(0.02D));
            }
        }
        
        // 记录信息以供 tickHooked 维护
        this.hookedPos = hitPos;  // 精确交点
        this.hitDirection = dir;   // 法线方向
        
        // 立即"钉死"勾爪：清空速度、固定位置、切换状态
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(finalPos.x, finalPos.y, finalPos.z);
        markVelocityModified(this);
        this.noPhysics = true;
        this.setStateSync(HookState.HOOKED);
        
        // 注意：不调用 super.onHitBlock()，避免 Projectile 的任何默认逻辑干扰
    }

    @Override
    protected void onHitEntity(net.minecraft.world.phys.EntityHitResult entityHitResult) {
        Entity hitEntity = entityHitResult.getEntity();
        System.out.println("[GrapplingHook] EntityHit: " + hitEntity.getName().getString()
                + " at " + entityHitResult.getLocation());

        // 防止“自碰撞”：如果命中的就是发射者自己，则忽略本次命中。
        // 这可以避免石质等低速勾爪在起步阶段被自身碰撞箱吞掉。
        if (hitEntity == this.getOwner()) {
            return;
        }

        super.onHitEntity(entityHitResult);

        // 命中实体时，当前版本先进入回收态，避免穿透飞走。
        this.setStateSync(HookState.RETRACTING);
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        Entity owner = this.getOwner();
        return super.canHitEntity(target) && target != owner;
    }

    @Override
    public boolean hurtServer(ServerLevel serverLevel, net.minecraft.world.damagesource.DamageSource damageSource,
            float amount) {
        // 勾爪不接受常规伤害，避免中途被异常击毁影响状态机。
        return false;
    }

    public HookState getHookState() {
        return state;
    }

    public ModGrapplingHookTiers getTier() {
        // 客户端渲染时优先读同步字段，确保不同材质显示不同贴图。
        int index = Mth.clamp(this.entityData.get(DATA_TIER_INDEX), 0, TIER_VALUES.length - 1);
        return this.level().isClientSide() ? TIER_VALUES[index] : tier;
    }

    public int getRange() {
        return range;
    }

    public double getSpeed() {
        return speed;
    }

    private void setTierInternal(ModGrapplingHookTiers tier) {
        this.tier = tier;
        this.entityData.set(DATA_TIER_INDEX, tier.ordinal());
    }

    private void setStateSync(HookState newState) {
        this.state = newState;
        this.noPhysics = (newState == HookState.HOOKED);
        // 服务端将状态同步到所有监听客户端。
        this.entityData.set(DATA_STATE, newState.ordinal());
    }

    private static void markVelocityModified(Entity entity) {
        // 与服务端同步位移更新的核心标志。
        entity.hurtMarked = true;

        // 兼容字段名差异：若当前映射存在 velocityModified，则一并置 true。
        if (!velocityModifiedFieldResolved) {
            velocityModifiedFieldResolved = true;
            Class<?> current = entity.getClass();
            while (current != null) {
                try {
                    velocityModifiedField = current.getDeclaredField("velocityModified");
                    velocityModifiedField.setAccessible(true);
                    break;
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
        }

        if (velocityModifiedField != null) {
            try {
                velocityModifiedField.setBoolean(entity, true);
            } catch (IllegalAccessException ignored) {
                // 反射失败时降级为仅使用 hurtMarked。
            }
        }
    }

    @Override
    public boolean shouldRender(double pX, double pY, double pZ) {
        // 因为勾爪渲染器绘制的是从玩家手部到实体的连接线，
        // 即使实体本身在视锥体外，连接线仍可能在视野范围内，所以不应被剔除。
        // 返回 true 确保勾爪始终被渲染。
        return true;
    }
}
