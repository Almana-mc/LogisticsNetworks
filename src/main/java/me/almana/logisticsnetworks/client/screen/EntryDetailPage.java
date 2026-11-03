package me.almana.logisticsnetworks.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import me.almana.logisticsnetworks.client.FilterResourceRenderer;
import me.almana.logisticsnetworks.component.NbtCriterion;
import me.almana.logisticsnetworks.data.IoSide;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.FilterTagUtil;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import me.almana.logisticsnetworks.filter.NbtFilterData;
import me.almana.logisticsnetworks.filter.NbtPath;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static me.almana.logisticsnetworks.client.screen.FilterScreen.*;

final class EntryDetailPage {

    interface Host {

        enum Part { KIND, ID, TAG_PICKER, BATCH, STOCK, CLEAR }

        Rect2i panel();

        void resize(int minWidth, int minHeight);

        void restoreSize();

        Component title(int entry);

        default boolean shows(Part part) {
            return true;
        }

        FilterTargetType kind(int entry);

        @Nullable
        String tag(int entry);

        FluidStack fluid(int entry);

        @Nullable
        String chemical(int entry);

        ItemStack sample(int entry);

        boolean hasEntry(int entry);

        boolean isNbtOnly(int entry);

        boolean hasNbt(int entry);

        boolean hasConfig(int entry);

        void renderAny(GuiGraphics g, int x, int y);

        void setTag(int entry, String tag, FilterTargetType kind);

        void clearTag(int entry);

        void setItem(int entry, ItemStack stack);

        void setFluid(int entry, FluidStack stack);

        void setChemical(int entry, String chemicalId);

        void clearEntry(int entry);

        void clearItem(int entry);

        void openTagPicker(int entry, FilterTargetType kind, @Nullable ResourceLocation current,
                Consumer<String> picked);

        int batch(int entry);

        int stock(int entry);

        void setAmounts(int entry, int batch, int stock);

        @Nullable
        IoSide side(int entry);

        void setSide(int entry, @Nullable IoSide side);

        String slotExpression(int entry);

        void setSlotExpression(int entry, String expression);

        @Nullable
        default Component slotsToggle(int entry) {
            return null;
        }

        default void toggleSlots(int entry) {
        }

        default String sideTooltip() {
            return "gui.logisticsnetworks.filter.detail.side.tooltip";
        }

        boolean nbtStrict(int entry);

        void setNbtStrict(int entry, boolean strict);

        List<NbtCriterion> nbtRules(int entry);

        @Nullable
        String nbtRaw(int entry);

        void addNbtRule(int entry, NbtPath path, String operator, String fallbackValue);

        void removeNbtRule(int entry, int ruleIndex);

        void setNbtRuleValue(int entry, int ruleIndex, String value);

        void setNbtRaw(int entry, String raw);

        void clearNbt(int entry);
    }

    private static final int DETAIL_MIN_HEIGHT = 230;
    private static final int DETAIL_NBT_MIN_WIDTH = 380;
    private static final int NBT_COL_TOGGLE = 14;
    private static final int NBT_COL_TOGGLE_GAP = 4;
    private static final int NBT_COL_OP = 20;
    private static final int NBT_COL_VAL = 140;
    private static final int NBT_COL_OP_GAP = 24;
    private static final int NBT_ROW_H = 14;
    private static final int NBT_HEADING_COLOR = 0xFF88AACC;
    private static final int DETAIL_SECTION_H = 22;
    private static final int DETAIL_LABEL_W = 52;
    private static final int DETAIL_TAG_COLOR = 0xFF44BB44;
    private static final int DETAIL_NBT_COLOR = 0xFFFFAA00;
    private static final int DETAIL_DUR_COLOR = 0xFF55BBFF;
    private static final int DETAIL_SLOT_COLOR = 0xFFBB88FF;
    private static final int DETAIL_SIDE_COLOR = 0xFF55DDDD;
    private static final int SLOTS_TOGGLE_W = 50;

    private record NbtRow(boolean heading, String display, int entryIdx, String group) {}

    private final Font font;
    private final Host host;

    private int detailEditSlot = -1;
    private FilterTargetType detailKind = FilterTargetType.ITEMS;
    private final List<String> detailCachedTags = new ArrayList<>();
    private final List<String> detailAllTags = new ArrayList<>();
    private final List<String> detailTagFilteredList = new ArrayList<>();
    private final List<String> detailAllItemIds = new ArrayList<>();
    private final List<String> detailItemFilteredList = new ArrayList<>();
    private final List<NbtFilterData.NbtEntry> detailCachedNbtEntries = new ArrayList<>();
    private int detailTagScrollOffset = 0;
    private boolean detailNbtRawMode = false;
    private boolean detailNbtPageOpen = false;
    private int detailNbtScrollOffset = 0;
    private int detailNbtSelectedIdx = -1;
    private String detailNbtOp = "=";
    private final Map<NbtPath, String> detailNbtActiveOps = new HashMap<>();
    private int nbtTableEditingRow = -1;
    private final List<NbtRow> nbtRows = new ArrayList<>();
    private final Set<String> nbtCollapsedGroups = new HashSet<>();
    private String lastDetailIdFilter = "";

    private final EditBox detailNbtValueBox;
    private final EditBox detailIdInputBox;
    private final EditBox detailBatchInputBox;
    private final EditBox detailStockInputBox;
    private final EditBox detailSlotMappingInputBox;
    private MultiLineEditBox detailNbtInputBox;

    EntryDetailPage(Font font, Host host) {
        this.font = font;
        this.host = host;

        detailIdInputBox = new EditBox(font, 0, 0, 100, 14, Component.empty());
        detailIdInputBox.setMaxLength(256);
        detailIdInputBox.setVisible(false);
        detailIdInputBox.setBordered(true);
        detailIdInputBox.setTextColor(COL_WHITE);
        detailIdInputBox.setHint(Component.literal("Item or #tag"));

        detailBatchInputBox = new EditBox(font, 0, 0, 50, 14, Component.empty());
        detailBatchInputBox.setMaxLength(32);
        detailBatchInputBox.setVisible(false);
        detailBatchInputBox.setBordered(true);
        detailBatchInputBox.setTextColor(COL_WHITE);

        detailStockInputBox = new EditBox(font, 0, 0, 50, 14, Component.empty());
        detailStockInputBox.setMaxLength(32);
        detailStockInputBox.setVisible(false);
        detailStockInputBox.setBordered(true);
        detailStockInputBox.setTextColor(COL_WHITE);

        detailSlotMappingInputBox = new EditBox(font, 0, 0, 100, 14, Component.empty());
        detailSlotMappingInputBox.setMaxLength(128);
        detailSlotMappingInputBox.setVisible(false);
        detailSlotMappingInputBox.setBordered(true);
        detailSlotMappingInputBox.setTextColor(COL_WHITE);

        detailNbtValueBox = new EditBox(font, 0, 0, 80, 14, Component.empty());
        detailNbtValueBox.setMaxLength(256);
        detailNbtValueBox.setVisible(false);
        detailNbtValueBox.setBordered(true);
        detailNbtValueBox.setTextColor(COL_WHITE);

        detailNbtInputBox = new ThemedMultiLineEditBox(
                font, 0, 0, 100, 40, Component.empty(), Component.empty());
        detailNbtInputBox.setCharacterLimit(2048);
        detailNbtInputBox.active = false;
    }

