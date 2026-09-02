package me.almana.logisticsnetworks.logic;

import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.NbtFilterData;
import me.almana.logisticsnetworks.integration.ars.ArsCompat;
import me.almana.logisticsnetworks.integration.ars.SourceTransferHelper;
import me.almana.logisticsnetworks.integration.create.CreateCompat;
import me.almana.logisticsnetworks.integration.mekanism.ChemicalTransferHelper;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.integration.sophisticated.SophisticatedCoreCompat;
import me.almana.logisticsnetworks.logic.async.ThreadGuard;
import me.almana.logisticsnetworks.logic.async.TransferPlan;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

public class TransferEngine {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float BACKOFF_MULTIPLIER = 1.3f;
    private static final float BACKOFF_DECAY_DIVISOR = 3f;
    private static final float BACKOFF_MAX_TICKS_ENERGY = 5f;

    public record ImportTarget(LogisticsNodeEntity node, ChannelData channel, int channelIndex) {
    }

    public record ItemTransferTarget(IItemHandler handler, @Nullable IItemHandler bulkHandler,
            ItemStack[] importFilters,
            FilterMode importFilterMode, TransferAmountRules.Constraints constraints, boolean hasItemNbtFilter,
            boolean[] allowedSlots, boolean hasImportSlotMapping) {
    }

    public record ResolvedItemTargets(List<ItemTransferTarget> targets, List<ImportTarget> refs, int status) {
        public static final int OK = 0;
        public static final int NO_REACHABLE = -1;
        public static final int PAUSED = -2;
    }

    public record NetworkContext(
            List<LogisticsNodeEntity> sortedNodes,
            Map<UUID, Integer> signalCache,
            List<ImportTarget>[] itemImports,
            Map<UUID, Boolean> dimensionalCache,
            Map<UUID, Integer> tierCache) {
    }

    @FunctionalInterface
    public interface MoveRecorder {
        void record(int sourceSlot, int targetIndex, ItemStack moved, boolean[] targetSlotMask);
    }

    public static long processNetwork(LogisticsNetwork network, MinecraftServer server) {
        return processNetwork(network, server, true);
    }

    public static long processNetworkWithoutItemTransfers(LogisticsNetwork network, MinecraftServer server) {
        return processNetwork(network, server, false);
    }

    private static long processNetwork(LogisticsNetwork network, MinecraftServer server, boolean includeItemTransfers) {
        if (network == null || server == null) {
            return Long.MAX_VALUE;
        }

        NetworkContext context = prepareNetwork(network, server);
        if (context == null) {
            return Long.MAX_VALUE;
        }

        Map<UUID, LogisticsNodeEntity> nodeCache = new HashMap<>(context.sortedNodes().size());
        for (LogisticsNodeEntity node : context.sortedNodes()) {
            nodeCache.put(node.getUUID(), node);
        }

        List<ImportTarget>[] fluidImports = resolveCache(network.getFluidImports(), nodeCache,
                context.signalCache());
        List<ImportTarget>[] energyImports = resolveCache(network.getEnergyImports(), nodeCache,
                context.signalCache());
        List<ImportTarget>[] chemicalImports = resolveCache(network.getChemicalImports(), nodeCache,
                context.signalCache());
        List<ImportTarget>[] sourceImports = resolveCache(network.getSourceImports(), nodeCache,
                context.signalCache());

        NetworkRegistry registry = NetworkRegistry.get((ServerLevel) server.overworld());
        boolean telemetryActive = registry.getTelemetryManager().isActive(network.getId());
        TransferCapabilityCache capCache = registry.getCapabilityCache();

        long minWakeDelta = Long.MAX_VALUE;
        for (LogisticsNodeEntity sourceNode : context.sortedNodes()) {
            long delta = processNode(sourceNode, context.itemImports(), fluidImports, energyImports, chemicalImports,
                    sourceImports, context.signalCache(), context.dimensionalCache(), context.tierCache(),
                    telemetryActive, capCache, includeItemTransfers);
            if (delta < minWakeDelta) {
                minWakeDelta = delta;
            }
        }

        return minWakeDelta;
    }

    @Nullable
    public static NetworkContext prepareNetwork(LogisticsNetwork network, MinecraftServer server) {
        NetworkRegistry registry = NetworkRegistry.get((ServerLevel) server.overworld());
        if (network.isCacheDirty()) {
            network.rebuildCache(registry);
            network.clearCacheDirty();
        }

        List<UUID> sortedUuids = network.getSortedUuids();
        if (sortedUuids.isEmpty()) {
            return null;
        }

        List<LogisticsNodeEntity> sortedNodes = new ArrayList<>(sortedUuids.size());
        Map<UUID, LogisticsNodeEntity> nodeCache = new HashMap<>(sortedUuids.size());
        for (UUID nodeId : sortedUuids) {
            LogisticsNodeEntity node = findNode(server, nodeId, network.getNodeDimension(nodeId));
            if (node != null && node.isValidNode()) {
                sortedNodes.add(node);
                nodeCache.put(node.getUUID(), node);
            } else if (Config.debugMode) {
                LOGGER.debug("Node {} missing from world, skipping.", nodeId);
            }
        }
        if (sortedNodes.isEmpty()) {
            return null;
        }

        Map<UUID, Integer> signalCache = buildSignalCache(sortedNodes);
        if (signalCache.isEmpty()) {
            return null;
        }

        return new NetworkContext(
                sortedNodes,
                signalCache,
                resolveCache(network.getItemImports(), nodeCache, signalCache),
                network.getDimensionalCache(),
                network.getTierCache());
    }

    private static Map<UUID, Integer> buildSignalCache(List<LogisticsNodeEntity> nodes) {
        Map<UUID, Integer> signalCache = new HashMap<>();
        boolean hasAnyExporter = false;

        for (LogisticsNodeEntity node : nodes) {
            if (!node.isValidNode())
                continue;
            boolean needsSignal = false;
            boolean hasExport = false;

            for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
                ChannelData ch = node.getChannel(i);
                if (ch != null && ch.isEnabled()) {
                    RedstoneMode rm = ch.getRedstoneMode();
                    if (rm == RedstoneMode.HIGH || rm == RedstoneMode.LOW) {
                        needsSignal = true;
                    }
                    if (ch.getMode() == ChannelMode.EXPORT) {
                        hasExport = true;
                    }
                }
            }

            if (hasExport)
                hasAnyExporter = true;

            if (!node.isMountedOnCreate() && node.level() instanceof ServerLevel level) {
                signalCache.put(node.getUUID(), needsSignal ? level.getBestNeighborSignal(node.getAttachedPos()) : 0);
            } else if (node.isMountedOnCreate()) {
                signalCache.put(node.getUUID(), 0);
            }
        }

