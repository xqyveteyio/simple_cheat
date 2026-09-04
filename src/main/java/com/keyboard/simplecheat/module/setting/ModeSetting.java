package com.keyboard.simplecheat.module.setting;

import com.google.gson.JsonObject;

/**
 * 多选一的设置项。{@code keys} 是写进配置文件的稳定标识，{@code labels} 是界面上显示的文本。
 */
public class ModeSetting extends Setting {
    private final String[] keys;
    private final String[] labels;
    private final int defaultIndex;
    private int index;

    public ModeSetting(String key, String displayName, String description,
                       String[] keys, String[] labels, int defaultIndex) {
        super(key, displayName, description);
        if (keys.length != labels.length) {
            throw new IllegalArgumentException("keys 与 labels 数量必须一致");
        }
        this.keys = keys;
        this.labels = labels;
        this.defaultIndex = defaultIndex;
        this.index = defaultIndex;
    }

    public String get() {
        return keys[index];
    }

    public boolean is(String key) {
        return keys[index].equals(key);
    }

    public String getLabel() {
        return labels[index];
    }

    public void next() {
        index = (index + 1) % keys.length;
    }

    public void previous() {
        index = (index - 1 + keys.length) % keys.length;
    }

    @Override
    public void write(JsonObject json) {
        json.addProperty(getKey(), keys[index]);
    }

    @Override
    public void read(JsonObject json) {
        if (!json.has(getKey()) || !json.get(getKey()).isJsonPrimitive()) {
            return;
        }
        String stored = json.get(getKey()).getAsString();
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(stored)) {
                index = i;
                return;
            }
        }
    }

    @Override
    public void reset() {
        index = defaultIndex;
    }
}
