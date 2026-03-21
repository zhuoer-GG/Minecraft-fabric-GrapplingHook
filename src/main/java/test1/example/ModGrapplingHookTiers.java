package test1.example;

/**
 * 勾爪材质枚举。
 *
 * 该枚举用于统一描述不同材质勾爪的核心参数，避免在物品逻辑中写死数值：
 * 1. speed（速度）: 决定勾爪拉动、发射或响应的基础速度倍率。
 * 2. maxDistance（距离）: 决定勾爪可作用的最大距离。
 * 3. hookCount（钩子数）: 决定一次可用或可生成的钩子数量。
 *
 * 后续当你实现具体勾爪行为（如实体飞行、拉拽玩家、连发机制）时，
 * 可以通过该枚举直接读取配置，减少硬编码并提升可维护性。
 */
public enum ModGrapplingHookTiers {
    /**
     * 石质勾爪：
     * 速度较慢、距离较短、单钩子，属于入门级配置。
     */
    STONE(0.4D, 12, 1),

    /**
     * 铁质勾爪：
     * 速度和距离都优于石质，依然为单钩子，整体更均衡。
     */
    IRON(0.6D, 18, 1),

    /**
     * 金质勾爪：
     * 速度较高，但距离略低于铁质，提供双钩子特性。
     */
    GOLD(0.9D, 15, 2),

    /**
     * 钻石勾爪：
     * 速度稳定、距离显著提升，并支持三钩子。
     */
    DIAMOND(0.8D, 24, 3),

    /**
     * 下界合金勾爪：
     * 顶级配置，速度和距离均为最高档，并支持四钩子。
     */
    NETHERITE(1.0D, 32, 4);

    /** 勾爪速度倍率。 */
    private final double speed;

    /** 勾爪最大作用距离。 */
    private final int maxDistance;

    /** 勾爪可用数量（或并发钩子数量）。 */
    private final int hookCount;

    /**
     * 构造函数：为每个材质注入固定参数。
     *
     * @param speed 勾爪速度倍率
     * @param maxDistance 最大作用距离
     * @param hookCount 钩子数量
     */
    ModGrapplingHookTiers(double speed, int maxDistance, int hookCount) {
        this.speed = speed;
        this.maxDistance = maxDistance;
        this.hookCount = hookCount;
    }

    /**
     * 获取勾爪速度倍率。
     *
     * @return 速度倍率
     */
    public double getSpeed() {
        return speed;
    }

    /**
     * 获取勾爪最大作用距离。
     *
     * @return 最大作用距离
     */
    public int getMaxDistance() {
        return maxDistance;
    }

    /**
     * 获取勾爪数量。
     *
     * @return 钩子数量
     */
    public int getHookCount() {
        return hookCount;
    }
}
