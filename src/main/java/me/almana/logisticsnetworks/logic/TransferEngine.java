package me.almana.logisticsnetworks.logic;

import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.logic.async.SnapshotItemHandler;
import me.almana.logisticsnetworks.logic.async.TransferPlan;
import me.almana.logisticsnetworks.logic.async.ThreadGuard;
import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.data.NodeRef;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.NbtFilterData;
import me.almana.logisticsnetworks.integration.ars.ArsCompat;
import me.almana.logisticsnetworks.integration.ars.SourceTransferHelper;
import me.almana.logisticsnetworks.integration.mekanism.ChemicalTransferHelper;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.integration.sophisticated.SophisticatedCoreCompat;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.BooleanSupplier;

public class TransferEngine {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float BACKOFF_MULTIPLIER = 1.3f;
    private static final float BACKOFF_DECAY_DIVISOR = 3f;
    private static final float BACKOFF_MAX_TICKS_ENERGY = 5f;

    public record ImportTarget(LogisticsNodeEntity node, ChannelData channel, int channelIndex) {
    }

    public record ItemTransferTarget(ResourceHandler<ItemResource> handler, ItemStack[] importFilters,
            FilterMode importFilterMode, TransferAmountRules.Constraints constraints, boolean hasItemNbtFilter,
            boolean[] allowedSlots, boolean hasImportSlotMapping) {
    }

    public static long processNetwork(LogisticsNetwork network, MinecraftServer server) {
        return processNetwork(network, server, true);
    }

    public static long processSynchronousNonItems(LogisticsNetwork network, MinecraftServer server) {
        ThreadGuard.requireServerThread();
        return processNetwork(network, server, false);
    }

    private static long processNetwork(LogisticsNetwork network, MinecraftServer server, boolean includeItems) {
        if (network == null || server == null)
            return Long.MAX_VALUE;

        NetworkContext context = prepareNetwork(network, server);
        if (context == null) return Long.MAX_VALUE;
        List<LogisticsNodeEntity> sortedNodes = context.sortedNodes();
        Map<UUID, Boolean> dimensionalCache = context.dimensionalCache();
        Map<UUID, Integer> tierCache = context.tierCache();
        Map<UUID, Integer> signalCache = context.signalCache();
        Map<UUID, LogisticsNodeEntity> nodeCache = new HashMap<>(sortedNodes.size());
        for (LogisticsNodeEntity node : sortedNodes) nodeCache.put(node.getUUID(), node);
        List<ImportTarget>[] itemImports = context.itemImports();
        List<ImportTarget>[] fluidImports = resolveCache(network.getFluidImports(), nodeCache, signalCache);
        List<ImportTarget>[] energyImports = resolveCache(network.getEnergyImports(), nodeCache, signalCache);
        List<ImportTarget>[] chemicalImports = resolveCache(network.getChemicalImports(), nodeCache, signalCache);
        List<ImportTarget>[] sourceImports = resolveCache(network.getSourceImports(), nodeCache, signalCache);
        NetworkRegistry registry = NetworkRegistry.get(server.overworld());

        boolean telemetryActive = registry.getTelemetryManager().isActive(network.getId());

        long minWakeDelta = Long.MAX_VALUE;
        for (LogisticsNodeEntity sourceNode : sortedNodes) {
            long delta = processNode(sourceNode, itemImports, fluidImports, energyImports, chemicalImports,
                    sourceImports, signalCache, dimensionalCache, tierCache, telemetryActive, includeItems);
            if (delta < minWakeDelta) {
                minWakeDelta = delta;
            }
        }

        return minWakeDelta;
    }

    public record NetworkContext(List<LogisticsNodeEntity> sortedNodes, Map<UUID, Integer> signalCache,
            List<ImportTarget>[] itemImports, Map<UUID, Boolean> dimensionalCache, Map<UUID, Integer> tierCache) {
    }

    public record ResolvedItemTargets(List<ItemTransferTarget> targets, List<ImportTarget> refs, int status) {
        public static final int OK = 0;
        public static final int NO_REACHABLE = -1;
    }

    @FunctionalInterface
    public interface MoveRecorder {
        void record(int sourceSlot, int targetIndex, ItemStack moved, boolean[] targetSlotMask);
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

