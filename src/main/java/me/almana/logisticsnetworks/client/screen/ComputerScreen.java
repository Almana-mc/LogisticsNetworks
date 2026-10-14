package me.almana.logisticsnetworks.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.client.ClientControls;
import me.almana.logisticsnetworks.menu.ComputerMenu;
import me.almana.logisticsnetworks.network.DeleteNetworkPayload;
import me.almana.logisticsnetworks.network.NetworkHandler;
import me.almana.logisticsnetworks.network.RequestNetworkNodesPayload;
import me.almana.logisticsnetworks.network.RequestOpenNodeSettingsPayload;
import me.almana.logisticsnetworks.network.SetNodeLabelsPayload;
import me.almana.logisticsnetworks.network.SetNetworkNodesVisibilityPayload;
import me.almana.logisticsnetworks.network.RequestChannelListPayload;
import me.almana.logisticsnetworks.network.SubscribeTelemetryPayload;
import me.almana.logisticsnetworks.network.SyncChannelListPayload;
import me.almana.logisticsnetworks.network.SyncNetworkListPayload;
import me.almana.logisticsnetworks.network.SyncNetworkNodesPayload;
import me.almana.logisticsnetworks.network.SyncTelemetryPayload;
import me.almana.logisticsnetworks.network.ToggleComputerPinnedNetworkPayload;
import me.almana.logisticsnetworks.network.ToggleNetworkLabelHighlightPayload;
import me.almana.logisticsnetworks.network.ToggleNetworkNodeHighlightPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ComputerScreen extends AbstractContainerScreen<ComputerMenu> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private enum Page {
        NETWORK_LIST,
        IO_MONITOR,
        NODE_MAP
    }

    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 240;
    private static final int NETWORKS_PER_PAGE = 4;
    private static final int NETWORK_ENTRY_HEIGHT = 30;
    private static final int NETWORK_LIST_X = 12;
    private static final int NETWORK_LIST_Y = 68;
    private static final int NETWORK_LIST_WIDTH = 116;
    private static final int DETAIL_PANEL_X = 136;
    private static final int DETAIL_PANEL_Y = 38;
    private static final int DETAIL_PANEL_WIDTH = 172;
    private static final int DETAIL_PANEL_HEIGHT = 194;
    private static final int OPTION_BTN_HEIGHT = 24;
    private static final int OPTION_BTN_GAP = 2;
    private static final int NODE_ENTRY_HEIGHT = 24;
    private static final int VIS_BTN_W = 54;
    private static final int VIS_BTN_H = 14;
    private static final int VIS_BTN_GAP = 6;
    private static final int HIGHLIGHT_BTN_W = 16;
    private static final int HIGHLIGHT_BTN_H = 12;
    private static final int SETTINGS_BTN_W = 16;
    private static final int SETTINGS_BTN_H = 12;
    private static final int SETTINGS_BTN_GAP = 4;
    private static final int SELECT_BOX_SIZE = 10;
    private static final int NODE_ROW_SIDE_PAD = 8;
    private static final int NODE_TEXT_GAP = 8;
    private static final int VIS_BTN_Y = 38;
    private static final int BACK_BTN_X = 10;
    private static final int BACK_BTN_Y = 38;
    private static final int BACK_BTN_W = 52;
    private static final int BACK_BTN_H = 14;
    private static final int PANEL_HEADER_HEIGHT = 14;
    private static final int LNET_ENTRY_HEIGHT = 18;
    private static final int LNET_FILES_PER_PAGE = 6;
    private static final int LNET_LABELS_PER_PAGE = 6;
    private static final int LNET_TOOLBAR_Y = 24;
    private static final int LNET_LIST_Y = 54;
    private static final int LNET_FILE_W = 104;
    private static final int LNET_LABEL_W = 80;
    private static final int LNET_COLUMN_GAP = 8;
    private static final int DELETE_BUTTON_SIZE = 18;
    private static final int DELETE_MODAL_WIDTH = 276;
    private static final int DELETE_MODAL_HEIGHT = 126;
    private static final ResourceLocation DELETE_ICON = ResourceLocation.fromNamespaceAndPath(
            "logisticsnetworks", "textures/gui/trash_can.png");

    private static final int COLOR_FRAME = 0xE0181E1A;
    private static final int COLOR_FRAME_EDGE = 0xFF73806F;
    private static final int COLOR_FRAME_INNER = 0xC0080E0B;
    private static final int COLOR_SCREEN = 0xD00C130F;
    private static final int COLOR_PANEL = 0xF0101713;
    private static final int COLOR_PANEL_ALT = 0xFF152019;
    private static final int COLOR_PANEL_HEADER = 0xFF1C2B22;
    private static final int COLOR_BORDER = 0xFF4D6654;
    private static final int COLOR_BORDER_BRIGHT = 0xFF96D9A9;
    private static final int COLOR_ROW = 0xFF14201A;
    private static final int COLOR_ROW_HOVER = 0xFF192920;
    private static final int COLOR_ROW_SELECTED = 0xFF23382B;
    private static final int COLOR_TEXT = 0xFFD8F7DD;
    private static final int COLOR_TEXT_SECONDARY = 0xFF88B693;
    private static final int COLOR_TEXT_MUTED = 0xFF587263;
    private static final int COLOR_ACCENT = 0xFF80F2A3;
    private static final int COLOR_ACCENT_DARK = 0xFF315D3B;
    private static final int COLOR_WARNING = 0xFFE4CA7D;
    private static final int COLOR_SCANLINE = 0x1200FF88;
    private static final int COLOR_BADGE_BG = 0xFF17221A;
    private static final int COLOR_BADGE_TEXT = 0xFFA4FDBB;
    private static final int COLOR_HIGHLIGHT_BG = 0xFF1B2640;
    private static final int COLOR_HIGHLIGHT_HOVER = 0xFF25355B;
    private static final int COLOR_HIGHLIGHT_BORDER = 0xFF72A7FF;
    private static final int COLOR_LAMP_OFF = 0xFF5F7568;
    private static final int COLOR_LAMP_OFF_GLOW = 0xFF37443D;
    private static final int COLOR_LAMP_ON = 0xFF72A7FF;
    private static final int COLOR_LAMP_ON_GLOW = 0xFFBED6FF;
    private static final int COLOR_LAMP_BASE = 0xFF8FA39C;
    private static final int COLOR_STAR = 0xFFFFD700;
    private static final int COLOR_STAR_EMPTY = 0xFF4A5A4E;
    private static final int COLOR_DANGER = 0xFFB33A3A;
    private static final int COLOR_DANGER_HOVER = 0xFFD9534F;
    private static final int COLOR_DANGER_BORDER = 0xFFFFA3A3;
    private static final int STAR_BTN_W = 10;
    private static final int SEARCH_BOX_HEIGHT = 16;
    private static final int SEARCH_INPUT_HEIGHT = SEARCH_BOX_HEIGHT - 4;
    private static final int SEARCH_INPUT_X = 4;
    private static final int SEARCH_INPUT_WIDTH = NETWORK_LIST_WIDTH - 8;

    private Page currentPage = Page.NETWORK_LIST;
    private List<SyncNetworkListPayload.NetworkEntry> networkList = new ArrayList<>();
    private int networkScrollOffset = 0;
    private UUID selectedNetworkId = null;
    private String selectedNetworkName = "";
    private boolean deleteConfirmationOpen;
    private EditBox networkSearchBox;
    private String lastSearchFilter = "";

    private List<SyncNetworkNodesPayload.NodeInfo> nodeInfoList = new ArrayList<>();
    private int nodeMapScrollOffset = 0;
    private final Set<String> collapsedGroups = new HashSet<>();
    private final Set<UUID> selectedNodeIds = new HashSet<>();
    private UUID labelSettingsSource;
    private EditBox nodeLabelBox;

    private UUID telemetryNetworkId;
    private FlowMonitorPage flowMonitor;

    private String lnetStatus = "";
    private int lnetStatusColor = COLOR_TEXT_SECONDARY;

    public ComputerScreen(ComputerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.titleLabelY = 10000;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        String pendingLabel = nodeLabelBox == null ? "" : nodeLabelBox.getValue();
        super.init();
        if (flowMonitor == null) {
            flowMonitor = new FlowMonitorPage(font, this::closeFlowMonitor);
        }
        networkSearchBox = new EditBox(font, 0, 0, SEARCH_INPUT_WIDTH, SEARCH_INPUT_HEIGHT, Component.empty());
        networkSearchBox.setMaxLength(32);
        networkSearchBox.setBordered(false);
        networkSearchBox.setTextColor(COLOR_TEXT);
        networkSearchBox.setHint(Component.translatable("gui.logisticsnetworks.computer.search_hint"));
        layoutSearchBox();
        addRenderableWidget(networkSearchBox);
        nodeLabelBox = new EditBox(font, 0, 0, 84, 16,
                Component.translatable("gui.logisticsnetworks.computer.bulk_label"));
        nodeLabelBox.setMaxLength(48);
        nodeLabelBox.setHint(Component.translatable("gui.logisticsnetworks.computer.bulk_label"));
        nodeLabelBox.setValue(pendingLabel);
        layoutNodeLabelBox();
        addRenderableWidget(nodeLabelBox);
    }

    @Override
    public void removed() {
        unsubscribeTelemetry();
        super.removed();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        if (currentPage != Page.IO_MONITOR) {
            renderComputerShell(g);
        }

        menu.setWrenchSlotActive(currentPage == Page.NETWORK_LIST);
        layoutSearchBox();
        if (currentPage != Page.NETWORK_LIST && networkSearchBox.isFocused()) {
            networkSearchBox.setFocused(false);
        }
        if (currentPage != Page.NODE_MAP && nodeLabelBox.isFocused()) nodeLabelBox.setFocused(false);
        networkSearchBox.visible = (currentPage == Page.NETWORK_LIST);
        layoutNodeLabelBox();
        nodeLabelBox.visible = currentPage == Page.NODE_MAP && !selectedNodeIds.isEmpty();

        switch (currentPage) {
            case NETWORK_LIST -> renderNetworkListPage(g, mouseX, mouseY);
            case IO_MONITOR -> flowMonitor.render(g, leftPos, topPos, mouseX, mouseY);
            case NODE_MAP -> renderNodeMapPage(g, mouseX, mouseY);
        }
    }

    private void renderComputerShell(GuiGraphics g) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, COLOR_FRAME);
        g.renderOutline(leftPos, topPos, imageWidth, imageHeight, COLOR_FRAME_EDGE);

        g.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, COLOR_FRAME_INNER);
        g.renderOutline(leftPos + 3, topPos + 3, imageWidth - 6, imageHeight - 6, COLOR_BORDER);

        g.fill(leftPos + 6, topPos + 6, leftPos + imageWidth - 6, topPos + imageHeight - 6, COLOR_SCREEN);
        g.fill(leftPos + 6, topPos + 6, leftPos + imageWidth - 6, topPos + 24, COLOR_PANEL_HEADER);
        g.fill(leftPos + 6, topPos + 24, leftPos + imageWidth - 6, topPos + 25, COLOR_BORDER);

        String shellTitle = trimText(title.getString().toUpperCase(Locale.ROOT), imageWidth - 96);
        g.drawString(font, shellTitle, leftPos + 12, topPos + 11, COLOR_ACCENT);
        renderStatusBadge(g, leftPos + imageWidth - 110, topPos + 10, 62, 10,
                line("gui.logisticsnetworks.computer.status.online"));
        renderScanlines(g, leftPos + 7, topPos + 25, imageWidth - 14, imageHeight - 32);
    }

    private void renderNetworkListPage(GuiGraphics g, int mouseX, int mouseY) {
        int listPanelX = leftPos + NETWORK_LIST_X - 4;
        int listPanelY = topPos + DETAIL_PANEL_Y;
        int listPanelW = NETWORK_LIST_WIDTH + 8;
        int listPanelH = DETAIL_PANEL_HEIGHT;

        renderTerminalPanel(g, listPanelX, listPanelY, listPanelW, listPanelH,
                line("gui.logisticsnetworks.computer.network_directory"));
        renderTerminalPanel(g, leftPos + DETAIL_PANEL_X, topPos + DETAIL_PANEL_Y,
                DETAIL_PANEL_WIDTH, DETAIL_PANEL_HEIGHT,
                line("gui.logisticsnetworks.computer.active_session"));
        renderDriveBay(g);
        renderNetworkList(g, mouseX, mouseY);

        if (selectedNetworkId == null) {
            renderIdleSession(g, leftPos + DETAIL_PANEL_X, topPos + DETAIL_PANEL_Y, mouseX, mouseY);
        } else {
            renderSelectedSession(g, mouseX, mouseY);
        }
    }

    private void renderDriveBay(GuiGraphics g) {
        Slot slot = menu.slots.get(0);
        int slotX = leftPos + slot.x;
        int slotY = topPos + slot.y;
        int housingX = slotX - 3;
        int housingY = slotY - 3;
        g.fill(housingX, housingY, housingX + 22, housingY + 22, COLOR_PANEL_ALT);
        g.renderOutline(housingX, housingY, 22, 22, COLOR_BORDER);
        g.fill(slotX - 2, slotY - 2, slotX + 18, slotY + 18, COLOR_FRAME_INNER);
        g.renderOutline(slotX - 2, slotY - 2, 20, 20, COLOR_ACCENT_DARK);
        g.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, COLOR_PANEL);
        g.renderOutline(slotX - 1, slotY - 1, 18, 18, COLOR_BORDER);
    }

    private void renderNetworkList(GuiGraphics g, int mouseX, int mouseY) {
        int startX = leftPos + NETWORK_LIST_X;
        int searchY = getSearchBoxTop();
        g.fill(startX, searchY, startX + NETWORK_LIST_WIDTH, searchY + SEARCH_BOX_HEIGHT, COLOR_PANEL_ALT);
        g.renderOutline(startX, searchY, NETWORK_LIST_WIDTH, SEARCH_BOX_HEIGHT, COLOR_BORDER);

        int startY = searchY + SEARCH_BOX_HEIGHT + 2;

        String currentFilter = networkSearchBox != null ? networkSearchBox.getValue().trim() : "";
        if (!currentFilter.equals(lastSearchFilter)) {
            lastSearchFilter = currentFilter;
            networkScrollOffset = 0;
        }

        if (networkList.isEmpty()) {
            g.drawString(font, label("gui.logisticsnetworks.computer.no_networks_online"), startX + 4, startY + 10,
                    COLOR_TEXT_MUTED);
            g.drawString(font, label("gui.logisticsnetworks.computer.insert_wrench"), startX + 4, startY + 22,
                    COLOR_TEXT_MUTED);
            return;
        }

        List<SyncNetworkListPayload.NetworkEntry> filtered = getFilteredNetworks();
        int maxScroll = Math.max(0, filtered.size() - NETWORKS_PER_PAGE);
        networkScrollOffset = Math.max(0, Math.min(networkScrollOffset, maxScroll));

        int listYOffset = startY - topPos;
        for (int i = 0; i < NETWORKS_PER_PAGE && (i + networkScrollOffset) < filtered.size(); i++) {
            int index = i + networkScrollOffset;
            SyncNetworkListPayload.NetworkEntry entry = filtered.get(index);
            int entryX = startX;
            int entryY = startY + (i * NETWORK_ENTRY_HEIGHT);
            boolean hovered = isHovering(NETWORK_LIST_X, listYOffset + (i * NETWORK_ENTRY_HEIGHT),
                    NETWORK_LIST_WIDTH, NETWORK_ENTRY_HEIGHT - 2, mouseX, mouseY);
            renderNetworkEntry(g, entry, entryX, entryY, NETWORK_LIST_WIDTH, hovered, mouseX, mouseY);
        }

        if (filtered.size() > NETWORKS_PER_PAGE) {
            int first = networkScrollOffset + 1;
            int last = Math.min(networkScrollOffset + NETWORKS_PER_PAGE, filtered.size());
            String pageInfo = line("gui.logisticsnetworks.node.page_info", first, last, filtered.size());
            int pageInfoX = leftPos + NETWORK_LIST_X + NETWORK_LIST_WIDTH - font.width(pageInfo);
            g.drawString(font, pageInfo, pageInfoX,
                    topPos + DETAIL_PANEL_Y + DETAIL_PANEL_HEIGHT - 11, COLOR_TEXT_MUTED);
        }
    }

    private void renderNetworkEntry(GuiGraphics g, SyncNetworkListPayload.NetworkEntry entry,
            int x, int y, int width, boolean hovered, int mouseX, int mouseY) {
        boolean selected = entry.id().equals(selectedNetworkId);
        boolean isPinned = entry.pinned();
        int entryHeight = NETWORK_ENTRY_HEIGHT - 2;
        int bgColor = selected ? COLOR_ROW_SELECTED : (hovered ? COLOR_ROW_HOVER : COLOR_ROW);
        int borderColor = selected ? COLOR_BORDER_BRIGHT : (hovered ? COLOR_ACCENT_DARK : COLOR_BORDER);

        g.fill(x, y, x + width, y + entryHeight, bgColor);
        g.renderOutline(x, y, width, entryHeight, borderColor);
        g.fill(x + 1, y + 1, x + 3, y + entryHeight - 1, borderColor);

        int starX = x + width - STAR_BTN_W - 3;
        int starY = y + 4;
        boolean starHovered = mouseX >= starX && mouseX < starX + STAR_BTN_W
                && mouseY >= starY && mouseY < starY + STAR_BTN_W;
        g.drawString(font, "\u2605", starX, starY,
                isPinned ? COLOR_STAR : (starHovered ? COLOR_TEXT_SECONDARY : COLOR_STAR_EMPTY), false);

        int textW = width - STAR_BTN_W - 14;
        String prefix = selected ? "> " : (hovered ? "+ " : "- ");
        String name = trimText(prefix + entry.name(), textW);
        String nodeCount = line("gui.logisticsnetworks.node.network_nodes", entry.nodeCount());

        g.drawString(font, name, x + 7, y + 6, selected ? COLOR_ACCENT : COLOR_TEXT);
        g.drawString(font, trimText(nodeCount, textW), x + 7, y + 18, COLOR_TEXT_SECONDARY);
    }

    private void renderIdleSession(GuiGraphics g, int panelX, int panelY, int mouseX, int mouseY) {
        int textX = panelX + 12;
        int lineY = panelY + 24;
        String totalNetworks = line("gui.logisticsnetworks.computer.total_networks_badge", networkList.size());
        int totalWidth = Math.max(58, font.width(totalNetworks) + 10);
        int badgeGroupX = textX;
        int badgeGroupW = 114;
        int totalX = badgeGroupX + ((badgeGroupW - totalWidth) / 2);

        g.drawString(font, label("gui.logisticsnetworks.computer.no_network_mounted"), textX, lineY, COLOR_WARNING);
        g.drawString(font, label("gui.logisticsnetworks.computer.select_directory"), textX, lineY + 16,
                COLOR_TEXT_SECONDARY);
        g.drawString(font, label("gui.logisticsnetworks.computer.open_workstation"), textX, lineY + 28,
                COLOR_TEXT_SECONDARY);
        renderStatusBadge(g, textX, lineY + 50, 48, 10, line("gui.logisticsnetworks.computer.status.idle"));
        renderStatusBadge(g, textX + 56, lineY + 50, 58, 10, line("gui.logisticsnetworks.computer.status.ready"));
        renderStatusBadge(g, totalX, lineY + 64, totalWidth, 10, totalNetworks);
        g.drawString(font, label("gui.logisticsnetworks.computer.hint.dir"), textX, lineY + 76, COLOR_TEXT_MUTED);
        g.drawString(font, label("gui.logisticsnetworks.computer.hint.tab"), textX, lineY + 88, COLOR_TEXT_MUTED);
        g.drawString(font, label("gui.logisticsnetworks.computer.hint.run"), textX, lineY + 100, COLOR_TEXT_MUTED);

        int loadY = panelY + 152;
        int buttonWidth = DETAIL_PANEL_WIDTH - 24;
        boolean loadHovered = mouseX >= textX && mouseX < textX + buttonWidth
                && mouseY >= loadY && mouseY < loadY + OPTION_BTN_HEIGHT;
        renderCommandCard(g, textX, loadY, buttonWidth, OPTION_BTN_HEIGHT,
                line("gui.logisticsnetworks.computer.load_network"),
                line("gui.logisticsnetworks.computer.load_network_detail"), loadHovered);
    }

    private void renderSelectedSession(GuiGraphics g, int mouseX, int mouseY) {
        int panelX = leftPos + DETAIL_PANEL_X;
        int panelY = topPos + DETAIL_PANEL_Y;
        int textX = panelX + 12;
        SyncNetworkListPayload.NetworkEntry selectedEntry = getSelectedNetworkEntry();

        g.drawString(font, label("gui.logisticsnetworks.computer.network"), textX, panelY + 24, COLOR_TEXT_SECONDARY);
        g.drawString(font, trimText(selectedNetworkName, DETAIL_PANEL_WIDTH - 24), textX, panelY + 38, COLOR_TEXT);
        String nodeCount = selectedEntry == null
                ? line("gui.logisticsnetworks.computer.nodes_unknown")
                : line("gui.logisticsnetworks.computer.nodes_badge", selectedEntry.nodeCount());
        renderStatusBadge(g, textX, panelY + 56, 58, 10, nodeCount);
        renderStatusBadge(g, textX + 66, panelY + 56, 54, 10, line("gui.logisticsnetworks.computer.status.synced"));
        g.drawString(font, label("gui.logisticsnetworks.computer.choose_subsystem"), textX, panelY + 72,
                COLOR_TEXT_SECONDARY);
        renderOptionButtons(g, mouseX, mouseY);
        renderDeleteButton(g, selectedEntry, mouseX, mouseY);

        if (!lnetStatus.isEmpty()) {
            g.drawString(font, trimText(lnetStatus, DETAIL_PANEL_WIDTH - 24), textX,
                    panelY + DETAIL_PANEL_HEIGHT - 12, lnetStatusColor);
        }
    }

    private void renderDeleteButton(GuiGraphics g, SyncNetworkListPayload.NetworkEntry entry,
            int mouseX, int mouseY) {
        if (entry == null || !entry.canDelete()) {
            return;
        }
        int x = leftPos + DETAIL_PANEL_X + DETAIL_PANEL_WIDTH - DELETE_BUTTON_SIZE - 8;
        int y = topPos + DETAIL_PANEL_Y + 20;
        boolean hovered = isHoveringAbs(x, y, DELETE_BUTTON_SIZE, DELETE_BUTTON_SIZE, mouseX, mouseY);
        if (hovered) {
            g.fill(x, y, x + DELETE_BUTTON_SIZE, y + DELETE_BUTTON_SIZE, COLOR_ROW_HOVER);
        }
        g.renderOutline(x, y, DELETE_BUTTON_SIZE, DELETE_BUTTON_SIZE,
                hovered ? COLOR_BORDER_BRIGHT : COLOR_BORDER);
        RenderSystem.enableBlend();
        g.blit(DELETE_ICON, x + 1, y + 1, 16, 16, 0, 0, 512, 512, 512, 512);
        RenderSystem.disableBlend();
    }

    private void renderOptionButtons(GuiGraphics g, int mouseX, int mouseY) {
        int panelX = leftPos + DETAIL_PANEL_X;
        int panelY = topPos + DETAIL_PANEL_Y;
        int buttonX = panelX + 12;
        int buttonWidth = DETAIL_PANEL_WIDTH - 24;
        int button1Y = panelY + 84;
        int button2Y = button1Y + OPTION_BTN_HEIGHT + OPTION_BTN_GAP;
        int button3Y = button2Y + OPTION_BTN_HEIGHT + OPTION_BTN_GAP;
        int button4Y = button3Y + OPTION_BTN_HEIGHT + OPTION_BTN_GAP;

        boolean button1Hovered = mouseX >= buttonX && mouseX < buttonX + buttonWidth
                && mouseY >= button1Y && mouseY < button1Y + OPTION_BTN_HEIGHT;
        boolean button2Hovered = mouseX >= buttonX && mouseX < buttonX + buttonWidth
                && mouseY >= button2Y && mouseY < button2Y + OPTION_BTN_HEIGHT;
        boolean button3Hovered = mouseX >= buttonX && mouseX < buttonX + buttonWidth
                && mouseY >= button3Y && mouseY < button3Y + OPTION_BTN_HEIGHT;

        renderCommandCard(g, buttonX, button1Y, buttonWidth, OPTION_BTN_HEIGHT,
                line("gui.logisticsnetworks.computer.open_io_monitor"),
                line("gui.logisticsnetworks.computer.throughput_timeline"), button1Hovered);
        renderCommandCard(g, buttonX, button2Y, buttonWidth, OPTION_BTN_HEIGHT,
                line("gui.logisticsnetworks.computer.open_node_table"),
                line("gui.logisticsnetworks.computer.device_topology"), button2Hovered);
        boolean button4Hovered = mouseX >= buttonX && mouseX < buttonX + buttonWidth
                && mouseY >= button4Y && mouseY < button4Y + OPTION_BTN_HEIGHT;
        renderCommandCard(g, buttonX, button4Y, buttonWidth, OPTION_BTN_HEIGHT,
                line("gui.logisticsnetworks.graph.open"),
                line("gui.logisticsnetworks.graph.description"), button4Hovered);
        renderCommandCard(g, buttonX, button3Y, buttonWidth, OPTION_BTN_HEIGHT,
                line("gui.logisticsnetworks.computer.open_lnet_files"),
                line("gui.logisticsnetworks.computer.lnet_file_ops"), button3Hovered);
    }

    private void renderCommandCard(GuiGraphics g, int x, int y, int w, int h,
            String label, String detail, boolean hovered) {
        int bgColor = hovered ? COLOR_ROW_SELECTED : COLOR_PANEL_ALT;
        int borderColor = hovered ? COLOR_BORDER_BRIGHT : COLOR_BORDER;

        g.fill(x, y, x + w, y + h, bgColor);
        g.renderOutline(x, y, w, h, borderColor);
        g.fill(x + 1, y + 1, x + w - 1, y + 8, hovered ? COLOR_ACCENT_DARK : COLOR_PANEL_HEADER);
        g.drawString(font, trimText(label, w - 16), x + 8, y + 3, hovered ? COLOR_ACCENT : COLOR_TEXT);
        g.drawString(font, trimText(detail, w - 16), x + 8, y + 13, COLOR_TEXT_SECONDARY);
    }

    private void renderNodeMapPage(GuiGraphics g, int mouseX, int mouseY) {
        int contentX = leftPos + 10;
        int contentY = topPos + 34;
        int contentW = imageWidth - 20;
        int contentH = imageHeight - 44;

        renderTerminalPanel(g, contentX, contentY, contentW, contentH, "");

        if (nodeInfoList.isEmpty()) {
            g.drawString(font, label("gui.logisticsnetworks.computer.no_nodes"), contentX + 12, contentY + 28,
                    COLOR_TEXT_MUTED);
            g.drawString(font, label("gui.logisticsnetworks.computer.attach_device"), contentX + 12, contentY + 40,
                    COLOR_TEXT_MUTED);
            renderBackButton(g, mouseX, mouseY);
            renderVisibilityButtons(g, mouseX, mouseY);
            g.drawString(font, trimText(line("gui.logisticsnetworks.computer.node_table_title", selectedNetworkName),
                            contentW - 156),
                    contentX + 64, contentY + 4, COLOR_ACCENT);
            return;
        }

        renderGroupedNodeMap(g, contentX, contentY, contentW, contentH, mouseX, mouseY);
        renderBackButton(g, mouseX, mouseY);
        renderVisibilityButtons(g, mouseX, mouseY);
        g.drawString(font, trimText(line("gui.logisticsnetworks.computer.node_table_title", selectedNetworkName),
                        contentW - 156),
                contentX + 64, contentY + 4, COLOR_ACCENT);
    }

    private void renderVisibilityButtons(GuiGraphics g, int mouseX, int mouseY) {
        int hideX = leftPos + imageWidth - 12 - VIS_BTN_W;
        int showX = hideX - VIS_BTN_W - VIS_BTN_GAP;
        int buttonY = topPos + VIS_BTN_Y;

        boolean showHovered = mouseX >= showX && mouseX < showX + VIS_BTN_W
                && mouseY >= buttonY && mouseY < buttonY + VIS_BTN_H;
        boolean hideHovered = mouseX >= hideX && mouseX < hideX + VIS_BTN_W
                && mouseY >= buttonY && mouseY < buttonY + VIS_BTN_H;

        renderHeaderButton(g, showX, buttonY, VIS_BTN_W, VIS_BTN_H,
                line("gui.logisticsnetworks.computer.show"), showHovered);
        renderHeaderButton(g, hideX, buttonY, VIS_BTN_W, VIS_BTN_H,
                line("gui.logisticsnetworks.computer.hide"), hideHovered);
    }

    private void renderHeaderButton(GuiGraphics g, int x, int y, int w, int h, String label, boolean hovered) {
        int bgColor = hovered ? COLOR_ROW_SELECTED : COLOR_BADGE_BG;
        int borderColor = hovered ? COLOR_BORDER_BRIGHT : COLOR_BORDER;

        g.fill(x, y, x + w, y + h, bgColor);
        g.renderOutline(x, y, w, h, borderColor);

        int textX = x + (w / 2) - (font.width(label) / 2);
        int textY = y + (h / 2) - (font.lineHeight / 2);
        g.drawString(font, label, textX, textY, hovered ? COLOR_ACCENT : COLOR_TEXT_SECONDARY);
    }

    private void renderToggleButton(GuiGraphics g, int x, int y, int w, int h, boolean hovered, boolean active) {
        int bgColor = active ? (hovered ? COLOR_HIGHLIGHT_HOVER : COLOR_HIGHLIGHT_BG)
                : (hovered ? COLOR_ROW_SELECTED : COLOR_BADGE_BG);
        int borderColor = active ? COLOR_HIGHLIGHT_BORDER : (hovered ? COLOR_BORDER_BRIGHT : COLOR_BORDER);

        g.fill(x, y, x + w, y + h, bgColor);
        g.renderOutline(x, y, w, h, borderColor);
        renderLampIcon(g, x, y, w, h, active);
    }

    private void renderLampIcon(GuiGraphics g, int x, int y, int w, int h, boolean active) {
        int centerX = x + (w / 2);
        int bulbLeft = centerX - 3;
        int bulbTop = y + 2;
        int bulbRight = bulbLeft + 6;
        int bulbBottom = bulbTop + 5;
        int baseLeft = centerX - 2;
        int baseRight = baseLeft + 4;
        int baseTop = bulbBottom;
        int baseBottom = baseTop + 3;
        int glowColor = active ? COLOR_LAMP_ON_GLOW : COLOR_LAMP_OFF_GLOW;
        int bulbColor = active ? COLOR_LAMP_ON : COLOR_LAMP_OFF;

        g.fill(bulbLeft - 1, bulbTop - 1, bulbRight + 1, bulbBottom + 1, glowColor);
        g.fill(bulbLeft, bulbTop, bulbRight, bulbBottom, bulbColor);
        g.fill(baseLeft, baseTop, baseRight, baseBottom, COLOR_LAMP_BASE);
        g.fill(centerX - 1, baseBottom, centerX + 1, baseBottom + 1, COLOR_LAMP_BASE);
    }

    private void renderSettingsButton(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        int bgColor = hovered ? COLOR_ROW_SELECTED : COLOR_BADGE_BG;
        int borderColor = hovered ? COLOR_BORDER_BRIGHT : COLOR_BORDER;
        g.fill(x, y, x + w, y + h, bgColor);
        g.renderOutline(x, y, w, h, borderColor);
        renderGearIcon(g, x, y, w, h, hovered);
    }

    private void renderGearIcon(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        int cx = x + (w / 2);
        int cy = y + (h / 2);
        int color = hovered ? COLOR_ACCENT : COLOR_TEXT_SECONDARY;
        int hole = hovered ? COLOR_ROW_SELECTED : COLOR_BADGE_BG;

        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, color);
        g.fill(cx - 1, cy - 4, cx + 1, cy - 2, color);
        g.fill(cx - 1, cy + 2, cx + 1, cy + 4, color);
        g.fill(cx - 4, cy - 1, cx - 2, cy + 1, color);
        g.fill(cx + 2, cy - 1, cx + 4, cy + 1, color);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, hole);
    }

    private boolean handleVisibilityButtonClick(double mouseX, double mouseY) {
        int hideX = leftPos + imageWidth - 12 - VIS_BTN_W;
        int showX = hideX - VIS_BTN_W - VIS_BTN_GAP;
        int buttonY = topPos + VIS_BTN_Y;

        if (selectedNetworkId == null) {
            return false;
        }

        if (mouseX >= showX && mouseX < showX + VIS_BTN_W
                && mouseY >= buttonY && mouseY < buttonY + VIS_BTN_H) {
            NetworkHandler.sendToServer(new SetNetworkNodesVisibilityPayload(selectedNetworkId, true));
            setAllNodeVisibility(true);
            return true;
        }
        if (mouseX >= hideX && mouseX < hideX + VIS_BTN_W
                && mouseY >= buttonY && mouseY < buttonY + VIS_BTN_H) {
            NetworkHandler.sendToServer(new SetNetworkNodesVisibilityPayload(selectedNetworkId, false));
            setAllNodeVisibility(false);
            return true;
        }
        return false;
    }

    private void renderGroupedNodeMap(GuiGraphics g, int contentX, int contentY,
            int contentW, int contentH, int mouseX, int mouseY) {
        List<RenderEntry> renderEntries = buildNodeRenderEntries();

        int rowsPerPage = nodeRowsPerPage();
        int maxScroll = Math.max(0, renderEntries.size() - rowsPerPage);
        nodeMapScrollOffset = Math.max(0, Math.min(nodeMapScrollOffset, maxScroll));

        int headerX = contentX + 8;
        int headerY = contentY + 22;
        int headerW = contentW - 16;
        g.fill(headerX, headerY, headerX + headerW, headerY + 14, COLOR_PANEL_ALT);
        g.renderOutline(headerX, headerY, headerW, 14, COLOR_BORDER);
        g.drawString(font, label("gui.logisticsnetworks.computer.device"), headerX + 22, headerY + 3,
                COLOR_TEXT_SECONDARY);
        g.drawString(font, label("gui.logisticsnetworks.computer.location"), headerX + headerW - 72, headerY + 3,
                COLOR_TEXT_SECONDARY);

        int listY = contentY + 40;
        for (int i = 0; i < rowsPerPage && (i + nodeMapScrollOffset) < renderEntries.size(); i++) {
            int index = i + nodeMapScrollOffset;
            RenderEntry entry = renderEntries.get(index);
            int entryY = listY + (i * NODE_ENTRY_HEIGHT);
            int buttonX = headerX + headerW - NODE_ROW_SIDE_PAD - HIGHLIGHT_BTN_W;
            int buttonY = entryY + ((NODE_ENTRY_HEIGHT - HIGHLIGHT_BTN_H - 2) / 2);
            boolean buttonHovered = mouseX >= buttonX && mouseX < buttonX + HIGHLIGHT_BTN_W
                    && mouseY >= buttonY && mouseY < buttonY + HIGHLIGHT_BTN_H;

            if (entry.isHeader) {
                int settingsX = buttonX - SETTINGS_BTN_GAP - SETTINGS_BTN_W;
                boolean settingsHovered = mouseX >= settingsX && mouseX < settingsX + SETTINGS_BTN_W
                        && mouseY >= buttonY && mouseY < buttonY + SETTINGS_BTN_H;

                String marker = collapsedGroups.contains(entry.headerName) ? "[+]" : "[-]";
                String countText = line("gui.logisticsnetworks.computer.devices_badge", entry.headerCount);
                boolean active = isGroupHighlighted(entry.headerName);
                int countX = settingsX - NODE_TEXT_GAP - font.width(countText);
                String headerText = marker + " "
                        + trimText(entry.headerName, Math.max(0, countX - NODE_TEXT_GAP - (headerX + 22)));

                g.fill(headerX, entryY, headerX + headerW, entryY + NODE_ENTRY_HEIGHT - 2, COLOR_PANEL_ALT);
                g.renderOutline(headerX, entryY, headerW, NODE_ENTRY_HEIGHT - 2, COLOR_ACCENT_DARK);
                renderCheckbox(g, headerX + 6, entryY + 6, groupSelectionState(entry.headerName));
                g.drawString(font, headerText, headerX + 22, entryY + 7, COLOR_ACCENT);
                g.drawString(font, countText, countX, entryY + 7, COLOR_TEXT_SECONDARY);
                renderSettingsButton(g, settingsX, buttonY, SETTINGS_BTN_W, SETTINGS_BTN_H, settingsHovered);
                renderToggleButton(g, buttonX, buttonY, HIGHLIGHT_BTN_W, HIGHLIGHT_BTN_H, buttonHovered, active);
                continue;
            }

            boolean hovered = mouseX >= headerX && mouseX < headerX + headerW
                    && mouseY >= entryY && mouseY < entryY + NODE_ENTRY_HEIGHT - 2;
            int bgColor = hovered ? COLOR_ROW_HOVER : COLOR_ROW;
            int borderColor = selectedNodeIds.contains(entry.nodeInfo.nodeId())
                    ? COLOR_BORDER_BRIGHT : hovered ? COLOR_ACCENT_DARK : COLOR_BORDER;
            g.fill(headerX, entryY, headerX + headerW, entryY + NODE_ENTRY_HEIGHT - 2, bgColor);
            g.renderOutline(headerX, entryY, headerW, NODE_ENTRY_HEIGHT - 2, borderColor);

            if (entry.isGrouped) {
                g.fill(headerX + 1, entryY + 1, headerX + 3, entryY + NODE_ENTRY_HEIGHT - 3, COLOR_ACCENT_DARK);
            }

            boolean active = entry.nodeInfo.highlighted();
            renderCheckbox(g, headerX + 6, entryY + 6,
                    selectedNodeIds.contains(entry.nodeInfo.nodeId()) ? 2 : 0);
            int textX = headerX + 22;

            int rightAnchor = buttonX;
            boolean settingsHovered = false;
            if (!entry.isGrouped) {
                int settingsX = buttonX - SETTINGS_BTN_GAP - SETTINGS_BTN_W;
                settingsHovered = mouseX >= settingsX && mouseX < settingsX + SETTINGS_BTN_W
                        && mouseY >= buttonY && mouseY < buttonY + SETTINGS_BTN_H;
                renderSettingsButton(g, settingsX, buttonY, SETTINGS_BTN_W, SETTINGS_BTN_H, settingsHovered);
                rightAnchor = settingsX;
            }

            ItemStack renderStack = ItemStack.EMPTY;
            ResourceLocation blockId = ResourceLocation.tryParse(entry.nodeInfo.blockName());
            if (blockId != null) {
                Item item = BuiltInRegistries.ITEM.get(blockId);
                if (item != Items.AIR) {
                    renderStack = new ItemStack(item);
                }
            }

            if (!renderStack.isEmpty()) {
                g.renderItem(renderStack, textX, entryY + 2);
                textX += 20;
            }

            String positionText = formatPosition(entry.nodeInfo);
            String blockLabel = trimText(resolveBlockLabel(entry.nodeInfo.blockName()),
                    Math.max(0, rightAnchor - NODE_TEXT_GAP - textX));

            g.drawString(font, blockLabel, textX, entryY + 3, COLOR_TEXT);
            g.drawString(font, positionText, textX, entryY + 13, COLOR_TEXT_SECONDARY);
            renderToggleButton(g, buttonX, buttonY, HIGHLIGHT_BTN_W, HIGHLIGHT_BTN_H, buttonHovered, active);
        }

        if (renderEntries.size() > rowsPerPage) {
            String scrollInfo = (nodeMapScrollOffset + 1) + "-"
                    + Math.min(nodeMapScrollOffset + rowsPerPage, renderEntries.size())
                    + " / " + renderEntries.size();
            g.drawString(font, scrollInfo, contentX + contentW - 12 - font.width(scrollInfo),
                    contentY + contentH - 31, COLOR_TEXT_MUTED);
        }
        renderLabelToolbar(g, contentX, contentY, contentW, contentH, mouseX, mouseY);
    }

    private void renderCheckbox(GuiGraphics g, int x, int y, int state) {
        g.fill(x, y, x + SELECT_BOX_SIZE, y + SELECT_BOX_SIZE, COLOR_BADGE_BG);
        g.renderOutline(x, y, SELECT_BOX_SIZE, SELECT_BOX_SIZE,
                state == 0 ? COLOR_BORDER : COLOR_BORDER_BRIGHT);
        if (state == 2) g.fill(x + 3, y + 3, x + 7, y + 7, COLOR_ACCENT);
        else if (state == 1) g.fill(x + 2, y + 4, x + 8, y + 6, COLOR_WARNING);
    }

    private void renderLabelToolbar(GuiGraphics g, int contentX, int contentY, int contentW, int contentH,
                                    int mouseX, int mouseY) {
        if (selectedNodeIds.isEmpty()) return;
        int y = contentY + contentH - 20;
        int applyX = contentX + 96;
        int sourceX = applyX + 44;
        int sameX = sourceX + 82;
        renderSmallButton(g, applyX, y, 40, 16,
                line("gui.logisticsnetworks.computer.apply_label"),
                isHoveringAbs(applyX, y, 40, 16, mouseX, mouseY));
        renderSmallButton(g, sourceX, y, 78, 16, labelSourceText(),
                isHoveringAbs(sourceX, y, 78, 16, mouseX, mouseY));
        renderSmallButton(g, sameX, y, Math.max(40, contentX + contentW - 8 - sameX), 16,
                line("gui.logisticsnetworks.computer.same_block"),
                isHoveringAbs(sameX, y, contentX + contentW - 8 - sameX, 16, mouseX, mouseY));
    }

    private void renderTerminalPanel(GuiGraphics g, int x, int y, int w, int h, String label) {
        g.fill(x, y, x + w, y + h, COLOR_PANEL);
        g.renderOutline(x, y, w, h, COLOR_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + PANEL_HEADER_HEIGHT, COLOR_PANEL_HEADER);
        g.fill(x + 1, y + PANEL_HEADER_HEIGHT, x + w - 1, y + PANEL_HEADER_HEIGHT + 1, COLOR_BORDER);
        g.drawString(font, trimText(label.toUpperCase(Locale.ROOT), w - 12), x + 6, y + 4, COLOR_ACCENT);
        renderScanlines(g, x + 1, y + PANEL_HEADER_HEIGHT + 1, w - 2, h - PANEL_HEADER_HEIGHT - 2);
    }

    private void renderStatusBadge(GuiGraphics g, int x, int y, int w, int h, String label) {
        g.fill(x, y, x + w, y + h, COLOR_BADGE_BG);
        g.renderOutline(x, y, w, h, COLOR_ACCENT_DARK);
        int textX = x + (w / 2) - (font.width(label) / 2);
        int textY = y + (h / 2) - (font.lineHeight / 2);
        g.drawString(font, label, textX, textY, COLOR_BADGE_TEXT);
    }

    private void renderScanlines(GuiGraphics g, int x, int y, int w, int h) {
        for (int row = y; row < y + h; row += 4) {
            g.fill(x, row, x + w, row + 1, COLOR_SCANLINE);
        }
    }

    private void renderSmallButton(GuiGraphics g, int x, int y, int width, int height, String label, boolean hovered) {
        int bgColor = hovered ? COLOR_ROW_SELECTED : COLOR_BADGE_BG;
        int borderColor = hovered ? COLOR_BORDER_BRIGHT : COLOR_BORDER;
        g.fill(x, y, x + width, y + height, bgColor);
        g.renderOutline(x, y, width, height, borderColor);
        String display = trimText(label, width - 8);
        int textX = x + Math.max(4, (width - font.width(display)) / 2);
        g.drawString(font, display, textX, y + 4, hovered ? COLOR_ACCENT : COLOR_TEXT_SECONDARY);
    }

    private void renderBackButton(GuiGraphics g, int mouseX, int mouseY) {
        int buttonX = leftPos + BACK_BTN_X;
        int buttonY = topPos + BACK_BTN_Y;
        boolean hovered = mouseX >= buttonX && mouseX < buttonX + BACK_BTN_W
                && mouseY >= buttonY && mouseY < buttonY + BACK_BTN_H;

        int bgColor = hovered ? COLOR_ROW_SELECTED : COLOR_BADGE_BG;
        int borderColor = hovered ? COLOR_BORDER_BRIGHT : COLOR_BORDER;
        g.fill(buttonX, buttonY, buttonX + BACK_BTN_W, buttonY + BACK_BTN_H, bgColor);
        g.renderOutline(buttonX, buttonY, BACK_BTN_W, BACK_BTN_H, borderColor);

        String label = line("gui.logisticsnetworks.computer.exit");
        int textX = buttonX + (BACK_BTN_W / 2) - (font.width(label) / 2);
        int textY = buttonY + (BACK_BTN_H / 2) - (font.lineHeight / 2);
        g.drawString(font, label, textX, textY, hovered ? COLOR_ACCENT : COLOR_TEXT_SECONDARY);
    }

    private boolean isBackButtonClicked(double mouseX, double mouseY) {
        int buttonX = leftPos + BACK_BTN_X;
        int buttonY = topPos + BACK_BTN_Y;
        return mouseX >= buttonX && mouseX < buttonX + BACK_BTN_W
                && mouseY >= buttonY && mouseY < buttonY + BACK_BTN_H;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        if (deleteConfirmationOpen) {
            renderDeleteConfirmation(g, mouseX, mouseY);
            return;
        }
        renderDeleteTooltip(g, mouseX, mouseY);
        renderHighlightTooltip(g, mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
        if (currentPage == Page.IO_MONITOR) {
            flowMonitor.renderTooltips(g, mouseX, mouseY);
        }
    }

    private void renderDeleteTooltip(GuiGraphics g, int mouseX, int mouseY) {
        SyncNetworkListPayload.NetworkEntry entry = getSelectedNetworkEntry();
        if (currentPage != Page.NETWORK_LIST || entry == null || !entry.canDelete()) {
            return;
        }
        int x = leftPos + DETAIL_PANEL_X + DETAIL_PANEL_WIDTH - DELETE_BUTTON_SIZE - 8;
        int y = topPos + DETAIL_PANEL_Y + 20;
        if (isHoveringAbs(x, y, DELETE_BUTTON_SIZE, DELETE_BUTTON_SIZE, mouseX, mouseY)) {
            g.renderTooltip(font, label("gui.logisticsnetworks.computer.delete_tooltip"), mouseX, mouseY);
        }
    }

    private void renderDeleteConfirmation(GuiGraphics g, int mouseX, int mouseY) {
        SyncNetworkListPayload.NetworkEntry entry = getSelectedNetworkEntry();
        if (entry == null) {
            deleteConfirmationOpen = false;
            return;
        }
        int x = (width - DELETE_MODAL_WIDTH) / 2;
        int y = (height - DELETE_MODAL_HEIGHT) / 2;
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(0, 0, width, height, 0xB0000000);
        g.fill(x, y, x + DELETE_MODAL_WIDTH, y + DELETE_MODAL_HEIGHT, COLOR_PANEL_ALT);
        g.renderOutline(x, y, DELETE_MODAL_WIDTH, DELETE_MODAL_HEIGHT, COLOR_DANGER_BORDER);
        g.fill(x + 1, y + 1, x + DELETE_MODAL_WIDTH - 1, y + 18, COLOR_PANEL_HEADER);
        g.drawString(font, label("gui.logisticsnetworks.computer.delete_title"), x + 10, y + 6,
                COLOR_DANGER_BORDER);
        renderDeleteWarning(g, entry, x, y);
        int deleteX = x + DELETE_MODAL_WIDTH - 82;
        int cancelX = deleteX - 80;
        int buttonY = y + DELETE_MODAL_HEIGHT - 26;
        renderConfirmationButton(g, cancelX, buttonY, 72,
                line("gui.logisticsnetworks.config.cancel"), false, mouseX, mouseY);
        renderConfirmationButton(g, deleteX, buttonY, 72,
                line("gui.logisticsnetworks.computer.delete_confirm"), true, mouseX, mouseY);
        g.pose().popPose();
    }

    private void renderDeleteWarning(GuiGraphics g, SyncNetworkListPayload.NetworkEntry entry, int x, int y) {
        Component warning = label("gui.logisticsnetworks.computer.delete_warning",
                entry.name(), entry.nodeCount());
        List<FormattedCharSequence> lines = font.split(warning, DELETE_MODAL_WIDTH - 20);
        int textY = y + 26;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x + 10, textY, COLOR_TEXT);
            textY += 10;
        }
    }

    private void renderConfirmationButton(GuiGraphics g, int x, int y, int width, String text,
            boolean danger, int mouseX, int mouseY) {
        boolean hovered = isHoveringAbs(x, y, width, 18, mouseX, mouseY);
        int background = danger ? (hovered ? COLOR_DANGER_HOVER : COLOR_DANGER)
                : (hovered ? COLOR_ROW_SELECTED : COLOR_BADGE_BG);
        int border = hovered ? (danger ? COLOR_DANGER_BORDER : COLOR_BORDER_BRIGHT) : COLOR_BORDER;
        g.fill(x, y, x + width, y + 18, background);
        g.renderOutline(x, y, width, 18, border);
        g.drawCenteredString(font, text, x + width / 2, y + 5, danger ? 0xFFFFFFFF : COLOR_TEXT);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (deleteConfirmationOpen) {
            if (keyCode == 256) {
                deleteConfirmationOpen = false;
            }
            return true;
        }
        if (currentPage == Page.NODE_MAP && nodeLabelBox != null && nodeLabelBox.isFocused()) {
            if (keyCode == 256) {
                nodeLabelBox.setFocused(false);
                return true;
            }
            nodeLabelBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (currentPage == Page.NETWORK_LIST && networkSearchBox != null && networkSearchBox.isFocused()) {
            if (keyCode == 256) {
                networkSearchBox.setFocused(false);
                return true;
            }
            networkSearchBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (keyCode == 256) return super.keyPressed(keyCode, scanCode, modifiers);

        int action = ClientControls.resolveKeyAction(keyCode, scanCode);
        if (action != -1) {
            handleInteraction(ClientControls.cursorX(minecraft), ClientControls.cursorY(minecraft), action);
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (deleteConfirmationOpen) {
            return true;
        }
        if (currentPage == Page.NETWORK_LIST && networkSearchBox != null && networkSearchBox.isFocused()) {
            return networkSearchBox.charTyped(ch, modifiers);
        }
        return super.charTyped(ch, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (deleteConfirmationOpen) {
            return true;
        }
        switch (currentPage) {
            case NETWORK_LIST -> {
                List<SyncNetworkListPayload.NetworkEntry> filtered = getFilteredNetworks();
                if (filtered.size() > NETWORKS_PER_PAGE) {
                    networkScrollOffset -= (int) scrollY;
                    int maxScroll = Math.max(0, filtered.size() - NETWORKS_PER_PAGE);
                    networkScrollOffset = Math.max(0, Math.min(networkScrollOffset, maxScroll));
                    return true;
                }
            }
            case IO_MONITOR -> {
                if (flowMonitor.scroll(mouseX, mouseY, scrollY)) {
                    return true;
                }
            }
            case NODE_MAP -> {
                if (!nodeInfoList.isEmpty()) {
                    List<RenderEntry> renderEntries = buildNodeRenderEntries();
                    int rowsPerPage = nodeRowsPerPage();
                    if (renderEntries.size() > rowsPerPage) {
                        nodeMapScrollOffset -= (int) scrollY;
                        int maxScroll = Math.max(0, renderEntries.size() - rowsPerPage);
                        nodeMapScrollOffset = Math.max(0, Math.min(nodeMapScrollOffset, maxScroll));
                        return true;
                    }
                }
            }
            default -> {
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int action = ClientControls.resolveMouseAction(button);
        if (deleteConfirmationOpen) {
            handleDeleteConfirmationClick(mouseX, mouseY, action);
            return true;
        }
        if (action != -1 && handleInteraction(mouseX, mouseY, action))
            return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleInteraction(double mouseX, double mouseY, int action) {
        if (action != 0)
            return false;

        return switch (currentPage) {
            case NETWORK_LIST -> handleNetworkListInteraction(mouseX, mouseY);
            case IO_MONITOR -> flowMonitor.click(leftPos, topPos, mouseX, mouseY);
            case NODE_MAP -> handleNodeMapInteraction(mouseX, mouseY);
        };
    }

    private boolean handleNetworkListInteraction(double mouseX, double mouseY) {
        if (handleSearchBoxClick(mouseX, mouseY)) {
            return true;
        }
        if (handleNetworkListClick(mouseX, mouseY)) {
            return true;
        }
        if (selectedNetworkId != null) {
            return handleOptionButtonClick(mouseX, mouseY);
        }
        return false;
    }

    private boolean handleNodeMapInteraction(double mouseX, double mouseY) {
        if (isBackButtonClicked(mouseX, mouseY)) {
            currentPage = Page.NETWORK_LIST;
            nodeInfoList.clear();
            nodeMapScrollOffset = 0;
            selectedNodeIds.clear();
            labelSettingsSource = null;
            return true;
        }
        if (handleVisibilityButtonClick(mouseX, mouseY)) {
            return true;
        }
        return handleNodeMapClick(mouseX, mouseY);
    }

    private void layoutSearchBox() {
        if (networkSearchBox == null) {
            return;
        }
        networkSearchBox.setX(leftPos + NETWORK_LIST_X + SEARCH_INPUT_X);
        networkSearchBox.setY(getSearchBoxTop() + ((SEARCH_BOX_HEIGHT - SEARCH_INPUT_HEIGHT) / 2));
        networkSearchBox.setWidth(SEARCH_INPUT_WIDTH);
    }

    private void layoutNodeLabelBox() {
        if (nodeLabelBox == null) return;
        nodeLabelBox.setX(leftPos + 18);
        nodeLabelBox.setY(topPos + imageHeight - 30);
        nodeLabelBox.setWidth(84);
    }

    private boolean handleSearchBoxClick(double mouseX, double mouseY) {
        if (networkSearchBox == null) {
            return false;
        }
        int startX = leftPos + NETWORK_LIST_X;
        int searchY = getSearchBoxTop();
        boolean inside = mouseX >= startX && mouseX < startX + NETWORK_LIST_WIDTH
                && mouseY >= searchY && mouseY < searchY + SEARCH_BOX_HEIGHT;
        if (!inside) {
            if (networkSearchBox.isFocused()) {
                networkSearchBox.setFocused(false);
            }
            return false;
        }
        networkSearchBox.setFocused(true);
        setFocused(networkSearchBox);
        networkSearchBox.mouseClicked(mouseX, mouseY, 0);
        return true;
    }

    private boolean isHoveringAbs(int x, int y, int w, int h, double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void setLnetStatus(String status, int color) {
        this.lnetStatus = status;
        this.lnetStatusColor = color;
    }

    private int getSearchBoxTop() {
        return topPos + DETAIL_PANEL_Y + PANEL_HEADER_HEIGHT + 4;
    }

    private boolean handleNetworkListClick(double mouseX, double mouseY) {
        List<SyncNetworkListPayload.NetworkEntry> filtered = getFilteredNetworks();
        int searchY = getSearchBoxTop() - topPos;
        int listYOffset = searchY + SEARCH_BOX_HEIGHT + 2;

        for (int i = 0; i < NETWORKS_PER_PAGE && (i + networkScrollOffset) < filtered.size(); i++) {
            int index = i + networkScrollOffset;
            if (isHovering(NETWORK_LIST_X, listYOffset + (i * NETWORK_ENTRY_HEIGHT),
                    NETWORK_LIST_WIDTH, NETWORK_ENTRY_HEIGHT - 2, (int) mouseX, (int) mouseY)) {
                SyncNetworkListPayload.NetworkEntry clickedEntry = filtered.get(index);

                int entryX = leftPos + NETWORK_LIST_X;
                int entryY = topPos + listYOffset + (i * NETWORK_ENTRY_HEIGHT);
                int starX = entryX + NETWORK_LIST_WIDTH - STAR_BTN_W - 3;
                int starY = entryY + 4;
                if (mouseX >= starX && mouseX < starX + STAR_BTN_W
                        && mouseY >= starY && mouseY < starY + STAR_BTN_W) {
                    NetworkHandler.sendToServer(
                            new ToggleComputerPinnedNetworkPayload(menu.getComputerPos(), clickedEntry.id()));
                    return true;
                }

                if (clickedEntry.id().equals(selectedNetworkId)) {
                    selectedNetworkId = null;
                    selectedNetworkName = "";
                } else {
                    selectedNetworkId = clickedEntry.id();
                    selectedNetworkName = clickedEntry.name();
                }
                return true;
            }
        }
        return false;
    }

    private boolean handleNodeMapClick(double mouseX, double mouseY) {
        if (selectedNetworkId == null) {
            return false;
        }
        if (handleLabelToolbarClick(mouseX, mouseY)) return true;

        int contentX = leftPos + 10;
        int contentY = topPos + 34;
        int contentW = imageWidth - 20;
        int headerX = contentX + 8;
        int headerW = contentW - 16;
        int listY = contentY + 40;
        List<RenderEntry> renderEntries = buildNodeRenderEntries();

        for (int i = 0; i < nodeRowsPerPage() && (i + nodeMapScrollOffset) < renderEntries.size(); i++) {
            int index = i + nodeMapScrollOffset;
            RenderEntry entry = renderEntries.get(index);
            int entryY = listY + (i * NODE_ENTRY_HEIGHT);
            int buttonX = headerX + headerW - NODE_ROW_SIDE_PAD - HIGHLIGHT_BTN_W;
            int buttonY = entryY + ((NODE_ENTRY_HEIGHT - HIGHLIGHT_BTN_H - 2) / 2);
            boolean buttonClicked = mouseX >= buttonX && mouseX < buttonX + HIGHLIGHT_BTN_W
                    && mouseY >= buttonY && mouseY < buttonY + HIGHLIGHT_BTN_H;

            if (entry.isHeader) {
                if (isHoveringAbs(headerX + 4, entryY + 4, 14, 14, mouseX, mouseY)) {
                    toggleGroupSelection(entry.headerName);
                    return true;
                }
                int settingsX = buttonX - SETTINGS_BTN_GAP - SETTINGS_BTN_W;
                boolean settingsClicked = mouseX >= settingsX && mouseX < settingsX + SETTINGS_BTN_W
                        && mouseY >= buttonY && mouseY < buttonY + SETTINGS_BTN_H;
                if (settingsClicked) {
                    openFirstNodeInLabel(entry.headerName);
                    return true;
                }
                if (buttonClicked) {
                    NetworkHandler.sendToServer(new ToggleNetworkLabelHighlightPayload(selectedNetworkId, entry.headerName));
                    toggleGroupHighlight(entry.headerName);
                    return true;
                }
                if (mouseX >= headerX && mouseX < headerX + headerW
                        && mouseY >= entryY && mouseY < entryY + NODE_ENTRY_HEIGHT - 2) {
                    if (collapsedGroups.contains(entry.headerName)) {
                        collapsedGroups.remove(entry.headerName);
                    } else {
                        collapsedGroups.add(entry.headerName);
                    }
                    return true;
                }
                continue;
            }

            if (isHoveringAbs(headerX + 4, entryY + 4, 14, 14, mouseX, mouseY)) {
                toggleNodeSelection(entry.nodeInfo.nodeId());
                return true;
            }

            if (!entry.isGrouped) {
                int settingsX = buttonX - SETTINGS_BTN_GAP - SETTINGS_BTN_W;
                boolean settingsClicked = mouseX >= settingsX && mouseX < settingsX + SETTINGS_BTN_W
                        && mouseY >= buttonY && mouseY < buttonY + SETTINGS_BTN_H;
                if (settingsClicked) {
                    openNodeSettings(entry.nodeInfo.nodeId());
                    return true;
                }
            }

            if (buttonClicked) {
                NetworkHandler.sendToServer(
                        new ToggleNetworkNodeHighlightPayload(selectedNetworkId, entry.nodeInfo.nodeId()));
                toggleNodeHighlight(entry.nodeInfo.nodeId());
                return true;
            }
        }

        return false;
    }

    private void openFirstNodeInLabel(String labelName) {
        for (SyncNetworkNodesPayload.NodeInfo info : nodeInfoList) {
            if (labelName.equals(info.nodeLabel())) {
                openNodeSettings(info.nodeId());
                return;
            }
        }
    }

    private void openNodeSettings(UUID nodeId) {
        NodeTableSession.begin(selectedNetworkId, selectedNetworkName, nodeMapScrollOffset, collapsedGroups,
                selectedNodeIds, labelSettingsSource, nodeLabelBox.getValue());
        NetworkHandler.sendToServer(new RequestOpenNodeSettingsPayload(selectedNetworkId, nodeId));
    }

    private boolean handleLabelToolbarClick(double mouseX, double mouseY) {
        if (selectedNodeIds.isEmpty()) return false;
        int contentX = leftPos + 10;
        int contentY = topPos + 34;
        int contentW = imageWidth - 20;
        int y = contentY + imageHeight - 44 - 20;
        int applyX = contentX + 96;
        int sourceX = applyX + 44;
        int sameX = sourceX + 82;
        if (isHoveringAbs(applyX, y, 40, 16, mouseX, mouseY)) {
            applyBulkLabel();
            return true;
        }
        if (isHoveringAbs(sourceX, y, 78, 16, mouseX, mouseY)) {
            cycleLabelSource();
            return true;
        }
        if (isHoveringAbs(sameX, y, contentX + contentW - 8 - sameX, 16, mouseX, mouseY)) {
            selectSameBlock();
            return true;
        }
        return false;
    }

    private boolean handleOptionButtonClick(double mouseX, double mouseY) {
        int panelX = leftPos + DETAIL_PANEL_X;
        int panelY = topPos + DETAIL_PANEL_Y;
        int buttonX = panelX + 12;
        int buttonWidth = DETAIL_PANEL_WIDTH - 24;
        int button1Y = panelY + 84;
        int button2Y = button1Y + OPTION_BTN_HEIGHT + OPTION_BTN_GAP;
        int button3Y = button2Y + OPTION_BTN_HEIGHT + OPTION_BTN_GAP;
        int button4Y = button3Y + OPTION_BTN_HEIGHT + OPTION_BTN_GAP;

        SyncNetworkListPayload.NetworkEntry entry = getSelectedNetworkEntry();
        int deleteX = panelX + DETAIL_PANEL_WIDTH - DELETE_BUTTON_SIZE - 8;
        int deleteY = panelY + 20;
        if (entry != null && entry.canDelete()
                && isHoveringAbs(deleteX, deleteY, DELETE_BUTTON_SIZE, DELETE_BUTTON_SIZE, mouseX, mouseY)) {
            deleteConfirmationOpen = true;
            networkSearchBox.setFocused(false);
            return true;
        }

        if (mouseX >= buttonX && mouseX < buttonX + buttonWidth
                && mouseY >= button1Y && mouseY < button1Y + OPTION_BTN_HEIGHT) {
            openFlowMonitor();
            return true;
        }

        if (mouseX >= buttonX && mouseX < buttonX + buttonWidth
                && mouseY >= button2Y && mouseY < button2Y + OPTION_BTN_HEIGHT) {
            currentPage = Page.NODE_MAP;
            nodeMapScrollOffset = 0;
            selectedNodeIds.clear();
            labelSettingsSource = null;
            NetworkHandler.sendToServer(new RequestNetworkNodesPayload(selectedNetworkId));
            return true;
        }

        if (mouseX >= buttonX && mouseX < buttonX + buttonWidth
                && mouseY >= button4Y && mouseY < button4Y + OPTION_BTN_HEIGHT) {
            NodeGraphSession.begin(selectedNetworkId);
            NetworkHandler.sendToServer(new me.almana.logisticsnetworks.network.RequestOpenGraphPayload(
                    menu.getComputerPos(), minecraft.level.dimension().location(), selectedNetworkId,
                    java.util.Optional.empty(), 0));
            return true;
        }

        return false;
    }

    private void handleDeleteConfirmationClick(double mouseX, double mouseY, int action) {
        if (action != 0) {
            return;
        }
        int x = (width - DELETE_MODAL_WIDTH) / 2;
        int y = (height - DELETE_MODAL_HEIGHT) / 2;
        int deleteX = x + DELETE_MODAL_WIDTH - 82;
        int cancelX = deleteX - 80;
        int buttonY = y + DELETE_MODAL_HEIGHT - 26;
        if (isHoveringAbs(cancelX, buttonY, 72, 18, mouseX, mouseY)) {
            deleteConfirmationOpen = false;
            return;
        }
        if (isHoveringAbs(deleteX, buttonY, 72, 18, mouseX, mouseY)) {
            UUID networkId = selectedNetworkId;
            deleteConfirmationOpen = false;
            if (networkId != null) {
                NetworkHandler.sendToServer(new DeleteNetworkPayload(networkId));
            }
        }
    }

    private List<RenderEntry> buildNodeRenderEntries() {
        Map<String, List<SyncNetworkNodesPayload.NodeInfo>> groups = new LinkedHashMap<>();
        List<SyncNetworkNodesPayload.NodeInfo> unlabeled = new ArrayList<>();

        for (SyncNetworkNodesPayload.NodeInfo info : nodeInfoList) {
            if (info.nodeLabel().isEmpty()) {
                unlabeled.add(info);
            } else {
                groups.computeIfAbsent(info.nodeLabel(), key -> new ArrayList<>()).add(info);
            }
        }

        List<RenderEntry> renderEntries = new ArrayList<>();
        for (Map.Entry<String, List<SyncNetworkNodesPayload.NodeInfo>> group : groups.entrySet()) {
            renderEntries.add(new RenderEntry(group.getKey(), group.getValue().size()));
            if (!collapsedGroups.contains(group.getKey())) {
                for (SyncNetworkNodesPayload.NodeInfo info : group.getValue()) {
                    renderEntries.add(new RenderEntry(info, true));
                }
            }
        }
        for (SyncNetworkNodesPayload.NodeInfo info : unlabeled) {
            renderEntries.add(new RenderEntry(info, false));
        }

        return renderEntries;
    }

    private int nodeRowsPerPage() {
        int contentHeight = imageHeight - 44;
        return Math.max(1, (contentHeight - 64) / NODE_ENTRY_HEIGHT);
    }

    private int groupSelectionState(String label) {
        int selected = 0;
        int total = 0;
        for (SyncNetworkNodesPayload.NodeInfo node : nodeInfoList) {
            if (!label.equals(node.nodeLabel())) continue;
            total++;
            if (selectedNodeIds.contains(node.nodeId())) selected++;
        }
        if (selected == 0) return 0;
        return selected == total ? 2 : 1;
    }

    private void toggleGroupSelection(String label) {
        boolean allSelected = groupSelectionState(label) == 2;
        for (SyncNetworkNodesPayload.NodeInfo node : nodeInfoList) {
            if (!label.equals(node.nodeLabel())) continue;
            if (allSelected) selectedNodeIds.remove(node.nodeId());
            else selectedNodeIds.add(node.nodeId());
        }
        normalizeLabelSource();
    }

    private void toggleNodeSelection(UUID nodeId) {
        if (!selectedNodeIds.remove(nodeId)) selectedNodeIds.add(nodeId);
        normalizeLabelSource();
    }

    private void normalizeLabelSource() {
        if (labelSettingsSource != null && selectedNodeIds.contains(labelSettingsSource)) return;
        labelSettingsSource = nodeInfoList.stream().map(SyncNetworkNodesPayload.NodeInfo::nodeId)
                .filter(selectedNodeIds::contains).findFirst().orElse(null);
    }

    private String labelSourceText() {
        normalizeLabelSource();
        String source = nodeInfoList.stream().filter(node -> node.nodeId().equals(labelSettingsSource))
                .map(node -> resolveBlockLabel(node.blockName())).findFirst().orElse("-");
        return line("gui.logisticsnetworks.computer.settings_source", source);
    }

    private void cycleLabelSource() {
        List<UUID> selected = nodeInfoList.stream().map(SyncNetworkNodesPayload.NodeInfo::nodeId)
                .filter(selectedNodeIds::contains).toList();
        if (selected.isEmpty()) return;
        int index = selected.indexOf(labelSettingsSource);
        labelSettingsSource = selected.get(Math.floorMod(index + 1, selected.size()));
    }

    private void selectSameBlock() {
        normalizeLabelSource();
        String block = nodeInfoList.stream().filter(node -> node.nodeId().equals(labelSettingsSource))
                .map(SyncNetworkNodesPayload.NodeInfo::blockName).findFirst().orElse(null);
        if (block == null) return;
        selectedNodeIds.clear();
        nodeInfoList.stream().filter(node -> block.equals(node.blockName()))
                .map(SyncNetworkNodesPayload.NodeInfo::nodeId).forEach(selectedNodeIds::add);
        normalizeLabelSource();
    }

    private void applyBulkLabel() {
        normalizeLabelSource();
        if (selectedNetworkId == null || labelSettingsSource == null) return;
        List<UUID> selected = nodeInfoList.stream().map(SyncNetworkNodesPayload.NodeInfo::nodeId)
                .filter(selectedNodeIds::contains).toList();
        if (selected.size() > SetNodeLabelsPayload.MAX_NODES) return;
        NetworkHandler.sendToServer(new SetNodeLabelsPayload(
                selectedNetworkId, selected, nodeLabelBox.getValue(), labelSettingsSource));
    }

    private void renderHighlightTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (currentPage != Page.NODE_MAP || selectedNetworkId == null) {
            return;
        }

        if (isSettingsButtonHovered(mouseX, mouseY)) {
            g.renderTooltip(font, label("gui.logisticsnetworks.computer.settings_tooltip"), mouseX, mouseY);
            return;
        }

        HighlightButtonArea area = findHighlightButton((int) mouseX, (int) mouseY);
        if (area != null) {
            g.renderTooltip(font, label("gui.logisticsnetworks.computer.highlight_tooltip"), (int) mouseX,
                    (int) mouseY);
        }
    }

    private boolean isSettingsButtonHovered(int mouseX, int mouseY) {
        int contentX = leftPos + 10;
        int contentY = topPos + 34;
        int contentW = imageWidth - 20;
        int headerX = contentX + 8;
        int headerW = contentW - 16;
        int listY = contentY + 40;
        List<RenderEntry> renderEntries = buildNodeRenderEntries();

        for (int i = 0; i < nodeRowsPerPage() && (i + nodeMapScrollOffset) < renderEntries.size(); i++) {
            int index = i + nodeMapScrollOffset;
            RenderEntry entry = renderEntries.get(index);
            int entryY = listY + (i * NODE_ENTRY_HEIGHT);
            int highlightX = headerX + headerW - NODE_ROW_SIDE_PAD - HIGHLIGHT_BTN_W;
            int buttonY = entryY + ((NODE_ENTRY_HEIGHT - HIGHLIGHT_BTN_H - 2) / 2);
            int settingsX = highlightX - SETTINGS_BTN_GAP - SETTINGS_BTN_W;

            boolean hasSettings = entry.isHeader || !entry.isGrouped;
            if (hasSettings && mouseX >= settingsX && mouseX < settingsX + SETTINGS_BTN_W
                    && mouseY >= buttonY && mouseY < buttonY + SETTINGS_BTN_H) {
                return true;
            }
        }
        return false;
    }

    private HighlightButtonArea findHighlightButton(int mouseX, int mouseY) {
        int contentX = leftPos + 10;
        int contentY = topPos + 34;
        int contentW = imageWidth - 20;
        int headerX = contentX + 8;
        int headerW = contentW - 16;
        int listY = contentY + 40;
        List<RenderEntry> renderEntries = buildNodeRenderEntries();

        for (int i = 0; i < nodeRowsPerPage() && (i + nodeMapScrollOffset) < renderEntries.size(); i++) {
            int index = i + nodeMapScrollOffset;
            RenderEntry entry = renderEntries.get(index);
            int entryY = listY + (i * NODE_ENTRY_HEIGHT);
            int buttonX = headerX + headerW - NODE_ROW_SIDE_PAD - HIGHLIGHT_BTN_W;
            int buttonY = entryY + ((NODE_ENTRY_HEIGHT - HIGHLIGHT_BTN_H - 2) / 2);
            if (mouseX >= buttonX && mouseX < buttonX + HIGHLIGHT_BTN_W
                    && mouseY >= buttonY && mouseY < buttonY + HIGHLIGHT_BTN_H) {
                return new HighlightButtonArea(entry.isHeader, entry.headerName,
                        entry.isHeader ? null : entry.nodeInfo.nodeId());
            }
        }

        return null;
    }

    private boolean isGroupHighlighted(String label) {
        boolean found = false;
        for (SyncNetworkNodesPayload.NodeInfo info : nodeInfoList) {
            if (!label.equals(info.nodeLabel())) {
                continue;
            }
            found = true;
            if (!info.highlighted()) {
                return false;
            }
        }
        return found;
    }

    private void setAllNodeVisibility(boolean visible) {
        List<SyncNetworkNodesPayload.NodeInfo> updated = new ArrayList<>(nodeInfoList.size());
        for (SyncNetworkNodesPayload.NodeInfo info : nodeInfoList) {
            updated.add(withVisibility(info, visible));
        }
        nodeInfoList = updated;
    }

    private void toggleGroupHighlight(String label) {
        boolean makeVisible = false;
        for (SyncNetworkNodesPayload.NodeInfo info : nodeInfoList) {
            if (label.equals(info.nodeLabel()) && !info.highlighted()) {
                makeVisible = true;
                break;
            }
        }
        setGroupHighlight(label, makeVisible);
    }

    private void setGroupHighlight(String label, boolean highlighted) {
        List<SyncNetworkNodesPayload.NodeInfo> updated = new ArrayList<>(nodeInfoList.size());
        for (SyncNetworkNodesPayload.NodeInfo info : nodeInfoList) {
            updated.add(label.equals(info.nodeLabel()) ? withHighlight(info, highlighted) : info);
        }
        nodeInfoList = updated;
    }

    private void toggleNodeHighlight(UUID nodeId) {
        List<SyncNetworkNodesPayload.NodeInfo> updated = new ArrayList<>(nodeInfoList.size());
        for (SyncNetworkNodesPayload.NodeInfo info : nodeInfoList) {
            updated.add(info.nodeId().equals(nodeId) ? withHighlight(info, !info.highlighted()) : info);
        }
        nodeInfoList = updated;
    }

    private SyncNetworkNodesPayload.NodeInfo withVisibility(SyncNetworkNodesPayload.NodeInfo info, boolean visible) {
        return new SyncNetworkNodesPayload.NodeInfo(
                info.nodeId(),
                info.nodePos(),
                info.attachedPos(),
                info.blockName(),
                info.nodeLabel(),
                info.dimension(),
                visible,
                info.highlighted());
    }

    private SyncNetworkNodesPayload.NodeInfo withHighlight(SyncNetworkNodesPayload.NodeInfo info, boolean highlighted) {
        return new SyncNetworkNodesPayload.NodeInfo(
                info.nodeId(),
                info.nodePos(),
                info.attachedPos(),
                info.blockName(),
                info.nodeLabel(),
                info.dimension(),
                info.visible(),
                highlighted);
    }

    private List<SyncNetworkListPayload.NetworkEntry> getFilteredNetworks() {
        String filter = networkSearchBox != null ? networkSearchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
        List<SyncNetworkListPayload.NetworkEntry> pinned = new ArrayList<>();
        List<SyncNetworkListPayload.NetworkEntry> unpinned = new ArrayList<>();
        for (SyncNetworkListPayload.NetworkEntry entry : networkList) {
            if (!filter.isEmpty() && !entry.name().toLowerCase(Locale.ROOT).contains(filter))
                continue;
            if (entry.pinned()) {
                pinned.add(entry);
            } else {
                unpinned.add(entry);
            }
        }
        pinned.addAll(unpinned);
        return pinned;
    }

    private SyncNetworkListPayload.NetworkEntry getSelectedNetworkEntry() {
        if (selectedNetworkId == null) {
            return null;
        }
        for (SyncNetworkListPayload.NetworkEntry entry : networkList) {
            if (entry.id().equals(selectedNetworkId)) {
                return entry;
            }
        }
        return null;
    }

    private Component label(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private String line(String key, Object... args) {
        return label(key, args).getString();
    }

    private String trimText(String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - 6)) + "...";
    }

    private String resolveBlockLabel(String blockName) {
        ResourceLocation blockId = ResourceLocation.tryParse(blockName);
        if (blockId != null) {
            Item item = BuiltInRegistries.ITEM.get(blockId);
            if (item != Items.AIR) {
                return item.getDefaultInstance().getHoverName().getString();
            }
        }

        return blockName;
    }

    private String formatPosition(SyncNetworkNodesPayload.NodeInfo nodeInfo) {
        String dimension = nodeInfo.dimension().getPath();
        dimension = switch (dimension) {
            case "overworld" -> line("gui.logisticsnetworks.computer.dimension.overworld");
            case "the_nether" -> line("gui.logisticsnetworks.computer.dimension.nether");
            case "the_end" -> line("gui.logisticsnetworks.computer.dimension.end");
            default -> nodeInfo.dimension().toString();
        };
        return line("gui.logisticsnetworks.computer.position", dimension,
                nodeInfo.attachedPos().getX(),
                nodeInfo.attachedPos().getY(),
                nodeInfo.attachedPos().getZ());
    }

    public void receiveNetworkList(List<SyncNetworkListPayload.NetworkEntry> networks) {
        NodeTableSession.State table = NodeTableSession.take();
        if (table != null) {
            currentPage = Page.NODE_MAP;
            selectedNetworkId = table.networkId();
            selectedNetworkName = table.networkName();
            nodeMapScrollOffset = table.scroll();
            collapsedGroups.clear();
            collapsedGroups.addAll(table.collapsedGroups());
            selectedNodeIds.clear();
            selectedNodeIds.addAll(table.selectedNodes());
            labelSettingsSource = table.settingsSource();
            nodeLabelBox.setValue(table.label());
            NetworkHandler.sendToServer(new RequestNetworkNodesPayload(table.networkId()));
        }
        UUID returningNetwork = NodeGraphSession.takeReturningNetwork();
        if (returningNetwork != null) {
            networks.stream().filter(network -> network.id().equals(returningNetwork)).findFirst().ifPresent(network -> {
                selectedNetworkId = network.id();
                selectedNetworkName = network.name();
            });
        }
        if (Config.debugMode) {
            LOGGER.debug("Received network list with {} entries", networks.size());
            for (SyncNetworkListPayload.NetworkEntry entry : networks) {
                LOGGER.debug("  - {} ({} nodes)", entry.name(), entry.nodeCount());
            }
        }
        this.networkList = new ArrayList<>(networks);
        SyncNetworkListPayload.NetworkEntry selected = getSelectedNetworkEntry();
        if (selectedNetworkId != null && selected == null) {
            if (currentPage == Page.IO_MONITOR) {
                closeFlowMonitor();
            }
            selectedNetworkId = null;
            selectedNetworkName = "";
            deleteConfirmationOpen = false;
        } else if (selected != null) {
            selectedNetworkName = selected.name();
            if (!selected.canDelete()) {
                deleteConfirmationOpen = false;
            }
        }
        this.networkScrollOffset = Math.min(this.networkScrollOffset,
                Math.max(0, networkList.size() - NETWORKS_PER_PAGE));
        if (Config.debugMode) LOGGER.debug("Network list updated, now have {} networks", this.networkList.size());
    }

    public void receiveNetworkNodes(UUID networkId, List<SyncNetworkNodesPayload.NodeInfo> nodes) {
        if (networkId.equals(selectedNetworkId)) {
            this.nodeInfoList = new ArrayList<>(nodes);
            Set<UUID> available = nodes.stream().map(SyncNetworkNodesPayload.NodeInfo::nodeId)
                    .collect(java.util.stream.Collectors.toSet());
            selectedNodeIds.retainAll(available);
            normalizeLabelSource();
        }
    }

    public void receiveChannelList(SyncChannelListPayload payload) {
        if (payload.networkId().equals(selectedNetworkId)) {
            flowMonitor.acceptChannels(payload);
        }
    }

    public void receiveTelemetry(SyncTelemetryPayload payload) {
        if (currentPage == Page.IO_MONITOR && payload.networkId().equals(selectedNetworkId)) {
            flowMonitor.acceptTelemetry(payload);
        }
    }

    private void openFlowMonitor() {
        currentPage = Page.IO_MONITOR;
        flowMonitor.open(selectedNetworkName);
        NetworkHandler.sendToServer(new RequestChannelListPayload(selectedNetworkId));
        subscribeTelemetry();
    }

    private void closeFlowMonitor() {
        unsubscribeTelemetry();
        currentPage = Page.NETWORK_LIST;
    }

    private void subscribeTelemetry() {
        if (telemetryNetworkId == null && selectedNetworkId != null) {
            telemetryNetworkId = selectedNetworkId;
            NetworkHandler.sendToServer(new SubscribeTelemetryPayload(telemetryNetworkId, true));
        }
    }

    private void unsubscribeTelemetry() {
        if (telemetryNetworkId != null) {
            NetworkHandler.sendToServer(new SubscribeTelemetryPayload(telemetryNetworkId, false));
            telemetryNetworkId = null;
        }
    }

    private static class RenderEntry {
        final boolean isHeader;
        final String headerName;
        final int headerCount;
        final SyncNetworkNodesPayload.NodeInfo nodeInfo;
        final boolean isGrouped;

        RenderEntry(String headerName, int count) {
            this.isHeader = true;
            this.headerName = headerName;
            this.headerCount = count;
            this.nodeInfo = null;
            this.isGrouped = false;
        }

        RenderEntry(SyncNetworkNodesPayload.NodeInfo nodeInfo, boolean isGrouped) {
            this.isHeader = false;
            this.headerName = null;
            this.headerCount = 0;
            this.nodeInfo = nodeInfo;
            this.isGrouped = isGrouped;
        }
    }

    private record HighlightButtonArea(boolean header, String label, UUID nodeId) {
    }
}
