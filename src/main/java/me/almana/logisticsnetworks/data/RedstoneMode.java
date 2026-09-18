package me.almana.logisticsnetworks.data;

public enum RedstoneMode {
    HIGH,
    LOW;

    public static RedstoneMode fromSerialized(String value) {
        return "HIGH".equalsIgnoreCase(value) ? HIGH : LOW;
    }

    public static boolean disablesChannel(String value) {
        return "ALWAYS_OFF".equalsIgnoreCase(value);
    }
}
