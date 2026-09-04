package com.keyboard.simplecheat.gui;

import com.keyboard.simplecheat.module.Module;
import com.keyboard.simplecheat.module.ModuleManager;
import com.keyboard.simplecheat.module.combat.KillAura;
import com.keyboard.simplecheat.module.combat.RangedDefense;
import com.keyboard.simplecheat.module.movement.AutoDodge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.List;
import java.util.Locale;

public final class HudRenderer {
    private static final int COLOR_BACKGROUND = 0x60000000;
    private static final int COLOR_MODULE = 0xFF6FE3A0;
    private static final int COLOR_TARGET = 0xFFFFC66D;
    private static final int COLOR_THREAT = 0xFF7FB3FF;
    private static final int COLOR_DODGE = 0xFFB388FF;
    private static final int COLOR_DANGER = 0xFFFF6B6B;

    private HudRenderer() {
    }

    public static void render(DrawContext context, ModuleManager moduleManager) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || mc.currentScreen instanceof ClickGuiScreen) {
            return;
        }

        List<Module> enabled = moduleManager.getEnabled();
        if (enabled.isEmpty()) {
            return;
        }

        TextRenderer textRenderer = mc.textRenderer;
        int screenWidth = context.getScaledWindowWidth();
        int y = 4;

        for (Module module : enabled) {
            y = drawRightAligned(context, textRenderer, module.getDisplayName(), screenWidth, y, COLOR_MODULE);
        }

        KillAura killAura = moduleManager.getKillAura();
        LivingEntity target = killAura.getCurrentTarget();
        if (killAura.isEnabled() && target != null) {
            String info = String.format(Locale.ROOT, "目标: %s  %.1f♥",
                    target.getName().getString(), target.getHealth() + target.getAbsorptionAmount());
            y = drawRightAligned(context, textRenderer, info, screenWidth, y, COLOR_TARGET);
        }

        RangedDefense rangedDefense = moduleManager.getRangedDefense();
        Entity threat = rangedDefense.getCurrentThreat();
        if (rangedDefense.isEnabled() && threat != null) {
            String info = "格挡: " + threat.getName().getString();
            y = drawRightAligned(context, textRenderer, info, screenWidth, y, COLOR_THREAT);
        }

        AutoDodge autoDodge = moduleManager.getAutoDodge();
        if (autoDodge.isEnabled() && autoDodge.getIncomingCount() > 0) {
            String info = autoDodge.isDodging()
                    ? "闪避中 x" + autoDodge.getIncomingCount()
                    : "无法闪避 x" + autoDodge.getIncomingCount();
            drawRightAligned(context, textRenderer, info, screenWidth, y,
                    autoDodge.isDodging() ? COLOR_DODGE : COLOR_DANGER);
        }
    }

    private static int drawRightAligned(DrawContext context, TextRenderer textRenderer,
                                        String text, int screenWidth, int y, int color) {
        int width = textRenderer.getWidth(text);
        context.fill(screenWidth - width - 6, y - 2, screenWidth - 2, y + 9, COLOR_BACKGROUND);
        context.drawTextWithShadow(textRenderer, text, screenWidth - width - 4, y, color);
        return y + 12;
    }
}
