package me.almana.logisticsnetworks.filter;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.util.ItemDataUtil;
import me.almana.logisticsnetworks.util.ItemStackCompat;
import me.almana.logisticsnetworks.item.BaseFilterItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

public final class FilterItemData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY_ROOT = "ln_filter";
    private static final String KEY_IS_BLACKLIST = "blacklist";
    private static final String KEY_TARGET_TYPE = "target";
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
    private static final String KEY_DUR_OP = "dur_op";
    private static final String KEY_DUR_VAL = "dur_val";
    private static final String KEY_NBT_RAW = "nbt_raw";
    private static final String KEY_NBT_OP = "nbt_op";
    private static final String KEY_NBT_ENTRIES = "nbt_list";
    private static final String KEY_SLOT_MAPPING = "slot_map";
    private static final String KEY_ENCHANTED = "enchanted";

    public enum NbtOperator {
        EQUAL("eq", "="),
        NOT_EQUAL("ne", "!="),
        GREATER_OR_EQUAL("ge", ">="),
        LESS_OR_EQUAL("le", "<=");

        private final String id;
        private final String symbol;

        NbtOperator(String id, String symbol) {
            this.id = id;
            this.symbol = symbol;
        }

        public String id() { return id; }
        public String symbol() { return symbol; }

        public NbtOperator next() {
            return switch (this) {
                case EQUAL -> NOT_EQUAL;
                case NOT_EQUAL -> GREATER_OR_EQUAL;
                case GREATER_OR_EQUAL -> LESS_OR_EQUAL;
                case LESS_OR_EQUAL -> EQUAL;
            };
        }

        public static NbtOperator fromId(@Nullable String id) {
            if (id == null) return EQUAL;
            for (NbtOperator op : values()) {
                if (op.id.equals(id)) return op;
            }
            return EQUAL;
        }
    }

    public static final class ReadCache {
        private final IdentityHashMap<ItemStack, ItemFilterView> itemViews = new IdentityHashMap<>();

        private ReadCache() {
        }
    }

    public record NbtConstraint(String path, Tag value, String op) {
    }

    private record ItemFilterSlot(
            @Nullable String tag,
            @Nullable Item item,
            int batch,
            int stock,
            List<NbtConstraint> nbtConstraints,
            @Nullable CompoundTag rawNbt,
            boolean invalidRawNbt,
            @Nullable String durOp,
            int durVal,
            boolean hasNbt,
            boolean nbtOnly,
            @Nullable int[] slotMapping,
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
            ItemFilterSlot[] entriesBySlot) {
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
        return getRoot(stack).getBoolean(KEY_IS_BLACKLIST);
    }

    public static boolean isBlacklist(ItemStack stack, @Nullable ReadCache readCache) {
        if (!isFilterItem(stack))
            return false;
        return getItemFilterView(stack, readCache).blacklist();
    }

    public static void setBlacklist(ItemStack stack, boolean isBlacklist) {
        if (!isFilterItem(stack))
            return;

        updateRoot(stack, root -> {
            if (isBlacklist) {
                root.putBoolean(KEY_IS_BLACKLIST, true);
            } else {
                root.remove(KEY_IS_BLACKLIST);
            }
        });
    }

    public static FilterTargetType getTargetType(ItemStack stack) {
        if (!isFilterItem(stack))
            return FilterTargetType.ITEMS;
        return FilterTargetType.fromOrdinal(getRoot(stack).getInt(KEY_TARGET_TYPE));
    }

    public static void setTargetType(ItemStack stack, FilterTargetType type) {
        if (!isFilterItem(stack))
            return;
        FilterTargetType target = type == null ? FilterTargetType.ITEMS : type;
        updateRoot(stack, root -> {
            if (target == FilterTargetType.ITEMS) {
                root.remove(KEY_TARGET_TYPE);
            } else {
                root.putInt(KEY_TARGET_TYPE, target.ordinal());
            }
        });
    }

    public static ItemStack getEntry(ItemStack stack, int slot, @Nullable HolderLookup.Provider provider) {
        if (!isFilterItem(stack) || provider == null)
            return ItemStack.EMPTY;

        CompoundTag root = getRoot(stack);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);

        for (Tag t : list) {
            if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                if (entry.contains(KEY_ITEM_TAG, Tag.TAG_COMPOUND)) {
                    return ItemStackCompat.parseOptional(provider, entry.getCompound(KEY_ITEM_TAG));
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static void setEntry(ItemStack stack, int slot, ItemStack value, @Nullable HolderLookup.Provider provider) {
        if (!isFilterItem(stack) || provider == null)
            return;
        if (slot < 0 || slot >= getCapacity(stack))
            return;

        ItemStack item = (value == null || value.isEmpty()) ? ItemStack.EMPTY : ItemStackCompat.copyWithCount(value, 1);
        int existingBatch = getEntryBatch(stack, slot);
        int existingStock = getEntryStock(stack, slot);

        updateRoot(stack, root -> {
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            removeFromList(list, slot);

            if (!item.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putInt(KEY_SLOT, slot);
                entry.put(KEY_ITEM_TAG, ItemStackCompat.save(item, provider));
                if (existingBatch > 0) entry.putInt(KEY_BATCH, existingBatch);
                if (existingStock > 0) entry.putInt(KEY_STOCK, existingStock);
                list.add(entry);
            }

            if (list.isEmpty()) {
                root.remove(KEY_ITEMS);
            } else {
                root.put(KEY_ITEMS, list);
            }
        });
    }

    public static void clearEntryItem(ItemStack stack, int slot) {
        if (!isFilterItem(stack)) return;
        if (slot < 0 || slot >= getCapacity(stack)) return;

        updateRoot(stack, root -> {
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            for (Tag t : list) {
                if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                    entry.remove(KEY_ITEM_TAG);
                    root.put(KEY_ITEMS, list);
                    return;
                }
            }
        });
    }

    public static FluidStack getFluidEntry(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return FluidStack.EMPTY;

        CompoundTag root = getRoot(stack);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);

        for (Tag t : list) {
            if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                if (entry.contains(KEY_FLUID_ID, Tag.TAG_STRING)) {
                    ResourceLocation id = ResourceLocation.tryParse(entry.getString(KEY_FLUID_ID));
                    if (id != null) {
                        return BuiltInRegistries.FLUID.getOptional(id)
                                .map(f -> new FluidStack(f, 1000))
                                .orElse(FluidStack.EMPTY);
                    }
                }
            }
        }
        return FluidStack.EMPTY;
    }

    public static void setFluidEntry(ItemStack stack, int slot, FluidStack fluid) {
        if (!isFilterItem(stack))
            return;
        if (slot < 0 || slot >= getCapacity(stack))
            return;

        ResourceLocation id = (fluid != null && !fluid.isEmpty())
                ? BuiltInRegistries.FLUID.getKey(fluid.getFluid())
                : null;
        int existingBatch = getEntryBatch(stack, slot);
        int existingStock = getEntryStock(stack, slot);

        updateRoot(stack, root -> {
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            removeFromList(list, slot);

            if (id != null) {
                CompoundTag entry = new CompoundTag();
                entry.putInt(KEY_SLOT, slot);
                entry.putString(KEY_FLUID_ID, id.toString());
                if (existingBatch > 0) entry.putInt(KEY_BATCH, existingBatch);
                if (existingStock > 0) entry.putInt(KEY_STOCK, existingStock);
                list.add(entry);
            }

            if (list.isEmpty()) {
                root.remove(KEY_ITEMS);
            } else {
                root.put(KEY_ITEMS, list);
            }
        });
    }

    public static boolean hasAnyEntries(ItemStack stack) {
        if (!isFilterItem(stack))
            return false;

        CompoundTag root = getRoot(stack);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        return !list.isEmpty();
    }

    public static boolean hasAnyItemEntries(ItemStack stack) {
        return hasEntryType(stack, KEY_ITEM_TAG);
    }

    public static boolean hasAnyItemMatchEntries(ItemStack stack, @Nullable ReadCache readCache) {
        if (!isFilterItem(stack))
            return false;
        ItemFilterView view = getItemFilterView(stack, readCache);
        return view.hasItemEntries() || view.hasTagEntries();
    }

    public static boolean hasAnyFluidEntries(ItemStack stack) {
        return hasEntryType(stack, KEY_FLUID_ID);
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

    public static boolean matches(ItemStack filter, ItemStack candidate, HolderLookup.Provider provider) {
        if (!isFilterItem(filter))
            return true;
        if (candidate.isEmpty())
            return false;

        if (!hasAnyEntries(filter))
            return true;

        boolean matched = containsItem(filter, candidate, provider);
        return isBlacklist(filter) != matched;
    }

    public static boolean matchesAny(ItemStack[] filters, ItemStack candidate, HolderLookup.Provider provider) {
        if (candidate.isEmpty() || filters == null || filters.length == 0)
            return false;

        boolean activeWhitelist = false;
        boolean whitelistMatched = false;

        for (ItemStack filter : filters) {
            if (!isFilterItem(filter) || !hasAnyEntries(filter))
                continue;

            boolean matched = containsItem(filter, candidate, provider);

            if (isBlacklist(filter)) {
                if (matched)
                    return false;
            } else {
                activeWhitelist = true;
                if (matched)
                    whitelistMatched = true;
            }
        }
        return !activeWhitelist || whitelistMatched;
    }

    public static int getEntryCount(ItemStack stack) {
        if (!isFilterItem(stack))
            return 0;
        CompoundTag root = getRoot(stack);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        return list.size();
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
            if (!entry.isEmpty() && entry.isFluidEqual(candidate)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static String getChemicalEntry(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return null;

        CompoundTag root = getRoot(stack);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);

        for (Tag t : list) {
            if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                if (entry.contains(KEY_CHEMICAL_ID, Tag.TAG_STRING)) {
                    return entry.getString(KEY_CHEMICAL_ID);
                }
            }
        }
        return null;
    }

    public static void setChemicalEntry(ItemStack stack, int slot, String chemicalId) {
        if (!isFilterItem(stack))
            return;
        if (slot < 0 || slot >= getCapacity(stack))
            return;

        int existingBatch = getEntryBatch(stack, slot);
        int existingStock = getEntryStock(stack, slot);

        updateRoot(stack, root -> {
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            removeFromList(list, slot);

            if (chemicalId != null && !chemicalId.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putInt(KEY_SLOT, slot);
                entry.putString(KEY_CHEMICAL_ID, chemicalId);
                if (existingBatch > 0) entry.putInt(KEY_BATCH, existingBatch);
                if (existingStock > 0) entry.putInt(KEY_STOCK, existingStock);
                list.add(entry);
            }

            if (list.isEmpty()) {
                root.remove(KEY_ITEMS);
            } else {
                root.put(KEY_ITEMS, list);
            }
        });
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

    public static boolean hasAnyChemicalEntries(ItemStack stack) {
        return hasEntryType(stack, KEY_CHEMICAL_ID);
    }

    // ── Tag per-slot methods ──

    @Nullable
    public static String getEntryTag(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return null;
        CompoundTag root = getRoot(stack);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                if (entry.contains(KEY_TAG, Tag.TAG_STRING)) {
                    return FilterTagUtil.normalizeTag(entry.getString(KEY_TAG));
                }
            }
        }
        return null;
    }

    public static void setEntryTag(ItemStack stack, int slot, @Nullable String tag) {
        if (!isFilterItem(stack))
            return;
        if (slot < 0 || slot >= getCapacity(stack))
            return;

        String normalizedTag = FilterTagUtil.normalizeTag(tag);

        updateRoot(stack, root -> {
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);

            CompoundTag existing = null;
            for (Tag t : list) {
                if (t instanceof CompoundTag c && c.getInt(KEY_SLOT) == slot) {
                    existing = c;
                    break;
                }
            }

            if (normalizedTag != null) {
                if (existing != null) {
                    existing.putString(KEY_TAG, normalizedTag);
                } else {
                    CompoundTag entry = new CompoundTag();
                    entry.putInt(KEY_SLOT, slot);
                    entry.putString(KEY_TAG, normalizedTag);
                    list.add(entry);
                }
            } else if (existing != null) {
                existing.remove(KEY_TAG);
            }

            if (list.isEmpty()) {
                root.remove(KEY_ITEMS);
            } else {
                root.put(KEY_ITEMS, list);
            }
        });
    }

    public static boolean isTagEntry(ItemStack stack, int slot) {
        return getEntryTag(stack, slot) != null;
    }

    public static boolean hasAnyTagEntries(ItemStack stack) {
        return hasEntryType(stack, KEY_TAG);
    }

    public static boolean hasAnyAmountEntries(ItemStack stack, @Nullable ReadCache readCache) {
        if (!isFilterItem(stack))
            return false;
        return getItemFilterView(stack, readCache).hasAmountEntries();
    }

    // ── Slot mapping per-entry methods ──

    @Nullable
    public static int[] getEntrySlotMapping(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return null;
        CompoundTag root = getRoot(stack);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                if (entry.contains(KEY_SLOT_MAPPING, Tag.TAG_INT_ARRAY)) {
                    int[] mapping = entry.getIntArray(KEY_SLOT_MAPPING);
                    return mapping.length > 0 ? mapping : null;
                }
            }
        }
        return null;
    }

    public static String getEntrySlotMappingExpression(ItemStack stack, int slot) {
        int[] mapping = getEntrySlotMapping(stack, slot);
        if (mapping == null) return "";
        List<Integer> list = new ArrayList<>();
        for (int s : mapping) list.add(s);
        return SlotExpressionUtil.formatSlots(list);
    }

    public static void setEntrySlotMapping(ItemStack stack, int slot, @Nullable int[] slots) {
        if (!isFilterItem(stack))
            return;
        if (slot < 0 || slot >= getCapacity(stack))
            return;

        updateRoot(stack, root -> {
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            for (Tag t : list) {
                if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                    if (slots != null && slots.length > 0) {
                        entry.putIntArray(KEY_SLOT_MAPPING, slots);
                    } else {
                        entry.remove(KEY_SLOT_MAPPING);
                    }
                    root.put(KEY_ITEMS, list);
                    return;
                }
            }

            if (slots != null && slots.length > 0) {
                CompoundTag entry = new CompoundTag();
                entry.putInt(KEY_SLOT, slot);
                entry.putIntArray(KEY_SLOT_MAPPING, slots);
                list.add(entry);
                root.put(KEY_ITEMS, list);
            }
        });
    }

    public static boolean hasEntrySlotMapping(ItemStack stack, int slot) {
        return getEntrySlotMapping(stack, slot) != null;
    }

    public static boolean entrySlotMappingContains(ItemStack stack, int entrySlot, int inventorySlot) {
        int[] mapping = getEntrySlotMapping(stack, entrySlot);
        if (mapping == null) return true;
        for (int s : mapping) {
            if (s == inventorySlot) return true;
        }
        return false;
    }

    // ── NBT per-slot methods ──

    @Nullable
    public static String getEntryNbtPath(ItemStack stack, int slot) {
        List<NbtConstraint> constraints = getEntryNbtConstraints(stack, slot);
        return constraints.isEmpty() ? null : constraints.get(0).path();
    }

    @Nullable
    public static Tag getEntryNbtValue(ItemStack stack, int slot) {
        List<NbtConstraint> constraints = getEntryNbtConstraints(stack, slot);
        return constraints.isEmpty() ? null : constraints.get(0).value();
    }

    public static void addEntryNbtConstraint(ItemStack stack, int slot, String path, Tag value, @Nullable String op) {
        if (!isFilterItem(stack) || path == null || path.isEmpty() || value == null)
            return;
        if (slot < 0 || slot >= getCapacity(stack))
            return;

        updateRoot(stack, root -> {
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            for (Tag t : list) {
                if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                    List<NbtConstraint> constraints = new ArrayList<>(readNbtConstraints(entry));
                    constraints.removeIf(c -> c.path().equals(path));
                    constraints.add(new NbtConstraint(path, value.copy(), op));
                    writeNbtConstraints(entry, constraints);
                    root.put(KEY_ITEMS, list);
                    return;
                }
            }
        });
    }

    public static void removeEntryNbtConstraint(ItemStack stack, int slot, String path) {
        if (!isFilterItem(stack) || path == null || path.isEmpty())
            return;
        if (slot < 0 || slot >= getCapacity(stack))
            return;

        updateRoot(stack, root -> {
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            for (Tag t : list) {
                if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                    List<NbtConstraint> constraints = new ArrayList<>(readNbtConstraints(entry));
                    constraints.removeIf(c -> c.path().equals(path));
                    writeNbtConstraints(entry, constraints);
                    root.put(KEY_ITEMS, list);
                    return;
                }
            }
        });
    }

    public static void clearAllEntryNbt(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return;
        if (slot < 0 || slot >= getCapacity(stack))
            return;

        updateRoot(stack, root -> {
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            for (Tag t : list) {
                if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                    writeNbtConstraints(entry, List.of());
                    root.put(KEY_ITEMS, list);
                    return;
                }
            }
        });
    }

    public static List<NbtConstraint> getEntryNbtConstraints(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return List.of();
        CompoundTag root = getRoot(stack);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                return readNbtConstraints(entry);
            }
        }
        return List.of();
    }

    public static void setEntryNbt(ItemStack stack, int slot, @Nullable String path, @Nullable Tag value) {
        if (!isFilterItem(stack))
            return;
        if (slot < 0 || slot >= getCapacity(stack))
            return;
        if (path != null && !path.isEmpty() && value != null) {
            addEntryNbtConstraint(stack, slot, path, value, null);
        } else {
            clearAllEntryNbt(stack, slot);
        }
    }

    @Nullable
    public static String getEntryNbtOp(ItemStack stack, int slot) {
        List<NbtConstraint> constraints = getEntryNbtConstraints(stack, slot);
        return constraints.isEmpty() ? null : constraints.get(0).op();
    }

    public static void setEntryNbtOp(ItemStack stack, int slot, String path, @Nullable String op) {
        if (!isFilterItem(stack) || path == null || path.isEmpty())
            return;
        if (slot < 0 || slot >= getCapacity(stack))
            return;

        updateRoot(stack, root -> {
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            for (Tag t : list) {
                if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                    List<NbtConstraint> constraints = new ArrayList<>(readNbtConstraints(entry));
                    for (int i = 0; i < constraints.size(); i++) {
                        if (constraints.get(i).path().equals(path)) {
                            NbtConstraint old = constraints.get(i);
                            constraints.set(i, new NbtConstraint(old.path(), old.value(), op));
                            break;
                        }
                    }
                    writeNbtConstraints(entry, constraints);
                    root.put(KEY_ITEMS, list);
                    return;
                }
            }
        });
    }

    @Deprecated
    public static void setEntryNbtOp(ItemStack stack, int slot, @Nullable String op) {
        List<NbtConstraint> constraints = getEntryNbtConstraints(stack, slot);
        if (!constraints.isEmpty()) {
            setEntryNbtOp(stack, slot, constraints.get(0).path(), op);
        }
    }

    public static boolean hasEntryNbt(ItemStack stack, int slot) {
        return !getEntryNbtConstraints(stack, slot).isEmpty() || getEntryNbtRaw(stack, slot) != null;
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
                    entry.remove(KEY_NBT_ENTRIES);
                    if (rawSnbt != null && !rawSnbt.isEmpty()) {
                        entry.putString(KEY_NBT_RAW, rawSnbt);
                    } else {
                        entry.remove(KEY_NBT_RAW);
                    }
                    root.put(KEY_ITEMS, list);
                    return;
                }
            }
            if (rawSnbt != null && !rawSnbt.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putInt(KEY_SLOT, slot);
                entry.putString(KEY_NBT_RAW, rawSnbt);
                list.add(entry);
                root.put(KEY_ITEMS, list);
            }
        });
    }

    public static CompoundTag buildRawFromPath(String path, Tag value) {
        String[] parts = path.split("\\.");
        CompoundTag leaf = new CompoundTag();
        leaf.put(parts[parts.length - 1], value.copy());
        for (int i = parts.length - 2; i >= 0; i--) {
            CompoundTag wrapper = new CompoundTag();
            wrapper.put(parts[i], leaf);
            leaf = wrapper;
        }
        return leaf;
    }

    public static boolean hasAnyNbtEntries(ItemStack stack) {
        return hasEntryType(stack, KEY_NBT_PATH) || hasEntryType(stack, KEY_NBT_RAW) || hasEntryType(stack, KEY_NBT_ENTRIES);
    }

    public static boolean isNbtOnlySlot(ItemStack stack, int slot) {
        if (!hasEntryNbt(stack, slot) && !hasEntryDurability(stack, slot) && !hasEntryEnchanted(stack, slot))
            return false;
        if (getEntryTag(stack, slot) != null)
            return false;
        if (!getFluidEntry(stack, slot).isEmpty())
            return false;
        CompoundTag root = getRoot(stack);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                if (entry.contains(KEY_ITEM_TAG, Tag.TAG_COMPOUND))
                    return false;
            }
        }
        return true;
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

    public static boolean hasEntryEnchanted(ItemStack stack, int slot) {
        return getEntryEnchanted(stack, slot) != null;
    }

    @Nullable
    public static Boolean getEntryEnchanted(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return null;
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
        if (!isFilterItem(stack))
            return;
        if (slot < 0 || slot >= getCapacity(stack))
            return;
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

    // ── Full matching methods (tag + NBT + durability aware) ──

    public static boolean containsItemFull(ItemStack filter, ItemStack candidate, HolderLookup.Provider provider) {
        return containsItemFull(filter, candidate, provider, null);
    }

    public static boolean containsItemFull(ItemStack filter, ItemStack candidate, HolderLookup.Provider provider,
            @Nullable CompoundTag candidateComponents) {
        return containsItemFull(filter, candidate, provider, candidateComponents, null);
    }

    public static boolean containsItemFull(ItemStack filter, ItemStack candidate, HolderLookup.Provider provider,
            @Nullable CompoundTag candidateComponents, @Nullable ReadCache readCache) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return false;

        ItemFilterView view = getItemFilterView(filter, readCache);
        LOGGER.debug("[containsItemFull] hasItemEntries={}, hasTagEntries={}, candidate={}",
                view.hasItemEntries(), view.hasTagEntries(), candidate.getItem());
        CompoundTag resolvedCandidateComponents = candidateComponents;
        boolean candidateComponentsResolved = candidateComponents != null;
        for (ItemFilterSlot entry : view.entriesBySlot()) {
            if (entry == null)
                continue;

            String tag = entry.tag();
            if (tag != null) {
                if (!candidate.getTags().map(t -> t.location().toString()).anyMatch(tag::equals))
                    continue;
                if (entry.hasNbt()) {
                    if (!candidateComponentsResolved) {
                        resolvedCandidateComponents = NbtFilterData.getSerializedComponents(candidate, provider);
                        candidateComponentsResolved = true;
                    }
                    if (!checkNbtConstraint(entry, resolvedCandidateComponents))
                        continue;
                }
                if (!checkDurabilityConstraint(entry, candidate))
                    continue;
                if (!checkEnchantedConstraint(entry, candidate))
                    continue;
                return true;
            }

            if (entry.nbtOnly()) {
                LOGGER.debug("[nbtOnly] candidate={}, hasNbt={}, constraints={}, rawNbt={}",
                        candidate.getItem(), entry.hasNbt(), entry.nbtConstraints().size(),
                        entry.rawNbt());
                if (entry.hasNbt()) {
                    if (!candidateComponentsResolved) {
                        resolvedCandidateComponents = NbtFilterData.getSerializedComponents(candidate, provider);
                        candidateComponentsResolved = true;
                    }
                    if (!checkNbtConstraint(entry, resolvedCandidateComponents))
                        continue;
                }
                if (!checkDurabilityConstraint(entry, candidate))
                    continue;
                if (!checkEnchantedConstraint(entry, candidate))
                    continue;
                LOGGER.debug("[nbtOnly] MATCHED");
                return true;
            }

            Item itemEntry = entry.item();
            if (itemEntry != null && itemEntry == candidate.getItem()) {
                if (entry.hasNbt()) {
                    if (!candidateComponentsResolved) {
                        resolvedCandidateComponents = NbtFilterData.getSerializedComponents(candidate, provider);
                        candidateComponentsResolved = true;
                    }
                    if (!checkNbtConstraint(entry, resolvedCandidateComponents))
                        continue;
                }
                if (!checkDurabilityConstraint(entry, candidate))
                    continue;
                if (!checkEnchantedConstraint(entry, candidate))
                    continue;
                return true;
            }
        }
        return false;
    }

    public static boolean containsItemFullInSlot(ItemStack filter, ItemStack candidate, int inventorySlot,
            HolderLookup.Provider provider, @Nullable CompoundTag candidateComponents,
            @Nullable ReadCache readCache) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return false;

        ItemFilterView view = getItemFilterView(filter, readCache);
        CompoundTag resolvedCandidateComponents = candidateComponents;
        boolean candidateComponentsResolved = candidateComponents != null;
        for (ItemFilterSlot entry : view.entriesBySlot()) {
            if (entry == null)
                continue;

            if (entry.slotMapping() != null) {
                boolean slotAllowed = false;
                for (int s : entry.slotMapping()) {
                    if (s == inventorySlot) { slotAllowed = true; break; }
                }
                if (!slotAllowed) continue;
            }

            String tag = entry.tag();
            if (tag != null) {
                if (!candidate.getTags().map(t -> t.location().toString()).anyMatch(tag::equals))
                    continue;
                if (entry.hasNbt()) {
                    if (!candidateComponentsResolved) {
                        resolvedCandidateComponents = NbtFilterData.getSerializedComponents(candidate, provider);
                        candidateComponentsResolved = true;
                    }
                    if (!checkNbtConstraint(entry, resolvedCandidateComponents))
                        continue;
                }
                if (!checkDurabilityConstraint(entry, candidate))
                    continue;
                if (!checkEnchantedConstraint(entry, candidate))
                    continue;
                return true;
            }

            if (entry.nbtOnly()) {
                LOGGER.debug("[nbtOnlySlot] candidate={}, slot={}, hasNbt={}, constraints={}, slotMap={}",
                        candidate.getItem(), inventorySlot, entry.hasNbt(), entry.nbtConstraints().size(),
                        entry.slotMapping() != null ? java.util.Arrays.toString(entry.slotMapping()) : "none");
                if (entry.hasNbt()) {
                    if (!candidateComponentsResolved) {
                        resolvedCandidateComponents = NbtFilterData.getSerializedComponents(candidate, provider);
                        candidateComponentsResolved = true;
                    }
                    if (!checkNbtConstraint(entry, resolvedCandidateComponents))
                        continue;
                }
                if (!checkDurabilityConstraint(entry, candidate))
                    continue;
                if (!checkEnchantedConstraint(entry, candidate))
                    continue;
                LOGGER.debug("[nbtOnlySlot] MATCHED");
                return true;
            }

            Item itemEntry = entry.item();
            if (itemEntry != null && itemEntry == candidate.getItem()) {
                if (entry.hasNbt()) {
                    if (!candidateComponentsResolved) {
                        resolvedCandidateComponents = NbtFilterData.getSerializedComponents(candidate, provider);
                        candidateComponentsResolved = true;
                    }
                    if (!checkNbtConstraint(entry, resolvedCandidateComponents))
                        continue;
                }
                if (!checkDurabilityConstraint(entry, candidate))
                    continue;
                if (!checkEnchantedConstraint(entry, candidate))
                    continue;
                return true;
            }
        }
        return false;
    }

    public static boolean hasAnySlotMappings(ItemStack filter, @Nullable ReadCache readCache) {
        if (!isFilterItem(filter))
            return false;
        ItemFilterView view = getItemFilterView(filter, readCache);
        for (ItemFilterSlot entry : view.entriesBySlot()) {
            if (entry != null && entry.slotMapping() != null)
                return true;
        }
        return false;
    }

    public static void collectMappedSlots(ItemStack filter, boolean[] mask, @Nullable ReadCache readCache) {
        if (!isFilterItem(filter))
            return;
        ItemFilterView view = getItemFilterView(filter, readCache);
        for (ItemFilterSlot entry : view.entriesBySlot()) {
            if (entry == null || entry.slotMapping() == null)
                continue;
            for (int s : entry.slotMapping()) {
                if (s >= 0 && s < mask.length) {
                    mask[s] = true;
                }
            }
        }
    }

    public static boolean containsFluidFull(ItemStack filter, FluidStack candidate, HolderLookup.Provider provider) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return false;

        int cap = getCapacity(filter);
        for (int i = 0; i < cap; i++) {
            String tag = getEntryTag(filter, i);
            if (tag != null) {
                if (candidate.getFluid().builtInRegistryHolder().tags()
                        .anyMatch(t -> t.location().toString().equals(tag))) {
                    return true;
                }
                continue;
            }

            FluidStack entry = getFluidEntry(filter, i);
            if (!entry.isEmpty() && entry.isFluidEqual(candidate)) {
                if (!checkNbtConstraint(filter, i, NbtFilterData.getSerializedComponents(candidate, provider)))
                    continue;
                return true;
            }
        }
        return false;
    }

    public static boolean containsChemicalFull(ItemStack filter, String chemicalId) {
        if (!isFilterItem(filter) || chemicalId == null || chemicalId.isEmpty())
            return false;

        int cap = getCapacity(filter);
        for (int i = 0; i < cap; i++) {
            String tag = getEntryTag(filter, i);
            if (tag != null) {
                if (MekanismCompat.chemicalHasTag(chemicalId, tag))
                    return true;
                continue;
            }

            String entry = getChemicalEntry(filter, i);
            if (entry != null && entry.equals(chemicalId))
                return true;
        }
        return false;
    }

    // ── Full amount threshold methods (tag-aware + constraint-aware) ──

    public static int getItemAmountThresholdFull(ItemStack filter, ItemStack candidate,
            HolderLookup.Provider provider) {
        return getItemAmountThresholdFull(filter, candidate, provider, null);
    }

    public static int getItemAmountThresholdFull(ItemStack filter, ItemStack candidate,
            HolderLookup.Provider provider, @Nullable CompoundTag candidateComponents) {
        return getItemAmountThresholdFull(filter, candidate, provider, candidateComponents, null);
    }

    public static int getItemAmountThresholdFull(ItemStack filter, ItemStack candidate,
            HolderLookup.Provider provider, @Nullable CompoundTag candidateComponents,
            @Nullable ReadCache readCache) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return 0;
        ItemFilterView view = getItemFilterView(filter, readCache);
        CompoundTag resolvedCandidateComponents = candidateComponents;
        boolean candidateComponentsResolved = candidateComponents != null;
        for (ItemFilterSlot entry : view.entriesBySlot()) {
            if (entry == null)
                continue;

            String tag = entry.tag();
            if (tag != null) {
                if (!candidate.getTags().map(t -> t.location().toString()).anyMatch(tag::equals))
                    continue;
                if (entry.hasNbt()) {
                    if (!candidateComponentsResolved) {
                        resolvedCandidateComponents = NbtFilterData.getSerializedComponents(candidate, provider);
                        candidateComponentsResolved = true;
                    }
                    if (!checkNbtConstraint(entry, resolvedCandidateComponents))
                        continue;
                }
                if (!checkDurabilityConstraint(entry, candidate))
                    continue;
                if (!checkEnchantedConstraint(entry, candidate))
                    continue;
                return entry.stock();
            }

            if (entry.nbtOnly()) {
                if (entry.hasNbt()) {
                    if (!candidateComponentsResolved) {
                        resolvedCandidateComponents = NbtFilterData.getSerializedComponents(candidate, provider);
                        candidateComponentsResolved = true;
                    }
                    if (!checkNbtConstraint(entry, resolvedCandidateComponents))
                        continue;
                }
                if (!checkDurabilityConstraint(entry, candidate))
                    continue;
                if (!checkEnchantedConstraint(entry, candidate))
                    continue;
                return entry.stock();
            }

            Item itemEntry = entry.item();
            if (itemEntry != null && itemEntry == candidate.getItem()) {
                if (entry.hasNbt()) {
                    if (!candidateComponentsResolved) {
                        resolvedCandidateComponents = NbtFilterData.getSerializedComponents(candidate, provider);
                        candidateComponentsResolved = true;
                    }
                    if (!checkNbtConstraint(entry, resolvedCandidateComponents))
                        continue;
                }
                if (!checkDurabilityConstraint(entry, candidate))
                    continue;
                if (!checkEnchantedConstraint(entry, candidate))
                    continue;
                return entry.stock();
            }
        }
        return 0;
    }

    public static int getItemExportCapFull(ItemStack filter, ItemStack candidate,
            HolderLookup.Provider provider, @Nullable CompoundTag candidateComponents,
            @Nullable ReadCache readCache, @Nullable Map<Item, Integer> sourceCounts) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return -1;
        ItemFilterView view = getItemFilterView(filter, readCache);
        CompoundTag resolved = candidateComponents;
        boolean resolvedDone = candidateComponents != null;
        for (ItemFilterSlot entry : view.entriesBySlot()) {
            if (entry == null)
                continue;

            String tag = entry.tag();
            if (tag != null) {
                if (!candidate.getTags().map(t -> t.location().toString()).anyMatch(tag::equals))
                    continue;
                if (entry.hasNbt()) {
                    if (!resolvedDone) {
                        resolved = NbtFilterData.getSerializedComponents(candidate, provider);
                        resolvedDone = true;
                    }
                    if (!checkNbtConstraint(entry, resolved))
                        continue;
                }
                if (!checkDurabilityConstraint(entry, candidate))
                    continue;
                if (!checkEnchantedConstraint(entry, candidate))
                    continue;
                if (entry.stock() <= 0)
                    return -1;
                int sourceCount = countItemsWithTag(sourceCounts, tag);
                return Math.max(0, sourceCount - entry.stock());
            }

            if (entry.nbtOnly()) {
                if (entry.hasNbt()) {
                    if (!resolvedDone) {
                        resolved = NbtFilterData.getSerializedComponents(candidate, provider);
                        resolvedDone = true;
                    }
                    if (!checkNbtConstraint(entry, resolved))
                        continue;
                }
                if (!checkDurabilityConstraint(entry, candidate))
                    continue;
                if (!checkEnchantedConstraint(entry, candidate))
                    continue;
                if (entry.stock() <= 0)
                    return -1;
                int totalCount = sumAllCounts(sourceCounts);
                return Math.max(0, totalCount - entry.stock());
            }

            Item itemEntry = entry.item();
            if (itemEntry != null && itemEntry == candidate.getItem()) {
                if (entry.hasNbt()) {
                    if (!resolvedDone) {
                        resolved = NbtFilterData.getSerializedComponents(candidate, provider);
                        resolvedDone = true;
                    }
                    if (!checkNbtConstraint(entry, resolved))
                        continue;
                }
                if (!checkDurabilityConstraint(entry, candidate))
                    continue;
                if (!checkEnchantedConstraint(entry, candidate))
                    continue;
                if (entry.stock() <= 0)
                    return -1;
                int count = sourceCounts != null ? sourceCounts.getOrDefault(candidate.getItem(), 0) : 0;
                return Math.max(0, count - entry.stock());
            }
        }
        return -1;
    }

    public static int getItemImportCapFull(ItemStack filter, ItemStack candidate,
            HolderLookup.Provider provider, @Nullable CompoundTag candidateComponents,
            @Nullable ReadCache readCache, @Nullable Map<Item, Integer> targetCounts) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return -1;
        ItemFilterView view = getItemFilterView(filter, readCache);
        CompoundTag resolved = candidateComponents;
        boolean resolvedDone = candidateComponents != null;
        for (ItemFilterSlot entry : view.entriesBySlot()) {
            if (entry == null)
                continue;

            String tag = entry.tag();
            if (tag != null) {
                if (!candidate.getTags().map(t -> t.location().toString()).anyMatch(tag::equals))
                    continue;
                if (entry.hasNbt()) {
                    if (!resolvedDone) {
                        resolved = NbtFilterData.getSerializedComponents(candidate, provider);
                        resolvedDone = true;
                    }
                    if (!checkNbtConstraint(entry, resolved))
                        continue;
                }
                if (!checkDurabilityConstraint(entry, candidate))
                    continue;
                if (!checkEnchantedConstraint(entry, candidate))
                    continue;
                if (entry.stock() <= 0)
                    return -1;
                int matchingCount = countItemsWithTag(targetCounts, tag);
                return Math.max(0, entry.stock() - matchingCount);
            }

            if (entry.nbtOnly()) {
                if (entry.hasNbt()) {
                    if (!resolvedDone) {
                        resolved = NbtFilterData.getSerializedComponents(candidate, provider);
                        resolvedDone = true;
                    }
                    if (!checkNbtConstraint(entry, resolved))
                        continue;
                }
                if (!checkDurabilityConstraint(entry, candidate))
                    continue;
                if (!checkEnchantedConstraint(entry, candidate))
                    continue;
                if (entry.stock() <= 0)
                    return -1;
                int totalCount = sumAllCounts(targetCounts);
                return Math.max(0, entry.stock() - totalCount);
            }

            Item itemEntry = entry.item();
            if (itemEntry != null && itemEntry == candidate.getItem()) {
                if (entry.hasNbt()) {
                    if (!resolvedDone) {
                        resolved = NbtFilterData.getSerializedComponents(candidate, provider);
                        resolvedDone = true;
                    }
                    if (!checkNbtConstraint(entry, resolved))
                        continue;
                }
                if (!checkDurabilityConstraint(entry, candidate))
                    continue;
                if (!checkEnchantedConstraint(entry, candidate))
                    continue;
                if (entry.stock() <= 0)
                    return -1;
                int count = targetCounts != null ? targetCounts.getOrDefault(candidate.getItem(), 0) : 0;
                return Math.max(0, entry.stock() - count);
            }
        }
        return -1;
    }

    private static int countItemsWithTag(@Nullable Map<Item, Integer> counts, String tag) {
        if (counts == null)
            return 0;
        int total = 0;
        for (Map.Entry<Item, Integer> e : counts.entrySet()) {
            if (e.getKey().builtInRegistryHolder().tags()
                    .anyMatch(t -> t.location().toString().equals(tag))) {
                total += e.getValue();
            }
        }
        return total;
    }

    private static int sumAllCounts(@Nullable Map<Item, Integer> counts) {
        if (counts == null)
            return 0;
        int total = 0;
        for (int v : counts.values())
            total += v;
        return total;
    }

    public static int getItemBatchLimitFull(ItemStack filter, ItemStack candidate,
            HolderLookup.Provider provider, @Nullable CompoundTag candidateComponents,
            @Nullable ReadCache readCache) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return 0;
        ItemFilterView view = getItemFilterView(filter, readCache);
        CompoundTag resolvedCandidateComponents = candidateComponents;
        boolean candidateComponentsResolved = candidateComponents != null;
        for (ItemFilterSlot entry : view.entriesBySlot()) {
            if (entry == null)
                continue;

            String tag = entry.tag();
            if (tag != null) {
                if (!candidate.getTags().map(t -> t.location().toString()).anyMatch(tag::equals))
                    continue;
                if (entry.hasNbt()) {
                    if (!candidateComponentsResolved) {
                        resolvedCandidateComponents = NbtFilterData.getSerializedComponents(candidate, provider);
                        candidateComponentsResolved = true;
                    }
                    if (!checkNbtConstraint(entry, resolvedCandidateComponents))
                        continue;
                }
                if (!checkDurabilityConstraint(entry, candidate))
                    continue;
                if (!checkEnchantedConstraint(entry, candidate))
                    continue;
                return entry.batch();
            }

            if (entry.nbtOnly()) {
                if (entry.hasNbt()) {
                    if (!candidateComponentsResolved) {
                        resolvedCandidateComponents = NbtFilterData.getSerializedComponents(candidate, provider);
                        candidateComponentsResolved = true;
                    }
                    if (!checkNbtConstraint(entry, resolvedCandidateComponents))
                        continue;
                }
                if (!checkDurabilityConstraint(entry, candidate))
                    continue;
                if (!checkEnchantedConstraint(entry, candidate))
                    continue;
                return entry.batch();
            }

            Item itemEntry = entry.item();
            if (itemEntry != null && itemEntry == candidate.getItem()) {
                if (entry.hasNbt()) {
                    if (!candidateComponentsResolved) {
                        resolvedCandidateComponents = NbtFilterData.getSerializedComponents(candidate, provider);
                        candidateComponentsResolved = true;
                    }
                    if (!checkNbtConstraint(entry, resolvedCandidateComponents))
                        continue;
                }
                if (!checkDurabilityConstraint(entry, candidate))
                    continue;
                if (!checkEnchantedConstraint(entry, candidate))
                    continue;
                return entry.batch();
            }
        }
        return 0;
    }

    public static int getFluidAmountThresholdFull(ItemStack filter, FluidStack candidate,
            HolderLookup.Provider provider) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return 0;
        int cap = getCapacity(filter);
        for (int i = 0; i < cap; i++) {
            String tag = getEntryTag(filter, i);
            if (tag != null) {
                if (candidate.getFluid().builtInRegistryHolder().tags()
                        .anyMatch(t -> t.location().toString().equals(tag)))
                    return getEntryStock(filter, i);
                continue;
            }

            FluidStack entry = getFluidEntry(filter, i);
            if (!entry.isEmpty() && entry.isFluidEqual(candidate))
                return getEntryStock(filter, i);
        }
        return 0;
    }

    public static int getChemicalAmountThresholdFull(ItemStack filter, String chemicalId) {
        if (!isFilterItem(filter) || chemicalId == null || chemicalId.isEmpty())
            return 0;
        int cap = getCapacity(filter);
        for (int i = 0; i < cap; i++) {
            String tag = getEntryTag(filter, i);
            if (tag != null) {
                if (MekanismCompat.chemicalHasTag(chemicalId, tag))
                    return getEntryStock(filter, i);
                continue;
            }

            String entry = getChemicalEntry(filter, i);
            if (entry != null && entry.equals(chemicalId))
                return getEntryStock(filter, i);
        }
        return 0;
    }

    // ── Constraint helpers ──

    private static boolean checkNbtConstraint(ItemStack filter, int slot, @Nullable CompoundTag components) {
        CompoundTag entry = getEntryData(filter, slot);
        return entry == null || checkNbtConstraint(entry, components);
    }

    private static boolean checkNbtConstraint(ItemFilterSlot entry, @Nullable CompoundTag components) {
        if (!entry.hasNbt())
            return true;
        if (components == null)
            return false;

        if (!entry.nbtConstraints().isEmpty()) {
            for (NbtConstraint c : entry.nbtConstraints()) {
                Tag actual = NbtFilterData.resolvePathValue(components, c.path());
                if (actual == null)
                    return false;
                NbtOperator op = NbtOperator.fromId(c.op());
                if (!compareNbtValues(actual, c.value(), op))
                    return false;
            }
            return true;
        }

        CompoundTag rawNbt = entry.rawNbt();
        if (rawNbt != null)
            return compoundContains(components, rawNbt);
        if (entry.invalidRawNbt())
            return false;

        return true;
    }

    private static boolean checkNbtConstraint(CompoundTag entry, @Nullable CompoundTag components) {
        if (!hasEntryNbt(entry))
            return true;
        if (components == null)
            return false;

        List<NbtConstraint> constraints = readNbtConstraints(entry);
        if (!constraints.isEmpty()) {
            for (NbtConstraint c : constraints) {
                Tag actual = NbtFilterData.resolvePathValue(components, c.path());
                if (actual == null)
                    return false;
                NbtOperator op = NbtOperator.fromId(c.op());
                if (!compareNbtValues(actual, c.value(), op))
                    return false;
            }
            return true;
        }

        String raw = getEntryNbtRaw(entry);
        if (raw != null) {
            try {
                CompoundTag expected = TagParser.parseTag(raw);
                return compoundContains(components, expected);
            } catch (Exception e) {
                return false;
            }
        }

        return true;
    }

    private static boolean compoundContains(CompoundTag actual, CompoundTag expected) {
        for (String key : expected.getAllKeys()) {
            Tag expectedVal = expected.get(key);
            Tag actualVal = actual.get(key);
            if (actualVal == null || expectedVal == null)
                return false;
            if (expectedVal instanceof CompoundTag ec && actualVal instanceof CompoundTag ac) {
                if (!compoundContains(ac, ec))
                    return false;
            } else if (!expectedVal.equals(actualVal)) {
                return false;
            }
        }
        return true;
    }

    private static boolean compareNbtValues(Tag actual, Tag expected, NbtOperator op) {
        if (op == NbtOperator.EQUAL)
            return expected.equals(actual);
        if (op == NbtOperator.NOT_EQUAL)
            return !expected.equals(actual);

        if (actual instanceof net.minecraft.nbt.NumericTag an && expected instanceof net.minecraft.nbt.NumericTag en) {
            double a = an.getAsDouble();
            double e = en.getAsDouble();
            return switch (op) {
                case GREATER_OR_EQUAL -> a >= e;
                case LESS_OR_EQUAL -> a <= e;
                default -> expected.equals(actual);
            };
        }

        int cmp = actual.getAsString().compareTo(expected.getAsString());
        return switch (op) {
            case GREATER_OR_EQUAL -> cmp >= 0;
            case LESS_OR_EQUAL -> cmp <= 0;
            default -> expected.equals(actual);
        };
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

    private static boolean checkDurabilityConstraint(CompoundTag entry, ItemStack candidate) {
        if (!hasEntryDurability(entry))
            return true;
        String durOp = getEntryDurabilityOp(entry);
        if (durOp == null || !candidate.isDamageableItem())
            return true;
        int durVal = getEntryDurabilityValue(entry);
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
        if (enchanted == null)
            return true;
        return candidate.isEnchanted() == enchanted;
    }

    public static int getEntryBatch(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return 0;
        CompoundTag root = getRoot(stack);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                return entry.contains(KEY_BATCH, Tag.TAG_INT) ? entry.getInt(KEY_BATCH) : 0;
            }
        }
        return 0;
    }

    public static int getEntryStock(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return 0;
        CompoundTag root = getRoot(stack);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                if (entry.contains(KEY_STOCK, Tag.TAG_INT))
                    return entry.getInt(KEY_STOCK);
                if (entry.contains(KEY_AMOUNT, Tag.TAG_INT))
                    return entry.getInt(KEY_AMOUNT);
                return 0;
            }
        }
        return 0;
    }

    public static void setEntryBatch(ItemStack stack, int slot, int batch) {
        if (!isFilterItem(stack))
            return;
        if (slot < 0 || slot >= getCapacity(stack))
            return;
        updateRoot(stack, root -> {
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            for (Tag t : list) {
                if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                    if (batch <= 0) {
                        entry.remove(KEY_BATCH);
                    } else {
                        entry.putInt(KEY_BATCH, batch);
                    }
                    root.put(KEY_ITEMS, list);
                    return;
                }
            }
            if (batch > 0) {
                CompoundTag entry = new CompoundTag();
                entry.putInt(KEY_SLOT, slot);
                entry.putInt(KEY_BATCH, batch);
                list.add(entry);
                root.put(KEY_ITEMS, list);
            }
        });
    }

    public static void setEntryStock(ItemStack stack, int slot, int stock) {
        if (!isFilterItem(stack))
            return;
        if (slot < 0 || slot >= getCapacity(stack))
            return;
        updateRoot(stack, root -> {
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            for (Tag t : list) {
                if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                    entry.remove(KEY_AMOUNT);
                    if (stock <= 0) {
                        entry.remove(KEY_STOCK);
                    } else {
                        entry.putInt(KEY_STOCK, stock);
                    }
                    root.put(KEY_ITEMS, list);
                    return;
                }
            }
            if (stock > 0) {
                CompoundTag entry = new CompoundTag();
                entry.putInt(KEY_SLOT, slot);
                entry.putInt(KEY_STOCK, stock);
                list.add(entry);
                root.put(KEY_ITEMS, list);
            }
        });
    }

    public static boolean hasAnyAmountEntries(ItemStack stack) {
        if (!isFilterItem(stack))
            return false;
        ListTag list = getRoot(stack).getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (!(t instanceof CompoundTag c))
                continue;
            if (c.contains(KEY_BATCH, Tag.TAG_INT) && c.getInt(KEY_BATCH) > 0)
                return true;
            if (c.contains(KEY_STOCK, Tag.TAG_INT) && c.getInt(KEY_STOCK) > 0)
                return true;
            if (c.contains(KEY_AMOUNT, Tag.TAG_INT) && c.getInt(KEY_AMOUNT) > 0)
                return true;
        }
        return false;
    }

    public static int getItemAmountThreshold(ItemStack filter, ItemStack candidate, HolderLookup.Provider provider) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return 0;
        int cap = getCapacity(filter);
        for (int i = 0; i < cap; i++) {
            ItemStack entry = getEntry(filter, i, provider);
            if (!entry.isEmpty() && ItemStack.isSameItem(entry, candidate)) {
                return getEntryStock(filter, i);
            }
        }
        return 0;
    }

    public static int getFluidAmountThreshold(ItemStack filter, FluidStack candidate) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return 0;
        int cap = getCapacity(filter);
        for (int i = 0; i < cap; i++) {
            FluidStack entry = getFluidEntry(filter, i);
            if (!entry.isEmpty() && entry.isFluidEqual(candidate)) {
                return getEntryStock(filter, i);
            }
        }
        return 0;
    }

    public static int getChemicalAmountThreshold(ItemStack filter, String chemicalId) {
        if (!isFilterItem(filter) || chemicalId == null || chemicalId.isEmpty())
            return 0;
        int cap = getCapacity(filter);
        for (int i = 0; i < cap; i++) {
            String entry = getChemicalEntry(filter, i);
            if (entry != null && entry.equals(chemicalId)) {
                return getEntryStock(filter, i);
            }
        }
        return 0;
    }

    public static List<String> getWarnings(ItemStack stack) {
        List<String> warnings = new ArrayList<>();
        if (!isFilterItem(stack))
            return warnings;

        int cap = getCapacity(stack);
        for (int i = 0; i < cap; i++) {
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

    private static void removeFromList(ListTag list, int slot) {
        list.removeIf(t -> t instanceof CompoundTag c && c.getInt(KEY_SLOT) == slot);
    }

    private static ItemFilterView getItemFilterView(ItemStack stack, @Nullable ReadCache readCache) {
        if (readCache == null) {
            return buildItemFilterView(stack);
        }

        ItemFilterView cached = readCache.itemViews.get(stack);
        if (cached != null) {
            return cached;
        }

        ItemFilterView built = buildItemFilterView(stack);
        readCache.itemViews.put(stack, built);
        return built;
    }

    private static ItemFilterView buildItemFilterView(ItemStack stack) {
        int cap = getCapacity(stack);
        ItemFilterSlot[] entriesBySlot = new ItemFilterSlot[Math.max(cap, 0)];
        if (!isFilterItem(stack) || cap <= 0) {
            return new ItemFilterView(false, false, false, false, false, false, false, entriesBySlot);
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
            List<NbtConstraint> nbtConstraints = readNbtConstraints(entry);
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
            Boolean enchanted = entry.contains(KEY_ENCHANTED, Tag.TAG_BYTE)
                    ? entry.getBoolean(KEY_ENCHANTED) : null;
            boolean hasNbt = !nbtConstraints.isEmpty() || raw != null;
            boolean nbtOnly = (hasNbt || durOp != null || enchanted != null || batch > 0 || stock > 0)
                    && tag == null && item == null && !hasFluid && !hasChemical;
            int[] slotMapping = entry.contains(KEY_SLOT_MAPPING, Tag.TAG_INT_ARRAY)
                    ? entry.getIntArray(KEY_SLOT_MAPPING)
                    : null;
            if (slotMapping != null && slotMapping.length == 0) slotMapping = null;

            entriesBySlot[slot] = new ItemFilterSlot(tag, item, batch, stock, nbtConstraints, rawNbt,
                    invalidRawNbt, durOp, durVal, hasNbt, nbtOnly, slotMapping, enchanted);

            hasItemEntries |= item != null || nbtOnly;
            hasFluidEntries |= hasFluid;
            hasChemicalEntries |= hasChemical;
            hasTagEntries |= tag != null;
            hasNbtEntries |= hasNbt;
            hasAmountEntries |= batch > 0 || stock > 0 || enchanted != null;
        }

        return new ItemFilterView(blacklist, hasItemEntries, hasFluidEntries, hasChemicalEntries,
                hasTagEntries, hasNbtEntries, hasAmountEntries, entriesBySlot);
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
    private static String getEntryNbtOp(CompoundTag entry) {
        return entry.contains(KEY_NBT_OP, Tag.TAG_STRING) ? entry.getString(KEY_NBT_OP) : null;
    }

    @Nullable
    private static String getEntryDurabilityOp(CompoundTag entry) {
        return entry.contains(KEY_DUR_OP, Tag.TAG_STRING) ? entry.getString(KEY_DUR_OP) : null;
    }

    private static int getEntryDurabilityValue(CompoundTag entry) {
        return entry.contains(KEY_DUR_VAL, Tag.TAG_INT) ? entry.getInt(KEY_DUR_VAL) : 0;
    }

    private static int getEntryBatch(CompoundTag entry) {
        return entry.contains(KEY_BATCH, Tag.TAG_INT) ? entry.getInt(KEY_BATCH) : 0;
    }

    private static int getEntryStock(CompoundTag entry) {
        if (entry.contains(KEY_STOCK, Tag.TAG_INT))
            return entry.getInt(KEY_STOCK);
        if (entry.contains(KEY_AMOUNT, Tag.TAG_INT))
            return entry.getInt(KEY_AMOUNT);
        return 0;
    }

    private static List<NbtConstraint> readNbtConstraints(CompoundTag entry) {
        if (entry.contains(KEY_NBT_ENTRIES, Tag.TAG_LIST)) {
            ListTag list = entry.getList(KEY_NBT_ENTRIES, Tag.TAG_COMPOUND);
            List<NbtConstraint> constraints = new ArrayList<>(list.size());
            for (Tag t : list) {
                if (t instanceof CompoundTag c) {
                    String path = c.getString(KEY_NBT_PATH);
                    Tag value = c.get(KEY_NBT_VALUE);
                    String op = c.contains(KEY_NBT_OP, Tag.TAG_STRING) ? c.getString(KEY_NBT_OP) : null;
                    if (!path.isEmpty() && value != null) {
                        constraints.add(new NbtConstraint(path, value, op));
                    }
                }
            }
            return constraints;
        }
        String path = getEntryNbtPath(entry);
        Tag value = getEntryNbtValue(entry);
        if (path != null && value != null) {
            String op = getEntryNbtOp(entry);
            return List.of(new NbtConstraint(path, value, op));
        }
        return List.of();
    }

    private static void writeNbtConstraints(CompoundTag entry, List<NbtConstraint> constraints) {
        entry.remove(KEY_NBT_PATH);
        entry.remove(KEY_NBT_VALUE);
        entry.remove(KEY_NBT_OP);

        if (constraints.isEmpty()) {
            entry.remove(KEY_NBT_ENTRIES);
            entry.remove(KEY_NBT_RAW);
            return;
        }

        ListTag list = new ListTag();
        for (NbtConstraint c : constraints) {
            CompoundTag ct = new CompoundTag();
            ct.putString(KEY_NBT_PATH, c.path());
            ct.put(KEY_NBT_VALUE, c.value().copy());
            if (c.op() != null && !c.op().isEmpty()) {
                ct.putString(KEY_NBT_OP, c.op());
            }
            list.add(ct);
        }
        entry.put(KEY_NBT_ENTRIES, list);

        CompoundTag merged = new CompoundTag();
        for (NbtConstraint c : constraints) {
            mergeInto(merged, buildRawFromPath(c.path(), c.value()));
        }
        entry.putString(KEY_NBT_RAW, merged.toString());
    }

    private static void mergeInto(CompoundTag target, CompoundTag source) {
        for (String key : source.getAllKeys()) {
            Tag srcVal = source.get(key);
            if (srcVal instanceof CompoundTag sc && target.contains(key, Tag.TAG_COMPOUND)) {
                mergeInto(target.getCompound(key), sc);
            } else if (srcVal != null) {
                target.put(key, srcVal.copy());
            }
        }
    }

    private static boolean hasEntryNbt(CompoundTag entry) {
        return !readNbtConstraints(entry).isEmpty() || getEntryNbtRaw(entry) != null;
    }

    private static boolean hasEntryDurability(CompoundTag entry) {
        return getEntryDurabilityOp(entry) != null;
    }

    private static CompoundTag getRoot(ItemStack stack) {
        CompoundTag custom = ItemDataUtil.getCustomData(stack);
        return custom.contains(KEY_ROOT, Tag.TAG_COMPOUND) ? custom.getCompound(KEY_ROOT) : new CompoundTag();
    }

    private static void updateRoot(ItemStack stack, Consumer<CompoundTag> modifier) {
        ItemDataUtil.updateCustomData(stack, customTag -> {
            CompoundTag root = customTag.contains(KEY_ROOT, Tag.TAG_COMPOUND)
                    ? customTag.getCompound(KEY_ROOT)
                    : new CompoundTag();

            modifier.accept(root);

            if (root.isEmpty()) {
                customTag.remove(KEY_ROOT);
            } else {
                customTag.put(KEY_ROOT, root);
            }
        });
    }
}
