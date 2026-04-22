package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.item.BaseFilterItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;
import java.util.List;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.function.Consumer;
import net.minecraft.nbt.TagParser;
import org.jetbrains.annotations.Nullable;

public final class FilterItemData {

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
    private static final String KEY_NBT_OP = "nbt_op";
    private static final String KEY_DUR_OP = "dur_op";
    private static final String KEY_DUR_VAL = "dur_val";
    private static final String KEY_NBT_RAW = "nbt_raw";
    private static final String KEY_SLOT_MAPPING = "slot_map";
    private static final String KEY_ENCHANTED = "enchanted";
    private static final String KEY_NBT_RULES = "nbt_rules";
    private static final String KEY_NBT_MATCH_ANY = "nbt_match_any";
    private static final String KEY_RULE_P = "p";
    private static final String KEY_RULE_O = "o";
    private static final String KEY_RULE_V = "v";
    private static final int MAX_NBT_RULES_PER_SLOT = 6;
    private static final String NBT_OP_EQUALS = "=";
    private static final String NBT_OP_NOT_EQUALS = "!=";
    private static final String NBT_OP_GT = ">";
    private static final String NBT_OP_LT = "<";
    private static final String NBT_OP_GTE = ">=";
    private static final String NBT_OP_LTE = "<=";
    private static final String[] NBT_OPS = { "=", "!=", ">", "<", ">=", "<=" };

    public static final class ReadCache {
        private final IdentityHashMap<ItemStack, ItemFilterView> itemViews = new IdentityHashMap<>();

        private ReadCache() {
        }
    }

