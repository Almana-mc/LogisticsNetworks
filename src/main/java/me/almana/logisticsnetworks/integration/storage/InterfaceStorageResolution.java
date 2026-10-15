package me.almana.logisticsnetworks.integration.storage;

import org.jetbrains.annotations.Nullable;

public record InterfaceStorageResolution(Status status, @Nullable StorageEndpoint endpoint) {

    public enum Status {
        UNSUPPORTED,
        UNAVAILABLE,
        AVAILABLE
    }

    private static final InterfaceStorageResolution UNSUPPORTED =
            new InterfaceStorageResolution(Status.UNSUPPORTED, null);
    private static final InterfaceStorageResolution UNAVAILABLE =
            new InterfaceStorageResolution(Status.UNAVAILABLE, null);

    public static InterfaceStorageResolution unsupported() {
        return UNSUPPORTED;
    }

    public static InterfaceStorageResolution unavailable() {
        return UNAVAILABLE;
    }

    public static InterfaceStorageResolution available(StorageEndpoint endpoint) {
        return new InterfaceStorageResolution(Status.AVAILABLE, endpoint);
    }
}
