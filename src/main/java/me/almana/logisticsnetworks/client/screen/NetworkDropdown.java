package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.network.SyncNetworkListPayload.NetworkEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

final class NetworkDropdown {

    private static final int HEADER_H = 16;
    private static final int SEARCH_H = 14;
    private static final int SORT_H = 13;
    private static final int ROW_H = 14;
    private static final int ROWS = 4;
    private static final int PANEL_H = 4 + SEARCH_H + 3 + SORT_H + 3 + ROWS * ROW_H + 4;

    private final Font font;
    private final int x;
    private final int y;
    private final int w;
    private final Consumer<UUID> onSelect;
    private final FlatEditBox search;
    private NetworkSortMode sortMode = NetworkSortMode.NEW_OLD;
    private boolean open;
    private int scroll;

    NetworkDropdown(Font font, int x, int y, int w, Consumer<UUID> onSelect) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.w = w;
        this.onSelect = onSelect;
        this.search = new FlatEditBox(font, x + 14, searchY(), w - 22, SEARCH_H, Component.empty());
        search.setMaxLength(32);
        search.setResponder(value -> scroll = 0);
    }

    boolean isOpen() {
        return open;
    }

    boolean typing() {
        return open && search.isFocused();
    }

    void close() {
        open = false;
        search.setFocused(false);
    }

    void renderHeader(GuiGraphics g, Theme t, String label, int swatch, int mx, int my) {
        boolean hovered = hits(mx, my, x, y, w, HEADER_H);
        ThemePaint.roundRect(g, x, y, w, HEADER_H, 2, t.surface2(), t.sharpCorners());
        ThemePaint.roundOutline(g, x, y, w, HEADER_H, 2, open || hovered ? t.accent() : t.border(),
                t.sharpCorners());
        int textX = x + 5;
        if (swatch >= 0) {
            g.fill(x + 5, y + 4, x + 13, y + 12, 0xFF000000 | swatch);
            textX = x + 17;
        }
        g.drawString(font, font.plainSubstrByWidth(label, x + w - 14 - textX), textX, y + 4, t.text(), false);
        g.drawString(font, open ? "▴" : "▾", x + w - 10, y + 4, t.textMuted(), false);
    }

    void renderPanel(GuiGraphics g, Theme t, List<NetworkEntry> networks, @Nullable UUID blocked,
            int mx, int my, float pt) {
        if (!open) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 450);
        ThemePaint.panel(g, x, panelY(), w, PANEL_H, t);
        String hint = search.getValue().isEmpty()
                ? Component.translatable("gui.logisticsnetworks.node.network_search_hint").getString()
                : "";
        ThemePaint.searchBox(g, font, x + 4, searchY(), w - 8, SEARCH_H, hint, search.isFocused(), t);
        search.setTextColor(t.text());
        search.render(g, mx, my, pt);
        String sortLabel = sortLabel();
        ThemePaint.sortButton(g, font, x + 4, sortY(), sortLabel,
                hits(mx, my, x + 4, sortY(), ThemePaint.sortButtonWidth(font, sortLabel), SORT_H), t);
        List<NetworkEntry> visible = filtered(networks);
        if (visible.isEmpty()) {
            g.drawString(font, Component.translatable("gui.logisticsnetworks.no_networks"), x + 6, listY() + 3,
                    t.textSubtle(), false);
        }
        int hoverFg = t.borderStrong() == t.text() ? t.bg() : t.text();
        for (int i = 0; i < ROWS && scroll + i < visible.size(); i++) {
            NetworkEntry entry = visible.get(scroll + i);
            int ry = listY() + i * ROW_H;
            boolean blockedRow = entry.id().equals(blocked);
            boolean hovered = !blockedRow && hits(mx, my, x + 4, ry, w - 8, ROW_H);
            if (hovered) {
                g.fill(x + 4, ry, x + w - 4, ry + ROW_H, t.borderStrong());
            }
            g.fill(x + 7, ry + 3, x + 15, ry + 11, 0xFF000000 | entry.color());
            int fg = blockedRow ? t.textSubtle() : hovered ? hoverFg : t.textMuted();
            g.drawString(font, font.plainSubstrByWidth(entry.name(), w - 26), x + 19, ry + 3, fg, false);
        }
        g.pose().popPose();
    }

    boolean mouseClicked(double mx, double my, List<NetworkEntry> networks, @Nullable UUID blocked) {
        if (hits(mx, my, x, y, w, HEADER_H)) {
            if (open) {
                close();
            } else {
                open = true;
                scroll = 0;
            }
            return true;
        }
        if (!open) {
            return false;
        }
        if (!hits(mx, my, x, panelY(), w, PANEL_H)) {
            close();
            return false;
        }
        search.mouseClicked(mx, my, 0);
        if (hits(mx, my, x + 4, sortY(), ThemePaint.sortButtonWidth(font, sortLabel()), SORT_H)) {
            sortMode = sortMode.next();
            scroll = 0;
            return true;
        }
        List<NetworkEntry> visible = filtered(networks);
        int row = (int) Math.floor((my - listY()) / ROW_H);
        if (row >= 0 && row < ROWS && scroll + row < visible.size()) {
            NetworkEntry entry = visible.get(scroll + row);
            if (!entry.id().equals(blocked)) {
                close();
                onSelect.accept(entry.id());
            }
        }
        return true;
    }

    boolean mouseScrolled(double mx, double my, double delta, List<NetworkEntry> networks) {
        if (!open || !hits(mx, my, x, panelY(), w, PANEL_H)) {
            return false;
        }
        int max = Math.max(0, filtered(networks).size() - ROWS);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        return true;
    }

    boolean keyPressed(int key, int scan, int mods) {
        return typing() && search.keyPressed(key, scan, mods);
    }

    boolean charTyped(char c, int mods) {
        return typing() && search.charTyped(c, mods);
    }

    static boolean hits(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private List<NetworkEntry> filtered(List<NetworkEntry> networks) {
        String filter = search.getValue().trim().toLowerCase(Locale.ROOT);
        return networks.stream()
                .filter(entry -> filter.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(filter))
                .sorted(sortMode.comparator())
                .toList();
    }

    private String sortLabel() {
        return Component.translatable(sortMode.labelKey()).getString();
    }

    private int panelY() {
        return y + HEADER_H + 2;
    }

    private int searchY() {
        return panelY() + 4;
    }

    private int sortY() {
        return searchY() + SEARCH_H + 3;
    }

    private int listY() {
        return sortY() + SORT_H + 3;
    }
}
