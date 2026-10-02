package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.FilterResourceRenderer;
import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.data.FlowResource;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class FlowResourceList {

    private static final int ROW_H = 18;
    private static final String KEY = "gui.logisticsnetworks.computer.flow.";

    private List<Map.Entry<FlowResource, Long>> rows = List.of();
    private long other;
    private int scroll;
    private int x;
    private int y;
    private int w;
    private int h;
    private int visible;
    private int hovered = -1;

    void reset() {
        scroll = 0;
    }

    void render(GuiGraphics g, Font font, Theme t, FlowHistory history, int channel, int window,
            @Nullable FlowResource filter, int color, String unit, int x, int y, int w, int h,
            int mouseX, int mouseY) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        rows = history.totals(channel, window);
        other = history.otherTotal(channel, window);
        int count = rowCount();
        visible = (h - 11) / ROW_H;
        scroll = Math.max(0, Math.min(scroll, count - visible));
        long all = Math.max(1, other + rows.stream().mapToLong(Map.Entry::getValue).sum());
        int span = history.span(window);

        ThemePaint.sunkPanel(g, x, y, w, h, t);
        hovered = indexAt(mouseX, mouseY);
        for (int r = 0; r < visible && scroll + r < count; r++) {
            renderRow(g, font, t, scroll + r, y + 1 + r * ROW_H, filter, color, unit, all, span);
        }
        if (count > visible) {
            String info = tr("scroll", scroll + 1, Math.min(count, scroll + visible), count);
            ThemePaint.drawCentered(g, font, info, x + w / 2, y + h - 10, t.textSubtle());
        }
    }

    void renderTooltip(GuiGraphics g, Font font, int span, String unit, String totalUnit, int mouseX, int mouseY) {
        if (hovered < 0 || hovered >= rows.size()) return;
        Map.Entry<FlowResource, Long> row = rows.get(hovered);
        List<Component> lines = new ArrayList<>(row.getKey() instanceof FlowResource.Item item
                ? Screen.getTooltipFromItem(Minecraft.getInstance(), item.stack())
                : List.of(name(row.getKey())));
        lines.add(Component.translatable(KEY + "rate_tooltip", FlowHistory.format(row.getValue() / span), unit)
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(KEY + "moved_tooltip", FlowHistory.format(row.getValue()), totalUnit)
                .withStyle(ChatFormatting.GRAY));
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    @Nullable
    FlowResource resourceAt(double mouseX, double mouseY) {
        int index = indexAt(mouseX, mouseY);
        return index >= 0 && index < rows.size() ? rows.get(index).getKey() : null;
    }

    boolean scroll(double mouseX, double mouseY, double delta) {
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) return false;
        scroll = Math.max(0, scroll - (int) Math.signum(delta));
        return true;
    }

    private static void renderIcon(GuiGraphics g, FlowResource resource, int x, int y) {
        switch (resource) {
            case FlowResource.Item item -> g.renderItem(item.stack(), x, y);
            case FlowResource.Fluid fluid -> FilterResourceRenderer.renderFluid(g, fluid.stack(), x, y);
            case FlowResource.Chemical chemical -> FilterResourceRenderer.renderChemical(g, chemical.id(), x, y);
        }
    }

    static Component name(FlowResource resource) {
        return switch (resource) {
            case FlowResource.Item item -> item.stack().getHoverName();
            case FlowResource.Fluid fluid -> fluid.stack().getHoverName();
            case FlowResource.Chemical chemical -> {
                Component name = MekanismCompat.getChemicalTextComponent(chemical.id());
                yield name != null ? name : Component.literal(chemical.id());
            }
        };
    }

    private void renderRow(GuiGraphics g, Font font, Theme t, int index, int ry, @Nullable FlowResource filter,
            int color, String unit, long all, int span) {
        boolean isOther = index >= rows.size();
        FlowResource resource = isOther ? null : rows.get(index).getKey();
        long amount = isOther ? other : rows.get(index).getValue();
        if (resource != null && resource.equals(filter)) {
            g.fill(x + 1, ry, x + w - 1, ry + ROW_H, t.surface2());
        } else if (index == hovered && !isOther) {
            g.fill(x + 1, ry, x + w - 1, ry + ROW_H, (t.text() & 0x00FFFFFF) | 0x10000000);
        }
        if (resource != null) {
            renderIcon(g, resource, x + 2, ry + 1);
        }
        String rate = FlowHistory.format(amount / span) + " " + unit;
        String share = amount * 100 / all + "%";
        int textW = w - 27 - font.width(rate);
        String name = isOther ? tr("other") : name(resource).getString();
        g.drawString(font, font.plainSubstrByWidth(name, textW), x + 21, ry + 2,
                isOther ? t.textMuted() : t.text(), false);
        int bar = (int) Math.max(1, amount * textW / all);
        g.fill(x + 21, ry + 12, x + 21 + bar, ry + 14, isOther ? t.textSubtle() : color);
        g.drawString(font, rate, x + w - 3 - font.width(rate), ry + 2, color, false);
        g.drawString(font, share, x + w - 3 - font.width(share), ry + 10, t.textSubtle(), false);
    }

    private int indexAt(double mouseX, double mouseY) {
        if (mouseX < x + 1 || mouseX >= x + w - 1 || mouseY < y + 1) return -1;
        int row = (int) (mouseY - y - 1) / ROW_H;
        int index = scroll + row;
        return row < visible && index < rowCount() ? index : -1;
    }

    private int rowCount() {
        return rows.size() + (other > 0 ? 1 : 0);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(KEY + key, args).getString();
    }
}
