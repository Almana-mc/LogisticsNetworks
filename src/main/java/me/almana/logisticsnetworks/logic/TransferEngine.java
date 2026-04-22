package me.almana.logisticsnetworks.logic;

import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.AmountFilterData;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.NbtFilterData;
import me.almana.logisticsnetworks.filter.SlotFilterData;
import me.almana.logisticsnetworks.integration.ars.ArsCompat;
import me.almana.logisticsnetworks.integration.ars.SourceTransferHelper;
import me.almana.logisticsnetworks.integration.mekanism.ChemicalTransferHelper;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.capabilities.Capabilities;
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

    private static long capKey(ServerLevel level, BlockPos pos, Direction dir) {
        long packed = pos.asLong();
        packed ^= ((long) dir.ordinal()) << 58;
        packed ^= ((long) level.dimension().location().hashCode()) << 32;
        return packed;
    }

    private static class CapCache {
        private final Map<Long, Object> items = new HashMap<>();
        private final Map<Long, Object> fluids = new HashMap<>();
        private final Map<Long, Object> energy = new HashMap<>();
        private static final Object ABSENT = new Object();

        IItemHandler getItemHandler(ServerLevel level, BlockPos pos, Direction dir) {
            long key = capKey(level, pos, dir);
            Object cached = items.get(key);
            if (cached == ABSENT) return null;
            if (cached != null) return (IItemHandler) cached;
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir);
            items.put(key, handler != null ? handler : ABSENT);
            return handler;
        }

        IFluidHandler getFluidHandler(ServerLevel level, BlockPos pos, Direction dir) {
            long key = capKey(level, pos, dir);
            Object cached = fluids.get(key);
            if (cached == ABSENT) return null;
            if (cached != null) return (IFluidHandler) cached;
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, dir);
            fluids.put(key, handler != null ? handler : ABSENT);
            return handler;
        }

        IEnergyStorage getEnergyHandler(ServerLevel level, BlockPos pos, Direction dir) {
            long key = capKey(level, pos, dir);
            Object cached = energy.get(key);
            if (cached == ABSENT) return null;
            if (cached != null) return (IEnergyStorage) cached;
            IEnergyStorage handler = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, dir);
            energy.put(key, handler != null ? handler : ABSENT);
            return handler;
        }

        IItemHandler findItemHandler(ServerLevel level, BlockPos pos, @Nullable Direction dir) {
            if (dir != null) return getItemHandler(level, pos, dir);
            List<IItemHandler> found = new ArrayList<>(6);
            for (Direction d : Direction.values()) {
                IItemHandler h = getItemHandler(level, pos, d);
                if (h == null) continue;
                boolean dup = false;
                for (IItemHandler existing : found) {
                    if (existing == h) { dup = true; break; }
                }
                if (!dup) found.add(h);
            }
            if (found.isEmpty()) return null;
            if (found.size() == 1) return found.get(0);
            return new CombinedItemHandler(found.toArray(new IItemHandler[0]));
        }

        IFluidHandler findFluidHandler(ServerLevel level, BlockPos pos, @Nullable Direction dir) {
            if (dir != null) return getFluidHandler(level, pos, dir);
            List<IFluidHandler> found = new ArrayList<>(6);
            for (Direction d : Direction.values()) {
                IFluidHandler h = getFluidHandler(level, pos, d);
                if (h == null) continue;
                boolean dup = false;
                for (IFluidHandler existing : found) {
                    if (existing == h) { dup = true; break; }
                }
                if (!dup) found.add(h);
            }
            if (found.isEmpty()) return null;
            if (found.size() == 1) return found.get(0);
            return new CombinedFluidHandler(found.toArray(new IFluidHandler[0]));
        }

        IEnergyStorage findEnergyHandler(ServerLevel level, BlockPos pos, @Nullable Direction dir) {
            if (dir != null) return getEnergyHandler(level, pos, dir);
            List<IEnergyStorage> found = new ArrayList<>(6);
            for (Direction d : Direction.values()) {
                IEnergyStorage h = getEnergyHandler(level, pos, d);
                if (h == null) continue;
                boolean dup = false;
                for (IEnergyStorage existing : found) {
                    if (existing == h) { dup = true; break; }
                }
                if (!dup) found.add(h);
            }
            if (found.isEmpty()) return null;
            if (found.size() == 1) return found.get(0);
            return new CombinedEnergyStorage(found.toArray(new IEnergyStorage[0]));
        }
    }

    private record ImportTarget(LogisticsNodeEntity node, ChannelData channel, int channelIndex) {
    }

    private record ItemTransferTarget(IItemHandler handler, ItemStack[] importFilters,
            FilterMode importFilterMode, AmountConstraints constraints, boolean hasItemNbtFilter,
            boolean[] allowedSlots) {
    }

    private record AmountConstraints(boolean hasExportThreshold, int exportThreshold,
            boolean hasImportThreshold, int importThreshold,
            boolean hasPerEntryAmounts) {
    }

    public static boolean processNetwork(LogisticsNetwork network, MinecraftServer server) {
        if (network == null || server == null)
            return false;

        NetworkRegistry registry = NetworkRegistry.get((ServerLevel) server.overworld());
        if (network.isCacheDirty()) {
            network.rebuildCache(registry);
            network.clearCacheDirty();
        }

        Set<UUID> nodeUuids = network.getNodeUuids();
        if (nodeUuids.isEmpty())
            return false;

        // Deterministic order
        List<UUID> sortedUuids = new ArrayList<>(nodeUuids);
        sortedUuids.sort(Comparator.comparingLong(UUID::getMostSignificantBits)
                .thenComparingLong(UUID::getLeastSignificantBits));

        // Cache nodes and upgrades
        List<LogisticsNodeEntity> sortedNodes = new ArrayList<>(sortedUuids.size());
        Map<UUID, Boolean> dimensionalCache = new HashMap<>(sortedUuids.size());
        Map<UUID, Integer> tierCache = new HashMap<>(sortedUuids.size());
        Map<UUID, LogisticsNodeEntity> nodeCache = new HashMap<>(sortedUuids.size());

        for (UUID nodeId : sortedUuids) {
            LogisticsNodeEntity node = findNode(server, nodeId);
            if (node != null && node.isValidNode()) {
                sortedNodes.add(node);
                dimensionalCache.put(node.getUUID(), NodeUpgradeData.hasDimensionalUpgrade(node));
                tierCache.put(node.getUUID(), NodeUpgradeData.getUpgradeTier(node));
                nodeCache.put(node.getUUID(), node);
            } else if (Config.debugMode) {
                LOGGER.debug("Node {} missing from world, skipping.", nodeId);
            }
        }

        if (sortedNodes.isEmpty())
            return false;

        Map<UUID, Integer> signalCache = buildSignalCache(sortedNodes);
        if (signalCache.isEmpty())
            return false;

        List<ImportTarget>[] itemImports = resolveCache(network.getItemImports(), nodeCache);
        List<ImportTarget>[] fluidImports = resolveCache(network.getFluidImports(), nodeCache);
        List<ImportTarget>[] energyImports = resolveCache(network.getEnergyImports(), nodeCache);
        List<ImportTarget>[] chemicalImports = resolveCache(network.getChemicalImports(), nodeCache);
        List<ImportTarget>[] sourceImports = resolveCache(network.getSourceImports(), nodeCache);

        boolean telemetryActive = registry.getTelemetryManager().isActive(network.getId());
        CapCache capCache = new CapCache();

        boolean anyActivePotential = false;
        for (LogisticsNodeEntity sourceNode : sortedNodes) {
            if (processNode(sourceNode, itemImports, fluidImports, energyImports, chemicalImports,
                    sourceImports, signalCache, dimensionalCache, tierCache, telemetryActive, capCache)) {
                anyActivePotential = true;
            }
        }

        return anyActivePotential;
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
                    if (ch.getRedstoneMode() != RedstoneMode.ALWAYS_ON) {
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

    @SuppressWarnings("unchecked")
    private static List<ImportTarget>[] resolveCache(List<NodeRef>[] cache,
            Map<UUID, LogisticsNodeEntity> nodeCache) {
        List<ImportTarget>[] resolved = new List[9];
        for (int i = 0; i < 9; i++) {
            List<NodeRef> cachedNodes = cache[i];
            List<ImportTarget> targets = new ArrayList<>(cachedNodes.size());
            for (NodeRef ref : cachedNodes) {
                LogisticsNodeEntity node = nodeCache.get(ref.nodeId());
                if (node == null)
                    continue;
                ChannelData cd = node.getChannel(i);
                if (cd != null) {
                    targets.add(new ImportTarget(node, cd, i));
                }
            }
            resolved[i] = targets;
        }
        return resolved;
    }

    private static boolean processNode(LogisticsNodeEntity sourceNode,
            List<ImportTarget>[] itemImports,
            List<ImportTarget>[] fluidImports,
            List<ImportTarget>[] energyImports,
            List<ImportTarget>[] chemicalImports,
            List<ImportTarget>[] sourceImports,
            Map<UUID, Integer> signalCache,
            Map<UUID, Boolean> dimensionalCache,
            Map<UUID, Integer> tierCache,
            boolean telemetryActive,
            CapCache capCache) {

        if (!sourceNode.isValidNode())
            return false;

        ServerLevel sourceLevel = (ServerLevel) sourceNode.level();
        long gameTime = sourceLevel.getGameTime();
        int redstoneSignal = signalCache.getOrDefault(sourceNode.getUUID(), 0);
        boolean hasActivePotential = false;
        int sourceTier = tierCache.getOrDefault(sourceNode.getUUID(), 0);

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

            hasActivePotential = true;

            // Backoff/Cool-down Check
            if (isOnCooldown(sourceNode, channel, i, sourceTier, gameTime))
                continue;

            targets = orderTargets(targets, channel.getDistributionMode(), sourceNode, i);

            int configuredBatch = getBatchLimit(channel.getType(), sourceTier);
            int effectiveBatchSize = Math.max(1, Math.min(channel.getBatchSize(), configuredBatch));

            int result = switch (channel.getType()) {
                case FLUID ->
                    transferFluids(sourceNode, sourceLevel, channel, targets, effectiveBatchSize, dimensionalCache, capCache);
                case ENERGY ->
                    transferEnergy(sourceNode, sourceLevel, channel, targets, effectiveBatchSize, dimensionalCache, capCache);
                case CHEMICAL ->
                    transferChemicals(sourceNode, sourceLevel, channel, targets, effectiveBatchSize, dimensionalCache);
                case SOURCE ->
                    transferSource(sourceNode, sourceLevel, channel, targets, effectiveBatchSize, dimensionalCache);
                default ->
                    transferItems(sourceNode, sourceLevel, channel, i, targets, effectiveBatchSize, dimensionalCache, capCache);
            };

            if (result < 0)
                continue;

            if (telemetryActive && result > 0) {
                channel.getTelemetry().record(result);
            }

            updateBackoff(sourceNode, channel, i, result > 0, gameTime, sourceTier, targets.size());
        }

        return hasActivePotential;
    }

    private static boolean isOnCooldown(LogisticsNodeEntity node, ChannelData channel, int index, int tier,
            long gameTime) {
        long lastRun = node.getLastExecution(index);
        boolean isInstantType = channel.getType() == ChannelType.ENERGY;
        long configuredDelay = isInstantType ? 1
                : Math.max(channel.getTickDelay(), NodeUpgradeData.getMinTickDelay(tier));
        float backoff = node.getBackoffTicks(index);
        boolean useBackoff = Config.backoffEnabled[channel.getType().ordinal()];
        long effectiveDelay = useBackoff ? Math.max(configuredDelay, (long) backoff) : configuredDelay;

        return gameTime - lastRun < effectiveDelay;
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
            long gameTime, int tier, int targetCount) {
        node.setLastExecution(index, gameTime);
        boolean isInstantType = channel.getType() == ChannelType.ENERGY;
        int configuredDelay = isInstantType ? 1
                : Math.max(channel.getTickDelay(), NodeUpgradeData.getMinTickDelay(tier));

        if (success) {
            float curBackoff = node.getBackoffTicks(index);
            if (curBackoff > configuredDelay) {
                node.setBackoffTicks(index, Math.max(configuredDelay, curBackoff / BACKOFF_DECAY_DIVISOR));
            }
            if (channel.getDistributionMode() == DistributionMode.ROUND_ROBIN) {
                node.advanceRoundRobin(index, targetCount);
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
            LogisticsNodeEntity sourceNode, int channelIndex) {
        if (targets.size() <= 1)
            return targets;

        switch (mode) {
            case PRIORITY -> {
                targets.sort((a, b) -> Integer.compare(b.channel.getPriority(), a.channel.getPriority()));
                return targets;
            }
            case NEAREST_FIRST -> {
                double sx = sourceNode.getX(), sy = sourceNode.getY(), sz = sourceNode.getZ();
                targets.sort(Comparator.comparingDouble(t -> t.node.distanceToSqr(sx, sy, sz)));
                return targets;
            }
            case FARTHEST_FIRST -> {
                double sx = sourceNode.getX(), sy = sourceNode.getY(), sz = sourceNode.getZ();
                targets.sort(
                        (a, b) -> Double.compare(b.node.distanceToSqr(sx, sy, sz), a.node.distanceToSqr(sx, sy, sz)));
                return targets;
            }
            case ROUND_ROBIN -> {
                int startIdx = sourceNode.getRoundRobinIndex(channelIndex) % targets.size();
                if (startIdx == 0)
                    return targets;
                List<ImportTarget> rotated = new ArrayList<>(targets.size());
                for (int i = 0; i < targets.size(); i++) {
                    rotated.add(targets.get((startIdx + i) % targets.size()));
                }
                return rotated;
            }
            default -> {
                return targets;
            }
        }
    }

    private static int transferItems(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, int channelIndex, List<ImportTarget> targets, int batchLimit,
            Map<UUID, Boolean> dimensionalCache, CapCache capCache) {

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
        boolean[] sourceAllowedSlots = buildSlotAccessMask(sourceHandler, exportFilters);

        for (ImportTarget target : targets) {
            if (target.node.getUUID().equals(sourceNode.getUUID()))
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

            IItemHandler targetHandler = capCache.findItemHandler(targetLevel, targetPos, target.channel.getIoDirection());
            if (targetHandler == null)
                continue;

            ItemStack[] importFilters = target.channel.getFilterItems();
            boolean[] targetAllowedSlots = buildSlotAccessMask(targetHandler, importFilters);
            if (targetAllowedSlots != null && !hasAnyAllowedSlots(targetAllowedSlots)) {
                continue;
            }

            reachableTargets.add(new ItemTransferTarget(
                    targetHandler,
                    importFilters,
                    target.channel.getFilterMode(),
                    collectAmountConstraints(exportFilters, importFilters),
                    FilterLogic.hasConfiguredItemNbtFilter(importFilters),
                    targetAllowedSlots));
        }
        if (!anyReachable)
            return -1;
        if (reachableTargets.isEmpty())
            return 0;

        return executeMove(sourceHandler, reachableTargets, batchLimit,
                exportFilters, exportChannel.getFilterMode(),
                sourceAllowedSlots,
                sourceLevel.registryAccess());
    }

    private static int transferFluids(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, List<ImportTarget> targets, int batchLimitMb,
            Map<UUID, Boolean> dimensionalCache, CapCache capCache) {

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceLevel.isLoaded(sourcePos))
            return -1;
        IFluidHandler sourceHandler = capCache.findFluidHandler(sourceLevel, sourcePos, exportChannel.getIoDirection());
        if (sourceHandler == null)
            return -1;

        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        int remaining = batchLimitMb;
        boolean anyReachable = false;
        FilterItemData.ReadCache filterReadCache = FilterItemData.createReadCache();

        for (ImportTarget target : targets) {
            if (remaining <= 0)
                break;
            if (target.node.getUUID().equals(sourceNode.getUUID()))
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
            ChannelData exportChannel, List<ImportTarget> targets, int batchLimitRF,
            Map<UUID, Boolean> dimensionalCache, CapCache capCache) {

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceLevel.isLoaded(sourcePos))
            return -1;
        IEnergyStorage sourceHandler = capCache.findEnergyHandler(sourceLevel, sourcePos, exportChannel.getIoDirection());
        if (sourceHandler == null || !sourceHandler.canExtract())
            return -1;

        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        int remaining = batchLimitRF;
        boolean anyReachable = false;

        for (ImportTarget target : targets) {
            if (remaining <= 0)
                break;
            if (target.node.getUUID().equals(sourceNode.getUUID()))
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

        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        int remaining = batchLimit;
        boolean anyReachable = false;
        FilterItemData.ReadCache filterReadCache = FilterItemData.createReadCache();

        for (ImportTarget target : targets) {
            if (remaining <= 0)
                break;
            if (target.node().getUUID().equals(sourceNode.getUUID()))
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

        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        int remaining = batchLimit;
        boolean anyReachable = false;

        for (ImportTarget target : targets) {
            if (remaining <= 0)
                break;
            if (target.node().getUUID().equals(sourceNode.getUUID()))
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

    private static int executeMove(IItemHandler source, List<ItemTransferTarget> targets, int limit,
            ItemStack[] exportFilters, FilterMode exportFilterMode,
            boolean[] sourceAllowedSlots,
            HolderLookup.Provider provider) {

        int remaining = limit;
        FilterItemData.ReadCache filterReadCache = FilterItemData.createReadCache();
        boolean hasExportNbtFilter = FilterLogic.hasConfiguredItemNbtFilter(exportFilters);
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
            if (t.constraints().hasExportThreshold || t.constraints().hasImportThreshold
                    || t.constraints().hasPerEntryAmounts) {
                anyAmountConstraints = true;
                break;
            }
        }
        Map<Item, Integer> sourceItemCounts = anyAmountConstraints ? buildItemCountCache(source) : null;
        Map<Item, Integer> batchMoved = anyAmountConstraints ? new HashMap<>() : null;
        List<Map<Item, Integer>> targetItemCounts = null;
        if (anyAmountConstraints) {
            targetItemCounts = new ArrayList<>(targets.size());
            for (ItemTransferTarget t : targets) {
                targetItemCounts.add(
                        (t.constraints().hasImportThreshold || t.constraints().hasPerEntryAmounts)
                                ? buildItemCountCache(t.handler())
                                : null);
            }
        }

        boolean movedAny;
        boolean[] openTargets = new boolean[targets.size()];
        Arrays.fill(openTargets, true);
        int openTargetCount = targets.size();

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

                    CompoundTag candidateComponents = (provider != null && hasNbtFilter)
                            ? NbtFilterData.getSerializedComponents(extracted, provider)
                            : null;

                    if (provider != null) {
                        if (!FilterLogic.matchesItemInSlot(exportFilters, exportFilterMode, extracted, provider,
                                candidateComponents, filterReadCache, slot)) {
                            continue;
                        }
                    }

                    if (provider != null && !FilterLogic.matchesItemInSlot(target.importFilters(), target.importFilterMode(),
                            extracted, provider, candidateComponents, filterReadCache, -1)) {
                        continue;
                    }

                    int allowedByAmount;
                    if (!anyAmountConstraints
                            || (!target.constraints().hasExportThreshold && !target.constraints().hasImportThreshold
                                    && !target.constraints().hasPerEntryAmounts)) {
                        allowedByAmount = extracted.getCount();
                    } else {
                        allowedByAmount = getAllowedTransferCached(extracted, target.constraints(),
                                sourceItemCounts, targetItemCounts.get(targetIndex));
                        if (target.constraints().hasPerEntryAmounts && provider != null) {
                            int perEntry = getPerEntryItemAmountLimit(extracted, exportFilters,
                                    target.importFilters(), sourceItemCounts,
                                    targetItemCounts.get(targetIndex), provider, candidateComponents,
                                    filterReadCache);
                            if (perEntry >= 0) {
                                allowedByAmount = Math.min(allowedByAmount, perEntry);
                            }
                            int batchLimit = getPerEntryBatchLimit(extracted, exportFilters, provider,
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
                            target.allowedSlots());
                    int acceptableCount = allowed - simRemainder.getCount();
                    if (acceptableCount <= 0) {
                        continue;
                    }

                    ItemStack toMove = source.extractItem(slot, acceptableCount, false);
                    if (toMove.isEmpty()) {
                        continue;
                    }

                    ItemStack uninserted = insertItemWithAllowedSlots(target.handler(), toMove, false,
                            target.allowedSlots());
                    int moved = toMove.getCount() - uninserted.getCount();

                    if (!uninserted.isEmpty()) {
                        ItemStack stillLeft = source.insertItem(slot, uninserted, false);
                        if (!stillLeft.isEmpty()) {
                            for (int fallback = 0; fallback < source.getSlots() && !stillLeft.isEmpty(); fallback++) {
                                stillLeft = source.insertItem(fallback, stillLeft, false);
                            }
                            if (!stillLeft.isEmpty()) {
                                LOGGER.error("ITEM VOIDING PREVENTED: Could not return {} to source handler {}. " +
                                        "Forcing back into target as last resort.",
                                        stillLeft, source.getClass().getSimpleName());
                                insertItemWithAllowedSlots(target.handler(), stillLeft, false, null);
                            }
                        }
                    }

                    if (moved > 0) {
                        movedAny = true;
                        movedForTarget = true;
                        remaining -= moved;

                        if (anyAmountConstraints) {
                            Item movedItem = extracted.getItem();
                            if (sourceItemCounts != null) {
                                sourceItemCounts.merge(movedItem, -moved, Integer::sum);
                            }
                            Map<Item, Integer> tgtCache = targetItemCounts.get(targetIndex);
                            if (tgtCache != null) {
                                tgtCache.merge(movedItem, moved, Integer::sum);
                            }
                            batchMoved.merge(movedItem, moved, Integer::sum);
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
        AmountConstraints amountConstraints = collectAmountConstraints(exportFilters, importFilters, filterReadCache);

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

            int allowedByAmount = getAllowedTransferByFluidAmountConstraints(source, target, simulated,
                    amountConstraints);
            if (amountConstraints.hasPerEntryAmounts) {
                int perEntry = getPerEntryFluidAmountLimit(simulated, exportFilters, importFilters, source, target,
                        filterReadCache);
                if (perEntry >= 0) {
                    allowedByAmount = Math.min(allowedByAmount, perEntry);
                }
            }
            if (allowedByAmount <= 0)
                continue;

            int request = Math.min(simulated.getAmount(), Math.min(remaining, allowedByAmount));
            int perEntryBatch = getPerEntryFluidBatchLimit(simulated, exportFilters, importFilters, filterReadCache);
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
                source.fill(drained.copyWithAmount(drained.getAmount() - filled), IFluidHandler.FluidAction.EXECUTE);
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

        return target.receiveEnergy(actuallyExtracted, false);
    }

    private static LogisticsNodeEntity findNode(MinecraftServer server, UUID nodeId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(nodeId);
            if (entity instanceof LogisticsNodeEntity node)
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

    private static AmountConstraints collectAmountConstraints(ItemStack[] exportFilters, ItemStack[] importFilters) {
        return collectAmountConstraints(exportFilters, importFilters, null);
    }

    private static AmountConstraints collectAmountConstraints(ItemStack[] exportFilters, ItemStack[] importFilters,
            @Nullable FilterItemData.ReadCache filterReadCache) {
        int exportThreshold = 0;
        boolean hasExportThreshold = false;
        boolean hasPerEntryAmounts = false;

        if (exportFilters != null) {
            for (ItemStack filter : exportFilters) {
                if (AmountFilterData.isAmountFilterItem(filter)) {
                    hasExportThreshold = true;
                    exportThreshold = Math.max(exportThreshold, AmountFilterData.getAmount(filter));
                }
                if (FilterItemData.hasAnyAmountEntries(filter, filterReadCache)) {
                    hasPerEntryAmounts = true;
                }
            }
        }

        int importThreshold = Integer.MAX_VALUE;
        boolean hasImportThreshold = false;

        if (importFilters != null) {
            for (ItemStack filter : importFilters) {
                if (AmountFilterData.isAmountFilterItem(filter)) {
                    hasImportThreshold = true;
                    importThreshold = Math.min(importThreshold, AmountFilterData.getAmount(filter));
                }
                if (FilterItemData.hasAnyAmountEntries(filter, filterReadCache)) {
                    hasPerEntryAmounts = true;
                }
            }
        }

        return new AmountConstraints(hasExportThreshold, exportThreshold, hasImportThreshold, importThreshold,
                hasPerEntryAmounts);
    }

    private static Map<Item, Integer> buildItemCountCache(IItemHandler handler) {
        Map<Item, Integer> counts = new HashMap<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    private static int getAllowedTransferCached(ItemStack candidate, AmountConstraints constraints,
            Map<Item, Integer> sourceCounts, Map<Item, Integer> targetCounts) {
        int allowed = Integer.MAX_VALUE;

        if (constraints.hasExportThreshold) {
            int sourceCount = sourceCounts != null ? sourceCounts.getOrDefault(candidate.getItem(), 0) : 0;
            int exportCap = sourceCount - constraints.exportThreshold;
            if (exportCap <= 0)
                return 0;
            allowed = Math.min(allowed, exportCap);
        }

        if (constraints.hasImportThreshold) {
            int targetCount = targetCounts != null ? targetCounts.getOrDefault(candidate.getItem(), 0) : 0;
            int importCap = constraints.importThreshold - targetCount;
            if (importCap <= 0)
                return 0;
            allowed = Math.min(allowed, importCap);
        }

        return allowed == Integer.MAX_VALUE ? candidate.getCount() : Math.max(0, allowed);
    }

    private static int getAllowedTransferByFluidAmountConstraints(IFluidHandler source, IFluidHandler target,
            FluidStack candidate, AmountConstraints constraints) {
        int allowed = Integer.MAX_VALUE;

        if (constraints.hasExportThreshold) {
            int sourceAmount = countMatchingFluid(source, candidate);
            int exportCap = sourceAmount - constraints.exportThreshold;
            if (exportCap <= 0)
                return 0;
            allowed = Math.min(allowed, exportCap);
        }

        if (constraints.hasImportThreshold) {
            int targetAmount = countMatchingFluid(target, candidate);
            int importCap = constraints.importThreshold - targetAmount;
            if (importCap <= 0)
                return 0;
            allowed = Math.min(allowed, importCap);
        }

        return allowed == Integer.MAX_VALUE ? candidate.getAmount() : Math.max(0, allowed);
    }

    private static int getPerEntryItemAmountLimit(ItemStack candidate, ItemStack[] exportFilters,
            ItemStack[] importFilters, Map<Item, Integer> sourceCounts, Map<Item, Integer> targetCounts,
            HolderLookup.Provider provider, @Nullable CompoundTag candidateComponents,
            @Nullable FilterItemData.ReadCache filterReadCache) {
        int allowed = Integer.MAX_VALUE;

        if (exportFilters != null) {
            for (ItemStack filter : exportFilters) {
                int threshold = FilterItemData.getItemAmountThresholdFull(filter, candidate, provider,
                        candidateComponents, filterReadCache);
                if (threshold > 0) {
                    int sourceCount = sourceCounts != null ? sourceCounts.getOrDefault(candidate.getItem(), 0) : 0;
                    int exportCap = sourceCount - threshold;
                    if (exportCap <= 0)
                        return 0;
                    allowed = Math.min(allowed, exportCap);
                }
            }
        }

        if (importFilters != null) {
            for (ItemStack filter : importFilters) {
                int threshold = FilterItemData.getItemAmountThresholdFull(filter, candidate, provider,
                        candidateComponents, filterReadCache);
                if (threshold > 0) {
                    int targetCount = targetCounts != null ? targetCounts.getOrDefault(candidate.getItem(), 0) : 0;
                    int importCap = threshold - targetCount;
                    if (importCap <= 0)
                        return 0;
                    allowed = Math.min(allowed, importCap);
                }
            }
        }

        return allowed == Integer.MAX_VALUE ? -1 : Math.max(0, allowed);
    }

    private static int getPerEntryBatchLimit(ItemStack candidate, ItemStack[] exportFilters,
            HolderLookup.Provider provider, @Nullable CompoundTag candidateComponents,
            @Nullable FilterItemData.ReadCache filterReadCache) {
        if (exportFilters == null) return -1;
        int limit = Integer.MAX_VALUE;
        for (ItemStack filter : exportFilters) {
            int batch = FilterItemData.getItemBatchLimitFull(filter, candidate, provider,
                    candidateComponents, filterReadCache);
            if (batch > 0)
                limit = Math.min(limit, batch);
        }
        return limit == Integer.MAX_VALUE ? -1 : limit;
    }

    private static int getPerEntryFluidAmountLimit(FluidStack candidate, ItemStack[] exportFilters,
            ItemStack[] importFilters, IFluidHandler source, IFluidHandler target,
            @Nullable FilterItemData.ReadCache filterReadCache) {
        int allowed = Integer.MAX_VALUE;

        if (exportFilters != null) {
            for (ItemStack filter : exportFilters) {
                int threshold = FilterItemData.getFluidAmountThresholdFull(filter, candidate, null, filterReadCache);
                if (threshold > 0) {
                    int sourceAmount = countMatchingFluid(source, candidate);
                    int exportCap = sourceAmount - threshold;
                    if (exportCap <= 0)
                        return 0;
                    allowed = Math.min(allowed, exportCap);
                }
            }
        }

        if (importFilters != null) {
            for (ItemStack filter : importFilters) {
                int threshold = FilterItemData.getFluidAmountThresholdFull(filter, candidate, null, filterReadCache);
                if (threshold > 0) {
                    int targetAmount = countMatchingFluid(target, candidate);
                    int importCap = threshold - targetAmount;
                    if (importCap <= 0)
                        return 0;
                    allowed = Math.min(allowed, importCap);
                }
            }
        }

        return allowed == Integer.MAX_VALUE ? -1 : Math.max(0, allowed);
    }

    private static int getPerEntryFluidBatchLimit(FluidStack candidate, ItemStack[] exportFilters,
            ItemStack[] importFilters, @Nullable FilterItemData.ReadCache filterReadCache) {
        int limit = Integer.MAX_VALUE;

        if (exportFilters != null) {
            for (ItemStack filter : exportFilters) {
                int batch = FilterItemData.getFluidBatchLimitFull(filter, candidate, filterReadCache);
                if (batch > 0) {
                    limit = Math.min(limit, batch);
                }
            }
        }

        if (importFilters != null) {
            for (ItemStack filter : importFilters) {
                int batch = FilterItemData.getFluidBatchLimitFull(filter, candidate, filterReadCache);
                if (batch > 0) {
                    limit = Math.min(limit, batch);
                }
            }
        }

        return limit == Integer.MAX_VALUE ? -1 : limit;
    }

    private static int countMatchingFluid(IFluidHandler handler, FluidStack candidate) {
        int amount = 0;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack stack = handler.getFluidInTank(i);
            if (!stack.isEmpty() && FluidStack.isSameFluidSameComponents(stack, candidate)) {
                amount += stack.getAmount();
            }
        }
        return amount;
    }

    private static boolean[] buildSlotAccessMask(IItemHandler handler, ItemStack[] filters) {
        if (handler == null || filters == null || filters.length == 0) {
            return null;
        }

        int slotCount = handler.getSlots();
        if (slotCount <= 0) {
            return null;
        }

        boolean[] allowed = new boolean[slotCount];
        boolean[] blacklistMask = new boolean[slotCount];

        boolean hasConfiguredSlotFilter = false;
        boolean hasWhitelist = false;

        for (ItemStack filter : filters) {
            if (!SlotFilterData.isSlotFilterItem(filter) || !SlotFilterData.hasAnySlots(filter)) {
                continue;
            }

            hasConfiguredSlotFilter = true;
            List<Integer> slots = SlotFilterData.getSlots(filter);
            if (slots.isEmpty()) {
                continue;
            }

            if (SlotFilterData.isBlacklist(filter)) {
                for (int slot : slots) {
                    if (slot >= 0 && slot < slotCount) {
                        blacklistMask[slot] = true;
                    }
                }
            } else {
                hasWhitelist = true;
                for (int slot : slots) {
                    if (slot >= 0 && slot < slotCount) {
                        allowed[slot] = true;
                    }
                }
            }
        }

        if (!hasConfiguredSlotFilter) {
            return null;
        }

        if (!hasWhitelist) {
            Arrays.fill(allowed, true);
        }

        for (int i = 0; i < slotCount; i++) {
            if (blacklistMask[i]) {
                allowed[i] = false;
            }
        }

        return allowed;
    }

    private static boolean hasAnyAllowedSlots(boolean[] allowedSlots) {
        if (allowedSlots == null) {
            return true;
        }
        for (boolean allowed : allowedSlots) {
            if (allowed) {
                return true;
            }
        }
        return false;
    }

    private static final class CombinedItemHandler implements IItemHandler {
        private final IItemHandler[] handlers;
        private final int[] slotOffsets;
        private final int totalSlots;

        CombinedItemHandler(IItemHandler[] handlers) {
            this.handlers = handlers;
            this.slotOffsets = new int[handlers.length];
            int running = 0;
            for (int i = 0; i < handlers.length; i++) {
                slotOffsets[i] = running;
                running += handlers[i].getSlots();
            }
            this.totalSlots = running;
        }

        private int handlerIndex(int slot) {
            for (int i = handlers.length - 1; i >= 0; i--) {
                if (slot >= slotOffsets[i]) return i;
            }
            return 0;
        }

        @Override
        public int getSlots() {
            return totalSlots;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= totalSlots) return ItemStack.EMPTY;
            int i = handlerIndex(slot);
            return handlers[i].getStackInSlot(slot - slotOffsets[i]);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= totalSlots) return stack;
            int i = handlerIndex(slot);
            return handlers[i].insertItem(slot - slotOffsets[i], stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= totalSlots) return ItemStack.EMPTY;
            int i = handlerIndex(slot);
            return handlers[i].extractItem(slot - slotOffsets[i], amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot < 0 || slot >= totalSlots) return 0;
            int i = handlerIndex(slot);
            return handlers[i].getSlotLimit(slot - slotOffsets[i]);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < 0 || slot >= totalSlots) return false;
            int i = handlerIndex(slot);
            return handlers[i].isItemValid(slot - slotOffsets[i], stack);
        }
    }

    private static final class CombinedFluidHandler implements IFluidHandler {
        private final IFluidHandler[] handlers;
        private final int[] tankOffsets;
        private final int totalTanks;

        CombinedFluidHandler(IFluidHandler[] handlers) {
            this.handlers = handlers;
            this.tankOffsets = new int[handlers.length];
            int running = 0;
            for (int i = 0; i < handlers.length; i++) {
                tankOffsets[i] = running;
                running += handlers[i].getTanks();
            }
            this.totalTanks = running;
        }

        private int handlerIndex(int tank) {
            for (int i = handlers.length - 1; i >= 0; i--) {
                if (tank >= tankOffsets[i]) return i;
            }
            return 0;
        }

        @Override
        public int getTanks() {
            return totalTanks;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (tank < 0 || tank >= totalTanks) return FluidStack.EMPTY;
            int i = handlerIndex(tank);
            return handlers[i].getFluidInTank(tank - tankOffsets[i]);
        }

        @Override
        public int getTankCapacity(int tank) {
            if (tank < 0 || tank >= totalTanks) return 0;
            int i = handlerIndex(tank);
            return handlers[i].getTankCapacity(tank - tankOffsets[i]);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            if (tank < 0 || tank >= totalTanks) return false;
            int i = handlerIndex(tank);
            return handlers[i].isFluidValid(tank - tankOffsets[i], stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            int remaining = resource.getAmount();
            int filled = 0;
            for (IFluidHandler h : handlers) {
                if (remaining <= 0) break;
                int accepted = h.fill(resource.copyWithAmount(remaining), action);
                if (accepted > 0) {
                    filled += accepted;
                    remaining -= accepted;
                }
            }
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return FluidStack.EMPTY;
            int remaining = resource.getAmount();
            int totalAmount = 0;
            FluidStack template = FluidStack.EMPTY;
            for (IFluidHandler h : handlers) {
                if (remaining <= 0) break;
                FluidStack drained = h.drain(resource.copyWithAmount(remaining), action);
                if (drained.isEmpty()) continue;
                if (template.isEmpty()) template = drained;
                totalAmount += drained.getAmount();
                remaining -= drained.getAmount();
            }
            return template.isEmpty() ? FluidStack.EMPTY : template.copyWithAmount(totalAmount);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) return FluidStack.EMPTY;
            int remaining = maxDrain;
            int totalAmount = 0;
            FluidStack template = FluidStack.EMPTY;
            for (IFluidHandler h : handlers) {
                if (remaining <= 0) break;
                FluidStack drained;
                if (template.isEmpty()) {
                    drained = h.drain(remaining, action);
                } else {
                    drained = h.drain(template.copyWithAmount(remaining), action);
                }
                if (drained.isEmpty()) continue;
                if (template.isEmpty()) template = drained;
                totalAmount += drained.getAmount();
                remaining -= drained.getAmount();
            }
            return template.isEmpty() ? FluidStack.EMPTY : template.copyWithAmount(totalAmount);
        }
    }

    private static final class CombinedEnergyStorage implements IEnergyStorage {
        private final IEnergyStorage[] handlers;

        CombinedEnergyStorage(IEnergyStorage[] handlers) {
            this.handlers = handlers;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) return 0;
            int remaining = maxReceive;
            int total = 0;
            for (IEnergyStorage h : handlers) {
                if (remaining <= 0) break;
                if (!h.canReceive()) continue;
                int accepted = h.receiveEnergy(remaining, simulate);
                if (accepted > 0) {
                    total += accepted;
                    remaining -= accepted;
                }
            }
            return total;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0) return 0;
            int remaining = maxExtract;
            int total = 0;
            for (IEnergyStorage h : handlers) {
                if (remaining <= 0) break;
                if (!h.canExtract()) continue;
                int extracted = h.extractEnergy(remaining, simulate);
                if (extracted > 0) {
                    total += extracted;
                    remaining -= extracted;
                }
            }
            return total;
        }

        @Override
        public int getEnergyStored() {
            long sum = 0;
            for (IEnergyStorage h : handlers) sum += h.getEnergyStored();
            return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
        }

        @Override
        public int getMaxEnergyStored() {
            long sum = 0;
            for (IEnergyStorage h : handlers) sum += h.getMaxEnergyStored();
            return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
        }

        @Override
        public boolean canExtract() {
            for (IEnergyStorage h : handlers) if (h.canExtract()) return true;
            return false;
        }

        @Override
        public boolean canReceive() {
            for (IEnergyStorage h : handlers) if (h.canReceive()) return true;
            return false;
        }
    }
}
