package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.logic.TransferEngine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.*;

public final class TransferCommitter {
    public record ItemCommitResult(int moved, long wakeDelta, int planned, int committed, int recovered,
            int revalidatedChannels) {}

    private record ChannelKey(UUID nodeId, int index) {}
    private record CommittedItems(int amount, Map<Item, Integer> byItem,
            Map<ResourceHandler<ItemResource>, Map<Item, Integer>> byTarget) {}

    private TransferCommitter() {}

    public static ItemCommitResult commitItems(TransferPlan plan, LogisticsNetwork network,
            MinecraftServer server, long runtimeId) {
        ThreadGuard.requireServerThread();
        if (plan.failed() || plan.runtimeId() != runtimeId || plan.generation() != network.getGeneration()
                || !plan.networkId().equals(network.getId())) return empty(Long.MAX_VALUE);
        if (plan.channels().isEmpty()) return empty(plan.itemWakeDelta());
        var context = TransferEngine.prepareNetwork(network, server);
        if (context == null) return empty(Long.MAX_VALUE);
        boolean telemetry = NetworkRegistry.get(server.overworld()).getTelemetryManager().isActive(network.getId());
        int planned = 0, committed = 0, recovered = 0, revalidated = 0;
        long wake = plan.itemWakeDelta();
        Set<ChannelKey> attempted = new HashSet<>();
        for (var channel : plan.channels()) {
            if (plan.generation() != network.getGeneration()) break;
            if (!attempted.add(new ChannelKey(channel.sourceNodeId(), channel.channelIndex()))) continue;
            var result = commitChannel(channel, network, server, context, telemetry, plan.generation());
            planned += result.planned();
            committed += result.committed();
            recovered += result.recovered();
            revalidated += result.revalidatedChannels();
            wake = Math.min(wake, result.wakeDelta());
        }
        return new ItemCommitResult(committed + recovered, wake, planned, committed, recovered, revalidated);
    }

    private static ItemCommitResult empty(long wake) {
        return new ItemCommitResult(0, wake, 0, 0, 0, 0);
    }

    private static ItemCommitResult commitChannel(TransferPlan.ChannelMoves plan, LogisticsNetwork network,
            MinecraftServer server, TransferEngine.NetworkContext context, boolean telemetry, long generation) {
        var sourceNode = sourceNode(plan, network, context);
        if (sourceNode == null) return empty(Long.MAX_VALUE);
        var channel = sourceNode.getChannel(plan.channelIndex());
        var level = (ServerLevel) sourceNode.level();
        int tier = context.tierCache().getOrDefault(sourceNode.getUUID(), 0);
        long cooldown = TransferEngine.cooldownRemaining(sourceNode, channel, plan.channelIndex(), tier, level.getGameTime());
        if (cooldown > 0) return empty(cooldown);
        var source = sourceNode.capabilities().findItemHandler(channel.getIoDirection());
        if (source == null) return empty(Long.MAX_VALUE);
        var cache = FilterItemData.createReadCache();
        var resolved = resolveTargets(sourceNode, channel, plan.channelIndex(), source, network, context, cache);
        int batch = batchLimit(channel, tier);
        int planned = plannedAmount(plan, source, batch);
        var progress = commitMoves(plan, source, channel, level, resolved, planned, cache, network, generation);
        int committed = progress.amount();
        int recovered = committed < planned && network.getGeneration() == generation ? recoverItemChannel(plan, network, server,
                planned - committed, committed, progress.byItem(), progress.byTarget()) : 0;
        int total = committed + recovered;
        long wake = TransferEngine.finishChannelAttempt(sourceNode, channel, plan.channelIndex(), total,
                level.getGameTime(), tier, telemetry);
        return new ItemCommitResult(total, wake, planned, committed, recovered, committed < planned ? 1 : 0);
    }

    private static CommittedItems commitMoves(TransferPlan.ChannelMoves plan, ResourceHandler<ItemResource> source,
            ChannelData channel, ServerLevel level, TransferEngine.ResolvedItemTargets resolved, int planned,
            FilterItemData.ReadCache cache, LogisticsNetwork network, long generation) {
        int committed = 0;
        Map<Item, Integer> movedByItem = new HashMap<>();
        Map<ResourceHandler<ItemResource>, Map<Item, Integer>> movedByTarget = new IdentityHashMap<>();
        boolean roundRobin = channel.getDistributionMode() == DistributionMode.ROUND_ROBIN;
        for (var intent : plan.moves()) {
            if (network.getGeneration() != generation) break;
            if (!validIntent(intent, source, plan.targets().size()) || committed >= planned) continue;
            var target = plannedTarget(plan.targets().get(intent.targetIndex()), resolved);
            if (target == null) continue;
            var targetTotals = movedByTarget.computeIfAbsent(target.handler(), ignored -> new HashMap<>());
            int moved = TransferEngine.commitSingleMove(source, target, intent, planned - committed,
                    channel.getFilterItems(), channel.getFilterMode(), level.registryAccess(), cache,
                    roundRobin ? targetTotals : movedByItem);
            if (moved == 0) continue;
            committed += moved;
            movedByItem.merge(intent.resource().getItem(), moved, Integer::sum);
            targetTotals.merge(intent.resource().getItem(), moved, Integer::sum);
        }
        return new CommittedItems(committed, movedByItem, movedByTarget);
    }

