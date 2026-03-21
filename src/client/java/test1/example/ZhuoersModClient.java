package test1.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import test1.example.entity.render.GrapplingHookEntityRenderer;

public class ZhuoersModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 客户端初始化：注册按键绑定（需在 ClientModInitializer 中调用）。
        ModKeyBindings.initializeClient();

        // 注册勾爪实体渲染器。
        EntityRendererRegistry.register(ModEntities.GRAPPLING_HOOK, GrapplingHookEntityRenderer::new);
    }
}