            if (node.level() instanceof ServerLevel level) {
                signalCache.put(node.getUUID(), needsSignal ? level.getBestNeighborSignal(node.getAttachedPos()) : 0);
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
            boolean telemetryActive, boolean includeItems) {

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
            if (!includeItems && channel.getType() == ChannelType.ITEM)
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

            long cooldown = cooldownRemaining(sourceNode, channel, i, sourceTier, gameTime);
            if (cooldown > 0) {
                if (cooldown < minWakeDelta) minWakeDelta = cooldown;
                continue;
            }

            int configuredBatch = getBatchLimit(channel.getType(), sourceTier);
            int effectiveBatchSize = Math.max(1, Math.min(channel.getBatchSize(), configuredBatch));

            int result = switch (channel.getType()) {
                case FLUID ->
                    transferFluids(sourceNode, sourceLevel, channel, targets, effectiveBatchSize, dimensionalCache);
                case ENERGY ->
                    transferEnergy(sourceNode, sourceLevel, channel, targets, effectiveBatchSize, dimensionalCache);
                case CHEMICAL ->
                    transferChemicals(sourceNode, sourceLevel, channel, targets, effectiveBatchSize, dimensionalCache);
                case SOURCE ->
                    transferSource(sourceNode, sourceLevel, channel, targets, effectiveBatchSize, dimensionalCache);
                default ->
                    transferItems(sourceNode, sourceLevel, channel, i, targets, effectiveBatchSize, dimensionalCache);
            };

            minWakeDelta = Math.min(minWakeDelta, finishChannelAttempt(
                    sourceNode, channel, i, result, gameTime, sourceTier, telemetryActive));
        }

        return minWakeDelta;
    }

