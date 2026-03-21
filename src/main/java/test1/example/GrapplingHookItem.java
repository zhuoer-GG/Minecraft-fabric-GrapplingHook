package test1.example;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import test1.example.entity.GrapplingHookEntity;

/**
 * 勾爪物品类。
 *
 * 这是触发勾爪发射的入口。当玩家右键点击且手持该物品时，
 * 在服务端生成一个 GrapplingHookEntity 实体，根据物品的材质等级
 * 从 ModGrapplingHookTiers 中读取对应的 range 和 speed 传入实体。
 */
public class GrapplingHookItem extends Item {
    /** 本勾爪的材质等级，用于读取速度和距离等配置参数。 */
    private final ModGrapplingHookTiers tier;

    /**
     * 构造函数：为每个勾爪物品关联一个材质等级。
     *
     * @param tier 勾爪的材质等级
     * @param properties 物品属性
     */
    public GrapplingHookItem(ModGrapplingHookTiers tier, Item.Properties properties) {
        super(properties);
        this.tier = tier;
    }

    /**
     * 触发勾爪发射入口。
     *
     * 玩家右键使用勾爪时：
     * 1. 客户端仅返回成功交互结果。
     * 2. 服务端创建 GrapplingHookEntity，并注入材质参数（range/speed）。
        *
        * 1.21.1 交互 API 说明：
        * - player: 发起交互的玩家实体。
        * - hand: 触发本次交互的手（MAIN_HAND 或 OFF_HAND）。
        *   客户端若主动调用 gameMode.useItem(...)，需要显式传入这两个参数，
        *   最终会在服务端路由到本方法处理具体物品逻辑。
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        GrapplingHookEntity hook = new GrapplingHookEntity(ModEntities.GRAPPLING_HOOK, serverLevel, player, this.tier);
        serverLevel.addFreshEntity(hook);
        return InteractionResult.SUCCESS;
    }

    /**
     * 获取本勾爪的材质等级。
     *
     * @return 材质等级枚举值
     */
    public ModGrapplingHookTiers getTier() {
        return tier;
    }
}
