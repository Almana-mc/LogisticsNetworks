package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.data.FlowResource;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

final class FlowChart {

    private static final int AXIS_W = 30;
    private static final String NOW = "gui.logisticsnetworks.computer.flow.now";
    private static final String AGO = "gui.logisticsnetworks.computer.flow.ago";

    private float shownMax;
    private int hoverAgo = -1;

    void render(GuiGraphics g, Font font, Theme t, FlowHistory history, int channel, int window,
            @Nullable FlowResource filter, int color, int x, int y, int w, int h, int mouseX, int mouseY) {
        ThemePaint.sunkPanel(g, x, y, w, h, t);
        int plotX = x + AXIS_W;
        int plotY = y + 5;
        int plotW = w - AXIS_W - 4;
        int plotH = h - 18;
        long target = FlowHistory.niceStep(history.peak(channel, window, filter)) * 4;
        shownMax = shownMax <= 0 ? target : shownMax + (target - shownMax) * 0.2f;

        renderGrid(g, font, t, plotX, plotY, plotW, plotH);
        renderBars(g, t, history, channel, window, filter, color, plotX, plotY, plotW, plotH, mouseX, mouseY);
        renderTimeLabels(g, font, t, window, plotX, y + h - 11, plotW);
    }

    void renderTooltip(GuiGraphics g, Font font, FlowHistory history, int channel, @Nullable FlowResource filter,
            String unit, int mouseX, int mouseY) {
        if (hoverAgo < 0) return;
        String when = hoverAgo == 0 ? tr(NOW) : tr(AGO, hoverAgo);
        long value = history.value(channel, hoverAgo, filter);
        g.renderTooltip(font, Component.literal(when + "  " + FlowHistory.format(value) + " " + unit), mouseX, mouseY);
    }

    private void renderGrid(GuiGraphics g, Font font, Theme t, int x, int y, int w, int h) {
        long step = FlowHistory.niceStep((long) shownMax);
        for (long value = 0; value <= shownMax; value += step) {
            int gy = y + h - Math.round(value / shownMax * h);
            g.fill(x, gy, x + w, gy + 1, t.border());
            String label = FlowHistory.format(value);
            g.drawString(font, label, x - 3 - font.width(label), gy - 4, t.textSubtle(), false);
        }
    }

    private void renderBars(GuiGraphics g, Theme t, FlowHistory history, int channel, int window,
            @Nullable FlowResource filter, int color, int x, int y, int w, int h, int mouseX, int mouseY) {
        hoverAgo = -1;
        int gap = w / window >= 3 ? 1 : 0;
        int hoverColor = (t.text() & 0x00FFFFFF) | 0x18000000;
        for (int i = 0; i < window; i++) {
            int x0 = x + i * w / window;
            int x1 = x + (i + 1) * w / window;
            int ago = window - 1 - i;
            if (mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + h) {
                hoverAgo = ago;
                g.fill(x0, y, x1, y + h, hoverColor);
            }
            long value = history.value(channel, ago, filter);
            if (value <= 0) continue;
            float grow = ago == 0 ? history.growProgress() : 1f;
            int bar = Math.min(h, Math.max(1, Math.round(value / shownMax * h * grow)));
            g.fill(x0, y + h - bar, x1 - gap, y + h, color);
        }
    }

    private void renderTimeLabels(GuiGraphics g, Font font, Theme t, int window, int x, int y, int w) {
        String start = tr(AGO, window);
        String middle = tr(AGO, window / 2);
        String now = tr(NOW);
        g.drawString(font, start, x, y, t.textSubtle(), false);
        g.drawString(font, middle, x + w / 2 - font.width(middle) / 2, y, t.textSubtle(), false);
        g.drawString(font, now, x + w - font.width(now), y, t.textSubtle(), false);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
