package com.keyboard.simplecheat.module;

public enum Category {
    COMBAT("战斗"),
    MOVEMENT("移动"),
    PLAYER("玩家"),
    RENDER("显示"),
    MISC("杂项");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
