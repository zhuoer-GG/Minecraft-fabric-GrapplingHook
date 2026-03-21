package test1.example.datagen;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import test1.example.ModItems;

/**
 * 模组成就（进度）数据生成器。
 *
 * 进度规划：
 * 1. 根成就：获得石质勾爪，作为入门引导。
 * 2. 挑战成就：获得下界合金勾爪，作为高阶目标。
 */
public class ModAdvancementProvider extends FabricAdvancementProvider {
    /**
     * 构造函数：由 DataGenerator 注入输出对象和注册表 Future。
     */
    public ModAdvancementProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    public void generateAdvancement(HolderLookup.Provider registries, Consumer<AdvancementHolder> consumer) {
        // 根成就：只要背包中出现石质勾爪即可解锁。
        AdvancementHolder root = Advancement.Builder.advancement()
                .display(
                        ModItems.STONE_GRAPPLING_HOOK,
                        Component.translatable("advancements.test1.root.title"),
                        Component.translatable("advancements.test1.root.description"),
                        Identifier.parse("minecraft:block/stone"),//使用石块作为成就背景图标
                        AdvancementType.TASK,
                        true,
                        true,
                        false
                )
                .addCriterion(
                        "has_stone_grappling_hook",
                        InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.STONE_GRAPPLING_HOOK)
                )
                .save(consumer, "test1:root");

        // 挑战成就：获得下界合金勾爪，作为根成就的子节点。
        Advancement.Builder.advancement()
                .parent(root)
                .display(
                        ModItems.NETHERITE_GRAPPLING_HOOK,
                        Component.translatable("advancements.test1.netherite.title"),
                        Component.translatable("advancements.test1.netherite.description"),
                        null,
                        AdvancementType.CHALLENGE,
                        true,
                        true,
                        false
                )
                .addCriterion(
                        "has_netherite_grappling_hook",
                        InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.NETHERITE_GRAPPLING_HOOK)
                )
                .save(consumer, "test1:netherite_grappling_hook");
    }
}
