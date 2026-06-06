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
    NAME,
    SLOT;

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
        if (SlotFilterData.isSlotFilterItem(stack)) {
            return SLOT;
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

    public VirtualFilterType next() {
        return switch (this) {
            case EXISTING, SMALL -> MEDIUM;
            case MEDIUM -> BIG;
            case BIG -> MOD;
            case MOD -> NAME;
            case NAME -> SLOT;
            case SLOT -> SMALL;
        };
    }

    public ItemStack createStack() {
        ItemStack stack = switch (this) {
            case EXISTING, SMALL -> new ItemStack(Registration.SMALL_FILTER.get());
            case MEDIUM -> new ItemStack(Registration.MEDIUM_FILTER.get());
            case BIG -> new ItemStack(Registration.BIG_FILTER.get());
            case MOD -> new ItemStack(Registration.MOD_FILTER.get());
            case NAME -> new ItemStack(Registration.NAME_FILTER.get());
            case SLOT -> new ItemStack(Registration.SMALL_FILTER.get());
        };
        if (this == SLOT) {
            SlotFilterData.markSlotFilter(stack);
        }
        return stack;
    }

    public boolean isSpecial() {
        return this == MOD || this == NAME || this == SLOT;
    }

    public String translationKey() {
        return switch (this) {
            case EXISTING, SMALL -> "gui.logisticsnetworks.node.filter.type.small";
            case MEDIUM -> "gui.logisticsnetworks.node.filter.type.medium";
            case BIG -> "gui.logisticsnetworks.node.filter.type.big";
            case MOD -> "gui.logisticsnetworks.node.filter.type.mod";
            case NAME -> "gui.logisticsnetworks.node.filter.type.name";
            case SLOT -> "gui.logisticsnetworks.node.filter.type.slot";
        };
    }
}
