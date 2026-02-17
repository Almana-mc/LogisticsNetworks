package me.almana.logisticsnetworks.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;

import me.almana.logisticsnetworks.filter.DurabilityFilterData;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import me.almana.logisticsnetworks.filter.NbtFilterData;
import me.almana.logisticsnetworks.filter.SlotFilterData;

import me.almana.logisticsnetworks.menu.FilterMenu;
import me.almana.logisticsnetworks.network.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public class FilterScreen extends AbstractContainerScreen<FilterMenu> {

    // Layout Constants
    private static final int GUI_WIDTH = 176;
    private static final int FILTER_SLOT_SIZE = 18;

    // Control Constants
    private static final int LIST_ROW_H = 12;
    private static final int DROPDOWN_ROWS = 6;

    // Colors
    private static final int COL_BG = 0xFF1A1A1A;
    private static final int COL_BORDER = 0xFF333333;
    private static final int COL_ACCENT = 0xFF44BB44;
    private static final int COL_WHITE = 0xFFFFFFFF;
    private static final int COL_GRAY = 0xFF999999;
    private static final int COL_HOVER = 0x33FFFFFF;
    private static final int COL_SELECTED = 0xFF2A4A2A;
    private static final int COL_BTN_BG = 0xFF2A2A2A;
    private static final int COL_BTN_HOVER = 0xFF3A3A3A;
    private static final int COL_BTN_BORDER = 0xFF4A4A4A;

    // State
    private EditBox manualInputBox;
    private boolean isDropdownOpen = false;
    private int listScrollOffset = 0;
    private boolean slotInfoOpen = false;
    private int slotInfoPage = 0;
    private boolean amountInfoOpen = false;
    private int amountInfoPage = 0;
    private boolean flushedTextOnClose = false;
    private boolean wasManualInputFocused = false;

    // Cached Data
    private List<String> cachedTags = new ArrayList<>();
    private List<String> cachedMods = new ArrayList<>();
    private List<NbtFilterData.NbtEntry> cachedNbtEntries = new ArrayList<>();

    // Animation
    private int textTick = 0;

    public FilterScreen(FilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = Math.max(166, menu.getPlayerInventoryY() + 83);
        this.inventoryLabelY = menu.getPlayerInventoryY() - 10;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - imageHeight) / 2;

        setupInputBox();
        refreshFilterData();
    }

    private void setupInputBox() {
        int w = 120;
        int h = 14;
        manualInputBox = new EditBox(font, leftPos + 28, topPos + 40, w, h, Component.empty());
        manualInputBox.setMaxLength(256);
        manualInputBox.setVisible(false);
        manualInputBox.setBordered(true);
        manualInputBox.setTextColor(COL_WHITE);
        addRenderableWidget(manualInputBox);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        textTick++;
        if (textTick > 10000)
            textTick = 0;

        refreshFilterData();

        if (menu.isTagMode() || menu.isModMode()) {
            manualInputBox.setVisible(true);
            manualInputBox.setHint(Component.literal(menu.isTagMode()
                    ? "Enter tag (e.g. forge:ores)..."
                    : "Enter mod ID (e.g. minecraft)..."));
            manualInputBox.setX(getSelectorInputX());
            manualInputBox.setY(getSelectorInputY());
            manualInputBox.setWidth(getSelectorInputWidth());
            if (!manualInputBox.isFocused() && !manualInputBox.getValue().equals(getCurrentTargetValue())) {
                manualInputBox.setValue(getCurrentTargetValue());
            }
        } else if (menu.isSlotMode()) {
            manualInputBox.setVisible(true);
            manualInputBox.setHint(Component.translatable("gui.logisticsnetworks.filter.slot.input_hint"));
            if (!manualInputBox.isFocused() && !manualInputBox.getValue().equals(getCurrentTargetValue())) {
                manualInputBox.setValue(getCurrentTargetValue());
            }
        } else {
            manualInputBox.setVisible(false);
            if (manualInputBox.isFocused()) {
                manualInputBox.setFocused(false);
            }
        }

        if (!menu.isSlotMode()) {
            slotInfoOpen = false;
            slotInfoPage = 0;
        }
        if (!menu.isAmountMode()) {
            amountInfoOpen = false;
            amountInfoPage = 0;
        }

        if (manualInputBox != null) {
            if (wasManualInputFocused && !manualInputBox.isFocused()) {
                commitManualInput();
            }
            wasManualInputFocused = manualInputBox.isFocused();
        }
    }

    private String getCurrentTargetValue() {
        if (menu.isTagMode())
            return Objects.requireNonNullElse(menu.getSelectedTag(), "");
        if (menu.isModMode())
            return Objects.requireNonNullElse(menu.getSelectedMod(), "");
        if (menu.isSlotMode())
            return Objects.requireNonNullElse(menu.getSlotExpression(), "");
        return "";
    }

    private void refreshFilterData() {
        if (minecraft == null || minecraft.player == null)
            return;

        ItemStack extractor = menu.getExtractorItem();
        boolean isFluid = menu.getTargetType() == FilterTargetType.FLUIDS;

        if (menu.isTagMode()) {
            cachedTags.clear();
            if (!extractor.isEmpty()) {
                if (isFluid) {
                    FluidStack fs = FluidUtil.getFluidContained(extractor).orElse(FluidStack.EMPTY);
                    if (!fs.isEmpty()) {
                        fs.getTags().forEach(t -> cachedTags.add(t.location().toString()));
                    }
                } else {
                    extractor.getTags().forEach(t -> cachedTags.add(t.location().toString()));
                }
                Collections.sort(cachedTags);
            }
        } else if (menu.isModMode()) {
            cachedMods.clear();
            if (!extractor.isEmpty()) {
                ResourceLocation id = null;
                if (isFluid) {
                    FluidStack fs = FluidUtil.getFluidContained(extractor).orElse(FluidStack.EMPTY);
                    if (!fs.isEmpty())
                        id = BuiltInRegistries.FLUID.getKey(fs.getFluid());
                } else {
                    id = BuiltInRegistries.ITEM.getKey(extractor.getItem());
                }
                if (id != null)
                    cachedMods.add(id.getNamespace());
            }
        } else if (menu.isNbtMode()) {
            cachedNbtEntries.clear();
            if (!extractor.isEmpty()) {
                HolderLookup.Provider provider = minecraft.player.level().registryAccess();
                if (isFluid) {
                    FluidStack fs = FluidUtil.getFluidContained(extractor).orElse(FluidStack.EMPTY);
                    if (!fs.isEmpty())
                        cachedNbtEntries = NbtFilterData.extractEntries(fs, provider);
                } else {
                    cachedNbtEntries = NbtFilterData.extractEntries(extractor, provider);
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);

        if (menu.isTagMode())
            renderTagTooltip(g, mx, my);
        else if (menu.isModMode())
            renderModTooltip(g, mx, my);
        else if (menu.getTargetType() == FilterTargetType.FLUIDS && !menu.isNbtMode()) {
            renderFluidTooltip(g, mx, my);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        renderPanel(g, leftPos, topPos, imageWidth, imageHeight);

        g.drawString(font, title, leftPos + 8, topPos + 6, COL_ACCENT, false);

        if (menu.isTagMode())
            renderTagMode(g, mx, my);
        else if (menu.isModMode())
            renderModMode(g, mx, my);
        else if (menu.isNbtMode())
            renderNbtMode(g, mx, my);
        else if (menu.isSlotMode())
            renderSlotMode(g, mx, my);
        else if (menu.isAmountMode())
            renderAmountMode(g, mx, my);
        else if (menu.isDurabilityMode())
            renderDurabilityMode(g, mx, my);
        else
            renderStandardFilterGrid(g, mx, my);

        int playerInvY = menu.getPlayerInventoryY();
        int sepY = topPos + playerInvY - 12;
        g.fill(leftPos + 8, sepY, leftPos + imageWidth - 8, sepY + 1, COL_BORDER);
        g.drawString(font, playerInventoryTitle, leftPos + 8, topPos + playerInvY - 10, COL_GRAY, false);

        renderPlayerSlots(g);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Labels are rendered manually in renderBg to support custom layouts per mode.
    }

    private void renderStandardFilterGrid(GuiGraphics g, int mx, int my) {
        for (int i = 0; i < menu.getFilterSlots() && i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            drawSlot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }

        if (menu.getTargetType() == FilterTargetType.FLUIDS) {
            renderFluidGhostItems(g);
        }
    }

    private void renderTagMode(GuiGraphics g, int mx, int my) {
        renderDropdownMode(g, mx, my, cachedTags,
                menu.getSelectedTag(),
                "Enter tag (e.g. forge:ores)...");
    }

    private void renderModMode(GuiGraphics g, int mx, int my) {
        renderDropdownMode(g, mx, my, cachedMods,
                menu.getSelectedMod(),
                "Enter mod ID (e.g. minecraft)...");
    }

    private void renderDropdownMode(GuiGraphics g, int mx, int my, List<String> items, String current,
            String hint) {
        int x = getSelectorInputX();
        int y = getSelectorInputY();
        int w = getSelectorInputWidth();
        int arrowX = getSelectorArrowX();

        renderExtractorSlotTarget(g, mx, my);
        g.drawString(font, "Selected: " + (current == null ? "None" : current), leftPos + 8, topPos + 22, COL_GRAY,
                false);

        manualInputBox.setHint(Component.literal(hint));

        boolean hoveringDropdown = isHovering(x, y, w, 14, mx, my);
        g.renderOutline(x, y, w, 14, (hoveringDropdown || isDropdownOpen) ? COL_WHITE : COL_BORDER);
        if (!manualInputBox.isVisible() && !manualInputBox.isFocused()) {
            g.drawCenteredString(font, current != null ? current : "", x + w / 2, y + 3, COL_WHITE);
        }

        g.drawCenteredString(font, isDropdownOpen ? "^" : "v", arrowX + 6, y + 3, COL_GRAY);

        if (isDropdownOpen) {
            renderDropdownList(g, x, y + 16, w, items, current, mx, my);
        }
    }

    private void renderDropdownList(GuiGraphics g, int x, int y, int w, List<String> items, String current, int mx,
            int my) {
        int visibleRows = Math.min(items.size(), DROPDOWN_ROWS);
        int listH = visibleRows * LIST_ROW_H;

        // Background
        g.pose().pushPose();
        g.pose().translate(0, 0, 200); // Render on top
        g.fill(x, y, x + w, y + listH, COL_BG);
        g.renderOutline(x, y, w, listH, COL_BORDER);

        int startIdx = listScrollOffset;
        int endIdx = Math.min(startIdx + DROPDOWN_ROWS, items.size());

        for (int i = startIdx; i < endIdx; i++) {
            int rowY = y + (i - startIdx) * LIST_ROW_H;
            String item = items.get(i);
            boolean isSelected = Objects.equals(item, current);
            boolean isHovered = mx >= x && mx <= x + w && my >= rowY && my < rowY + LIST_ROW_H;

            if (isSelected)
                g.fill(x, rowY, x + w, rowY + LIST_ROW_H, COL_SELECTED);
            else if (isHovered)
                g.fill(x, rowY, x + w, rowY + LIST_ROW_H, COL_HOVER);

            String text = scrollText(item, w - 4, i);
            g.drawString(font, text, x + 2, rowY + 2, isSelected ? COL_ACCENT : COL_WHITE, false);
        }
        g.pose().popPose();
    }

    private void renderNbtMode(GuiGraphics g, int mx, int my) {
        int x = getSelectorInputX();
        int y = topPos + 30;
        int w = getSelectorInputWidth();
        int arrowX = getSelectorArrowX();

        if (manualInputBox != null && manualInputBox.isVisible()) {
            manualInputBox.setVisible(false);
            manualInputBox.setFocused(false);
        }

        renderExtractorSlotTarget(g, mx, my);

        // Path Selector
        String path = menu.getSelectedNbtPath();
        String displayPath = path == null ? "Select Path..." : path;

        drawButton(g, x, y, w, 14, scrollText(displayPath, w - 16, 0), mx, my, true);
        g.drawCenteredString(font, isDropdownOpen ? "^" : "v", arrowX + 6, y + 3, COL_GRAY);

        if (isDropdownOpen) {
            List<String> paths = cachedNbtEntries.stream().map(NbtFilterData.NbtEntry::path).toList();
            renderDropdownList(g, x, y + 16, w, paths, path, mx, my);
        } else {
            int valY = y + 25;
            g.drawString(font, Component.translatable("gui.logisticsnetworks.filter.nbt.value"), x, valY, COL_GRAY, false);
            String val = menu.getSelectedNbtValue();
            g.drawString(font, scrollText(val != null ? val : "None", w, 50), x, valY + 10, COL_ACCENT, false);
        }
    }

    private void renderSlotMode(GuiGraphics g, int mx, int my) {
        int contentX = leftPos + 8;
        int contentW = imageWidth - 16;
        int modeY = topPos + 18;
        int modeBtnW = 64;
        int modeBtnH = 14;
        int modeBtnX = leftPos + imageWidth - 8 - modeBtnW;
        int inputY = topPos + 34;
        int activeY = topPos + 52;
        int hintY = topPos + 62;
        int infoBtnX = leftPos + imageWidth - 8 - 12;
        int infoBtnY = topPos + 6;
        int infoBtnSize = 12;

        drawButton(g, infoBtnX, infoBtnY, infoBtnSize, infoBtnSize,
                Component.translatable("gui.logisticsnetworks.filter.info.icon").getString(), mx, my, true);

        g.drawString(font, Component.translatable("gui.logisticsnetworks.filter.slot.mode_label"),
                contentX, modeY + 3, COL_GRAY, false);

        String modeLabel = Component.translatable(menu.isBlacklistMode()
                ? "gui.logisticsnetworks.filter.mode.blacklist"
                : "gui.logisticsnetworks.filter.mode.whitelist").getString();
        drawButton(g, modeBtnX, modeY, modeBtnW, modeBtnH, modeLabel, mx, my, true);

        manualInputBox.setX(contentX);
        manualInputBox.setY(inputY);
        manualInputBox.setWidth(contentW);

        String value = menu.getSlotExpression();
        String display = value.isEmpty()
                ? Component.translatable("gui.logisticsnetworks.filter.slot.none").getString()
                : value;
        String activeLine = Component.translatable("gui.logisticsnetworks.filter.slot.active", display).getString();
        g.drawString(font, font.plainSubstrByWidth(activeLine, contentW), contentX, activeY, COL_ACCENT, false);

        String hintLine = Component
                .translatable("gui.logisticsnetworks.filter.slot.hint", SlotFilterData.MIN_SLOT, SlotFilterData.MAX_SLOT)
                .getString();
        g.drawString(font, font.plainSubstrByWidth(hintLine, contentW), contentX, hintY, COL_GRAY, false);

        if (slotInfoOpen) {
            renderSlotInfoOverlay(g, mx, my);
        }
    }

    private void renderSlotInfoOverlay(GuiGraphics g, int mx, int my) {
        int x = leftPos + 8;
        int y = topPos + 16;
        int w = imageWidth - 16;
        int maxBottom = topPos + menu.getPlayerInventoryY() - 14;
        int h = Math.max(68, maxBottom - y);
        if (y + h > maxBottom) {
            h = Math.max(40, maxBottom - y);
        }
        int pad = 4;

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(x, y, x + w, y + h, 0xF0101010);
        g.renderOutline(x, y, w, h, COL_BORDER);

        String titleKey = slotInfoPage == 0
                ? "gui.logisticsnetworks.filter.slot.info.export.title"
                : "gui.logisticsnetworks.filter.slot.info.import.title";
        g.drawString(font, Component.translatable(titleKey), x + pad, y + pad, COL_WHITE, false);

        String line1 = Component.translatable(slotInfoPage == 0
                ? "gui.logisticsnetworks.filter.slot.info.export.p1"
                : "gui.logisticsnetworks.filter.slot.info.import.p1").getString();
        String line2 = Component.translatable(slotInfoPage == 0
                ? "gui.logisticsnetworks.filter.slot.info.export.p2"
                : "gui.logisticsnetworks.filter.slot.info.import.p2").getString();

        int navY = y + h - 16;
        int textY = y + pad + 11;
        int textW = w - pad * 2;
        int maxTextBottom = navY - 2;
        for (var part : font.split(Component.literal(line1), textW)) {
            if (textY + 8 > maxTextBottom) {
                break;
            }
            g.drawString(font, part, x + pad, textY, COL_GRAY, false);
            textY += 9;
        }
        for (var part : font.split(Component.literal(line2), textW)) {
            if (textY + 8 > maxTextBottom) {
                break;
            }
            g.drawString(font, part, x + pad, textY, COL_GRAY, false);
            textY += 9;
        }

        int prevX = x + w - 40;
        int nextX = x + w - 22;
        drawButton(g, prevX, navY, 14, 12, "<", mx, my, slotInfoPage > 0);
        drawButton(g, nextX, navY, 14, 12, ">", mx, my, slotInfoPage < 1);

        g.pose().popPose();
    }

    private void renderAmountMode(GuiGraphics g, int mx, int my) {
        int cx = leftPos + GUI_WIDTH / 2;
        int cy = topPos + 40;
        int infoBtnX = leftPos + imageWidth - 8 - 12;
        int infoBtnY = topPos + 6;
        int infoBtnSize = 12;

        drawButton(g, infoBtnX, infoBtnY, infoBtnSize, infoBtnSize,
                Component.translatable("gui.logisticsnetworks.filter.info.icon").getString(), mx, my, true);

        g.drawCenteredString(font, Component.translatable("gui.logisticsnetworks.filter.amount.threshold"), cx,
                topPos + 20, COL_WHITE);

        g.fill(cx - 30, cy - 2, cx + 30, cy + 10, COL_BTN_BG);
        g.renderOutline(cx - 30, cy - 2, 60, 12, COL_BORDER);
        g.drawCenteredString(font, String.valueOf(menu.getAmount()), cx, cy, COL_ACCENT);

        int btnY = cy + 15;
        drawAmountButton(g, cx - 70, btnY, "-64", mx, my);
        drawAmountButton(g, cx - 44, btnY, "-10", mx, my);
        drawAmountButton(g, cx - 18, btnY, "-1", mx, my);
        drawAmountButton(g, cx + 18, btnY, "+1", mx, my);
        drawAmountButton(g, cx + 44, btnY, "+10", mx, my);
        drawAmountButton(g, cx + 70, btnY, "+64", mx, my);

        if (amountInfoOpen) {
            renderAmountInfoOverlay(g, mx, my);
        }
    }

    private void renderAmountInfoOverlay(GuiGraphics g, int mx, int my) {
        int x = leftPos + 8;
        int y = topPos + 16;
        int w = imageWidth - 16;
        int maxBottom = topPos + menu.getPlayerInventoryY() - 14;
        int h = Math.max(68, maxBottom - y);
        if (y + h > maxBottom) {
            h = Math.max(40, maxBottom - y);
        }
        int pad = 4;

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(x, y, x + w, y + h, 0xF0101010);
        g.renderOutline(x, y, w, h, COL_BORDER);

        String titleKey = amountInfoPage == 0
                ? "gui.logisticsnetworks.filter.amount.info.import.title"
                : "gui.logisticsnetworks.filter.amount.info.export.title";
        g.drawString(font, Component.translatable(titleKey), x + pad, y + pad, COL_WHITE, false);

        String line1Key = amountInfoPage == 0
                ? "gui.logisticsnetworks.filter.amount.info.import.p1"
                : "gui.logisticsnetworks.filter.amount.info.export.p1";
        String line2Key = amountInfoPage == 0
                ? "gui.logisticsnetworks.filter.amount.info.import.p2"
                : "gui.logisticsnetworks.filter.amount.info.export.p2";
        String line3Key = amountInfoPage == 0
                ? "gui.logisticsnetworks.filter.amount.info.import.p3"
                : "gui.logisticsnetworks.filter.amount.info.export.p3";

        int navY = y + h - 16;
        int textY = y + pad + 11;
        int textW = w - pad * 2;
        int maxTextBottom = navY - 2;
        textY = drawWrappedInfoLine(g, Component.translatable(line1Key).getString(), x + pad, textY, textW, maxTextBottom);
        textY = drawWrappedInfoLine(g, Component.translatable(line2Key).getString(), x + pad, textY, textW, maxTextBottom);
        drawWrappedInfoLine(g, Component.translatable(line3Key).getString(), x + pad, textY, textW, maxTextBottom);

        int prevX = x + w - 40;
        int nextX = x + w - 22;
        drawButton(g, prevX, navY, 14, 12, "<", mx, my, amountInfoPage > 0);
        drawButton(g, nextX, navY, 14, 12, ">", mx, my, amountInfoPage < 1);

        g.pose().popPose();
    }

    private int drawWrappedInfoLine(GuiGraphics g, String line, int x, int y, int width, int maxBottom) {
        int nextY = y;
        for (var part : font.split(Component.literal(line), width)) {
            if (nextY + 8 > maxBottom) {
                break;
            }
            g.drawString(font, part, x, nextY, COL_GRAY, false);
            nextY += 9;
        }
        return nextY;
    }

    private void renderDurabilityMode(GuiGraphics g, int mx, int my) {
        int cx = leftPos + GUI_WIDTH / 2;
        int cy = topPos + 40;

        g.drawCenteredString(font, "Durability Limit", cx, topPos + 20, COL_WHITE);

        DurabilityFilterData.Operator op = menu.getDurabilityOperator();
        drawButton(g, cx - 50, cy, 20, 12, op.symbol(), mx, my, true);
        g.drawString(font, String.valueOf(menu.getDurabilityValue()), cx - 20, cy + 2, COL_ACCENT, false);

        int btnY = cy + 20;
        drawAmountButton(g, cx - 70, btnY, "-64", mx, my);
        drawAmountButton(g, cx - 44, btnY, "-10", mx, my);
        drawAmountButton(g, cx - 18, btnY, "-1", mx, my);
        drawAmountButton(g, cx + 18, btnY, "+1", mx, my);
        drawAmountButton(g, cx + 44, btnY, "+10", mx, my);
        drawAmountButton(g, cx + 70, btnY, "+64", mx, my);
    }

    private void renderPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, COL_BG);
        g.renderOutline(x, y, w, h, COL_BORDER);
    }

    private void renderPlayerSlots(GuiGraphics g) {
        for (int i = 0; i < menu.slots.size(); i++) {
            if (!menu.isPlayerInventorySlot(i)) {
                continue;
            }
            var slot = menu.slots.get(i);
            drawSlot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
    }

    private void drawSlot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF0A0A0A);
        g.renderOutline(x, y, 18, 18, 0xFF3A3A3A);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String label, int mx, int my, boolean active) {
        boolean hovered = active && isHovering(x, y, w, h, mx, my);
        g.fill(x, y, x + w, y + h, hovered ? COL_BTN_HOVER : COL_BTN_BG);
        g.renderOutline(x, y, w, h, hovered ? COL_WHITE : COL_BTN_BORDER);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, hovered ? COL_WHITE : COL_GRAY);
    }

    private void drawAmountButton(GuiGraphics g, int x, int y, String label, int mx, int my) {
        drawButton(g, x - 10, y, 20, 14, label, mx, my, true);
    }

    private void renderFluidGhostItems(GuiGraphics g) {
        for (int i = 0; i < menu.getFilterSlots(); i++) {
            FluidStack fs = menu.getFluidFilter(i);
            if (!fs.isEmpty()) {
                var slot = menu.slots.get(i);
                int x = leftPos + slot.x;
                int y = topPos + slot.y;
                renderFluidStack(g, fs, x, y);
            }
        }
    }

    private void renderFluidStack(GuiGraphics g, FluidStack stack, int x, int y) {
        IClientFluidTypeExtensions clientFluid = IClientFluidTypeExtensions.of(stack.getFluid());
        ResourceLocation stillTex = clientFluid.getStillTexture(stack);
        if (stillTex == null)
            return;

        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stillTex);
        int color = clientFluid.getTintColor(stack);

        float r = ((color >> 16) & 0xFF) / 255f;
        float gr = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(r, gr, b, 1.0f);
        g.blit(x, y, 0, 16, 16, sprite);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    private String scrollText(String text, int width, int offset) {
        if (font.width(text) <= width)
            return text;
        String s = text + "   " + text;
        int len = s.length();
        int ticks = (textTick / 5 + offset * 10) % len;
        String rotated = s.substring(ticks) + s.substring(0, ticks);
        return font.plainSubstrByWidth(rotated, width);
    }

    private boolean isHovering(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        boolean handled = false;
        if (menu.isTagMode())
            handled = handleTagClick(mx, my, btn);
        else if (menu.isModMode())
            handled = handleModClick(mx, my, btn);
        else if (menu.isNbtMode())
            handled = handleNbtClick(mx, my, btn);
        else if (menu.isSlotMode())
            handled = handleSlotClick(mx, my, btn);
        else if (menu.isAmountMode())
            handled = handleAmountClick(mx, my, btn);
        else if (menu.isDurabilityMode())
            handled = handleDurabilityClick(mx, my, btn);

        if (!handled) {
            if (isDropdownOpen && !isHoveringDropdown(mx, my)) {
                isDropdownOpen = false;
                return true;
            }
            return super.mouseClicked(mx, my, btn);
        }
        return true;
    }

    private boolean isHoveringDropdown(double mx, double my) {
        if (!isDropdownOpen)
            return false;
        return true;
    }

    private boolean handleTagClick(double mx, double my, int btn) {
        int x = getSelectorInputX();
        int y = getSelectorInputY();
        int w = getSelectorInputWidth();
        int arrowX = getSelectorArrowX();

        if (isHovering(arrowX, y, 12, 14, (int) mx, (int) my)) {
            isDropdownOpen = !isDropdownOpen;
            listScrollOffset = 0;
            return true;
        }

        if (isDropdownOpen) {
            if (isHovering(x, y + 16, w, DROPDOWN_ROWS * LIST_ROW_H, (int) mx, (int) my)) {
                int row = ((int) my - (y + 16)) / LIST_ROW_H;
                int idx = listScrollOffset + row;
                if (idx >= 0 && idx < cachedTags.size()) {
                    String tag = cachedTags.get(idx);
                    menu.setSelectedTag(tag);
                    manualInputBox.setValue(tag);
                    sendTagUpdate(tag);
                    isDropdownOpen = false;
                    return true;
                }
            }
        }
        if (btn == 1 && isHovering(x, y, w, 14, (int) mx, (int) my)) {
            String toRemove = menu.getSelectedTag();
            menu.setSelectedTag(null);
            sendTagRemove(toRemove);
            manualInputBox.setValue("");
            return true;
        }

        return false;
    }

    private boolean handleModClick(double mx, double my, int btn) {
        int x = getSelectorInputX();
        int y = getSelectorInputY();
        int w = getSelectorInputWidth();
        int arrowX = getSelectorArrowX();

        if (isHovering(arrowX, y, 12, 14, (int) mx, (int) my)) {
            isDropdownOpen = !isDropdownOpen;
            listScrollOffset = 0;
            return true;
        }

        if (isDropdownOpen) {
            if (isHovering(x, y + 16, w, DROPDOWN_ROWS * LIST_ROW_H, (int) mx, (int) my)) {
                int row = ((int) my - (y + 16)) / LIST_ROW_H;
                int idx = listScrollOffset + row;
                if (idx >= 0 && idx < cachedMods.size()) {
                    String mod = cachedMods.get(idx);
                    menu.setSelectedMod(mod);
                    manualInputBox.setValue(mod);
                    sendModUpdate(mod);
                    isDropdownOpen = false;
                    return true;
                }
            }
        }
        if (btn == 1 && isHovering(x, y, w, 14, (int) mx, (int) my)) {
            String toRemove = menu.getSelectedMod();
            menu.setSelectedMod(null);
            sendModRemove(toRemove);
            manualInputBox.setValue("");
            return true;
        }
        return false;
    }

    private boolean handleNbtClick(double mx, double my, int btn) {
        int x = getSelectorInputX();
        int y = topPos + 30;
        int w = getSelectorInputWidth();
        int arrowX = getSelectorArrowX();

        if (isHovering(x, y, w + 12, 14, (int) mx, (int) my)) {
            isDropdownOpen = !isDropdownOpen;
            if (isDropdownOpen) {
                listScrollOffset = 0;
            }
            return true;
        }

        if (isDropdownOpen) {
            if (isHovering(x, y + 16, w, DROPDOWN_ROWS * LIST_ROW_H, (int) mx, (int) my)) {
                int row = ((int) my - (y + 16)) / LIST_ROW_H;
                int idx = listScrollOffset + row;
                if (idx >= 0 && idx < cachedNbtEntries.size()) {
                    String path = cachedNbtEntries.get(idx).path();
                    menu.setSelectedNbtPath(path);
                    sendNbtUpdate(path);
                    isDropdownOpen = false;
                    return true;
                }
            }
        }

        return false;
    }

    private boolean handleAmountClick(double mx, double my, int btn) {
        int cx = leftPos + GUI_WIDTH / 2;
        int infoBtnX = leftPos + imageWidth - 8 - 12;
        int infoBtnY = topPos + 6;
        int infoBtnSize = 12;

        if (isHovering(infoBtnX, infoBtnY, infoBtnSize, infoBtnSize, (int) mx, (int) my)) {
            amountInfoOpen = !amountInfoOpen;
            return true;
        }

        if (amountInfoOpen) {
            int x = leftPos + 8;
            int y = topPos + 16;
            int w = imageWidth - 16;
            int maxBottom = topPos + menu.getPlayerInventoryY() - 14;
            int h = Math.max(68, maxBottom - y);
            if (y + h > maxBottom) {
                h = Math.max(40, maxBottom - y);
            }
            int navY = y + h - 16;
            int prevX = x + w - 40;
            int nextX = x + w - 22;

            if (isHovering(prevX, navY, 14, 12, (int) mx, (int) my) && amountInfoPage > 0) {
                amountInfoPage--;
                return true;
            }
            if (isHovering(nextX, navY, 14, 12, (int) mx, (int) my) && amountInfoPage < 1) {
                amountInfoPage++;
                return true;
            }

            if (isHovering(x, y, w, h, (int) mx, (int) my)) {
                return true;
            }
        }

        int cy = topPos + 40 + 15;

        if (checkAmountBtn(mx, my, cx - 70, cy, -64))
            return true;
        if (checkAmountBtn(mx, my, cx - 44, cy, -10))
            return true;
        if (checkAmountBtn(mx, my, cx - 18, cy, -1))
            return true;
        if (checkAmountBtn(mx, my, cx + 18, cy, 1))
            return true;
        if (checkAmountBtn(mx, my, cx + 44, cy, 10))
            return true;
        if (checkAmountBtn(mx, my, cx + 70, cy, 64))
            return true;

        return false;
    }

    private boolean handleSlotClick(double mx, double my, int btn) {
        int contentX = leftPos + 8;
        int contentW = imageWidth - 16;
        int modeY = topPos + 18;
        int modeBtnW = 64;
        int modeBtnH = 14;
        int modeBtnX = leftPos + imageWidth - 8 - modeBtnW;
        int inputY = topPos + 34;
        int infoBtnX = leftPos + imageWidth - 8 - 12;
        int infoBtnY = topPos + 6;
        int infoBtnSize = 12;

        if (isHovering(infoBtnX, infoBtnY, infoBtnSize, infoBtnSize, (int) mx, (int) my)) {
            slotInfoOpen = !slotInfoOpen;
            return true;
        }

        if (slotInfoOpen) {
            int x = leftPos + 8;
            int y = topPos + 16;
            int w = imageWidth - 16;
            int maxBottom = topPos + menu.getPlayerInventoryY() - 14;
            int h = Math.max(68, maxBottom - y);
            if (y + h > maxBottom) {
                h = Math.max(40, maxBottom - y);
            }
            int navY = y + h - 16;
            int prevX = x + w - 40;
            int nextX = x + w - 22;

            if (isHovering(prevX, navY, 14, 12, (int) mx, (int) my) && slotInfoPage > 0) {
                slotInfoPage--;
                return true;
            }
            if (isHovering(nextX, navY, 14, 12, (int) mx, (int) my) && slotInfoPage < 1) {
                slotInfoPage++;
                return true;
            }

            if (isHovering(x, y, w, h, (int) mx, (int) my)) {
                return true;
            }
        }

        if (isHovering(modeBtnX, modeY, modeBtnW, modeBtnH, (int) mx, (int) my)) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
            return true;
        }

        if (btn == 1 && isHovering(contentX, inputY, contentW, 14, (int) mx, (int) my)) {
            manualInputBox.setValue("");
            sendSlotUpdate("");
            return true;
        }

        return false;
    }

    private boolean handleDurabilityClick(double mx, double my, int btn) {
        int cx = leftPos + GUI_WIDTH / 2;
        int cy = topPos + 40;

        if (isHovering(cx - 50, cy, 20, 12, (int) mx, (int) my)) {
            return true;
        }

        int btnY = cy + 20;
        if (checkAmountBtn(mx, my, cx - 70, btnY, -64))
            return true;
        if (checkAmountBtn(mx, my, cx - 44, btnY, -10))
            return true;
        if (checkAmountBtn(mx, my, cx - 18, btnY, -1))
            return true;
        if (checkAmountBtn(mx, my, cx + 18, btnY, 1))
            return true;
        if (checkAmountBtn(mx, my, cx + 44, btnY, 10))
            return true;
        if (checkAmountBtn(mx, my, cx + 70, btnY, 64))
            return true;

        return false;
    }

    private boolean checkAmountBtn(double mx, double my, int x, int y, int delta) {
        if (isHovering(x - 10, y, 20, 14, (int) mx, (int) my)) {
            if (menu.isAmountMode()) {
                sendAmountUpdate(menu.getAmount() + delta);
            } else {
                sendDurabilityUpdate(menu.getDurabilityValue() + delta);
            }
            return true;
        }
        return false;
    }

    private void sendTagUpdate(String tag) {
        menu.setSelectedTag(tag == null || tag.isBlank() ? null : tag.trim());
        PacketDistributor.sendToServer(new ModifyFilterTagPayload(tag == null ? "" : tag, false));
    }

    private void sendTagRemove(String tag) {
        menu.setSelectedTag(null);
        PacketDistributor.sendToServer(new ModifyFilterTagPayload(tag == null ? "" : tag, true));
    }

    private void sendModUpdate(String mod) {
        menu.setSelectedMod(mod == null || mod.isBlank() ? null : mod.trim());
        PacketDistributor.sendToServer(new ModifyFilterModPayload(mod == null ? "" : mod, false));
    }

    private void sendModRemove(String mod) {
        menu.setSelectedMod(null);
        PacketDistributor.sendToServer(new ModifyFilterModPayload(mod == null ? "" : mod, true));
    }

    private void sendNbtUpdate(String path) {
        PacketDistributor.sendToServer(new ModifyFilterNbtPayload(path, path == null));
    }

    private void sendAmountUpdate(int amount) {
        PacketDistributor.sendToServer(new SetAmountFilterValuePayload(amount));
    }

    private void sendDurabilityUpdate(int val) {
        PacketDistributor.sendToServer(new SetDurabilityFilterValuePayload(val));
    }

    private void sendSlotUpdate(String expression) {
        PacketDistributor.sendToServer(new SetSlotFilterSlotsPayload(expression == null ? "" : expression));
    }

    private void flushManualInputToServer() {
        if (flushedTextOnClose || manualInputBox == null || !manualInputBox.isVisible()) {
            return;
        }
        flushedTextOnClose = true;
        commitManualInput();
    }

    private void commitManualInput() {
        if (manualInputBox == null || !manualInputBox.isVisible()) {
            return;
        }

        String val = manualInputBox.getValue() == null ? "" : manualInputBox.getValue().trim();
        if (menu.isTagMode()) {
            if (val.isEmpty()) {
                sendTagRemove(menu.getSelectedTag());
            } else {
                sendTagUpdate(val);
            }
        } else if (menu.isModMode()) {
            if (val.isEmpty()) {
                sendModRemove(menu.getSelectedMod());
            } else {
                sendModUpdate(val);
            }
        } else if (menu.isSlotMode()) {
            sendSlotUpdate(val);
        }
    }

    @Override
    public void onClose() {
        flushManualInputToServer();
        super.onClose();
    }

    @Override
    public void removed() {
        flushManualInputToServer();
        super.removed();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 256) {
            return super.keyPressed(key, scan, modifiers);
        }

        if (manualInputBox.isFocused()) {
            if (key == 257) {
                commitManualInput();
                manualInputBox.setFocused(false);
                return true;
            }
            return manualInputBox.keyPressed(key, scan, modifiers);
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (isDropdownOpen) {
            if (sy > 0 && listScrollOffset > 0)
                listScrollOffset--;
            else if (sy < 0)
                listScrollOffset++;
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (manualInputBox.isFocused())
            return manualInputBox.charTyped(ch, modifiers);
        return super.charTyped(ch, modifiers);
    }

    private void renderTagTooltip(GuiGraphics g, int mx, int my) {
        if (isHovering(getSelectorArrowX(), getSelectorInputY(), 12, 14, mx, my)) {
            g.renderTooltip(font, Component.literal("Select from Extractor Item"), mx, my);
            return;
        }
        var extractor = getExtractorRect();
        if (extractor != null && isHovering(extractor[0], extractor[1], 18, 18, mx, my)) {
            g.renderTooltip(font, Component.translatable("gui.logisticsnetworks.filter.selector_hint"), mx, my);
        }
    }

    private void renderModTooltip(GuiGraphics g, int mx, int my) {
        if (isHovering(getSelectorArrowX(), getSelectorInputY(), 12, 14, mx, my)) {
            g.renderTooltip(font, Component.literal("Select from Extractor Item"), mx, my);
            return;
        }
        var extractor = getExtractorRect();
        if (extractor != null && isHovering(extractor[0], extractor[1], 18, 18, mx, my)) {
            g.renderTooltip(font, Component.translatable("gui.logisticsnetworks.filter.selector_hint"), mx, my);
        }
    }

    private void renderFluidTooltip(GuiGraphics g, int mx, int my) {
        for (int i = 0; i < menu.getFilterSlots(); i++) {
            var slot = menu.slots.get(i);
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            if (isHovering(x, y, 18, 18, mx, my)) {
                FluidStack fs = menu.getFluidFilter(i);
                if (!fs.isEmpty()) {
                    g.renderTooltip(font, fs.getHoverName(), mx, my);
                }
                break;
            }
        }
    }

    public void setFluidFilterEntry(Player player, int slot, FluidStack fluidStack) {
        if (fluidStack.isEmpty())
            return;
        PacketDistributor.sendToServer(
                new SetFilterFluidEntryPayload(slot, BuiltInRegistries.FLUID.getKey(fluidStack.getFluid()).toString()));
        menu.setFluidFilterEntry(player, slot, fluidStack);
    }

    public void setItemFilterEntry(Player player, int slot, ItemStack stack) {
        if (stack.isEmpty())
            return;
        PacketDistributor.sendToServer(new SetFilterItemEntryPayload(slot, stack));
        menu.setItemFilterEntry(player, slot, stack);
    }

    public boolean acceptsFluidSelectorGhostIngredient() {
        return menu.isTagMode() || menu.isModMode() || menu.isNbtMode();
    }

    public boolean acceptsItemSelectorGhostIngredient() {
        return menu.isTagMode() || menu.isModMode() || menu.isNbtMode();
    }

    public boolean supportsGhostIngredientTargets() {
        return !menu.isTagMode() && !menu.isModMode() && !menu.isNbtMode() && !menu.isAmountMode()
                && !menu.isDurabilityMode() && !menu.isSlotMode();
    }

    public int getGhostFilterSlotCount() {
        return menu.getFilterSlots();
    }

    public Rect2i getGhostFilterSlotArea(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= menu.getFilterSlots() || slotIndex >= menu.slots.size()) {
            return new Rect2i(leftPos, topPos, 0, 0);
        }
        var slot = menu.slots.get(slotIndex);
        return new Rect2i(leftPos + slot.x, topPos + slot.y, 16, 16);
    }

    public Rect2i getSelectorGhostArea() {
        int extractorIndex = menu.getExtractorSlotIndex();
        if (extractorIndex >= 0 && extractorIndex < menu.slots.size()) {
            var slot = menu.slots.get(extractorIndex);
            return new Rect2i(leftPos + slot.x, topPos + slot.y, 16, 16);
        }
        return new Rect2i(leftPos, topPos, 0, 0);
    }

    public void setGhostFluidFilterEntry(int slot, FluidStack stack) {
        setFluidFilterEntry(minecraft.player, slot, stack);
    }

    public void setGhostItemFilterEntry(int slot, ItemStack stack) {
        setItemFilterEntry(minecraft.player, slot, stack);
    }

    public void setSelectorGhostFluid(FluidStack stack) {
        if (menu.getExtractorSlotIndex() >= 0) {
            menu.slots.get(menu.getExtractorSlotIndex()).set(new ItemStack(stack.getFluid().getBucket()));
        }
    }

    public void setSelectorGhostItem(ItemStack stack) {
        if (menu.getExtractorSlotIndex() >= 0) {
            menu.slots.get(menu.getExtractorSlotIndex()).set(stack.copyWithCount(1));
        }
    }

    private int getSelectorInputX() {
        int extractorIndex = menu.getExtractorSlotIndex();
        if (extractorIndex >= 0 && extractorIndex < menu.slots.size()) {
            return leftPos + menu.slots.get(extractorIndex).x + 20;
        }
        return leftPos + 28;
    }

    private int getSelectorInputY() {
        return topPos + 40;
    }

    private int getSelectorInputWidth() {
        int x = getSelectorInputX();
        int w = (leftPos + imageWidth - 20) - x;
        return Math.max(80, w);
    }

    private int getSelectorArrowX() {
        return getSelectorInputX() + getSelectorInputWidth() + 4;
    }

    private int[] getExtractorRect() {
        int extractorIndex = menu.getExtractorSlotIndex();
        if (extractorIndex < 0 || extractorIndex >= menu.slots.size()) {
            return null;
        }
        var slot = menu.slots.get(extractorIndex);
        return new int[] { leftPos + slot.x - 1, topPos + slot.y - 1 };
    }

    private void renderExtractorSlotTarget(GuiGraphics g, int mx, int my) {
        int[] rect = getExtractorRect();
        if (rect == null) {
            return;
        }
        int x = rect[0];
        int y = rect[1];
        drawSlot(g, x, y);
        if (isHovering(x, y, 18, 18, mx, my)) {
            g.fill(x, y, x + 18, y + 18, COL_HOVER);
        }
    }
}
