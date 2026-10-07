package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.GuiGraphics;
import me.almana.logisticsnetworks.client.theme.ChannelTint;
import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.client.theme.ThemeState;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.FlowResource;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.network.SyncChannelListPayload;
import me.almana.logisticsnetworks.network.SyncTelemetryPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

final class FlowMonitorPage {

    private static final int W = 332;
    private static final int H = 240;
    private static final int RAIL_X = 6;
    private static final int RAIL_W = 86;
    private static final int ROW_H = 21;
    private static final int BODY_Y = 29;
    private static final int MAIN_X = 98;
    private static final int MAIN_W = 228;
    private static final int[] WINDOWS = {30, 60, 120};
    private static final String KEY = "gui.logisticsnetworks.computer.";

    private final Font font;
    private final Runnable onBack;
    private final FlowChart chart = new FlowChart();
    private final FlowResourceList list = new FlowResourceList();
    private final int[] listTypes = new int[LogisticsNodeEntity.CHANNEL_COUNT];
    private final int[] nodeCounts = new int[LogisticsNodeEntity.CHANNEL_COUNT];
    private FlowHistory history = new FlowHistory();
    private List<String> names = List.of();
    private String networkName = "";
    private int selected = -1;
    private ChannelType selectedType = ChannelType.ITEM;
    private int window = 1;
    @Nullable
    private FlowResource filter;
    private int clearX;
    private int clearW;

    FlowMonitorPage(Font font, Runnable onBack) {
        this.font = font;
        this.onBack = onBack;
        open("");
    }

    void open(String networkName) {
        this.networkName = networkName;
        history = new FlowHistory();
        names = List.of();
        Arrays.fill(listTypes, -1);
        Arrays.fill(nodeCounts, 0);
        selected = -1;
        filter = null;
        list.reset();
    }

    void acceptChannels(SyncChannelListPayload payload) {
        names = payload.channelNames();
        Arrays.fill(listTypes, -1);
        Arrays.fill(nodeCounts, 0);
        for (SyncChannelListPayload.ChannelEntry entry : payload.channels()) {
            listTypes[entry.channelIndex()] = entry.typeOrdinal();
            nodeCounts[entry.channelIndex()] = entry.nodeCount();
        }
        selectFirstActive();
        refreshSelectedType();
    }

    void acceptTelemetry(SyncTelemetryPayload payload) {
        history.accept(payload);
        selectFirstActive();
        refreshSelectedType();
    }