        return hasAnyExporter ? signalCache : Collections.emptyMap();
    }

    private static final List<ImportTarget>[] EMPTY_RESOLVED = createEmptyResolved();

    @SuppressWarnings("unchecked")
    private static List<ImportTarget>[] createEmptyResolved() {
        List<ImportTarget>[] arr = new List[9];
        Arrays.fill(arr, Collections.emptyList());
        return arr;
    }

    @SuppressWarnings("unchecked")
    private static List<ImportTarget>[] resolveCache(List<NodeRef>[] cache,
            Map<UUID, LogisticsNodeEntity> nodeCache,
            Map<UUID, Integer> signalCache) {
        boolean anyNonEmpty = false;
        for (int i = 0; i < 9; i++) {
            if (!cache[i].isEmpty()) {
                anyNonEmpty = true;
                break;
            }
        }
        if (!anyNonEmpty)
            return EMPTY_RESOLVED;

        List<ImportTarget>[] resolved = new List[9];
        for (int i = 0; i < 9; i++) {
            List<NodeRef> cachedNodes = cache[i];
            if (cachedNodes.isEmpty()) {
                resolved[i] = Collections.emptyList();
                continue;
            }
            List<ImportTarget> targets = new ArrayList<>(cachedNodes.size());
            for (NodeRef ref : cachedNodes) {
                LogisticsNodeEntity node = nodeCache.get(ref.nodeId());
                if (node == null)
                    continue;
                ChannelData cd = node.getChannel(i);
                if (cd == null)
                    continue;
                if (!canRunChannel(node, cd) || !CreateCompat.isResolved(node))
                    continue;
                int sig = signalCache.getOrDefault(ref.nodeId(), 0);
                if (!isRedstoneActive(cd.getRedstoneMode(), sig))
                    continue;
                targets.add(new ImportTarget(node, cd, i));
            }
            resolved[i] = targets;
        }
        return resolved;
    }

    private static long processNode(LogisticsNodeEntity sourceNode,
            List<ImportTarget>[] itemImports,
            List<ImportTarget>[] fluidImports,
            List<ImportTarget>[] energyImports,
            List<ImportTarget>[] chemicalImports,
            List<ImportTarget>[] sourceImports,
            Map<UUID, Integer> signalCache,
            Map<UUID, Boolean> dimensionalCache,
            Map<UUID, Integer> tierCache,
            boolean telemetryActive,
            TransferCapabilityCache capCache,
            boolean includeItemTransfers) {

        if (!sourceNode.isValidNode())
            return Long.MAX_VALUE;

        ServerLevel sourceLevel = (ServerLevel) sourceNode.level();
        long gameTime = sourceLevel.getGameTime();
        int redstoneSignal = signalCache.getOrDefault(sourceNode.getUUID(), 0);
        int sourceTier = tierCache.getOrDefault(sourceNode.getUUID(), 0);
        long minWakeDelta = Long.MAX_VALUE;

        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            ChannelData channel = sourceNode.getChannel(i);
            if (channel == null || !channel.isEnabled())
                continue;
            if (channel.getMode() != ChannelMode.EXPORT)
                continue;
            if (!canRunChannel(sourceNode, channel) || !CreateCompat.isResolved(sourceNode))
                continue;
            if (!isRedstoneActive(channel.getRedstoneMode(), redstoneSignal))
                continue;

            List<ImportTarget> targets = switch (channel.getType()) {
                case FLUID -> fluidImports[i];
                case ENERGY -> energyImports[i];
                case CHEMICAL -> chemicalImports[i];
                case SOURCE -> sourceImports[i];
                default -> itemImports[i];
            };

            if (targets == null || targets.isEmpty())
                continue;

            if (sourceNode.isMountedOnCreate()) {
                boolean storageAvailable = switch (channel.getType()) {
                    case ITEM -> capCache.findItemHandler(sourceNode, channel.getIoDirection()) != null;
                    case FLUID -> capCache.findFluidHandler(sourceNode, channel.getIoDirection()) != null;
                    default -> false;
                };
                if (!storageAvailable)
                    continue;
            }

            long cooldown = cooldownRemaining(sourceNode, channel, i, sourceTier, gameTime);
            if (cooldown > 0) {
                if (cooldown < minWakeDelta) minWakeDelta = cooldown;
                continue;
            }

            if (!includeItemTransfers && channel.getType() == ChannelType.ITEM)
                continue;

            int configuredBatch = getBatchLimit(channel.getType(), sourceTier);
            int effectiveBatchSize = Math.max(1, Math.min(channel.getBatchSize(), configuredBatch));

            int result = switch (channel.getType()) {
                case FLUID ->
                    transferFluids(sourceNode, sourceLevel, channel, i, targets, effectiveBatchSize, dimensionalCache, capCache);
                case ENERGY ->
                    transferEnergy(sourceNode, sourceLevel, channel, i, targets, effectiveBatchSize, dimensionalCache, capCache);
                case CHEMICAL ->
                    transferChemicals(sourceNode, sourceLevel, channel, i, targets, effectiveBatchSize, dimensionalCache, capCache);
                case SOURCE ->
                    transferSource(sourceNode, sourceLevel, channel, i, targets, effectiveBatchSize, dimensionalCache);
                default ->
                    transferItems(sourceNode, sourceLevel, channel, i, targets, effectiveBatchSize, dimensionalCache, capCache);
            };

            if (result < 0)
                continue;

            long wakeDelta = finishChannelAttempt(
                    sourceNode, channel, i, result, gameTime, sourceTier, telemetryActive);
            if (wakeDelta < minWakeDelta) minWakeDelta = wakeDelta;
        }

        return minWakeDelta;
    }

    public static long finishChannelAttempt(LogisticsNodeEntity node, ChannelData channel, int index, int result,
            long gameTime, int tier, boolean telemetryActive) {
        if (telemetryActive && result > 0) {
            channel.getTelemetry().record(result);
        }

        updateBackoff(node, channel, index, result > 0, gameTime, tier);
        if (result > 0) {
            return 0L;
        }
        return Math.max(1L, cooldownRemaining(node, channel, index, tier, gameTime));
    }

    public static long cooldownRemaining(LogisticsNodeEntity node, ChannelData channel, int index, int tier,
            long gameTime) {
        long lastRun = node.getLastExecution(index);
        boolean isInstantType = channel.getType() == ChannelType.ENERGY;
        long configuredDelay = isInstantType ? 1
                : Math.max(channel.getTickDelay(), NodeUpgradeData.getMinTickDelay(tier));
        float backoff = node.getBackoffTicks(index);
        boolean useBackoff = Config.backoffEnabled[channel.getType().ordinal()];
        long effectiveDelay = useBackoff ? Math.max(configuredDelay, (long) backoff) : configuredDelay;
        long elapsed = gameTime - lastRun;
        return Math.max(0L, effectiveDelay - elapsed);
    }

    public static int getBatchLimit(ChannelType type, int tier) {
        return switch (type) {
            case FLUID -> NodeUpgradeData.getFluidOperationCapMb(tier);
            case ENERGY -> NodeUpgradeData.getEnergyOperationCap(tier);
            case CHEMICAL -> NodeUpgradeData.getChemicalOperationCap(tier);
            case SOURCE -> NodeUpgradeData.getSourceOperationCap(tier);
            default -> NodeUpgradeData.getItemOperationCap(tier);
        };
    }

    private static void updateBackoff(LogisticsNodeEntity node, ChannelData channel, int index, boolean success,
            long gameTime, int tier) {
        node.setLastExecution(index, gameTime);
        boolean isInstantType = channel.getType() == ChannelType.ENERGY;
        int configuredDelay = isInstantType ? 1
                : Math.max(channel.getTickDelay(), NodeUpgradeData.getMinTickDelay(tier));

        if (success) {
            float curBackoff = node.getBackoffTicks(index);
            if (curBackoff > configuredDelay) {
                node.setBackoffTicks(index, Math.max(configuredDelay, curBackoff / BACKOFF_DECAY_DIVISOR));
            }
        } else if (Config.backoffEnabled[channel.getType().ordinal()]) {
            float maxBackoff = isInstantType ? BACKOFF_MAX_TICKS_ENERGY : (float) Config.backoffMaxTicks;
            float curBackoff = Math.max(node.getBackoffTicks(index), configuredDelay);
            if (curBackoff <= configuredDelay) {
                float nextThreshold = (configuredDelay + 1.05f) / BACKOFF_MULTIPLIER;
                node.setBackoffTicks(index, Math.min(maxBackoff, Math.max(configuredDelay + 0.1f, nextThreshold)));
            } else {
                node.setBackoffTicks(index, Math.min(maxBackoff, curBackoff * BACKOFF_MULTIPLIER));
            }
        }
    }

    private static List<ImportTarget> orderTargets(List<ImportTarget> targets, DistributionMode mode,
            LogisticsNodeEntity sourceNode) {
        if (targets.size() <= 1)
            return targets;

        switch (mode) {
            case NEAREST_FIRST -> {
                double sx = sourceNode.getX(), sy = sourceNode.getY(), sz = sourceNode.getZ();
                List<ImportTarget> sorted = new ArrayList<>(targets);
                sorted.sort(Comparator.comparingDouble(t -> t.node.distanceToSqr(sx, sy, sz)));
                return sorted;
            }
            case FARTHEST_FIRST -> {
                double sx = sourceNode.getX(), sy = sourceNode.getY(), sz = sourceNode.getZ();
                List<ImportTarget> sorted = new ArrayList<>(targets);
                sorted.sort(
                        (a, b) -> Double.compare(b.node.distanceToSqr(sx, sy, sz), a.node.distanceToSqr(sx, sy, sz)));
                return sorted;
            }
            default -> {
                return targets;
            }
        }
    }

    private static int transferItems(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, int channelIndex, List<ImportTarget> targets, int batchLimit,
            Map<UUID, Boolean> dimensionalCache, TransferCapabilityCache capCache) {
        return transferItems(sourceNode, sourceLevel, exportChannel, channelIndex, targets,
                batchLimit, dimensionalCache, capCache, Collections.emptyMap());
    }

    private static int transferItems(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, int channelIndex, List<ImportTarget> targets, int batchLimit,
            Map<UUID, Boolean> dimensionalCache, TransferCapabilityCache capCache,
            Map<Item, Integer> priorBatchMoved) {

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceNode.isMountedOnCreate() && !sourceLevel.isLoaded(sourcePos))
            return -1;
        IItemHandler sourceHandler = capCache.findItemHandler(sourceNode, exportChannel.getIoDirection());
        if (sourceHandler == null)
            return -1;

        ItemStack[] exportFilters = exportChannel.getFilterItems();
        boolean[] sourceAllowedSlots = null;
        FilterItemData.ReadCache filterReadCache = FilterItemData.createReadCache();

        ResolvedItemTargets resolved = resolveItemTargets(sourceNode, sourceLevel, exportChannel, targets,
                sourceHandler, dimensionalCache, capCache, filterReadCache);
        if (resolved.status() == ResolvedItemTargets.NO_REACHABLE
                || resolved.status() == ResolvedItemTargets.PAUSED) {
            return -1;
        }
        if (resolved.targets().isEmpty()) {
            return 0;
        }

        return executeMove(sourceHandler, resolved.targets(), batchLimit,
                exportFilters, exportChannel.getFilterMode(),
                sourceAllowedSlots,
                sourceLevel.registryAccess(),
                sourceLevel, sourcePos, filterReadCache,
                exportChannel.getDistributionMode() == DistributionMode.ROUND_ROBIN,
                null, priorBatchMoved);
    }

    public static int recoverItemChannel(LogisticsNetwork network, MinecraftServer server,
            TransferCapabilityCache capCache, UUID sourceNodeId, int channelIndex,
            int plannedShortfall, int committed, Map<Item, Integer> committedByItem) {
        ThreadGuard.requireServerThread();
        if (plannedShortfall <= 0 || channelIndex < 0 || channelIndex >= LogisticsNodeEntity.CHANNEL_COUNT) {
            return 0;
        }

        NetworkContext context = prepareNetwork(network, server);
        if (context == null) {
            return 0;
        }

        LogisticsNodeEntity sourceNode = null;
        for (LogisticsNodeEntity node : context.sortedNodes()) {
            if (node.getUUID().equals(sourceNodeId)) {
                sourceNode = node;
                break;
            }
        }
        if (sourceNode == null || !sourceNode.isValidNode()) {
            return 0;
        }

        ChannelData channel = sourceNode.getChannel(channelIndex);
        int signal = context.signalCache().getOrDefault(sourceNodeId, 0);
        if (channel == null || !channel.isEnabled()
                || channel.getMode() != ChannelMode.EXPORT || channel.getType() != ChannelType.ITEM
                || !canRunChannel(sourceNode, channel) || !CreateCompat.isResolved(sourceNode)
                || !isRedstoneActive(channel.getRedstoneMode(), signal)) {
            return 0;
        }

        List<ImportTarget> targets = context.itemImports()[channelIndex];
        if (targets == null || targets.isEmpty()) {
            return 0;
        }

        int tier = context.tierCache().getOrDefault(sourceNodeId, 0);
        int configuredBatch = getBatchLimit(ChannelType.ITEM, tier);
        int currentBatch = Math.max(1, Math.min(channel.getBatchSize(), configuredBatch));
        int recoveryLimit = Math.min(plannedShortfall, Math.max(0, currentBatch - committed));
        if (recoveryLimit == 0) {
            return 0;
        }

        int recovered = transferItems(
                sourceNode, (ServerLevel) sourceNode.level(), channel, channelIndex,
                targets, recoveryLimit, context.dimensionalCache(), capCache, committedByItem);
        return Math.max(0, recovered);
    }

    public static ResolvedItemTargets resolveItemTargets(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, List<ImportTarget> targets, IItemHandler sourceHandler,
            Map<UUID, Boolean> dimensionalCache, TransferCapabilityCache capCache,
            FilterItemData.ReadCache filterReadCache) {

        targets = orderTargets(targets, exportChannel.getDistributionMode(), sourceNode);
        BlockPos sourcePos = sourceNode.getAttachedPos();
        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        boolean anyReachable = false;
        boolean hasUsableTarget = false;
        boolean hasUnavailableMountedTarget = false;
        boolean hasStationaryTarget = false;
        List<ItemTransferTarget> reachableTargets = new ArrayList<>(targets.size());
        List<ImportTarget> resolvedRefs = new ArrayList<>(targets.size());
        ItemStack[] exportFilters = exportChannel.getFilterItems();

        for (ImportTarget target : targets) {
            if (target.node == sourceNode)
                continue;
            if (!target.node.isValidNode())
                continue;
            if (!canReach(sourceNode, target.node, sourceDimensional, dimensionalCache))
                continue;

            anyReachable = true;
            hasStationaryTarget |= !target.node.isMountedOnCreate();
            ServerLevel targetLevel = (ServerLevel) target.node.level();
            BlockPos targetPos = target.node.getAttachedPos();
            if (!target.node.isMountedOnCreate() && !targetLevel.isLoaded(targetPos))
                continue;
            if (!sourceNode.isMountedOnCreate() && !target.node.isMountedOnCreate()
                    && isSameItemStorage(sourceLevel, sourcePos, targetLevel, targetPos))
                continue;

            IItemHandler targetHandler = capCache.findItemHandler(target.node, target.channel.getIoDirection());
            if (targetHandler == null) {
                hasUnavailableMountedTarget |= target.node.isMountedOnCreate();
                continue;
            }
            hasUsableTarget = true;
            if (sourceHandler == targetHandler)
                continue;

            ItemStack[] importFilters = target.channel.getFilterItems();
            boolean[] targetAllowedSlots = null;
            boolean hasImportSlotMapping = FilterLogic.hasConfiguredSlotMapping(importFilters, filterReadCache);
            IItemHandler bulkHandler = hasImportSlotMapping
                    ? null
                    : capCache.findBulkItemHandler(target.node, targetHandler);

            reachableTargets.add(new ItemTransferTarget(
                    targetHandler,
                    bulkHandler,
                    importFilters,
                    target.channel.getFilterMode(),
                    TransferAmountRules.collect(exportFilters, importFilters, filterReadCache),
                    FilterLogic.hasConfiguredItemNbtFilter(importFilters, filterReadCache),
                    targetAllowedSlots,
                    hasImportSlotMapping));
            resolvedRefs.add(target);
        }
        if (!anyReachable) {
            return new ResolvedItemTargets(reachableTargets, resolvedRefs, ResolvedItemTargets.NO_REACHABLE);
        }
        if (shouldPauseForUnavailableMountedTargets(hasUsableTarget, hasUnavailableMountedTarget,
                hasStationaryTarget)) {
            return new ResolvedItemTargets(reachableTargets, resolvedRefs, ResolvedItemTargets.PAUSED);
        }
        return new ResolvedItemTargets(reachableTargets, resolvedRefs, ResolvedItemTargets.OK);
    }

    private static int transferFluids(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, int channelIndex, List<ImportTarget> targets, int batchLimitMb,
            Map<UUID, Boolean> dimensionalCache, TransferCapabilityCache capCache) {

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceNode.isMountedOnCreate() && !sourceLevel.isLoaded(sourcePos))
            return -1;
        IFluidHandler sourceHandler = capCache.findFluidHandler(sourceNode, exportChannel.getIoDirection());
        if (sourceHandler == null)
            return -1;

        targets = orderTargets(targets, exportChannel.getDistributionMode(), sourceNode);
        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        int remaining = batchLimitMb;
        boolean anyReachable = false;
        boolean hasUsableTarget = false;
        boolean hasUnavailableMountedTarget = false;
        boolean hasStationaryTarget = false;
        FilterItemData.ReadCache filterReadCache = FilterItemData.createReadCache();

        for (ImportTarget target : targets) {
            if (remaining <= 0)
                break;
            if (target.node == sourceNode)
                continue;
            if (!target.node.isValidNode())
                continue;
            if (!canReach(sourceNode, target.node, sourceDimensional, dimensionalCache))
                continue;

            anyReachable = true;
            hasStationaryTarget |= !target.node.isMountedOnCreate();
            ServerLevel targetLevel = (ServerLevel) target.node.level();
            BlockPos targetPos = target.node.getAttachedPos();
            if (!target.node.isMountedOnCreate() && !targetLevel.isLoaded(targetPos))
                continue;

            IFluidHandler targetHandler = capCache.findFluidHandler(target.node, target.channel.getIoDirection());
            if (targetHandler == null) {
                hasUnavailableMountedTarget |= target.node.isMountedOnCreate();
                continue;
            }
            hasUsableTarget = true;
            if (sourceHandler == targetHandler)
                continue;

            int filled = executeFluidMove(sourceHandler, targetHandler, remaining,
                    exportChannel.getFilterItems(), exportChannel.getFilterMode(),
                    target.channel.getFilterItems(), target.channel.getFilterMode(),
                    sourceLevel.registryAccess(), filterReadCache);
            if (filled > 0)
                remaining -= filled;
        }

        if (!anyReachable || shouldPauseForUnavailableMountedTargets(hasUsableTarget, hasUnavailableMountedTarget,
                hasStationaryTarget))
            return -1;
        return batchLimitMb - remaining;
    }

    private static int transferEnergy(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, int channelIndex, List<ImportTarget> targets, int batchLimitRF,
            Map<UUID, Boolean> dimensionalCache, TransferCapabilityCache capCache) {

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceLevel.isLoaded(sourcePos))
            return -1;
        IEnergyStorage sourceHandler = capCache.findEnergyHandler(sourceLevel, sourcePos, exportChannel.getIoDirection());
        if (sourceHandler == null || !sourceHandler.canExtract())
            return -1;

        targets = orderTargets(targets, exportChannel.getDistributionMode(), sourceNode);
        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        int remaining = batchLimitRF;
        boolean anyReachable = false;

        for (ImportTarget target : targets) {
            if (remaining <= 0)
                break;
            if (target.node == sourceNode)
                continue;
            if (!target.node.isValidNode())
                continue;
            if (!canReach(sourceNode, target.node, sourceDimensional, dimensionalCache))
                continue;

            anyReachable = true;
            ServerLevel targetLevel = (ServerLevel) target.node.level();
            BlockPos targetPos = target.node.getAttachedPos();
            if (!targetLevel.isLoaded(targetPos))
                continue;

            IEnergyStorage targetHandler = capCache.findEnergyHandler(targetLevel, targetPos, target.channel.getIoDirection());
            if (targetHandler == null || !targetHandler.canReceive())
                continue;

            int moved = executeEnergyMove(sourceHandler, targetHandler, remaining);
            if (moved > 0)
                remaining -= moved;
        }

        if (!anyReachable)
            return -1;
        return batchLimitRF - remaining;
    }

    private static int transferChemicals(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, int channelIndex, List<ImportTarget> targets, int batchLimit,
            Map<UUID, Boolean> dimensionalCache, TransferCapabilityCache capCache) {

        if (!MekanismCompat.isLoaded()) {
            if (Config.debugMode)
                LOGGER.debug("[Chemical] Mekanism not loaded, skipping");
            return -1;
        }

        if (!NodeUpgradeData.hasMekanismChemicalUpgrade(sourceNode)) {
            if (Config.debugMode)
                LOGGER.debug("[Chemical] No chemical upgrade on source node, skipping");
            return -1;
        }

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceLevel.isLoaded(sourcePos))
            return -1;

        IChemicalHandler sourceHandler = capCache.findChemicalHandler(sourceLevel, sourcePos,
                exportChannel.getIoDirection());
        if (sourceHandler == null)
            return -1;

        targets = orderTargets(targets, exportChannel.getDistributionMode(), sourceNode);
        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        int remaining = batchLimit;
        boolean anyReachable = false;
        FilterItemData.ReadCache filterReadCache = FilterItemData.createReadCache();

        for (ImportTarget target : targets) {
            if (remaining <= 0)
                break;
            if (target.node() == sourceNode)
                continue;
            if (!target.node().isValidNode())
                continue;
            if (!canReach(sourceNode, target.node(), sourceDimensional, dimensionalCache))
                continue;

            ServerLevel targetLevel = (ServerLevel) target.node().level();
            BlockPos targetPos = target.node().getAttachedPos();
            if (!targetLevel.isLoaded(targetPos))
                continue;

            IChemicalHandler targetHandler = capCache.findChemicalHandler(targetLevel, targetPos,
                    target.channel().getIoDirection());
            if (targetHandler == null)
                continue;

            anyReachable = true;
            long moved = ChemicalTransferHelper.transferBetween(
                    sourceHandler, targetHandler, remaining,
                    exportChannel.getFilterItems(), exportChannel.getFilterMode(),
                    target.channel().getFilterItems(), target.channel().getFilterMode(),
                    filterReadCache);
            if (Config.debugMode)
                LOGGER.debug("[Chemical] Transfer {} -> {}: moved={}, batch={}",
                        sourcePos, targetPos, moved, remaining);
            if (moved > 0)
                remaining -= (int) moved;
        }

        if (Config.debugMode && !anyReachable)
            LOGGER.debug("[Chemical] No reachable targets for {}", sourcePos);
        if (!anyReachable)
            return -1;
        return batchLimit - remaining;
    }

    private static int transferSource(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, int channelIndex, List<ImportTarget> targets, int batchLimit,
            Map<UUID, Boolean> dimensionalCache) {

        if (!ArsCompat.isLoaded()) {
            if (Config.debugMode)
                LOGGER.debug("[Source] Ars Nouveau not loaded, skipping");
            return -1;
        }

        if (!NodeUpgradeData.hasArsSourceUpgrade(sourceNode)) {
            if (Config.debugMode)
                LOGGER.debug("[Source] No source upgrade on source node, skipping");
            return -1;
        }

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceLevel.isLoaded(sourcePos))
            return -1;

        targets = orderTargets(targets, exportChannel.getDistributionMode(), sourceNode);
        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        int remaining = batchLimit;
        boolean anyReachable = false;

        for (ImportTarget target : targets) {
            if (remaining <= 0)
                break;
            if (target.node() == sourceNode)
                continue;
            if (!target.node().isValidNode())
                continue;
            if (!canReach(sourceNode, target.node(), sourceDimensional, dimensionalCache))
                continue;

            anyReachable = true;
            ServerLevel targetLevel = (ServerLevel) target.node().level();
            BlockPos targetPos = target.node().getAttachedPos();
            if (!targetLevel.isLoaded(targetPos))
                continue;

            int moved = SourceTransferHelper.transferBetween(
                    sourceLevel, sourcePos, targetLevel, targetPos, remaining);
            if (Config.debugMode)
                LOGGER.debug("[Source] Transfer {} -> {}: moved={}, batch={}",
                        sourcePos, targetPos, moved, batchLimit);
            if (moved > 0)
                remaining -= moved;
        }

        if (!anyReachable)
            return -1;
        return batchLimit - remaining;
    }

    private static boolean canReach(LogisticsNodeEntity source, LogisticsNodeEntity target, boolean sourceDim,
            Map<UUID, Boolean> dimCache) {
        if (source.level().dimension().equals(target.level().dimension()))
            return true;
        return sourceDim && dimCache.getOrDefault(target.getUUID(), false);
    }

    public static boolean isSameItemStorage(ServerLevel sourceLevel, BlockPos sourcePos,
            ServerLevel targetLevel, BlockPos targetPos) {
        if (!sourceLevel.dimension().equals(targetLevel.dimension()))
            return false;
        if (sourcePos.equals(targetPos))
            return true;

        BlockState sourceState = sourceLevel.getBlockState(sourcePos);
        BlockState targetState = targetLevel.getBlockState(targetPos);
        if (!(sourceState.getBlock() instanceof ChestBlock) || !(targetState.getBlock() instanceof ChestBlock))
            return false;
        if (sourceState.getValue(ChestBlock.TYPE) == ChestType.SINGLE
                || targetState.getValue(ChestBlock.TYPE) == ChestType.SINGLE)
            return false;

        return sourcePos.relative(ChestBlock.getConnectedDirection(sourceState)).equals(targetPos)
                && targetPos.relative(ChestBlock.getConnectedDirection(targetState)).equals(sourcePos);
    }

    public static int executeMove(IItemHandler source, List<ItemTransferTarget> targets, int limit,
            ItemStack[] exportFilters, FilterMode exportFilterMode,
            boolean[] sourceAllowedSlots,
            HolderLookup.Provider provider,
            @Nullable ServerLevel sourceLevel, @Nullable BlockPos sourcePos,
            FilterItemData.ReadCache filterReadCache,
            boolean roundRobin,
            @Nullable MoveRecorder recorder) {
        return executeMove(source, targets, limit, exportFilters, exportFilterMode,
                sourceAllowedSlots, provider, sourceLevel, sourcePos, filterReadCache,
                roundRobin, recorder, Collections.emptyMap());
    }

    public static int executeMove(IItemHandler source, List<ItemTransferTarget> targets, int limit,
            ItemStack[] exportFilters, FilterMode exportFilterMode,
            boolean[] sourceAllowedSlots,
            HolderLookup.Provider provider,
            @Nullable ServerLevel sourceLevel, @Nullable BlockPos sourcePos,
            FilterItemData.ReadCache filterReadCache,
            boolean roundRobin,
            @Nullable MoveRecorder recorder,
            Map<Item, Integer> priorBatchMoved) {

        int remaining = limit;
        boolean hasExportNbtFilter = FilterLogic.hasConfiguredItemNbtFilter(exportFilters, filterReadCache);
        boolean hasAnyImportNbtFilter = false;
        for (ItemTransferTarget target : targets) {
            if (target.hasItemNbtFilter()) {
                hasAnyImportNbtFilter = true;
                break;
            }
        }
        boolean hasNbtFilter = hasExportNbtFilter || hasAnyImportNbtFilter;

        boolean anyAmountConstraints = false;
        for (ItemTransferTarget t : targets) {
            if (t.constraints().hasExportThreshold() || t.constraints().hasImportThreshold()
                    || t.constraints().hasPerEntryAmounts()) {
                anyAmountConstraints = true;
                break;
            }
        }
        Map<Item, Integer> sourceItemCounts = anyAmountConstraints ? TransferAmountRules.countItems(source) : null;
        Map<Item, Integer> batchMoved = anyAmountConstraints && !roundRobin
                ? new HashMap<>(priorBatchMoved)
                : null;
        List<Map<Item, Integer>> targetBatchMoved = null;
        List<Map<Item, Integer>> targetItemCounts = null;
        if (anyAmountConstraints) {
            if (roundRobin) {
                targetBatchMoved = new ArrayList<>(targets.size());
            }
            targetItemCounts = new ArrayList<>(targets.size());
            for (ItemTransferTarget t : targets) {
                if (roundRobin) {
                    targetBatchMoved.add(new HashMap<>());
                }
                targetItemCounts.add(
                        (t.constraints().hasImportThreshold() || t.constraints().hasPerEntryAmounts())
                                ? TransferAmountRules.countItems(t.handler())
                                : null);
            }
        }

        boolean movedAny;
        boolean[] openTargets = new boolean[targets.size()];
        Arrays.fill(openTargets, true);
        int openTargetCount = targets.size();
        BulkInsertRejectionCache bulkInsertRejections = null;

        // Serialize each source slot once across the target loop
        CompoundTag[] slotComponents = hasNbtFilter ? new CompoundTag[source.getSlots()] : null;
        boolean[] slotComponentsCached = hasNbtFilter ? new boolean[source.getSlots()] : null;

        while (remaining > 0 && openTargetCount > 0) {
            movedAny = false;
            int targetsLeft = openTargetCount;

            for (int targetIndex = 0; targetIndex < targets.size() && remaining > 0; targetIndex++) {
                if (!openTargets[targetIndex]) {
                    continue;
                }

                ItemTransferTarget target = targets.get(targetIndex);
                IItemHandler bulkHandler = target.bulkHandler();
                boolean movedForTarget = false;
                int targetRemaining = roundRobin ? Math.ceilDiv(remaining, targetsLeft) : remaining;
                targetsLeft--;

                for (int slot = 0; slot < source.getSlots() && remaining > 0 && targetRemaining > 0; slot++) {
                    if (sourceAllowedSlots != null
                            && (slot >= sourceAllowedSlots.length || !sourceAllowedSlots[slot])) {
                        continue;
                    }

                    ItemStack extracted = source.extractItem(slot, Math.min(remaining, targetRemaining), true);
                    if (extracted.isEmpty() || extracted.is(ModTags.RESOURCE_BLACKLIST_ITEMS)) {
                        continue;
                    }

                    CompoundTag candidateComponents = null;
                    if (provider != null && hasNbtFilter) {
                        if (!slotComponentsCached[slot]) {
                            slotComponents[slot] = NbtFilterData.getSerializedComponents(extracted, provider);
                            slotComponentsCached[slot] = true;
                        }
                        candidateComponents = slotComponents[slot];
                    }

                    if (provider != null) {
                        if (!FilterLogic.matchesItemInSlot(exportFilters, exportFilterMode, extracted, provider,
                                candidateComponents, filterReadCache, slot)) {
                            continue;
                        }
                    }

                    boolean[] importAllowedSlots = target.allowedSlots();
                    if (provider != null) {
                        if (target.hasImportSlotMapping()) {
                            importAllowedSlots = computeImportAllowedSlots(target.handler(), target.importFilters(),
                                    target.importFilterMode(), extracted, provider, candidateComponents,
                                    filterReadCache);
                            if (importAllowedSlots == null) {
                                continue;
                            }
                        } else if (!FilterLogic.matchesItemInSlot(target.importFilters(), target.importFilterMode(),
                                extracted, provider, candidateComponents, filterReadCache, -1)) {
                            continue;
                        }
                    }

                    int allowedByAmount;
                    if (!anyAmountConstraints
                            || (!target.constraints().hasExportThreshold() && !target.constraints().hasImportThreshold()
                                    && !target.constraints().hasPerEntryAmounts())) {
                        allowedByAmount = extracted.getCount();
                    } else {
                        allowedByAmount = TransferAmountRules.allowedItems(extracted, target.constraints(),
                                sourceItemCounts, targetItemCounts.get(targetIndex));
                        if (target.constraints().hasPerEntryAmounts() && provider != null) {
                            int perEntry = TransferAmountRules.perEntryItemAmount(extracted, exportFilters,
                                    target.importFilters(), sourceItemCounts,
                                    targetItemCounts.get(targetIndex), provider, candidateComponents,
                                    filterReadCache);
                            if (perEntry >= 0) {
                                allowedByAmount = Math.min(allowedByAmount, perEntry);
                            }
                            int batchLimit = TransferAmountRules.perEntryItemBatch(extracted, exportFilters, provider,
                                    candidateComponents, filterReadCache);
                            if (batchLimit > 0) {
                                Map<Item, Integer> movedByItem = roundRobin
                                        ? targetBatchMoved.get(targetIndex)
                                        : batchMoved;
                                int alreadyMoved = movedByItem.getOrDefault(extracted.getItem(), 0);
                                allowedByAmount = Math.min(allowedByAmount, Math.max(0, batchLimit - alreadyMoved));
                            }
                        }
                    }
                    if (allowedByAmount <= 0) {
                        continue;
                    }

                    int allowed = Math.min(extracted.getCount(), allowedByAmount);
                    if (allowed <= 0) {
                        continue;
                    }

                    ItemStack simulatedInsert = extracted.copyWithCount(allowed);
                    ItemStack simRemainder;
                    if (bulkHandler != null) {
                        if (bulkInsertRejections == null) {
                            bulkInsertRejections = new BulkInsertRejectionCache(
                                    (handler, stack) -> SophisticatedCoreCompat.insertItem(handler, stack, true));
                        }
                        simRemainder = bulkInsertRejections.simulate(bulkHandler, simulatedInsert);
                    } else {
                        simRemainder = insertItemWithAllowedSlots(target.handler(), null,
                                simulatedInsert, true, importAllowedSlots);
                    }
                    int acceptableCount = allowed - simRemainder.getCount();
                    if (acceptableCount <= 0) {
                        continue;
                    }

                    ItemStack toMove = source.extractItem(slot, acceptableCount, false);
                    if (toMove.isEmpty()) {
                        continue;
                    }

                    ItemStack uninserted = insertItemWithAllowedSlots(target.handler(), bulkHandler,
                            toMove, false, importAllowedSlots);
                    int targetAccepted = toMove.getCount() - uninserted.getCount();
                    if (targetAccepted > 0 && bulkHandler != null) {
                        bulkInsertRejections.clear(bulkHandler);
                    }
                    int droppedToWorld = 0;

                    if (!uninserted.isEmpty()) {
                        ItemStack stillLeft = source.insertItem(slot, uninserted, false);
                        if (!stillLeft.isEmpty()) {
                            for (int fallback = 0; fallback < source.getSlots() && !stillLeft.isEmpty(); fallback++) {
                                stillLeft = source.insertItem(fallback, stillLeft, false);
                            }
                            if (!stillLeft.isEmpty()) {
                                ItemStack forcedRemainder = insertItemWithAllowedSlots(target.handler(),
                                        bulkHandler, stillLeft, false, importAllowedSlots);
                                int forcedIn = stillLeft.getCount() - forcedRemainder.getCount();
                                targetAccepted += forcedIn;
                                if (forcedIn > 0 && bulkHandler != null) {
                                    bulkInsertRejections.clear(bulkHandler);
                                }
                                if (!forcedRemainder.isEmpty()) {
                                    LOGGER.error("ITEM VOIDING PREVENTED: Could not return {} to source or fit into "
                                            + "target slot mask. Dropping at source pos {}.",
                                            forcedRemainder, sourcePos);
                                    if (sourceLevel != null && sourcePos != null) {
                                        droppedToWorld = forcedRemainder.getCount();
                                        Block.popResource(sourceLevel, sourcePos, forcedRemainder);
                                    }
                                }
                            }
                        }
                    }

                    int sourceLost = targetAccepted + droppedToWorld;
                    if (sourceLost > 0) {
                        if (recorder != null) {
                            recorder.record(slot, targetIndex, toMove.copyWithCount(sourceLost), importAllowedSlots);
                        }
                        movedAny = true;
                        movedForTarget = true;
                        remaining -= sourceLost;
                        targetRemaining -= sourceLost;

                        if (anyAmountConstraints) {
                            Item movedItem = extracted.getItem();
                            if (sourceItemCounts != null) {
                                sourceItemCounts.merge(movedItem, -sourceLost, Integer::sum);
                            }
                            Map<Item, Integer> tgtCache = targetItemCounts.get(targetIndex);
                            if (tgtCache != null && targetAccepted > 0) {
                                tgtCache.merge(movedItem, targetAccepted, Integer::sum);
                            }
                            Map<Item, Integer> movedByItem = roundRobin
                                    ? targetBatchMoved.get(targetIndex)
                                    : batchMoved;
                            movedByItem.merge(movedItem, sourceLost, Integer::sum);
                        }

                        if (slotComponentsCached != null) {
                            slotComponentsCached[slot] = false;
                        }

                        if (!roundRobin) {
                            break;
                        }
                    }
                }

                if (!movedForTarget) {
                    openTargets[targetIndex] = false;
                    openTargetCount--;
                }
            }

            if (!movedAny) {
                break;
            }
        }
        return limit - remaining;
    }

    public static int commitSingleMove(IItemHandler source, IItemHandler target,
            TransferPlan.ItemMove move, LogisticsNodeEntity sourceNode) {
        return commitSingleMove(source, target, null, move, sourceNode);
    }

    public static int commitSingleMove(IItemHandler source, IItemHandler target,
            @Nullable IItemHandler bulkTarget, TransferPlan.ItemMove move, LogisticsNodeEntity sourceNode) {
        ThreadGuard.requireServerThread();

        if (move.sourceSlot() < 0 || move.sourceSlot() >= source.getSlots() || move.amount() <= 0) {
            return 0;
        }

        boolean[] targetSlotMask = move.targetSlotMask();
        if (targetSlotMask != null
                && (bulkTarget != null || targetSlotMask.length != target.getSlots())) {
            return 0;
        }

        ItemStack available = source.extractItem(move.sourceSlot(), move.amount(), true);
        if (available.isEmpty()) {
            return 0;
        }
        if (available.getItem() != move.expectedItem()
                || !available.getComponents().equals(move.expectedComponents())) {
            return 0;
        }

        int acceptable;
        if (bulkTarget != null && targetSlotMask == null) {
            acceptable = available.getCount();
        } else {
            ItemStack simRemainder = insertItemWithAllowedSlots(target, bulkTarget,
                    available.copyWithCount(available.getCount()), true, targetSlotMask);
            acceptable = available.getCount() - simRemainder.getCount();
        }
        if (acceptable <= 0) {
            return 0;
        }

        ItemStack toMove = source.extractItem(move.sourceSlot(), acceptable, false);
        if (toMove.isEmpty()) {
            return 0;
        }

        ItemStack uninserted = insertItemWithAllowedSlots(target, bulkTarget, toMove, false, targetSlotMask);
        int accepted = toMove.getCount() - uninserted.getCount();

        if (!uninserted.isEmpty()) {
            ItemStack stillLeft = source.insertItem(move.sourceSlot(), uninserted, false);
            for (int fallback = 0; fallback < source.getSlots() && !stillLeft.isEmpty(); fallback++) {
                stillLeft = source.insertItem(fallback, stillLeft, false);
            }
            if (!stillLeft.isEmpty()) {
                ItemStack forcedRemainder = insertItemWithAllowedSlots(target, bulkTarget, stillLeft, false,
                        targetSlotMask);
                accepted += stillLeft.getCount() - forcedRemainder.getCount();
                if (!forcedRemainder.isEmpty() && sourceNode.level() instanceof ServerLevel level) {
                    LOGGER.error("ITEM VOIDING PREVENTED: could not return {} to source or target. Dropping at {}.",
                            forcedRemainder, sourceNode.getAttachedPos());
                    Block.popResource(level, sourceNode.getAttachedPos(), forcedRemainder);
                    accepted += forcedRemainder.getCount();
                }
            }
        }

        return accepted;
    }

    private static boolean[] computeImportAllowedSlots(IItemHandler handler, ItemStack[] importFilters,
            FilterMode importFilterMode, ItemStack candidate, HolderLookup.Provider provider,
            @Nullable CompoundTag candidateComponents, @Nullable FilterItemData.ReadCache filterReadCache) {
        int size = handler.getSlots();
        boolean[] mask = new boolean[size];
        boolean any = false;
        for (int ds = 0; ds < size; ds++) {
            if (FilterLogic.matchesItemInSlot(importFilters, importFilterMode, candidate, provider,
                    candidateComponents, filterReadCache, ds)) {
                mask[ds] = true;
                any = true;
            }
        }
        return any ? mask : null;
    }

    private static ItemStack insertItemWithAllowedSlots(IItemHandler handler, @Nullable IItemHandler bulkHandler,
            ItemStack stack, boolean simulate, boolean[] allowedSlots) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (allowedSlots == null) {
            if (bulkHandler != null) {
                return SophisticatedCoreCompat.insertItem(bulkHandler, stack, simulate);
            }
            return ItemHandlerHelper.insertItemStacked(handler, stack, simulate);
        }
        if (handler instanceof IItemHandlerModifiable modifiable) {
            return insertItemStrictAllowedSlots(modifiable, stack, simulate, allowedSlots);
        }

        ItemStack remaining = stack.copy();

        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            if (slot >= allowedSlots.length || !allowedSlots[slot]) {
                continue;
            }
            ItemStack slotStack = handler.getStackInSlot(slot);
            if (slotStack.isEmpty()) {
                continue;
            }
            if (!ItemStack.isSameItemSameComponents(slotStack, remaining)) {
                continue;
            }
            remaining = handler.insertItem(slot, remaining, simulate);
        }

        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            if (slot >= allowedSlots.length || !allowedSlots[slot]) {
                continue;
            }
            ItemStack slotStack = handler.getStackInSlot(slot);
            if (!slotStack.isEmpty()) {
                continue;
            }
            remaining = handler.insertItem(slot, remaining, simulate);
        }

        return remaining;
    }

    private static ItemStack insertItemStrictAllowedSlots(IItemHandlerModifiable handler, ItemStack stack,
            boolean simulate, boolean[] allowedSlots) {
        ItemStack remaining = stack.copy();

        for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
            boolean mergePass = pass == 0;

            for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                if (slot >= allowedSlots.length || !allowedSlots[slot]) {
                    continue;
                }

                ItemStack slotStack = handler.getStackInSlot(slot);
                boolean slotEmpty = slotStack.isEmpty();

                if (mergePass && slotEmpty) {
                    continue;
                }
                if (!mergePass && !slotEmpty) {
                    continue;
                }
                if (!slotEmpty && !ItemStack.isSameItemSameComponents(slotStack, remaining)) {
                    continue;
                }
                if (!handler.isItemValid(slot, remaining)) {
                    continue;
                }

                int slotLimit = Math.min(handler.getSlotLimit(slot), remaining.getMaxStackSize());
                if (!slotEmpty) {
                    slotLimit = Math.min(slotLimit, slotStack.getMaxStackSize());
                }

                int currentCount = slotEmpty ? 0 : slotStack.getCount();
                int space = slotLimit - currentCount;
                if (space <= 0) {
                    continue;
                }

                int toInsert = Math.min(space, remaining.getCount());
                if (toInsert <= 0) {
                    continue;
                }

                if (!simulate) {
                    if (slotEmpty) {
                        handler.setStackInSlot(slot, remaining.copyWithCount(toInsert));
                    } else {
                        ItemStack updated = slotStack.copy();
                        updated.grow(toInsert);
                        handler.setStackInSlot(slot, updated);
                    }
                }

                remaining.shrink(toInsert);
            }
        }

        return remaining;
    }

    private static int executeFluidMove(IFluidHandler source, IFluidHandler target, int limitMb,
            ItemStack[] exportFilters, FilterMode exportFilterMode,
            ItemStack[] importFilters, FilterMode importFilterMode,
            HolderLookup.Provider provider, @Nullable FilterItemData.ReadCache filterReadCache) {

        int remaining = limitMb;
        TransferAmountRules.Constraints amountConstraints = TransferAmountRules.collect(exportFilters, importFilters,
                filterReadCache);

        for (int tank = 0; tank < source.getTanks() && remaining > 0; tank++) {
            FluidStack tankFluid = source.getFluidInTank(tank);
            if (tankFluid.isEmpty())
                continue;
            if (tankFluid.getFluid().builtInRegistryHolder().is(ModTags.RESOURCE_BLACKLIST_FLUIDS))
                continue;

            int requestFromTank = Math.min(remaining, tankFluid.getAmount());
            FluidStack simulated = source.drain(tankFluid.copyWithAmount(requestFromTank),
                    IFluidHandler.FluidAction.SIMULATE);
            if (simulated.isEmpty())
                continue;

            if (provider != null) {
                if (!FilterLogic.matchesFluid(exportFilters, exportFilterMode, simulated, provider, filterReadCache))
                    continue;
                if (!FilterLogic.matchesFluid(importFilters, importFilterMode, simulated, provider, filterReadCache))
                    continue;
            }

            int allowedByAmount = TransferAmountRules.allowedFluids(source, target, simulated,
                    amountConstraints);
            if (amountConstraints.hasPerEntryAmounts()) {
                int perEntry = TransferAmountRules.perEntryFluidAmount(simulated, exportFilters, importFilters,
                        source, target, filterReadCache);
                if (perEntry >= 0) {
                    allowedByAmount = Math.min(allowedByAmount, perEntry);
                }
            }
            if (allowedByAmount <= 0)
                continue;

            int request = Math.min(simulated.getAmount(), Math.min(remaining, allowedByAmount));
            int perEntryBatch = TransferAmountRules.perEntryFluidBatch(simulated, exportFilters, importFilters,
                    filterReadCache);
            if (perEntryBatch > 0) {
                request = Math.min(request, perEntryBatch);
            }
            int accepted = target.fill(simulated.copyWithAmount(request), IFluidHandler.FluidAction.SIMULATE);
            if (accepted <= 0)
                continue;

            FluidStack drained = source.drain(simulated.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
            if (drained.isEmpty())
                continue;

            int filled = target.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            if (filled < drained.getAmount()) {
                int rollbackAmount = drained.getAmount() - filled;
                FluidStack rollback = drained.copyWithAmount(rollbackAmount);
                int returned = source.fill(rollback, IFluidHandler.FluidAction.EXECUTE);
                if (returned < rollbackAmount) {
                    LOGGER.error("FLUID VOIDING: Source rejected rollback of {} mB ({}). {} mB lost.",
                            rollbackAmount - returned, drained.getFluid(), rollbackAmount - returned);
                }
            }

            if (filled > 0) {
                remaining -= filled;
            }
        }
        return limitMb - remaining;
    }

    private static int executeEnergyMove(IEnergyStorage source, IEnergyStorage target, int limitRF) {
        int extracted = source.extractEnergy(limitRF, true);
        if (extracted <= 0)
            return 0;

        int accepted = target.receiveEnergy(extracted, true);
        if (accepted <= 0)
            return 0;

        int toMove = Math.min(extracted, accepted);
        int actuallyExtracted = source.extractEnergy(toMove, false);
        if (actuallyExtracted <= 0)
            return 0;

        int received = target.receiveEnergy(actuallyExtracted, false);
        if (received < actuallyExtracted) {
            int rollbackAmount = actuallyExtracted - received;
            int returned = source.receiveEnergy(rollbackAmount, false);
            if (returned < rollbackAmount) {
                LOGGER.error("ENERGY VOIDING: Source rejected rollback of {} RF. {} RF lost.",
                        rollbackAmount - returned, rollbackAmount - returned);
            }
        }
        return received;
    }

    public static LogisticsNodeEntity findNode(MinecraftServer server, UUID nodeId,
            @Nullable ResourceKey<Level> cachedDim) {
        if (cachedDim != null) {
            ServerLevel level = server.getLevel(cachedDim);
            if (level == null)
                return null;
            return level.getEntity(nodeId) instanceof LogisticsNodeEntity node ? node : null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(nodeId) instanceof LogisticsNodeEntity node)
                return node;
        }
        return null;
    }

    public static boolean isRedstoneActive(RedstoneMode mode, int signalStrength) {
        return switch (mode) {
            case ALWAYS_ON -> true;
            case ALWAYS_OFF -> false;
            case HIGH -> signalStrength > 0;
            case LOW -> signalStrength == 0;
        };
    }

    static boolean canRunChannel(boolean mounted, ChannelType type, RedstoneMode redstoneMode) {
        if (!mounted) {
            return true;
        }
        return redstoneMode == RedstoneMode.ALWAYS_ON
                && (type == ChannelType.ITEM || type == ChannelType.FLUID);
    }

    public static boolean canRunChannel(LogisticsNodeEntity node, ChannelData channel) {
        return canRunChannel(node.isMountedOnCreate(), channel.getType(), channel.getRedstoneMode());
    }

    static boolean shouldPauseForUnavailableMountedTargets(boolean hasUsableTarget,
            boolean hasUnavailableMountedTarget, boolean hasStationaryTarget) {
        return !hasUsableTarget && hasUnavailableMountedTarget && !hasStationaryTarget;
    }
}
