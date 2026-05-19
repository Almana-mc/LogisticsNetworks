package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.filter.AmountFilterData;
import me.almana.logisticsnetworks.filter.FilterItemData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

final class TransferAmountRules {
    record Constraints(boolean hasExportThreshold, int exportThreshold,
            boolean hasImportThreshold, int importThreshold,
            boolean hasPerEntryAmounts) {
    }

    private TransferAmountRules() {
    }

    static Constraints collect(ItemStack[] exportFilters, ItemStack[] importFilters) {
        return collect(exportFilters, importFilters, null);
    }

    static Constraints collect(ItemStack[] exportFilters, ItemStack[] importFilters,
            @Nullable FilterItemData.ReadCache filterReadCache) {
        int exportThreshold = 0;
        boolean hasExportThreshold = false;
        boolean hasPerEntryAmounts = false;

        if (exportFilters != null) {
            for (ItemStack filter : exportFilters) {
                if (AmountFilterData.isAmountFilterItem(filter)) {
                    hasExportThreshold = true;
                    exportThreshold = Math.max(exportThreshold, AmountFilterData.getAmount(filter));
                }
                if (FilterItemData.hasAnyAmountEntries(filter, filterReadCache)) {
                    hasPerEntryAmounts = true;
                }
            }
        }

        int importThreshold = Integer.MAX_VALUE;
        boolean hasImportThreshold = false;

        if (importFilters != null) {
            for (ItemStack filter : importFilters) {
                if (AmountFilterData.isAmountFilterItem(filter)) {
                    hasImportThreshold = true;
                    importThreshold = Math.min(importThreshold, AmountFilterData.getAmount(filter));
                }
                if (FilterItemData.hasAnyAmountEntries(filter, filterReadCache)) {
                    hasPerEntryAmounts = true;
                }
            }
        }

        return new Constraints(hasExportThreshold, exportThreshold, hasImportThreshold, importThreshold,
                hasPerEntryAmounts);
    }

