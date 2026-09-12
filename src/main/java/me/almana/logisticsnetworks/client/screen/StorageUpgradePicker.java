package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.integration.storage.StorageBackend;
import me.almana.logisticsnetworks.network.SyncStorageUpgradeCatalogPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class StorageUpgradePicker {

    private static final int WIDTH = 58;
    private static final int MESSAGE_WIDTH = 136;
    private static final int TITLE_HEIGHT = 18;
    private static final int ROW_HEIGHT = 22;
    private static final int MAX_VISIBLE = 5;

    private boolean open;
    private boolean loading;
    private boolean available;
    private StorageBackend backend;
    private int preferredSlot = -1;
    private int scroll;
    private List<SyncStorageUpgradeCatalogPayload.Entry> entries = List.of();

    void open(int slot) {
        open = true;
        loading = true;
        available = true;
        backend = null;
        preferredSlot = slot;
        scroll = 0;
        entries = List.of();
    }

    void receive(int slot, StorageBackend receivedBackend, boolean networkAvailable,
                 List<SyncStorageUpgradeCatalogPayload.Entry> received) {
        if (!open || preferredSlot != slot) return;
        loading = false;
        backend = receivedBackend;
        available = networkAvailable;
        entries = new ArrayList<>(received);
        scroll = 0;
    }

    void close() {
        open = false;
        backend = null;
        preferredSlot = -1;
        entries = List.of();
    }

    boolean isOpen() {
        return open;
    }

    int preferredSlot() {
        return preferredSlot;
    }

    int x(int left) {
        return left + 114;
    }

    int y(int top) {
        return top + 103;
    }

    int height() {
        int rows = loading || !available || entries.isEmpty() ? 1 : Math.min(entries.size(), MAX_VISIBLE);
        return TITLE_HEIGHT + rows * ROW_HEIGHT + 4;
    }

    private int width() {
        return loading || !available || entries.isEmpty() ? MESSAGE_WIDTH : WIDTH;
    }

    boolean contains(double mouseX, double mouseY, int left, int top) {
        int x = x(left);
        int y = y(top);
        return mouseX >= x && mouseX < x + width() && mouseY >= y && mouseY < y + height();
    }

    @Nullable
    SyncStorageUpgradeCatalogPayload.Entry entryAt(double mouseX, double mouseY, int left, int top) {
        if (loading || !available || entries.isEmpty()) return null;
        int listY = y(top) + TITLE_HEIGHT;
        int visible = Math.min(entries.size(), MAX_VISIBLE);
        if (mouseY < listY || mouseY >= listY + visible * ROW_HEIGHT
                || mouseX < x(left) || mouseX >= x(left) + width()) return null;
        int row = ((int) mouseY - listY) / ROW_HEIGHT;
        return entries.get(row + scroll);
    }

    boolean scroll(double mouseX, double mouseY, double amount, int left, int top) {
        if (!open || entries.size() <= MAX_VISIBLE || !contains(mouseX, mouseY, left, top)) return false;
        int max = entries.size() - MAX_VISIBLE;
        if (amount > 0 && scroll > 0) scroll--;
        if (amount < 0 && scroll < max) scroll++;
        return true;
    }

    void render(GuiGraphics graphics, Font font, Theme theme, int left, int top, int mouseX, int mouseY) {
        if (!open) return;
        int x = x(left);
        int y = y(top);
        int width = width();
        int height = height();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 450);
        ThemePaint.panel(graphics, x, y, width, height, theme);
        Component title = backend == null
                ? Component.translatable("gui.logisticsnetworks.node.storage_upgrades")
                : Component.translatable("gui.logisticsnetworks.node.storage_upgrades.backend", backend.displayName());
        graphics.drawString(font, title, x + 6, y + 5, theme.text(), false);

        if (loading || !available || entries.isEmpty()) {
            Component message = loading
                    ? Component.translatable("gui.logisticsnetworks.node.storage_upgrades.loading")
                    : !available
                    ? Component.translatable("gui.logisticsnetworks.node.storage_upgrades.unavailable")
                    : Component.translatable("gui.logisticsnetworks.node.storage_upgrades.empty");
            ThemePaint.drawCentered(graphics, font, message, x + width / 2,
                    y + TITLE_HEIGHT + 7, theme.textMuted());
            graphics.pose().popPose();
            return;
        }

        int visible = Math.min(entries.size(), MAX_VISIBLE);
        for (int row = 0; row < visible; row++) {
            SyncStorageUpgradeCatalogPayload.Entry entry = entries.get(row + scroll);
            int rowY = y + TITLE_HEIGHT + row * ROW_HEIGHT;
            boolean hovered = mouseX >= x + 2 && mouseX < x + width - 2
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hovered) graphics.fill(x + 2, rowY, x + width - 2, rowY + ROW_HEIGHT, 0x22FFFFFF);
            graphics.renderItem(entry.item(), x + 5, rowY + 3);
            if (entry.stored() > 0) {
                graphics.drawString(font, "x " + entry.stored(), x + 27, rowY + 7, theme.accent(), false);
            }
        }
        if (scroll > 0) graphics.drawString(font, "▲", x + width - 12, y + TITLE_HEIGHT,
                theme.textMuted(), false);
        if (scroll + visible < entries.size()) {
            graphics.drawString(font, "▼", x + width - 12, y + height - 11, theme.textMuted(), false);
        }
        graphics.pose().popPose();
    }

    void renderTooltip(GuiGraphics graphics, Font font, int left, int top, int mouseX, int mouseY) {
        SyncStorageUpgradeCatalogPayload.Entry entry = entryAt(mouseX, mouseY, left, top);
        if (entry == null) return;
        Component availability = entry.stored() > 0
                ? Component.translatable("gui.logisticsnetworks.node.storage_upgrades.stored", entry.stored())
                .withStyle(ChatFormatting.GRAY)
                : Component.translatable("gui.logisticsnetworks.node.storage_upgrades.craftable")
                .withStyle(ChatFormatting.AQUA);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        graphics.renderComponentTooltip(font, List.of(entry.item().getHoverName(), availability), mouseX, mouseY);
        graphics.pose().popPose();
    }
}
