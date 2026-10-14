package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.item.BaseFilterItem;
import me.almana.logisticsnetworks.item.ModFilterItem;
import me.almana.logisticsnetworks.item.NameFilterItem;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.world.item.ItemStack;

public enum VirtualFilterType {
    EXISTING,
    SMALL,
    MEDIUM,
    BIG,
    MOD,
    NAME;

    public static VirtualFilterType fromOrdinal(int ordinal) {
        VirtualFilterType[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return EXISTING;
        }
        return values[ordinal];
    }

    public static VirtualFilterType fromStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return SMALL;
        }
        if (stack.is(Registration.BIG_FILTER.get())) {
            return BIG;
        }
        if (stack.is(Registration.MEDIUM_FILTER.get())) {
            return MEDIUM;
        }
        if (stack.is(Registration.SMALL_FILTER.get())) {
            return SMALL;
        }
        if (stack.getItem() instanceof BaseFilterItem) {
            return BIG;
        }
        if (stack.getItem() instanceof ModFilterItem) {
            return MOD;
        }
        if (stack.getItem() instanceof NameFilterItem) {
            return NAME;
        }
        return SMALL;
    }

    public ItemStack createStack() {
        return switch (this) {
            case EXISTING, SMALL -> new ItemStack(Registration.SMALL_FILTER.get());
            case MEDIUM -> new ItemStack(Registration.MEDIUM_FILTER.get());
            case BIG -> new ItemStack(Registration.BIG_FILTER.get());
            case MOD -> new ItemStack(Registration.MOD_FILTER.get());
            case NAME -> new ItemStack(Registration.NAME_FILTER.get());
        };
    }

    public boolean isSpecial() {
        return this == MOD || this == NAME;
    }
}
