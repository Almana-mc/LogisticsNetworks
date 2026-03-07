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
import net.minecraft.client.gui.components.MultiLineEditBox;
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
import net.minecraft.core.HolderLookup;
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
    private static final int LIST_ROW_H = 12;
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
    private EditBox tagInputBox;
    private MultiLineEditBox nbtInputBox;

    // Cached Data
    private List<String> cachedTags = new ArrayList<>();
    private List<String> cachedMods = new ArrayList<>();
    private List<NbtFilterData.NbtEntry> cachedNbtEntries = new ArrayList<>();
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

        nbtInputBox = new MultiLineEditBox(
                font, leftPos + 12, topPos + 50, 100, 45, Component.empty(), Component.empty());
        nbtInputBox.setCharacterLimit(512);
        nbtInputBox.active = false;
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
        } else if (menu.isNbtMode()) {
            cachedNbtEntries.clear();
            if (!extractor.isEmpty()) {
                HolderLookup.Provider provider = minecraft.player.level().registryAccess();
                if (isFluid) {
                    FluidStack fs = FluidUtil.getFluidContained(extractor).orElse(FluidStack.EMPTY);
                    if (!fs.isEmpty())
                        cachedNbtEntries.addAll(NbtFilterData.extractEntries(fs, provider));
                } else if (isChemical) {
                    // NBT filtering is not currently supported for chemicals
                } else {
                    cachedNbtEntries.addAll(NbtFilterData.extractEntries(extractor, provider));
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

        boolean hoverSpecialFilter = (menu.isTagMode() || menu.isModMode() || menu.isNbtMode())
                && this.hoveredSlot != null && this.hoveredSlot.index < menu.getFilterSlots();

        if (menu.isTagMode())
            renderTagTooltip(g, mx, my);
        else if (menu.isModMode())
            renderModTooltip(g, mx, my);
        else if ((menu.getTargetType() == FilterTargetType.FLUIDS || menu.getTargetType() == FilterTargetType.CHEMICALS)
                && !menu.isNbtMode()) {
            renderFluidTooltip(g, mx, my);
        }

        // Standard-mode tag slot tooltip
        if (!menu.isTagMode() && !menu.isModMode() && !menu.isNbtMode()
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
            // NBT info tooltip
            String nbtRaw = FilterItemData.getEntryNbtRaw(menu.getOpenedStack(), idx);
            if (nbtRaw != null && !hoverSpecialFilter) {
                g.renderTooltip(font, Component.literal("NBT: " + nbtRaw), mx, my);
                hoverSpecialFilter = true;
            } else if (!hoverSpecialFilter) {
                String nbtPath = menu.getEntryNbtPath(idx);
                if (nbtPath != null) {
                    Tag nbtVal = FilterItemData.getEntryNbtValue(menu.getOpenedStack(), idx);
                    String display = nbtVal != null ? "{" + nbtPath + ":" + nbtVal + "}" : nbtPath;
                    g.renderTooltip(font, Component.literal("NBT: " + display), mx, my);
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
        else if (menu.isNbtMode())
            renderNbtMode(g, mx, my);
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

        // Render sub-mode overlays on top
        if (tagEditSlot >= 0) {
            renderTagSubMode(g, mx, my);
        } else if (nbtEditSlot >= 0) {
            renderNbtSubMode(g, mx, my);
        }
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

    private void renderNbtMode(GuiGraphics g, int mx, int my) {
        int x = getSelectorInputX();
        int y = topPos + 34;
        int w = getSelectorInputWidth();
        int arrowX = getSelectorArrowX();

        if (manualInputBox != null && manualInputBox.isVisible()) {
            manualInputBox.setVisible(false);
            manualInputBox.setFocused(false);
        }

        renderModeControls(g, mx, my, false);
        renderExtractorSlotTarget(g, mx, my);

        // Path Selector
        String path = menu.getSelectedNbtPath();
        String displayPath = path == null ? tr("gui.logisticsnetworks.filter.nbt.select_path") : path;

        drawButton(g, x, y, w, 14, scrollText(displayPath, w - 16, 0), mx, my, true);
        g.drawCenteredString(font, isDropdownOpen ? "^" : "v", arrowX + 6, y + 3, COL_GRAY);

        if (isDropdownOpen) {
            List<String> paths = cachedNbtEntries.stream().map(NbtFilterData.NbtEntry::path).toList();
            renderDropdownList(g, x, y + 16, w, paths, path, mx, my);
        } else {
            int valY = y + 25;
            g.drawString(font, Component.translatable("gui.logisticsnetworks.filter.nbt.value"), x, valY, COL_GRAY,
                    false);
            String val = menu.getSelectedNbtValue();
            g.drawString(font, scrollText(val != null ? val : tr("gui.logisticsnetworks.filter.nbt.value.none"), w, 50),
                    x, valY + 10, COL_ACCENT, false);
        }
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
        else if (menu.isNbtMode())
            handled = handleNbtClick(mx, my, btn);
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
                    tagEditSlot = -1;
                    return true;
                }
                return true;
            }
            if (nbtEditSlot >= 0) {
                handled = handleNbtSubModeClick(mx, my, btn);
                if (!handled) {
                    nbtEditSlot = -1;
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

    private boolean handleNbtClick(double mx, double my, int btn) {
        int x = getSelectorInputX();
        int y = topPos + 34;
        int w = getSelectorInputWidth();
        int arrowX = getSelectorArrowX();

        if (handleModeControlClick(mx, my, false))
            return true;

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

        // Tag sub-mode input
        if (tagInputBox != null && tagInputBox.isFocused()) {
            if (key == 257) {
                commitTagInput();
                return true;
            }
            tagInputBox.keyPressed(key, scan, modifiers);
            return true;
        }

        // NBT sub-mode input
        if (nbtInputBox != null && nbtInputBox.isFocused()) {
            nbtInputBox.keyPressed(key, scan, modifiers);
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
        if (tagInputBox != null && tagInputBox.isFocused()) {
            return tagInputBox.charTyped(c, modifiers);
        }
        if (nbtInputBox != null && nbtInputBox.isFocused()) {
            return nbtInputBox.charTyped(c, modifiers);
        }
        if (manualInputBox.isFocused()) {
            return manualInputBox.charTyped(c, modifiers);
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
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

        if (!menu.isAmountMode() && !menu.isTagMode() && !menu.isModMode() && !menu.isNbtMode()
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
        return menu.isTagMode() || menu.isModMode() || menu.isNbtMode();
    }

    public boolean acceptsItemSelectorGhostIngredient() {
        return menu.isTagMode() || menu.isModMode() || menu.isNbtMode();
    }

    public boolean supportsGhostIngredientTargets() {
        return !menu.isTagMode() && !menu.isModMode() && !menu.isNbtMode() && !menu.isAmountMode()
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
            this.cachedNbtEntries.clear();
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
        subModeDropdownOpen = true;

        int panelX = leftPos + 4;
        int panelY = topPos + 20;
        int panelW = imageWidth - 8;
        int panelH = menu.getPlayerInventoryY() - 24;
        int inputX = panelX + 4;
        int inputY = panelY + 30;
        int inputW = panelW - 8;
        int inputH = panelH - 30 - 20;

        nbtInputBox = new MultiLineEditBox(
                font, inputX, inputY, inputW, inputH, Component.empty(), Component.empty());
        nbtInputBox.setCharacterLimit(512);

        String existing = FilterItemData.getEntryNbtRaw(menu.getOpenedStack(), slot);
        if (existing != null) {
            nbtInputBox.setValue(existing);
        } else {
            ItemStack slotItem = getSlotItemForSubMode(slot);
            if (!slotItem.isEmpty() && minecraft != null && minecraft.player != null) {
                CompoundTag components = NbtFilterData.getSerializedComponents(
                        slotItem, minecraft.player.level().registryAccess());
                nbtInputBox.setValue(components != null ? components.toString() : "");
            } else {
                nbtInputBox.setValue("");
            }
        }
        nbtInputBox.active = true;
        nbtInputBox.setFocused(true);
    }

    private ItemStack getSlotItemForSubMode(int slot) {
        if (menu.isTagSlot(slot)) {
            return ItemStack.EMPTY;
        }
        if (slot < menu.slots.size()) {
            return menu.slots.get(slot).getItem();
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

    private void renderNbtSubMode(GuiGraphics g, int mx, int my) {
        int panelX = leftPos + 4;
        int panelY = topPos + 20;
        int panelW = imageWidth - 8;
        int panelH = menu.getPlayerInventoryY() - 24;

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0101010);
        g.renderOutline(panelX, panelY, panelW, panelH, 0xFFFFAA00);

        g.drawString(font, "NBT Filter - Slot " + nbtEditSlot, panelX + 4, panelY + 4, COL_WHITE, false);

        int clearW = 40;
        int clearX = panelX + panelW - clearW - 4;
        int clearY = panelY + 4;
        drawButton(g, clearX, clearY, clearW, 12, "Clear", (int) mx, (int) my, true);

        g.drawString(font, "Raw SNBT:", panelX + 4, panelY + 18, COL_ACCENT, false);

        int inputY = panelY + 30;
        int inputW = panelW - 8;
        int inputH = panelH - 30 - 20;
        nbtInputBox.render(g, mx, my, 0);

        int sbX = panelX + 4 + inputW - 8;
        g.fill(sbX, inputY + 1, sbX + 8, inputY + inputH - 1, 0xFF000000);
        g.fill(sbX + 7, inputY, sbX + 8, inputY + inputH, 0xFFFFFFFF);

        int doneW = 50;
        int doneX = panelX + (panelW - doneW) / 2;
        int doneY = inputY + inputH + 4;
        drawButton(g, doneX, doneY, doneW, 14, "Done", mx, my, true);

        g.pose().popPose();
    }

    private boolean handleNbtSubModeClick(double mx, double my, int btn) {
        int panelX = leftPos + 4;
        int panelY = topPos + 20;
        int panelW = imageWidth - 8;
        int panelH = menu.getPlayerInventoryY() - 24;

        if (!isHovering(panelX, panelY, panelW, panelH, (int) mx, (int) my)) {
            return false;
        }

        // Clear button
        int clearW = 40;
        int clearX = panelX + panelW - clearW - 4;
        int clearY = panelY + 4;
        if (isHovering(clearX, clearY, clearW, 12, (int) mx, (int) my)) {
            PacketDistributor.sendToServer(new SetFilterEntryNbtPayload(nbtEditSlot, "", true));
            menu.clearEntryNbt(null, nbtEditSlot);
            closeNbtSubMode();
            return true;
        }

        // Done button
        int inputY = panelY + 30;
        int inputH = panelH - 30 - 20;
        int doneW = 50;
        int doneX = panelX + (panelW - doneW) / 2;
        int doneY = inputY + inputH + 4;
        if (isHovering(doneX, doneY, doneW, 14, (int) mx, (int) my)) {
            commitNbtInput();
            return true;
        }

        // Input box click
        if (nbtInputBox != null && nbtInputBox.active) {
            nbtInputBox.mouseClicked(mx, my, btn);
            return true;
        }

        return true;
    }

    private void commitNbtInput() {
        if (nbtInputBox == null || nbtEditSlot < 0)
            return;
        String val = nbtInputBox.getValue().replace("\n", " ").trim();
        if (!val.isEmpty()) {
            PacketDistributor.sendToServer(
                    new SetFilterEntryNbtPayload(nbtEditSlot, "", false, val));
        }
        closeNbtSubMode();
    }

    private void closeNbtSubMode() {
        nbtEditSlot = -1;
        subModeDropdownOpen = false;
        if (nbtInputBox != null) {
            nbtInputBox.active = false;
            nbtInputBox.setFocused(false);
        }
    }
}
