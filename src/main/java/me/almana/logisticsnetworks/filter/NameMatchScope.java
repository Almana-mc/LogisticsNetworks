package me.almana.logisticsnetworks.filter;

import java.util.Locale;

public enum NameMatchScope {
    NAME,
    TOOLTIP,
    BOTH;

    public NameMatchScope next() {
        NameMatchScope[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static NameMatchScope fromOrdinal(int ordinal) {
        NameMatchScope[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NAME;
    }

    public static NameMatchScope byName(String name) {
        for (NameMatchScope scope : values()) {
            if (scope.serializedName().equals(name))
                return scope;
        }
        return NAME;
    }
}
