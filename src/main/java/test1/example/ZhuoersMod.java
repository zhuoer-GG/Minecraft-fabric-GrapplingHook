package test1.example;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZhuoersMod implements ModInitializer {
	public static final String MOD_ID = "zhuoers-mod";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// 模组初始化步骤 1：注册实体类型。
		ModEntities.registerModEntities();

		// 模组初始化步骤 2：注册本模组的所有自定义物品。
		ModItems.registerModItems();

		// 模组初始化步骤 3：注册本模组的自定义物品组（创造模式分类页）。
		ModItemGroups.registerItemGroups();

		LOGGER.info("Hello Fabric world!");
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			LOGGER.info("Player joined: {} this is a test", handler.getPlayer().getName().getString()); // 输出玩家名字
		});
	}
}