    void render(GuiGraphics g, int left, int top, int mouseX, int mouseY) {
        Theme t = ThemeState.active();
        ThemePaint.window(g, left, top, W, H, t);
        renderTitle(g, t, left, top, mouseX, mouseY);
        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            renderRailRow(g, t, i, left + RAIL_X, top + BODY_Y + i * (ROW_H + 2), mouseX, mouseY);
        }
        int x = left + MAIN_X;
        int y = top + BODY_Y;
        if (selected < 0) {
            ThemePaint.drawCentered(g, font, tr("no_channels"), x + MAIN_W / 2, y + 80, t.textMuted());
            ThemePaint.drawCentered(g, font, tr("enable_channels_hint"), x + MAIN_W / 2, y + 92, t.textSubtle());
            return;
        }
        int color = ChannelTint.digit(selectedType, t);
        renderHeader(g, t, x, y, color);
        renderToolbar(g, t, x, y + 24);
        if (hasBreakdown()) {
            chart.render(g, font, t, history, selected, seconds(), filter, color, x, y + 39, MAIN_W, 92,
                    mouseX, mouseY);
            list.render(g, font, t, history, selected, seconds(), filter, color, unit(), x, y + 134, MAIN_W, 71,
                    mouseX, mouseY);
        } else {
            chart.render(g, font, t, history, selected, seconds(), null, color, x, y + 39, MAIN_W, 134,
                    mouseX, mouseY);
            renderStats(g, t, x, y + 178);
        }
    }

    void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (selected < 0) return;
        chart.renderTooltip(g, font, history, selected, hasBreakdown() ? filter : null, unit(), mouseX, mouseY);
        if (hasBreakdown()) {
            list.renderTooltip(g, font, history.span(seconds()), unit(), totalUnit(), mouseX, mouseY);
        }
    }

    boolean click(int left, int top, double mouseX, double mouseY) {
        if (inside(mouseX, mouseY, left + 6, top + 6, 36, 14)) {
            onBack.run();
            return true;
        }
        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            if (typeOrdinal(i) >= 0
                    && inside(mouseX, mouseY, left + RAIL_X, top + BODY_Y + i * (ROW_H + 2), RAIL_W, ROW_H)) {
                if (i != selected) {
                    select(i);
                }
                return true;
            }
        }
        if (selected < 0) return false;
        int segment = segmentAt(left + MAIN_X, top + BODY_Y + 24, mouseX, mouseY);
        if (segment >= 0) {
            window = segment;
            return true;
        }
        if (filter != null && inside(mouseX, mouseY, clearX, top + BODY_Y + 24, clearW, 12)) {
            filter = null;
            return true;
        }
        FlowResource resource = hasBreakdown() ? list.resourceAt(mouseX, mouseY) : null;
        if (resource != null) {
            filter = resource.equals(filter) ? null : resource;
            return true;
        }
        return false;
    }

    boolean scroll(double mouseX, double mouseY, double delta) {
        return selected >= 0 && hasBreakdown() && list.scroll(mouseX, mouseY, delta);
    }

    private void renderTitle(GuiGraphics g, Theme t, int left, int top, int mouseX, int mouseY) {
        ThemePaint.button(g, font, left + 6, top + 6, 36, 14, tr("flow.back"),
                inside(mouseX, mouseY, left + 6, top + 6, 36, 14), t);
        String title = tr("flow.title");
        String live = tr("telemetry.live");
        int pillX = left + W - 6 - ThemePaint.pillWidth(font, live);
        int titleX = left + 48;
        g.drawString(font, title, titleX, top + 9, t.text(), false);
        int nameX = titleX + font.width(title);
        g.drawString(font, font.plainSubstrByWidth(" · " + networkName, pillX - nameX - 6), nameX, top + 9,
                t.textMuted(), false);
        ThemePaint.pill(g, font, pillX, top + 8, live, Theme.Variant.ACCENT, false, t);
        ThemePaint.divider(g, left + 6, top + 25, W - 12, t);
    }

    private void renderRailRow(GuiGraphics g, Theme t, int channel, int x, int y, int mouseX, int mouseY) {
        int ordinal = typeOrdinal(channel);
        boolean active = ordinal >= 0;
        ChannelType type = active ? ChannelType.values()[ordinal] : selectedType;
        boolean on = channel == selected;
        boolean hovered = active && inside(mouseX, mouseY, x, y, RAIL_W, ROW_H);
        int border = on ? ChannelTint.border(type, t) : hovered ? t.borderStrong() : t.border();
        int tint = active ? ChannelTint.digit(type, t) : t.borderStrong();
        ThemePaint.roundRect(g, x, y, RAIL_W, ROW_H, 2, on ? ChannelTint.tabBg(type, t) : t.surface2(),
                t.sharpCorners());
        ThemePaint.roundOutline(g, x, y, RAIL_W, ROW_H, 2, border, t.sharpCorners());
        g.fill(x + 1, y + 1, x + 3, y + ROW_H - 1, tint);
        g.drawString(font, font.plainSubstrByWidth(channelName(channel), RAIL_W - 10), x + 6, y + 2,
                active ? t.text() : t.textSubtle(), false);
        g.drawString(font, tr("flow.channel_short", channel), x + 6, y + 11,
                active ? t.textMuted() : t.textSubtle(), false);
        String right = active ? FlowHistory.format(history.value(channel, 0, null)) : tr("flow.no_nodes");
        g.drawString(font, right, x + RAIL_W - 4 - font.width(right), y + 11, active ? tint : t.textSubtle(), false);
    }

    private void renderHeader(GuiGraphics g, Theme t, int x, int y, int color) {
        g.drawString(font, font.plainSubstrByWidth(channelName(selected), 140), x, y, t.text(), false);
        String prefix = tr("flow.channel_short", selected) + " · ";
        String typeName = tr("telemetry." + typeKey());
        String nodes = " · " + tr("channel_nodes", typeOrdinal(selected) < 0 ? 0 : nodeCounts[selected]);
        g.drawString(font, prefix, x, y + 11, t.textMuted(), false);
        g.drawString(font, typeName, x + font.width(prefix), y + 11, color, false);
        g.drawString(font, nodes, x + font.width(prefix) + font.width(typeName), y + 11, t.textMuted(), false);

        String value = FlowHistory.format(history.value(selected, 0, hasBreakdown() ? filter : null));
        g.pose().pushPose();
        g.pose().translate(x + MAIN_W - font.width(value) * 1.5f, y, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.drawString(font, value, 0, 0, color, false);
        g.pose().popPose();
        g.drawString(font, unit(), x + MAIN_W - font.width(unit()), y + 14, t.textMuted(), false);
    }

    private void renderToolbar(GuiGraphics g, Theme t, int x, int y) {
        String[] labels = windowLabels();
        int segX = x + MAIN_W - ThemePaint.segmentedWidth(font, labels);
        ThemePaint.segmented(g, font, segX, y, 12, labels, window, t);
        if (hasBreakdown() && filter != null) {
            String clear = " ✕";
            String name = font.plainSubstrByWidth(FlowResourceList.name(filter).getString(),
                    segX - x - 6 - font.width(clear));
            g.drawString(font, name, x, y + 2, t.text(), false);
            clearX = x + font.width(name);
            clearW = font.width(clear);
            g.drawString(font, clear, clearX, y + 2, t.danger(), false);
            return;
        }
        String text = hasBreakdown()
                ? tr("flow.avg_peak", FlowHistory.format(history.average(selected, seconds(), null)),
                        FlowHistory.format(history.peak(selected, seconds(), null)))
                : tr("flow.throughput");
        g.drawString(font, text, x, y + 2, t.textMuted(), false);
    }

    private void renderStats(GuiGraphics g, Theme t, int x, int y) {
        String[][] cards = {
                {tr("flow.average"), FlowHistory.format(history.average(selected, seconds(), null)) + " " + unit()},
                {tr("flow.peak"), FlowHistory.format(history.peak(selected, seconds(), null)) + " " + unit()},
                {tr("flow.moved", windowLabels()[window]),
                        FlowHistory.format(history.sum(selected, seconds(), null)) + " " + totalUnit()}};
        int cardW = (MAIN_W - 8) / 3;
        for (int i = 0; i < cards.length; i++) {
            int cx = x + i * (cardW + 4);
            ThemePaint.panel(g, cx, y, cardW, 27, t);
            g.drawString(font, cards[i][0], cx + 4, y + 4, t.textMuted(), false);
            g.drawString(font, font.plainSubstrByWidth(cards[i][1], cardW - 8), cx + 4, y + 15, t.text(), false);
        }
    }

    private int segmentAt(int x, int y, double mouseX, double mouseY) {
        String[] labels = windowLabels();
        int cx = x + MAIN_W - ThemePaint.segmentedWidth(font, labels) + 2;
        for (int i = 0; i < labels.length; i++) {
            int segmentW = font.width(labels[i]) + 10;
            if (inside(mouseX, mouseY, cx, y, segmentW, 12)) return i;
            cx += segmentW;
        }
        return -1;
    }

    private void select(int channel) {
        selected = channel;
        selectedType = ChannelType.values()[typeOrdinal(channel)];
        filter = null;
        list.reset();
    }

    private void selectFirstActive() {
        for (int i = 0; selected < 0 && i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            if (typeOrdinal(i) >= 0) select(i);
        }
    }

    private void refreshSelectedType() {
        int ordinal = selected < 0 ? -1 : typeOrdinal(selected);
        if (ordinal >= 0 && ChannelType.values()[ordinal] != selectedType) {
            selectedType = ChannelType.values()[ordinal];
            filter = null;
        }
    }

    private int typeOrdinal(int channel) {
        return history.hasData() ? history.type(channel) : listTypes[channel];
    }

    private String channelName(int channel) {
        String name = channel < names.size() ? names.get(channel) : "";
        return name.isEmpty() ? tr("flow.channel", channel) : name;
    }

    private boolean hasBreakdown() {
        return selectedType == ChannelType.ITEM || selectedType == ChannelType.FLUID
                || selectedType == ChannelType.CHEMICAL;
    }

    private String typeKey() {
        return switch (selectedType) {
            case ITEM -> "items";
            case FLUID -> "fluids";
            case ENERGY -> "energy";
            case CHEMICAL -> "chemicals";
            case SOURCE -> "source";
        };
    }

    private String unit() {
        return tr("telemetry.unit." + typeKey());
    }

    private String totalUnit() {
        return tr("telemetry.total." + typeKey());
    }

    private int seconds() {
        return WINDOWS[window];
    }

    private String[] windowLabels() {
        return new String[] {tr("flow.window.30"), tr("flow.window.60"), tr("flow.window.120")};
    }

    private String tr(String key, Object... args) {
        return Component.translatable(KEY + key, args).getString();
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
