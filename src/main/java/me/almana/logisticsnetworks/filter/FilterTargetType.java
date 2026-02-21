package me.almana.logisticsnetworks.filter;

public enum FilterTargetType {
    ITEMS,
    FLUIDS,
    CHEMICALS;

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
