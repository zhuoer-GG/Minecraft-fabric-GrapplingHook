package test1.example.datagen;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SmithingTransformRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import test1.example.ModItems;

/**
 * 模组合成表数据生成器。
 *
 * 该类负责在运行 DataGen 时自动生成配方 JSON，
 * 避免手写 JSON 出错并便于后续维护和扩展。
 */
public class ModRecipeProvider extends FabricRecipeProvider {
    /**
     * 构造函数：由 DataGenerator 在初始化时注入输出路径与注册表查找器。
     */
    public ModRecipeProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput exporter) {
        return new RecipeProvider(registries, exporter) {
            @Override
            public void buildRecipes() {
                // 统一竖向有损合成模板：
                // 顶部 S = 史莱姆球，中部 M = 对应材质，底部 C = 锁链。

                // 石质勾爪：史莱姆球 + 圆石 + 锁链。
                shaped(RecipeCategory.TOOLS, ModItems.STONE_GRAPPLING_HOOK)
                        .pattern("S")
                        .pattern("M")
                        .pattern("C")
                        .define('S', Items.SLIME_BALL)
                        .define('M', Items.COBBLESTONE)
                        .define('C', Items.IRON_CHAIN)
                        .unlockedBy("has_cobblestone", has(Items.COBBLESTONE))
                        .save(exporter);

                // 铁质勾爪：史莱姆球 + 铁锭 + 锁链。
                shaped(RecipeCategory.TOOLS, ModItems.IRON_GRAPPLING_HOOK)
                        .pattern("S")
                        .pattern("M")
                        .pattern("C")
                        .define('S', Items.SLIME_BALL)
                        .define('M', Items.IRON_INGOT)
                        .define('C', Items.IRON_CHAIN)
                        .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
                        .save(exporter);

                // 金质勾爪：史莱姆球 + 金锭 + 锁链。
                shaped(RecipeCategory.TOOLS, ModItems.GOLD_GRAPPLING_HOOK)
                        .pattern("S")
                        .pattern("M")
                        .pattern("C")
                        .define('S', Items.SLIME_BALL)
                        .define('M', Items.GOLD_INGOT)
                        .define('C', Items.IRON_CHAIN)
                        .unlockedBy("has_gold_ingot", has(Items.GOLD_INGOT))
                        .save(exporter);

                // 钻石质勾爪：史莱姆球 + 钻石 + 锁链。
                shaped(RecipeCategory.TOOLS, ModItems.DIAMOND_GRAPPLING_HOOK)
                        .pattern("S")
                        .pattern("M")
                        .pattern("C")
                        .define('S', Items.SLIME_BALL)
                        .define('M', Items.DIAMOND)
                        .define('C', Items.IRON_CHAIN)
                        .unlockedBy("has_diamond", has(Items.DIAMOND))
                        .save(exporter);

                // 下界合金勾爪：史莱姆球 + 下界合金锭 + 锁链
                SmithingTransformRecipeBuilder.smithing(
                                Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                                Ingredient.of(ModItems.DIAMOND_GRAPPLING_HOOK),
                                Ingredient.of(Items.NETHERITE_INGOT),
                                RecipeCategory.TOOLS,
                                ModItems.NETHERITE_GRAPPLING_HOOK
                        )
                        .unlocks("has_diamond_grappling_hook", has(ModItems.DIAMOND_GRAPPLING_HOOK))
                        .save(exporter, "test1:netherite_grappling_hook_smithing");
            }
        };
    }

    @Override
    public String getName() {
        return "Mod Recipes";
    }
}
