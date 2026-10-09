package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.client.GuiGraphics;
import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.item.WrenchItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

final class WrenchColoursTab implements WrenchTab {
    private static final Identifier CASE_TEXTURE = Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID,
            "textures/item/wrench_case.png");
    private static final Identifier SCREEN_TEXTURE = Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID,
            "textures/item/wrench_screen.png");
    private static final Identifier BASE_TEXTURE = Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID,
            "textures/item/wrench_base.png");
    private static final String[] TARGETS = {
            "gui.logisticsnetworks.wrench.colors.case", "gui.logisticsnetworks.wrench.colors.screen" };
    private static final int[] DEFAULTS = { WrenchItem.DEFAULT_CASE_COLOR, WrenchItem.DEFAULT_SCREEN_COLOR };
    private static final int PICKER_W = 100;
    private static final int TARGET_W = 48;
    private static final int TARGET_H = 14;
    private static final int PREVIEW = 96;

    private final ColorPicker picker = new ColorPicker(PICKER_W, 76);
    private final int[] colors;
    private int target;
    private int x;
    private int y;

    WrenchColoursTab(int caseColor, int screenColor) {
        colors = new int[] { caseColor, screenColor };
        picker.load(caseColor);
    }

    @Override
    public void place(int x, int y) {
        this.x = x;
        this.y = y;
        picker.place(x, y + TARGET_H + 8);
    }

    @Override
    public void render(GuiGraphics g, Font font, int mouseX, int mouseY, Theme theme) {
        for (int i = 0; i < TARGETS.length; i++) {
            int tx = x + i * (TARGET_W + 4);
            boolean hovered = ColorPicker.inRect(mouseX, mouseY, tx, y, TARGET_W, TARGET_H);
            ThemePaint.button(g, font, tx, y, TARGET_W, TARGET_H, Component.translatable(TARGETS[i]).getString(),
                    hovered || target == i, theme);
            if (target == i) g.renderOutline(tx, y, TARGET_W, TARGET_H, theme.accent());
        }
        picker.render(g, font, theme);
        renderPreview(g, theme);
    }

    private void renderPreview(GuiGraphics g, Theme theme) {
        int px = x + PICKER_W + 14;
        int py = y + TARGET_H + 12;
        ThemePaint.sunkPanel(g, px - 4, py - 4, PREVIEW + 8, PREVIEW + 8, theme);
        int[] shown = colors();
        drawLayer(g, CASE_TEXTURE, px, py, shown[0]);
        drawLayer(g, SCREEN_TEXTURE, px, py, shown[1]);
        drawLayer(g, BASE_TEXTURE, px, py, 0xFFFFFF);
    }

    private static void drawLayer(GuiGraphics g, Identifier texture, int px, int py, int color) {
        g.raw().blit(RenderPipelines.GUI_TEXTURED, texture, px, py, 0f, 0f, PREVIEW, PREVIEW, 16, 16, 16, 16,
                0xFF000000 | color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY) {
        for (int i = 0; i < TARGETS.length; i++) {
            if (ColorPicker.inRect(mouseX, mouseY, x + i * (TARGET_W + 4), y, TARGET_W, TARGET_H)) {
                select(i);
                return true;
            }
        }
        return picker.mouseClicked(mouseX, mouseY);
    }

    private void select(int next) {
        if (next == target) return;
        colors[target] = picker.color();
        target = next;
        picker.load(colors[target]);
    }

    @Override
    public void reset() {
        System.arraycopy(DEFAULTS, 0, colors, 0, colors.length);
        picker.load(colors[target]);
    }

    @Override
    public ColorPicker picker() {
        return picker;
    }

    int[] colors() {
        colors[target] = picker.color();
        return colors;
    }
}
