package me.almana.logisticsnetworks.component;

import me.almana.logisticsnetworks.filter.FilterTargetType;
import net.minecraft.world.item.ItemStack;

public final class FilterSettingsData {

    private FilterSettingsData() {
    }

    public static FilterSettings get(ItemStack stack) {
        return stack.getOrDefault(LogisticsDataComponents.FILTER_SETTINGS, FilterSettings.DEFAULT);
    }

    public static void setBlacklist(ItemStack stack, boolean blacklist) {
        FilterSettings current = get(stack);
        set(stack, new FilterSettings(current.target(), blacklist));
    }

    public static void setTarget(ItemStack stack, FilterTargetType target) {
        FilterSettings current = get(stack);
        set(stack, new FilterSettings(target, current.blacklist()));
    }

    public static void set(ItemStack stack, FilterSettings settings) {
        if (settings.isDefault()) {
            stack.remove(LogisticsDataComponents.FILTER_SETTINGS);
        } else {
            stack.set(LogisticsDataComponents.FILTER_SETTINGS, settings);
        }
    }
}
