package me.almana.logisticsnetworks.client.theme;

import me.almana.logisticsnetworks.client.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public final class ThemePaint {

    public static void drawCentered(GuiGraphics graphics, Font font, String text, int centerX, int y, int color) {
        int width = font.width(text);
        graphics.drawString(font, text, centerX - width / 2, y, color, false);
    }

    public static void drawCentered(GuiGraphics graphics, Font font, Component text, int centerX, int y, int color) {
        int width = font.width(text);
        graphics.drawString(font, text, centerX - width / 2, y, color, false);
    }

    public static void roundRect(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color,
            boolean sharp) {
        if (sharp || radius <= 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        int r = Math.min(radius, Math.min(width, height) / 2);
        graphics.fill(x + r, y, x + width - r, y + height, color);
        graphics.fill(x, y + r, x + r, y + height - r, color);
        graphics.fill(x + width - r, y + r, x + width, y + height - r, color);
        if (r >= 2) {
            graphics.fill(x + 1, y + 1, x + r, y + r, color);
            graphics.fill(x + width - r, y + 1, x + width - 1, y + r, color);
            graphics.fill(x + 1, y + height - r, x + r, y + height - 1, color);
            graphics.fill(x + width - r, y + height - r, x + width - 1, y + height - 1, color);
        }
    }

    public static void roundOutline(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color,
            boolean sharp) {
        if (sharp || radius <= 0) {
            graphics.renderOutline(x, y, width, height, color);
            return;
        }
        int r = Math.min(radius, Math.min(width, height) / 2);
        graphics.fill(x + r, y, x + width - r, y + 1, color);
        graphics.fill(x + r, y + height - 1, x + width - r, y + height, color);
        graphics.fill(x, y + r, x + 1, y + height - r, color);
        graphics.fill(x + width - 1, y + r, x + width, y + height - r, color);
        if (r >= 2) {
            graphics.fill(x + 1, y + 1, x + r, y + 2, color);
            graphics.fill(x + 1, y + 1, x + 2, y + r, color);
            graphics.fill(x + width - r, y + 1, x + width - 1, y + 2, color);
            graphics.fill(x + width - 2, y + 1, x + width - 1, y + r, color);
            graphics.fill(x + 1, y + height - 2, x + r, y + height - 1, color);
            graphics.fill(x + 1, y + height - r, x + 2, y + height - 1, color);
            graphics.fill(x + width - r, y + height - 2, x + width - 1, y + height - 1, color);
            graphics.fill(x + width - 2, y + height - r, x + width - 1, y + height - 1, color);
        }
    }

    public static void window(GuiGraphics graphics, int x, int y, int width, int height, Theme theme) {
        if (theme.hardShadow()) {
            graphics.fill(x + 3, y + 3, x + width + 3, y + height + 3, theme.shadow());
        } else {
            graphics.fill(x + 2, y + height, x + width + 2, y + height + 3, softShadow(theme.shadow()));
            graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, veilShadow(theme.shadow()));
        }
        roundRect(graphics, x, y, width, height, 3, theme.surface(), theme.sharpCorners());
        roundOutline(graphics, x, y, width, height, 3, theme.border(), theme.sharpCorners());
    }

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height, Theme theme) {
        roundRect(graphics, x, y, width, height, 3, theme.surface2(), theme.sharpCorners());
        roundOutline(graphics, x, y, width, height, 3, theme.border(), theme.sharpCorners());
    }

    public static void sunkPanel(GuiGraphics graphics, int x, int y, int width, int height, Theme theme) {
        roundRect(graphics, x, y, width, height, 2, theme.surfaceSunken(), theme.sharpCorners());
        roundOutline(graphics, x, y, width, height, 2, theme.border(), theme.sharpCorners());
    }

    public static void divider(GuiGraphics graphics, int x, int y, int width, Theme theme) {
        graphics.fill(x, y, x + width, y + 1, theme.border());
    }

    public static void slot(GuiGraphics graphics, int x, int y, int size, Theme theme) {
        graphics.fill(x, y, x + size, y + size, theme.slotBg());
        graphics.renderOutline(x, y, size, size, theme.slotBorder());
        graphics.fill(x + 1, y + 1, x + size - 1, y + 2, innerHighlight());
    }

    public static void pill(GuiGraphics graphics, Font font, int x, int y, String text, Theme.Variant variant,
            boolean hovered, Theme theme) {
        int textWidth = font.width(text);
        int dotWidth = 6;
        int paddingX = 4;
        int width = textWidth + dotWidth + paddingX * 2 + 2;
        int height = 10;
        int bg = theme.variantBg(variant);
        int fg = theme.variantFg(variant);
        if (hovered) {
            bg = brighten(bg, 0x12);
        }
        roundRect(graphics, x, y, width, height, 5, bg, theme.sharpCorners());
        if (theme.sharpCorners()) {
            roundOutline(graphics, x, y, width, height, 0, theme.border(), true);
        }
        graphics.fill(x + paddingX, y + height / 2 - 1, x + paddingX + 2, y + height / 2 + 1, fg);
        graphics.drawString(font, text, x + paddingX + dotWidth, y + 1, fg, false);
    }

    public static int pillWidth(Font font, String text) {
        return font.width(text) + 16;
    }

    public static void tab(GuiGraphics graphics, Font font, int x, int y, int width, int height, String label,
            boolean active, boolean hasDot, boolean hovered, Theme theme) {
        int bg = active ? theme.tabActiveBg() : theme.surface2();
        int fg = active ? theme.tabActiveFg() : (hovered ? theme.text() : theme.textMuted());
        int border = active ? theme.tabActiveBg() : theme.border();
        if (theme.hardShadow() && active) {
            graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, 0xFF000000);
        }
        roundRect(graphics, x, y, width, height, 2, bg, theme.sharpCorners());
        if (!active) {
            roundOutline(graphics, x, y, width, height, 2, border, theme.sharpCorners());
        } else if (theme.sharpCorners()) {
            roundOutline(graphics, x, y, width, height, 0, theme.border(), true);
        }
        drawCentered(graphics, font, label, x + width / 2, y + (height - 7) / 2, fg);
        if (hasDot) {
            int dotX = x + width - 4;
            int dotY = y + 2;
            graphics.fill(dotX - 1, dotY, dotX + 2, dotY + 3, theme.accent());
        }
    }

    public static void visibleToggle(GuiGraphics graphics, Font font, int x, int y, int width, int height,
            String label, boolean on, boolean hovered, Theme theme) {
        int bg = on ? theme.accentSoft() : theme.surface();
        int border = on ? theme.accent() : theme.borderStrong();
        int fg = on ? theme.accent() : theme.text();
        if (hovered) {
            bg = brighten(bg, 0x10);
        }
        roundRect(graphics, x, y, width, height, height / 2, bg, theme.sharpCorners());
        roundOutline(graphics, x, y, width, height, height / 2, border, theme.sharpCorners());
        int dotX = x + 4;
        int dotY = y + height / 2 - 2;
        int dotColor = on ? theme.accent() : theme.textSubtle();
        graphics.fill(dotX, dotY, dotX + 4, dotY + 4, dotColor);
        if (on && theme.glow()) {
            int glow = (theme.accent() & 0x00FFFFFF) | 0x40000000;
            graphics.fill(dotX - 1, dotY - 1, dotX + 5, dotY, glow);
            graphics.fill(dotX - 1, dotY + 4, dotX + 5, dotY + 5, glow);
            graphics.fill(dotX - 1, dotY, dotX, dotY + 4, glow);
            graphics.fill(dotX + 4, dotY, dotX + 5, dotY + 4, glow);
        }
        graphics.drawString(font, label, x + 10, y + (height - 7) / 2, fg, false);
    }

    public static void button(GuiGraphics graphics, Font font, int x, int y, int width, int height, String label,
            boolean hovered, Theme theme) {
        int bg = hovered ? brighten(theme.surface2(), 0x10) : theme.surface2();
        int border = hovered ? theme.accent() : theme.borderStrong();
        int fg = hovered ? theme.text() : theme.textMuted();
        if (theme.hardShadow()) {
            graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, 0xFF000000);
        }
        roundRect(graphics, x, y, width, height, 2, bg, theme.sharpCorners());
        roundOutline(graphics, x, y, width, height, 2, border, theme.sharpCorners());
        drawCentered(graphics, font, label, x + width / 2, y + (height - 7) / 2, fg);
    }

    public static void ghostButton(GuiGraphics graphics, Font font, int x, int y, int width, int height,
            String label, boolean hovered, Theme theme) {
        int fg = hovered ? theme.text() : theme.textMuted();
        int border = hovered ? theme.borderStrong() : theme.border();
        roundOutline(graphics, x, y, width, height, 2, border, theme.sharpCorners());
        drawCentered(graphics, font, label, x + width / 2, y + (height - 7) / 2, fg);
    }

    public static void chip(GuiGraphics graphics, Font font, int x, int y, String text, Theme theme) {
        int width = font.width(text) + 12;
        int height = 12;
        roundRect(graphics, x, y, width, height, 3, theme.accentSoft(), theme.sharpCorners());
        graphics.drawString(font, text, x + 6, y + (height - 7) / 2, theme.accent(), false);
    }

    public static void setLabelBtn(GuiGraphics graphics, Font font, int x, int y, int width, int height,
            String label, boolean hovered, Theme theme) {
        int bg = hovered ? brighten(theme.surface2(), 0x10) : theme.surface2();
        int border = theme.borderStrong();
        int fg = theme.text();
        if (theme.hardShadow()) {
            graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, 0xFF000000);
        }
        roundRect(graphics, x, y, width, height, 2, bg, theme.sharpCorners());
        roundOutline(graphics, x, y, width, height, 2, border, theme.sharpCorners());
        int px = x + 4;
        int py = y + height / 2 - 2;
        graphics.fill(px + 2, py, px + 3, py + 1, fg);
        graphics.fill(px + 1, py + 1, px + 3, py + 2, fg);
        graphics.fill(px, py + 2, px + 2, py + 3, fg);
        graphics.fill(px, py + 3, px + 1, py + 4, fg);
        graphics.drawString(font, label, x + 10, y + (height - 7) / 2, fg, false);
    }

    public static void modalVeil(GuiGraphics graphics, int x, int y, int width, int height, Theme theme) {
        int base = theme.bg() & 0x00FFFFFF;
        graphics.fill(x, y, x + width, y + height, 0xC0000000 | base);
    }

    public static void swatchPreview(GuiGraphics graphics, int x, int y, int width, int height, Theme preview,
            Theme frame) {
        roundRect(graphics, x, y, width, height, 2, preview.surface(), frame.sharpCorners());
        int half = width / 2;
        graphics.fill(x + half, y, x + width, y + height, preview.accent());
        roundOutline(graphics, x, y, width, height, 2, frame.border(), frame.sharpCorners());
    }

    private static int brighten(int argb, int delta) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = Math.min(0xFF, ((argb >> 16) & 0xFF) + delta);
        int green = Math.min(0xFF, ((argb >> 8) & 0xFF) + delta);
        int blue = Math.min(0xFF, (argb & 0xFF) + delta);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int softShadow(int shadow) {
        int alpha = Math.max(0x20, (shadow >>> 24) & 0xFF) / 2;
        return (alpha << 24) | (shadow & 0x00FFFFFF);
    }

    private static int veilShadow(int shadow) {
        int alpha = (shadow >>> 24) & 0xFF;
        int quarter = Math.max(0x10, alpha / 4);
        return (quarter << 24) | (shadow & 0x00FFFFFF);
    }

    private static int innerHighlight() {
        return 0x14FFFFFF;
    }

    private ThemePaint() {
    }
}
