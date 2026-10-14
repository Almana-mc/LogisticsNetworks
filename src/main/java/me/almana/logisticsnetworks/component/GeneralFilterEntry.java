package me.almana.logisticsnetworks.component;

import me.almana.logisticsnetworks.filter.DurabilityFilterData;
import me.almana.logisticsnetworks.filter.NbtPath;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public record GeneralFilterEntry(
        int slot,
        @Nullable StackSnapshot item,
        @Nullable String fluidId,
        @Nullable String chemicalId,
        @Nullable String tag,
        EntryCounts counts,
        SlotMapping slotMapping,
        NbtConstraints nbt) {

    public static final NbtPath ENCHANTED_PATH = NbtPath.of(NbtPath.Component.of("minecraft:enchanted"));
    public static final NbtPath DURABILITY_PATH = NbtPath.of(NbtPath.Component.of("minecraft:durability"));

    private static final String KEY_SLOT = "slot";
    private static final String KEY_ITEM = "item";
    private static final String KEY_FLUID = "fluid";
    private static final String KEY_CHEMICAL = "chemical";
    private static final String KEY_TAG = "tag";
    private static final String KEY_AMOUNT = "amount";
    private static final String KEY_BATCH = "batch";
    private static final String KEY_STOCK = "stock";
    private static final String KEY_SLOT_MAP = "slot_map";
    private static final String KEY_SLOT_MAP_EXPR = "slot_map_expr";
    private static final String KEY_RULES = "nbt_rules";
    private static final String KEY_MATCH_ANY = "nbt_match_any";
    private static final String KEY_STRICT = "nbt_strict";
    private static final String KEY_RAW = "nbt_raw";

    public GeneralFilterEntry {
        counts = counts == null ? EntryCounts.EMPTY : counts;
        slotMapping = slotMapping == null ? SlotMapping.EMPTY : slotMapping;
        nbt = nbt == null ? NbtConstraints.EMPTY : nbt;
    }

    public static GeneralFilterEntry empty(int slot) {
        return new GeneralFilterEntry(slot, null, null, null, null, EntryCounts.EMPTY, SlotMapping.EMPTY,
                NbtConstraints.EMPTY);
    }

    public boolean isEmpty() {
        return equals(empty(slot));
    }

    public GeneralFilterEntry withItem(@Nullable StackSnapshot value) {
        return new GeneralFilterEntry(slot, value, fluidId, chemicalId, tag, counts, slotMapping, nbt);
    }

    public GeneralFilterEntry withTag(@Nullable String value) {
        return new GeneralFilterEntry(slot, item, fluidId, chemicalId, value, counts, slotMapping, nbt);
    }

    public GeneralFilterEntry withCounts(EntryCounts value) {
        return new GeneralFilterEntry(slot, item, fluidId, chemicalId, tag, value, slotMapping, nbt);
    }

    public GeneralFilterEntry withNbt(NbtConstraints value) {
        return new GeneralFilterEntry(slot, item, fluidId, chemicalId, tag, counts, slotMapping, value);
    }

    public GeneralFilterEntry withSlotMapping(SlotMapping value) {
        return new GeneralFilterEntry(slot, item, fluidId, chemicalId, tag, counts, value, nbt);
    }

    // legacy: filter saved before v2
    public static GeneralFilterEntry load(CompoundTag entry, boolean legacy) {
        StackSnapshot item = entry.contains(KEY_ITEM, Tag.TAG_COMPOUND)
                ? new StackSnapshot(entry.getCompound(KEY_ITEM).copy()) : null;
        EntryCounts counts = new EntryCounts(entry.getInt(KEY_AMOUNT), entry.getInt(KEY_BATCH),
                entry.getInt(KEY_STOCK));
        int[] slots = entry.getIntArray(KEY_SLOT_MAP);
        SlotMapping mapping = slots.length == 0 ? SlotMapping.EMPTY
                : new SlotMapping(Arrays.stream(slots).boxed().toList(), entry.getString(KEY_SLOT_MAP_EXPR));
        Optional<Boolean> strict = entry.contains(KEY_STRICT, Tag.TAG_BYTE)
                ? Optional.of(entry.getBoolean(KEY_STRICT))
                : legacy ? Optional.of(false) : Optional.empty();
        NbtConstraints nbt = new NbtConstraints(loadRules(entry), entry.getBoolean(KEY_MATCH_ANY), strict,
                entry.getString(KEY_RAW));
        return new GeneralFilterEntry(entry.getInt(KEY_SLOT), item, string(entry, KEY_FLUID),
                string(entry, KEY_CHEMICAL), string(entry, KEY_TAG), counts, mapping, nbt);
    }

    public CompoundTag save() {
        CompoundTag entry = new CompoundTag();
        entry.putInt(KEY_SLOT, slot);
        if (item != null)
            entry.put(KEY_ITEM, item.tag().copy());
        putString(entry, KEY_FLUID, fluidId);
        putString(entry, KEY_CHEMICAL, chemicalId);
        putString(entry, KEY_TAG, tag);
        putPositive(entry, KEY_AMOUNT, counts.amount());
        putPositive(entry, KEY_BATCH, counts.batch());
        putPositive(entry, KEY_STOCK, counts.stock());
        if (!slotMapping.slots().isEmpty()) {
            entry.putIntArray(KEY_SLOT_MAP, slotMapping.slots());
            putString(entry, KEY_SLOT_MAP_EXPR, slotMapping.expression());
        }
        saveNbt(entry);
        return entry;
    }

    private void saveNbt(CompoundTag entry) {
        if (!nbt.rules().isEmpty()) {
            ListTag rules = new ListTag();
            nbt.rules().forEach(rule -> rules.add(rule.save()));
            entry.put(KEY_RULES, rules);
        }
        if (nbt.matchAny())
            entry.putBoolean(KEY_MATCH_ANY, true);
        nbt.strict().ifPresent(value -> entry.putBoolean(KEY_STRICT, value));
        putString(entry, KEY_RAW, nbt.raw());
    }

    // Folds 1.3.4 single rules
    private static List<NbtCriterion> loadRules(CompoundTag entry) {
        List<NbtCriterion> rules = new ArrayList<>();
        for (Tag tag : entry.getList(KEY_RULES, Tag.TAG_COMPOUND)) {
            NbtCriterion rule = NbtCriterion.load((CompoundTag) tag);
            if (rule != null)
                rules.add(rule);
        }
        if (rules.isEmpty() && entry.contains("nbt_path", Tag.TAG_STRING) && entry.contains("nbt_val")) {
            rules.add(new NbtCriterion(NbtPath.parseLenient(entry.getString("nbt_path")),
                    entry.getString("nbt_op"), entry.get("nbt_val")));
        }
        if (entry.contains("enchanted", Tag.TAG_BYTE) && rules.stream().noneMatch(r -> r.path().equals(ENCHANTED_PATH)))
            rules.add(new NbtCriterion(ENCHANTED_PATH, "=", ByteTag.valueOf(entry.getBoolean("enchanted"))));
        if (entry.contains("dur_op", Tag.TAG_STRING) && rules.stream().noneMatch(r -> r.path().equals(DURABILITY_PATH))) {
            rules.add(new NbtCriterion(DURABILITY_PATH,
                    DurabilityFilterData.Operator.fromId(entry.getString("dur_op")).symbol(),
                    IntTag.valueOf(Math.max(0, Math.min(3000, entry.getInt("dur_val"))))));
        }
        return rules;
    }

    @Nullable
    private static String string(CompoundTag entry, String key) {
        return entry.contains(key, Tag.TAG_STRING) ? entry.getString(key) : null;
    }

    private static void putString(CompoundTag entry, String key, @Nullable String value) {
        if (value != null && !value.isEmpty())
            entry.putString(key, value);
    }

    private static void putPositive(CompoundTag entry, String key, int value) {
        if (value > 0)
            entry.putInt(key, value);
    }

    public record EntryCounts(int amount, int batch, int stock) {
        public static final EntryCounts EMPTY = new EntryCounts(0, 0, 0);
    }

    public record SlotMapping(List<Integer> slots, String expression) {
        public static final SlotMapping EMPTY = new SlotMapping(List.of(), "");

        public SlotMapping {
            slots = List.copyOf(slots);
            expression = expression == null ? "" : expression;
        }
    }

    public record NbtConstraints(List<NbtCriterion> rules, boolean matchAny, Optional<Boolean> strict, String raw) {
        public static final NbtConstraints EMPTY = new NbtConstraints(List.of(), false, Optional.empty(), "");

        public NbtConstraints {
            rules = List.copyOf(rules);
            strict = strict == null ? Optional.empty() : strict;
            raw = raw == null ? "" : raw;
        }

        public NbtConstraints withRules(List<NbtCriterion> value) {
            return new NbtConstraints(value, matchAny, strict, raw);
        }

        public NbtConstraints withMatchAny(boolean value) {
            return new NbtConstraints(rules, value, strict, raw);
        }

        public NbtConstraints withStrict(boolean value) {
            return new NbtConstraints(rules, matchAny, Optional.of(value), raw);
        }

        public NbtConstraints withRaw(@Nullable String value) {
            return new NbtConstraints(rules, matchAny, strict, value);
        }
    }
}
