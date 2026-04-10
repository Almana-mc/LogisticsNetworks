package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.network.NetworkHandler;

import me.almana.logisticsnetworks.util.ItemStackCompat;

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
import net.minecraft.client.gui.components.MultiLineEditBox;
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
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

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

    // Detail page state
    private int detailEditSlot = -1;
    private List<String> detailCachedTags = new ArrayList<>();
    private List<String> detailAllTags = new ArrayList<>();
    private List<String> detailTagFilteredList = new ArrayList<>();
    private List<String> detailAllItemIds = new ArrayList<>();
    private List<String> detailItemFilteredList = new ArrayList<>();
    private List<NbtFilterData.NbtEntry> detailCachedNbtEntries = new ArrayList<>();
    private int detailTagScrollOffset = 0;
    private boolean detailNbtRawMode = false;
    private boolean detailNbtPageOpen = false;
    private int detailNbtScrollOffset = 0;
    private int detailNbtSelectedIdx = -1;
    private String detailNbtOp = "=";
    private Map<String, String> detailNbtActiveOps = new HashMap<>();
    private int nbtTableEditingRow = -1;
    private boolean detailItemEnchanted = false;
    private boolean detailEnchantedEnabled = false;
    private List<ItemStack> nbtOnlyCycleItems;
    private int detailItemDurability = -1;
    private int detailItemMaxDurability = -1;
    private int detailItemStackSize = 1;
    private EditBox detailNbtValueBox;
    private EditBox detailIdInputBox;
    private EditBox detailBatchInputBox;
    private EditBox detailStockInputBox;
    private EditBox detailSlotMappingInputBox;
    private EditBox detailDurabilityValueBox;
    private MultiLineEditBox detailNbtInputBox;
    private String detailDurabilityOp = null;
    private int savedImageHeight = -1;
    private int savedTopPos = -1;
    private int savedImageWidth = -1;
    private int savedLeftPos = -1;
    private String lastDetailIdFilter = "";
    private static final int DETAIL_MIN_HEIGHT = 230;
    private static final int DETAIL_NBT_MIN_WIDTH = 380;
    private int nbtSavedImageWidth = -1;
    private int nbtSavedLeftPos = -1;
    private static final int NBT_COL_TOGGLE = 14;
    private static final int NBT_COL_TOGGLE_GAP = 4;
    private static final int NBT_COL_OP = 20;
    private static final int NBT_COL_VAL = 140;
    private static final int NBT_COL_OP_GAP = 24;
    private static final int NBT_ROW_H = 14;
    private static final int NBT_BUILTIN_ROWS = 3;
    private static final int NBT_EDIT_DURABILITY = -10;
    private static final int NBT_EDIT_ENCHANTED = -11;
    private static final int NBT_EDIT_STACK_SIZE = -12;
    private static final int NBT_MIN_GROUP_PREFIX = 10;
    private static final int NBT_HEADING_COLOR = 0xFF88AACC;

    private record NbtRow(boolean heading, String display, int entryIdx, String group) {}
    private List<NbtRow> nbtRows = new ArrayList<>();
    private Set<String> nbtCollapsedGroups = new HashSet<>();

    private static final int NBT_INDICATOR_W = 8;
    private static final int NBT_OP_BTN_W = 18;

    private static final Map<String, String> PATH_ABBREV = Map.of(
            "enchantments", "ench",
            "stored_enchantments", "stored",
            "potion_contents", "potion",
            "custom_data", "data",
            "attribute_modifiers", "attr"
    );

    // Legacy sub-mode compat
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

        detailIdInputBox = new EditBox(font, leftPos + 12, topPos + 50, 100, 14, Component.empty());
        detailIdInputBox.setMaxLength(256);
        detailIdInputBox.setVisible(false);
        detailIdInputBox.setBordered(true);
        detailIdInputBox.setTextColor(COL_WHITE);
        detailIdInputBox.setHint(Component.literal("Item or #tag"));

        detailBatchInputBox = new EditBox(font, leftPos + 12, topPos + 50, 50, 14, Component.empty());
        detailBatchInputBox.setMaxLength(10);
        detailBatchInputBox.setVisible(false);
        detailBatchInputBox.setBordered(true);
        detailBatchInputBox.setTextColor(COL_WHITE);

        detailStockInputBox = new EditBox(font, leftPos + 12, topPos + 66, 50, 14, Component.empty());
        detailStockInputBox.setMaxLength(10);
        detailStockInputBox.setVisible(false);
        detailStockInputBox.setBordered(true);
        detailStockInputBox.setTextColor(COL_WHITE);

        detailSlotMappingInputBox = new EditBox(font, leftPos + 12, topPos + 50, 100, 14, Component.empty());
        detailSlotMappingInputBox.setMaxLength(128);
        detailSlotMappingInputBox.setVisible(false);
        detailSlotMappingInputBox.setBordered(true);
        detailSlotMappingInputBox.setTextColor(COL_WHITE);

        detailDurabilityValueBox = new EditBox(font, leftPos + 12, topPos + 50, 40, 14, Component.empty());
        detailDurabilityValueBox.setMaxLength(5);
        detailDurabilityValueBox.setVisible(false);
        detailDurabilityValueBox.setBordered(true);
        detailDurabilityValueBox.setTextColor(COL_WHITE);

        detailNbtValueBox = new EditBox(font, leftPos + 12, topPos + 50, 80, 14, Component.empty());
        detailNbtValueBox.setMaxLength(256);
        detailNbtValueBox.setVisible(false);
        detailNbtValueBox.setBordered(true);
        detailNbtValueBox.setTextColor(COL_WHITE);

        detailNbtInputBox = new ThemedMultiLineEditBox(
                font, leftPos + 12, topPos + 50, 100, 40, Component.empty(), Component.empty());
        detailNbtInputBox.setCharacterLimit(2048);
        detailNbtInputBox.active = false;
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

        if (detailEditSlot >= 0 && detailIdInputBox != null) {
            String currentFilter = detailIdInputBox.getValue().trim().toLowerCase();
            if (!currentFilter.equals(lastDetailIdFilter)) {
                rebuildIdFilteredList();
                detailTagScrollOffset = 0;
            }
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
            if (!ItemStack.isSameItemSameTags(extractor, lastExtractorItem)
                    || currentTarget != lastTargetType) {
                lastExtractorItem = extractor.copy();
                lastTargetType = currentTarget;
                cachedTags.clear();
                if (!extractor.isEmpty()) {
                    if (isFluid) {
                        FluidStack fs = FluidUtil.getFluidContained(extractor).orElse(FluidStack.EMPTY);
                        if (!fs.isEmpty()) {
                            fs.getFluid().builtInRegistryHolder().tags()
                                    .forEach(t -> cachedTags.add(t.location().toString()));
                        }
                    } else if (isChemical && MekanismCompat.isLoaded()) {
                        List<String> chemTags = MekanismCompat.getChemicalTagsFromItem(extractor);
                        if (chemTags != null) {
                            cachedTags.addAll(chemTags);
                        }
                    } else {
                        extractor.getTags().forEach(t -> cachedTags.add(t.location().toString()));
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
        if (detailEditSlot >= 0) {
            this.renderBackground(g);
            this.renderBg(g, pt, mx, my);
            renderDetailPage(g, mx, my);
            return;
        } else if (tagEditSlot >= 0 || nbtEditSlot >= 0) {
            super.render(g, -1, -1, pt);
        } else {
            super.render(g, mx, my, pt);
        }

        renderEntryIndicatorOverlays(g);

        boolean hoverSpecialFilter = (menu.isTagMode() || menu.isModMode())
                && this.hoveredSlot != null && this.hoveredSlot.index < menu.getFilterSlots();

        if (menu.isTagMode())
            renderTagTooltip(g, mx, my);
        else if (menu.isModMode())
            renderModTooltip(g, mx, my);
        else if (menu.getTargetType() == FilterTargetType.FLUIDS || menu.getTargetType() == FilterTargetType.CHEMICALS) {
            renderFluidTooltip(g, mx, my);
        }

        if (!menu.isTagMode() && !menu.isModMode()
                && tagEditSlot < 0 && nbtEditSlot < 0 && detailEditSlot < 0
                && this.hoveredSlot != null && this.hoveredSlot.index < menu.getFilterSlots()) {
            int idx = this.hoveredSlot.index;
            ItemStack openedStack = menu.getOpenedStack();
            List<Component> lines = buildFilterEntryTooltip(idx, openedStack);
            if (!lines.isEmpty()) {
                g.renderComponentTooltip(font, lines, mx, my);
                hoverSpecialFilter = true;
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

        if (!menu.isTagMode() && !menu.isModMode() && !menu.isSlotMode() && !menu.isAmountMode()
                && !menu.isDurabilityMode() && !menu.isNameMode()
                && detailEditSlot < 0 && tagEditSlot < 0 && nbtEditSlot < 0) {
            Component hint = Component.translatable("gui.logisticsnetworks.filter.hint.ctrl_click");
            float scale = 0.75f;
            int hintW = (int)(font.width(hint) * scale);
            g.pose().pushPose();
            g.pose().translate(leftPos + (imageWidth - hintW) / 2f, sepY - 10, 0);
            g.pose().scale(scale, scale, 1f);
            g.drawString(font, hint, 0, 0, 0xFF666666, false);
            g.pose().popPose();
        }

        g.fill(leftPos + 8, sepY, leftPos + imageWidth - 8, sepY + 1, COL_BORDER);
        g.drawString(font, playerInventoryTitle, leftPos + 8, topPos + playerInvY - 10, COL_GRAY, false);

        renderPlayerSlots(g);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private void renderStandardFilterGrid(GuiGraphics g, int mx, int my) {
        if (detailEditSlot >= 0) {
            return;
        }

        for (int i = 0; i < menu.getFilterSlots() && i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            int sx = leftPos + slot.x - 1;
            int sy = topPos + slot.y - 1;

            if (menu.isTagSlot(i)) {
                drawSlot(g, sx, sy);
                g.renderOutline(sx, sy, 18, 18, 0xFF44BB44);

                String tag = menu.getEntryTag(i);
                if (tag != null) {
                    ResourceLocation tagId = ResourceLocation.tryParse(tag);
                    if (tagId != null) {
                        FilterTargetType targetType = menu.getTargetType();
                        if (targetType == FilterTargetType.FLUIDS) {
                            TagKey<Fluid> fluidTagKey = TagKey.create(Registries.FLUID, tagId);
                            var list = new java.util.ArrayList<Fluid>();
                            for (Fluid fluid : BuiltInRegistries.FLUID) {
                                if (fluid.builtInRegistryHolder().is(fluidTagKey)) {
                                    list.add(fluid);
                                }
                            }
                            if (!list.isEmpty()) {
                                long tick = (System.currentTimeMillis() / 1000);
                                int idx = (int) (tick % list.size());
                                renderFluidStack(g, new FluidStack(list.get(idx), 1000), sx + 1, sy + 1);
                            }
                        } else if (targetType == FilterTargetType.CHEMICALS) {
                            g.drawString(font, "#", sx + 5, sy + 5, 0xFF44BB44, true);
                        } else {
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
                }
            } else if (FilterItemData.isNbtOnlySlot(menu.getOpenedStack(), i)
                    || isAnyItemSlot(i)) {
                drawSlot(g, sx, sy);
                g.renderOutline(sx, sy, 18, 18, 0xFF44BB44);
                if (nbtOnlyCycleItems == null) {
                    nbtOnlyCycleItems = BuiltInRegistries.ITEM.stream()
                            .map(ItemStack::new)
                            .filter(s -> !s.isEmpty())
                            .toList();
                }
                if (!nbtOnlyCycleItems.isEmpty()) {
                    long tick = (System.currentTimeMillis() / 1000);
                    int idx = (int) (tick % nbtOnlyCycleItems.size());
                    g.renderItem(nbtOnlyCycleItems.get(idx), sx + 1, sy + 1);
                }
            } else {
                drawSlot(g, sx, sy);
            }

        }

        if (menu.getTargetType() == FilterTargetType.FLUIDS) {
            renderFluidGhostItems(g);
        } else if (menu.getTargetType() == FilterTargetType.CHEMICALS) {
            renderChemicalGhostItems(g);
        }

        renderModeControls(g, mx, my, true);

        if (tagEditSlot >= 0) {
            renderTagSubMode(g, mx, my);
        } else if (nbtEditSlot >= 0) {
            renderNbtSubMode(g, mx, my);
        }
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
                g.drawString(font, "N", bx, by, 0xFFFFAA00, true);
                g.pose().popPose();
            }

            if (FilterItemData.hasEntryDurability(openedStack, i)) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.pose().scale(0.5f, 0.5f, 1.0f);
                int bx = (int) ((sx + 12) / 0.5f);
                int by = (int) ((sy + 1) / 0.5f);
                g.drawString(font, "D", bx, by, 0xFF55BBFF, true);
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

        int maxScroll = Math.max(0, items.size() - DROPDOWN_ROWS);
        listScrollOffset = Math.max(0, Math.min(listScrollOffset, maxScroll));

        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
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
            if (detailEditSlot >= 0) {
                handled = handleDetailPageClick(mx, my, btn);
                if (!handled) {
                    closeDetailPage();
                    return true;
                }
                return true;
            }

            if (tagEditSlot >= 0) {
                handled = handleTagSubModeClick(mx, my, btn);
                if (!handled) {
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

            if (hasControlDown()) {
                int hoveredSlot = getHoveredFilterSlot((int) mx, (int) my);
                if (hoveredSlot >= 0) {
                    if (btn == 0) {
                        enterDetailPage(hoveredSlot);
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
        menu.setSelectedTag(tag == null || tag.isBlank() ? null : tag.trim());
        NetworkHandler.sendToServer(new ModifyFilterTagPayload(tag == null ? "" : tag, false));
    }

    private void sendTagRemove(String tag) {
        menu.setSelectedTag(null);
        NetworkHandler.sendToServer(new ModifyFilterTagPayload(tag == null ? "" : tag, true));
    }

    private void sendModUpdate(String mod) {
        menu.setSelectedMod(mod == null || mod.isBlank() ? null : mod.trim());
        NetworkHandler.sendToServer(new ModifyFilterModPayload(mod == null ? "" : mod, false));
    }

    private void sendModRemove(String mod) {
        menu.setSelectedMod(null);
        NetworkHandler.sendToServer(new ModifyFilterModPayload(mod == null ? "" : mod, true));
    }

    private void sendAmountUpdate(int amount) {
        NetworkHandler.sendToServer(new SetAmountFilterValuePayload(amount));
    }

    private void sendDurabilityUpdate(int val) {
        NetworkHandler.sendToServer(new SetDurabilityFilterValuePayload(val));
    }

    private void sendSlotUpdate(String expression) {
        NetworkHandler.sendToServer(new SetSlotFilterSlotsPayload(expression == null ? "" : expression));
    }

    private void sendNameUpdate(String name) {
        NetworkHandler.sendToServer(new SetNameFilterPayload(name == null ? "" : name));
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
        if (minecraft.options.keyInventory.matches(key, scan)) {
            return true;
        }
        if (key == 256) {
            if (detailEditSlot >= 0 && detailNbtPageOpen) {
                closeNbtSubPage();
                return true;
            }
            if (detailEditSlot >= 0) {
                closeDetailPage();
                return true;
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
            return super.keyPressed(key, scan, modifiers);
        }

        if (detailEditSlot >= 0) {
            return handleDetailPageKey(key, scan, modifiers);
        }

        if (nbtValueEditBox != null && nbtValueEditBox.isFocused()) {
            if (key == 257) {
                commitNbtValueEdit();
                return true;
            }
            nbtValueEditBox.keyPressed(key, scan, modifiers);
            return true;
        }

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
            manualInputBox.keyPressed(key, scan, modifiers);
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (detailEditSlot >= 0) {
            if (detailIdInputBox != null && detailIdInputBox.isFocused())
                return detailIdInputBox.charTyped(c, modifiers);
            if (detailBatchInputBox != null && detailBatchInputBox.isFocused())
                return detailBatchInputBox.charTyped(c, modifiers);
            if (detailStockInputBox != null && detailStockInputBox.isFocused())
                return detailStockInputBox.charTyped(c, modifiers);
            if (detailSlotMappingInputBox != null && detailSlotMappingInputBox.isFocused())
                return detailSlotMappingInputBox.charTyped(c, modifiers);
            if (detailNbtInputBox != null && detailNbtInputBox.isFocused())
                return detailNbtInputBox.charTyped(c, modifiers);
            if (detailNbtValueBox != null && detailNbtValueBox.isFocused())
                return detailNbtValueBox.charTyped(c, modifiers);
            return true;
        }
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
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (detailEditSlot >= 0) {
            return handleDetailPageScroll(mx, my, delta);
        }

        if (nbtEditSlot >= 0) {
            int maxScroll = getNbtSubModeMaxScroll();
            if (delta > 0 && nbtListScrollOffset > 0)
                nbtListScrollOffset--;
            else if (delta < 0 && nbtListScrollOffset < maxScroll)
                nbtListScrollOffset++;
            return true;
        }

        if (subModeDropdownOpen) {
            if (delta > 0 && subModeScrollOffset > 0)
                subModeScrollOffset--;
            else if (delta < 0)
                subModeScrollOffset++;
            return true;
        }

        if (isDropdownOpen) {
            if (delta > 0 && listScrollOffset > 0)
                listScrollOffset--;
            else if (delta < 0)
                listScrollOffset++;
            return true;
        }

        return super.mouseScrolled(mx, my, delta);
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
        if (slot < menu.slots.size() && !menu.slots.get(slot).getItem().isEmpty())
            return true;
        ItemStack openedStack = menu.getOpenedStack();
        return FilterItemData.hasEntryNbt(openedStack, slot)
                || FilterItemData.hasEntryDurability(openedStack, slot)
                || FilterItemData.hasEntryEnchanted(openedStack, slot);
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

    private List<Component> buildFilterEntryTooltip(int slot, ItemStack filterStack) {
        List<Component> lines = new ArrayList<>();

        String tag = menu.getEntryTag(slot);
        ItemStack slotItem = slot < menu.slots.size() ? menu.slots.get(slot).getItem() : ItemStack.EMPTY;
        boolean isNbtOnly = FilterItemData.isNbtOnlySlot(filterStack, slot);

        int batch = menu.getEntryBatch(slot);
        int stock = menu.getEntryStock(slot);

        if (tag != null) {
            lines.add(Component.literal("#" + tag).withStyle(ChatFormatting.GOLD));
        } else if (!slotItem.isEmpty()) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(slotItem.getItem());
            lines.add(Component.literal(itemId.toString()).withStyle(ChatFormatting.WHITE));
        } else if (isNbtOnly || batch > 0 || stock > 0) {
            lines.add(Component.literal("Any Item").withStyle(ChatFormatting.AQUA));
        } else {
            return lines;
        }
        if (batch > 0 || stock > 0) {
            lines.add(Component.literal("Batch | Stock: " + batch + " | " + stock).withStyle(ChatFormatting.GRAY));
        }

        List<FilterItemData.SlotNbtRule> nbtRules = FilterItemData.getSlotNbtRules(filterStack, slot);
        if (!nbtRules.isEmpty()) {
            for (FilterItemData.SlotNbtRule r : nbtRules) {
                lines.add(Component.literal("NBT: " + r.displayText()).withStyle(ChatFormatting.GOLD));
            }
        } else {
            String nbtRaw = FilterItemData.getEntryNbtRaw(filterStack, slot);
            if (nbtRaw != null) {
                String preview = nbtRaw.length() > 50 ? nbtRaw.substring(0, 50) + "..." : nbtRaw;
                lines.add(Component.literal("NBT: " + preview).withStyle(ChatFormatting.GOLD));
            }
        }

        Boolean enchanted = FilterItemData.getEntryEnchanted(filterStack, slot);
        if (enchanted != null) {
            String enchStr = enchanted ? "Enchanted: Yes" : "Enchanted: No";
            lines.add(Component.literal(enchStr).withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        String durOp = FilterItemData.getEntryDurabilityOp(filterStack, slot);
        if (durOp != null) {
            int durVal = FilterItemData.getEntryDurabilityValue(filterStack, slot);
            lines.add(Component.literal("Durability: " + durOp + " " + durVal).withStyle(ChatFormatting.BLUE));
        }

        String slotExpr = menu.getEntrySlotMappingExpression(slot);
        if (slotExpr != null && !slotExpr.isEmpty()) {
            lines.add(Component.literal("Slots: " + slotExpr).withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        return lines;
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
                    g.renderTooltip(font, fs.getDisplayName(), mx, my);
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
        NetworkHandler.sendToServer(
                new SetFilterFluidEntryPayload(slot, BuiltInRegistries.FLUID.getKey(fluidStack.getFluid()).toString()));
        menu.setFluidFilterEntry(player, slot, fluidStack);
    }

    public void setChemicalFilterEntry(Player player, int slot, String chemicalId) {
        if (chemicalId == null || chemicalId.isBlank())
            return;
        NetworkHandler.sendToServer(
                new SetFilterChemicalEntryPayload(slot, chemicalId));
        menu.setChemicalFilterEntry(player, slot, chemicalId);
    }

    public void setItemFilterEntry(Player player, int slot, ItemStack stack) {
        if (stack.isEmpty())
            return;
        NetworkHandler.sendToServer(new SetFilterItemEntryPayload(slot, stack));
        menu.setItemFilterEntry(player, slot, stack);
    }

    public boolean acceptsFluidSelectorGhostIngredient() {
        return menu.isTagMode() || menu.isModMode();
    }

    public boolean acceptsItemSelectorGhostIngredient() {
        return menu.isTagMode() || menu.isModMode();
    }

    public boolean supportsGhostIngredientTargets() {
        if (detailEditSlot >= 0) return false;
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

    public List<Rect2i> getExtraGuiAreas() {
        List<Rect2i> areas = new ArrayList<>();
        if (detailEditSlot >= 0) {
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
        return detailEditSlot >= 0 && !detailNbtPageOpen;
    }

    public Rect2i getDetailSlotArea() {
        int panelX = leftPos + 4;
        int panelY = topPos + 20;
        int contentX = panelX + 4;
        int slotY = panelY + 20;
        return new Rect2i(contentX, slotY, 18, 18);
    }

    public void setDetailGhostItem(ItemStack stack) {
        if (detailEditSlot < 0 || stack.isEmpty()) return;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        menu.clearEntryTag(detailEditSlot);
        NetworkHandler.sendToServer(new SetFilterItemEntryPayload(detailEditSlot, stack));
        menu.setItemFilterEntry(minecraft.player, detailEditSlot, stack);
        detailIdInputBox.setValue(itemId.toString());
    }

    public void setSelectorGhostFluid(FluidStack stack) {
        if (menu.getExtractorSlotIndex() >= 0) {
            menu.slots.get(menu.getExtractorSlotIndex()).set(new ItemStack(stack.getFluid().getBucket()));
        }
    }

    public void setSelectorGhostItem(ItemStack stack) {
        if (menu.getExtractorSlotIndex() >= 0) {
            menu.slots.get(menu.getExtractorSlotIndex()).set(ItemStackCompat.copyWithCount(stack, 1));
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
            slotItem.getTags().forEach(t -> cachedSlotTags.add(t.location().toString()));
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
            NetworkHandler.sendToServer(new SetFilterEntryTagPayload(tagEditSlot, ""));
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
                NetworkHandler.sendToServer(new SetFilterEntryTagPayload(tagEditSlot, tag));
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
            NetworkHandler.sendToServer(new SetFilterEntryTagPayload(tagEditSlot, normalizedTag));
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
        return (width - getNbtPanelW()) / 2;
    }

    private int getNbtPanelW() {
        return imageWidth + 50;
    }

    private int getNbtColW(int rowW) {
        return (rowW - NBT_INDICATOR_W - NBT_OP_BTN_W - 4) / 2;
    }

    private boolean isBooleanValue(String displayVal) {
        return "true".equals(displayVal) || "false".equals(displayVal);
    }

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
            return stripNamespace(inner);
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
            NetworkHandler.sendToServer(SetFilterEntryNbtPayload.setValue(nbtEditSlot, nbtEditingRuleIndex, val));
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

        int clearW = 30;
        int clearX = panelX + panelW - clearW - 4;
        int clearY = panelY + 4;
        drawButton(g, clearX, clearY, clearW, 10, tr("gui.logisticsnetworks.filter.nbt.clear"), mx, my, true);

        List<FilterItemData.SlotNbtRule> activeRules = menu.getSlotNbtRules(nbtEditSlot);
        int listX = panelX + 4;
        int listY = panelY + 16;
        int listW = panelW - 8;
        int listH = panelH - 34;
        renderNbtEntryList(g, listX, listY, listW, listH, activeRules, mx, my);

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

            int dotX = listX + 2;
            int dotY = rowY + (LIST_ROW_H - 5) / 2;
            if (active) {
                g.fill(dotX, dotY, dotX + 5, dotY + 5, COL_ACCENT);
            } else {
                g.renderOutline(dotX, dotY, 5, 5, COL_GRAY);
            }

            FilterItemData.SlotNbtRule activeRule = active ? activeRules.get(ruleIdx) : null;
            String op = active ? activeRule.operator() : "=";

            int pathX = listX + NBT_INDICATOR_W;
            int opX = pathX + colW + 1;
            int valX = opX + NBT_OP_BTN_W + 1;

            String displayPath = formatNbtPath(entry.path());
            g.fill(pathX, rowY, pathX + colW, rowY + LIST_ROW_H, 0xFF080808);
            g.renderOutline(pathX, rowY, colW, LIST_ROW_H, active ? COL_BTN_BORDER : 0xFF222222);
            g.drawString(font, font.plainSubstrByWidth(displayPath, colW - 4),
                    pathX + 2, rowY + 1, active ? COL_ACCENT : COL_WHITE, false);

            boolean opHover = active && isHovering(opX, rowY, NBT_OP_BTN_W, LIST_ROW_H, mx, my);
            g.fill(opX, rowY, opX + NBT_OP_BTN_W, rowY + LIST_ROW_H,
                    opHover ? COL_BTN_HOVER : COL_BTN_BG);
            g.renderOutline(opX, rowY, NBT_OP_BTN_W, LIST_ROW_H, COL_BTN_BORDER);
            g.drawCenteredString(font, op, opX + NBT_OP_BTN_W / 2, rowY + 1,
                    active ? (opHover ? COL_WHITE : 0xFFFFAA00) : COL_GRAY);

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

        int clearW = 30;
        int clearX = panelX + panelW - clearW - 4;
        int clearY = panelY + 4;
        if (isHovering(clearX, clearY, clearW, 10, (int) mx, (int) my)) {
            cancelNbtValueEdit();
            NetworkHandler.sendToServer(SetFilterEntryNbtPayload.clear(nbtEditSlot));
            menu.clearSlotNbtRules(nbtEditSlot);
            return true;
        }

        List<FilterItemData.SlotNbtRule> activeRules = menu.getSlotNbtRules(nbtEditSlot);
        int listX = panelX + 4;
        int listY = panelY + 16;
        int listW = panelW - 8;
        int listH = panelH - 34;
        if (handleNbtEntryListClick(listX, listY, listW, listH, activeRules, mx, my, btn))
            return true;

        boolean matchAny = menu.isSlotNbtMatchAny(nbtEditSlot);
        String matchLabel = matchAny
                ? tr("gui.logisticsnetworks.filter.nbt.match_any")
                : tr("gui.logisticsnetworks.filter.nbt.match_all");
        int matchW = font.width(matchLabel) + 12;
        int matchX = panelX + 4;
        int matchY = panelY + panelH - 16;
        if (isHovering(matchX, matchY, matchW, 14, (int) mx, (int) my)) {
            commitNbtValueEditIfActive();
            NetworkHandler.sendToServer(SetFilterEntryNbtPayload.toggleMatch(nbtEditSlot));
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

            if (active && isHovering(opX, rowY, NBT_OP_BTN_W, LIST_ROW_H, (int) mx, (int) my)) {
                commitNbtValueEditIfActive();
                String savedVal = formatNbtValue(activeRules.get(ruleIdx).value().toString());
                String currentOp = activeRules.get(ruleIdx).operator();
                String newOp = FilterItemData.nextNbtOperator(currentOp);
                NetworkHandler.sendToServer(SetFilterEntryNbtPayload.remove(nbtEditSlot, ruleIdx));
                menu.removeSlotNbtRule(nbtEditSlot, ruleIdx);
                NetworkHandler.sendToServer(SetFilterEntryNbtPayload.add(nbtEditSlot, path, newOp, ""));
                if (minecraft != null && minecraft.player != null) {
                    menu.addSlotNbtRule(minecraft.player, nbtEditSlot, path, newOp);
                }
                List<FilterItemData.SlotNbtRule> updatedRules = menu.getSlotNbtRules(nbtEditSlot);
                int newIdx = findActiveRuleIndex(updatedRules, path);
                if (newIdx >= 0) {
                    NetworkHandler.sendToServer(
                            SetFilterEntryNbtPayload.setValue(nbtEditSlot, newIdx, savedVal));
                    menu.setSlotNbtRuleValue(nbtEditSlot, newIdx, savedVal);
                }
                return true;
            }

            if (active && btn == 0 && mx >= valX) {
                String displayVal = formatNbtValue(activeRules.get(ruleIdx).value().toString());

                if (isBooleanValue(displayVal)) {
                    commitNbtValueEditIfActive();
                    String toggled = "true".equals(displayVal) ? "false" : "true";
                    NetworkHandler.sendToServer(
                            SetFilterEntryNbtPayload.setValue(nbtEditSlot, ruleIdx, toggled));
                    menu.setSlotNbtRuleValue(nbtEditSlot, ruleIdx, toggled);
                    return true;
                }

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

            if (btn == 0 && mx < opX) {
                commitNbtValueEditIfActive();
                if (active) {
                    NetworkHandler.sendToServer(SetFilterEntryNbtPayload.remove(nbtEditSlot, ruleIdx));
                    menu.removeSlotNbtRule(nbtEditSlot, ruleIdx);
                } else {
                    NetworkHandler.sendToServer(SetFilterEntryNbtPayload.add(nbtEditSlot, path, "=", ""));
                    if (minecraft != null && minecraft.player != null) {
                        menu.addSlotNbtRule(minecraft.player, nbtEditSlot, path, "=");
                    }
                }
                return true;
            }

            if (active && btn == 1) {
                commitNbtValueEditIfActive();
                String savedVal = formatNbtValue(activeRules.get(ruleIdx).value().toString());
                String currentOp = activeRules.get(ruleIdx).operator();
                String newOp = FilterItemData.nextNbtOperator(currentOp);
                NetworkHandler.sendToServer(SetFilterEntryNbtPayload.remove(nbtEditSlot, ruleIdx));
                menu.removeSlotNbtRule(nbtEditSlot, ruleIdx);
                NetworkHandler.sendToServer(SetFilterEntryNbtPayload.add(nbtEditSlot, path, newOp, ""));
                if (minecraft != null && minecraft.player != null) {
                    menu.addSlotNbtRule(minecraft.player, nbtEditSlot, path, newOp);
                }
                List<FilterItemData.SlotNbtRule> updatedRules = menu.getSlotNbtRules(nbtEditSlot);
                int newIdx = findActiveRuleIndex(updatedRules, path);
                if (newIdx >= 0) {
                    NetworkHandler.sendToServer(
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

    private void closeNbtSubMode() {
        commitNbtValueEditIfActive();
        nbtEditSlot = -1;
        subModeDropdownOpen = false;
        cachedSlotNbtEntries.clear();
        nbtPendingOperator = "=";
        nbtListScrollOffset = 0;
    }

    // ── Detail Page ──

    private static final int DETAIL_SECTION_H = 22;
    private static final int DETAIL_TAG_COLOR = 0xFF44BB44;
    private static final int DETAIL_NBT_COLOR = 0xFFFFAA00;
    private static final int DETAIL_DUR_COLOR = 0xFF55BBFF;
    private static final int DETAIL_SLOT_COLOR = 0xFFBB88FF;

    private void enterDetailPage(int slot) {
        detailEditSlot = slot;
        tagEditSlot = -1;
        nbtEditSlot = -1;
        detailTagScrollOffset = 0;
        detailNbtRawMode = false;
        detailNbtPageOpen = false;
        detailNbtScrollOffset = 0;
        lastDetailIdFilter = "";

        savedImageHeight = imageHeight;
        savedTopPos = topPos;
        savedImageWidth = imageWidth;
        savedLeftPos = leftPos;
        if (imageHeight < DETAIL_MIN_HEIGHT) {
            imageHeight = DETAIL_MIN_HEIGHT;
            topPos = (height - imageHeight) / 2;
        }

        FilterTargetType targetType = menu.getTargetType();
        boolean isFluidOrChemical = targetType != FilterTargetType.ITEMS;

        detailCachedTags.clear();
        detailAllTags.clear();
        detailAllItemIds.clear();

        String existingTag = menu.getEntryTag(slot);

        if (targetType == FilterTargetType.FLUIDS) {
            FluidStack fluidEntry = FilterItemData.getFluidEntry(menu.getOpenedStack(), slot);
            if (!fluidEntry.isEmpty()) {
                fluidEntry.getFluid().builtInRegistryHolder().tags()
                        .forEach(t -> detailCachedTags.add(t.location().toString()));
            }
            Collections.sort(detailCachedTags);

            BuiltInRegistries.FLUID.getTagNames().forEach(tagKey -> {
                String tagStr = tagKey.location().toString();
                if (!detailCachedTags.contains(tagStr)) {
                    detailAllTags.add(tagStr);
                }
            });
            Collections.sort(detailAllTags);
            detailAllTags.addAll(0, detailCachedTags);

            BuiltInRegistries.FLUID.keySet().forEach(rl -> detailAllItemIds.add(rl.toString()));
            Collections.sort(detailAllItemIds);

            if (existingTag != null) {
                detailIdInputBox.setValue("#" + existingTag);
            } else if (!fluidEntry.isEmpty()) {
                ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluidEntry.getFluid());
                detailIdInputBox.setValue(fluidId.toString());
            } else {
                detailIdInputBox.setValue("");
            }
            detailIdInputBox.setHint(Component.literal("Fluid or #tag"));
        } else if (targetType == FilterTargetType.CHEMICALS) {
            List<String> allChemTags = MekanismCompat.getAllChemicalTags();
            detailAllTags.addAll(allChemTags);
            Collections.sort(detailAllTags);

            List<String> allChemIds = MekanismCompat.getAllChemicalIds();
            detailAllItemIds.addAll(allChemIds);
            Collections.sort(detailAllItemIds);

            String chemEntry = FilterItemData.getChemicalEntry(menu.getOpenedStack(), slot);
            if (existingTag != null) {
                detailIdInputBox.setValue("#" + existingTag);
            } else if (chemEntry != null) {
                detailIdInputBox.setValue(chemEntry);
            } else {
                detailIdInputBox.setValue("");
            }
            detailIdInputBox.setHint(Component.literal("Chemical or #tag"));
        } else {
            ItemStack slotItem = getSlotItemForSubMode(slot);
            if (!slotItem.isEmpty()) {
                slotItem.getTags().forEach(t -> detailCachedTags.add(t.location().toString()));
            }
            Collections.sort(detailCachedTags);

            BuiltInRegistries.ITEM.getTagNames().forEach(tagKey -> {
                String tagStr = tagKey.location().toString();
                if (!detailCachedTags.contains(tagStr)) {
                    detailAllTags.add(tagStr);
                }
            });
            Collections.sort(detailAllTags);
            detailAllTags.addAll(0, detailCachedTags);

            BuiltInRegistries.ITEM.keySet().forEach(rl -> detailAllItemIds.add(rl.toString()));
            Collections.sort(detailAllItemIds);

            if (existingTag != null) {
                detailIdInputBox.setValue("#" + existingTag);
            } else if (!slotItem.isEmpty()) {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(slotItem.getItem());
                detailIdInputBox.setValue(itemId.toString());
            } else {
                detailIdInputBox.setValue("");
            }
            detailIdInputBox.setHint(Component.literal("Item or #tag"));
        }

        if (existingTag != null && !detailAllTags.contains(existingTag)) {
            detailAllTags.add(0, existingTag);
        }

        detailIdInputBox.setVisible(true);
        detailIdInputBox.setFocused(false);
        rebuildIdFilteredList();

        int batch = menu.getEntryBatch(slot);
        int stock = menu.getEntryStock(slot);
        detailBatchInputBox.setValue(batch > 0 ? String.valueOf(batch) : "");
        detailBatchInputBox.setVisible(true);
        detailBatchInputBox.setFocused(false);
        detailStockInputBox.setValue(stock > 0 ? String.valueOf(stock) : "");
        detailStockInputBox.setVisible(true);
        detailStockInputBox.setFocused(false);

        if (isFluidOrChemical) {
            detailSlotMappingInputBox.setValue("");
            detailSlotMappingInputBox.setVisible(false);
            detailDurabilityOp = null;
            detailDurabilityValueBox.setVisible(false);
            detailNbtInputBox.setValue("");
            detailNbtInputBox.active = false;
            detailNbtInputBox.setFocused(false);
            detailCachedNbtEntries.clear();
            detailNbtActiveOps.clear();
            detailNbtOp = "=";
            detailNbtSelectedIdx = -1;
            detailNbtValueBox.setValue("");
            detailNbtValueBox.setVisible(false);
            nbtTableEditingRow = -1;
            detailItemEnchanted = false;
            detailEnchantedEnabled = false;
            detailItemDurability = -1;
            detailItemMaxDurability = 0;
            detailItemStackSize = 1;
        } else {
            String slotMapping = menu.getEntrySlotMappingExpression(slot);
            detailSlotMappingInputBox.setValue(slotMapping);
            detailSlotMappingInputBox.setVisible(true);
            detailSlotMappingInputBox.setFocused(false);

            String durOp = FilterItemData.getEntryDurabilityOp(menu.getOpenedStack(), slot);
            int durVal = FilterItemData.getEntryDurabilityValue(menu.getOpenedStack(), slot);
            detailDurabilityOp = durOp;
            detailDurabilityValueBox.setVisible(false);
            detailDurabilityValueBox.setFocused(false);

            ItemStack slotItem = getSlotItemForSubMode(slot);
            String existingNbtRaw = FilterItemData.getEntryNbtRaw(menu.getOpenedStack(), slot);
            if (existingNbtRaw != null) {
                detailNbtInputBox.setValue(existingNbtRaw);
            } else {
                if (!slotItem.isEmpty() && minecraft != null && minecraft.player != null) {
                    CompoundTag components = NbtFilterData.getSerializedComponents(
                            slotItem, minecraft.player.level().registryAccess());
                    detailNbtInputBox.setValue(components != null ? components.toString() : "");
                } else {
                    detailNbtInputBox.setValue("");
                }
            }
            detailNbtInputBox.active = false;
            detailNbtInputBox.setFocused(false);

            detailCachedNbtEntries.clear();
            if (!slotItem.isEmpty() && minecraft != null && minecraft.player != null) {
                detailCachedNbtEntries.addAll(NbtFilterData.extractEntries(
                        slotItem, minecraft.player.level().registryAccess()));
            } else {
                List<FilterItemData.SlotNbtRule> stored = menu.getSlotNbtRules(slot);
                for (FilterItemData.SlotNbtRule r : stored) {
                    String display = r.value() != null ? r.value().getAsString() : "?";
                    detailCachedNbtEntries.add(new NbtFilterData.NbtEntry(r.path(), display));
                }
            }
            nbtCollapsedGroups.clear();
            buildNbtRows();

            detailNbtActiveOps.clear();
            List<FilterItemData.SlotNbtRule> existingRules = menu.getSlotNbtRules(slot);
            for (FilterItemData.SlotNbtRule r : existingRules) {
                detailNbtActiveOps.put(r.path(), r.operator());
            }
            detailNbtOp = "=";
            detailNbtSelectedIdx = -1;
            detailNbtValueBox.setValue("");
            detailNbtValueBox.setVisible(false);
            detailNbtValueBox.setFocused(false);
            nbtTableEditingRow = -1;

            Boolean savedEnchanted = FilterItemData.getEntryEnchanted(menu.getOpenedStack(), slot);
            if (!slotItem.isEmpty()) {
                detailItemEnchanted = savedEnchanted != null ? savedEnchanted : slotItem.isEnchanted();
                detailEnchantedEnabled = savedEnchanted != null;
                detailItemMaxDurability = slotItem.getMaxDamage();
                detailItemDurability = detailItemMaxDurability > 0
                        ? detailItemMaxDurability - slotItem.getDamageValue() : -1;
                detailItemStackSize = slotItem.getCount();
            } else {
                detailItemEnchanted = savedEnchanted != null ? savedEnchanted : false;
                detailEnchantedEnabled = savedEnchanted != null;
                detailItemDurability = 0;
                detailItemMaxDurability = 0;
                detailItemStackSize = 1;
            }
        }
    }

    private void rebuildIdFilteredList() {
        String raw = detailIdInputBox != null ? detailIdInputBox.getValue().trim() : "";
        boolean isTagMode = raw.startsWith("#");
        detailTagFilteredList.clear();
        detailItemFilteredList.clear();

        if (isTagMode) {
            String filter = raw.substring(1).toLowerCase();
            if (filter.isEmpty()) {
                detailTagFilteredList.addAll(detailAllTags);
            } else {
                for (String tag : detailAllTags) {
                    if (tag.toLowerCase().contains(filter)) {
                        detailTagFilteredList.add(tag);
                    }
                }
            }
        } else if (!raw.isEmpty()) {
            String filter = raw.toLowerCase();
            int count = 0;
            for (String itemId : detailAllItemIds) {
                if (itemId.toLowerCase().contains(filter)) {
                    detailItemFilteredList.add(itemId);
                    if (++count >= 100) break;
                }
            }
        }
        lastDetailIdFilter = raw.toLowerCase();
    }

    private void closeDetailPage() {
        flushDetailPageInputs();
        detailEditSlot = -1;
        detailNbtPageOpen = false;
        nbtSavedImageWidth = -1;

        if (savedImageHeight > 0) {
            imageHeight = savedImageHeight;
            topPos = savedTopPos;
            savedImageHeight = -1;
        }
        if (savedImageWidth > 0) {
            imageWidth = savedImageWidth;
            leftPos = savedLeftPos;
            savedImageWidth = -1;
        }

        detailIdInputBox.setVisible(false);
        detailIdInputBox.setFocused(false);
        detailBatchInputBox.setVisible(false);
        detailBatchInputBox.setFocused(false);
        detailStockInputBox.setVisible(false);
        detailStockInputBox.setFocused(false);
        detailSlotMappingInputBox.setVisible(false);
        detailSlotMappingInputBox.setFocused(false);
        detailDurabilityValueBox.setVisible(false);
        detailDurabilityValueBox.setFocused(false);
        detailNbtInputBox.active = false;
        detailNbtInputBox.setFocused(false);
    }

    private void flushDetailPageInputs() {
        if (detailEditSlot < 0) return;
        int slot = detailEditSlot;
        FilterTargetType targetType = menu.getTargetType();
        boolean isFluidOrChemical = targetType != FilterTargetType.ITEMS;

        String idVal = detailIdInputBox.getValue().trim();
        if (idVal.startsWith("#")) {
            String normalizedTag = FilterTagUtil.normalizeTag(idVal);
            String current = menu.getEntryTag(slot);
            if (!Objects.equals(normalizedTag, current)) {
                if (normalizedTag == null) {
                    NetworkHandler.sendToServer(new SetFilterEntryTagPayload(slot, ""));
                    menu.clearEntryTag(slot);
                } else {
                    NetworkHandler.sendToServer(new SetFilterEntryTagPayload(slot, normalizedTag));
                    menu.setEntryTag(null, slot, normalizedTag);
                }
            }
        } else if (!idVal.isEmpty()) {
            if (targetType == FilterTargetType.FLUIDS) {
                ResourceLocation fluidId = ResourceLocation.tryParse(idVal);
                if (fluidId != null && BuiltInRegistries.FLUID.containsKey(fluidId)) {
                    if (menu.getEntryTag(slot) != null) {
                        menu.clearEntryTag(slot);
                    }
                    NetworkHandler.sendToServer(new SetFilterFluidEntryPayload(slot, fluidId.toString()));
                    menu.setFluidFilterEntry(minecraft.player, slot, new FluidStack(BuiltInRegistries.FLUID.get(fluidId), 1000));
                }
            } else if (targetType == FilterTargetType.CHEMICALS) {
                if (MekanismCompat.isValidChemicalId(idVal)) {
                    if (menu.getEntryTag(slot) != null) {
                        menu.clearEntryTag(slot);
                    }
                    NetworkHandler.sendToServer(new SetFilterChemicalEntryPayload(slot, idVal));
                    menu.setChemicalFilterEntry(minecraft.player, slot, idVal);
                }
            } else {
                ResourceLocation itemId = ResourceLocation.tryParse(idVal);
                if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
                    Item item = BuiltInRegistries.ITEM.get(itemId);
                    if (item != Items.AIR) {
                        ItemStack stack = new ItemStack(item);
                        if (menu.getEntryTag(slot) != null) {
                            menu.clearEntryTag(slot);
                        }
                        NetworkHandler.sendToServer(new SetFilterItemEntryPayload(slot, stack));
                        menu.setItemFilterEntry(minecraft.player, slot, stack);
                    }
                }
            }
        } else if (hasEntryInSlot(slot)) {
            if (isFluidOrChemical) {
                menu.clearFilterEntry(slot);
            } else {
                ItemStack opened = menu.getOpenedStack();
                boolean hasConfig = FilterItemData.hasEntryNbt(opened, slot)
                        || FilterItemData.hasEntryDurability(opened, slot)
                        || FilterItemData.hasEntryEnchanted(opened, slot);
                if (hasConfig) {
                    menu.clearFilterEntryItem(slot);
                    NetworkHandler.sendToServer(new SetFilterItemEntryPayload(slot, ItemStack.EMPTY));
                } else {
                    menu.clearFilterEntry(slot);
                }
            }
        }

        String batchStr = detailBatchInputBox.getValue().trim();
        int batchVal = 0;
        if (!batchStr.isEmpty()) {
            try { batchVal = Integer.parseInt(batchStr); } catch (NumberFormatException ignored) {}
        }
        String stockStr = detailStockInputBox.getValue().trim();
        int stockVal = 0;
        if (!stockStr.isEmpty()) {
            try { stockVal = Integer.parseInt(stockStr); } catch (NumberFormatException ignored) {}
        }
        batchVal = Math.max(0, batchVal);
        stockVal = Math.max(0, stockVal);
        if (batchVal != menu.getEntryBatch(slot) || stockVal != menu.getEntryStock(slot)) {
            menu.setEntryBatch(null, slot, batchVal);
            menu.setEntryStock(null, slot, stockVal);
            NetworkHandler.sendToServer(new SetFilterEntryAmountPayload(slot, batchVal, stockVal));
        }

        if (!isFluidOrChemical) {
            String slotMapStr = detailSlotMappingInputBox.getValue().trim();
            String currentSlotMap = menu.getEntrySlotMappingExpression(slot);
            if (!slotMapStr.equals(currentSlotMap)) {
                NetworkHandler.sendToServer(new SetFilterEntrySlotMappingPayload(slot, slotMapStr));
                menu.setEntrySlotMapping(null, slot, slotMapStr);
            }

            if (detailDurabilityOp != null && detailItemDurability >= 0) {
                menu.setEntryDurability(null, slot, detailDurabilityOp, detailItemDurability);
                NetworkHandler.sendToServer(new SetFilterEntryDurabilityPayload(
                        slot, detailDurabilityOp, detailItemDurability));
            } else {
                String existingOp = FilterItemData.getEntryDurabilityOp(menu.getOpenedStack(), slot);
                if (existingOp != null) {
                    menu.clearEntryDurability(null, slot);
                    NetworkHandler.sendToServer(new SetFilterEntryDurabilityPayload(slot, "", 0));
                }
            }

            if (detailNbtPageOpen) {
                flushNbtSubPage();
            }
        }
    }

    private void renderDetailPage(GuiGraphics g, int mx, int my) {
        if (detailNbtPageOpen) {
            renderNbtSubPage(g, mx, my);
            return;
        }

        int panelX = leftPos + 4;
        int panelY = topPos + 20;
        int panelW = imageWidth - 8;
        int panelH = imageHeight - 24;
        int contentX = panelX + 4;
        int contentW = panelW - 8;

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0101010);
        g.renderOutline(panelX, panelY, panelW, panelH, COL_ACCENT);

        int backW = 30;
        drawButton(g, panelX + 4, panelY + 4, backW, 12, "<", mx, my, true);
        g.drawString(font, tr("gui.logisticsnetworks.filter.detail.title", detailEditSlot),
                panelX + backW + 8, panelY + 6, COL_WHITE, false);

        int clearW = 40;
        int clearX = panelX + panelW - clearW - 4;
        drawButton(g, clearX, panelY + 4, clearW, 12, tr("gui.logisticsnetworks.filter.detail.clear"), mx, my, true);

        int y = panelY + 20;

        ItemStack openedStack = menu.getOpenedStack();
        int slotX = contentX;
        int slotY = y;
        drawSlot(g, slotX, slotY);

        FilterTargetType detailTargetType = menu.getTargetType();
        boolean isFluidOrChemical = detailTargetType != FilterTargetType.ITEMS;
        String idVal = detailIdInputBox.getValue().trim();
        if (idVal.startsWith("#")) {
            String tagStr = idVal.substring(1).trim();
            ResourceLocation tagId = ResourceLocation.tryParse(tagStr);
            if (tagId != null) {
                g.renderOutline(slotX, slotY, 18, 18, DETAIL_TAG_COLOR);
                if (detailTargetType == FilterTargetType.FLUIDS) {
                    TagKey<Fluid> fluidTagKey = TagKey.create(Registries.FLUID, tagId);
                    var list = new java.util.ArrayList<Fluid>();
                    for (Fluid fluid : BuiltInRegistries.FLUID) {
                        if (fluid.builtInRegistryHolder().is(fluidTagKey)) {
                            list.add(fluid);
                        }
                    }
                    if (!list.isEmpty()) {
                        int idx = (int) ((System.currentTimeMillis() / 1000) % list.size());
                        renderFluidStack(g, new FluidStack(list.get(idx), 1000), slotX + 1, slotY + 1);
                    }
                } else if (detailTargetType == FilterTargetType.CHEMICALS) {
                    g.drawString(font, "#", slotX + 5, slotY + 5, DETAIL_TAG_COLOR, true);
                } else {
                    TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
                    var holders = BuiltInRegistries.ITEM.getTag(tagKey);
                    if (holders.isPresent()) {
                        var list = holders.get().stream().toList();
                        if (!list.isEmpty()) {
                            int idx = (int) ((System.currentTimeMillis() / 1000) % list.size());
                            g.renderItem(new ItemStack(list.get(idx)), slotX + 1, slotY + 1);
                        }
                    }
                }
            } else {
                g.renderOutline(slotX, slotY, 18, 18, COL_ACCENT);
                int qx = slotX + (18 - font.width("?")) / 2;
                g.drawString(font, "?", qx, slotY + 5, COL_ACCENT, true);
            }
        } else if (!idVal.isEmpty()) {
            if (detailTargetType == FilterTargetType.FLUIDS) {
                ResourceLocation fluidId = ResourceLocation.tryParse(idVal);
                if (fluidId != null && BuiltInRegistries.FLUID.containsKey(fluidId)) {
                    renderFluidStack(g, new FluidStack(BuiltInRegistries.FLUID.get(fluidId), 1000), slotX + 1, slotY + 1);
                } else {
                    g.renderOutline(slotX, slotY, 18, 18, COL_ACCENT);
                    int qx = slotX + (18 - font.width("?")) / 2;
                    g.drawString(font, "?", qx, slotY + 5, COL_ACCENT, true);
                }
            } else if (detailTargetType == FilterTargetType.CHEMICALS) {
                if (MekanismCompat.isValidChemicalId(idVal)) {
                    renderChemicalStack(g, idVal, slotX + 1, slotY + 1);
                } else {
                    g.renderOutline(slotX, slotY, 18, 18, COL_ACCENT);
                    int qx = slotX + (18 - font.width("?")) / 2;
                    g.drawString(font, "?", qx, slotY + 5, COL_ACCENT, true);
                }
            } else {
                ResourceLocation itemId = ResourceLocation.tryParse(idVal);
                if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
                    Item item = BuiltInRegistries.ITEM.get(itemId);
                    if (item != Items.AIR) {
                        g.renderItem(new ItemStack(item), slotX + 1, slotY + 1);
                    } else {
                        g.renderOutline(slotX, slotY, 18, 18, COL_ACCENT);
                        int qx = slotX + (18 - font.width("?")) / 2;
                        g.drawString(font, "?", qx, slotY + 5, COL_ACCENT, true);
                    }
                } else if (detailEditSlot < menu.slots.size()) {
                    ItemStack itemInSlot = menu.slots.get(detailEditSlot).getItem();
                    if (!itemInSlot.isEmpty()) {
                        g.renderItem(itemInSlot, slotX + 1, slotY + 1);
                    } else {
                        g.renderOutline(slotX, slotY, 18, 18, COL_ACCENT);
                        int qx = slotX + (18 - font.width("?")) / 2;
                        g.drawString(font, "?", qx, slotY + 5, COL_ACCENT, true);
                    }
                }
            }
        } else if (!isFluidOrChemical && FilterItemData.isNbtOnlySlot(openedStack, detailEditSlot)) {
            boolean hasNbt = FilterItemData.hasEntryNbt(openedStack, detailEditSlot);
            int ic = hasNbt ? DETAIL_NBT_COLOR : DETAIL_DUR_COLOR;
            String il = hasNbt ? "N" : "D";
            g.renderOutline(slotX, slotY, 18, 18, ic);
            int nx = slotX + (18 - font.width(il)) / 2;
            g.drawString(font, il, nx, slotY + 5, ic, true);
        } else {
            g.renderOutline(slotX, slotY, 18, 18, COL_ACCENT);
            int qx = slotX + (18 - font.width("?")) / 2;
            g.drawString(font, "?", qx, slotY + 5, COL_ACCENT, true);
        }

        // ID field next to item slot
        int idFieldX = slotX + 22;
        int idFieldW = contentW - 22;
        detailIdInputBox.setX(idFieldX);
        detailIdInputBox.setY(slotY + 2);
        detailIdInputBox.setWidth(idFieldW);
        detailIdInputBox.render(g, mx, my, 0);

        y = slotY + 22;

        // Autocomplete dropdown
        boolean isTagMode = idVal.startsWith("#");
        List<String> dropdownList = isTagMode ? detailTagFilteredList : detailItemFilteredList;
        int labelW = 52;
        if (detailIdInputBox.isFocused() && !dropdownList.isEmpty()) {
            int dropRows = Math.min(DROPDOWN_ROWS, dropdownList.size());
            int dropH = dropRows * LIST_ROW_H;
            int dropX = idFieldX;
            int dropW = idFieldW;
            boolean scrollable = dropdownList.size() > DROPDOWN_ROWS;
            int rowW = scrollable ? dropW - SUBMODE_SCROLLBAR_W - SUBMODE_SCROLLBAR_GAP : dropW;

            g.fill(dropX, y, dropX + dropW, y + dropH, 0xF0101010);
            g.renderOutline(dropX, y, dropW, dropH, COL_BORDER);

            String currentTag = menu.getEntryTag(detailEditSlot);
            int maxScroll = Math.max(0, dropdownList.size() - DROPDOWN_ROWS);
            detailTagScrollOffset = Mth.clamp(detailTagScrollOffset, 0, maxScroll);
            int startIdx = detailTagScrollOffset;
            int endIdx = Math.min(startIdx + DROPDOWN_ROWS, dropdownList.size());

            for (int i = startIdx; i < endIdx; i++) {
                int rowY = y + (i - startIdx) * LIST_ROW_H;
                String entry = dropdownList.get(i);
                boolean selected = isTagMode && Objects.equals(entry, currentTag);
                boolean hovered = mx >= dropX && mx < dropX + rowW
                        && my >= rowY && my < rowY + LIST_ROW_H;

                if (selected)
                    g.fill(dropX + 1, rowY, dropX + rowW - 1, rowY + LIST_ROW_H, COL_SELECTED);
                else if (hovered)
                    g.fill(dropX + 1, rowY, dropX + rowW - 1, rowY + LIST_ROW_H, COL_HOVER);

                String displayText = isTagMode ? "#" + entry : entry;
                String text = font.plainSubstrByWidth(displayText, rowW - 6);
                g.drawString(font, text, dropX + 3, rowY + 2,
                        selected ? COL_ACCENT : COL_WHITE, false);
            }

            if (scrollable) {
                int scrollbarX = dropX + rowW + SUBMODE_SCROLLBAR_GAP;
                int thumbH = Math.max(8, (dropH * DROPDOWN_ROWS) / dropdownList.size());
                int thumbTravel = Math.max(0, dropH - thumbH);
                int thumbY = maxScroll <= 0 ? y : y + (detailTagScrollOffset * thumbTravel) / maxScroll;

                g.fill(scrollbarX, y, scrollbarX + SUBMODE_SCROLLBAR_W, y + dropH, COL_BTN_BG);
                g.fill(scrollbarX + 1, thumbY, scrollbarX + SUBMODE_SCROLLBAR_W - 1, thumbY + thumbH, COL_ACCENT);
            }

            y += dropH + 2;
        }

        // Batch section
        g.drawString(font, tr("gui.logisticsnetworks.filter.detail.batch"), contentX, y + 3, COL_WHITE, false);
        int batchInputX = contentX + labelW;
        detailBatchInputBox.setX(batchInputX);
        detailBatchInputBox.setY(y);
        detailBatchInputBox.setWidth(50);
        detailBatchInputBox.render(g, mx, my, 0);
        if (isFluidOrChemical) {
            g.drawString(font, "mB", batchInputX + 54, y + 3, COL_GRAY, false);
        }
        y += DETAIL_SECTION_H;

        // Stock section
        g.drawString(font, tr("gui.logisticsnetworks.filter.detail.stock"), contentX, y + 3, COL_WHITE, false);
        int stockInputX = contentX + labelW;
        detailStockInputBox.setX(stockInputX);
        detailStockInputBox.setY(y);
        detailStockInputBox.setWidth(50);
        detailStockInputBox.render(g, mx, my, 0);
        if (isFluidOrChemical) {
            g.drawString(font, "mB", stockInputX + 54, y + 3, COL_GRAY, false);
        }
        y += DETAIL_SECTION_H;

        if (!isFluidOrChemical) {
            // NBT button
            g.drawString(font, tr("gui.logisticsnetworks.filter.detail.nbt"), contentX, y + 3, DETAIL_NBT_COLOR, false);
            int nbtBtnX = contentX + labelW;
            String nbtBtnLabel = tr("gui.logisticsnetworks.filter.detail.nbt.configure");
            int nbtBtnW = Math.max(70, font.width(nbtBtnLabel) + 8);
            drawButton(g, nbtBtnX, y, nbtBtnW, 14, nbtBtnLabel, mx, my, true);

            if (isHovering(nbtBtnX, y, nbtBtnW, 14, mx, my)) {
                List<FilterItemData.SlotNbtRule> hoverRules = menu.getSlotNbtRules(detailEditSlot);
                if (!hoverRules.isEmpty()) {
                    List<Component> tipLines = new ArrayList<>();
                    for (FilterItemData.SlotNbtRule r : hoverRules) {
                        tipLines.add(Component.literal(abbreviateNbtPath(r.path()) + " " + r.operator() + " " + r.value()));
                    }
                    g.renderComponentTooltip(font, tipLines, mx, my);
                } else {
                    String nbtRaw = FilterItemData.getEntryNbtRaw(openedStack, detailEditSlot);
                    if (nbtRaw != null) {
                        String preview = nbtRaw.length() > 60 ? nbtRaw.substring(0, 60) + "..." : nbtRaw;
                        g.renderTooltip(font, Component.literal("SNBT: " + preview), mx, my);
                    }
                }
            }
            y += DETAIL_SECTION_H;

            // Slot mapping section
            g.drawString(font, tr("gui.logisticsnetworks.filter.detail.slots"), contentX, y + 3, DETAIL_SLOT_COLOR, false);
            int slotInputX = contentX + labelW;
            int slotInputW = contentW - labelW;
            detailSlotMappingInputBox.setX(slotInputX);
            detailSlotMappingInputBox.setY(y);
            detailSlotMappingInputBox.setWidth(slotInputW);
            detailSlotMappingInputBox.setHint(Component.empty());
            detailSlotMappingInputBox.render(g, mx, my, 0);
        }
    }

    private void renderNbtSubPage(GuiGraphics g, int mx, int my) {
        int panelX = leftPos + 4;
        int panelY = topPos + 20;
        int panelW = imageWidth - 8;
        int panelH = imageHeight - 24;
        int contentX = panelX + 4;
        int contentW = panelW - 8;

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0101010);
        g.renderOutline(panelX, panelY, panelW, panelH, DETAIL_NBT_COLOR);

        int backW = 50;
        drawButton(g, panelX + 4, panelY + 4, backW, 12, "< Back", mx, my, true);
        g.drawString(font, "NBT Filter", panelX + backW + 8, panelY + 6, COL_WHITE, false);

        int clearW = 40;
        int clearXPos = panelX + panelW - clearW - 4;
        drawButton(g, clearXPos, panelY + 4, clearW, 12, "Clear", mx, my, true);

        int y = panelY + 20;

        String rawLabel = detailNbtRawMode ? "Raw SNBT" : "Table";
        int modeW = Math.max(60, font.width(rawLabel) + 8);
        drawButton(g, contentX, y, modeW, 12, rawLabel, mx, my, true);
        y += 16;

        if (detailNbtRawMode) {
            int nbtH = panelY + panelH - y - 20;
            if (nbtH > 20) {
                if (detailNbtInputBox.getWidth() != contentW || detailNbtInputBox.getHeight() != nbtH) {
                    String oldVal = detailNbtInputBox.getValue();
                    detailNbtInputBox = new ThemedMultiLineEditBox(
                            font, contentX, y, contentW, nbtH,
                            Component.empty(), Component.empty());
                    detailNbtInputBox.setCharacterLimit(2048);
                    detailNbtInputBox.setValue(oldVal);
                    detailNbtInputBox.active = true;
                }
                detailNbtInputBox.setX(contentX);
                detailNbtInputBox.setY(y);
                detailNbtInputBox.active = true;
                detailNbtInputBox.render(g, mx, my, 0);
                y += nbtH + 4;
            }

            int doneW = 50;
            int doneX = panelX + (panelW - doneW) / 2;
            drawButton(g, doneX, y, doneW, 14, "Done", mx, my, true);

            int len = detailNbtInputBox.getValue().length();
            if (len >= 1800) {
                String counter = len + "/2048";
                int counterColor = len >= 2000 ? 0xFFFF5555 : COL_GRAY;
                g.drawString(font, counter, panelX + panelW - 4 - font.width(counter), y + 3, counterColor, false);
            }
        } else {
            renderNbtTable(g, mx, my, contentX, y, contentW, panelY + panelH - y - 4);
        }
    }

    private void buildNbtRows() {
        nbtRows.clear();
        int n = detailCachedNbtEntries.size();
        if (n == 0) return;

        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparing(idx -> detailCachedNbtEntries.get(idx).path()));

        int i = 0;
        while (i < n) {
            String pi = detailCachedNbtEntries.get(order[i]).path();
            String groupPrefix = "";

            if (i + 1 < n) {
                String pj = detailCachedNbtEntries.get(order[i + 1]).path();
                String lcp = nbtCommonPrefix(pi, pj);
                int sep = -1;
                for (int k = lcp.length() - 1; k >= 0; k--) {
                    char c = lcp.charAt(k);
                    if (c == '.' || c == ':' || c == '/') { sep = k; break; }
                }
                if (sep >= NBT_MIN_GROUP_PREFIX) {
                    groupPrefix = lcp.substring(0, sep);
                    int j = i + 1;
                    while (j < n) {
                        String pk = detailCachedNbtEntries.get(order[j]).path();
                        if (pk.startsWith(groupPrefix) && pk.length() > groupPrefix.length()) {
                            j++;
                        } else break;
                    }
                    if (j - i >= 2) {
                        nbtRows.add(new NbtRow(true, groupPrefix, -1, groupPrefix));
                        for (int k = i; k < j; k++) {
                            String full = detailCachedNbtEntries.get(order[k]).path();
                            String suffix = full.substring(groupPrefix.length());
                            if (!suffix.isEmpty()) {
                                char fc = suffix.charAt(0);
                                if (fc == '.' || fc == ':' || fc == '/') suffix = suffix.substring(1);
                            }
                            nbtRows.add(new NbtRow(false, suffix, order[k], groupPrefix));
                        }
                        i = j;
                        continue;
                    }
                }
            }

            nbtRows.add(new NbtRow(false, pi, order[i], ""));
            i++;
        }
    }

    private String nbtCommonPrefix(String a, String b) {
        int len = Math.min(a.length(), b.length());
        int i = 0;
        while (i < len && a.charAt(i) == b.charAt(i)) i++;
        return a.substring(0, i);
    }

    private List<NbtRow> getVisibleNbtRows() {
        List<NbtRow> visible = new ArrayList<>();
        for (NbtRow row : nbtRows) {
            if (row.heading()) {
                visible.add(row);
            } else if (row.group().isEmpty() || !nbtCollapsedGroups.contains(row.group())) {
                visible.add(row);
            }
        }
        return visible;
    }

    private void renderBuiltinEditBox(GuiGraphics g, int mx, int my, int colValX, int rowY) {
        detailNbtValueBox.setX(colValX);
        detailNbtValueBox.setY(rowY);
        detailNbtValueBox.setWidth(NBT_COL_VAL);
        detailNbtValueBox.setHeight(NBT_ROW_H);
        detailNbtValueBox.setVisible(true);
        detailNbtValueBox.render(g, mx, my, 0);
    }

    private void renderNbtTable(GuiGraphics g, int mx, int my, int tableX, int tableY, int tableW, int tableH) {
        Set<String> activePaths = detailNbtActiveOps.keySet();
        boolean durEnabled = detailDurabilityOp != null;
        if (nbtTableEditingRow < 0) detailNbtValueBox.setVisible(false);

        List<NbtRow> visible = getVisibleNbtRows();
        int totalRows = NBT_BUILTIN_ROWS + visible.size();
        boolean scrollable = totalRows * NBT_ROW_H > tableH;
        int scrollW = scrollable ? SUBMODE_SCROLLBAR_W + SUBMODE_SCROLLBAR_GAP : 0;
        int rowW = tableW - scrollW;
        int visibleCount = Math.max(1, tableH / NBT_ROW_H);
        int maxScroll = Math.max(0, totalRows - visibleCount);
        detailNbtScrollOffset = Mth.clamp(detailNbtScrollOffset, 0, maxScroll);

        g.fill(tableX, tableY, tableX + tableW, tableY + tableH, 0x40000000);
        g.renderOutline(tableX, tableY, tableW, tableH, COL_BORDER);

        int colToggleX = tableX + 2;
        int colPathX = colToggleX + NBT_COL_TOGGLE + NBT_COL_TOGGLE_GAP;
        int colValEnd = tableX + rowW - 2;
        int colOpX = colValEnd - NBT_COL_VAL - NBT_COL_OP_GAP - NBT_COL_OP;
        int colValX = colValEnd - NBT_COL_VAL;
        int pathW = colOpX - NBT_COL_OP_GAP - colPathX;

        int startIdx = detailNbtScrollOffset;
        int endIdx = Math.min(startIdx + visibleCount, totalRows);
        String hoveredFullText = null;

        for (int vi = startIdx; vi < endIdx; vi++) {
            int rowY = tableY + (vi - startIdx) * NBT_ROW_H;
            boolean hovered = mx >= tableX && mx < tableX + rowW
                    && my >= rowY && my < rowY + NBT_ROW_H;

            if (vi < NBT_BUILTIN_ROWS) {
                if (hovered)
                    g.fill(tableX + 1, rowY, tableX + rowW - 1, rowY + NBT_ROW_H, 0x20FFFFFF);
                g.fill(tableX + 1, rowY + NBT_ROW_H - 1, tableX + rowW - 1, rowY + NBT_ROW_H, 0x20FFFFFF);

                switch (vi) {
                    case 0 -> {
                        drawToggle(g, colToggleX, rowY + 1, durEnabled);
                        g.drawString(font, "Durability", colPathX, rowY + 3, DETAIL_DUR_COLOR, false);
                        String opStr = detailDurabilityOp != null ? detailDurabilityOp : "=";
                        g.drawString(font, opStr, colOpX + 4, rowY + 3, COL_WHITE, false);
                        if (nbtTableEditingRow == NBT_EDIT_DURABILITY) {
                            renderBuiltinEditBox(g, mx, my, colValX, rowY);
                        } else {
                            String durVal = detailItemMaxDurability > 0
                                    ? detailItemDurability + "/" + detailItemMaxDurability
                                    : String.valueOf(detailItemDurability);
                            String truncVal = font.plainSubstrByWidth(durVal, NBT_COL_VAL - 4);
                            g.drawString(font, truncVal, colValX + 2, rowY + 3,
                                    durEnabled ? COL_WHITE : COL_GRAY, false);
                        }
                    }
                    case 1 -> {
                        drawToggle(g, colToggleX, rowY + 1, detailEnchantedEnabled);
                        g.drawString(font, "Enchanted", colPathX, rowY + 3, 0xFFDD88FF, false);
                        int enchColor = detailItemEnchanted ? COL_ACCENT : 0xFFFF5555;
                        String enchStr = detailItemEnchanted ? "true" : "false";
                        g.drawString(font, enchStr, colValX + 2, rowY + 3,
                                detailEnchantedEnabled ? enchColor : COL_GRAY, false);
                    }
                    case 2 -> {
                        drawToggle(g, colToggleX, rowY + 1, false);
                        g.drawString(font, "Stack Size", colPathX, rowY + 3, 0xFFFFCC44, false);
                        if (nbtTableEditingRow == NBT_EDIT_STACK_SIZE) {
                            renderBuiltinEditBox(g, mx, my, colValX, rowY);
                        } else {
                            g.drawString(font, String.valueOf(detailItemStackSize), colValX + 2, rowY + 3, COL_GRAY, false);
                        }
                    }
                }
            } else {
                NbtRow row = visible.get(vi - NBT_BUILTIN_ROWS);

                if (row.heading()) {
                    boolean collapsed = nbtCollapsedGroups.contains(row.group());
                    g.fill(tableX + 1, rowY, tableX + rowW - 1, rowY + NBT_ROW_H, 0x18FFFFFF);
                    g.fill(tableX + 1, rowY + NBT_ROW_H - 1, tableX + rowW - 1, rowY + NBT_ROW_H, 0x20FFFFFF);
                    if (hovered)
                        g.fill(tableX + 1, rowY, tableX + rowW - 1, rowY + NBT_ROW_H, 0x10FFFFFF);

                    String arrow = collapsed ? ">" : "v";
                    g.drawString(font, arrow, colToggleX + 2, rowY + 3, NBT_HEADING_COLOR, false);
                    String headText = font.plainSubstrByWidth(row.display(), rowW - 20);
                    g.drawString(font, headText, colToggleX + 12, rowY + 3, NBT_HEADING_COLOR, false);
                    if (hovered && headText.length() < row.display().length()) {
                        hoveredFullText = row.display();
                    }
                } else {
                    int entryIdx = row.entryIdx();
                    NbtFilterData.NbtEntry entry = detailCachedNbtEntries.get(entryIdx);
                    boolean active = activePaths.contains(entry.path());
                    boolean indented = !row.group().isEmpty();

                    if (active)
                        g.fill(tableX + 1, rowY, tableX + rowW - 1, rowY + NBT_ROW_H, COL_SELECTED);
                    else if (hovered)
                        g.fill(tableX + 1, rowY, tableX + rowW - 1, rowY + NBT_ROW_H, COL_HOVER);

                    drawToggle(g, colToggleX, rowY + 1, active);

                    int entryPathX = indented ? colPathX + 6 : colPathX;
                    int entryPathW = indented ? pathW - 6 : pathW;
                    String pathDisplay = row.display();
                    String truncPath = font.plainSubstrByWidth(pathDisplay, entryPathW - 4);
                    g.drawString(font, truncPath, entryPathX, rowY + 3,
                            active ? COL_ACCENT : COL_WHITE, false);

                    if (hovered && truncPath.length() < pathDisplay.length()) {
                        hoveredFullText = entry.path() + " = " + entry.valueDisplay();
                    }

                    String opStr = active ? detailNbtActiveOps.getOrDefault(entry.path(), "=") : "=";
                    int opColor = active ? COL_WHITE : COL_GRAY;
                    g.drawString(font, opStr, colOpX + 4, rowY + 3, opColor, false);

                    if (nbtTableEditingRow == entryIdx) {
                        detailNbtValueBox.setX(colValX);
                        detailNbtValueBox.setY(rowY);
                        detailNbtValueBox.setWidth(NBT_COL_VAL);
                        detailNbtValueBox.setHeight(NBT_ROW_H);
                        detailNbtValueBox.setVisible(true);
                        detailNbtValueBox.render(g, mx, my, 0);
                    } else {
                        String valDisplay = entry.valueDisplay();
                        String truncVal = font.plainSubstrByWidth(valDisplay, NBT_COL_VAL - 4);
                        g.drawString(font, truncVal, colValX + 2, rowY + 3,
                                active ? COL_ACCENT : COL_GRAY, false);
                        if (hovered && truncVal.length() < valDisplay.length() && hoveredFullText == null) {
                            hoveredFullText = entry.path() + " = " + valDisplay;
                        }
                    }
                }
            }
        }

        if (scrollable) {
            int scrollbarX = tableX + rowW + SUBMODE_SCROLLBAR_GAP;
            int thumbH = Math.max(8, (tableH * visibleCount) / totalRows);
            int thumbTravel = Math.max(0, tableH - thumbH);
            int thumbY = maxScroll <= 0 ? tableY : tableY + (detailNbtScrollOffset * thumbTravel) / maxScroll;

            g.fill(scrollbarX, tableY, scrollbarX + SUBMODE_SCROLLBAR_W, tableY + tableH, COL_BTN_BG);
            g.fill(scrollbarX + 1, thumbY, scrollbarX + SUBMODE_SCROLLBAR_W - 1, thumbY + thumbH, COL_ACCENT);
        }

        if (totalRows == 0) {
            g.drawString(font, "No NBT data", tableX + 3, tableY + 2, COL_GRAY, false);
        }

        if (hoveredFullText != null) {
            g.renderTooltip(font, Component.literal(hoveredFullText), mx, my);
        }
    }

    private void drawToggle(GuiGraphics g, int x, int y, boolean on) {
        int size = 10;
        g.fill(x, y, x + size, y + size, on ? 0xFF225522 : 0xFF332222);
        g.renderOutline(x, y, size, size, on ? COL_ACCENT : 0xFF884444);
        if (on) {
            g.fill(x + 3, y + 3, x + 7, y + 7, COL_ACCENT);
        }
    }

    private boolean handleDetailPageClick(double mx, double my, int btn) {
        if (detailNbtPageOpen) {
            return handleNbtSubPageClick(mx, my, btn);
        }

        FilterTargetType targetType = menu.getTargetType();
        boolean isFluidOrChemical = targetType != FilterTargetType.ITEMS;

        int panelX = leftPos + 4;
        int panelY = topPos + 20;
        int panelW = imageWidth - 8;
        int panelH = imageHeight - 24;

        if (!isHovering(panelX, panelY, panelW, panelH, (int) mx, (int) my)) {
            return false;
        }

        int backW = 30;
        if (isHovering(panelX + 4, panelY + 4, backW, 12, (int) mx, (int) my)) {
            closeDetailPage();
            return true;
        }

        int clearW = 40;
        int clearXPos = panelX + panelW - clearW - 4;
        if (isHovering(clearXPos, panelY + 4, clearW, 12, (int) mx, (int) my)) {
            clearDetailEntry();
            return true;
        }

        int contentX = panelX + 4;
        int contentW = panelW - 8;
        int slotX = contentX;
        int itemSlotY = panelY + 20;
        int idFieldX = slotX + 22;
        int idFieldW = contentW - 22;

        if (isHovering(slotX, itemSlotY, 18, 18, (int) mx, (int) my)) {
            String currentId = detailIdInputBox.getValue().trim();
            if (!currentId.isEmpty() && !currentId.startsWith("#")) {
                if (isFluidOrChemical) {
                    menu.clearFilterEntry(detailEditSlot);
                } else {
                    ItemStack openedStack = menu.getOpenedStack();
                    boolean hasConfig = FilterItemData.hasEntryNbt(openedStack, detailEditSlot)
                            || FilterItemData.hasEntryDurability(openedStack, detailEditSlot)
                            || FilterItemData.hasEntryEnchanted(openedStack, detailEditSlot);
                    if (hasConfig) {
                        menu.clearFilterEntryItem(detailEditSlot);
                        NetworkHandler.sendToServer(new SetFilterItemEntryPayload(detailEditSlot, ItemStack.EMPTY));
                    } else {
                        menu.clearFilterEntry(detailEditSlot);
                    }
                }
                detailIdInputBox.setValue("");
                return true;
            }
        }

        // Dropdown clicks
        int dropY = itemSlotY + 22;
        String idVal = detailIdInputBox.getValue().trim();
        boolean isTagMode = idVal.startsWith("#");
        List<String> dropdownList = isTagMode ? detailTagFilteredList : detailItemFilteredList;
        if (detailIdInputBox.isFocused() && !dropdownList.isEmpty()) {
            int dropRows = Math.min(DROPDOWN_ROWS, dropdownList.size());
            int dropH = dropRows * LIST_ROW_H;
            boolean scrollable = dropdownList.size() > DROPDOWN_ROWS;
            int rowW = scrollable ? idFieldW - SUBMODE_SCROLLBAR_W - SUBMODE_SCROLLBAR_GAP : idFieldW;
            int maxScroll = Math.max(0, dropdownList.size() - DROPDOWN_ROWS);
            int startIdx = Mth.clamp(detailTagScrollOffset, 0, maxScroll);
            int endIdx = Math.min(startIdx + DROPDOWN_ROWS, dropdownList.size());

            if (isHovering(idFieldX, dropY, idFieldW, dropH, (int) mx, (int) my)) {
                for (int i = startIdx; i < endIdx; i++) {
                    int rowY = dropY + (i - startIdx) * LIST_ROW_H;
                    if (mx >= idFieldX && mx < idFieldX + rowW
                            && my >= rowY && my < rowY + LIST_ROW_H) {
                        String selected = dropdownList.get(i);
                        if (isTagMode) {
                            NetworkHandler.sendToServer(new SetFilterEntryTagPayload(detailEditSlot, selected));
                            menu.setEntryTag(null, detailEditSlot, selected);
                            detailIdInputBox.setValue("#" + selected);
                        } else if (targetType == FilterTargetType.FLUIDS) {
                            ResourceLocation fluidId = ResourceLocation.tryParse(selected);
                            if (fluidId != null && BuiltInRegistries.FLUID.containsKey(fluidId)) {
                                menu.clearEntryTag(detailEditSlot);
                                NetworkHandler.sendToServer(new SetFilterFluidEntryPayload(detailEditSlot, fluidId.toString()));
                                menu.setFluidFilterEntry(minecraft.player, detailEditSlot, new FluidStack(BuiltInRegistries.FLUID.get(fluidId), 1000));
                                detailIdInputBox.setValue(selected);
                            }
                        } else if (targetType == FilterTargetType.CHEMICALS) {
                            if (MekanismCompat.isValidChemicalId(selected)) {
                                menu.clearEntryTag(detailEditSlot);
                                NetworkHandler.sendToServer(new SetFilterChemicalEntryPayload(detailEditSlot, selected));
                                menu.setChemicalFilterEntry(minecraft.player, detailEditSlot, selected);
                                detailIdInputBox.setValue(selected);
                            }
                        } else {
                            ResourceLocation itemId = ResourceLocation.tryParse(selected);
                            if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
                                Item item = BuiltInRegistries.ITEM.get(itemId);
                                if (item != Items.AIR) {
                                    ItemStack stack = new ItemStack(item);
                                    menu.clearEntryTag(detailEditSlot);
                                    NetworkHandler.sendToServer(new SetFilterItemEntryPayload(detailEditSlot, stack));
                                    menu.setItemFilterEntry(minecraft.player, detailEditSlot, stack);
                                    detailIdInputBox.setValue(selected);
                                }
                            }
                        }
                        detailIdInputBox.setFocused(false);
                        return true;
                    }
                }
                return true;
            }
            dropY += dropH + 2;
        }

        // Compute section Y positions after dropdown
        int labelW = 52;
        int batchY = dropY;
        int stockY = batchY + DETAIL_SECTION_H;

        if (!isFluidOrChemical) {
            int nbtY = stockY + DETAIL_SECTION_H;

            // NBT configure button
            int nbtBtnX = contentX + labelW;
            String nbtBtnLabel = tr("gui.logisticsnetworks.filter.detail.nbt.configure");
            int nbtBtnW = Math.max(70, font.width(nbtBtnLabel) + 8);
            if (isHovering(nbtBtnX, nbtY, nbtBtnW, 14, (int) mx, (int) my)) {
                detailNbtPageOpen = true;
                detailNbtScrollOffset = 0;
                detailNbtInputBox.active = true;
                detailNbtInputBox.setFocused(false);
                nbtSavedImageWidth = imageWidth;
                nbtSavedLeftPos = leftPos;
                if (imageWidth < DETAIL_NBT_MIN_WIDTH) {
                    imageWidth = DETAIL_NBT_MIN_WIDTH;
                    leftPos = (width - imageWidth) / 2;
                }
                return true;
            }
        }

        unfocusAllDetailInputs();

        if (detailIdInputBox.isMouseOver(mx, my)) {
            detailIdInputBox.setFocused(true);
            detailIdInputBox.mouseClicked(mx, my, btn);
            return true;
        }
        if (detailBatchInputBox.isMouseOver(mx, my)) {
            detailBatchInputBox.setFocused(true);
            detailBatchInputBox.mouseClicked(mx, my, btn);
            return true;
        }
        if (detailStockInputBox.isMouseOver(mx, my)) {
            detailStockInputBox.setFocused(true);
            detailStockInputBox.mouseClicked(mx, my, btn);
            return true;
        }
        if (!isFluidOrChemical && detailSlotMappingInputBox.isMouseOver(mx, my)) {
            detailSlotMappingInputBox.setFocused(true);
            detailSlotMappingInputBox.mouseClicked(mx, my, btn);
            return true;
        }
        return true;
    }

    private boolean handleNbtSubPageClick(double mx, double my, int btn) {
        int panelX = leftPos + 4;
        int panelY = topPos + 20;
        int panelW = imageWidth - 8;
        int panelH = imageHeight - 24;
        int contentX = panelX + 4;
        int contentW = panelW - 8;

        if (!isHovering(panelX, panelY, panelW, panelH, (int) mx, (int) my)) {
            return false;
        }

        int backW = 50;
        if (isHovering(panelX + 4, panelY + 4, backW, 12, (int) mx, (int) my)) {
            closeNbtSubPage();
            return true;
        }

        int clearW = 40;
        int clearXPos = panelX + panelW - clearW - 4;
        if (isHovering(clearXPos, panelY + 4, clearW, 12, (int) mx, (int) my)) {
            NetworkHandler.sendToServer(SetFilterEntryNbtPayload.clear(detailEditSlot));
            menu.clearSlotNbtRules(detailEditSlot);
            detailNbtSelectedIdx = -1;
            detailNbtActiveOps.clear();
            nbtTableEditingRow = -1;
            detailNbtInputBox.setValue("");
            detailNbtValueBox.setValue("");
            detailNbtValueBox.setVisible(false);
            detailNbtValueBox.setFocused(false);
            return true;
        }

        int y = panelY + 20;

        String rawLabel = detailNbtRawMode ? "Raw SNBT" : "Table";
        int modeW = Math.max(60, font.width(rawLabel) + 8);
        if (isHovering(contentX, y, modeW, 12, (int) mx, (int) my)) {
            detailNbtRawMode = !detailNbtRawMode;
            if (detailNbtRawMode) {
                int rawY = y + 16;
                int nbtH = panelY + panelH - rawY - 20;
                if (nbtH > 20) {
                    String oldVal = detailNbtInputBox.getValue();
                    detailNbtInputBox = new ThemedMultiLineEditBox(
                            font, contentX, rawY, contentW, nbtH,
                            Component.empty(), Component.empty());
                    detailNbtInputBox.setCharacterLimit(2048);
                    detailNbtInputBox.setValue(oldVal);
                    detailNbtInputBox.active = true;
                }
            }
            return true;
        }
        y += 16;

        if (detailNbtRawMode) {
            if (detailNbtInputBox.active) {
                detailNbtInputBox.mouseClicked(mx, my, btn);
            }

            int nbtH = panelY + panelH - y - 20;
            int doneW = 50;
            int doneX = panelX + (panelW - doneW) / 2;
            int doneY = y + Math.max(20, nbtH) + 4;
            if (isHovering(doneX, doneY, doneW, 14, (int) mx, (int) my)) {
                closeNbtSubPage();
                return true;
            }
        } else {
            return handleNbtTableClick(mx, my, btn, contentX, y, contentW, panelY + panelH - y - 4);
        }

        return true;
    }

    private boolean handleNbtTableClick(double mx, double my, int btn,
                                         int tableX, int tableY, int tableW, int tableH) {
        Set<String> activePaths = detailNbtActiveOps.keySet();
        List<NbtRow> visible = getVisibleNbtRows();
        int totalRows = NBT_BUILTIN_ROWS + visible.size();
        boolean scrollable = totalRows * NBT_ROW_H > tableH;
        int scrollW = scrollable ? SUBMODE_SCROLLBAR_W + SUBMODE_SCROLLBAR_GAP : 0;
        int rowW = tableW - scrollW;
        int visibleCount = Math.max(1, tableH / NBT_ROW_H);
        int maxScroll = Math.max(0, totalRows - visibleCount);
        int startIdx = Mth.clamp(detailNbtScrollOffset, 0, maxScroll);
        int endIdx = Math.min(startIdx + visibleCount, totalRows);

        int colToggleX = tableX + 2;
        int colValEnd = tableX + rowW - 2;
        int colOpX = colValEnd - NBT_COL_VAL - NBT_COL_OP_GAP - NBT_COL_OP;
        int colValX = colValEnd - NBT_COL_VAL;

        for (int vi = startIdx; vi < endIdx; vi++) {
            int rowY = tableY + (vi - startIdx) * NBT_ROW_H;
            if (mx < tableX || mx >= tableX + rowW || my < rowY || my >= rowY + NBT_ROW_H)
                continue;

            if (vi < NBT_BUILTIN_ROWS) {
                int builtinEditId = vi == 0 ? NBT_EDIT_DURABILITY
                        : vi == 1 ? NBT_EDIT_ENCHANTED : NBT_EDIT_STACK_SIZE;

                if (vi == 1) {
                    if (mx < colToggleX + NBT_COL_TOGGLE + 4) {
                        detailEnchantedEnabled = !detailEnchantedEnabled;
                    } else {
                        detailItemEnchanted = !detailItemEnchanted;
                        if (!detailEnchantedEnabled) detailEnchantedEnabled = true;
                    }
                    NetworkHandler.sendToServer(new SetFilterEntryEnchantedPayload(
                            detailEditSlot, detailEnchantedEnabled, detailItemEnchanted));
                    menu.setEntryEnchanted(null, detailEditSlot,
                            detailEnchantedEnabled ? detailItemEnchanted : null);
                    return true;
                }

                if (mx < colToggleX + NBT_COL_TOGGLE + 4) {
                    if (vi == 0) {
                        detailDurabilityOp = detailDurabilityOp != null ? null : ">=";
                        sendDetailDurabilityPacket();
                    }
                } else if (mx >= colOpX && mx < colOpX + NBT_COL_OP) {
                    if (vi == 0) {
                        cycleDurabilityOp();
                        sendDetailDurabilityPacket();
                    }
                } else if (mx >= colValX) {
                    if (nbtTableEditingRow != builtinEditId) {
                        commitDetailBuiltinEdit();
                        nbtTableEditingRow = builtinEditId;
                        detailNbtValueBox.setValue(getBuiltinDefault(builtinEditId));
                    }
                    detailNbtValueBox.setFocused(true);
                    detailNbtValueBox.mouseClicked(mx, my, btn);
                    return true;
                }
                return true;
            }

            NbtRow row = visible.get(vi - NBT_BUILTIN_ROWS);

            if (row.heading()) {
                if (nbtCollapsedGroups.contains(row.group())) {
                    nbtCollapsedGroups.remove(row.group());
                } else {
                    nbtCollapsedGroups.add(row.group());
                }
                return true;
            }

            int entryIdx = row.entryIdx();
            NbtFilterData.NbtEntry entry = detailCachedNbtEntries.get(entryIdx);
            boolean active = activePaths.contains(entry.path());

            if (mx < colToggleX + NBT_COL_TOGGLE + 4) {
                if (active) {
                    int ruleIdx = findActiveRuleIndex(menu.getSlotNbtRules(detailEditSlot), entry.path());
                    if (ruleIdx >= 0) {
                        NetworkHandler.sendToServer(SetFilterEntryNbtPayload.remove(detailEditSlot, ruleIdx));
                        menu.removeSlotNbtRule(detailEditSlot, ruleIdx);
                    }
                    detailNbtActiveOps.remove(entry.path());
                    if (detailNbtSelectedIdx == entryIdx) {
                        detailNbtSelectedIdx = -1;
                        nbtTableEditingRow = -1;
                        detailNbtValueBox.setValue("");
                        detailNbtValueBox.setVisible(false);
                        detailNbtValueBox.setFocused(false);
                    }
                } else {
                    detailNbtSelectedIdx = entryIdx;
                    detailNbtOp = "=";
                    detailNbtValueBox.setValue(entry.valueDisplay());
                    applyNbtSelection();
                    nbtTableEditingRow = -1;
                }
                return true;
            }

            if (mx >= colOpX && mx < colOpX + NBT_COL_OP) {
                if (!active) {
                    detailNbtSelectedIdx = entryIdx;
                    detailNbtOp = "=";
                    detailNbtValueBox.setValue(entry.valueDisplay());
                    applyNbtSelection();
                }
                cycleDetailNbtOp(entry.path());
                return true;
            }

            if (mx >= colValX) {
                detailNbtSelectedIdx = entryIdx;
                if (nbtTableEditingRow != entryIdx) {
                    detailNbtValueBox.setValue(entry.valueDisplay());
                }
                nbtTableEditingRow = entryIdx;
                detailNbtValueBox.setFocused(true);
                detailNbtValueBox.mouseClicked(mx, my, btn);
                return true;
            }

            return true;
        }

        if (nbtTableEditingRow >= 0) {
            commitDetailNbtValueEdit();
        } else if (nbtTableEditingRow <= NBT_EDIT_DURABILITY) {
            commitDetailBuiltinEdit();
        }
        return true;
    }

    private void cycleDetailNbtOp(String path) {
        String currentOp = detailNbtActiveOps.getOrDefault(path, "=");
        String nextOp = FilterItemData.nextNbtOperator(currentOp);
        detailNbtActiveOps.put(path, nextOp);
        detailNbtOp = nextOp;

        int ruleIdx = findActiveRuleIndex(menu.getSlotNbtRules(detailEditSlot), path);
        if (ruleIdx >= 0) {
            String savedVal = formatNbtValue(menu.getSlotNbtRules(detailEditSlot).get(ruleIdx).value().toString());
            NetworkHandler.sendToServer(SetFilterEntryNbtPayload.remove(detailEditSlot, ruleIdx));
            menu.removeSlotNbtRule(detailEditSlot, ruleIdx);
            NetworkHandler.sendToServer(SetFilterEntryNbtPayload.add(detailEditSlot, path, nextOp, savedVal));
            menu.addSlotNbtRule(minecraft.player, detailEditSlot, path, nextOp, savedVal);
            List<FilterItemData.SlotNbtRule> updatedRules = menu.getSlotNbtRules(detailEditSlot);
            int newIdx = findActiveRuleIndex(updatedRules, path);
            if (newIdx >= 0) {
                NetworkHandler.sendToServer(SetFilterEntryNbtPayload.setValue(detailEditSlot, newIdx, savedVal));
                menu.setSlotNbtRuleValue(detailEditSlot, newIdx, savedVal);
            }
        }
    }

    private void applyNbtSelection() {
        if (detailEditSlot < 0 || detailNbtSelectedIdx < 0
                || detailNbtSelectedIdx >= detailCachedNbtEntries.size())
            return;

        NbtFilterData.NbtEntry entry = detailCachedNbtEntries.get(detailNbtSelectedIdx);
        String opSymbol = detailNbtActiveOps.getOrDefault(entry.path(), detailNbtOp);

        String valueOverride = detailNbtValueBox.getValue().trim();
        String fallbackValue = valueOverride.isEmpty() ? entry.valueDisplay() : valueOverride;
        NetworkHandler.sendToServer(SetFilterEntryNbtPayload.add(detailEditSlot, entry.path(), opSymbol, fallbackValue));
        menu.addSlotNbtRule(minecraft.player, detailEditSlot, entry.path(), opSymbol, fallbackValue);

        if (!valueOverride.isEmpty() && !valueOverride.equals(entry.valueDisplay())) {
            List<FilterItemData.SlotNbtRule> rules = menu.getSlotNbtRules(detailEditSlot);
            int ruleIdx = findActiveRuleIndex(rules, entry.path());
            if (ruleIdx >= 0) {
                NetworkHandler.sendToServer(SetFilterEntryNbtPayload.setValue(detailEditSlot, ruleIdx, valueOverride));
                menu.setSlotNbtRuleValue(detailEditSlot, ruleIdx, valueOverride);
            }
            detailCachedNbtEntries.set(detailNbtSelectedIdx,
                    new NbtFilterData.NbtEntry(entry.path(), valueOverride));
            buildNbtRows();
        }
        detailNbtActiveOps.put(entry.path(), opSymbol);
    }

    private String getBuiltinDefault(int builtinId) {
        return switch (builtinId) {
            case NBT_EDIT_DURABILITY -> String.valueOf(detailItemDurability);
            case NBT_EDIT_ENCHANTED -> detailItemEnchanted ? "true" : "false";
            case NBT_EDIT_STACK_SIZE -> String.valueOf(detailItemStackSize);
            default -> "";
        };
    }

    private void commitDetailBuiltinEdit() {
        if (nbtTableEditingRow >= 0 || nbtTableEditingRow == -1) return;
        String val = detailNbtValueBox.getValue().trim();
        if (val.isEmpty()) val = getBuiltinDefault(nbtTableEditingRow);

        switch (nbtTableEditingRow) {
            case NBT_EDIT_DURABILITY -> {
                try {
                    int durVal = Integer.parseInt(val);
                    detailItemDurability = durVal;
                    if (detailDurabilityOp == null) detailDurabilityOp = ">=";
                    menu.setEntryDurability(null, detailEditSlot, detailDurabilityOp, durVal);
                    NetworkHandler.sendToServer(new SetFilterEntryDurabilityPayload(
                            detailEditSlot, detailDurabilityOp, durVal));
                } catch (NumberFormatException ignored) {}
            }
            case NBT_EDIT_ENCHANTED -> {
                detailItemEnchanted = "true".equalsIgnoreCase(val);
            }
            case NBT_EDIT_STACK_SIZE -> {
                try {
                    detailItemStackSize = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }
        }
        nbtTableEditingRow = -1;
        detailNbtValueBox.setVisible(false);
        detailNbtValueBox.setFocused(false);
    }

    private void commitDetailNbtValueEdit() {
        String val = detailNbtValueBox.getValue().trim();
        if (val.isEmpty() && detailNbtSelectedIdx >= 0
                && detailNbtSelectedIdx < detailCachedNbtEntries.size()) {
            NbtFilterData.NbtEntry entry = detailCachedNbtEntries.get(detailNbtSelectedIdx);
            detailNbtValueBox.setValue(entry.valueDisplay());
        }
        applyNbtSelection();
        detailNbtValueBox.setFocused(false);
        nbtTableEditingRow = -1;
    }

    private void commitDetailId() {
        String val = detailIdInputBox.getValue().trim();
        if (val.isEmpty()) return;

        if (val.startsWith("#")) {
            String normalized = FilterTagUtil.normalizeTag(val);
            if (normalized != null) {
                NetworkHandler.sendToServer(new SetFilterEntryTagPayload(detailEditSlot, normalized));
                menu.setEntryTag(null, detailEditSlot, normalized);
                detailIdInputBox.setValue("#" + normalized);
            }
        } else {
            ResourceLocation itemId = ResourceLocation.tryParse(val);
            if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
                Item item = BuiltInRegistries.ITEM.get(itemId);
                if (item != Items.AIR) {
                    ItemStack stack = new ItemStack(item);
                    menu.clearEntryTag(detailEditSlot);
                    NetworkHandler.sendToServer(new SetFilterItemEntryPayload(detailEditSlot, stack));
                    menu.setItemFilterEntry(minecraft.player, detailEditSlot, stack);
                    detailIdInputBox.setValue(itemId.toString());
                }
            }
        }
    }

    private void flushNbtSubPage() {
        if (detailEditSlot < 0) return;
        if (detailNbtRawMode) {
            String nbtVal = detailNbtInputBox.getValue().replace("\n", " ").trim();
            String existingRaw = FilterItemData.getEntryNbtRaw(menu.getOpenedStack(), detailEditSlot);
            if (!nbtVal.isEmpty() && !nbtVal.equals(existingRaw)) {
                NetworkHandler.sendToServer(SetFilterEntryNbtPayload.setRaw(detailEditSlot, nbtVal));
            } else if (nbtVal.isEmpty() && existingRaw != null) {
                NetworkHandler.sendToServer(SetFilterEntryNbtPayload.clear(detailEditSlot));
                menu.clearSlotNbtRules(detailEditSlot);
            }
        }
    }

    private void closeNbtSubPage() {
        flushNbtSubPage();
        detailNbtPageOpen = false;
        detailNbtInputBox.active = false;
        detailNbtInputBox.setFocused(false);
        detailNbtValueBox.setVisible(false);
        detailNbtValueBox.setFocused(false);
        nbtTableEditingRow = -1;
        if (nbtSavedImageWidth > 0) {
            imageWidth = nbtSavedImageWidth;
            leftPos = nbtSavedLeftPos;
            nbtSavedImageWidth = -1;
        }
    }

    private void unfocusAllDetailInputs() {
        if (detailIdInputBox.isFocused()) {
            commitDetailId();
        }
        if (detailBatchInputBox.isFocused() || detailStockInputBox.isFocused() || detailSlotMappingInputBox.isFocused()) {
            flushDetailPageInputs();
        }
        detailIdInputBox.setFocused(false);
        detailBatchInputBox.setFocused(false);
        detailStockInputBox.setFocused(false);
        detailSlotMappingInputBox.setFocused(false);
        detailNbtInputBox.setFocused(false);
        detailNbtValueBox.setFocused(false);
    }

    private boolean handleDetailPageKey(int key, int scan, int modifiers) {
        if (detailNbtPageOpen) {
            if (detailNbtInputBox.isFocused()) {
                detailNbtInputBox.keyPressed(key, scan, modifiers);
                return true;
            }
            if (detailNbtValueBox.isFocused()) {
                if (key == 257 || key == 256) {
                    if (nbtTableEditingRow <= NBT_EDIT_DURABILITY) {
                        commitDetailBuiltinEdit();
                    } else {
                        commitDetailNbtValueEdit();
                    }
                    return true;
                }
                detailNbtValueBox.keyPressed(key, scan, modifiers);
                return true;
            }
            return true;
        }

        if (key == 257) {
            if (detailIdInputBox.isFocused()) {
                commitDetailId();
                detailIdInputBox.setFocused(false);
                return true;
            }
            if (detailBatchInputBox.isFocused()) {
                flushDetailPageInputs();
                detailBatchInputBox.setFocused(false);
                return true;
            }
            if (detailStockInputBox.isFocused()) {
                flushDetailPageInputs();
                detailStockInputBox.setFocused(false);
                return true;
            }
            if (detailSlotMappingInputBox.isFocused()) {
                flushDetailPageInputs();
                detailSlotMappingInputBox.setFocused(false);
                return true;
            }
        }

        if (detailIdInputBox.isFocused()) {
            detailIdInputBox.keyPressed(key, scan, modifiers);
            return true;
        }
        if (detailBatchInputBox.isFocused()) {
            detailBatchInputBox.keyPressed(key, scan, modifiers);
            return true;
        }
        if (detailStockInputBox.isFocused()) {
            detailStockInputBox.keyPressed(key, scan, modifiers);
            return true;
        }
        if (detailSlotMappingInputBox.isFocused()) {
            detailSlotMappingInputBox.keyPressed(key, scan, modifiers);
            return true;
        }

        return true;
    }

    private boolean handleDetailPageScroll(double mx, double my, double delta) {
        if (detailNbtPageOpen) {
            if (detailNbtRawMode && detailNbtInputBox.active) {
                return detailNbtInputBox.mouseScrolled(mx, my, delta);
            }
            int panelY = topPos + 20;
            int panelH = imageHeight - 24;
            int y = panelY + 36;
            int listH = panelY + panelH - y - 4;
            int totalRows = NBT_BUILTIN_ROWS + getVisibleNbtRows().size();
            int visibleRows = Math.max(1, listH / NBT_ROW_H);
            int maxScroll = Math.max(0, totalRows - visibleRows);
            detailNbtScrollOffset = Mth.clamp(detailNbtScrollOffset - (int) delta, 0, maxScroll);
            return true;
        }

        // Dropdown scroll
        if (detailIdInputBox.isFocused()) {
            String idVal = detailIdInputBox.getValue().trim();
            List<String> activeList = idVal.startsWith("#") ? detailTagFilteredList : detailItemFilteredList;
            if (!activeList.isEmpty()) {
                int maxScroll = Math.max(0, activeList.size() - DROPDOWN_ROWS);
                detailTagScrollOffset = Mth.clamp(detailTagScrollOffset - (int) delta, 0, maxScroll);
                return true;
            }
        }

        if (detailBatchInputBox.isMouseOver(mx, my) || detailBatchInputBox.isFocused()) {
            String str = detailBatchInputBox.getValue().trim();
            int current = 0;
            if (!str.isEmpty()) {
                try { current = Integer.parseInt(str); } catch (NumberFormatException ignored) {}
            }
            int scrollDelta = computeScrollDelta(delta, menu.getTargetType());
            int next = Math.max(0, current + scrollDelta);
            detailBatchInputBox.setValue(String.valueOf(next));
            return true;
        }
        if (detailStockInputBox.isMouseOver(mx, my) || detailStockInputBox.isFocused()) {
            String str = detailStockInputBox.getValue().trim();
            int current = 0;
            if (!str.isEmpty()) {
                try { current = Integer.parseInt(str); } catch (NumberFormatException ignored) {}
            }
            int scrollDelta = computeScrollDelta(delta, menu.getTargetType());
            int next = Math.max(0, current + scrollDelta);
            detailStockInputBox.setValue(String.valueOf(next));
            return true;
        }

        return true;
    }

    private boolean isAnyItemSlot(int slot) {
        if (slot >= menu.slots.size()) return false;
        if (!menu.slots.get(slot).getItem().isEmpty()) return false;
        if (menu.isTagSlot(slot)) return false;
        return menu.getEntryBatch(slot) > 0 || menu.getEntryStock(slot) > 0;
    }

    private void cycleDurabilityOp() {
        if (detailDurabilityOp == null) {
            detailDurabilityOp = "=";
        } else if (detailDurabilityOp.equals("=")) {
            detailDurabilityOp = ">=";
        } else if (detailDurabilityOp.equals(">=")) {
            detailDurabilityOp = "<=";
        } else {
            detailDurabilityOp = "=";
        }
    }

    private void sendDetailDurabilityPacket() {
        if (detailDurabilityOp != null) {
            menu.setEntryDurability(null, detailEditSlot, detailDurabilityOp, detailItemDurability);
            NetworkHandler.sendToServer(new SetFilterEntryDurabilityPayload(
                    detailEditSlot, detailDurabilityOp, detailItemDurability));
        } else {
            menu.setEntryDurability(null, detailEditSlot, null, 0);
            NetworkHandler.sendToServer(new SetFilterEntryDurabilityPayload(
                    detailEditSlot, "", 0));
        }
    }

    private static String abbreviateNbtPath(String path) {
        StringBuilder result = new StringBuilder();
        String[] segments = path.split("\\.");
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) result.append(".");
            String seg = segments[i];
            int colon = seg.indexOf(':');
            if (colon > 4) {
                result.append(seg, 0, 4).append(seg.substring(colon));
            } else {
                result.append(seg);
            }
        }
        return result.toString();
    }

    private void clearDetailEntry() {
        if (detailEditSlot < 0) return;
        int slot = detailEditSlot;

        ItemStack openedStack = menu.getOpenedStack();
        boolean hasNbtConfig = FilterItemData.hasEntryNbt(openedStack, slot)
                || FilterItemData.hasEntryDurability(openedStack, slot)
                || FilterItemData.hasEntryEnchanted(openedStack, slot);

        if (hasNbtConfig) {
            menu.clearFilterEntryItem(slot);
            NetworkHandler.sendToServer(new SetFilterItemEntryPayload(slot, ItemStack.EMPTY));
        } else {
            menu.clearFilterEntry(slot);
        }

        detailIdInputBox.setValue("");
        detailBatchInputBox.setValue("");
        detailStockInputBox.setValue("");
        detailSlotMappingInputBox.setValue("");
        if (!hasNbtConfig) {
            detailDurabilityValueBox.setValue("");
            detailDurabilityOp = null;
            detailNbtInputBox.setValue("");
            detailNbtValueBox.setValue("");
            detailNbtSelectedIdx = -1;
            detailNbtOp = "=";
            detailNbtActiveOps.clear();
            nbtTableEditingRow = -1;
        }
    }

    private static class ThemedMultiLineEditBox extends MultiLineEditBox {
        ThemedMultiLineEditBox(Font font, int x, int y, int w, int h,
                               Component message, Component placeholder) {
            super(font, x, y, w, h, message, placeholder);
        }

        @Override
        protected void renderDecorations(GuiGraphics graphics) {
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            if (scrollbarVisible()) {
                int sbX = getX() + width - 8;
                int sbY = getY();
                int sbH = height;

                g.fill(sbX, sbY, sbX + 8, sbY + sbH, 0xFF111111);

                int maxScroll = getMaxScrollAmount();
                if (maxScroll > 0) {
                    int totalH = sbH + maxScroll;
                    int thumbH = Mth.clamp((int) ((float) (sbH * sbH) / (float) totalH), 32, sbH);
                    int thumbY = (int) (scrollAmount() * (sbH - thumbH) / maxScroll) + sbY;
                    thumbY = Math.max(thumbY, sbY);

                    g.fill(sbX, thumbY, sbX + 8, thumbY + thumbH, 0xFF3A3A3A);
                    g.fill(sbX, thumbY, sbX + 7, thumbY + thumbH - 1, 0xFF4A4A4A);
                }
            }
        }
    }
}
