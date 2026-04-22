package me.almana.logisticsnetworks.client.screen;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.DistributionMode;
import me.almana.logisticsnetworks.data.FilterMode;
import me.almana.logisticsnetworks.data.RedstoneMode;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.integration.ars.ArsCompat;
import me.almana.logisticsnetworks.integration.guideme.GuideMeCompat;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.menu.NodeMenu;
import me.almana.logisticsnetworks.network.AssignNetworkPayload;
import me.almana.logisticsnetworks.network.RenameNetworkPayload;
import me.almana.logisticsnetworks.network.RequestNetworkLabelsPayload;
import me.almana.logisticsnetworks.network.SelectNodeChannelPayload;
import me.almana.logisticsnetworks.network.SetChannelNamePayload;
import me.almana.logisticsnetworks.network.SetNodeLabelPayload;
import me.almana.logisticsnetworks.network.SyncNetworkListPayload;
import me.almana.logisticsnetworks.network.ToggleNodeVisibilityPayload;
import me.almana.logisticsnetworks.network.UpdateChannelPayload;
import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.client.theme.ThemeState;
import me.almana.logisticsnetworks.client.theme.Themes;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    private static final int GUI_HEIGHT = 298;
    private static final int INV_X = 47;
    private static final int INV_Y = 218;
    private static final int NETWORKS_PER_PAGE = 3;
    private static final int BATCH_MIN = 1;
    private static final int BATCH_MAX = 1_000_000;
    private static final int DELAY_MIN = 1;
    private static final int DELAY_MAX = 10_000;
    private static final int PRIORITY_MIN = -99;
    private static final int PRIORITY_MAX = 99;

    private Theme theme() { return ThemeState.active(); }
    private int cPanel() { return theme().surface2(); }
    private int cBorder() { return theme().border(); }
    private int cBorderStrong() { return theme().borderStrong(); }
    private int cAccent() { return theme().accent(); }
    private int cDanger() { return theme().danger(); }
    private int cWarn() { return theme().warn(); }
    private int cInfo() { return theme().info(); }
    private int cText() { return theme().text(); }
    private int cMuted() { return theme().textMuted(); }
    private int cSubtle() { return theme().textSubtle(); }
    private int cHover() { return 0x22FFFFFF; }
    private final Runnable themeListener = () -> { if (getMenu() != null) rebuildPageLayout(); };

    private Page currentPage = Page.NETWORK_SELECT;
    private int selectedChannel = 0;
    public int getSelectedChannel() { return selectedChannel; }
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
    private String lastNetworkFilter = "";
    private int networkScrollOffset = 0;

    // Rename state
    private UUID renamingNetworkId = null;
    private EditBox renameEditBox = null;

    // Settings scroll state
    private int settingsScrollOffset = 0;
    private static final int SETTINGS_VISIBLE_ROWS = 9;
    private static final int SETTINGS_TOTAL_ROWS = 9;

    private int settingsHoverRow = -1;
    private long settingsHoverStartTime = 0;
    private static final long TOOLTIP_DELAY = 1000L;

    private long lastTabClickTime = 0;
    private int lastTabClickIndex = -1;
    private boolean channelNameEditing = false;
    private EditBox channelNameEditBox = null;
    private int editingChannelIndex = -1;
    private Component hoveredChannelName = null;

    // Label picker state
    private boolean labelPickerOpen = false;
    private EditBox labelEditBox = null;
    private List<String> networkLabels = new ArrayList<>();
    private int labelScrollOffset = 0;
    private static final int LABEL_PICKER_ENTRY_H = 14;
    private static final int LABEL_PICKER_MAX_VISIBLE = 5;

    private int getLabelPickerWidth() {
        int maxW = font.width(tr("gui.logisticsnetworks.node.label.clear")) + 24;
        if (labelEditBox != null) {
            maxW = Math.max(maxW, font.width(labelEditBox.getValue()) + 24);
        }
        for (String lbl : networkLabels) {
            maxW = Math.max(maxW, font.width(lbl) + 24);
        }
        if (labelEditBox != null && labelEditBox.getValue().length() > 40) {
            maxW = Math.max(maxW, 90);
        }
        return Math.max(80, Math.min(144, maxW));
    }

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
            ThemeState.addListener(themeListener);
        }
        selectedChannel = getMenu().getSelectedChannel();
        rebuildPageLayout();
    }

    private void rebuildPageLayout() {
        stopNumericEdit(false);
        stopRenameEdit(false);
        clearWidgets();
        getMenu().setNodeSlotsVisible(currentPage == Page.CHANNEL_CONFIG);
        if (currentPage == Page.NETWORK_SELECT) {
            int cx = leftPos + GUI_WIDTH / 2;
            int y = topPos + 32;
            networkNameField = new EditBox(this.font, cx - 75, y, 150, 16, Component.empty());
            networkNameField.setMaxLength(32);
            networkNameField.setHint(Component.translatable("gui.logisticsnetworks.node.network_name_hint"));
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
                case CHEMICAL -> NodeUpgradeData.getChemicalOperationCap(node);
                case SOURCE -> NodeUpgradeData.getSourceOperationCap(node);
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
        if (labelPickerOpen && currentPage == Page.CHANNEL_CONFIG) {
            renderLabelPicker(g, mx, my, pt);
        }
        if (tweaksOpen) {
            renderTweaksPanel(g, mx, my);
        }
        this.renderTooltip(g, mx, my);
        if (hoveredChannelName != null && currentPage == Page.CHANNEL_CONFIG) {
            g.renderTooltip(font, hoveredChannelName, mx, my);
        }
        if (settingsHoverRow >= 0 && currentPage == Page.CHANNEL_CONFIG
                && System.currentTimeMillis() - settingsHoverStartTime >= TOOLTIP_DELAY && !tweaksOpen) {
            LogisticsNodeEntity node = getMenu().getNode();
            List<Component> tip = getSettingTooltip(node.getChannel(selectedChannel), settingsHoverRow);
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    @Override
    public void onClose() {
        ThemeState.removeListener(themeListener);
        super.onClose();
    }

    @Override
    public void removed() {
        ThemeState.removeListener(themeListener);
        super.removed();
    }

    private boolean tweaksOpen = false;

    private int tweaksFabX() { return leftPos + GUI_WIDTH - 56; }
    private int tweaksFabY() { return topPos + INV_Y - 36; }
    private int tweaksFabW() { return 48; }
    private int tweaksFabH() { return 10; }

    private boolean isInTweaksFab(double mx, double my) {
        return mx >= tweaksFabX() && mx <= tweaksFabX() + tweaksFabW()
                && my >= tweaksFabY() && my <= tweaksFabY() + tweaksFabH();
    }

    private int docsFabX() { return tweaksFabX() - (docsFabW() + 6); }
    private int docsFabY() { return tweaksFabY(); }
    private int docsFabW() { return 38; }
    private int docsFabH() { return tweaksFabH(); }

    private boolean isInDocsFab(double mx, double my) {
        return mx >= docsFabX() && mx <= docsFabX() + docsFabW()
                && my >= docsFabY() && my <= docsFabY() + docsFabH();
    }

    private void renderDocsFab(GuiGraphics g, int mx, int my) {
        if (currentPage != Page.CHANNEL_CONFIG) return;
        int fx = docsFabX();
        int fy = docsFabY();
        int fw = docsFabW();
        int fh = docsFabH();
        boolean hovered = !tweaksOpen && isInDocsFab(mx, my);
        String label = tr("gui.logisticsnetworks.node.docs");
        Theme t = theme();
        int bg = hovered ? t.surface() : t.surface2();
        int border = hovered ? t.accent() : t.borderStrong();
        ThemePaint.roundRect(g, fx, fy, fw, fh, fh / 2, bg, t.sharpCorners());
        ThemePaint.roundOutline(g, fx, fy, fw, fh, fh / 2, border, t.sharpCorners());
        int gx = fx + 4;
        int gy = fy + fh / 2 - 1;
        g.fill(gx, gy, gx + 3, gy + 1, t.text());
        g.drawString(font, label, fx + 10, fy + 1, t.text(), false);
    }

    private void renderTweaksFab(GuiGraphics g, int mx, int my) {
        if (currentPage != Page.CHANNEL_CONFIG) return;
        int fx = tweaksFabX();
        int fy = tweaksFabY();
        int fw = tweaksFabW();
        int fh = tweaksFabH();
        boolean hovered = !tweaksOpen && isInTweaksFab(mx, my);
        String label = tr("gui.logisticsnetworks.node.tweaks");
        Theme t = theme();
        int bg = hovered ? t.surface() : t.surface2();
        int border = hovered ? t.accent() : t.borderStrong();
        ThemePaint.roundRect(g, fx, fy, fw, fh, fh / 2, bg, t.sharpCorners());
        ThemePaint.roundOutline(g, fx, fy, fw, fh, fh / 2, border, t.sharpCorners());
        int gx = fx + 4;
        int gy = fy + fh / 2 - 1;
        g.fill(gx, gy, gx + 3, gy + 1, t.text());
        g.drawString(font, label, fx + 10, fy + 1, t.text(), false);
    }

    private static final int TWEAKS_W = 164;
    private static final int TWEAKS_H = 150;

    private int tweaksX() { return leftPos + (GUI_WIDTH - TWEAKS_W) / 2; }
    private int tweaksY() { return topPos + (GUI_HEIGHT - TWEAKS_H) / 2; }

    private void renderTweaksPanel(GuiGraphics g, int mx, int my) {
        Theme t = theme();
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        ThemePaint.modalVeil(g, leftPos, topPos, GUI_WIDTH, GUI_HEIGHT, t);

        int x = tweaksX();
        int y = tweaksY();
        ThemePaint.window(g, x, y, TWEAKS_W, TWEAKS_H, t);

        g.drawString(font, tr("gui.logisticsnetworks.node.tweaks"), x + 10, y + 8, cMuted(), false);
        String closeStr = "\u00D7";
        int closeX = x + TWEAKS_W - 14;
        int closeY = y + 6;
        boolean closeHover = mx >= closeX && mx <= closeX + 10 && my >= closeY && my <= closeY + 10;
        g.drawString(font, closeStr, closeX + 2, closeY + 1, closeHover ? cText() : cMuted(), false);

        ThemePaint.divider(g, x + 8, y + 20, TWEAKS_W - 16, t);

        g.drawString(font, tr("gui.logisticsnetworks.node.tweaks.theme"), x + 10, y + 26, cSubtle(), false);

        int cols = 2;
        int swatchW = (TWEAKS_W - 20 - (cols - 1) * 4) / cols;
        int swatchH = 22;
        int startY = y + 36;
        for (int i = 0; i < Themes.ALL.size(); i++) {
            Theme preview = Themes.ALL.get(i);
            int col = i % cols;
            int row = i / cols;
            int sx = x + 10 + col * (swatchW + 4);
            int sy = startY + row * (swatchH + 4);
            boolean active = preview.id().equals(t.id());
            boolean hovered = mx >= sx && mx <= sx + swatchW && my >= sy && my <= sy + swatchH;
            ThemePaint.swatchPreview(g, sx, sy, swatchW, 12, preview, t);
            int labelY = sy + 13;
            int fg = active ? cAccent() : (hovered ? cText() : cMuted());
            ThemePaint.drawCentered(g, font, preview.label(), sx + swatchW / 2, labelY, fg);
            if (active) {
                ThemePaint.roundOutline(g, sx, sy, swatchW, swatchH, 2, cAccent(), t.sharpCorners());
            }
        }

        g.pose().popPose();
    }

    private boolean handleTweaksClick(double mx, double my) {
        int x = tweaksX();
        int y = tweaksY();
        if (mx < x || mx > x + TWEAKS_W || my < y || my > y + TWEAKS_H) {
            tweaksOpen = false;
            return true;
        }
        int closeX = x + TWEAKS_W - 14;
        int closeY = y + 6;
        if (mx >= closeX && mx <= closeX + 10 && my >= closeY && my <= closeY + 10) {
            tweaksOpen = false;
            return true;
        }

        int cols = 2;
        int swatchW = (TWEAKS_W - 20 - (cols - 1) * 4) / cols;
        int swatchH = 22;
        int startY = y + 36;
        for (int i = 0; i < Themes.ALL.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int sx = x + 10 + col * (swatchW + 4);
            int sy = startY + row * (swatchH + 4);
            if (mx >= sx && mx <= sx + swatchW && my >= sy && my <= sy + swatchH) {
                ThemeState.setTheme(Themes.ALL.get(i));
                return true;
            }
        }

        return true;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        Theme t = theme();
        ThemePaint.window(g, leftPos, topPos, GUI_WIDTH, GUI_HEIGHT, t);

        if (currentPage == Page.NETWORK_SELECT) {
            renderNetworkSelectionPage(g, mx, my);
        } else {
            renderChannelConfigPage(g, mx, my);
        }

        int sepY = topPos + INV_Y - 18;
        ThemePaint.divider(g, leftPos + 6, sepY, GUI_WIDTH - 12, t);
        g.drawString(font, Component.translatable("gui.logisticsnetworks.node.inventory"), leftPos + INV_X,
                topPos + INV_Y - 12, cSubtle(), false);

        renderPlayerSlots(g);

        renderDocsFab(g, mx, my);
        renderTweaksFab(g, mx, my);
    }

    private void renderPlayerSlots(GuiGraphics g) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int x = leftPos + INV_X + col * 18 - 1;
                int y = topPos + INV_Y + row * 18 - 1;
                drawSlot(g, x, y);
            }
        }
        for (int col = 0; col < 9; col++) {
            int x = leftPos + INV_X + col * 18 - 1;
            int y = topPos + INV_Y + 58 - 1;
            drawSlot(g, x, y);
        }
    }

    private void drawSlot(GuiGraphics g, int x, int y) {
        ThemePaint.slot(g, x, y, 18, theme());
    }

    private void renderNetworkSelectionPage(GuiGraphics g, int mx, int my) {
        int cx = leftPos + GUI_WIDTH / 2;
        ThemePaint.drawCentered(g, font, Component.translatable("gui.logisticsnetworks.select_network"), cx, topPos + 8, cAccent());

        drawButton(g, cx - 45, topPos + 54, 90, 16,
                tr("gui.logisticsnetworks.create_network"), mx, my);

        g.fill(leftPos + 12, topPos + 76, leftPos + GUI_WIDTH - 12, topPos + 77, cBorder());
        g.drawString(font, Component.translatable("gui.logisticsnetworks.node.existing_networks"), leftPos + 14,
                topPos + 82, cSubtle(), false);

        String currentFilter = networkNameField != null ? networkNameField.getValue().trim() : "";
        if (!currentFilter.equals(lastNetworkFilter)) {
            lastNetworkFilter = currentFilter;
            networkScrollOffset = 0;
        }

        List<SyncNetworkListPayload.NetworkEntry> filtered = getFilteredNetworks();
        int listY = topPos + 95;
        int endIdx = Math.min(networkScrollOffset + NETWORKS_PER_PAGE, filtered.size());

        if (filtered.isEmpty()) {
            ThemePaint.drawCentered(g, font, Component.translatable("gui.logisticsnetworks.no_networks"), cx, listY + 15, cSubtle());
        } else {
            for (int i = networkScrollOffset; i < endIdx; i++) {
                SyncNetworkListPayload.NetworkEntry entry = filtered.get(i);
                int y = listY + (i - networkScrollOffset) * 20;
                drawNetworkListEntry(g, entry, leftPos + 14, y, GUI_WIDTH - 28, mx, my);
            }
        }

        if (filtered.size() > NETWORKS_PER_PAGE) {
            int pageInfoY = listY + NETWORKS_PER_PAGE * 20 + 4;
            String pageInfo = tr("gui.logisticsnetworks.node.page_info", networkScrollOffset + 1, endIdx,
                    filtered.size());
            ThemePaint.drawCentered(g, font, pageInfo, cx, pageInfoY, cSubtle());
        }
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String label, int mx, int my) {
        boolean hovered = !labelPickerOpen && mx >= x && mx <= x + w && my >= y && my <= y + h;
        ThemePaint.button(g, font, x, y, w, h, label, hovered, theme());
    }

    private void drawNetworkListEntry(GuiGraphics g, SyncNetworkListPayload.NetworkEntry entry, int x, int y, int w,
            int mx, int my) {
        boolean isRenaming = entry.id().equals(renamingNetworkId);
        int renameBtnW = font.width(tr("gui.logisticsnetworks.rename")) + 14;
        int renameBtnX = x + w - renameBtnW;

        if (isRenaming && renameEditBox != null) {
            g.fill(x, y, x + w, y + 17, cPanel());
            g.renderOutline(x, y, w, 17, cAccent());
            return;
        }

        boolean hoveredRow = mx >= x && mx <= x + w && my >= y && my <= y + 17;
        boolean hoveredRename = mx >= renameBtnX && mx <= renameBtnX + renameBtnW && my >= y && my <= y + 17;

        g.fill(x, y, x + w, y + 17, hoveredRow ? cBorderStrong() : cPanel());
        g.renderOutline(x, y, w, 17, hoveredRow ? cAccent() : cBorder());
        g.drawString(font, entry.name(), x + 5, y + 4, hoveredRow ? cText() : cMuted(), false);

        String info = tr("gui.logisticsnetworks.node.network_nodes", entry.nodeCount());
        int infoX = renameBtnX - font.width(info) - 4;
        g.drawString(font, info, infoX, y + 4, cSubtle(), false);

        // Rename button
        g.fill(renameBtnX, y, renameBtnX + renameBtnW, y + 17, hoveredRename ? cBorderStrong() : cPanel());
        g.renderOutline(renameBtnX, y, renameBtnW, 17, hoveredRename ? cAccent() : cBorder());
        ThemePaint.drawCentered(g, font, tr("gui.logisticsnetworks.rename"), renameBtnX + renameBtnW / 2, y + 4,
                hoveredRename ? cText() : cMuted());
    }

    private void renderChannelConfigPage(GuiGraphics g, int mx, int my) {
        Theme t = theme();
        LogisticsNodeEntity node = getMenu().getNode();
        if (node == null)
            return;

        String netName = clipToWidth(getNetworkName(node.getNetworkId()), GUI_WIDTH - 140);
        int netNameW = font.width(netName);
        int netChipX = leftPos + (GUI_WIDTH - netNameW - 12) / 2;
        ThemePaint.chip(g, font, netChipX, topPos + 4, netName, t);

        boolean isVisible = node.isRenderVisible();
        String visibilityLabel = getVisibilityLabel(isVisible);
        int visW = font.width(visibilityLabel) + 16;
        boolean visHover = !labelPickerOpen && mx >= leftPos + 8 && mx <= leftPos + 8 + visW
                && my >= topPos + 4 && my <= topPos + 16;
        ThemePaint.visibleToggle(g, font, leftPos + 8, topPos + 4, visW, 12,
                visibilityLabel, isVisible, visHover, t);

        String changeLabel = tr("gui.logisticsnetworks.node.change_network");
        boolean changeHover = !labelPickerOpen && mx >= leftPos + GUI_WIDTH - 52 && mx <= leftPos + GUI_WIDTH - 8
                && my >= topPos + 4 && my <= topPos + 16;
        ThemePaint.ghostButton(g, font, leftPos + GUI_WIDTH - 52, topPos + 4, 44, 12,
                changeLabel, changeHover, t);

        drawChannelTabs(g, node, topPos + 22, mx, my);

        hoveredChannelName = null;
        if (!channelNameEditing) {
            for (int i = 0; i < 9; i++) {
                ChannelData ch = node.getChannel(i);
                if (ch != null) {
                    int tabX = leftPos + 10 + i * 26;
                    if (mx >= tabX && mx <= tabX + 24 && my >= topPos + 22 && my <= topPos + 34) {
                        if (!ch.getName().isEmpty()) {
                            hoveredChannelName = Component.literal(ch.getName());
                        } else {
                            hoveredChannelName = Component.translatable("gui.logisticsnetworks.node.channel_name.set_tooltip");
                        }
                    }
                }
            }
        }

        if (!channelNameEditing) {
            String nodeLabel = node.getNodeLabel();
            String labelDisplay = nodeLabel.isEmpty() ? tr("gui.logisticsnetworks.node.label.set") : nodeLabel;
            int labelW = font.width(labelDisplay) + 14;
            int labelX = leftPos + 10 + (148 - labelW) / 2;
            int labelY = topPos + 40;
            boolean labelHover = !labelPickerOpen && mx >= labelX && mx <= labelX + labelW
                    && my >= labelY && my <= labelY + 12;
            ThemePaint.setLabelBtn(g, font, labelX, labelY, labelW, 12, labelDisplay, labelHover, t);
        } else if (channelNameEditBox != null) {
            int ebx = channelNameEditBox.getX() - 2;
            int eby = channelNameEditBox.getY() - 2;
            int ebw = channelNameEditBox.getWidth() + 4;
            int ebh = channelNameEditBox.getHeight() + 4;
            ThemePaint.panel(g, ebx, eby, ebw, ebh, t);
            ThemePaint.roundOutline(g, ebx, eby, ebw, ebh, 2, cAccent(), t.sharpCorners());
        }

        ChannelData channel = node.getChannel(selectedChannel);
        if (channel == null)
            return;

        drawSettingsPanel(g, channel, leftPos + 10, topPos + 58, mx, my);
        drawFilterGrid(g, channel, leftPos + 168, topPos + 56, mx, my);
    }

    private void renderLabelPicker(GuiGraphics g, int mx, int my, float pt) {
        int pickerW = getLabelPickerWidth();
        int pickerX = leftPos + 10 + (148 - pickerW) / 2;
        int pickerY = topPos + 58;
        int entryCount = Math.min(networkLabels.size(), LABEL_PICKER_MAX_VISIBLE);
        int listH = entryCount * LABEL_PICKER_ENTRY_H;
        int pickerH = 22 + listH + (networkLabels.isEmpty() ? 0 : 4) + 16; // edit + list + clear btn

        if (labelEditBox != null) {
            labelEditBox.setX(pickerX + 2);
            labelEditBox.setWidth(pickerW - 4);
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 200);

        // Background
        g.fill(pickerX - 1, pickerY - 1, pickerX + pickerW + 1, pickerY + pickerH + 1, cBorder());
        g.fill(pickerX, pickerY, pickerX + pickerW, pickerY + pickerH, cPanel());

        // Edit Box
        if (labelEditBox != null) {
            labelEditBox.render(g, mx, my, pt);
        }

        // Character counter (shown only when > 40 chars)
        if (labelEditBox != null && labelEditBox.getValue().length() > 40) {
            String counter = labelEditBox.getValue().length() + "/48";
            int counterX = pickerX + pickerW - font.width(counter) - 3;
            int counterColor = labelEditBox.getValue().length() >= 48 ? cDanger() : cSubtle();
            g.drawString(font, counter, counterX, pickerY + 22, counterColor, false);
        }

        // Existing labels list
        int listY = pickerY + 22;
        int maxScroll = Math.max(0, networkLabels.size() - LABEL_PICKER_MAX_VISIBLE);
        labelScrollOffset = Math.max(0, Math.min(labelScrollOffset, maxScroll));

        for (int i = 0; i < LABEL_PICKER_MAX_VISIBLE && (i + labelScrollOffset) < networkLabels.size(); i++) {
            int idx = i + labelScrollOffset;
            String label = networkLabels.get(idx);
            int entryY = listY + i * LABEL_PICKER_ENTRY_H;
            boolean hovered = mx >= pickerX + 2 && mx < pickerX + pickerW - 2
                    && my >= entryY && my < entryY + LABEL_PICKER_ENTRY_H;
            if (hovered) {
                g.fill(pickerX + 2, entryY, pickerX + pickerW - 2,
                        entryY + LABEL_PICKER_ENTRY_H, cHover());
            }
            String display = label;
            if (font.width(display) > pickerW - 8) {
                display = font.plainSubstrByWidth(display, pickerW - 13) + "...";
            }
            ThemePaint.drawCentered(g, font, display, pickerX + pickerW / 2, entryY + 3, cInfo());
        }

        // Clear button
        int clearY = listY + listH + 2;
        String clearLabel = tr("gui.logisticsnetworks.node.label.clear");
        int clearW = font.width(clearLabel) + 8;
        int clearX = pickerX + (pickerW - clearW) / 2;
        boolean clearHovered = mx >= clearX && mx < clearX + clearW
                && my >= clearY && my < clearY + 12;
        g.fill(clearX, clearY, clearX + clearW, clearY + 12,
                clearHovered ? cBorderStrong() : cPanel());
        g.renderOutline(clearX, clearY, clearW, 12, cBorder());
        g.drawString(font, clearLabel, clearX + 4, clearY + 2, cMuted(), false);

        g.pose().popPose();
    }

    private String getNetworkName(UUID netId) {
        if (netId == null)
            return tr("gui.logisticsnetworks.node.network.none");
        String listName = networkList.stream()
                .filter(e -> e.id().equals(netId))
                .map(SyncNetworkListPayload.NetworkEntry::name)
                .findFirst()
                .orElse(null);
        if (listName != null)
            return listName;
        // Fallback to entity synced name
        LogisticsNodeEntity node = getMenu().getNode();
        if (node != null) {
            String syncedName = node.getNetworkName();
            if (syncedName != null && !syncedName.isBlank())
                return syncedName;
        }
        return tr("gui.logisticsnetworks.node.network.fallback", netId.toString().substring(0, 8));
    }

    private String clipToWidth(String text, int maxWidth) {
        if (text == null)
            return "";
        if (maxWidth <= 0)
            return "";
        if (font.width(text) <= maxWidth)
            return text;

        String ellipsis = "...";
        if (font.width(ellipsis) > maxWidth)
            return "";

        String value = text;
        while (!value.isEmpty() && font.width(value + ellipsis) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }

        return value.isEmpty() ? ellipsis : value + ellipsis;
    }

    private void drawChannelTabs(GuiGraphics g, LogisticsNodeEntity node, int y, int mx, int my) {
        Theme t = theme();
        int startX = leftPos + 10;
        for (int i = 0; i < 9; i++) {
            ChannelData ch = node.getChannel(i);
            boolean isSelected = (i == selectedChannel);
            boolean isEnabled = ch != null && ch.isEnabled();
            int x = startX + i * 26;
            boolean hovered = !labelPickerOpen && mx >= x && mx <= x + 24 && my >= y && my <= y + 12;
            boolean hasDot = !isSelected && isEnabled;
            ThemePaint.tab(g, font, x, y, 24, 12, String.valueOf(i), isSelected, hasDot, hovered, t);
            if (isSelected && !isEnabled) {
                ThemePaint.roundOutline(g, x, y, 24, 12, 2, cDanger(), t.sharpCorners());
            }
        }
    }

    private void drawSettingsPanel(GuiGraphics g, ChannelData ch, int x, int y, int mx, int my) {
        Theme t = theme();
        int w = 148;
        int rowH = 14;
        int h = rowH * SETTINGS_VISIBLE_ROWS + 4;

        ThemePaint.panel(g, x, y, w, h, t);

        String[] labels = {
                tr("gui.logisticsnetworks.node.setting.status"),
                tr("gui.logisticsnetworks.node.setting.mode"),
                tr("gui.logisticsnetworks.node.setting.type"),
                tr("gui.logisticsnetworks.node.setting.side"),
                tr("gui.logisticsnetworks.node.setting.redstone"),
                tr("gui.logisticsnetworks.node.setting.distribution"),
                tr("gui.logisticsnetworks.node.setting.priority"),
                tr("gui.logisticsnetworks.node.setting.batch"),
                tr("gui.logisticsnetworks.node.setting.delay")
        };
        String[] values = {
                ch.isEnabled() ? tr("gui.logisticsnetworks.node.value.enabled")
                        : tr("gui.logisticsnetworks.node.value.disabled"),
                getChannelModeLabel(ch.getMode()),
                getChannelTypeLabel(ch.getType()),
                ch.getIoDirection() != null ? getDirectionLabel(ch.getIoDirection().getName()) : getDirectionLabel("all"),
                getRedstoneModeLabel(ch.getRedstoneMode()),
                getDistributionModeLabel(ch.getDistributionMode()),
                editingRow == 6 ? "" : String.valueOf(ch.getPriority()),
                editingRow == 7 ? "" : formatBatchDisplay(ch),
                editingRow == 8 ? "" : tr("gui.logisticsnetworks.node.value.tick_delay", ch.getTickDelay())
        };
        Theme.Variant[] variants = {
                ch.isEnabled() ? Theme.Variant.ACCENT : Theme.Variant.NEUTRAL,
                getModeVariant(ch.getMode()),
                getTypeVariant(ch.getType()),
                Theme.Variant.NEUTRAL,
                getRedstoneVariant(ch.getRedstoneMode()),
                getDistributionVariant(ch.getDistributionMode()),
                Theme.Variant.NEUTRAL,
                Theme.Variant.NEUTRAL,
                Theme.Variant.NEUTRAL
        };
        boolean[] enabled = new boolean[9];
        for (int i = 0; i < 9; i++)
            enabled[i] = !isSettingDisabled(ch, i);

        int maxScroll = SETTINGS_TOTAL_ROWS - SETTINGS_VISIBLE_ROWS;
        settingsScrollOffset = Math.max(0, Math.min(settingsScrollOffset, maxScroll));

        int rowW = w - 4;
        int rx = x + 2;
        int ry = y + 2;

        int hoveredRow = -1;
        for (int vi = 0; vi < SETTINGS_VISIBLE_ROWS; vi++) {
            int row = vi + settingsScrollOffset;
            if (row >= SETTINGS_TOTAL_ROWS)
                break;
            drawSettingRow(g, rx, ry + vi * rowH, rowW, rowH, labels[row], values[row], variants[row], row, mx, my, enabled[row], vi < SETTINGS_VISIBLE_ROWS - 1);
            if (!labelPickerOpen && enabled[row] && editingRow == -1
                    && mx >= rx && mx <= rx + rowW && my >= ry + vi * rowH && my <= ry + vi * rowH + rowH) {
                hoveredRow = row;
            }
        }

        if (hoveredRow != settingsHoverRow) {
            settingsHoverRow = hoveredRow;
            settingsHoverStartTime = System.currentTimeMillis();
        }

        if (settingsScrollOffset > 0) {
            g.drawString(font, "\u25B2", x + w + 2, y + 1, cSubtle(), false);
        }
        if (settingsScrollOffset < maxScroll) {
            g.drawString(font, "\u25BC", x + w + 2, y + h - 9, cSubtle(), false);
        }
    }

    private Theme.Variant getModeVariant(ChannelMode mode) {
        return mode == ChannelMode.EXPORT ? Theme.Variant.WARN : Theme.Variant.ACCENT;
    }

    private Theme.Variant getTypeVariant(ChannelType type) {
        return switch (type) {
            case ITEM -> Theme.Variant.ACCENT;
            case FLUID -> Theme.Variant.INFO;
            case ENERGY -> Theme.Variant.WARN;
            case CHEMICAL -> Theme.Variant.NEUTRAL;
            case SOURCE -> Theme.Variant.INFO;
        };
    }

    private Theme.Variant getRedstoneVariant(RedstoneMode mode) {
        return switch (mode) {
            case ALWAYS_ON -> Theme.Variant.DANGER;
            case ALWAYS_OFF -> Theme.Variant.NEUTRAL;
            case HIGH -> Theme.Variant.ACCENT;
            case LOW -> Theme.Variant.WARN;
        };
    }

    private Theme.Variant getDistributionVariant(DistributionMode mode) {
        return switch (mode) {
            case PRIORITY -> Theme.Variant.INFO;
            case ROUND_ROBIN -> Theme.Variant.ACCENT;
            case NEAREST_FIRST -> Theme.Variant.WARN;
            case FARTHEST_FIRST -> Theme.Variant.WARN;
        };
    }

    private void drawFilterGrid(GuiGraphics g, ChannelData ch, int x, int y, int mx, int my) {
        Theme t = theme();
        String filtersLabel = tr("gui.logisticsnetworks.node.filters");
        g.drawString(font, filtersLabel, x, y, cMuted(), false);

        String modeLabel = getFilterModeLabel(ch.getFilterMode());
        int btnW = font.width(modeLabel) + 10;
        int btnX = x + font.width(filtersLabel) + 4;
        boolean modeHover = !labelPickerOpen && mx >= btnX && mx <= btnX + btnW
                && my >= y - 1 && my <= y + 9;
        ThemePaint.button(g, font, btnX, y - 1, btnW, 10, modeLabel, modeHover, t);

        int gridY = y + 12;
        int gridW = 3 * 19 - 1;
        ThemePaint.sunkPanel(g, x - 2, gridY - 2, gridW + 4, 3 * 19 + 2, t);
        drawSlotGrid(g, x, gridY, 3, 3, mx, my);

        int upgY = gridY + 3 * 19 + 2;
        String upgradesLabel = Component.translatable("gui.logisticsnetworks.node.upgrades").getString();
        g.drawString(font, upgradesLabel, x, upgY, cMuted(), false);

        int gridW2 = 2 * 19 - 1;
        ThemePaint.sunkPanel(g, x - 2, upgY + 8, gridW2 + 4, 2 * 19 + 2, t);
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

    private void drawSettingRow(GuiGraphics g, int x, int y, int w, int rowH, String label, String value,
            Theme.Variant variant, int row, int mx, int my, boolean enabled, boolean showDivider) {
        Theme t = theme();
        boolean hovered = !labelPickerOpen && mx >= x && mx <= x + w && my >= y && my <= y + rowH;
        if (enabled && hovered) {
            g.fill(x, y, x + w, y + rowH, cHover());
        }

        int valueBoxW;
        if (value.isEmpty()) {
            valueBoxW = 0;
        } else if (row >= 6 && row <= 8 || row == 3) {
            valueBoxW = font.width(value) + 8;
        } else {
            valueBoxW = ThemePaint.pillWidth(font, value);
        }

        int textY = y + (rowH - 7) / 2;
        int chipY = y + (rowH - 9) / 2;
        int pillY = y + (rowH - 10) / 2;

        int labelX = x + 4;
        int labelMaxW = w - 4 - valueBoxW - 6;
        String labelDraw = label;
        if (font.width(labelDraw) > labelMaxW && labelMaxW > 0) {
            labelDraw = font.plainSubstrByWidth(labelDraw, labelMaxW);
        }
        g.drawString(font, labelDraw, labelX, textY, enabled ? cMuted() : cSubtle(), false);

        if (showDivider) {
            g.fill(x + 2, y + rowH - 1, x + w - 2, y + rowH, t.border());
        }

        if (value.isEmpty()) return;

        if (!enabled) {
            g.drawString(font, value, x + w - font.width(value) - 4, textY, cSubtle(), false);
            return;
        }

        if (row >= 6 && row <= 8 || row == 3) {
            int vx = x + w - valueBoxW - 3;
            g.fill(vx, chipY, vx + valueBoxW, chipY + 9, t.surfaceSunken());
            g.drawString(font, value, vx + 4, chipY + 1, cText(), false);
            return;
        }

        int pillX = x + w - valueBoxW - 3;
        ThemePaint.pill(g, font, pillX, pillY, value, variant, false, t);
    }

    private String formatBatchDisplay(ChannelData ch) {
        if (ch.getType() == ChannelType.FLUID)
            return tr("gui.logisticsnetworks.node.value.batch.fluid", ch.getBatchSize());
        if (ch.getType() == ChannelType.ENERGY)
            return tr("gui.logisticsnetworks.node.value.batch.energy", ch.getBatchSize());
        if (ch.getType() == ChannelType.CHEMICAL)
            return tr("gui.logisticsnetworks.node.value.batch.chemical", ch.getBatchSize());
        if (ch.getType() == ChannelType.SOURCE)
            return tr("gui.logisticsnetworks.node.value.batch.source", ch.getBatchSize());
        return String.valueOf(ch.getBatchSize());
    }

    private boolean isSettingDisabled(ChannelData ch, int row) {
        if (ch.getMode() == ChannelMode.IMPORT) {
            return row == 4 || row == 5 || row == 7 || row == 8;
        }
        return (ch.getType() == ChannelType.ENERGY) && row == 8;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (tweaksOpen) {
            if (btn == 0) return handleTweaksClick(mx, my);
            return true;
        }
        if (currentPage == Page.CHANNEL_CONFIG && btn == 0 && isInDocsFab(mx, my)) {
            if (minecraft != null && minecraft.player != null) {
                GuideMeCompat.openGuide(minecraft.player,
                        ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "guide"));
            }
            return true;
        }
        if (currentPage == Page.CHANNEL_CONFIG && btn == 0 && isInTweaksFab(mx, my)) {
            tweaksOpen = true;
            return true;
        }
        if (editingRow != -1 && numericEditBox != null && !numericEditBox.isMouseOver(mx, my)) {
            stopNumericEdit(true);
        }
        if (channelNameEditing && channelNameEditBox != null && !channelNameEditBox.isMouseOver(mx, my)) {
            stopChannelNameEdit(true);
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
        // Cancel rename if clicking outside the rename edit box
        if (renamingNetworkId != null && renameEditBox != null && !renameEditBox.isMouseOver(mx, my)) {
            stopRenameEdit(false);
        }

        if (isHoveringAbs(leftPos + GUI_WIDTH / 2 - 45, topPos + 54, 90, 16, mx, my)) {
            String name = networkNameField.getValue().trim();
            if (name.isEmpty())
                name = tr("gui.logisticsnetworks.node.network.unnamed");
            sendNetworkAssign(Optional.empty(), name);
            return true;
        }

        List<SyncNetworkListPayload.NetworkEntry> filtered = getFilteredNetworks();
        int listY = topPos + 95;
        int entryW = GUI_WIDTH - 28;
        int endIdx = Math.min(networkScrollOffset + NETWORKS_PER_PAGE, filtered.size());
        for (int i = networkScrollOffset; i < endIdx; i++) {
            SyncNetworkListPayload.NetworkEntry entry = filtered.get(i);
            int y = listY + (i - networkScrollOffset) * 20;
            int renameBtnW = font.width(tr("gui.logisticsnetworks.rename")) + 14;
            int renameBtnX = leftPos + 14 + entryW - renameBtnW;

            // Check rename button click
            if (isHoveringAbs(renameBtnX, y, renameBtnW, 17, mx, my)) {
                startRenameEdit(entry, leftPos + 14 + 3, y + 1, entryW - 6);
                return true;
            }

            // Check row click (select network) - only if not in the rename button area
            if (isHoveringAbs(leftPos + 14, y, entryW, 17, mx, my)) {
                sendNetworkAssign(Optional.of(entry.id()), "");
                return true;
            }
        }
        return false;
    }

    private void startRenameEdit(SyncNetworkListPayload.NetworkEntry entry, int x, int y, int w) {
        stopRenameEdit(false);
        renamingNetworkId = entry.id();
        renameEditBox = new EditBox(font, x, y, w, 15, Component.empty());
        renameEditBox.setMaxLength(32);
        renameEditBox.setValue(entry.name());
        renameEditBox.setBordered(false);
        renameEditBox.setTextColor(cText());
        renameEditBox.setFocused(true);
        addRenderableWidget(renameEditBox);
        setFocused(renameEditBox);
    }

    private void stopRenameEdit(boolean commit) {
        if (renamingNetworkId == null || renameEditBox == null)
            return;

        if (commit) {
            String newName = renameEditBox.getValue().trim();
            if (!newName.isEmpty()) {
                PacketDistributor.sendToServer(new RenameNetworkPayload(renamingNetworkId, newName));
            }
        }

        removeWidget(renameEditBox);
        renameEditBox = null;
        renamingNetworkId = null;
    }

    private void openLabelPicker(LogisticsNodeEntity node) {
        closeLabelPicker();
        labelPickerOpen = true;
        labelScrollOffset = 0;

        int pickerW = getLabelPickerWidth();
        int pickerX = leftPos + 10 + (148 - pickerW) / 2;
        int pickerY = topPos + 58;

        labelEditBox = new EditBox(font, pickerX + 2, pickerY + 2, pickerW - 4, 16, Component.empty());
        labelEditBox.setMaxLength(48);
        labelEditBox.setValue(node.getNodeLabel());
        labelEditBox.setBordered(true);
        labelEditBox.setTextColor(cText());
        labelEditBox.setFocused(true);
        addRenderableWidget(labelEditBox);
        setFocused(labelEditBox);

        // Request existing labels from server
        if (node.getNetworkId() != null) {
            PacketDistributor.sendToServer(new RequestNetworkLabelsPayload(node.getNetworkId()));
        }
    }

    private void closeLabelPicker() {
        labelPickerOpen = false;
        if (labelEditBox != null) {
            removeWidget(labelEditBox);
            labelEditBox = null;
        }
        networkLabels.clear();
    }

    private void commitLabelChange(String label) {
        LogisticsNodeEntity node = getMenu().getNode();
        if (node != null) {
            node.setNodeLabel(label);
            PacketDistributor.sendToServer(new SetNodeLabelPayload(node.getId(), label));
        }
        closeLabelPicker();
    }

    private boolean handleLabelPickerClick(LogisticsNodeEntity node, double mx, double my) {
        int pickerW = getLabelPickerWidth();
        int pickerX = leftPos + 10 + (148 - pickerW) / 2;
        int pickerY = topPos + 58;
        int entryCount = Math.min(networkLabels.size(), LABEL_PICKER_MAX_VISIBLE);
        int listH = entryCount * LABEL_PICKER_ENTRY_H;
        int pickerH = 22 + listH + (networkLabels.isEmpty() ? 0 : 4) + 16;

        // Check if click is inside picker area
        if (mx < pickerX || mx > pickerX + pickerW || my < pickerY || my > pickerY + pickerH) {
            return false; // Outside picker
        }

        // Check label list entries
        int listY = pickerY + 22;
        for (int i = 0; i < LABEL_PICKER_MAX_VISIBLE && (i + labelScrollOffset) < networkLabels.size(); i++) {
            int idx = i + labelScrollOffset;
            int entryY = listY + i * LABEL_PICKER_ENTRY_H;
            if (mx >= pickerX + 2 && mx < pickerX + pickerW - 2
                    && my >= entryY && my < entryY + LABEL_PICKER_ENTRY_H) {
                commitLabelChange(networkLabels.get(idx));
                return true;
            }
        }

        // Check clear button
        int clearY = listY + listH + 2;
        String clearLabel = tr("gui.logisticsnetworks.node.label.clear");
        int clearW = font.width(clearLabel) + 8;
        int clearX = pickerX + (pickerW - clearW) / 2;
        if (mx >= clearX && mx < clearX + clearW && my >= clearY && my < clearY + 12) {
            commitLabelChange("");
            return true;
        }

        return true; // Absorb click inside picker
    }

    public void receiveNetworkLabels(List<String> labels) {
        this.networkLabels = new ArrayList<>(labels);
        this.labelScrollOffset = 0;
    }

    private boolean handleChannelPageClick(double mx, double my, int btn) {
        LogisticsNodeEntity node = getMenu().getNode();
        if (node == null)
            return false;

        // Handle label picker clicks first if open
        if (labelPickerOpen) {
            if (handleLabelPickerClick(node, mx, my))
                return true;
            // Click outside picker closes it
            closeLabelPicker();
            return true;
        }

        String visibilityLabel = getVisibilityLabel(node.isRenderVisible());
        if (isHoveringAbs(leftPos + 8, topPos + 4, font.width(visibilityLabel) + 16, 12, mx, my)) {
            node.setRenderVisible(!node.isRenderVisible());
            PacketDistributor.sendToServer(new ToggleNodeVisibilityPayload(node.getId()));
            return true;
        }

        if (isHoveringAbs(leftPos + GUI_WIDTH - 52, topPos + 4, 44, 12, mx, my)) {
            currentPage = Page.NETWORK_SELECT;
            rebuildPageLayout();
            return true;
        }

        String nodeLabel = node.getNodeLabel();
        String labelDisplay = nodeLabel.isEmpty() ? tr("gui.logisticsnetworks.node.label.set") : nodeLabel;
        int labelW = font.width(labelDisplay) + 14;
        int labelX = leftPos + 10 + (148 - labelW) / 2;
        int labelY = topPos + 40;
        if (isHoveringAbs(labelX, labelY, labelW, 12, mx, my)) {
            openLabelPicker(node);
            return true;
        }

        for (int i = 0; i < 9; i++) {
            if (isHoveringAbs(leftPos + 10 + i * 26, topPos + 22, 24, 12, mx, my)) {
                if (i == selectedChannel && checkTabDoubleClick(i)) {
                    startChannelNameEdit(node, i);
                } else {
                    selectedChannel = i;
                    settingsScrollOffset = 0;
                    getMenu().setSelectedChannel(i);
                    PacketDistributor.sendToServer(new SelectNodeChannelPayload(node.getId(), i));
                }
                return true;
            }
        }

        return handleSettingsClick(node, mx, my, btn);
    }

    private boolean handleSettingsClick(LogisticsNodeEntity node, double mx, double my, int btn) {
        ChannelData ch = node.getChannel(selectedChannel);
        if (ch == null || (btn != 0 && btn != 1))
            return false;

        int rowH = 14;
        int startY = topPos + 60;
        int startX = leftPos + 12;
        int w = 144;

        for (int vi = 0; vi < SETTINGS_VISIBLE_ROWS; vi++) {
            int row = vi + settingsScrollOffset;
            if (row >= SETTINGS_TOTAL_ROWS)
                break;
            int y = startY + vi * rowH;
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

        int modeBtnX = leftPos + 168 + font.width(tr("gui.logisticsnetworks.node.filters")) + 4;
        int modeBtnY = topPos + 56 - 1;
        String modeLabel = getFilterModeLabel(ch.getFilterMode());
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
            case 1 -> ch.setMode(cycleModeForNode(ch.getMode(), dir));
            case 2 -> {
                ChannelType oldT = ch.getType();
                ch.setType(cycleChannelType(ch.getType(), dir));
                resetDefaultsForTypeChange(ch, oldT, ch.getType());
            }
            case 3 -> ch.setIoDirection(cycleSide(ch.getIoDirection(), dir));
            case 4 -> ch.setRedstoneMode(cycleEnum(ch.getRedstoneMode(), dir));
            case 5 -> ch.setDistributionMode(cycleEnum(ch.getDistributionMode(), dir));
            case 6 -> ch.setPriority(ch.getPriority() + (hasShiftDown() ? 10 : 1) * dir);
            case 7 -> ch.setBatchSize(ch.getBatchSize() + (hasShiftDown() ? 8 : 1) * dir);
            case 8 -> ch.setTickDelay(ch.getTickDelay() + (hasShiftDown() ? 10 : 1) * dir);
        }
    }

    private ChannelType cycleChannelType(ChannelType current, int dir) {
        LogisticsNodeEntity node = getMenu().getNode();
        ChannelType[] values = ChannelType.values();
        int len = values.length;
        int index = current.ordinal();
        for (int i = 0; i < len; i++) {
            index = (index + dir + len) % len;
            ChannelType candidate = values[index];
            if (candidate == ChannelType.CHEMICAL) {
                if (!MekanismCompat.isLoaded())
                    continue;
                if (node == null || !NodeUpgradeData.hasMekanismChemicalUpgrade(node))
                    continue;
            }
            if (candidate == ChannelType.SOURCE) {
                if (!ArsCompat.isLoaded())
                    continue;
                if (node == null || !NodeUpgradeData.hasArsSourceUpgrade(node))
                    continue;
            }
            return candidate;
        }
        return current;
    }

    private ChannelMode cycleModeForNode(ChannelMode current, int dir) {
        ChannelMode[] values = ChannelMode.values();
        int len = values.length;
        int index = (current.ordinal() + dir + len) % len;
        return values[index];
    }

    private <T extends Enum<T>> T cycleEnum(T current, int dir) {
        T[] values = current.getDeclaringClass().getEnumConstants();
        int index = (current.ordinal() + dir + values.length) % values.length;
        return values[index];
    }

    private @Nullable Direction cycleSide(@Nullable Direction current, int dir) {
        int pos = current != null ? current.ordinal() : 6;
        pos = (pos + dir + 7) % 7;
        return pos < 6 ? Direction.values()[pos] : null;
    }

    private void resetDefaultsForTypeChange(ChannelData ch, ChannelType oldT, ChannelType newT) {
        if (oldT == newT)
            return;
        if (newT == ChannelType.FLUID || newT == ChannelType.CHEMICAL || newT == ChannelType.SOURCE) {
            ch.setBatchSize(100);
        } else if (newT == ChannelType.ENERGY) {
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

        numericEditBox = new EditBox(font, x, y, 70, 11, Component.empty());
        numericEditBox.setMaxLength(10);
        numericEditBox.setValue(val);
        numericEditBox.setBordered(true);
        numericEditBox.setTextColor(cText());
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
                ch.getIoDirection() != null ? ch.getIoDirection().ordinal() : 6,
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

    private boolean checkTabDoubleClick(int tabIndex) {
        long now = System.currentTimeMillis();
        boolean isDouble = (lastTabClickIndex == tabIndex && now - lastTabClickTime < 250);
        lastTabClickIndex = tabIndex;
        lastTabClickTime = now;
        return isDouble;
    }

    private void startChannelNameEdit(LogisticsNodeEntity node, int channelIndex) {
        stopChannelNameEdit(false);
        channelNameEditing = true;
        editingChannelIndex = channelIndex;

        ChannelData ch = node.getChannel(channelIndex);
        int tabX = leftPos + 10 + channelIndex * 26;
        int editX = Math.max(leftPos + 4, Math.min(tabX - 20, leftPos + GUI_WIDTH - 84));

        channelNameEditBox = new EditBox(font, editX, topPos + 40, 80, 12, Component.empty());
        channelNameEditBox.setMaxLength(24);
        channelNameEditBox.setValue(ch.getName());
        channelNameEditBox.setBordered(true);
        channelNameEditBox.setTextColor(cText());
        channelNameEditBox.setFocused(true);
        addRenderableWidget(channelNameEditBox);
        setFocused(channelNameEditBox);
    }

    private void stopChannelNameEdit(boolean commit) {
        if (!channelNameEditing || channelNameEditBox == null)
            return;

        if (commit) {
            String name = channelNameEditBox.getValue().trim();
            LogisticsNodeEntity node = getMenu().getNode();
            if (node != null) {
                ChannelData ch = node.getChannel(editingChannelIndex);
                if (ch != null) {
                    ch.setName(name);
                    PacketDistributor.sendToServer(new SetChannelNamePayload(node.getId(), editingChannelIndex, name));
                }
            }
        }

        removeWidget(channelNameEditBox);
        channelNameEditBox = null;
        channelNameEditing = false;
        editingChannelIndex = -1;
    }

    private void setNumericExtremum(ChannelData ch, int row, boolean max) {
        switch (row) {
            case 6 -> ch.setPriority(max ? PRIORITY_MAX : PRIORITY_MIN);
            case 7 -> {
                if (max) {
                    LogisticsNodeEntity node = getMenu().getNode();
                    int cap = switch (ch.getType()) {
                        case FLUID -> NodeUpgradeData.getFluidOperationCapMb(node);
                        case ENERGY -> NodeUpgradeData.getEnergyOperationCap(node);
                        case CHEMICAL -> NodeUpgradeData.getChemicalOperationCap(node);
                        case SOURCE -> NodeUpgradeData.getSourceOperationCap(node);
                        default -> NodeUpgradeData.getItemOperationCap(node);
                    };
                    ch.setBatchSize(cap);
                } else {
                    ch.setBatchSize(BATCH_MIN);
                }
            }
            case 8 -> ch.setTickDelay(max ? DELAY_MAX : DELAY_MIN);
        }
    }

    @Override
    protected boolean isHovering(int x, int y, int w, int h, double mx, double my) {
        if (currentPage == Page.NETWORK_SELECT) {
            // Only allow hover on player inventory slots (below the GUI area)
            if (y < INV_Y)
                return false;
        }
        return super.isHovering(x, y, w, h, mx, my);
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
        if (key == 256) {
            if (channelNameEditing) {
                stopChannelNameEdit(false);
                return true;
            }
            if (labelPickerOpen) {
                closeLabelPicker();
                return true;
            }
            if (renamingNetworkId != null) {
                stopRenameEdit(false);
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }

        if (channelNameEditing && channelNameEditBox != null) {
            if (key == 257 || key == 335) {
                stopChannelNameEdit(true);
            } else {
                channelNameEditBox.keyPressed(key, scan, modifiers);
            }
            return true;
        }
        if (labelPickerOpen && labelEditBox != null) {
            if (key == 257 || key == 335) {
                String val = labelEditBox.getValue().trim();
                commitLabelChange(val);
            } else {
                labelEditBox.keyPressed(key, scan, modifiers);
            }
            return true;
        }
        if (renamingNetworkId != null && renameEditBox != null) {
            if (key == 257 || key == 335) {
                stopRenameEdit(true);
            } else {
                renameEditBox.keyPressed(key, scan, modifiers);
            }
            return true;
        }
        if (editingRow != -1) {
            if (key == 257 || key == 335)
                stopNumericEdit(true);
            else
                numericEditBox.keyPressed(key, scan, modifiers);
            return true;
        }
        if (networkNameField != null && networkNameField.isFocused()) {
            if (key == 257 || key == 335)
                networkNameField.setFocused(false);
            else
                networkNameField.keyPressed(key, scan, modifiers);
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (channelNameEditing && channelNameEditBox != null) {
            return channelNameEditBox.charTyped(ch, modifiers);
        }
        if (labelPickerOpen && labelEditBox != null) {
            return labelEditBox.charTyped(ch, modifiers);
        }
        if (renamingNetworkId != null && renameEditBox != null) {
            return renameEditBox.charTyped(ch, modifiers);
        }
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
        if (labelPickerOpen && networkLabels.size() > LABEL_PICKER_MAX_VISIBLE) {
            int pickerW = getLabelPickerWidth();
            int pickerX = leftPos + 10 + (148 - pickerW) / 2;
            int pickerY = topPos + 58;
            int entryCount = Math.min(networkLabels.size(), LABEL_PICKER_MAX_VISIBLE);
            int listH = entryCount * LABEL_PICKER_ENTRY_H;
            int pickerH = 22 + listH + (networkLabels.isEmpty() ? 0 : 4) + 16;
            if (mx >= pickerX && mx <= pickerX + pickerW
                    && my >= pickerY && my <= pickerY + pickerH) {
                int maxScroll = networkLabels.size() - LABEL_PICKER_MAX_VISIBLE;
                if (sy > 0 && labelScrollOffset > 0)
                    labelScrollOffset--;
                else if (sy < 0 && labelScrollOffset < maxScroll)
                    labelScrollOffset++;
                return true;
            }
        }
        if (currentPage == Page.CHANNEL_CONFIG) {
            int panelX = leftPos + 10;
            int panelY = topPos + 58;
            int panelW = 148;
            int panelH = 14 * SETTINGS_VISIBLE_ROWS + 4;
            if (mx >= panelX && mx <= panelX + panelW && my >= panelY && my <= panelY + panelH) {
                int maxScroll = SETTINGS_TOTAL_ROWS - SETTINGS_VISIBLE_ROWS;
                if (sy > 0 && settingsScrollOffset > 0) {
                    settingsScrollOffset--;
                    return true;
                } else if (sy < 0 && settingsScrollOffset < maxScroll) {
                    settingsScrollOffset++;
                    return true;
                }
            }
        }
        if (currentPage == Page.NETWORK_SELECT) {
            List<SyncNetworkListPayload.NetworkEntry> filtered = getFilteredNetworks();
            if (sy > 0 && networkScrollOffset > 0)
                networkScrollOffset--;
            else if (sy < 0 && networkScrollOffset + NETWORKS_PER_PAGE < filtered.size())
                networkScrollOffset++;
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    public void receiveNetworkList(List<SyncNetworkListPayload.NetworkEntry> networks) {
        this.networkList = new ArrayList<>(networks);
    }

    private List<SyncNetworkListPayload.NetworkEntry> getFilteredNetworks() {
        if (networkNameField == null) return networkList;
        String filter = networkNameField.getValue().trim().toLowerCase();
        if (filter.isEmpty()) return networkList;
        List<SyncNetworkListPayload.NetworkEntry> filtered = new ArrayList<>();
        for (SyncNetworkListPayload.NetworkEntry entry : networkList) {
            if (entry.name().toLowerCase().contains(filter))
                filtered.add(entry);
        }
        return filtered;
    }

    private String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private String getVisibilityLabel(boolean visible) {
        return tr(visible
                ? "gui.logisticsnetworks.node.visibility.visible"
                : "gui.logisticsnetworks.node.visibility.hidden");
    }

    private List<Component> getSettingTooltip(ChannelData ch, int row) {
        String[] tipKeys = {
                "gui.logisticsnetworks.node.tip.status",
                "gui.logisticsnetworks.node.tip.mode",
                "gui.logisticsnetworks.node.tip.type",
                "gui.logisticsnetworks.node.tip.side",
                "gui.logisticsnetworks.node.tip.redstone",
                "gui.logisticsnetworks.node.tip.distribution",
                "gui.logisticsnetworks.node.tip.priority",
                "gui.logisticsnetworks.node.tip.batch",
                "gui.logisticsnetworks.node.tip.delay"
        };

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(tipKeys[row]));

        String valueTipKey = null;
        if (row == 1) {
            valueTipKey = "gui.logisticsnetworks.node.tip.mode." + ch.getMode().name().toLowerCase(Locale.ROOT);
        } else if (row == 4) {
            valueTipKey = "gui.logisticsnetworks.node.tip.redstone." + ch.getRedstoneMode().name().toLowerCase(Locale.ROOT);
        } else if (row == 5) {
            valueTipKey = "gui.logisticsnetworks.node.tip.distribution." + ch.getDistributionMode().name().toLowerCase(Locale.ROOT);
        }

        if (valueTipKey != null) {
            lines.add(Component.translatable(valueTipKey).withStyle(style -> style.withColor(0x999999)));
        }

        return lines;
    }

    private String getChannelModeLabel(ChannelMode mode) {
        return tr("gui.logisticsnetworks.channel_mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    private String getChannelTypeLabel(ChannelType type) {
        return tr("gui.logisticsnetworks.channel_type." + type.name().toLowerCase(Locale.ROOT));
    }

    private String getRedstoneModeLabel(RedstoneMode mode) {
        return tr("gui.logisticsnetworks.redstone_mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    private String getDistributionModeLabel(DistributionMode mode) {
        return tr("gui.logisticsnetworks.distribution_mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    private String getDirectionLabel(String directionName) {
        return tr("gui.logisticsnetworks.direction." + directionName.toLowerCase(Locale.ROOT));
    }

    private String getFilterModeLabel(FilterMode mode) {
        return tr(mode == FilterMode.MATCH_ALL
                ? "gui.logisticsnetworks.filter_mode.match_all"
                : "gui.logisticsnetworks.filter_mode.match_any");
    }
}
