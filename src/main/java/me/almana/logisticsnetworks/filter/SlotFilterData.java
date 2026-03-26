package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.item.SlotFilterItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.Consumer;

public final class SlotFilterData {

    private static final String KEY_ROOT = "ln_slot_filter";
    private static final String KEY_IS_BLACKLIST = "blacklist";
    private static final String KEY_SLOTS = "slots";

    public static final int MIN_SLOT = 0;
    public static final int MAX_SLOT = 53;

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
        return getRoot(stack).getBooleanOr(KEY_IS_BLACKLIST, false);
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

        int[] stored = getRoot(stack).getIntArray(KEY_SLOTS).orElseGet(() -> new int[0]);
        if (stored.length == 0) {
            return List.of();
        }

        BitSet bits = new BitSet(MAX_SLOT + 1);
        for (int slot : stored) {
            if (slot >= MIN_SLOT && slot <= MAX_SLOT) {
                bits.set(slot);
            }
        }

        if (bits.isEmpty()) {
            return List.of();
        }

        List<Integer> slots = new ArrayList<>();
        for (int slot = bits.nextSetBit(MIN_SLOT); slot >= 0; slot = bits.nextSetBit(slot + 1)) {
            slots.add(slot);
        }
        return slots;
    }

    public static String getSlotExpression(ItemStack stack) {
        return formatSlots(getSlots(stack));
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

        BitSet parsed = parseSlots(normalized);
        if (parsed == null) {
            return new ParseResult(false, false);
        }

        List<Integer> slots = new ArrayList<>();
        for (int slot = parsed.nextSetBit(MIN_SLOT); slot >= 0; slot = parsed.nextSetBit(slot + 1)) {
            slots.add(slot);
        }
        boolean changed = setSlots(stack, slots);
        return new ParseResult(true, changed);
    }

    public static String formatSlots(List<Integer> slots) {
        if (slots == null || slots.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < slots.size()) {
            int start = slots.get(i);
            int end = start;
            while (i + 1 < slots.size() && slots.get(i + 1) == end + 1) {
                i++;
                end = slots.get(i);
            }

            if (!out.isEmpty()) {
                out.append(", ");
            }
            if (start == end) {
                out.append(start);
            } else {
                out.append(start).append('-').append(end);
            }
            i++;
        }

        return out.toString();
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

    private static BitSet parseSlots(String expression) {
        BitSet bits = new BitSet(MAX_SLOT + 1);
        String[] tokens = expression.split("[,;\\s]+");

        for (String rawToken : tokens) {
            if (rawToken == null) {
                continue;
            }
            String token = rawToken.trim();
            if (token.isEmpty()) {
                continue;
            }

            int dash = token.indexOf('-');
            if (dash >= 0) {
                String left = token.substring(0, dash).trim();
                String right = token.substring(dash + 1).trim();
                Integer a = parseSlot(left);
                Integer b = parseSlot(right);
                if (a == null || b == null) {
                    return null;
                }

                int from = Math.min(a, b);
                int to = Math.max(a, b);
                bits.set(from, to + 1);
            } else {
                Integer slot = parseSlot(token);
                if (slot == null) {
                    return null;
                }
                bits.set(slot);
            }
        }
        return bits;
    }

    private static Integer parseSlot(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < MIN_SLOT || value > MAX_SLOT) {
                return null;
            }
            return value;
        } catch (NumberFormatException ignored) {
            return null;
        }
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
