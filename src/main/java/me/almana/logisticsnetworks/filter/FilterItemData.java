package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.component.FilterSettings;
import me.almana.logisticsnetworks.component.FilterSettingsData;
import me.almana.logisticsnetworks.component.GeneralFilterConfig;
import me.almana.logisticsnetworks.component.GeneralFilterEntry;
import me.almana.logisticsnetworks.component.LegacyComponentMigration;
import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import me.almana.logisticsnetworks.component.StackSnapshot;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.item.BaseFilterItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public final class FilterItemData {

    private static final String KEY_IS_BLACKLIST = "blacklist";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_SLOT = "slot";
    private static final String KEY_ITEM_TAG = "item";
    private static final String KEY_FLUID_ID = "fluid";
    private static final String KEY_CHEMICAL_ID = "chemical";
    private static final String KEY_AMOUNT = "amount";
    private static final String KEY_BATCH = "batch";
    private static final String KEY_STOCK = "stock";
    private static final String KEY_TAG = "tag";
    private static final String KEY_NBT_PATH = "nbt_path";
    private static final String KEY_NBT_VALUE = "nbt_val";
    private static final String KEY_NBT_OP = "nbt_op";
    private static final String KEY_DUR_OP = "dur_op";
    private static final String KEY_DUR_VAL = "dur_val";
    private static final String KEY_NBT_RAW = "nbt_raw";
    private static final String KEY_SLOT_MAPPING = "slot_map";
    private static final String KEY_SLOT_MAPPING_EXPR = "slot_map_expr";
    private static final String KEY_ENCHANTED = "enchanted";
    private static final String KEY_NBT_RULES = "nbt_rules";
    private static final String KEY_NBT_MATCH_ANY = "nbt_match_any";
    private static final String KEY_NBT_STRICT = "nbt_strict";
    private static final String KEY_RULE_P = "p";
    private static final String KEY_RULE_O = "o";
    private static final String KEY_RULE_V = "v";
    private static final int MAX_NBT_RULES_PER_SLOT = 8;
    private static final String NBT_OP_EQUALS = "=";

    public static final class ReadCache {
        private final IdentityHashMap<ItemStack, CachedItemView> itemViews = new IdentityHashMap<>();
        final IdentityHashMap<ItemStack, ModFilterData.CachedModView> modViews = new IdentityHashMap<>();
        final IdentityHashMap<ItemStack, NameFilterData.CachedNameView> nameViews = new IdentityHashMap<>();
        final Map<String, NameFilterData.ValidationResult> namePatterns = new HashMap<>();

        private ReadCache() {
        }
    }

    private record CachedItemView(@Nullable FilterSettings settings, @Nullable GeneralFilterConfig config,
            @Nullable CustomData customData, ItemFilterView view) {
    }

    private record ItemFilterSlot(
            int slotIndex,
            @Nullable String tag,
            @Nullable Item item,
            @Nullable DataComponentMap expectedComponents,
            @Nullable String chemicalId,
            @Nullable FluidStack fluidEntry,
            int batch,
            int stock,
            @Nullable NbtPath nbtPath,
            @Nullable Tag nbtValue,
            @Nullable String nbtOp,
            @Nullable CompoundTag rawNbt,
            boolean invalidRawNbt,
            @Nullable String durOp,
            int durVal,
            boolean hasNbt,
            boolean nbtOnly,
            boolean nbtStrict,
            List<SlotNbtRule> nbtRules,
            boolean nbtMatchAny,
            @Nullable int[] slotMapping,
            boolean slotOnly,
            @Nullable Boolean enchanted) {
    }

    private record ItemFilterView(
            boolean blacklist,
            boolean hasItemEntries,
            boolean hasFluidEntries,
            boolean hasChemicalEntries,
            boolean hasTagEntries,
            boolean hasNbtEntries,
            boolean hasAmountEntries,
            boolean hasSlotOnlyEntries,
            ItemFilterSlot[] entriesBySlot) {
    }

    public record SlotNbtRule(NbtPath path, String operator, Tag value) {
        public String displayText() {
            String val = value != null ? value.toString() : "";
            return path + " " + operator + " " + val;
        }
    }

    public record ItemStock(int amount, @Nullable int[] slots) {
    }

    private FilterItemData() {
    }

    public static ReadCache createReadCache() {
        return new ReadCache();
    }

    public static boolean isFilterItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BaseFilterItem;
    }

    public static int getCapacity(ItemStack stack) {
        if (stack.getItem() instanceof BaseFilterItem item) {
            return item.getSlotCount();
        }
        return 0;
    }

    public static boolean isBlacklist(ItemStack stack) {
        if (!isFilterItem(stack))
            return false;
        LegacyComponentMigration.migrateGeneralFilter(stack, null);
        return FilterSettingsData.get(stack).blacklist();
    }

    public static boolean isBlacklist(ItemStack stack, @Nullable ReadCache readCache) {
        if (!isFilterItem(stack))
            return false;
        return getItemFilterView(stack, readCache).blacklist();
    }

    public static void setBlacklist(ItemStack stack, boolean isBlacklist) {
        if (!isFilterItem(stack))
            return;

        LegacyComponentMigration.migrateGeneralFilter(stack, null);
        FilterSettingsData.setBlacklist(stack, isBlacklist);
    }

    public static FilterTargetType getTargetType(ItemStack stack) {
        if (!isFilterItem(stack))
            return FilterTargetType.ITEMS;
        LegacyComponentMigration.migrateGeneralFilter(stack, null);
        return FilterSettingsData.get(stack).target();
    }

    public static void setTargetType(ItemStack stack, FilterTargetType type) {
        if (!isFilterItem(stack))
            return;
        LegacyComponentMigration.migrateGeneralFilter(stack, null);
        FilterSettingsData.setTarget(stack, type);
    }

    public static ItemStack getEntry(ItemStack stack, int slot, @Nullable HolderLookup.Provider provider) {
        GeneralFilterEntry entry = entry(stack, slot);
        return entry == null || entry.item() == null ? ItemStack.EMPTY : entry.item().toStack();
    }

    public static boolean setEntry(ItemStack stack, int slot, ItemStack value, @Nullable HolderLookup.Provider provider) {
        return edit(stack, slot, entry -> value.isEmpty()
                ? GeneralFilterEntry.empty(slot)
                : entry.withItem(StackSnapshot.of(value.copyWithCount(1))));
    }

    public static boolean addItem(ItemStack filter, ItemStack item, @Nullable HolderLookup.Provider provider) {
        if (!isFilterItem(filter) || item.isEmpty())
            return false;
        ItemStack entry = item.copyWithCount(1);
        int cap = getCapacity(filter);
        for (int i = 0; i < cap; i++) {
            if (ItemStack.isSameItemSameComponents(getEntry(filter, i, provider), entry))
                return false;
        }
        for (int i = 0; i < cap; i++) {
            if (isEntrySlotAvailable(filter, i)) {
                return setEntry(filter, i, entry, provider);
            }
        }
        return false;
    }

    public static void clearEntryItem(ItemStack stack, int slot) {
        edit(stack, slot, entry -> entry.withItem(null));
    }

    public static FluidStack getFluidEntry(ItemStack stack, int slot) {
        GeneralFilterEntry entry = entry(stack, slot);
        FluidStack fluid = entry == null ? null : resolveFluidEntry(nonEmpty(entry.fluidId()));
        return fluid == null ? FluidStack.EMPTY : fluid;
    }

    public static boolean setFluidEntry(ItemStack stack, int slot, FluidStack fluid) {
        String id = fluid.isEmpty() ? null : BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString();
        return edit(stack, slot, entry -> resourceEntry(entry, id, null));
    }

    public static boolean addFluid(ItemStack filter, FluidStack fluid) {
        if (!isFilterItem(filter) || fluid == null || fluid.isEmpty()) {
            return false;
        }
        int cap = getCapacity(filter);
        for (int i = 0; i < cap; i++) {
            FluidStack existing = getFluidEntry(filter, i);
            if (!existing.isEmpty() && FluidStack.isSameFluidSameComponents(existing, fluid)) {
                return false;
            }
        }
        for (int i = 0; i < cap; i++) {
            if (isEntrySlotAvailable(filter, i)) {
                return setFluidEntry(filter, i, fluid);
            }
        }
        return false;
    }

    public static boolean hasAnyEntries(ItemStack stack) {
        return !entries(stack).isEmpty();
    }

    public static boolean hasAnyItemMatchEntries(ItemStack stack, @Nullable ReadCache readCache) {
        if (!isFilterItem(stack))
            return false;
        ItemFilterView view = getItemFilterView(stack, readCache);
        return view.hasItemEntries() || view.hasTagEntries() || view.hasSlotOnlyEntries();
    }

    public static boolean hasAnyFluidEntries(ItemStack stack, @Nullable ReadCache readCache) {
        if (!isFilterItem(stack))
            return false;
        return getItemFilterView(stack, readCache).hasFluidEntries();
    }

    private static boolean hasEntryType(ItemStack stack, String key) {
        if (!isFilterItem(stack))
            return false;
        ListTag list = getRoot(stack).getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (t instanceof CompoundTag c && c.contains(key))
                return true;
        }
        return false;
    }

    public static int getEntryCount(ItemStack stack) {
        return entries(stack).size();
    }

    public static boolean containsItem(ItemStack filter, ItemStack candidate, HolderLookup.Provider provider) {
        int cap = getCapacity(filter);
        for (int i = 0; i < cap; i++) {
            ItemStack entry = getEntry(filter, i, provider);
            if (!entry.isEmpty() && ItemStack.isSameItem(entry, candidate)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsFluid(ItemStack filter, FluidStack candidate) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return false;

        int cap = getCapacity(filter);
        for (int i = 0; i < cap; i++) {
            FluidStack entry = getFluidEntry(filter, i);
            if (!entry.isEmpty() && FluidStack.isSameFluidSameComponents(entry, candidate)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static String getChemicalEntry(ItemStack stack, int slot) {
        GeneralFilterEntry entry = entry(stack, slot);
        return entry == null ? null : nonEmpty(entry.chemicalId());
    }

    public static boolean setChemicalEntry(ItemStack stack, int slot, @Nullable String chemicalId) {
        return edit(stack, slot, entry -> resourceEntry(entry, null, nonEmpty(chemicalId)));
    }

    private static GeneralFilterEntry resourceEntry(GeneralFilterEntry current, @Nullable String fluidId,
            @Nullable String chemicalId) {
        if (fluidId == null && chemicalId == null)
            return GeneralFilterEntry.empty(current.slot());
        GeneralFilterEntry.EntryCounts counts = new GeneralFilterEntry.EntryCounts(0,
                Math.max(0, current.counts().batch()), Math.max(0, stockOf(current)));
        return new GeneralFilterEntry(current.slot(), null, fluidId, chemicalId, null, counts,
                GeneralFilterEntry.SlotMapping.EMPTY, null, GeneralFilterEntry.NbtConstraints.EMPTY, null);
    }

    public static boolean addChemical(ItemStack filter, String chemicalId) {
        if (!isFilterItem(filter) || chemicalId == null || chemicalId.isBlank()) {
            return false;
        }
        int cap = getCapacity(filter);
        for (int i = 0; i < cap; i++) {
            if (chemicalId.equals(getChemicalEntry(filter, i))) {
                return false;
            }
        }
        for (int i = 0; i < cap; i++) {
            if (isEntrySlotAvailable(filter, i)) {
                return setChemicalEntry(filter, i, chemicalId);
            }
        }
        return false;
    }

    public static boolean containsChemical(ItemStack filter, String chemicalId) {
        if (!isFilterItem(filter) || chemicalId == null || chemicalId.isEmpty())
            return false;

        int cap = getCapacity(filter);
        for (int i = 0; i < cap; i++) {
            String entry = getChemicalEntry(filter, i);
            if (entry != null && entry.equals(chemicalId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEntrySlotAvailable(ItemStack filter, int slot) {
        GeneralFilterEntry entry = entry(filter, slot);
        return entry == null || (entry.item() == null && nonEmpty(entry.fluidId()) == null
                && nonEmpty(entry.chemicalId()) == null && FilterTagUtil.normalizeTag(entry.tag()) == null
                && entry.slotMapping().slots().isEmpty() && !isNbtOnly(entry));
    }

    public static boolean hasAvailableEntrySlot(ItemStack filter) {
        int cap = getCapacity(filter);
        for (int i = 0; i < cap; i++) {
            if (isEntrySlotAvailable(filter, i)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasAnyChemicalEntries(ItemStack stack, @Nullable ReadCache readCache) {
        if (!isFilterItem(stack))
            return false;
        return getItemFilterView(stack, readCache).hasChemicalEntries();
    }

    // ── Tag per-slot methods ──

    @Nullable
    public static String getEntryTag(ItemStack stack, int slot) {
        GeneralFilterEntry entry = entry(stack, slot);
        return entry == null ? null : FilterTagUtil.normalizeTag(entry.tag());
    }

    public static void setEntryTag(ItemStack stack, int slot, @Nullable String tag) {
        String normalized = FilterTagUtil.normalizeTag(tag);
        edit(stack, slot, entry -> entry.withTag(normalized));
    }

    public static boolean hasAnyTagEntries(ItemStack stack, @Nullable ReadCache readCache) {
        return isFilterItem(stack) && getItemFilterView(stack, readCache).hasTagEntries();
    }

    public static boolean hasAnyAmountEntries(ItemStack stack, @Nullable ReadCache readCache) {
        if (!isFilterItem(stack))
            return false;
        return getItemFilterView(stack, readCache).hasAmountEntries();
    }

    public static boolean hasAnyStockEntries(ItemStack stack, @Nullable ReadCache readCache) {
        if (!isFilterItem(stack)) return false;
        for (ItemFilterSlot entry : getItemFilterView(stack, readCache).entriesBySlot()) {
            if (entry != null && entry.stock() > 0) return true;
        }
        return false;
    }

    // ── Batch/Stock per-slot methods ──

    public static int getEntryBatch(ItemStack stack, int slot) {
        GeneralFilterEntry entry = entry(stack, slot);
        return entry == null ? 0 : entry.counts().batch();
    }

    public static void setEntryBatch(ItemStack stack, int slot, int batch) {
        edit(stack, slot, entry -> entry.withCounts(new GeneralFilterEntry.EntryCounts(
                entry.counts().amount(), Math.max(0, batch), entry.counts().stock())));
    }

    public static int getEntryStock(ItemStack stack, int slot) {
        GeneralFilterEntry entry = entry(stack, slot);
        return entry == null ? 0 : stockOf(entry);
    }

    public static void setEntryStock(ItemStack stack, int slot, int stock) {
        edit(stack, slot, entry -> entry.withCounts(new GeneralFilterEntry.EntryCounts(
                0, entry.counts().batch(), Math.max(0, stock))));
    }

    // ── Slot mapping per-entry methods ──

    public static String getEntrySlotMappingExpression(ItemStack stack, int slot) {
        GeneralFilterEntry entry = entry(stack, slot);
        if (entry == null)
            return "";
        GeneralFilterEntry.SlotMapping mapping = entry.slotMapping();
        if (!mapping.expression().isEmpty())
            return mapping.expression();
        return mapping.slots().isEmpty() ? "" : SlotExpressionUtil.formatSlots(mapping.slots());
    }

    public static void setEntrySlotMapping(ItemStack stack, int slot, @Nullable int[] slots) {
        setEntrySlotMapping(stack, slot, slots, null);
    }

    public static void setEntrySlotMapping(ItemStack stack, int slot, @Nullable int[] slots,
            @Nullable String expression) {
        GeneralFilterEntry.SlotMapping mapping = slots == null || slots.length == 0
                ? GeneralFilterEntry.SlotMapping.EMPTY
                : new GeneralFilterEntry.SlotMapping(Arrays.stream(slots).boxed().toList(), expression);
        edit(stack, slot, entry -> entry.withSlotMapping(mapping));
    }

    public static boolean hasEntrySlotMapping(ItemStack stack, int slot) {
        GeneralFilterEntry entry = entry(stack, slot);
        return entry != null && !entry.slotMapping().slots().isEmpty();
    }

    public static boolean hasAnySlotMappings(ItemStack filter, @Nullable ReadCache readCache) {
        if (!isFilterItem(filter)) return false;
        ItemFilterView view = getItemFilterView(filter, readCache);
        for (ItemFilterSlot entry : view.entriesBySlot()) {
            if (entry != null && entry.slotMapping() != null) return true;
        }
        return false;
    }

    // ── Enchanted per-entry methods ──

    @Nullable
    public static Boolean getEntryEnchanted(ItemStack stack, int slot) {
        if (!isFilterItem(stack)) return null;
        CompoundTag root = getRoot(stack);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                if (entry.contains(KEY_ENCHANTED, Tag.TAG_BYTE)) {
                    return entry.getBoolean(KEY_ENCHANTED);
                }
            }
        }
        return null;
    }

    public static void setEntryEnchanted(ItemStack stack, int slot, @Nullable Boolean value) {
        if (!isFilterItem(stack)) return;
        if (slot < 0 || slot >= getCapacity(stack)) return;

        updateRoot(stack, root -> {
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            for (Tag t : list) {
                if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                    if (value != null) {
                        entry.putBoolean(KEY_ENCHANTED, value);
                    } else {
                        entry.remove(KEY_ENCHANTED);
                    }
                    root.put(KEY_ITEMS, list);
                    return;
                }
            }
            if (value != null) {
                CompoundTag entry = new CompoundTag();
                entry.putInt(KEY_SLOT, slot);
                entry.putBoolean(KEY_ENCHANTED, value);
                list.add(entry);
                root.put(KEY_ITEMS, list);
            }
        });
    }

    public static boolean hasEntryEnchanted(ItemStack stack, int slot) {
        return getEntryEnchanted(stack, slot) != null;
    }

    // ── NBT per-slot methods ──

    @Nullable
    public static String getEntryNbtPath(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return null;
        CompoundTag root = getRoot(stack);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                if (entry.contains(KEY_NBT_PATH, Tag.TAG_STRING)) {
                    return entry.getString(KEY_NBT_PATH);
                }
            }
        }
        return null;
    }

    public static void setEntryNbt(ItemStack stack, int slot, @Nullable NbtPath path, @Nullable Tag value) {
        setEntryNbt(stack, slot, path, value, NBT_OP_EQUALS);
    }

    public static void setEntryNbt(ItemStack stack, int slot, @Nullable NbtPath path, @Nullable Tag value,
            @Nullable String operator) {
        if (!isFilterItem(stack))
            return;
        if (slot < 0 || slot >= getCapacity(stack))
            return;

        String normalizedOperator = normalizeNbtOperator(operator);

        updateRoot(stack, root -> {
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            for (Tag t : list) {
                if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                    if (path != null && !path.isEmpty() && value != null) {
                        entry.remove(KEY_NBT_RAW);
                        entry.putString(KEY_NBT_PATH, path.toString());
                        entry.put(KEY_NBT_VALUE, value.copy());
                        entry.putString(KEY_NBT_OP, normalizedOperator);
                    } else {
                        entry.remove(KEY_NBT_PATH);
                        entry.remove(KEY_NBT_VALUE);
                        entry.remove(KEY_NBT_OP);
                    }
                    root.put(KEY_ITEMS, list);
                    return;
                }
            }
        });
    }

    public static boolean hasEntryNbt(ItemStack stack, int slot) {
        return !getSlotNbtRules(stack, slot).isEmpty()
                || getEntryNbtPath(stack, slot) != null
                || getEntryNbtRaw(stack, slot) != null;
    }

    @Nullable
    public static String getEntryNbtOperator(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return null;
        CompoundTag root = getRoot(stack);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                return getEntryNbtOperator(entry);
            }
        }
        return null;
    }

    @Nullable
    public static String getEntryNbtRaw(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return null;
        CompoundTag root = getRoot(stack);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                if (entry.contains(KEY_NBT_RAW, Tag.TAG_STRING)) {
                    String raw = entry.getString(KEY_NBT_RAW);
                    return raw.isEmpty() ? null : raw;
                }
            }
        }
        return null;
    }

    public static void setEntryNbtRaw(ItemStack stack, int slot, @Nullable String rawSnbt) {
        if (!isFilterItem(stack))
            return;
        if (slot < 0 || slot >= getCapacity(stack))
            return;

        updateRoot(stack, root -> {
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            for (Tag t : list) {
                if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                    entry.remove(KEY_NBT_PATH);
                    entry.remove(KEY_NBT_VALUE);
                    entry.remove(KEY_NBT_OP);
                    if (rawSnbt != null && !rawSnbt.isEmpty()) {
                        entry.putString(KEY_NBT_RAW, rawSnbt);
                    } else {
                        entry.remove(KEY_NBT_RAW);
                    }
                    root.put(KEY_ITEMS, list);
                    return;
                }
            }
            // No entry exists yet, create one
            if (rawSnbt != null && !rawSnbt.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putInt(KEY_SLOT, slot);
                entry.putString(KEY_NBT_RAW, rawSnbt);
                list.add(entry);
                root.put(KEY_ITEMS, list);
            }
        });
    }

    public static boolean hasAnyNbtEntries(ItemStack stack) {
        return hasEntryType(stack, KEY_NBT_RULES)
                || hasEntryType(stack, KEY_NBT_PATH)
                || hasEntryType(stack, KEY_NBT_RAW);
    }

    public static boolean hasAnyNbtEntries(ItemStack stack, @Nullable ReadCache readCache) {
        if (!isFilterItem(stack))
            return false;
        if (readCache == null)
            return hasAnyNbtEntries(stack);
        return getItemFilterView(stack, readCache).hasNbtEntries();
    }

    public static boolean isNbtOnlySlot(ItemStack stack, int slot) {
        if (!hasEntryNbt(stack, slot) && !hasEntryDurability(stack, slot) && !hasEntryEnchanted(stack, slot)
                && getEntryBatch(stack, slot) <= 0 && getEntryStock(stack, slot) <= 0)
            return false;
        return getEntryTag(stack, slot) == null
                && !hasEntryItem(stack, slot)
                && getFluidEntry(stack, slot).isEmpty()
                && getChemicalEntry(stack, slot) == null;
    }

    public static boolean isEntryNbtStrict(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return false;

        CompoundTag entry = getEntryData(stack, slot);
        if (entry == null || !entry.contains(KEY_ITEM_TAG))
            return false;

        return isEntryNbtStrict(entry);
    }

    public static void setEntryNbtStrict(ItemStack stack, int slot, boolean strict) {
        if (!isFilterItem(stack))
            return;
        if (slot < 0 || slot >= getCapacity(stack))
            return;

        updateRoot(stack, root -> {
            ListTag items = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            for (Tag t : items) {
                if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                    entry.putBoolean(KEY_NBT_STRICT, strict);
                    root.put(KEY_ITEMS, items);
                    return;
                }
            }
        });
    }

    private static boolean hasEntryItem(ItemStack stack, int slot) {
        CompoundTag root = getRoot(stack);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                return entry.contains(KEY_ITEM_TAG, Tag.TAG_COMPOUND);
            }
        }
        return false;
    }

    // ── Multi-rule NBT per-slot methods ──

    public static List<SlotNbtRule> getSlotNbtRules(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return List.of();
        CompoundTag root = getRoot(stack);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                return readSlotNbtRules(entry);
            }
        }
        return List.of();
    }

    public static boolean addSlotNbtRule(ItemStack stack, int slot, String path, String operator, Tag value) {
        if (!isFilterItem(stack) || path == null || path.isEmpty() || value == null)
            return false;
        if (slot < 0 || slot >= getCapacity(stack))
            return false;

        String op = normalizeNbtOperator(operator);
        boolean[] result = { false };

        updateRoot(stack, root -> {
            ListTag items = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            CompoundTag entry = null;
            for (Tag t : items) {
                if (t instanceof CompoundTag c && c.getInt(KEY_SLOT) == slot) {
                    entry = c;
                    break;
                }
            }

            if (entry == null) {
                entry = new CompoundTag();
                entry.putInt(KEY_SLOT, slot);
                items.add(entry);
                root.put(KEY_ITEMS, items);
            }

            migrateToNbtRules(entry);
            ListTag rules = entry.contains(KEY_NBT_RULES, Tag.TAG_LIST)
                    ? entry.getList(KEY_NBT_RULES, Tag.TAG_COMPOUND)
                    : new ListTag();

            if (rules.size() >= MAX_NBT_RULES_PER_SLOT)
                return;

            for (Tag rt : rules) {
                if (rt instanceof CompoundTag r && path.equals(r.getString(KEY_RULE_P))
                        && op.equals(r.contains(KEY_RULE_O) ? r.getString(KEY_RULE_O) : NBT_OP_EQUALS)) {
                    r.put(KEY_RULE_V, value.copy());
                    entry.put(KEY_NBT_RULES, rules);
                    result[0] = true;
                    return;
                }
            }

            CompoundTag rule = new CompoundTag();
            rule.putString(KEY_RULE_P, path);
            rule.putString(KEY_RULE_O, op);
            rule.put(KEY_RULE_V, value.copy());
            rules.add(rule);
            entry.put(KEY_NBT_RULES, rules);
            result[0] = true;
        });

        return result[0];
    }

    public static boolean removeSlotNbtRule(ItemStack stack, int slot, int ruleIndex) {
        if (!isFilterItem(stack))
            return false;

        boolean[] result = { false };

        updateRoot(stack, root -> {
            ListTag items = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            for (Tag t : items) {
                if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                    migrateToNbtRules(entry);
                    ListTag rules = entry.getList(KEY_NBT_RULES, Tag.TAG_COMPOUND);
                    if (ruleIndex >= 0 && ruleIndex < rules.size()) {
                        rules.remove(ruleIndex);
                        if (rules.isEmpty()) {
                            entry.remove(KEY_NBT_RULES);
                            entry.remove(KEY_NBT_MATCH_ANY);
                        } else {
                            entry.put(KEY_NBT_RULES, rules);
                        }
                        result[0] = true;
                    }
                    return;
                }
            }
        });

        return result[0];
    }

    public static void clearSlotNbtRules(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return;

        updateRoot(stack, root -> {
            ListTag items = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            for (Tag t : items) {
                if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                    entry.remove(KEY_NBT_RULES);
                    entry.remove(KEY_NBT_MATCH_ANY);
                    entry.remove(KEY_NBT_PATH);
                    entry.remove(KEY_NBT_VALUE);
                    entry.remove(KEY_NBT_OP);
                    entry.remove(KEY_NBT_RAW);
                    return;
                }
            }
        });
    }

    public static boolean isSlotNbtMatchAny(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return false;
        CompoundTag root = getRoot(stack);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                return entry.getBoolean(KEY_NBT_MATCH_ANY);
            }
        }
        return false;
    }

    public static void toggleSlotNbtMatchMode(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return;

        updateRoot(stack, root -> {
            ListTag items = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            for (Tag t : items) {
                if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                    boolean current = entry.getBoolean(KEY_NBT_MATCH_ANY);
                    if (!current) {
                        entry.putBoolean(KEY_NBT_MATCH_ANY, true);
                    } else {
                        entry.remove(KEY_NBT_MATCH_ANY);
                    }
                    return;
                }
            }
        });
    }

    public static boolean setSlotNbtRuleValue(ItemStack stack, int slot, int ruleIndex, Tag newValue) {
        if (!isFilterItem(stack) || newValue == null)
            return false;

        boolean[] result = { false };
        updateRoot(stack, root -> {
            ListTag items = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            for (Tag t : items) {
                if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                    if (!entry.contains(KEY_NBT_RULES, Tag.TAG_LIST))
                        return;
                    ListTag rules = entry.getList(KEY_NBT_RULES, Tag.TAG_COMPOUND);
                    if (ruleIndex < 0 || ruleIndex >= rules.size())
                        return;
                    CompoundTag rule = (CompoundTag) rules.get(ruleIndex);
                    rule.put(KEY_RULE_V, newValue.copy());
                    entry.put(KEY_NBT_RULES, rules);
                    result[0] = true;
                    return;
                }
            }
        });
        return result[0];
    }

    private static List<SlotNbtRule> readSlotNbtRules(CompoundTag entry) {
        if (entry.contains(KEY_NBT_RULES, Tag.TAG_LIST)) {
            ListTag rules = entry.getList(KEY_NBT_RULES, Tag.TAG_COMPOUND);
            List<SlotNbtRule> result = new ArrayList<>(rules.size());
            for (Tag t : rules) {
                if (t instanceof CompoundTag r) {
                    NbtPath p = NbtPath.parseLenient(r.getString(KEY_RULE_P));
                    String o = r.contains(KEY_RULE_O) ? r.getString(KEY_RULE_O) : NBT_OP_EQUALS;
                    Tag v = r.get(KEY_RULE_V);
                    if (!p.isEmpty() && v != null) {
                        result.add(new SlotNbtRule(p, normalizeNbtOperator(o), v.copy()));
                    }
                }
            }
            return result;
        }

        String rawPath = getEntryNbtPath(entry);
        NbtPath path = rawPath == null ? null : NbtPath.parseLenient(rawPath);
        Tag value = getEntryNbtValue(entry);
        if (path != null && value != null) {
            String op = getEntryNbtOperator(entry);
            return List.of(new SlotNbtRule(path, normalizeNbtOperator(op), value.copy()));
        }

        return List.of();
    }

    private static void migrateToNbtRules(CompoundTag entry) {
        if (entry.contains(KEY_NBT_RULES, Tag.TAG_LIST))
            return;

        String path = getEntryNbtPath(entry);
        Tag value = getEntryNbtValue(entry);
        String op = getEntryNbtOperator(entry);
        entry.remove(KEY_NBT_PATH);
        entry.remove(KEY_NBT_VALUE);
        entry.remove(KEY_NBT_OP);
        entry.remove(KEY_NBT_RAW);

        if (path != null && value != null) {
            ListTag rules = new ListTag();
            CompoundTag rule = new CompoundTag();
            rule.putString(KEY_RULE_P, path);
            rule.putString(KEY_RULE_O, normalizeNbtOperator(op));
            rule.put(KEY_RULE_V, value.copy());
            rules.add(rule);
            entry.put(KEY_NBT_RULES, rules);
        }
    }

    // ── Durability per-slot methods ──

    @Nullable
    public static String getEntryDurabilityOp(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return null;
        CompoundTag root = getRoot(stack);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                if (entry.contains(KEY_DUR_OP, Tag.TAG_STRING)) {
                    return entry.getString(KEY_DUR_OP);
                }
            }
        }
        return null;
    }

    public static int getEntryDurabilityValue(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return 0;
        CompoundTag root = getRoot(stack);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                if (entry.contains(KEY_DUR_VAL, Tag.TAG_INT)) {
                    return entry.getInt(KEY_DUR_VAL);
                }
            }
        }
        return 0;
    }

    public static void setEntryDurability(ItemStack stack, int slot, @Nullable String op, int value) {
        if (!isFilterItem(stack))
            return;
        if (slot < 0 || slot >= getCapacity(stack))
            return;

        updateRoot(stack, root -> {
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            for (Tag t : list) {
                if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                    if (op != null && !op.isEmpty()) {
                        entry.putString(KEY_DUR_OP, op);
                        entry.putInt(KEY_DUR_VAL, Math.max(0, Math.min(3000, value)));
                    } else {
                        entry.remove(KEY_DUR_OP);
                        entry.remove(KEY_DUR_VAL);
                    }
                    root.put(KEY_ITEMS, list);
                    return;
                }
            }
        });
    }

    public static boolean hasEntryDurability(ItemStack stack, int slot) {
        return getEntryDurabilityOp(stack, slot) != null;
    }

    // ── Full matching methods (tag + NBT + durability aware) ──

    public static boolean containsItemFull(ItemStack filter, ItemStack candidate, HolderLookup.Provider provider,
            @Nullable CompoundTag candidateComponents, @Nullable ReadCache readCache) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return false;

        ItemFilterView view = getItemFilterView(filter, readCache);
        LazyComponents components = new LazyComponents(candidateComponents);
        for (ItemFilterSlot entry : view.entriesBySlot()) {
            if (entry == null)
                continue;

            if (entry.slotOnly())
                return true;

            String tag = entry.tag();
            if (tag != null) {
                if (candidate.getTags().map(t -> t.location().toString()).anyMatch(tag::equals)
                        && entryConstraintsMatch(entry, candidate, provider, components))
                    return true;
                continue;
            }

            if (entry.nbtOnly()) {
                if (entryConstraintsMatch(entry, candidate, provider, components))
                    return true;
                continue;
            }

            Item itemEntry = entry.item();
            if (itemEntry != null && itemEntry == candidate.getItem()
                    && itemEntryConstraintsMatch(filter, entry, candidate, provider, components))
                return true;
        }
        return false;
    }

    public static boolean containsItemFullInSlot(ItemStack filter, ItemStack candidate, HolderLookup.Provider provider,
            @Nullable CompoundTag candidateComponents, @Nullable ReadCache readCache, int inventorySlot) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return false;

        ItemFilterView view = getItemFilterView(filter, readCache);
        LazyComponents components = new LazyComponents(candidateComponents);
        for (ItemFilterSlot entry : view.entriesBySlot()) {
            if (entry == null || !coversSlot(entry, inventorySlot))
                continue;

            if (entry.slotOnly())
                return true;

            String tag = entry.tag();
            if (tag != null) {
                if (candidate.getTags().map(t -> t.location().toString()).anyMatch(tag::equals)
                        && entryConstraintsMatch(entry, candidate, provider, components))
                    return true;
                continue;
            }

            if (entry.nbtOnly()) {
                if (entryConstraintsMatch(entry, candidate, provider, components))
                    return true;
                continue;
            }

            Item itemEntry = entry.item();
            if (itemEntry != null && itemEntry == candidate.getItem()
                    && itemEntryConstraintsMatch(filter, entry, candidate, provider, components))
                return true;
        }
        return false;
    }

    public static boolean containsFluidFull(ItemStack filter, FluidStack candidate, HolderLookup.Provider provider,
            @Nullable ReadCache readCache) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return false;

        ItemFilterView view = getItemFilterView(filter, readCache);
        CompoundTag candidateComponents = null;
        boolean candidateComponentsResolved = false;
        for (ItemFilterSlot slot : view.entriesBySlot()) {
            if (slot == null)
                continue;
            String tag = slot.tag();
            if (tag != null || slot.nbtOnly()) {
                if (tag == null || candidate.getTags().map(t -> t.location().toString()).anyMatch(tag::equals)) {
                    if (slot.hasNbt()) {
                        if (!candidateComponentsResolved) {
                            candidateComponents = NbtFilterData.getSerializedComponents(candidate, provider);
                            candidateComponentsResolved = true;
                        }
                        if (!checkNbtConstraint(slot, candidateComponents))
                            continue;
                    }
                    return true;
                }
                continue;
            }
            FluidStack entry = slot.fluidEntry();
            if (entry != null && !entry.isEmpty() && FluidStack.isSameFluidSameComponents(entry, candidate)) {
                if (slot.hasNbt()) {
                    if (!candidateComponentsResolved) {
                        candidateComponents = NbtFilterData.getSerializedComponents(candidate, provider);
                        candidateComponentsResolved = true;
                    }
                    if (!checkNbtConstraint(slot, candidateComponents))
                        continue;
                }
                return true;
            }
        }
        return false;
    }

    public static boolean containsChemicalFull(ItemStack filter, String chemicalId, @Nullable ReadCache readCache) {
        if (!isFilterItem(filter) || chemicalId == null || chemicalId.isEmpty())
            return false;

        ItemFilterView view = getItemFilterView(filter, readCache);
        for (ItemFilterSlot slot : view.entriesBySlot()) {
            if (slot == null)
                continue;
            String tag = slot.tag();
            if (tag != null) {
                if (MekanismCompat.chemicalHasTag(chemicalId, tag))
                    return true;
                continue;
            }
            if (slot.nbtOnly())
                return true;
            String entryId = slot.chemicalId();
            if (entryId != null && entryId.equals(chemicalId))
                return true;
        }
        return false;
    }

    // ── Full amount threshold methods (tag-aware + constraint-aware) ──

    public static List<ItemStock> getItemStocksFull(ItemStack filter, ItemStack candidate,
            HolderLookup.Provider provider, @Nullable CompoundTag candidateComponents, @Nullable ReadCache readCache,
            int inventorySlot) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return List.of();
        ItemFilterView view = getItemFilterView(filter, readCache);
        LazyComponents components = new LazyComponents(candidateComponents);
        List<ItemStock> stocks = new ArrayList<>();
        for (ItemFilterSlot entry : view.entriesBySlot()) {
            if (entry != null && coversSlot(entry, inventorySlot)
                    && itemEntryMatches(filter, entry, candidate, provider, components))
                stocks.add(new ItemStock(entry.stock(), entry.slotMapping()));
        }
        return stocks;
    }

    private static boolean itemEntryMatches(ItemStack filter, ItemFilterSlot entry, ItemStack candidate,
            HolderLookup.Provider provider, LazyComponents components) {
        String tag = entry.tag();
        if (tag != null)
            return candidate.getTags().map(t -> t.location().toString()).anyMatch(tag::equals)
                    && entryConstraintsMatch(entry, candidate, provider, components);
        if (entry.nbtOnly())
            return entryConstraintsMatch(entry, candidate, provider, components);
        return entry.item() == candidate.getItem()
                && itemEntryConstraintsMatch(filter, entry, candidate, provider, components);
    }

    private static boolean coversSlot(ItemFilterSlot entry, int inventorySlot) {
        if (inventorySlot < 0 || entry.slotMapping() == null) return true;
        for (int s : entry.slotMapping()) {
            if (s == inventorySlot) return true;
        }
        return false;
    }

    public static int getItemBatchLimitFull(ItemStack filter, ItemStack candidate,
            HolderLookup.Provider provider, @Nullable CompoundTag candidateComponents, @Nullable ReadCache readCache) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return 0;
        ItemFilterView view = getItemFilterView(filter, readCache);
        LazyComponents components = new LazyComponents(candidateComponents);
        for (ItemFilterSlot entry : view.entriesBySlot()) {
            if (entry == null)
                continue;

            String tag = entry.tag();
            if (tag != null) {
                if (candidate.getTags().map(t -> t.location().toString()).anyMatch(tag::equals)
                        && entryConstraintsMatch(entry, candidate, provider, components))
                    return entry.batch();
                continue;
            }

            if (entry.nbtOnly()) {
                if (entryConstraintsMatch(entry, candidate, provider, components))
                    return entry.batch();
                continue;
            }

            Item itemEntry = entry.item();
            if (itemEntry != null && itemEntry == candidate.getItem()
                    && itemEntryConstraintsMatch(filter, entry, candidate, provider, components))
                return entry.batch();
        }
        return 0;
    }

    public static int getFluidAmountThresholdFull(ItemStack filter, FluidStack candidate,
            HolderLookup.Provider provider, @Nullable ReadCache readCache) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return 0;
        ItemFilterView view = getItemFilterView(filter, readCache);
        for (ItemFilterSlot slot : view.entriesBySlot()) {
            if (slot == null)
                continue;
            String tag = slot.tag();
            if (tag != null) {
                if (candidate.getTags().map(t -> t.location().toString()).anyMatch(tag::equals))
                    return slot.stock();
                continue;
            }
            if (slot.nbtOnly())
                return slot.stock();
            FluidStack entry = slot.fluidEntry();
            if (entry != null && !entry.isEmpty() && FluidStack.isSameFluidSameComponents(entry, candidate))
                return slot.stock();
        }
        return 0;
    }

    public static int getChemicalAmountThresholdFull(ItemStack filter, String chemicalId,
            @Nullable ReadCache readCache) {
        if (!isFilterItem(filter) || chemicalId == null || chemicalId.isEmpty())
            return 0;
        ItemFilterView view = getItemFilterView(filter, readCache);
        for (ItemFilterSlot slot : view.entriesBySlot()) {
            if (slot == null)
                continue;
            String tag = slot.tag();
            if (tag != null) {
                if (MekanismCompat.chemicalHasTag(chemicalId, tag))
                    return slot.stock();
                continue;
            }
            if (slot.nbtOnly())
                return slot.stock();
            String entryId = slot.chemicalId();
            if (entryId != null && entryId.equals(chemicalId))
                return slot.stock();
        }
        return 0;
    }

    public static int getFluidBatchLimitFull(ItemStack filter, FluidStack candidate,
            @Nullable ReadCache readCache) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return 0;
        ItemFilterView view = getItemFilterView(filter, readCache);
        for (ItemFilterSlot slot : view.entriesBySlot()) {
            if (slot == null)
                continue;
            String tag = slot.tag();
            if (tag != null) {
                if (candidate.getTags().map(t -> t.location().toString()).anyMatch(tag::equals))
                    return slot.batch();
                continue;
            }
            if (slot.nbtOnly())
                return slot.batch();
            FluidStack entry = slot.fluidEntry();
            if (entry != null && !entry.isEmpty() && FluidStack.isSameFluidSameComponents(entry, candidate))
                return slot.batch();
        }
        return 0;
    }

    public static int getChemicalBatchLimitFull(ItemStack filter, String chemicalId,
            @Nullable ReadCache readCache) {
        if (!isFilterItem(filter) || chemicalId == null || chemicalId.isEmpty())
            return 0;
        ItemFilterView view = getItemFilterView(filter, readCache);
        for (ItemFilterSlot slot : view.entriesBySlot()) {
            if (slot == null)
                continue;
            String tag = slot.tag();
            if (tag != null) {
                if (MekanismCompat.chemicalHasTag(chemicalId, tag))
                    return slot.batch();
                continue;
            }
            if (slot.nbtOnly())
                return slot.batch();
            String entryId = slot.chemicalId();
            if (entryId != null && entryId.equals(chemicalId))
                return slot.batch();
        }
        return 0;
    }

    // ── Constraint helpers ──

    private static final class LazyComponents {
        private CompoundTag components;
        private boolean resolved;

        LazyComponents(@Nullable CompoundTag preresolved) {
            components = preresolved;
            resolved = preresolved != null;
        }

        @Nullable
        CompoundTag of(ItemStack stack, HolderLookup.Provider provider) {
            if (!resolved) {
                components = NbtFilterData.getSerializedComponents(stack, provider);
                resolved = true;
            }
            return components;
        }
    }

    private static boolean entryConstraintsMatch(ItemFilterSlot entry, ItemStack candidate,
            HolderLookup.Provider provider, LazyComponents components) {
        if (entry.hasNbt() && !checkNbtConstraint(entry, components.of(candidate, provider)))
            return false;
        return checkDurabilityConstraint(entry, candidate) && checkEnchantedConstraint(entry, candidate);
    }

    private static boolean itemEntryConstraintsMatch(ItemStack filter, ItemFilterSlot entry, ItemStack candidate,
            HolderLookup.Provider provider, LazyComponents components) {
        if (entry.nbtStrict()) {
            if (entry.expectedComponents() != null)
                return entry.expectedComponents().equals(candidate.getComponents());
            ItemStack expected = getEntry(filter, entry.slotIndex(), provider);
            return !expected.isEmpty() && ItemStack.isSameItemSameComponents(expected, candidate);
        }
        return entryConstraintsMatch(entry, candidate, provider, components);
    }

    private static boolean checkNbtConstraint(ItemFilterSlot entry, @Nullable CompoundTag components) {
        if (!entry.hasNbt())
            return true;
        if (components == null)
            return false;

        List<SlotNbtRule> rules = entry.nbtRules();
        if (!rules.isEmpty()) {
            boolean matchAny = entry.nbtMatchAny();
            for (SlotNbtRule rule : rules) {
                Tag actual = NbtFilterData.resolvePathValue(components, rule.path());
                boolean matches = matchesNbtValue(rule.operator(), rule.value(), actual);
                if (matchAny && matches) return true;
                if (!matchAny && !matches) return false;
            }
            return !matchAny;
        }

        CompoundTag rawNbt = entry.rawNbt();
        if (rawNbt != null) {
            return NbtRuleMatcher.compoundContains(components, rawNbt);
        }
        if (entry.invalidRawNbt()) {
            return false;
        }

        NbtPath nbtPath = entry.nbtPath();
        Tag nbtExpected = entry.nbtValue();
        if (nbtPath == null || nbtExpected == null)
            return true;
        Tag actual = NbtFilterData.resolvePathValue(components, nbtPath);
        return matchesNbtValue(entry.nbtOp(), nbtExpected, actual);
    }

    private static boolean checkDurabilityConstraint(ItemFilterSlot entry, ItemStack candidate) {
        String durOp = entry.durOp();
        if (durOp == null || !candidate.isDamageableItem())
            return true;
        int durVal = entry.durVal();
        int remaining = candidate.getMaxDamage() - candidate.getDamageValue();
        DurabilityFilterData.Operator op = DurabilityFilterData.Operator.fromId(durOp);
        return switch (op) {
            case LESS_OR_EQUAL -> remaining <= durVal;
            case EQUAL -> remaining == durVal;
            case GREATER_OR_EQUAL -> remaining >= durVal;
        };
    }

    private static boolean checkEnchantedConstraint(ItemFilterSlot entry, ItemStack candidate) {
        Boolean enchanted = entry.enchanted();
        if (enchanted == null) return true;
        return candidate.isEnchanted() == enchanted;
    }

    public static int getEntryAmount(ItemStack stack, int slot) {
        GeneralFilterEntry entry = entry(stack, slot);
        return entry == null ? 0 : entry.counts().amount();
    }

    public static void setEntryAmount(ItemStack stack, int slot, int amount) {
        edit(stack, slot, entry -> entry.isEmpty() ? entry : entry.withCounts(new GeneralFilterEntry.EntryCounts(
                Math.max(0, amount), entry.counts().batch(), entry.counts().stock())));
    }

    /**
     * Returns a list of warning messages for misconfigured filter entries.
     * Checks for: invalid/unparseable NBT raw SNBT, and empty tag references.
     */
    public static List<String> getWarnings(ItemStack stack) {
        List<String> warnings = new ArrayList<>();
        if (!isFilterItem(stack))
            return warnings;

        int cap = getCapacity(stack);
        for (int i = 0; i < cap; i++) {
            // Check for invalid raw SNBT
            String raw = getEntryNbtRaw(stack, i);
            if (raw != null) {
                try {
                    TagParser.parseTag(raw);
                } catch (Exception e) {
                    warnings.add("Slot " + (i + 1) + ": invalid NBT (" + e.getMessage() + ")");
                }
            }
        }
        return warnings;
    }

    private static ItemFilterView getItemFilterView(ItemStack stack, @Nullable ReadCache readCache) {
        FilterSettings settings = stack.get(LogisticsDataComponents.FILTER_SETTINGS);
        GeneralFilterConfig config = stack.get(LogisticsDataComponents.FILTER_ENTRIES);
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (readCache != null) {
            CachedItemView cached = readCache.itemViews.get(stack);
            if (cached != null && cached.settings() == settings && cached.config() == config
                    && cached.customData() == customData) {
                return cached.view();
            }
        }

        boolean migrated = LegacyComponentMigration.migrateGeneralFilter(stack, null);
        settings = stack.get(LogisticsDataComponents.FILTER_SETTINGS);
        config = stack.get(LogisticsDataComponents.FILTER_ENTRIES);
        customData = stack.get(DataComponents.CUSTOM_DATA);
        ItemFilterView built = migrated
                ? buildItemFilterView(stack, settings, config)
                : buildLegacyItemFilterView(stack);
        if (readCache != null) {
            readCache.itemViews.put(stack, new CachedItemView(settings, config, customData, built));
        }
        return built;
    }

    private static ItemFilterView buildItemFilterView(ItemStack stack, @Nullable FilterSettings settings,
            @Nullable GeneralFilterConfig config) {
        int cap = getCapacity(stack);
        ItemFilterSlot[] entriesBySlot = new ItemFilterSlot[Math.max(cap, 0)];
        if (!isFilterItem(stack) || cap <= 0) {
            return emptyItemFilterView(entriesBySlot);
        }

        if (config != null) {
            for (GeneralFilterEntry entry : config.entries()) {
                int slot = entry.slot();
                if (slot >= 0 && slot < cap && entriesBySlot[slot] == null) {
                    entriesBySlot[slot] = buildItemFilterSlot(entry);
                }
            }
        }
        return summarizeItemFilterView(settings != null && settings.blacklist(), entriesBySlot);
    }

    private static ItemFilterSlot buildItemFilterSlot(GeneralFilterEntry entry) {
        ItemStack expected = entry.item() == null ? ItemStack.EMPTY : entry.item().toStack();
        Item item = expected.isEmpty() ? null : expected.getItem();
        DataComponentMap expectedComponents = expected.isEmpty() ? null : expected.getComponents();
        String tag = FilterTagUtil.normalizeTag(entry.tag());
        String fluidId = nonEmpty(entry.fluidId());
        String chemicalId = nonEmpty(entry.chemicalId());
        FluidStack fluid = resolveFluidEntry(fluidId);
        List<SlotNbtRule> rules = entry.nbt().rules().stream()
                .filter(rule -> !rule.path().isEmpty())
                .map(rule -> new SlotNbtRule(rule.path(), normalizeNbtOperator(rule.operator()), rule.value()))
                .toList();
        ParsedRawNbt raw = parseRawNbt(entry.nbt().raw());
        String durOp = entry.durability() == null ? null : entry.durability().operator().id();
        int durVal = entry.durability() == null ? 0 : entry.durability().value();
        int stock = stockOf(entry);
        int[] mapping = entry.slotMapping().slots().isEmpty()
                ? null
                : entry.slotMapping().slots().stream().mapToInt(Integer::intValue).toArray();
        boolean hasNbt = !rules.isEmpty() || !entry.nbt().raw().isEmpty();
        boolean hasDur = entry.durability() != null;
        boolean nbtOnly = (hasNbt || hasDur || entry.enchanted() != null || entry.counts().batch() > 0 || stock > 0)
                && tag == null && item == null && fluidId == null && chemicalId == null;
        boolean strict = item != null && entry.nbt().strict().orElse(
                !hasNbt && !hasDur && entry.enchanted() == null);
        boolean slotOnly = mapping != null && tag == null && item == null && fluidId == null
                && chemicalId == null && !hasNbt && !hasDur && entry.enchanted() == null;
        return new ItemFilterSlot(entry.slot(), tag, item, expectedComponents, chemicalId, fluid,
                entry.counts().batch(), stock, null, null, NBT_OP_EQUALS, raw.value(), raw.invalid(), durOp,
                durVal, hasNbt, nbtOnly, strict, rules, entry.nbt().matchAny(), mapping, slotOnly,
                entry.enchanted());
    }

    private static ItemFilterView summarizeItemFilterView(boolean blacklist, ItemFilterSlot[] entriesBySlot) {
        boolean item = false, fluid = false, chemical = false, tag = false;
        boolean nbt = false, amount = false, slotOnly = false;
        for (ItemFilterSlot entry : entriesBySlot) {
            if (entry == null)
                continue;
            item |= entry.item() != null || entry.nbtOnly();
            fluid |= entry.fluidEntry() != null || entry.nbtOnly();
            chemical |= entry.chemicalId() != null || entry.nbtOnly();
            tag |= entry.tag() != null;
            nbt |= entry.hasNbt();
            amount |= entry.batch() > 0 || entry.stock() > 0 || entry.enchanted() != null;
            slotOnly |= entry.slotOnly();
        }
        return new ItemFilterView(blacklist, item, fluid, chemical, tag, nbt, amount, slotOnly, entriesBySlot);
    }

    private static ItemFilterView emptyItemFilterView(ItemFilterSlot[] entriesBySlot) {
        return new ItemFilterView(false, false, false, false, false, false, false, false, entriesBySlot);
    }

    @Nullable
    private static FluidStack resolveFluidEntry(@Nullable String fluidId) {
        if (fluidId == null)
            return null;
        ResourceLocation id = ResourceLocation.tryParse(fluidId);
        return id == null ? FluidStack.EMPTY : BuiltInRegistries.FLUID.getOptional(id)
                .map(fluid -> new FluidStack(fluid, 1000))
                .orElse(FluidStack.EMPTY);
    }

    @Nullable
    private static String nonEmpty(@Nullable String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static List<GeneralFilterEntry> entries(ItemStack stack) {
        if (!isFilterItem(stack))
            return List.of();
        LegacyComponentMigration.migrateGeneralFilter(stack, null);
        GeneralFilterConfig config = stack.get(LogisticsDataComponents.FILTER_ENTRIES);
        return config == null ? List.of() : config.entries();
    }

    @Nullable
    private static GeneralFilterEntry entry(ItemStack stack, int slot) {
        List<GeneralFilterEntry> entries = entries(stack);
        int index = indexOfSlot(entries, slot);
        return index < 0 ? null : entries.get(index);
    }

    private static int indexOfSlot(List<GeneralFilterEntry> entries, int slot) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).slot() == slot)
                return i;
        }
        return -1;
    }

    private static boolean edit(ItemStack stack, int slot, UnaryOperator<GeneralFilterEntry> change) {
        if (!isFilterItem(stack) || slot < 0 || slot >= getCapacity(stack)
                || !LegacyComponentMigration.migrateGeneralFilter(stack, null))
            return false;
        List<GeneralFilterEntry> entries = new ArrayList<>(entries(stack));
        int index = indexOfSlot(entries, slot);
        GeneralFilterEntry current = index < 0 ? GeneralFilterEntry.empty(slot) : entries.get(index);
        GeneralFilterEntry next = change.apply(current);
        if (next.equals(current))
            return false;
        if (index < 0)
            entries.add(next);
        else if (next.isEmpty())
            entries.remove(index);
        else
            entries.set(index, next);
        if (entries.isEmpty())
            stack.remove(LogisticsDataComponents.FILTER_ENTRIES);
        else
            stack.set(LogisticsDataComponents.FILTER_ENTRIES, new GeneralFilterConfig(entries));
        return true;
    }

    private static boolean hasNbt(GeneralFilterEntry entry) {
        return !entry.nbt().rules().isEmpty() || !entry.nbt().raw().isEmpty();
    }

    private static int stockOf(GeneralFilterEntry entry) {
        return entry.counts().stock() != 0 ? entry.counts().stock() : entry.counts().amount();
    }

    private static boolean isNbtOnly(GeneralFilterEntry entry) {
        return (hasNbt(entry) || entry.counts().batch() > 0 || stockOf(entry) > 0)
                && entry.item() == null && FilterTagUtil.normalizeTag(entry.tag()) == null
                && nonEmpty(entry.fluidId()) == null && nonEmpty(entry.chemicalId()) == null;
    }

    private static ParsedRawNbt parseRawNbt(String raw) {
        if (raw.isEmpty())
            return new ParsedRawNbt(null, false);
        try {
            return new ParsedRawNbt(TagParser.parseTag(raw), false);
        } catch (Exception e) {
            return new ParsedRawNbt(null, true);
        }
    }

    private record ParsedRawNbt(@Nullable CompoundTag value, boolean invalid) {
    }

    private static ItemFilterView buildLegacyItemFilterView(ItemStack stack) {
        int cap = getCapacity(stack);
        ItemFilterSlot[] entriesBySlot = new ItemFilterSlot[Math.max(cap, 0)];
        if (!isFilterItem(stack) || cap <= 0) {
            return new ItemFilterView(false, false, false, false, false, false, false, false, entriesBySlot);
        }

        CompoundTag root = getRoot(stack);
        boolean blacklist = root.getBoolean(KEY_IS_BLACKLIST);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);

        boolean hasItemEntries = false;
        boolean hasFluidEntries = false;
        boolean hasChemicalEntries = false;
        boolean hasTagEntries = false;
        boolean hasNbtEntries = false;
        boolean hasAmountEntries = false;
        boolean hasSlotOnlyEntries = false;

        for (Tag t : list) {
            if (!(t instanceof CompoundTag entry))
                continue;

            int slot = entry.getInt(KEY_SLOT);
            if (slot < 0 || slot >= cap || entriesBySlot[slot] != null)
                continue;

            String tag = getEntryTag(entry);
            Item item = resolveEntryItem(entry);
            boolean hasFluid = entry.contains(KEY_FLUID_ID, Tag.TAG_STRING);
            boolean hasChemical = entry.contains(KEY_CHEMICAL_ID, Tag.TAG_STRING);
            String chemicalId = hasChemical ? entry.getString(KEY_CHEMICAL_ID) : null;
            FluidStack fluidEntry = null;
            if (hasFluid) {
                ResourceLocation fluidId = ResourceLocation.tryParse(entry.getString(KEY_FLUID_ID));
                if (fluidId != null) {
                    fluidEntry = BuiltInRegistries.FLUID.getOptional(fluidId)
                            .map(f -> new FluidStack(f, 1000))
                            .orElse(null);
                }
            }
            List<SlotNbtRule> nbtRules = readSlotNbtRules(entry);
            boolean nbtMatchAny = entry.getBoolean(KEY_NBT_MATCH_ANY);

            String rawNbtPath = getEntryNbtPath(entry);
            NbtPath nbtPath = rawNbtPath == null ? null : NbtPath.parseLenient(rawNbtPath);
            Tag nbtValue = getEntryNbtValue(entry);
            String nbtOp = getEntryNbtOperator(entry);
            String raw = getEntryNbtRaw(entry);
            CompoundTag rawNbt = null;
            boolean invalidRawNbt = false;
            if (raw != null) {
                try {
                    rawNbt = TagParser.parseTag(raw);
                } catch (Exception e) {
                    invalidRawNbt = true;
                }
            }

            String durOp = getEntryDurabilityOp(entry);
            int durVal = getEntryDurabilityValue(entry);
            int batch = getEntryBatch(entry);
            int stock = getEntryStock(entry);
            boolean hasNbt = !nbtRules.isEmpty() || nbtPath != null || raw != null;
            boolean hasDur = durOp != null;
            Boolean enchanted = entry.contains(KEY_ENCHANTED, Tag.TAG_BYTE) ? entry.getBoolean(KEY_ENCHANTED) : null;
            boolean nbtOnly = (hasNbt || hasDur || enchanted != null || batch > 0 || stock > 0)
                    && tag == null && item == null && !hasFluid && !hasChemical;

            boolean nbtStrict = isEntryNbtStrict(entry);

            int[] slotMapping = null;
            if (entry.contains(KEY_SLOT_MAPPING, Tag.TAG_INT_ARRAY)) {
                int[] arr = entry.getIntArray(KEY_SLOT_MAPPING);
                if (arr.length > 0) slotMapping = arr;
            }
            boolean slotOnly = slotMapping != null && tag == null && item == null && !hasFluid
                    && !hasChemical && !hasNbt && !hasDur && enchanted == null;

            entriesBySlot[slot] = new ItemFilterSlot(slot, tag, item, null, chemicalId, fluidEntry, batch, stock, nbtPath,
                    nbtValue, nbtOp, rawNbt, invalidRawNbt, durOp, durVal, hasNbt, nbtOnly, nbtStrict, nbtRules,
                    nbtMatchAny, slotMapping, slotOnly, enchanted);

            hasItemEntries |= item != null || nbtOnly;
            hasFluidEntries |= hasFluid || nbtOnly;
            hasChemicalEntries |= hasChemical || nbtOnly;
            hasTagEntries |= tag != null;
            hasNbtEntries |= hasNbt;
            hasAmountEntries |= batch > 0 || stock > 0 || enchanted != null;
            hasSlotOnlyEntries |= slotOnly;
        }

        return new ItemFilterView(blacklist, hasItemEntries, hasFluidEntries, hasChemicalEntries,
                hasTagEntries, hasNbtEntries, hasAmountEntries, hasSlotOnlyEntries, entriesBySlot);
    }

    @Nullable
    private static CompoundTag getEntryData(ItemStack stack, int slot) {
        if (slot < 0)
            return null;

        ListTag list = getRoot(stack).getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                return entry;
            }
        }
        return null;
    }

    @Nullable
    private static String getEntryTag(CompoundTag entry) {
        return entry.contains(KEY_TAG, Tag.TAG_STRING)
                ? FilterTagUtil.normalizeTag(entry.getString(KEY_TAG))
                : null;
    }

    @Nullable
    private static Item resolveEntryItem(CompoundTag entry) {
        if (!entry.contains(KEY_ITEM_TAG, Tag.TAG_COMPOUND))
            return null;

        CompoundTag itemTag = entry.getCompound(KEY_ITEM_TAG);
        if (!itemTag.contains("id", Tag.TAG_STRING))
            return null;

        ResourceLocation id = ResourceLocation.tryParse(itemTag.getString("id"));
        if (id == null)
            return null;

        return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    @Nullable
    private static String getEntryNbtPath(CompoundTag entry) {
        return entry.contains(KEY_NBT_PATH, Tag.TAG_STRING) ? entry.getString(KEY_NBT_PATH) : null;
    }

    @Nullable
    private static String getEntryNbtOperator(CompoundTag entry) {
        if (!entry.contains(KEY_NBT_OP, Tag.TAG_STRING))
            return NBT_OP_EQUALS;
        return normalizeNbtOperator(entry.getString(KEY_NBT_OP));
    }

    @Nullable
    private static Tag getEntryNbtValue(CompoundTag entry) {
        return entry.contains(KEY_NBT_VALUE) ? entry.get(KEY_NBT_VALUE) : null;
    }

    @Nullable
    private static String getEntryNbtRaw(CompoundTag entry) {
        if (!entry.contains(KEY_NBT_RAW, Tag.TAG_STRING))
            return null;
        String raw = entry.getString(KEY_NBT_RAW);
        return raw.isEmpty() ? null : raw;
    }

    @Nullable
    private static String getEntryDurabilityOp(CompoundTag entry) {
        return entry.contains(KEY_DUR_OP, Tag.TAG_STRING) ? entry.getString(KEY_DUR_OP) : null;
    }

    private static int getEntryDurabilityValue(CompoundTag entry) {
        return entry.contains(KEY_DUR_VAL, Tag.TAG_INT) ? entry.getInt(KEY_DUR_VAL) : 0;
    }

    private static int getEntryAmount(CompoundTag entry) {
        return entry.contains(KEY_AMOUNT, Tag.TAG_INT) ? entry.getInt(KEY_AMOUNT) : 0;
    }

    private static int getEntryBatch(CompoundTag entry) {
        return entry.contains(KEY_BATCH, Tag.TAG_INT) ? entry.getInt(KEY_BATCH) : 0;
    }

    private static int getEntryStock(CompoundTag entry) {
        if (entry.contains(KEY_STOCK, Tag.TAG_INT)) return entry.getInt(KEY_STOCK);
        return getEntryAmount(entry);
    }

    private static boolean hasEntryNbt(CompoundTag entry) {
        return entry.contains(KEY_NBT_RULES, Tag.TAG_LIST)
                || getEntryNbtPath(entry) != null
                || getEntryNbtRaw(entry) != null;
    }

    private static boolean isEntryNbtStrict(CompoundTag entry) {
        if (!entry.contains(KEY_ITEM_TAG))
            return false;
        if (entry.contains(KEY_NBT_STRICT))
            return entry.getBoolean(KEY_NBT_STRICT);
        return !hasEntryNbt(entry) && !hasEntryDurability(entry) && !entry.contains(KEY_ENCHANTED);
    }

    private static String normalizeNbtOperator(@Nullable String operator) {
        return NbtRuleMatcher.normalizeOperator(operator);
    }

    public static String nextNbtOperator(String current) {
        return NbtRuleMatcher.nextOperator(current);
    }

    private static boolean matchesNbtValue(@Nullable String operator, Tag expected, @Nullable Tag actual) {
        return NbtRuleMatcher.matchesValue(operator, expected, actual);
    }

    private static boolean hasEntryDurability(CompoundTag entry) {
        return getEntryDurabilityOp(entry) != null;
    }

    private static CompoundTag getRoot(ItemStack stack) {
        return getRoot(stack, null);
    }

    private static void updateRoot(ItemStack stack, Consumer<CompoundTag> modifier) {
        updateRoot(stack, null, modifier);
    }

    private static CompoundTag getRoot(ItemStack stack, @Nullable HolderLookup.Provider provider) {
        return LegacyComponentMigration.getGeneralFilterRoot(stack, provider);
    }

    private static void updateRoot(ItemStack stack, @Nullable HolderLookup.Provider provider,
            Consumer<CompoundTag> modifier) {
        LegacyComponentMigration.updateGeneralFilterRoot(stack, provider, modifier);
    }
}
