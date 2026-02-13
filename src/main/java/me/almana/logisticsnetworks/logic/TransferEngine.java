package me.almana.logisticsnetworks.logic;

import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.AmountFilterData;
import me.almana.logisticsnetworks.filter.NbtFilterData;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
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

    private record AmountConstraints(boolean hasExportThreshold, int exportThreshold,
            boolean hasImportThreshold, int importThreshold) {
    }

    public static boolean processNetwork(LogisticsNetwork network, MinecraftServer server) {
        if (network == null || server == null)
            return false;

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

        for (UUID nodeId : sortedUuids) {
            LogisticsNodeEntity node = findNode(server, nodeId);
            if (node != null && node.isValidNode()) {
                sortedNodes.add(node);
                dimensionalCache.put(node.getUUID(), NodeUpgradeData.hasDimensionalUpgrade(node));
                tierCache.put(node.getUUID(), NodeUpgradeData.getUpgradeTier(node));
            } else if (Config.debugMode) {
                LOGGER.debug("Node {} missing from world, skipping.", nodeId);
            }
        }

        if (sortedNodes.isEmpty())
            return false;

        Map<UUID, Integer> signalCache = buildSignalCache(sortedNodes);
        if (signalCache.isEmpty())
            return false;

        Map<Integer, List<ImportTarget>> itemImports = new HashMap<>();
        Map<Integer, List<ImportTarget>> fluidImports = new HashMap<>();
        Map<Integer, List<ImportTarget>> energyImports = new HashMap<>();

        populateImportCaches(sortedNodes, signalCache, itemImports, fluidImports, energyImports);

        boolean anyActivePotential = false;
        for (LogisticsNodeEntity sourceNode : sortedNodes) {
            if (processNode(sourceNode, itemImports, fluidImports, energyImports,
                    signalCache, dimensionalCache, tierCache)) {
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

    private static void populateImportCaches(List<LogisticsNodeEntity> nodes, Map<UUID, Integer> signalCache,
            Map<Integer, List<ImportTarget>> itemImports,
            Map<Integer, List<ImportTarget>> fluidImports,
            Map<Integer, List<ImportTarget>> energyImports) {
        for (LogisticsNodeEntity node : nodes) {
            int signal = signalCache.getOrDefault(node.getUUID(), 0);
            for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
                ChannelData ch = node.getChannel(i);
                if (ch != null && ch.isEnabled() && ch.getMode() == ChannelMode.IMPORT) {
                    if (isRedstoneActive(ch.getRedstoneMode(), signal)) {
                        switch (ch.getType()) {
                            case ITEM -> itemImports.computeIfAbsent(i, k -> new ArrayList<>())
                                    .add(new ImportTarget(node, ch, i));
                            case FLUID -> fluidImports.computeIfAbsent(i, k -> new ArrayList<>())
                                    .add(new ImportTarget(node, ch, i));
                            case ENERGY -> energyImports.computeIfAbsent(i, k -> new ArrayList<>())
                                    .add(new ImportTarget(node, ch, i));
                        }
                    }
                }
            }
        }
    }

    private static boolean processNode(LogisticsNodeEntity sourceNode,
            Map<Integer, List<ImportTarget>> itemImports,
            Map<Integer, List<ImportTarget>> fluidImports,
            Map<Integer, List<ImportTarget>> energyImports,
            Map<UUID, Integer> signalCache,
            Map<UUID, Boolean> dimensionalCache,
            Map<UUID, Integer> tierCache) {

        if (!sourceNode.isValidNode())
            return false;

        ServerLevel sourceLevel = (ServerLevel) sourceNode.level();
        long gameTime = sourceLevel.getGameTime();
        int redstoneSignal = signalCache.getOrDefault(sourceNode.getUUID(), 0);
        boolean hasActivePotential = false;
        int sourceTier = tierCache.getOrDefault(sourceNode.getUUID(), 0);

        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            ChannelData channel = sourceNode.getChannel(i);
            if (channel == null || !channel.isEnabled() || channel.getMode() != ChannelMode.EXPORT)
                continue;
            if (!isRedstoneActive(channel.getRedstoneMode(), redstoneSignal))
                continue;

            List<ImportTarget> targets = switch (channel.getType()) {
                case FLUID -> fluidImports.get(i);
                case ENERGY -> energyImports.get(i);
                default -> itemImports.get(i);
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
                default ->
                    transferItems(sourceNode, sourceLevel, channel, targets, effectiveBatchSize, dimensionalCache);
            };

            if (result < 0)
                continue;

            updateBackoff(sourceNode, channel, i, result > 0, gameTime, sourceTier, targets.size());
        }

        return hasActivePotential;
    }

    private static boolean isOnCooldown(LogisticsNodeEntity node, ChannelData channel, int index, int tier,
            long gameTime) {
        long lastRun = node.getLastExecution(index);
        int configuredDelay = channel.getType() == ChannelType.ENERGY ? 1
                : Math.max(channel.getTickDelay(), NodeUpgradeData.getMinTickDelay(tier));
        int effectiveDelay = Math.max(configuredDelay, (int) node.getBackoffTicks(index));
        return (gameTime - lastRun) < effectiveDelay;
    }

    private static int getBatchLimit(ChannelType type, int tier) {
        return switch (type) {
            case FLUID -> NodeUpgradeData.getFluidOperationCapMb(tier);
            case ENERGY -> NodeUpgradeData.getEnergyOperationCap(tier);
            default -> NodeUpgradeData.getItemOperationCap(tier);
        };
    }

    private static void updateBackoff(LogisticsNodeEntity node, ChannelData channel, int index, boolean success,
            long gameTime, int tier, int targetCount) {
        node.setLastExecution(index, gameTime);
        int configuredDelay = channel.getType() == ChannelType.ENERGY ? 1
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
            float maxBackoff = channel.getType() == ChannelType.ENERGY ? BACKOFF_MAX_TICKS_ENERGY : BACKOFF_MAX_TICKS;
            float curBackoff = Math.max(node.getBackoffTicks(index), configuredDelay);
            node.setBackoffTicks(index, Math.min(maxBackoff, curBackoff * BACKOFF_MULTIPLIER));
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
            ChannelData exportChannel, List<ImportTarget> targets, int batchLimit,
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

            if (executeMove(sourceHandler, targetHandler, batchLimit,
                    exportChannel.getFilterItems(), exportChannel.getFilterMode(),
                    target.channel.getFilterItems(), target.channel.getFilterMode(),
                    sourceLevel.registryAccess())) {
                return 1;
            }
        }
        return anyReachable ? 0 : -1;
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

            if (executeFluidMove(sourceHandler, targetHandler, batchLimitMb,
                    exportChannel.getFilterItems(), exportChannel.getFilterMode(),
                    target.channel.getFilterItems(), target.channel.getFilterMode(),
                    sourceLevel.registryAccess())) {
                return 1;
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
        return remaining < batchLimitRF ? 1 : 0;
    }

    private static boolean canReach(LogisticsNodeEntity source, LogisticsNodeEntity target, boolean sourceDim,
            Map<UUID, Boolean> dimCache) {
        if (source.level().dimension().equals(target.level().dimension()))
            return true;
        return sourceDim && dimCache.getOrDefault(target.getUUID(), false);
    }

    private static boolean executeMove(IItemHandler source, IItemHandler target, int limit,
            ItemStack[] exportFilters, FilterMode exportFilterMode,
            ItemStack[] importFilters, FilterMode importFilterMode,
            HolderLookup.Provider provider) {

        int remaining = limit;
        boolean movedAny = false;
        AmountConstraints amountConstraints = collectAmountConstraints(exportFilters, importFilters);
        boolean hasNbtFilter = FilterLogic.hasConfiguredItemNbtFilter(exportFilters)
                || FilterLogic.hasConfiguredItemNbtFilter(importFilters);

        for (int slot = 0; slot < source.getSlots() && remaining > 0; slot++) {
            ItemStack extracted = source.extractItem(slot, remaining, true);
            if (extracted.isEmpty())
                continue;
            if (extracted.is(ModTags.RESOURCE_BLACKLIST_ITEMS))
                continue;

            CompoundTag candidateComponents = (provider != null && hasNbtFilter)
                    ? NbtFilterData.getSerializedComponents(extracted, provider)
                    : null;

            if (provider != null) {
                if (!FilterLogic.matchesItem(exportFilters, exportFilterMode, extracted, provider, candidateComponents))
                    continue;
                if (!FilterLogic.matchesItem(importFilters, importFilterMode, extracted, provider, candidateComponents))
                    continue;
            }

            int allowedByAmount = getAllowedTransferByAmountConstraints(source, target, extracted, amountConstraints);
            if (allowedByAmount <= 0)
                continue;

            int allowed = Math.min(extracted.getCount(), Math.min(remaining, allowedByAmount));
            ItemStack capped = extracted.copyWithCount(allowed);
            ItemStack leftover = ItemHandlerHelper.insertItemStacked(target, capped, true);
            int accepted = capped.getCount() - leftover.getCount();

            if (accepted > 0) {
                ItemStack toMove = source.extractItem(slot, accepted, false);
                ItemHandlerHelper.insertItemStacked(target, toMove, false);
                remaining -= accepted;
                movedAny = true;
            }
        }
        return movedAny;
    }

    private static boolean executeFluidMove(IFluidHandler source, IFluidHandler target, int limitMb,
            ItemStack[] exportFilters, FilterMode exportFilterMode,
            ItemStack[] importFilters, FilterMode importFilterMode,
            HolderLookup.Provider provider) {

        int remaining = limitMb;
        boolean movedAny = false;
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
                movedAny = true;
            }
        }
        return movedAny;
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

        if (exportFilters != null) {
            for (ItemStack filter : exportFilters) {
                if (AmountFilterData.isAmountFilterItem(filter)) {
                    hasExportThreshold = true;
                    exportThreshold = Math.max(exportThreshold, AmountFilterData.getAmount(filter));
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
            }
        }

        return new AmountConstraints(hasExportThreshold, exportThreshold, hasImportThreshold, importThreshold);
    }

    private static int getAllowedTransferByAmountConstraints(IItemHandler source, IItemHandler target,
            ItemStack candidate, AmountConstraints constraints) {
        int allowed = Integer.MAX_VALUE;

        if (constraints.hasExportThreshold) {
            int sourceCount = countMatchingItems(source, candidate);
            int exportCap = sourceCount - constraints.exportThreshold;
            if (exportCap <= 0)
                return 0;
            allowed = Math.min(allowed, exportCap);
        }

        if (constraints.hasImportThreshold) {
            int targetCount = countMatchingItems(target, candidate);
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

    private static int countMatchingItems(IItemHandler handler, ItemStack candidate) {
        int count = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && ItemStack.isSameItem(stack, candidate)) {
                count += stack.getCount();
            }
        }
        return count;
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
}
