package me.almana.logisticsnetworks.integration.storage;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record StorageFailure(Reason reason, List<MissingItem> missingItems) {

    public enum Reason {
        MISSING_ITEMS,
        NO_PATTERN,
        NETWORK_UNAVAILABLE,
        PERMISSION_DENIED,
        NO_CPU,
        CPU_BUSY,
        CPU_OFFLINE,
        CPU_TOO_SMALL,
        CANCELED,
        SUBMISSION,
        TARGET_INVALID
    }

    public record MissingItem(ItemStack stack, long amount) {
    }

    public StorageFailure {
        missingItems = List.copyOf(missingItems);
    }

    public static StorageFailure of(Reason reason) {
        return new StorageFailure(reason, List.of());
    }

    public static StorageFailure missing(List<MissingItem> missingItems) {
        return new StorageFailure(Reason.MISSING_ITEMS, missingItems);
    }

    public boolean retryable() {
        return reason == Reason.CPU_BUSY;
    }

    public Component detail(StorageBackend backend, Component subject) {
        if (reason == Reason.MISSING_ITEMS && !missingItems.isEmpty()) {
            return Component.translatable("message.logisticsnetworks.storage.failure.missing",
                    backend.displayName(), subject, formatMissing());
        }
        if (reason == Reason.MISSING_ITEMS) {
            return Component.translatable("message.logisticsnetworks.storage.failure.missing_unknown",
                    backend.displayName(), subject);
        }
        return Component.translatable("message.logisticsnetworks.storage.failure."
                + reason.name().toLowerCase(), backend.displayName(), subject);
    }

    private Component formatMissing() {
        Component result = Component.empty();
        for (int index = 0; index < missingItems.size(); index++) {
            MissingItem missing = missingItems.get(index);
            if (index > 0) result = result.copy().append(", ");
            result = result.copy().append(Component.literal(missing.amount() + "x "))
                    .append(missing.stack().getHoverName());
        }
        return result;
    }
}
