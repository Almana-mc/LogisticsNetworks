package me.almana.logisticsnetworks.logic;

import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.Config;
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
import net.minecraft.world.level.block.Block;

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

public class TransferEngine {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float BACKOFF_MULTIPLIER = 1.3f;
    private static final float BACKOFF_DECAY_DIVISOR = 3f;
    private static final float BACKOFF_MAX_TICKS_ENERGY = 5f;

    private record ImportTarget(LogisticsNodeEntity node, ChannelData channel, int channelIndex) {
    }

    private record ItemTransferTarget(ResourceHandler<ItemResource> handler, ItemStack[] importFilters,
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

        Set<UUID> nodeUuids = network.getNodeUuids();
        if (nodeUuids.isEmpty())
            return Long.MAX_VALUE;

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
        TransferCapabilityCache capCache = new TransferCapabilityCache();

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

    @SuppressWarnings("unchecked")
    private static List<ImportTarget>[] resolveCache(List<NodeRef>[] cache,
            Map<UUID, LogisticsNodeEntity> nodeCache,
            Map<UUID, Integer> signalCache) {
        List<ImportTarget>[] resolved = new List[9];
        for (int i = 0; i < 9; i++) {
            List<NodeRef> cachedNodes = cache[i];
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

            targets = orderTargets(targets, channel.getDistributionMode(), sourceNode);

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
                return targets;
            }
            default -> {
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
        ResourceHandler<ItemResource> sourceHandler = capCache.findItemHandler(sourceLevel, sourcePos, exportChannel.getIoDirection());
        if (sourceHandler == null)
            return -1;

        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        boolean anyReachable = false;
        List<ItemTransferTarget> reachableTargets = new ArrayList<>(targets.size());
        ItemStack[] exportFilters = exportChannel.getFilterItems();
        boolean[] sourceAllowedSlots = null;

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

            ResourceHandler<ItemResource> targetHandler = capCache.findItemHandler(targetLevel, targetPos, target.channel.getIoDirection());
            if (targetHandler == null)
                continue;

            ItemStack[] importFilters = target.channel.getFilterItems();
            boolean[] targetAllowedSlots = null;

            reachableTargets.add(new ItemTransferTarget(
                    targetHandler,
                    importFilters,
                    target.channel.getFilterMode(),
                    TransferAmountRules.collect(exportFilters, importFilters),
                    FilterLogic.hasConfiguredItemNbtFilter(importFilters),
                    targetAllowedSlots,
                    FilterLogic.hasConfiguredSlotMapping(importFilters, null)));
        }
        if (!anyReachable)
            return -1;
        if (reachableTargets.isEmpty())
            return 0;

        return executeMove(sourceHandler, reachableTargets, batchLimit,
                exportFilters, exportChannel.getFilterMode(),
                sourceAllowedSlots,
                sourceLevel.registryAccess(),
                sourceLevel, sourcePos);
    }

    private static int transferFluids(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, List<ImportTarget> targets, int batchLimitMb,
            Map<UUID, Boolean> dimensionalCache, TransferCapabilityCache capCache) {

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceLevel.isLoaded(sourcePos))
            return -1;
        ResourceHandler<FluidResource> sourceHandler = capCache.findFluidHandler(sourceLevel, sourcePos, exportChannel.getIoDirection());
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

            ResourceHandler<FluidResource> targetHandler = capCache.findFluidHandler(targetLevel, targetPos, target.channel.getIoDirection());
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
            Map<UUID, Boolean> dimensionalCache, TransferCapabilityCache capCache) {

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceLevel.isLoaded(sourcePos))
            return -1;
        EnergyHandler sourceHandler = capCache.findEnergyHandler(sourceLevel, sourcePos, exportChannel.getIoDirection());
        if (sourceHandler == null)
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

            EnergyHandler targetHandler = capCache.findEnergyHandler(targetLevel, targetPos, target.channel.getIoDirection());
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

