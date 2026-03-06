package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.menu.ComputerMenu;
import me.almana.logisticsnetworks.network.SyncNetworkListPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ComputerScreen extends AbstractContainerScreen<ComputerMenu> {

    // Constants
    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 240;
    private static final int NETWORKS_PER_PAGE = 8;
    private static final int NETWORK_ENTRY_HEIGHT = 24;
    private static final int NETWORK_LIST_X = 8;
    private static final int NETWORK_LIST_Y = 30;
    private static final int NETWORK_LIST_WIDTH = 120;
    private static final int DIVIDER_X = 132;
    private static final int DETAIL_PANEL_X = 136;
    private static final int DETAIL_PANEL_Y = 30;
    private static final int DETAIL_PANEL_WIDTH = 176;
    private static final int DETAIL_PANEL_HEIGHT = 190;
    private static final int NODE_LIST_Y = 40;
    private static final int NODE_ENTRY_HEIGHT = 18;
    private static final int NODES_PER_PAGE = 9;

    // Colors
    private static final int COLOR_BG = 0xC0101010;
    private static final int COLOR_PANEL = 0xFF2B2B2B;
    private static final int COLOR_HOVER = 0xFF3B3B3B;
    private static final int COLOR_BORDER = 0xFF8B8B8B;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_SECONDARY = 0xFFAAAAAA;
    private static final int COLOR_SLOT_BG = 0xFF1A1A1A;

    private List<SyncNetworkListPayload.NetworkEntry> networkList = new ArrayList<>();
    private int networkScrollOffset = 0;
    private UUID selectedNetworkId = null;
    private int nodeScrollOffset = 0;

    public ComputerScreen(ComputerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.inventoryLabelY = 10000; // Hide player inventory label
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // Main background
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, COLOR_BG);
        g.renderOutline(leftPos, topPos, imageWidth, imageHeight, COLOR_BORDER);

        // Title
        Component title = Component.translatable("container.logisticsnetworks.computer");
        int titleX = leftPos + (imageWidth / 2) - (font.width(title) / 2);
        g.drawString(font, title, titleX, topPos + 8, COLOR_TEXT);

        // Wrench slot background (top right)
        int slotX = leftPos + 292 - 1;
        int slotY = topPos + 8 - 1;
        g.fill(slotX, slotY, slotX + 18, slotY + 18, COLOR_SLOT_BG);
        g.renderOutline(slotX, slotY, 18, 18, COLOR_BORDER);

        // Network list
        renderNetworkList(g, mouseX, mouseY);

        // Vertical divider line
        int dividerX = leftPos + DIVIDER_X;
        g.fill(dividerX, topPos + NETWORK_LIST_Y, dividerX + 1, topPos + imageHeight - 10, COLOR_BORDER);

        // Detail panel
        if (selectedNetworkId != null) {
            renderDetailPanel(g, mouseX, mouseY);
        }
    }

    private void renderNetworkList(GuiGraphics g, int mouseX, int mouseY) {
        int startX = leftPos + NETWORK_LIST_X;
        int startY = topPos + NETWORK_LIST_Y;

        // Network list header
        g.drawString(font, "Your Networks:", startX, startY - 12, COLOR_TEXT);

        if (networkList.isEmpty()) {
            // No networks message
            Component noNetworks = Component.translatable("gui.logisticsnetworks.computer.no_networks");
            g.drawString(font, noNetworks, startX + 4, startY + 10, COLOR_TEXT_SECONDARY);
            return;
        }

        // Render visible networks
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

        // Pagination info
        if (networkList.size() > NETWORKS_PER_PAGE) {
            int currentPage = (networkScrollOffset / NETWORKS_PER_PAGE) + 1;
            int totalPages = (int) Math.ceil((double) networkList.size() / NETWORKS_PER_PAGE);
            String pageInfo = "Page " + currentPage + "/" + totalPages;
            int pageInfoX = leftPos + (imageWidth / 2) - (font.width(pageInfo) / 2);
            g.drawString(font, pageInfo, pageInfoX, topPos + imageHeight - 15, COLOR_TEXT_SECONDARY);
        }
    }

    private void renderNetworkEntry(GuiGraphics g, SyncNetworkListPayload.NetworkEntry entry,
                                     int x, int y, int width, boolean hovered) {
        // Background
        boolean selected = entry.id().equals(selectedNetworkId);
        int bgColor = selected ? 0xFF4A4A4A : (hovered ? COLOR_HOVER : COLOR_PANEL);
        g.fill(x, y, x + width, y + NETWORK_ENTRY_HEIGHT, bgColor);
        g.renderOutline(x, y, width, NETWORK_ENTRY_HEIGHT, selected ? 0xFFFFFFFF : COLOR_BORDER);

        // Network name (top)
        String name = entry.name();
        if (font.width(name) > width - 8) {
            name = font.plainSubstrByWidth(name, width - 13) + "...";
        }
        g.drawString(font, name, x + 4, y + 4, COLOR_TEXT);

        // Node count (bottom) - just the number
        String nodeCount = String.valueOf(entry.nodeCount());
        g.drawString(font, nodeCount, x + 4, y + 13, COLOR_TEXT_SECONDARY);
    }

    private void renderDetailPanel(GuiGraphics g, int mouseX, int mouseY) {
        int panelX = leftPos + DETAIL_PANEL_X;
        int panelY = topPos + DETAIL_PANEL_Y;

        // Background
        g.fill(panelX, panelY, panelX + DETAIL_PANEL_WIDTH, panelY + DETAIL_PANEL_HEIGHT, COLOR_PANEL);
        g.renderOutline(panelX, panelY, DETAIL_PANEL_WIDTH, DETAIL_PANEL_HEIGHT, COLOR_BORDER);

        // Find selected network
        SyncNetworkListPayload.NetworkEntry selectedNetwork = null;
        for (SyncNetworkListPayload.NetworkEntry entry : networkList) {
            if (entry.id().equals(selectedNetworkId)) {
                selectedNetwork = entry;
                break;
            }
        }

        if (selectedNetwork == null) {
            return;
        }

        // Header
        String header = "Nodes in " + selectedNetwork.name();
        if (font.width(header) > DETAIL_PANEL_WIDTH - 16) {
            header = font.plainSubstrByWidth(header, DETAIL_PANEL_WIDTH - 21) + "...";
        }
        g.drawString(font, header, panelX + 8, panelY + 8, COLOR_TEXT);

        // Node list
        int nodeCount = selectedNetwork.nodeCount();
        if (nodeCount == 0) {
            String noNodes = "No nodes in this network";
            int noNodesX = panelX + (DETAIL_PANEL_WIDTH / 2) - (font.width(noNodes) / 2);
            g.drawString(font, noNodes, noNodesX, panelY + 90, COLOR_TEXT_SECONDARY);
            return;
        }

        // Render node entries with scrolling
        int maxScroll = Math.max(0, nodeCount - NODES_PER_PAGE);
        nodeScrollOffset = Math.max(0, Math.min(nodeScrollOffset, maxScroll));

        int nodeListStartY = panelY + NODE_LIST_Y;
        for (int i = 0; i < NODES_PER_PAGE && (i + nodeScrollOffset) < nodeCount; i++) {
            int index = i + nodeScrollOffset;
            int entryY = nodeListStartY + (i * NODE_ENTRY_HEIGHT);

            // Node entry background
            boolean hovered = mouseX >= panelX + 8 && mouseX < panelX + DETAIL_PANEL_WIDTH - 8 &&
                             mouseY >= entryY && mouseY < entryY + NODE_ENTRY_HEIGHT - 2;
            int bgColor = hovered ? COLOR_HOVER : 0xFF1F1F1F;
            g.fill(panelX + 8, entryY, panelX + DETAIL_PANEL_WIDTH - 8, entryY + NODE_ENTRY_HEIGHT - 2, bgColor);

            // Node info (placeholder - will be replaced with actual node data)
            String nodeInfo = "Node " + (index + 1);
            g.drawString(font, nodeInfo, panelX + 12, entryY + 5, COLOR_TEXT);
        }

        // Scroll indicator
        if (nodeCount > NODES_PER_PAGE) {
            String scrollInfo = (nodeScrollOffset + 1) + "-" +
                               Math.min(nodeScrollOffset + NODES_PER_PAGE, nodeCount) +
                               " of " + nodeCount;
            int scrollInfoX = panelX + DETAIL_PANEL_WIDTH - font.width(scrollInfo) - 8;
            g.drawString(font, scrollInfo, scrollInfoX, panelY + 22, COLOR_TEXT_SECONDARY);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Check if scrolling in detail panel
        if (selectedNetworkId != null) {
            int panelX = leftPos + DETAIL_PANEL_X;
            int panelY = topPos + DETAIL_PANEL_Y;
            if (mouseX >= panelX && mouseX < panelX + DETAIL_PANEL_WIDTH &&
                mouseY >= panelY && mouseY < panelY + DETAIL_PANEL_HEIGHT) {
                // Find selected network to get node count
                for (SyncNetworkListPayload.NetworkEntry entry : networkList) {
                    if (entry.id().equals(selectedNetworkId)) {
                        int nodeCount = entry.nodeCount();
                        if (nodeCount > NODES_PER_PAGE) {
                            nodeScrollOffset -= (int) scrollY;
                            int maxScroll = Math.max(0, nodeCount - NODES_PER_PAGE);
                            nodeScrollOffset = Math.max(0, Math.min(nodeScrollOffset, maxScroll));
                            return true;
                        }
                        break;
                    }
                }
            }
        }

        // Otherwise scroll network list
        if (networkList.size() > NETWORKS_PER_PAGE) {
            networkScrollOffset -= (int) scrollY;
            int maxScroll = Math.max(0, networkList.size() - NETWORKS_PER_PAGE);
            networkScrollOffset = Math.max(0, Math.min(networkScrollOffset, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left click
            // Check if clicked on a network entry
            for (int i = 0; i < NETWORKS_PER_PAGE && (i + networkScrollOffset) < networkList.size(); i++) {
                int index = i + networkScrollOffset;
                if (isHovering(NETWORK_LIST_X, NETWORK_LIST_Y + (i * NETWORK_ENTRY_HEIGHT),
                        NETWORK_LIST_WIDTH, NETWORK_ENTRY_HEIGHT - 2, (int) mouseX, (int) mouseY)) {
                    SyncNetworkListPayload.NetworkEntry clickedEntry = networkList.get(index);
                    // Toggle selection
                    if (clickedEntry.id().equals(selectedNetworkId)) {
                        selectedNetworkId = null;
                    } else {
                        selectedNetworkId = clickedEntry.id();
                        nodeScrollOffset = 0; // Reset node scroll when selecting a new network
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
}
