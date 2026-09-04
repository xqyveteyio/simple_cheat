package com.keyboard.simplecheat;

import com.keyboard.simplecheat.config.ConfigManager;
import com.keyboard.simplecheat.gui.HudRenderer;
import com.keyboard.simplecheat.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleCheatClient implements ClientModInitializer {
    public static final String MOD_ID = "simple-cheat";
    public static final Logger LOGGER = LoggerFactory.getLogger("SimpleCheat");

    private static ModuleManager moduleManager;
    private static ConfigManager configManager;

    @Override
    public void onInitializeClient() {
        moduleManager = new ModuleManager();
        configManager = new ConfigManager();
        configManager.load(moduleManager);

        KeyBindings.register(moduleManager);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            KeyBindings.handle(client);
            moduleManager.onTick();
        });

        HudRenderCallback.EVENT.register((context, tickDelta) -> HudRenderer.render(context, moduleManager));

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveConfig());

        LOGGER.info("Simple Cheat 已加载，按右 Shift 打开设置界面");
    }

    public static ModuleManager getModuleManager() {
        return moduleManager;
    }

    public static void saveConfig() {
        if (configManager != null && moduleManager != null) {
            configManager.save(moduleManager);
        }
    }
}
