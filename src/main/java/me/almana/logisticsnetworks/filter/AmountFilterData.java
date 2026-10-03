package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.component.AmountFilterConfig;
import me.almana.logisticsnetworks.component.LegacyComponentMigration;
import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import me.almana.logisticsnetworks.item.AmountFilterItem;
import net.minecraft.world.item.ItemStack;

public final class AmountFilterData {

    private static final int DEFAULT_AMOUNT = AmountFilterConfig.DEFAULT_AMOUNT;

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

}