    public static long finishChannelAttempt(LogisticsNodeEntity node, ChannelData channel, int index, int result,
            long gameTime, int tier, boolean telemetryActive) {
        if (result < 0) return Long.MAX_VALUE;
        if (telemetryActive && result > 0) channel.getTelemetry().record(result);
        updateBackoff(node, channel, index, result > 0, gameTime, tier);
        return result > 0 ? 0 : Math.max(1L, cooldownRemaining(node, channel, index, tier, gameTime));
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
            case PRIORITY -> {
                return targets;
            }
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
            case ROUND_ROBIN -> {
                return targets;
            }
            default -> {
                return targets;
            }
        }
    }

    private static int transferItems(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, int channelIndex, List<ImportTarget> targets, int batchLimit,
            Map<UUID, Boolean> dimensionalCache) {

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceLevel.isLoaded(sourcePos))
            return -1;
        ResourceHandler<ItemResource> sourceHandler = sourceNode.capabilities().findItemHandler(exportChannel.getIoDirection());
        if (sourceHandler == null)
            return -1;

        ResolvedItemTargets resolved = resolveItemTargets(sourceNode, sourceLevel, exportChannel, targets,
                sourceHandler, dimensionalCache, exportChannel.getReadCache());
        if (resolved.status() != ResolvedItemTargets.OK) return resolved.status();
        if (resolved.targets().isEmpty()) return 0;
        return executeMove(sourceHandler, resolved.targets(), batchLimit,
                exportChannel.getFilterItems(), exportChannel.getFilterMode(), null,
                sourceLevel.registryAccess(), exportChannel.getDistributionMode() == DistributionMode.ROUND_ROBIN,
                exportChannel.getReadCache());
    }

    public static ResolvedItemTargets resolveItemTargets(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, List<ImportTarget> targets, ResourceHandler<ItemResource> sourceHandler,
            Map<UUID, Boolean> dimensionalCache, FilterItemData.ReadCache filterReadCache) {
        BlockPos sourcePos = sourceNode.getAttachedPos();
        targets = orderTargets(targets, exportChannel.getDistributionMode(), sourceNode);
        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        boolean anyReachable = false;
        List<ItemTransferTarget> reachableTargets = new ArrayList<>(targets.size());
        ItemStack[] exportFilters = exportChannel.getFilterItems();
        List<ImportTarget> refs = new ArrayList<>();

        for (ImportTarget target : targets) {
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

            if (isSameItemStorage(sourceLevel, sourcePos, targetLevel, targetPos))
                continue;

            ResourceHandler<ItemResource> targetHandler = target.node.capabilities().findItemHandler(target.channel.getIoDirection());
            if (targetHandler == null || targetHandler == sourceHandler)
                continue;

            ItemStack[] importFilters = target.channel.getFilterItems();
            boolean[] targetAllowedSlots = null;

            reachableTargets.add(new ItemTransferTarget(
                    targetHandler,
                    importFilters,
                    target.channel.getFilterMode(),
                    TransferAmountRules.collect(exportFilters, importFilters, filterReadCache),
                    FilterLogic.hasConfiguredItemNbtFilter(importFilters, filterReadCache),
                    targetAllowedSlots,
                    FilterLogic.hasConfiguredSlotMapping(importFilters, filterReadCache)));
            refs.add(target);
        }
        return new ResolvedItemTargets(reachableTargets, refs,
                anyReachable ? ResolvedItemTargets.OK : ResolvedItemTargets.NO_REACHABLE);
    }

    private static int transferFluids(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, List<ImportTarget> targets, int batchLimitMb,
            Map<UUID, Boolean> dimensionalCache) {

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceLevel.isLoaded(sourcePos))
            return -1;
        ResourceHandler<FluidResource> sourceHandler = sourceNode.capabilities().findFluidHandler(exportChannel.getIoDirection());
        if (sourceHandler == null)
            return -1;

        targets = orderTargets(targets, exportChannel.getDistributionMode(), sourceNode);
        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        int remaining = batchLimitMb;
        boolean anyReachable = false;
        FilterItemData.ReadCache filterReadCache = exportChannel.getReadCache();

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

            ResourceHandler<FluidResource> targetHandler = target.node.capabilities().findFluidHandler(target.channel.getIoDirection());
            if (targetHandler == null)
                continue;

            int filled = executeFluidMove(sourceHandler, targetHandler, remaining,
                    exportChannel.getFilterItems(), exportChannel.getFilterMode(),
                    target.channel.getFilterItems(), target.channel.getFilterMode(),
                    sourceLevel.registryAccess(), filterReadCache);
            if (filled > 0)
                remaining -= filled;
        }

        if (!anyReachable)
            return -1;
        return batchLimitMb - remaining;
    }

    private static int transferEnergy(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, List<ImportTarget> targets, int batchLimitRF,
            Map<UUID, Boolean> dimensionalCache) {

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceLevel.isLoaded(sourcePos))
            return -1;
        EnergyHandler sourceHandler = sourceNode.capabilities().findEnergyHandler(exportChannel.getIoDirection());
        if (sourceHandler == null)
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

            EnergyHandler targetHandler = target.node.capabilities().findEnergyHandler(target.channel.getIoDirection());
            if (targetHandler == null)
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
            ChannelData exportChannel, List<ImportTarget> targets, int batchLimit,
            Map<UUID, Boolean> dimensionalCache) {

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

            long moved = ChemicalTransferHelper.transferBetween(
                    sourceLevel, sourcePos, exportChannel.getIoDirection(),
                    targetLevel, targetPos, target.channel().getIoDirection(),
                    remaining,
                    exportChannel.getFilterItems(), exportChannel.getFilterMode(),
                    target.channel().getFilterItems(), target.channel().getFilterMode());
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
            ChannelData exportChannel, List<ImportTarget> targets, int batchLimit,
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

    private static int executeMove(ResourceHandler<ItemResource> source, List<ItemTransferTarget> targets, int limit,
            ItemStack[] exportFilters, FilterMode exportFilterMode,
            boolean[] sourceAllowedSlots,
            HolderLookup.Provider provider,
            boolean roundRobin,
            FilterItemData.ReadCache filterReadCache) {

        return executeMove(source, targets, limit, exportFilters, exportFilterMode, sourceAllowedSlots,
                provider, roundRobin, filterReadCache, null);
    }

    public static int executeMove(ResourceHandler<ItemResource> source, List<ItemTransferTarget> targets, int limit,
            ItemStack[] exportFilters, FilterMode exportFilterMode,
            boolean[] sourceAllowedSlots,
            HolderLookup.Provider provider,
            boolean roundRobin,
            FilterItemData.ReadCache filterReadCache, @Nullable MoveRecorder recorder) {

        return executeMove(source, targets, limit, exportFilters, exportFilterMode, sourceAllowedSlots,
                provider, roundRobin, filterReadCache, recorder, Map.of(), null, null);
    }

    public static int executeMove(ResourceHandler<ItemResource> source, List<ItemTransferTarget> targets, int limit,
            ItemStack[] exportFilters, FilterMode exportFilterMode, boolean[] sourceAllowedSlots,
            HolderLookup.Provider provider, boolean roundRobin, FilterItemData.ReadCache filterReadCache,
            @Nullable MoveRecorder recorder, Map<Item, Integer> priorBatchMoved,
            @Nullable Map<ResourceHandler<ItemResource>, Map<Item, Integer>> priorTargetBatchMoved,
            @Nullable BooleanSupplier isCurrent) {
        return executeMove(source, targets, limit, exportFilters, exportFilterMode, sourceAllowedSlots,
                provider, roundRobin, filterReadCache, recorder, priorBatchMoved, priorTargetBatchMoved, isCurrent, -1);
    }

    private static int executeMove(ResourceHandler<ItemResource> source, List<ItemTransferTarget> targets, int limit,
            ItemStack[] exportFilters, FilterMode exportFilterMode, boolean[] sourceAllowedSlots,
            HolderLookup.Provider provider, boolean roundRobin, FilterItemData.ReadCache filterReadCache,
            @Nullable MoveRecorder recorder, Map<Item, Integer> priorBatchMoved,
            @Nullable Map<ResourceHandler<ItemResource>, Map<Item, Integer>> priorTargetBatchMoved,
            @Nullable BooleanSupplier isCurrent, int intentSlot) {
        int remaining = limit;
        int firstSourceSlot = Math.max(0, intentSlot);
        int sourceSlotEnd = intentSlot < 0 ? source.size() : intentSlot + 1;
        BulkInsertRejectionCache bulkRejections = new BulkInsertRejectionCache();
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
        Map<Item, Integer> batchMoved = anyAmountConstraints && !roundRobin ? new HashMap<>(priorBatchMoved) : null;
        List<Map<Item, Integer>> targetBatchMoved = null;
        List<Map<Item, Integer>> targetItemCounts = null;
        Map<ResourceHandler<ItemResource>, Map<Item, Integer>> recoveryBatches = new IdentityHashMap<>();
        Map<ResourceHandler<ItemResource>, Map<Item, Integer>> recoveryCounts = new IdentityHashMap<>();
        if (anyAmountConstraints) {
            if (roundRobin) {
                targetBatchMoved = new ArrayList<>(targets.size());
            }
            targetItemCounts = new ArrayList<>(targets.size());
            for (ItemTransferTarget t : targets) {
                if (roundRobin) {
                    targetBatchMoved.add(priorTargetBatchMoved == null ? new HashMap<>()
                            : recoveryBatches.computeIfAbsent(t.handler(), handler ->
                                    new HashMap<>(priorTargetBatchMoved.getOrDefault(handler, Map.of()))));
                }
                targetItemCounts.add(priorTargetBatchMoved != null
                        ? recoveryCounts.computeIfAbsent(t.handler(), TransferAmountRules::countItems)
                        : (t.constraints().hasImportThreshold() || t.constraints().hasPerEntryAmounts())
                                ? TransferAmountRules.countItems(t.handler()) : null);
            }
        }

        boolean movedAny;
        boolean[] openTargets = new boolean[targets.size()];
        Arrays.fill(openTargets, true);
        int openTargetCount = targets.size();

        try (var tx = Transaction.openRoot()) {
            transfer: while (remaining > 0 && openTargetCount > 0) {
                movedAny = false;
                int targetsLeft = openTargetCount;

                for (int targetIndex = 0; targetIndex < targets.size() && remaining > 0; targetIndex++) {
                    if (!openTargets[targetIndex]) {
                        continue;
                    }

                    ItemTransferTarget target = targets.get(targetIndex);
                    boolean movedForTarget = false;
                    int targetRemaining = roundRobin ? Math.ceilDiv(remaining, targetsLeft) : remaining;
                    targetsLeft--;

                    for (int slot = firstSourceSlot; slot < sourceSlotEnd && remaining > 0 && targetRemaining > 0; slot++) {
                        if (isCurrent != null && !isCurrent.getAsBoolean()) break transfer;
                        if (sourceAllowedSlots != null
                                && (slot >= sourceAllowedSlots.length || !sourceAllowedSlots[slot])) {
                            continue;
                        }

                        ItemStack inSlot = ItemUtil.getStack(source, slot);
                        if (inSlot.isEmpty() || inSlot.is(ModTags.RESOURCE_BLACKLIST_ITEMS)) {
                            continue;
                        }
                        ItemStack extracted = inSlot.copyWithCount(Math.min(targetRemaining, inSlot.getCount()));

                        CompoundTag candidateComponents = (provider != null && hasNbtFilter)
                                ? NbtFilterData.getSerializedComponents(extracted, provider)
                                : null;

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
                                        target.importFilterMode(), extracted, provider, candidateComponents, filterReadCache);
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
                                    Map<Item, Integer> movedByItem = roundRobin ? targetBatchMoved.get(targetIndex) : batchMoved;
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

                        int extractable;
                        try (var check = Transaction.open(tx)) {
                            extractable = extractItem(source, slot, allowed, check).getCount();
                        }
                        if (extractable <= 0) {
                            continue;
                        }

                        ItemStack toMove = extracted.copyWithCount(Math.min(allowed, extractable));
                        ItemResource candidate = ItemResource.of(toMove);
                        boolean bulk = importAllowedSlots == null && (target.handler() instanceof SnapshotItemHandler snapshot
                                ? snapshot.supportsBulkInsertion() : SophisticatedCoreCompat.isBulkHandler(target.handler()));
                        if (bulk && bulkRejections.isRejected(target.handler(), candidate, toMove.getCount())) {
                            continue;
                        }

                        if (isCurrent != null && !isCurrent.getAsBoolean()) break transfer;
                        int movedCount;
                        try (var move = Transaction.open(tx)) {
                            ItemStack uninserted = insertItemWithAllowedSlots(target.handler(), toMove, move,
                                    importAllowedSlots);
                            int targetAccepted = toMove.getCount() - uninserted.getCount();
                            if (targetAccepted <= 0) {
                                if (bulk) {
                                    bulkRejections.reject(target.handler(), candidate, toMove.getCount());
                                }
                                continue;
                            }
                            if (!candidate.equals(source.getResource(slot))
                                    || source.extract(slot, candidate, targetAccepted, move) != targetAccepted) {
                                continue;
                            }
                            movedCount = targetAccepted;
                            move.commit();
                        }

                        if (movedCount > 0) {
                            if (recorder != null) {
                                recorder.record(slot, targetIndex, candidate.toStack(movedCount), importAllowedSlots);
                            }
                            bulkRejections.clear();
                            movedAny = true;
                            movedForTarget = true;
                            remaining -= movedCount;
                            targetRemaining -= movedCount;

                            if (anyAmountConstraints) {
                                Item movedItem = extracted.getItem();
                                if (sourceItemCounts != null) {
                                    sourceItemCounts.merge(movedItem, -movedCount, Integer::sum);
                                }
                                Map<Item, Integer> tgtCache = targetItemCounts.get(targetIndex);
                                if (tgtCache != null) {
                                    tgtCache.merge(movedItem, movedCount, Integer::sum);
                                }
                                Map<Item, Integer> movedByItem = roundRobin ? targetBatchMoved.get(targetIndex) : batchMoved;
                                movedByItem.merge(movedItem, movedCount, Integer::sum);
                            }
                            if (intentSlot >= 0) break transfer;
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

            tx.commit();
        }

        return limit - remaining;
    }

    public static int commitSingleMove(ResourceHandler<ItemResource> source, ItemTransferTarget target,
            TransferPlan.MoveIntent intent, int remaining, ItemStack[] exportFilters, FilterMode exportMode,
            HolderLookup.Provider provider, FilterItemData.ReadCache cache, Map<Item, Integer> priorBatchMoved) {
        ThreadGuard.requireServerThread();
        int slot = intent.sourceSlot();
        if (source == target.handler() || slot < 0 || slot >= source.size() || intent.amount() <= 0
                || intent.resource().isEmpty() || !intent.resource().equals(source.getResource(slot))) return 0;
        ItemStack candidate = intent.resource().toStack(Math.min(intent.amount(), remaining));
        if (candidate.isEmpty() || candidate.is(ModTags.RESOURCE_BLACKLIST_ITEMS)) return 0;
        CompoundTag components = provider != null && (target.hasItemNbtFilter()
                || FilterLogic.hasConfiguredItemNbtFilter(exportFilters, cache))
                ? NbtFilterData.getSerializedComponents(candidate, provider) : null;
        boolean[] mask = target.allowedSlots();
        if (provider != null && target.hasImportSlotMapping()) {
            mask = computeImportAllowedSlots(target.handler(), target.importFilters(), target.importFilterMode(),
                    candidate, provider, components, cache);
            if (mask == null) return 0;
        }
        if (!Arrays.equals(mask, intent.targetSlotMask())) return 0;
        return executeMove(source, List.of(target), candidate.getCount(), exportFilters, exportMode,
                null, provider, false, cache, null, priorBatchMoved, null, null, slot);
    }

    private static ItemStack extractItem(ResourceHandler<ItemResource> handler, int slot, int amount,
            TransactionContext transaction) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemResource resource = handler.getResource(slot);
        if (resource.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int request = Math.min(amount, resource.getMaxStackSize());
        int extracted = handler.extract(slot, resource, request, transaction);
        return extracted <= 0 ? ItemStack.EMPTY : resource.toStack(extracted);
    }

    private static ItemStack insertItem(ResourceHandler<ItemResource> handler, int slot, ItemStack stack,
            TransactionContext transaction) {
        return ItemUtil.insertItemReturnRemaining(handler, slot, stack, false, transaction);
    }

    private static int fillFluid(ResourceHandler<FluidResource> handler, FluidStack stack,
            TransactionContext transaction) {
        if (stack.isEmpty()) {
            return 0;
        }

        return handler.insert(FluidResource.of(stack), stack.getAmount(), transaction);
    }

    private static FluidStack drainFluid(ResourceHandler<FluidResource> handler, FluidStack stack,
            TransactionContext transaction) {
        if (stack.isEmpty()) {
            return FluidStack.EMPTY;
        }

        FluidResource resource = FluidResource.of(stack);
        int extracted = handler.extract(resource, stack.getAmount(), transaction);
        return extracted <= 0 ? FluidStack.EMPTY : resource.toStack(extracted);
    }

    private static boolean[] computeImportAllowedSlots(ResourceHandler<ItemResource> handler, ItemStack[] importFilters,
            FilterMode importFilterMode, ItemStack candidate, HolderLookup.Provider provider,
            @Nullable CompoundTag candidateComponents, @Nullable FilterItemData.ReadCache filterReadCache) {
        int size = handler.size();
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

    private static ItemStack insertItemWithAllowedSlots(ResourceHandler<ItemResource> handler, ItemStack stack,
            TransactionContext transaction, boolean[] allowedSlots) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (allowedSlots == null) {
            return ItemUtil.insertItemReturnRemaining(handler, stack, false, transaction);
        }

        ItemStack remaining = stack.copy();

        for (int slot = 0; slot < handler.size() && !remaining.isEmpty(); slot++) {
            if (slot >= allowedSlots.length || !allowedSlots[slot]) {
                continue;
            }
            ItemStack slotStack = ItemUtil.getStack(handler, slot);
            if (slotStack.isEmpty()) {
                continue;
            }
            if (!ItemStack.isSameItemSameComponents(slotStack, remaining)) {
                continue;
            }
            if (!handler.isValid(slot, ItemResource.of(remaining))) {
                continue;
            }
            remaining = insertItem(handler, slot, remaining, transaction);
        }

        for (int slot = 0; slot < handler.size() && !remaining.isEmpty(); slot++) {
            if (slot >= allowedSlots.length || !allowedSlots[slot]) {
                continue;
            }
            ItemStack slotStack = ItemUtil.getStack(handler, slot);
            if (!slotStack.isEmpty()) {
                continue;
            }
            if (!handler.isValid(slot, ItemResource.of(remaining))) {
                continue;
            }
            remaining = insertItem(handler, slot, remaining, transaction);
        }

        return remaining;
    }

    private static int executeFluidMove(ResourceHandler<FluidResource> source, ResourceHandler<FluidResource> target, int limitMb,
            ItemStack[] exportFilters, FilterMode exportFilterMode,
            ItemStack[] importFilters, FilterMode importFilterMode,
            HolderLookup.Provider provider, @Nullable FilterItemData.ReadCache filterReadCache) {

        int remaining = limitMb;
        TransferAmountRules.Constraints amountConstraints = TransferAmountRules.collect(exportFilters, importFilters, filterReadCache);

        try (var tx = Transaction.openRoot()) {
            for (int tank = 0; tank < source.size() && remaining > 0; tank++) {
                FluidStack tankFluid = FluidUtil.getStack(source, tank);
                if (tankFluid.isEmpty())
                    continue;
                if (tankFluid.getFluid().builtInRegistryHolder().is(ModTags.RESOURCE_BLACKLIST_FLUIDS))
                    continue;

                int requestFromTank = Math.min(remaining, tankFluid.getAmount());
                FluidStack simulated = tankFluid.copyWithAmount(requestFromTank);

                if (provider != null) {
                    if (!FilterLogic.matchesFluid(exportFilters, exportFilterMode, simulated, provider, filterReadCache))
                        continue;
                    if (!FilterLogic.matchesFluid(importFilters, importFilterMode, simulated, provider, filterReadCache))
                        continue;
                }

                int allowedByAmount = TransferAmountRules.allowedFluids(source, target, simulated,
                        amountConstraints);
                if (amountConstraints.hasPerEntryAmounts()) {
                    int perEntry = TransferAmountRules.perEntryFluidAmount(simulated, exportFilters, importFilters, source, target,
                            filterReadCache);
                    if (perEntry >= 0) {
                        allowedByAmount = Math.min(allowedByAmount, perEntry);
                    }
                }
                if (allowedByAmount <= 0)
                    continue;

                int request = Math.min(simulated.getAmount(), Math.min(remaining, allowedByAmount));
                int perEntryBatch = TransferAmountRules.perEntryFluidBatch(simulated, exportFilters, importFilters, filterReadCache);
                if (perEntryBatch > 0) {
                    request = Math.min(request, perEntryBatch);
                }
                if (request <= 0)
                    continue;

                FluidStack drained = drainFluid(source, simulated.copyWithAmount(request), tx);
                if (drained.isEmpty())
                    continue;

                int filled = fillFluid(target, drained, tx);
                if (filled < drained.getAmount()) {
                    int rollbackAmount = drained.getAmount() - filled;
                    int returned = fillFluid(source, drained.copyWithAmount(rollbackAmount), tx);
                    if (returned < rollbackAmount) {
                        if (Config.debugMode) LOGGER.error("FLUID VOIDING: Source rejected rollback of {} mB ({}). {} mB lost.",
                                rollbackAmount - returned, drained.getFluid(), rollbackAmount - returned);
                    }
                }

                if (filled > 0) {
                    remaining -= filled;
                }
            }

            tx.commit();
        }
        return limitMb - remaining;
    }

    private static int executeEnergyMove(EnergyHandler source, EnergyHandler target, int limitRF) {
        return EnergyHandlerUtil.move(source, target, limitRF, null);
    }

    private static LogisticsNodeEntity findNode(MinecraftServer server, UUID nodeId,
            @Nullable ResourceKey<Level> cachedDim) {
        if (cachedDim != null) {
            ServerLevel level = server.getLevel(cachedDim);
            if (level == null)
                return null;
            return level.getEntity(nodeId) instanceof LogisticsNodeEntity node ? node : null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(nodeId);
            if (entity instanceof LogisticsNodeEntity node)
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
}
