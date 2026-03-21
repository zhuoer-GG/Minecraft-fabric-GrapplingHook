package test1.example;

import java.util.function.Function;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * 模组物品注册类。
 *
 * 所有自定义物品都应集中在本类中声明并注册，便于统一维护：
 * 1. 静态字段负责持有物品实例，方便在其他系统（物品组、配方、渲染）中复用。
 * 2. registerModItems() 在模组初始化阶段调用，确保物品进入 Minecraft 注册表。
 */
public final class ModItems {
    /**
     * 物品命名空间（Namespace）。
     *
     * 这里使用 test1，与本次语言文件路径 assets/test1 保持一致，
     * 使物品翻译键和注册 Identifier 对齐。
     */
    public static final String NAMESPACE = "test1";

    /** 石质勾爪：基础版本，使用 GrapplingHookItem 实现勾爪逻辑。 */
    public static final Item STONE_GRAPPLING_HOOK = registerItem(
            "stone_grappling_hook",
            (properties) -> new GrapplingHookItem(ModGrapplingHookTiers.STONE, properties),
            new Item.Properties()
    );

    /**
     * 铁质勾爪：
     * 相比石质拥有更高的基础性能，同样使用 GrapplingHookItem 实现勾爪逻辑。
     */
    public static final Item IRON_GRAPPLING_HOOK = registerItem(
            "iron_grappling_hook",
            (properties) -> new GrapplingHookItem(ModGrapplingHookTiers.IRON, properties),
            new Item.Properties()
    );

    /**
     * 金质勾爪：
     * 强调灵活定位的中高阶版本，同样使用 GrapplingHookItem 实现勾爪逻辑。
     */
    public static final Item GOLD_GRAPPLING_HOOK = registerItem(
            "gold_grappling_hook",
            (properties) -> new GrapplingHookItem(ModGrapplingHookTiers.GOLD, properties),
            new Item.Properties()
    );

    /**
     * 钻石质勾爪：
     * 高耐用与高性能定位，同样使用 GrapplingHookItem 实现勾爪逻辑。
     */
    public static final Item DIAMOND_GRAPPLING_HOOK = registerItem(
            "diamond_grappling_hook",
            (properties) -> new GrapplingHookItem(ModGrapplingHookTiers.DIAMOND, properties),
            new Item.Properties()
    );

    /**
     * 下界合金勾爪：高级版本。
     * 使用 fireResistant() 防火特性，同样使用 GrapplingHookItem 实现勾爪逻辑。
     */
    public static final Item NETHERITE_GRAPPLING_HOOK = registerItem(
            "netherite_grappling_hook",
            (properties) -> new GrapplingHookItem(ModGrapplingHookTiers.NETHERITE, properties),
            new Item.Properties().fireResistant()
    );

    private ModItems() {
        // 工具类不应被实例化。
    }

    /**
     * 执行物品注册。
     *
     * 该方法本身不需要重复注册逻辑，
     * 主要用于在主入口中显式触发类加载，确保静态字段完成注册。
     */
    public static void registerModItems() {
        // 该日志用于确认五种勾爪均已在静态初始化阶段完成注册。
        ZhuoersMod.LOGGER.info("Registering mod items for namespace: {}", NAMESPACE);
    }

    /**
     * 向物品注册表注册单个物品。
     *
     * @param path 物品路径（如 stone_grappling_hook）
     * 1.21.2+ 版本中，Item 需要先关联 RegistryKey 才能安全创建，
     * 否则会在游戏启动时触发 "Item id not set" 崩溃。
     *
     * @param factory 物品工厂（如 Item::new）
     * @param settings 物品配置
     * @return 注册后的物品实例
     */
    private static Item registerItem(String path, Function<Item.Properties, Item> factory, Item.Properties settings) {
        Identifier id = Identifier.fromNamespaceAndPath(NAMESPACE, path);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);

        // 使用原版 Items.registerItem，内部会把 registry key 写入 settings 后再构造 Item。
        return Items.registerItem(itemKey, factory, settings);
    }
}
