package me.almana.logisticsnetworks.filter;

public enum NameMatchScope {
    NAME;

    public NameMatchScope next() {
        return NAME;
    }

    public static NameMatchScope fromOrdinal(int ordinal) {
        return NAME;
    }
}
