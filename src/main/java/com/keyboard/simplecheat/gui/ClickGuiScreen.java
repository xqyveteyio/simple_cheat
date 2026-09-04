package com.keyboard.simplecheat.gui;

import com.keyboard.simplecheat.SimpleCheatClient;
import com.keyboard.simplecheat.module.Module;
import com.keyboard.simplecheat.module.setting.BooleanSetting;
import com.keyboard.simplecheat.module.setting.ModeSetting;
import com.keyboard.simplecheat.module.setting.NumberSetting;
import com.keyboard.simplecheat.module.setting.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClickGuiScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int HEADER_HEIGHT = 24;
    private static final int FOOTER_HEIGHT = 22;
    private static final int ROW_HEIGHT = 16;
    private static final int PADDING = 8;

    private static final int COLOR_PANEL = 0xE60F1117;
    private static final int COLOR_HEADER = 0xFF1B2030;
    private static final int COLOR_BORDER = 0xFF39415C;
    private static final int COLOR_TEXT = 0xFFE6E9EF;
    private static final int COLOR_TEXT_DIM = 0xFF8B93A7;
    private static final int COLOR_ON = 0xFF6FE3A0;
    private static final int COLOR_OFF = 0xFF6B7280;
    private static final int COLOR_HOVER = 0x25FFFFFF;
    private static final int COLOR_SLIDER_BG = 0xFF262C3D;
    private static final int COLOR_SLIDER_FILL = 0xFF3F6FD8;

    // 界面关闭后仍保留展开状态，方便反复调同一个模块
    private static final Set<String> EXPANDED = new HashSet<>();

    private int panelX;
    private int panelY;
    private int panelHeight;
    private int scroll;
    private NumberSetting draggingSlider;

    public ClickGuiScreen() {
        super(Text.literal("Simple Cheat"));
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = Math.max(10, this.height / 10);
        panelHeight = this.height - panelY * 2;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        int contentTop = panelY + HEADER_HEIGHT;
        int contentBottom = panelY + panelHeight - FOOTER_HEIGHT;

        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, COLOR_PANEL);
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, contentTop, COLOR_HEADER);
        drawBorder(context, panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight);

        context.drawTextWithShadow(textRenderer, "Simple Cheat", panelX + PADDING, panelY + 8, COLOR_ON);
        String hint = "左键切换开关 · 右键展开设置";
        context.drawTextWithShadow(textRenderer, hint,
                panelX + PANEL_WIDTH - PADDING - textRenderer.getWidth(hint), panelY + 8, COLOR_TEXT_DIM);

        List<Row> rows = buildRows();
        clampScroll(rows.size(), contentBottom - contentTop);

        Setting hoveredSetting = null;
        context.enableScissor(panelX, contentTop, panelX + PANEL_WIDTH, contentBottom);
        for (int i = 0; i < rows.size(); i++) {
            int rowY = contentTop + i * ROW_HEIGHT - scroll;
            if (rowY + ROW_HEIGHT < contentTop || rowY > contentBottom) {
                continue;
            }
            boolean hovered = mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH
                    && mouseY >= Math.max(rowY, contentTop) && mouseY < Math.min(rowY + ROW_HEIGHT, contentBottom);

            Row row = rows.get(i);
            if (row.setting == null) {
                renderModuleRow(context, row.module, rowY, hovered);
            } else {
                renderSettingRow(context, row.setting, rowY, hovered);
                if (hovered) {
                    hoveredSetting = row.setting;
                }
            }
        }
        context.disableScissor();

        context.fill(panelX, contentBottom, panelX + PANEL_WIDTH, panelY + panelHeight, COLOR_HEADER);
        String footer = hoveredSetting != null && !hoveredSetting.getDescription().isEmpty()
                ? hoveredSetting.getDescription()
                : "按 Esc 关闭并保存";
        context.drawTextWithShadow(textRenderer,
                textRenderer.trimToWidth(footer, PANEL_WIDTH - PADDING * 2),
                panelX + PADDING, contentBottom + 7, COLOR_TEXT_DIM);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderModuleRow(DrawContext context, Module module, int rowY, boolean hovered) {
        if (hovered) {
            context.fill(panelX, rowY, panelX + PANEL_WIDTH, rowY + ROW_HEIGHT, COLOR_HOVER);
        }
        boolean expanded = EXPANDED.contains(module.getId());
        String prefix = expanded ? "-" : "+";
        context.drawTextWithShadow(textRenderer, prefix, panelX + PADDING, rowY + 4, COLOR_TEXT_DIM);
        context.drawTextWithShadow(textRenderer, module.getDisplayName(), panelX + PADDING + 10, rowY + 4,
                module.isEnabled() ? COLOR_ON : COLOR_TEXT);

        String state = module.isEnabled() ? "开" : "关";
        context.drawTextWithShadow(textRenderer, state,
                panelX + PANEL_WIDTH - PADDING - textRenderer.getWidth(state), rowY + 4,
                module.isEnabled() ? COLOR_ON : COLOR_OFF);
    }

    private void renderSettingRow(DrawContext context, Setting setting, int rowY, boolean hovered) {
        int labelX = panelX + PADDING + 14;

        if (setting instanceof NumberSetting number) {
            int barX1 = panelX + PADDING + 10;
            int barX2 = panelX + PANEL_WIDTH - PADDING;
            int barY1 = rowY + 2;
            int barY2 = rowY + ROW_HEIGHT - 2;
            context.fill(barX1, barY1, barX2, barY2, COLOR_SLIDER_BG);
            int fillWidth = (int) Math.round((barX2 - barX1) * number.getRatio());
            context.fill(barX1, barY1, barX1 + fillWidth, barY2, COLOR_SLIDER_FILL);
            context.drawTextWithShadow(textRenderer, number.getDisplayName(), barX1 + 4, rowY + 4, COLOR_TEXT);
            String value = number.getDisplayValue();
            context.drawTextWithShadow(textRenderer, value, barX2 - 4 - textRenderer.getWidth(value), rowY + 4, COLOR_TEXT);
            return;
        }

        if (hovered) {
            context.fill(panelX, rowY, panelX + PANEL_WIDTH, rowY + ROW_HEIGHT, COLOR_HOVER);
        }
        context.drawTextWithShadow(textRenderer, setting.getDisplayName(), labelX, rowY + 4, COLOR_TEXT);

        String value;
        int color;
        if (setting instanceof BooleanSetting bool) {
            value = bool.get() ? "[✔]" : "[  ]";
            color = bool.get() ? COLOR_ON : COLOR_OFF;
        } else if (setting instanceof ModeSetting mode) {
            value = mode.getLabel();
            color = COLOR_SLIDER_FILL | 0xFF000000;
        } else {
            return;
        }
        context.drawTextWithShadow(textRenderer, value,
                panelX + PANEL_WIDTH - PADDING - textRenderer.getWidth(value), rowY + 4, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int contentTop = panelY + HEADER_HEIGHT;
        int contentBottom = panelY + panelHeight - FOOTER_HEIGHT;

        if (mouseX < panelX || mouseX > panelX + PANEL_WIDTH || mouseY < contentTop || mouseY > contentBottom) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        List<Row> rows = buildRows();
        int index = (int) ((mouseY - contentTop + scroll) / ROW_HEIGHT);
        if (index < 0 || index >= rows.size()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        Row row = rows.get(index);
        if (row.setting == null) {
            if (button == 1) {
                if (!EXPANDED.remove(row.module.getId())) {
                    EXPANDED.add(row.module.getId());
                }
            } else if (button == 0) {
                row.module.toggle();
            }
            return true;
        }

        if (row.setting instanceof BooleanSetting bool) {
            bool.toggle();
        } else if (row.setting instanceof ModeSetting mode) {
            if (button == 1) {
                mode.previous();
            } else {
                mode.next();
            }
        } else if (row.setting instanceof NumberSetting number) {
            draggingSlider = number;
            updateSlider(number, mouseX);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingSlider != null) {
            updateSlider(draggingSlider, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSlider = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll -= (int) (amount * ROW_HEIGHT);
        clampScroll(buildRows().size(), panelHeight - HEADER_HEIGHT - FOOTER_HEIGHT);
        return true;
    }

    @Override
    public void removed() {
        SimpleCheatClient.saveConfig();
        super.removed();
    }

    private void updateSlider(NumberSetting setting, double mouseX) {
        int barX1 = panelX + PADDING + 10;
        int barX2 = panelX + PANEL_WIDTH - PADDING;
        setting.setFromRatio((mouseX - barX1) / (double) (barX2 - barX1));
    }

    private void clampScroll(int rowCount, int visibleHeight) {
        int maxScroll = Math.max(0, rowCount * ROW_HEIGHT - visibleHeight);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
    }

    private List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        for (Module module : SimpleCheatClient.getModuleManager().getModules()) {
            rows.add(new Row(module, null));
            if (EXPANDED.contains(module.getId())) {
                for (Setting setting : module.getSettings()) {
                    rows.add(new Row(module, setting));
                }
            }
        }
        return rows;
    }

    private void drawBorder(DrawContext context, int x1, int y1, int x2, int y2) {
        context.fill(x1, y1, x2, y1 + 1, COLOR_BORDER);
        context.fill(x1, y2 - 1, x2, y2, COLOR_BORDER);
        context.fill(x1, y1, x1 + 1, y2, COLOR_BORDER);
        context.fill(x2 - 1, y1, x2, y2, COLOR_BORDER);
    }

    private record Row(Module module, Setting setting) {
    }
}
