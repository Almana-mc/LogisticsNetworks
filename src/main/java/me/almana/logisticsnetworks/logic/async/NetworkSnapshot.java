package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.data.FilterMode;
import me.almana.logisticsnetworks.data.DistributionMode;
import me.almana.logisticsnetworks.integration.storage.StorageEndpoint;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record NetworkSnapshot(
        UUID networkId,
        long generation,
        long runtimeId,
        long gameTime,
        long itemWakeDelta,
        HolderLookup.Provider registries,
        List<ItemEndpoint> endpoints,
        List<ChannelUnit> units) {

    public NetworkSnapshot {
        endpoints = List.copyOf(endpoints);
        units = List.copyOf(units);
    }

    public record ChannelUnit(
            UUID sourceNodeId,
            int channelIndex,
            int batchLimit,
            ItemStack[] exportFilters,
            FilterMode exportFilterMode,
            int sourceEndpoint,
            boolean roundRobin,
            boolean resourceRoundRobin,
            @Nullable me.almana.logisticsnetworks.logic.ItemResourceOrder.Cursor resourceCursor,
            List<TargetUnit> targets, TransferPlan.EndpointBinding sourceBinding, DistributionMode distributionMode) {
        public ChannelUnit {
            exportFilters = Snapshots.copyFilters(exportFilters);
            targets = List.copyOf(targets);
        }

        @Override
        public ItemStack[] exportFilters() {
            return Snapshots.copyFilters(exportFilters);
        }
    }

    public record TargetUnit(
            UUID nodeId,
            int channelIndex,
            ItemStack[] importFilters,
            FilterMode importFilterMode,
            boolean hasImportSlotMapping,
            boolean bulk,
            int endpoint, TransferPlan.EndpointBinding binding) {
        public TargetUnit {
            importFilters = Snapshots.copyFilters(importFilters);
        }

        @Override
        public ItemStack[] importFilters() {
            return Snapshots.copyFilters(importFilters);
        }
    }

    public record ItemEndpoint(
            int totalSlots,
            int[] occupiedSlots,
            ItemStack[] occupiedStacks,
            int defaultSlotLimit,
            int[] occupiedSlotLimits,
            @Nullable int[] bulkSlotLimits,
            @Nullable DirectEndpoint direct) {
        public ItemEndpoint(int totalSlots, int[] occupiedSlots, ItemStack[] occupiedStacks,
                int defaultSlotLimit, int[] occupiedSlotLimits, @Nullable int[] bulkSlotLimits) {
            this(totalSlots, occupiedSlots, occupiedStacks, defaultSlotLimit, occupiedSlotLimits,
                    bulkSlotLimits, null);
        }

        public ItemEndpoint {
            occupiedSlots = occupiedSlots.clone();
            occupiedStacks = Snapshots.copyFilters(occupiedStacks);
            occupiedSlotLimits = occupiedSlotLimits.clone();
            bulkSlotLimits = bulkSlotLimits == null ? null : bulkSlotLimits.clone();
        }

        @Override
        public int[] occupiedSlots() { return occupiedSlots.clone(); }

        @Override
        public ItemStack[] occupiedStacks() { return Snapshots.copyFilters(occupiedStacks); }

        @Override
        public int[] occupiedSlotLimits() { return occupiedSlotLimits.clone(); }

        @Override
        public int[] bulkSlotLimits() { return bulkSlotLimits == null ? null : bulkSlotLimits.clone(); }
    }

    public record DirectEndpoint(int network, int binding, boolean exporting,
            List<StorageEndpoint.StoredItem> exports, Map<Item, Long> counts) {
        public DirectEndpoint {
            exports = List.copyOf(exports);
            counts = Map.copyOf(counts);
        }
    }
}
