package me.almana.logisticsnetworks.logic;

import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.NbtFilterData;
import me.almana.logisticsnetworks.integration.ars.ArsCompat;
import me.almana.logisticsnetworks.integration.ars.SourceTransferHelper;
import me.almana.logisticsnetworks.integration.mekanism.ChemicalTransferHelper;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
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

    private record ImportTarget(LogisticsNodeEntity node, ChannelData channel, int channelIndex) {
    }

    private record ItemTransferTarget(IItemHandler handler, ItemStack[] importFilters,
            FilterMode importFilterMode, TransferAmountRules.Constraints constraints, boolean hasItemNbtFilter,
            boolean[] allowedSlots, boolean hasImportSlotMapping) {
    }

    public static long processNetwork(LogisticsNetwork network, MinecraftServer server) {
        if (network == null || server == null)
            return Long.MAX_VALUE;

        NetworkRegistry registry = NetworkRegistry.get((ServerLevel) server.overworld());
        if (network.isCacheDirty()) {
            network.rebuildCache(registry);
            network.clearCacheDirty();
        }

        List<UUID> sortedUuids = network.getSortedUuids();
        if (sortedUuids.isEmpty())
            return Long.MAX_VALUE;

        Map<UUID, Boolean> dimensionalCache = network.getDimensionalCache();
        Map<UUID, Integer> tierCache = network.getTierCache();

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

        if (sortedNodes.isEmpty())
            return Long.MAX_VALUE;

        Map<UUID, Integer> signalCache = buildSignalCache(sortedNodes);
        if (signalCache.isEmpty())
            return Long.MAX_VALUE;

        List<ImportTarget>[] itemImports = resolveCache(network.getItemImports(), nodeCache, signalCache);
        List<ImportTarget>[] fluidImports = resolveCache(network.getFluidImports(), nodeCache, signalCache);
        List<ImportTarget>[] energyImports = resolveCache(network.getEnergyImports(), nodeCache, signalCache);
        List<ImportTarget>[] chemicalImports = resolveCache(network.getChemicalImports(), nodeCache, signalCache);
        List<ImportTarget>[] sourceImports = resolveCache(network.getSourceImports(), nodeCache, signalCache);

        boolean telemetryActive = registry.getTelemetryManager().isActive(network.getId());
        TransferCapabilityCache capCache = registry.getCapabilityCache();

        long minWakeDelta = Long.MAX_VALUE;
        for (LogisticsNodeEntity sourceNode : sortedNodes) {
            long delta = processNode(sourceNode, itemImports, fluidImports, energyImports, chemicalImports,
                    sourceImports, signalCache, dimensionalCache, tierCache, telemetryActive, capCache);
            if (delta < minWakeDelta) {
                minWakeDelta = delta;
            }
        }

        return minWakeDelta;
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
            boolean telemetryActive,
            TransferCapabilityCache capCache) {

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

            if (telemetryActive && result > 0) {
                channel.getTelemetry().record(result);
            }

            updateBackoff(sourceNode, channel, i, result > 0, gameTime, sourceTier);

            if (result > 0) {
                minWakeDelta = 0;
            } else {
                long postCooldown = cooldownRemaining(sourceNode, channel, i, sourceTier, gameTime);
                long wakeAt = Math.max(1L, postCooldown);
                if (wakeAt < minWakeDelta) minWakeDelta = wakeAt;
            }
        }

        return minWakeDelta;
    }

    private static long cooldownRemaining(LogisticsNodeEntity node, ChannelData channel, int index, int tier,
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

    private static int getBatchLimit(ChannelType type, int tier) {
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
                // PRIORITY: pre-sorted at rebuild. ROUND_ROBIN: fixed order, no cursor.
                return targets;
            }
        }
    }

    private static int transferItems(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, int channelIndex, List<ImportTarget> targets, int batchLimit,
            Map<UUID, Boolean> dimensionalCache, TransferCapabilityCache capCache) {

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceLevel.isLoaded(sourcePos))
            return -1;
        IItemHandler sourceHandler = capCache.findItemHandler(sourceLevel, sourcePos, exportChannel.getIoDirection());
        if (sourceHandler == null)
            return -1;

        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        boolean anyReachable = false;
        List<ItemTransferTarget> reachableTargets = new ArrayList<>(targets.size());
        ItemStack[] exportFilters = exportChannel.getFilterItems();
        boolean[] sourceAllowedSlots = null;
        FilterItemData.ReadCache filterReadCache = FilterItemData.createReadCache();

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

            IItemHandler targetHandler = capCache.findItemHandler(targetLevel, targetPos, target.channel.getIoDirection());
            if (targetHandler == null)
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
        }
        if (!anyReachable)
            return -1;
        if (reachableTargets.isEmpty())
            return 0;

        return executeMove(sourceHandler, reachableTargets, batchLimit,
                exportFilters, exportChannel.getFilterMode(),
                sourceAllowedSlots,
                sourceLevel.registryAccess(),
                sourceLevel, sourcePos, filterReadCache);
    }

    private static int transferFluids(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, int channelIndex, List<ImportTarget> targets, int batchLimitMb,
            Map<UUID, Boolean> dimensionalCache, TransferCapabilityCache capCache) {

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceLevel.isLoaded(sourcePos))
            return -1;
        IFluidHandler sourceHandler = capCache.findFluidHandler(sourceLevel, sourcePos, exportChannel.getIoDirection());
        if (sourceHandler == null)
            return -1;

        targets = orderTargets(targets, exportChannel.getDistributionMode(), sourceNode);
        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        int remaining = batchLimitMb;
        boolean anyReachable = false;
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
            ServerLevel targetLevel = (ServerLevel) target.node.level();
            BlockPos targetPos = target.node.getAttachedPos();
            if (!targetLevel.isLoaded(targetPos))
                continue;

            IFluidHandler targetHandler = capCache.findFluidHandler(targetLevel, targetPos, target.channel.getIoDirection());
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

    private static boolean isSameItemStorage(ServerLevel sourceLevel, BlockPos sourcePos,
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

    private static int executeMove(IItemHandler source, List<ItemTransferTarget> targets, int limit,
            ItemStack[] exportFilters, FilterMode exportFilterMode,
            boolean[] sourceAllowedSlots,
            HolderLookup.Provider provider,
            ServerLevel sourceLevel, BlockPos sourcePos, FilterItemData.ReadCache filterReadCache) {

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
        Map<Item, Integer> batchMoved = anyAmountConstraints ? new HashMap<>() : null;
        List<Map<Item, Integer>> targetItemCounts = null;
        if (anyAmountConstraints) {
            targetItemCounts = new ArrayList<>(targets.size());
            for (ItemTransferTarget t : targets) {
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

        // Serialize each source slot once across the target loop
        CompoundTag[] slotComponents = hasNbtFilter ? new CompoundTag[source.getSlots()] : null;
        boolean[] slotComponentsCached = hasNbtFilter ? new boolean[source.getSlots()] : null;

        while (remaining > 0 && openTargetCount > 0) {
            movedAny = false;

            for (int targetIndex = 0; targetIndex < targets.size() && remaining > 0; targetIndex++) {
                if (!openTargets[targetIndex]) {
                    continue;
                }

                ItemTransferTarget target = targets.get(targetIndex);
                boolean movedForTarget = false;

                for (int slot = 0; slot < source.getSlots() && remaining > 0; slot++) {
                    if (sourceAllowedSlots != null
                            && (slot >= sourceAllowedSlots.length || !sourceAllowedSlots[slot])) {
                        continue;
                    }

                    ItemStack extracted = source.extractItem(slot, remaining, true);
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
                                int alreadyMoved = batchMoved.getOrDefault(extracted.getItem(), 0);
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
                    ItemStack simRemainder = insertItemWithAllowedSlots(target.handler(), simulatedInsert, true,
                            importAllowedSlots);
                    int acceptableCount = allowed - simRemainder.getCount();
                    if (acceptableCount <= 0) {
                        continue;
                    }

                    ItemStack toMove = source.extractItem(slot, acceptableCount, false);
                    if (toMove.isEmpty()) {
                        continue;
                    }

                    ItemStack uninserted = insertItemWithAllowedSlots(target.handler(), toMove, false,
                            importAllowedSlots);
                    int targetAccepted = toMove.getCount() - uninserted.getCount();
                    int droppedToWorld = 0;

                    if (!uninserted.isEmpty()) {
                        ItemStack stillLeft = source.insertItem(slot, uninserted, false);
                        if (!stillLeft.isEmpty()) {
                            for (int fallback = 0; fallback < source.getSlots() && !stillLeft.isEmpty(); fallback++) {
                                stillLeft = source.insertItem(fallback, stillLeft, false);
                            }
                            if (!stillLeft.isEmpty()) {
                                ItemStack forcedRemainder = insertItemWithAllowedSlots(target.handler(), stillLeft,
                                        false, importAllowedSlots);
                                int forcedIn = stillLeft.getCount() - forcedRemainder.getCount();
                                targetAccepted += forcedIn;
                                if (!forcedRemainder.isEmpty()) {
                                    LOGGER.error("ITEM VOIDING PREVENTED: Could not return {} to source or fit into "
                                            + "target slot mask. Dropping at source pos {}.",
                                            forcedRemainder, sourcePos);
                                    droppedToWorld = forcedRemainder.getCount();
                                    Block.popResource(sourceLevel, sourcePos, forcedRemainder);
                                }
                            }
                        }
                    }

                    int sourceLost = targetAccepted + droppedToWorld;
                    if (sourceLost > 0) {
                        movedAny = true;
                        movedForTarget = true;
                        remaining -= sourceLost;

                        if (anyAmountConstraints) {
                            Item movedItem = extracted.getItem();
                            if (sourceItemCounts != null) {
                                sourceItemCounts.merge(movedItem, -sourceLost, Integer::sum);
                            }
                            Map<Item, Integer> tgtCache = targetItemCounts.get(targetIndex);
                            if (tgtCache != null && targetAccepted > 0) {
                                tgtCache.merge(movedItem, targetAccepted, Integer::sum);
                            }
                            batchMoved.merge(movedItem, sourceLost, Integer::sum);
                        }

                        if (slotComponentsCached != null) {
                            slotComponentsCached[slot] = false;
                        }

                        break;
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

    private static ItemStack insertItemWithAllowedSlots(IItemHandler handler, ItemStack stack, boolean simulate,
            boolean[] allowedSlots) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (allowedSlots == null) {
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

    private static LogisticsNodeEntity findNode(MinecraftServer server, UUID nodeId,
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

    private static boolean isRedstoneActive(RedstoneMode mode, int signalStrength) {
        return switch (mode) {
            case ALWAYS_ON -> true;
            case ALWAYS_OFF -> false;
            case HIGH -> signalStrength > 0;
            case LOW -> signalStrength == 0;
        };
    }
}
