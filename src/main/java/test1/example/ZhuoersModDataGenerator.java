package test1.example;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import test1.example.datagen.ModAdvancementProvider;
import test1.example.datagen.ModRecipeProvider;

/**
 * 模组 DataGen 入口。
 *
 * 该入口用于声明并注册所有数据生成 Provider，
 * 运行 datagen 任务时会自动调用这些 Provider 产出 JSON 文件。
 */
public class ZhuoersModDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        // 创建数据生成包（Pack），用于集中管理本模组的数据生成任务。
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();

        // 注册配方生成器：负责输出 recipes 下的合成表数据。
        pack.addProvider(ModRecipeProvider::new);

        // 注册成就生成器：负责输出 advancements 下的进度数据。
        pack.addProvider(ModAdvancementProvider::new);
    }
}
