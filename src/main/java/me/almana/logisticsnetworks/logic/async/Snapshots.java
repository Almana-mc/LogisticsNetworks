package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.DistributionMode;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.integration.create.CreateCompat;
import me.almana.logisticsnetworks.logic.FilterLogic;
import me.almana.logisticsnetworks.logic.TransferCapabilityCache;
import me.almana.logisticsnetworks.logic.TransferAmountRules;
import me.almana.logisticsnetworks.logic.TransferEngine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class Snapshots {

    private static final OccupiedSlotLimitExceeded OCCUPIED_SLOT_LIMIT =
            new OccupiedSlotLimitExceeded();

    private Snapshots() {
    }

    public static NetworkSnapshot.ItemEndpoint captureItems(IItemHandler handler) {
        return captureItems(handler, null);
    }

    static NetworkSnapshot.ItemEndpoint captureItems(
            IItemHandler handler, @Nullable OccupiedSlotBudget budget) {
        return ItemEndpointTable.capture(handler, budget);
    }

    public static ItemStack[] copyFilters(ItemStack[] filters) {
        ItemStack[] copied = new ItemStack[filters.length];
        for (int i = 0; i < filters.length; i++) {
            copied[i] = filters[i].isEmpty() ? ItemStack.EMPTY : filters[i].copy();
        }
        return copied;
    }

    public static NetworkCapture captureNetwork(LogisticsNetwork network, MinecraftServer server,
            long runtimeId, TransferCapabilityCache capCache) {
        ThreadGuard.requireServerThread();
        try (var operation = capCache.storageOperation(false)) {
            return captureNetworkInOperation(network, server, runtimeId, capCache);
        }
    }

    private static NetworkCapture captureNetworkInOperation(LogisticsNetwork network, MinecraftServer server,
            long runtimeId, TransferCapabilityCache capCache) {

        TransferEngine.NetworkContext context = TransferEngine.prepareNetwork(network, server);
        if (context == null) {
            return NetworkCapture.unavailable();
        }

        ServerLevel overworld = server.overworld();
        List<NetworkSnapshot.ChannelUnit> units = new ArrayList<>();
        ItemEndpointTable endpoints = new ItemEndpointTable();
        OccupiedSlotBudget occupiedSlots = new OccupiedSlotBudget(Config.asyncMaxOccupiedSlots);
        long itemWakeDelta = Long.MAX_VALUE;

        try {
            for (LogisticsNodeEntity node : context.sortedNodes()) {
                itemWakeDelta = captureNodeChannels(
                        node, context, capCache, endpoints, units, occupiedSlots, itemWakeDelta);
            }
        } catch (OccupiedSlotLimitExceeded exception) {
            return NetworkCapture.occupiedLimitExceeded();
        }

        return NetworkCapture.captured(new NetworkSnapshot(
                network.getId(),
                network.getGeneration(),
                runtimeId,
                overworld.getGameTime(),
                itemWakeDelta,
                overworld.registryAccess(),
                endpoints.endpoints(),
                units), endpoints.bindings());
    }

    private static long captureNodeChannels(LogisticsNodeEntity node, TransferEngine.NetworkContext context,
            TransferCapabilityCache capCache, ItemEndpointTable endpoints,
            List<NetworkSnapshot.ChannelUnit> units,
            OccupiedSlotBudget occupiedSlots, long itemWakeDelta) {

        ServerLevel level = (ServerLevel) node.level();
        long gameTime = level.getGameTime();
        int signal = context.signalCache().getOrDefault(node.getUUID(), 0);
        int tier = context.tierCache().getOrDefault(node.getUUID(), 0);

        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            ChannelData channel = node.getChannel(i);
            if (channel == null || !channel.isEnabled()) {
                continue;
            }
            if (channel.getMode() != ChannelMode.EXPORT || channel.getType() != ChannelType.ITEM) {
                continue;
            }
            if (!TransferEngine.canRunChannel(node, channel) || !CreateCompat.isResolved(node)) {
                continue;
            }
            if (!TransferEngine.isRedstoneActive(channel.getRedstoneMode(), signal)) {
                continue;
            }
            List<TransferEngine.ImportTarget> targets = context.itemImports()[i];
            if (targets == null || targets.isEmpty()) {
                continue;
            }
            long cooldown = TransferEngine.cooldownRemaining(node, channel, i, tier, gameTime);
            if (cooldown > 0L) {
                itemWakeDelta = itemWakeDelta(itemWakeDelta, targets, cooldown);
                continue;
            }

            if (!node.isMountedOnCreate() && !level.isLoaded(node.getAttachedPos())) {
                continue;
            }

            FilterItemData.ReadCache readCache = FilterItemData.createReadCache();
            boolean directSource = !FilterLogic.hasConfiguredSlotMapping(
                    channel.getFilterItems(), readCache);
            IItemHandler sourceHandler = capCache.findItemExportHandler(
                    node, channel.getIoDirection(), directSource);
            if (sourceHandler == null) {
                continue;
            }

            TransferEngine.ResolvedItemTargets resolved = TransferEngine.resolveItemTargets(
                    node, level, channel, targets, sourceHandler,
                    context.dimensionalCache(), capCache, readCache);
            if (resolved.status() != TransferEngine.ResolvedItemTargets.OK) {
                continue;
            }

            int configuredBatch = TransferEngine.getBatchLimit(ChannelType.ITEM, tier);
            int batchLimit = Math.max(1, Math.min(channel.getBatchSize(), configuredBatch));
            int sourceEndpoint = endpoints.capture(
                    node, channel.getIoDirection(), sourceHandler, occupiedSlots);

            List<NetworkSnapshot.TargetUnit> targetUnits = new ArrayList<>(resolved.refs().size());
            for (int t = 0; t < resolved.refs().size(); t++) {
                TransferEngine.ImportTarget ref = resolved.refs().get(t);
                TransferEngine.ItemTransferTarget engineTarget = resolved.targets().get(t);
                int targetEndpoint = endpoints.capture(ref.node(), ref.channel().getIoDirection(),
                        engineTarget.handler(), occupiedSlots, engineTarget.bulkHandler() != null);
                if (engineTarget.constraints().hasImportThreshold()
                        || TransferAmountRules.hasStockFilter(engineTarget.importFilters(), readCache)) {
                    endpoints.requireCounts(targetEndpoint, sourceEndpoint);
                }
                targetUnits.add(new NetworkSnapshot.TargetUnit(
                        ref.node().getUUID(),
                        ref.channelIndex(),
                        copyFilters(engineTarget.importFilters()),
                        engineTarget.importFilterMode(),
                        engineTarget.hasImportSlotMapping(),
                        engineTarget.bulkHandler() != null,
                        targetEndpoint));
            }

            units.add(new NetworkSnapshot.ChannelUnit(
                    node.getUUID(),
                    i,
                    batchLimit,
                    copyFilters(channel.getFilterItems()),
                    channel.getFilterMode(),
                    sourceEndpoint,
                    channel.getDistributionMode() == DistributionMode.ROUND_ROBIN,
                    targetUnits));
        }
        return itemWakeDelta;
    }

    static long earlierItemWakeDelta(long currentMinimum, long cooldown) {
        return cooldown > 0L ? Math.min(currentMinimum, cooldown) : currentMinimum;
    }

    static long itemWakeDelta(long currentMinimum, List<TransferEngine.ImportTarget> targets, long cooldown) {
        return targets == null || targets.isEmpty()
                ? currentMinimum
                : earlierItemWakeDelta(currentMinimum, cooldown);
    }

    public record NetworkCapture(CaptureStatus status, @Nullable NetworkSnapshot snapshot,
            List<DirectStorageBinding> bindings) {

        public static NetworkCapture captured(NetworkSnapshot snapshot) {
            return captured(snapshot, List.of());
        }

        public static NetworkCapture captured(NetworkSnapshot snapshot, List<DirectStorageBinding> bindings) {
            return new NetworkCapture(CaptureStatus.CAPTURED, snapshot, bindings);
        }

        public static NetworkCapture unavailable() {
            return new NetworkCapture(CaptureStatus.UNAVAILABLE, null, List.of());
        }

        public static NetworkCapture occupiedLimitExceeded() {
            return new NetworkCapture(CaptureStatus.OCCUPIED_SLOT_LIMIT_EXCEEDED, null, List.of());
        }
    }

    public enum CaptureStatus {
        CAPTURED,
        UNAVAILABLE,
        OCCUPIED_SLOT_LIMIT_EXCEEDED
    }

    static final class OccupiedSlotBudget {

        private int remaining;

        OccupiedSlotBudget(int limit) {
            remaining = limit;
        }

        void retain() {
            if (remaining == 0) {
                throw OCCUPIED_SLOT_LIMIT;
            }
            remaining--;
        }
    }

    static final class OccupiedSlotLimitExceeded extends RuntimeException {

        private OccupiedSlotLimitExceeded() {
            super(null, null, false, false);
        }
    }
}
