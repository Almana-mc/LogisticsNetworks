package me.almana.logisticsnetworks.integration.storage;

public final class DirectStorageReads {
    private DirectStorageReads() {
    }

    public static int clamp(long amount) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, amount));
    }
}
