package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.menu.ComputerMenu;
import me.almana.logisticsnetworks.logic.TelemetryManager;
import me.almana.logisticsnetworks.network.RequestNetworkNodesPayload;
import me.almana.logisticsnetworks.network.RequestOpenNodeSettingsPayload;
import me.almana.logisticsnetworks.network.SetNetworkNodesVisibilityPayload;
import me.almana.logisticsnetworks.network.RequestChannelListPayload;
import me.almana.logisticsnetworks.network.SubscribeTelemetryPayload;
import me.almana.logisticsnetworks.network.SyncChannelListPayload;
import me.almana.logisticsnetworks.network.SyncNetworkListPayload;
import me.almana.logisticsnetworks.network.SyncNetworkNodesPayload;
import me.almana.logisticsnetworks.network.SyncTelemetryPayload;
import me.almana.logisticsnetworks.network.ToggleNetworkLabelHighlightPayload;
import me.almana.logisticsnetworks.network.ToggleNetworkNodeHighlightPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ComputerScreen extends AbstractContainerScreen<ComputerMenu> {

    private enum Page {
        NETWORK_LIST,
        IO_CHANNEL_LIST,
        IO_CHANNEL_GRAPH,
        NODE_MAP
    }

    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 240;
    private static final int NETWORKS_PER_PAGE = 5;
    private static final int NETWORK_ENTRY_HEIGHT = 30;
    private static final int NETWORK_LIST_X = 12;
    private static final int NETWORK_LIST_Y = 68;
    private static final int NETWORK_LIST_WIDTH = 116;
    private static final int DETAIL_PANEL_X = 136;
    private static final int DETAIL_PANEL_Y = 38;
    private static final int DETAIL_PANEL_WIDTH = 172;
    private static final int DETAIL_PANEL_HEIGHT = 194;
    private static final int OPTION_BTN_HEIGHT = 34;
    private static final int OPTION_BTN_GAP = 12;
    private static final int NODE_ENTRY_HEIGHT = 22;
    private static final int NODES_PER_PAGE = 7;
    private static final int VIS_BTN_W = 54;
    private static final int VIS_BTN_H = 14;
    private static final int VIS_BTN_GAP = 6;
    private static final int HIGHLIGHT_BTN_W = 16;
    private static final int HIGHLIGHT_BTN_H = 12;
    private static final int SETTINGS_BTN_W = 16;
    private static final int SETTINGS_BTN_H = 12;
    private static final int SETTINGS_BTN_GAP = 4;
    private static final int NODE_ROW_SIDE_PAD = 8;
    private static final int NODE_TEXT_GAP = 8;
    private static final int VIS_BTN_Y = 38;
    private static final int BACK_BTN_X = 10;
    private static final int BACK_BTN_Y = 38;
    private static final int BACK_BTN_W = 52;
    private static final int BACK_BTN_H = 14;
    private static final int PANEL_HEADER_HEIGHT = 14;
    private static final int CHANNEL_ENTRY_HEIGHT = 18;
    private static final int CHANNELS_PER_PAGE = 7;

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
    private static final int COLOR_GRAPH = 0xFF6EE896;
    private static final int COLOR_GRAPH_GRID = 0xFF213529;
    private static final int COLOR_HIGHLIGHT_BG = 0xFF1B2640;
    private static final int COLOR_HIGHLIGHT_HOVER = 0xFF25355B;
    private static final int COLOR_HIGHLIGHT_BORDER = 0xFF72A7FF;
    private static final int COLOR_LAMP_OFF = 0xFF5F7568;
    private static final int COLOR_LAMP_OFF_GLOW = 0xFF37443D;
    private static final int COLOR_LAMP_ON = 0xFF72A7FF;
    private static final int COLOR_LAMP_ON_GLOW = 0xFFBED6FF;
    private static final int COLOR_LAMP_BASE = 0xFF8FA39C;
    private static final int COLOR_FLUID_BAR = 0xFF6EB4E8;
    private static final int COLOR_ENERGY_BAR = 0xFFE8D46E;
    private static final int COLOR_CHEMICAL_BAR = 0xFFD070E8;
    private static final int COLOR_SOURCE_BAR = 0xFF70E8D0;

    private Page currentPage = Page.NETWORK_LIST;
    private List<SyncNetworkListPayload.NetworkEntry> networkList = new ArrayList<>();
    private int networkScrollOffset = 0;
    private UUID selectedNetworkId = null;
    private String selectedNetworkName = "";

    private List<SyncNetworkNodesPayload.NodeInfo> nodeInfoList = new ArrayList<>();
    private int nodeMapScrollOffset = 0;
    private final Set<String> collapsedGroups = new HashSet<>();

    private List<SyncChannelListPayload.ChannelEntry> channelList = new ArrayList<>();
    private int channelListScrollOffset;
    private int watchedChannelIndex;
    private int watchedTypeOrdinal;
    private long[] telemetryHistory = new long[TelemetryManager.HISTORY_SIZE];
    private int telemetryIndex;
    private boolean telemetrySubscribed;

    public ComputerScreen(ComputerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.titleLabelY = 10000;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void removed() {
        unsubscribeTelemetry();
        super.removed();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        renderComputerShell(g);

        switch (currentPage) {
            case NETWORK_LIST -> renderNetworkListPage(g, mouseX, mouseY);
            case IO_CHANNEL_LIST -> renderChannelListPage(g, mouseX, mouseY);
            case IO_CHANNEL_GRAPH -> renderChannelGraphPage(g, mouseX, mouseY);
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
            renderIdleSession(g, leftPos + DETAIL_PANEL_X, topPos + DETAIL_PANEL_Y);
        } else {
            renderSelectedSession(g, mouseX, mouseY);
        }
    }

    private void renderDriveBay(GuiGraphics g) {
        Slot slot = menu.slots.get(0);
        int bayX = leftPos + slot.x - 4;
        int bayY = topPos + slot.y - 4;
        g.fill(bayX, bayY, bayX + 24, bayY + 24, COLOR_PANEL_ALT);
        g.renderOutline(bayX, bayY, 24, 24, COLOR_BORDER);
        g.fill(bayX + 1, bayY + 1, bayX + 23, bayY + 23, COLOR_FRAME_INNER);
        g.renderOutline(bayX + 3, bayY + 3, 18, 18, COLOR_ACCENT_DARK);
        g.fill(bayX + 3, bayY + 3, bayX + 21, bayY + 4, COLOR_BORDER);
        g.fill(bayX + 3, bayY + 20, bayX + 21, bayY + 21, COLOR_ACCENT_DARK);
    }

    private void renderNetworkList(GuiGraphics g, int mouseX, int mouseY) {
        int startX = leftPos + NETWORK_LIST_X;
        int startY = topPos + NETWORK_LIST_Y;
        int summaryY = topPos + DETAIL_PANEL_Y + PANEL_HEADER_HEIGHT + 6;
        String summary = networkList.isEmpty()
                ? line("gui.logisticsnetworks.computer.no_signal")
                : line("gui.logisticsnetworks.computer.mounted", networkList.size());
        g.drawString(font, summary, startX, summaryY, COLOR_TEXT_SECONDARY);

        if (networkList.isEmpty()) {
            g.drawString(font, label("gui.logisticsnetworks.computer.no_networks_online"), startX + 4, startY + 10,
                    COLOR_TEXT_MUTED);
            g.drawString(font, label("gui.logisticsnetworks.computer.insert_wrench"), startX + 4, startY + 22,
                    COLOR_TEXT_MUTED);
            return;
        }

        int maxScroll = Math.max(0, networkList.size() - NETWORKS_PER_PAGE);
        networkScrollOffset = Math.max(0, Math.min(networkScrollOffset, maxScroll));

        for (int i = 0; i < NETWORKS_PER_PAGE && (i + networkScrollOffset) < networkList.size(); i++) {
            int index = i + networkScrollOffset;
            SyncNetworkListPayload.NetworkEntry entry = networkList.get(index);
            int entryX = startX;
            int entryY = startY + (i * NETWORK_ENTRY_HEIGHT);
            boolean hovered = isHovering(NETWORK_LIST_X, NETWORK_LIST_Y + (i * NETWORK_ENTRY_HEIGHT),
                    NETWORK_LIST_WIDTH, NETWORK_ENTRY_HEIGHT - 2, mouseX, mouseY);
            renderNetworkEntry(g, entry, entryX, entryY, NETWORK_LIST_WIDTH, hovered);
        }

        if (networkList.size() > NETWORKS_PER_PAGE) {
            int first = networkScrollOffset + 1;
            int last = Math.min(networkScrollOffset + NETWORKS_PER_PAGE, networkList.size());
            String pageInfo = line("gui.logisticsnetworks.node.page_info", first, last, networkList.size());
            int pageInfoX = leftPos + NETWORK_LIST_X + NETWORK_LIST_WIDTH - font.width(pageInfo);
            g.drawString(font, pageInfo, pageInfoX,
                    topPos + DETAIL_PANEL_Y + DETAIL_PANEL_HEIGHT - 11, COLOR_TEXT_MUTED);
        }
    }

    private void renderNetworkEntry(GuiGraphics g, SyncNetworkListPayload.NetworkEntry entry,
            int x, int y, int width, boolean hovered) {
        boolean selected = entry.id().equals(selectedNetworkId);
        int entryHeight = NETWORK_ENTRY_HEIGHT - 2;
        int bgColor = selected ? COLOR_ROW_SELECTED : (hovered ? COLOR_ROW_HOVER : COLOR_ROW);
        int borderColor = selected ? COLOR_BORDER_BRIGHT : (hovered ? COLOR_ACCENT_DARK : COLOR_BORDER);

        g.fill(x, y, x + width, y + entryHeight, bgColor);
        g.renderOutline(x, y, width, entryHeight, borderColor);
        g.fill(x + 1, y + 1, x + 3, y + entryHeight - 1, borderColor);

        String prefix = selected ? "> " : (hovered ? "+ " : "- ");
        String name = trimText(prefix + entry.name(), width - 12);
        String nodeCount = line("gui.logisticsnetworks.node.network_nodes", entry.nodeCount());

        g.drawString(font, name, x + 7, y + 6, selected ? COLOR_ACCENT : COLOR_TEXT);
        g.drawString(font, trimText(nodeCount, width - 12), x + 7, y + 18, COLOR_TEXT_SECONDARY);
    }

    private void renderIdleSession(GuiGraphics g, int panelX, int panelY) {
        int textX = panelX + 12;
        int lineY = panelY + 24;

        g.drawString(font, label("gui.logisticsnetworks.computer.no_network_mounted"), textX, lineY, COLOR_WARNING);
        g.drawString(font, label("gui.logisticsnetworks.computer.select_directory"), textX, lineY + 16,
                COLOR_TEXT_SECONDARY);
        g.drawString(font, label("gui.logisticsnetworks.computer.open_workstation"), textX, lineY + 28,
                COLOR_TEXT_SECONDARY);
        renderStatusBadge(g, textX, lineY + 50, 48, 10, line("gui.logisticsnetworks.computer.status.idle"));
        renderStatusBadge(g, textX + 56, lineY + 50, 58, 10, line("gui.logisticsnetworks.computer.status.ready"));
        g.drawString(font, label("gui.logisticsnetworks.computer.hint.dir"), textX, lineY + 76, COLOR_TEXT_MUTED);
        g.drawString(font, label("gui.logisticsnetworks.computer.hint.tab"), textX, lineY + 88, COLOR_TEXT_MUTED);
        g.drawString(font, label("gui.logisticsnetworks.computer.hint.run"), textX, lineY + 100, COLOR_TEXT_MUTED);
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
        g.drawString(font, label("gui.logisticsnetworks.computer.choose_subsystem"), textX, panelY + 82,
                COLOR_TEXT_SECONDARY);
        g.drawString(font, label("gui.logisticsnetworks.computer.inspect_network"), textX, panelY + 94,
                COLOR_TEXT_SECONDARY);
        renderOptionButtons(g, mouseX, mouseY);
    }

    private void renderOptionButtons(GuiGraphics g, int mouseX, int mouseY) {
        int panelX = leftPos + DETAIL_PANEL_X;
        int panelY = topPos + DETAIL_PANEL_Y;
        int buttonX = panelX + 12;
        int buttonWidth = DETAIL_PANEL_WIDTH - 24;
        int button1Y = panelY + 112;
        int button2Y = button1Y + OPTION_BTN_HEIGHT + OPTION_BTN_GAP;

        boolean button1Hovered = mouseX >= buttonX && mouseX < buttonX + buttonWidth
                && mouseY >= button1Y && mouseY < button1Y + OPTION_BTN_HEIGHT;
        boolean button2Hovered = mouseX >= buttonX && mouseX < buttonX + buttonWidth
                && mouseY >= button2Y && mouseY < button2Y + OPTION_BTN_HEIGHT;

        renderCommandCard(g, buttonX, button1Y, buttonWidth, OPTION_BTN_HEIGHT,
                line("gui.logisticsnetworks.computer.open_io_monitor"),
                line("gui.logisticsnetworks.computer.throughput_timeline"), button1Hovered);
        renderCommandCard(g, buttonX, button2Y, buttonWidth, OPTION_BTN_HEIGHT,
                line("gui.logisticsnetworks.computer.open_node_table"),
                line("gui.logisticsnetworks.computer.device_topology"), button2Hovered);
    }

    private void renderCommandCard(GuiGraphics g, int x, int y, int w, int h,
            String label, String detail, boolean hovered) {
        int bgColor = hovered ? COLOR_ROW_SELECTED : COLOR_PANEL_ALT;
        int borderColor = hovered ? COLOR_BORDER_BRIGHT : COLOR_BORDER;

        g.fill(x, y, x + w, y + h, bgColor);
        g.renderOutline(x, y, w, h, borderColor);
        g.fill(x + 1, y + 1, x + w - 1, y + 9, hovered ? COLOR_ACCENT_DARK : COLOR_PANEL_HEADER);
        g.drawString(font, label, x + 8, y + 6, hovered ? COLOR_ACCENT : COLOR_TEXT);
        g.drawString(font, detail, x + 8, y + 20, COLOR_TEXT_SECONDARY);
    }

    private void renderChannelListPage(GuiGraphics g, int mouseX, int mouseY) {
        int contentX = leftPos + 10;
        int contentY = topPos + 34;
        int contentW = imageWidth - 20;
        int contentH = imageHeight - 44;

        renderTerminalPanel(g, contentX, contentY, contentW, contentH, "");
        renderBackButton(g, mouseX, mouseY);
        g.drawString(font, trimText(line("gui.logisticsnetworks.computer.io_monitor_title", selectedNetworkName),
                        contentW - 88),
                contentX + 64, contentY + 4, COLOR_ACCENT);

        if (channelList.isEmpty()) {
            g.drawString(font, label("gui.logisticsnetworks.computer.no_channels"), contentX + 12, contentY + 28,
                    COLOR_TEXT_MUTED);
            g.drawString(font, label("gui.logisticsnetworks.computer.enable_channels_hint"), contentX + 12,
                    contentY + 40, COLOR_TEXT_MUTED);
            return;
        }

        int headerX = contentX + 8;
        int headerY = contentY + 22;
        int headerW = contentW - 16;
        g.fill(headerX, headerY, headerX + headerW, headerY + 14, COLOR_PANEL_ALT);
        g.renderOutline(headerX, headerY, headerW, 14, COLOR_BORDER);
        g.drawString(font, label("gui.logisticsnetworks.computer.channel_header_device"), headerX + 8, headerY + 3,
                COLOR_TEXT_SECONDARY);
        g.drawString(font, label("gui.logisticsnetworks.computer.channel_header_type"), headerX + headerW - 72,
                headerY + 3, COLOR_TEXT_SECONDARY);

        int listY = contentY + 40;
        int maxScroll = Math.max(0, channelList.size() - CHANNELS_PER_PAGE);
        channelListScrollOffset = Math.max(0, Math.min(channelListScrollOffset, maxScroll));

        for (int i = 0; i < CHANNELS_PER_PAGE && (i + channelListScrollOffset) < channelList.size(); i++) {
            int index = i + channelListScrollOffset;
            SyncChannelListPayload.ChannelEntry entry = channelList.get(index);
            int entryY = listY + (i * CHANNEL_ENTRY_HEIGHT);
            boolean hovered = mouseX >= headerX && mouseX < headerX + headerW
                    && mouseY >= entryY && mouseY < entryY + CHANNEL_ENTRY_HEIGHT - 2;
            renderChannelEntry(g, entry, headerX, entryY, headerW, hovered);
        }

        if (channelList.size() > CHANNELS_PER_PAGE) {
            int first = channelListScrollOffset + 1;
            int last = Math.min(channelListScrollOffset + CHANNELS_PER_PAGE, channelList.size());
            String scrollInfo = line("gui.logisticsnetworks.node.page_info", first, last, channelList.size());
            g.drawString(font, scrollInfo, contentX + contentW - 12 - font.width(scrollInfo),
                    contentY + contentH - 14, COLOR_TEXT_MUTED);
        }
    }

    private void renderChannelEntry(GuiGraphics g, SyncChannelListPayload.ChannelEntry entry,
            int x, int y, int width, boolean hovered) {
        int h = CHANNEL_ENTRY_HEIGHT - 2;
        int bgColor = hovered ? COLOR_ROW_HOVER : COLOR_ROW;
        int borderColor = hovered ? COLOR_ACCENT_DARK : COLOR_BORDER;
        int typeColor = resolveTypeColor(entry.typeOrdinal());

        g.fill(x, y, x + width, y + h, bgColor);
        g.renderOutline(x, y, width, h, borderColor);
        g.fill(x + 1, y + 1, x + 3, y + h - 1, typeColor);

        String chLabel = "CH" + entry.channelIndex();
        String typeName = resolveTypeName(entry.typeOrdinal());
        String nodesBadge = line("gui.logisticsnetworks.computer.channel_nodes", entry.nodeCount());

        int textX = x + 8;
        int nodesX = x + width - 6 - font.width(nodesBadge);
        int typeX = nodesX - 6 - font.width(typeName);

        g.drawString(font, chLabel, textX, y + 5, COLOR_TEXT);
        g.drawString(font, typeName, typeX, y + 5, typeColor);
        g.drawString(font, nodesBadge, nodesX, y + 5, COLOR_TEXT_SECONDARY);
    }

    private void renderChannelGraphPage(GuiGraphics g, int mouseX, int mouseY) {
        int contentX = leftPos + 10;
        int contentY = topPos + 34;
        int contentW = imageWidth - 20;
        int contentH = imageHeight - 44;

        renderTerminalPanel(g, contentX, contentY, contentW, contentH, "");
        renderBackButton(g, mouseX, mouseY);
        g.drawString(font, trimText(line("gui.logisticsnetworks.computer.channel_graph_title",
                        "CH" + watchedChannelIndex), contentW - 88),
                contentX + 64, contentY + 4, COLOR_ACCENT);

        int graphX = contentX + 10;
        int graphY = contentY + 24;
        int graphW = contentW - 20;
        int graphH = contentH - 48;
        int barColor = resolveTypeColor(watchedTypeOrdinal);
        String typeName = resolveTypeName(watchedTypeOrdinal);
        String unit = resolveTypeUnit(watchedTypeOrdinal);

        renderTelemetryGraph(g, graphX, graphY, graphW, graphH,
                typeName, telemetryHistory, telemetryIndex, barColor, unit);

        int statusY = graphY + graphH + 4;
        renderStatusBadge(g, graphX, statusY, 42, 10,
                line("gui.logisticsnetworks.computer.telemetry.live"));
    }

    private int resolveTypeColor(int typeOrdinal) {
        return switch (typeOrdinal) {
            case 1 -> COLOR_FLUID_BAR;
            case 2 -> COLOR_ENERGY_BAR;
            case 3 -> COLOR_CHEMICAL_BAR;
            case 4 -> COLOR_SOURCE_BAR;
            default -> COLOR_GRAPH;
        };
    }

    private String resolveTypeName(int typeOrdinal) {
        return switch (typeOrdinal) {
            case 1 -> line("gui.logisticsnetworks.computer.telemetry.fluids");
            case 2 -> line("gui.logisticsnetworks.computer.telemetry.energy");
            case 3 -> line("gui.logisticsnetworks.computer.telemetry.chemicals");
            case 4 -> line("gui.logisticsnetworks.computer.telemetry.source");
            default -> line("gui.logisticsnetworks.computer.telemetry.items");
        };
    }

    private String resolveTypeUnit(int typeOrdinal) {
        return switch (typeOrdinal) {
            case 1 -> line("gui.logisticsnetworks.computer.telemetry.unit.fluids");
            case 2 -> line("gui.logisticsnetworks.computer.telemetry.unit.energy");
            case 3 -> line("gui.logisticsnetworks.computer.telemetry.unit.chemicals");
            case 4 -> line("gui.logisticsnetworks.computer.telemetry.unit.source");
            default -> line("gui.logisticsnetworks.computer.telemetry.unit.items");
        };
    }

    private void renderTelemetryGraph(GuiGraphics g, int x, int y, int w, int h,
            String label, long[] history, int writeIndex, int barColor, String unit) {
        g.fill(x, y, x + w, y + h, COLOR_PANEL_ALT);
        g.renderOutline(x, y, w, h, COLOR_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 11, COLOR_PANEL_HEADER);
        g.fill(x + 1, y + 11, x + w - 1, y + 12, COLOR_BORDER);

        int barAreaX = x + 4;
        int barAreaY = y + 14;
        int barAreaW = w - 8;
        int barAreaH = h - 18;

        int barW = 4;
        int barStep = barW + 1;
        int maxBars = Math.min(barAreaW / barStep, TelemetryManager.HISTORY_SIZE);

        long maxVal = 1;
        for (int i = 0; i < maxBars; i++) {
            int idx = ((writeIndex - 1 - i) % TelemetryManager.HISTORY_SIZE
                    + TelemetryManager.HISTORY_SIZE) % TelemetryManager.HISTORY_SIZE;
            maxVal = Math.max(maxVal, history[idx]);
        }

        for (int gy = barAreaY + 4; gy < barAreaY + barAreaH; gy += 6) {
            g.fill(barAreaX, gy, barAreaX + barAreaW, gy + 1, COLOR_GRAPH_GRID);
        }

        int newestIdx = ((writeIndex - 1) % TelemetryManager.HISTORY_SIZE
                + TelemetryManager.HISTORY_SIZE) % TelemetryManager.HISTORY_SIZE;
        long newestVal = history[newestIdx];

        for (int i = 0; i < maxBars; i++) {
            int idx = ((writeIndex - 1 - i) % TelemetryManager.HISTORY_SIZE
                    + TelemetryManager.HISTORY_SIZE) % TelemetryManager.HISTORY_SIZE;
            long val = history[idx];
            if (val <= 0) continue;

            int barH = (int) (((double) val / maxVal) * (barAreaH - 2));
            barH = Math.max(1, barH);
            int bx = barAreaX + barAreaW - barStep * (i + 1);
            if (bx < barAreaX) break;
            int by = barAreaY + barAreaH - barH;
            g.fill(bx, by, bx + barW, barAreaY + barAreaH, barColor);
            g.fill(bx, by, bx + barW, by + 1, COLOR_ACCENT);
        }

        g.drawString(font, label, x + 4, y + 2, COLOR_TEXT_SECONDARY);
        String valueText = formatThroughput(newestVal) + " " + unit;
        g.drawString(font, valueText, x + w - 4 - font.width(valueText), y + 2, barColor);
    }

    private String formatThroughput(long value) {
        if (value < 1000) return String.valueOf(value);
        if (value < 1000000) return String.format("%.1fK", value / 1000.0);
        return String.format("%.1fM", value / 1000000.0);
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
            PacketDistributor.sendToServer(new SetNetworkNodesVisibilityPayload(selectedNetworkId, true));
            setAllNodeVisibility(true);
            return true;
        }
        if (mouseX >= hideX && mouseX < hideX + VIS_BTN_W
                && mouseY >= buttonY && mouseY < buttonY + VIS_BTN_H) {
            PacketDistributor.sendToServer(new SetNetworkNodesVisibilityPayload(selectedNetworkId, false));
            setAllNodeVisibility(false);
            return true;
        }
        return false;
    }

    private void renderGroupedNodeMap(GuiGraphics g, int contentX, int contentY,
            int contentW, int contentH, int mouseX, int mouseY) {
        List<RenderEntry> renderEntries = buildNodeRenderEntries();

        int maxScroll = Math.max(0, renderEntries.size() - NODES_PER_PAGE);
        nodeMapScrollOffset = Math.max(0, Math.min(nodeMapScrollOffset, maxScroll));

        int headerX = contentX + 8;
        int headerY = contentY + 22;
        int headerW = contentW - 16;
        g.fill(headerX, headerY, headerX + headerW, headerY + 14, COLOR_PANEL_ALT);
        g.renderOutline(headerX, headerY, headerW, 14, COLOR_BORDER);
        g.drawString(font, label("gui.logisticsnetworks.computer.device"), headerX + 8, headerY + 3,
                COLOR_TEXT_SECONDARY);
        g.drawString(font, label("gui.logisticsnetworks.computer.location"), headerX + headerW - 72, headerY + 3,
                COLOR_TEXT_SECONDARY);

        int listY = contentY + 40;
        for (int i = 0; i < NODES_PER_PAGE && (i + nodeMapScrollOffset) < renderEntries.size(); i++) {
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
                        + trimText(entry.headerName, Math.max(0, countX - NODE_TEXT_GAP - (headerX + NODE_ROW_SIDE_PAD)));

                g.fill(headerX, entryY, headerX + headerW, entryY + NODE_ENTRY_HEIGHT - 2, COLOR_PANEL_ALT);
                g.renderOutline(headerX, entryY, headerW, NODE_ENTRY_HEIGHT - 2, COLOR_ACCENT_DARK);
                g.drawString(font, headerText, headerX + NODE_ROW_SIDE_PAD, entryY + 7, COLOR_ACCENT);
                g.drawString(font, countText, countX, entryY + 7, COLOR_TEXT_SECONDARY);
                renderSettingsButton(g, settingsX, buttonY, SETTINGS_BTN_W, SETTINGS_BTN_H, settingsHovered);
                renderToggleButton(g, buttonX, buttonY, HIGHLIGHT_BTN_W, HIGHLIGHT_BTN_H, buttonHovered, active);
                continue;
            }

            boolean hovered = mouseX >= headerX && mouseX < headerX + headerW
                    && mouseY >= entryY && mouseY < entryY + NODE_ENTRY_HEIGHT - 2;
            int bgColor = hovered ? COLOR_ROW_HOVER : COLOR_ROW;
            int borderColor = hovered ? COLOR_ACCENT_DARK : COLOR_BORDER;
            g.fill(headerX, entryY, headerX + headerW, entryY + NODE_ENTRY_HEIGHT - 2, bgColor);
            g.renderOutline(headerX, entryY, headerW, NODE_ENTRY_HEIGHT - 2, borderColor);

            if (entry.isGrouped) {
                g.fill(headerX + 1, entryY + 1, headerX + 3, entryY + NODE_ENTRY_HEIGHT - 3, COLOR_ACCENT_DARK);
            }

            boolean active = entry.nodeInfo.highlighted();
            int textX = headerX + (entry.isGrouped ? 16 : NODE_ROW_SIDE_PAD);

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

            String positionText = trimText(formatPosition(entry.nodeInfo), 72);
            int positionX = rightAnchor - NODE_TEXT_GAP - font.width(positionText);
            String blockLabel = trimText(resolveBlockLabel(entry.nodeInfo.blockName()),
                    Math.max(0, positionX - NODE_TEXT_GAP - textX));

            g.drawString(font, blockLabel, textX, entryY + 7, COLOR_TEXT);
            g.drawString(font, positionText, positionX, entryY + 7, COLOR_TEXT_SECONDARY);
            renderToggleButton(g, buttonX, buttonY, HIGHLIGHT_BTN_W, HIGHLIGHT_BTN_H, buttonHovered, active);
        }

        if (renderEntries.size() > NODES_PER_PAGE) {
            String scrollInfo = (nodeMapScrollOffset + 1) + "-"
                    + Math.min(nodeMapScrollOffset + NODES_PER_PAGE, renderEntries.size())
                    + " / " + renderEntries.size();
            g.drawString(font, scrollInfo, contentX + contentW - 12 - font.width(scrollInfo),
                    contentY + contentH - 14, COLOR_TEXT_MUTED);
        }
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
        super.render(g, mouseX, mouseY, partialTick);
        renderHighlightTooltip(g, mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        switch (currentPage) {
            case NETWORK_LIST -> {
                if (networkList.size() > NETWORKS_PER_PAGE) {
                    networkScrollOffset -= (int) scrollY;
                    int maxScroll = Math.max(0, networkList.size() - NETWORKS_PER_PAGE);
                    networkScrollOffset = Math.max(0, Math.min(networkScrollOffset, maxScroll));
                    return true;
                }
            }
            case IO_CHANNEL_LIST -> {
                if (channelList.size() > CHANNELS_PER_PAGE) {
                    channelListScrollOffset -= (int) scrollY;
                    int maxScroll = Math.max(0, channelList.size() - CHANNELS_PER_PAGE);
                    channelListScrollOffset = Math.max(0, Math.min(channelListScrollOffset, maxScroll));
                    return true;
                }
            }
            case NODE_MAP -> {
                if (!nodeInfoList.isEmpty()) {
                    List<RenderEntry> renderEntries = buildNodeRenderEntries();
                    if (renderEntries.size() > NODES_PER_PAGE) {
                        nodeMapScrollOffset -= (int) scrollY;
                        int maxScroll = Math.max(0, renderEntries.size() - NODES_PER_PAGE);
                        nodeMapScrollOffset = Math.max(0, Math.min(nodeMapScrollOffset, maxScroll));
                        return true;
                    }
                }
            }
            default -> {
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            switch (currentPage) {
                case NETWORK_LIST -> {
                    if (handleNetworkListClick(mouseX, mouseY)) {
                        return true;
                    }
                    if (selectedNetworkId != null && handleOptionButtonClick(mouseX, mouseY)) {
                        return true;
                    }
                }
                case IO_CHANNEL_LIST -> {
                    if (isBackButtonClicked(mouseX, mouseY)) {
                        currentPage = Page.NETWORK_LIST;
                        channelList.clear();
                        channelListScrollOffset = 0;
                        return true;
                    }
                    if (handleChannelListClick(mouseX, mouseY)) {
                        return true;
                    }
                }
                case IO_CHANNEL_GRAPH -> {
                    if (isBackButtonClicked(mouseX, mouseY)) {
                        unsubscribeTelemetry();
                        currentPage = Page.IO_CHANNEL_LIST;
                        channelListScrollOffset = 0;
                        PacketDistributor.sendToServer(new RequestChannelListPayload(selectedNetworkId));
                        return true;
                    }
                }
                case NODE_MAP -> {
                    if (isBackButtonClicked(mouseX, mouseY)) {
                        currentPage = Page.NETWORK_LIST;
                        nodeInfoList.clear();
                        nodeMapScrollOffset = 0;
                        return true;
                    }
                    if (handleVisibilityButtonClick(mouseX, mouseY)) {
                        return true;
                    }
                    if (handleNodeMapClick(mouseX, mouseY)) {
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleNetworkListClick(double mouseX, double mouseY) {
        for (int i = 0; i < NETWORKS_PER_PAGE && (i + networkScrollOffset) < networkList.size(); i++) {
            int index = i + networkScrollOffset;
            if (isHovering(NETWORK_LIST_X, NETWORK_LIST_Y + (i * NETWORK_ENTRY_HEIGHT),
                    NETWORK_LIST_WIDTH, NETWORK_ENTRY_HEIGHT - 2, (int) mouseX, (int) mouseY)) {
                SyncNetworkListPayload.NetworkEntry clickedEntry = networkList.get(index);
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

        int contentX = leftPos + 10;
        int contentY = topPos + 34;
        int contentW = imageWidth - 20;
        int headerX = contentX + 8;
        int headerW = contentW - 16;
        int listY = contentY + 40;
        List<RenderEntry> renderEntries = buildNodeRenderEntries();

        for (int i = 0; i < NODES_PER_PAGE && (i + nodeMapScrollOffset) < renderEntries.size(); i++) {
            int index = i + nodeMapScrollOffset;
            RenderEntry entry = renderEntries.get(index);
            int entryY = listY + (i * NODE_ENTRY_HEIGHT);
            int buttonX = headerX + headerW - NODE_ROW_SIDE_PAD - HIGHLIGHT_BTN_W;
            int buttonY = entryY + ((NODE_ENTRY_HEIGHT - HIGHLIGHT_BTN_H - 2) / 2);
            boolean buttonClicked = mouseX >= buttonX && mouseX < buttonX + HIGHLIGHT_BTN_W
                    && mouseY >= buttonY && mouseY < buttonY + HIGHLIGHT_BTN_H;

            if (entry.isHeader) {
                int settingsX = buttonX - SETTINGS_BTN_GAP - SETTINGS_BTN_W;
                boolean settingsClicked = mouseX >= settingsX && mouseX < settingsX + SETTINGS_BTN_W
                        && mouseY >= buttonY && mouseY < buttonY + SETTINGS_BTN_H;
                if (settingsClicked) {
                    openFirstNodeInLabel(entry.headerName);
                    return true;
                }
                if (buttonClicked) {
                    PacketDistributor.sendToServer(new ToggleNetworkLabelHighlightPayload(selectedNetworkId, entry.headerName));
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

            if (!entry.isGrouped) {
                int settingsX = buttonX - SETTINGS_BTN_GAP - SETTINGS_BTN_W;
                boolean settingsClicked = mouseX >= settingsX && mouseX < settingsX + SETTINGS_BTN_W
                        && mouseY >= buttonY && mouseY < buttonY + SETTINGS_BTN_H;
                if (settingsClicked) {
                    PacketDistributor.sendToServer(
                            new RequestOpenNodeSettingsPayload(selectedNetworkId, entry.nodeInfo.nodeId()));
                    return true;
                }
            }

            if (buttonClicked) {
                PacketDistributor.sendToServer(
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
                PacketDistributor.sendToServer(
                        new RequestOpenNodeSettingsPayload(selectedNetworkId, info.nodeId()));
                return;
            }
        }
    }

    private boolean handleOptionButtonClick(double mouseX, double mouseY) {
        int panelX = leftPos + DETAIL_PANEL_X;
        int panelY = topPos + DETAIL_PANEL_Y;
        int buttonX = panelX + 12;
        int buttonWidth = DETAIL_PANEL_WIDTH - 24;
        int button1Y = panelY + 112;
        int button2Y = button1Y + OPTION_BTN_HEIGHT + OPTION_BTN_GAP;

        if (mouseX >= buttonX && mouseX < buttonX + buttonWidth
                && mouseY >= button1Y && mouseY < button1Y + OPTION_BTN_HEIGHT) {
            currentPage = Page.IO_CHANNEL_LIST;
            channelListScrollOffset = 0;
            PacketDistributor.sendToServer(new RequestChannelListPayload(selectedNetworkId));
            return true;
        }

        if (mouseX >= buttonX && mouseX < buttonX + buttonWidth
                && mouseY >= button2Y && mouseY < button2Y + OPTION_BTN_HEIGHT) {
            currentPage = Page.NODE_MAP;
            nodeMapScrollOffset = 0;
            PacketDistributor.sendToServer(new RequestNetworkNodesPayload(selectedNetworkId));
            return true;
        }

        return false;
    }

    private boolean handleChannelListClick(double mouseX, double mouseY) {
        int contentX = leftPos + 10;
        int contentY = topPos + 34;
        int contentW = imageWidth - 20;
        int headerX = contentX + 8;
        int headerW = contentW - 16;
        int listY = contentY + 40;

        for (int i = 0; i < CHANNELS_PER_PAGE && (i + channelListScrollOffset) < channelList.size(); i++) {
            int index = i + channelListScrollOffset;
            int entryY = listY + (i * CHANNEL_ENTRY_HEIGHT);
            if (mouseX >= headerX && mouseX < headerX + headerW
                    && mouseY >= entryY && mouseY < entryY + CHANNEL_ENTRY_HEIGHT - 2) {
                SyncChannelListPayload.ChannelEntry entry = channelList.get(index);
                watchedChannelIndex = entry.channelIndex();
                watchedTypeOrdinal = entry.typeOrdinal();
                currentPage = Page.IO_CHANNEL_GRAPH;
                subscribeTelemetry();
                return true;
            }
        }
        return false;
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

        for (int i = 0; i < NODES_PER_PAGE && (i + nodeMapScrollOffset) < renderEntries.size(); i++) {
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

        for (int i = 0; i < NODES_PER_PAGE && (i + nodeMapScrollOffset) < renderEntries.size(); i++) {
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
        if (dimension.equals("overworld")) {
            dimension = line("gui.logisticsnetworks.computer.dimension.overworld");
        } else if (dimension.equals("the_nether")) {
            dimension = line("gui.logisticsnetworks.computer.dimension.nether");
        } else if (dimension.equals("the_end")) {
            dimension = line("gui.logisticsnetworks.computer.dimension.end");
        } else {
            dimension = trimText(dimension.toUpperCase(Locale.ROOT), 10);
        }
        return line("gui.logisticsnetworks.computer.position", dimension,
                nodeInfo.attachedPos().getX(),
                nodeInfo.attachedPos().getY(),
                nodeInfo.attachedPos().getZ());
    }

    public void receiveNetworkList(List<SyncNetworkListPayload.NetworkEntry> networks) {
        System.out.println("[ComputerScreen] Received network list with " + networks.size() + " entries");
        for (SyncNetworkListPayload.NetworkEntry entry : networks) {
            System.out.println("[ComputerScreen]   - " + entry.name() + " (" + entry.nodeCount() + " nodes)");
        }
        this.networkList = new ArrayList<>(networks);
        this.networkScrollOffset = Math.min(this.networkScrollOffset,
                Math.max(0, networkList.size() - NETWORKS_PER_PAGE));
        System.out.println("[ComputerScreen] Network list updated, now have " + this.networkList.size() + " networks");
    }

    public void receiveNetworkNodes(UUID networkId, List<SyncNetworkNodesPayload.NodeInfo> nodes) {
        if (networkId.equals(selectedNetworkId)) {
            this.nodeInfoList = new ArrayList<>(nodes);
            this.nodeMapScrollOffset = 0;
        }
    }

    public void receiveChannelList(UUID networkId, List<SyncChannelListPayload.ChannelEntry> channels) {
        if (networkId.equals(selectedNetworkId)) {
            this.channelList = new ArrayList<>(channels);
            this.channelListScrollOffset = Math.min(this.channelListScrollOffset,
                    Math.max(0, channelList.size() - CHANNELS_PER_PAGE));
        }
    }

    public void receiveTelemetry(SyncTelemetryPayload payload) {
        if (payload.networkId().equals(selectedNetworkId)
                && payload.channelIndex() == watchedChannelIndex) {
            this.telemetryHistory = payload.history();
            this.telemetryIndex = payload.historyIndex();
        }
    }

    private void subscribeTelemetry() {
        if (!telemetrySubscribed && selectedNetworkId != null) {
            telemetryHistory = new long[TelemetryManager.HISTORY_SIZE];
            telemetryIndex = 0;
            PacketDistributor.sendToServer(new SubscribeTelemetryPayload(
                    selectedNetworkId, true, watchedChannelIndex));
            telemetrySubscribed = true;
        }
    }

    private void unsubscribeTelemetry() {
        if (telemetrySubscribed && selectedNetworkId != null) {
            PacketDistributor.sendToServer(new SubscribeTelemetryPayload(
                    selectedNetworkId, false, watchedChannelIndex));
            telemetrySubscribed = false;
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