    private static int executeMove(ResourceHandler<ItemResource> source, List<ItemTransferTarget> targets, int limit,
            ItemStack[] exportFilters, FilterMode exportFilterMode,
            boolean[] sourceAllowedSlots,
            HolderLookup.Provider provider,
            ServerLevel sourceLevel, BlockPos sourcePos) {

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

        while (remaining > 0 && openTargetCount > 0) {
            movedAny = false;

            for (int targetIndex = 0; targetIndex < targets.size() && remaining > 0; targetIndex++) {
                if (!openTargets[targetIndex]) {
                    continue;
                }

                ItemTransferTarget target = targets.get(targetIndex);
                boolean movedForTarget = false;

                for (int slot = 0; slot < source.size() && remaining > 0; slot++) {
                    if (sourceAllowedSlots != null
                            && (slot >= sourceAllowedSlots.length || !sourceAllowedSlots[slot])) {
                        continue;
                    }

                    ItemStack extracted = extractItem(source, slot, remaining, true);
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

                    int targetAccepted;
                    int droppedToWorld = 0;
                    ItemStack dropStack = ItemStack.EMPTY;

                    try (var tx = Transaction.openRoot()) {
                        ItemStack toMove = extractItem(source, slot, acceptableCount, tx);
                        if (toMove.isEmpty()) {
                            continue;
                        }

                        ItemStack uninserted = insertItemWithAllowedSlots(target.handler(), toMove, tx,
                                importAllowedSlots);
                        targetAccepted = toMove.getCount() - uninserted.getCount();

                        if (!uninserted.isEmpty()) {
                            ItemStack stillLeft = insertItem(source, slot, uninserted, tx);
                            if (!stillLeft.isEmpty()) {
                                for (int fallback = 0; fallback < source.size() && !stillLeft.isEmpty(); fallback++) {
                                    stillLeft = insertItem(source, fallback, stillLeft, tx);
                                }
                                if (!stillLeft.isEmpty()) {
                                    ItemStack forcedRemainder = insertItemWithAllowedSlots(target.handler(), stillLeft,
                                            tx, importAllowedSlots);
                                    int forcedIn = stillLeft.getCount() - forcedRemainder.getCount();
                                    targetAccepted += forcedIn;
                                    if (!forcedRemainder.isEmpty()) {
                                        if (Config.debugMode) LOGGER.error("ITEM VOIDING PREVENTED: Could not return {} to source or fit into "
                                                + "target slot mask. Dropping at source pos {}.",
                                                forcedRemainder, sourcePos);
                                        droppedToWorld = forcedRemainder.getCount();
                                        dropStack = forcedRemainder.copy();
                                    }
                                }
                            }
                        }

                        tx.commit();
                    }

                    if (!dropStack.isEmpty()) {
                        Block.popResource(sourceLevel, sourcePos, dropStack);
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

    private static ItemStack extractItem(ResourceHandler<ItemResource> handler, int slot, int amount, boolean simulate) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }

        try (var tx = Transaction.openRoot()) {
            ItemStack extracted = extractItem(handler, slot, amount, tx);
            if (!simulate) {
                tx.commit();
            }
            return extracted;
        }
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

    private static ItemStack insertItem(ResourceHandler<ItemResource> handler, int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        try (var tx = Transaction.openRoot()) {
            ItemStack remaining = insertItem(handler, slot, stack, tx);
            if (!simulate) {
                tx.commit();
            }
            return remaining;
        }
    }

    private static ItemStack insertItem(ResourceHandler<ItemResource> handler, int slot, ItemStack stack,
            TransactionContext transaction) {
        return ItemUtil.insertItemReturnRemaining(handler, slot, stack, false, transaction);
    }

    private static int fillFluid(ResourceHandler<FluidResource> handler, FluidStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return 0;
        }

        try (var tx = Transaction.openRoot()) {
            int inserted = fillFluid(handler, stack, tx);
            if (!simulate) {
                tx.commit();
            }
            return inserted;
        }
    }

    private static int fillFluid(ResourceHandler<FluidResource> handler, FluidStack stack,
            TransactionContext transaction) {
        if (stack.isEmpty()) {
            return 0;
        }

        return handler.insert(FluidResource.of(stack), stack.getAmount(), transaction);
    }

    private static FluidStack drainFluid(ResourceHandler<FluidResource> handler, FluidStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return FluidStack.EMPTY;
        }

        try (var tx = Transaction.openRoot()) {
            FluidStack extracted = drainFluid(handler, stack, tx);
            if (!simulate) {
                tx.commit();
            }
            return extracted;
        }
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

    private static ItemStack insertItemWithAllowedSlots(ResourceHandler<ItemResource> handler, ItemStack stack, boolean simulate,
            boolean[] allowedSlots) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        try (var tx = Transaction.openRoot()) {
            ItemStack remaining = insertItemWithAllowedSlots(handler, stack, tx, allowedSlots);
            if (!simulate) {
                tx.commit();
            }
            return remaining;
        }
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
        TransferAmountRules.Constraints amountConstraints = TransferAmountRules.collect(exportFilters, importFilters);

        for (int tank = 0; tank < source.size() && remaining > 0; tank++) {
            FluidStack tankFluid = FluidUtil.getStack(source, tank);
            if (tankFluid.isEmpty())
                continue;
            if (tankFluid.getFluid().builtInRegistryHolder().is(ModTags.RESOURCE_BLACKLIST_FLUIDS))
                continue;

            int requestFromTank = Math.min(remaining, tankFluid.getAmount());
            FluidStack simulated = drainFluid(source, tankFluid.copyWithAmount(requestFromTank), true);
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
            int accepted = fillFluid(target, simulated.copyWithAmount(request), true);
            if (accepted <= 0)
                continue;

            int toMove = Math.min(accepted,
                    drainFluid(source, simulated.copyWithAmount(accepted), true).getAmount());
            if (toMove <= 0)
                continue;

            int filled;
            try (var tx = Transaction.openRoot()) {
                FluidStack drained = drainFluid(source, simulated.copyWithAmount(toMove), tx);
                if (drained.isEmpty()) {
                    continue;
                }

                filled = fillFluid(target, drained, tx);
                if (filled < drained.getAmount()) {
                    int rollbackAmount = drained.getAmount() - filled;
                    int returned = fillFluid(source, drained.copyWithAmount(rollbackAmount), tx);
                    if (returned < rollbackAmount) {
                        if (Config.debugMode) LOGGER.error("FLUID VOIDING: Source rejected rollback of {} mB ({}). {} mB lost.",
                                rollbackAmount - returned, drained.getFluid(), rollbackAmount - returned);
                    }
                }

                tx.commit();
            }

            if (filled > 0) {
                remaining -= filled;
            }
        }
        return limitMb - remaining;
    }

    private static int executeEnergyMove(EnergyHandler source, EnergyHandler target, int limitRF) {
        return EnergyHandlerUtil.move(source, target, limitRF, null);
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
}
