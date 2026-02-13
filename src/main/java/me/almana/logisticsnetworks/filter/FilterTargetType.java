package me.almana.logisticsnetworks.filter;

public enum FilterTargetType {
    ITEMS,
    FLUIDS;

    public FilterTargetType next() {
        return this == ITEMS ? FLUIDS : ITEMS;
    }

    public static FilterTargetType fromOrdinal(int ordinal) {
        FilterTargetType[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return ITEMS;
        }
        return values[ordinal];
    }
}
