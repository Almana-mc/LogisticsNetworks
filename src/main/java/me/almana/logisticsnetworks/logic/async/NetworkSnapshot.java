package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.data.FilterMode;
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

    public record ChannelUnit(
            UUID sourceNodeId,
            int channelIndex,
            int batchLimit,
            ItemStack[] exportFilters,
            FilterMode exportFilterMode,
            int sourceEndpoint,
            boolean roundRobin,
            List<TargetUnit> targets) {
    }

    public record TargetUnit(
            UUID nodeId,
            int channelIndex,
            ItemStack[] importFilters,
            FilterMode importFilterMode,
            boolean hasImportSlotMapping,
            boolean bulk,
            int endpoint) {
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
            this(totalSlots, occupiedSlots, occupiedStacks, defaultSlotLimit, occupiedSlotLimits, bulkSlotLimits, null);
        }
    }

    public record DirectEndpoint(int network, int binding, boolean exporting,
            List<StorageEndpoint.StoredItem> exports, Map<Item, Long> counts) {
    }
}
