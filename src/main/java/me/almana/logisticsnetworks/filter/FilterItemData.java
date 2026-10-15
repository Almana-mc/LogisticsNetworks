package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.component.GeneralFilterEntry;
import me.almana.logisticsnetworks.component.NbtCriterion;
import me.almana.logisticsnetworks.component.StackSnapshot;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.item.BaseFilterItem;
import me.almana.logisticsnetworks.util.ItemDataUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public final class FilterItemData {

    private static final int MAX_NBT_RULES_PER_SLOT = 8;
    private static final int MAX_NBT_PATH_LENGTH = 512;
    private static final int MAX_NBT_VALUE_LENGTH = 1024;
    // Bounds nbt rule sizes
    private static final int MAX_NBT_VALUE_BYTES = 2048;
    private static final int MAX_NBT_RAW_LENGTH = 4096;
    private static final String NBT_OP_EQUALS = "=";
    // Bounds channel sync size
    private static final int MAX_ENTRY_TAG_BYTES = 2048;
    private static final int MAX_FILTER_BYTES = 32768;
    private static final int MAX_ID_LENGTH = 256;
    private static final String KEY_ROOT = "ln_filter";
    private static final String KEY_VERSION = "v";
    private static final int VERSION = 2;
    private static final String KEY_BLACKLIST = "blacklist";
    private static final String KEY_TARGET = "target";
    private static final String KEY_ITEMS = "items";

    public static final class ReadCache {
        private final IdentityHashMap<ItemStack, ItemFilterView> itemViews = new IdentityHashMap<>();
        final Map<String, NameFilterData.ValidationResult> namePatterns = new HashMap<>();

        private ReadCache() {
        }
    }

    private record ItemFilterSlot(
            @Nullable String tag,
            @Nullable Item item,
            @Nullable CompoundTag expectedTag,
            @Nullable String chemicalId,
            @Nullable FluidStack fluidEntry,
            int batch,
            int stock,
            @Nullable CompoundTag rawNbt,
            boolean invalidRawNbt,
            boolean hasNbt,
            boolean nbtOnly,
            boolean nbtStrict,
            List<NbtCriterion> nbtRules,
            boolean nbtMatchAny,
            @Nullable int[] slotMapping,
            boolean slotOnly) {
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
        return getRoot(stack).getBoolean(KEY_BLACKLIST);
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
            if (isBlacklist)
                root.putBoolean(KEY_BLACKLIST, true);
            else
                root.remove(KEY_BLACKLIST);
        });
    }

    public static FilterTargetType getTargetType(ItemStack stack) {
        if (!isFilterItem(stack))
            return FilterTargetType.ITEMS;
        return FilterTargetType.fromOrdinal(getRoot(stack).getInt(KEY_TARGET));
    }

    public static void setTargetType(ItemStack stack, FilterTargetType type) {
        if (!isFilterItem(stack))
            return;
        FilterTargetType target = type == null ? FilterTargetType.ITEMS : type;
        updateRoot(stack, root -> {
            if (target == FilterTargetType.ITEMS)
                root.remove(KEY_TARGET);
            else
                root.putInt(KEY_TARGET, target.ordinal());
        });
    }

    public static ItemStack getEntry(ItemStack stack, int slot) {
        GeneralFilterEntry entry = entry(stack, slot);
        return entry == null || entry.item() == null ? ItemStack.EMPTY : entry.item().toStack();
    }

    public static Edit setEntry(ItemStack stack, int slot, ItemStack value) {
        if (!fitsEntry(value))
            return Edit.SAME;
        return edit(stack, slot, entry -> value.isEmpty()
                ? GeneralFilterEntry.empty(slot)
                : entry.withItem(StackSnapshot.of(value.copyWithCount(1))));
    }

    public static boolean fitsEntry(ItemStack value) {
        return !value.hasTag() || value.getTag().sizeInBytes() <= MAX_ENTRY_TAG_BYTES;
    }

    public static Edit addItem(ItemStack filter, ItemStack item) {
        if (!isFilterItem(filter) || item.isEmpty() || !fitsEntry(item))
            return Edit.SAME;
        ItemStack entry = item.copyWithCount(1);
        int cap = getCapacity(filter);
        for (int i = 0; i < cap; i++) {
            if (ItemStack.isSameItemSameTags(getEntry(filter, i), entry))
                return Edit.SAME;
        }
        for (int i = 0; i < cap; i++) {
            if (isEntrySlotAvailable(filter, i)) {
                return setEntry(filter, i, entry);
            }
        }
        return Edit.SAME;
    }

    public static void clearEntryItem(ItemStack stack, int slot) {
        edit(stack, slot, entry -> entry.withItem(null));
    }

    public static FluidStack getFluidEntry(ItemStack stack, int slot) {
        GeneralFilterEntry entry = entry(stack, slot);
        FluidStack fluid = entry == null ? null : resolveFluidEntry(nonEmpty(entry.fluidId()));
        return fluid == null ? FluidStack.EMPTY : fluid;
    }

    public static Edit setFluidEntry(ItemStack stack, int slot, FluidStack fluid) {
        String id = fluid.isEmpty() ? null : BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString();
        return edit(stack, slot, entry -> resourceEntry(entry, id, null));
    }

    public static boolean hasAnyEntries(ItemStack stack) {
        return !entries(stack).isEmpty();
    }

    public static boolean hasAnyFluidEntries(ItemStack stack, @Nullable ReadCache readCache) {
        if (!isFilterItem(stack))
            return false;
        return getItemFilterView(stack, readCache).hasFluidEntries();
    }

    public static int getEntryCount(ItemStack stack) {
        return entries(stack).size();
    }

    @Nullable
    public static String getChemicalEntry(ItemStack stack, int slot) {
        GeneralFilterEntry entry = entry(stack, slot);
        return entry == null ? null : nonEmpty(entry.chemicalId());
    }

    public static Edit setChemicalEntry(ItemStack stack, int slot, @Nullable String chemicalId) {
        return edit(stack, slot, entry -> resourceEntry(entry, null, nonEmpty(chemicalId)));
    }

    private static GeneralFilterEntry resourceEntry(GeneralFilterEntry current, @Nullable String fluidId,
            @Nullable String chemicalId) {
        if (fluidId == null && chemicalId == null)
            return GeneralFilterEntry.empty(current.slot());
        GeneralFilterEntry.EntryCounts counts = new GeneralFilterEntry.EntryCounts(0,
                Math.max(0, current.counts().batch()), Math.max(0, stockOf(current)));
        return new GeneralFilterEntry(current.slot(), null, fluidId, chemicalId, null, counts,
                GeneralFilterEntry.SlotMapping.EMPTY, GeneralFilterEntry.NbtConstraints.EMPTY);
    }

    private static boolean isEntrySlotAvailable(ItemStack filter, int slot) {
        return isFree(entry(filter, slot));
    }

    private static boolean isFree(@Nullable GeneralFilterEntry entry) {
        return entry == null || (entry.item() == null && nonEmpty(entry.fluidId()) == null
                && nonEmpty(entry.chemicalId()) == null && FilterTagUtil.normalizeTag(entry.tag()) == null
                && entry.slotMapping().slots().isEmpty() && !isNbtOnly(entry));
    }

    public enum Edit {
        SAME, CHANGED, REFUSED
    }

    public record BulkAdd(int added, boolean full, boolean tooLarge) {
    }

    // Decode once, save once
    public static BulkAdd addAll(ItemStack filter, List<GeneralFilterEntry> candidates) {
        if (!isFilterItem(filter))
            return new BulkAdd(0, false, false);
        List<GeneralFilterEntry> entries = new ArrayList<>(entries(filter));
        Set<String> known = new HashSet<>();
        entries.forEach(entry -> known.add(resourceKey(entry)));
        int cap = getCapacity(filter);
        int added = 0;
        boolean full = false;
        for (GeneralFilterEntry candidate : candidates) {
            if (!known.add(resourceKey(candidate)))
                continue;
            int slot = firstFreeSlot(entries, cap);
            if (slot < 0) {
                full = true;
                break;
            }
            int index = indexOfSlot(entries, slot);
            GeneralFilterEntry current = index < 0 ? GeneralFilterEntry.empty(slot) : entries.get(index);
            GeneralFilterEntry next = candidate.item() != null ? current.withItem(candidate.item())
                    : resourceEntry(current, candidate.fluidId(), candidate.chemicalId());
            if (index < 0)
                entries.add(next);
            else
                entries.set(index, next);
            added++;
        }
        if (added > 0 && !saveEntries(filter, entries))
            return new BulkAdd(0, full, true);
        return new BulkAdd(added, full, false);
    }

    private static int firstFreeSlot(List<GeneralFilterEntry> entries, int cap) {
        for (int slot = 0; slot < cap; slot++) {
            int index = indexOfSlot(entries, slot);
            if (index < 0 || isFree(entries.get(index)))
                return slot;
        }
        return -1;
    }

    @Nullable
    private static String resourceKey(GeneralFilterEntry entry) {
        if (entry.item() != null)
            return "item:" + entry.item().tag().getString("id");
        if (nonEmpty(entry.fluidId()) != null)
            return "fluid:" + entry.fluidId();
        return nonEmpty(entry.chemicalId()) == null ? null : "chemical:" + entry.chemicalId();
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

    public static Edit setEntryTag(ItemStack stack, int slot, @Nullable String tag) {
        String normalized = FilterTagUtil.normalizeTag(tag);
        return edit(stack, slot, entry -> entry.withTag(normalized));
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

    public static Edit setEntryBatch(ItemStack stack, int slot, int batch) {
        return edit(stack, slot, entry -> entry.withCounts(new GeneralFilterEntry.EntryCounts(
                entry.counts().amount(), Math.max(0, batch), entry.counts().stock())));
    }

    public static int getEntryStock(ItemStack stack, int slot) {
        GeneralFilterEntry entry = entry(stack, slot);
        return entry == null ? 0 : stockOf(entry);
    }

    public static Edit setEntryStock(ItemStack stack, int slot, int stock) {
        return edit(stack, slot, entry -> entry.withCounts(new GeneralFilterEntry.EntryCounts(
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

    public static Edit setEntrySlotMapping(ItemStack stack, int slot, @Nullable int[] slots) {
        return setEntrySlotMapping(stack, slot, slots, null);
    }

    public static Edit setEntrySlotMapping(ItemStack stack, int slot, @Nullable int[] slots,
            @Nullable String expression) {
        GeneralFilterEntry.SlotMapping mapping = slots == null || slots.length == 0
                ? GeneralFilterEntry.SlotMapping.EMPTY
                : new GeneralFilterEntry.SlotMapping(Arrays.stream(slots).boxed().toList(), expression);
        return edit(stack, slot, entry -> entry.withSlotMapping(mapping));
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
        NbtCriterion rule = findRule(stack, slot, GeneralFilterEntry.ENCHANTED_PATH);
        if (rule == null || !NBT_OP_EQUALS.equals(rule.operator()) || !(rule.value() instanceof NumericTag value))
            return null;
        return value.getAsByte() != 0;
    }

    public static Edit setEntryEnchanted(ItemStack stack, int slot, @Nullable Boolean value) {
        NbtCriterion rule = value == null ? null
                : new NbtCriterion(GeneralFilterEntry.ENCHANTED_PATH, NBT_OP_EQUALS, ByteTag.valueOf(value));
        return edit(stack, slot, entry -> entry.withNbt(entry.nbt().withRules(
                replaceRule(entry.nbt().rules(), GeneralFilterEntry.ENCHANTED_PATH, rule))));
    }

    public static boolean hasEntryEnchanted(ItemStack stack, int slot) {
        return findRule(stack, slot, GeneralFilterEntry.ENCHANTED_PATH) != null;
    }

    // ── NBT per-slot methods ──

    public static boolean hasEntryNbt(ItemStack stack, int slot) {
        GeneralFilterEntry entry = entry(stack, slot);
        return entry != null && hasNbt(entry);
    }

    @Nullable
    public static String getEntryNbtRaw(ItemStack stack, int slot) {
        GeneralFilterEntry entry = entry(stack, slot);
        return entry == null ? null : nonEmpty(entry.nbt().raw());
    }

    public static Edit setEntryNbtRaw(ItemStack stack, int slot, @Nullable String rawSnbt) {
        if (rawSnbt != null && rawSnbt.length() > MAX_NBT_RAW_LENGTH)
            return Edit.SAME;
        return edit(stack, slot, entry -> entry.withNbt(entry.nbt().withRaw(rawSnbt)));
    }

    public static boolean hasAnyNbtEntries(ItemStack stack, @Nullable ReadCache readCache) {
        return isFilterItem(stack) && getItemFilterView(stack, readCache).hasNbtEntries();
    }

    public static boolean isNbtOnlySlot(ItemStack stack, int slot) {
        GeneralFilterEntry entry = entry(stack, slot);
        return entry != null && isNbtOnly(entry);
    }

    public static boolean isEntryNbtStrict(ItemStack stack, int slot) {
        GeneralFilterEntry entry = entry(stack, slot);
        return entry != null && entry.item() != null && entry.nbt().strict().orElse(!hasNbt(entry));
    }

    public static Edit setEntryNbtStrict(ItemStack stack, int slot, boolean strict) {
        return edit(stack, slot, entry -> entry.isEmpty() ? entry : entry.withNbt(entry.nbt().withStrict(strict)));
    }

    // ── Multi-rule NBT per-slot methods ──

    public static List<NbtCriterion> getSlotNbtRules(ItemStack stack, int slot) {
        GeneralFilterEntry entry = entry(stack, slot);
        return entry == null ? List.of() : entry.nbt().rules();
    }

    public static Edit addSlotNbtRule(ItemStack stack, int slot, NbtPath path, String operator, Tag value) {
        if (path.isEmpty() || path.toString().length() > MAX_NBT_PATH_LENGTH || isOversizedNbtValue(value))
            return Edit.SAME;
        NbtCriterion added = new NbtCriterion(path, normalizeNbtOperator(operator), value);
        return edit(stack, slot, entry -> {
            List<NbtCriterion> rules = new ArrayList<>(entry.nbt().rules());
            if (rules.size() >= MAX_NBT_RULES_PER_SLOT)
                return entry;
            int index = indexOfRule(rules, added);
            if (index < 0)
                rules.add(added);
            else
                rules.set(index, added);
            // First rule replaces raw SNBT
            String raw = entry.nbt().rules().isEmpty() ? "" : entry.nbt().raw();
            return entry.withNbt(entry.nbt().withRules(rules).withRaw(raw));
        });
    }

    public static Edit removeSlotNbtRule(ItemStack stack, int slot, int ruleIndex) {
        return edit(stack, slot, entry -> {
            List<NbtCriterion> rules = new ArrayList<>(entry.nbt().rules());
            if (ruleIndex < 0 || ruleIndex >= rules.size())
                return entry;
            rules.remove(ruleIndex);
            GeneralFilterEntry.NbtConstraints nbt = entry.nbt().withRules(rules);
            return entry.withNbt(rules.isEmpty() ? nbt.withMatchAny(false) : nbt);
        });
    }

    public static void clearSlotNbtRules(ItemStack stack, int slot) {
        edit(stack, slot, entry -> entry.withNbt(new GeneralFilterEntry.NbtConstraints(
                List.of(), false, entry.nbt().strict(), "")));
    }

    public static boolean isSlotNbtMatchAny(ItemStack stack, int slot) {
        GeneralFilterEntry entry = entry(stack, slot);
        return entry != null && entry.nbt().matchAny();
    }

    public static void toggleSlotNbtMatchMode(ItemStack stack, int slot) {
        edit(stack, slot, entry -> entry.isEmpty() ? entry
                : entry.withNbt(entry.nbt().withMatchAny(!entry.nbt().matchAny())));
    }

    public static Edit setSlotNbtRuleValue(ItemStack stack, int slot, int ruleIndex, Tag newValue) {
        if (isOversizedNbtValue(newValue))
            return Edit.SAME;
        return edit(stack, slot, entry -> {
            List<NbtCriterion> rules = new ArrayList<>(entry.nbt().rules());
            if (ruleIndex < 0 || ruleIndex >= rules.size())
                return entry;
            NbtCriterion rule = rules.get(ruleIndex);
            rules.set(ruleIndex, new NbtCriterion(rule.path(), rule.operator(), newValue));
            return entry.withNbt(entry.nbt().withRules(rules));
        });
    }

    private static boolean isOversizedNbtValue(Tag value) {
        return value.sizeInBytes() > MAX_NBT_VALUE_BYTES || value.toString().length() > MAX_NBT_VALUE_LENGTH;
    }

    public static int indexOfRule(List<NbtCriterion> rules, NbtCriterion rule) {
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).path().equals(rule.path()) && rules.get(i).operator().equals(rule.operator()))
                return i;
        }
        return -1;
    }

    // ── Durability per-slot methods ──

    @Nullable
    public static String getEntryDurabilityOp(ItemStack stack, int slot) {
        NbtCriterion rule = findRule(stack, slot, GeneralFilterEntry.DURABILITY_PATH);
        if (rule == null)
            return null;
        for (DurabilityFilterData.Operator operator : DurabilityFilterData.Operator.values()) {
            if (operator.symbol().equals(rule.operator()))
                return operator.id();
        }
        return null;
    }

    public static int getEntryDurabilityValue(ItemStack stack, int slot) {
        NbtCriterion rule = findRule(stack, slot, GeneralFilterEntry.DURABILITY_PATH);
        return rule != null && rule.value() instanceof NumericTag value ? value.getAsInt() : 0;
    }

    public static Edit setEntryDurability(ItemStack stack, int slot, @Nullable String op, int value) {
        NbtCriterion rule = op == null || op.isEmpty() ? null
                : new NbtCriterion(GeneralFilterEntry.DURABILITY_PATH,
                        DurabilityFilterData.Operator.fromId(op).symbol(),
                        IntTag.valueOf(Math.max(0, Math.min(3000, value))));
        return edit(stack, slot, entry -> entry.isEmpty() ? entry : entry.withNbt(entry.nbt().withRules(
                replaceRule(entry.nbt().rules(), GeneralFilterEntry.DURABILITY_PATH, rule))));
    }

    public static boolean hasEntryDurability(ItemStack stack, int slot) {
        return findRule(stack, slot, GeneralFilterEntry.DURABILITY_PATH) != null;
    }

    @Nullable
    private static NbtCriterion findRule(ItemStack stack, int slot, NbtPath path) {
        for (NbtCriterion rule : getSlotNbtRules(stack, slot)) {
            if (rule.path().equals(path))
                return rule;
        }
        return null;
    }

    private static List<NbtCriterion> replaceRule(List<NbtCriterion> rules, NbtPath path,
            @Nullable NbtCriterion replacement) {
        // Keep position, drop duplicates
        List<NbtCriterion> result = new ArrayList<>(rules.size() + 1);
        boolean placed = replacement == null;
        for (NbtCriterion rule : rules) {
            if (!rule.path().equals(path)) {
                result.add(rule);
            } else if (!placed) {
                result.add(replacement);
                placed = true;
            }
        }
        if (!placed)
            result.add(replacement);
        return result;
    }

    // ── Full matching methods (tag + NBT + durability aware) ──

    public enum ItemVerdict {
        NONE, WHITELIST_HIT, WHITELIST_MISS, BLACKLIST_HIT, BLACKLIST_MISS;

        public boolean matched() {
            return this == WHITELIST_HIT || this == BLACKLIST_HIT;
        }

        public boolean blacklist() {
            return this == BLACKLIST_HIT || this == BLACKLIST_MISS;
        }
    }

    public static ItemVerdict checkItem(ItemStack filter, ItemStack candidate, HolderLookup.Provider provider,
            @Nullable CandidateComponents candidateComponents, @Nullable ReadCache readCache, int inventorySlot) {
        if (!isFilterItem(filter))
            return ItemVerdict.NONE;
        ItemFilterView view = getItemFilterView(filter, readCache);
        if (!view.hasItemEntries() && !view.hasTagEntries() && !view.hasSlotOnlyEntries())
            return ItemVerdict.NONE;
        boolean matched = matchesView(view, candidate, provider, candidateComponents, inventorySlot);
        if (view.blacklist())
            return matched ? ItemVerdict.BLACKLIST_HIT : ItemVerdict.BLACKLIST_MISS;
        return matched ? ItemVerdict.WHITELIST_HIT : ItemVerdict.WHITELIST_MISS;
    }

    private static boolean matchesView(ItemFilterView view, ItemStack candidate, HolderLookup.Provider provider,
            @Nullable CandidateComponents candidateComponents, int inventorySlot) {
        CandidateComponents components = candidateComponents != null ? candidateComponents
                : new CandidateComponents(candidate, provider);
        for (ItemFilterSlot entry : view.entriesBySlot()) {
            if (entry == null || !coversSlot(entry, inventorySlot))
                continue;

            if (entry.slotOnly())
                return true;

            String tag = entry.tag();
            if (tag != null) {
                if (candidate.getTags().map(t -> t.location().toString()).anyMatch(tag::equals)
                        && entryConstraintsMatch(entry, candidate, components))
                    return true;
                continue;
            }

            if (entry.nbtOnly()) {
                if (entryConstraintsMatch(entry, candidate, components))
                    return true;
                continue;
            }

            Item itemEntry = entry.item();
            if (itemEntry != null && itemEntry == candidate.getItem()
                    && itemEntryConstraintsMatch(entry, candidate, components))
                return true;
        }
        return false;
    }

    public static boolean containsFluidFull(ItemStack filter, FluidStack candidate, HolderLookup.Provider provider,
            @Nullable ReadCache readCache) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return false;

        ItemFilterView view = getItemFilterView(filter, readCache);
        CandidateComponents candidateComponents = null;
        boolean candidateComponentsResolved = false;
        for (ItemFilterSlot slot : view.entriesBySlot()) {
            if (slot == null)
                continue;
            String tag = slot.tag();
            if (tag != null || slot.nbtOnly()) {
                if (tag == null || fluidHasTag(candidate, tag)) {
                    if (slot.hasNbt()) {
                        if (!candidateComponentsResolved) {
                            candidateComponents = CandidateComponents.of(candidate, provider);
                            candidateComponentsResolved = true;
                        }
                        if (!checkNbtConstraint(slot, candidateComponents, false))
                            continue;
                    }
                    return true;
                }
                continue;
            }
            FluidStack entry = slot.fluidEntry();
            if (entry != null && !entry.isEmpty() && entry.isFluidEqual(candidate)) {
                if (slot.hasNbt()) {
                    if (!candidateComponentsResolved) {
                        candidateComponents = CandidateComponents.of(candidate, provider);
                        candidateComponentsResolved = true;
                    }
                    if (!checkNbtConstraint(slot, candidateComponents, false))
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
            HolderLookup.Provider provider, @Nullable CandidateComponents candidateComponents,
            @Nullable ReadCache readCache, int inventorySlot) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return List.of();
        ItemFilterView view = getItemFilterView(filter, readCache);
        CandidateComponents components = candidateComponents != null ? candidateComponents
                : new CandidateComponents(candidate, provider);
        List<ItemStock> stocks = new ArrayList<>();
        for (ItemFilterSlot entry : view.entriesBySlot()) {
            if (entry != null && coversSlot(entry, inventorySlot)
                    && itemEntryMatches(entry, candidate, components))
                stocks.add(new ItemStock(entry.stock(), entry.slotMapping()));
        }
        return stocks;
    }

    private static boolean itemEntryMatches(ItemFilterSlot entry, ItemStack candidate,
            CandidateComponents components) {
        String tag = entry.tag();
        if (tag != null)
            return candidate.getTags().map(t -> t.location().toString()).anyMatch(tag::equals)
                    && entryConstraintsMatch(entry, candidate, components);
        if (entry.nbtOnly())
            return entryConstraintsMatch(entry, candidate, components);
        return entry.item() == candidate.getItem()
                && itemEntryConstraintsMatch(entry, candidate, components);
    }

    private static boolean coversSlot(ItemFilterSlot entry, int inventorySlot) {
        if (inventorySlot < 0 || entry.slotMapping() == null) return true;
        for (int s : entry.slotMapping()) {
            if (s == inventorySlot) return true;
        }
        return false;
    }

    public static int getItemBatchLimitFull(ItemStack filter, ItemStack candidate,
            HolderLookup.Provider provider, @Nullable CandidateComponents candidateComponents,
            @Nullable ReadCache readCache) {
        if (!isFilterItem(filter) || candidate.isEmpty())
            return 0;
        ItemFilterView view = getItemFilterView(filter, readCache);
        CandidateComponents components = candidateComponents != null ? candidateComponents
                : new CandidateComponents(candidate, provider);
        for (ItemFilterSlot entry : view.entriesBySlot()) {
            if (entry == null)
                continue;

            String tag = entry.tag();
            if (tag != null) {
                if (candidate.getTags().map(t -> t.location().toString()).anyMatch(tag::equals)
                        && entryConstraintsMatch(entry, candidate, components))
                    return entry.batch();
                continue;
            }

            if (entry.nbtOnly()) {
                if (entryConstraintsMatch(entry, candidate, components))
                    return entry.batch();
                continue;
            }

            Item itemEntry = entry.item();
            if (itemEntry != null && itemEntry == candidate.getItem()
                    && itemEntryConstraintsMatch(entry, candidate, components))
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
                if (fluidHasTag(candidate, tag))
                    return slot.stock();
                continue;
            }
            if (slot.nbtOnly())
                return slot.stock();
            FluidStack entry = slot.fluidEntry();
            if (entry != null && !entry.isEmpty() && entry.isFluidEqual(candidate))
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
                if (fluidHasTag(candidate, tag))
                    return slot.batch();
                continue;
            }
            if (slot.nbtOnly())
                return slot.batch();
            FluidStack entry = slot.fluidEntry();
            if (entry != null && !entry.isEmpty() && entry.isFluidEqual(candidate))
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

    private static boolean entryConstraintsMatch(ItemFilterSlot entry, ItemStack candidate,
            CandidateComponents components) {
        return !entry.hasNbt() || checkNbtConstraint(entry, components, candidate.isDamageableItem());
    }

    private static boolean itemEntryConstraintsMatch(ItemFilterSlot entry, ItemStack candidate,
            CandidateComponents components) {
        if (entry.nbtStrict())
            return Objects.equals(entry.expectedTag(), candidate.getTag());
        return entryConstraintsMatch(entry, candidate, components);
    }

    private static boolean checkNbtConstraint(ItemFilterSlot entry, @Nullable CandidateComponents components,
            boolean damageable) {
        if (!entry.hasNbt())
            return true;

        List<NbtCriterion> rules = entry.nbtRules();
        if (!rules.isEmpty()) {
            boolean matchAny = entry.nbtMatchAny();
            boolean evaluated = false;
            for (NbtCriterion rule : rules) {
                // Durability ignores undamageable items
                if (!damageable && rule.path().equals(GeneralFilterEntry.DURABILITY_PATH))
                    continue;
                if (components == null)
                    return false;
                evaluated = true;
                Tag actual = components.resolve(rule.path());
                boolean matches = rule.matches(actual);
                if (matchAny && matches) return true;
                if (!matchAny && !matches) return false;
            }
            return !matchAny || !evaluated;
        }
        if (components == null)
            return false;

        CompoundTag rawNbt = entry.rawNbt();
        if (rawNbt != null) {
            return NbtRuleMatcher.compoundContains(components.select(rawNbt.getAllKeys()), rawNbt);
        }
        return !entry.invalidRawNbt();
    }

    public static int getEntryAmount(ItemStack stack, int slot) {
        GeneralFilterEntry entry = entry(stack, slot);
        return entry == null ? 0 : entry.counts().amount();
    }

    public static Edit setEntryAmount(ItemStack stack, int slot, int amount) {
        return edit(stack, slot, entry -> entry.isEmpty() ? entry : entry.withCounts(new GeneralFilterEntry.EntryCounts(
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
        if (readCache == null)
            return buildItemFilterView(stack);
        ItemFilterView cached = readCache.itemViews.get(stack);
        if (cached == null) {
            cached = buildItemFilterView(stack);
            readCache.itemViews.put(stack, cached);
        }
        return cached;
    }

    private static ItemFilterView buildItemFilterView(ItemStack stack) {
        int cap = getCapacity(stack);
        ItemFilterSlot[] entriesBySlot = new ItemFilterSlot[Math.max(cap, 0)];
        if (!isFilterItem(stack) || cap <= 0) {
            return emptyItemFilterView(entriesBySlot);
        }

        for (GeneralFilterEntry entry : entries(stack)) {
            int slot = entry.slot();
            if (slot >= 0 && slot < cap && entriesBySlot[slot] == null) {
                entriesBySlot[slot] = buildItemFilterSlot(entry);
            }
        }
        return summarizeItemFilterView(isBlacklist(stack), entriesBySlot);
    }

    private static ItemFilterSlot buildItemFilterSlot(GeneralFilterEntry entry) {
        ItemStack expected = entry.item() == null ? ItemStack.EMPTY : entry.item().toStack();
        Item item = expected.isEmpty() ? null : expected.getItem();
        CompoundTag expectedTag = expected.isEmpty() ? null : expected.getTag();
        String tag = FilterTagUtil.normalizeTag(entry.tag());
        String fluidId = nonEmpty(entry.fluidId());
        String chemicalId = nonEmpty(entry.chemicalId());
        List<NbtCriterion> rules = entry.nbt().rules().stream()
                .filter(rule -> !rule.path().isEmpty())
                .toList();
        ParsedRawNbt raw = parseRawNbt(entry.nbt().raw());
        int stock = stockOf(entry);
        int[] mapping = entry.slotMapping().slots().isEmpty()
                ? null
                : entry.slotMapping().slots().stream().mapToInt(Integer::intValue).toArray();
        boolean hasNbt = !rules.isEmpty() || !entry.nbt().raw().isEmpty();
        boolean nbtOnly = (hasNbt || entry.counts().batch() > 0 || stock > 0)
                && tag == null && item == null && fluidId == null && chemicalId == null;
        boolean slotOnly = mapping != null && tag == null && item == null && fluidId == null
                && chemicalId == null && !hasNbt;
        return new ItemFilterSlot(tag, item, expectedTag, chemicalId, resolveFluidEntry(fluidId),
                entry.counts().batch(), stock, raw.value(), raw.invalid(), hasNbt, nbtOnly,
                item != null && entry.nbt().strict().orElse(!hasNbt), rules, entry.nbt().matchAny(), mapping,
                slotOnly);
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
            amount |= entry.batch() > 0 || entry.stock() > 0;
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

    public static List<GeneralFilterEntry> getEntries(ItemStack stack) {
        return entries(stack);
    }

    // Rebuilds untrusted filter entries
    public static boolean copySanitized(ItemStack source, ItemStack target) {
        if (!isFilterItem(source) || !isFilterItem(target))
            return false;
        int cap = getCapacity(target);
        Set<Integer> used = new HashSet<>();
        List<GeneralFilterEntry> kept = new ArrayList<>();
        for (GeneralFilterEntry entry : entries(source)) {
            GeneralFilterEntry clean = sanitizedEntry(entry);
            if (clean != null && entry.slot() >= 0 && entry.slot() < cap && used.add(entry.slot()))
                kept.add(clean);
        }
        if (!kept.isEmpty() && !saveEntries(target, kept))
            return false;
        setTargetType(target, getTargetType(source));
        setBlacklist(target, isBlacklist(source));
        return true;
    }

    @Nullable
    private static GeneralFilterEntry sanitizedEntry(GeneralFilterEntry entry) {
        StackSnapshot item = null;
        if (entry.item() != null) {
            ItemStack stack = entry.item().toStack();
            if (stack.isEmpty() || !fitsEntry(stack))
                return null;
            ItemStack clean = new ItemStack(stack.getItem());
            clean.setTag(stack.getTag());
            item = StackSnapshot.of(clean);
        }
        if (tooLong(entry.fluidId()) || tooLong(entry.chemicalId()) || tooLong(entry.tag())
                || !isValidNbt(entry.nbt()))
            return null;
        GeneralFilterEntry.EntryCounts counts = entry.counts();
        List<Integer> slots = entry.slotMapping().slots().stream()
                .filter(s -> s >= SlotExpressionUtil.MIN_SLOT && s <= SlotExpressionUtil.MAX_SLOT)
                .distinct().toList();
        String expression = tooLong(entry.slotMapping().expression()) ? "" : entry.slotMapping().expression();
        return new GeneralFilterEntry(entry.slot(), item, entry.fluidId(), entry.chemicalId(),
                FilterTagUtil.normalizeTag(entry.tag()),
                new GeneralFilterEntry.EntryCounts(Math.max(0, counts.amount()), Math.max(0, counts.batch()),
                        Math.max(0, counts.stock())),
                slots.isEmpty() ? GeneralFilterEntry.SlotMapping.EMPTY
                        : new GeneralFilterEntry.SlotMapping(slots, expression),
                entry.nbt());
    }

    private static boolean isValidNbt(GeneralFilterEntry.NbtConstraints nbt) {
        if (nbt.rules().size() > MAX_NBT_RULES_PER_SLOT || nbt.raw().length() > MAX_NBT_RAW_LENGTH)
            return false;
        for (NbtCriterion rule : nbt.rules()) {
            if (rule.path().isEmpty() || rule.path().toString().length() > MAX_NBT_PATH_LENGTH
                    || isOversizedNbtValue(rule.value()))
                return false;
        }
        return true;
    }

    private static boolean tooLong(@Nullable String value) {
        return value != null && value.length() > MAX_ID_LENGTH;
    }

    private static List<GeneralFilterEntry> entries(ItemStack stack) {
        if (!isFilterItem(stack))
            return List.of();
        CompoundTag root = getRoot(stack);
        // D3: pre-v2 entries stay loose
        boolean legacy = root.getInt(KEY_VERSION) < VERSION;
        List<GeneralFilterEntry> entries = new ArrayList<>();
        for (Tag tag : root.getList(KEY_ITEMS, Tag.TAG_COMPOUND))
            entries.add(GeneralFilterEntry.load((CompoundTag) tag, legacy));
        return entries;
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

    private static Edit edit(ItemStack stack, int slot, UnaryOperator<GeneralFilterEntry> change) {
        if (!isFilterItem(stack) || slot < 0 || slot >= getCapacity(stack))
            return Edit.SAME;
        List<GeneralFilterEntry> entries = new ArrayList<>(entries(stack));
        int index = indexOfSlot(entries, slot);
        GeneralFilterEntry current = index < 0 ? GeneralFilterEntry.empty(slot) : entries.get(index);
        GeneralFilterEntry next = change.apply(current);
        if (next.equals(current))
            return Edit.SAME;
        if (index < 0)
            entries.add(next);
        else if (next.isEmpty())
            entries.remove(index);
        else
            entries.set(index, next);
        return saveEntries(stack, entries) ? Edit.CHANGED : Edit.REFUSED;
    }

    private static int encodedSize(ListTag list) {
        try (DataOutputStream out = new DataOutputStream(OutputStream.nullOutputStream())) {
            list.write(out);
            return out.size();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean saveEntries(ItemStack stack, List<GeneralFilterEntry> entries) {
        ListTag list = new ListTag();
        entries.forEach(entry -> list.add(entry.save()));
        ListTag old = getRoot(stack).getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        int size = encodedSize(list);
        if (size > MAX_FILTER_BYTES && size > encodedSize(old))
            return false;
        updateRoot(stack, root -> {
            root.putInt(KEY_VERSION, VERSION);
            if (list.isEmpty())
                root.remove(KEY_ITEMS);
            else
                root.put(KEY_ITEMS, list);
        });
        return true;
    }

    private static boolean fluidHasTag(FluidStack fluid, String tag) {
        return fluid.getFluid().builtInRegistryHolder().tags().anyMatch(t -> t.location().toString().equals(tag));
    }

    private static CompoundTag getRoot(ItemStack stack) {
        CompoundTag custom = ItemDataUtil.getCustomData(stack);
        return custom.contains(KEY_ROOT, Tag.TAG_COMPOUND) ? custom.getCompound(KEY_ROOT) : new CompoundTag();
    }

    private static void updateRoot(ItemStack stack, Consumer<CompoundTag> modifier) {
        ItemDataUtil.updateCustomData(stack, custom -> {
            CompoundTag root = custom.contains(KEY_ROOT, Tag.TAG_COMPOUND) ? custom.getCompound(KEY_ROOT)
                    : new CompoundTag();
            modifier.accept(root);
            if (root.isEmpty())
                custom.remove(KEY_ROOT);
            else
                custom.put(KEY_ROOT, root);
        });
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

    private static String normalizeNbtOperator(@Nullable String operator) {
        return NbtRuleMatcher.normalizeOperator(operator);
    }

    public static String nextNbtOperator(String current) {
        return NbtRuleMatcher.nextOperator(current);
    }
}
