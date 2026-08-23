package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
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

    private TransferCommitter() {
    }

    public static int commit(TransferPlan plan, LogisticsNetwork network, MinecraftServer server,
            TransferCapabilityCache capCache, long runtimeId) {
        ThreadGuard.requireServerThread();

        if (plan.runtimeId() != runtimeId || plan.generation() != network.getGeneration()) {
            return 0;
        }

        int total = 0;
        for (TransferPlan.ChannelMoves channel : plan.channels()) {
            total += commitChannel(channel, network, server, capCache);
        }
        return total;
    }

    private static int commitChannel(TransferPlan.ChannelMoves channel, LogisticsNetwork network,
            MinecraftServer server, TransferCapabilityCache capCache) {
        LogisticsNodeEntity sourceNode = TransferEngine.findNode(server, channel.sourceNodeId(),
                network.getNodeDimension(channel.sourceNodeId()));
        if (sourceNode == null || !sourceNode.isValidNode()) {
            return 0;
        }

        ChannelData sourceChannel = sourceNode.getChannel(channel.channelIndex());
        if (!isItemChannel(sourceChannel, ChannelMode.EXPORT)
                || !TransferEngine.canRunChannel(sourceNode, sourceChannel)) {
            return 0;
        }

        if (!isEndpointLoaded(sourceNode)) {
            return 0;
        }

        IItemHandler source = capCache.findItemHandler(sourceNode, sourceChannel.getIoDirection());
        if (source == null) {
            return 0;
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

        if (moved > 0) {
            sourceChannel.getTelemetry().record(moved);
        }
        return moved;
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
