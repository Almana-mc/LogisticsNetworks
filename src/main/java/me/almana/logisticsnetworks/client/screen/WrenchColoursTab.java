package me.almana.logisticsnetworks.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.item.WrenchItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class WrenchColoursTab {
    private static final ResourceLocation CASE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            LogisticsNetworks.MOD_ID, "textures/item/wrench_case.png");
    private static final ResourceLocation SCREEN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            LogisticsNetworks.MOD_ID, "textures/item/wrench_screen.png");
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

    void place(int x, int y) {
        this.x = x;
        this.y = y;
        picker.place(x, y + TARGET_H + 8);
    }

    void render(GuiGraphics g, Font font, int mouseX, int mouseY, Theme theme) {
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
        g.pose().pushPose();
        g.pose().translate(px, py, 0);
        g.pose().scale(PREVIEW / 16f, PREVIEW / 16f, 1f);
        drawLayer(g, CASE_TEXTURE, shown[0]);
        drawLayer(g, SCREEN_TEXTURE, shown[1]);
        g.pose().popPose();
    }

    private static void drawLayer(GuiGraphics g, ResourceLocation texture, int color) {
        RenderSystem.setShaderColor(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f, 1f);
        g.blit(texture, 0, 0, 0f, 0f, 16, 16, 16, 16);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    boolean mouseClicked(double mouseX, double mouseY) {
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

    void reset() {
        System.arraycopy(DEFAULTS, 0, colors, 0, colors.length);
        picker.load(colors[target]);
    }

    ColorPicker picker() {
        return picker;
    }

    int[] colors() {
        colors[target] = picker.color();
        return colors;
    }
}
