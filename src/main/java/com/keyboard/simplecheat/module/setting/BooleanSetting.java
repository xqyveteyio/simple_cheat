package com.keyboard.simplecheat.module.setting;

import com.google.gson.JsonObject;

public class BooleanSetting extends Setting {
    private final boolean defaultValue;
    private boolean value;

    public BooleanSetting(String key, String displayName, String description, boolean defaultValue) {
        super(key, displayName, description);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public boolean get() {
        return value;
    }

    public void set(boolean value) {
        this.value = value;
    }

    public void toggle() {
        this.value = !this.value;
    }

    @Override
    public void write(JsonObject json) {
        json.addProperty(getKey(), value);
    }

    @Override
    public void read(JsonObject json) {
        if (json.has(getKey()) && json.get(getKey()).isJsonPrimitive()) {
            value = json.get(getKey()).getAsBoolean();
        }
    }

    @Override
    public void reset() {
        value = defaultValue;
    }
}
