package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.data.ChannelType;

public enum FilterTargetType {
    ITEMS,
    FLUIDS,
    CHEMICALS;

    public static FilterTargetType forChannel(ChannelType type) {
        return switch (type) {
            case ITEM -> ITEMS;
            case FLUID -> FLUIDS;
            case CHEMICAL -> CHEMICALS;
            case ENERGY, SOURCE -> null;
        };
    }

    public FilterTargetType next() {
        FilterTargetType[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static FilterTargetType fromOrdinal(int ordinal) {
        FilterTargetType[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return ITEMS;
        }
        return values[ordinal];
    }
}
