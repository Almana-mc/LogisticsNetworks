package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.FilterMode;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.menu.NodeMenu;
import me.almana.logisticsnetworks.network.AssignNetworkPayload;
import me.almana.logisticsnetworks.network.SelectNodeChannelPayload;
import me.almana.logisticsnetworks.network.SyncNetworkListPayload;
import me.almana.logisticsnetworks.network.ToggleNodeVisibilityPayload;
import me.almana.logisticsnetworks.network.UpdateChannelPayload;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public class NodeScreen extends AbstractContainerScreen<NodeMenu> {

    private enum Page {
        NETWORK_SELECT, CHANNEL_CONFIG
    }

    // Constants
    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 256;
    private static final int INV_X = 47;
    private static final int INV_Y = 176;
    private static final int NETWORKS_PER_PAGE = 4;
    private static final int BATCH_MIN = 1;
    private static final int BATCH_MAX = 1_000_000;
    private static final int DELAY_MIN = 1;
    private static final int DELAY_MAX = 9_999;
    private static final int PRIORITY_MIN = -99;
    private static final int PRIORITY_MAX = 99;

    // Colors
    private static final int COLOR_BG = 0xE6111111;
    private static final int COLOR_PANEL = 0xFF1A1A1A;
    private static final int COLOR_BORDER = 0xFF333333;
    private static final int COLOR_ACCENT = 0xFF44BB44;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_GRAY = 0xFF999999;
    private static final int COLOR_DARK_GRAY = 0xFF666666;
    private static final int COLOR_ENABLED_BG = 0xFF1A3A1A;
    private static final int COLOR_DISABLED_BG = 0xFF3A1A1A;
    private static final int COLOR_IMPORT = 0xFF5599FF;
    private static final int COLOR_EXPORT = 0xFFFF9944;
    private static final int COLOR_HOVER = 0x33FFFFFF;
    private static final int COLOR_SLOT_BG = 0xFF0A0A0A;
    private static final int COLOR_SLOT_BORDER = 0xFF3A3A3A;
    private static final int COLOR_BTN_BG = 0xFF2A2A2A;
    private static final int COLOR_BTN_HOVER = 0xFF3A3A3A;
    private static final int COLOR_BTN_BORDER = 0xFF4A4A4A;
    private static final int COLOR_DISABLED_TXT = 0xFF666666;

    private Page currentPage = Page.NETWORK_SELECT;
    private int selectedChannel = 0;
    private boolean isInitialized = false;

    // State tracking
    private UUID lastKnownNetworkId = null;
    private int editingRow = -1;
    private EditBox numericEditBox = null;
    private long lastSettingClickTime = 0;
    private int lastSettingClickRow = -1;

    // Network select widgets
    private EditBox networkNameField;
    private List<SyncNetworkListPayload.NetworkEntry> networkList = new ArrayList<>();
    private int networkScrollOffset = 0;

    public NodeScreen(NodeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.inventoryLabelY = 10_000;
        this.titleLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;

        if (!isInitialized) {
            isInitialized = true;
            LogisticsNodeEntity node = getMenu().getNode();
            if (node != null && node.getNetworkId() != null) {
                currentPage = Page.CHANNEL_CONFIG;
                lastKnownNetworkId = node.getNetworkId();
            }
        }
        selectedChannel = getMenu().getSelectedChannel();
        rebuildPageLayout();
    }

    private void rebuildPageLayout() {
        stopNumericEdit(false);
        clearWidgets();
        if (currentPage == Page.NETWORK_SELECT) {
            int cx = leftPos + GUI_WIDTH / 2;
            int y = topPos + 32;
            networkNameField = new EditBox(this.font, cx - 75, y, 150, 16, Component.empty());
            networkNameField.setMaxLength(32);
            networkNameField.setHint(Component.literal("Enter network name..."));
            networkNameField.setBordered(true);
            addRenderableWidget(networkNameField);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        LogisticsNodeEntity node = getMenu().getNode();
        if (node == null)
            return;

        UUID currentNetId = node.getNetworkId();
        if (!Objects.equals(lastKnownNetworkId, currentNetId)) {
            lastKnownNetworkId = currentNetId;
            if (currentPage == Page.NETWORK_SELECT && currentNetId != null) {
                currentPage = Page.CHANNEL_CONFIG;
                rebuildPageLayout();
            }
        }

        if (currentPage == Page.CHANNEL_CONFIG) {
            validateChannelConfigs(node);
        }
    }

    private void validateChannelConfigs(LogisticsNodeEntity node) {
        for (int i = 0; i < 9; i++) {
            ChannelData ch = node.getChannel(i);
            if (ch == null)
                continue;

            int batchCap = switch (ch.getType()) {
                case FLUID -> NodeUpgradeData.getFluidOperationCapMb(node);
                case ENERGY -> NodeUpgradeData.getEnergyOperationCap(node);
                default -> NodeUpgradeData.getItemOperationCap(node);
            };

            if (ch.getBatchSize() > batchCap)
                ch.setBatchSize(batchCap);
            if (ch.getBatchSize() < 1)
                ch.setBatchSize(1);

            if (ch.getType() == ChannelType.ENERGY) {
                if (ch.getTickDelay() != 1)
                    ch.setTickDelay(1);
            } else {
                int minDelay = NodeUpgradeData.getMinTickDelay(node);
                if (ch.getTickDelay() < minDelay)
                    ch.setTickDelay(minDelay);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        this.renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        // Main Background
        g.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, COLOR_BG);
        g.renderOutline(leftPos, topPos, GUI_WIDTH, GUI_HEIGHT, COLOR_BORDER);

        if (currentPage == Page.NETWORK_SELECT) {
            renderNetworkSelectionPage(g, mx, my);
        } else {
            renderChannelConfigPage(g, mx, my);
        }

        // Inventory Separator
        int sepY = topPos + INV_Y - 14;
        g.fill(leftPos + 4, sepY, leftPos + GUI_WIDTH - 4, sepY + 1, COLOR_BORDER);
        g.drawString(font, "Inventory", leftPos + INV_X, topPos + INV_Y - 11, COLOR_DARK_GRAY, false);

        renderPlayerSlots(g);
    }

    private void renderPlayerSlots(GuiGraphics g) {
        // Main Inventory (3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int x = leftPos + INV_X + col * 18 - 1;
                int y = topPos + INV_Y + row * 18 - 1;
                drawSlot(g, x, y);
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            int x = leftPos + INV_X + col * 18 - 1;
            int y = topPos + INV_Y + 58 - 1;
            drawSlot(g, x, y);
        }
    }

    private void drawSlot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, COLOR_SLOT_BG);
        g.renderOutline(x, y, 18, 18, COLOR_SLOT_BORDER);
    }

    private void renderNetworkSelectionPage(GuiGraphics g, int mx, int my) {
        int cx = leftPos + GUI_WIDTH / 2;
        g.drawCenteredString(font, "Select or Create Network", cx, topPos + 8, COLOR_ACCENT);

        drawButton(g, cx - 45, topPos + 54, 90, 16, "Create", mx, my);

        g.fill(leftPos + 12, topPos + 76, leftPos + GUI_WIDTH - 12, topPos + 77, COLOR_BORDER);
        g.drawString(font, "Existing Networks", leftPos + 14, topPos + 82, COLOR_DARK_GRAY, false);

        int listY = topPos + 95;
        int endIdx = Math.min(networkScrollOffset + NETWORKS_PER_PAGE, networkList.size());

        if (networkList.isEmpty()) {
            g.drawCenteredString(font, "No networks yet", cx, listY + 15, COLOR_DARK_GRAY);
        } else {
            for (int i = networkScrollOffset; i < endIdx; i++) {
                SyncNetworkListPayload.NetworkEntry entry = networkList.get(i);
                int y = listY + (i - networkScrollOffset) * 20;
                drawNetworkListEntry(g, entry, leftPos + 14, y, GUI_WIDTH - 28, mx, my);
            }
        }

        if (networkList.size() > NETWORKS_PER_PAGE) {
            String pageInfo = String.format("%d-%d / %d", networkScrollOffset + 1, endIdx, networkList.size());
            g.drawCenteredString(font, pageInfo, cx, topPos + 138, COLOR_DARK_GRAY);
        }
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String label, int mx, int my) {
        boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + h;
        g.fill(x, y, x + w, y + h, hovered ? COLOR_BTN_HOVER : COLOR_BTN_BG);
        g.renderOutline(x, y, w, h, hovered ? COLOR_ACCENT : COLOR_BTN_BORDER);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, hovered ? COLOR_WHITE : COLOR_GRAY);
    }

    private void drawNetworkListEntry(GuiGraphics g, SyncNetworkListPayload.NetworkEntry entry, int x, int y, int w,
            int mx, int my) {
        boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + 17;
        g.fill(x, y, x + w, y + 17, hovered ? COLOR_BTN_HOVER : COLOR_PANEL);
        g.renderOutline(x, y, w, 17, hovered ? COLOR_ACCENT : COLOR_BORDER);
        g.drawString(font, entry.name(), x + 5, y + 4, hovered ? COLOR_WHITE : COLOR_GRAY, false);

        String info = entry.nodeCount() + " nodes";
        g.drawString(font, info, x + w - font.width(info) - 5, y + 4, COLOR_DARK_GRAY, false);
    }

    private void renderChannelConfigPage(GuiGraphics g, int mx, int my) {
        LogisticsNodeEntity node = getMenu().getNode();
        if (node == null)
            return;

        String netName = clipToWidth(getNetworkName(node.getNetworkId()), GUI_WIDTH - 120);
        g.drawCenteredString(font, netName, leftPos + GUI_WIDTH / 2, topPos + 6, COLOR_ACCENT);

        boolean isVisible = node.isRenderVisible();
        drawButton(g, leftPos + 8, topPos + 4, font.width(isVisible ? "Visible" : "Hidden") + 10, 12,
                isVisible ? "Visible" : "Hidden", mx, my);
        drawButton(g, leftPos + GUI_WIDTH - 50, topPos + 4, 42, 12, "Change", mx, my);

        drawChannelTabs(g, node, topPos + 20);

        ChannelData channel = node.getChannel(selectedChannel);
        if (channel == null)
            return;

        drawSettingsPanel(g, channel, leftPos + 10, topPos + 38, mx, my);
        drawFilterGrid(g, channel, leftPos + 168, topPos + 38, mx, my);
    }

    private String getNetworkName(UUID netId) {
        if (netId == null)
            return "No Network";
        return networkList.stream()
                .filter(e -> e.id().equals(netId))
                .map(SyncNetworkListPayload.NetworkEntry::name)
                .findFirst()
                .orElse("Network " + netId.toString().substring(0, 8));
    }

    private String clipToWidth(String text, int maxWidth) {
        if (text == null)
            return "";
        if (font.width(text) <= maxWidth)
            return text;

        String value = text;
        while (value.length() > 3 && font.width(value + "...") > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "...";
    }

    private void drawChannelTabs(GuiGraphics g, LogisticsNodeEntity node, int y) {
        int startX = leftPos + 10;
        for (int i = 0; i < 9; i++) {
            ChannelData ch = node.getChannel(i);
            boolean isSelected = (i == selectedChannel);
            boolean isEnabled = ch != null && ch.isEnabled();

            int borderColor = isSelected ? (isEnabled ? COLOR_ACCENT : 0xFFCC3333) : COLOR_BORDER;
            int bgColor = isSelected ? (isEnabled ? COLOR_ENABLED_BG : COLOR_DISABLED_BG) : COLOR_PANEL;
            int textColor = isSelected ? COLOR_WHITE : (isEnabled ? COLOR_ACCENT : COLOR_DARK_GRAY);

            int x = startX + i * 26;
            g.fill(x, y, x + 24, y + 14, bgColor | 0xFF000000);
            g.renderOutline(x, y, 24, 14, borderColor);
            g.drawCenteredString(font, String.valueOf(i), x + 12, y + 3, textColor);
        }
    }

    private void drawSettingsPanel(GuiGraphics g, ChannelData ch, int x, int y, int mx, int my) {
        int w = 148;
        int rowH = 13;
        int h = rowH * 9 + 4;

        g.fill(x, y, x + w, y + h, COLOR_PANEL);
        g.renderOutline(x, y, w, h, COLOR_BORDER);

        int rowW = w - 4;
        int rx = x + 2;
        int ry = y + 2;

        drawSettingRow(g, rx, ry, rowW, "Status", ch.isEnabled() ? "ENABLED" : "DISABLED",
                ch.isEnabled() ? COLOR_ACCENT : 0xFFCC3333, mx, my, true);
        drawSettingRow(g, rx, ry + rowH, rowW, "Mode", ch.getMode().name(),
                ch.getMode() == ChannelMode.EXPORT ? COLOR_EXPORT : COLOR_IMPORT, mx, my, true);
        drawSettingRow(g, rx, ry + rowH * 2, rowW, "Type", ch.getType().name(), COLOR_WHITE, mx, my, true);
        drawSettingRow(g, rx, ry + rowH * 3, rowW, "Side", ch.getIoDirection().getName().toUpperCase(), COLOR_WHITE, mx,
                my, true);
        drawSettingRow(g, rx, ry + rowH * 4, rowW, "Redstone", formatEnumName(ch.getRedstoneMode().name()), 0xFFFF5555,
                mx, my, !isSettingDisabled(ch, 4));
        drawSettingRow(g, rx, ry + rowH * 5, rowW, "Distrib.", formatEnumName(ch.getDistributionMode().name()),
                0xFFBB88FF, mx, my, true);
        drawSettingRow(g, rx, ry + rowH * 6, rowW, "Priority", editingRow == 6 ? "" : String.valueOf(ch.getPriority()),
                0xFFFFDD44, mx, my, true);
        drawSettingRow(g, rx, ry + rowH * 7, rowW, "Batch", editingRow == 7 ? "" : formatBatchDisplay(ch), COLOR_WHITE,
                mx, my, !isSettingDisabled(ch, 7));
        drawSettingRow(g, rx, ry + rowH * 8, rowW, "Delay", editingRow == 8 ? "" : ch.getTickDelay() + "t", COLOR_WHITE,
                mx, my, !isSettingDisabled(ch, 8));
    }

    private void drawFilterGrid(GuiGraphics g, ChannelData ch, int x, int y, int mx, int my) {

        g.drawString(font, "Filters", x, y, COLOR_DARK_GRAY, false);

        String modeLabel = ch.getFilterMode() == FilterMode.MATCH_ALL ? "All" : "Any";
        int btnW = font.width(modeLabel) + 8;
        int btnX = x + font.width("Filters") + 4;
        drawButton(g, btnX, y - 1, btnW, 10, modeLabel, mx, my);

        // Grid Filters
        int gridY = y + 12;
        drawSlotGrid(g, x, gridY, 3, 3, mx, my);

        int upgY = gridY + 3 * 19 + 2;
        g.drawString(font, "Upgrades", x, upgY, COLOR_DARK_GRAY, false);

        // Grid Upgrades
        drawSlotGrid(g, x, upgY + 10, 2, 2, mx, my);
    }

    private void drawSlotGrid(GuiGraphics g, int startX, int startY, int rows, int cols, int mx, int my) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int x = startX + c * 19;
                int y = startY + r * 19;
                drawSlot(g, x - 1, y - 1);
            }
        }
    }

    private void drawSettingRow(GuiGraphics g, int x, int y, int w, String label, String value, int color, int mx,
            int my, boolean enabled) {
        boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + 13;
        if (enabled && hovered) {
            g.fill(x, y, x + w, y + 13, COLOR_HOVER);
        }
        g.drawString(font, label, x + 4, y + 4, enabled ? COLOR_GRAY : COLOR_DISABLED_TXT, false);
        g.drawString(font, value, x + w - font.width(value) - 4, y + 4, enabled ? color : COLOR_DISABLED_TXT, false);
    }

    private String formatEnumName(String name) {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!sb.isEmpty())
                sb.append(' ');
            sb.append(p.charAt(0)).append(p.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private String formatBatchDisplay(ChannelData ch) {
        if (ch.getType() == ChannelType.FLUID)
            return ch.getBatchSize() + "mb";
        if (ch.getType() == ChannelType.ENERGY)
            return ch.getBatchSize() + " RF/t";
        return String.valueOf(ch.getBatchSize());
    }

    private boolean isSettingDisabled(ChannelData ch, int row) {
        if (ch.getType() == ChannelType.ENERGY && row == 8)
            return true;
        return ch.getMode() == ChannelMode.IMPORT && row == 4;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (editingRow != -1 && numericEditBox != null && !numericEditBox.isMouseOver(mx, my)) {
            stopNumericEdit(true);
        }

        if (isHoveringMenuSlot(mx, my)) {
            return super.mouseClicked(mx, my, btn);
        }

        if (currentPage == Page.NETWORK_SELECT) {
            if (handleNetworkPageClick(mx, my))
                return true;
        } else {
            if (handleChannelPageClick(mx, my, btn))
                return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    private boolean handleNetworkPageClick(double mx, double my) {
        if (isHoveringAbs(leftPos + GUI_WIDTH / 2 - 45, topPos + 54, 90, 16, mx, my)) {
            String name = networkNameField.getValue().trim();
            if (name.isEmpty())
                name = "Unnamed";
            sendNetworkAssign(Optional.empty(), name);
            return true;
        }

        int listY = topPos + 95;
        int endIdx = Math.min(networkScrollOffset + NETWORKS_PER_PAGE, networkList.size());
        for (int i = networkScrollOffset; i < endIdx; i++) {
            int y = listY + (i - networkScrollOffset) * 20;
            if (isHoveringAbs(leftPos + 14, y, GUI_WIDTH - 28, 17, mx, my)) {
                sendNetworkAssign(Optional.of(networkList.get(i).id()), "");
                return true;
            }
        }
        return false;
    }

    private boolean handleChannelPageClick(double mx, double my, int btn) {
        LogisticsNodeEntity node = getMenu().getNode();
        if (node == null)
            return false;

        if (isHoveringAbs(leftPos + 8, topPos + 4, font.width(node.isRenderVisible() ? "Visible" : "Hidden") + 10, 12, mx,
                my)) {
            node.setRenderVisible(!node.isRenderVisible());
            PacketDistributor.sendToServer(new ToggleNodeVisibilityPayload(node.getId()));
            return true;
        }

        if (isHoveringAbs(leftPos + GUI_WIDTH - 50, topPos + 4, 42, 12, mx, my)) {
            currentPage = Page.NETWORK_SELECT;
            rebuildPageLayout();
            return true;
        }

        for (int i = 0; i < 9; i++) {
            if (isHoveringAbs(leftPos + 10 + i * 26, topPos + 20, 24, 14, mx, my)) {
                selectedChannel = i;
                getMenu().setSelectedChannel(i);
                PacketDistributor.sendToServer(new SelectNodeChannelPayload(node.getId(), i));
                return true;
            }
        }

        return handleSettingsClick(node, mx, my, btn);
    }

    private boolean handleSettingsClick(LogisticsNodeEntity node, double mx, double my, int btn) {
        ChannelData ch = node.getChannel(selectedChannel);
        if (ch == null || (btn != 0 && btn != 1))
            return false;

        int rowH = 13;
        int startY = topPos + 40;
        int startX = leftPos + 12;
        int w = 144;

        for (int row = 0; row < 9; row++) {
            int y = startY + row * rowH;
            if (isHoveringAbs(startX, y, w, rowH, mx, my)) {
                if (isSettingDisabled(ch, row))
                    return true;

                if (row >= 6 && row <= 8) {
                    if (hasAltDown()) {
                        setNumericExtremum(ch, row, btn == 0);
                        commitChannelUpdate(node, ch);
                        return true;
                    }
                    if (checkDoubleClicks(row)) {
                        startNumericEdit(ch, row, startX + w / 2 + 2, y);
                        return true;
                    }
                }

                int dir = (btn == 0) ? 1 : -1;
                cycleSetting(ch, row, dir);
                commitChannelUpdate(node, ch);
                return true;
            }
        }

        int modeBtnX = leftPos + 168 + font.width("Filters") + 4;
        int modeBtnY = topPos + 38 - 1;
        String modeLabel = ch.getFilterMode() == FilterMode.MATCH_ALL ? "All" : "Any";
        int modeBtnW = font.width(modeLabel) + 8;

        if (isHoveringAbs(modeBtnX, modeBtnY, modeBtnW, 10, mx, my)) {
            ch.setFilterMode(cycleEnum(ch.getFilterMode(), (btn == 0) ? 1 : -1));
            commitChannelUpdate(node, ch);
            return true;
        }

        return false;
    }

    private void cycleSetting(ChannelData ch, int row, int dir) {
        switch (row) {
            case 0 -> ch.setEnabled(!ch.isEnabled());
            case 1 -> ch.setMode(cycleEnum(ch.getMode(), dir));
            case 2 -> {
                ChannelType oldT = ch.getType();
                ch.setType(cycleEnum(ch.getType(), dir));
                resetDefaultsForTypeChange(ch, oldT, ch.getType());
            }
            case 3 -> ch.setIoDirection(cycleEnum(ch.getIoDirection(), dir));
            case 4 -> ch.setRedstoneMode(cycleEnum(ch.getRedstoneMode(), dir));
            case 5 -> ch.setDistributionMode(cycleEnum(ch.getDistributionMode(), dir));
            case 6 -> ch.setPriority(ch.getPriority() + (hasShiftDown() ? 10 : 1) * dir);
            case 7 -> ch.setBatchSize(ch.getBatchSize() + (hasShiftDown() ? 8 : 1) * dir);
            case 8 -> ch.setTickDelay(ch.getTickDelay() + (hasShiftDown() ? 10 : 1) * dir);
        }
    }

    private <T extends Enum<T>> T cycleEnum(T current, int dir) {
        T[] values = current.getDeclaringClass().getEnumConstants();
        int index = (current.ordinal() + dir + values.length) % values.length;
        return values[index];
    }

    private void resetDefaultsForTypeChange(ChannelData ch, ChannelType oldT, ChannelType newT) {
        if (oldT == newT)
            return;
        if (newT == ChannelType.FLUID)
            ch.setBatchSize(100);
        else if (newT == ChannelType.ENERGY) {
            ch.setBatchSize(2000);
            ch.setTickDelay(1);
        } else if (oldT == ChannelType.ENERGY) {
            ch.setBatchSize(8);
            ch.setTickDelay(20);
        }
    }

    private void startNumericEdit(ChannelData ch, int row, int x, int y) {
        stopNumericEdit(false);
        editingRow = row;

        String val = switch (row) {
            case 6 -> String.valueOf(ch.getPriority());
            case 7 -> String.valueOf(ch.getBatchSize());
            case 8 -> String.valueOf(ch.getTickDelay());
            default -> "";
        };

        numericEditBox = new EditBox(font, x, y, 70, 13, Component.empty());
        numericEditBox.setMaxLength(10);
        numericEditBox.setValue(val);
        numericEditBox.setBordered(true);
        numericEditBox.setTextColor(COLOR_WHITE);
        numericEditBox.setFocused(true);
        addRenderableWidget(numericEditBox);
        setFocused(numericEditBox);
    }

    private void stopNumericEdit(boolean commit) {
        if (editingRow == -1 || numericEditBox == null)
            return;

        if (commit) {
            try {
                int val = Integer.parseInt(numericEditBox.getValue().trim());
                LogisticsNodeEntity node = getMenu().getNode();
                ChannelData ch = node.getChannel(selectedChannel);
                if (ch != null) {
                    switch (editingRow) {
                        case 6 -> ch.setPriority(val);
                        case 7 -> ch.setBatchSize(val);
                        case 8 -> ch.setTickDelay(val);
                    }
                    commitChannelUpdate(node, ch);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        removeWidget(numericEditBox);
        numericEditBox = null;
        editingRow = -1;
    }

    private void commitChannelUpdate(LogisticsNodeEntity node, ChannelData ch) {
        validateChannelConfigs(node);
        PacketDistributor.sendToServer(new UpdateChannelPayload(
                node.getId(), selectedChannel, ch.isEnabled(),
                ch.getMode().ordinal(), ch.getType().ordinal(),
                ch.getBatchSize(), ch.getTickDelay(),
                ch.getIoDirection().ordinal(),
                ch.getRedstoneMode().ordinal(),
                ch.getDistributionMode().ordinal(),
                ch.getFilterMode().ordinal(),
                ch.getPriority()));
    }

    private void sendNetworkAssign(Optional<UUID> id, String name) {
        LogisticsNodeEntity node = getMenu().getNode();
        if (node != null) {
            PacketDistributor.sendToServer(new AssignNetworkPayload(node.getId(), id, name));
        }
    }

    private boolean checkDoubleClicks(int row) {
        long now = System.currentTimeMillis();
        boolean isDouble = (lastSettingClickRow == row && now - lastSettingClickTime < 250);
        lastSettingClickRow = row;
        lastSettingClickTime = now;
        return isDouble;
    }

    private void setNumericExtremum(ChannelData ch, int row, boolean max) {
        switch (row) {
            case 6 -> ch.setPriority(max ? PRIORITY_MAX : PRIORITY_MIN);
            case 7 -> ch.setBatchSize(max ? BATCH_MAX : BATCH_MIN);
            case 8 -> ch.setTickDelay(max ? DELAY_MAX : DELAY_MIN);
        }
    }

    private boolean isHoveringAbs(int x, int y, int w, int h, double mx, double my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private boolean isHoveringMenuSlot(double mx, double my) {
        for (Slot slot : menu.slots) {
            if (isHovering(slot.x, slot.y, 16, 16, mx, my)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (editingRow != -1) {
            if (key == 256)
                stopNumericEdit(false);
            else if (key == 257 || key == 335)
                stopNumericEdit(true);
            else
                numericEditBox.keyPressed(key, scan, modifiers);
            return true;
        }
        if (networkNameField != null && networkNameField.isFocused()) {
            if (key == 256)
                networkNameField.setFocused(false);
            else if (key == 257 || key == 335)
                networkNameField.setFocused(false);
            else
                networkNameField.keyPressed(key, scan, modifiers);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (editingRow != -1 && numericEditBox != null) {
            if (Character.isDigit(ch) || ch == '-')
                return numericEditBox.charTyped(ch, modifiers);
            return true;
        }
        if (networkNameField != null && networkNameField.isFocused()) {
            return networkNameField.charTyped(ch, modifiers);
        }
        return super.charTyped(ch, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (currentPage == Page.NETWORK_SELECT) {
            if (sy > 0 && networkScrollOffset > 0)
                networkScrollOffset--;
            else if (sy < 0 && networkScrollOffset + NETWORKS_PER_PAGE < networkList.size())
                networkScrollOffset++;
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    public void receiveNetworkList(List<SyncNetworkListPayload.NetworkEntry> networks) {
        this.networkList = new ArrayList<>(networks);
    }
}
