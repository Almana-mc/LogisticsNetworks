package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.IoSide;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.integration.sophisticated.SophisticatedCoreCompat;
import me.almana.logisticsnetworks.integration.storage.DirectStorageHandlers;
import me.almana.logisticsnetworks.item.RecipeCardItem;
import me.almana.logisticsnetworks.logic.FilterLogic;
import me.almana.logisticsnetworks.logic.TransferCapabilityCache;
import me.almana.logisticsnetworks.logic.TransferEngine;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
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
        try (var operation = TransferCapabilityCache.storageOperation()) {
            return captureNetworkInOperation(network, server, runtimeId, maxOccupiedSlots);
        }
    }

    private static NetworkCapture captureNetworkInOperation(LogisticsNetwork network, MinecraftServer server,
            long runtimeId, int maxOccupiedSlots) {

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
                units), endpoints.bindings());
    }

    private static long captureNodeChannels(LogisticsNodeEntity node, TransferEngine.NetworkContext context,
            ItemEndpointTable endpoints, List<NetworkSnapshot.ChannelUnit> units,
            OccupiedSlotBudget occupiedSlots, long itemWakeDelta) {
        ServerLevel level = (ServerLevel) node.level();
        long gameTime = level.getGameTime();
        int tier = context.tierCache().getOrDefault(node.getUUID(), 0);
        for (int index = 0; index < LogisticsNodeEntity.CHANNEL_COUNT; index++) {
            ChannelData channel = node.getChannel(index);
            if (channel == null || !channel.isEnabled()
                    || channel.getMode() != ChannelMode.EXPORT || !channel.hasType(ChannelType.ITEM)) continue;
            if (RecipeCardItem.anyIn(channel.getFilterItems())) continue;
            if (!TransferEngine.isRedstoneActive(node, channel.getRedstoneMode())) continue;
            List<TransferEngine.ImportTarget> targets = context.imports().get(ChannelType.ITEM, index);
            if (targets.isEmpty()) continue;
            long cooldown = TransferEngine.cooldownRemaining(
                    node, channel, index, ChannelType.ITEM.ordinal(), tier, gameTime);
            if (cooldown > 0L) {
                itemWakeDelta = itemWakeDelta(itemWakeDelta, targets, cooldown);
                continue;
            }
            if (!level.isLoaded(node.getAttachedPos())) continue;
            boolean directSource = !FilterLogic.hasConfiguredSlotMapping(channel.getFilterItems(), channel.getReadCache());
            ResourceHandler<ItemResource> sourceHandler = node.capabilities().findItemExportHandler(
                    channel.getIoSide(), directSource);
            if (sourceHandler == null) {
                itemWakeDelta = itemWakeDelta(itemWakeDelta, targets,
                        TransferEngine.finishChannelAttempt(
                                node, channel, index, ChannelType.ITEM.ordinal(), 0, gameTime, tier, false));
                continue;
            }
            NetworkSnapshot.ChannelUnit unit = captureChannel(
                    node, channel, index, tier, sourceHandler, context, endpoints, occupiedSlots);
            if (unit != null) units.add(unit);
        }
        return itemWakeDelta;
    }

    @Nullable
    private static NetworkSnapshot.ChannelUnit captureChannel(LogisticsNodeEntity node, ChannelData channel,
            int index, int tier, ResourceHandler<ItemResource> sourceHandler, TransferEngine.NetworkContext context,
            ItemEndpointTable endpoints, OccupiedSlotBudget occupiedSlots) {
        ServerLevel level = (ServerLevel) node.level();
        FilterItemData.ReadCache readCache = channel.getReadCache();
        TransferEngine.ResolvedItemTargets resolved = TransferEngine.resolveItemTargets(
                node, level, channel, context.imports().get(ChannelType.ITEM, index), sourceHandler,
                context.dimensionalCache(), readCache);
        if (resolved.status() != TransferEngine.ResolvedItemTargets.OK) return null;
        int batchLimit = NodeUpgradeData.effectiveBatch(channel, ChannelType.ITEM, tier);
        int sourceEndpoint = endpoints.capture(node, channel.getIoSide(), sourceHandler, occupiedSlots);
        return new NetworkSnapshot.ChannelUnit(node.getUUID(), index, batchLimit,
                channel.getFilterItems(), channel.getFilterMode(), sourceEndpoint,
                channel.canRotateResources(), channel.getItemResourceCursor(),
                captureTargets(resolved, channel.getFilterItems(), endpoints, occupiedSlots, readCache),
                binding(node, channel), channel.getDistributionMode());
    }

    private static List<NetworkSnapshot.TargetUnit> captureTargets(TransferEngine.ResolvedItemTargets resolved,
            ItemStack[] exportFilters, ItemEndpointTable endpoints, OccupiedSlotBudget occupiedSlots,
            FilterItemData.ReadCache readCache) {
        List<NetworkSnapshot.TargetUnit> units = new ArrayList<>(resolved.refs().size());
        for (int index = 0; index < resolved.refs().size(); index++) {
            TransferEngine.ImportTarget ref = resolved.refs().get(index);
            TransferEngine.ItemTransferTarget target = resolved.targets().get(index);
            boolean upgradedCapacity = DirectStorageHandlers.isDirect(target.handler())
                    || SophisticatedCoreCompat.isBulkHandler(target.handler());
            boolean bulk = !target.hasImportSlotMapping() && upgradedCapacity;
            int targetEndpoint = endpoints.capture(ref.node(), ref.channel().getIoSide(), target.handler(),
                    occupiedSlots, upgradedCapacity);
            units.add(new NetworkSnapshot.TargetUnit(ref.node().getUUID(), ref.channelIndex(),
                    target.importFilters(), target.importFilterMode(), target.hasImportSlotMapping(), bulk,
                    targetEndpoint, binding(ref.node(), ref.channel()), target.faces() == null ? null
                            : captureFaces(ref, target, exportFilters, endpoints, occupiedSlots, readCache)));
        }
        return units;
    }

    private static NetworkSnapshot.TargetUnit[] captureFaces(TransferEngine.ImportTarget ref,
            TransferEngine.ItemTransferTarget target, ItemStack[] exportFilters, ItemEndpointTable endpoints,
            OccupiedSlotBudget occupiedSlots, FilterItemData.ReadCache readCache) {
        var faces = target.faces();
        int mask = (faces.imports() ? FilterLogic.entrySideMask(target.importFilters(), readCache) : 0)
                | (faces.exports() ? FilterLogic.entrySideMask(exportFilters, readCache) : 0);
        NetworkSnapshot.TargetUnit[] units = new NetworkSnapshot.TargetUnit[IoSide.values().length];
        for (IoSide side : IoSide.values()) {
            if ((mask & 1 << side.ordinal()) == 0 || side == ref.channel().getIoSide())
                continue;
            TransferEngine.ItemTransferTarget face = faces.face(side, target);
            if (face == null)
                continue;
            boolean upgradedCapacity = DirectStorageHandlers.isDirect(face.handler())
                    || SophisticatedCoreCompat.isBulkHandler(face.handler());
            int endpoint = endpoints.capture(ref.node(), side, face.handler(), occupiedSlots, upgradedCapacity);
            units[side.ordinal()] = new NetworkSnapshot.TargetUnit(ref.node().getUUID(), ref.channelIndex(),
                    target.importFilters(), target.importFilterMode(), target.hasImportSlotMapping(),
                    !target.hasImportSlotMapping() && upgradedCapacity, endpoint,
                    new TransferPlan.EndpointBinding(ref.node().level().dimension(),
                            ref.node().getAttachedPos().asLong(), side), null);
        }
        return units;
    }

    static TransferPlan.EndpointBinding binding(LogisticsNodeEntity node, ChannelData channel) {
        return new TransferPlan.EndpointBinding(node.level().dimension(), node.getAttachedPos().asLong(),
                channel.getIoSide());
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
            return new NetworkCapture(CaptureStatus.CAPTURED, snapshot, List.copyOf(bindings));
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
