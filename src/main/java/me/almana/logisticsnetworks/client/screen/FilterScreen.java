package me.almana.logisticsnetworks.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;

import me.almana.logisticsnetworks.filter.DurabilityFilterData;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.FilterTagUtil;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import me.almana.logisticsnetworks.filter.NameFilterData;
import me.almana.logisticsnetworks.filter.NameMatchScope;
import me.almana.logisticsnetworks.filter.NbtFilterData;
import me.almana.logisticsnetworks.filter.SlotFilterData;
import net.minecraft.core.registries.Registries;
import net.minecraft.client.gui.Font;
import net.minecraft.util.Mth;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.TagKey;

import me.almana.logisticsnetworks.menu.FilterMenu;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.network.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
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
    private static final int LIST_ROW_H = 10;
    private static final int DROPDOWN_ROWS = 6;
    private static final int SUBMODE_SCROLLBAR_W = 6;
    private static final int SUBMODE_SCROLLBAR_GAP = 2;

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

    // Sub-mode state
    private int tagEditSlot = -1;
    private int nbtEditSlot = -1;
    private List<String> cachedSlotTags = new ArrayList<>();
    private List<NbtFilterData.NbtEntry> cachedSlotNbtEntries = new ArrayList<>();
    private int subModeScrollOffset = 0;
    private boolean subModeDropdownOpen = false;
    private String nbtPendingOperator = "=";
    private EditBox tagInputBox;

    // NBT sub-mode state
    private int nbtListScrollOffset = 0;
    private int nbtEditingRuleIndex = -1;
    private EditBox nbtValueEditBox;

    // Cached Data
    private List<String> cachedTags = new ArrayList<>();
    private List<String> cachedMods = new ArrayList<>();
    private ItemStack lastExtractorItem = ItemStack.EMPTY;
    private FilterTargetType lastTargetType = null;

    // Animation
    private int textTick = 0;
    private String currentSlotExpr;
    private Component selectorGhostChemicalName = null;
    private String selectorGhostChemicalId = null;
    private List<String> selectorGhostChemicalTags = null;

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

        tagInputBox = new EditBox(font, leftPos + 12, topPos + 50, 100, 14, Component.empty());
        tagInputBox.setMaxLength(256);
        tagInputBox.setVisible(false);
        tagInputBox.setBordered(true);
        tagInputBox.setTextColor(COL_WHITE);

        nbtValueEditBox = new EditBox(font, 0, 0, 60, LIST_ROW_H, Component.empty());
        nbtValueEditBox.setMaxLength(128);
        nbtValueEditBox.setVisible(false);
        nbtValueEditBox.setBordered(false);
        nbtValueEditBox.setTextColor(0xFFFFAA00);
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
            manualInputBox.setHint(Component.translatable(menu.isTagMode()
                    ? "gui.logisticsnetworks.filter.tag.input_full_hint"
                    : "gui.logisticsnetworks.filter.mod.input_full_hint"));
            manualInputBox.setX(getSelectorInputX());
            manualInputBox.setY(getSelectorInputY());
            manualInputBox.setWidth(getSelectorInputWidth());
            if (!manualInputBox.isFocused() && !manualInputBox.getValue().equals(getCurrentTargetValue())) {
                manualInputBox.setValue(getCurrentTargetValue());
            }
        } else if (menu.isNameMode()) {
            manualInputBox.setVisible(true);
            manualInputBox.setHint(Component.translatable("gui.logisticsnetworks.filter.name.input_hint"));
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
        if (menu.isNameMode())
            return Objects.requireNonNullElse(menu.getNameFilter(), "");
        if (menu.isSlotMode())
            return Objects.requireNonNullElse(menu.getSlotExpression(), "");
        return "";
    }

    private void refreshFilterData() {
        if (minecraft == null || minecraft.player == null)
            return;

        ItemStack extractor = menu.getExtractorItem();
        boolean isFluid = menu.getTargetType() == FilterTargetType.FLUIDS;
        boolean isChemical = menu.getTargetType() == FilterTargetType.CHEMICALS;

        if (menu.isTagMode()) {
            FilterTargetType currentTarget = menu.getTargetType();
            if (!ItemStack.isSameItemSameComponents(extractor, lastExtractorItem)
                    || currentTarget != lastTargetType) {
                lastExtractorItem = extractor.copy();
                lastTargetType = currentTarget;
                cachedTags.clear();
                if (!extractor.isEmpty()) {
                    if (isFluid) {
                        FluidStack fs = FluidUtil.getFluidContained(extractor).orElse(FluidStack.EMPTY);
                        if (!fs.isEmpty()) {
                            fs.getTags().forEach(t -> cachedTags.add(t.location().toString()));
                        }
                    } else if (isChemical && MekanismCompat.isLoaded()) {
                        List<String> chemTags = MekanismCompat.getChemicalTagsFromItem(extractor);
                        if (chemTags != null) {
                            cachedTags.addAll(chemTags);
                        }
                    } else {
                        var item = extractor.getItem();
                        BuiltInRegistries.ITEM.getTagNames().forEach(tagKey -> {
                            var holders = BuiltInRegistries.ITEM.getTag(tagKey);
                            if (holders.isPresent()
                                    && holders.get().stream().anyMatch(h -> h.value() == item)) {
                                cachedTags.add(tagKey.location().toString());
                            }
                        });
                    }
                } else if (selectorGhostChemicalTags != null) {
                    cachedTags.addAll(selectorGhostChemicalTags);
                }
                Collections.sort(cachedTags);
            }
        } else if (menu.isModMode()) {
            cachedMods.clear();
            if (!extractor.isEmpty()) {
                String ns = null;
                if (isFluid) {
                    FluidStack fs = FluidUtil.getFluidContained(extractor).orElse(FluidStack.EMPTY);
                    if (!fs.isEmpty())
                        ns = BuiltInRegistries.FLUID.getKey(fs.getFluid()).getNamespace();
                } else if (isChemical && MekanismCompat.isLoaded()) {
                    String chemId = MekanismCompat.getChemicalIdFromItem(extractor);
                    if (chemId != null) {
                        ResourceLocation loc = ResourceLocation.tryParse(chemId);
                        if (loc != null)
                            ns = loc.getNamespace();
                    }
                } else {
                    ns = BuiltInRegistries.ITEM.getKey(extractor.getItem()).getNamespace();
                }
                if (ns != null)
                    cachedMods.add(ns);
            } else if (selectorGhostChemicalId != null) {
                ResourceLocation loc = ResourceLocation.tryParse(selectorGhostChemicalId);
                if (loc != null) {
                    cachedMods.add(loc.getNamespace());
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // Pass fake coords when sub-mode is active
        if (tagEditSlot >= 0 || nbtEditSlot >= 0) {
            super.render(g, -1, -1, pt);
        } else {
            super.render(g, mx, my, pt);
        }

        // Render sub-mode overlays AFTER super.render to cover item/durability bars
        if (tagEditSlot >= 0) {
            renderTagSubMode(g, mx, my);
        } else if (nbtEditSlot >= 0) {
            renderNbtSubMode(g, mx, my);
        }

        boolean hoverSpecialFilter = (menu.isTagMode() || menu.isModMode())
                && this.hoveredSlot != null && this.hoveredSlot.index < menu.getFilterSlots();

        if (menu.isTagMode())
            renderTagTooltip(g, mx, my);
        else if (menu.isModMode())
            renderModTooltip(g, mx, my);
        else if (menu.getTargetType() == FilterTargetType.FLUIDS || menu.getTargetType() == FilterTargetType.CHEMICALS) {
            renderFluidTooltip(g, mx, my);
        }

        // Standard-mode tag slot tooltip
        if (!menu.isTagMode() && !menu.isModMode()
                && tagEditSlot < 0 && nbtEditSlot < 0
                && this.hoveredSlot != null && this.hoveredSlot.index < menu.getFilterSlots()) {
            int idx = this.hoveredSlot.index;
            if (menu.isTagSlot(idx)) {
                String tag = menu.getEntryTag(idx);
                if (tag != null) {
                    g.renderTooltip(font, Component.literal("#" + tag), mx, my);
                    hoverSpecialFilter = true;
                }
            }
            if (!hoverSpecialFilter) {
                List<FilterItemData.SlotNbtRule> nbtRules = menu.getSlotNbtRules(idx);
                if (!nbtRules.isEmpty()) {
                    List<Component> lines = new ArrayList<>();
                    boolean matchAny = menu.isSlotNbtMatchAny(idx);
                    lines.add(Component.literal("NBT (" + nbtRules.size() + " rules, "
                            + (matchAny ? "any" : "all") + ")"));
                    for (FilterItemData.SlotNbtRule r : nbtRules) {
                        String path = formatNbtPath(r.path());
                        String val = formatNbtValue(r.value() != null ? r.value().toString() : "");
                        lines.add(Component.literal("  " + path + " " + r.operator() + " " + val));
                    }
                    g.renderTooltip(font, lines, Optional.empty(), mx, my);
                    hoverSpecialFilter = true;
                }
            }
        }

        if (!hoverSpecialFilter) {
            this.renderTooltip(g, mx, my);
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
        else if (menu.isSlotMode())
            renderSlotMode(g, mx, my);
        else if (menu.isAmountMode())
            renderAmountMode(g, mx, my);
        else if (menu.isDurabilityMode())
            renderDurabilityMode(g, mx, my);
        else if (menu.isNameMode())
            renderNameMode(g, mx, my);
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
            int sx = leftPos + slot.x - 1;
            int sy = topPos + slot.y - 1;

            if (menu.isTagSlot(i)) {
                // green outline for tag slots
                drawSlot(g, sx, sy);
                g.renderOutline(sx, sy, 18, 18, 0xFF44BB44);

                String tag = menu.getEntryTag(i);
                if (tag != null) {
                    // cycle items from tag
                    ResourceLocation tagId = ResourceLocation.tryParse(tag);
                    if (tagId != null) {
                        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
                        var holders = BuiltInRegistries.ITEM.getTag(tagKey);
                        if (holders.isPresent()) {
                            var list = holders.get().stream().toList();
                            if (!list.isEmpty()) {
                                long tick = (System.currentTimeMillis() / 1000);
                                int idx = (int) (tick % list.size());
                                ItemStack display = new ItemStack(list.get(idx));
                                g.renderItem(display, sx + 1, sy + 1);
                            }
                        }
                    }
                }
            } else if (FilterItemData.isNbtOnlySlot(menu.getOpenedStack(), i)) {
                // orange outline + centered N
                drawSlot(g, sx, sy);
                g.renderOutline(sx, sy, 18, 18, 0xFFFFAA00);
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                int nx = sx + (18 - font.width("N")) / 2;
                int ny = sy + 5;
                g.drawString(font, "N", nx, ny, 0xFFFFAA00, true);
                g.pose().popPose();
            } else {
                drawSlot(g, sx, sy);
            }

            // NBT badge (skip for nbt-only slots)
            if (FilterItemData.hasEntryNbt(menu.getOpenedStack(), i)
                    && !FilterItemData.isNbtOnlySlot(menu.getOpenedStack(), i)) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.pose().scale(0.5f, 0.5f, 1.0f);
                int bx = (int) ((sx + 1) / 0.5f);
                int by = (int) ((sy + 1) / 0.5f);
                g.drawString(font, "N", bx, by, 0xFFFFAA00, true);
                g.pose().popPose();
            }

            // Durability badge
            if (FilterItemData.hasEntryDurability(menu.getOpenedStack(), i)) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.pose().scale(0.5f, 0.5f, 1.0f);
                int bx = (int) ((sx + 12) / 0.5f);
                int by = (int) ((sy + 1) / 0.5f);
                g.drawString(font, "D", bx, by, 0xFF55BBFF, true);
                g.pose().popPose();
            }
        }

        if (menu.getTargetType() == FilterTargetType.FLUIDS) {
            renderFluidGhostItems(g);
        } else if (menu.getTargetType() == FilterTargetType.CHEMICALS) {
            renderChemicalGhostItems(g);
        }

        renderEntryAmountOverlays(g);
        renderModeControls(g, mx, my, true);

    }

    private void renderEntryAmountOverlays(GuiGraphics g) {
        boolean isMb = menu.getTargetType() == FilterTargetType.FLUIDS
                || menu.getTargetType() == FilterTargetType.CHEMICALS;

        for (int i = 0; i < menu.getFilterSlots() && i < menu.slots.size(); i++) {
            int amount = menu.getEntryAmount(i);
            if (amount <= 0)
                continue;

            var slot = menu.slots.get(i);
            int x = leftPos + slot.x;
            int y = topPos + slot.y;

            String text = isMb ? formatMb(amount) : String.valueOf(amount);
            float scale = isMb ? 0.5f : 0.65f;

            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.pose().scale(scale, scale, 1.0f);
            int textW = font.width(text);
            int drawX = (int) ((x + 17) / scale) - textW;
            int drawY = (int) ((y + 10) / scale);
            g.drawString(font, text, drawX, drawY, 0xFFBBBBBB, true);
            g.pose().popPose();
        }
    }

    private String formatMb(int amount) {
        if (amount >= 1000 && amount % 1000 == 0) {
            return (amount / 1000) + "B";
        }
        return amount + "mB";
    }

    private void renderTagMode(GuiGraphics g, int mx, int my) {
        renderModeControls(g, mx, my, true);
        renderDropdownMode(g, mx, my, cachedTags,
                menu.getSelectedTag(),
                Component.translatable("gui.logisticsnetworks.filter.tag.input_full_hint"));
    }

    private void renderModMode(GuiGraphics g, int mx, int my) {
        renderModeControls(g, mx, my, false);
        renderDropdownMode(g, mx, my, cachedMods,
                menu.getSelectedMod(),
                Component.translatable("gui.logisticsnetworks.filter.mod.input_full_hint"));
    }

    private void renderDropdownMode(GuiGraphics g, int mx, int my, List<String> items, String current,
            Component hint) {
        int x = getSelectorInputX();
        int y = getSelectorInputY();
        int w = getSelectorInputWidth();
        int arrowX = getSelectorArrowX();

        renderExtractorSlotTarget(g, mx, my);
        String displayValue = current == null ? tr("gui.logisticsnetworks.filter.none") : current;
        g.drawString(font, Component.translatable("gui.logisticsnetworks.filter.selector.selected", displayValue),
                leftPos + 8, topPos + 22, COL_GRAY, false);

        manualInputBox.setHint(hint);

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

        // Clamp scroll offset
        int maxScroll = Math.max(0, items.size() - DROPDOWN_ROWS);
        listScrollOffset = Math.max(0, Math.min(listScrollOffset, maxScroll));

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

    private void renderSlotMode(GuiGraphics g, int mx, int my) {
        int contentX = leftPos + 8;
        int contentW = imageWidth - 16;
        int inputY = topPos + 34;
        int activeY = topPos + 52;
        int hintY = topPos + 62;
        int infoBtnX = leftPos + imageWidth - 8 - 12;
        int infoBtnY = topPos + 6;
        int infoBtnSize = 12;

        drawButton(g, infoBtnX, infoBtnY, infoBtnSize, infoBtnSize,
                Component.translatable("gui.logisticsnetworks.filter.info.icon").getString(), mx, my, true);

        renderModeControls(g, mx, my, false);

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
                .translatable("gui.logisticsnetworks.filter.slot.hint", SlotFilterData.MIN_SLOT,
                        SlotFilterData.MAX_SLOT)
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

        Component line1 = Component.translatable(slotInfoPage == 0
                ? "gui.logisticsnetworks.filter.slot.info.export.p1"
                : "gui.logisticsnetworks.filter.slot.info.import.p1");
        Component line2 = Component.translatable(slotInfoPage == 0
                ? "gui.logisticsnetworks.filter.slot.info.export.p2"
                : "gui.logisticsnetworks.filter.slot.info.import.p2");

        int navY = y + h - 16;
        int textY = y + pad + 11;
        int textW = w - pad * 2;
        int maxTextBottom = navY - 2;
        for (var part : font.split(line1, textW)) {
            if (textY + 8 > maxTextBottom) {
                break;
            }
            g.drawString(font, part, x + pad, textY, COL_GRAY, false);
            textY += 9;
        }
        for (var part : font.split(line2, textW)) {
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
        int cy = topPos + 50;
        int infoBtnX = leftPos + imageWidth - 8 - 12;
        int infoBtnY = topPos + 6;
        int infoBtnSize = 12;
        boolean isFluid = menu.getTargetType() == FilterTargetType.FLUIDS;
        boolean isChemical = menu.getTargetType() == FilterTargetType.CHEMICALS;
        boolean isMb = isFluid || isChemical;

        drawButton(g, infoBtnX, infoBtnY, infoBtnSize, infoBtnSize,
                Component.translatable("gui.logisticsnetworks.filter.info.icon").getString(), mx, my, true);

        renderModeControls(g, mx, my, true);

        g.drawCenteredString(font, Component.translatable("gui.logisticsnetworks.filter.amount.threshold"), cx,
                topPos + 34, COL_WHITE);

        String valueText = isMb ? menu.getAmount() + " mB" : String.valueOf(menu.getAmount());
        g.fill(cx - 35, cy - 2, cx + 35, cy + 10, COL_BTN_BG);
        g.renderOutline(cx - 35, cy - 2, 70, 12, COL_BORDER);
        g.drawCenteredString(font, valueText, cx, cy, COL_ACCENT);

        int btnY = cy + 15;
        if (isMb) {
            String[] negLabels = { "-1000", "-500", "-100" };
            String[] posLabels = { "+1000", "+500", "+100" };
            int[] negCenters = rowBtnCenters(negLabels);
            int[] posCenters = rowBtnCenters(posLabels);
            for (int i = 0; i < 3; i++)
                drawAmountButton(g, negCenters[i], btnY, negLabels[i], mx, my);
            for (int i = 0; i < 3; i++)
                drawAmountButton(g, posCenters[i], btnY + 18, posLabels[i], mx, my);
        } else {
            String[] labels = { "-64", "-10", "-1", "+1", "+10", "+64" };
            int[] centers = amountBtnCenters(labels);
            for (int i = 0; i < 6; i++)
                drawAmountButton(g, centers[i], btnY, labels[i], mx, my);
        }

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
        textY = drawWrappedInfoLine(g, Component.translatable(line1Key), x + pad, textY, textW, maxTextBottom);
        textY = drawWrappedInfoLine(g, Component.translatable(line2Key), x + pad, textY, textW, maxTextBottom);
        drawWrappedInfoLine(g, Component.translatable(line3Key), x + pad, textY, textW, maxTextBottom);

        int prevX = x + w - 40;
        int nextX = x + w - 22;
        drawButton(g, prevX, navY, 14, 12, "<", mx, my, amountInfoPage > 0);
        drawButton(g, nextX, navY, 14, 12, ">", mx, my, amountInfoPage < 1);

        g.pose().popPose();
    }

    private int drawWrappedInfoLine(GuiGraphics g, Component line, int x, int y, int width, int maxBottom) {
        int nextY = y;
        for (var part : font.split(line, width)) {
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

        renderModeControls(g, mx, my, true);

        g.drawCenteredString(font, Component.translatable("gui.logisticsnetworks.filter.durability.limit"), cx,
                topPos + 20, COL_WHITE);

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

    private int[] amountBtnCenters(String[] labels) {
        int PAD = 5;
        int BTN_GAP = 2;
        int GROUP_GAP = 6;
        int cx = leftPos + GUI_WIDTH / 2;
        int[] centers = new int[6];
        int rightEdge = cx - GROUP_GAP / 2;
        for (int i = 2; i >= 0; i--) {
            int w = Math.max(24, font.width(labels[i]) + PAD * 2);
            centers[i] = rightEdge - w / 2;
            rightEdge -= w + BTN_GAP;
        }
        int leftEdge = cx + GROUP_GAP / 2;
        for (int i = 3; i < 6; i++) {
            int w = Math.max(24, font.width(labels[i]) + PAD * 2);
            centers[i] = leftEdge + w / 2;
            leftEdge += w + BTN_GAP;
        }
        return centers;
    }

    private void renderPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, COL_BG);
        g.renderOutline(x, y, w, h, COL_BORDER);
    }

    private int[] rowBtnCenters(String[] labels) {
        int PAD = 5;
        int GAP = 6;
        int cx = leftPos + GUI_WIDTH / 2;
        int totalW = 0;
        int[] widths = new int[labels.length];
        for (int i = 0; i < labels.length; i++) {
            widths[i] = Math.max(24, font.width(labels[i]) + PAD * 2);
            totalW += widths[i];
        }
        totalW += GAP * (labels.length - 1);
        int[] centers = new int[labels.length];
        int x = cx - totalW / 2;
        for (int i = 0; i < labels.length; i++) {
            centers[i] = x + widths[i] / 2;
            x += widths[i] + GAP;
        }
        return centers;
    }

    private void renderModeControls(GuiGraphics g, int mx, int my, boolean showTargetType) {
        int btnH = 12;
        int btnY = topPos + 6;
        int rightEdge = leftPos + imageWidth - 8;

        String modeLabel = menu.isBlacklistMode()
                ? tr("gui.logisticsnetworks.filter.mode.blacklist")
                : tr("gui.logisticsnetworks.filter.mode.whitelist");
        int modeBtnW = Math.max(48, font.width(modeLabel) + 8);
        int modeBtnX = rightEdge - modeBtnW;
        drawButton(g, modeBtnX, btnY, modeBtnW, btnH, modeLabel, mx, my, true);

        if (showTargetType) {
            String typeLabel;
            if (menu.getTargetType() == FilterTargetType.CHEMICALS) {
                typeLabel = tr("gui.logisticsnetworks.filter.target.chemicals");
            } else if (menu.getTargetType() == FilterTargetType.FLUIDS) {
                typeLabel = tr("gui.logisticsnetworks.filter.target.fluids");
            } else {
                typeLabel = tr("gui.logisticsnetworks.filter.target.items");
            }
            int typeBtnW = Math.max(40, font.width(typeLabel) + 8);
            int typeBtnX = modeBtnX - typeBtnW - 4;
            drawButton(g, typeBtnX, btnY, typeBtnW, btnH, typeLabel, mx, my, true);
        }
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
        int w = Math.max(24, font.width(label) + 10);
        drawButton(g, x - w / 2, y, w, 14, label, mx, my, true);
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

    private void renderChemicalGhostItems(GuiGraphics g) {
        for (int i = 0; i < menu.getFilterSlots(); i++) {
            String chemId = menu.getChemicalFilter(i);
            if (chemId != null) {
                var slot = menu.slots.get(i);
                int x = leftPos + slot.x;
                int y = topPos + slot.y;
                renderChemicalStack(g, chemId, x, y);
            }
        }
    }

    private void renderChemicalStack(GuiGraphics g, String chemId, int x, int y) {
        ResourceLocation iconPath = MekanismCompat.getChemicalIcon(chemId);
        if (iconPath == null)
            return;

        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(iconPath);
        int color = MekanismCompat.getChemicalTint(chemId);

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
        else if (menu.isSlotMode())
            handled = handleSlotClick(mx, my, btn);
        else if (menu.isAmountMode())
            handled = handleAmountClick(mx, my, btn);
        else if (menu.isDurabilityMode())
            handled = handleDurabilityClick(mx, my, btn);
        else if (menu.isNameMode())
            handled = handleNameClick(mx, my, btn);
        else {
            // Standard mode sub-mode interception
            if (tagEditSlot >= 0) {
                handled = handleTagSubModeClick(mx, my, btn);
                if (!handled) {
                    // click outside = close
                    closeTagSubMode();
                    return true;
                }
                return true;
            }
            if (nbtEditSlot >= 0) {
                handled = handleNbtSubModeClick(mx, my, btn);
                if (!handled) {
                    closeNbtSubMode();
                    return true;
                }
                return true;
            }

            // Ctrl+click to enter sub-modes
            if (hasControlDown()) {
                int hoveredSlot = getHoveredFilterSlot((int) mx, (int) my);
                if (hoveredSlot >= 0) {
                    if (btn == 0) {
                        enterTagSubMode(hoveredSlot);
                        return true;
                    } else if (btn == 1) {
                        enterNbtSubMode(hoveredSlot);
                        return true;
                    }
                }
            }

            handled = handleModeControlClick(mx, my, true);
        }

        if (!handled) {
            if (isDropdownOpen && !isHoveringDropdown(mx, my)) {
                isDropdownOpen = false;
                return true;
            }
            return super.mouseClicked(mx, my, btn);
        }
        return true;
    }

    private boolean handleModeControlClick(double mx, double my, boolean hasTargetType) {
        int btnH = 12;
        int btnY = topPos + 6;
        int rightEdge = leftPos + imageWidth - 8;

        String modeLabel = menu.isBlacklistMode()
                ? tr("gui.logisticsnetworks.filter.mode.blacklist")
                : tr("gui.logisticsnetworks.filter.mode.whitelist");
        int modeBtnW = Math.max(48, font.width(modeLabel) + 8);
        int modeBtnX = rightEdge - modeBtnW;

        if (isHovering(modeBtnX, btnY, modeBtnW, btnH, (int) mx, (int) my)) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
            return true;
        }

        if (hasTargetType) {
            String typeLabel;
            if (menu.getTargetType() == FilterTargetType.CHEMICALS) {
                typeLabel = tr("gui.logisticsnetworks.filter.target.chemicals");
            } else if (menu.getTargetType() == FilterTargetType.FLUIDS) {
                typeLabel = tr("gui.logisticsnetworks.filter.target.fluids");
            } else {
                typeLabel = tr("gui.logisticsnetworks.filter.target.items");
            }
            int typeBtnW = Math.max(40, font.width(typeLabel) + 8);
            int typeBtnX = modeBtnX - typeBtnW - 4;

            if (isHovering(typeBtnX, btnY, typeBtnW, btnH, (int) mx, (int) my)) {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 8);
                }
                return true;
            }
        }

        return false;
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

        if (handleModeControlClick(mx, my, true))
            return true;

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

        if (handleModeControlClick(mx, my, false))
            return true;

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

    private boolean handleAmountClick(double mx, double my, int btn) {
        int cx = leftPos + GUI_WIDTH / 2;
        int infoBtnX = leftPos + imageWidth - 8 - 12;
        int infoBtnY = topPos + 6;
        int infoBtnSize = 12;

        if (isHovering(infoBtnX, infoBtnY, infoBtnSize, infoBtnSize, (int) mx, (int) my)) {
            amountInfoOpen = !amountInfoOpen;
            return true;
        }

        if (handleModeControlClick(mx, my, true))
            return true;

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

            // Click outside info panel = close it
            amountInfoOpen = false;
            return true;
        }

        boolean isMb = menu.getTargetType() == FilterTargetType.FLUIDS
                || menu.getTargetType() == FilterTargetType.CHEMICALS;
        int cy = topPos + 50 + 15;
        if (isMb) {
            String[] negLabels = { "-1000", "-500", "-100" };
            String[] posLabels = { "+1000", "+500", "+100" };
            int[] negDeltas = { -1000, -500, -100 };
            int[] posDeltas = { 1000, 500, 100 };
            int[] negCenters = rowBtnCenters(negLabels);
            int[] posCenters = rowBtnCenters(posLabels);
            for (int i = 0; i < 3; i++)
                if (checkAmountBtn(mx, my, negCenters[i], cy, negDeltas[i], negLabels[i]))
                    return true;
            for (int i = 0; i < 3; i++)
                if (checkAmountBtn(mx, my, posCenters[i], cy + 18, posDeltas[i], posLabels[i]))
                    return true;
        } else {
            String[] labels = { "-64", "-10", "-1", "+1", "+10", "+64" };
            int[] deltas = { -64, -10, -1, 1, 10, 64 };
            int[] centers = amountBtnCenters(labels);
            for (int i = 0; i < 6; i++)
                if (checkAmountBtn(mx, my, centers[i], cy, deltas[i], labels[i]))
                    return true;
        }

        return false;
    }

    private boolean handleSlotClick(double mx, double my, int btn) {
        int contentX = leftPos + 8;
        int inputY = topPos + 34;
        int contentW = imageWidth - 16;
        int infoBtnX = leftPos + imageWidth - 8 - 12;
        int infoBtnY = topPos + 6;
        int infoBtnSize = 12;

        if (isHovering(infoBtnX, infoBtnY, infoBtnSize, infoBtnSize, (int) mx, (int) my)) {
            slotInfoOpen = !slotInfoOpen;
            return true;
        }

        if (handleModeControlClick(mx, my, false))
            return true;

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

            // Click outside info panel = close it
            slotInfoOpen = false;
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

        if (handleModeControlClick(mx, my, true))
            return true;

        if (isHovering(cx - 50, cy, 20, 12, (int) mx, (int) my)) {
            return true;
        }

        int btnY = cy + 20;
        String[] lbls = { "-64", "-10", "-1", "+1", "+10", "+64" };
        int[] durs = { -64, -10, -1, 1, 10, 64 };
        int[] durCenters = amountBtnCenters(lbls);
        for (int i = 0; i < 6; i++) {
            if (checkAmountBtn(mx, my, durCenters[i], btnY, durs[i], lbls[i]))
                return true;
        }

        return false;
    }

    private boolean checkAmountBtn(double mx, double my, int cx, int y, int delta, String label) {
        int w = Math.max(24, font.width(label) + 10);
        if (isHovering(cx - w / 2, y, w, 14, (int) mx, (int) my)) {
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
        String normalizedTag = FilterTagUtil.normalizeTag(tag);
        menu.setSelectedTag(normalizedTag);
        PacketDistributor.sendToServer(new ModifyFilterTagPayload(normalizedTag == null ? "" : normalizedTag, false));
    }

    private void sendTagRemove(String tag) {
        String normalizedTag = FilterTagUtil.normalizeTag(tag);
        menu.setSelectedTag(null);
        PacketDistributor.sendToServer(new ModifyFilterTagPayload(normalizedTag == null ? "" : normalizedTag, true));
    }

    private void sendModUpdate(String mod) {
        menu.setSelectedMod(mod == null || mod.isBlank() ? null : mod.trim());
        PacketDistributor.sendToServer(new ModifyFilterModPayload(mod == null ? "" : mod, false));
    }

    private void sendModRemove(String mod) {
        menu.setSelectedMod(null);
        PacketDistributor.sendToServer(new ModifyFilterModPayload(mod == null ? "" : mod, true));
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

    private void sendNameUpdate(String name) {
        PacketDistributor.sendToServer(new SetNameFilterPayload(name == null ? "" : name));
    }

    private void renderNameMode(GuiGraphics g, int mx, int my) {
        int contentX = leftPos + 8;
        int contentW = imageWidth - 16;
        int btnRowY = topPos + 20;
        int inputY = topPos + 38;
        int activeY = topPos + 56;
        int hintY = topPos + 66;

        renderNameButtons(g, mx, my, btnRowY);

        manualInputBox.setX(contentX);
        manualInputBox.setY(inputY);
        manualInputBox.setWidth(contentW);

        String value = menu.getNameFilter();
        String display = value.isEmpty()
                ? Component.translatable("gui.logisticsnetworks.filter.name.none").getString()
                : value;
        String activeLine = Component.translatable("gui.logisticsnetworks.filter.name.active", display).getString();
        g.drawString(font, font.plainSubstrByWidth(activeLine, contentW), contentX, activeY, COL_ACCENT, false);

        // Show invalid regex warning
        if (!value.isEmpty() && !NameFilterData.isValidRegex(value)) {
            String warning = Component.translatable("gui.logisticsnetworks.filter.name.invalid_regex").getString();
            g.drawString(font, warning, contentX, hintY, 0xFFFF5555, false);
        } else {
            String hintLine = Component.translatable("gui.logisticsnetworks.filter.name.input_hint").getString();
            g.drawString(font, font.plainSubstrByWidth(hintLine, contentW), contentX, hintY, COL_GRAY, false);
        }
    }

    private void renderNameButtons(GuiGraphics g, int mx, int my, int btnY) {
        int btnH = 12;
        int leftEdge = leftPos + 8;

        // Scope button
        NameMatchScope scope = menu.getNameMatchScope();
        String scopeLabel;
        if (scope == NameMatchScope.TOOLTIP) {
            scopeLabel = tr("gui.logisticsnetworks.filter.name.scope.tooltip");
        } else if (scope == NameMatchScope.BOTH) {
            scopeLabel = tr("gui.logisticsnetworks.filter.name.scope.both");
        } else {
            scopeLabel = tr("gui.logisticsnetworks.filter.name.scope.name");
        }
        int scopeBtnW = Math.max(40, font.width(scopeLabel) + 8);
        drawButton(g, leftEdge, btnY, scopeBtnW, btnH, scopeLabel, mx, my, true);

        // Target type button
        String typeLabel;
        if (menu.getTargetType() == FilterTargetType.CHEMICALS) {
            typeLabel = tr("gui.logisticsnetworks.filter.target.chemicals");
        } else if (menu.getTargetType() == FilterTargetType.FLUIDS) {
            typeLabel = tr("gui.logisticsnetworks.filter.target.fluids");
        } else {
            typeLabel = tr("gui.logisticsnetworks.filter.target.items");
        }
        int typeBtnW = Math.max(40, font.width(typeLabel) + 8);
        int typeBtnX = leftEdge + scopeBtnW + 4;
        drawButton(g, typeBtnX, btnY, typeBtnW, btnH, typeLabel, mx, my, true);

        // Whitelist/Blacklist button
        String modeLabel = menu.isBlacklistMode()
                ? tr("gui.logisticsnetworks.filter.mode.blacklist")
                : tr("gui.logisticsnetworks.filter.mode.whitelist");
        int modeBtnW = Math.max(48, font.width(modeLabel) + 8);
        int modeBtnX = typeBtnX + typeBtnW + 4;
        drawButton(g, modeBtnX, btnY, modeBtnW, btnH, modeLabel, mx, my, true);
    }

    private boolean handleNameClick(double mx, double my, int btn) {
        int contentX = leftPos + 8;
        int inputY = topPos + 38;
        int contentW = imageWidth - 16;

        if (handleNameButtonsClick(mx, my))
            return true;

        if (btn == 1 && isHovering(contentX, inputY, contentW, 14, (int) mx, (int) my)) {
            manualInputBox.setValue("");
            sendNameUpdate("");
            return true;
        }

        return false;
    }

    private boolean handleNameButtonsClick(double mx, double my) {
        int btnH = 12;
        int btnY = topPos + 20;
        int leftEdge = leftPos + 8;

        // Scope button
        NameMatchScope scope = menu.getNameMatchScope();
        String scopeLabel;
        if (scope == NameMatchScope.TOOLTIP) {
            scopeLabel = tr("gui.logisticsnetworks.filter.name.scope.tooltip");
        } else if (scope == NameMatchScope.BOTH) {
            scopeLabel = tr("gui.logisticsnetworks.filter.name.scope.both");
        } else {
            scopeLabel = tr("gui.logisticsnetworks.filter.name.scope.name");
        }
        int scopeBtnW = Math.max(40, font.width(scopeLabel) + 8);

        if (isHovering(leftEdge, btnY, scopeBtnW, btnH, (int) mx, (int) my)) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 9);
            }
            return true;
        }

        // Target type button
        String typeLabel;
        if (menu.getTargetType() == FilterTargetType.CHEMICALS) {
            typeLabel = tr("gui.logisticsnetworks.filter.target.chemicals");
        } else if (menu.getTargetType() == FilterTargetType.FLUIDS) {
            typeLabel = tr("gui.logisticsnetworks.filter.target.fluids");
        } else {
            typeLabel = tr("gui.logisticsnetworks.filter.target.items");
        }
        int typeBtnW = Math.max(40, font.width(typeLabel) + 8);
        int typeBtnX = leftEdge + scopeBtnW + 4;

        if (isHovering(typeBtnX, btnY, typeBtnW, btnH, (int) mx, (int) my)) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 8);
            }
            return true;
        }

        // Whitelist/Blacklist button
        String modeLabel = menu.isBlacklistMode()
                ? tr("gui.logisticsnetworks.filter.mode.blacklist")
                : tr("gui.logisticsnetworks.filter.mode.whitelist");
        int modeBtnW = Math.max(48, font.width(modeLabel) + 8);
        int modeBtnX = typeBtnX + typeBtnW + 4;

        if (isHovering(modeBtnX, btnY, modeBtnW, btnH, (int) mx, (int) my)) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
            return true;
        }

        return false;
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
        } else if (menu.isNameMode()) {
            sendNameUpdate(val);
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
            if (nbtEditingRuleIndex >= 0) {
                cancelNbtValueEdit();
                return true;
            }
            if (tagEditSlot >= 0) {
                closeTagSubMode();
                return true;
            }
            if (nbtEditSlot >= 0) {
                closeNbtSubMode();
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }

        if (nbtValueEditBox != null && nbtValueEditBox.isFocused()) {
            if (key == 257) {
                commitNbtValueEdit();
                return true;
            }
            nbtValueEditBox.keyPressed(key, scan, modifiers);
            return true;
        }

        // Tag sub-mode input
        if (tagInputBox != null && tagInputBox.isFocused()) {
            if (key == 257) {
                commitTagInput();
                return true;
            }
            tagInputBox.keyPressed(key, scan, modifiers);
            return true;
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
    public boolean charTyped(char c, int modifiers) {
        if (nbtValueEditBox != null && nbtValueEditBox.isFocused()) {
            return nbtValueEditBox.charTyped(c, modifiers);
        }
        if (tagInputBox != null && tagInputBox.isFocused()) {
            return tagInputBox.charTyped(c, modifiers);
        }
        if (manualInputBox.isFocused()) {
            return manualInputBox.charTyped(c, modifiers);
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (nbtEditSlot >= 0) {
            int maxScroll = getNbtSubModeMaxScroll();
            if (sy > 0 && nbtListScrollOffset > 0)
                nbtListScrollOffset--;
            else if (sy < 0 && nbtListScrollOffset < maxScroll)
                nbtListScrollOffset++;
            return true;
        }

        if (subModeDropdownOpen) {
            if (sy > 0 && subModeScrollOffset > 0)
                subModeScrollOffset--;
            else if (sy < 0)
                subModeScrollOffset++;
            return true;
        }

        if (isDropdownOpen) {
            if (sy > 0 && listScrollOffset > 0)
                listScrollOffset--;
            else if (sy < 0)
                listScrollOffset++;
            return true;
        }

        if (!menu.isAmountMode() && !menu.isTagMode() && !menu.isModMode()
                && !menu.isDurabilityMode() && !menu.isSlotMode() && !menu.isNameMode()) {
            int hoveredSlot = getHoveredFilterSlot((int) mx, (int) my);
            if (hoveredSlot >= 0 && hasEntryInSlot(hoveredSlot)) {
                int current = menu.getEntryAmount(hoveredSlot);
                int next;
                if (hasAltDown()) {
                    next = sy > 0 ? getMaxAmountForType(menu.getTargetType()) : (current > 0 ? 1 : 0);
                } else {
                    int delta = computeScrollDelta(sy, menu.getTargetType());
                    next = Math.max(0, current + delta);
                }
                if (next != current) {
                    menu.setEntryAmount(null, hoveredSlot, next);
                    PacketDistributor.sendToServer(new SetFilterEntryAmountPayload(hoveredSlot, next));
                }
                return true;
            }
        }

        return super.mouseScrolled(mx, my, sx, sy);
    }

    private int getHoveredFilterSlot(int mx, int my) {
        for (int i = 0; i < menu.getFilterSlots() && i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            int slotX = leftPos + slot.x;
            int slotY = topPos + slot.y;
            if (mx >= slotX && mx < slotX + 16 && my >= slotY && my < slotY + 16) {
                return i;
            }
        }
        return -1;
    }

    private boolean hasEntryInSlot(int slot) {
        if (menu.isTagSlot(slot))
            return true;
        if (menu.getTargetType() == FilterTargetType.FLUIDS) {
            return !menu.getFluidFilter(slot).isEmpty();
        }
        if (menu.getTargetType() == FilterTargetType.CHEMICALS) {
            return menu.getChemicalFilter(slot) != null;
        }
        return slot < menu.slots.size() && !menu.slots.get(slot).getItem().isEmpty();
    }

    private int computeScrollDelta(double scrollDirection, FilterTargetType targetType) {
        int sign = scrollDirection > 0 ? 1 : -1;
        if (targetType == FilterTargetType.FLUIDS || targetType == FilterTargetType.CHEMICALS) {
            if (hasControlDown())
                return sign * 1000;
            if (hasShiftDown())
                return sign * 500;
            return sign * 50;
        }
        if (hasControlDown())
            return sign * 64;
        if (hasShiftDown())
            return sign * 8;
        return sign;
    }

    private int getMaxAmountForType(FilterTargetType targetType) {
        if (targetType == FilterTargetType.FLUIDS || targetType == FilterTargetType.CHEMICALS) {
            return 1_000_000;
        }
        return 1024;
    }

    private void renderTagTooltip(GuiGraphics g, int mx, int my) {
        if (isHovering(getSelectorArrowX(), getSelectorInputY(), 12, 14, mx, my)) {
            g.renderTooltip(font, Component.translatable("gui.logisticsnetworks.filter.tag.select_from_item"), mx, my);
            return;
        }
        var extractor = getExtractorRect();
        if (extractor != null && menu.getExtractorItem().isEmpty()
                && isHovering(extractor[0], extractor[1], 18, 18, mx, my)) {
            g.renderTooltip(font, Component.translatable("gui.logisticsnetworks.filter.selector_hint"), mx, my);
        }
    }

    private void renderModTooltip(GuiGraphics g, int mx, int my) {
        if (isHovering(getSelectorArrowX(), getSelectorInputY(), 12, 14, mx, my)) {
            g.renderTooltip(font, Component.translatable("gui.logisticsnetworks.filter.mod.select_from_item"), mx, my);
            return;
        }
        var extractor = getExtractorRect();
        if (extractor != null && menu.getExtractorItem().isEmpty()
                && isHovering(extractor[0], extractor[1], 18, 18, mx, my)) {
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
                    break;
                }
                String chemId = menu.getChemicalFilter(i);
                if (chemId != null) {
                    Component name = MekanismCompat.getChemicalTextComponent(chemId);
                    if (name != null) {
                        g.renderTooltip(font, name, mx, my);
                    } else {
                        g.renderTooltip(font, Component.literal(chemId), mx, my);
                    }
                    break;
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

    public void setChemicalFilterEntry(Player player, int slot, String chemicalId) {
        if (chemicalId == null || chemicalId.isBlank())
            return;
        PacketDistributor.sendToServer(
                new SetFilterChemicalEntryPayload(slot, chemicalId));
        menu.setChemicalFilterEntry(player, slot, chemicalId);
    }

    public void setItemFilterEntry(Player player, int slot, ItemStack stack) {
        if (stack.isEmpty())
            return;
        PacketDistributor.sendToServer(new SetFilterItemEntryPayload(slot, stack));
        menu.setItemFilterEntry(player, slot, stack);
    }

    public boolean acceptsFluidSelectorGhostIngredient() {
        return menu.isTagMode() || menu.isModMode();
    }

    public boolean acceptsItemSelectorGhostIngredient() {
        return menu.isTagMode() || menu.isModMode();
    }

    public boolean supportsGhostIngredientTargets() {
        return !menu.isTagMode() && !menu.isModMode() && !menu.isAmountMode()
                && !menu.isDurabilityMode() && !menu.isSlotMode() && !menu.isNameMode();
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

    public void setGhostChemicalFilterEntry(int slot, String chemicalId) {
        setChemicalFilterEntry(minecraft.player, slot, chemicalId);
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
            this.selectorGhostChemicalId = null;
            this.selectorGhostChemicalTags = null;
            this.selectorGhostChemicalName = null;
        }
    }

    public void setSelectorGhostChemical(String chemId, List<String> tags, Component name) {
        if (menu.getExtractorSlotIndex() >= 0) {
            menu.slots.get(menu.getExtractorSlotIndex()).set(ItemStack.EMPTY);
            this.selectorGhostChemicalId = chemId;
            this.selectorGhostChemicalTags = tags;
            this.selectorGhostChemicalName = name;
            // Force tag list refresh
            this.cachedTags.clear();
            this.cachedMods.clear();
            this.lastExtractorItem = ItemStack.EMPTY;
            this.lastTargetType = null;
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
        return topPos + 48;
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

    private String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private void enterTagSubMode(int slot) {
        tagEditSlot = slot;
        nbtEditSlot = -1;
        subModeScrollOffset = 0;
        subModeDropdownOpen = true;
        cachedSlotTags.clear();

        ItemStack slotItem = getSlotItemForSubMode(slot);
        if (!slotItem.isEmpty()) {
            var item = slotItem.getItem();
            BuiltInRegistries.ITEM.getTagNames().forEach(tagKey -> {
                var holders = BuiltInRegistries.ITEM.getTag(tagKey);
                if (holders.isPresent()
                        && holders.get().stream().anyMatch(h -> h.value() == item)) {
                    cachedSlotTags.add(tagKey.location().toString());
                }
            });
        }
        Collections.sort(cachedSlotTags);

        String existing = menu.getEntryTag(slot);
        if (existing != null && !cachedSlotTags.contains(existing)) {
            cachedSlotTags.add(0, existing);
        }

        tagInputBox.setValue(existing != null ? existing : "");
        tagInputBox.setVisible(true);
        tagInputBox.setFocused(true);
    }

    private void enterNbtSubMode(int slot) {
        nbtEditSlot = slot;
        tagEditSlot = -1;
        subModeScrollOffset = 0;
        subModeDropdownOpen = false;
        nbtPendingOperator = "=";
        nbtListScrollOffset = 0;
        cachedSlotNbtEntries.clear();

        if (minecraft != null && minecraft.player != null) {
            if (menu.getTargetType() == FilterTargetType.FLUIDS) {
                FluidStack fluid = menu.getFluidFilter(slot);
                if (!fluid.isEmpty()) {
                    cachedSlotNbtEntries.addAll(NbtFilterData.extractEntries(
                            fluid, minecraft.player.level().registryAccess()));
                }
            } else {
                ItemStack slotItem = getSlotItemForSubMode(slot);
                if (!slotItem.isEmpty()) {
                    cachedSlotNbtEntries.addAll(NbtFilterData.extractEntries(
                            slotItem, minecraft.player.level().registryAccess()));
                } else {
                    cachedSlotNbtEntries.addAll(NbtFilterData.getDefaultEntries());
                }
            }
        }
    }

    private ItemStack getSlotItemForSubMode(int slot) {
        if (menu.isTagSlot(slot)) {
            return ItemStack.EMPTY;
        }
        if (minecraft != null && minecraft.player != null) {
            return FilterItemData.getEntry(menu.getOpenedStack(), slot, minecraft.player.level().registryAccess());
        }
        return ItemStack.EMPTY;
    }

    private int getTagSubModeVisibleRows() {
        int panelY = topPos + 20;
        int panelH = menu.getPlayerInventoryY() - 24;
        int listY = panelY + 48;
        int listBottom = panelY + panelH - 4;
        return Math.max(1, (listBottom - listY) / LIST_ROW_H);
    }

    private int getTagSubModeMaxScroll() {
        return Math.max(0, cachedSlotTags.size() - getTagSubModeVisibleRows());
    }

    private void clampTagSubModeScrollOffset() {
        subModeScrollOffset = Math.max(0, Math.min(subModeScrollOffset, getTagSubModeMaxScroll()));
    }

    private void renderTagSubMode(GuiGraphics g, int mx, int my) {
        int panelX = leftPos + 4;
        int panelY = topPos + 20;
        int panelW = imageWidth - 8;
        int panelH = menu.getPlayerInventoryY() - 24;
        clampTagSubModeScrollOffset();

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0101010);
        g.renderOutline(panelX, panelY, panelW, panelH, COL_ACCENT);

        String title = "Tag for slot " + tagEditSlot;
        g.drawString(font, title, panelX + 4, panelY + 4, COL_WHITE, false);

        String current = menu.getEntryTag(tagEditSlot);
        String display = current != null ? "#" + current : "None";
        g.drawString(font, display, panelX + 4, panelY + 16, COL_ACCENT, false);

        int clearW = 40;
        int clearX = panelX + panelW - clearW - 4;
        int clearY = panelY + 4;
        boolean hoverClear = isHovering(clearX, clearY, clearW, 12, mx, my);
        g.fill(clearX, clearY, clearX + clearW, clearY + 12,
                hoverClear ? COL_BTN_HOVER : COL_BTN_BG);
        g.renderOutline(clearX, clearY, clearW, 12,
                hoverClear ? COL_WHITE : COL_BTN_BORDER);
        g.drawCenteredString(font, "Clear", clearX + clearW / 2, clearY + 2,
                hoverClear ? COL_WHITE : COL_GRAY);

        int inputY = panelY + 30;
        int inputW = panelW - 60;
        tagInputBox.setX(panelX + 4);
        tagInputBox.setY(inputY);
        tagInputBox.setWidth(inputW);
        tagInputBox.render(g, mx, my, 0);

        int doneW = 40;
        int doneX = panelX + panelW - doneW - 4;
        drawButton(g, doneX, inputY, doneW, 14, "Done", mx, my, true);

        int listY = inputY + 18;
        int maxVisibleRows = getTagSubModeVisibleRows();
        int visibleRows = Math.min(maxVisibleRows, cachedSlotTags.size());
        int listH = maxVisibleRows * LIST_ROW_H;
        boolean scrollable = cachedSlotTags.size() > maxVisibleRows;
        int listX = panelX + 4;
        int listW = panelW - 8;
        int rowW = scrollable ? listW - SUBMODE_SCROLLBAR_W - SUBMODE_SCROLLBAR_GAP : listW;
        int startIdx = subModeScrollOffset;
        int endIdx = Math.min(startIdx + maxVisibleRows, cachedSlotTags.size());

        g.fill(listX, listY, listX + listW, listY + listH, 0x40000000);
        g.renderOutline(listX, listY, listW, listH, COL_BORDER);

        for (int i = startIdx; i < endIdx; i++) {
            int rowY = listY + (i - startIdx) * LIST_ROW_H;
            String tag = cachedSlotTags.get(i);
            boolean selected = Objects.equals(tag, current);
            boolean hovered = mx >= listX && mx < listX + rowW
                    && my >= rowY && my < rowY + LIST_ROW_H;

            if (selected)
                g.fill(listX + 1, rowY, listX + rowW - 1, rowY + LIST_ROW_H, COL_SELECTED);
            else if (hovered)
                g.fill(listX + 1, rowY, listX + rowW - 1, rowY + LIST_ROW_H, COL_HOVER);

            String text = scrollText(tag, rowW - 6, i);
            g.drawString(font, text, listX + 3, rowY + 2,
                    selected ? COL_ACCENT : COL_WHITE, false);
        }

        if (scrollable) {
            int maxScroll = getTagSubModeMaxScroll();
            int scrollbarX = listX + rowW + SUBMODE_SCROLLBAR_GAP;
            int thumbH = Math.max(8, (listH * maxVisibleRows) / cachedSlotTags.size());
            int thumbTravel = Math.max(0, listH - thumbH);
            int thumbY = maxScroll <= 0 ? listY : listY + (subModeScrollOffset * thumbTravel) / maxScroll;

            g.fill(scrollbarX, listY, scrollbarX + SUBMODE_SCROLLBAR_W, listY + listH, COL_BTN_BG);
            g.renderOutline(scrollbarX, listY, SUBMODE_SCROLLBAR_W, listH, COL_BTN_BORDER);
            g.fill(scrollbarX + 1, thumbY, scrollbarX + SUBMODE_SCROLLBAR_W - 1, thumbY + thumbH, COL_ACCENT);

            if (subModeScrollOffset > 0) {
                g.fill(scrollbarX + 1, listY + 1, scrollbarX + SUBMODE_SCROLLBAR_W - 1, listY + 2, COL_WHITE);
            }
            if (subModeScrollOffset < maxScroll) {
                g.fill(scrollbarX + 1, listY + listH - 2, scrollbarX + SUBMODE_SCROLLBAR_W - 1,
                        listY + listH - 1, COL_WHITE);
            }
        }

        if (cachedSlotTags.isEmpty()) {
            g.drawString(font, "No tags available", listX + 3, listY + 2, COL_GRAY, false);
        }

        g.pose().popPose();
    }

    private boolean handleTagSubModeClick(double mx, double my, int btn) {
        int panelX = leftPos + 4;
        int panelY = topPos + 20;
        int panelW = imageWidth - 8;
        int panelH = menu.getPlayerInventoryY() - 24;

        if (!isHovering(panelX, panelY, panelW, panelH, (int) mx, (int) my)) {
            return false;
        }

        int clearW = 40;
        int clearX = panelX + panelW - clearW - 4;
        int clearY = panelY + 4;
        if (isHovering(clearX, clearY, clearW, 12, (int) mx, (int) my)) {
            PacketDistributor.sendToServer(new SetFilterEntryTagPayload(tagEditSlot, ""));
            menu.clearEntryTag(tagEditSlot);
            closeTagSubMode();
            return true;
        }

        int inputY = panelY + 30;
        int doneW = 40;
        int doneX = panelX + panelW - doneW - 4;
        if (isHovering(doneX, inputY, doneW, 14, (int) mx, (int) my)) {
            commitTagInput();
            return true;
        }

        if (tagInputBox != null && tagInputBox.isVisible()) {
            int inputW = panelW - 60;
            if (isHovering(panelX + 4, inputY, inputW, 14, (int) mx, (int) my)) {
                tagInputBox.mouseClicked(mx, my, btn);
                return true;
            }
        }

        int listY = inputY + 18;
        int maxVisibleRows = getTagSubModeVisibleRows();
        int visibleRows = Math.min(maxVisibleRows, cachedSlotTags.size());
        int listH = maxVisibleRows * LIST_ROW_H;
        boolean scrollable = cachedSlotTags.size() > maxVisibleRows;
        int listX = panelX + 4;
        int listW = panelW - 8;
        int rowW = scrollable ? listW - SUBMODE_SCROLLBAR_W - SUBMODE_SCROLLBAR_GAP : listW;
        int startIdx = subModeScrollOffset;
        int endIdx = Math.min(startIdx + maxVisibleRows, cachedSlotTags.size());

        if (scrollable) {
            int scrollbarX = listX + rowW + SUBMODE_SCROLLBAR_GAP;
            if (isHovering(scrollbarX, listY, SUBMODE_SCROLLBAR_W, listH, (int) mx, (int) my)) {
                int maxScroll = getTagSubModeMaxScroll();
                int thumbH = Math.max(8, (listH * maxVisibleRows) / cachedSlotTags.size());
                int thumbTravel = Math.max(0, listH - thumbH);
                int thumbY = maxScroll <= 0 ? listY : listY + (subModeScrollOffset * thumbTravel) / maxScroll;
                if (my < thumbY) {
                    subModeScrollOffset = Math.max(0, subModeScrollOffset - 1);
                } else if (my >= thumbY + thumbH) {
                    subModeScrollOffset = Math.min(maxScroll, subModeScrollOffset + 1);
                }
                return true;
            }
        }

        for (int i = startIdx; i < endIdx; i++) {
            int rowY = listY + (i - startIdx) * LIST_ROW_H;
            if (mx >= listX && mx < listX + rowW
                    && my >= rowY && my < rowY + LIST_ROW_H) {
                String tag = cachedSlotTags.get(i);
                PacketDistributor.sendToServer(new SetFilterEntryTagPayload(tagEditSlot, tag));
                menu.setEntryTag(null, tagEditSlot, tag);
                closeTagSubMode();
                return true;
            }
        }

        return true;
    }

    private void commitTagInput() {
        if (tagInputBox == null || tagEditSlot < 0)
            return;
        String rawValue = tagInputBox.getValue();
        String normalizedTag = FilterTagUtil.normalizeTag(rawValue);
        if (normalizedTag != null) {
            PacketDistributor.sendToServer(new SetFilterEntryTagPayload(tagEditSlot, normalizedTag));
            menu.setEntryTag(null, tagEditSlot, normalizedTag);
        }
        closeTagSubMode();
    }

    private void closeTagSubMode() {
        tagEditSlot = -1;
        subModeDropdownOpen = false;
        if (tagInputBox != null) {
            tagInputBox.setVisible(false);
            tagInputBox.setFocused(false);
        }
    }

    private int getNbtPanelX() {
        int panelW = getNbtPanelW();
        return (width - panelW) / 2;
    }

    private int getNbtPanelW() {
        return imageWidth + 50;
    }

    private void renderNbtSubMode(GuiGraphics g, int mx, int my) {
        int panelW = getNbtPanelW();
        int panelX = getNbtPanelX();
        int panelY = topPos + 20;
        int panelH = menu.getPlayerInventoryY() - 24;

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF101010);
        g.renderOutline(panelX, panelY, panelW, panelH, 0xFFFFAA00);

        g.drawString(font, tr("gui.logisticsnetworks.filter.nbt.slot_title", nbtEditSlot + 1),
                panelX + 4, panelY + 4, COL_WHITE, false);

        // Clear button
        int clearW = 30;
        int clearX = panelX + panelW - clearW - 4;
        int clearY = panelY + 4;
        drawButton(g, clearX, clearY, clearW, 10, tr("gui.logisticsnetworks.filter.nbt.clear"), mx, my, true);

        // NBT entries list
        List<FilterItemData.SlotNbtRule> activeRules = menu.getSlotNbtRules(nbtEditSlot);
        int listX = panelX + 4;
        int listY = panelY + 16;
        int listW = panelW - 8;
        int listH = panelH - 34;
        renderNbtEntryList(g, listX, listY, listW, listH, activeRules, mx, my);

        // Match mode button
        boolean matchAny = menu.isSlotNbtMatchAny(nbtEditSlot);
        String matchLabel = matchAny
                ? tr("gui.logisticsnetworks.filter.nbt.match_any")
                : tr("gui.logisticsnetworks.filter.nbt.match_all");
        int matchW = font.width(matchLabel) + 12;
        int matchX = panelX + 4;
        int matchY = panelY + panelH - 16;
        drawButton(g, matchX, matchY, matchW, 14, matchLabel, mx, my, true);

        g.pose().popPose();
    }

    private static final int NBT_INDICATOR_W = 8;
    private static final int NBT_OP_BTN_W = 18;

    private int getNbtColW(int rowW) {
        return (rowW - NBT_INDICATOR_W - NBT_OP_BTN_W - 4) / 2;
    }

    private boolean isBooleanValue(String displayVal) {
        return "true".equals(displayVal) || "false".equals(displayVal);
    }

    private void renderNbtEntryList(GuiGraphics g, int listX, int listY, int listW, int listH,
            List<FilterItemData.SlotNbtRule> activeRules, int mx, int my) {
        int totalEntries = cachedSlotNbtEntries.size();
        int maxRows = Math.max(1, listH / LIST_ROW_H);
        int maxScroll = Math.max(0, totalEntries - maxRows);
        nbtListScrollOffset = Mth.clamp(nbtListScrollOffset, 0, maxScroll);
        int drawH = maxRows * LIST_ROW_H;
        boolean scrollable = totalEntries > maxRows;
        int rowW = scrollable ? listW - SUBMODE_SCROLLBAR_W - SUBMODE_SCROLLBAR_GAP : listW;
        int endIdx = Math.min(nbtListScrollOffset + maxRows, totalEntries);

        g.fill(listX, listY, listX + listW, listY + drawH, 0xFF101010);
        g.renderOutline(listX, listY, listW, drawH, COL_BORDER);

        int colW = getNbtColW(rowW);

        for (int i = nbtListScrollOffset; i < endIdx; i++) {
            int rowY = listY + (i - nbtListScrollOffset) * LIST_ROW_H;
            NbtFilterData.NbtEntry entry = cachedSlotNbtEntries.get(i);
            int ruleIdx = findActiveRuleIndex(activeRules, entry.path());
            boolean active = ruleIdx >= 0;
            boolean hovered = mx >= listX && mx < listX + rowW && my >= rowY && my < rowY + LIST_ROW_H;
            boolean editing = active && nbtEditingRuleIndex == ruleIdx;

            if (active)
                g.fill(listX + 1, rowY, listX + rowW - 1, rowY + LIST_ROW_H, COL_SELECTED);
            else if (hovered)
                g.fill(listX + 1, rowY, listX + rowW - 1, rowY + LIST_ROW_H, COL_HOVER);

            // Toggle indicator
            int dotX = listX + 2;
            int dotY = rowY + (LIST_ROW_H - 5) / 2;
            if (active) {
                g.fill(dotX, dotY, dotX + 5, dotY + 5, COL_ACCENT);
            } else {
                g.renderOutline(dotX, dotY, 5, 5, COL_GRAY);
            }

            FilterItemData.SlotNbtRule activeRule = active ? activeRules.get(ruleIdx) : null;
            String op = active ? activeRule.operator() : "=";

            // Layout: [indicator] [path col] [op col] [value col]
            int pathX = listX + NBT_INDICATOR_W;
            int opX = pathX + colW + 1;
            int valX = opX + NBT_OP_BTN_W + 1;

            // Path column
            String displayPath = formatNbtPath(entry.path());
            g.fill(pathX, rowY, pathX + colW, rowY + LIST_ROW_H, 0xFF080808);
            g.renderOutline(pathX, rowY, colW, LIST_ROW_H, active ? COL_BTN_BORDER : 0xFF222222);
            g.drawString(font, font.plainSubstrByWidth(displayPath, colW - 4),
                    pathX + 2, rowY + 1, active ? COL_ACCENT : COL_WHITE, false);

            // Operator column
            boolean opHover = active && isHovering(opX, rowY, NBT_OP_BTN_W, LIST_ROW_H, mx, my);
            g.fill(opX, rowY, opX + NBT_OP_BTN_W, rowY + LIST_ROW_H,
                    opHover ? COL_BTN_HOVER : COL_BTN_BG);
            g.renderOutline(opX, rowY, NBT_OP_BTN_W, LIST_ROW_H, COL_BTN_BORDER);
            g.drawCenteredString(font, op, opX + NBT_OP_BTN_W / 2, rowY + 1,
                    active ? (opHover ? COL_WHITE : 0xFFFFAA00) : COL_GRAY);

            // Value column
            String displayVal = active
                    ? formatNbtValue(activeRule.value().toString())
                    : formatNbtValue(entry.valueDisplay());

            g.fill(valX, rowY, valX + colW, rowY + LIST_ROW_H, 0xFF080808);
            boolean isBool = isBooleanValue(displayVal);
            g.renderOutline(valX, rowY, colW, LIST_ROW_H,
                    editing ? COL_ACCENT : (active ? COL_BTN_BORDER : 0xFF222222));

            if (editing) {
                nbtValueEditBox.setX(valX + 2);
                nbtValueEditBox.setY(rowY + 1);
                nbtValueEditBox.setWidth(colW - 4);
                nbtValueEditBox.setVisible(true);
                nbtValueEditBox.render(g, mx, my, 0);
            } else {
                int valColor;
                if (isBool) {
                    valColor = "true".equals(displayVal) ? COL_ACCENT : 0xFFFF5555;
                } else {
                    valColor = active ? COL_WHITE : COL_GRAY;
                }
                g.drawString(font, font.plainSubstrByWidth(displayVal, colW - 4), valX + 2, rowY + 1,
                        valColor, false);
            }
        }

        // Hide edit box if editing row scrolled out of view
        if (nbtEditingRuleIndex >= 0) {
            boolean visible = false;
            for (int i = nbtListScrollOffset; i < endIdx; i++) {
                int rIdx = findActiveRuleIndex(activeRules, cachedSlotNbtEntries.get(i).path());
                if (rIdx == nbtEditingRuleIndex) { visible = true; break; }
            }
            if (!visible) {
                nbtValueEditBox.setVisible(false);
            }
        }

        if (scrollable) {
            int scrollbarX = listX + rowW + SUBMODE_SCROLLBAR_GAP;
            int thumbH = Math.max(8, (drawH * maxRows) / totalEntries);
            int thumbTravel = Math.max(0, drawH - thumbH);
            int thumbY = maxScroll <= 0 ? listY : listY + (nbtListScrollOffset * thumbTravel) / maxScroll;
            g.fill(scrollbarX, listY, scrollbarX + SUBMODE_SCROLLBAR_W, listY + drawH, COL_BTN_BG);
            g.renderOutline(scrollbarX, listY, SUBMODE_SCROLLBAR_W, drawH, COL_BTN_BORDER);
            g.fill(scrollbarX + 1, thumbY, scrollbarX + SUBMODE_SCROLLBAR_W - 1, thumbY + thumbH, COL_ACCENT);
        }

        if (totalEntries == 0) {
            g.drawString(font, tr("gui.logisticsnetworks.filter.nbt.no_entries"),
                    listX + 3, listY + 2, COL_GRAY, false);
        }
    }

    private boolean handleNbtSubModeClick(double mx, double my, int btn) {
        int panelW = getNbtPanelW();
        int panelX = getNbtPanelX();
        int panelY = topPos + 20;
        int panelH = menu.getPlayerInventoryY() - 24;

        if (!isHovering(panelX, panelY, panelW, panelH, (int) mx, (int) my))
            return false;

        // Clear button
        int clearW = 30;
        int clearX = panelX + panelW - clearW - 4;
        int clearY = panelY + 4;
        if (isHovering(clearX, clearY, clearW, 10, (int) mx, (int) my)) {
            cancelNbtValueEdit();
            PacketDistributor.sendToServer(SetFilterEntryNbtPayload.clear(nbtEditSlot));
            menu.clearSlotNbtRules(nbtEditSlot);
            return true;
        }

        // NBT entries list
        List<FilterItemData.SlotNbtRule> activeRules = menu.getSlotNbtRules(nbtEditSlot);
        int listX = panelX + 4;
        int listY = panelY + 16;
        int listW = panelW - 8;
        int listH = panelH - 34;
        if (handleNbtEntryListClick(listX, listY, listW, listH, activeRules, mx, my, btn))
            return true;

        // Match mode button
        boolean matchAny = menu.isSlotNbtMatchAny(nbtEditSlot);
        String matchLabel = matchAny
                ? tr("gui.logisticsnetworks.filter.nbt.match_any")
                : tr("gui.logisticsnetworks.filter.nbt.match_all");
        int matchW = font.width(matchLabel) + 12;
        int matchX = panelX + 4;
        int matchY = panelY + panelH - 16;
        if (isHovering(matchX, matchY, matchW, 14, (int) mx, (int) my)) {
            commitNbtValueEditIfActive();
            PacketDistributor.sendToServer(SetFilterEntryNbtPayload.toggleMatch(nbtEditSlot));
            menu.toggleSlotNbtMatchMode(nbtEditSlot);
            return true;
        }

        commitNbtValueEditIfActive();
        return true;
    }

    private boolean handleNbtEntryListClick(int listX, int listY, int listW, int listH,
            List<FilterItemData.SlotNbtRule> activeRules, double mx, double my, int btn) {
        int totalEntries = cachedSlotNbtEntries.size();
        int maxRows = Math.max(1, listH / LIST_ROW_H);
        int drawH = maxRows * LIST_ROW_H;
        if (!isHovering(listX, listY, listW, drawH, (int) mx, (int) my))
            return false;

        boolean scrollable = totalEntries > maxRows;
        int rowW = scrollable ? listW - SUBMODE_SCROLLBAR_W - SUBMODE_SCROLLBAR_GAP : listW;

        if (scrollable) {
            int scrollbarX = listX + rowW + SUBMODE_SCROLLBAR_GAP;
            if (isHovering(scrollbarX, listY, SUBMODE_SCROLLBAR_W, drawH, (int) mx, (int) my)) {
                int maxScroll = Math.max(0, totalEntries - maxRows);
                if (my < listY + drawH / 2.0)
                    nbtListScrollOffset = Math.max(0, nbtListScrollOffset - 1);
                else
                    nbtListScrollOffset = Math.min(maxScroll, nbtListScrollOffset + 1);
                return true;
            }
        }

        int colW = getNbtColW(rowW);
        int endIdx = Math.min(nbtListScrollOffset + maxRows, totalEntries);
        for (int i = nbtListScrollOffset; i < endIdx; i++) {
            int rowY = listY + (i - nbtListScrollOffset) * LIST_ROW_H;
            if (!(mx >= listX && mx < listX + rowW && my >= rowY && my < rowY + LIST_ROW_H))
                continue;

            NbtFilterData.NbtEntry entry = cachedSlotNbtEntries.get(i);
            String path = entry.path();
            int ruleIdx = findActiveRuleIndex(activeRules, path);
            boolean active = ruleIdx >= 0;

            int pathX = listX + NBT_INDICATOR_W;
            int opX = pathX + colW + 1;
            int valX = opX + NBT_OP_BTN_W + 1;

            // Click operator button = cycle operator
            if (active && isHovering(opX, rowY, NBT_OP_BTN_W, LIST_ROW_H, (int) mx, (int) my)) {
                commitNbtValueEditIfActive();
                String savedVal = formatNbtValue(activeRules.get(ruleIdx).value().toString());
                String currentOp = activeRules.get(ruleIdx).operator();
                String newOp = FilterItemData.nextNbtOperator(currentOp);
                PacketDistributor.sendToServer(SetFilterEntryNbtPayload.remove(nbtEditSlot, ruleIdx));
                menu.removeSlotNbtRule(nbtEditSlot, ruleIdx);
                PacketDistributor.sendToServer(SetFilterEntryNbtPayload.add(nbtEditSlot, path, newOp));
                if (minecraft != null && minecraft.player != null) {
                    menu.addSlotNbtRule(minecraft.player, nbtEditSlot, path, newOp);
                }
                List<FilterItemData.SlotNbtRule> updatedRules = menu.getSlotNbtRules(nbtEditSlot);
                int newIdx = findActiveRuleIndex(updatedRules, path);
                if (newIdx >= 0) {
                    PacketDistributor.sendToServer(
                            SetFilterEntryNbtPayload.setValue(nbtEditSlot, newIdx, savedVal));
                    menu.setSlotNbtRuleValue(nbtEditSlot, newIdx, savedVal);
                }
                return true;
            }

            // Click value area of active rule
            if (active && btn == 0 && mx >= valX) {
                String displayVal = formatNbtValue(activeRules.get(ruleIdx).value().toString());

                // Boolean toggle
                if (isBooleanValue(displayVal)) {
                    commitNbtValueEditIfActive();
                    String toggled = "true".equals(displayVal) ? "false" : "true";
                    PacketDistributor.sendToServer(
                            SetFilterEntryNbtPayload.setValue(nbtEditSlot, ruleIdx, toggled));
                    menu.setSlotNbtRuleValue(nbtEditSlot, ruleIdx, toggled);
                    return true;
                }

                // Edit value
                if (nbtEditingRuleIndex == ruleIdx) {
                    nbtValueEditBox.mouseClicked(mx, my, btn);
                    return true;
                }
                commitNbtValueEditIfActive();
                nbtEditingRuleIndex = ruleIdx;
                nbtValueEditBox.setValue(displayVal);
                nbtValueEditBox.setVisible(true);
                nbtValueEditBox.setFocused(true);
                return true;
            }

            // Click toggle indicator or path area = toggle rule
            if (btn == 0 && mx < opX) {
                commitNbtValueEditIfActive();
                if (active) {
                    PacketDistributor.sendToServer(SetFilterEntryNbtPayload.remove(nbtEditSlot, ruleIdx));
                    menu.removeSlotNbtRule(nbtEditSlot, ruleIdx);
                } else {
                    PacketDistributor.sendToServer(SetFilterEntryNbtPayload.add(nbtEditSlot, path, "="));
                    if (minecraft != null && minecraft.player != null) {
                        menu.addSlotNbtRule(minecraft.player, nbtEditSlot, path, "=");
                    }
                }
                return true;
            }

            // Right-click = cycle operator (if active)
            if (active && btn == 1) {
                commitNbtValueEditIfActive();
                String savedVal = formatNbtValue(activeRules.get(ruleIdx).value().toString());
                String currentOp = activeRules.get(ruleIdx).operator();
                String newOp = FilterItemData.nextNbtOperator(currentOp);
                PacketDistributor.sendToServer(SetFilterEntryNbtPayload.remove(nbtEditSlot, ruleIdx));
                menu.removeSlotNbtRule(nbtEditSlot, ruleIdx);
                PacketDistributor.sendToServer(SetFilterEntryNbtPayload.add(nbtEditSlot, path, newOp));
                if (minecraft != null && minecraft.player != null) {
                    menu.addSlotNbtRule(minecraft.player, nbtEditSlot, path, newOp);
                }
                List<FilterItemData.SlotNbtRule> updatedRules = menu.getSlotNbtRules(nbtEditSlot);
                int newIdx = findActiveRuleIndex(updatedRules, path);
                if (newIdx >= 0) {
                    PacketDistributor.sendToServer(
                            SetFilterEntryNbtPayload.setValue(nbtEditSlot, newIdx, savedVal));
                    menu.setSlotNbtRuleValue(nbtEditSlot, newIdx, savedVal);
                }
                return true;
            }

            return true;
        }

        commitNbtValueEditIfActive();
        return true;
    }

    private FilterItemData.SlotNbtRule findActiveRule(List<FilterItemData.SlotNbtRule> rules, String path) {
        for (FilterItemData.SlotNbtRule rule : rules) {
            if (rule.path().equals(path))
                return rule;
        }
        return null;
    }

    private int findActiveRuleIndex(List<FilterItemData.SlotNbtRule> rules, String path) {
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).path().equals(path))
                return i;
        }
        return -1;
    }

    private static final Map<String, String> PATH_ABBREV = Map.of(
            "enchantments", "ench",
            "stored_enchantments", "stored",
            "potion_contents", "potion",
            "custom_data", "data",
            "attribute_modifiers", "attr"
    );

    private static String stripNamespace(String s) {
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s;
    }

    private String formatNbtPath(String path) {
        String[] segments = path.split("\\.");
        String last = null;
        String parent = null;
        for (String seg : segments) {
            if (seg.equals("levels")) continue;
            String clean = stripNamespace(seg);
            parent = last;
            last = clean;
        }
        if (parent != null && last != null) {
            String abbr = PATH_ABBREV.getOrDefault(parent, parent);
            return abbr + " > " + last;
        }
        return last != null ? last : path;
    }

    private String formatNbtValue(String raw) {
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            String inner = raw.substring(1, raw.length() - 1);
            inner = stripNamespace(inner);
            return inner;
        }
        if (raw.endsWith("b") || raw.endsWith("s") || raw.endsWith("L")
                || raw.endsWith("f") || raw.endsWith("d")) {
            String num = raw.substring(0, raw.length() - 1);
            if (raw.endsWith("b")) {
                if ("0".equals(num)) return "false";
                if ("1".equals(num)) return "true";
            }
            return num;
        }
        return raw;
    }

    private int getNbtSubModeMaxScroll() {
        int panelH = menu.getPlayerInventoryY() - 24;
        int listH = panelH - 34;
        int maxRows = Math.max(1, listH / LIST_ROW_H);
        return Math.max(0, cachedSlotNbtEntries.size() - maxRows);
    }

    private void commitNbtValueEdit() {
        if (nbtEditingRuleIndex < 0 || nbtEditSlot < 0) return;
        String val = nbtValueEditBox.getValue().trim();
        if (!val.isEmpty()) {
            PacketDistributor.sendToServer(SetFilterEntryNbtPayload.setValue(nbtEditSlot, nbtEditingRuleIndex, val));
            menu.setSlotNbtRuleValue(nbtEditSlot, nbtEditingRuleIndex, val);
        }
        nbtEditingRuleIndex = -1;
        nbtValueEditBox.setVisible(false);
        nbtValueEditBox.setFocused(false);
    }

    private void commitNbtValueEditIfActive() {
        if (nbtEditingRuleIndex >= 0) commitNbtValueEdit();
    }

    private void cancelNbtValueEdit() {
        nbtEditingRuleIndex = -1;
        nbtValueEditBox.setVisible(false);
        nbtValueEditBox.setFocused(false);
    }

    private void closeNbtSubMode() {
        commitNbtValueEditIfActive();
        nbtEditSlot = -1;
        subModeDropdownOpen = false;
        cachedSlotNbtEntries.clear();
        nbtPendingOperator = "=";
        nbtListScrollOffset = 0;
    }

    public List<Rect2i> getExtraAreas() {
        List<Rect2i> areas = new ArrayList<>();
        if (nbtEditSlot >= 0) {
            int panelW = getNbtPanelW();
            int panelX = getNbtPanelX();
            int panelY = topPos + 20;
            int panelH = menu.getPlayerInventoryY() - 24;
            areas.add(new Rect2i(panelX, panelY, panelW, panelH));
        }
        if (tagEditSlot >= 0) {
            int panelX = leftPos + 4;
            int panelY = topPos + 20;
            int panelW = imageWidth - 8;
            int panelH = menu.getPlayerInventoryY() - 24;
            areas.add(new Rect2i(panelX, panelY, panelW, panelH));
        }
        return areas;
    }
}
