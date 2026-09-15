package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.integration.storage.StorageBackend;
import me.almana.logisticsnetworks.network.SyncStorageUpgradeCatalogPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

final class StorageUpgradePicker {
    private static final int ROW_HEIGHT = 22;
    private static final int MAX_VISIBLE = 5;
    private static final NumberFormat COUNT_FORMAT = NumberFormat.getCompactNumberInstance(
            Locale.ROOT, NumberFormat.Style.SHORT);

    static {
        COUNT_FORMAT.setMaximumFractionDigits(1);
    }

    private boolean open;
    private boolean loading;
    private boolean available;
    private StorageBackend backend;
    private int preferredSlot = -1;
    private int scroll;
    private int titleHeight = 18;
    private int visibleRows = MAX_VISIBLE;
    private Rect2i bounds = new Rect2i(0, 0, 0, 0);
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
        entries = List.copyOf(received);
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

    Rect2i bounds() {
        return bounds;
    }

    boolean contains(double mouseX, double mouseY) {
        return bounds.contains((int) mouseX, (int) mouseY);
    }

    @Nullable
    SyncStorageUpgradeCatalogPayload.Entry entryAt(double mouseX, double mouseY) {
        if (loading || !available || entries.isEmpty() || !contains(mouseX, mouseY)) return null;
        double offset = mouseY - bounds.getY() - titleHeight;
        if (offset < 0 || offset >= visibleRows * ROW_HEIGHT) return null;
        return entries.get((int) offset / ROW_HEIGHT + scroll);
    }

    boolean scroll(double mouseX, double mouseY, double amount) {
        if (!open || entries.size() <= visibleRows || !contains(mouseX, mouseY)) return false;
        scroll = Math.clamp(scroll - (int) Math.signum(amount), 0, entries.size() - visibleRows);
        return true;
    }

    private Component title() {
        return backend == null ? Component.translatable("gui.logisticsnetworks.node.storage_upgrades")
                : Component.translatable("gui.logisticsnetworks.node.storage_upgrades.backend", backend.displayName());
    }

    private Component message() {
        String state = loading ? "loading" : !available ? "unavailable" : "empty";
        return Component.translatable("gui.logisticsnetworks.node.storage_upgrades." + state);
    }

    private void layout(Font font, int screenWidth, int screenHeight, int left, int top) {
        int width = font.width(title()) + 12;
        if (loading || !available || entries.isEmpty()) width = Math.max(width, font.width(message()) + 12);
        for (var entry : entries) {
            width = Math.max(width, font.width(entry.item().getHoverName()) + font.width(availability(entry)) + 48);
        }
        width = Math.min(width, Math.min(248, screenWidth - 8));
        titleHeight = font.split(title(), width - 12).size() * font.lineHeight + 10;
        visibleRows = Math.min(Math.min(entries.size(), MAX_VISIBLE),
                Math.max(1, (screenHeight - titleHeight - 12) / ROW_HEIGHT));
        scroll = Math.clamp(scroll, 0, Math.max(0, entries.size() - visibleRows));
        int bodyHeight = loading || !available || entries.isEmpty()
                ? font.split(message(), width - 12).size() * font.lineHeight + 12 : visibleRows * ROW_HEIGHT;
        int height = titleHeight + bodyHeight + 4;
        bounds = new Rect2i(Math.clamp(left + 114, 4, screenWidth - width - 4),
                Math.clamp(top + 103, 4, screenHeight - height - 4), width, height);
    }

    void render(GuiGraphics graphics, Font font, Theme theme, int screenWidth, int screenHeight,
                int left, int top, int mouseX, int mouseY) {
        if (!open) return;
        layout(font, screenWidth, screenHeight, left, top);
        int x = bounds.getX();
        int y = bounds.getY();
        int width = bounds.getWidth();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 450);
        ThemePaint.panel(graphics, x, y, width, bounds.getHeight(), theme);
        graphics.drawWordWrap(font, title(), x + 6, y + 5, width - 12, theme.text());
        if (loading || !available || entries.isEmpty()) {
            graphics.drawWordWrap(font, message(), x + 6, y + titleHeight + 5, width - 12, theme.textMuted());
        } else {
            renderEntries(graphics, font, theme, mouseX, mouseY);
        }
        graphics.pose().popPose();
    }

    private void renderEntries(GuiGraphics graphics, Font font, Theme theme, int mouseX, int mouseY) {
        int x = bounds.getX();
        int width = bounds.getWidth();
        for (int row = 0; row < visibleRows; row++) {
            var entry = entries.get(row + scroll);
            int rowY = bounds.getY() + titleHeight + row * ROW_HEIGHT;
            if (mouseX >= x + 2 && mouseX < x + width - 2 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                graphics.fill(x + 2, rowY, x + width - 2, rowY + ROW_HEIGHT, 0x22FFFFFF);
            }
            graphics.renderItem(entry.item(), x + 5, rowY + 3);
            String count = availability(entry);
            int countX = x + width - 14 - font.width(count);
            String name = entry.item().getHoverName().getString();
            int nameWidth = Math.max(0, countX - x - 32);
            if (font.width(name) > nameWidth) name = font.plainSubstrByWidth(name, Math.max(0, nameWidth - 9)) + "...";
            graphics.drawString(font, name, x + 27, rowY + 7, theme.text(), false);
            graphics.drawString(font, count, countX, rowY + 7, theme.accent(), false);
        }
        if (scroll > 0) graphics.drawString(font, "▲", x + width - 10,
                bounds.getY() + titleHeight, theme.textMuted(), false);
        if (scroll + visibleRows < entries.size()) graphics.drawString(font, "▼", x + width - 10,
                bounds.getY() + bounds.getHeight() - 11, theme.textMuted(), false);
    }

    private String availability(SyncStorageUpgradeCatalogPayload.Entry entry) {
        return entry.stored() > 0 ? "× " + compactCount(entry.stored())
                : Component.translatable("gui.logisticsnetworks.node.storage_upgrades.craft").getString();
    }

    private static String compactCount(long count) {
        COUNT_FORMAT.setMaximumFractionDigits((Long.toString(count).length() - 1) % 3 == 0 ? 1 : 0);
        return COUNT_FORMAT.format(count).toLowerCase(Locale.ROOT);
    }

    void renderTooltip(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!open) return;
        var entry = entryAt(mouseX, mouseY);
        if (entry == null) return;
        Component availability = entry.stored() > 0
                ? Component.translatable("gui.logisticsnetworks.node.storage_upgrades.stored", entry.stored()).withStyle(ChatFormatting.GRAY)
                : Component.translatable("gui.logisticsnetworks.node.storage_upgrades.craftable").withStyle(ChatFormatting.AQUA);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        graphics.renderComponentTooltip(font, List.of(entry.item().getHoverName(), availability), mouseX, mouseY);
        graphics.pose().popPose();
    }
}
