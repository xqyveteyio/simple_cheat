package com.keyboard.simplecheat;

import com.keyboard.simplecheat.gui.ClickGuiScreen;
import com.keyboard.simplecheat.module.Module;
import com.keyboard.simplecheat.module.ModuleManager;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * 每个模块注册一个原版按键绑定，玩家可以在「选项 - 控制」里自行改键。
 */
public final class KeyBindings {
    private static final String CATEGORY = "key.categories.simple-cheat";

    private static final Map<Module, KeyBinding> MODULE_KEYS = new HashMap<>();
    private static KeyBinding openGuiKey;

    private KeyBindings() {
    }

    public static void register(ModuleManager moduleManager) {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.simple-cheat.click_gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));

        for (Module module : moduleManager.getModules()) {
            KeyBinding binding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.simple-cheat." + module.getId(), InputUtil.Type.KEYSYM, module.getDefaultKey(), CATEGORY));
            MODULE_KEYS.put(module, binding);
        }
    }

    public static void handle(MinecraftClient client) {
        while (openGuiKey.wasPressed()) {
            client.setScreen(new ClickGuiScreen());
        }

        for (Map.Entry<Module, KeyBinding> entry : MODULE_KEYS.entrySet()) {
            while (entry.getValue().wasPressed()) {
                entry.getKey().toggleWithFeedback();
            }
        }
    }
}
