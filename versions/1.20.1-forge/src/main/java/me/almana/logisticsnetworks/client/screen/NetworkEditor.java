package me.almana.logisticsnetworks.client.screen;

import java.util.function.IntConsumer;

import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.data.NetworkColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class NetworkEditor {

    private static final int W = 120;
    private static final int SQ = 100;
    private static final int SQ_H = 76;
    private static final int HUE_H = 10;
    private static final int PAD = 10;
    private static final int SV_COLS = 25;
    private static final int SV_ROWS = 19;
    private static final int HUE_CELLS = 50;

    private final Font font;
    private final int x;
    private final int y;
    private final IntConsumer onApply;
    private final Runnable onClose;

    private float hue;
    private float sat;
    private float val;
    private String hex;
    private boolean hexFocused;
    private int drag = -1;

    public NetworkEditor(Font font, int screenW, int screenH, int initialColor, IntConsumer onApply,
            Runnable onClose) {
        this.font = font;
        this.x = (screenW - W) / 2;
        this.y = (screenH - height()) / 2;
        this.onApply = onApply;
        this.onClose = onClose;
        setColor(initialColor);
    }

    private int height() {
        return PAD + 10 + SQ_H + 6 + HUE_H + 8 + 12 + 8 + 12 + PAD;
    }

    private int current() {
        return NetworkColors.hsvToRgb(hue, sat, val);
    }

    private void setColor(int rgb) {
        float[] hsv = NetworkColors.rgbToHsv(rgb);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
        this.hex = NetworkColors.toHex(rgb);
    }

    private int svX() { return x + (W - SQ) / 2; }
    private int svY() { return y + PAD + 10; }
    private int hueY() { return svY() + SQ_H + 6; }
    private int rowY() { return hueY() + HUE_H + 8; }
    private int btnY() { return rowY() + 12 + 8; }

    public void render(GuiGraphics g, int mx, int my, Theme t) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);

        ThemePaint.window(g, x, y, W, height(), t);
        ThemePaint.drawCentered(g, font, Component.translatable("gui.logisticsnetworks.node.color.title"),
                x + W / 2, y + PAD, t.accent());

        renderSvSquare(g);
        renderHueStrip(g);

        int rx = svX();
        g.fill(rx, rowY(), rx + 16, rowY() + 12, 0xFF000000 | current());
        g.renderOutline(rx, rowY(), 16, 12, t.border());

        int hexX = rx + 22;
        ThemePaint.sunkPanel(g, hexX, rowY(), SQ - 22, 12, t);
        g.drawString(font, "#" + hex + (hexFocused ? "_" : ""), hexX + 4, rowY() + 2, t.text(), false);

        int half = (SQ - 6) / 2;
        ThemePaint.button(g, font, rx, btnY(), half, 12, tr("gui.logisticsnetworks.node.color.random"),
                inRect(mx, my, rx, btnY(), half, 12), t);
        ThemePaint.button(g, font, rx + half + 6, btnY(), half, 12, tr("gui.logisticsnetworks.node.color.apply"),
                inRect(mx, my, rx + half + 6, btnY(), half, 12), t);

        g.pose().popPose();
    }

    private void renderSvSquare(GuiGraphics g) {
        float cellW = SQ / (float) SV_COLS;
        float cellH = SQ_H / (float) SV_ROWS;
        for (int column = 0; column < SV_COLS; column++) {
            for (int row = 0; row < SV_ROWS; row++) {
                float s = column / (float) (SV_COLS - 1);
                float v = 1f - row / (float) (SV_ROWS - 1);
                int color = 0xFF000000 | NetworkColors.hsvToRgb(hue, s, v);
                int x0 = svX() + Math.round(column * cellW);
                int y0 = svY() + Math.round(row * cellH);
                int x1 = svX() + Math.round((column + 1) * cellW);
                int y1 = svY() + Math.round((row + 1) * cellH);
                g.fill(x0, y0, x1, y1, color);
            }
        }
        int cx = svX() + Math.round(sat * SQ);
        int cy = svY() + Math.round((1f - val) * SQ_H);
        g.renderOutline(cx - 2, cy - 2, 4, 4, 0xFFFFFFFF);
        g.renderOutline(cx - 1, cy - 1, 2, 2, 0xFF000000);
    }

    private void renderHueStrip(GuiGraphics g) {
        float cellW = SQ / (float) HUE_CELLS;
        for (int i = 0; i < HUE_CELLS; i++) {
            int color = 0xFF000000 | NetworkColors.hsvToRgb(i / (float) (HUE_CELLS - 1), 1f, 1f);
            int x0 = svX() + Math.round(i * cellW);
            int x1 = svX() + Math.round((i + 1) * cellW);
            g.fill(x0, hueY(), x1, hueY() + HUE_H, color);
        }
        int cx = svX() + Math.round(hue * SQ);
        g.renderOutline(cx - 1, hueY() - 1, 3, HUE_H + 2, 0xFFFFFFFF);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return isInside(mx, my);
        }
        if (inRect(mx, my, svX(), svY(), SQ, SQ_H)) {
            drag = 0;
            updateSv(mx, my);
            hexFocused = false;
            return true;
        }
        if (inRect(mx, my, svX(), hueY(), SQ, HUE_H)) {
            drag = 1;
            updateHue(mx);
            hexFocused = false;
            return true;
        }
        int rx = svX();
        if (inRect(mx, my, rx + 22, rowY(), SQ - 22, 12)) {
            hexFocused = true;
            return true;
        }
        int half = (SQ - 6) / 2;
        if (inRect(mx, my, rx, btnY(), half, 12)) {
            setColor(NetworkColors.randomColor());
            hexFocused = false;
            return true;
        }
        if (inRect(mx, my, rx + half + 6, btnY(), half, 12)) {
            apply();
            return true;
        }
        if (!isInside(mx, my)) {
            onClose.run();
            return true;
        }
        hexFocused = false;
        return true;
    }

    public boolean mouseDragged(double mx, double my) {
        if (drag == 0) {
            updateSv(mx, my);
            return true;
        }
        if (drag == 1) {
            updateHue(mx);
            return true;
        }
        return false;
    }

    public boolean mouseReleased() {
        if (drag != -1) {
            drag = -1;
            return true;
        }
        return false;
    }

    public boolean charTyped(char c) {
        if (!hexFocused || hex.length() >= 6) {
            return hexFocused;
        }
        if (isHexChar(c)) {
            hex += Character.toUpperCase(c);
            syncFromHex();
        }
        return true;
    }

    public boolean keyPressed(int key) {
        if (key == 256) {
            onClose.run();
            return true;
        }
        if (!hexFocused) {
            return false;
        }
        if (key == 259 && !hex.isEmpty()) {
            hex = hex.substring(0, hex.length() - 1);
            syncFromHex();
            return true;
        }
        if (key == 257 || key == 335) {
            hexFocused = false;
            return true;
        }
        return true;
    }

    private void apply() {
        onApply.accept(current());
        onClose.run();
    }

    private void syncFromHex() {
        if (hex.length() == 6) {
            float[] hsv = NetworkColors.rgbToHsv(NetworkColors.parseHex(hex, current()));
            this.hue = hsv[0];
            this.sat = hsv[1];
            this.val = hsv[2];
        }
    }

    private void updateSv(double mx, double my) {
        sat = clamp01((float) (mx - svX()) / SQ);
        val = 1f - clamp01((float) (my - svY()) / SQ_H);
        hex = NetworkColors.toHex(current());
    }

    private void updateHue(double mx) {
        hue = clamp01((float) (mx - svX()) / SQ);
        hex = NetworkColors.toHex(current());
    }

    public boolean isInside(double mx, double my) {
        return inRect(mx, my, x, y, W, height());
    }

    private static boolean inRect(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh;
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private String tr(String key) {
        return Component.translatable(key).getString();
    }
}
