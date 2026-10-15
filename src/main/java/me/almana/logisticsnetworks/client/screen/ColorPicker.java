package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.data.NetworkColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

final class ColorPicker {
    private static final int SV_COLS = 25;
    private static final int SV_ROWS = 19;
    private static final int HUE_CELLS = 50;
    private static final int HUE_H = 10;

    private final int width;
    private final int svHeight;
    private int x;
    private int y;
    private float hue;
    private float sat;
    private float val;
    private String hex;
    private boolean hexFocused;
    private int drag = -1;

    ColorPicker(int width, int svHeight) {
        this.width = width;
        this.svHeight = svHeight;
    }

    void place(int x, int y) {
        this.x = x;
        this.y = y;
    }

    int bottom() {
        return rowY() + 12;
    }

    int color() {
        return NetworkColors.hsvToRgb(hue, sat, val);
    }

    void load(int rgb) {
        float[] hsv = NetworkColors.rgbToHsv(rgb);
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
        hex = NetworkColors.toHex(rgb);
        hexFocused = false;
    }

    void blur() {
        hexFocused = false;
    }

    private int hueY() {
        return y + svHeight + 6;
    }

    private int rowY() {
        return hueY() + HUE_H + 8;
    }

    void render(GuiGraphics g, Font font, Theme theme) {
        renderSvSquare(g);
        renderHueStrip(g);
        g.fill(x, rowY(), x + 16, rowY() + 12, 0xFF000000 | color());
        g.renderOutline(x, rowY(), 16, 12, theme.border());
        ThemePaint.sunkPanel(g, x + 22, rowY(), width - 22, 12, theme);
        g.drawString(font, "#" + hex + (hexFocused ? "_" : ""), x + 26, rowY() + 2, theme.text(), false);
    }

    private void renderSvSquare(GuiGraphics g) {
        float cellW = width / (float) SV_COLS;
        float cellH = svHeight / (float) SV_ROWS;
        for (int column = 0; column < SV_COLS; column++) {
            for (int row = 0; row < SV_ROWS; row++) {
                float s = column / (float) (SV_COLS - 1);
                float v = 1f - row / (float) (SV_ROWS - 1);
                int color = 0xFF000000 | NetworkColors.hsvToRgb(hue, s, v);
                g.fill(x + Math.round(column * cellW), y + Math.round(row * cellH),
                        x + Math.round((column + 1) * cellW), y + Math.round((row + 1) * cellH), color);
            }
        }
        int cx = x + Math.round(sat * width);
        int cy = y + Math.round((1f - val) * svHeight);
        g.renderOutline(cx - 2, cy - 2, 4, 4, 0xFFFFFFFF);
        g.renderOutline(cx - 1, cy - 1, 2, 2, 0xFF000000);
    }

    private void renderHueStrip(GuiGraphics g) {
        float cellW = width / (float) HUE_CELLS;
        for (int i = 0; i < HUE_CELLS; i++) {
            int color = 0xFF000000 | NetworkColors.hsvToRgb(i / (float) (HUE_CELLS - 1), 1f, 1f);
            g.fill(x + Math.round(i * cellW), hueY(), x + Math.round((i + 1) * cellW), hueY() + HUE_H, color);
        }
        int cx = x + Math.round(hue * width);
        g.renderOutline(cx - 1, hueY() - 1, 3, HUE_H + 2, 0xFFFFFFFF);
    }

    boolean mouseClicked(double mx, double my) {
        hexFocused = false;
        if (inRect(mx, my, x, y, width, svHeight)) {
            drag = 0;
            updateSv(mx, my);
        } else if (inRect(mx, my, x, hueY(), width, HUE_H)) {
            drag = 1;
            updateHue(mx);
        } else if (inRect(mx, my, x + 22, rowY(), width - 22, 12)) {
            hexFocused = true;
        } else {
            return false;
        }
        return true;
    }

    boolean mouseDragged(double mx, double my) {
        if (drag == 0) updateSv(mx, my);
        else if (drag == 1) updateHue(mx);
        return drag != -1;
    }

    boolean mouseReleased() {
        boolean dragging = drag != -1;
        drag = -1;
        return dragging;
    }

    boolean charTyped(char c) {
        if (!hexFocused) return false;
        if (hex.length() < 6 && isHexChar(c)) {
            hex += Character.toUpperCase(c);
            syncFromHex();
        }
        return true;
    }

    boolean keyPressed(int key) {
        if (!hexFocused) return false;
        if (key == 259 && !hex.isEmpty()) {
            hex = hex.substring(0, hex.length() - 1);
            syncFromHex();
        } else if (key == 257 || key == 335 || key == 256) {
            hexFocused = false;
        }
        return true;
    }

    private void syncFromHex() {
        if (hex.length() == 6) {
            float[] hsv = NetworkColors.rgbToHsv(NetworkColors.parseHex(hex, color()));
            hue = hsv[0];
            sat = hsv[1];
            val = hsv[2];
        }
    }

    private void updateSv(double mx, double my) {
        sat = clamp01((float) (mx - x) / width);
        val = 1f - clamp01((float) (my - y) / svHeight);
        hex = NetworkColors.toHex(color());
    }

    private void updateHue(double mx) {
        hue = clamp01((float) (mx - x) / width);
        hex = NetworkColors.toHex(color());
    }

    static boolean inRect(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh;
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
