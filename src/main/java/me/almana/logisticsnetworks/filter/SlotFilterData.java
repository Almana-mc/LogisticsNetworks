package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.component.FilterSettingsData;
import me.almana.logisticsnetworks.component.LegacyComponentMigration;
import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import me.almana.logisticsnetworks.component.SlotFilterConfig;
import me.almana.logisticsnetworks.item.SlotFilterItem;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class SlotFilterData {

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

    public static String formatSlots(List<Integer> slots) {
        return SlotExpressionUtil.formatSlots(slots);
    }

}
