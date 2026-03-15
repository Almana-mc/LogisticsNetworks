package me.almana.logisticsnetworks.filter;

public enum NameMatchScope {
    NAME,
    TOOLTIP,
    BOTH;

    public NameMatchScope next() {
        NameMatchScope[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static NameMatchScope fromOrdinal(int ordinal) {
        NameMatchScope[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return NAME;
        }
        return values[ordinal];
    }
}
