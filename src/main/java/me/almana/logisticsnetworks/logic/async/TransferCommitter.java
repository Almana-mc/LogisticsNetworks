package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.DistributionMode;
import me.almana.logisticsnetworks.data.RedstoneMode;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.integration.create.CreateCompat;
import me.almana.logisticsnetworks.integration.storage.DirectStorageHandlers;
import me.almana.logisticsnetworks.logic.FilterLogic;
import me.almana.logisticsnetworks.logic.PlannedItemValidation;
import me.almana.logisticsnetworks.logic.TransferCapabilityCache;
import me.almana.logisticsnetworks.logic.TransferEngine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TransferCommitter {

    public record ItemCommitResult(
            int moved,
            long wakeDelta,
            int planned,
            int committed,
            int recovered,
            int revalidatedChannels) {
    }

    private record ChannelCommitResult(
            int planned,
            int committed,
            int recovered,
            long wakeDelta,
            boolean revalidated) {

        private int moved() {
            return committed + recovered;
        }

        private static ChannelCommitResult skipped(int planned) {
            return new ChannelCommitResult(planned, 0, 0, Long.MAX_VALUE, false);
        }
    }

    private TransferCommitter() {
    }

    public static int commit(TransferPlan plan, LogisticsNetwork network, MinecraftServer server,
            TransferCapabilityCache capCache, long runtimeId) {
        return commitItems(plan, network, server, capCache, runtimeId).moved();
    }

    public static ItemCommitResult commitItems(TransferPlan plan, LogisticsNetwork network, MinecraftServer server,
            TransferCapabilityCache capCache, long runtimeId) {
        return commitItems(plan, network, server, capCache, runtimeId, List.of());
    }

    public static ItemCommitResult commitItems(TransferPlan plan, LogisticsNetwork network, MinecraftServer server,
            TransferCapabilityCache capCache, long runtimeId, List<DirectStorageBinding> bindings) {
        ThreadGuard.requireServerThread();

        if (plan.runtimeId() != runtimeId || plan.generation() != network.getGeneration()) {
            return new ItemCommitResult(0, Long.MAX_VALUE, 0, 0, 0, 0);
        }
        if (plan.channels().isEmpty()) {
            return new ItemCommitResult(0, plan.itemWakeDelta(), 0, 0, 0, 0);
        }

        int total = 0;
        int planned = 0;
        int committed = 0;
        int recovered = 0;
        int revalidatedChannels = 0;
        long minWakeDelta = plan.itemWakeDelta();
        boolean telemetryActive = NetworkRegistry.get((ServerLevel) server.overworld())
                .getTelemetryManager().isActive(network.getId());
        for (TransferPlan.ChannelMoves channel : plan.channels()) {
            ChannelCommitResult result;
            try (var operation = capCache.storageOperation(true)) {
                result = commitChannel(channel, network, server, capCache, telemetryActive, bindings);
            }
            total += result.moved();
            planned += result.planned();
            committed += result.committed();
            recovered += result.recovered();
            if (result.revalidated()) {
                revalidatedChannels++;
            }
            minWakeDelta = earlierWakeDelta(minWakeDelta, result.wakeDelta());
        }
        return new ItemCommitResult(
                total, minWakeDelta, planned, committed, recovered, revalidatedChannels);
    }

    static long earlierWakeDelta(long currentMinimum, long channelWakeDelta) {
        return Math.min(currentMinimum, channelWakeDelta);
    }

    private static ChannelCommitResult commitChannel(TransferPlan.ChannelMoves channel, LogisticsNetwork network,
            MinecraftServer server, TransferCapabilityCache capCache, boolean telemetryActive,
            List<DirectStorageBinding> bindings) {
        int planned = channel.moves().stream().mapToInt(TransferPlan.ItemMove::amount).sum();
        LogisticsNodeEntity sourceNode = TransferEngine.findNode(server, channel.sourceNodeId(),
                network.getNodeDimension(channel.sourceNodeId()));
        if (sourceNode == null || !sourceNode.isValidNode()) {
            return ChannelCommitResult.skipped(planned);
        }

        ChannelData sourceChannel = sourceNode.getChannel(channel.channelIndex());
        if (!isItemChannel(sourceChannel, ChannelMode.EXPORT)
                || !TransferEngine.canRunChannel(sourceNode, sourceChannel)) {
            return ChannelCommitResult.skipped(planned);
        }

        if (!isEndpointLoaded(sourceNode) || !isActive(sourceNode, sourceChannel)) {
            return ChannelCommitResult.skipped(planned);
        }

        boolean directSource = !FilterLogic.hasConfiguredSlotMapping(
                sourceChannel.getFilterItems(), FilterItemData.createReadCache());
        IItemHandler source = capCache.findItemExportHandler(
                sourceNode, sourceChannel.getIoDirection(), directSource);
        if (source == null) {
            return ChannelCommitResult.skipped(planned);
        }
        if (!matchesBinding(channel.sourceBinding(), source, bindings)) {
            return new ChannelCommitResult(planned, 0, 0, 1, true);
        }
        IItemHandler sourceBulk = capCache.findBulkItemHandler(sourceNode, source);

        ResolvedTarget[] targets = resolveTargets(channel.targets(), sourceNode, network, server, capCache);
        for (int i = 0; i < targets.length; i++) {
            int binding = channel.targets().get(i).binding();
            if (targets[i] == null ? binding >= 0 : !matchesBinding(binding, targets[i].handler(), bindings)) {
                return new ChannelCommitResult(planned, 0, 0, 1, true);
            }
        }

        int committed = 0;
        int tier = network.getTierCache().getOrDefault(sourceNode.getUUID(), 0);
        int currentBatch = Math.max(1, Math.min(sourceChannel.getBatchSize(),
                TransferEngine.getBatchLimit(ChannelType.ITEM, tier)));
        boolean roundRobin = sourceChannel.getDistributionMode() == DistributionMode.ROUND_ROBIN;
        Map<Item, Integer> committedByItem = new HashMap<>();
        Map<UUID, Map<Item, Integer>> committedByTarget = new HashMap<>();
        PlannedItemValidation validation = new PlannedItemValidation(
                source, sourceChannel, sourceNode.level().registryAccess(), channel.moves());
        for (TransferPlan.ItemMove move : channel.moves()) {
            if (committed >= currentBatch) break;
            if (move.targetIndex() < 0 || move.targetIndex() >= targets.length) {
                continue;
            }
            ResolvedTarget target = targets[move.targetIndex()];
            if (target == null || sharesItemHandler(
                    source, sourceBulk, target.handler(), target.bulkHandler())) {
                continue;
            }
            Map<Item, Integer> batchMoved = roundRobin
                    ? committedByTarget.computeIfAbsent(channel.targets().get(move.targetIndex()).nodeId(),
                            ignored -> new HashMap<>())
                    : committedByItem;
            TransferPlan.ItemMove validated = validation.validate(
                    move, target.handler(), target.channel(), batchMoved, currentBatch - committed);
            int moved = TransferEngine.commitSingleMove(
                    source, target.handler(), target.bulkHandler(), validated, sourceNode);
            committed += moved;
            if (moved > 0) {
                committedByItem.merge(move.expectedItem(), moved, Integer::sum);
                if (roundRobin) batchMoved.merge(move.expectedItem(), moved, Integer::sum);
                validation.moved(move.expectedItem(), moved, target.handler());
            }
        }

        ServerLevel sourceLevel = (ServerLevel) sourceNode.level();
        int recoveryGoal = Math.min(planned, currentBatch);
        if (DirectStorageHandlers.isDirect(source)) {
            recoveryGoal = currentBatch;
        }
        boolean revalidated = committed < recoveryGoal;
        int recovered = 0;
        if (revalidated) {
            try (var operation = capCache.storageOperation(true)) {
                recovered = TransferEngine.recoverItemChannel(
                        network, server, capCache, channel.sourceNodeId(), channel.channelIndex(),
                        recoveryGoal - committed, committed, committedByItem, committedByTarget);
            }
        }
        int totalMoved = committed + recovered;

        long wakeDelta = TransferEngine.finishChannelAttempt(
                sourceNode, sourceChannel, channel.channelIndex(), totalMoved,
                sourceLevel.getGameTime(), tier, telemetryActive);
        return new ChannelCommitResult(planned, committed, recovered, wakeDelta, revalidated);
    }

    private static ResolvedTarget[] resolveTargets(List<TransferPlan.TargetRef> refs,
            LogisticsNodeEntity sourceNode, LogisticsNetwork network, MinecraftServer server,
            TransferCapabilityCache capCache) {
        ResolvedTarget[] targets = new ResolvedTarget[refs.size()];
        for (int i = 0; i < refs.size(); i++) {
            TransferPlan.TargetRef ref = refs.get(i);
            LogisticsNodeEntity node = TransferEngine.findNode(server, ref.nodeId(),
                    network.getNodeDimension(ref.nodeId()));
            if (node == null || node == sourceNode || !node.isValidNode()) {
                continue;
            }
            ChannelData channel = node.getChannel(ref.channelIndex());
            if (!isItemChannel(channel, ChannelMode.IMPORT)
                    || !TransferEngine.canRunChannel(node, channel)) {
                continue;
            }
            if (!isEndpointLoaded(node) || !isActive(node, channel)) {
                continue;
            }
            if (!sourceNode.level().dimension().equals(node.level().dimension())
                    && !(network.getDimensionalCache().getOrDefault(sourceNode.getUUID(), false)
                    && network.getDimensionalCache().getOrDefault(node.getUUID(), false))) continue;
            if (!sourceNode.isMountedOnCreate() && !node.isMountedOnCreate()
                    && TransferEngine.isSameItemStorage(
                            (ServerLevel) sourceNode.level(), sourceNode.getAttachedPos(),
                            (ServerLevel) node.level(), node.getAttachedPos())) {
                continue;
            }
            boolean directTarget = !FilterLogic.hasConfiguredSlotMapping(
                    channel.getFilterItems(), FilterItemData.createReadCache());
            IItemHandler handler = capCache.findItemImportHandler(
                    node, channel.getIoDirection(), directTarget);
            if (handler == null) {
                continue;
            }
            IItemHandler bulkHandler = null;
            if (ref.bulk()) {
                bulkHandler = capCache.findBulkItemHandler(node, handler);
                if (bulkHandler == null) {
                    continue;
                }
            }
            targets[i] = new ResolvedTarget(handler, bulkHandler, channel);
        }
        return targets;
    }

    static boolean sharesItemHandler(IItemHandler source, @Nullable IItemHandler sourceBulk,
            IItemHandler target, @Nullable IItemHandler targetBulk) {
        return source == target || source == targetBulk
                || sourceBulk != null && (sourceBulk == target || sourceBulk == targetBulk)
                || DirectStorageHandlers.shareNetwork(source, target);
    }

    private static boolean isItemChannel(@Nullable ChannelData channel, ChannelMode mode) {
        return channel != null && channel.isEnabled()
                && channel.getMode() == mode && channel.getType() == ChannelType.ITEM;
    }

    private static boolean isEndpointLoaded(LogisticsNodeEntity node) {
        if (node.isMountedOnCreate()) {
            return CreateCompat.isResolved(node);
        }
        return node.level() instanceof ServerLevel level && level.isLoaded(node.getAttachedPos());
    }

    private static boolean matchesBinding(int index, IItemHandler handler, List<DirectStorageBinding> bindings) {
        return index < 0 ? DirectStorageHandlers.snapshotView(handler) == 0
                : index < bindings.size() && bindings.get(index).matches(handler);
    }

    private static boolean isActive(LogisticsNodeEntity node, ChannelData channel) {
        if (channel.getRedstoneMode() == RedstoneMode.ALWAYS_ON) return true;
        if (channel.getRedstoneMode() == RedstoneMode.ALWAYS_OFF) return false;
        int signal = node.isMountedOnCreate() ? 0 : node.level().getBestNeighborSignal(node.getAttachedPos());
        return TransferEngine.isRedstoneActive(channel.getRedstoneMode(), signal);
    }

    private record ResolvedTarget(IItemHandler handler, @Nullable IItemHandler bulkHandler, ChannelData channel) {
    }
}
