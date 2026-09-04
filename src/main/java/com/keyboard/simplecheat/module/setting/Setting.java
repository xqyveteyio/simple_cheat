package com.keyboard.simplecheat.module.setting;

import com.google.gson.JsonObject;

/**
 * 模块设置项的基类，每个设置项负责自己的 JSON 序列化。
 */
public abstract class Setting {
    private final String key;
    private final String displayName;
    private final String description;

    protected Setting(String key, String displayName, String description) {
        this.key = key;
        this.displayName = displayName;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public abstract void write(JsonObject json);

    public abstract void read(JsonObject json);

    public abstract void reset();
}
