package me.almana.logisticsnetworks.component;

import me.almana.logisticsnetworks.filter.DurabilityFilterData;
import me.almana.logisticsnetworks.filter.NbtRuleMatcher;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GeneralFilterBridge {

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
        ListTag storedEntries = root.getList("items", Tag.TAG_COMPOUND);
        for (Tag tag : storedEntries) {
            if (!(tag instanceof CompoundTag entryTag)) {
                continue;
            }
            int slot = entryTag.getInt("slot");
            StackSnapshot item = null;
            if (entryTag.get("item") instanceof CompoundTag itemTag) {
                item = readItem(itemTag, provider, existingItems.get(slot));
                complete &= item != null;
            }

            String fluid = readString(entryTag, "fluid");
            String chemical = readString(entryTag, "chemical");
            String entryFilterTag = readString(entryTag, "tag");
            GeneralFilterEntry.EntryCounts counts = new GeneralFilterEntry.EntryCounts(
                    entryTag.getInt("amount"), entryTag.getInt("batch"), entryTag.getInt("stock"));
            GeneralFilterEntry.SlotMapping mapping = new GeneralFilterEntry.SlotMapping(
                    toList(entryTag.getIntArray("slot_map")), entryTag.getString("slot_map_expr"));
            Boolean enchanted = entryTag.contains("enchanted", Tag.TAG_BYTE)
                    ? entryTag.getBoolean("enchanted")
                    : null;
            GeneralFilterEntry.NbtConstraints nbt = new GeneralFilterEntry.NbtConstraints(
                    readRules(entryTag),
                    entryTag.getBoolean("nbt_match_any"),
                    entryTag.contains("nbt_strict", Tag.TAG_BYTE)
                            ? Optional.of(entryTag.getBoolean("nbt_strict"))
                            : Optional.empty(),
                    entryTag.getString("nbt_raw"));
            GeneralFilterEntry.DurabilityConstraint durability = entryTag.contains("dur_op", Tag.TAG_STRING)
                    ? new GeneralFilterEntry.DurabilityConstraint(
                            DurabilityFilterData.Operator.fromId(entryTag.getString("dur_op")),
                            entryTag.getInt("dur_val"))
                    : null;

            entries.add(new GeneralFilterEntry(slot, item, fluid, chemical, entryFilterTag, counts, mapping,
                    enchanted, nbt, durability));
        }
        return new ReadResult(new GeneralFilterConfig(entries), complete);
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
                stored.putIntArray("slot_map", entry.slotMapping().slots());
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
        if (tag.getBoolean(SNAPSHOT_MARKER)) {
            return existing;
        }
        ItemStack stack;
        if (provider != null) {
            stack = ItemStack.parseOptional(provider, tag);
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
                : stack.save(provider);
        if (encoded instanceof CompoundTag compound) {
            return compound;
        }
        CompoundTag fallback = new CompoundTag();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        fallback.putString("id", id.toString());
        fallback.putBoolean(SNAPSHOT_MARKER, true);
        return fallback;
    }

    private static List<NbtCriterion> readRules(CompoundTag entry) {
        List<NbtCriterion> result = new ArrayList<>();
        ListTag rules = entry.getList("nbt_rules", Tag.TAG_COMPOUND);
        for (Tag tag : rules) {
            if (!(tag instanceof CompoundTag rule)) {
                continue;
            }
            String path = rule.getString("p");
            Tag value = rule.get("v");
            if (!path.isEmpty() && value != null) {
                result.add(new NbtCriterion(path,
                        NbtRuleMatcher.normalizeOperator(rule.getString("o")), value));
            }
        }
        if (!result.isEmpty()) {
            return result;
        }
        String path = entry.getString("nbt_path");
        Tag value = entry.get("nbt_val");
        return path.isEmpty() || value == null
                ? List.of()
                : List.of(new NbtCriterion(path,
                        NbtRuleMatcher.normalizeOperator(entry.getString("nbt_op")), value));
    }

    private static void writeNbt(CompoundTag entry, GeneralFilterEntry.NbtConstraints nbt) {
        if (!nbt.rules().isEmpty()) {
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
        return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : null;
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
