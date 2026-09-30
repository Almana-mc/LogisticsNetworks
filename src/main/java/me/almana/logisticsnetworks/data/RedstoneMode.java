package me.almana.logisticsnetworks.data;

public enum RedstoneMode {
    IGNORED,
    HIGH,
    LOW;

    public static RedstoneMode fromSerialized(String value) {
        if ("HIGH".equalsIgnoreCase(value)) return HIGH;
        if ("LOW".equalsIgnoreCase(value)) return LOW;
        return IGNORED;
    }

    public static boolean disablesChannel(String value) {
        return "ALWAYS_OFF".equalsIgnoreCase(value);
    }
}
