package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.component.FilterSettingsData;
import me.almana.logisticsnetworks.component.LegacyComponentMigration;
import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import me.almana.logisticsnetworks.component.TagFilterConfig;
import me.almana.logisticsnetworks.item.TagFilterItem;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class TagFilterData {

    private TagFilterData() {
    }

    public static boolean isTagFilterItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof TagFilterItem;
    }

    public static boolean isBlacklist(ItemStack stack) {
        if (!isTagFilterItem(stack)) {
            return false;
        }
        LegacyComponentMigration.migrateTagFilter(stack);
        return FilterSettingsData.get(stack).blacklist();
    }

    public static List<String> getTagFilters(ItemStack filterStack) {
        if (!isTagFilterItem(filterStack)) {
            return List.of();
        }

        LegacyComponentMigration.migrateTagFilter(filterStack);
        TagFilterConfig config = filterStack.get(LogisticsDataComponents.TAG_FILTER);
        if (config == null || config.tag().isEmpty()) {
            return List.of();
        }
        return List.of(config.tag());
    }

    public static int getTagFilterCount(ItemStack filterStack) {
        return getTagFilters(filterStack).size();
    }

}
