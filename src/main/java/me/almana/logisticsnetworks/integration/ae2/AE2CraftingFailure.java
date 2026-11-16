package me.almana.logisticsnetworks.integration.ae2;

import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import me.almana.logisticsnetworks.integration.storage.StorageFailure;
import java.util.ArrayList;
import java.util.List;

record AE2CraftingFailure(Reason reason, List<MissingItem> missingItems) {

    enum Reason {
        MISSING_ITEMS,
        NO_PATTERN,
        GRID_UNAVAILABLE,
        NO_CPU,
        CPU_BUSY,
        CPU_OFFLINE,
        CPU_TOO_SMALL,
        CANCELED,
        SUBMISSION,
        TARGET_INVALID
    }

    record MissingItem(AEKey key, long amount) {
    }

    AE2CraftingFailure {
        missingItems = List.copyOf(missingItems);
    }

    static AE2CraftingFailure fromPlan(ICraftingPlan plan) {
        List<MissingItem> missing = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : plan.missingItems()) {
            if (entry.getLongValue() > 0) {
                missing.add(new MissingItem(entry.getKey(), entry.getLongValue()));
            }
        }
        return new AE2CraftingFailure(Reason.MISSING_ITEMS, missing);
    }

    static AE2CraftingFailure fromSubmit(ICraftingSubmitResult result) {
        CraftingSubmitErrorCode code = result.errorCode();
        if (code == CraftingSubmitErrorCode.MISSING_INGREDIENT
                && result.errorDetail() instanceof GenericStack missing) {
            return new AE2CraftingFailure(Reason.MISSING_ITEMS,
                    List.of(new MissingItem(missing.what(), missing.amount())));
        }
        return fromSubmitCode(code == null ? "SUBMISSION" : code.name());
    }

    static AE2CraftingFailure fromSubmitCode(String code) {
        Reason reason = switch (code) {
            case "CPU_BUSY" -> Reason.CPU_BUSY;
            case "NO_CPU_FOUND", "NO_SUITABLE_CPU_FOUND" -> Reason.NO_CPU;
            case "CPU_OFFLINE" -> Reason.CPU_OFFLINE;
            case "CPU_TOO_SMALL" -> Reason.CPU_TOO_SMALL;
            case "MISSING_INGREDIENT", "INCOMPLETE_PLAN" -> Reason.MISSING_ITEMS;
            default -> Reason.SUBMISSION;
        };
        return new AE2CraftingFailure(reason, List.of());
    }

    StorageFailure toStorageFailure() {
        StorageFailure.Reason storageReason = switch (reason) {
            case MISSING_ITEMS -> StorageFailure.Reason.MISSING_ITEMS;
            case NO_PATTERN -> StorageFailure.Reason.NO_PATTERN;
            case GRID_UNAVAILABLE -> StorageFailure.Reason.NETWORK_UNAVAILABLE;
            case NO_CPU -> StorageFailure.Reason.NO_CPU;
            case CPU_BUSY -> StorageFailure.Reason.CPU_BUSY;
            case CPU_OFFLINE -> StorageFailure.Reason.CPU_OFFLINE;
            case CPU_TOO_SMALL -> StorageFailure.Reason.CPU_TOO_SMALL;
            case CANCELED -> StorageFailure.Reason.CANCELED;
            case SUBMISSION -> StorageFailure.Reason.SUBMISSION;
            case TARGET_INVALID -> StorageFailure.Reason.TARGET_INVALID;
        };
        List<StorageFailure.MissingItem> missing = new ArrayList<>();
        for (MissingItem item : missingItems) {
            if (item.key() instanceof appeng.api.stacks.AEItemKey key) {
                missing.add(new StorageFailure.MissingItem(key.toStack(), item.amount()));
            }
        }
        return new StorageFailure(storageReason, missing);
    }

}