    static Map<Item, Integer> countItems(IItemHandler handler) {
        Map<Item, Integer> counts = new HashMap<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    static int allowedItems(ItemStack candidate, Constraints constraints,
            Map<Item, Integer> sourceCounts, Map<Item, Integer> targetCounts) {
        int allowed = Integer.MAX_VALUE;

        if (constraints.hasExportThreshold) {
            int sourceCount = sourceCounts != null ? sourceCounts.getOrDefault(candidate.getItem(), 0) : 0;
            int exportCap = sourceCount - constraints.exportThreshold;
            if (exportCap <= 0) return 0;
            allowed = Math.min(allowed, exportCap);
        }

        if (constraints.hasImportThreshold) {
            int targetCount = targetCounts != null ? targetCounts.getOrDefault(candidate.getItem(), 0) : 0;
            int importCap = constraints.importThreshold - targetCount;
            if (importCap <= 0) return 0;
            allowed = Math.min(allowed, importCap);
        }

        return allowed == Integer.MAX_VALUE ? candidate.getCount() : Math.max(0, allowed);
    }

    static int allowedFluids(IFluidHandler source, IFluidHandler target, FluidStack candidate,
            Constraints constraints) {
        int allowed = Integer.MAX_VALUE;

        if (constraints.hasExportThreshold) {
            int sourceAmount = countFluids(source, candidate);
            int exportCap = sourceAmount - constraints.exportThreshold;
            if (exportCap <= 0) return 0;
            allowed = Math.min(allowed, exportCap);
        }

        if (constraints.hasImportThreshold) {
            int targetAmount = countFluids(target, candidate);
            int importCap = constraints.importThreshold - targetAmount;
            if (importCap <= 0) return 0;
            allowed = Math.min(allowed, importCap);
        }

        return allowed == Integer.MAX_VALUE ? candidate.getAmount() : Math.max(0, allowed);
    }

    static int perEntryItemAmount(ItemStack candidate, ItemStack[] exportFilters,
            ItemStack[] importFilters, Map<Item, Integer> sourceCounts, Map<Item, Integer> targetCounts,
            HolderLookup.Provider provider, @Nullable CompoundTag candidateComponents,
            @Nullable FilterItemData.ReadCache filterReadCache) {
        int allowed = Integer.MAX_VALUE;

        if (exportFilters != null) {
            for (ItemStack filter : exportFilters) {
                int threshold = FilterItemData.getItemAmountThresholdFull(filter, candidate, provider,
                        candidateComponents, filterReadCache);
                if (threshold > 0) {
                    int sourceCount = sourceCounts != null ? sourceCounts.getOrDefault(candidate.getItem(), 0) : 0;
                    int exportCap = sourceCount - threshold;
                    if (exportCap <= 0) return 0;
                    allowed = Math.min(allowed, exportCap);
                }
            }
        }

        if (importFilters != null) {
            for (ItemStack filter : importFilters) {
                int threshold = FilterItemData.getItemAmountThresholdFull(filter, candidate, provider,
                        candidateComponents, filterReadCache);
                if (threshold > 0) {
                    int targetCount = targetCounts != null ? targetCounts.getOrDefault(candidate.getItem(), 0) : 0;
                    int importCap = threshold - targetCount;
                    if (importCap <= 0) return 0;
                    allowed = Math.min(allowed, importCap);
                }
            }
        }

        return allowed == Integer.MAX_VALUE ? -1 : Math.max(0, allowed);
    }

    static int perEntryItemBatch(ItemStack candidate, ItemStack[] exportFilters,
            HolderLookup.Provider provider, @Nullable CompoundTag candidateComponents,
            @Nullable FilterItemData.ReadCache filterReadCache) {
        if (exportFilters == null) return -1;
        int limit = Integer.MAX_VALUE;
        for (ItemStack filter : exportFilters) {
            int batch = FilterItemData.getItemBatchLimitFull(filter, candidate, provider,
                    candidateComponents, filterReadCache);
            if (batch > 0) {
                limit = Math.min(limit, batch);
            }
        }
        return limit == Integer.MAX_VALUE ? -1 : limit;
    }

    static int perEntryFluidAmount(FluidStack candidate, ItemStack[] exportFilters,
            ItemStack[] importFilters, IFluidHandler source, IFluidHandler target,
            @Nullable FilterItemData.ReadCache filterReadCache) {
        int allowed = Integer.MAX_VALUE;

        if (exportFilters != null) {
            for (ItemStack filter : exportFilters) {
                int threshold = FilterItemData.getFluidAmountThresholdFull(filter, candidate, null, filterReadCache);
                if (threshold > 0) {
                    int sourceAmount = countFluids(source, candidate);
                    int exportCap = sourceAmount - threshold;
                    if (exportCap <= 0) return 0;
                    allowed = Math.min(allowed, exportCap);
                }
            }
        }

        if (importFilters != null) {
            for (ItemStack filter : importFilters) {
                int threshold = FilterItemData.getFluidAmountThresholdFull(filter, candidate, null, filterReadCache);
                if (threshold > 0) {
                    int targetAmount = countFluids(target, candidate);
                    int importCap = threshold - targetAmount;
                    if (importCap <= 0) return 0;
                    allowed = Math.min(allowed, importCap);
                }
            }
        }

        return allowed == Integer.MAX_VALUE ? -1 : Math.max(0, allowed);
    }

    static int perEntryFluidBatch(FluidStack candidate, ItemStack[] exportFilters,
            ItemStack[] importFilters, @Nullable FilterItemData.ReadCache filterReadCache) {
        int limit = Integer.MAX_VALUE;

        if (exportFilters != null) {
            for (ItemStack filter : exportFilters) {
                int batch = FilterItemData.getFluidBatchLimitFull(filter, candidate, filterReadCache);
                if (batch > 0) {
                    limit = Math.min(limit, batch);
                }
            }
        }

        if (importFilters != null) {
            for (ItemStack filter : importFilters) {
                int batch = FilterItemData.getFluidBatchLimitFull(filter, candidate, filterReadCache);
                if (batch > 0) {
                    limit = Math.min(limit, batch);
                }
            }
        }

        return limit == Integer.MAX_VALUE ? -1 : limit;
    }

    private static int countFluids(IFluidHandler handler, FluidStack candidate) {
        int amount = 0;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack stack = handler.getFluidInTank(i);
            if (!stack.isEmpty() && FluidStack.isSameFluidSameComponents(stack, candidate)) {
                amount += stack.getAmount();
            }
        }
        return amount;
    }
}
