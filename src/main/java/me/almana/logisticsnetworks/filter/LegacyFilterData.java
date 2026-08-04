package me.almana.logisticsnetworks.filter;

import java.util.BitSet;
import me.almana.logisticsnetworks.item.LegacyFilterItem;
import me.almana.logisticsnetworks.util.ItemDataUtil;
import me.almana.logisticsnetworks.util.NbtAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public final class LegacyFilterData {
    private static final String CANONICAL_ROOT = "ln_filter";
    private static final String LEGACY_AMOUNT = "legacy_amount";

    private LegacyFilterData() {
    }

    public static CompoundTag getCanonicalRoot(ItemStack stack, CompoundTag customData) {
        if (customData.contains(CANONICAL_ROOT)) {
            return NbtAccess.getCompound(customData, CANONICAL_ROOT);
        }
        if (stack.getItem() instanceof LegacyFilterItem item) {
            return convert(item.kind(), customData);
        }
        return new CompoundTag();
    }

    public static void removeLegacyRoot(ItemStack stack, CompoundTag customData) {
        if (stack.getItem() instanceof LegacyFilterItem item) {
            customData.remove(rootKey(item.kind()));
        }
    }

    public static int getAmountThreshold(ItemStack stack) {
        if (!(stack.getItem() instanceof LegacyFilterItem item) || item.kind() != LegacyFilterItem.Kind.AMOUNT) {
            return -1;
        }
        CompoundTag customData = ItemDataUtil.getCustomData(stack);
        if (customData.contains(CANONICAL_ROOT)) {
            CompoundTag root = NbtAccess.getCompound(customData, CANONICAL_ROOT);
            return root.contains(LEGACY_AMOUNT) ? clampAmount(NbtAccess.getInt(root, LEGACY_AMOUNT, 0)) : -1;
        }
        CompoundTag legacy = NbtAccess.getCompound(customData, rootKey(item.kind()));
        return clampAmount(NbtAccess.getInt(legacy, "amount", 64));
    }

    static CompoundTag convert(LegacyFilterItem.Kind kind, CompoundTag customData) {
        CompoundTag legacy = NbtAccess.getCompound(customData, rootKey(kind));
        CompoundTag root = new CompoundTag();
        switch (kind) {
            case AMOUNT -> root.putInt(LEGACY_AMOUNT, clampAmount(NbtAccess.getInt(legacy, "amount", 64)));
            case DURABILITY -> convertDurability(legacy, root);
            case NBT -> convertNbt(legacy, root);
            case SLOT -> convertSlots(legacy, root);
            case TAG -> convertTag(legacy, root);
        }
        return root;
    }

    private static void convertDurability(CompoundTag legacy, CompoundTag root) {
        CompoundTag entry = new CompoundTag();
        entry.putInt("slot", 0);
        String operator = NbtAccess.getString(legacy, "operator", "ge");
        entry.putString("dur_op", switch (operator) {
            case "le", "eq" -> operator;
            default -> "ge";
        });
        entry.putInt("dur_val", Math.max(0, Math.min(3000, NbtAccess.getInt(legacy, "value", 0))));
        putEntry(root, entry);
    }

    private static void convertNbt(CompoundTag legacy, CompoundTag root) {
        copyMode(legacy, root);
        ListTag converted = new ListTag();
        ListTag rules = NbtAccess.getList(legacy, "rules", 10);
        for (Tag tag : rules) {
            if (!(tag instanceof CompoundTag rule) || !NbtAccess.getBoolean(rule, "enabled", true)) {
                continue;
            }
            addNbtRule(converted, NbtAccess.getString(rule, "path", ""),
                    NbtAccess.getInt(rule, "operator", 0) == 1 ? "!=" : "=", rule.get("value"));
        }
        if (converted.isEmpty()) {
            addNbtRule(converted, NbtAccess.getString(legacy, "path", ""), "=", legacy.get("value"));
        }
        if (converted.isEmpty()) {
            return;
        }
        CompoundTag entry = new CompoundTag();
        entry.putInt("slot", 0);
        entry.put("nbt_rules", converted);
        putEntry(root, entry);
        if (!legacy.contains("target")) {
            CompoundTag first = (CompoundTag) converted.get(0);
            if (NbtAccess.getString(first, "p", "").startsWith("fluid.components")) {
                root.putInt("target", FilterTargetType.FLUIDS.ordinal());
            }
        }
    }

    private static void addNbtRule(ListTag rules, String path, String operator, Tag value) {
        if (path.isBlank() || value == null) {
            return;
        }
        CompoundTag rule = new CompoundTag();
        rule.putString("p", path);
        rule.putString("o", operator);
        rule.put("v", value.copy());
        rules.add(rule);
    }

    private static void convertSlots(CompoundTag legacy, CompoundTag root) {
        copyBlacklist(legacy, root);
        BitSet slots = new BitSet(SlotExpressionUtil.MAX_SLOT + 1);
        for (int slot : NbtAccess.getIntArray(legacy, "slots")) {
            if (slot >= SlotExpressionUtil.MIN_SLOT && slot <= SlotExpressionUtil.MAX_SLOT) {
                slots.set(slot);
            }
        }
        if (slots.isEmpty()) {
            return;
        }
        CompoundTag entry = new CompoundTag();
        entry.putInt("slot", 0);
        entry.putIntArray("slot_map", slots.stream().toArray());
        putEntry(root, entry);
    }

    private static void convertTag(CompoundTag legacy, CompoundTag root) {
        copyMode(legacy, root);
        ListTag tags = NbtAccess.getList(legacy, "tags", 8);
        for (Tag tag : tags) {
            String value = stringValue(tag);
            String normalized = FilterTagUtil.normalizeTag(value);
            if (normalized != null) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("slot", 0);
                entry.putString("tag", normalized);
                putEntry(root, entry);
                return;
            }
        }
    }

    private static String stringValue(Tag tag) {
        //? if <26 {
        /*return tag.getAsString();
        *///?} else {
        return tag instanceof StringTag value ? value.value() : "";
        //?}
    }

    private static void copyMode(CompoundTag legacy, CompoundTag root) {
        copyBlacklist(legacy, root);
        int target = NbtAccess.getInt(legacy, "target", 0);
        if (target > 0 && target < FilterTargetType.values().length) {
            root.putInt("target", target);
        }
    }

    private static void copyBlacklist(CompoundTag legacy, CompoundTag root) {
        if (NbtAccess.getBoolean(legacy, "blacklist", false)) {
            root.putBoolean("blacklist", true);
        }
    }

    private static void putEntry(CompoundTag root, CompoundTag entry) {
        ListTag entries = new ListTag();
        entries.add(entry);
        root.put("items", entries);
    }

    private static int clampAmount(int amount) {
        return Math.max(0, Math.min(1_000_000, amount));
    }

    private static String rootKey(LegacyFilterItem.Kind kind) {
        return "ln_" + kind.id();
    }
}
