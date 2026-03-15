package me.almana.logisticsnetworks.logic;

import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.data.NodeRef;
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
import org.slf4j.Logger;

import java.util.*;

public class TransferEngine {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float BACKOFF_MULTIPLIER = 1.3f;
    private static final float BACKOFF_DECAY_DIVISOR = 3f;
    private static final float BACKOFF_MAX_TICKS = 40f;
    private static final float BACKOFF_MAX_TICKS_ENERGY = 5f;

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

    private record RecipeEntry(ItemStack item, String tag, int amount) {
    }

    private record RecipeCursorResult(int moved, int entryIndex, int entryRemaining, boolean completed) {
    }

    private static List<RecipeEntry> buildRecipe(ItemStack[] exportFilters, HolderLookup.Provider provider) {
        List<RecipeEntry> recipe = new ArrayList<>();
        if (exportFilters == null)
            return recipe;

        for (ItemStack filter : exportFilters) {
            if (!FilterItemData.isFilterItem(filter))
                continue;
            int cap = FilterItemData.getCapacity(filter);
            for (int slot = 0; slot < cap; slot++) {
                int amount = FilterItemData.getEntryAmount(filter, slot);
                if (amount <= 0)
                    continue;

                String tag = FilterItemData.getEntryTag(filter, slot);
                if (tag != null) {
                    recipe.add(new RecipeEntry(ItemStack.EMPTY, tag, amount));
                    continue;
                }

                ItemStack entry = FilterItemData.getEntry(filter, slot, provider);
                if (!entry.isEmpty()) {
                    recipe.add(new RecipeEntry(entry, null, amount));
                }
            }
        }
        return recipe;
    }

    private static boolean matchesRecipeEntry(RecipeEntry entry, ItemStack candidate) {
        if (entry.tag != null) {
            return candidate.getTags()
                    .map(t -> t.location().toString())
                    .anyMatch(entry.tag::equals);
        }
        return !entry.item.isEmpty() && ItemStack.isSameItem(entry.item, candidate);
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

        boolean anyActivePotential = false;
        for (LogisticsNodeEntity sourceNode : sortedNodes) {
            if (processNode(sourceNode, itemImports, fluidImports, energyImports, chemicalImports,
                    sourceImports, signalCache, dimensionalCache, tierCache, telemetryActive)) {
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
            boolean telemetryActive) {

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
        long effectiveDelay = Math.max(configuredDelay, (long) backoff);

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
        } else {
            float maxBackoff = isInstantType ? BACKOFF_MAX_TICKS_ENERGY : BACKOFF_MAX_TICKS;
            float curBackoff = Math.max(node.getBackoffTicks(index), configuredDelay);
            if (curBackoff <= configuredDelay) {
                // First failure: Set backoff such that it doesn't incur an extra integer tick
                // of delay now,
                // but the NEXT failure (multiplied by BACKOFF_MULTIPLIER) will cross the +1
                // threshold.
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
            case ROUND_ROBIN, RECIPE_ROBIN -> {
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
            Map<UUID, Boolean> dimensionalCache) {

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceLevel.isLoaded(sourcePos))
            return -1;
        IItemHandler sourceHandler = sourceLevel.getCapability(Capabilities.ItemHandler.BLOCK, sourcePos,
                exportChannel.getIoDirection());
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

            IItemHandler targetHandler = targetLevel.getCapability(Capabilities.ItemHandler.BLOCK, targetPos,
                    target.channel.getIoDirection());
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

        int moved;
        if (exportChannel.getDistributionMode() == DistributionMode.RECIPE_ROBIN) {
            moved = executeMoveRecipeWithCursor(sourceNode, channelIndex, sourceHandler,
                    reachableTargets, batchLimit, exportFilters, exportChannel.getFilterMode(),
                    sourceAllowedSlots, sourceLevel.registryAccess());
        } else {
            moved = executeMove(sourceHandler, reachableTargets, batchLimit,
                    exportFilters, exportChannel.getFilterMode(),
                    sourceAllowedSlots,
                    sourceLevel.registryAccess());
        }
        return moved;
    }

    private static int transferFluids(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, List<ImportTarget> targets, int batchLimitMb,
            Map<UUID, Boolean> dimensionalCache) {

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceLevel.isLoaded(sourcePos))
            return -1;
        IFluidHandler sourceHandler = sourceLevel.getCapability(Capabilities.FluidHandler.BLOCK, sourcePos,
                exportChannel.getIoDirection());
        if (sourceHandler == null)
            return -1;

        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        boolean anyReachable = false;

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

            IFluidHandler targetHandler = targetLevel.getCapability(Capabilities.FluidHandler.BLOCK, targetPos,
                    target.channel.getIoDirection());
            if (targetHandler == null)
                continue;

            int filled = executeFluidMove(sourceHandler, targetHandler, batchLimitMb,
                    exportChannel.getFilterItems(), exportChannel.getFilterMode(),
                    target.channel.getFilterItems(), target.channel.getFilterMode(),
                    sourceLevel.registryAccess());
            if (filled > 0) {
                return filled;
            }
        }
        return anyReachable ? 0 : -1;
    }

    private static int transferEnergy(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, List<ImportTarget> targets, int batchLimitRF,
            Map<UUID, Boolean> dimensionalCache) {

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceLevel.isLoaded(sourcePos))
            return -1;
        IEnergyStorage sourceHandler = sourceLevel.getCapability(Capabilities.EnergyStorage.BLOCK, sourcePos,
                exportChannel.getIoDirection());
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

            IEnergyStorage targetHandler = targetLevel.getCapability(Capabilities.EnergyStorage.BLOCK, targetPos,
                    target.channel.getIoDirection());
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
        boolean anyReachable = false;

        for (ImportTarget target : targets) {
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
                    batchLimit,
                    exportChannel.getFilterItems(), exportChannel.getFilterMode(),
                    target.channel().getFilterItems(), target.channel().getFilterMode());
            if (Config.debugMode)
                LOGGER.debug("[Chemical] Transfer {} -> {}: moved={}, batch={}",
                        sourcePos, targetPos, moved, batchLimit);
            if (moved > 0)
                return (int) moved;
        }

