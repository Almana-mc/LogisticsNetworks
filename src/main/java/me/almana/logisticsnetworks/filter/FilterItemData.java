package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.item.BaseFilterItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;
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
    private static final String KEY_TAG = "tag";
    private static final String KEY_NBT_PATH = "nbt_path";
    private static final String KEY_NBT_VALUE = "nbt_val";
    private static final String KEY_DUR_OP = "dur_op";
    private static final String KEY_DUR_VAL = "dur_val";
    private static final String KEY_NBT_RAW = "nbt_raw";

    private FilterItemData() {
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
                    return ItemStack.parseOptional(provider, entry.getCompound(KEY_ITEM_TAG));
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
        int existingAmount = getEntryAmount(stack, slot);

        updateRoot(stack, root -> {
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            removeFromList(list, slot);

            if (!item.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putInt(KEY_SLOT, slot);
                entry.put(KEY_ITEM_TAG, item.save(provider));
                if (existingAmount > 0) {
                    entry.putInt(KEY_AMOUNT, existingAmount);
                }
                list.add(entry);
            }

            if (list.isEmpty()) {
                root.remove(KEY_ITEMS);
            } else {
                root.put(KEY_ITEMS, list);
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
        int existingAmount = getEntryAmount(stack, slot);

        updateRoot(stack, root -> {
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            removeFromList(list, slot);

            if (id != null) {
                CompoundTag entry = new CompoundTag();
                entry.putInt(KEY_SLOT, slot);
                entry.putString(KEY_FLUID_ID, id.toString());
                if (existingAmount > 0) {
                    entry.putInt(KEY_AMOUNT, existingAmount);
                }
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

        int existingAmount = getEntryAmount(stack, slot);

        updateRoot(stack, root -> {
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            removeFromList(list, slot);

            if (chemicalId != null && !chemicalId.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putInt(KEY_SLOT, slot);
                entry.putString(KEY_CHEMICAL_ID, chemicalId);
                if (existingAmount > 0) {
                    entry.putInt(KEY_AMOUNT, existingAmount);
                }
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
                    return entry.getString(KEY_TAG);
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

        int existingAmount = getEntryAmount(stack, slot);

        updateRoot(stack, root -> {
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            removeFromList(list, slot);

            if (tag != null && !tag.isBlank()) {
                CompoundTag entry = new CompoundTag();
                entry.putInt(KEY_SLOT, slot);
                entry.putString(KEY_TAG, tag.trim());
                if (existingAmount > 0) {
                    entry.putInt(KEY_AMOUNT, existingAmount);
                }
                list.add(entry);
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

    @Nullable
    public static Tag getEntryNbtValue(ItemStack stack, int slot) {
        if (!isFilterItem(stack))
            return null;
        CompoundTag root = getRoot(stack);
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                if (entry.contains(KEY_NBT_VALUE)) {
                    return entry.get(KEY_NBT_VALUE);
                }
            }
        }
        return null;
    }

    public static void setEntryNbt(ItemStack stack, int slot, @Nullable String path, @Nullable Tag value) {
        if (!isFilterItem(stack))
            return;
        if (slot < 0 || slot >= getCapacity(stack))
            return;

        updateRoot(stack, root -> {
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            for (Tag t : list) {
                if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                    if (path != null && !path.isEmpty() && value != null) {
                        entry.putString(KEY_NBT_PATH, path);
                        entry.put(KEY_NBT_VALUE, value.copy());
                    } else {
                        entry.remove(KEY_NBT_PATH);
                        entry.remove(KEY_NBT_VALUE);
                    }
                    root.put(KEY_ITEMS, list);
                    return;
                }
            }
        });
    }

    public static boolean hasEntryNbt(ItemStack stack, int slot) {
        return getEntryNbtPath(stack, slot) != null || getEntryNbtRaw(stack, slot) != null;
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
        return hasEntryType(stack, KEY_NBT_PATH) || hasEntryType(stack, KEY_NBT_RAW);
    }

    public static boolean isNbtOnlySlot(ItemStack stack, int slot) {
        if (!hasEntryNbt(stack, slot))
            return false;
        return getEntryTag(stack, slot) == null
                && getEntry(stack, slot, null).isEmpty()
                && getFluidEntry(stack, slot).isEmpty();
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

    public static boolean containsItemFull(ItemStack filter, ItemStack candidate, HolderLookup.Provider provider) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return false;

        int cap = getCapacity(filter);
        for (int i = 0; i < cap; i++) {
            String tag = getEntryTag(filter, i);
            if (tag != null) {
                if (candidate.getTags().map(t -> t.location().toString()).anyMatch(tag::equals)) {
                    return true;
                }
                continue;
            }

            // NBT-only slot
            if (isNbtOnlySlot(filter, i)) {
                CompoundTag components = NbtFilterData.getSerializedComponents(candidate, provider);
                if (checkNbtConstraint(filter, i, components))
                    return true;
                continue;
            }

            ItemStack entry = getEntry(filter, i, provider);
            if (!entry.isEmpty() && ItemStack.isSameItem(entry, candidate)) {
                if (!checkNbtConstraint(filter, i, NbtFilterData.getSerializedComponents(candidate, provider)))
                    continue;
                if (!checkDurabilityConstraint(filter, i, candidate))
                    continue;
                return true;
            }
        }
        return false;
    }

    public static boolean containsFluidFull(ItemStack filter, FluidStack candidate, HolderLookup.Provider provider) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return false;

        int cap = getCapacity(filter);
        for (int i = 0; i < cap; i++) {
            String tag = getEntryTag(filter, i);
            if (tag != null) {
                if (candidate.getTags().map(t -> t.location().toString()).anyMatch(tag::equals)) {
                    return true;
                }
                continue;
            }

            FluidStack entry = getFluidEntry(filter, i);
            if (!entry.isEmpty() && FluidStack.isSameFluidSameComponents(entry, candidate)) {
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
        if (!isFilterItem(filter) || candidate.isEmpty())
            return 0;
        int cap = getCapacity(filter);
        for (int i = 0; i < cap; i++) {
            String tag = getEntryTag(filter, i);
            if (tag != null) {
                if (candidate.getTags().map(t -> t.location().toString()).anyMatch(tag::equals))
                    return getEntryAmount(filter, i);
                continue;
            }

            ItemStack entry = getEntry(filter, i, provider);
            if (!entry.isEmpty() && ItemStack.isSameItem(entry, candidate)) {
                if (!checkNbtConstraint(filter, i, NbtFilterData.getSerializedComponents(candidate, provider)))
                    continue;
                if (!checkDurabilityConstraint(filter, i, candidate))
                    continue;
                return getEntryAmount(filter, i);
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
                if (candidate.getTags().map(t -> t.location().toString()).anyMatch(tag::equals))
                    return getEntryAmount(filter, i);
                continue;
            }

            FluidStack entry = getFluidEntry(filter, i);
            if (!entry.isEmpty() && FluidStack.isSameFluidSameComponents(entry, candidate))
                return getEntryAmount(filter, i);
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
                    return getEntryAmount(filter, i);
                continue;
            }

            String entry = getChemicalEntry(filter, i);
            if (entry != null && entry.equals(chemicalId))
                return getEntryAmount(filter, i);
        }
        return 0;
    }

    // ── Constraint helpers ──

    private static boolean checkNbtConstraint(ItemStack filter, int slot, @Nullable CompoundTag components) {
        if (!hasEntryNbt(filter, slot))
            return true;
        if (components == null)
            return false;

        // Raw SNBT mode
        String raw = getEntryNbtRaw(filter, slot);
        if (raw != null) {
            try {
                CompoundTag expected = net.minecraft.nbt.TagParser.parseTag(raw);
                return compoundContains(components, expected);
            } catch (Exception e) {
                return false;
            }
        }

        // Legacy path mode
        String nbtPath = getEntryNbtPath(filter, slot);
        Tag nbtExpected = getEntryNbtValue(filter, slot);
        if (nbtPath == null || nbtExpected == null)
            return true;
        Tag actual = NbtFilterData.resolvePathValue(components, nbtPath);
        return actual != null && nbtExpected.equals(actual);
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

    private static boolean checkDurabilityConstraint(ItemStack filter, int slot, ItemStack candidate) {
        if (!hasEntryDurability(filter, slot))
            return true;
        String durOp = getEntryDurabilityOp(filter, slot);
        if (durOp == null || !candidate.isDamageableItem())
            return true;
        int durVal = getEntryDurabilityValue(filter, slot);
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
        ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);

        for (Tag t : list) {
            if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
                return entry.contains(KEY_AMOUNT, Tag.TAG_INT) ? entry.getInt(KEY_AMOUNT) : 0;
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
            ListTag list = root.getList(KEY_ITEMS, Tag.TAG_COMPOUND);

            for (Tag t : list) {
                if (t instanceof CompoundTag entry && entry.getInt(KEY_SLOT) == slot) {
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
        ListTag list = getRoot(stack).getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (t instanceof CompoundTag c && c.contains(KEY_AMOUNT, Tag.TAG_INT) && c.getInt(KEY_AMOUNT) > 0)
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
    public static java.util.List<String> getWarnings(ItemStack stack) {
        java.util.List<String> warnings = new java.util.ArrayList<>();
        if (!isFilterItem(stack))
            return warnings;

        int cap = getCapacity(stack);
        for (int i = 0; i < cap; i++) {
            // Check for invalid raw SNBT
            String raw = getEntryNbtRaw(stack, i);
            if (raw != null) {
                try {
                    net.minecraft.nbt.TagParser.parseTag(raw);
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

    private static CompoundTag getRoot(ItemStack stack) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return custom.contains(KEY_ROOT, Tag.TAG_COMPOUND) ? custom.getCompound(KEY_ROOT) : new CompoundTag();
    }

    private static void updateRoot(ItemStack stack, java.util.function.Consumer<CompoundTag> modifier) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
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
