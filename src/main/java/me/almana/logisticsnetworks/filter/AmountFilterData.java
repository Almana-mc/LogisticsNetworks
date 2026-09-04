package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.component.AmountFilterConfig;
import me.almana.logisticsnetworks.component.FilterSettingsData;
import me.almana.logisticsnetworks.component.LegacyComponentMigration;
import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import me.almana.logisticsnetworks.item.AmountFilterItem;
import net.minecraft.world.item.ItemStack;

public final class AmountFilterData {

    private static final int DEFAULT_AMOUNT = AmountFilterConfig.DEFAULT_AMOUNT;
    private static final int MIN_AMOUNT = 0;
    private static final int MAX_AMOUNT = 1_000_000;

    private AmountFilterData() {
    }

    public static boolean isAmountFilterItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof AmountFilterItem;
    }

    public static int getAmount(ItemStack stack) {
        if (!isAmountFilterItem(stack))
            return DEFAULT_AMOUNT;

        LegacyComponentMigration.migrateAmountFilter(stack);
        return stack.getOrDefault(LogisticsDataComponents.AMOUNT_FILTER,
                new AmountFilterConfig(DEFAULT_AMOUNT)).amount();
    }

    public static boolean isBlacklist(ItemStack stack) {
        if (!isAmountFilterItem(stack))
            return false;
        LegacyComponentMigration.migrateAmountFilter(stack);
        return FilterSettingsData.get(stack).blacklist();
    }

    public static void setBlacklist(ItemStack stack, boolean isBlacklist) {
        if (!isAmountFilterItem(stack))
            return;
        LegacyComponentMigration.migrateAmountFilter(stack);
        FilterSettingsData.setBlacklist(stack, isBlacklist);
    }

    public static FilterTargetType getTargetType(ItemStack stack) {
        if (!isAmountFilterItem(stack))
            return FilterTargetType.ITEMS;
        LegacyComponentMigration.migrateAmountFilter(stack);
        return FilterSettingsData.get(stack).target();
    }

    public static void setTargetType(ItemStack stack, FilterTargetType type) {
        if (!isAmountFilterItem(stack))
            return;
        LegacyComponentMigration.migrateAmountFilter(stack);
        FilterSettingsData.setTarget(stack, type);
    }

    public static void setAmount(ItemStack stack, int amount) {
        if (!isAmountFilterItem(stack))
            return;

        int clamped = clamp(amount);
        LegacyComponentMigration.migrateAmountFilter(stack);
        if (clamped == DEFAULT_AMOUNT) {
            stack.remove(LogisticsDataComponents.AMOUNT_FILTER);
        } else {
            stack.set(LogisticsDataComponents.AMOUNT_FILTER, new AmountFilterConfig(clamped));
        }
    }

    private static int clamp(int amount) {
        return Math.max(MIN_AMOUNT, Math.min(MAX_AMOUNT, amount));
    }

}
