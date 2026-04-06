package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.util.ItemDataUtil;

import me.almana.logisticsnetworks.item.SlotFilterItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.BitSet;
import java.util.List;

public final class SlotFilterData {

    private static final String KEY_ROOT = "ln_slot_filter";
    private static final String KEY_IS_BLACKLIST = "blacklist";
    private static final String KEY_SLOTS = "slots";

    public static final int MIN_SLOT = SlotExpressionUtil.MIN_SLOT;
    public static final int MAX_SLOT = SlotExpressionUtil.MAX_SLOT;

    public record ParseResult(boolean valid, boolean changed) {
    }

    private SlotFilterData() {
    }

    public static boolean isSlotFilterItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof SlotFilterItem;
    }

    public static boolean isBlacklist(ItemStack stack) {
        if (!isSlotFilterItem(stack)) {
            return false;
        }
        return getRoot(stack).getBoolean(KEY_IS_BLACKLIST);
    }

    public static void setBlacklist(ItemStack stack, boolean blacklist) {
        if (!isSlotFilterItem(stack)) {
            return;
        }

        updateRoot(stack, root -> {
            if (blacklist) {
                root.putBoolean(KEY_IS_BLACKLIST, true);
            } else {
                root.remove(KEY_IS_BLACKLIST);
            }
        });
    }

    public static boolean hasAnySlots(ItemStack stack) {
        if (!isSlotFilterItem(stack)) {
            return false;
        }
        return !getSlots(stack).isEmpty();
    }

    public static List<Integer> getSlots(ItemStack stack) {
        if (!isSlotFilterItem(stack)) {
            return List.of();
        }

        int[] stored = getRoot(stack).getIntArray(KEY_SLOTS);
        if (stored.length == 0) {
            return List.of();
        }

        BitSet bits = new BitSet(SlotExpressionUtil.MAX_SLOT + 1);
        for (int slot : stored) {
            if (slot >= SlotExpressionUtil.MIN_SLOT && slot <= SlotExpressionUtil.MAX_SLOT) {
                bits.set(slot);
            }
        }

        if (bits.isEmpty()) {
            return List.of();
        }

        return SlotExpressionUtil.bitSetToList(bits);
    }

    public static String getSlotExpression(ItemStack stack) {
        return SlotExpressionUtil.formatSlots(getSlots(stack));
    }

    public static ParseResult setSlotsFromExpression(ItemStack stack, String expression) {
        if (!isSlotFilterItem(stack)) {
            return new ParseResult(false, false);
        }

        String normalized = expression == null ? "" : expression.trim();
        if (normalized.isEmpty()) {
            boolean changed = setSlots(stack, List.of());
            return new ParseResult(true, changed);
        }

        BitSet parsed = SlotExpressionUtil.parseSlots(normalized);
        if (parsed == null) {
            return new ParseResult(false, false);
        }

        boolean changed = setSlots(stack, SlotExpressionUtil.bitSetToList(parsed));
        return new ParseResult(true, changed);
    }

    private static boolean setSlots(ItemStack stack, List<Integer> slots) {
        List<Integer> current = getSlots(stack);
        if (current.equals(slots)) {
            return false;
        }

        int[] compact = slots.stream().mapToInt(Integer::intValue).toArray();
        updateRoot(stack, root -> {
            if (compact.length == 0) {
                root.remove(KEY_SLOTS);
            } else {
                root.putIntArray(KEY_SLOTS, compact);
            }
        });
        return true;
    }

    private static CompoundTag getRoot(ItemStack stack) {
        CompoundTag custom = ItemDataUtil.getCustomData(stack);
        return custom.contains(KEY_ROOT, Tag.TAG_COMPOUND) ? custom.getCompound(KEY_ROOT) : new CompoundTag();
    }

    private static void updateRoot(ItemStack stack, java.util.function.Consumer<CompoundTag> modifier) {
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