    private record ItemFilterSlot(
            @Nullable String tag,
            @Nullable Item item,
            @Nullable String chemicalId,
            @Nullable FluidStack fluidEntry,
            int batch,
            int stock,
            @Nullable String nbtPath,
            @Nullable Tag nbtValue,
            @Nullable String nbtOp,
            @Nullable CompoundTag rawNbt,
            boolean invalidRawNbt,
            @Nullable String durOp,
            int durVal,
            boolean hasNbt,
            boolean nbtOnly,
            List<SlotNbtRule> nbtRules,
            boolean nbtMatchAny,
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

    public record SlotNbtRule(String path, String operator, Tag value) {
        public String displayText() {
            String val = value != null ? value.toString() : "";
            return path + " " + operator + " " + val;
        }
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
        return getRoot(stack).getBooleanOr(KEY_IS_BLACKLIST, false);
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
        return FilterTargetType.fromOrdinal(getRoot(stack).getIntOr(KEY_TARGET_TYPE, 0));
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
        ListTag list = getItemEntries(root);

        for (Tag t : list) {
            if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                if (entry.contains(KEY_ITEM_TAG)) {
                    RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, provider);
                    return entry.read(KEY_ITEM_TAG, ItemStack.OPTIONAL_CODEC, ops).orElse(ItemStack.EMPTY);
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

        ItemStack item = (value == null || value.isEmpty()) ? ItemStack.EMPTY : value.copyWithCount(1);
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, provider);

        updateRoot(stack, root -> {
            ListTag list = getItemEntries(root);

            CompoundTag existing = null;
            int existingIdx = -1;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) instanceof CompoundTag c && getSlotIndex(c) == slot) {
                    existing = c;
                    existingIdx = i;
                    break;
                }
            }

            if (!item.isEmpty()) {
                if (existing != null) {
                    existing.store(KEY_ITEM_TAG, ItemStack.OPTIONAL_CODEC, ops, item);
                } else {
                    CompoundTag entry = new CompoundTag();
                    entry.putInt(KEY_SLOT, slot);
                    entry.store(KEY_ITEM_TAG, ItemStack.OPTIONAL_CODEC, ops, item);
                    list.add(entry);
                }
            } else if (existing != null) {
                list.remove(existingIdx);
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
            ListTag list = getItemEntries(root);
            for (Tag t : list) {
                if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
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
        ListTag list = getItemEntries(root);

        for (Tag t : list) {
            if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                if (entry.contains(KEY_FLUID_ID)) {
                    Identifier id = Identifier.tryParse(entry.getStringOr(KEY_FLUID_ID, ""));
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

        Identifier id = (fluid != null && !fluid.isEmpty())
                ? BuiltInRegistries.FLUID.getKey(fluid.getFluid())
                : null;
        int existingBatch = getEntryBatch(stack, slot);
        int existingStock = getEntryStock(stack, slot);

        updateRoot(stack, root -> {
            ListTag list = getItemEntries(root);
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
        ListTag list = getItemEntries(root);
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

    public static boolean hasAnyFluidEntries(ItemStack stack, @Nullable ReadCache readCache) {
        if (!isFilterItem(stack))
            return false;
        if (readCache == null)
            return hasEntryType(stack, KEY_FLUID_ID);
        return getItemFilterView(stack, readCache).hasFluidEntries();
    }

    private static boolean hasEntryType(ItemStack stack, String key) {
        if (!isFilterItem(stack))
            return false;
        ListTag list = getItemEntries(getRoot(stack));
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
        ListTag list = getItemEntries(root);
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
            if (!entry.isEmpty() && FluidStack.isSameFluidSameComponents(entry, candidate)) {
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
        ListTag list = getItemEntries(root);

        for (Tag t : list) {
            if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                if (entry.contains(KEY_CHEMICAL_ID)) {
                    return entry.getStringOr(KEY_CHEMICAL_ID, "");
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
            ListTag list = getItemEntries(root);
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

    public static boolean hasAnyChemicalEntries(ItemStack stack, @Nullable ReadCache readCache) {
        if (!isFilterItem(stack))
            return false;
        if (readCache == null)
            return hasEntryType(stack, KEY_CHEMICAL_ID);
        return getItemFilterView(stack, readCache).hasChemicalEntries();
    }

    // ── Tag per-slot methods ──

    @Nullable
    public static String getEntryTag(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return null;
        CompoundTag root = getRoot(stack);
        ListTag list = getItemEntries(root);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                if (entry.contains(KEY_TAG)) {
                    return FilterTagUtil.normalizeTag(entry.getStringOr(KEY_TAG, ""));
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
            ListTag list = getItemEntries(root);

            CompoundTag existing = null;
            for (Tag t : list) {
                if (t instanceof CompoundTag c && getSlotIndex(c) == slot) {
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

    public static boolean hasAnyTagEntries(ItemStack stack, @Nullable ReadCache readCache) {
        if (!isFilterItem(stack))
            return false;
        if (readCache == null)
            return hasEntryType(stack, KEY_TAG);
        return getItemFilterView(stack, readCache).hasTagEntries();
    }

    public static boolean hasAnyAmountEntries(ItemStack stack, @Nullable ReadCache readCache) {
        if (!isFilterItem(stack))
            return false;
        return getItemFilterView(stack, readCache).hasAmountEntries();
    }

    // ── Batch/Stock per-slot methods ──

    public static int getEntryBatch(ItemStack stack, int slot) {
        if (!isFilterItem(stack)) return 0;
        CompoundTag root = getRoot(stack);
        ListTag list = getItemEntries(root);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                return entry.contains(KEY_BATCH) ? entry.getIntOr(KEY_BATCH, 0) : 0;
            }
        }
        return 0;
    }

    public static void setEntryBatch(ItemStack stack, int slot, int batch) {
        if (!isFilterItem(stack)) return;
        if (slot < 0 || slot >= getCapacity(stack)) return;

        updateRoot(stack, root -> {
            ListTag list = getItemEntries(root);
            for (Tag t : list) {
                if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
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

    public static int getEntryStock(ItemStack stack, int slot) {
        if (!isFilterItem(stack)) return 0;
        CompoundTag root = getRoot(stack);
        ListTag list = getItemEntries(root);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                if (entry.contains(KEY_STOCK)) return entry.getIntOr(KEY_STOCK, 0);
                if (entry.contains(KEY_AMOUNT)) return entry.getIntOr(KEY_AMOUNT, 0);
                return 0;
            }
        }
        return 0;
    }

    public static void setEntryStock(ItemStack stack, int slot, int stock) {
        if (!isFilterItem(stack)) return;
        if (slot < 0 || slot >= getCapacity(stack)) return;

        updateRoot(stack, root -> {
            ListTag list = getItemEntries(root);
            for (Tag t : list) {
                if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
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

    // ── Slot mapping per-entry methods ──

    @Nullable
    public static int[] getEntrySlotMapping(ItemStack stack, int slot) {
        if (!isFilterItem(stack)) return null;
        CompoundTag root = getRoot(stack);
        ListTag list = getItemEntries(root);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                if (entry.contains(KEY_SLOT_MAPPING)) {
                    int[] mapping = entry.getIntArray(KEY_SLOT_MAPPING).orElse(new int[0]);
                    return mapping.length > 0 ? mapping : null;
                }
            }
        }
        return null;
    }

    public static String getEntrySlotMappingExpression(ItemStack stack, int slot) {
        int[] mapping = getEntrySlotMapping(stack, slot);
        if (mapping == null) return "";
        List<Integer> sorted = new ArrayList<>();
        for (int s : mapping) sorted.add(s);
        return SlotExpressionUtil.formatSlots(sorted);
    }

    public static void setEntrySlotMapping(ItemStack stack, int slot, @Nullable int[] slots) {
        if (!isFilterItem(stack)) return;
        if (slot < 0 || slot >= getCapacity(stack)) return;

        updateRoot(stack, root -> {
            ListTag list = getItemEntries(root);
            for (Tag t : list) {
                if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
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

    public static boolean hasAnySlotMappings(ItemStack filter, @Nullable ReadCache readCache) {
        if (!isFilterItem(filter)) return false;
        ItemFilterView view = getItemFilterView(filter, readCache);
        for (ItemFilterSlot entry : view.entriesBySlot()) {
            if (entry != null && entry.slotMapping() != null) return true;
        }
        return false;
    }

    public static void collectMappedSlots(ItemStack filter, boolean[] mask, @Nullable ReadCache readCache) {
        if (!isFilterItem(filter)) return;
        ItemFilterView view = getItemFilterView(filter, readCache);
        for (ItemFilterSlot entry : view.entriesBySlot()) {
            if (entry == null || entry.slotMapping() == null) continue;
            for (int s : entry.slotMapping()) {
                if (s >= 0 && s < mask.length) mask[s] = true;
            }
        }
    }

    // ── Enchanted per-entry methods ──

    @Nullable
    public static Boolean getEntryEnchanted(ItemStack stack, int slot) {
        if (!isFilterItem(stack)) return null;
        CompoundTag root = getRoot(stack);
        ListTag list = getItemEntries(root);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                if (entry.contains(KEY_ENCHANTED)) {
                    return entry.getBooleanOr(KEY_ENCHANTED, false);
                }
            }
        }
        return null;
    }

    public static void setEntryEnchanted(ItemStack stack, int slot, @Nullable Boolean value) {
        if (!isFilterItem(stack)) return;
        if (slot < 0 || slot >= getCapacity(stack)) return;

        updateRoot(stack, root -> {
            ListTag list = getItemEntries(root);
            for (Tag t : list) {
                if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
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
        ListTag list = getItemEntries(root);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                if (entry.contains(KEY_NBT_PATH)) {
                    return entry.getStringOr(KEY_NBT_PATH, "");
                }
            }
        }
        return null;
    }

    @Nullable
    public static Tag getEntryNbtValue(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return null;
        CompoundTag root = getRoot(stack);
        ListTag list = getItemEntries(root);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                if (entry.contains(KEY_NBT_VALUE)) {
                    return entry.get(KEY_NBT_VALUE);
                }
            }
        }
        return null;
    }

    public static void setEntryNbt(ItemStack stack, int slot, @Nullable String path, @Nullable Tag value) {
        setEntryNbt(stack, slot, path, value, NBT_OP_EQUALS);
    }

    public static void setEntryNbt(ItemStack stack, int slot, @Nullable String path, @Nullable Tag value,
            @Nullable String operator) {
        if (!isFilterItem(stack))
            return;
        if (slot < 0 || slot >= getCapacity(stack))
            return;

        String normalizedOperator = normalizeNbtOperator(operator);

        updateRoot(stack, root -> {
            ListTag list = getItemEntries(root);
            for (Tag t : list) {
                if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                    if (path != null && !path.isEmpty() && value != null) {
                        entry.remove(KEY_NBT_RAW);
                        entry.putString(KEY_NBT_PATH, path);
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
        ListTag list = getItemEntries(root);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
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
        ListTag list = getItemEntries(root);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                if (entry.contains(KEY_NBT_RAW)) {
                    String raw = entry.getStringOr(KEY_NBT_RAW, "");
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
            ListTag list = getItemEntries(root);
            for (Tag t : list) {
                if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
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

    public static boolean isNbtOnlySlot(ItemStack stack, int slot) {
        if (!hasEntryNbt(stack, slot) && !hasEntryDurability(stack, slot) && !hasEntryEnchanted(stack, slot))
            return false;
        return getEntryTag(stack, slot) == null
                && !hasEntryItem(stack, slot)
                && getFluidEntry(stack, slot).isEmpty();
    }

    private static boolean hasEntryItem(ItemStack stack, int slot) {
        CompoundTag root = getRoot(stack);
        ListTag list = getItemEntries(root);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                return entry.contains(KEY_ITEM_TAG);
            }
        }
        return false;
    }

    // ── Multi-rule NBT per-slot methods ──

    public static List<SlotNbtRule> getSlotNbtRules(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return List.of();
        CompoundTag root = getRoot(stack);
        ListTag list = getItemEntries(root);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
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
            ListTag items = getItemEntries(root);
            CompoundTag entry = null;
            for (Tag t : items) {
                if (t instanceof CompoundTag c && getSlotIndex(c) == slot) {
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
            ListTag rules = entry.contains(KEY_NBT_RULES)
                    ? entry.getListOrEmpty(KEY_NBT_RULES)
                    : new ListTag();

            if (rules.size() >= MAX_NBT_RULES_PER_SLOT)
                return;

            for (Tag rt : rules) {
                if (rt instanceof CompoundTag r && path.equals(r.getStringOr(KEY_RULE_P, ""))
                        && op.equals(r.contains(KEY_RULE_O) ? r.getStringOr(KEY_RULE_O, NBT_OP_EQUALS) : NBT_OP_EQUALS)) {
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
            ListTag items = getItemEntries(root);
            for (Tag t : items) {
                if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                    migrateToNbtRules(entry);
                    ListTag rules = entry.getListOrEmpty(KEY_NBT_RULES);
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
            ListTag items = getItemEntries(root);
            for (Tag t : items) {
                if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
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
        ListTag list = getItemEntries(root);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                return entry.getBooleanOr(KEY_NBT_MATCH_ANY, false);
            }
        }
        return false;
    }

    public static void toggleSlotNbtMatchMode(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return;

        updateRoot(stack, root -> {
            ListTag items = getItemEntries(root);
            for (Tag t : items) {
                if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                    boolean current = entry.getBooleanOr(KEY_NBT_MATCH_ANY, false);
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
            ListTag items = getItemEntries(root);
            for (Tag t : items) {
                if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                    if (!entry.contains(KEY_NBT_RULES))
                        return;
                    ListTag rules = entry.getListOrEmpty(KEY_NBT_RULES);
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
        if (entry.contains(KEY_NBT_RULES)) {
            ListTag rules = entry.getListOrEmpty(KEY_NBT_RULES);
            List<SlotNbtRule> result = new ArrayList<>(rules.size());
            for (Tag t : rules) {
                if (t instanceof CompoundTag r) {
                    String p = r.getStringOr(KEY_RULE_P, "");
                    String o = r.contains(KEY_RULE_O) ? r.getStringOr(KEY_RULE_O, NBT_OP_EQUALS) : NBT_OP_EQUALS;
                    Tag v = r.get(KEY_RULE_V);
                    if (!p.isEmpty() && v != null) {
                        result.add(new SlotNbtRule(p, normalizeNbtOperator(o), v.copy()));
                    }
                }
            }
            return result;
        }

        String path = getEntryNbtPath(entry);
        Tag value = getEntryNbtValue(entry);
        if (path != null && value != null) {
            String op = getEntryNbtOperator(entry);
            return List.of(new SlotNbtRule(path, normalizeNbtOperator(op), value.copy()));
        }

        return List.of();
    }

    private static void migrateToNbtRules(CompoundTag entry) {
        if (entry.contains(KEY_NBT_RULES))
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
        ListTag list = getItemEntries(root);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                if (entry.contains(KEY_DUR_OP)) {
                    return entry.getStringOr(KEY_DUR_OP, "");
                }
            }
        }
        return null;
    }

    public static int getEntryDurabilityValue(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return 0;
        CompoundTag root = getRoot(stack);
        ListTag list = getItemEntries(root);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                if (entry.contains(KEY_DUR_VAL)) {
                    return entry.getIntOr(KEY_DUR_VAL, 0);
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
            ListTag list = getItemEntries(root);
            for (Tag t : list) {
                if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
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
        CompoundTag resolvedCandidateComponents = candidateComponents;
        boolean candidateComponentsResolved = candidateComponents != null;
        for (ItemFilterSlot entry : view.entriesBySlot()) {
            if (entry == null)
                continue;

            String tag = entry.tag();
            if (tag != null) {
                if (candidate.typeHolder().tags().map(t -> t.location().toString()).anyMatch(tag::equals)) {
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
                continue;
            }

            if (entry.nbtOnly()) {
                if (!candidateComponentsResolved) {
                    resolvedCandidateComponents = NbtFilterData.getSerializedComponents(candidate, provider);
                    candidateComponentsResolved = true;
                }
                if (!checkNbtConstraint(entry, resolvedCandidateComponents))
                    continue;
                if (!checkDurabilityConstraint(entry, candidate))
                    continue;
                if (!checkEnchantedConstraint(entry, candidate))
                    continue;
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

    public static boolean containsItemFullInSlot(ItemStack filter, ItemStack candidate, HolderLookup.Provider provider,
            @Nullable CompoundTag candidateComponents, @Nullable ReadCache readCache, int inventorySlot) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return false;

        ItemFilterView view = getItemFilterView(filter, readCache);
        CompoundTag resolvedCandidateComponents = candidateComponents;
        boolean candidateComponentsResolved = candidateComponents != null;
        for (ItemFilterSlot entry : view.entriesBySlot()) {
            if (entry == null)
                continue;

            if (inventorySlot >= 0 && entry.slotMapping() != null) {
                boolean found = false;
                for (int s : entry.slotMapping()) {
                    if (s == inventorySlot) { found = true; break; }
                }
                if (!found) continue;
            }

            String tag = entry.tag();
            if (tag != null) {
                if (candidate.typeHolder().tags().map(t -> t.location().toString()).anyMatch(tag::equals)) {
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
                continue;
            }

            if (entry.nbtOnly()) {
                if (!candidateComponentsResolved) {
                    resolvedCandidateComponents = NbtFilterData.getSerializedComponents(candidate, provider);
                    candidateComponentsResolved = true;
                }
                if (!checkNbtConstraint(entry, resolvedCandidateComponents))
                    continue;
                if (!checkDurabilityConstraint(entry, candidate))
                    continue;
                if (!checkEnchantedConstraint(entry, candidate))
                    continue;
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

    public static boolean containsFluidFull(ItemStack filter, FluidStack candidate, HolderLookup.Provider provider) {
        return containsFluidFull(filter, candidate, provider, null);
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
            if (tag != null) {
                if (candidate.typeHolder().tags().map(t -> t.location().toString()).anyMatch(tag::equals)) {
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

    public static boolean containsChemicalFull(ItemStack filter, String chemicalId) {
        return containsChemicalFull(filter, chemicalId, null);
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
            String entryId = slot.chemicalId();
            if (entryId != null && entryId.equals(chemicalId))
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
            HolderLookup.Provider provider, @Nullable CompoundTag candidateComponents, @Nullable ReadCache readCache) {
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
                if (candidate.typeHolder().tags().map(t -> t.location().toString()).anyMatch(tag::equals))
                    return entry.stock();
                continue;
            }

            if (entry.nbtOnly()) {
                if (!candidateComponentsResolved) {
                    resolvedCandidateComponents = NbtFilterData.getSerializedComponents(candidate, provider);
                    candidateComponentsResolved = true;
                }
                if (checkNbtConstraint(entry, resolvedCandidateComponents)
                        && checkDurabilityConstraint(entry, candidate)
                        && checkEnchantedConstraint(entry, candidate))
                    return entry.stock();
                continue;
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

    public static int getItemBatchLimitFull(ItemStack filter, ItemStack candidate,
            HolderLookup.Provider provider, @Nullable CompoundTag candidateComponents, @Nullable ReadCache readCache) {
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
                if (candidate.typeHolder().tags().map(t -> t.location().toString()).anyMatch(tag::equals))
                    return entry.batch();
                continue;
            }

            if (entry.nbtOnly()) {
                if (!candidateComponentsResolved) {
                    resolvedCandidateComponents = NbtFilterData.getSerializedComponents(candidate, provider);
                    candidateComponentsResolved = true;
                }
                if (checkNbtConstraint(entry, resolvedCandidateComponents)
                        && checkDurabilityConstraint(entry, candidate)
                        && checkEnchantedConstraint(entry, candidate))
                    return entry.batch();
                continue;
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
        return getFluidAmountThresholdFull(filter, candidate, provider, null);
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
                if (candidate.typeHolder().tags().map(t -> t.location().toString()).anyMatch(tag::equals))
                    return slot.stock();
                continue;
            }

            FluidStack entry = slot.fluidEntry();
            if (entry != null && !entry.isEmpty() && FluidStack.isSameFluidSameComponents(entry, candidate))
                return slot.stock();
        }
        return 0;
    }

    public static int getChemicalAmountThresholdFull(ItemStack filter, String chemicalId) {
        return getChemicalAmountThresholdFull(filter, chemicalId, null);
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

            String entryId = slot.chemicalId();
            if (entryId != null && entryId.equals(chemicalId))
                return slot.stock();
        }
        return 0;
    }

    public static int getFluidBatchLimitFull(ItemStack filter, FluidStack candidate) {
        return getFluidBatchLimitFull(filter, candidate, null);
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
                if (candidate.typeHolder().tags().map(t -> t.location().toString()).anyMatch(tag::equals))
                    return slot.batch();
                continue;
            }

            FluidStack entry = slot.fluidEntry();
            if (entry != null && !entry.isEmpty() && FluidStack.isSameFluidSameComponents(entry, candidate))
                return slot.batch();
        }
        return 0;
    }

    public static int getChemicalBatchLimitFull(ItemStack filter, String chemicalId) {
        return getChemicalBatchLimitFull(filter, chemicalId, null);
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

            String entryId = slot.chemicalId();
            if (entryId != null && entryId.equals(chemicalId))
                return slot.batch();
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
            return compoundContains(components, rawNbt);
        }
        if (entry.invalidRawNbt()) {
            return false;
        }

        String nbtPath = entry.nbtPath();
        Tag nbtExpected = entry.nbtValue();
        if (nbtPath == null || nbtExpected == null)
            return true;
        Tag actual = NbtFilterData.resolvePathValue(components, nbtPath);
        return matchesNbtValue(entry.nbtOp(), nbtExpected, actual);
    }

    private static boolean checkNbtConstraint(CompoundTag entry, @Nullable CompoundTag components) {
        if (!hasEntryNbt(entry))
            return true;
        if (components == null)
            return false;

        List<SlotNbtRule> rules = readSlotNbtRules(entry);
        if (!rules.isEmpty()) {
            boolean matchAny = entry.getBooleanOr(KEY_NBT_MATCH_ANY, false);
            for (SlotNbtRule rule : rules) {
                Tag actual = NbtFilterData.resolvePathValue(components, rule.path());
                boolean matches = matchesNbtValue(rule.operator(), rule.value(), actual);
                if (matchAny && matches) return true;
                if (!matchAny && !matches) return false;
            }
            return !matchAny;
        }

        String raw = getEntryNbtRaw(entry);
        if (raw != null) {
            try {
                CompoundTag expected = TagParser.parseCompoundFully(raw);
                return compoundContains(components, expected);
            } catch (Exception e) {
                return false;
            }
        }

        String nbtPath = getEntryNbtPath(entry);
        Tag nbtExpected = getEntryNbtValue(entry);
        if (nbtPath == null || nbtExpected == null)
            return true;
        Tag actual = NbtFilterData.resolvePathValue(components, nbtPath);
        return matchesNbtValue(getEntryNbtOperator(entry), nbtExpected, actual);
    }

    private static boolean compoundContains(CompoundTag actual, CompoundTag expected) {
        for (String key : expected.keySet()) {
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

    private static boolean checkDurabilityConstraint(ItemStack filter, int slot, ItemStack candidate) {
        CompoundTag entry = getEntryData(filter, slot);
        return entry == null || checkDurabilityConstraint(entry, candidate);
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

    public static int getEntryAmount(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return 0;

        CompoundTag root = getRoot(stack);
        ListTag list = getItemEntries(root);

        for (Tag t : list) {
            if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                return entry.contains(KEY_AMOUNT) ? entry.getIntOr(KEY_AMOUNT, 0) : 0;
            }
        }
        return 0;
    }

    public static void setEntryAmount(ItemStack stack, int slot, int amount) {
        if (!isFilterItem(stack))
            return;
        if (slot < 0 || slot >= getCapacity(stack))
            return;

        updateRoot(stack, root -> {
            ListTag list = getItemEntries(root);

            for (Tag t : list) {
                if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                    if (amount <= 0) {
                        entry.remove(KEY_AMOUNT);
                    } else {
                        entry.putInt(KEY_AMOUNT, amount);
                    }
                    root.put(KEY_ITEMS, list);
                    return;
                }
            }
        });
    }

    public static boolean hasAnyAmountEntries(ItemStack stack) {
        if (!isFilterItem(stack))
            return false;
        ListTag list = getItemEntries(getRoot(stack));
        for (Tag t : list) {
            if (t instanceof CompoundTag c) {
                if (getEntryBatch(c) > 0 || getEntryStock(c) > 0 || c.contains(KEY_ENCHANTED))
                    return true;
            }
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
                return getEntryAmount(filter, i);
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
            if (!entry.isEmpty() && FluidStack.isSameFluidSameComponents(entry, candidate)) {
                return getEntryAmount(filter, i);
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
                return getEntryAmount(filter, i);
            }
        }
        return 0;
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
                    TagParser.parseCompoundFully(raw);
                } catch (Exception e) {
                    warnings.add("Slot " + (i + 1) + ": invalid NBT (" + e.getMessage() + ")");
                }
            }
        }
        return warnings;
    }

    private static void removeFromList(ListTag list, int slot) {
        list.removeIf(t -> t instanceof CompoundTag c && getSlotIndex(c) == slot);
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
        boolean blacklist = root.getBooleanOr(KEY_IS_BLACKLIST, false);
        ListTag list = getItemEntries(root);

        boolean hasItemEntries = false;
        boolean hasFluidEntries = false;
        boolean hasChemicalEntries = false;
        boolean hasTagEntries = false;
        boolean hasNbtEntries = false;
        boolean hasAmountEntries = false;

        for (Tag t : list) {
            if (!(t instanceof CompoundTag entry))
                continue;

            int slot = getSlotIndex(entry);
            if (slot < 0 || slot >= cap || entriesBySlot[slot] != null)
                continue;

            String tag = getEntryTag(entry);
            Item item = resolveEntryItem(entry);
            boolean hasFluid = entry.contains(KEY_FLUID_ID);
            boolean hasChemical = entry.contains(KEY_CHEMICAL_ID);
            String chemicalId = hasChemical ? entry.getStringOr(KEY_CHEMICAL_ID, "") : null;
            FluidStack fluidEntry = null;
            if (hasFluid) {
                Identifier fluidId = Identifier.tryParse(entry.getStringOr(KEY_FLUID_ID, ""));
                if (fluidId != null) {
                    fluidEntry = BuiltInRegistries.FLUID.getOptional(fluidId)
                            .map(f -> new FluidStack(f, 1000))
                            .orElse(null);
                }
            }
            List<SlotNbtRule> nbtRules = readSlotNbtRules(entry);
            boolean nbtMatchAny = entry.getBooleanOr(KEY_NBT_MATCH_ANY, false);

            String nbtPath = getEntryNbtPath(entry);
            Tag nbtValue = getEntryNbtValue(entry);
            String nbtOp = getEntryNbtOperator(entry);
            String raw = getEntryNbtRaw(entry);
            CompoundTag rawNbt = null;
            boolean invalidRawNbt = false;
            if (raw != null) {
                try {
                    rawNbt = TagParser.parseCompoundFully(raw);
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
            Boolean enchanted = entry.contains(KEY_ENCHANTED) ? entry.getBooleanOr(KEY_ENCHANTED, false) : null;
            boolean nbtOnly = (hasNbt || hasDur || enchanted != null) && tag == null && item == null && !hasFluid && !hasChemical;

            int[] slotMapping = null;
            if (entry.contains(KEY_SLOT_MAPPING)) {
                int[] arr = entry.getIntArray(KEY_SLOT_MAPPING).orElse(new int[0]);
                if (arr.length > 0) slotMapping = arr;
            }

            entriesBySlot[slot] = new ItemFilterSlot(tag, item, chemicalId, fluidEntry, batch, stock, nbtPath,
                    nbtValue, nbtOp, rawNbt, invalidRawNbt, durOp, durVal, hasNbt, nbtOnly, nbtRules,
                    nbtMatchAny, slotMapping, enchanted);

            hasItemEntries |= item != null;
            hasFluidEntries |= hasFluid;
            hasChemicalEntries |= hasChemical;
            hasTagEntries |= tag != null;
            hasNbtEntries |= hasNbt;
            hasAmountEntries |= batch > 0 || stock > 0 || enchanted != null;
        }

        return new ItemFilterView(blacklist, hasItemEntries, hasFluidEntries, hasChemicalEntries,
                hasTagEntries, hasNbtEntries, hasAmountEntries, entriesBySlot);
    }

    private static CompoundTag[] getEntriesBySlot(ItemStack stack, int cap) {
        CompoundTag[] entriesBySlot = new CompoundTag[Math.max(cap, 0)];
        if (cap <= 0)
            return entriesBySlot;

        ListTag list = getItemEntries(getRoot(stack));
        for (Tag t : list) {
            if (!(t instanceof CompoundTag entry))
                continue;
            int slot = getSlotIndex(entry);
            if (slot >= 0 && slot < cap && entriesBySlot[slot] == null) {
                entriesBySlot[slot] = entry;
            }
        }
        return entriesBySlot;
    }

    @Nullable
    private static CompoundTag getEntryData(ItemStack stack, int slot) {
        if (slot < 0)
            return null;

        ListTag list = getItemEntries(getRoot(stack));
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && getSlotIndex(entry) == slot) {
                return entry;
            }
        }
        return null;
    }

    @Nullable
    private static String getEntryTag(CompoundTag entry) {
        return entry.contains(KEY_TAG)
                ? FilterTagUtil.normalizeTag(entry.getStringOr(KEY_TAG, ""))
                : null;
    }

    @Nullable
    private static Item resolveEntryItem(CompoundTag entry) {
        if (!entry.contains(KEY_ITEM_TAG))
            return null;

        CompoundTag itemTag = entry.getCompound(KEY_ITEM_TAG).orElseGet(CompoundTag::new);
        if (!itemTag.contains("id"))
            return null;

        Identifier id = Identifier.tryParse(itemTag.getStringOr("id", ""));
        if (id == null)
            return null;

        return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    @Nullable
    private static String getEntryNbtPath(CompoundTag entry) {
        return entry.contains(KEY_NBT_PATH) ? entry.getStringOr(KEY_NBT_PATH, "") : null;
    }

    @Nullable
    private static String getEntryNbtOperator(CompoundTag entry) {
        if (!entry.contains(KEY_NBT_OP))
            return NBT_OP_EQUALS;
        return normalizeNbtOperator(entry.getStringOr(KEY_NBT_OP, NBT_OP_EQUALS));
    }

    @Nullable
    private static Tag getEntryNbtValue(CompoundTag entry) {
        return entry.contains(KEY_NBT_VALUE) ? entry.get(KEY_NBT_VALUE) : null;
    }

    @Nullable
    private static String getEntryNbtRaw(CompoundTag entry) {
        if (!entry.contains(KEY_NBT_RAW))
            return null;
        String raw = entry.getStringOr(KEY_NBT_RAW, "");
        return raw.isEmpty() ? null : raw;
    }

    @Nullable
    private static String getEntryDurabilityOp(CompoundTag entry) {
        return entry.contains(KEY_DUR_OP) ? entry.getStringOr(KEY_DUR_OP, "") : null;
    }

    private static int getEntryDurabilityValue(CompoundTag entry) {
        return entry.contains(KEY_DUR_VAL) ? entry.getIntOr(KEY_DUR_VAL, 0) : 0;
    }

    private static int getEntryAmount(CompoundTag entry) {
        return entry.contains(KEY_AMOUNT) ? entry.getIntOr(KEY_AMOUNT, 0) : 0;
    }

    private static int getEntryBatch(CompoundTag entry) {
        return entry.contains(KEY_BATCH) ? entry.getIntOr(KEY_BATCH, 0) : 0;
    }

    private static int getEntryStock(CompoundTag entry) {
        if (entry.contains(KEY_STOCK)) return entry.getIntOr(KEY_STOCK, 0);
        return getEntryAmount(entry);
    }

    private static boolean hasEntryNbt(CompoundTag entry) {
        return entry.contains(KEY_NBT_RULES)
                || getEntryNbtPath(entry) != null
                || getEntryNbtRaw(entry) != null;
    }

    private static String normalizeNbtOperator(@Nullable String operator) {
        if (operator == null) return NBT_OP_EQUALS;
        return switch (operator) {
            case "!=", ">", "<", ">=", "<=" -> operator;
            default -> NBT_OP_EQUALS;
        };
    }

    public static String nextNbtOperator(String current) {
        for (int i = 0; i < NBT_OPS.length; i++) {
            if (NBT_OPS[i].equals(current)) return NBT_OPS[(i + 1) % NBT_OPS.length];
        }
        return NBT_OPS[0];
    }

    private static boolean matchesNbtValue(@Nullable String operator, Tag expected, @Nullable Tag actual) {
        if (actual == null) return NBT_OP_NOT_EQUALS.equals(operator);
        String op = operator != null ? operator : NBT_OP_EQUALS;
        return switch (op) {
            case "!=" -> !expected.equals(actual);
            case ">", "<", ">=", "<=" -> compareNumericNbt(op, expected, actual);
            default -> expected.equals(actual);
        };
    }

    private static boolean compareNumericNbt(String op, Tag expected, Tag actual) {
        double exp = tagToDouble(expected);
        double act = tagToDouble(actual);
        if (Double.isNaN(exp) || Double.isNaN(act)) return false;
        return switch (op) {
            case ">" -> act > exp;
            case "<" -> act < exp;
            case ">=" -> act >= exp;
            case "<=" -> act <= exp;
            default -> false;
        };
    }

    private static double tagToDouble(Tag tag) {
        if (tag instanceof NumericTag nt) return nt.doubleValue();
        if (tag instanceof StringTag st) {
            try {
                return Double.parseDouble(st.value());
            } catch (Exception e) {
                return Double.NaN;
            }
        }
        try { return Double.parseDouble(tag.toString()); } catch (Exception e) { return Double.NaN; }
    }

    private static boolean hasEntryDurability(CompoundTag entry) {
        return getEntryDurabilityOp(entry) != null;
    }

    private static boolean isNbtOnlyEntry(CompoundTag entry) {
        if (!hasEntryNbt(entry) && !hasEntryDurability(entry) && !entry.contains(KEY_ENCHANTED))
            return false;
        return !entry.contains(KEY_TAG)
                && !entry.contains(KEY_ITEM_TAG)
                && !entry.contains(KEY_FLUID_ID)
                && !entry.contains(KEY_CHEMICAL_ID);
    }

    private static ItemStack parseItemEntry(CompoundTag entry, @Nullable HolderLookup.Provider provider) {
        if (provider == null || !entry.contains(KEY_ITEM_TAG))
            return ItemStack.EMPTY;
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, provider);
        return entry.read(KEY_ITEM_TAG, ItemStack.OPTIONAL_CODEC, ops).orElse(ItemStack.EMPTY);
    }

    private static ListTag getItemEntries(CompoundTag root) {
        return root.getListOrEmpty(KEY_ITEMS);
    }

    private static int getSlotIndex(CompoundTag entry) {
        return entry.getIntOr(KEY_SLOT, -1);
    }

    private static CompoundTag getRoot(ItemStack stack) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return custom.getCompound(KEY_ROOT).orElseGet(CompoundTag::new);
    }

    private static void updateRoot(ItemStack stack, Consumer<CompoundTag> modifier) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
            CompoundTag root = customTag.getCompound(KEY_ROOT).orElseGet(CompoundTag::new);

            modifier.accept(root);

            if (root.isEmpty()) {
                customTag.remove(KEY_ROOT);
            } else {
                customTag.put(KEY_ROOT, root);
            }
        });
    }
}