    public static int recoverItemChannel(TransferPlan.ChannelMoves plan, LogisticsNetwork network,
            MinecraftServer server, int shortfall, int committed, Map<Item, Integer> movedByItem,
            Map<ResourceHandler<ItemResource>, Map<Item, Integer>> movedByTarget) {
        ThreadGuard.requireServerThread();
        if (shortfall <= 0) return 0;
        var context = TransferEngine.prepareNetwork(network, server);
        if (context == null) return 0;
        var node = sourceNode(plan, network, context);
        if (node == null) return 0;
        var channel = node.getChannel(plan.channelIndex());
        int tier = context.tierCache().getOrDefault(node.getUUID(), 0);
        int limit = Math.min(shortfall, Math.max(0, batchLimit(channel, tier) - committed));
        if (limit == 0) return 0;
        var source = node.capabilities().findItemHandler(channel.getIoDirection());
        if (source == null) return 0;
        var cache = FilterItemData.createReadCache();
        var targets = resolveTargets(node, channel, plan.channelIndex(), source, network, context, cache);
        return TransferEngine.executeMove(source, targets.targets(), limit, channel.getFilterItems(),
                channel.getFilterMode(), null, ((ServerLevel) node.level()).registryAccess(),
                channel.getDistributionMode() == DistributionMode.ROUND_ROBIN, cache, null,
                movedByItem, movedByTarget);
    }

    private static int batchLimit(ChannelData channel, int tier) {
        return Math.max(1, Math.min(channel.getBatchSize(), TransferEngine.getBatchLimit(ChannelType.ITEM, tier)));
    }

    private static LogisticsNodeEntity sourceNode(TransferPlan.ChannelMoves plan, LogisticsNetwork network,
            TransferEngine.NetworkContext context) {
        if (plan.channelIndex() < 0 || plan.channelIndex() >= LogisticsNodeEntity.CHANNEL_COUNT
                || !network.getNodeUuids().contains(plan.sourceNodeId())) return null;
        for (var node : context.sortedNodes()) {
            if (!node.getUUID().equals(plan.sourceNodeId())) continue;
            var channel = node.getChannel(plan.channelIndex());
            if (isActive(node, channel, ChannelMode.EXPORT)
                    && plan.distributionMode() == channel.getDistributionMode()
                    && Objects.equals(plan.sourceBinding(), Snapshots.binding(node, channel))) return node;
        }
        return null;
    }

    private static boolean isActive(LogisticsNodeEntity node, ChannelData channel, ChannelMode mode) {
        if (channel == null || !channel.isEnabled() || channel.getMode() != mode || channel.getType() != ChannelType.ITEM
                || !node.isValidNode() || !(node.level() instanceof ServerLevel level)
                || !level.isLoaded(node.getAttachedPos())) return false;
        return TransferEngine.isRedstoneActive(channel.getRedstoneMode(),
                level.getBestNeighborSignal(node.getAttachedPos()));
    }

    private static TransferEngine.ResolvedItemTargets resolveTargets(LogisticsNodeEntity sourceNode,
            ChannelData channel, int index, ResourceHandler<ItemResource> source, LogisticsNetwork network,
            TransferEngine.NetworkContext context, FilterItemData.ReadCache cache) {
        List<TransferEngine.ImportTarget> targets = new ArrayList<>();
        for (var ref : context.itemImports()[index]) {
            var current = ref.node().getChannel(index);
            if (network.getNodeUuids().contains(ref.node().getUUID()) && isActive(ref.node(), current, ChannelMode.IMPORT))
                targets.add(new TransferEngine.ImportTarget(ref.node(), current, index));
        }
        return TransferEngine.resolveItemTargets(sourceNode, (ServerLevel) sourceNode.level(), channel,
                targets, source, context.dimensionalCache(), cache);
    }

    private static TransferEngine.ItemTransferTarget plannedTarget(TransferPlan.TargetRef target,
            TransferEngine.ResolvedItemTargets resolved) {
        for (int i = 0; i < resolved.refs().size(); i++) {
            var ref = resolved.refs().get(i);
            if (target.nodeId().equals(ref.node().getUUID()) && target.channelIndex() == ref.channelIndex()
                    && Objects.equals(target.binding(), Snapshots.binding(ref.node(), ref.channel())))
                return resolved.targets().get(i);
        }
        return null;
    }

    private static int plannedAmount(TransferPlan.ChannelMoves plan, ResourceHandler<ItemResource> source, int batch) {
        long total = 0;
        for (var move : plan.moves()) {
            if (validIntent(move, source, plan.targets().size())) total += move.amount();
        }
        return (int) Math.min(batch, total);
    }

    private static boolean validIntent(TransferPlan.MoveIntent move, ResourceHandler<ItemResource> source, int targets) {
        return move.amount() > 0 && !move.resource().isEmpty() && move.sourceSlot() >= 0
                && move.sourceSlot() < source.size() && move.targetIndex() >= 0 && move.targetIndex() < targets;
    }
}
