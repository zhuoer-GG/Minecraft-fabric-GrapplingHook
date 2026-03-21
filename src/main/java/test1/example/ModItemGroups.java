package test1.example;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * 自定义物品组注册类。
 *
 * 物品组用于在创造模式物品栏中提供一个独立分页，
 * 让玩家能快速找到本模组的相关物品。
 */
public final class ModItemGroups {
    /**
     * 自定义勾爪物品组。
     *
     * 关键配置：
     * 1. Identifier: test1:grappling_hook（用于注册唯一标识）
     * 2. 图标: 下界合金勾爪
     * 3. 显示名翻译键: itemGroup.test1.grappling_hook
     * 4. 条目: 石质勾爪、下界合金勾爪
     */
        public static final CreativeModeTab GRAPPLING_HOOK_GROUP = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
                Identifier.fromNamespaceAndPath(ModItems.NAMESPACE, "grappling_hook"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(ModItems.NETHERITE_GRAPPLING_HOOK))
                    .title(Component.translatable("grappling_hook"))
                    .displayItems((displayContext, entries) -> {
                        // 按固定顺序放入：石质 -> 铁质 -> 金质 -> 钻石质 -> 下界合金质。
                        entries.accept(ModItems.STONE_GRAPPLING_HOOK);
                        entries.accept(ModItems.IRON_GRAPPLING_HOOK);
                        entries.accept(ModItems.GOLD_GRAPPLING_HOOK);
                        entries.accept(ModItems.DIAMOND_GRAPPLING_HOOK);
                        entries.accept(ModItems.NETHERITE_GRAPPLING_HOOK);
                    })
                    .build()
    );

    private ModItemGroups() {
        // 工具类不应被实例化。
    }

    /**
     * 执行物品组注册。
     *
     * 该方法在主类初始化时调用，用于触发本类加载并完成静态注册。
     */
    public static void registerItemGroups() {
        ZhuoersMod.LOGGER.info("Registering item groups for namespace: {}", ModItems.NAMESPACE);
    }
}
