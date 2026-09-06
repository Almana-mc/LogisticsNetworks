package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.DistributionMode;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.integration.sophisticated.SophisticatedCoreCompat;
import me.almana.logisticsnetworks.logic.TransferEngine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class Snapshots {

    private static final OccupiedSlotLimitExceeded OCCUPIED_SLOT_LIMIT =
            new OccupiedSlotLimitExceeded();

    private Snapshots() {
    }

    public static NetworkSnapshot.ItemEndpoint captureItems(ResourceHandler<ItemResource> handler) {
        return captureItems(handler, null);
    }

    public static NetworkSnapshot.ItemEndpoint captureItems(ResourceHandler<ItemResource> handler, boolean bulk) {
        return ItemEndpointTable.capture(handler, null, bulk);
    }

    static NetworkSnapshot.ItemEndpoint captureItems(
            ResourceHandler<ItemResource> handler, @Nullable OccupiedSlotBudget budget) {
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
            long runtimeId, int maxOccupiedSlots) {
        ThreadGuard.requireServerThread();

        TransferEngine.NetworkContext context = TransferEngine.prepareNetwork(network, server);
        if (context == null) {
            return NetworkCapture.unavailable();
        }

        ServerLevel overworld = server.overworld();
        List<NetworkSnapshot.ChannelUnit> units = new ArrayList<>();
        ItemEndpointTable endpoints = new ItemEndpointTable();
        OccupiedSlotBudget occupiedSlots = new OccupiedSlotBudget(maxOccupiedSlots);
        long itemWakeDelta = Long.MAX_VALUE;

        try {
            for (LogisticsNodeEntity node : context.sortedNodes()) {
                itemWakeDelta = captureNodeChannels(
                        node, context, endpoints, units, occupiedSlots, itemWakeDelta);
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
                units));
    }

    private static long captureNodeChannels(LogisticsNodeEntity node, TransferEngine.NetworkContext context,
            ItemEndpointTable endpoints, List<NetworkSnapshot.ChannelUnit> units,
            OccupiedSlotBudget occupiedSlots, long itemWakeDelta) {
        ServerLevel level = (ServerLevel) node.level();
        long gameTime = level.getGameTime();
        int signal = context.signalCache().getOrDefault(node.getUUID(), 0);
        int tier = context.tierCache().getOrDefault(node.getUUID(), 0);
        for (int index = 0; index < LogisticsNodeEntity.CHANNEL_COUNT; index++) {
            ChannelData channel = node.getChannel(index);
            if (channel == null || !channel.isEnabled()
                    || channel.getMode() != ChannelMode.EXPORT || channel.getType() != ChannelType.ITEM) continue;
            if (!TransferEngine.isRedstoneActive(channel.getRedstoneMode(), signal)) continue;
            List<TransferEngine.ImportTarget> targets = context.itemImports()[index];
            if (targets == null || targets.isEmpty()) continue;
            long cooldown = TransferEngine.cooldownRemaining(node, channel, index, tier, gameTime);
            if (cooldown > 0L) {
                itemWakeDelta = itemWakeDelta(itemWakeDelta, targets, cooldown);
                continue;
            }
            NetworkSnapshot.ChannelUnit unit = captureChannel(node, channel, index, tier, context, endpoints, occupiedSlots);
            if (unit != null) units.add(unit);
        }
        return itemWakeDelta;
    }

    @Nullable
    private static NetworkSnapshot.ChannelUnit captureChannel(LogisticsNodeEntity node, ChannelData channel,
            int index, int tier, TransferEngine.NetworkContext context, ItemEndpointTable endpoints,
            OccupiedSlotBudget occupiedSlots) {
        ServerLevel level = (ServerLevel) node.level();
        if (!level.isLoaded(node.getAttachedPos())) return null;
        ResourceHandler<ItemResource> sourceHandler = node.capabilities().findItemHandler(channel.getIoDirection());
        if (sourceHandler == null) return null;
        FilterItemData.ReadCache readCache = FilterItemData.createReadCache();
        TransferEngine.ResolvedItemTargets resolved = TransferEngine.resolveItemTargets(
                node, level, channel, context.itemImports()[index], sourceHandler, context.dimensionalCache(), readCache);
        if (resolved.status() != TransferEngine.ResolvedItemTargets.OK) return null;
        int configuredBatch = TransferEngine.getBatchLimit(ChannelType.ITEM, tier);
        int batchLimit = Math.max(1, Math.min(channel.getBatchSize(), configuredBatch));
        int sourceEndpoint = endpoints.capture(node, channel.getIoDirection(), sourceHandler, occupiedSlots);
        return new NetworkSnapshot.ChannelUnit(node.getUUID(), index, batchLimit,
                channel.getFilterItems(), channel.getFilterMode(), sourceEndpoint,
                channel.getDistributionMode() == DistributionMode.ROUND_ROBIN,
                captureTargets(resolved, endpoints, occupiedSlots), binding(node, channel), channel.getDistributionMode());
    }

    private static List<NetworkSnapshot.TargetUnit> captureTargets(TransferEngine.ResolvedItemTargets resolved,
            ItemEndpointTable endpoints, OccupiedSlotBudget occupiedSlots) {
        List<NetworkSnapshot.TargetUnit> units = new ArrayList<>(resolved.refs().size());
        for (int index = 0; index < resolved.refs().size(); index++) {
            TransferEngine.ImportTarget ref = resolved.refs().get(index);
            TransferEngine.ItemTransferTarget target = resolved.targets().get(index);
            boolean upgradedCapacity = SophisticatedCoreCompat.isBulkHandler(target.handler());
            boolean bulk = !target.hasImportSlotMapping() && upgradedCapacity;
            units.add(new NetworkSnapshot.TargetUnit(ref.node().getUUID(), ref.channelIndex(),
                    target.importFilters(), target.importFilterMode(), target.hasImportSlotMapping(), bulk,
                    endpoints.capture(ref.node(), ref.channel().getIoDirection(), target.handler(),
                            occupiedSlots, upgradedCapacity), binding(ref.node(), ref.channel())));
        }
        return units;
    }

    static TransferPlan.EndpointBinding binding(LogisticsNodeEntity node, ChannelData channel) {
        return new TransferPlan.EndpointBinding(node.level().dimension(), node.getAttachedPos().asLong(),
                channel.getIoDirection());
    }

    static long earlierItemWakeDelta(long currentMinimum, long cooldown) {
        return cooldown > 0L ? Math.min(currentMinimum, cooldown) : currentMinimum;
    }

    static long itemWakeDelta(long currentMinimum, List<TransferEngine.ImportTarget> targets, long cooldown) {
        return targets == null || targets.isEmpty()
                ? currentMinimum
                : earlierItemWakeDelta(currentMinimum, cooldown);
    }

    public record NetworkCapture(CaptureStatus status, @Nullable NetworkSnapshot snapshot) {

        public static NetworkCapture captured(NetworkSnapshot snapshot) {
            return new NetworkCapture(CaptureStatus.CAPTURED, snapshot);
        }

        public static NetworkCapture unavailable() {
            return new NetworkCapture(CaptureStatus.UNAVAILABLE, null);
        }

        public static NetworkCapture occupiedLimitExceeded() {
            return new NetworkCapture(CaptureStatus.OCCUPIED_SLOT_LIMIT_EXCEEDED, null);
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
