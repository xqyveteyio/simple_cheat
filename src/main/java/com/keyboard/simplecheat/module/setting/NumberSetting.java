package com.keyboard.simplecheat.module.setting;

import com.google.gson.JsonObject;

import java.util.Locale;

public class NumberSetting extends Setting {
    private final double defaultValue;
    private final double min;
    private final double max;
    private final double step;
    private double value;

    public NumberSetting(String key, String displayName, String description,
                         double defaultValue, double min, double max, double step) {
        super(key, displayName, description);
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = defaultValue;
    }

    public double get() {
        return value;
    }

    public int getInt() {
        return (int) Math.round(value);
    }

    public void set(double raw) {
        double snapped = Math.round(raw / step) * step;
        // 避免 0.1 这类步长累积出 3.9000000000000004 这样的值
        snapped = Math.round(snapped * 1000.0) / 1000.0;
        this.value = Math.max(min, Math.min(max, snapped));
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    /** 当前值在 [min, max] 区间内的比例，供滑动条使用。 */
    public double getRatio() {
        return (value - min) / (max - min);
    }

    public void setFromRatio(double ratio) {
        set(min + Math.max(0.0, Math.min(1.0, ratio)) * (max - min));
    }

    public String getDisplayValue() {
        if (step >= 1.0) {
            return String.valueOf(getInt());
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    @Override
    public void write(JsonObject json) {
        json.addProperty(getKey(), value);
    }

    @Override
    public void read(JsonObject json) {
        if (json.has(getKey()) && json.get(getKey()).isJsonPrimitive()) {
            set(json.get(getKey()).getAsDouble());
        }
    }

    @Override
    public void reset() {
        value = defaultValue;
    }
}
