package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.DistributionMode;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.integration.storage.DirectStorageHandlers;
import me.almana.logisticsnetworks.logic.FilterLogic;
import me.almana.logisticsnetworks.logic.ItemResourceOrder;
import me.almana.logisticsnetworks.logic.PlannedItemValidation;
import me.almana.logisticsnetworks.logic.TransferCapabilityCache;
import me.almana.logisticsnetworks.logic.TransferEngine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class TransferCommitter {
    public record ItemCommitResult(int moved, long wakeDelta, int planned, int committed, int recovered,
            int revalidatedChannels) {
    }

    private record ChannelKey(UUID nodeId, int index) {
    }

    private record CommittedItems(int amount, Map<Item, Integer> byItem,
            Map<UUID, Map<Item, Integer>> byTarget, ItemResource resource, ItemResourceOrder.Cursor cursor) {
    }

    private record ResolvedTarget(TransferEngine.ItemTransferTarget target, ChannelData channel) {
    }

    private TransferCommitter() {
    }

    public static ItemCommitResult commitItems(TransferPlan plan, LogisticsNetwork network,
            MinecraftServer server, long runtimeId) {
        return commitItems(plan, network, server, runtimeId, List.of());
    }

    public static ItemCommitResult commitItems(TransferPlan plan, LogisticsNetwork network,
            MinecraftServer server, long runtimeId, List<DirectStorageBinding> bindings) {
        ThreadGuard.requireServerThread();
        if (plan.failed() || plan.runtimeId() != runtimeId || plan.generation() != network.getGeneration()
                || !plan.networkId().equals(network.getId())) return empty(Long.MAX_VALUE);
        if (plan.channels().isEmpty()) return empty(plan.itemWakeDelta());
        TransferEngine.NetworkContext context = TransferEngine.prepareNetwork(network, server);
        if (context == null) return empty(Long.MAX_VALUE);
        boolean telemetry = NetworkRegistry.get(server.overworld()).getTelemetryManager().isActive(network.getId());
        int planned = 0;
        int committed = 0;
        int recovered = 0;
        int revalidated = 0;
        long wake = plan.itemWakeDelta();
        Set<ChannelKey> attempted = new HashSet<>();
        for (TransferPlan.ChannelMoves channel : plan.channels()) {
            if (plan.generation() != network.getGeneration()) break;
            if (!attempted.add(new ChannelKey(channel.sourceNodeId(), channel.channelIndex()))) continue;
            ItemCommitResult result;
            try (var operation = TransferCapabilityCache.storageOperation(true)) {
                result = commitChannel(channel, network, server, context, telemetry,
                        plan.generation(), bindings);
            }
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
            MinecraftServer server, TransferEngine.NetworkContext context, boolean telemetry,
            long generation, List<DirectStorageBinding> bindings) {
        int planned = plannedAmount(plan);
        LogisticsNodeEntity sourceNode = sourceNode(plan, network, context);
        if (sourceNode == null) return skipped(planned, Long.MAX_VALUE);
        ChannelData channel = sourceNode.getChannel(plan.channelIndex());
        ServerLevel level = (ServerLevel) sourceNode.level();
        int tier = context.tierCache().getOrDefault(sourceNode.getUUID(), 0);
        long cooldown = TransferEngine.cooldownRemaining(
                sourceNode, channel, plan.channelIndex(), tier, level.getGameTime());
        if (cooldown > 0) return skipped(planned, cooldown);
        FilterItemData.ReadCache cache = channel.getReadCache();
        boolean directSource = !FilterLogic.hasConfiguredSlotMapping(channel.getFilterItems(), cache);
        ResourceHandler<ItemResource> source = sourceNode.capabilities().findItemExportHandler(
                channel.getIoDirection(), directSource);
        if (source == null) return skipped(planned, Long.MAX_VALUE);
        if (!matchesBinding(plan.sourceStorageBinding(), source, bindings)) return revalidated(planned);

        TransferEngine.ResolvedItemTargets resolved = resolveTargets(
                sourceNode, channel, plan.channelIndex(), source, network, context, cache);
        ResolvedTarget[] targets = new ResolvedTarget[plan.targets().size()];
        for (int index = 0; index < targets.length; index++) {
            targets[index] = plannedTarget(plan.targets().get(index), resolved);
            int binding = plan.targets().get(index).storageBinding();
            if (targets[index] == null ? binding >= 0
                    : !matchesBinding(binding, targets[index].target().handler(), bindings)) {
                return revalidated(planned);
            }
        }

        int batch = batchLimit(channel, tier);
        CommittedItems progress = commitMoves(
                plan, source, channel, level, targets, batch, network, generation);
        int committed = progress.amount();
        int recoveryGoal = DirectStorageHandlers.isDirect(source) ? batch : Math.min(planned, batch);
        int recovered = 0;
        ItemResourceOrder.Cursor cursor = progress.cursor();
        if (committed < recoveryGoal && network.getGeneration() == generation) {
            try (var operation = TransferCapabilityCache.storageOperation(true)) {
                var recovery = recoverItemChannel(plan, network, server, recoveryGoal - committed, committed,
                        progress.byItem(), progress.byTarget(), progress.resource());
                recovered = recovery.moved();
                if (recovery.cursor() != null) cursor = recovery.cursor();
            }
        }
        int total = committed + recovered;
        if (channel.canRotateResources() && total > 0 && cursor != null) channel.setItemResourceCursor(cursor);
        long wake = TransferEngine.finishChannelAttempt(sourceNode, channel, plan.channelIndex(), total,
                level.getGameTime(), tier, telemetry);
        return new ItemCommitResult(total, wake, planned, committed, recovered,
                committed < recoveryGoal ? 1 : 0);
    }

    private static CommittedItems commitMoves(TransferPlan.ChannelMoves plan,
            ResourceHandler<ItemResource> source, ChannelData channel, ServerLevel level,
            ResolvedTarget[] targets, int batch, LogisticsNetwork network, long generation) {
        int committed = 0;
        Map<Item, Integer> movedByItem = new HashMap<>();
        Map<UUID, Map<Item, Integer>> movedByTarget = new HashMap<>();
        ItemResource movedResource = null;
        ItemResourceOrder.Cursor cursor = null;
        boolean roundRobin = channel.getDistributionMode() == DistributionMode.ROUND_ROBIN;
        PlannedItemValidation validation = new PlannedItemValidation(
                source, channel, level.registryAccess(), plan.moves());
        for (TransferPlan.MoveIntent intent : plan.moves()) {
            if (network.getGeneration() != generation || committed >= batch) break;
            if (channel.canRotateResources() && movedResource != null && !movedResource.equals(intent.resource())) continue;
            if (intent.targetIndex() < 0 || intent.targetIndex() >= targets.length) continue;
            ResolvedTarget resolved = targets[intent.targetIndex()];
            if (resolved == null) continue;
            UUID targetId = plan.targets().get(intent.targetIndex()).nodeId();
            Map<Item, Integer> targetTotals = movedByTarget.computeIfAbsent(targetId,
                    ignored -> new HashMap<>());
            Map<Item, Integer> batchMoved = roundRobin ? targetTotals : movedByItem;
            TransferPlan.MoveIntent validated = validation.validate(
                    intent, resolved.target().handler(), resolved.channel(), batchMoved, batch - committed);
            ItemResourceOrder.Cursor nextCursor = channel.canRotateResources() && movedResource == null
                    ? ItemResourceOrder.after(source, intent.resource()) : null;
            int moved = TransferEngine.commitSingleMove(source, resolved.target(), validated);
            if (moved == 0) continue;
            if (nextCursor != null) cursor = nextCursor;
            movedResource = intent.resource();
            committed += moved;
            movedByItem.merge(intent.getItem(), moved, Integer::sum);
            targetTotals.merge(intent.getItem(), moved, Integer::sum);
            validation.moved(intent.getItem(), moved, resolved.target().handler());
        }
        return new CommittedItems(committed, movedByItem, movedByTarget, movedResource, cursor);
    }

    public static ItemResourceOrder.Result recoverItemChannel(TransferPlan.ChannelMoves plan, LogisticsNetwork network,
            MinecraftServer server, int shortfall, int committed, Map<Item, Integer> movedByItem,
            Map<UUID, Map<Item, Integer>> movedByTarget, ItemResource requiredResource) {
        ThreadGuard.requireServerThread();
        if (shortfall <= 0) return ItemResourceOrder.EMPTY;
        long generation = network.getGeneration();
        TransferEngine.NetworkContext context = TransferEngine.prepareNetwork(network, server);
        if (context == null) return ItemResourceOrder.EMPTY;
        LogisticsNodeEntity node = sourceNode(plan, network, context);
        if (node == null) return ItemResourceOrder.EMPTY;
        ChannelData channel = node.getChannel(plan.channelIndex());
        int tier = context.tierCache().getOrDefault(node.getUUID(), 0);
        int limit = Math.min(shortfall, Math.max(0, batchLimit(channel, tier) - committed));
        if (limit == 0) return ItemResourceOrder.EMPTY;
        FilterItemData.ReadCache cache = channel.getReadCache();
        boolean directSource = !FilterLogic.hasConfiguredSlotMapping(channel.getFilterItems(), cache);
        ResourceHandler<ItemResource> source = node.capabilities().findItemExportHandler(
                channel.getIoDirection(), directSource);
        if (source == null) return ItemResourceOrder.EMPTY;
        TransferEngine.ResolvedItemTargets targets = resolveTargets(
                node, channel, plan.channelIndex(), source, network, context, cache);
        Map<ResourceHandler<ItemResource>, Map<Item, Integer>> targetBatches = new IdentityHashMap<>();
        for (int index = 0; index < targets.refs().size(); index++) {
            Map<Item, Integer> moved = movedByTarget.get(targets.refs().get(index).node().getUUID());
            if (moved != null) targetBatches.put(targets.targets().get(index).handler(), moved);
        }
        return TransferEngine.executeItemOperation(source, targets.targets(), limit, channel.getFilterItems(),
                channel.getFilterMode(), null, ((ServerLevel) node.level()).registryAccess(),
                channel.getDistributionMode() == DistributionMode.ROUND_ROBIN, cache, null,
                movedByItem, targetBatches, () -> network.getGeneration() == generation,
                channel.canRotateResources(), channel.getItemResourceCursor(), requiredResource);
    }

    private static int batchLimit(ChannelData channel, int tier) {
        return Math.max(1, Math.min(
                channel.getBatchSize(), TransferEngine.getBatchLimit(ChannelType.ITEM, tier)));
    }

    private static LogisticsNodeEntity sourceNode(TransferPlan.ChannelMoves plan, LogisticsNetwork network,
            TransferEngine.NetworkContext context) {
        if (plan.channelIndex() < 0 || plan.channelIndex() >= LogisticsNodeEntity.CHANNEL_COUNT
                || !network.getNodeUuids().contains(plan.sourceNodeId())) return null;
        for (LogisticsNodeEntity node : context.sortedNodes()) {
            if (!node.getUUID().equals(plan.sourceNodeId())) continue;
            ChannelData channel = node.getChannel(plan.channelIndex());
            if (isActive(node, channel, ChannelMode.EXPORT)
                    && plan.distributionMode() == channel.getDistributionMode()
                    && Objects.equals(plan.sourceBinding(), Snapshots.binding(node, channel))) return node;
        }
        return null;
    }

    private static boolean isActive(LogisticsNodeEntity node, ChannelData channel, ChannelMode mode) {
        if (channel == null || !channel.isEnabled() || channel.getMode() != mode
                || channel.getType() != ChannelType.ITEM || !node.isValidNode()
                || !(node.level() instanceof ServerLevel level)
                || !level.isLoaded(node.getAttachedPos())) return false;
        return TransferEngine.isRedstoneActive(channel.getRedstoneMode(),
                level.getBestNeighborSignal(node.getAttachedPos()));
    }

    private static TransferEngine.ResolvedItemTargets resolveTargets(LogisticsNodeEntity sourceNode,
            ChannelData channel, int index, ResourceHandler<ItemResource> source,
            LogisticsNetwork network, TransferEngine.NetworkContext context, FilterItemData.ReadCache cache) {
        List<TransferEngine.ImportTarget> targets = new ArrayList<>();
        for (TransferEngine.ImportTarget ref : context.itemImports()[index]) {
            ChannelData current = ref.node().getChannel(index);
            if (network.getNodeUuids().contains(ref.node().getUUID())
                    && isActive(ref.node(), current, ChannelMode.IMPORT)) {
                targets.add(new TransferEngine.ImportTarget(ref.node(), current, index));
            }
        }
        return TransferEngine.resolveItemTargets(sourceNode, (ServerLevel) sourceNode.level(), channel,
                targets, source, context.dimensionalCache(), cache);
    }

    private static ResolvedTarget plannedTarget(TransferPlan.TargetRef target,
            TransferEngine.ResolvedItemTargets resolved) {
        for (int index = 0; index < resolved.refs().size(); index++) {
            TransferEngine.ImportTarget ref = resolved.refs().get(index);
            if (target.nodeId().equals(ref.node().getUUID())
                    && target.channelIndex() == ref.channelIndex()
                    && Objects.equals(target.binding(), Snapshots.binding(ref.node(), ref.channel()))) {
                return new ResolvedTarget(resolved.targets().get(index), ref.channel());
            }
        }
        return null;
    }

    private static boolean matchesBinding(int index, ResourceHandler<ItemResource> handler,
            List<DirectStorageBinding> bindings) {
        return index < 0 ? DirectStorageHandlers.snapshotView(handler) == 0
                : index < bindings.size() && bindings.get(index).matches(handler);
    }

    private static int plannedAmount(TransferPlan.ChannelMoves plan) {
        long total = 0;
        for (TransferPlan.MoveIntent move : plan.moves()) {
            if (move.amount() > 0 && !move.resource().isEmpty()) total += move.amount();
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static ItemCommitResult skipped(int planned, long wake) {
        return new ItemCommitResult(0, wake, planned, 0, 0, 0);
    }

    private static ItemCommitResult revalidated(int planned) {
        return new ItemCommitResult(0, 1, planned, 0, 0, 1);
    }
}
