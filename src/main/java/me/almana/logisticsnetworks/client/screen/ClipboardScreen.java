package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.client.ClientControls;
import me.almana.logisticsnetworks.client.ClientInput;
import me.almana.logisticsnetworks.client.GuiGraphics;
import me.almana.logisticsnetworks.client.LegacyContainerScreen;
import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.client.theme.ThemeState;
import me.almana.logisticsnetworks.client.theme.Themes;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.DistributionMode;
import me.almana.logisticsnetworks.data.FilterMode;
import me.almana.logisticsnetworks.data.NetworkColors;
import me.almana.logisticsnetworks.data.NodeClipboardConfig;
import me.almana.logisticsnetworks.data.RedstoneMode;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import me.almana.logisticsnetworks.filter.ModFilterData;
import me.almana.logisticsnetworks.filter.NameFilterData;
import me.almana.logisticsnetworks.filter.VirtualFilterType;
import me.almana.logisticsnetworks.integration.ars.ArsCompat;
import me.almana.logisticsnetworks.integration.guideme.GuideMeCompat;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.menu.ClipboardMenu;
import me.almana.logisticsnetworks.network.RequestChannelListPayload;
import me.almana.logisticsnetworks.network.RequestNetworkLabelsPayload;
import me.almana.logisticsnetworks.network.SetComputerWrenchClipboardPayload;
import me.almana.logisticsnetworks.network.SyncNetworkListPayload;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.registration.Registration;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class ClipboardScreen extends LegacyContainerScreen<ClipboardMenu> implements VirtualFilterTarget {

    private enum Page {
        NETWORK_SELECT, CHANNEL_CONFIG
    }

    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 298;
    private static final int INV_X = 47;
    private static final int INV_Y = 218;
    private static final int NETWORKS_PER_PAGE = 4;
    private static final int FILTER_PICKER_W = 56;
    private static final int FILTER_PICKER_ROW_H = 13;
    private static final int LABEL_PICKER_ENTRY_H = 14;
    private static final int LABEL_PICKER_MAX_VISIBLE = 5;
    private static final int TWEAKS_W = 164;
    private static final int TWEAKS_H = 150;

    private final Runnable themeListener = this::rebuildPageLayout;
    private Page currentPage = Page.CHANNEL_CONFIG;
    private int selectedChannel;
    private boolean initialized;
    private boolean tweaksOpen;

    private EditBox networkNameField;
    private List<SyncNetworkListPayload.NetworkEntry> networkList = new ArrayList<>();
    private int networkScrollOffset;
    private String lastNetworkFilter = "";
    @Nullable
    private UUID pendingNetworkId;
    private String pendingNetworkName = "";
    private boolean switchAfterChannelSync;

    private boolean labelPickerOpen;
    private EditBox labelEditBox;
    private List<String> networkLabels = new ArrayList<>();
    private int labelScrollOffset;

    private boolean channelNameEditing;
    private EditBox channelNameEditBox;
    private int editingChannelIndex = -1;
    private Component hoveredChannelName;
    private long lastTabClickTime;
    private int lastTabClickIndex = -1;

    private int editingRow = -1;
    private EditBox numericEditBox;
    private long lastSettingClickTime;
    private int lastSettingClickRow = -1;

    private boolean filterPickerOpen;
    private int filterPickerSlot = -1;
    private boolean filterDisabledHover;
    private boolean filterAddHover;
    private long filterAddedToastUntil;

    public ClipboardScreen(ClipboardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, GUI_WIDTH, GUI_HEIGHT);
        inventoryLabelY = 10_000;
        titleLabelY = 10_000;
    }

    private Theme theme() {
        return ThemeState.active();
    }

    private int cText() {
        return theme().text();
    }

    private int cMuted() {
        return theme().textMuted();
    }

    private int cSubtle() {
        return theme().textSubtle();
    }

    private int cAccent() {
        return theme().accent();
    }

    private int cDanger() {
        return theme().danger();
    }

    private int cInfo() {
        return theme().info();
    }

    private int cHover() {
        return 0x22FFFFFF;
    }

    private NodeClipboardConfig config() {
        return menu.getClipboard();
    }

    @Override
    protected void init() {
        super.init();
        leftPos = (width - GUI_WIDTH) / 2;
        topPos = (height - GUI_HEIGHT) / 2;
        selectedChannel = menu.getSelectedChannel();
        if (!initialized) {
            initialized = true;
            ThemeState.addListener(themeListener);
            UUID networkId = config().getNetworkId();
            if (networkId != null) {
                pendingNetworkId = networkId;
                pendingNetworkName = Objects.requireNonNullElse(config().getNetworkName(), "");
                ClientPacketDistributor.sendToServer(new RequestChannelListPayload(networkId));
            }
        }
        rebuildPageLayout();
    }

    private void rebuildPageLayout() {
        if (!initialized || font == null) return;
        menu.setUpgradeSlotsVisible(currentPage == Page.CHANNEL_CONFIG);
        stopNumericEdit(false);
        stopChannelNameEdit(false);
        closeLabelPicker();
        closeFilterPicker();
        clearWidgets();
        networkNameField = null;
        if (currentPage == Page.NETWORK_SELECT) {
            networkNameField = new EditBox(font, leftPos + 53, topPos + 31, 150, 16, Component.empty());
            networkNameField.setMaxLength(32);
            networkNameField.setHint(Component.translatable("gui.logisticsnetworks.node.network_search_hint"));
            addRenderableWidget(networkNameField);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        ThemePaint.window(g, leftPos, topPos, GUI_WIDTH, GUI_HEIGHT, theme());
        if (currentPage == Page.NETWORK_SELECT) renderNetworkPage(g, mouseX, mouseY);
        else renderChannelPage(g, mouseX, mouseY);

        int separatorY = topPos + INV_Y - 18;
        ThemePaint.divider(g, leftPos + 6, separatorY, GUI_WIDTH - 12, theme());
        g.drawString(font, Component.translatable("gui.logisticsnetworks.node.inventory"),
                leftPos + INV_X, topPos + INV_Y - 12, cSubtle(), false);
        renderPlayerSlots(g);
        if (currentPage == Page.CHANNEL_CONFIG) {
            renderUtilityButtons(g, mouseX, mouseY);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, tweaksOpen ? Integer.MIN_VALUE : mouseX, tweaksOpen ? Integer.MIN_VALUE : mouseY, partialTick);
        if (labelPickerOpen) renderLabelPicker(g, mouseX, mouseY, partialTick);
        if (filterPickerOpen) renderFilterPicker(g, mouseX, mouseY);
        if (tweaksOpen) renderTweaksPanel(g, mouseX, mouseY);
        if (!tweaksOpen) {
            renderTooltip(g, mouseX, mouseY);
            if (hoveredChannelName != null) g.renderTooltip(font, hoveredChannelName, mouseX, mouseY);
            if (filterDisabledHover) {
                g.renderTooltip(font, Component.translatable("gui.logisticsnetworks.node.filter.unfilterable"),
                        mouseX, mouseY);
            } else if (filterAddHover) {
                g.renderTooltip(font, Component.translatable("gui.logisticsnetworks.node.filter.add.hint",
                        ClientControls.PRIMARY_INTERACTION.getTranslatedKeyMessage()), mouseX, mouseY);
            } else if (System.currentTimeMillis() < filterAddedToastUntil) {
                g.renderTooltip(font, Component.translatable("gui.logisticsnetworks.node.filter.add.done")
                        .withStyle(ChatFormatting.GREEN), mouseX, mouseY);
            } else {
                renderFilterPreview(g, mouseX, mouseY);
            }
        }
    }

    private void renderNetworkPage(GuiGraphics g, int mouseX, int mouseY) {
        int center = leftPos + GUI_WIDTH / 2;
        ThemePaint.drawCentered(g, font, Component.translatable("gui.logisticsnetworks.select_network"),
                center, topPos + 8, cAccent());
        drawButton(g, leftPos + 14, topPos + 53, 108, 16,
                tr("gui.logisticsnetworks.node.network.none"), mouseX, mouseY);
        drawButton(g, leftPos + 134, topPos + 53, 108, 16,
                tr("gui.logisticsnetworks.create_network"), mouseX, mouseY);
        ThemePaint.divider(g, leftPos + 12, topPos + 76, GUI_WIDTH - 24, theme());
        g.drawString(font, Component.translatable("gui.logisticsnetworks.node.existing_networks"),
                leftPos + 14, topPos + 82, cSubtle(), false);

        List<SyncNetworkListPayload.NetworkEntry> filtered = filteredNetworks();
        int end = Math.min(filtered.size(), networkScrollOffset + NETWORKS_PER_PAGE);
        if (filtered.isEmpty()) {
            ThemePaint.drawCentered(g, font, Component.translatable("gui.logisticsnetworks.no_networks"),
                    center, topPos + 110, cSubtle());
        }
        for (int index = networkScrollOffset; index < end; index++) {
            SyncNetworkListPayload.NetworkEntry entry = filtered.get(index);
            int y = topPos + 95 + (index - networkScrollOffset) * 20;
            boolean hovered = isInside(leftPos + 14, y, GUI_WIDTH - 28, 17, mouseX, mouseY);
            g.fill(leftPos + 14, y, leftPos + GUI_WIDTH - 14, y + 17,
                    hovered ? theme().borderStrong() : theme().surface2());
            g.renderOutline(leftPos + 14, y, GUI_WIDTH - 28, 17,
                    hovered ? cAccent() : theme().border());
            g.fill(leftPos + 19, y + 5, leftPos + 27, y + 13, 0xFF000000 | entry.color());
            g.drawString(font, clip(entry.name(), 130), leftPos + 31, y + 4,
                    hovered ? cText() : cMuted(), false);
            String nodes = tr("gui.logisticsnetworks.node.network_nodes", entry.nodeCount());
            g.drawString(font, nodes, leftPos + GUI_WIDTH - 19 - font.width(nodes), y + 4,
                    hovered ? cText() : cSubtle(), false);
        }
        if (filtered.size() > NETWORKS_PER_PAGE) {
            String page = tr("gui.logisticsnetworks.node.page_info",
                    networkScrollOffset + 1, end, filtered.size());
            ThemePaint.drawCentered(g, font, page, center,
                    topPos + 95 + NETWORKS_PER_PAGE * 20 + 4, cSubtle());
        }
    }

    private void renderChannelPage(GuiGraphics g, int mouseX, int mouseY) {
        NodeClipboardConfig config = config();
        String networkName = networkDisplayName();
        int nameWidth = font.width(networkName);
        int chipX = leftPos + (GUI_WIDTH - nameWidth - 12) / 2;
        int swatch = networkColor();
        g.fill(chipX - 11, topPos + 5, chipX - 3, topPos + 13, 0xFF000000 | swatch);
        g.renderOutline(chipX - 11, topPos + 5, 8, 8, theme().border());
        ThemePaint.chip(g, font, chipX, topPos + 4, networkName, theme());

        String visibility = visibilityLabel(config.isRenderVisible());
        int visibilityWidth = font.width(visibility) + 16;
        ThemePaint.visibleToggle(g, font, leftPos + 8, topPos + 4, visibilityWidth, 12,
                visibility, config.isRenderVisible(),
                isInside(leftPos + 8, topPos + 4, visibilityWidth, 12, mouseX, mouseY), theme());
        ThemePaint.ghostButton(g, font, leftPos + GUI_WIDTH - 52, topPos + 4, 44, 12,
                tr("gui.logisticsnetworks.node.change_network"),
                isInside(leftPos + GUI_WIDTH - 52, topPos + 4, 44, 12, mouseX, mouseY), theme());

        drawChannelTabs(g, mouseX, mouseY);
        drawNodeLabel(g, mouseX, mouseY);
        drawSettings(g, mouseX, mouseY);
        drawFiltersAndUpgrades(g, mouseX, mouseY);
    }

    private void drawChannelTabs(GuiGraphics g, int mouseX, int mouseY) {
        hoveredChannelName = null;
        for (int channel = 0; channel < 9; channel++) {
            int x = leftPos + 10 + channel * 26;
            boolean selected = channel == selectedChannel;
            boolean enabled = config().isChannelEnabled(channel);
            boolean hovered = isInside(x, topPos + 22, 24, 12, mouseX, mouseY);
            ThemePaint.channelTab(g, font, x, topPos + 22, 24, 12, String.valueOf(channel),
                    config().getChannelType(channel), selected, enabled && !selected, hovered, theme());
            if (selected && !enabled) {
                ThemePaint.roundOutline(g, x, topPos + 22, 24, 12, 2, cDanger(), theme().sharpCorners());
            }
            if (hovered && !channelNameEditing) {
                String name = config().getChannelName(channel);
                if (!name.isEmpty()) hoveredChannelName = Component.literal(name);
                else if (!hasLiveNetwork()) hoveredChannelName = Component.translatable(
                        "gui.logisticsnetworks.node.channel_name.set_tooltip",
                        ClientControls.PRIMARY_INTERACTION.getTranslatedKeyMessage());
            }
        }
    }

    private void drawNodeLabel(GuiGraphics g, int mouseX, int mouseY) {
        if (channelNameEditing && channelNameEditBox != null) {
            ThemePaint.panel(g, channelNameEditBox.getX() - 2, channelNameEditBox.getY() - 2,
                    channelNameEditBox.getWidth() + 4, channelNameEditBox.getHeight() + 4, theme());
            return;
        }
        String label = config().getNodeLabel().isEmpty()
                ? tr("gui.logisticsnetworks.node.label.set") : config().getNodeLabel();
        int width = font.width(label) + 14;
        int x = leftPos + 10 + (148 - width) / 2;
        ThemePaint.setLabelBtn(g, font, x, topPos + 40, width, 12, label,
                !labelPickerOpen && isInside(x, topPos + 40, width, 12, mouseX, mouseY), theme());
    }

    private void drawSettings(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + 10;
        int y = topPos + 58;
        int width = 148;
        int rowHeight = 14;
        ThemePaint.panel(g, x, y, width, rowHeight * 9 + 4, theme());
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
                config().isChannelEnabled(selectedChannel) ? tr("gui.logisticsnetworks.node.value.enabled")
                        : tr("gui.logisticsnetworks.node.value.disabled"),
                channelModeLabel(config().getChannelMode(selectedChannel)),
                channelTypeLabel(config().getChannelType(selectedChannel)),
                directionLabel(config().getChannelDirection(selectedChannel)),
                redstoneLabel(config().getChannelRedstoneMode(selectedChannel)),
                distributionLabel(config().getChannelDistributionMode(selectedChannel)),
                editingRow == 6 ? "" : String.valueOf(config().getChannelPriority(selectedChannel)),
                editingRow == 7 ? "" : batchLabel(),
                editingRow == 8 ? "" : tr("gui.logisticsnetworks.node.value.tick_delay",
                        config().getChannelTickDelay(selectedChannel))
        };
        Theme.Variant[] variants = {
                config().isChannelEnabled(selectedChannel) ? Theme.Variant.ACCENT : Theme.Variant.NEUTRAL,
                config().getChannelMode(selectedChannel) == ChannelMode.EXPORT
                        ? Theme.Variant.WARN : Theme.Variant.ACCENT,
                typeVariant(config().getChannelType(selectedChannel)),
                Theme.Variant.NEUTRAL,
                config().getChannelRedstoneMode(selectedChannel) == RedstoneMode.HIGH
                        ? Theme.Variant.ACCENT : Theme.Variant.WARN,
                distributionVariant(config().getChannelDistributionMode(selectedChannel)),
                Theme.Variant.NEUTRAL, Theme.Variant.NEUTRAL, Theme.Variant.NEUTRAL
        };
        for (int row = 0; row < 9; row++) {
            drawSettingRow(g, x + 2, y + 2 + row * rowHeight, width - 4, rowHeight,
                    labels[row], values[row], variants[row], row, mouseX, mouseY, !isSettingDisabled(row));
        }
    }

    private void drawSettingRow(GuiGraphics g, int x, int y, int width, int height, String label,
            String value, Theme.Variant variant, int row, int mouseX, int mouseY, boolean enabled) {
        if (enabled && editingRow < 0 && isInside(x, y, width, height, mouseX, mouseY)) {
            g.fill(x, y, x + width, y + height, cHover());
        }
        int valueWidth = value.isEmpty() ? 0 : row >= 3 && row != 4 && row != 5
                ? font.width(value) + 8 : ThemePaint.pillWidth(font, value);
        int maxLabel = width - valueWidth - 10;
        g.drawString(font, font.plainSubstrByWidth(label, Math.max(0, maxLabel)), x + 4, y + 3,
                enabled ? cMuted() : cSubtle(), false);
        if (row < 8) g.fill(x + 2, y + height - 1, x + width - 2, y + height, theme().border());
        if (value.isEmpty()) return;
        if (!enabled) {
            g.drawString(font, value, x + width - font.width(value) - 4, y + 3, cSubtle(), false);
        } else if (row >= 3 && row != 4 && row != 5) {
            int valueX = x + width - valueWidth - 3;
            g.fill(valueX, y + 2, valueX + valueWidth, y + 11, theme().surfaceSunken());
            g.drawString(font, value, valueX + 4, y + 3, cText(), false);
        } else {
            ThemePaint.pill(g, font, x + width - valueWidth - 3, y + 2, value, variant, false, theme());
        }
    }

    private void drawFiltersAndUpgrades(GuiGraphics g, int mouseX, int mouseY) {
        filterDisabledHover = false;
        filterAddHover = false;
        int x = leftPos + 168;
        int y = topPos + 56;
        String label = tr("gui.logisticsnetworks.node.filters");
        g.drawString(font, label, x, y, cMuted(), false);
        String mode = filterModeLabel(config().getChannelFilterMode(selectedChannel));
        int modeX = x + font.width(label) + 4;
        int modeWidth = font.width(mode) + 10;
        ThemePaint.button(g, font, modeX, y - 1, modeWidth, 10, mode,
                isInside(modeX, y - 1, modeWidth, 10, mouseX, mouseY), theme());

        boolean filterable = isFilterable();
        int gridY = y + 12;
        ThemePaint.sunkPanel(g, x - 2, gridY - 2, 60, 40, theme());
        for (int slot = 0; slot < ChannelData.FILTER_SIZE; slot++) {
            int buttonX = filterButtonX(slot);
            int buttonY = filterButtonY(slot);
            boolean hovered = filterable && !filterPickerOpen && !labelPickerOpen
                    && isInside(buttonX - 1, buttonY - 1, 18, 18, mouseX, mouseY);
            ItemStack filter = config().getFilterItem(selectedChannel, slot);
            ThemePaint.button(g, font, buttonX - 1, buttonY - 1, 18, 18,
                    filterable ? filterButtonText(filter) : "", hovered, theme());
            if (hovered && !menu.getCarried().isEmpty() && !menu.getCarried().is(ModTags.FILTERS)
                    && isFilterSlotItemAddable(slot)) filterAddHover = true;
        }
        if (!filterable) {
            g.fill(x - 2, gridY - 2, x + 58, gridY + 38, 0x99202020);
            filterDisabledHover = isInside(x - 2, gridY - 2, 60, 40, mouseX, mouseY);
        }

        int upgradeY = gridY + 40;
        g.drawString(font, Component.translatable("gui.logisticsnetworks.node.upgrades"),
                x, upgradeY, cMuted(), false);
        ThemePaint.sunkPanel(g, x - 2, upgradeY + 8, 41, 40, theme());
        for (int slot = 0; slot < 4; slot++) {
            ThemePaint.slot(g, leftPos + 167 + (slot % 2) * 19,
                    topPos + 117 + (slot / 2) * 19, 18, theme());
        }
    }

    private void renderPlayerSlots(GuiGraphics g) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                ThemePaint.slot(g, leftPos + INV_X + col * 18 - 1,
                        topPos + INV_Y + row * 18 - 1, 18, theme());
            }
        }
        for (int col = 0; col < 9; col++) {
            ThemePaint.slot(g, leftPos + INV_X + col * 18 - 1, topPos + INV_Y + 57, 18, theme());
        }
    }

    private void renderUtilityButtons(GuiGraphics g, int mouseX, int mouseY) {
        int y = topPos + INV_Y - 36;
        ThemePaint.ghostButton(g, font, leftPos + 168, topPos + 166, 38, 10,
                tr("gui.logisticsnetworks.clipboard.clear"),
                isInside(leftPos + 168, topPos + 166, 38, 10, mouseX, mouseY), theme());
        ThemePaint.ghostButton(g, font, leftPos + GUI_WIDTH - 100, y, 38, 10,
                tr("gui.logisticsnetworks.node.docs"),
                isInside(leftPos + GUI_WIDTH - 100, y, 38, 10, mouseX, mouseY), theme());
        ThemePaint.ghostButton(g, font, leftPos + GUI_WIDTH - 56, y, 48, 10,
                tr("gui.logisticsnetworks.node.tweaks"),
                isInside(leftPos + GUI_WIDTH - 56, y, 48, 10, mouseX, mouseY), theme());
    }

    private void drawButton(GuiGraphics g, int x, int y, int width, int height,
            String label, int mouseX, int mouseY) {
        ThemePaint.button(g, font, x, y, width, height, label,
                isInside(x, y, width, height, mouseX, mouseY), theme());
    }

    private void renderLabelPicker(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int width = labelPickerWidth();
        int x = leftPos + 10 + (148 - width) / 2;
        int y = topPos + 58;
        int entries = Math.min(networkLabels.size(), LABEL_PICKER_MAX_VISIBLE);
        int height = 22 + entries * LABEL_PICKER_ENTRY_H + 18;
        if (labelEditBox != null) {
            labelEditBox.setX(x + 2);
            labelEditBox.setWidth(width - 4);
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        ThemePaint.panel(g, x, y, width, height, theme());
        if (labelEditBox != null) labelEditBox.extractRenderState(g.raw(), mouseX, mouseY, partialTick);
        for (int row = 0; row < entries; row++) {
            String label = networkLabels.get(row + labelScrollOffset);
            int rowY = y + 22 + row * LABEL_PICKER_ENTRY_H;
            if (isInside(x + 2, rowY, width - 4, LABEL_PICKER_ENTRY_H, mouseX, mouseY)) {
                g.fill(x + 2, rowY, x + width - 2, rowY + LABEL_PICKER_ENTRY_H, cHover());
            }
            ThemePaint.drawCentered(g, font, clip(label, width - 10), x + width / 2, rowY + 3, cInfo());
        }
        int clearY = y + 22 + entries * LABEL_PICKER_ENTRY_H + 2;
        ThemePaint.ghostButton(g, font, x + 4, clearY, width - 8, 12,
                tr("gui.logisticsnetworks.node.label.clear"),
                isInside(x + 4, clearY, width - 8, 12, mouseX, mouseY), theme());
        g.pose().popPose();
    }

    private void renderFilterPicker(GuiGraphics g, int mouseX, int mouseY) {
        int x = filterPickerX();
        int y = filterPickerY();
        String[] labels = {
                tr("gui.logisticsnetworks.node.filter.pick.normal"),
                tr("gui.logisticsnetworks.node.filter.pick.mod"),
                tr("gui.logisticsnetworks.node.filter.pick.regex")
        };
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        ThemePaint.panel(g, x, y, FILTER_PICKER_W, FILTER_PICKER_ROW_H * 3, theme());
        for (int row = 0; row < labels.length; row++) {
            int rowY = y + row * FILTER_PICKER_ROW_H;
            if (isInside(x, rowY, FILTER_PICKER_W, FILTER_PICKER_ROW_H, mouseX, mouseY)) {
                g.fill(x, rowY, x + FILTER_PICKER_W, rowY + FILTER_PICKER_ROW_H, cHover());
            }
            g.drawString(font, labels[row], x + 4, rowY + 3, cInfo(), false);
        }
        g.pose().popPose();
    }

    private void renderTweaksPanel(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + (GUI_WIDTH - TWEAKS_W) / 2;
        int y = topPos + (GUI_HEIGHT - TWEAKS_H) / 2;
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        ThemePaint.modalVeil(g, leftPos, topPos, GUI_WIDTH, GUI_HEIGHT, theme());
        ThemePaint.window(g, x, y, TWEAKS_W, TWEAKS_H, theme());
        g.drawString(font, tr("gui.logisticsnetworks.node.tweaks"), x + 10, y + 8, cMuted(), false);
        g.drawString(font, "\u00D7", x + TWEAKS_W - 12, y + 7, cText(), false);
        ThemePaint.divider(g, x + 8, y + 20, TWEAKS_W - 16, theme());
        g.drawString(font, tr("gui.logisticsnetworks.node.tweaks.theme"), x + 10, y + 26, cSubtle(), false);
        int swatchWidth = (TWEAKS_W - 24) / 2;
        for (int index = 0; index < Themes.ALL.size(); index++) {
            Theme preview = Themes.ALL.get(index);
            int sx = x + 10 + (index % 2) * (swatchWidth + 4);
            int sy = y + 36 + (index / 2) * 26;
            ThemePaint.swatchPreview(g, sx, sy, swatchWidth, 12, preview, theme());
            ThemePaint.drawCentered(g, font, preview.label(), sx + swatchWidth / 2, sy + 13,
                    preview.id().equals(theme().id()) ? cAccent() : cMuted());
        }
        g.pose().popPose();
    }

    private void renderFilterPreview(GuiGraphics g, int mouseX, int mouseY) {
        if (currentPage != Page.CHANNEL_CONFIG || labelPickerOpen || filterPickerOpen || !isFilterable()) return;
        for (int slot = 0; slot < ChannelData.FILTER_SIZE; slot++) {
            if (!isInside(filterButtonX(slot) - 1, filterButtonY(slot) - 1, 18, 18, mouseX, mouseY)) continue;
            ItemStack filter = config().getFilterItem(selectedChannel, slot);
            if (isFilterButtonEmpty(filter)) return;
            if (FilterItemData.isFilterItem(filter)) {
                g.renderTooltip(font, Component.translatable("tooltip.logisticsnetworks.filter.entries",
                        FilterItemData.getEntryCount(filter), FilterItemData.getCapacity(filter)), mouseX, mouseY);
            } else if (NameFilterData.isNameFilter(filter)) {
                g.renderTooltip(font, Component.translatable("tooltip.logisticsnetworks.filter.name",
                        NameFilterData.getNameFilter(filter)), mouseX, mouseY);
            } else if (ModFilterData.isModFilter(filter)) {
                g.renderTooltip(font, Component.translatable("tooltip.logisticsnetworks.filter.mod",
                        String.join(", ", ModFilterData.getModFilters(filter))), mouseX, mouseY);
            }
            return;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int action = ClientControls.resolveMouseAction(mouseX, mouseY, button);
        if (action != -1 && handleInteraction(mouseX, mouseY, action)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleInteraction(double mouseX, double mouseY, int action) {
        if (action != 0 && action != 1) return false;
        if (tweaksOpen) return action != 0 || handleTweaksClick(mouseX, mouseY);
        if (editingRow >= 0 && numericEditBox != null && !numericEditBox.isMouseOver(mouseX, mouseY)) {
            stopNumericEdit(true);
        }
        if (channelNameEditing && channelNameEditBox != null
                && !channelNameEditBox.isMouseOver(mouseX, mouseY)) stopChannelNameEdit(true);
        if (currentPage == Page.CHANNEL_CONFIG && labelPickerOpen) {
            if (handleLabelPickerClick(mouseX, mouseY)) return true;
            closeLabelPicker();
            return true;
        }
        if (currentPage == Page.CHANNEL_CONFIG && filterPickerOpen) {
            if (handleFilterPickerClick(mouseX, mouseY)) return true;
            closeFilterPicker();
            return true;
        }
        if (isHoveringMenuSlot(mouseX, mouseY)) return false;
        return currentPage == Page.NETWORK_SELECT
                ? handleNetworkClick(mouseX, mouseY, action)
                : handleChannelClick(mouseX, mouseY, action);
    }

    private boolean handleNetworkClick(double mouseX, double mouseY, int action) {
        if (action != 0) return false;
        if (isInside(leftPos + 14, topPos + 53, 108, 16, mouseX, mouseY)) {
            pendingNetworkId = null;
            switchAfterChannelSync = false;
            config().setNetworkTarget(null, null);
            commit();
            currentPage = Page.CHANNEL_CONFIG;
            rebuildPageLayout();
            return true;
        }
        if (isInside(leftPos + 134, topPos + 53, 108, 16, mouseX, mouseY)) {
            pendingNetworkId = null;
            switchAfterChannelSync = false;
            String name = networkNameField == null ? "" : networkNameField.getValue().trim();
            config().setNetworkTarget(null,
                    name.isEmpty() ? tr("gui.logisticsnetworks.node.network.unnamed") : name);
            commit();
            currentPage = Page.CHANNEL_CONFIG;
            rebuildPageLayout();
            return true;
        }
        List<SyncNetworkListPayload.NetworkEntry> filtered = filteredNetworks();
        int end = Math.min(filtered.size(), networkScrollOffset + NETWORKS_PER_PAGE);
        for (int index = networkScrollOffset; index < end; index++) {
            int y = topPos + 95 + (index - networkScrollOffset) * 20;
            if (!isInside(leftPos + 14, y, GUI_WIDTH - 28, 17, mouseX, mouseY)) continue;
            SyncNetworkListPayload.NetworkEntry entry = filtered.get(index);
            pendingNetworkId = entry.id();
            pendingNetworkName = entry.name();
            switchAfterChannelSync = true;
            ClientPacketDistributor.sendToServer(new RequestChannelListPayload(entry.id()));
            return true;
        }
        return false;
    }

    private boolean handleChannelClick(double mouseX, double mouseY, int action) {
        String visibility = visibilityLabel(config().isRenderVisible());
        if (isInside(leftPos + 8, topPos + 4, font.width(visibility) + 16, 12, mouseX, mouseY)) {
            config().setRenderVisible(!config().isRenderVisible());
            commit();
            return true;
        }
        if (isInside(leftPos + GUI_WIDTH - 52, topPos + 4, 44, 12, mouseX, mouseY)) {
            currentPage = Page.NETWORK_SELECT;
            rebuildPageLayout();
            return true;
        }
        int utilityY = topPos + INV_Y - 36;
        if (action == 0 && isInside(leftPos + 168, topPos + 166, 38, 10, mouseX, mouseY)) {
            config().clear();
            commit();
            return true;
        }
        if (action == 0 && isInside(leftPos + GUI_WIDTH - 100, utilityY, 38, 10, mouseX, mouseY)) {
            GuideMeCompat.openGuide(minecraft.player,
                    Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "guide"));
            return true;
        }
        if (action == 0 && isInside(leftPos + GUI_WIDTH - 56, utilityY, 48, 10, mouseX, mouseY)) {
            tweaksOpen = true;
            return true;
        }

        if (!channelNameEditing) {
            String label = config().getNodeLabel().isEmpty()
                    ? tr("gui.logisticsnetworks.node.label.set") : config().getNodeLabel();
            int labelWidth = font.width(label) + 14;
            int labelX = leftPos + 10 + (148 - labelWidth) / 2;
            if (isInside(labelX, topPos + 40, labelWidth, 12, mouseX, mouseY)) {
                openLabelPicker();
                return true;
            }
        }

        for (int channel = 0; channel < 9; channel++) {
            if (!isInside(leftPos + 10 + channel * 26, topPos + 22, 24, 12, mouseX, mouseY)) continue;
            if (channel == selectedChannel && !hasLiveNetwork() && checkTabDoubleClick(channel)) {
                startChannelNameEdit(channel);
            } else {
                selectedChannel = channel;
                menu.setSelectedChannel(channel);
                sendButton(ClipboardMenu.ID_SELECT_CHANNEL_BASE + channel);
            }
            return true;
        }

        if (isFilterable()) {
            for (int slot = 0; slot < ChannelData.FILTER_SIZE; slot++) {
                if (!isInside(filterButtonX(slot) - 1, filterButtonY(slot) - 1, 18, 18, mouseX, mouseY)) continue;
                ItemStack carried = menu.getCarried();
                if (!carried.isEmpty() && !carried.is(ModTags.FILTERS)) {
                    addItemToFilterSlot(slot, carried);
                    return true;
                }
                ItemStack filter = config().getFilterItem(selectedChannel, slot);
                if (isFilterButtonEmpty(filter)) {
                    if (action == 0) openFilterPicker(slot);
                } else if (action == 1) {
                    config().setFilterItem(selectedChannel, slot, ItemStack.EMPTY);
                    commit();
                } else {
                    sendButton(ClipboardMenu.filterButtonId(slot, VirtualFilterType.EXISTING));
                }
                return true;
            }
        }
        return handleSettingsClick(mouseX, mouseY, action);
    }

    private boolean handleSettingsClick(double mouseX, double mouseY, int action) {
        int x = leftPos + 12;
        int y = topPos + 60;
        for (int row = 0; row < 9; row++) {
            if (!isInside(x, y + row * 14, 144, 14, mouseX, mouseY)) continue;
            if (isSettingDisabled(row)) return true;
            if (row >= 6 && ClientControls.modifier3Down()) {
                setNumericExtremum(row, action == 0);
            } else if (row >= 6 && checkSettingDoubleClick(row)) {
                startNumericEdit(row, x + 74, y + row * 14);
                return true;
            } else {
                cycleSetting(row, action == 0 ? 1 : -1);
            }
            commit();
            return true;
        }
        String label = tr("gui.logisticsnetworks.node.filters");
        String mode = filterModeLabel(config().getChannelFilterMode(selectedChannel));
        int modeX = leftPos + 168 + font.width(label) + 4;
        int modeWidth = font.width(mode) + 10;
        if (isInside(modeX, topPos + 55, modeWidth, 10, mouseX, mouseY)) {
            config().setChannelFilterMode(selectedChannel,
                    cycle(config().getChannelFilterMode(selectedChannel), action == 0 ? 1 : -1));
            commit();
            return true;
        }
        return false;
    }

    private void cycleSetting(int row, int direction) {
        switch (row) {
            case 0 -> config().setChannelEnabled(selectedChannel, !config().isChannelEnabled(selectedChannel));
            case 1 -> config().setChannelMode(selectedChannel,
                    cycle(config().getChannelMode(selectedChannel), direction));
            case 2 -> config().setChannelType(selectedChannel, cycleChannelType(direction));
            case 3 -> config().setChannelDirection(selectedChannel,
                    cycleDirection(config().getChannelDirection(selectedChannel), direction));
            case 4 -> config().setChannelRedstoneMode(selectedChannel,
                    cycle(config().getChannelRedstoneMode(selectedChannel), direction));
            case 5 -> config().setChannelDistributionMode(selectedChannel,
                    cycle(config().getChannelDistributionMode(selectedChannel), direction));
            case 6 -> config().setChannelPriority(selectedChannel,
                    config().getChannelPriority(selectedChannel) + direction * (ClientControls.modifier1Down() ? 10 : 1));
            case 7 -> config().setChannelBatchSize(selectedChannel,
                    config().getChannelBatchSize(selectedChannel) + direction * (ClientControls.modifier1Down() ? 8 : 1));
            case 8 -> config().setChannelTickDelay(selectedChannel,
                    config().getChannelTickDelay(selectedChannel) + direction * (ClientControls.modifier1Down() ? 10 : 1));
        }
    }

    private ChannelType cycleChannelType(int direction) {
        ChannelType current = config().getChannelType(selectedChannel);
        ChannelType[] values = ChannelType.values();
        int index = current.ordinal();
        for (int count = 0; count < values.length; count++) {
            index = (index + direction + values.length) % values.length;
            ChannelType candidate = values[index];
            if (candidate == ChannelType.CHEMICAL
                    && (!MekanismCompat.isLoaded() || !hasUpgrade(Registration.MEKANISM_CHEMICAL_UPGRADE.get()))) continue;
            if (candidate == ChannelType.SOURCE
                    && (!ArsCompat.isLoaded() || !hasUpgrade(Registration.ARS_SOURCE_UPGRADE.get()))) continue;
            return candidate;
        }
        return current;
    }

    private boolean hasUpgrade(net.minecraft.world.item.Item upgrade) {
        for (int slot = 0; slot < config().getUpgradeSlotCount(); slot++) {
            if (config().getUpgradeItem(slot).is(upgrade)) return true;
        }
        return false;
    }

    private boolean isSettingDisabled(int row) {
        if (config().getChannelMode(selectedChannel) == ChannelMode.IMPORT) {
            return row == 5 || row == 7 || row == 8;
        }
        return config().getChannelType(selectedChannel) == ChannelType.ENERGY && row == 8;
    }

    private void setNumericExtremum(int row, boolean maximum) {
        if (row == 6) config().setChannelPriority(selectedChannel, maximum ? 99 : -99);
        else if (row == 7) config().setChannelBatchSize(selectedChannel,
                maximum ? NodeUpgradeData.getOperationCap(config().getChannelType(selectedChannel),
                        config().getUpgradeTier()) : 1);
        else if (row == 8) config().setChannelTickDelay(selectedChannel, maximum ? 10_000 : 1);
    }

    private void startNumericEdit(int row, int x, int y) {
        stopNumericEdit(false);
        editingRow = row;
        int value = row == 6 ? config().getChannelPriority(selectedChannel)
                : row == 7 ? config().getChannelBatchSize(selectedChannel)
                : config().getChannelTickDelay(selectedChannel);
        numericEditBox = new EditBox(font, x, y, 70, 11, Component.empty());
        numericEditBox.setMaxLength(10);
        numericEditBox.setValue(String.valueOf(value));
        numericEditBox.setTextColor(cText());
        numericEditBox.setFocused(true);
        addRenderableWidget(numericEditBox);
        setFocused(numericEditBox);
    }

    private void stopNumericEdit(boolean save) {
        if (numericEditBox == null) return;
        if (save) {
            try {
                int value = Integer.parseInt(numericEditBox.getValue().trim());
                if (editingRow == 6) config().setChannelPriority(selectedChannel, value);
                else if (editingRow == 7) config().setChannelBatchSize(selectedChannel, value);
                else if (editingRow == 8) config().setChannelTickDelay(selectedChannel, value);
                commit();
            } catch (NumberFormatException ignored) {
            }
        }
        removeWidget(numericEditBox);
        numericEditBox = null;
        editingRow = -1;
    }

    private void startChannelNameEdit(int channel) {
        stopChannelNameEdit(false);
        channelNameEditing = true;
        editingChannelIndex = channel;
        int tabX = leftPos + 10 + channel * 26;
        int editX = Math.max(leftPos + 4, Math.min(tabX - 20, leftPos + GUI_WIDTH - 84));
        channelNameEditBox = new FlatEditBox(font, editX, topPos + 40, 80, 12, Component.empty());
        channelNameEditBox.setMaxLength(24);
        channelNameEditBox.setValue(config().getChannelName(channel));
        channelNameEditBox.setTextColor(cText());
        channelNameEditBox.setFocused(true);
        addRenderableWidget(channelNameEditBox);
        setFocused(channelNameEditBox);
    }

    private void stopChannelNameEdit(boolean save) {
        if (channelNameEditBox == null) return;
        if (save && !hasLiveNetwork()) {
            config().setChannelName(editingChannelIndex, channelNameEditBox.getValue());
            commit();
        }
        removeWidget(channelNameEditBox);
        channelNameEditBox = null;
        channelNameEditing = false;
        editingChannelIndex = -1;
    }

    private void openLabelPicker() {
        closeLabelPicker();
        labelPickerOpen = true;
        int width = labelPickerWidth();
        int x = leftPos + 10 + (148 - width) / 2;
        labelEditBox = new EditBox(font, x + 2, topPos + 60, width - 4, 16, Component.empty());
        labelEditBox.setMaxLength(48);
        labelEditBox.setValue(config().getNodeLabel());
        labelEditBox.setTextColor(cText());
        labelEditBox.setFocused(true);
        addRenderableWidget(labelEditBox);
        setFocused(labelEditBox);
        if (config().getNetworkId() != null) {
            ClientPacketDistributor.sendToServer(new RequestNetworkLabelsPayload(config().getNetworkId()));
        }
    }

    private void closeLabelPicker() {
        labelPickerOpen = false;
        if (labelEditBox != null) removeWidget(labelEditBox);
        labelEditBox = null;
        networkLabels.clear();
        labelScrollOffset = 0;
    }

    private boolean handleLabelPickerClick(double mouseX, double mouseY) {
        int width = labelPickerWidth();
        int x = leftPos + 10 + (148 - width) / 2;
        int y = topPos + 58;
        int entries = Math.min(networkLabels.size(), LABEL_PICKER_MAX_VISIBLE);
        int height = 22 + entries * LABEL_PICKER_ENTRY_H + 18;
        if (!isInside(x, y, width, height, mouseX, mouseY)) return false;
        for (int row = 0; row < entries; row++) {
            int rowY = y + 22 + row * LABEL_PICKER_ENTRY_H;
            if (isInside(x + 2, rowY, width - 4, LABEL_PICKER_ENTRY_H, mouseX, mouseY)) {
                commitNodeLabel(networkLabels.get(row + labelScrollOffset));
                return true;
            }
        }
        int clearY = y + 22 + entries * LABEL_PICKER_ENTRY_H + 2;
        if (isInside(x + 4, clearY, width - 8, 12, mouseX, mouseY)) {
            commitNodeLabel("");
        }
        return true;
    }

    private void commitNodeLabel(String label) {
        config().setNodeLabel(label);
        commit();
        closeLabelPicker();
    }

    private void openFilterPicker(int slot) {
        filterPickerOpen = true;
        filterPickerSlot = slot;
    }

    private void closeFilterPicker() {
        filterPickerOpen = false;
        filterPickerSlot = -1;
    }

    private boolean handleFilterPickerClick(double mouseX, double mouseY) {
        VirtualFilterType[] types = {VirtualFilterType.SMALL, VirtualFilterType.MOD, VirtualFilterType.NAME};
        for (int row = 0; row < types.length; row++) {
            if (isInside(filterPickerX(), filterPickerY() + row * FILTER_PICKER_ROW_H,
                    FILTER_PICKER_W, FILTER_PICKER_ROW_H, mouseX, mouseY)) {
                sendButton(ClipboardMenu.filterButtonId(filterPickerSlot, types[row]));
                closeFilterPicker();
                return true;
            }
        }
        return false;
    }

    public int getSelectedChannel() {
        return selectedChannel;
    }

    public int getFilterSlotCount() {
        return ChannelData.FILTER_SIZE;
    }

    public boolean isFilterSlotItemAddable(int slot) {
        if (currentPage != Page.CHANNEL_CONFIG || !isFilterable()
                || slot < 0 || slot >= ChannelData.FILTER_SIZE) return false;
        ItemStack filter = config().getFilterItem(selectedChannel, slot);
        return filter.isEmpty() || FilterItemData.isFilterItem(filter);
    }

    public Rect2i getFilterSlotArea(int slot) {
        return new Rect2i(filterButtonX(slot) - 1, filterButtonY(slot) - 1, 18, 18);
    }

    public void addItemToFilterSlot(int slot, ItemStack item) {
        if (!isFilterSlotItemAddable(slot) || item.isEmpty() || item.is(ModTags.FILTERS)) return;
        ItemStack filter = config().getFilterItem(selectedChannel, slot);
        if (filter.isEmpty()) {
            filter = VirtualFilterType.SMALL.createStack();
            FilterItemData.setTargetType(filter, FilterTargetType.forChannel(config().getChannelType(selectedChannel)));
        } else {
            filter = filter.copy();
        }
        if (!FilterItemData.addItem(filter, item, minecraft.level.registryAccess())) return;
        config().setFilterItem(selectedChannel, slot, filter);
        commit();
        filterAddedToastUntil = System.currentTimeMillis() + 1500;
    }

    public void receiveNetworkList(List<SyncNetworkListPayload.NetworkEntry> networks) {
        networkList = new ArrayList<>(networks);
        UUID current = config().getNetworkId();
        if (current == null) return;
        for (SyncNetworkListPayload.NetworkEntry entry : networks) {
            if (!entry.id().equals(current)) continue;
            config().setNetworkTarget(current, entry.name());
            if (Objects.equals(pendingNetworkId, current)) pendingNetworkName = entry.name();
            return;
        }
    }

    public void receiveNetworkLabels(List<String> labels) {
        networkLabels = new ArrayList<>(labels);
        labelScrollOffset = 0;
    }

    public void receiveChannelList(UUID networkId, List<String> channelNames) {
        if (!Objects.equals(pendingNetworkId, networkId)) return;
        config().setNetworkTarget(networkId, pendingNetworkName);
        for (int channel = 0; channel < 9; channel++) {
            config().setChannelName(channel, channel < channelNames.size() ? channelNames.get(channel) : "");
        }
        pendingNetworkId = null;
        commit();
        if (switchAfterChannelSync) {
            switchAfterChannelSync = false;
            currentPage = Page.CHANNEL_CONFIG;
            rebuildPageLayout();
        }
    }

    private void commit() {
        if (minecraft == null || minecraft.level == null) return;
        ClientPacketDistributor.sendToServer(new SetComputerWrenchClipboardPayload(
                config().save(minecraft.level.registryAccess())));
    }

    private void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == 256) {
            if (tweaksOpen) {
                tweaksOpen = false;
                return true;
            }
            if (channelNameEditing) {
                stopChannelNameEdit(false);
                return true;
            }
            if (editingRow >= 0) {
                stopNumericEdit(false);
                return true;
            }
            if (labelPickerOpen) {
                closeLabelPicker();
                return true;
            }
            if (filterPickerOpen) {
                closeFilterPicker();
                return true;
            }
            if (currentPage == Page.NETWORK_SELECT) {
                if (switchAfterChannelSync) {
                    pendingNetworkId = null;
                    switchAfterChannelSync = false;
                }
                currentPage = Page.CHANNEL_CONFIG;
                rebuildPageLayout();
                return true;
            }
            return super.keyPressed(key, scanCode, modifiers);
        }
        if (channelNameEditBox != null) {
            if (key == 257 || key == 335) stopChannelNameEdit(true);
            else channelNameEditBox.keyPressed(ClientInput.key(key, scanCode, modifiers));
            return true;
        }
        if (numericEditBox != null) {
            if (key == 257 || key == 335) stopNumericEdit(true);
            else numericEditBox.keyPressed(ClientInput.key(key, scanCode, modifiers));
            return true;
        }
        if (labelEditBox != null) {
            if (key == 257 || key == 335) commitNodeLabel(labelEditBox.getValue());
            else labelEditBox.keyPressed(ClientInput.key(key, scanCode, modifiers));
            return true;
        }
        if (networkNameField != null && networkNameField.isFocused()) {
            networkNameField.keyPressed(ClientInput.key(key, scanCode, modifiers));
            return true;
        }
        int action = ClientControls.resolveKeyAction(key, scanCode, modifiers);
        if (action != -1) {
            handleInteraction(ClientControls.cursorX(minecraft), ClientControls.cursorY(minecraft), action);
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (channelNameEditBox != null) return channelNameEditBox.charTyped(ClientInput.character(codePoint));
        if (numericEditBox != null) {
            return (Character.isDigit(codePoint) || codePoint == '-')
                    && numericEditBox.charTyped(ClientInput.character(codePoint));
        }
        if (labelEditBox != null) return labelEditBox.charTyped(ClientInput.character(codePoint));
        if (networkNameField != null && networkNameField.isFocused()) {
            return networkNameField.charTyped(ClientInput.character(codePoint));
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (labelPickerOpen && networkLabels.size() > LABEL_PICKER_MAX_VISIBLE) {
            int max = networkLabels.size() - LABEL_PICKER_MAX_VISIBLE;
            labelScrollOffset = Math.clamp(labelScrollOffset + (scrollY < 0 ? 1 : -1), 0, max);
            return true;
        }
        if (currentPage == Page.NETWORK_SELECT) {
            int max = Math.max(0, filteredNetworks().size() - NETWORKS_PER_PAGE);
            networkScrollOffset = Math.clamp(networkScrollOffset + (scrollY < 0 ? 1 : -1), 0, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean handleTweaksClick(double mouseX, double mouseY) {
        int x = leftPos + (GUI_WIDTH - TWEAKS_W) / 2;
        int y = topPos + (GUI_HEIGHT - TWEAKS_H) / 2;
        if (!isInside(x, y, TWEAKS_W, TWEAKS_H, mouseX, mouseY)
                || isInside(x + TWEAKS_W - 14, y + 6, 10, 10, mouseX, mouseY)) {
            tweaksOpen = false;
            return true;
        }
        int swatchWidth = (TWEAKS_W - 24) / 2;
        for (int index = 0; index < Themes.ALL.size(); index++) {
            int sx = x + 10 + (index % 2) * (swatchWidth + 4);
            int sy = y + 36 + (index / 2) * 26;
            if (isInside(sx, sy, swatchWidth, 22, mouseX, mouseY)) {
                ThemeState.setTheme(Themes.ALL.get(index));
                return true;
            }
        }
        return true;
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

    private List<SyncNetworkListPayload.NetworkEntry> filteredNetworks() {
        String filter = networkNameField == null ? "" : networkNameField.getValue().trim().toLowerCase(Locale.ROOT);
        if (!filter.equals(lastNetworkFilter)) {
            lastNetworkFilter = filter;
            networkScrollOffset = 0;
        }
        if (filter.isEmpty()) return networkList;
        return networkList.stream().filter(entry -> entry.name().toLowerCase(Locale.ROOT).contains(filter)).toList();
    }

    private int labelPickerWidth() {
        int width = 90;
        for (String label : networkLabels) width = Math.max(width, font.width(label) + 24);
        return Math.min(144, width);
    }

    private int filterButtonX(int slot) {
        return leftPos + 168 + (slot % 3) * 19;
    }

    private int filterButtonY(int slot) {
        return topPos + 68 + (slot / 3) * 19;
    }

    private int filterPickerX() {
        return Math.min(filterButtonX(filterPickerSlot), leftPos + GUI_WIDTH - 6 - FILTER_PICKER_W);
    }

    private int filterPickerY() {
        return filterButtonY(filterPickerSlot) + 18;
    }

    private boolean isFilterable() {
        ChannelType type = config().getChannelType(selectedChannel);
        return type != ChannelType.ENERGY && type != ChannelType.SOURCE;
    }

    private String filterButtonText(ItemStack stack) {
        if (isFilterButtonEmpty(stack)) return "+";
        return switch (VirtualFilterType.fromStack(stack)) {
            case MOD -> "Mo";
            case NAME -> "Rx";
            default -> "N";
        };
    }

    private boolean isFilterButtonEmpty(ItemStack stack) {
        if (stack.isEmpty()) return true;
        return switch (VirtualFilterType.fromStack(stack)) {
            case EXISTING -> false;
            case SMALL, MEDIUM, BIG -> !FilterItemData.hasAnyEntries(stack);
            case MOD -> !ModFilterData.hasAnyMods(stack);
            case NAME -> !NameFilterData.hasNameFilter(stack);
        };
    }

    private boolean hasLiveNetwork() {
        return config().getNetworkId() != null;
    }

    private String networkDisplayName() {
        String name = config().getNetworkName();
        return clip(name == null || name.isBlank()
                ? tr("gui.logisticsnetworks.node.network.none") : name, GUI_WIDTH - 140);
    }

    private int networkColor() {
        UUID id = config().getNetworkId();
        if (id == null) return NetworkColors.DEFAULT;
        return networkList.stream().filter(entry -> entry.id().equals(id))
                .map(SyncNetworkListPayload.NetworkEntry::color).findFirst().orElse(NetworkColors.DEFAULT);
    }

    private String batchLabel() {
        int value = config().getChannelBatchSize(selectedChannel);
        return switch (config().getChannelType(selectedChannel)) {
            case FLUID -> tr("gui.logisticsnetworks.node.value.batch.fluid", value);
            case ENERGY -> tr("gui.logisticsnetworks.node.value.batch.energy", value);
            case CHEMICAL -> tr("gui.logisticsnetworks.node.value.batch.chemical", value);
            case SOURCE -> tr("gui.logisticsnetworks.node.value.batch.source", value);
            default -> String.valueOf(value);
        };
    }

    private Theme.Variant typeVariant(ChannelType type) {
        return switch (type) {
            case ITEM -> Theme.Variant.ACCENT;
            case FLUID, SOURCE -> Theme.Variant.INFO;
            case ENERGY -> Theme.Variant.WARN;
            case CHEMICAL -> Theme.Variant.NEUTRAL;
        };
    }

    private Theme.Variant distributionVariant(DistributionMode mode) {
        return switch (mode) {
            case PRIORITY -> Theme.Variant.INFO;
            case ROUND_ROBIN -> Theme.Variant.ACCENT;
            case NEAREST_FIRST, FARTHEST_FIRST -> Theme.Variant.WARN;
        };
    }

    private boolean checkTabDoubleClick(int tab) {
        long now = System.currentTimeMillis();
        boolean result = lastTabClickIndex == tab && now - lastTabClickTime < 250;
        lastTabClickIndex = tab;
        lastTabClickTime = now;
        return result;
    }

    private boolean checkSettingDoubleClick(int row) {
        long now = System.currentTimeMillis();
        boolean result = lastSettingClickRow == row && now - lastSettingClickTime < 250;
        lastSettingClickRow = row;
        lastSettingClickTime = now;
        return result;
    }

    private boolean isHoveringMenuSlot(double mouseX, double mouseY) {
        for (Slot slot : menu.slots) {
            if (slot.isActive() && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) return true;
        }
        return false;
    }

    private boolean isInside(double x, double y, double width, double height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private String clip(String value, int width) {
        if (font.width(value) <= width) return value;
        return font.plainSubstrByWidth(value, Math.max(0, width - font.width("..."))) + "...";
    }

    private <T extends Enum<T>> T cycle(T value, int direction) {
        T[] values = value.getDeclaringClass().getEnumConstants();
        return values[(value.ordinal() + direction + values.length) % values.length];
    }

    private @Nullable Direction cycleDirection(@Nullable Direction direction, int step) {
        int index = direction == null ? 6 : direction.ordinal();
        index = (index + step + 7) % 7;
        return index < 6 ? Direction.values()[index] : null;
    }

    private String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private String visibilityLabel(boolean visible) {
        return tr(visible ? "gui.logisticsnetworks.node.visibility.visible"
                : "gui.logisticsnetworks.node.visibility.hidden");
    }

    private String channelModeLabel(ChannelMode mode) {
        return tr("gui.logisticsnetworks.channel_mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    private String channelTypeLabel(ChannelType type) {
        return tr("gui.logisticsnetworks.channel_type." + type.name().toLowerCase(Locale.ROOT));
    }

    private String redstoneLabel(RedstoneMode mode) {
        return tr("gui.logisticsnetworks.redstone_mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    private String distributionLabel(DistributionMode mode) {
        return tr("gui.logisticsnetworks.distribution_mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    private String directionLabel(@Nullable Direction direction) {
        return tr("gui.logisticsnetworks.direction."
                + (direction == null ? "all" : direction.getName().toLowerCase(Locale.ROOT)));
    }

    private String filterModeLabel(FilterMode mode) {
        return tr(mode == FilterMode.MATCH_ALL
                ? "gui.logisticsnetworks.filter_mode.match_all"
                : "gui.logisticsnetworks.filter_mode.match_any");
    }
}
