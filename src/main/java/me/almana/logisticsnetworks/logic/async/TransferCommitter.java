package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.create.CreateCompat;
import me.almana.logisticsnetworks.logic.TransferCapabilityCache;
import me.almana.logisticsnetworks.logic.TransferEngine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class TransferCommitter {

    public record ItemCommitResult(int moved, long wakeDelta) {
    }

    private record ChannelCommitResult(int moved, long wakeDelta) {

        private static final ChannelCommitResult SKIPPED = new ChannelCommitResult(0, Long.MAX_VALUE);
    }

    private TransferCommitter() {
    }

    public static int commit(TransferPlan plan, LogisticsNetwork network, MinecraftServer server,
            TransferCapabilityCache capCache, long runtimeId) {
        return commitItems(plan, network, server, capCache, runtimeId).moved();
    }

    public static ItemCommitResult commitItems(TransferPlan plan, LogisticsNetwork network, MinecraftServer server,
            TransferCapabilityCache capCache, long runtimeId) {
        ThreadGuard.requireServerThread();

        if (plan.runtimeId() != runtimeId || plan.generation() != network.getGeneration()) {
            return new ItemCommitResult(0, Long.MAX_VALUE);
        }
        if (plan.channels().isEmpty()) {
            return new ItemCommitResult(0, plan.itemWakeDelta());
        }

        int total = 0;
        long minWakeDelta = plan.itemWakeDelta();
        boolean telemetryActive = NetworkRegistry.get((ServerLevel) server.overworld())
                .getTelemetryManager().isActive(network.getId());
        for (TransferPlan.ChannelMoves channel : plan.channels()) {
            ChannelCommitResult result = commitChannel(channel, network, server, capCache, telemetryActive);
            total += result.moved();
            minWakeDelta = earlierWakeDelta(minWakeDelta, result.wakeDelta());
        }
        return new ItemCommitResult(total, minWakeDelta);
    }

    static long earlierWakeDelta(long currentMinimum, long channelWakeDelta) {
        return Math.min(currentMinimum, channelWakeDelta);
    }

    private static ChannelCommitResult commitChannel(TransferPlan.ChannelMoves channel, LogisticsNetwork network,
            MinecraftServer server, TransferCapabilityCache capCache, boolean telemetryActive) {
        LogisticsNodeEntity sourceNode = TransferEngine.findNode(server, channel.sourceNodeId(),
                network.getNodeDimension(channel.sourceNodeId()));
        if (sourceNode == null || !sourceNode.isValidNode()) {
            return ChannelCommitResult.SKIPPED;
        }

        ChannelData sourceChannel = sourceNode.getChannel(channel.channelIndex());
        if (!isItemChannel(sourceChannel, ChannelMode.EXPORT)
                || !TransferEngine.canRunChannel(sourceNode, sourceChannel)) {
            return ChannelCommitResult.SKIPPED;
        }

        if (!isEndpointLoaded(sourceNode)) {
            return ChannelCommitResult.SKIPPED;
        }

        IItemHandler source = capCache.findItemHandler(sourceNode, sourceChannel.getIoDirection());
        if (source == null) {
            return ChannelCommitResult.SKIPPED;
        }
        IItemHandler sourceBulk = capCache.findBulkItemHandler(sourceNode, source);

        ResolvedTarget[] targets = resolveTargets(channel.targets(), sourceNode, network, server, capCache);

        int moved = 0;
        for (TransferPlan.ItemMove move : channel.moves()) {
            if (move.targetIndex() < 0 || move.targetIndex() >= targets.length) {
                continue;
            }
            ResolvedTarget target = targets[move.targetIndex()];
            if (target == null || sharesItemHandler(
                    source, sourceBulk, target.handler(), target.bulkHandler())) {
                continue;
            }
            moved += TransferEngine.commitSingleMove(
                    source, target.handler(), target.bulkHandler(), move, sourceNode);
        }

        ServerLevel sourceLevel = (ServerLevel) sourceNode.level();
        int tier = network.getTierCache().getOrDefault(sourceNode.getUUID(), 0);
        long wakeDelta = TransferEngine.finishChannelAttempt(
                sourceNode, sourceChannel, channel.channelIndex(), moved,
                sourceLevel.getGameTime(), tier, telemetryActive);
        return new ChannelCommitResult(moved, wakeDelta);
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
            if (!isEndpointLoaded(node)) {
                continue;
            }
            if (!sourceNode.isMountedOnCreate() && !node.isMountedOnCreate()
                    && TransferEngine.isSameItemStorage(
                            (ServerLevel) sourceNode.level(), sourceNode.getAttachedPos(),
                            (ServerLevel) node.level(), node.getAttachedPos())) {
                continue;
            }
            IItemHandler handler = capCache.findItemHandler(node, channel.getIoDirection());
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
            targets[i] = new ResolvedTarget(handler, bulkHandler);
        }
        return targets;
    }

    static boolean sharesItemHandler(IItemHandler source, @Nullable IItemHandler sourceBulk,
            IItemHandler target, @Nullable IItemHandler targetBulk) {
        return source == target || source == targetBulk
                || sourceBulk != null && (sourceBulk == target || sourceBulk == targetBulk);
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

    private record ResolvedTarget(IItemHandler handler, @Nullable IItemHandler bulkHandler) {
    }
}
