package me.almana.logisticsnetworks.component;

import me.almana.logisticsnetworks.filter.AmountFilterData;
import me.almana.logisticsnetworks.filter.DurabilityFilterData;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.ModFilterData;
import me.almana.logisticsnetworks.filter.NameFilterData;
import me.almana.logisticsnetworks.filter.NbtFilterData;
import me.almana.logisticsnetworks.filter.SlotFilterData;
import me.almana.logisticsnetworks.filter.TagFilterData;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class FilterComponentData {

    private FilterComponentData() {
    }

    public static void migrate(ItemStack stack, @Nullable HolderLookup.Provider provider) {
        if (FilterItemData.isFilterItem(stack)) {
            LegacyComponentMigration.migrateGeneralFilter(stack, provider);
        } else if (TagFilterData.isTagFilterItem(stack)) {
            LegacyComponentMigration.migrateTagFilter(stack);
        } else if (ModFilterData.isModFilter(stack)) {
            LegacyComponentMigration.migrateModFilter(stack);
        } else if (NameFilterData.isNameFilter(stack)) {
            LegacyComponentMigration.migrateNameFilter(stack);
        } else if (AmountFilterData.isAmountFilterItem(stack)) {
            LegacyComponentMigration.migrateAmountFilter(stack);
        } else if (DurabilityFilterData.isDurabilityFilterItem(stack)) {
            LegacyComponentMigration.migrateDurabilityFilter(stack);
        } else if (NbtFilterData.isNbtFilterItem(stack)) {
            LegacyComponentMigration.migrateNbtFilter(stack);
        } else if (SlotFilterData.isSlotFilterItem(stack)) {
            LegacyComponentMigration.migrateSlotFilter(stack);
        }
    }

    public static boolean isConfigured(ItemStack stack, @Nullable HolderLookup.Provider provider) {
        migrate(stack, provider);
        return stack.has(LogisticsDataComponents.FILTER_SETTINGS)
                || stack.has(LogisticsDataComponents.FILTER_ENTRIES)
                || stack.has(LogisticsDataComponents.TAG_FILTER)
                || stack.has(LogisticsDataComponents.MOD_FILTER)
                || stack.has(LogisticsDataComponents.NAME_FILTER)
                || stack.has(LogisticsDataComponents.AMOUNT_FILTER)
                || stack.has(LogisticsDataComponents.DURABILITY_FILTER)
                || stack.has(LogisticsDataComponents.NBT_FILTER)
                || stack.has(LogisticsDataComponents.SLOT_FILTER);
    }
}
