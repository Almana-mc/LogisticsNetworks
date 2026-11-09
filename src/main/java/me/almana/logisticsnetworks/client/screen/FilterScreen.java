package me.almana.logisticsnetworks.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.client.ClientControls;
import me.almana.logisticsnetworks.client.ClientInput;
import me.almana.logisticsnetworks.client.FilterClickHandler;
import me.almana.logisticsnetworks.client.FilterResourceRenderer;
import me.almana.logisticsnetworks.client.LegacyContainerScreen;
import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.client.theme.ThemeState;
import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.component.NbtCriterion;
import me.almana.logisticsnetworks.data.IoSide;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.FilterTagUtil;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import me.almana.logisticsnetworks.filter.NameFilterData;
import me.almana.logisticsnetworks.filter.NameMatchScope;
import me.almana.logisticsnetworks.filter.NbtFilterData;
import me.almana.logisticsnetworks.filter.NbtPath;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.item.RecipeCardItem;
import me.almana.logisticsnetworks.menu.FilterMenu;
import me.almana.logisticsnetworks.network.ModifyFilterModPayload;
import me.almana.logisticsnetworks.network.OpenNodeMenuPayload;
import me.almana.logisticsnetworks.network.ScanAttachedStoragePayload;
import me.almana.logisticsnetworks.network.SetFilterChemicalEntryPayload;
import me.almana.logisticsnetworks.network.SetFilterEntryAmountPayload;
import me.almana.logisticsnetworks.network.SetFilterEntryDurabilityPayload;
import me.almana.logisticsnetworks.network.SetFilterEntryEnchantedPayload;
import me.almana.logisticsnetworks.network.SetFilterEntryNbtPayload;
import me.almana.logisticsnetworks.network.SetFilterEntrySidePayload;
import me.almana.logisticsnetworks.network.SetFilterEntrySlotMappingPayload;
import me.almana.logisticsnetworks.network.SetFilterEntryTagPayload;
import me.almana.logisticsnetworks.network.SetFilterFluidEntryPayload;
import me.almana.logisticsnetworks.network.SetFilterItemEntryPayload;
import me.almana.logisticsnetworks.network.SetNameFilterPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import me.almana.logisticsnetworks.client.GuiGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class FilterScreen extends LegacyContainerScreen<FilterMenu> {

    // Mutable shadow — parent fields may be final on 26.1
    protected int imageHeight;
    protected int imageWidth;

    // Layout Constants
    private static final int GUI_WIDTH = 176;
    private static final int FILTER_SLOT_SIZE = 18;
    private static final int BACK_BUTTON_W = 18;
    private static final int BACK_BUTTON_H = 12;

    // Control Constants
    static final int LIST_ROW_H = 10;
    static final int DROPDOWN_ROWS = 6;
    static final int SUBMODE_SCROLLBAR_W = 6;
    static final int SUBMODE_SCROLLBAR_GAP = 2;

    // Colors
    static Theme theme() { return ThemeState.active(); }
    static int cBg() { return theme().surface(); }
    static int cBorder() { return theme().border(); }
    static int cAccent() { return theme().accent(); }
    static int cText() { return theme().text(); }
    static int cMuted() { return theme().textMuted(); }
    static int cHover() { return (theme().text() & 0x00FFFFFF) | 0x22000000; }
    static int cSelected() { return theme().accentSoft(); }
    static int cBtnBg() { return theme().surface2(); }
    static int cBtnHover() { return ThemePaint.brighten(theme().surface2(), 0x10); }
    static int cBtnBorder() { return theme().borderStrong(); }
    static int cInfo() { return theme().info(); }
    static int cWarn() { return theme().warn(); }
    static int cDanger() { return theme().danger(); }
    static int cOverlayPanel() { return (theme().surface() & 0x00FFFFFF) | 0xF0000000; }
    static int cTextVeil(int alpha) { return (theme().text() & 0x00FFFFFF) | (alpha << 24); }

    private static final Identifier COPY_ICON = Identifier.fromNamespaceAndPath(
            LogisticsNetworks.MOD_ID, "textures/gui/filter_copy.png");
    private static final Identifier PASTE_ICON = Identifier.fromNamespaceAndPath(
            LogisticsNetworks.MOD_ID, "textures/gui/filter_paste.png");
    private static final Identifier SCAN_STORAGE_ICON = Identifier.fromNamespaceAndPath(
            LogisticsNetworks.MOD_ID, "textures/gui/filter_scan_storage.png");
    private static final Identifier RESOURCE_ROTATION_ICON = Identifier.fromNamespaceAndPath(
            LogisticsNetworks.MOD_ID, "textures/gui/filter_resource_rotation.png");
    private static final int CLIPBOARD_BUTTON_SIZE = 12;
    private static final int CLIPBOARD_BUTTON_GAP = 2;
    private static FilterClipboardSnapshot copiedFilter;

    private record FilterClipboardSnapshot(
            boolean modMode,
            boolean nameMode,
            boolean blacklist,
            FilterTargetType targetType,
            String selectedMod,
            String nameFilter,
            NameMatchScope nameScope,
            List<FilterEntrySnapshot> entries,
            int sourceSlots) {
    }

    private record FilterEntrySnapshot(
            ItemStack item,
            FluidStack fluid,
            String chemicalId,
            String tag,
            int batch,
            int stock,
            String slotExpression,
            String durabilityOp,
            int durabilityValue,
            Boolean enchanted,
            String rawNbt,
            boolean strictNbt,
            boolean nbtMatchAny,
            List<NbtCriterion> nbtRules,
            FilterTargetType tagKind,
            IoSide side) {
    }

    private EditBox manualInputBox;
    private boolean isDropdownOpen = false;
    private int listScrollOffset = 0;
    private boolean amountInfoOpen = false;
    private int amountInfoPage = 0;
    private boolean flushedEditorsOnExit = false;
    private boolean wasManualInputFocused = false;

    private int tagEditSlot = -1;
    private int nbtEditSlot = -1;
    private List<String> cachedSlotTags = new ArrayList<>();
    private List<NbtFilterData.NbtEntry> cachedSlotNbtEntries = new ArrayList<>();
    private List<String> cachedSlotNbtPathLabels = List.of();
    private int subModeScrollOffset = 0;
    private boolean subModeDropdownOpen = false;
    private String nbtPendingOperator = "=";
    private EditBox tagInputBox;

    private int nbtListScrollOffset = 0;
    private int nbtEditingRuleIndex = -1;
    private EditBox nbtValueEditBox;

    private final EntryDetailPage detailPage = new EntryDetailPage(Minecraft.getInstance().font, new DetailHost());
    private FilterTagPicker detailTagPicker;
    private List<ItemStack> nbtOnlyCycleItems;
    private List<Fluid> anyCycleFluids;
    private List<String> anyCycleChemicals;
    private FilterTargetType[] anyCycleKinds;
    private int savedImageHeight = -1;
    private int savedTopPos = -1;
    private int savedImageWidth = -1;
    private int savedLeftPos = -1;

    private List<String> cachedTags = new ArrayList<>();
    private List<String> cachedMods = new ArrayList<>();
    private ItemStack lastExtractorItem = ItemStack.EMPTY;
    private FilterTargetType lastTargetType = null;

    private int textTick = 0;
    private String currentSlotExpr;
    private Component selectorGhostChemicalName = null;
    private String selectorGhostChemicalId = null;
    private List<String> selectorGhostChemicalTags = null;

    public FilterScreen(FilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, GUI_WIDTH, Math.max(166, menu.getPlayerInventoryY() + 83));
        this.imageWidth = getXSize();
        this.imageHeight = getYSize();
        this.inventoryLabelY = menu.getPlayerInventoryY() - 10;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - imageHeight) / 2;

        if (detailTagPicker != null) {
            addRenderableWidget(manualInputBox);
            if (savedImageWidth > 0) savedLeftPos = (width - savedImageWidth) / 2;
            if (savedImageHeight > 0) savedTopPos = (height - savedImageHeight) / 2;
            return;
        }
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
        manualInputBox.setTextColor(cText());
        addRenderableWidget(manualInputBox);

        tagInputBox = new EditBox(font, leftPos + 12, topPos + 50, 100, 14, Component.empty());
        tagInputBox.setMaxLength(256);
        tagInputBox.setVisible(false);
        tagInputBox.setBordered(true);
        tagInputBox.setTextColor(cText());

        nbtValueEditBox = new EditBox(font, 0, 0, 60, LIST_ROW_H, Component.empty());
        nbtValueEditBox.setMaxLength(128);
        nbtValueEditBox.setVisible(false);
        nbtValueEditBox.setBordered(false);
        nbtValueEditBox.setTextColor(cWarn());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        textTick++;
        if (textTick > 10000)
            textTick = 0;

        refreshFilterData();

        if (menu.isModMode()) {
            manualInputBox.setMaxLength(256);
            manualInputBox.setVisible(true);
            manualInputBox.setX(getSelectorInputX());
            manualInputBox.setY(getSelectorInputY());
            manualInputBox.setWidth(getSelectorInputWidth());
            manualInputBox.setHint(fitHint(
                    Component.translatable("gui.logisticsnetworks.filter.mod.input_full_hint"),
                    getSelectorInputWidth()));
            if (!manualInputBox.isFocused() && !manualInputBox.getValue().equals(getCurrentTargetValue())) {
                manualInputBox.setValue(getCurrentTargetValue());
            }
        } else if (menu.isNameMode()) {
            manualInputBox.setMaxLength(NameFilterData.MAX_EXPRESSION_LENGTH);
            manualInputBox.setVisible(true);
            manualInputBox.setHint(fitHint(
                    Component.translatable("gui.logisticsnetworks.filter.name.input_hint"),
                    imageWidth - 16));
            if (!manualInputBox.isFocused() && !manualInputBox.getValue().equals(getCurrentTargetValue())) {
                manualInputBox.setValue(getCurrentTargetValue());
            }
        } else {
            manualInputBox.setVisible(false);
            if (manualInputBox.isFocused()) {
                manualInputBox.setFocused(false);
            }
        }

        amountInfoOpen = false;
        amountInfoPage = 0;

        detailPage.tick();

        if (manualInputBox != null) {
            if (wasManualInputFocused && !manualInputBox.isFocused()) {
                if (!commitManualInput()) {
                    manualInputBox.setFocused(true);
                    setFocused(manualInputBox);
                }
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
                        FluidStack fs = FluidUtil.getFirstStackContained(extractor);
                        if (!fs.isEmpty()) {
                            fs.getFluid().builtInRegistryHolder().tags().forEach(t -> cachedTags.add(t.location().toString()));
                        }
                    } else if (isChemical && MekanismCompat.isLoaded()) {
                        List<String> chemTags = MekanismCompat.getChemicalTagsFromItem(extractor);
                        if (chemTags != null) {
                            cachedTags.addAll(chemTags);
                        }
                    } else {
                        extractor.getItem().builtInRegistryHolder().tags()
                                .map(tagKey -> tagKey.location().toString())
                                .forEach(cachedTags::add);
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
                    FluidStack fs = FluidUtil.getFirstStackContained(extractor);
                    if (!fs.isEmpty())
                        ns = BuiltInRegistries.FLUID.getKey(fs.getFluid()).getNamespace();
                } else if (isChemical && MekanismCompat.isLoaded()) {
                    String chemId = MekanismCompat.getChemicalIdFromItem(extractor);
                    if (chemId != null) {
                        Identifier loc = Identifier.tryParse(chemId);
                        if (loc != null)
                            ns = loc.getNamespace();
                    }
                } else {
                    ns = BuiltInRegistries.ITEM.getKey(extractor.getItem()).getNamespace();
                }
                if (ns != null)
                    cachedMods.add(ns);
            } else if (selectorGhostChemicalId != null) {
                Identifier loc = Identifier.tryParse(selectorGhostChemicalId);
                if (loc != null) {
                    cachedMods.add(loc.getNamespace());
                }
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mx, int my, float pt) {
        if (detailTagPicker != null) {
            graphics.fill(0, 0, width, height, 0xC0101010);
            detailTagPicker.render(guiGraphics(graphics), mx, my, pt, width, height);
            return;
        }
        super.extractRenderState(graphics, mx, my, pt);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (detailPage.isOpen()) {
            return;
        }

        if (renderClipboardTooltip(g, mx, my)) {
            return;
        }

        renderEntryIndicatorOverlays(g);

        if (tagEditSlot >= 0) {
            renderTagSubMode(g, mx, my);
        } else if (nbtEditSlot >= 0) {
            renderNbtSubMode(g, mx, my);
        }

        boolean hoverSpecialFilter = (menu.isTagMode() || menu.isModMode())
                && this.hoveredSlot != null && this.hoveredSlot.index < menu.getFilterSlots();

        if (menu.isModMode())
            renderModTooltip(g, mx, my);

        if (!menu.isTagMode() && !menu.isModMode()
                && tagEditSlot < 0 && nbtEditSlot < 0 && !detailPage.isOpen()
                && this.hoveredSlot != null && this.hoveredSlot.index < menu.getFilterSlots()) {
            int idx = this.hoveredSlot.index;
            ItemStack openedStack = menu.getOpenedStack();
            List<Component> lines = buildFilterEntryTooltip(idx, openedStack);
            if (!lines.isEmpty()) {
                g.renderTooltip(font, lines, Optional.empty(), mx, my);
                hoverSpecialFilter = true;
            }
        }

        if (!hoverSpecialFilter) {
            this.renderTooltip(g, mx, my);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (tagEditSlot >= 0 || nbtEditSlot >= 0 || detailPage.isOpen()) {
            return;
        }
        if (this.hoveredSlot != null && this.hoveredSlot.index < menu.getFilterSlots()) {
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        if (detailTagPicker != null) return;
        renderPanel(g, leftPos, topPos, imageWidth, imageHeight);

        if (detailPage.isOpen()) {
            detailPage.render(g, mx, my);
            return;
        }

        int titleX = leftPos + 8;
        if (menu.isBoundFilter()) {
            drawButton(g, backButtonX(), backButtonY(), BACK_BUTTON_W, BACK_BUTTON_H, "<", mx, my, true);
            titleX += BACK_BUTTON_W + 4;
        }
        g.drawString(font, filterTitle(), titleX, topPos + 6, cAccent(), false);
        renderClipboardButtons(g, mx, my);

        if (menu.isModMode())
            renderModMode(g, mx, my);
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
        g.fill(leftPos + 8, sepY, leftPos + imageWidth - 8, sepY + 1, cBorder());
        g.drawString(font, playerInventoryTitle, leftPos + 8, topPos + playerInvY - 10, cMuted(), false);

        renderPlayerSlots(g);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        if (slot.index < menu.getFilterSlots() && RecipeCardItem.is(slot.getItem())) {
            List<RecipeCardData.Ingredient> inputs = RecipeCardData.of(slot.getItem()).ingredients();
            if (!inputs.isEmpty()) {
                RecipeCardCells.icon(guiGraphics(graphics), inputs.get((int) (System.currentTimeMillis() / 1000 % inputs.size())),
                        slot.x, slot.y);
                return;
            }
        }
        super.extractSlot(graphics, slot, mouseX, mouseY);
    }

    private void renderStandardFilterGrid(GuiGraphics g, int mx, int my) {
        if (detailPage.isOpen()) return;

        for (int i = 0; i < menu.getFilterSlots() && i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            int sx = leftPos + slot.x - 1;
            int sy = topPos + slot.y - 1;

            if (menu.isTagSlot(i)) {
                drawSlot(g, sx, sy);
                g.renderOutline(sx, sy, 18, 18, cAccent());

                String tag = menu.getEntryTag(i);
                if (tag != null) {
                    Identifier tagId = Identifier.tryParse(tag);
                    if (tagId != null) {
                        FilterTargetType targetType = menu.getEntryKind(i);
                        if (targetType == FilterTargetType.FLUIDS) {
                            TagKey<Fluid> fluidTagKey = TagKey.create(Registries.FLUID, tagId);
                            var list = new ArrayList<Fluid>();
                            for (Fluid fluid : BuiltInRegistries.FLUID) {
                                if (fluid.builtInRegistryHolder().is(fluidTagKey)) {
                                    list.add(fluid);
                                }
                            }
                            if (!list.isEmpty()) {
                                long tick = (System.currentTimeMillis() / 1000);
                                int idx = (int) (tick % list.size());
                                FilterResourceRenderer.renderFluid(g,
                                        new FluidStack(list.get(idx), 1000), sx + 1, sy + 1);
                            }
                        } else if (targetType == FilterTargetType.CHEMICALS) {
                            g.drawString(font, "#", sx + 5, sy + 5, cAccent(), true);
                        } else {
                            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
                            List<Item> taggedItems = new ArrayList<>();
                            for (Item candidate : BuiltInRegistries.ITEM) {
                                if (candidate.builtInRegistryHolder().is(tagKey)) {
                                    taggedItems.add(candidate);
                                }
                            }
                            if (!taggedItems.isEmpty()) {
                                long tick = (System.currentTimeMillis() / 1000);
                                int idx = (int) (tick % taggedItems.size());
                                ItemStack display = new ItemStack(taggedItems.get(idx));
                                g.renderItem(display, sx + 1, sy + 1);
                            }
                        }
                    }
                }
            } else if (FilterItemData.isNbtOnlySlot(menu.getOpenedStack(), i)
                    || isAnyItemSlot(i)) {
                drawSlot(g, sx, sy);
                g.renderOutline(sx, sy, 18, 18, cAccent());
                renderAnyResource(g, sx + 1, sy + 1);
            } else {
                drawSlot(g, sx, sy);
            }
        }

        renderFluidGhostItems(g);
        renderChemicalGhostItems(g);
        renderKindSummary(g);

        renderModeControls(g, mx, my);
    }

    private void renderKindSummary(GuiGraphics g) {
        int[] counts = new int[FilterTargetType.values().length];
        int cards = 0;
        for (int i = 0; i < menu.getFilterSlots() && i < menu.slots.size(); i++) {
            if (RecipeCardItem.is(menu.slots.get(i).getItem())) cards++;
            else if (hasEntryInSlot(i)) counts[menu.getEntryKind(i).ordinal()]++;
        }
        List<String> parts = new ArrayList<>();
        for (FilterTargetType kind : FilterTargetType.values()) {
            if (counts[kind.ordinal()] > 0)
                parts.add(tr("gui.logisticsnetworks.filter.summary." + kind.getSerializedName(), counts[kind.ordinal()]));
        }
        if (cards > 0) parts.add(tr("gui.logisticsnetworks.filter.summary.cards", cards));
        if (parts.isEmpty()) return;
        g.drawString(font, font.plainSubstrByWidth(String.join(" \u00B7 ", parts), imageWidth - 16),
                leftPos + 8, topPos + menu.getPlayerInventoryY() - 24, cMuted(), false);
    }

    private void renderEntryIndicatorOverlays(GuiGraphics g) {
        ItemStack openedStack = menu.getOpenedStack();
        for (int i = 0; i < menu.getFilterSlots() && i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            int sx = leftPos + slot.x - 1;
            int sy = topPos + slot.y - 1;

            if (FilterItemData.hasEntryNbt(openedStack, i)) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.pose().scale(0.5f, 0.5f, 1.0f);
                int bx = (int) ((sx + 1) / 0.5f);
                int by = (int) ((sy + 1) / 0.5f);
                g.drawString(font, "N", bx, by, cWarn(), true);
                g.pose().popPose();
            }

            if (FilterItemData.hasEntryDurability(openedStack, i)) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.pose().scale(0.5f, 0.5f, 1.0f);
                int bx = (int) ((sx + 12) / 0.5f);
                int by = (int) ((sy + 1) / 0.5f);
                g.drawString(font, "D", bx, by, cInfo(), true);
                g.pose().popPose();
            }

            if (FilterItemData.hasEntryEnchanted(openedStack, i)) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.pose().scale(0.5f, 0.5f, 1.0f);
                int bx = (int) ((sx + 1) / 0.5f);
                int by = (int) ((sy + 12) / 0.5f);
                g.drawString(font, "E", bx, by, 0xFFDD88FF, true);
                g.pose().popPose();
            }

            if (FilterItemData.hasEntrySlotMapping(openedStack, i)) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.pose().scale(0.5f, 0.5f, 1.0f);
                int bx = (int) ((sx + 12) / 0.5f);
                int by = (int) ((sy + 12) / 0.5f);
                g.drawString(font, "S", bx, by, cMuted(), true);
                g.pose().popPose();
            }
        }
    }

    private String formatMb(int amount) {
        if (amount >= 1000 && amount % 1000 == 0) {
            return (amount / 1000) + "B";
        }
        return amount + "mB";
    }


    private void renderModMode(GuiGraphics g, int mx, int my) {
        renderModeControls(g, mx, my);
        int typeBtnW = modTargetButtonW();
        drawButton(g, leftPos + imageWidth - 8 - typeBtnW, topPos + 20, typeBtnW, 12,
                targetLabel(menu.getTargetType()), mx, my, true);
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
        String selected = tr("gui.logisticsnetworks.filter.selector.selected", displayValue);
        g.drawString(font, font.plainSubstrByWidth(selected, imageWidth - 20 - modTargetButtonW()),
                leftPos + 8, topPos + 22, cMuted(), false);

        manualInputBox.setHint(fitHint(hint, w));

        boolean hoveringDropdown = isHovering(x, y, w, 14, mx, my);
        g.renderOutline(x, y, w, 14, (hoveringDropdown || isDropdownOpen) ? cText() : cBorder());
        if (!manualInputBox.isVisible() && !manualInputBox.isFocused()) {
            g.drawCenteredString(font, current != null ? current : "", x + w / 2, y + 3, cText());
        }

        g.drawCenteredString(font, isDropdownOpen ? "^" : "v", arrowX + 6, y + 3, cMuted());

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
        g.pose().translate(0, 0, 200);
        g.fill(x, y, x + w, y + listH, cBg());
        g.renderOutline(x, y, w, listH, cBorder());

        int startIdx = listScrollOffset;
        int endIdx = Math.min(startIdx + DROPDOWN_ROWS, items.size());

        for (int i = startIdx; i < endIdx; i++) {
            int rowY = y + (i - startIdx) * LIST_ROW_H;
            String item = items.get(i);
            boolean isSelected = Objects.equals(item, current);
            boolean isHovered = mx >= x && mx <= x + w && my >= rowY && my < rowY + LIST_ROW_H;

            if (isSelected)
                g.fill(x, rowY, x + w, rowY + LIST_ROW_H, cSelected());
            else if (isHovered)
                g.fill(x, rowY, x + w, rowY + LIST_ROW_H, cHover());

            String text = scrollText(item, w - 4, i);
            g.drawString(font, text, x + 2, rowY + 2, isSelected ? cAccent() : cText(), false);
        }
        g.pose().popPose();
    }

    private void renderAmountMode(GuiGraphics g, int mx, int my) {
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
        g.fill(x, y, x + w, y + h, cOverlayPanel());
        g.renderOutline(x, y, w, h, cBorder());

        String titleKey = amountInfoPage == 0
                ? "gui.logisticsnetworks.filter.amount.info.import.title"
                : "gui.logisticsnetworks.filter.amount.info.export.title";
        g.drawString(font, Component.translatable(titleKey), x + pad, y + pad, cText(), false);

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
            g.drawString(font, part, x, nextY, cMuted(), false);
            nextY += 9;
        }
        return nextY;
    }

    private void renderDurabilityMode(GuiGraphics g, int mx, int my) {
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
        g.fill(x, y, x + w, y + h, cBg());
        g.renderOutline(x, y, w, h, cBorder());
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

    private void renderModeControls(GuiGraphics g, int mx, int my) {
        int btnH = 12;
        int btnY = topPos + 6;
        int rightEdge = leftPos + imageWidth - 8;

        String modeLabel = menu.isBlacklistMode()
                ? tr("gui.logisticsnetworks.filter.mode.blacklist")
                : tr("gui.logisticsnetworks.filter.mode.whitelist");
        int modeBtnW = Math.max(48, font.width(modeLabel) + 8);
        int modeBtnX = rightEdge - modeBtnW;
        drawButton(g, modeBtnX, btnY, modeBtnW, btnH, modeLabel, mx, my, true);
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
        g.fill(x, y, x + 18, y + 18, theme().slotBg());
        g.renderOutline(x, y, 18, 18, theme().slotBorder());
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String label, int mx, int my, boolean active) {
        boolean hovered = active && isHovering(x, y, w, h, mx, my);
        g.fill(x, y, x + w, y + h, hovered ? cBtnHover() : cBtnBg());
        g.renderOutline(x, y, w, h, hovered ? cText() : cBtnBorder());
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, hovered ? cText() : cMuted());
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
                FilterResourceRenderer.renderFluid(g, fs, x, y);
            }
        }
    }

    private void renderAnyResource(GuiGraphics g, int x, int y) {
        long tick = System.currentTimeMillis() / 1000;
        if (anyCycleKinds == null) {
            anyCycleKinds = MekanismCompat.isLoaded()
                    ? FilterTargetType.values()
                    : new FilterTargetType[] { FilterTargetType.ITEMS, FilterTargetType.FLUIDS };
        }
        switch (anyCycleKinds[(int) (tick % anyCycleKinds.length)]) {
            case FLUIDS -> {
                if (anyCycleFluids == null) {
                    anyCycleFluids = BuiltInRegistries.FLUID.stream()
                            .filter(f -> f != Fluids.EMPTY && f.isSource(f.defaultFluidState()))
                            .toList();
                }
                if (!anyCycleFluids.isEmpty()) {
                    Fluid fluid = anyCycleFluids.get((int) (tick % anyCycleFluids.size()));
                    FilterResourceRenderer.renderFluid(g, new FluidStack(fluid, 1000), x, y);
                }
            }
            case CHEMICALS -> {
                if (anyCycleChemicals == null) {
                    anyCycleChemicals = MekanismCompat.getAllChemicalIds();
                }
                if (!anyCycleChemicals.isEmpty()) {
                    String id = anyCycleChemicals.get((int) (tick % anyCycleChemicals.size()));
                    FilterResourceRenderer.renderChemical(g, id, x, y);
                }
            }
            default -> {
                if (nbtOnlyCycleItems == null) {
                    nbtOnlyCycleItems = BuiltInRegistries.ITEM.stream()
                            .map(ItemStack::new)
                            .filter(s -> !s.isEmpty())
                            .toList();
                }
                if (!nbtOnlyCycleItems.isEmpty()) {
                    g.renderItem(nbtOnlyCycleItems.get((int) (tick % nbtOnlyCycleItems.size())), x, y);
                }
            }
        }
    }

    private void renderChemicalGhostItems(GuiGraphics g) {
        for (int i = 0; i < menu.getFilterSlots(); i++) {
            String chemId = menu.getChemicalFilter(i);
            if (chemId != null) {
                var slot = menu.slots.get(i);
                int x = leftPos + slot.x;
                int y = topPos + slot.y;
                FilterResourceRenderer.renderChemical(g, chemId, x, y);
            }
        }
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

    private void renderClipboardButtons(GuiGraphics g, int mx, int my) {
        if (menu.canSetResourceRoundRobin()) {
            drawIconButton(g, resourceRotationButtonX(), clipboardButtonY(), RESOURCE_ROTATION_ICON, mx, my,
                    true, menu.isResourceRoundRobin());
        }
        drawIconButton(g, scanStorageButtonX(), clipboardButtonY(), SCAN_STORAGE_ICON, mx, my,
                menu.canScanAttachedStorage(), false);
        drawIconButton(g, copyButtonX(), clipboardButtonY(), COPY_ICON, mx, my, true, false);
        drawIconButton(g, pasteButtonX(), clipboardButtonY(), PASTE_ICON, mx, my, copiedFilter != null, false);
    }

    private void drawIconButton(GuiGraphics g, int x, int y, Identifier icon, int mx, int my,
            boolean active, boolean selected) {
        boolean hovered = active && isHovering(x, y, CLIPBOARD_BUTTON_SIZE, CLIPBOARD_BUTTON_SIZE, mx, my);
        g.fill(x, y, x + CLIPBOARD_BUTTON_SIZE, y + CLIPBOARD_BUTTON_SIZE,
                selected ? cSelected() : hovered ? cBtnHover() : cBtnBg());
        g.renderOutline(x, y, CLIPBOARD_BUTTON_SIZE, CLIPBOARD_BUTTON_SIZE,
                selected ? cAccent() : hovered ? cText() : cBtnBorder());
        g.blit(icon, x + 2, y + 2, 0f, 0f, 8, 8, 8, 8);
    }

    private int copyButtonX() {
        return pasteButtonX() - CLIPBOARD_BUTTON_SIZE - CLIPBOARD_BUTTON_GAP;
    }

    private int scanStorageButtonX() {
        return copyButtonX() - CLIPBOARD_BUTTON_SIZE - CLIPBOARD_BUTTON_GAP;
    }

    private int resourceRotationButtonX() {
        return scanStorageButtonX() - CLIPBOARD_BUTTON_SIZE - CLIPBOARD_BUTTON_GAP;
    }

    private int pasteButtonX() {
        return clipboardRightEdge() - CLIPBOARD_BUTTON_SIZE;
    }

    private int clipboardButtonY() {
        return topPos + 6;
    }

    private int clipboardRightEdge() {
        if (menu.isSpecialMode()) {
            return leftPos + imageWidth - 8;
        }

        int rightEdge = leftPos + imageWidth - 8;
        String modeLabel = menu.isBlacklistMode()
                ? tr("gui.logisticsnetworks.filter.mode.blacklist")
                : tr("gui.logisticsnetworks.filter.mode.whitelist");
        int modeBtnW = Math.max(48, font.width(modeLabel) + 8);
        int left = rightEdge - modeBtnW;

        return left - CLIPBOARD_BUTTON_GAP;
    }

    private boolean renderClipboardTooltip(GuiGraphics g, int mx, int my) {
        if (menu.canSetResourceRoundRobin()
                && isHovering(resourceRotationButtonX(), clipboardButtonY(), CLIPBOARD_BUTTON_SIZE,
                        CLIPBOARD_BUTTON_SIZE, mx, my)) {
            g.renderTooltip(font, List.of(
                    Component.translatable("gui.logisticsnetworks.node.resource_rotation.hint"),
                    Component.translatable("gui.logisticsnetworks.node.resource_rotation.hint.operation"),
                    Component.translatable("gui.logisticsnetworks.node.resource_rotation.hint.blocked"),
                    Component.translatable("gui.logisticsnetworks.node.resource_rotation.hint.settings")), mx, my);
            return true;
        }
        if (isHovering(scanStorageButtonX(), clipboardButtonY(), CLIPBOARD_BUTTON_SIZE, CLIPBOARD_BUTTON_SIZE,
                mx, my)) {
            String key = menu.canScanAttachedStorage()
                    ? "gui.logisticsnetworks.filter.scan_storage"
                    : "gui.logisticsnetworks.filter.scan_storage.unavailable";
            g.renderTooltip(font, Component.translatable(key), mx, my);
            return true;
        }
        if (isHovering(copyButtonX(), clipboardButtonY(), CLIPBOARD_BUTTON_SIZE, CLIPBOARD_BUTTON_SIZE, mx, my)) {
            g.renderTooltip(font, Component.translatable("gui.logisticsnetworks.filter.copy"), mx, my);
            return true;
        }
        if (isHovering(pasteButtonX(), clipboardButtonY(), CLIPBOARD_BUTTON_SIZE, CLIPBOARD_BUTTON_SIZE, mx, my)) {
            String key = copiedFilter == null
                    ? "gui.logisticsnetworks.filter.paste.empty"
                    : "gui.logisticsnetworks.filter.paste";
            g.renderTooltip(font, Component.translatable(key), mx, my);
            return true;
        }
        return false;
    }

    private boolean handleClipboardButtonClick(double mx, double my, int action) {
        if (action != 0) {
            return false;
        }
        if (menu.canSetResourceRoundRobin()
                && isHovering(resourceRotationButtonX(), clipboardButtonY(), CLIPBOARD_BUTTON_SIZE,
                        CLIPBOARD_BUTTON_SIZE, (int) mx, (int) my)) {
            ClickSound.play();
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 10);
            }
            return true;
        }
        if (isHovering(scanStorageButtonX(), clipboardButtonY(), CLIPBOARD_BUTTON_SIZE, CLIPBOARD_BUTTON_SIZE,
                (int) mx, (int) my)) {
            return scanAttachedStorage();
        }
        if (isHovering(copyButtonX(), clipboardButtonY(), CLIPBOARD_BUTTON_SIZE, CLIPBOARD_BUTTON_SIZE,
                (int) mx, (int) my)) {
            ClickSound.play();
            return copyOpenFilter();
        }
        if (isHovering(pasteButtonX(), clipboardButtonY(), CLIPBOARD_BUTTON_SIZE, CLIPBOARD_BUTTON_SIZE,
                (int) mx, (int) my)) {
            if (copiedFilter != null) {
                ClickSound.play();
            }
            return pasteOpenFilter();
        }
        return false;
    }

    private boolean scanAttachedStorage() {
        if (!menu.canScanAttachedStorage()) {
            return true;
        }
        ClickSound.play();
        flushOpenEditors();
        ClientPacketDistributor.sendToServer(ScanAttachedStoragePayload.INSTANCE);
        return true;
    }

    public void showScanResult(int added, boolean storageFound, boolean filterFull) {
        if (!storageFound) {
            showFilterMessage("message.logisticsnetworks.filter.scan_storage.unavailable");
        } else if (filterFull) {
            showFilterMessage("message.logisticsnetworks.filter.scan_storage.full", added);
        } else if (added == 0) {
            showFilterMessage("message.logisticsnetworks.filter.scan_storage.no_new_entries");
        } else {
            showFilterMessage("message.logisticsnetworks.filter.scan_storage.success", added);
        }
    }

    private boolean handleClipboardShortcut(int key) {
        if (!ClientControls.modifier2Down() || isTextInputFocused()) {
            return false;
        }
        if (key == InputConstants.KEY_C) {
            return copyOpenFilter();
        }
        if (key == InputConstants.KEY_V) {
            return pasteOpenFilter();
        }
        return false;
    }

    private boolean isTextInputFocused() {
        return manualInputBox != null && manualInputBox.isFocused()
                || tagInputBox != null && tagInputBox.isFocused()
                || nbtValueEditBox != null && nbtValueEditBox.isFocused()
                || detailPage.hasFocusedInput();
    }

    private void flushOpenEditors() {
        detailPage.flush();
        if (tagInputBox != null && tagInputBox.isFocused()) {
            commitTagInput();
        }
        if (nbtValueEditBox != null && nbtValueEditBox.isFocused()) {
            commitNbtValueEdit();
        }
        commitManualInput();
    }

    private void showFilterMessage(String key, Object... args) {
        if (minecraft != null && minecraft.gui != null) {
            minecraft.gui.setOverlayMessage(Component.translatable(key, args), false);
        }
    }

    private boolean copyOpenFilter() {
        flushOpenEditors();

        if (menu.isModMode()) {
            copiedFilter = new FilterClipboardSnapshot(true, false, menu.isBlacklistMode(), menu.getTargetType(),
                    menu.getSelectedMod(), "", NameMatchScope.NAME, List.of(), 0);
            showFilterMessage("message.logisticsnetworks.filter.copy.success");
            return true;
        }

        if (menu.isNameMode()) {
            copiedFilter = new FilterClipboardSnapshot(false, true, menu.isBlacklistMode(), menu.getTargetType(),
                    null, menu.getNameFilter(), menu.getNameMatchScope(), List.of(), 0);
            showFilterMessage("message.logisticsnetworks.filter.copy.success");
            return true;
        }

        List<FilterEntrySnapshot> entries = new ArrayList<>();
        ItemStack opened = menu.getOpenedStack();
        for (int slot = 0; slot < menu.getFilterSlots(); slot++) {
            entries.add(copyEntry(opened, slot));
        }

        if (entries.isEmpty()) {
            showFilterMessage("message.logisticsnetworks.filter.copy.empty");
            return true;
        }

        copiedFilter = new FilterClipboardSnapshot(false, false, menu.isBlacklistMode(), menu.getTargetType(),
                null, "", NameMatchScope.NAME, entries, menu.getFilterSlots());
        showFilterMessage("message.logisticsnetworks.filter.copy.success");
        return true;
    }

    private FilterEntrySnapshot copyEntry(ItemStack opened, int slot) {
        ItemStack item = slot < menu.slots.size() ? menu.slots.get(slot).getItem().copy() : ItemStack.EMPTY;
        FluidStack fluid = menu.getFluidFilter(slot).copy();
        String chemicalId = menu.getChemicalFilter(slot);
        String tag = menu.getEntryTag(slot);
        String slotExpression = menu.getEntrySlotMappingExpression(slot);
        String durabilityOp = FilterItemData.getEntryDurabilityOp(opened, slot);
        int durabilityValue = FilterItemData.getEntryDurabilityValue(opened, slot);
        Boolean enchanted = FilterItemData.getEntryEnchanted(opened, slot);
        String rawNbt = FilterItemData.getEntryNbtRaw(opened, slot);

        return new FilterEntrySnapshot(item, fluid, chemicalId, tag, menu.getEntryBatch(slot), menu.getEntryStock(slot),
                slotExpression, durabilityOp, durabilityValue, enchanted, rawNbt, menu.isEntryNbtStrict(slot),
                menu.isSlotNbtMatchAny(slot), menu.getSlotNbtRules(slot), menu.getEntryKind(slot),
                menu.getEntrySide(slot));
    }

    private boolean pasteOpenFilter() {
        flushOpenEditors();

        if (copiedFilter == null) {
            showFilterMessage("message.logisticsnetworks.filter.paste.empty");
            return true;
        }

        if (menu.isModMode()) {
            return pasteModFilter();
        }

        if (menu.isNameMode()) {
            return pasteNameFilter();
        }

        if (copiedFilter.modMode() || copiedFilter.nameMode()) {
            showFilterMessage("message.logisticsnetworks.filter.paste.incompatible");
            return true;
        }

        pasteStandardFilter();
        return true;
    }

    private boolean pasteModFilter() {
        if (!copiedFilter.modMode()) {
            showFilterMessage("message.logisticsnetworks.filter.paste.incompatible");
            return true;
        }
        syncHeaderModes(copiedFilter.blacklist(), copiedFilter.targetType());
        String current = menu.getSelectedMod();
        if (current != null && !current.isBlank()) {
            sendModRemove(current);
        }
        if (copiedFilter.selectedMod() != null && !copiedFilter.selectedMod().isBlank()) {
            sendModUpdate(copiedFilter.selectedMod());
            manualInputBox.setValue(copiedFilter.selectedMod());
        } else {
            manualInputBox.setValue("");
        }
        showFilterMessage("message.logisticsnetworks.filter.paste.success");
        return true;
    }

    private boolean pasteNameFilter() {
        if (!copiedFilter.nameMode()) {
            showFilterMessage("message.logisticsnetworks.filter.paste.incompatible");
            return true;
        }
        syncHeaderModes(copiedFilter.blacklist(), copiedFilter.targetType());
        sendNameUpdate(copiedFilter.nameFilter());
        menu.setNameFilter(copiedFilter.nameFilter());
        manualInputBox.setValue(copiedFilter.nameFilter());
        syncNameScope(copiedFilter.nameScope());
        showFilterMessage("message.logisticsnetworks.filter.paste.success");
        return true;
    }

    private void pasteStandardFilter() {
        syncHeaderModes(copiedFilter.blacklist(), copiedFilter.targetType());

        int copied = Math.min(menu.getFilterSlots(), copiedFilter.entries().size());
        for (int slot = 0; slot < menu.getFilterSlots(); slot++) {
            clearFilterEntry(slot);
            ClientPacketDistributor.sendToServer(SetFilterEntryTagPayload.clear(slot));
            ClientPacketDistributor.sendToServer(SetFilterEntryNbtPayload.clear(slot));
            ClientPacketDistributor.sendToServer(new SetFilterEntryDurabilityPayload(slot, "", 0));
            ClientPacketDistributor.sendToServer(new SetFilterEntryEnchantedPayload(slot, false, false));
            ClientPacketDistributor.sendToServer(new SetFilterEntrySlotMappingPayload(slot, ""));
            ClientPacketDistributor.sendToServer(new SetFilterEntryAmountPayload(slot, 0, 0));
            ClientPacketDistributor.sendToServer(new SetFilterEntrySidePayload(slot, -1));
        }

        for (int slot = 0; slot < copied; slot++) {
            pasteEntry(slot, copiedFilter.entries().get(slot));
        }

        if (copied < copiedFilter.sourceSlots()) {
            showFilterMessage("message.logisticsnetworks.filter.paste.partial", copied, copiedFilter.sourceSlots());
        } else {
            showFilterMessage("message.logisticsnetworks.filter.paste.success");
        }
    }

    private void pasteEntry(int slot, FilterEntrySnapshot entry) {
        if (entry.tag() != null) {
            ClientPacketDistributor.sendToServer(new SetFilterEntryTagPayload(slot, entry.tag(), entry.tagKind()));
            menu.setEntryTag(minecraft.player, slot, entry.tag(), entry.tagKind());
        } else if (!entry.fluid().isEmpty()) {
            setFluidFilterEntry(minecraft.player, slot, entry.fluid());
        } else if (entry.chemicalId() != null && !entry.chemicalId().isBlank()) {
            setChemicalFilterEntry(minecraft.player, slot, entry.chemicalId());
        } else if (!entry.item().isEmpty()) {
            setItemFilterEntry(minecraft.player, slot, entry.item());
        }

        if (entry.batch() > 0 || entry.stock() > 0) {
            menu.setEntryBatch(minecraft.player, slot, entry.batch());
            menu.setEntryStock(minecraft.player, slot, entry.stock());
            ClientPacketDistributor.sendToServer(new SetFilterEntryAmountPayload(slot, entry.batch(), entry.stock()));
        }

        if (entry.slotExpression() != null && !entry.slotExpression().isBlank()) {
            menu.setEntrySlotMapping(minecraft.player, slot, entry.slotExpression());
            ClientPacketDistributor.sendToServer(new SetFilterEntrySlotMappingPayload(slot, entry.slotExpression()));
        }

        if (entry.side() != null) {
            menu.setEntrySide(minecraft.player, slot, entry.side());
            ClientPacketDistributor.sendToServer(new SetFilterEntrySidePayload(slot, entry.side().ordinal()));
        }

        if (entry.enchanted() != null) {
            menu.setEntryEnchanted(minecraft.player, slot, entry.enchanted());
            ClientPacketDistributor.sendToServer(new SetFilterEntryEnchantedPayload(slot, true, entry.enchanted()));
        }

        if (entry.durabilityOp() != null && !entry.durabilityOp().isBlank()) {
            menu.setEntryDurability(minecraft.player, slot, entry.durabilityOp(), entry.durabilityValue());
            ClientPacketDistributor.sendToServer(new SetFilterEntryDurabilityPayload(slot, entry.durabilityOp(),
                    entry.durabilityValue()));
        }

        pasteNbt(slot, entry);
    }

    private void pasteNbt(int slot, FilterEntrySnapshot entry) {
        if (entry.strictNbt()) {
            menu.setEntryNbtStrict(slot, true);
            ClientPacketDistributor.sendToServer(SetFilterEntryNbtPayload.setStrict(slot, true));
        }

        if (entry.rawNbt() != null && !entry.rawNbt().isBlank()) {
            menu.setEntryNbtRaw(minecraft.player, slot, NbtPath.EMPTY, entry.rawNbt());
            ClientPacketDistributor.sendToServer(SetFilterEntryNbtPayload.setRaw(slot, entry.rawNbt()));
        }

        for (NbtCriterion rule : entry.nbtRules()) {
            String value = rule.value().toString();
            menu.addSlotNbtRule(minecraft.player, slot, rule.path(), rule.operator(), value);
            ClientPacketDistributor.sendToServer(SetFilterEntryNbtPayload.add(slot, rule.path(), rule.operator(), value));
            // Add prefers the ghost value
            int ruleIdx = FilterItemData.indexOfRule(menu.getSlotNbtRules(slot), rule);
            if (ruleIdx >= 0) {
                ClientPacketDistributor.sendToServer(SetFilterEntryNbtPayload.setValue(slot, ruleIdx, value));
                menu.setSlotNbtRuleValue(slot, ruleIdx, value);
            }
        }

        if (entry.nbtMatchAny() != menu.isSlotNbtMatchAny(slot)) {
            menu.toggleSlotNbtMatchMode(slot);
            ClientPacketDistributor.sendToServer(SetFilterEntryNbtPayload.toggleMatch(slot));
        }
    }

    private void syncHeaderModes(boolean blacklist, FilterTargetType targetType) {
        if (minecraft == null || minecraft.gameMode == null) {
            return;
        }
        if (menu.isBlacklistMode() != blacklist) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }
        if (!menu.isModMode() && !menu.isNameMode()) return;
        FilterTargetType[] types = FilterTargetType.values();
        int presses = ((targetType.ordinal() - menu.getTargetType().ordinal()) % types.length + types.length)
                % types.length;
        for (int i = 0; i < presses; i++) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 8);
        }
    }

    private void syncNameScope(NameMatchScope scope) {
        if (minecraft == null || minecraft.gameMode == null)
            return;
        int count = NameMatchScope.values().length;
        int presses = ((scope.ordinal() - menu.getNameMatchScope().ordinal()) % count + count) % count;
        for (int i = 0; i < presses; i++) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 9);
        }
    }

    private Component filterTitle() {
        return menu.isSpecialMode() ? title : Component.translatable("gui.logisticsnetworks.filter.title");
    }

    private int backButtonX() {
        return leftPos + 8;
    }

    private int backButtonY() {
        return topPos + 5;
    }

    private boolean isHoveringBackButton(double mx, double my) {
        return menu.isBoundFilter()
                && isHovering(backButtonX(), backButtonY(), BACK_BUTTON_W, BACK_BUTTON_H, (int) mx, (int) my);
    }

    private void editRecipeCard(int slot) {
        if (!menu.isBoundFilter() || menu.getGraphContext() != null || minecraft == null
                || minecraft.gameMode == null) return;
        flushEditorsBeforeExit();
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FilterMenu.ID_EDIT_CARD_BASE + slot);
    }

    private boolean returnToNodeScreen() {
        if (!menu.isBoundFilter()) {
            return false;
        }
        flushEditorsBeforeExit();
        if (menu.isClipboardFilter()) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FilterMenu.ID_RETURN_TO_CLIPBOARD);
            }
            return true;
        }
        ClientPacketDistributor.sendToServer(new OpenNodeMenuPayload(
                menu.getNodeSource().getId(), menu.getNodeChannel()));
        return true;
    }

    private Component fitHint(Component hint, int boxWidth) {
        int inner = Math.max(0, boxWidth - 8);
        String s = hint.getString();
        if (font.width(s) <= inner) {
            return hint;
        }
        int ellipsis = font.width("...");
        return Component.literal(font.plainSubstrByWidth(s, Math.max(0, inner - ellipsis)) + "...");
    }

    private boolean blurManualInputIfNeeded(double mx, double my) {
        if (manualInputBox == null || !manualInputBox.isVisible() || !manualInputBox.isFocused()) {
            return false;
        }
        if (isHoveringManualInput(mx, my)) {
            return false;
        }
        return !saveManualInputAndClearFocus();
    }

    private boolean isHoveringManualInput(double mx, double my) {
        if (menu.isModMode()) {
            return isHovering(getSelectorInputX(), getSelectorInputY(), getSelectorInputWidth(), 14, (int) mx, (int) my);
        }
        if (menu.isNameMode()) {
            return isHovering(leftPos + 8, topPos + 38, imageWidth - 16, 14, (int) mx, (int) my);
        }
        return false;
    }

    private boolean handleManualInputClick(double mx, double my, int btn) {
        if (btn != 0 || manualInputBox == null || !manualInputBox.isVisible() || !isHoveringManualInput(mx, my)) {
            return false;
        }
        manualInputBox.mouseClicked(ClientInput.mouse(mx, my, btn), false);
        manualInputBox.setFocused(true);
        setFocused(manualInputBox);
        wasManualInputFocused = true;
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int action = ClientControls.resolveMouseAction(mx, my, btn);
        if (detailTagPicker != null) {
            detailTagPicker.mouseClicked(mx, my, action < 0 ? btn : action,
                    InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_LCONTROL));
            return true;
        }
        if (action != -1 && handleInteraction(mx, my, action))
            return true;
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (detailTagPicker != null) {
            detailTagPicker.mouseDragged(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (detailTagPicker != null) {
            detailTagPicker.mouseReleased();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private boolean handleInteraction(double mx, double my, int action) {
        if (action != 0 && action != 1)
            return false;

        if (blurManualInputIfNeeded(mx, my))
            return true;
        if (action == 0 && isHoveringBackButton(mx, my)) {
            ClickSound.play();
            return returnToNodeScreen();
        }
        if (!detailPage.isOpen() && handleClipboardButtonClick(mx, my, action)) {
            return true;
        }

        if (menu.isTagMode())
            return finishInteraction(handleTagClick(mx, my, action), mx, my);
        if (menu.isModMode())
            return finishInteraction(handleModClick(mx, my, action), mx, my);
        if (menu.isAmountMode())
            return finishInteraction(handleAmountClick(mx, my, action), mx, my);
        if (menu.isDurabilityMode())
            return finishInteraction(handleDurabilityClick(mx, my, action), mx, my);
        if (menu.isNameMode())
            return finishInteraction(handleNameClick(mx, my, action), mx, my);
        return handleDefaultModeInteraction(mx, my, action);
    }

    private boolean handleDefaultModeInteraction(double mx, double my, int action) {
        if (detailPage.isOpen())
            return handleDetailPageInteraction(mx, my, action);
        if (tagEditSlot >= 0)
            return handleTagPageInteraction(mx, my, action);
        if (nbtEditSlot >= 0)
            return handleNbtPageInteraction(mx, my, action);

        if (action == 0 && ClientControls.modifier2Down()) {
            int hoveredSlot = getHoveredFilterSlot((int) mx, (int) my);
            if (hoveredSlot >= 0) {
                ClickSound.play();
                if (RecipeCardItem.is(menu.slots.get(hoveredSlot).getItem())) {
                    editRecipeCard(hoveredSlot);
                } else {
                    tagEditSlot = -1;
                    nbtEditSlot = -1;
                    detailPage.open(hoveredSlot);
                }
                return true;
            }
        }
        int hoveredSlot = getHoveredFilterSlot((int) mx, (int) my);
        if (hoveredSlot >= 0) {
            minecraft.gameMode.handleContainerInput(menu.containerId, hoveredSlot, action,
                    net.minecraft.world.inventory.ContainerInput.PICKUP, minecraft.player);
            return true;
        }

        return finishInteraction(handleModeControlClick(mx, my), mx, my);
    }

    private boolean handleDetailPageInteraction(double mx, double my, int action) {
        if (!detailPage.mouseClicked(mx, my, action))
            detailPage.close();
        return true;
    }

    private boolean handleTagPageInteraction(double mx, double my, int action) {
        if (!handleTagSubModeClick(mx, my, action))
            closeTagSubMode();
        return true;
    }

    private boolean handleNbtPageInteraction(double mx, double my, int action) {
        if (!handleNbtSubModeClick(mx, my, action))
            closeNbtSubMode();
        return true;
    }

    private boolean finishInteraction(boolean handled, double mx, double my) {
        if (handled)
            return true;
        if (isDropdownOpen && !isHoveringDropdown(mx, my)) {
            isDropdownOpen = false;
            return true;
        }
        return false;
    }

    private boolean handleModeControlClick(double mx, double my) {
        int btnH = 12;
        int btnY = topPos + 6;
        int rightEdge = leftPos + imageWidth - 8;

        String modeLabel = menu.isBlacklistMode()
                ? tr("gui.logisticsnetworks.filter.mode.blacklist")
                : tr("gui.logisticsnetworks.filter.mode.whitelist");
        int modeBtnW = Math.max(48, font.width(modeLabel) + 8);
        int modeBtnX = rightEdge - modeBtnW;

        if (isHovering(modeBtnX, btnY, modeBtnW, btnH, (int) mx, (int) my)) {
            ClickSound.play();
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
            return true;
        }

        return false;
    }

    private boolean isHoveringDropdown(double mx, double my) {
        if (!isDropdownOpen)
            return false;
        return true;
    }

    private boolean handleTagClick(double mx, double my, int btn) {
        return false;
    }

    private boolean handleModClick(double mx, double my, int action) {
        int x = getSelectorInputX();
        int y = getSelectorInputY();
        int w = getSelectorInputWidth();
        int arrowX = getSelectorArrowX();

        if (handleModeControlClick(mx, my))
            return true;

        int typeBtnW = modTargetButtonW();
        if (isHovering(leftPos + imageWidth - 8 - typeBtnW, topPos + 20, typeBtnW, 12, (int) mx, (int) my)) {
            ClickSound.play();
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 8);
            }
            return true;
        }

        if (isHovering(arrowX, y, 12, 14, (int) mx, (int) my)) {
            ClickSound.play();
            isDropdownOpen = !isDropdownOpen;
            listScrollOffset = 0;
            return true;
        }

        if (isDropdownOpen) {
            if (isHovering(x, y + 16, w, DROPDOWN_ROWS * LIST_ROW_H, (int) mx, (int) my)) {
                int row = ((int) my - (y + 16)) / LIST_ROW_H;
                int idx = listScrollOffset + row;
                if (idx >= 0 && idx < cachedMods.size()) {
                    ClickSound.play();
                    String mod = cachedMods.get(idx);
                    menu.setSelectedMod(mod);
                    manualInputBox.setValue(mod);
                    sendModUpdate(mod);
                    isDropdownOpen = false;
                    return true;
                }
            }
        }
        if (handleManualInputClick(mx, my, action)) {
            isDropdownOpen = false;
            return true;
        }
        if (action == 1 && isHovering(x, y, w, 14, (int) mx, (int) my)) {
            String toRemove = menu.getSelectedMod();
            menu.setSelectedMod(null);
            sendModRemove(toRemove);
            manualInputBox.setValue("");
            return true;
        }
        return false;
    }

    private boolean handleAmountClick(double mx, double my, int btn) {
        return false;
    }

    private boolean handleDurabilityClick(double mx, double my, int btn) {
        return false;
    }

    private boolean checkAmountBtn(double mx, double my, int cx, int y, int delta, String label) {
        int w = Math.max(24, font.width(label) + 10);
        return isHovering(cx - w / 2, y, w, 14, (int) mx, (int) my);
    }



    private void sendModUpdate(String mod) {
        menu.setSelectedMod(mod == null || mod.isBlank() ? null : mod.trim());
        ClientPacketDistributor.sendToServer(new ModifyFilterModPayload(mod == null ? "" : mod, false));
    }

    private void sendModRemove(String mod) {
        menu.setSelectedMod(null);
        ClientPacketDistributor.sendToServer(new ModifyFilterModPayload(mod == null ? "" : mod, true));
    }




    private void sendNameUpdate(String name) {
        String expression = name == null ? "" : name;
        if (expression.length() <= NameFilterData.MAX_EXPRESSION_LENGTH)
            ClientPacketDistributor.sendToServer(new SetNameFilterPayload(expression));
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
        NameFilterData.ValidationResult validation = NameFilterData.validateRegex(value);
        boolean validRegex = value.isEmpty() || validation.accepted();
        String display = value.isEmpty()
                ? Component.translatable("gui.logisticsnetworks.filter.name.none").getString()
                : value;
        String activeKey = validRegex
                ? "gui.logisticsnetworks.filter.name.active"
                : "gui.logisticsnetworks.filter.name.pattern";
        int activeColor = validRegex ? cAccent() : cMuted();
        String activeLine = Component.translatable(activeKey, display).getString();
        g.drawString(font, font.plainSubstrByWidth(activeLine, contentW), contentX, activeY, activeColor, false);

        if (!value.isEmpty() && !validRegex) {
            String warningKey = switch (validation.error()) {
                case TOO_LONG -> "gui.logisticsnetworks.filter.name.too_long";
                case UNSUPPORTED -> "gui.logisticsnetworks.filter.name.unsafe_regex";
                default -> "gui.logisticsnetworks.filter.name.invalid_regex";
            };
            String warning = Component.translatable(warningKey).getString();
            g.drawString(font, warning, contentX, hintY, cDanger(), false);
        } else {
            String hintLine = Component.translatable("gui.logisticsnetworks.filter.name.input_hint").getString();
            g.drawString(font, font.plainSubstrByWidth(hintLine, contentW), contentX, hintY, cMuted(), false);
        }
    }

    private void renderNameButtons(GuiGraphics g, int mx, int my, int btnY) {
        int btnH = 12;
        int leftEdge = leftPos + 8;

        String typeLabel;
        if (menu.getTargetType() == FilterTargetType.CHEMICALS) {
            typeLabel = tr("gui.logisticsnetworks.filter.target.chemicals");
        } else if (menu.getTargetType() == FilterTargetType.FLUIDS) {
            typeLabel = tr("gui.logisticsnetworks.filter.target.fluids");
        } else {
            typeLabel = tr("gui.logisticsnetworks.filter.target.items");
        }
        int typeBtnW = Math.max(40, font.width(typeLabel) + 8);
        drawButton(g, leftEdge, btnY, typeBtnW, btnH, typeLabel, mx, my, true);
        int modeBtnX = leftEdge + typeBtnW + 4;

        String modeLabel = menu.isBlacklistMode()
                ? tr("gui.logisticsnetworks.filter.mode.blacklist")
                : tr("gui.logisticsnetworks.filter.mode.whitelist");
        int modeBtnW = Math.max(48, font.width(modeLabel) + 8);
        drawButton(g, modeBtnX, btnY, modeBtnW, btnH, modeLabel, mx, my, true);

        String scopeLabel = tr("gui.logisticsnetworks.filter.name.scope." + menu.getNameMatchScope().serializedName());
        int scopeBtnW = Math.max(40, font.width(scopeLabel) + 8);
        drawButton(g, modeBtnX + modeBtnW + 4, btnY, scopeBtnW, btnH, scopeLabel, mx, my, true);
    }

    private boolean handleNameClick(double mx, double my, int action) {
        int contentX = leftPos + 8;
        int inputY = topPos + 38;
        int contentW = imageWidth - 16;

        if (handleNameButtonsClick(mx, my))
            return true;

        if (handleManualInputClick(mx, my, action)) {
            return true;
        }
        if (action == 1 && isHovering(contentX, inputY, contentW, 14, (int) mx, (int) my)) {
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

        String typeLabel;
        if (menu.getTargetType() == FilterTargetType.CHEMICALS) {
            typeLabel = tr("gui.logisticsnetworks.filter.target.chemicals");
        } else if (menu.getTargetType() == FilterTargetType.FLUIDS) {
            typeLabel = tr("gui.logisticsnetworks.filter.target.fluids");
        } else {
            typeLabel = tr("gui.logisticsnetworks.filter.target.items");
        }
        int typeBtnW = Math.max(40, font.width(typeLabel) + 8);
        if (isHovering(leftEdge, btnY, typeBtnW, btnH, (int) mx, (int) my)) {
            ClickSound.play();
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 8);
            }
            return true;
        }
        int modeBtnX = leftEdge + typeBtnW + 4;

        String modeLabel = menu.isBlacklistMode()
                ? tr("gui.logisticsnetworks.filter.mode.blacklist")
                : tr("gui.logisticsnetworks.filter.mode.whitelist");
        int modeBtnW = Math.max(48, font.width(modeLabel) + 8);

        if (isHovering(modeBtnX, btnY, modeBtnW, btnH, (int) mx, (int) my)) {
            ClickSound.play();
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
            return true;
        }

        String scopeLabel = tr("gui.logisticsnetworks.filter.name.scope." + menu.getNameMatchScope().serializedName());
        int scopeBtnW = Math.max(40, font.width(scopeLabel) + 8);
        if (isHovering(modeBtnX + modeBtnW + 4, btnY, scopeBtnW, btnH, (int) mx, (int) my)) {
            ClickSound.play();
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 9);
            }
            return true;
        }

        return false;
    }

    private void flushEditorsBeforeExit() {
        if (flushedEditorsOnExit) {
            return;
        }
        flushedEditorsOnExit = true;
        flushOpenEditors();
        wasManualInputFocused = false;
    }

    private boolean saveManualInputAndClearFocus() {
        if (manualInputBox == null || !manualInputBox.isVisible()) {
            return false;
        }
        if (!commitManualInput())
            return false;
        manualInputBox.setFocused(false);
        wasManualInputFocused = false;
        return true;
    }

    private boolean commitManualInput() {
        if (manualInputBox == null || !manualInputBox.isVisible()) {
            return false;
        }

        String val = manualInputBox.getValue() == null ? "" : manualInputBox.getValue().trim();
        if (menu.isModMode()) {
            if (val.isEmpty()) {
                sendModRemove(menu.getSelectedMod());
            } else {
                sendModUpdate(val);
            }
            return true;
        } else if (menu.isNameMode()) {
            NameFilterData.ValidationResult validation = NameFilterData.validateRegex(val);
            if (!val.isEmpty() && !validation.accepted()) {
                showRegexChatMessage(validation.error());
                return false;
            }
            menu.setNameFilter(val);
            sendNameUpdate(val);
            return true;
        }
        return false;
    }

    private void showRegexChatMessage(NameFilterData.ValidationError error) {
        if (minecraft == null || minecraft.player == null)
            return;
        String key = switch (error) {
            case TOO_LONG -> "message.logisticsnetworks.filter.regex.too_long";
            case UNSUPPORTED -> "message.logisticsnetworks.filter.regex.unsupported";
            default -> "message.logisticsnetworks.filter.regex.invalid";
        };
        minecraft.player.sendSystemMessage(Component.translatable(key));
    }

    @Override
    public void onClose() {
        flushEditorsBeforeExit();
        super.onClose();
    }

    @Override
    public void removed() {
        flushEditorsBeforeExit();
        super.removed();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (detailTagPicker != null) {
            int action = ClientControls.resolveKeyAction(key, scan, modifiers);
            if (key != InputConstants.KEY_ESCAPE && action == 0 && !detailTagPicker.isSearchFocused()) {
                detailTagPicker.mouseClicked(ClientControls.cursorX(minecraft), ClientControls.cursorY(minecraft), action,
                        InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_LCONTROL));
            } else {
                detailTagPicker.keyPressed(key, scan, modifiers);
            }
            return true;
        }
        if (minecraft.options.keyInventory.matches(new net.minecraft.client.input.KeyEvent(key, scan, modifiers))) {
            return true;
        }
        if (key == 256) {
            if (detailPage.isOpen()) {
                detailPage.escape();
                return true;
            }
            if (manualInputBox != null && manualInputBox.isFocused()) {
                flushEditorsBeforeExit();
                manualInputBox.setFocused(false);
                if (returnToNodeScreen()) {
                    return true;
                }
                return super.keyPressed(key, scan, modifiers);
            }
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
            if (returnToNodeScreen()) {
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }

        if (handleClipboardShortcut(key)) {
            return true;
        }

        if (detailPage.isOpen() && detailPage.keyPressed(key, scan, modifiers)) {
            return true;
        }

        if (nbtValueEditBox != null && nbtValueEditBox.isFocused()) {
            if (key == 257) {
                commitNbtValueEdit();
                return true;
            }
            nbtValueEditBox.keyPressed(ClientInput.key(key, scan, modifiers));
            return true;
        }

        if (tagInputBox != null && tagInputBox.isFocused()) {
            if (key == 257) {
                commitTagInput();
                return true;
            }
            tagInputBox.keyPressed(ClientInput.key(key, scan, modifiers));
            return true;
        }

        if (manualInputBox.isFocused()) {
            if (key == 257) {
                saveManualInputAndClearFocus();
                return true;
            }
            manualInputBox.keyPressed(ClientInput.key(key, scan, modifiers));
            return true;
        }

        int action = ClientControls.resolveKeyAction(key, scan, modifiers);
        if (action != -1) {
            if (action == 1 && FilterClickHandler.openHoveredFilter(this)) {
                return true;
            }
            handleInteraction(ClientControls.cursorX(minecraft), ClientControls.cursorY(minecraft), action);
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (detailTagPicker != null) {
            detailTagPicker.charTyped(c, modifiers);
            return true;
        }
        if (detailPage.isOpen()) {
            return detailPage.charTyped(c, modifiers);
        }
        if (nbtValueEditBox != null && nbtValueEditBox.isFocused()) {
            nbtValueEditBox.charTyped(ClientInput.character(c));
            return true;
        }
        if (tagInputBox != null && tagInputBox.isFocused()) {
            tagInputBox.charTyped(ClientInput.character(c));
            return true;
        }
        if (manualInputBox.isFocused()) {
            manualInputBox.charTyped(ClientInput.character(c));
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (detailTagPicker != null) {
            detailTagPicker.mouseScrolled(mx, my, sy);
            return true;
        }
        if (detailPage.isOpen()) {
            return detailPage.mouseScrolled(mx, my, sy);
        }

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
                && !menu.isDurabilityMode() && !menu.isNameMode()) {
            int hoveredSlot = getHoveredFilterSlot((int) mx, (int) my);
            if (hoveredSlot >= 0 && hasEntryInSlot(hoveredSlot)) {
                int current = menu.getEntryAmount(hoveredSlot);
                int next;
                if (ClientControls.modifier3Down()) {
                    next = sy > 0 ? getMaxAmountForType(menu.getEntryKind(hoveredSlot)) : (current > 0 ? 1 : 0);
                } else {
                    int delta = computeScrollDelta(sy, menu.getEntryKind(hoveredSlot));
                    next = Math.max(0, current + delta);
                }
                if (next != current) {
                    menu.setEntryAmount(minecraft.player, hoveredSlot, next);
                    int currentStock = menu.getEntryStock(hoveredSlot);
                    ClientPacketDistributor.sendToServer(new SetFilterEntryAmountPayload(hoveredSlot, next, currentStock));
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
        ItemStack openedStack = menu.getOpenedStack();
        if (menu.isTagSlot(slot) || FilterItemData.isNbtOnlySlot(openedStack, slot))
            return true;
        if (!menu.getFluidFilter(slot).isEmpty() || menu.getChemicalFilter(slot) != null)
            return true;
        if (slot < menu.slots.size() && !menu.slots.get(slot).getItem().isEmpty())
            return true;
        return FilterItemData.hasEntrySlotMapping(openedStack, slot);
    }

    private String targetLabel(FilterTargetType kind) {
        return tr("gui.logisticsnetworks.filter.target." + kind.getSerializedName());
    }

    private int modTargetButtonW() {
        return Math.max(40, font.width(targetLabel(menu.getTargetType())) + 8);
    }

    static FilterTargetType nextKind(FilterTargetType kind) {
        FilterTargetType next = kind.next();
        return next == FilterTargetType.CHEMICALS && !MekanismCompat.isLoaded() ? next.next() : next;
    }

    static int computeScrollDelta(double scrollDirection, FilterTargetType targetType) {
        int sign = scrollDirection > 0 ? 1 : -1;
        if (targetType == FilterTargetType.FLUIDS || targetType == FilterTargetType.CHEMICALS) {
            if (ClientControls.modifier2Down())
                return sign * 1000;
            if (ClientControls.modifier1Down())
                return sign * 500;
            return sign * 50;
        }
        if (ClientControls.modifier2Down())
            return sign * 64;
        if (ClientControls.modifier1Down())
            return sign * 8;
        return sign;
    }

    private int getMaxAmountForType(FilterTargetType targetType) {
        if (targetType == FilterTargetType.FLUIDS || targetType == FilterTargetType.CHEMICALS) {
            return 1_000_000;
        }
        return 1024;
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

    public void setFluidFilterEntry(Player player, int slot, FluidStack fluidStack) {
        if (fluidStack.isEmpty())
            return;
        ClientPacketDistributor.sendToServer(
                new SetFilterFluidEntryPayload(slot, BuiltInRegistries.FLUID.getKey(fluidStack.getFluid()).toString()));
        menu.setFluidFilterEntry(player, slot, fluidStack);
    }

    public void setChemicalFilterEntry(Player player, int slot, String chemicalId) {
        if (chemicalId == null || chemicalId.isBlank())
            return;
        ClientPacketDistributor.sendToServer(
                new SetFilterChemicalEntryPayload(slot, chemicalId));
        menu.setChemicalFilterEntry(player, slot, chemicalId);
    }

    public void setItemFilterEntry(Player player, int slot, ItemStack stack) {
        if (stack.isEmpty())
            return;
        ClientPacketDistributor.sendToServer(SetFilterItemEntryPayload.set(slot, stack));
        menu.setItemFilterEntry(player, slot, stack);
    }

    private void clearItemFilterEntry(int slot) {
        menu.clearFilterEntryItem(minecraft.player, slot);
        ClientPacketDistributor.sendToServer(SetFilterItemEntryPayload.clearItem(slot));
    }

    private void clearFilterEntry(int slot) {
        menu.clearFilterEntry(slot);
        ClientPacketDistributor.sendToServer(SetFilterItemEntryPayload.clearEntry(slot));
    }

    public boolean acceptsFluidSelectorGhostIngredient() {
        return menu.isTagMode() || menu.isModMode();
    }

    public boolean acceptsItemSelectorGhostIngredient() {
        return menu.isTagMode() || menu.isModMode();
    }

    public boolean supportsGhostIngredientTargets() {
        if (detailPage.isOpen()) return false;
        return !menu.isTagMode() && !menu.isModMode() && !menu.isAmountMode()
                && !menu.isDurabilityMode() && !menu.isNameMode();
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

    public boolean isDetailPageOpen() {
        return detailPage.isOpen() && !detailPage.isNbtOpen() && detailTagPicker == null;
    }

    public Rect2i getDetailSlotArea() {
        return detailPage.slotArea();
    }

    public void setDetailGhostItem(ItemStack stack) {
        if (!isDetailPageOpen() || stack.isEmpty()) return;
        detailPage.dropItem(stack);
    }

    public void setDetailGhostFluid(FluidStack stack) {
        if (!isDetailPageOpen() || stack.isEmpty()) return;
        detailPage.dropFluid(stack);
    }

    public void setDetailGhostChemical(String chemicalId) {
        if (!isDetailPageOpen() || chemicalId == null || chemicalId.isBlank()) return;
        detailPage.dropChemical(chemicalId);
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
            g.fill(x, y, x + 18, y + 18, cHover());
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
            slotItem.getItem().builtInRegistryHolder().tags()
                    .map(tagKey -> tagKey.location().toString())
                    .forEach(cachedSlotTags::add);
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
            FluidStack fluid = menu.getFluidFilter(slot);
            if (!fluid.isEmpty()) {
                cachedSlotNbtEntries.addAll(NbtFilterData.extractEntries(
                        fluid, minecraft.player.level().registryAccess()));
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
        cachedSlotNbtPathLabels = cachedSlotNbtEntries.stream()
                .map(entry -> formatNbtPath(entry.path().toString()))
                .toList();
    }

    private ItemStack getSlotItemForSubMode(int slot) {
        if (menu.isTagSlot(slot)) {
            return ItemStack.EMPTY;
        }
        return FilterItemData.getEntry(menu.getOpenedStack(), slot);
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

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, cOverlayPanel());
        g.renderOutline(panelX, panelY, panelW, panelH, cAccent());

        String title = "Tag for slot " + tagEditSlot;
        g.drawString(font, title, panelX + 4, panelY + 4, cText(), false);

        String current = menu.getEntryTag(tagEditSlot);
        String display = current != null ? "#" + current : "None";
        g.drawString(font, display, panelX + 4, panelY + 16, cAccent(), false);

        int clearW = 40;
        int clearX = panelX + panelW - clearW - 4;
        int clearY = panelY + 4;
        boolean hoverClear = isHovering(clearX, clearY, clearW, 12, mx, my);
        g.fill(clearX, clearY, clearX + clearW, clearY + 12,
                hoverClear ? cBtnHover() : cBtnBg());
        g.renderOutline(clearX, clearY, clearW, 12,
                hoverClear ? cText() : cBtnBorder());
        g.drawCenteredString(font, "Clear", clearX + clearW / 2, clearY + 2,
                hoverClear ? cText() : cMuted());

        int inputY = panelY + 30;
        int inputW = panelW - 60;
        tagInputBox.setX(panelX + 4);
        tagInputBox.setY(inputY);
        tagInputBox.setWidth(inputW);
        tagInputBox.extractRenderState(g.raw(), mx, my, 0);

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
        g.renderOutline(listX, listY, listW, listH, cBorder());

        for (int i = startIdx; i < endIdx; i++) {
            int rowY = listY + (i - startIdx) * LIST_ROW_H;
            String tag = cachedSlotTags.get(i);
            boolean selected = Objects.equals(tag, current);
            boolean hovered = mx >= listX && mx < listX + rowW
                    && my >= rowY && my < rowY + LIST_ROW_H;

            if (selected)
                g.fill(listX + 1, rowY, listX + rowW - 1, rowY + LIST_ROW_H, cSelected());
            else if (hovered)
                g.fill(listX + 1, rowY, listX + rowW - 1, rowY + LIST_ROW_H, cHover());

            String text = scrollText(tag, rowW - 6, i);
            g.drawString(font, text, listX + 3, rowY + 2,
                    selected ? cAccent() : cText(), false);
        }

        if (scrollable) {
            int maxScroll = getTagSubModeMaxScroll();
            int scrollbarX = listX + rowW + SUBMODE_SCROLLBAR_GAP;
            int thumbH = Math.max(8, (listH * maxVisibleRows) / cachedSlotTags.size());
            int thumbTravel = Math.max(0, listH - thumbH);
            int thumbY = maxScroll <= 0 ? listY : listY + (subModeScrollOffset * thumbTravel) / maxScroll;

            g.fill(scrollbarX, listY, scrollbarX + SUBMODE_SCROLLBAR_W, listY + listH, cBtnBg());
            g.renderOutline(scrollbarX, listY, SUBMODE_SCROLLBAR_W, listH, cBtnBorder());
            g.fill(scrollbarX + 1, thumbY, scrollbarX + SUBMODE_SCROLLBAR_W - 1, thumbY + thumbH, cAccent());

            if (subModeScrollOffset > 0) {
                g.fill(scrollbarX + 1, listY + 1, scrollbarX + SUBMODE_SCROLLBAR_W - 1, listY + 2, cText());
            }
            if (subModeScrollOffset < maxScroll) {
                g.fill(scrollbarX + 1, listY + listH - 2, scrollbarX + SUBMODE_SCROLLBAR_W - 1,
                        listY + listH - 1, cText());
            }
        }

        if (cachedSlotTags.isEmpty()) {
            g.drawString(font, "No tags available", listX + 3, listY + 2, cMuted(), false);
        }

        g.pose().popPose();
    }

    private boolean handleTagSubModeClick(double mx, double my, int action) {
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
            ClientPacketDistributor.sendToServer(SetFilterEntryTagPayload.clear(tagEditSlot));
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
                tagInputBox.mouseClicked(ClientInput.mouse(mx, my, action), false);
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
                ClientPacketDistributor.sendToServer(new SetFilterEntryTagPayload(tagEditSlot, tag, menu.getEntryKind(tagEditSlot)));
                menu.setEntryTag(minecraft.player, tagEditSlot, tag, menu.getEntryKind(tagEditSlot));
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
            ClientPacketDistributor.sendToServer(new SetFilterEntryTagPayload(tagEditSlot, normalizedTag, menu.getEntryKind(tagEditSlot)));
            menu.setEntryTag(minecraft.player, tagEditSlot, normalizedTag, menu.getEntryKind(tagEditSlot));
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

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, cBg());
        g.renderOutline(panelX, panelY, panelW, panelH, cWarn());

        g.drawString(font, tr("gui.logisticsnetworks.filter.nbt.slot_title", nbtEditSlot + 1),
                panelX + 4, panelY + 4, cText(), false);

        // Clear button
        int clearW = 30;
        int clearX = panelX + panelW - clearW - 4;
        int clearY = panelY + 4;
        drawButton(g, clearX, clearY, clearW, 10, tr("gui.logisticsnetworks.filter.nbt.clear"), mx, my, true);

        // NBT entries list
        List<NbtCriterion> activeRules = menu.getSlotNbtRules(nbtEditSlot);
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
            List<NbtCriterion> activeRules, int mx, int my) {
        int totalEntries = cachedSlotNbtEntries.size();
        int maxRows = Math.max(1, listH / LIST_ROW_H);
        int maxScroll = Math.max(0, totalEntries - maxRows);
        nbtListScrollOffset = Mth.clamp(nbtListScrollOffset, 0, maxScroll);
        int drawH = maxRows * LIST_ROW_H;
        boolean scrollable = totalEntries > maxRows;
        int rowW = scrollable ? listW - SUBMODE_SCROLLBAR_W - SUBMODE_SCROLLBAR_GAP : listW;
        int endIdx = Math.min(nbtListScrollOffset + maxRows, totalEntries);

        g.fill(listX, listY, listX + listW, listY + drawH, cBg());
        g.renderOutline(listX, listY, listW, drawH, cBorder());

        int colW = getNbtColW(rowW);

        for (int i = nbtListScrollOffset; i < endIdx; i++) {
            int rowY = listY + (i - nbtListScrollOffset) * LIST_ROW_H;
            NbtFilterData.NbtEntry entry = cachedSlotNbtEntries.get(i);
            int ruleIdx = findActiveRuleIndex(activeRules, entry.path());
            boolean active = ruleIdx >= 0;
            boolean hovered = mx >= listX && mx < listX + rowW && my >= rowY && my < rowY + LIST_ROW_H;
            boolean editing = active && nbtEditingRuleIndex == ruleIdx;

            if (active)
                g.fill(listX + 1, rowY, listX + rowW - 1, rowY + LIST_ROW_H, cSelected());
            else if (hovered)
                g.fill(listX + 1, rowY, listX + rowW - 1, rowY + LIST_ROW_H, cHover());

            int dotX = listX + 2;
            int dotY = rowY + (LIST_ROW_H - 5) / 2;
            if (active) {
                g.fill(dotX, dotY, dotX + 5, dotY + 5, cAccent());
            } else {
                g.renderOutline(dotX, dotY, 5, 5, cMuted());
            }

            NbtCriterion activeRule = active ? activeRules.get(ruleIdx) : null;
            String op = active ? activeRule.operator() : "=";

            int pathX = listX + NBT_INDICATOR_W;
            int opX = pathX + colW + 1;
            int valX = opX + NBT_OP_BTN_W + 1;

            String displayPath = cachedSlotNbtPathLabels.get(i);
            g.fill(pathX, rowY, pathX + colW, rowY + LIST_ROW_H, theme().slotBg());
            g.renderOutline(pathX, rowY, colW, LIST_ROW_H, active ? cBtnBorder() : cBorder());
            g.drawString(font, font.plainSubstrByWidth(displayPath, colW - 4),
                    pathX + 2, rowY + 1, active ? cAccent() : cText(), false);

            boolean opHover = active && isHovering(opX, rowY, NBT_OP_BTN_W, LIST_ROW_H, mx, my);
            g.fill(opX, rowY, opX + NBT_OP_BTN_W, rowY + LIST_ROW_H,
                    opHover ? cBtnHover() : cBtnBg());
            g.renderOutline(opX, rowY, NBT_OP_BTN_W, LIST_ROW_H, cBtnBorder());
            g.drawCenteredString(font, op, opX + NBT_OP_BTN_W / 2, rowY + 1,
                    active ? (opHover ? cText() : cWarn()) : cMuted());

            String displayVal = active
                    ? formatNbtValue(activeRule.value().toString())
                    : formatNbtValue(entry.valueDisplay());

            g.fill(valX, rowY, valX + colW, rowY + LIST_ROW_H, theme().slotBg());
            boolean isBool = isBooleanValue(displayVal);
            g.renderOutline(valX, rowY, colW, LIST_ROW_H,
                    editing ? cAccent() : (active ? cBtnBorder() : cBorder()));

            if (editing) {
                nbtValueEditBox.setX(valX + 2);
                nbtValueEditBox.setY(rowY + 1);
                nbtValueEditBox.setWidth(colW - 4);
                nbtValueEditBox.setVisible(true);
                nbtValueEditBox.extractRenderState(g.raw(), mx, my, 0);
            } else {
                int valColor;
                if (isBool) {
                    valColor = "true".equals(displayVal) ? cAccent() : cDanger();
                } else {
                    valColor = active ? cText() : cMuted();
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
            g.fill(scrollbarX, listY, scrollbarX + SUBMODE_SCROLLBAR_W, listY + drawH, cBtnBg());
            g.renderOutline(scrollbarX, listY, SUBMODE_SCROLLBAR_W, drawH, cBtnBorder());
            g.fill(scrollbarX + 1, thumbY, scrollbarX + SUBMODE_SCROLLBAR_W - 1, thumbY + thumbH, cAccent());
        }

        if (totalEntries == 0) {
            g.drawString(font, tr("gui.logisticsnetworks.filter.nbt.no_entries"),
                    listX + 3, listY + 2, cMuted(), false);
        }
    }

    private boolean handleNbtSubModeClick(double mx, double my, int action) {
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
            ClientPacketDistributor.sendToServer(SetFilterEntryNbtPayload.clear(nbtEditSlot));
            menu.clearSlotNbtRules(nbtEditSlot);
            return true;
        }

        // NBT entries list
        List<NbtCriterion> activeRules = menu.getSlotNbtRules(nbtEditSlot);
        int listX = panelX + 4;
        int listY = panelY + 16;
        int listW = panelW - 8;
        int listH = panelH - 34;
        if (handleNbtEntryListClick(listX, listY, listW, listH, activeRules, mx, my, action))
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
            ClientPacketDistributor.sendToServer(SetFilterEntryNbtPayload.toggleMatch(nbtEditSlot));
            menu.toggleSlotNbtMatchMode(nbtEditSlot);
            return true;
        }

        commitNbtValueEditIfActive();
        return true;
    }

    private boolean handleNbtEntryListClick(int listX, int listY, int listW, int listH,
            List<NbtCriterion> activeRules, double mx, double my, int action) {
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
            NbtPath path = entry.path();
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
                ClientPacketDistributor.sendToServer(SetFilterEntryNbtPayload.remove(nbtEditSlot, ruleIdx));
                menu.removeSlotNbtRule(nbtEditSlot, ruleIdx);
                ClientPacketDistributor.sendToServer(SetFilterEntryNbtPayload.add(nbtEditSlot, entry.path(), newOp, ""));
                if (minecraft != null && minecraft.player != null) {
                    menu.addSlotNbtRule(minecraft.player, nbtEditSlot, entry.path(), newOp);
                }
                List<NbtCriterion> updatedRules = menu.getSlotNbtRules(nbtEditSlot);
                int newIdx = findActiveRuleIndex(updatedRules, path);
                if (newIdx >= 0) {
                    ClientPacketDistributor.sendToServer(
                            SetFilterEntryNbtPayload.setValue(nbtEditSlot, newIdx, savedVal));
                    menu.setSlotNbtRuleValue(nbtEditSlot, newIdx, savedVal);
                }
                return true;
            }

            // Click value area of active rule
            if (active && action == 0 && mx >= valX) {
                String displayVal = formatNbtValue(activeRules.get(ruleIdx).value().toString());

                // Boolean toggle
                if (isBooleanValue(displayVal)) {
                    commitNbtValueEditIfActive();
                    String toggled = "true".equals(displayVal) ? "false" : "true";
                    ClientPacketDistributor.sendToServer(
                            SetFilterEntryNbtPayload.setValue(nbtEditSlot, ruleIdx, toggled));
                    menu.setSlotNbtRuleValue(nbtEditSlot, ruleIdx, toggled);
                    return true;
                }

                // Edit value
                if (nbtEditingRuleIndex == ruleIdx) {
                    nbtValueEditBox.mouseClicked(ClientInput.mouse(mx, my, action), false);
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
            if (action == 0 && mx < opX) {
                commitNbtValueEditIfActive();
                if (active) {
                    ClientPacketDistributor.sendToServer(SetFilterEntryNbtPayload.remove(nbtEditSlot, ruleIdx));
                    menu.removeSlotNbtRule(nbtEditSlot, ruleIdx);
                } else {
                    ClientPacketDistributor.sendToServer(SetFilterEntryNbtPayload.add(nbtEditSlot, entry.path(), "=", ""));
                    if (minecraft != null && minecraft.player != null) {
                        menu.addSlotNbtRule(minecraft.player, nbtEditSlot, entry.path(), "=");
                    }
                }
                return true;
            }

            // Right-click = cycle operator (if active)
            if (active && action == 1) {
                commitNbtValueEditIfActive();
                String savedVal = formatNbtValue(activeRules.get(ruleIdx).value().toString());
                String currentOp = activeRules.get(ruleIdx).operator();
                String newOp = FilterItemData.nextNbtOperator(currentOp);
                ClientPacketDistributor.sendToServer(SetFilterEntryNbtPayload.remove(nbtEditSlot, ruleIdx));
                menu.removeSlotNbtRule(nbtEditSlot, ruleIdx);
                ClientPacketDistributor.sendToServer(SetFilterEntryNbtPayload.add(nbtEditSlot, entry.path(), newOp, ""));
                if (minecraft != null && minecraft.player != null) {
                    menu.addSlotNbtRule(minecraft.player, nbtEditSlot, entry.path(), newOp);
                }
                List<NbtCriterion> updatedRules = menu.getSlotNbtRules(nbtEditSlot);
                int newIdx = findActiveRuleIndex(updatedRules, path);
                if (newIdx >= 0) {
                    ClientPacketDistributor.sendToServer(
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

    static int findActiveRuleIndex(List<NbtCriterion> rules, NbtPath path) {
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

    static String formatNbtValue(String raw) {
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
            ClientPacketDistributor.sendToServer(SetFilterEntryNbtPayload.setValue(nbtEditSlot, nbtEditingRuleIndex, val));
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
        cachedSlotNbtPathLabels = List.of();
        nbtPendingOperator = "=";
        nbtListScrollOffset = 0;
    }

    public List<Rect2i> getExtraAreas() {
        if (detailTagPicker != null) {
            return List.of(new Rect2i(0, 0, width, height));
        }
        List<Rect2i> areas = new ArrayList<>();
        if (detailPage.isOpen()) {
            areas.add(new Rect2i(leftPos, topPos, imageWidth, imageHeight));
        }
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

    private boolean isAnyItemSlot(int slot) {
        if (slot >= menu.slots.size()) return false;
        if (!menu.slots.get(slot).getItem().isEmpty()) return false;
        if (menu.isTagSlot(slot)) return false;
        return menu.getEntryBatch(slot) > 0 || menu.getEntryStock(slot) > 0
                || FilterItemData.hasEntrySlotMapping(menu.getOpenedStack(), slot);
    }

    private List<Component> buildFilterEntryTooltip(int slot, ItemStack filterStack) {
        List<Component> lines = new ArrayList<>();

        String tag = menu.getEntryTag(slot);
        ItemStack slotItem = slot < menu.slots.size() ? menu.slots.get(slot).getItem() : ItemStack.EMPTY;
        if (RecipeCardItem.is(slotItem)) {
            lines.addAll(getTooltipFromItem(minecraft, slotItem));
            if (menu.isBoundFilter() && menu.getGraphContext() == null) {
                lines.add(Component.translatable("tooltip.logisticsnetworks.recipe_card.edit",
                        ClientControls.MODIFIER_2.getTranslatedKeyMessage()).withStyle(ChatFormatting.DARK_GRAY));
            }
            return lines;
        }
        FluidStack fluid = menu.getFluidFilter(slot);
        String chemId = menu.getChemicalFilter(slot);
        boolean isNbtOnly = FilterItemData.isNbtOnlySlot(filterStack, slot);

        int batch = menu.getEntryBatch(slot);
        int stock = menu.getEntryStock(slot);

        if (tag != null) {
            lines.add(Component.literal("#" + tag).withStyle(ChatFormatting.GOLD));
        } else if (!slotItem.isEmpty()) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(slotItem.getItem());
            lines.add(Component.literal(itemId.toString()).withStyle(ChatFormatting.WHITE));
        } else if (!fluid.isEmpty()) {
            lines.add(fluid.getHoverName().copy().withStyle(ChatFormatting.WHITE));
        } else if (chemId != null) {
            Component name = MekanismCompat.getChemicalTextComponent(chemId);
            lines.add((name != null ? name.copy() : Component.literal(chemId)).withStyle(ChatFormatting.WHITE));
        } else if (isNbtOnly || FilterItemData.hasEntrySlotMapping(filterStack, slot)) {
            String any = isNbtOnly ? "Any Resource" : "Any Item";
            lines.add(Component.literal(any).withStyle(ChatFormatting.AQUA));
        } else {
            return lines;
        }
        if (batch > 0 || stock > 0) {
            lines.add(Component.literal("Batch " + batch + " | Stock " + stock).withStyle(ChatFormatting.GRAY));
        }

        List<NbtCriterion> nbtRules = menu.getSlotNbtRules(slot);
        if (!nbtRules.isEmpty()) {
            for (NbtCriterion r : nbtRules) {
                String display = r.path() + " " + r.operator() + " " + r.value();
                lines.add(Component.literal("NBT: " + display).withStyle(ChatFormatting.GOLD));
            }
        } else {
            String nbtRaw = FilterItemData.getEntryNbtRaw(filterStack, slot);
            if (nbtRaw != null) {
                String preview = nbtRaw.length() > 50 ? nbtRaw.substring(0, 50) + "..." : nbtRaw;
                lines.add(Component.literal("NBT: " + preview).withStyle(ChatFormatting.GOLD));
            }
        }

        String slotExpr = menu.getEntrySlotMappingExpression(slot);
        if (slotExpr != null && !slotExpr.isEmpty()) {
            lines.add(Component.literal("Slots: " + slotExpr).withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        IoSide side = menu.getEntrySide(slot);
        if (side != null) {
            lines.add(Component.translatable("gui.logisticsnetworks.filter.detail.side").append(": ")
                    .append(Component.translatable("gui.logisticsnetworks.direction." + side.key()))
                    .withStyle(ChatFormatting.AQUA));
        }

        lines.add(1, Component.translatable("gui.logisticsnetworks.filter.advanced_settings_hint",
                ClientControls.MODIFIER_2.getTranslatedKeyMessage()).withStyle(ChatFormatting.GREEN));
        lines.add(2, Component.literal("-----").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private final class DetailHost implements EntryDetailPage.Host {

        @Override
        public Rect2i panel() {
            return new Rect2i(leftPos + 4, topPos + 20, imageWidth - 8, imageHeight - 24);
        }

        @Override
        public void resize(int minWidth, int minHeight) {
            menu.setSlotsHidden(true);
            if (savedImageWidth < 0) {
                savedImageWidth = imageWidth;
                savedLeftPos = leftPos;
                savedImageHeight = imageHeight;
                savedTopPos = topPos;
            }
            imageWidth = Math.max(savedImageWidth, minWidth);
            leftPos = imageWidth > savedImageWidth ? (width - imageWidth) / 2 : savedLeftPos;
            imageHeight = Math.max(savedImageHeight, minHeight);
            topPos = imageHeight > savedImageHeight ? (height - imageHeight) / 2 : savedTopPos;
        }

        @Override
        public void restoreSize() {
            menu.setSlotsHidden(false);
            if (savedImageWidth < 0) return;
            imageWidth = savedImageWidth;
            leftPos = savedLeftPos;
            imageHeight = savedImageHeight;
            topPos = savedTopPos;
            savedImageWidth = -1;
            savedImageHeight = -1;
        }

        @Override
        public Component title(int entry) {
            return Component.translatable("gui.logisticsnetworks.filter.detail.title", entry);
        }

        @Override
        public FilterTargetType kind(int entry) {
            return menu.getEntryKind(entry);
        }

        @Override
        public String tag(int entry) {
            return menu.getEntryTag(entry);
        }

        @Override
        public FluidStack fluid(int entry) {
            return menu.getFluidFilter(entry);
        }

        @Override
        public String chemical(int entry) {
            return menu.getChemicalFilter(entry);
        }

        @Override
        public ItemStack sample(int entry) {
            return getSlotItemForSubMode(entry);
        }

        @Override
        public boolean hasEntry(int entry) {
            return hasEntryInSlot(entry);
        }

        @Override
        public boolean isNbtOnly(int entry) {
            return FilterItemData.isNbtOnlySlot(menu.getOpenedStack(), entry);
        }

        @Override
        public boolean hasNbt(int entry) {
            return FilterItemData.hasEntryNbt(menu.getOpenedStack(), entry);
        }

        @Override
        public boolean hasConfig(int entry) {
            ItemStack opened = menu.getOpenedStack();
            return FilterItemData.hasEntryNbt(opened, entry)
                    || FilterItemData.hasEntryDurability(opened, entry)
                    || FilterItemData.hasEntryEnchanted(opened, entry);
        }

        @Override
        public void renderAny(GuiGraphics g, int x, int y) {
            renderAnyResource(g, x, y);
        }

        @Override
        public void setTag(int entry, String tag, FilterTargetType kind) {
            ClientPacketDistributor.sendToServer(new SetFilterEntryTagPayload(entry, tag, kind));
            menu.setEntryTag(minecraft.player, entry, tag, kind);
        }

        @Override
        public void clearTag(int entry) {
            ClientPacketDistributor.sendToServer(SetFilterEntryTagPayload.clear(entry));
            menu.clearEntryTag(entry);
        }

        @Override
        public void setItem(int entry, ItemStack stack) {
            menu.clearEntryTag(entry);
            setItemFilterEntry(minecraft.player, entry, stack);
        }

        @Override
        public void setFluid(int entry, FluidStack stack) {
            menu.clearEntryTag(entry);
            setFluidFilterEntry(minecraft.player, entry, stack);
        }

        @Override
        public void setChemical(int entry, String chemicalId) {
            menu.clearEntryTag(entry);
            setChemicalFilterEntry(minecraft.player, entry, chemicalId);
        }

        @Override
        public void clearEntry(int entry) {
            clearFilterEntry(entry);
        }

        @Override
        public void clearItem(int entry) {
            clearItemFilterEntry(entry);
        }

        @Override
        public void openTagPicker(int entry, FilterTargetType kind, Identifier current, Consumer<String> picked) {
            detailTagPicker = new FilterTagPicker(font, kind, current, menu.getEntryTag(entry), tag -> {
                picked.accept(tag);
                detailTagPicker = null;
            }, () -> detailTagPicker = null, width, height);
        }

        @Override
        public int batch(int entry) {
            return menu.getEntryBatch(entry);
        }

        @Override
        public int stock(int entry) {
            return menu.getEntryStock(entry);
        }

        @Override
        public void setAmounts(int entry, int batch, int stock) {
            menu.setEntryBatch(minecraft.player, entry, batch);
            menu.setEntryStock(minecraft.player, entry, stock);
            ClientPacketDistributor.sendToServer(new SetFilterEntryAmountPayload(entry, batch, stock));
        }

        @Override
        public IoSide side(int entry) {
            return menu.getEntrySide(entry);
        }

        @Override
        public void setSide(int entry, IoSide side) {
            ClientPacketDistributor.sendToServer(new SetFilterEntrySidePayload(entry, side == null ? -1 : side.ordinal()));
            menu.setEntrySide(minecraft.player, entry, side);
        }

        @Override
        public String slotExpression(int entry) {
            return menu.getEntrySlotMappingExpression(entry);
        }

        @Override
        public void setSlotExpression(int entry, String expression) {
            ClientPacketDistributor.sendToServer(new SetFilterEntrySlotMappingPayload(entry, expression));
            menu.setEntrySlotMapping(minecraft.player, entry, expression);
        }

        @Override
        public boolean nbtStrict(int entry) {
            return menu.isEntryNbtStrict(entry);
        }

        @Override
        public void setNbtStrict(int entry, boolean strict) {
            ClientPacketDistributor.sendToServer(SetFilterEntryNbtPayload.setStrict(entry, strict));
            menu.setEntryNbtStrict(entry, strict);
        }

        @Override
        public List<NbtCriterion> nbtRules(int entry) {
            return menu.getSlotNbtRules(entry);
        }

        @Override
        public String nbtRaw(int entry) {
            return FilterItemData.getEntryNbtRaw(menu.getOpenedStack(), entry);
        }

        @Override
        public void addNbtRule(int entry, NbtPath path, String operator, String fallbackValue) {
            ClientPacketDistributor.sendToServer(SetFilterEntryNbtPayload.add(entry, path, operator, fallbackValue));
            menu.addSlotNbtRule(minecraft.player, entry, path, operator, fallbackValue);
        }

        @Override
        public void removeNbtRule(int entry, int ruleIndex) {
            ClientPacketDistributor.sendToServer(SetFilterEntryNbtPayload.remove(entry, ruleIndex));
            menu.removeSlotNbtRule(entry, ruleIndex);
        }

        @Override
        public void setNbtRuleValue(int entry, int ruleIndex, String value) {
            ClientPacketDistributor.sendToServer(SetFilterEntryNbtPayload.setValue(entry, ruleIndex, value));
            menu.setSlotNbtRuleValue(entry, ruleIndex, value);
        }

        @Override
        public void setNbtRaw(int entry, String raw) {
            menu.setEntryNbtRaw(minecraft.player, entry, NbtPath.EMPTY, raw);
            ClientPacketDistributor.sendToServer(SetFilterEntryNbtPayload.setRaw(entry, raw));
        }

        @Override
        public void clearNbt(int entry) {
            ClientPacketDistributor.sendToServer(SetFilterEntryNbtPayload.clear(entry));
            menu.clearSlotNbtRules(entry);
        }
    }
}