        if (Config.debugMode && !anyReachable)
            LOGGER.debug("[Chemical] No reachable targets for {}", sourcePos);
        return anyReachable ? 0 : -1;
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
        boolean hasExportNbtFilter = FilterLogic.hasConfiguredItemNbtFilter(exportFilters);
        boolean hasAnyImportNbtFilter = false;
        for (ItemTransferTarget target : targets) {
            if (target.hasItemNbtFilter()) {
                hasAnyImportNbtFilter = true;
                break;
            }
        }
        boolean hasNbtFilter = hasExportNbtFilter || hasAnyImportNbtFilter;

        // Build amount constraint caches to avoid repeated full-inventory scans
        boolean anyAmountConstraints = false;
        for (ItemTransferTarget t : targets) {
            if (t.constraints().hasExportThreshold || t.constraints().hasImportThreshold
                    || t.constraints().hasPerEntryAmounts) {
                anyAmountConstraints = true;
                break;
            }
        }
        Map<Item, Integer> sourceItemCounts = anyAmountConstraints ? buildItemCountCache(source) : null;
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
                        if (!FilterLogic.matchesItem(exportFilters, exportFilterMode, extracted, provider,
                                candidateComponents)) {
                            continue;
                        }
                    }

                    if (provider != null && !FilterLogic.matchesItem(target.importFilters(), target.importFilterMode(),
                            extracted, provider, candidateComponents)) {
                        continue;
                    }

                    int allowedByAmount;
                    if (!anyAmountConstraints
                            || (!target.constraints().hasExportThreshold && !target.constraints().hasImportThreshold
                                    && !target.constraints().hasPerEntryAmounts)) {
                        allowedByAmount = extracted.getCount(); // extracted.getCount() is bounded by 'remaining'
                                                                // already
                    } else {
                        allowedByAmount = getAllowedTransferCached(extracted, target.constraints(),
                                sourceItemCounts, targetItemCounts.get(targetIndex));
                        if (target.constraints().hasPerEntryAmounts && provider != null) {
                            int perEntry = getPerEntryItemAmountLimit(extracted, exportFilters,
                                    target.importFilters(), sourceItemCounts,
                                    targetItemCounts.get(targetIndex), provider);
                            if (perEntry >= 0) {
                                allowedByAmount = Math.min(allowedByAmount, perEntry);
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

                    // Simulate insertion first to determine how many the target can actually accept
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
                        // Put back what couldn't be inserted
                        ItemStack stillLeft = source.insertItem(slot, uninserted, false);
                        if (!stillLeft.isEmpty()) {
                            // Source rejected the put-back; try all source slots as a safety net
                            for (int fallback = 0; fallback < source.getSlots() && !stillLeft.isEmpty(); fallback++) {
                                stillLeft = source.insertItem(fallback, stillLeft, false);
                            }
                            if (!stillLeft.isEmpty()) {
                                LOGGER.error("ITEM VOIDING PREVENTED: Could not return {} to source handler {}. " +
                                        "Forcing back into target as last resort.",
                                        stillLeft, source.getClass().getSimpleName());
                                // Last resort: we cannot void items. Re-insert into target to undo.
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
                        }

                        // We successfully transferred an item to this target.
                        // Break out of the slot loop to allow the next target in the Round Robin queue
                        // to get a turn.
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

    private static RecipeCursorResult executeMoveRecipeToTargetWithCursor(IItemHandler source, ItemTransferTarget target,
            int limit, List<RecipeEntry> recipe, boolean[] sourceAllowedSlots,
            HolderLookup.Provider provider, int startEntryIndex, int startEntryRemaining) {

        int totalMoved = 0;
        int currentEntryIdx = startEntryIndex;
        int currentRemaining = startEntryRemaining;

        while (currentEntryIdx < recipe.size()) {
            RecipeEntry entry = recipe.get(currentEntryIdx);

            int wantToMove = Math.min(currentRemaining, limit - totalMoved);
            if (wantToMove <= 0)
                break;

            int movedForEntry = 0;

            for (int slot = 0; slot < source.getSlots() && movedForEntry < wantToMove; slot++) {
                if (sourceAllowedSlots != null
                        && (slot >= sourceAllowedSlots.length || !sourceAllowedSlots[slot])) {
                    continue;
                }

                int needed = wantToMove - movedForEntry;
                ItemStack extracted = source.extractItem(slot, needed, true);
                if (extracted.isEmpty() || extracted.is(ModTags.RESOURCE_BLACKLIST_ITEMS)) {
                    continue;
                }

                if (!matchesRecipeEntry(entry, extracted)) {
                    continue;
                }

                if (provider != null && !FilterLogic.matchesItem(target.importFilters(),
                        target.importFilterMode(), extracted, provider, null)) {
                    continue;
                }

                int toExtract = Math.min(extracted.getCount(), needed);

                ItemStack simulatedInsert = extracted.copyWithCount(toExtract);
                ItemStack simRemainder = insertItemWithAllowedSlots(
                        target.handler(), simulatedInsert, true, target.allowedSlots());
                int acceptableCount = toExtract - simRemainder.getCount();
                if (acceptableCount <= 0)
                    continue;

                ItemStack toMove = source.extractItem(slot, acceptableCount, false);
                if (toMove.isEmpty())
                    continue;

                ItemStack uninserted = insertItemWithAllowedSlots(
                        target.handler(), toMove, false, target.allowedSlots());
                int moved = toMove.getCount() - uninserted.getCount();

                if (!uninserted.isEmpty()) {
                    ItemStack stillLeft = source.insertItem(slot, uninserted, false);
                    if (!stillLeft.isEmpty()) {
                        for (int fb = 0; fb < source.getSlots() && !stillLeft.isEmpty(); fb++) {
                            stillLeft = source.insertItem(fb, stillLeft, false);
                        }
                        if (!stillLeft.isEmpty()) {
                            LOGGER.error(
                                    "ITEM VOIDING PREVENTED in recipe robin: Could not return {} to source handler {}. "
                                            + "Forcing back into target as last resort.",
                                    stillLeft, source.getClass().getSimpleName());
                            insertItemWithAllowedSlots(target.handler(), stillLeft, false, null);
                        }
                    }
                }

                movedForEntry += moved;
            }

            totalMoved += movedForEntry;
            currentRemaining -= movedForEntry;

            if (currentRemaining <= 0) {
                currentEntryIdx++;
                if (currentEntryIdx < recipe.size()) {
                    currentRemaining = recipe.get(currentEntryIdx).amount();
                } else {
                    return new RecipeCursorResult(totalMoved, 0, 0, true);
                }
            } else {
                break;
            }
        }

        if (currentEntryIdx >= recipe.size()) {
            return new RecipeCursorResult(totalMoved, 0, 0, true);
        }

        return new RecipeCursorResult(totalMoved, currentEntryIdx, currentRemaining, false);
    }

    private static int executeMoveRecipeWithCursor(
            LogisticsNodeEntity sourceNode, int channelIndex,
            IItemHandler source, List<ItemTransferTarget> targets, int limit,
            ItemStack[] exportFilters, FilterMode exportFilterMode,
            boolean[] sourceAllowedSlots, HolderLookup.Provider provider) {

        List<RecipeEntry> recipe = buildRecipe(exportFilters, provider);

        if (recipe.isEmpty()) {
            return executeMove(source, targets, limit, exportFilters, exportFilterMode,
                    sourceAllowedSlots, provider);
        }

        if (targets.isEmpty())
            return 0;

        int cursorEntry = sourceNode.getRecipeCursorEntry(channelIndex);
        int cursorRemaining = sourceNode.getRecipeCursorRemaining(channelIndex);

        if (cursorEntry >= recipe.size() || cursorEntry < 0) {
            cursorEntry = 0;
            cursorRemaining = 0;
        }
        if (cursorRemaining <= 0) {
            cursorRemaining = recipe.get(cursorEntry).amount();
        }

        int totalMoved = 0;
        int remaining = limit;
        int targetsCompleted = 0;

        for (int t = 0; t < targets.size() && remaining > 0; t++) {
            ItemTransferTarget target = targets.get(t);

            RecipeCursorResult result = executeMoveRecipeToTargetWithCursor(
                    source, target, remaining, recipe,
                    sourceAllowedSlots, provider,
                    cursorEntry, cursorRemaining);

            totalMoved += result.moved();
            remaining -= result.moved();

            if (result.completed()) {
                targetsCompleted++;
                cursorEntry = 0;
                cursorRemaining = recipe.get(0).amount();
            } else {
                cursorEntry = result.entryIndex();
                cursorRemaining = result.entryRemaining();
                break;
            }
        }

        sourceNode.setRecipeCursor(channelIndex, cursorEntry, cursorRemaining);

        if (targetsCompleted > 0) {
            sourceNode.advanceRoundRobin(channelIndex, targets.size(), targetsCompleted);
        }

        return totalMoved;
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
            HolderLookup.Provider provider) {

        int remaining = limitMb;
        AmountConstraints amountConstraints = collectAmountConstraints(exportFilters, importFilters);

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
                if (!FilterLogic.matchesFluid(exportFilters, exportFilterMode, simulated, provider))
                    continue;
                if (!FilterLogic.matchesFluid(importFilters, importFilterMode, simulated, provider))
                    continue;
            }

            int allowedByAmount = getAllowedTransferByFluidAmountConstraints(source, target, simulated,
                    amountConstraints);
            if (amountConstraints.hasPerEntryAmounts) {
                int perEntry = getPerEntryFluidAmountLimit(simulated, exportFilters, importFilters, source, target);
                if (perEntry >= 0) {
                    allowedByAmount = Math.min(allowedByAmount, perEntry);
                }
            }
            if (allowedByAmount <= 0)
                continue;

            int request = Math.min(simulated.getAmount(), Math.min(remaining, allowedByAmount));
            int accepted = target.fill(simulated.copyWithAmount(request), IFluidHandler.FluidAction.SIMULATE);
            if (accepted <= 0)
                continue;

            int toMove = Math.min(accepted,
                    source.drain(simulated.copyWithAmount(accepted), IFluidHandler.FluidAction.SIMULATE).getAmount());
            if (toMove <= 0)
                continue;

            FluidStack drained = source.drain(simulated.copyWithAmount(toMove), IFluidHandler.FluidAction.EXECUTE);
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
        int exportThreshold = 0;
        boolean hasExportThreshold = false;
        boolean hasPerEntryAmounts = false;

        if (exportFilters != null) {
            for (ItemStack filter : exportFilters) {
                if (AmountFilterData.isAmountFilterItem(filter)) {
                    hasExportThreshold = true;
                    exportThreshold = Math.max(exportThreshold, AmountFilterData.getAmount(filter));
                }
                if (FilterItemData.hasAnyAmountEntries(filter)) {
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
                if (FilterItemData.hasAnyAmountEntries(filter)) {
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
            HolderLookup.Provider provider) {
        int allowed = Integer.MAX_VALUE;

        if (exportFilters != null) {
            for (ItemStack filter : exportFilters) {
                int threshold = FilterItemData.getItemAmountThresholdFull(filter, candidate, provider);
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
                int threshold = FilterItemData.getItemAmountThresholdFull(filter, candidate, provider);
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

    private static int getPerEntryFluidAmountLimit(FluidStack candidate, ItemStack[] exportFilters,
            ItemStack[] importFilters, IFluidHandler source, IFluidHandler target) {
        int allowed = Integer.MAX_VALUE;

        if (exportFilters != null) {
            for (ItemStack filter : exportFilters) {
                int threshold = FilterItemData.getFluidAmountThresholdFull(filter, candidate, null);
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
                int threshold = FilterItemData.getFluidAmountThresholdFull(filter, candidate, null);
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
}
