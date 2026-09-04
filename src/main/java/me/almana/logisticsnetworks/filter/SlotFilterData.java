package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.component.FilterSettingsData;
import me.almana.logisticsnetworks.component.LegacyComponentMigration;
import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import me.almana.logisticsnetworks.component.SlotFilterConfig;
import me.almana.logisticsnetworks.item.SlotFilterItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public final class SlotFilterData {

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
        LegacyComponentMigration.migrateSlotFilter(stack);
        return FilterSettingsData.get(stack).blacklist();
    }

    public static void setBlacklist(ItemStack stack, boolean blacklist) {
        if (!isSlotFilterItem(stack)) {
            return;
        }

        LegacyComponentMigration.migrateSlotFilter(stack);
        FilterSettingsData.setBlacklist(stack, blacklist);
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

        LegacyComponentMigration.migrateSlotFilter(stack);
        return stack.getOrDefault(LogisticsDataComponents.SLOT_FILTER, new SlotFilterConfig(List.of())).slots();
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
        return SlotExpressionUtil.formatSlots(slots);
    }

    private static boolean setSlots(ItemStack stack, List<Integer> slots) {
        List<Integer> current = getSlots(stack);
        if (current.equals(slots)) {
            return false;
        }

        LegacyComponentMigration.migrateSlotFilter(stack);
        if (slots.isEmpty()) {
            stack.remove(LogisticsDataComponents.SLOT_FILTER);
        } else {
            stack.set(LogisticsDataComponents.SLOT_FILTER, new SlotFilterConfig(slots));
        }
        return true;
    }

    private static BitSet parseSlots(String expression) {
        return SlotExpressionUtil.parseSlots(expression);
    }

}
