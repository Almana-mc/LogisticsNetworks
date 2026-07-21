package me.almana.logisticsnetworks.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class FlatEditBox extends EditBox {
    private final Font font;
    private int textColor = 0xE0E0E0;
    private int displayPos = 0;
    private int highlightPos = 0;

    public FlatEditBox(Font font, int x, int y, int w, int h, Component msg) {
        super(font, x, y, w, h, msg);
        this.font = font;
        setBordered(false);
    }

    @Override
    public void setTextColor(int c) {
        super.setTextColor(c);
        this.textColor = c;
    }

    @Override
    public void setValue(String s) {
        super.setValue(s);
        if (displayPos > s.length()) displayPos = s.length();
        if (highlightPos > s.length()) highlightPos = s.length();
    }

    @Override
    public void setHighlightPos(int p) {
        super.setHighlightPos(p);
        this.highlightPos = Mth.clamp(p, 0, getValue().length());
    }

    private void clampScroll() {
        String value = getValue();
        int cursorPos = getCursorPosition();
        int innerW = getWidth();
        if (displayPos > value.length()) displayPos = value.length();
        if (cursorPos < displayPos) displayPos = cursorPos;
        while (displayPos < value.length()) {
            String view = font.plainSubstrByWidth(value.substring(displayPos), innerW);
            if (cursorPos > displayPos + view.length()) {
                displayPos++;
            } else {
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (!isVisible()) return false;
        boolean hit = mx >= getX() && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight();
        setFocused(hit);
        if (isFocused() && hit && btn == 0) {
            int relX = Mth.floor(mx) - getX();
            String value = getValue();
            String view = font.plainSubstrByWidth(value.substring(displayPos), getWidth());
            int idx = font.plainSubstrByWidth(view, relX).length();
            moveCursorTo(displayPos + idx, false);
            return true;
        }
        return hit;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        if (!isVisible()) return;
        clampScroll();

        String value = getValue();
        int cursorPos = getCursorPosition();
        int hp = highlightPos;
        String view = font.plainSubstrByWidth(value.substring(displayPos), getWidth());

        int x = getX();
        int y = getY() + (getHeight() - 8) / 2;

        if (!view.isEmpty()) {
            g.drawString(font, view, x, y, textColor, false);
        }

        if (hp != cursorPos) {
            int from = Math.min(cursorPos, hp);
            int to = Math.max(cursorPos, hp);
            int viewEnd = displayPos + view.length();
            from = Math.max(displayPos, Math.min(from, viewEnd));
            to = Math.max(displayPos, Math.min(to, viewEnd));
            if (to > from) {
                int hx1 = x + font.width(value.substring(displayPos, from));
                int hx2 = x + font.width(value.substring(displayPos, to));
                g.fill(RenderType.guiTextHighlight(), hx1, y - 1, hx2, y + 9, 0xFF0000FF);
            }
        }

        if (isFocused() && (System.currentTimeMillis() / 500) % 2 == 0) {
            int viewEnd = displayPos + view.length();
            int cIdx = Math.max(displayPos, Math.min(cursorPos, viewEnd));
            int cx = x + font.width(value.substring(displayPos, cIdx));
            if (cursorPos >= value.length()) {
                g.drawString(font, "_", cx, y, textColor, false);
            } else {
                g.fill(cx, y - 1, cx + 1, y + 9, textColor);
            }
        }
    }
}
