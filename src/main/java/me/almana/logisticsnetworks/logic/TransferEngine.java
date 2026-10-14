package me.almana.logisticsnetworks.logic;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.CandidateComponents;
import me.almana.logisticsnetworks.integration.ars.ArsCompat;
import me.almana.logisticsnetworks.integration.ars.SourceTransferHelper;
import me.almana.logisticsnetworks.integration.mekanism.ChemicalTransferHelper;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import mekanism.api.chemical.IChemicalHandler;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import me.almana.logisticsnetworks.util.ItemStackCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
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
        private final Map<Long, Object> chemicals = new HashMap<>();
        private static final Object ABSENT = new Object();

        IItemHandler getItemHandler(ServerLevel level, BlockPos pos, Direction dir) {
            long key = capKey(level, pos, dir);
            Object cached = items.get(key);
            if (cached == ABSENT) return null;
            if (cached != null) return (IItemHandler) cached;
            BlockEntity be = level.getBlockEntity(pos);
            IItemHandler handler = be != null ? be.getCapability(ForgeCapabilities.ITEM_HANDLER, dir).orElse(null) : null;
            items.put(key, handler != null ? handler : ABSENT);
            return handler;
        }

        IFluidHandler getFluidHandler(ServerLevel level, BlockPos pos, Direction dir) {
            long key = capKey(level, pos, dir);
            Object cached = fluids.get(key);
            if (cached == ABSENT) return null;
            if (cached != null) return (IFluidHandler) cached;
            BlockEntity be = level.getBlockEntity(pos);
            IFluidHandler handler = be != null ? be.getCapability(ForgeCapabilities.FLUID_HANDLER, dir).orElse(null) : null;
            fluids.put(key, handler != null ? handler : ABSENT);
            return handler;
        }

        IEnergyStorage getEnergyHandler(ServerLevel level, BlockPos pos, Direction dir) {
            long key = capKey(level, pos, dir);
            Object cached = energy.get(key);
            if (cached == ABSENT) return null;
            if (cached != null) return (IEnergyStorage) cached;
            BlockEntity be = level.getBlockEntity(pos);
            IEnergyStorage handler = be != null ? be.getCapability(ForgeCapabilities.ENERGY, dir).orElse(null) : null;
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

        @SuppressWarnings("rawtypes")
        IChemicalHandler findChemicalHandler(ServerLevel level, BlockPos pos, @Nullable Direction dir) {
            long key = capKey(level, pos, dir == null ? Direction.DOWN : dir);
            if (dir == null) key ^= 0x1L << 62;
            Object cached = chemicals.get(key);
            if (cached == ABSENT) return null;
            if (cached != null) return (IChemicalHandler) cached;
            IChemicalHandler handler = ChemicalTransferHelper.getHandler(level, pos, dir);
            chemicals.put(key, handler != null ? handler : ABSENT);
            return handler;
        }
    }

    private record ImportTarget(LogisticsNodeEntity node, ChannelData channel, int channelIndex) {
    }

    private record ItemTransferTarget(IItemHandler handler, ItemStack[] importFilters,
            FilterMode importFilterMode, AmountConstraints constraints, boolean hasItemNbtFilter,
            boolean[] allowedSlots, boolean hasImportSlotMapping) {
    }

    private record ItemKey(Item item, @Nullable CompoundTag tag) {
    }

    private record AmountConstraints(boolean hasExportThreshold, int exportThreshold,
            boolean hasImportThreshold, int importThreshold,
            boolean hasPerEntryAmounts) {
    }

    private record NetworkContext(
            List<LogisticsNodeEntity> sortedNodes,
            Map<UUID, Integer> signalCache,
            List<ImportTarget>[] itemImports,
            List<ImportTarget>[] fluidImports,
            List<ImportTarget>[] energyImports,
            List<ImportTarget>[] chemicalImports,
            List<ImportTarget>[] sourceImports,
            Map<UUID, Boolean> dimensionalCache,
            Map<UUID, Integer> tierCache) {
    }

    public static long processNetwork(LogisticsNetwork network, MinecraftServer server) {
        if (network == null || server == null)
            return Long.MAX_VALUE;

        NetworkContext context = prepareNetwork(network, server);
        if (context == null)
            return Long.MAX_VALUE;

        NetworkRegistry registry = NetworkRegistry.get((ServerLevel) server.overworld());
        boolean telemetryActive = registry.getTelemetryManager().isActive(network.getId());
        CapCache capCache = new CapCache();

        long minWakeDelta = Long.MAX_VALUE;
        for (LogisticsNodeEntity sourceNode : context.sortedNodes()) {
            long delta = processNode(sourceNode, context.itemImports(), context.fluidImports(),
                    context.energyImports(), context.chemicalImports(), context.sourceImports(),
                    context.signalCache(), context.dimensionalCache(), context.tierCache(),
                    telemetryActive, capCache);
            if (delta < minWakeDelta) {
                minWakeDelta = delta;
            }
        }

        return minWakeDelta;
    }

    @Nullable
    private static NetworkContext prepareNetwork(LogisticsNetwork network, MinecraftServer server) {
        if (network.isCacheDirty()) {
            network.rebuildCache(NetworkRegistry.get((ServerLevel) server.overworld()));
            network.clearCacheDirty();
        }

        List<UUID> sortedUuids = network.getSortedUuids();
        if (sortedUuids.isEmpty())
            return null;

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
            return null;

        Map<UUID, Integer> signalCache = buildSignalCache(sortedNodes);
        if (signalCache.isEmpty())
            return null;

        return new NetworkContext(sortedNodes, signalCache,
                resolveCache(network.getItemImports(), nodeCache, signalCache),
                resolveCache(network.getFluidImports(), nodeCache, signalCache),
                resolveCache(network.getEnergyImports(), nodeCache, signalCache),
                resolveCache(network.getChemicalImports(), nodeCache, signalCache),
                resolveCache(network.getSourceImports(), nodeCache, signalCache),
                network.getDimensionalCache(), network.getTierCache());
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
            CapCache capCache) {

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

            targets = orderTargets(targets, channel, sourceNode);

            int configuredBatch = getBatchLimit(channel.getType(), sourceTier);
            int effectiveBatchSize = Math.max(1, Math.min(channel.getBatchSize(), configuredBatch));

            int result = switch (channel.getType()) {
                case FLUID ->
                    transferFluids(sourceNode, sourceLevel, channel, targets, effectiveBatchSize, dimensionalCache, capCache);
                case ENERGY ->
                    transferEnergy(sourceNode, sourceLevel, channel, targets, effectiveBatchSize, dimensionalCache, capCache);
                case CHEMICAL ->
                    transferChemicals(sourceNode, sourceLevel, channel, targets, effectiveBatchSize, dimensionalCache, capCache);
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

    private static List<ImportTarget> orderTargets(List<ImportTarget> targets, ChannelData exportChannel,
            LogisticsNodeEntity sourceNode) {
        if (targets.size() <= 1)
            return targets;

        switch (exportChannel.getDistributionMode()) {
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
            case PRIORITY_ROBIN -> {
                return PriorityRobin.rotate(targets, exportChannel.getRobinCursor(),
                        t -> t.channel.getPriority(), t -> t.node.getUUID());
            }
            default -> {
                return targets;
            }
        }
    }

    private static void markServed(ChannelData exportChannel, ImportTarget target) {
        exportChannel.setRobinCursor(new PriorityRobin.Cursor(target.channel.getPriority(), target.node.getUUID()));
    }

    private static int transferItems(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, int channelIndex, List<ImportTarget> targets, int batchLimit,
            Map<UUID, Boolean> dimensionalCache, CapCache capCache) {

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceLevel.isLoaded(sourcePos))
            return -1;
        IItemHandler sourceHandler = capCache.findItemHandler(sourceLevel, sourcePos, exportChannel.getIoDirection());
        if (sourceHandler == null)
            return 0;

        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        boolean anyReachable = false;
        List<ItemTransferTarget> reachableTargets = new ArrayList<>(targets.size());
        List<ImportTarget> reachableRefs = new ArrayList<>(targets.size());
        ItemStack[] exportFilters = exportChannel.getFilterItems();
        boolean[] sourceAllowedSlots = null;
        FilterItemData.ReadCache filterReadCache = FilterItemData.createReadCache();

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
            boolean[] targetAllowedSlots = null;

            reachableTargets.add(new ItemTransferTarget(
                    targetHandler,
                    importFilters,
                    target.channel.getFilterMode(),
                    collectAmountConstraints(exportFilters, importFilters),
                    FilterLogic.hasConfiguredItemNbtFilter(importFilters, filterReadCache),
                    targetAllowedSlots,
                    FilterLogic.hasConfiguredSlotMapping(importFilters, filterReadCache)));
            reachableRefs.add(target);
        }
        if (!anyReachable)
            return -1;
        if (reachableTargets.isEmpty())
            return 0;

        Map<Item, Integer> sourceItemCounts = hasAmountConstraints(reachableTargets)
                ? buildItemCountCache(sourceHandler)
                : null;
        if (exportChannel.canRotateResources()) {
            return rotateItems(sourceHandler, reachableTargets, reachableRefs, exportChannel, batchLimit,
                    sourceAllowedSlots, sourceLevel, sourcePos, filterReadCache, sourceItemCounts);
        }
        if (exportChannel.getDistributionMode() == DistributionMode.ROUND_ROBIN) {
            return dealItems(sourceHandler, reachableTargets, batchLimit, exportFilters,
                    exportChannel.getFilterMode(), sourceAllowedSlots, sourceLevel, sourcePos, filterReadCache,
                    sourceItemCounts);
        }
        if (exportChannel.getDistributionMode() == DistributionMode.PRIORITY_ROBIN) {
            return robinItems(sourceHandler, reachableTargets, reachableRefs, exportChannel, batchLimit,
                    sourceAllowedSlots, sourceLevel, sourcePos, filterReadCache, sourceItemCounts);
        }

        return executeMove(sourceHandler, reachableTargets, batchLimit,
                exportFilters, exportChannel.getFilterMode(),
                sourceAllowedSlots,
                sourceLevel.registryAccess(),
                sourceLevel, sourcePos, filterReadCache, sourceItemCounts,
                sourceItemCounts == null ? null : buildTargetItemCounts(reachableTargets), false, null, null);
    }

    private static int dealItems(IItemHandler source, List<ItemTransferTarget> targets, int batchLimit,
            ItemStack[] exportFilters, FilterMode exportFilterMode, boolean[] sourceAllowedSlots,
            ServerLevel sourceLevel, BlockPos sourcePos, FilterItemData.ReadCache filterReadCache,
            @Nullable Map<Item, Integer> sourceItemCounts) {
        Map<ItemKey, IntArrayList> groups = new LinkedHashMap<>();
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (!stack.isEmpty())
                groups.computeIfAbsent(new ItemKey(stack.getItem(), stack.getTag()), key -> new IntArrayList())
                        .add(slot);
        }
        HolderLookup.Provider provider = sourceLevel.registryAccess();
        boolean hasExportNbtFilter = FilterLogic.hasConfiguredItemNbtFilter(exportFilters, filterReadCache);
        List<Map<Item, Integer>> targetItemCounts = null;
        int moved = 0;
        // Split per item type
        for (IntArrayList group : groups.values()) {
            if (moved >= batchLimit)
                break;
            int[] slots = group.toIntArray();
            ItemStack resource = source.getStackInSlot(slots[0]).copy();
            int budget = exportableStock(source, slots, sourceAllowedSlots, exportFilters, exportFilterMode,
                    provider, filterReadCache, resource, sourceItemCounts, batchLimit - moved, hasExportNbtFilter);
            if (budget <= 0)
                continue;
            if (sourceItemCounts != null && targetItemCounts == null)
                targetItemCounts = buildTargetItemCounts(targets);
            moved += executeMove(source, targets, budget, exportFilters, exportFilterMode, sourceAllowedSlots,
                    provider, sourceLevel, sourcePos, filterReadCache, sourceItemCounts, targetItemCounts, true,
                    slots, resource);
        }
        return moved;
    }

    private static int robinItems(IItemHandler source, List<ItemTransferTarget> targets, List<ImportTarget> refs,
            ChannelData exportChannel, int batchLimit, boolean[] sourceAllowedSlots, ServerLevel sourceLevel,
            BlockPos sourcePos, FilterItemData.ReadCache filterReadCache,
            @Nullable Map<Item, Integer> sourceItemCounts) {
        // Receiver-outer avoids resource lockstep
        for (int i = 0; i < targets.size(); i++) {
            List<ItemTransferTarget> one = List.of(targets.get(i));
            int moved = executeMove(source, one, batchLimit, exportChannel.getFilterItems(),
                    exportChannel.getFilterMode(), sourceAllowedSlots, sourceLevel.registryAccess(), sourceLevel,
                    sourcePos, filterReadCache, sourceItemCounts,
                    sourceItemCounts == null ? null : buildTargetItemCounts(one), false, null, null);
            if (moved > 0) {
                markServed(exportChannel, refs.get(i));
                return moved;
            }
        }
        return 0;
    }

    private static int rotateItems(IItemHandler source, List<ItemTransferTarget> targets, List<ImportTarget> refs,
            ChannelData exportChannel, int batchLimit, boolean[] sourceAllowedSlots, ServerLevel sourceLevel,
            BlockPos sourcePos, FilterItemData.ReadCache filterReadCache,
            @Nullable Map<Item, Integer> sourceItemCounts) {
        DistributionMode mode = exportChannel.getDistributionMode();
        if (mode != DistributionMode.PRIORITY_ROBIN) {
            return rotateItemsOnce(source, targets, exportChannel, batchLimit, mode == DistributionMode.ROUND_ROBIN,
                    sourceAllowedSlots, sourceLevel, sourcePos, filterReadCache, sourceItemCounts);
        }
        // Receiver-outer avoids resource lockstep
        for (int i = 0; i < targets.size(); i++) {
            int moved = rotateItemsOnce(source, List.of(targets.get(i)), exportChannel, batchLimit, false,
                    sourceAllowedSlots, sourceLevel, sourcePos, filterReadCache, sourceItemCounts);
            if (moved > 0) {
                markServed(exportChannel, refs.get(i));
                return moved;
            }
        }
        return 0;
    }

    private static int rotateItemsOnce(IItemHandler source, List<ItemTransferTarget> targets,
            ChannelData exportChannel, int batchLimit, boolean equal, boolean[] sourceAllowedSlots,
            ServerLevel sourceLevel, BlockPos sourcePos, FilterItemData.ReadCache filterReadCache,
            @Nullable Map<Item, Integer> sourceItemCounts) {
        HolderLookup.Provider provider = sourceLevel.registryAccess();
        ItemStack[] filters = exportChannel.getFilterItems();
        FilterMode filterMode = exportChannel.getFilterMode();
        boolean nbtFilter = FilterLogic.hasConfiguredItemNbtFilter(filters, filterReadCache);
        List<Map<Item, Integer>> targetCounts = sourceItemCounts == null ? null : buildTargetItemCounts(targets);
        ItemResourceOrder.Result result = ItemResourceOrder.move(source, exportChannel.getItemResourceCursor(),
                slots -> {
                    ItemStack resource = source.getStackInSlot(slots[0]).copy();
                    int budget = equal ? exportableStock(source, slots, sourceAllowedSlots, filters, filterMode,
                            provider, filterReadCache, resource, sourceItemCounts, batchLimit, nbtFilter) : batchLimit;
                    return budget <= 0 ? 0 : executeMove(source, targets, budget, filters, filterMode,
                            sourceAllowedSlots, provider, sourceLevel, sourcePos, filterReadCache, sourceItemCounts,
                            targetCounts, equal, slots, resource);
                });
        if (result.cursor() != null)
            exportChannel.setItemResourceCursor(result.cursor());
        return result.moved();
    }

    private static boolean hasAmountConstraints(List<ItemTransferTarget> targets) {
        for (ItemTransferTarget t : targets) {
            if (t.constraints().hasExportThreshold || t.constraints().hasImportThreshold
                    || t.constraints().hasPerEntryAmounts)
                return true;
        }
        return false;
    }

    private static List<Map<Item, Integer>> buildTargetItemCounts(List<ItemTransferTarget> targets) {
        List<Map<Item, Integer>> counts = new ArrayList<>(targets.size());
        for (ItemTransferTarget t : targets) {
            counts.add(t.constraints().hasImportThreshold || t.constraints().hasPerEntryAmounts
                    ? buildItemCountCache(t.handler())
                    : null);
        }
        return counts;
    }

    private record FluidTarget(IFluidHandler handler, ImportTarget ref) {
    }

    private static int transferFluids(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, List<ImportTarget> targets, int batchLimitMb,
            Map<UUID, Boolean> dimensionalCache, CapCache capCache) {

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceLevel.isLoaded(sourcePos))
            return -1;
        IFluidHandler sourceHandler = capCache.findFluidHandler(sourceLevel, sourcePos, exportChannel.getIoDirection());
        if (sourceHandler == null)
            return 0;

        List<FluidTarget> resolved = new ArrayList<>(targets.size());
        if (!resolveFluidTargets(sourceNode, targets, dimensionalCache, capCache, resolved))
            return -1;
        FilterItemData.ReadCache cache = FilterItemData.createReadCache();
        HolderLookup.Provider provider = sourceLevel.registryAccess();
        if (exportChannel.canRotateResources())
            return rotateFluids(sourceHandler, resolved, exportChannel, batchLimitMb, provider, cache);
        if (exportChannel.getDistributionMode() == DistributionMode.ROUND_ROBIN)
            return dealFluids(sourceHandler, resolved, batchLimitMb, exportChannel, provider, cache);
        return pushFluids(sourceHandler, resolved, exportChannel, batchLimitMb, FluidStack.EMPTY, provider, cache);
    }

    // False when no target is reachable
    private static boolean resolveFluidTargets(LogisticsNodeEntity sourceNode, List<ImportTarget> targets,
            Map<UUID, Boolean> dimensionalCache, CapCache capCache, List<FluidTarget> out) {
        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        boolean anyReachable = false;
        for (ImportTarget target : targets) {
            if (target.node.getUUID().equals(sourceNode.getUUID()) || !target.node.isValidNode()
                    || !canReach(sourceNode, target.node, sourceDimensional, dimensionalCache))
                continue;
            anyReachable = true;
            ServerLevel targetLevel = (ServerLevel) target.node.level();
            BlockPos targetPos = target.node.getAttachedPos();
            if (!targetLevel.isLoaded(targetPos))
                continue;
            IFluidHandler handler = capCache.findFluidHandler(targetLevel, targetPos, target.channel.getIoDirection());
            if (handler != null)
                out.add(new FluidTarget(handler, target));
        }
        return anyReachable;
    }

    private static int pushFluids(IFluidHandler source, List<FluidTarget> targets, ChannelData exportChannel,
            int limit, FluidStack resource, HolderLookup.Provider provider, FilterItemData.ReadCache cache) {
        boolean robin = exportChannel.getDistributionMode() == DistributionMode.PRIORITY_ROBIN;
        int remaining = limit;
        for (FluidTarget target : targets) {
            if (remaining <= 0)
                break;
            int filled = executeFluidMove(source, target.handler(), remaining, exportChannel.getFilterItems(),
                    exportChannel.getFilterMode(), target.ref().channel.getFilterItems(),
                    target.ref().channel.getFilterMode(), provider, cache, resource);
            remaining -= filled;
            if (filled > 0 && robin) {
                markServed(exportChannel, target.ref());
                break;
            }
        }
        return limit - remaining;
    }

    private static int rotateFluids(IFluidHandler source, List<FluidTarget> targets, ChannelData exportChannel,
            int limit, HolderLookup.Provider provider, FilterItemData.ReadCache cache) {
        if (exportChannel.getDistributionMode() != DistributionMode.PRIORITY_ROBIN)
            return rotateFluidsOnce(source, targets, exportChannel, limit, provider, cache);
        // Receiver-outer avoids resource lockstep
        for (FluidTarget target : targets) {
            int moved = rotateFluidsOnce(source, List.of(target), exportChannel, limit, provider, cache);
            if (moved > 0)
                return moved;
        }
        return 0;
    }

    private static int rotateFluidsOnce(IFluidHandler source, List<FluidTarget> targets, ChannelData exportChannel,
            int limit, HolderLookup.Provider provider, FilterItemData.ReadCache cache) {
        boolean equal = exportChannel.getDistributionMode() == DistributionMode.ROUND_ROBIN;
        FluidResourceOrder.Result result = FluidResourceOrder.move(source, exportChannel.getFluidResourceCursor(),
                resource -> equal
                        ? dealFluid(source, targets, exportChannel, limit, resource, provider, cache)
                        : pushFluids(source, targets, exportChannel, limit, resource, provider, cache));
        if (result.cursor() != null)
            exportChannel.setFluidResourceCursor(result.cursor());
        return result.moved();
    }

    private static int dealFluids(IFluidHandler source, List<FluidTarget> targets, int limit,
            ChannelData exportChannel, HolderLookup.Provider provider, FilterItemData.ReadCache cache) {
        List<FluidStack> resources = new ArrayList<>();
        for (int tank = 0; tank < source.getTanks(); tank++) {
            FluidStack stack = source.getFluidInTank(tank);
            if (!stack.isEmpty() && resources.stream().noneMatch(stack::isFluidEqual))
                resources.add(copyFluidWithAmount(stack, 1));
        }
        int moved = 0;
        // Split per fluid type
        for (FluidStack resource : resources) {
            if (moved >= limit)
                break;
            moved += dealFluid(source, targets, exportChannel, limit - moved, resource, provider, cache);
        }
        return moved;
    }

    private static int dealFluid(IFluidHandler source, List<FluidTarget> targets, ChannelData exportChannel,
            int limit, FluidStack resource, HolderLookup.Provider provider, FilterItemData.ReadCache cache) {
        ItemStack[] filters = exportChannel.getFilterItems();
        FilterMode mode = exportChannel.getFilterMode();
        return (int) EvenSplit.deal(targets.size(), fluidStock(source, limit, filters, mode, provider, cache, resource),
                (i, share) -> executeFluidMove(source, targets.get(i).handler(), (int) share, filters, mode,
                        targets.get(i).ref().channel.getFilterItems(), targets.get(i).ref().channel.getFilterMode(),
                        provider, cache, resource));
    }

    private static int fluidStock(IFluidHandler source, int limit, ItemStack[] filters, FilterMode mode,
            HolderLookup.Provider provider, FilterItemData.ReadCache cache, FluidStack resource) {
        if (resource.getFluid().builtInRegistryHolder().is(ModTags.RESOURCE_BLACKLIST_FLUIDS))
            return 0;
        // Drains every tank of fluid
        FluidStack drainable = source.drain(copyFluidWithAmount(resource, limit), IFluidHandler.FluidAction.SIMULATE);
        if (drainable.isEmpty() || !FilterLogic.matchesFluid(filters, mode, drainable, provider, cache))
            return 0;
        int keepCap = getPerEntryFluidAmountLimit(drainable, filters, null, source, null, cache);
        return keepCap >= 0 ? Math.min(drainable.getAmount(), keepCap) : drainable.getAmount();
    }

    private static int transferEnergy(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, List<ImportTarget> targets, int batchLimitRF,
            Map<UUID, Boolean> dimensionalCache, CapCache capCache) {

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceLevel.isLoaded(sourcePos))
            return -1;
        IEnergyStorage sourceHandler = capCache.findEnergyHandler(sourceLevel, sourcePos, exportChannel.getIoDirection());
        if (sourceHandler == null || !sourceHandler.canExtract())
            return 0;

        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        int remaining = batchLimitRF;
        boolean anyReachable = false;
        List<IEnergyStorage> equalTargets = exportChannel.getDistributionMode() == DistributionMode.ROUND_ROBIN
                ? new ArrayList<>() : null;
        boolean robin = exportChannel.getDistributionMode() == DistributionMode.PRIORITY_ROBIN;

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
            if (equalTargets != null) {
                equalTargets.add(targetHandler);
                continue;
            }

            int moved = executeEnergyMove(sourceHandler, targetHandler, remaining);
            if (moved > 0) {
                remaining -= moved;
                if (robin) {
                    markServed(exportChannel, target);
                    break;
                }
            }
        }

        if (!anyReachable)
            return -1;
        if (equalTargets != null) {
            // Stored, not per-call extract
            return (int) EvenSplit.deal(equalTargets.size(), Math.min(batchLimitRF, sourceHandler.getEnergyStored()),
                    (i, share) -> executeEnergyMove(sourceHandler, equalTargets.get(i), (int) share));
        }
        return batchLimitRF - remaining;
    }

    private static int transferChemicals(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, List<ImportTarget> targets, int batchLimit,
            Map<UUID, Boolean> dimensionalCache, CapCache capCache) {

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

        @SuppressWarnings("rawtypes")
        IChemicalHandler sourceHandler = capCache.findChemicalHandler(sourceLevel, sourcePos,
                exportChannel.getIoDirection());
        if (sourceHandler == null)
            return 0;

        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        int remaining = batchLimit;
        boolean anyReachable = false;
        FilterItemData.ReadCache filterReadCache = FilterItemData.createReadCache();
        @SuppressWarnings("rawtypes")
        List<IChemicalHandler> equalHandlers = exportChannel.getDistributionMode() == DistributionMode.ROUND_ROBIN
                ? new ArrayList<>() : null;
        List<ChannelData> equalChannels = equalHandlers == null ? null : new ArrayList<>();
        boolean robin = exportChannel.getDistributionMode() == DistributionMode.PRIORITY_ROBIN;

        for (ImportTarget target : targets) {
            if (remaining <= 0)
                break;
            if (target.node().getUUID().equals(sourceNode.getUUID()))
                continue;
            if (!target.node().isValidNode())
                continue;
            if (!canReach(sourceNode, target.node(), sourceDimensional, dimensionalCache))
                continue;

            ServerLevel targetLevel = (ServerLevel) target.node().level();
            anyReachable = true;
            BlockPos targetPos = target.node().getAttachedPos();
            if (!targetLevel.isLoaded(targetPos))
                continue;

            @SuppressWarnings("rawtypes")
            IChemicalHandler targetHandler = capCache.findChemicalHandler(targetLevel, targetPos,
                    target.channel().getIoDirection());
            if (targetHandler == null)
                continue;

            if (equalHandlers != null) {
                equalHandlers.add(targetHandler);
                equalChannels.add(target.channel());
                continue;
            }
            long moved = ChemicalTransferHelper.transferBetween(
                    sourceHandler, targetHandler, remaining,
                    exportChannel.getFilterItems(), exportChannel.getFilterMode(),
                    target.channel().getFilterItems(), target.channel().getFilterMode(),
                    filterReadCache, null);
            if (Config.debugMode)
                LOGGER.debug("[Chemical] Transfer {} -> {}: moved={}, batch={}",
                        sourcePos, targetPos, moved, remaining);
            if (moved > 0) {
                remaining -= (int) moved;
                if (robin) {
                    markServed(exportChannel, target);
                    break;
                }
            }
        }

        if (Config.debugMode && !anyReachable)
            LOGGER.debug("[Chemical] No reachable targets for {}", sourcePos);
        if (!anyReachable)
            return -1;
        if (equalHandlers != null)
            return dealChemicals(sourceHandler, equalHandlers, equalChannels, batchLimit, exportChannel,
                    filterReadCache);
        return batchLimit - remaining;
    }

    @SuppressWarnings("rawtypes")
    private static int dealChemicals(IChemicalHandler source, List<IChemicalHandler> targets,
            List<ChannelData> channels, int limit, ChannelData exportChannel, FilterItemData.ReadCache cache) {
        ItemStack[] filters = exportChannel.getFilterItems();
        FilterMode mode = exportChannel.getFilterMode();
        long moved = 0;
        // Split per chemical type
        for (Map.Entry<String, Long> stock : ChemicalTransferHelper.exportableAmounts(source, filters, mode, cache)
                .entrySet()) {
            if (moved >= limit)
                break;
            moved += EvenSplit.deal(targets.size(), Math.min(stock.getValue(), limit - moved),
                    (i, share) -> ChemicalTransferHelper.transferBetween(source, targets.get(i), share, filters, mode,
                            channels.get(i).getFilterItems(), channels.get(i).getFilterMode(), cache, stock.getKey()));
        }
        return (int) moved;
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
        List<ImportTarget> equalTargets = exportChannel.getDistributionMode() == DistributionMode.ROUND_ROBIN
                ? new ArrayList<>() : null;
        boolean robin = exportChannel.getDistributionMode() == DistributionMode.PRIORITY_ROBIN;

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
            if (equalTargets != null) {
                if (SourceTransferHelper.hasSourceTile(targetLevel, targetPos))
                    equalTargets.add(target);
                continue;
            }

            int moved = SourceTransferHelper.transferBetween(
                    sourceLevel, sourcePos, targetLevel, targetPos, remaining);
            if (Config.debugMode)
                LOGGER.debug("[Source] Transfer {} -> {}: moved={}, batch={}",
                        sourcePos, targetPos, moved, batchLimit);
            if (moved > 0) {
                remaining -= moved;
                if (robin) {
                    markServed(exportChannel, target);
                    break;
                }
            }
        }

        if (!anyReachable)
            return -1;
        if (equalTargets != null) {
            return (int) EvenSplit.deal(equalTargets.size(),
                    Math.min(batchLimit, SourceTransferHelper.exportable(sourceLevel, sourcePos)),
                    (i, share) -> SourceTransferHelper.transferBetween(sourceLevel, sourcePos,
                            (ServerLevel) equalTargets.get(i).node().level(),
                            equalTargets.get(i).node().getAttachedPos(), (int) share));
        }
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
            HolderLookup.Provider provider,
            ServerLevel sourceLevel, BlockPos sourcePos, FilterItemData.ReadCache filterReadCache,
            @Nullable Map<Item, Integer> sourceItemCounts, @Nullable List<Map<Item, Integer>> targetItemCounts,
            boolean roundRobin, @Nullable int[] sourceSlots, @Nullable ItemStack resource) {

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

        boolean anyAmountConstraints = targetItemCounts != null;
        Map<Item, Integer> batchMoved = anyAmountConstraints && !roundRobin ? new HashMap<>() : null;
        List<Map<Item, Integer>> targetBatchMoved = null;
        if (anyAmountConstraints && roundRobin) {
            targetBatchMoved = new ArrayList<>(targets.size());
            for (int i = 0; i < targets.size(); i++) {
                targetBatchMoved.add(new HashMap<>());
            }
        }

        boolean movedAny;
        boolean[] openTargets = new boolean[targets.size()];
        Arrays.fill(openTargets, true);
        int openTargetCount = targets.size();
        List<List<ItemStack>> rejectedInserts = new ArrayList<>(targets.size());
        List<Map<ItemKey, boolean[]>> importMasks = new ArrayList<>(targets.size());
        for (int i = 0; i < targets.size(); i++) {
            rejectedInserts.add(null);
            importMasks.add(targets.get(i).hasImportSlotMapping() ? new HashMap<>() : null);
        }

        while (remaining > 0 && openTargetCount > 0) {
            movedAny = false;
            int share = (int) EvenSplit.share(remaining, openTargetCount);

            for (int targetIndex = 0; targetIndex < targets.size() && remaining > 0; targetIndex++) {
                if (!openTargets[targetIndex]) {
                    continue;
                }

                ItemTransferTarget target = targets.get(targetIndex);
                boolean movedForTarget = false;
                int targetRemaining = roundRobin ? share : remaining;

                int slotCount = sourceSlots == null ? source.getSlots() : sourceSlots.length;
                for (int entry = 0; entry < slotCount && remaining > 0 && targetRemaining > 0; entry++) {
                    int slot = sourceSlots == null ? entry : sourceSlots[entry];
                    if (sourceAllowedSlots != null
                            && (slot >= sourceAllowedSlots.length || !sourceAllowedSlots[slot])) {
                        continue;
                    }

                    ItemStack extracted = source.extractItem(slot, Math.min(remaining, targetRemaining), true);
                    if (extracted.isEmpty() || extracted.is(ModTags.RESOURCE_BLACKLIST_ITEMS)
                            || resource != null && !ItemStackCompat.isSameItemSameComponents(resource, extracted)) {
                        continue;
                    }

                    CandidateComponents candidateComponents = (provider != null && hasNbtFilter)
                            ? new CandidateComponents(extracted, provider)
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
                            importAllowedSlots = importMask(importMasks.get(targetIndex), target, extracted,
                                    provider, candidateComponents, filterReadCache);
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
                            || (!target.constraints().hasExportThreshold && !target.constraints().hasImportThreshold
                                    && !target.constraints().hasPerEntryAmounts)) {
                        allowedByAmount = extracted.getCount();
                    } else {
                        allowedByAmount = getAllowedTransferCached(extracted, target.constraints(),
                                sourceItemCounts, targetItemCounts.get(targetIndex));
                        if (target.constraints().hasPerEntryAmounts && provider != null) {
                            int perEntry = getPerEntryItemAmountLimit(extracted, exportFilters,
                                    target.importFilters(), source, slot, target.handler(), importAllowedSlots,
                                    sourceItemCounts, targetItemCounts.get(targetIndex), provider, candidateComponents,
                                    filterReadCache);
                            if (perEntry >= 0) {
                                allowedByAmount = Math.min(allowedByAmount, perEntry);
                            }
                            int batchLimit = getPerEntryBatchLimit(extracted, exportFilters, provider,
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
                    if (hasRejectedInsert(rejectedInserts.get(targetIndex), extracted)) {
                        continue;
                    }

                    ItemStack simulatedInsert = extracted.copyWithCount(allowed);
                    ItemStack simRemainder = insertItemWithAllowedSlots(target.handler(), simulatedInsert, true,
                            importAllowedSlots);
                    int acceptableCount = allowed - simRemainder.getCount();
                    if (acceptableCount <= 0) {
                        rememberRejectedInsert(rejectedInserts, targetIndex, extracted);
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
                        rejectedInserts.set(targetIndex, null);
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

                        if (!roundRobin) {
                            break;
                        }
                    }
                }

                if (!movedForTarget || roundRobin && targetRemaining > 0) {
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

    private static int exportableStock(IItemHandler source, @Nullable int[] sourceSlots,
            @Nullable boolean[] sourceAllowedSlots, ItemStack[] exportFilters, FilterMode exportFilterMode,
            HolderLookup.Provider provider, FilterItemData.ReadCache filterReadCache, @Nullable ItemStack resource,
            @Nullable Map<Item, Integer> sourceItemCounts, int limit, boolean hasExportNbtFilter) {
        Map<Item, Integer> keepCaps = new HashMap<>();
        long stock = 0;
        int slotCount = sourceSlots == null ? source.getSlots() : sourceSlots.length;
        for (int entry = 0; entry < slotCount && stock < limit; entry++) {
            int slot = sourceSlots == null ? entry : sourceSlots[entry];
            if (sourceAllowedSlots != null && (slot >= sourceAllowedSlots.length || !sourceAllowedSlots[slot])) {
                continue;
            }
            ItemStack available = source.extractItem(slot, limit, true);
            if (available.isEmpty() || available.is(ModTags.RESOURCE_BLACKLIST_ITEMS)
                    || resource != null && !ItemStackCompat.isSameItemSameComponents(resource, available)) {
                continue;
            }
            CandidateComponents components = hasExportNbtFilter
                    ? new CandidateComponents(available, provider)
                    : null;
            if (!FilterLogic.matchesItemInSlot(exportFilters, exportFilterMode, available, provider, components,
                    filterReadCache, slot)) {
                continue;
            }
            int count = available.getCount();
            if (sourceItemCounts != null) {
                // Export keep amounts per item
                int cap = keepCaps.computeIfAbsent(available.getItem(), item -> getPerEntryItemAmountLimit(
                        available, exportFilters, null, source, slot, null, null, sourceItemCounts, null, provider,
                        components, filterReadCache));
                if (cap >= 0) {
                    count = Math.min(count, cap);
                    keepCaps.put(available.getItem(), cap - count);
                }
            }
            stock += count;
        }
        return (int) Math.min(stock, limit);
    }

    private static boolean hasRejectedInsert(@Nullable List<ItemStack> rejected, ItemStack stack) {
        if (rejected == null) {
            return false;
        }
        for (ItemStack rejectedStack : rejected) {
            if (ItemStackCompat.isSameItemSameComponents(rejectedStack, stack)) {
                return true;
            }
        }
        return false;
    }

    private static void rememberRejectedInsert(List<List<ItemStack>> rejectedInserts, int targetIndex, ItemStack stack) {
        List<ItemStack> rejected = rejectedInserts.get(targetIndex);
        if (rejected == null) {
            rejected = new ArrayList<>();
            rejectedInserts.set(targetIndex, rejected);
        }
        rejected.add(stack.copyWithCount(1));
    }

    private static boolean[] importMask(Map<ItemKey, boolean[]> masks, ItemTransferTarget target,
            ItemStack candidate, HolderLookup.Provider provider, @Nullable CandidateComponents candidateComponents,
            @Nullable FilterItemData.ReadCache filterReadCache) {
        ItemKey key = new ItemKey(candidate.getItem(), candidate.getTag());
        if (!masks.containsKey(key))
            masks.put(key, computeImportAllowedSlots(target, candidate, provider, candidateComponents,
                    filterReadCache));
        boolean[] mask = masks.get(key);
        // importCap mutates its copy
        return mask == null ? null : mask.clone();
    }

    private static boolean[] computeImportAllowedSlots(ItemTransferTarget target, ItemStack candidate,
            HolderLookup.Provider provider, @Nullable CandidateComponents candidateComponents,
            @Nullable FilterItemData.ReadCache filterReadCache) {
        boolean[] slotFilter = target.allowedSlots();
        int size = target.handler().getSlots();
        boolean[] mask = new boolean[size];
        boolean any = false;
        for (int ds = 0; ds < size; ds++) {
            if (slotFilter != null && (ds >= slotFilter.length || !slotFilter[ds]))
                continue;
            if (FilterLogic.matchesItemInSlot(target.importFilters(), target.importFilterMode(), candidate, provider,
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
            if (!ItemStackCompat.isSameItemSameComponents(slotStack, remaining)) {
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
                if (!slotEmpty && !ItemStackCompat.isSameItemSameComponents(slotStack, remaining)) {
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
            HolderLookup.Provider provider, @Nullable FilterItemData.ReadCache filterReadCache, FluidStack required) {

        int remaining = limitMb;
        AmountConstraints amountConstraints = collectAmountConstraints(exportFilters, importFilters, filterReadCache);

        for (int tank = 0; tank < source.getTanks() && remaining > 0; tank++) {
            FluidStack tankFluid = source.getFluidInTank(tank);
            if (tankFluid.isEmpty() || !required.isEmpty() && !required.isFluidEqual(tankFluid))
                continue;
            if (tankFluid.getFluid().builtInRegistryHolder().is(ModTags.RESOURCE_BLACKLIST_FLUIDS))
                continue;

            int requestFromTank = Math.min(remaining, tankFluid.getAmount());
            FluidStack simulated = source.drain(copyFluidWithAmount(tankFluid, requestFromTank),
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
            int accepted = target.fill(copyFluidWithAmount(simulated, request), IFluidHandler.FluidAction.SIMULATE);
            if (accepted <= 0)
                continue;

            FluidStack drained = source.drain(copyFluidWithAmount(simulated, accepted), IFluidHandler.FluidAction.EXECUTE);
            if (drained.isEmpty())
                continue;

            int filled = target.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            if (filled < drained.getAmount()) {
                int rollbackAmount = drained.getAmount() - filled;
                FluidStack rollback = copyFluidWithAmount(drained, rollbackAmount);
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
        int toMove = getEnergyMoveAmount(source, target, limitRF);
        if (toMove <= 0)
            return 0;

        int extracted = source.extractEnergy(toMove, false);
        if (extracted <= 0)
            return 0;

        int received = target.receiveEnergy(extracted, false);
        if (received == extracted)
            return received;

        int rollbackAmount = extracted - received;
        if (source.canReceive()) {
            int returned = source.receiveEnergy(rollbackAmount, false);
            rollbackAmount -= returned;
        }

        if (rollbackAmount > 0 && Config.debugMode) {
            LOGGER.warn("ENERGY TRANSFER MISMATCH: {} RF could not be inserted or returned.", rollbackAmount);
        }

        return received;
    }

    private static int getEnergyMoveAmount(IEnergyStorage source, IEnergyStorage target, int limitRF) {
        if (limitRF <= 0)
            return 0;

        int sourceStored = Math.max(0, source.getEnergyStored());
        int targetSpace = getEnergySpace(target);
        int limit = Math.min(limitRF, Math.min(sourceStored, targetSpace));
        if (limit <= 0)
            return 0;

        int extracted = source.extractEnergy(limit, true);
        if (extracted <= 0)
            return 0;

        int accepted = target.receiveEnergy(Math.min(limit, extracted), true);
        return Math.max(0, Math.min(limit, Math.min(extracted, accepted)));
    }

    private static int getEnergySpace(IEnergyStorage target) {
        long space = (long) target.getMaxEnergyStored() - target.getEnergyStored();
        if (space <= 0)
            return 0;
        return space > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) space;
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
            case IGNORED -> true;
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
                if (FilterItemData.hasAnyAmountEntries(filter, filterReadCache)) {
                    hasPerEntryAmounts = true;
                }
            }
        }

        int importThreshold = Integer.MAX_VALUE;
        boolean hasImportThreshold = false;

        if (importFilters != null) {
            for (ItemStack filter : importFilters) {
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
            ItemStack[] importFilters, IItemHandler source, int sourceSlot,
            @Nullable IItemHandler target, @Nullable boolean[] importMask,
            Map<Item, Integer> sourceCounts, Map<Item, Integer> targetCounts,
            HolderLookup.Provider provider, @Nullable CandidateComponents candidateComponents,
            @Nullable FilterItemData.ReadCache filterReadCache) {
        int allowed = Integer.MAX_VALUE;

        if (exportFilters != null) {
            for (ItemStack filter : exportFilters) {
                List<FilterItemData.ItemStock> stocks = FilterItemData.getItemStocksFull(filter, candidate, provider,
                        candidateComponents, filterReadCache, sourceSlot);
                if (stocks.isEmpty() || stocks.get(0).amount() <= 0)
                    continue;
                FilterItemData.ItemStock stock = stocks.get(0);
                int sourceCount = stockedCount(source, stock.slots(), sourceCounts, candidate.getItem());
                int exportCap = sourceCount - stock.amount();
                if (exportCap <= 0)
                    return 0;
                allowed = Math.min(allowed, exportCap);
            }
        }

        if (importFilters != null) {
            for (ItemStack filter : importFilters) {
                int importCap = importCap(FilterItemData.getItemStocksFull(filter, candidate, provider,
                        candidateComponents, filterReadCache, -1), target, importMask, targetCounts,
                        candidate.getItem());
                if (importCap <= 0)
                    return 0;
                allowed = Math.min(allowed, importCap);
            }
        }

        return allowed == Integer.MAX_VALUE ? -1 : Math.max(0, allowed);
    }

    private static int importCap(List<FilterItemData.ItemStock> stocks, IItemHandler target,
            @Nullable boolean[] importMask, @Nullable Map<Item, Integer> targetCounts, Item item) {
        int cap = Integer.MAX_VALUE;
        boolean wholeSeen = false;
        for (FilterItemData.ItemStock stock : stocks) {
            if (stock.slots() == null) {
                if (wholeSeen)
                    continue;
                wholeSeen = true;
            }
            if (stock.amount() <= 0)
                continue;
            int room = stock.amount() - stockedCount(target, stock.slots(), targetCounts, item);
            if (room > 0) {
                cap = Math.min(cap, room);
            } else if (stock.slots() == null) {
                return 0;
            } else if (importMask != null) {
                // full bucket, block its slots
                for (int slot : stock.slots()) {
                    if (slot < importMask.length)
                        importMask[slot] = false;
                }
            }
        }
        return cap;
    }

    private static int stockedCount(IItemHandler handler, @Nullable int[] slots,
            @Nullable Map<Item, Integer> counts, Item item) {
        if (slots == null)
            return counts != null ? counts.getOrDefault(item, 0) : 0;
        long count = 0;
        for (int slot : slots) {
            if (slot < handler.getSlots()) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.getItem() == item)
                    count += stack.getCount();
            }
        }
        return (int) Math.min(count, Integer.MAX_VALUE);
    }

    private static int getPerEntryBatchLimit(ItemStack candidate, ItemStack[] exportFilters,
            HolderLookup.Provider provider, @Nullable CandidateComponents candidateComponents,
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
            if (!stack.isEmpty() && sameFluid(stack, candidate)) {
                amount += stack.getAmount();
            }
        }
        return amount;
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
                int accepted = h.fill(copyFluidWithAmount(resource, remaining), action);
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
                FluidStack drained = h.drain(copyFluidWithAmount(resource, remaining), action);
                if (drained.isEmpty()) continue;
                if (template.isEmpty()) template = drained;
                totalAmount += drained.getAmount();
                remaining -= drained.getAmount();
            }
            return template.isEmpty() ? FluidStack.EMPTY : copyFluidWithAmount(template, totalAmount);
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
                    drained = h.drain(copyFluidWithAmount(template, remaining), action);
                }
                if (drained.isEmpty()) continue;
                if (template.isEmpty()) template = drained;
                totalAmount += drained.getAmount();
                remaining -= drained.getAmount();
            }
            return template.isEmpty() ? FluidStack.EMPTY : copyFluidWithAmount(template, totalAmount);
        }
    }

    private static FluidStack copyFluidWithAmount(FluidStack stack, int amount) {
        if (stack.isEmpty()) {
            return FluidStack.EMPTY;
        }
        FluidStack copy = stack.copy();
        copy.setAmount(amount);
        return copy;
    }

    private static boolean sameFluid(FluidStack left, FluidStack right) {
        return left.isFluidEqual(right);
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
