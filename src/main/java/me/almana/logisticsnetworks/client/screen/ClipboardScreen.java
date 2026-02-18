package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.menu.ClipboardMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.Locale;

public class ClipboardScreen extends AbstractContainerScreen<ClipboardMenu> {

    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 284;

    private static final int COLOR_BG = 0xFF111111;
    private static final int COLOR_PANEL = 0xFF1A1A1A;
    private static final int COLOR_BORDER = 0xFF333333;
    private static final int COLOR_ACCENT = 0xFF55CC55;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_DIM = 0xFF888888;
    private static final int COLOR_HOVER = 0x30FFFFFF;
    private static final Component EDITOR_TITLE = Component.translatable("gui.logisticsnetworks.clipboard.editor");
    private static final Component VISUAL_SLOTS_HINT = Component
            .translatable("gui.logisticsnetworks.clipboard.visual_slots_hint");

    public ClipboardScreen(ClipboardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.inventoryLabelY = 10_000;
        this.titleLabelY = 10_000;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, COLOR_BG);
        graphics.renderOutline(leftPos, topPos, GUI_WIDTH, GUI_HEIGHT, COLOR_BORDER);

        graphics.drawCenteredString(font, EDITOR_TITLE, leftPos + GUI_WIDTH / 2, topPos + 8, COLOR_ACCENT);
        drawButton(graphics, leftPos + GUI_WIDTH - 56, topPos + 6, 46, 12,
                tr("gui.logisticsnetworks.clipboard.clear"), mouseX, mouseY);