    boolean isOpen() {
        return detailEditSlot >= 0;
    }

    boolean isNbtOpen() {
        return detailNbtPageOpen;
    }

    boolean hasFocusedInput() {
        return detailIdInputBox.isFocused()
                || detailBatchInputBox.isFocused()
                || detailStockInputBox.isFocused()
                || detailSlotMappingInputBox.isFocused()
                || detailNbtValueBox.isFocused()
                || detailNbtInputBox.isFocused();
    }

    void tick() {
        if (detailEditSlot < 0) return;
        String currentFilter = detailIdInputBox.getValue().trim().toLowerCase();
        if (!currentFilter.equals(lastDetailIdFilter)) {
            rebuildIdFilteredList();
            detailTagScrollOffset = 0;
        }
    }

    void flush() {
        flushDetailPageInputs();
        if (detailNbtPageOpen) {
            flushNbtSubPage();
        }
    }

    void escape() {
        if (detailNbtPageOpen) {
            closeNbtSubPage();
        } else {
            close();
        }
    }

    Rect2i slotArea() {
        Rect2i panel = host.panel();
        return new Rect2i(panel.getX() + 4, panel.getY() + 20, 18, 18);
    }

    void dropItem(ItemStack stack) {
        host.setItem(detailEditSlot, stack);
        detailIdInputBox.setValue(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    void dropFluid(FluidStack stack) {
        host.setFluid(detailEditSlot, stack);
        detailIdInputBox.setValue(BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString());
    }

    void dropChemical(String chemicalId) {
        host.setChemical(detailEditSlot, chemicalId);
        detailIdInputBox.setValue(chemicalId);
    }

    void open(int slot) {
        detailEditSlot = slot;
        detailNbtRawMode = false;
        detailNbtPageOpen = false;
        detailNbtScrollOffset = 0;
        host.resize(0, DETAIL_MIN_HEIGHT);

        detailKind = host.kind(slot);
        loadDetailKind(slot);
    }

    private void loadDetailKind(int slot) {
        FilterTargetType targetType = detailKind;
        boolean isFluidOrChemical = targetType != FilterTargetType.ITEMS;
        detailTagScrollOffset = 0;
        lastDetailIdFilter = "";

        detailCachedTags.clear();
        detailAllTags.clear();
        detailAllItemIds.clear();

        String existingTag = host.tag(slot);
        Minecraft mc = Minecraft.getInstance();

        if (targetType == FilterTargetType.FLUIDS) {
            FluidStack fluidEntry = host.fluid(slot);
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

            String chemEntry = host.chemical(slot);
            if (existingTag != null) {
                detailIdInputBox.setValue("#" + existingTag);
            } else if (chemEntry != null) {
                detailIdInputBox.setValue(chemEntry);
            } else {
                detailIdInputBox.setValue("");
            }
            detailIdInputBox.setHint(Component.literal("Chemical or #tag"));
        } else {
            ItemStack slotItem = host.sample(slot);
            if (!slotItem.isEmpty()) {
                var item = slotItem.getItem();
                BuiltInRegistries.ITEM.getTagNames().forEach(tagKey -> {
                    var holders = BuiltInRegistries.ITEM.getTag(tagKey);
                    if (holders.isPresent()
                            && holders.get().stream().anyMatch(h -> h.value() == item)) {
                        detailCachedTags.add(tagKey.location().toString());
                    }
                });
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

        detailIdInputBox.setVisible(host.shows(Host.Part.ID));
        detailIdInputBox.setFocused(false);
        rebuildIdFilteredList();

        int batch = host.batch(slot);
        int stock = host.stock(slot);
        detailBatchInputBox.setValue(batch > 0 ? String.valueOf(batch) : "");
        detailBatchInputBox.setVisible(host.shows(Host.Part.BATCH));
        detailBatchInputBox.setFocused(false);
        detailStockInputBox.setValue(stock > 0 ? String.valueOf(stock) : "");
        detailStockInputBox.setVisible(host.shows(Host.Part.STOCK));
        detailStockInputBox.setFocused(false);

        if (isFluidOrChemical) {
            detailSlotMappingInputBox.setValue("");
            detailSlotMappingInputBox.setVisible(false);
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
        } else {
            String slotMapping = host.slotExpression(slot);
            detailSlotMappingInputBox.setValue(slotMapping);
            detailSlotMappingInputBox.setVisible(true);
            detailSlotMappingInputBox.setFocused(false);

            ItemStack slotItem = host.sample(slot);
            String existingNbtRaw = host.nbtRaw(slot);
            if (existingNbtRaw != null) {
                detailNbtInputBox.setValue(existingNbtRaw);
            } else {
                if (!slotItem.isEmpty() && mc.player != null) {
                    CompoundTag components = NbtFilterData.getSerializedComponents(
                            slotItem, mc.player.level().registryAccess());
                    detailNbtInputBox.setValue(components != null ? components.toString() : "");
                } else {
                    detailNbtInputBox.setValue("");
                }
            }
            detailNbtInputBox.active = false;
            detailNbtInputBox.setFocused(false);

            detailCachedNbtEntries.clear();
            if (!slotItem.isEmpty() && mc.player != null) {
                detailCachedNbtEntries.addAll(NbtFilterData.extractEntries(
                        slotItem, mc.player.level().registryAccess()));
            } else {
                List<NbtCriterion> stored = host.nbtRules(slot);
                for (NbtCriterion r : stored) {
                    String display = r.value().getAsString();
                    detailCachedNbtEntries.add(new NbtFilterData.NbtEntry(r.path(), display));
                }
                for (NbtFilterData.NbtEntry entry : NbtFilterData.getDefaultEntries()) {
                    if (detailCachedNbtEntries.stream().noneMatch(existing -> existing.path().equals(entry.path()))) {
                        detailCachedNbtEntries.add(entry);
                    }
                }
            }
            nbtCollapsedGroups.clear();
            buildNbtRows();

            detailNbtActiveOps.clear();
            List<NbtCriterion> existingRules = host.nbtRules(slot);
            for (NbtCriterion r : existingRules) {
                detailNbtActiveOps.put(r.path(), r.operator());
            }
            detailNbtOp = "=";
            detailNbtSelectedIdx = -1;
            detailNbtValueBox.setValue("");
            detailNbtValueBox.setVisible(false);
            detailNbtValueBox.setFocused(false);
            nbtTableEditingRow = -1;
        }
    }

    private void rebuildIdFilteredList() {
        String raw = detailIdInputBox.getValue().trim();
        boolean isTagMode = raw.startsWith("#");
        detailTagFilteredList.clear();
        detailItemFilteredList.clear();

        if (isTagMode) {
            String filter = raw.substring(1).toLowerCase();
            if (filter.isEmpty()) {
                detailTagFilteredList.addAll(detailAllTags);
            } else {
                for (String tag : detailAllTags) {
                    if (tag.toLowerCase().contains(filter))
                        detailTagFilteredList.add(tag);
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

    void close() {
        flushDetailPageInputs();
        detailEditSlot = -1;
        detailNbtPageOpen = false;
        host.restoreSize();

        detailIdInputBox.setVisible(false);
        detailIdInputBox.setFocused(false);
        detailBatchInputBox.setVisible(false);
        detailBatchInputBox.setFocused(false);
        detailStockInputBox.setVisible(false);
        detailStockInputBox.setFocused(false);
        detailSlotMappingInputBox.setVisible(false);
        detailSlotMappingInputBox.setFocused(false);
        detailNbtInputBox.active = false;
        detailNbtInputBox.setFocused(false);
    }

    private void flushDetailPageInputs() {
        if (detailEditSlot < 0) return;
        int slot = detailEditSlot;
        FilterTargetType targetType = detailKind;
        boolean isFluidOrChemical = targetType != FilterTargetType.ITEMS;

        if (host.shows(Host.Part.ID)) {
            flushDetailId(slot, targetType, isFluidOrChemical);
        }

        if (host.shows(Host.Part.BATCH) || host.shows(Host.Part.STOCK)) {
            String batchStr = detailBatchInputBox.getValue().trim();
            int batchVal = 0;
            if (!batchStr.isEmpty()) {
                try { batchVal = ArithmeticExpression.evaluate(batchStr); } catch (NumberFormatException ignored) {}
            }
            String stockStr = detailStockInputBox.getValue().trim();
            int stockVal = 0;
            if (!stockStr.isEmpty()) {
                try { stockVal = ArithmeticExpression.evaluate(stockStr); } catch (NumberFormatException ignored) {}
            }
            batchVal = Math.max(0, batchVal);
            stockVal = Math.max(0, stockVal);
            detailBatchInputBox.setValue(batchVal > 0 ? String.valueOf(batchVal) : "");
            detailStockInputBox.setValue(stockVal > 0 ? String.valueOf(stockVal) : "");
            if (batchVal != host.batch(slot) || stockVal != host.stock(slot)) {
                host.setAmounts(slot, batchVal, stockVal);
            }
        }

        if (!isFluidOrChemical) {
            String slotMapStr = detailSlotMappingInputBox.getValue().trim();
            String currentSlotMap = host.slotExpression(slot);
            if (!slotMapStr.equals(currentSlotMap)) {
                host.setSlotExpression(slot, slotMapStr);
            }

            if (detailNbtPageOpen) {
                flushNbtSubPage();
            }
        }
    }

    private void flushDetailId(int slot, FilterTargetType targetType, boolean isFluidOrChemical) {
        String idVal = detailIdInputBox.getValue().trim();
        if (idVal.startsWith("#")) {
            String normalizedTag = FilterTagUtil.normalizeTag(idVal);
            String current = host.tag(slot);
            if (!Objects.equals(normalizedTag, current)
                    || normalizedTag != null && host.kind(slot) != targetType) {
                if (normalizedTag == null) {
                    host.clearTag(slot);
                } else {
                    host.setTag(slot, normalizedTag, detailKind);
                }
            }
        } else if (!idVal.isEmpty()) {
            if (targetType == FilterTargetType.FLUIDS) {
                ResourceLocation fluidId = ResourceLocation.tryParse(idVal);
                if (fluidId != null && BuiltInRegistries.FLUID.containsKey(fluidId)) {
                    host.setFluid(slot, new FluidStack(BuiltInRegistries.FLUID.get(fluidId), 1000));
                }
            } else if (targetType == FilterTargetType.CHEMICALS) {
                if (MekanismCompat.isValidChemicalId(idVal)) {
                    host.setChemical(slot, idVal);
                }
            } else {
                ResourceLocation itemId = ResourceLocation.tryParse(idVal);
                if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
                    Item item = BuiltInRegistries.ITEM.get(itemId);
                    if (item != Items.AIR) {
                        ItemStack currentSlotItem = host.sample(slot);
                        if (currentSlotItem.isEmpty() || !currentSlotItem.is(item)) {
                            host.setItem(slot, new ItemStack(item));
                        }
                    }
                }
            }
        } else if (host.hasEntry(slot) && host.kind(slot) == targetType) {
            if (isFluidOrChemical || !host.hasConfig(slot)) {
                host.clearEntry(slot);
            } else {
                host.clearItem(slot);
            }
        }
    }

    void render(GuiGraphics g, int mx, int my) {
        if (detailNbtPageOpen) {
            renderNbtSubPage(g, mx, my);
            return;
        }

        Rect2i panel = host.panel();
        int panelX = panel.getX();
        int panelY = panel.getY();
        int panelW = panel.getWidth();
        int panelH = panel.getHeight();
        int contentX = panelX + 4;
        int contentW = panelW - 8;

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0101010);
        g.renderOutline(panelX, panelY, panelW, panelH, COL_ACCENT);

        int backW = 30;
        drawButton(g, panelX + 4, panelY + 4, backW, 12, "<", mx, my, true);
        int titleX = panelX + backW + 8;
        if (!host.shows(Host.Part.ID)) {
            drawSlot(g, titleX, panelY + 1);
            renderSlotIcon(g, titleX, panelY + 1);
            titleX += 22;
        }
        g.drawString(font, host.title(detailEditSlot), titleX, panelY + 6, COL_WHITE, false);

        int clearW = 40;
        int clearXPos = panelX + panelW - clearW - 4;
        if (host.shows(Host.Part.CLEAR))
            drawButton(g, clearXPos, panelY + 4, clearW, 12, tr("gui.logisticsnetworks.filter.detail.clear"), mx, my, true);
        if (host.shows(Host.Part.KIND))
            drawButton(g, clearXPos - 40 - 4, panelY + 4, 40, 12, kindLabel(detailKind), mx, my, true);

        int y = panelY + 20;
        if (host.shows(Host.Part.ID)) {
            y = renderIdRow(g, mx, my, contentX, contentW, y);
        }

        boolean isFluidOrChemical = detailKind != FilterTargetType.ITEMS;
        int labelW = DETAIL_LABEL_W;
        if (host.shows(Host.Part.BATCH)) {
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
        }

        if (host.shows(Host.Part.STOCK)) {
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
        }

        if (!isFluidOrChemical) {
            renderNbtRow(g, mx, my, contentX, y);
            y += DETAIL_SECTION_H;
            renderSlotsRow(g, mx, my, contentX, contentW, y);
            y += DETAIL_SECTION_H;
        }
        renderSideRow(g, mx, my, contentX, contentW, y);

        if (host.shows(Host.Part.ID) && host.shows(Host.Part.TAG_PICKER) && detailTagButtonArea().contains(mx, my)) {
            g.renderTooltip(font, Component.translatable("gui.logisticsnetworks.filter.tag_picker.open"), mx, my);
        }
    }

    private int renderIdRow(GuiGraphics g, int mx, int my, int contentX, int contentW, int y) {
        int slotX = contentX;
        int slotY = y;
        drawSlot(g, slotX, slotY);
        renderSlotIcon(g, slotX, slotY);

        int idFieldX = slotX + 22;
        int idFieldW = contentW - 40;
        detailIdInputBox.setX(idFieldX);
        detailIdInputBox.setY(slotY + 2);
        detailIdInputBox.setWidth(idFieldW);
        detailIdInputBox.render(g, mx, my, 0);
        if (host.shows(Host.Part.TAG_PICKER)) {
            Rect2i tagButton = detailTagButtonArea();
            drawButton(g, tagButton.getX(), tagButton.getY(), tagButton.getWidth(), tagButton.getHeight(),
                    "T", mx, my, true);
        }

        y = slotY + 22;

        String idVal = detailIdInputBox.getValue().trim();
        boolean isTagInput = idVal.startsWith("#");
        List<String> dropdownList = isTagInput ? detailTagFilteredList : detailItemFilteredList;
        if (detailIdInputBox.isFocused() && !dropdownList.isEmpty()) {
            int dropRows = Math.min(DROPDOWN_ROWS, dropdownList.size());
            int dropH = dropRows * LIST_ROW_H;
            int dropX = idFieldX;
            int dropW = idFieldW;
            boolean scrollable = dropdownList.size() > DROPDOWN_ROWS;
            int rowW = scrollable ? dropW - SUBMODE_SCROLLBAR_W - SUBMODE_SCROLLBAR_GAP : dropW;

            g.fill(dropX, y, dropX + dropW, y + dropH, 0xF0101010);
            g.renderOutline(dropX, y, dropW, dropH, COL_BORDER);

            String currentTag = host.tag(detailEditSlot);
            int maxScroll = Math.max(0, dropdownList.size() - DROPDOWN_ROWS);
            detailTagScrollOffset = Mth.clamp(detailTagScrollOffset, 0, maxScroll);
            int startIdx = detailTagScrollOffset;
            int endIdx = Math.min(startIdx + DROPDOWN_ROWS, dropdownList.size());

            for (int i = startIdx; i < endIdx; i++) {
                int rowY = y + (i - startIdx) * LIST_ROW_H;
                String entry = dropdownList.get(i);
                boolean selected = isTagInput && Objects.equals(entry, currentTag);
                boolean hovered = mx >= dropX && mx < dropX + rowW
                        && my >= rowY && my < rowY + LIST_ROW_H;

                if (selected)
                    g.fill(dropX + 1, rowY, dropX + rowW - 1, rowY + LIST_ROW_H, COL_SELECTED);
                else if (hovered)
                    g.fill(dropX + 1, rowY, dropX + rowW - 1, rowY + LIST_ROW_H, COL_HOVER);

                String displayText = isTagInput ? "#" + entry : entry;
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
        return y;
    }

    private void renderSlotIcon(GuiGraphics g, int slotX, int slotY) {
        FilterTargetType detailTargetType = detailKind;
        boolean isFluidOrChemical = detailTargetType != FilterTargetType.ITEMS;
        String idVal = detailIdInputBox.getValue().trim();
        if (idVal.startsWith("#")) {
            String tagStr = idVal.substring(1).trim();
            ResourceLocation tagId = ResourceLocation.tryParse(tagStr);
            if (tagId != null) {
                g.renderOutline(slotX, slotY, 18, 18, DETAIL_TAG_COLOR);
                if (detailTargetType == FilterTargetType.FLUIDS) {
                    TagKey<Fluid> fluidTagKey = TagKey.create(Registries.FLUID, tagId);
                    var list = new ArrayList<Fluid>();
                    for (Fluid fluid : BuiltInRegistries.FLUID) {
                        if (fluid.builtInRegistryHolder().is(fluidTagKey)) {
                            list.add(fluid);
                        }
                    }
                    if (!list.isEmpty()) {
                        int idx = (int) ((System.currentTimeMillis() / 1000) % list.size());
                        FilterResourceRenderer.renderFluid(g,
                                new FluidStack(list.get(idx), 1000), slotX + 1, slotY + 1);
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
                renderUnknown(g, slotX, slotY);
            }
        } else if (!idVal.isEmpty()) {
            if (detailTargetType == FilterTargetType.FLUIDS) {
                ResourceLocation fluidId = ResourceLocation.tryParse(idVal);
                if (fluidId != null && BuiltInRegistries.FLUID.containsKey(fluidId)) {
                    FilterResourceRenderer.renderFluid(g,
                            new FluidStack(BuiltInRegistries.FLUID.get(fluidId), 1000), slotX + 1, slotY + 1);
                } else {
                    renderUnknown(g, slotX, slotY);
                }
            } else if (detailTargetType == FilterTargetType.CHEMICALS) {
                if (MekanismCompat.isValidChemicalId(idVal)) {
                    FilterResourceRenderer.renderChemical(g, idVal, slotX + 1, slotY + 1);
                } else {
                    renderUnknown(g, slotX, slotY);
                }
            } else {
                ResourceLocation itemId = ResourceLocation.tryParse(idVal);
                if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
                    Item item = BuiltInRegistries.ITEM.get(itemId);
                    if (item != Items.AIR) {
                        g.renderItem(new ItemStack(item), slotX + 1, slotY + 1);
                    } else {
                        renderUnknown(g, slotX, slotY);
                    }
                } else {
                    ItemStack itemInSlot = host.sample(detailEditSlot);
                    if (!itemInSlot.isEmpty()) {
                        g.renderItem(itemInSlot, slotX + 1, slotY + 1);
                    } else {
                        renderUnknown(g, slotX, slotY);
                    }
                }
            }
        } else if (isFluidOrChemical && host.isNbtOnly(detailEditSlot)) {
            g.renderOutline(slotX, slotY, 18, 18, 0xFF44BB44);
            host.renderAny(g, slotX + 1, slotY + 1);
        } else if (host.isNbtOnly(detailEditSlot)) {
            boolean hasNbt = host.hasNbt(detailEditSlot);
            int ic = hasNbt ? DETAIL_NBT_COLOR : DETAIL_DUR_COLOR;
            String il = hasNbt ? "N" : "D";
            g.renderOutline(slotX, slotY, 18, 18, ic);
            int nx = slotX + (18 - font.width(il)) / 2;
            g.drawString(font, il, nx, slotY + 5, ic, true);
        } else {
            renderUnknown(g, slotX, slotY);
        }
    }

    private void renderUnknown(GuiGraphics g, int slotX, int slotY) {
        g.renderOutline(slotX, slotY, 18, 18, COL_ACCENT);
        int qx = slotX + (18 - font.width("?")) / 2;
        g.drawString(font, "?", qx, slotY + 5, COL_ACCENT, true);
    }

    private void renderNbtRow(GuiGraphics g, int mx, int my, int contentX, int y) {
        g.drawString(font, tr("gui.logisticsnetworks.filter.detail.nbt"), contentX, y + 3, DETAIL_NBT_COLOR, false);
        boolean strictNbt = host.nbtStrict(detailEditSlot);
        int strictToggleX = contentX + DETAIL_LABEL_W;
        String strictLabel = tr("gui.logisticsnetworks.filter.detail.nbt.strict");
        int strictToggleW = 14 + font.width(strictLabel);
        drawToggle(g, strictToggleX, y + 2, strictNbt);
        g.drawString(font, strictLabel, strictToggleX + 14, y + 3,
                strictNbt ? COL_ACCENT : COL_GRAY, false);

        int nbtBtnX = strictToggleX + strictToggleW + 8;
        String nbtBtnLabel = tr("gui.logisticsnetworks.filter.detail.nbt.configure");
        int nbtBtnW = Math.max(34, font.width(nbtBtnLabel) + 8);
        drawButton(g, nbtBtnX, y, nbtBtnW, 14, nbtBtnLabel, mx, my, !strictNbt);

        if (isHovering(strictToggleX, y, strictToggleW, 14, mx, my)) {
            g.renderTooltip(font, Component.translatable(strictNbt
                    ? "gui.logisticsnetworks.filter.detail.nbt.strict.on"
                    : "gui.logisticsnetworks.filter.detail.nbt.strict.off"), mx, my);
        }

        if (strictNbt && isHovering(nbtBtnX, y, nbtBtnW, 14, mx, my)) {
            g.renderTooltip(font,
                    Component.translatable("gui.logisticsnetworks.filter.detail.nbt.edit.disabled"), mx, my);
        } else if (isHovering(nbtBtnX, y, nbtBtnW, 14, mx, my)) {
            List<NbtCriterion> hoverRules = host.nbtRules(detailEditSlot);
            if (!hoverRules.isEmpty()) {
                List<Component> tipLines = new ArrayList<>();
                for (NbtCriterion r : hoverRules) {
                    tipLines.add(Component.literal(abbreviateNbtPath(r.path().toString()) + " " + r.operator() + " " + r.value()));
                }
                g.renderTooltip(font, tipLines, Optional.empty(), mx, my);
            } else {
                String nbtRaw = host.nbtRaw(detailEditSlot);
                if (nbtRaw != null) {
                    String preview = nbtRaw.length() > 60 ? nbtRaw.substring(0, 60) + "..." : nbtRaw;
                    g.renderTooltip(font, Component.literal("SNBT: " + preview), mx, my);
                }
            }
        }
    }

    private void renderSlotsRow(GuiGraphics g, int mx, int my, int contentX, int contentW, int y) {
        g.drawString(font, tr("gui.logisticsnetworks.filter.detail.slots"), contentX, y + 3, DETAIL_SLOT_COLOR, false);
        int slotInputX = contentX + DETAIL_LABEL_W;
        int slotInputW = contentW - DETAIL_LABEL_W;
        Component toggle = host.slotsToggle(detailEditSlot);
        if (toggle != null) {
            slotInputW -= SLOTS_TOGGLE_W + 4;
            drawButton(g, slotInputX + slotInputW + 4, y, SLOTS_TOGGLE_W, 14, toggle.getString(), mx, my, true);
        }
        detailSlotMappingInputBox.setX(slotInputX);
        detailSlotMappingInputBox.setY(y);
        detailSlotMappingInputBox.setWidth(slotInputW);
        detailSlotMappingInputBox.setHint(Component.empty());
        detailSlotMappingInputBox.render(g, mx, my, 0);
    }

    private void renderSideRow(GuiGraphics g, int mx, int my, int contentX, int contentW, int y) {
        g.drawString(font, tr("gui.logisticsnetworks.filter.detail.side"), contentX, y + 3, DETAIL_SIDE_COLOR, false);
        int sideX = contentX + DETAIL_LABEL_W;
        int sideW = contentW - DETAIL_LABEL_W;
        IoSide side = host.side(detailEditSlot);
        String label = side == null
                ? tr("gui.logisticsnetworks.filter.detail.side.node")
                : tr("gui.logisticsnetworks.direction." + side.key());
        drawButton(g, sideX, y, sideW, 14, label, mx, my, true);
        if (isHovering(sideX, y, sideW, 14, mx, my)) {
            g.renderTooltip(font, font.split(
                    Component.translatable(host.sideTooltip()), 180), mx, my);
        }
    }

    // Null, then every IoSide
    private void cycleSide(int action) {
        IoSide side = host.side(detailEditSlot);
        int count = IoSide.values().length + 1;
        int at = Math.floorMod((side == null ? 0 : side.ordinal() + 1) + (action == 1 ? -1 : 1), count);
        host.setSide(detailEditSlot, at == 0 ? null : IoSide.values()[at - 1]);
    }

    private Rect2i detailTagButtonArea() {
        Rect2i panel = host.panel();
        return new Rect2i(panel.getX() + panel.getWidth() - 18, panel.getY() + 22, 14, 14);
    }

    private void openDetailTagPicker() {
        flushDetailPageInputs();
        detailIdInputBox.setFocused(false);
        detailBatchInputBox.setFocused(false);
        detailStockInputBox.setFocused(false);
        detailSlotMappingInputBox.setFocused(false);
        String value = detailIdInputBox.getValue().trim();
        ResourceLocation current = value.isEmpty() || value.startsWith("#") ? null : ResourceLocation.tryParse(value);
        host.openTagPicker(detailEditSlot, detailKind, current, this::applyDetailTag);
    }

    private void applyDetailTag(String tag) {
        host.setTag(detailEditSlot, tag, detailKind);
        detailIdInputBox.setValue("#" + tag);
        rebuildIdFilteredList();
    }

    private void renderNbtSubPage(GuiGraphics g, int mx, int my) {
        Rect2i panel = host.panel();
        int panelX = panel.getX();
        int panelY = panel.getY();
        int panelW = panel.getWidth();
        int panelH = panel.getHeight();
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
        List<String> pathTexts = detailCachedNbtEntries.stream()
                .map(entry -> entry.path().toString())
                .toList();
        Map<String, List<Integer>> groupedEntries = new HashMap<>();
        for (int i = 0; i < pathTexts.size(); i++) {
            String category = nbtCategory(pathTexts.get(i));
            groupedEntries.computeIfAbsent(category, ignored -> new ArrayList<>()).add(i);
        }

        List<String> categories = new ArrayList<>(groupedEntries.keySet());
        categories.sort(EntryDetailPage::compareNbtCategories);
        for (String category : categories) {
            List<Integer> entries = groupedEntries.get(category);
            entries.sort(Comparator.comparing(pathTexts::get));
            nbtRows.add(new NbtRow(true, category, -1, category));
            for (int entryIdx : entries) {
                nbtRows.add(new NbtRow(false, nbtDisplayPath(pathTexts.get(entryIdx), category), entryIdx, category));
            }
        }
    }

    private static String nbtCategory(String path) {
        String componentPath = nbtComponentPath(path);
        if (componentPath.equals("minecraft:enchanted")
                || componentPath.equals("minecraft:enchantments")
                || componentPath.startsWith("minecraft:enchantments.")
                || componentPath.equals("minecraft:stored_enchantments")
                || componentPath.startsWith("minecraft:stored_enchantments.")) {
            return "Enchantments";
        }

        int separator = componentPath.indexOf(':');
        if (separator < 1) return "Other";
        String namespace = componentPath.substring(0, separator);
        return namespace.equals("minecraft") ? "Minecraft" : namespace;
    }

    private static int compareNbtCategories(String left, String right) {
        if (left.equals(right)) return 0;
        if (left.equals("Enchantments")) return -1;
        if (right.equals("Enchantments")) return 1;
        if (left.equals("Minecraft")) return -1;
        if (right.equals("Minecraft")) return 1;
        if (left.equals("Other")) return 1;
        if (right.equals("Other")) return -1;
        return left.compareTo(right);
    }

    private static String nbtDisplayPath(String path, String category) {
        String componentPath = nbtComponentPath(path);
        if (category.equals("Enchantments")) {
            if (componentPath.equals("minecraft:enchanted")) return "enchanted";
            if (componentPath.equals("minecraft:enchantments")) return "enchantments";
            if (componentPath.equals("minecraft:stored_enchantments")) return "stored";
            if (componentPath.startsWith("minecraft:enchantments.")) {
                String suffix = componentPath.substring("minecraft:enchantments.".length());
                return suffix.startsWith("levels.") ? suffix.substring("levels.".length()) : suffix;
            }
            if (componentPath.startsWith("minecraft:stored_enchantments.")) {
                String suffix = componentPath.substring("minecraft:stored_enchantments.".length());
                if (suffix.startsWith("levels.")) suffix = suffix.substring("levels.".length());
                return "stored > " + suffix;
            }
        }

        int separator = componentPath.indexOf(':');
        return separator >= 0 ? componentPath.substring(separator + 1) : componentPath;
    }

    private static String nbtComponentPath(String path) {
        String prefix = "fluid.components.";
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
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

    private void renderNbtTable(GuiGraphics g, int mx, int my, int tableX, int tableY, int tableW, int tableH) {
        Set<NbtPath> activePaths = detailNbtActiveOps.keySet();
        if (nbtTableEditingRow < 0) detailNbtValueBox.setVisible(false);

        List<NbtRow> visible = getVisibleNbtRows();
        int totalRows = visible.size();
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

            NbtRow row = visible.get(vi);

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
                        hoveredFullText = entry.path() + " = " + entry.valueDisplay();
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

    boolean mouseClicked(double mx, double my, int action) {
        if (detailNbtPageOpen) {
            return handleNbtSubPageClick(mx, my, action);
        }
        if (action == 0 && host.shows(Host.Part.ID) && host.shows(Host.Part.TAG_PICKER)
                && detailTagButtonArea().contains((int) mx, (int) my)) {
            ClickSound.play();
            openDetailTagPicker();
            return true;
        }

        FilterTargetType targetType = detailKind;
        boolean isFluidOrChemical = targetType != FilterTargetType.ITEMS;

        Rect2i panel = host.panel();
        int panelX = panel.getX();
        int panelY = panel.getY();
        int panelW = panel.getWidth();
        int panelH = panel.getHeight();

        if (!isHovering(panelX, panelY, panelW, panelH, (int) mx, (int) my)) {
            return false;
        }

        int backW = 30;
        if (isHovering(panelX + 4, panelY + 4, backW, 12, (int) mx, (int) my)) {
            ClickSound.play();
            close();
            return true;
        }

        int clearW = 40;
        int clearXPos = panelX + panelW - clearW - 4;
        if (host.shows(Host.Part.CLEAR) && isHovering(clearXPos, panelY + 4, clearW, 12, (int) mx, (int) my)) {
            ClickSound.play();
            clearDetailEntry();
            return true;
        }

        if (host.shows(Host.Part.KIND) && isHovering(clearXPos - 40 - 4, panelY + 4, 40, 12, (int) mx, (int) my)) {
            ClickSound.play();
            flushDetailPageInputs();
            detailKind = nextKind(detailKind);
            loadDetailKind(detailEditSlot);
            return true;
        }

        int contentX = panelX + 4;
        int contentW = panelW - 8;
        int y = panelY + 20;
        if (host.shows(Host.Part.ID)) {
            int bottom = handleIdRowClick(mx, my, action, contentX, contentW, y);
            if (bottom < 0) return true;
            y = bottom;
        }

        int labelW = DETAIL_LABEL_W;
        if (host.shows(Host.Part.BATCH)) y += DETAIL_SECTION_H;
        if (host.shows(Host.Part.STOCK)) y += DETAIL_SECTION_H;

        if (!isFluidOrChemical) {
            int nbtY = y;

            int strictToggleX = contentX + labelW;
            String strictLabel = tr("gui.logisticsnetworks.filter.detail.nbt.strict");
            int strictToggleW = 14 + font.width(strictLabel);
            boolean strictNbt = host.nbtStrict(detailEditSlot);
            if (isHovering(strictToggleX, nbtY, strictToggleW, 14, (int) mx, (int) my)) {
                ClickSound.play();
                host.setNbtStrict(detailEditSlot, !strictNbt);
                return true;
            }

            int nbtBtnX = strictToggleX + strictToggleW + 8;
            String nbtBtnLabel = tr("gui.logisticsnetworks.filter.detail.nbt.configure");
            int nbtBtnW = Math.max(34, font.width(nbtBtnLabel) + 8);
            if (!strictNbt && isHovering(nbtBtnX, nbtY, nbtBtnW, 14, (int) mx, (int) my)) {
                ClickSound.play();
                detailNbtPageOpen = true;
                detailNbtScrollOffset = 0;
                detailNbtInputBox.active = true;
                detailNbtInputBox.setFocused(false);
                host.resize(DETAIL_NBT_MIN_WIDTH, DETAIL_MIN_HEIGHT);
                return true;
            }
            y += DETAIL_SECTION_H;

            int toggleX = contentX + contentW - SLOTS_TOGGLE_W;
            if (host.slotsToggle(detailEditSlot) != null
                    && isHovering(toggleX, y, SLOTS_TOGGLE_W, 14, (int) mx, (int) my)) {
                ClickSound.play();
                flushDetailPageInputs();
                host.toggleSlots(detailEditSlot);
                return true;
            }
            y += DETAIL_SECTION_H;
        }

        if (isHovering(contentX + labelW, y, contentW - labelW, 14, (int) mx, (int) my)) {
            ClickSound.play();
            cycleSide(action);
            return true;
        }

        unfocusAllDetailInputs();

        if (detailIdInputBox.isMouseOver(mx, my)) {
            detailIdInputBox.setFocused(true);
            detailIdInputBox.mouseClicked(mx, my, action);
            return true;
        }
        if (detailBatchInputBox.isMouseOver(mx, my)) {
            detailBatchInputBox.setFocused(true);
            detailBatchInputBox.mouseClicked(mx, my, action);
            return true;
        }
        if (detailStockInputBox.isMouseOver(mx, my)) {
            detailStockInputBox.setFocused(true);
            detailStockInputBox.mouseClicked(mx, my, action);
            return true;
        }
        if (!isFluidOrChemical && detailSlotMappingInputBox.isMouseOver(mx, my)) {
            detailSlotMappingInputBox.setFocused(true);
            detailSlotMappingInputBox.mouseClicked(mx, my, action);
            return true;
        }
        return true;
    }

    // Returns -1 when consumed
    private int handleIdRowClick(double mx, double my, int action, int contentX, int contentW, int itemSlotY) {
        FilterTargetType targetType = detailKind;
        boolean isFluidOrChemical = targetType != FilterTargetType.ITEMS;
        int slotX = contentX;
        int idFieldX = slotX + 22;
        int idFieldW = contentW - 40;

        if (isHovering(slotX, itemSlotY, 18, 18, (int) mx, (int) my)) {
            String currentId = detailIdInputBox.getValue().trim();
            if (!currentId.isEmpty() && !currentId.startsWith("#")) {
                ClickSound.play();
                if (isFluidOrChemical || !host.hasConfig(detailEditSlot)) {
                    host.clearEntry(detailEditSlot);
                } else {
                    host.clearItem(detailEditSlot);
                }
                detailIdInputBox.setValue("");
                return -1;
            }
        }

        int dropY = itemSlotY + 22;
        String idVal = detailIdInputBox.getValue().trim();
        boolean isTagInput = idVal.startsWith("#");
        List<String> dropdownList = isTagInput ? detailTagFilteredList : detailItemFilteredList;
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
                        ClickSound.play();
                        selectDropdownEntry(dropdownList.get(i), isTagInput, targetType);
                        detailIdInputBox.setFocused(false);
                        return -1;
                    }
                }
                return -1;
            }
            dropY += dropH + 2;
        }
        return dropY;
    }

    private void selectDropdownEntry(String selected, boolean isTagInput, FilterTargetType targetType) {
        if (isTagInput) {
            host.setTag(detailEditSlot, selected, detailKind);
            detailIdInputBox.setValue("#" + selected);
        } else if (targetType == FilterTargetType.FLUIDS) {
            ResourceLocation fluidId = ResourceLocation.tryParse(selected);
            if (fluidId != null && BuiltInRegistries.FLUID.containsKey(fluidId)) {
                host.setFluid(detailEditSlot, new FluidStack(BuiltInRegistries.FLUID.get(fluidId), 1000));
                detailIdInputBox.setValue(selected);
            }
        } else if (targetType == FilterTargetType.CHEMICALS) {
            if (MekanismCompat.isValidChemicalId(selected)) {
                host.setChemical(detailEditSlot, selected);
                detailIdInputBox.setValue(selected);
            }
        } else {
            ResourceLocation itemId = ResourceLocation.tryParse(selected);
            if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
                Item item = BuiltInRegistries.ITEM.get(itemId);
                if (item != Items.AIR) {
                    host.setItem(detailEditSlot, new ItemStack(item));
                    detailIdInputBox.setValue(selected);
                }
            }
        }
    }

    private boolean handleNbtSubPageClick(double mx, double my, int action) {
        Rect2i panel = host.panel();
        int panelX = panel.getX();
        int panelY = panel.getY();
        int panelW = panel.getWidth();
        int panelH = panel.getHeight();
        int contentX = panelX + 4;
        int contentW = panelW - 8;

        if (!isHovering(panelX, panelY, panelW, panelH, (int) mx, (int) my)) {
            return false;
        }

        if (detailNbtRawMode && detailNbtInputBox.isFocused()
                && !detailNbtInputBox.isMouseOver(mx, my)) {
            flushNbtSubPage();
            detailNbtInputBox.setFocused(false);
        }

        int backW = 50;
        if (isHovering(panelX + 4, panelY + 4, backW, 12, (int) mx, (int) my)) {
            ClickSound.play();
            closeNbtSubPage();
            return true;
        }

        int clearW = 40;
        int clearXPos = panelX + panelW - clearW - 4;
        if (isHovering(clearXPos, panelY + 4, clearW, 12, (int) mx, (int) my)) {
            ClickSound.play();
            host.clearNbt(detailEditSlot);
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
            ClickSound.play();
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
            if (detailNbtInputBox.active && detailNbtInputBox.isMouseOver(mx, my)) {
                if (action == 0) {
                    detailNbtInputBox.setFocused(true);
                }
                detailNbtInputBox.mouseClicked(mx, my, action);
                return true;
            }

            int nbtH = panelY + panelH - y - 20;
            int doneW = 50;
            int doneX = panelX + (panelW - doneW) / 2;
            int doneY = y + Math.max(20, nbtH) + 4;
            if (isHovering(doneX, doneY, doneW, 14, (int) mx, (int) my)) {
                ClickSound.play();
                closeNbtSubPage();
                return true;
            }
        } else {
            return handleNbtTableClick(mx, my, action, contentX, y, contentW, panelY + panelH - y - 4);
        }

        return true;
    }

    private boolean handleNbtTableClick(double mx, double my, int action,
                                         int tableX, int tableY, int tableW, int tableH) {
        Set<NbtPath> activePaths = detailNbtActiveOps.keySet();
        List<NbtRow> visible = getVisibleNbtRows();
        int totalRows = visible.size();
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

            NbtRow row = visible.get(vi);

            if (row.heading()) {
                ClickSound.play();
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
                ClickSound.play();
                if (active) {
                    int ruleIdx = findActiveRuleIndex(host.nbtRules(detailEditSlot), entry.path());
                    if (ruleIdx >= 0) {
                        host.removeNbtRule(detailEditSlot, ruleIdx);
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
                ClickSound.play();
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
                detailNbtValueBox.mouseClicked(mx, my, action);
                return true;
            }

            return true;
        }

        if (nbtTableEditingRow >= 0) {
            commitDetailNbtValueEdit();
        }
        return true;
    }

    private void cycleDetailNbtOp(NbtPath path) {
        String currentOp = detailNbtActiveOps.getOrDefault(path, "=");
        String nextOp = FilterItemData.nextNbtOperator(currentOp);
        detailNbtActiveOps.put(path, nextOp);
        detailNbtOp = nextOp;

        int ruleIdx = findActiveRuleIndex(host.nbtRules(detailEditSlot), path);
        if (ruleIdx >= 0) {
            String savedVal = formatNbtValue(host.nbtRules(detailEditSlot).get(ruleIdx).value().toString());
            host.removeNbtRule(detailEditSlot, ruleIdx);
            host.addNbtRule(detailEditSlot, path, nextOp, savedVal);
            List<NbtCriterion> updatedRules = host.nbtRules(detailEditSlot);
            int newIdx = findActiveRuleIndex(updatedRules, path);
            if (newIdx >= 0) {
                host.setNbtRuleValue(detailEditSlot, newIdx, savedVal);
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
        if (host.nbtStrict(detailEditSlot)) {
            host.setNbtStrict(detailEditSlot, false);
        }
        host.addNbtRule(detailEditSlot, entry.path(), opSymbol, fallbackValue);

        if (!valueOverride.isEmpty() && !valueOverride.equals(entry.valueDisplay())) {
            List<NbtCriterion> rules = host.nbtRules(detailEditSlot);
            int ruleIdx = findActiveRuleIndex(rules, entry.path());
            if (ruleIdx >= 0) {
                host.setNbtRuleValue(detailEditSlot, ruleIdx, valueOverride);
            }
            detailCachedNbtEntries.set(detailNbtSelectedIdx,
                    new NbtFilterData.NbtEntry(entry.path(), valueOverride));
            buildNbtRows();
        }
        detailNbtActiveOps.put(entry.path(), opSymbol);
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
                host.setTag(detailEditSlot, normalized, detailKind);
                detailIdInputBox.setValue("#" + normalized);
            }
        } else {
            ResourceLocation itemId = ResourceLocation.tryParse(val);
            if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
                Item item = BuiltInRegistries.ITEM.get(itemId);
                if (item != Items.AIR) {
                    host.setItem(detailEditSlot, new ItemStack(item));
                    detailIdInputBox.setValue(itemId.toString());
                }
            }
        }
    }

    private void flushNbtSubPage() {
        if (detailEditSlot < 0) return;
        if (detailNbtRawMode) {
            String nbtVal = detailNbtInputBox.getValue().replace("\n", " ").trim();
            String existingRaw = host.nbtRaw(detailEditSlot);
            if (!nbtVal.isEmpty() && !nbtVal.equals(existingRaw)) {
                if (host.nbtStrict(detailEditSlot)) {
                    host.setNbtStrict(detailEditSlot, false);
                }
                host.setNbtRaw(detailEditSlot, nbtVal);
            } else if (nbtVal.isEmpty() && existingRaw != null) {
                host.clearNbt(detailEditSlot);
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
        host.resize(0, DETAIL_MIN_HEIGHT);
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

    boolean keyPressed(int key, int scan, int modifiers) {
        if (detailNbtPageOpen) {
            if (detailNbtInputBox.isFocused()) {
                if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                    flushNbtSubPage();
                    detailNbtInputBox.setFocused(false);
                    return true;
                }
                detailNbtInputBox.keyPressed(key, scan, modifiers);
                return true;
            }
            if (detailNbtValueBox.isFocused()) {
                if (key == 257 || key == 256) {
                    commitDetailNbtValueEdit();
                    return true;
                }
                detailNbtValueBox.keyPressed(key, scan, modifiers);
                return true;
            }
            return false;
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

        return false;
    }

    boolean charTyped(char c, int modifiers) {
        if (detailIdInputBox.isFocused())
            return detailIdInputBox.charTyped(c, modifiers);
        if (detailBatchInputBox.isFocused())
            return detailBatchInputBox.charTyped(c, modifiers);
        if (detailStockInputBox.isFocused())
            return detailStockInputBox.charTyped(c, modifiers);
        if (detailSlotMappingInputBox.isFocused())
            return detailSlotMappingInputBox.charTyped(c, modifiers);
        if (detailNbtInputBox.isFocused())
            return detailNbtInputBox.charTyped(c, modifiers);
        if (detailNbtValueBox.isFocused())
            return detailNbtValueBox.charTyped(c, modifiers);
        return true;
    }

    boolean mouseScrolled(double mx, double my, double delta) {
        if (detailNbtPageOpen) {
            if (detailNbtRawMode && detailNbtInputBox.active) {
                return detailNbtInputBox.mouseScrolled(mx, my, 0, delta);
            }
            Rect2i panel = host.panel();
            int panelY = panel.getY();
            int panelH = panel.getHeight();
            int y = panelY + 36;
            int listH = panelY + panelH - y - 4;
            int totalRows = getVisibleNbtRows().size();
            int visibleRows = Math.max(1, listH / NBT_ROW_H);
            int maxScroll = Math.max(0, totalRows - visibleRows);
            detailNbtScrollOffset = Mth.clamp(detailNbtScrollOffset - (int) delta, 0, maxScroll);
            return true;
        }

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
            scrollBoxAmount(detailBatchInputBox, delta);
            return true;
        }
        if (detailStockInputBox.isMouseOver(mx, my) || detailStockInputBox.isFocused()) {
            scrollBoxAmount(detailStockInputBox, delta);
            return true;
        }

        return true;
    }

    private void scrollBoxAmount(EditBox box, double delta) {
        String str = box.getValue().trim();
        int current = 0;
        if (!str.isEmpty()) {
            try { current = ArithmeticExpression.evaluate(str); } catch (NumberFormatException ignored) {}
        }
        int scrollDelta = computeScrollDelta(delta, detailKind);
        int next = Math.max(0, current + scrollDelta);
        box.setValue(String.valueOf(next));
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

        boolean hasNbtConfig = host.hasConfig(slot);

        if (hasNbtConfig) {
            host.clearItem(slot);
        } else {
            host.clearEntry(slot);
        }

        detailIdInputBox.setValue("");
        detailBatchInputBox.setValue("");
        detailStockInputBox.setValue("");
        detailSlotMappingInputBox.setValue("");
        if (!hasNbtConfig) {
            detailNbtInputBox.setValue("");
            detailNbtValueBox.setValue("");
            detailNbtSelectedIdx = -1;
            detailNbtOp = "=";
            detailNbtActiveOps.clear();
            nbtTableEditingRow = -1;
        }
    }

    private String kindLabel(FilterTargetType kind) {
        return tr("gui.logisticsnetworks.filter.kind." + kind.getSerializedName());
    }

    private String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private boolean isHovering(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
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

    private void drawToggle(GuiGraphics g, int x, int y, boolean on) {
        int size = 10;
        g.fill(x, y, x + size, y + size, on ? 0xFF225522 : 0xFF332222);
        g.renderOutline(x, y, size, size, on ? COL_ACCENT : 0xFF884444);
        if (on) {
            g.fill(x + 3, y + 3, x + 7, y + 7, COL_ACCENT);
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
