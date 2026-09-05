package me.almana.logisticsnetworks.component;

import me.almana.logisticsnetworks.filter.DurabilityFilterData;
import me.almana.logisticsnetworks.filter.NbtRuleMatcher;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GeneralFilterBridge {

    static final List<String> ENTRY_FIELDS = List.of("item", "fluid", "chemical", "tag", "amount", "batch", "stock",
            "slot_map", "slot_map_expr", "enchanted", "nbt_rules", "nbt_match_any", "nbt_strict", "nbt_raw",
            "dur_op", "dur_val", "nbt_path", "nbt_val", "nbt_op");
    private static final String SNAPSHOT_MARKER = "ln_component_snapshot";

    private GeneralFilterBridge() {
    }

    public static ReadResult read(CompoundTag root, @Nullable HolderLookup.Provider provider,
            @Nullable GeneralFilterConfig existing) {
        Map<Integer, StackSnapshot> existingItems = new HashMap<>();
        if (existing != null) {
            for (GeneralFilterEntry entry : existing.entries()) {
                if (entry.item() != null) {
                    existingItems.put(entry.slot(), entry.item());
                }
            }
        }

        List<GeneralFilterEntry> entries = new ArrayList<>();
        boolean complete = true;
        ListTag storedEntries = root.getListOrEmpty("items");
        for (Tag tag : storedEntries) {
            if (!(tag instanceof CompoundTag entryTag)) {
                continue;
            }
            int slot = entryTag.getIntOr("slot", 0);
            StackSnapshot item = null;
            if (entryTag.get("item") instanceof CompoundTag itemTag) {
                item = readItem(itemTag, provider, existingItems.get(slot));
                complete &= item != null;
            }

            GeneralFilterEntry entry = readMetadata(entryTag, slot, item);
            if (!entry.equals(GeneralFilterEntry.empty(slot))) {
                entries.add(entry);
            }
        }
        return new ReadResult(new GeneralFilterConfig(entries), complete);
    }

    private static GeneralFilterEntry readMetadata(CompoundTag entryTag, int slot, @Nullable StackSnapshot item) {
        String fluid = readString(entryTag, "fluid");
        String chemical = readString(entryTag, "chemical");
        String entryFilterTag = readString(entryTag, "tag");
        GeneralFilterEntry.EntryCounts counts = new GeneralFilterEntry.EntryCounts(
                entryTag.getIntOr("amount", 0), entryTag.getIntOr("batch", 0), entryTag.getIntOr("stock", 0));
        GeneralFilterEntry.SlotMapping mapping = new GeneralFilterEntry.SlotMapping(
                toList(entryTag.getIntArray("slot_map").orElse(new int[0])), entryTag.getStringOr("slot_map_expr", ""));
        Boolean enchanted = entryTag.contains("enchanted")
                ? entryTag.getBooleanOr("enchanted", false)
                : null;
        GeneralFilterEntry.NbtConstraints nbt = new GeneralFilterEntry.NbtConstraints(
                readRules(entryTag),
                entryTag.getBooleanOr("nbt_match_any", false),
                entryTag.contains("nbt_strict")
                        ? Optional.of(entryTag.getBooleanOr("nbt_strict", false))
                        : Optional.empty(),
                entryTag.getStringOr("nbt_raw", ""));
        GeneralFilterEntry.DurabilityConstraint durability = entryTag.contains("dur_op")
                ? new GeneralFilterEntry.DurabilityConstraint(
                        DurabilityFilterData.Operator.fromId(entryTag.getStringOr("dur_op", "")),
                        entryTag.getIntOr("dur_val", 0))
                : null;

        return new GeneralFilterEntry(slot, item, fluid, chemical, entryFilterTag, counts, mapping,
                enchanted, nbt, durability);
    }

    public static CompoundTag write(GeneralFilterConfig config, @Nullable HolderLookup.Provider provider) {
        CompoundTag root = new CompoundTag();
        if (config.entries().isEmpty()) {
            return root;
        }
        ListTag entries = new ListTag();
        for (GeneralFilterEntry entry : config.entries()) {
            CompoundTag stored = new CompoundTag();
            stored.putInt("slot", entry.slot());
            if (entry.item() != null) {
                stored.put("item", writeItem(entry.item(), provider));
            }
            putString(stored, "fluid", entry.fluidId());
            putString(stored, "chemical", entry.chemicalId());
            putString(stored, "tag", entry.tag());
            if (entry.counts().amount() != 0) stored.putInt("amount", entry.counts().amount());
            if (entry.counts().batch() != 0) stored.putInt("batch", entry.counts().batch());
            if (entry.counts().stock() != 0) stored.putInt("stock", entry.counts().stock());
            if (!entry.slotMapping().slots().isEmpty()) {
                stored.putIntArray("slot_map", entry.slotMapping().slots().stream().mapToInt(Integer::intValue).toArray());
            }
            if (!entry.slotMapping().expression().isEmpty()) {
                stored.putString("slot_map_expr", entry.slotMapping().expression());
            }
            if (entry.enchanted() != null) stored.putBoolean("enchanted", entry.enchanted());
            writeNbt(stored, entry.nbt());
            if (entry.durability() != null) {
                stored.putString("dur_op", entry.durability().operator().id());
                stored.putInt("dur_val", entry.durability().value());
            }
            entries.add(stored);
        }
        root.put("items", entries);
        return root;
    }

    private static @Nullable StackSnapshot readItem(CompoundTag tag, @Nullable HolderLookup.Provider provider,
            @Nullable StackSnapshot existing) {
        if (tag.getBooleanOr(SNAPSHOT_MARKER, false)) {
            return existing;
        }
        ItemStack stack;
        if (provider != null) {
            stack = ItemStack.CODEC.parse(provider.createSerializationContext(NbtOps.INSTANCE), tag).result().orElse(ItemStack.EMPTY);
        } else {
            stack = ItemStack.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(ItemStack.EMPTY);
        }
        if (stack.isEmpty()) {
            return null;
        }
        FilterComponentData.migrate(stack, provider);
        return StackSnapshot.of(stack.copyWithCount(1));
    }

    private static CompoundTag writeItem(StackSnapshot snapshot, @Nullable HolderLookup.Provider provider) {
        ItemStack stack = snapshot.toStack().copyWithCount(1);
        Tag encoded = provider == null
                ? ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack).result().orElse(null)
                : ItemStack.CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), stack).result().orElse(null);
        if (encoded instanceof CompoundTag compound) {
            return compound;
        }
        CompoundTag fallback = new CompoundTag();
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        fallback.putString("id", id.toString());
        fallback.putBoolean(SNAPSHOT_MARKER, true);
        return fallback;
    }

    private static List<NbtCriterion> readRules(CompoundTag entry) {
        List<NbtCriterion> result = new ArrayList<>();
        ListTag rules = entry.getListOrEmpty("nbt_rules");
        for (Tag tag : rules) {
            if (!(tag instanceof CompoundTag rule)) {
                continue;
            }
            String path = rule.getStringOr("p", "");
            Tag value = rule.get("v");
            if (!path.isEmpty() && value != null) {
                result.add(new NbtCriterion(path,
                        NbtRuleMatcher.normalizeOperator(rule.getStringOr("o", "")), value));
            }
        }
        if (!result.isEmpty()) {
            return result;
        }
        String path = entry.getStringOr("nbt_path", "");
        Tag value = entry.get("nbt_val");
        return path.isEmpty() || value == null
                ? List.of()
                : List.of(new NbtCriterion(path,
                        NbtRuleMatcher.normalizeOperator(entry.getStringOr("nbt_op", "")), value));
    }

    private static void writeNbt(CompoundTag entry, GeneralFilterEntry.NbtConstraints nbt) {
        if (nbt.rules().size() == 1) {
            NbtCriterion rule = nbt.rules().getFirst();
            entry.putString("nbt_path", rule.path());
            entry.putString("nbt_op", rule.operator());
            entry.put("nbt_val", rule.value());
        } else if (!nbt.rules().isEmpty()) {
            ListTag rules = new ListTag();
            for (NbtCriterion criterion : nbt.rules()) {
                CompoundTag rule = new CompoundTag();
                rule.putString("p", criterion.path());
                rule.putString("o", criterion.operator());
                rule.put("v", criterion.value());
                rules.add(rule);
            }
            entry.put("nbt_rules", rules);
        }
        if (nbt.matchAny()) entry.putBoolean("nbt_match_any", true);
        nbt.strict().ifPresent(strict -> entry.putBoolean("nbt_strict", strict));
        if (!nbt.raw().isEmpty()) entry.putString("nbt_raw", nbt.raw());
    }

    private static @Nullable String readString(CompoundTag tag, String key) {
        return tag.contains(key) ? tag.getStringOr(key, "") : null;
    }

    private static void putString(CompoundTag tag, String key, @Nullable String value) {
        if (value != null && !value.isEmpty()) {
            tag.putString(key, value);
        }
    }

    private static List<Integer> toList(int[] values) {
        List<Integer> result = new ArrayList<>(values.length);
        for (int value : values) {
            result.add(value);
        }
        return result;
    }

    public record ReadResult(GeneralFilterConfig config, boolean complete) {
    }
}
