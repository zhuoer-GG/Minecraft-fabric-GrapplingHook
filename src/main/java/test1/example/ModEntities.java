package test1.example;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import test1.example.entity.GrapplingHookEntity;

/**
 * 模组实体注册。
 */
public final class ModEntities {
    /** 勾爪投射物实体类型。 */
    public static final EntityType<GrapplingHookEntity> GRAPPLING_HOOK = register("grappling_hook",
            EntityType.Builder.<GrapplingHookEntity>of(GrapplingHookEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
    );

    private ModEntities() {
        // 工具类不应被实例化。
    }

    public static void registerModEntities() {
        ZhuoersMod.LOGGER.info("Registering mod entities for mod: {}", ZhuoersMod.MOD_ID);
    }

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType.Builder<T> builder) {
        Identifier id = Identifier.fromNamespaceAndPath(ModItems.NAMESPACE, path);
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id, builder.build(key));
    }
}
