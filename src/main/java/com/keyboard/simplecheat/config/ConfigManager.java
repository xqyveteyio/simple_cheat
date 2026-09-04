package com.keyboard.simplecheat.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.keyboard.simplecheat.SimpleCheatClient;
import com.keyboard.simplecheat.module.Module;
import com.keyboard.simplecheat.module.ModuleManager;
import com.keyboard.simplecheat.module.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("simple-cheat.json");

    public void load(ModuleManager moduleManager) {
        if (!Files.exists(configPath)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return;
            }
            JsonObject modulesJson = root.getAsJsonObject().getAsJsonObject("modules");
            if (modulesJson == null) {
                return;
            }

            for (Module module : moduleManager.getModules()) {
                JsonElement element = modulesJson.get(module.getId());
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject moduleJson = element.getAsJsonObject();

                if (moduleJson.has("enabled")) {
                    module.setEnabled(moduleJson.get("enabled").getAsBoolean());
                }

                JsonObject settingsJson = moduleJson.getAsJsonObject("settings");
                if (settingsJson != null) {
                    for (Setting setting : module.getSettings()) {
                        setting.read(settingsJson);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            SimpleCheatClient.LOGGER.warn("读取配置失败，将使用默认设置", e);
        }
    }

    public void save(ModuleManager moduleManager) {
        JsonObject modulesJson = new JsonObject();

        for (Module module : moduleManager.getModules()) {
            JsonObject settingsJson = new JsonObject();
            for (Setting setting : module.getSettings()) {
                setting.write(settingsJson);
            }

            JsonObject moduleJson = new JsonObject();
            moduleJson.addProperty("enabled", module.isEnabled());
            moduleJson.add("settings", settingsJson);

            modulesJson.add(module.getId(), moduleJson);
        }

        JsonObject root = new JsonObject();
        root.add("modules", modulesJson);

        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            SimpleCheatClient.LOGGER.warn("保存配置失败", e);
        }
    }
}
