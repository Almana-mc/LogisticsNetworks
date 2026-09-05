package me.almana.logisticsnetworks.component;

import me.almana.logisticsnetworks.filter.DurabilityFilterData;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.ModFilterData;
import me.almana.logisticsnetworks.filter.NameFilterData;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

public final class FilterComponentData {

    private FilterComponentData() {
    }

    public static void migrate(ItemStack stack, @Nullable HolderLookup.Provider provider) {
        if (!FilterItemData.isFilterItem(stack) && !ModFilterData.isModFilter(stack) && !NameFilterData.isNameFilter(stack)) {
            return;
        }
        LegacyComponentMigration.migrateGeneralFilter(stack, provider);
        LegacyComponentMigration.migrateModFilter(stack);
        LegacyComponentMigration.migrateNameFilter(stack);
        LegacyComponentMigration.migrateTagFilter(stack);
        LegacyComponentMigration.migrateAmountFilter(stack);
        LegacyComponentMigration.migrateDurabilityFilter(stack);
        LegacyComponentMigration.migrateNbtFilter(stack);
        LegacyComponentMigration.migrateSlotFilter(stack);
    }

    public static boolean isConfigured(ItemStack stack, @Nullable HolderLookup.Provider provider) {
        ItemStack working = stack.copy();
        migrate(working, provider);
        return hasConfiguredComponents(working) || hasPendingConfiguration(working, provider);
    }

    private static boolean hasConfiguredComponents(ItemStack stack) {
        GeneralFilterConfig entries = stack.get(LogisticsDataComponents.FILTER_ENTRIES);
        TagFilterConfig tag = stack.get(LogisticsDataComponents.TAG_FILTER);
        ModFilterConfig mod = stack.get(LogisticsDataComponents.MOD_FILTER);
        NameFilterConfig name = stack.get(LogisticsDataComponents.NAME_FILTER);
        AmountFilterConfig amount = stack.get(LogisticsDataComponents.AMOUNT_FILTER);
        DurabilityFilterConfig durability = stack.get(LogisticsDataComponents.DURABILITY_FILTER);
        NbtFilterConfig nbt = stack.get(LogisticsDataComponents.NBT_FILTER);
        SlotFilterConfig slots = stack.get(LogisticsDataComponents.SLOT_FILTER);
        return !FilterSettingsData.get(stack).isDefault()
                || entries != null && entries.entries().stream().anyMatch(entry -> !entry.equals(GeneralFilterEntry.empty(entry.slot())))
                || tag != null && !tag.tag().isEmpty()
                || mod != null && !mod.namespaces().isEmpty()
                || name != null && !name.expression().isEmpty()
                || amount != null && amount.amount() != AmountFilterConfig.DEFAULT_AMOUNT
                || durability != null && (durability.value() != 0
                        || durability.operator() != DurabilityFilterData.Operator.GREATER_OR_EQUAL)
                || nbt != null && !nbt.rules().isEmpty()
                || slots != null && !slots.slots().isEmpty();
    }

    private static boolean hasPendingConfiguration(ItemStack stack, @Nullable HolderLookup.Provider provider) {
        var legacy = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .getCompoundOrEmpty("ln_filter");
        return !stack.has(LogisticsDataComponents.FILTER_SETTINGS)
                && (legacy.getBooleanOr("blacklist", false) || legacy.getIntOr("target", 0) != 0)
                || !stack.has(LogisticsDataComponents.FILTER_ENTRIES)
                && !GeneralFilterBridge.read(legacy, provider, null).complete();
    }
}
