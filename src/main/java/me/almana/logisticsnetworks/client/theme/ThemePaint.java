package me.almana.logisticsnetworks.client.theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class ThemePaint {

    public static void drawCentered(GuiGraphics g, Font font, String text, int cx, int y, int color) {
        int w = font.width(text);
        g.drawString(font, text, cx - w / 2, y, color, false);
    }

    public static void drawCentered(GuiGraphics g, Font font, Component text, int cx, int y, int color) {
        int w = font.width(text);
        g.drawString(font, text, cx - w / 2, y, color, false);
    }

    public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color, boolean sharp) {
        if (sharp || radius <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        int r = Math.min(radius, Math.min(w, h) / 2);
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        if (r >= 2) {
            g.fill(x + 1, y + 1, x + r, y + r, color);
            g.fill(x + w - r, y + 1, x + w - 1, y + r, color);
            g.fill(x + 1, y + h - r, x + r, y + h - 1, color);
            g.fill(x + w - r, y + h - r, x + w - 1, y + h - 1, color);
        }
    }

    public static void roundOutline(GuiGraphics g, int x, int y, int w, int h, int radius, int color, boolean sharp) {
        if (sharp || radius <= 0) {
            g.renderOutline(x, y, w, h, color);
            return;
        }
        int r = Math.min(radius, Math.min(w, h) / 2);
        g.fill(x + r, y, x + w - r, y + 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        g.fill(x, y + r, x + 1, y + h - r, color);
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);
        if (r >= 2) {
            g.fill(x + 1, y + 1, x + r, y + 2, color);
            g.fill(x + 1, y + 1, x + 2, y + r, color);
            g.fill(x + w - r, y + 1, x + w - 1, y + 2, color);
            g.fill(x + w - 2, y + 1, x + w - 1, y + r, color);
            g.fill(x + 1, y + h - 2, x + r, y + h - 1, color);
            g.fill(x + 1, y + h - r, x + 2, y + h - 1, color);
            g.fill(x + w - r, y + h - 2, x + w - 1, y + h - 1, color);
            g.fill(x + w - 2, y + h - r, x + w - 1, y + h - 1, color);
        }
    }

    public static void window(GuiGraphics g, int x, int y, int w, int h, Theme t) {
        if (t.hardShadow()) {
            g.fill(x + 3, y + 3, x + w + 3, y + h + 3, t.shadow());
        } else {
            g.fill(x + 2, y + h, x + w + 2, y + h + 3, softShadow(t.shadow()));
            g.fill(x + 2, y + 2, x + w + 2, y + h + 2, veilShadow(t.shadow()));
        }
        roundRect(g, x, y, w, h, 3, t.surface(), t.sharpCorners());
        roundOutline(g, x, y, w, h, 3, t.border(), t.sharpCorners());
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h, Theme t) {
        roundRect(g, x, y, w, h, 3, t.surface2(), t.sharpCorners());
        roundOutline(g, x, y, w, h, 3, t.border(), t.sharpCorners());
    }

    public static void sunkPanel(GuiGraphics g, int x, int y, int w, int h, Theme t) {
        roundRect(g, x, y, w, h, 2, t.surfaceSunken(), t.sharpCorners());
        roundOutline(g, x, y, w, h, 2, t.border(), t.sharpCorners());
    }

    public static void divider(GuiGraphics g, int x, int y, int w, Theme t) {
        g.fill(x, y, x + w, y + 1, t.border());
    }

    public static void slot(GuiGraphics g, int x, int y, int size, Theme t) {
        g.fill(x, y, x + size, y + size, t.slotBg());
        g.renderOutline(x, y, size, size, t.slotBorder());
        g.fill(x + 1, y + 1, x + size - 1, y + 2, innerHighlight(t));
    }

    public static void pill(GuiGraphics g, Font font, int x, int y, String text,
                             Theme.Variant variant, boolean hovered, Theme t) {
        int textW = font.width(text);
        int dotW = 6;
        int padX = 4;
        int w = textW + dotW + padX * 2 + 2;
        int h = 10;
        int bg = t.variantBg(variant);
        int fg = t.variantFg(variant);
        if (hovered) bg = brighten(bg, 0x12);
        roundRect(g, x, y, w, h, 5, bg, t.sharpCorners());
        if (t.sharpCorners()) {
            roundOutline(g, x, y, w, h, 0, t.border(), true);
        }
        g.fill(x + padX, y + h / 2 - 1, x + padX + 2, y + h / 2 + 1, fg);
        g.drawString(font, text, x + padX + dotW, y + 1, fg, false);
    }

    public static int pillWidth(Font font, String text) {
        return font.width(text) + 6 + 8 + 2;
    }

    public static void tab(GuiGraphics g, Font font, int x, int y, int w, int h,
                            String label, boolean active, boolean hasDot, boolean hovered, Theme t) {
        int bg = active ? t.tabActiveBg() : t.surface2();
        int fg = active ? t.tabActiveFg() : (hovered ? t.text() : t.textMuted());
        int border = active ? t.tabActiveBg() : t.border();
        if (t.hardShadow() && active) {
            g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0xFF000000);
        }
        roundRect(g, x, y, w, h, 2, bg, t.sharpCorners());
        if (!active) roundOutline(g, x, y, w, h, 2, border, t.sharpCorners());
        else if (t.sharpCorners()) roundOutline(g, x, y, w, h, 0, t.border(), true);
        drawCentered(g, font, label, x + w / 2, y + (h - 7) / 2, fg);
        if (hasDot) {
            int dx = x + w - 4;
            int dy = y + 2;
            g.fill(dx - 1, dy, dx + 2, dy + 3, t.accent());
        }
    }

    public static void visibleToggle(GuiGraphics g, Font font, int x, int y, int w, int h,
                                      String label, boolean on, boolean hovered, Theme t) {
        int bg = on ? t.accentSoft() : t.surface();
        int border = on ? t.accent() : t.borderStrong();
        int fg = on ? t.accent() : t.text();
        if (hovered) bg = brighten(bg, 0x10);
        roundRect(g, x, y, w, h, h / 2, bg, t.sharpCorners());
        roundOutline(g, x, y, w, h, h / 2, border, t.sharpCorners());
        int dotX = x + 4;
        int dotY = y + h / 2 - 2;
        int dotColor = on ? t.accent() : t.textSubtle();
        g.fill(dotX, dotY, dotX + 4, dotY + 4, dotColor);
        if (on && t.glow()) {
            int glow = (t.accent() & 0x00FFFFFF) | 0x40000000;
            g.fill(dotX - 1, dotY - 1, dotX + 5, dotY, glow);
            g.fill(dotX - 1, dotY + 4, dotX + 5, dotY + 5, glow);
            g.fill(dotX - 1, dotY, dotX, dotY + 4, glow);
            g.fill(dotX + 4, dotY, dotX + 5, dotY + 4, glow);
        }
        g.drawString(font, label, x + 10, y + (h - 7) / 2, fg, false);
    }

    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h,
                               String label, boolean hovered, Theme t) {
        int bg = hovered ? brighten(t.surface2(), 0x10) : t.surface2();
        int border = hovered ? t.accent() : t.borderStrong();
        int fg = hovered ? t.text() : t.textMuted();
        if (t.hardShadow()) {
            g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0xFF000000);
        }
        roundRect(g, x, y, w, h, 2, bg, t.sharpCorners());
        roundOutline(g, x, y, w, h, 2, border, t.sharpCorners());
        drawCentered(g, font, label, x + w / 2, y + (h - 7) / 2, fg);
    }

    public static void ghostButton(GuiGraphics g, Font font, int x, int y, int w, int h,
                                    String label, boolean hovered, Theme t) {
        int fg = hovered ? t.text() : t.textMuted();
        int border = hovered ? t.borderStrong() : t.border();
        roundOutline(g, x, y, w, h, 2, border, t.sharpCorners());
        drawCentered(g, font, label, x + w / 2, y + (h - 7) / 2, fg);
    }

    public static void chip(GuiGraphics g, Font font, int x, int y, String text, Theme t) {
        int w = font.width(text) + 12;
        int h = 12;
        roundRect(g, x, y, w, h, 3, t.accentSoft(), t.sharpCorners());
        g.drawString(font, text, x + 6, y + (h - 7) / 2, t.accent(), false);
    }

    public static int chipWidth(Font font, String text) {
        return font.width(text) + 12;
    }

    public static void searchBox(GuiGraphics g, Font font, int x, int y, int w, int h,
                                  String placeholder, boolean focused, Theme t) {
        int bg = t.surface2();
        int border = focused ? t.accent() : t.border();
        roundRect(g, x, y, w, h, 2, bg, t.sharpCorners());
        roundOutline(g, x, y, w, h, 2, border, t.sharpCorners());
        int gx = x + 3;
        int gy = y + h / 2 - 2;
        g.renderOutline(gx, gy, 4, 4, t.textSubtle());
        g.fill(gx + 3, gy + 3, gx + 5, gy + 5, t.textSubtle());
        g.drawString(font, placeholder, x + 10, y + (h - 7) / 2, t.textSubtle(), false);
    }

    public static void compass(GuiGraphics g, int x, int y, int dirDeg, Theme t) {
        int size = 8;
        roundRect(g, x, y, size, size, size / 2, t.surface(), t.sharpCorners());
        roundOutline(g, x, y, size, size, size / 2, t.borderStrong(), t.sharpCorners());
        int cx = x + size / 2;
        int cy = y + size / 2;
        int ax = cx;
        int ay = cy - 2;
        int d = dirDeg % 360;
        if (d == 0) { ax = cx; ay = cy - 3; }
        else if (d == 90) { ax = cx + 2; ay = cy; }
        else if (d == 180) { ax = cx; ay = cy + 2; }
        else if (d == 270) { ax = cx - 3; ay = cy; }
        g.fill(ax, ay, ax + 1, ay + 1, t.accent());
    }

    public static void setLabelBtn(GuiGraphics g, Font font, int x, int y, int w, int h,
                                    String label, boolean hovered, Theme t) {
        int bg = hovered ? brighten(t.surface2(), 0x10) : t.surface2();
        int border = t.borderStrong();
        int fg = t.text();
        if (t.hardShadow()) {
            g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0xFF000000);
        }
        roundRect(g, x, y, w, h, 2, bg, t.sharpCorners());
        roundOutline(g, x, y, w, h, 2, border, t.sharpCorners());
        int px = x + 4;
        int py = y + h / 2 - 2;
        g.fill(px + 2, py, px + 3, py + 1, fg);
        g.fill(px + 1, py + 1, px + 3, py + 2, fg);
        g.fill(px, py + 2, px + 2, py + 3, fg);
        g.fill(px, py + 3, px + 1, py + 4, fg);
        g.drawString(font, label, x + 10, y + (h - 7) / 2, fg, false);
    }

    public static void stepper(GuiGraphics g, Font font, int x, int y, int w, int h,
                                int value, boolean hoverMinus, boolean hoverPlus, Theme t) {
        roundRect(g, x, y, w, h, 2, t.surface(), t.sharpCorners());
        roundOutline(g, x, y, w, h, 2, t.border(), t.sharpCorners());
        int thirdW = w / 3;
        if (hoverMinus) g.fill(x + 1, y + 1, x + thirdW, y + h - 1, t.surfaceSunken());
        if (hoverPlus)  g.fill(x + 2 * thirdW, y + 1, x + w - 1, y + h - 1, t.surfaceSunken());
        g.fill(x + thirdW, y + 1, x + thirdW + 1, y + h - 1, t.border());
        g.fill(x + 2 * thirdW, y + 1, x + 2 * thirdW + 1, y + h - 1, t.border());
        drawCentered(g, font, "-", x + thirdW / 2, y + (h - 7) / 2, hoverMinus ? t.text() : t.textMuted());
        String num = String.valueOf(value);
        drawCentered(g, font, num, x + thirdW + thirdW / 2, y + (h - 7) / 2, t.text());
        drawCentered(g, font, "+", x + 2 * thirdW + thirdW / 2, y + (h - 7) / 2, hoverPlus ? t.text() : t.textMuted());
    }

    public static void segmented(GuiGraphics g, Font font, int x, int y, int h,
                                  String[] labels, int activeIdx, Theme t) {
        int totalW = 0;
        int[] widths = new int[labels.length];
        for (int i = 0; i < labels.length; i++) {
            widths[i] = font.width(labels[i]) + 10;
            totalW += widths[i];
        }
        totalW += 4;
        roundRect(g, x, y, totalW, h, 2, t.surfaceSunken(), t.sharpCorners());
        roundOutline(g, x, y, totalW, h, 2, t.border(), t.sharpCorners());
        int cx = x + 2;
        for (int i = 0; i < labels.length; i++) {
            int fg;
            if (i == activeIdx) {
                roundRect(g, cx, y + 1, widths[i], h - 2, 2, t.surface(), t.sharpCorners());
                fg = t.text();
            } else {
                fg = t.textMuted();
            }
            drawCentered(g, font, labels[i], cx + widths[i] / 2, y + (h - 7) / 2, fg);
            cx += widths[i];
        }
    }

    public static int segmentedWidth(Font font, String[] labels) {
        int totalW = 4;
        for (String l : labels) totalW += font.width(l) + 10;
        return totalW;
    }

    public static void modalVeil(GuiGraphics g, int x, int y, int w, int h, Theme t) {
        int base = t.bg() & 0x00FFFFFF;
        g.fill(x, y, x + w, y + h, 0xC0000000 | base);
    }

    public static void swatchPreview(GuiGraphics g, int x, int y, int w, int h, Theme preview, Theme frame) {
        roundRect(g, x, y, w, h, 2, preview.surface(), frame.sharpCorners());
        int half = w / 2;
        g.fill(x + half, y, x + w, y + h, preview.accent());
        roundOutline(g, x, y, w, h, 2, frame.border(), frame.sharpCorners());
    }

    public static void glowText(GuiGraphics g, Font font, String text, int x, int y, int color, Theme t) {
        g.drawString(font, text, x, y, color, false);
    }

    public static void componentText(GuiGraphics g, Font font, Component c, int x, int y, int color, Theme t) {
        g.drawString(font, c.getString(), x, y, color, false);
    }

    private static int brighten(int argb, int delta) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(0xFF, ((argb >> 16) & 0xFF) + delta);
        int gC = Math.min(0xFF, ((argb >> 8) & 0xFF) + delta);
        int b = Math.min(0xFF, (argb & 0xFF) + delta);
        return (a << 24) | (r << 16) | (gC << 8) | b;
    }

    private static int softShadow(int shadow) {
        int a = Math.max(0x20, (shadow >>> 24) & 0xFF) / 2;
        return (a << 24) | (shadow & 0x00FFFFFF);
    }

    private static int veilShadow(int shadow) {
        int a = (shadow >>> 24) & 0xFF;
        int half = Math.max(0x10, a / 4);
        return (half << 24) | (shadow & 0x00FFFFFF);
    }

    private static int innerHighlight(Theme t) {
        return 0x14FFFFFF;
    }

    private ThemePaint() {}
}