        renderChannelTabs(graphics, mouseX, mouseY);
        renderSettingsPanel(graphics, mouseX, mouseY);
        renderVisualSections(graphics);
        renderPlayerSlots(graphics);
    }

    private void renderChannelTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int startX = leftPos + 10;
        int y = topPos + 22;
        int selected = menu.getSelectedChannel();

        for (int i = 0; i < 9; i++) {
            int x = startX + i * 26;
            boolean isSelected = i == selected;
            boolean hovered = isHoveringBox(x, y, 24, 14, mouseX, mouseY);
            int bg = isSelected ? 0xFF2A4A2A : COLOR_PANEL;
            int border = isSelected ? COLOR_ACCENT : COLOR_BORDER;
            if (hovered) {
                bg = isSelected ? bg : 0xFF262626;
            }
            graphics.fill(x, y, x + 24, y + 14, bg);
            graphics.renderOutline(x, y, 24, 14, border);
            graphics.drawCenteredString(font, String.valueOf(i), x + 12, y + 3, isSelected ? COLOR_ACCENT : COLOR_TEXT);
        }
    }

    private void renderSettingsPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = leftPos + 10;
        int panelY = topPos + 42;
        int panelW = 148;
        int rowH = 14;

        graphics.fill(panelX, panelY, panelX + panelW, panelY + rowH * 10 + 4, COLOR_PANEL);
        graphics.renderOutline(panelX, panelY, panelW, rowH * 10 + 4, COLOR_BORDER);

        drawRow(graphics, panelX + 2, panelY + 2 + rowH * 0, panelW - 4,
                tr("gui.logisticsnetworks.node.setting.status"),
                menu.isChannelEnabled() ? tr("gui.logisticsnetworks.node.value.enabled")
                        : tr("gui.logisticsnetworks.node.value.disabled"),
                mouseX, mouseY);
        drawRow(graphics, panelX + 2, panelY + 2 + rowH * 1, panelW - 4, tr("gui.logisticsnetworks.node.setting.mode"),
                getChannelModeLabel(menu.getChannelMode()), mouseX,
                mouseY);
        drawRow(graphics, panelX + 2, panelY + 2 + rowH * 2, panelW - 4, tr("gui.logisticsnetworks.node.setting.type"),
                getChannelTypeLabel(menu.getChannelType()), mouseX,
                mouseY);
        drawRow(graphics, panelX + 2, panelY + 2 + rowH * 3, panelW - 4, tr("gui.logisticsnetworks.node.setting.side"),
                getDirectionLabel(menu.getDirection().getName()), mouseX, mouseY);
        drawRow(graphics, panelX + 2, panelY + 2 + rowH * 4, panelW - 4,
                tr("gui.logisticsnetworks.node.setting.redstone"),
                getRedstoneModeLabel(menu.getRedstoneMode()),
                mouseX, mouseY);
        drawRow(graphics, panelX + 2, panelY + 2 + rowH * 5, panelW - 4,
                tr("gui.logisticsnetworks.node.setting.distribution"),
                getDistributionModeLabel(menu.getDistributionMode()), mouseX, mouseY);
        drawRow(graphics, panelX + 2, panelY + 2 + rowH * 6, panelW - 4,
                tr("gui.logisticsnetworks.node.setting.filter_mode"),
                getFilterModeLabel(menu.getFilterMode()),
                mouseX, mouseY);
        drawRow(graphics, panelX + 2, panelY + 2 + rowH * 7, panelW - 4,
                tr("gui.logisticsnetworks.node.setting.priority"), String.valueOf(menu.getPriority()),
                mouseX, mouseY);
        drawRow(graphics, panelX + 2, panelY + 2 + rowH * 8, panelW - 4, tr("gui.logisticsnetworks.node.setting.batch"),
                String.valueOf(menu.getBatch()),
                mouseX, mouseY);
        drawRow(graphics, panelX + 2, panelY + 2 + rowH * 9, panelW - 4, tr("gui.logisticsnetworks.node.setting.delay"),
                tr("gui.logisticsnetworks.node.value.tick_delay", menu.getDelay()), mouseX, mouseY);
    }

    private void drawRow(GuiGraphics graphics, int x, int y, int width, String label, String value, int mouseX,
            int mouseY) {
        if (isHoveringBox(x, y, width, 13, mouseX, mouseY)) {
            graphics.fill(x, y, x + width, y + 13, COLOR_HOVER);
        }
        graphics.drawString(font, label, x + 4, y + 3, COLOR_DIM, false);
        graphics.drawString(font, value, x + width - font.width(value) - 4, y + 3, COLOR_TEXT, false);
    }

    private void renderVisualSections(GuiGraphics graphics) {
        int filterX = leftPos + 170;
        int filterY = topPos + 52;
        graphics.drawString(font, Component.translatable("gui.logisticsnetworks.node.filters"), filterX, topPos + 40,
                COLOR_DIM, false);
        drawSlotGrid(graphics, filterX, filterY, 3, 3);

        int upgradeX = leftPos + 170;
        int upgradeY = topPos + 130;
        graphics.drawString(font, Component.translatable("gui.logisticsnetworks.node.upgrades"), upgradeX,
                topPos + 118, COLOR_DIM, false);
        drawSlotGrid(graphics, upgradeX, upgradeY, 2, 2);

        graphics.drawString(font, VISUAL_SLOTS_HINT, leftPos + 10, topPos + 182, COLOR_DIM, false);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int width, int height, String label, int mouseX,
            int mouseY) {
        boolean hovered = isHoveringBox(x, y, width, height, mouseX, mouseY);
        int bg = hovered ? 0xFF3A3A3A : 0xFF2A2A2A;
        graphics.fill(x, y, x + width, y + height, bg);
        graphics.renderOutline(x, y, width, height, hovered ? COLOR_ACCENT : COLOR_BORDER);
        graphics.drawCenteredString(font, label, x + width / 2, y + 2, hovered ? COLOR_TEXT : COLOR_DIM);
    }

    private void drawSlotGrid(GuiGraphics graphics, int startX, int startY, int rows, int cols) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = startX + col * 19 - 1;
                int y = startY + row * 19 - 1;
                graphics.fill(x, y, x + 18, y + 18, 0xFF090909);
                graphics.renderOutline(x, y, 18, 18, 0xFF3A3A3A);
            }
        }
    }

    private void renderPlayerSlots(GuiGraphics graphics) {
        int startX = leftPos + 32;
        int startY = topPos + 194;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int x = startX + col * 18 - 1;
                int y = startY + row * 18 - 1;
                graphics.fill(x, y, x + 18, y + 18, 0xFF090909);
                graphics.renderOutline(x, y, 18, 18, 0xFF3A3A3A);
            }
        }
        for (int col = 0; col < 9; col++) {
            int x = startX + col * 18 - 1;
            int y = startY + 58 - 1;
            graphics.fill(x, y, x + 18, y + 18, 0xFF090909);
            graphics.renderOutline(x, y, 18, 18, 0xFF3A3A3A);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHoveringMenuSlot(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (button == 0 || button == 1) {
            if (handleUtilityClick(mouseX, mouseY)) {
                return true;
            }
            if (handleHeaderClick(mouseX, mouseY)) {
                return true;
            }
            if (handleSettingsClick(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleUtilityClick(double mouseX, double mouseY) {
        int clearX = leftPos + GUI_WIDTH - 56;
        int clearY = topPos + 6;
        if (isHoveringBox(clearX, clearY, 46, 12, mouseX, mouseY)) {
            sendButton(ClipboardMenu.ID_CLEAR_CLIPBOARD);
            return true;
        }
        return false;
    }

    private boolean handleHeaderClick(double mouseX, double mouseY) {
        int startX = leftPos + 10;
        int y = topPos + 22;
        for (int i = 0; i < 9; i++) {
            int x = startX + i * 26;
            if (isHoveringBox(x, y, 24, 14, mouseX, mouseY)) {
                sendButton(ClipboardMenu.ID_SELECT_CHANNEL_BASE + i);
                return true;
            }
        }
        return false;
    }

    private boolean handleSettingsClick(double mouseX, double mouseY, int mouseButton) {
        int panelX = leftPos + 12;
        int panelY = topPos + 44;
        int rowH = 14;
        int width = 144;

        for (int row = 0; row < 10; row++) {
            int y = panelY + row * rowH;
            if (!isHoveringBox(panelX, y, width, 13, mouseX, mouseY)) {
                continue;
            }

            int id = mapRowToButton(row, mouseButton == 0);
            if (id != -1) {
                sendButton(id);
                return true;
            }
        }

        return false;
    }

    private int mapRowToButton(int row, boolean leftClick) {
        return switch (row) {
            case 0 -> ClipboardMenu.ID_TOGGLE_ENABLED;
            case 1 -> leftClick ? ClipboardMenu.ID_MODE_NEXT : ClipboardMenu.ID_MODE_PREV;
            case 2 -> leftClick ? ClipboardMenu.ID_TYPE_NEXT : ClipboardMenu.ID_TYPE_PREV;
            case 3 -> leftClick ? ClipboardMenu.ID_DIRECTION_NEXT : ClipboardMenu.ID_DIRECTION_PREV;
            case 4 -> leftClick ? ClipboardMenu.ID_REDSTONE_NEXT : ClipboardMenu.ID_REDSTONE_PREV;
            case 5 -> leftClick ? ClipboardMenu.ID_DISTRIBUTION_NEXT : ClipboardMenu.ID_DISTRIBUTION_PREV;
            case 6 -> leftClick ? ClipboardMenu.ID_FILTER_MODE_NEXT : ClipboardMenu.ID_FILTER_MODE_PREV;
            case 7 -> leftClick ? ClipboardMenu.ID_PRIORITY_INC : ClipboardMenu.ID_PRIORITY_DEC;
            case 8 -> leftClick ? ClipboardMenu.ID_BATCH_INC : ClipboardMenu.ID_BATCH_DEC;
            case 9 -> leftClick ? ClipboardMenu.ID_DELAY_INC : ClipboardMenu.ID_DELAY_DEC;
            default -> -1;
        };
    }

    private void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private boolean isHoveringBox(double x, double y, double width, double height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private boolean isHoveringMenuSlot(double mouseX, double mouseY) {
        for (Slot slot : menu.slots) {
            if (isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    private String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private String getChannelModeLabel(me.almana.logisticsnetworks.data.ChannelMode mode) {
        return tr("gui.logisticsnetworks.channel_mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    private String getChannelTypeLabel(me.almana.logisticsnetworks.data.ChannelType type) {
        return tr("gui.logisticsnetworks.channel_type." + type.name().toLowerCase(Locale.ROOT));
    }

    private String getRedstoneModeLabel(me.almana.logisticsnetworks.data.RedstoneMode mode) {
        return tr("gui.logisticsnetworks.redstone_mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    private String getDistributionModeLabel(me.almana.logisticsnetworks.data.DistributionMode mode) {
        return tr("gui.logisticsnetworks.distribution_mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    private String getDirectionLabel(String directionName) {
        return tr("gui.logisticsnetworks.direction." + directionName.toLowerCase(Locale.ROOT));
    }

    private String getFilterModeLabel(me.almana.logisticsnetworks.data.FilterMode mode) {
        return tr(mode == me.almana.logisticsnetworks.data.FilterMode.MATCH_ALL
                ? "gui.logisticsnetworks.filter_mode.match_all"
                : "gui.logisticsnetworks.filter_mode.match_any");
    }
}
