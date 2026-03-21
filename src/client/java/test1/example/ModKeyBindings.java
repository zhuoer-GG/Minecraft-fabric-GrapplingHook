package test1.example;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * 客户端按键绑定注册。
 *
 * 注意：该类需要在 ClientModInitializer 中调用初始化方法，
 * 否则按键不会被注册到客户端输入系统。
 */
public final class ModKeyBindings {
    /**
     * 勾爪使用按键：默认绑定为鼠标右键。
     *
     * 翻译键可在 lang 文件中补充：
     * key.zhuoers-mod.use_grappling_hook
     */
    public static final KeyMapping KEY_USE_GRAPPLING_HOOK = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "key." + ZhuoersMod.MOD_ID + ".use_grappling_hook",
                    InputConstants.Type.MOUSE,
                    GLFW.GLFW_MOUSE_BUTTON_RIGHT,
                    KeyMapping.Category.register(Identifier.parse(ZhuoersMod.MOD_ID))
            )
    );

    private ModKeyBindings() {
        // 工具类不应被实例化。
    }

    /** 客户端初始化入口：仅执行按键注册相关初始化。 */
    public static void initializeClient() {
        ZhuoersMod.LOGGER.info("Client key bindings initialized for mod: {}", ZhuoersMod.MOD_ID);
    }
}
