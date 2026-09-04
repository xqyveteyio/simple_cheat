package com.keyboard.simplecheat.module;

import com.keyboard.simplecheat.module.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
    protected final MinecraftClient mc = MinecraftClient.getInstance();

    private final String id;
    private final String displayName;
    private final String description;
    private final Category category;
    private final int defaultKey;
    private final List<Setting> settings = new ArrayList<>();

    private boolean enabled;

    protected Module(String id, String displayName, String description, Category category, int defaultKey) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.category = category;
        this.defaultKey = defaultKey;
    }

    protected Module(String id, String displayName, String description, Category category) {
        this(id, displayName, description, category, GLFW.GLFW_KEY_UNKNOWN);
    }

    protected void addSettings(Setting... toAdd) {
        Collections.addAll(settings, toAdd);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public int getDefaultKey() {
        return defaultKey;
    }

    public List<Setting> getSettings() {
        return settings;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean value) {
        if (this.enabled == value) {
            return;
        }
        this.enabled = value;
        if (value) {
            onEnable();
        } else {
            onDisable();
        }
    }

    /** 切换开关并在物品栏上方给出提示，供按键与界面调用。 */
    public void toggleWithFeedback() {
        toggle();
        if (mc.player != null) {
            Text message = Text.literal(displayName)
                    .append(Text.literal(enabled ? " 已开启" : " 已关闭")
                            .formatted(enabled ? Formatting.GREEN : Formatting.RED));
            mc.player.sendMessage(message, true);
        }
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    /** 每客户端 tick 调用一次，仅在模块开启时。 */
    public void onTick() {
    }
}
