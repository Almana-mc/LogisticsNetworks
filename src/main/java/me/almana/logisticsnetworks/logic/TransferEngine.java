package me.almana.logisticsnetworks.logic;

import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.CandidateComponents;
import me.almana.logisticsnetworks.integration.ars.ArsCompat;
import me.almana.logisticsnetworks.integration.ars.SourceTransferHelper;
import me.almana.logisticsnetworks.integration.mekanism.ChemicalTransferHelper;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.integration.storage.DirectItemAccess;
import me.almana.logisticsnetworks.integration.storage.DirectStorageHandlers;
import me.almana.logisticsnetworks.integration.storage.ItemResource;
import me.almana.logisticsnetworks.logic.async.SnapshotItemHandler;
import me.almana.logisticsnetworks.logic.async.ThreadGuard;
import me.almana.logisticsnetworks.logic.async.TransferPlan;
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
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

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
    private static final byte SLOT_REJECTED = 1;
    private static final byte SLOT_PASSED = 2;

    private static long capKey(ServerLevel level, BlockPos pos, Direction dir) {
        long packed = pos.asLong();
        packed ^= ((long) dir.ordinal()) << 58;
        packed ^= ((long) level.dimension().location().hashCode()) << 32;
        return packed;
    }

    static final class CapCache {
        private final Map<Long, Object> items = new HashMap<>();
        private final Map<Long, Object> fluids = new HashMap<>();
        private final Map<Long, Object> energy = new HashMap<>();
        private final Map<Long, Object> chemicals = new HashMap<>();
        private static final Object ABSENT = new Object();
        final TransferCapabilityCache interfaces = new TransferCapabilityCache(this);

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
    }

    private record ItemKey(Item item, @Nullable CompoundTag tag) {
    }

    public record NetworkContext(
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

    private static long processNetwork(LogisticsNetwork network, MinecraftServer server,
            boolean includeItemTransfers) {
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
                    telemetryActive, capCache, includeItemTransfers);
            if (delta < minWakeDelta) {
                minWakeDelta = delta;
            }
        }

        return minWakeDelta;
    }

    @Nullable
    public static NetworkContext prepareNetwork(LogisticsNetwork network, MinecraftServer server) {
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

        @SuppressWarnings("unchecked")
        List<ImportTarget>[][] imports = new List[][] {
                resolveCache(network.getItemImports(), nodeCache, signalCache),
                resolveCache(network.getFluidImports(), nodeCache, signalCache),
                resolveCache(network.getEnergyImports(), nodeCache, signalCache),
                resolveCache(network.getChemicalImports(), nodeCache, signalCache),
                resolveCache(network.getSourceImports(), nodeCache, signalCache) };
        Map<UUID, Boolean> dimensionalCache = mergeLinkedServers(network, server, imports);

        return new NetworkContext(sortedNodes, signalCache, imports[0], imports[1], imports[2], imports[3],
                imports[4], dimensionalCache, network.getTierCache());
    }

    private static Map<UUID, Boolean> mergeLinkedServers(LogisticsNetwork network, MinecraftServer server,
            List<ImportTarget>[][] imports) {
        NetworkRegistry registry = NetworkRegistry.get((ServerLevel) server.overworld());
        List<ServerRackLinks.Link> links = registry.getRackLinks().linksFor(network.getId());
        if (links.isEmpty()) {
            return network.getDimensionalCache();
        }
        Map<UUID, Boolean> dimensionalCache = new HashMap<>(network.getDimensionalCache());
        BridgedImports.merge(links, registry, server, imports, dimensionalCache);
        return dimensionalCache;
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
            CapCache capCache,
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

            if (!includeItemTransfers && channel.getType() == ChannelType.ITEM)
                continue;

            // Item targets order on resolve
            if (channel.getType() != ChannelType.ITEM)
                targets = orderTargets(targets, channel, sourceNode);

            int configuredBatch = getBatchLimit(channel.getType(), sourceTier);
            int effectiveBatchSize = Math.max(1, Math.min(channel.getBatchSize(), configuredBatch));

            ChannelTelemetry.startRecording(telemetryActive ? channel.getTelemetry() : null);
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
                    transferItems(sourceNode, sourceLevel, channel, i, targets, effectiveBatchSize, dimensionalCache,
                            capCache.interfaces, telemetryActive);
            };

            ChannelTelemetry.startRecording(null);
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
            Map<UUID, Boolean> dimensionalCache, TransferCapabilityCache capCache, boolean telemetryActive) {
        return transferItems(sourceNode, sourceLevel, exportChannel, channelIndex, targets,
                batchLimit, dimensionalCache, capCache, Collections.emptyMap(), Collections.emptyMap(), null,
                telemetryActive).moved();
    }

    private static ItemResourceOrder.Result transferItems(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, int channelIndex, List<ImportTarget> targets, int batchLimit,
            Map<UUID, Boolean> dimensionalCache, TransferCapabilityCache capCache,
            Map<Item, Integer> priorBatchMoved, Map<UUID, Map<Item, Integer>> priorTargetBatches,
            @Nullable ItemResource requiredResource, boolean telemetryActive) {

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceLevel.isLoaded(sourcePos))
            return new ItemResourceOrder.Result(null, -1);
        ItemStack[] exportFilters = exportChannel.getFilterItems();
        FilterItemData.ReadCache filterReadCache = FilterItemData.createReadCache();
        boolean directSource = !FilterLogic.hasConfiguredSlotMapping(exportFilters, filterReadCache);
        IItemHandler sourceHandler = capCache.findItemExportHandler(
                sourceNode, exportChannel.getIoDirection(), directSource);
        if (sourceHandler == null)
            return ItemResourceOrder.EMPTY;

        boolean[] sourceAllowedSlots = null;

        ResolvedItemTargets resolved = resolveItemTargets(sourceNode, sourceLevel, exportChannel, targets,
                sourceHandler, dimensionalCache, capCache, filterReadCache);
        if (resolved.status() == ResolvedItemTargets.NO_REACHABLE) {
            return new ItemResourceOrder.Result(null, -1);
        }
        if (resolved.targets().isEmpty()) {
            return ItemResourceOrder.EMPTY;
        }

        DistributionMode distribution = exportChannel.getDistributionMode();
        MoveRecorder served = distribution == DistributionMode.PRIORITY_ROBIN
                ? (slot, index, moved, mask) -> markServed(exportChannel, resolved.refs().get(index))
                : null;
        MoveRecorder recorder = telemetryActive ? withTelemetry(served, exportChannel.getTelemetry()) : served;
        ItemResourceOrder.Result result = executeItemOperation(sourceHandler, resolved.targets(), batchLimit,
                exportFilters, exportChannel.getFilterMode(),
                sourceAllowedSlots,
                sourceLevel.registryAccess(),
                sourceLevel, sourcePos, filterReadCache,
                distribution, recorder, priorBatchMoved, resolved.refs().stream()
                        .map(ref -> priorTargetBatches.getOrDefault(ref.node().getUUID(), Map.of())).toList(),
                exportChannel.canRotateResources(), exportChannel.getItemResourceCursor(), requiredResource);
        if (result.cursor() != null) exportChannel.setItemResourceCursor(result.cursor());
        return result;
    }

    private static MoveRecorder withTelemetry(@Nullable MoveRecorder next, ChannelTelemetry telemetry) {
        return (slot, index, moved, mask) -> {
            telemetry.recordResource(new FlowResource.Item(moved), moved.getCount());
            if (next != null) {
                next.record(slot, index, moved, mask);
            }
        };
    }

    public static ItemResourceOrder.Result recoverItemChannel(LogisticsNetwork network, MinecraftServer server,
            TransferCapabilityCache capCache, UUID sourceNodeId, int channelIndex,
            int plannedShortfall, int committed, Map<Item, Integer> committedByItem,
            Map<UUID, Map<Item, Integer>> committedByTarget, @Nullable ItemResource requiredResource,
            @Nullable UUID onlyTarget) {
        ThreadGuard.requireServerThread();
        if (plannedShortfall <= 0 || channelIndex < 0 || channelIndex >= LogisticsNodeEntity.CHANNEL_COUNT) {
            return ItemResourceOrder.EMPTY;
        }

        NetworkContext context = prepareNetwork(network, server);
        if (context == null) {
            return ItemResourceOrder.EMPTY;
        }

        LogisticsNodeEntity sourceNode = null;
        for (LogisticsNodeEntity node : context.sortedNodes()) {
            if (node.getUUID().equals(sourceNodeId)) {
                sourceNode = node;
                break;
            }
        }
        if (sourceNode == null || !sourceNode.isValidNode()) {
            return ItemResourceOrder.EMPTY;
        }

        ChannelData channel = sourceNode.getChannel(channelIndex);
        int signal = context.signalCache().getOrDefault(sourceNodeId, 0);
        if (channel == null || !channel.isEnabled()
                || channel.getMode() != ChannelMode.EXPORT || channel.getType() != ChannelType.ITEM
                || !isRedstoneActive(channel.getRedstoneMode(), signal)) {
            return ItemResourceOrder.EMPTY;
        }

        List<ImportTarget> targets = context.itemImports()[channelIndex];
        if (targets == null || targets.isEmpty()) {
            return ItemResourceOrder.EMPTY;
        }
        if (onlyTarget != null) {
            // Robin recovery stays on receiver
            targets = targets.stream().filter(t -> t.node().getUUID().equals(onlyTarget)).toList();
        }

        int tier = context.tierCache().getOrDefault(sourceNodeId, 0);
        int configuredBatch = getBatchLimit(ChannelType.ITEM, tier);
        int currentBatch = Math.max(1, Math.min(channel.getBatchSize(), configuredBatch));
        int recoveryLimit = Math.min(plannedShortfall, Math.max(0, currentBatch - committed));
        if (recoveryLimit == 0) {
            return ItemResourceOrder.EMPTY;
        }

        boolean telemetryActive = NetworkRegistry.get((ServerLevel) server.overworld())
                .getTelemetryManager().isActive(network.getId());
        ItemResourceOrder.Result recovered = transferItems(
                sourceNode, (ServerLevel) sourceNode.level(), channel, channelIndex,
                targets, recoveryLimit, context.dimensionalCache(), capCache, committedByItem, committedByTarget,
                requiredResource, telemetryActive);
        return recovered.moved() < 0 ? ItemResourceOrder.EMPTY : recovered;
    }

    public static ResolvedItemTargets resolveItemTargets(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, List<ImportTarget> targets, IItemHandler sourceHandler,
            Map<UUID, Boolean> dimensionalCache, TransferCapabilityCache capCache,
            FilterItemData.ReadCache filterReadCache) {

        targets = orderTargets(targets, exportChannel, sourceNode);
        BlockPos sourcePos = sourceNode.getAttachedPos();
        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        boolean anyReachable = false;
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
            ServerLevel targetLevel = (ServerLevel) target.node.level();
            BlockPos targetPos = target.node.getAttachedPos();
            if (!targetLevel.isLoaded(targetPos))
                continue;
            if (isSameItemStorage(sourceLevel, sourcePos, targetLevel, targetPos))
                continue;

            ItemStack[] importFilters = target.channel.getFilterItems();
            boolean hasImportSlotMapping = FilterLogic.hasConfiguredSlotMapping(importFilters, filterReadCache);
            IItemHandler targetHandler = capCache.findItemImportHandler(
                    target.node, target.channel.getIoDirection(), !hasImportSlotMapping);
            if (targetHandler == null)
                continue;
            if (sourceHandler == targetHandler || DirectStorageHandlers.shareNetwork(sourceHandler, targetHandler))
                continue;

            boolean[] targetAllowedSlots = null;
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
        int status = anyReachable ? ResolvedItemTargets.OK : ResolvedItemTargets.NO_REACHABLE;
        return new ResolvedItemTargets(reachableTargets, resolvedRefs, status);
    }

    private record FluidTarget(IFluidHandler handler, ImportTarget ref) {
    }

    private static int transferFluids(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, List<ImportTarget> targets, int batchLimitMb,
            Map<UUID, Boolean> dimensionalCache, CapCache capCache) {

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceLevel.isLoaded(sourcePos))
            return -1;
        IFluidHandler sourceHandler = capCache.interfaces.findFluidExportHandler(sourceNode,
                exportChannel.getIoDirection());
        if (sourceHandler == null)
            return 0;

        List<FluidTarget> resolved = new ArrayList<>(targets.size());
        if (!resolveFluidTargets(sourceNode, sourceHandler, targets, dimensionalCache, capCache, resolved))
            return -1;
        FilterItemData.ReadCache cache = FilterItemData.createReadCache();
        HolderLookup.Provider provider = sourceLevel.registryAccess();
        if (exportChannel.canRotateResources())
            return rotateFluids(sourceHandler, resolved, exportChannel, batchLimitMb, provider, cache);
        if (exportChannel.getDistributionMode() == DistributionMode.ROUND_ROBIN)
            return dealFluids(sourceHandler, resolved, batchLimitMb, exportChannel, provider, cache);
        return pushFluids(sourceHandler, resolved, exportChannel, batchLimitMb, FluidStack.EMPTY, provider, cache);
    }

    // False if none reachable
    private static boolean resolveFluidTargets(LogisticsNodeEntity sourceNode, IFluidHandler sourceHandler,
            List<ImportTarget> targets, Map<UUID, Boolean> dimensionalCache, CapCache capCache,
            List<FluidTarget> out) {
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
            IFluidHandler handler = capCache.interfaces.findFluidImportHandler(target.node,
                    target.channel.getIoDirection());
            if (handler != null && !DirectStorageHandlers.shareNetwork(sourceHandler, handler))
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
        int keepCap = TransferAmountRules.perEntryFluidAmount(drainable, filters, null, source, null, cache);
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

    public static ItemResourceOrder.Result executeItemOperation(IItemHandler source,
            List<ItemTransferTarget> targets, int limit, ItemStack[] exportFilters, FilterMode exportFilterMode,
            boolean[] sourceAllowedSlots, HolderLookup.Provider provider, @Nullable ServerLevel sourceLevel,
            @Nullable BlockPos sourcePos, FilterItemData.ReadCache filterReadCache, DistributionMode distribution,
            @Nullable MoveRecorder recorder, Map<Item, Integer> priorBatchMoved,
            List<Map<Item, Integer>> priorTargetBatches, boolean rotate,
            @Nullable ItemResourceOrder.Cursor cursor, @Nullable ItemResource required) {
        if (distribution == DistributionMode.PRIORITY_ROBIN) {
            // Receiver-outer avoids resource lockstep
            for (int i = 0; i < targets.size(); i++) {
                int index = i;
                MoveRecorder single = (slot, ignored, moved, mask) -> recorder.record(slot, index, moved, mask);
                ItemResourceOrder.Result result = executeItemOperation(source, List.of(targets.get(i)), limit,
                        exportFilters, exportFilterMode, sourceAllowedSlots, provider, sourceLevel, sourcePos,
                        filterReadCache, DistributionMode.PRIORITY, single, priorBatchMoved, List.of(),
                        rotate, cursor, required);
                if (result.moved() > 0) return result;
            }
            return ItemResourceOrder.EMPTY;
        }
        boolean roundRobin = distribution == DistributionMode.ROUND_ROBIN;
        if (!rotate && roundRobin) {
            // Split each resource on its own
            return new ItemResourceOrder.Result(null, ItemResourceOrder.moveEach(source, limit, (slots, left) ->
                    executeMove(source, targets, left, exportFilters, exportFilterMode, sourceAllowedSlots, provider,
                            sourceLevel, sourcePos, filterReadCache, true, recorder, priorBatchMoved,
                            priorTargetBatches, slots, new ItemResource(source.getStackInSlot(slots[0])))));
        }
        if (!rotate) {
            return new ItemResourceOrder.Result(null, executeMove(source, targets, limit, exportFilters,
                    exportFilterMode, sourceAllowedSlots, provider, sourceLevel, sourcePos, filterReadCache,
                    roundRobin, recorder, priorBatchMoved, priorTargetBatches, null, null));
        }
        return ItemResourceOrder.move(source, cursor, required, slots -> {
            ItemResource resource = new ItemResource(source.getStackInSlot(slots[0]));
            return executeMove(source, targets, limit, exportFilters, exportFilterMode, sourceAllowedSlots,
                    provider, sourceLevel, sourcePos, filterReadCache, roundRobin, recorder, priorBatchMoved,
                    priorTargetBatches, slots, resource);
        });
    }

    private static int executeMove(IItemHandler source, List<ItemTransferTarget> targets, int limit,
            ItemStack[] exportFilters, FilterMode exportFilterMode, boolean[] sourceAllowedSlots,
            HolderLookup.Provider provider, @Nullable ServerLevel sourceLevel, @Nullable BlockPos sourcePos,
            FilterItemData.ReadCache filterReadCache, boolean roundRobin, @Nullable MoveRecorder recorder,
            Map<Item, Integer> priorBatchMoved, List<Map<Item, Integer>> priorTargetBatches,
            @Nullable int[] sourceSlots, @Nullable ItemResource resource) {

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
        boolean sourceNeedsCounts = TransferAmountRules.hasStockFilter(exportFilters, filterReadCache);
        boolean anyCountsNeeded = sourceNeedsCounts;
        for (ItemTransferTarget t : targets) {
            sourceNeedsCounts |= t.constraints().hasExportThreshold();
            anyCountsNeeded |= t.constraints().hasExportThreshold() || t.constraints().hasImportThreshold()
                    || TransferAmountRules.hasStockFilter(t.importFilters(), filterReadCache);
            if (t.constraints().hasExportThreshold() || t.constraints().hasImportThreshold()
                    || t.constraints().hasPerEntryAmounts()) {
                anyAmountConstraints = true;
            }
        }
        Set<Item> countCandidates = new HashSet<>();
        if (anyCountsNeeded) {
            for (int slot = 0; slot < source.getSlots(); slot++) {
                ItemStack candidate = source.getStackInSlot(slot);
                if (!candidate.isEmpty()) countCandidates.add(candidate.getItem());
            }
        }
        Map<Item, Integer> sourceItemCounts = sourceNeedsCounts
                ? TransferAmountRules.countItems(source, countCandidates) : null;
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
                    int index = targetBatchMoved.size();
                    targetBatchMoved.add(new HashMap<>(index < priorTargetBatches.size()
                            ? priorTargetBatches.get(index) : Map.of()));
                }
                targetItemCounts.add(
                        (t.constraints().hasImportThreshold()
                                || TransferAmountRules.hasStockFilter(t.importFilters(), filterReadCache))
                                ? TransferAmountRules.countItems(t.handler(), countCandidates)
                                : null);
            }
        }

        boolean movedAny;
        boolean[] openTargets = new boolean[targets.size()];
        Arrays.fill(openTargets, true);
        int openTargetCount = targets.size();
        BulkInsertRejectionCache bulkInsertRejections = null;
        BulkInsertRejectionCache insertRejections = null;
        List<Map<ItemKey, boolean[]>> importMasks = new ArrayList<>(targets.size());
        for (ItemTransferTarget target : targets) {
            importMasks.add(target.hasImportSlotMapping() ? new HashMap<>() : null);
        }

        // Memo components per source slot
        CandidateComponents[] slotComponents = hasNbtFilter ? new CandidateComponents[source.getSlots()] : null;
        byte[] slotVerdicts = new byte[sourceSlots == null ? source.getSlots() : sourceSlots.length];
        // Simulated extracts shared across targets
        ItemStack[] slotStacks = new ItemStack[slotVerdicts.length];
        if (roundRobin) {
            remaining = exportableStock(source, sourceSlots, sourceAllowedSlots, exportFilters, exportFilterMode,
                    provider, filterReadCache, resource, sourceItemCounts, limit, slotVerdicts, slotStacks);
        }
        int budget = remaining;

        while (remaining > 0 && openTargetCount > 0) {
            movedAny = false;
            int share = (int) EvenSplit.share(remaining, openTargetCount);

            for (int targetIndex = 0; targetIndex < targets.size() && remaining > 0; targetIndex++) {
                if (!openTargets[targetIndex]) {
                    continue;
                }

                ItemTransferTarget target = targets.get(targetIndex);
                IItemHandler bulkHandler = target.bulkHandler();
                boolean movedForTarget = false;
                int targetRemaining = roundRobin ? share : remaining;

                int slotCount = sourceSlots == null ? source.getSlots() : sourceSlots.length;
                for (int entry = 0; entry < slotCount && remaining > 0 && targetRemaining > 0; entry++) {
                    int slot = sourceSlots == null ? entry : sourceSlots[entry];
                    if (sourceAllowedSlots != null
                            && (slot >= sourceAllowedSlots.length || !sourceAllowedSlots[slot])) {
                        continue;
                    }

                    if (slotVerdicts[entry] == SLOT_REJECTED) {
                        continue;
                    }

                    ItemStack available = slotStacks[entry];
                    if (available == null) {
                        available = source.extractItem(slot, remaining, true);
                        slotStacks[entry] = available;
                    }
                    int request = Math.min(remaining, targetRemaining);
                    ItemStack extracted = available.getCount() > request ? available.copyWithCount(request) : available;
                    boolean sourcePassed = slotVerdicts[entry] == SLOT_PASSED;
                    if (extracted.isEmpty() || !sourcePassed && (extracted.is(ModTags.RESOURCE_BLACKLIST_ITEMS)
                            || resource != null && !resource.equals(new ItemResource(extracted)))) {
                        slotVerdicts[entry] = SLOT_REJECTED;
                        continue;
                    }

                    CandidateComponents candidateComponents = null;
                    if (provider != null && hasNbtFilter) {
                        if (slotComponents[slot] == null) {
                            // Copy, extracted reaches target handlers
                            slotComponents[slot] = new CandidateComponents(extracted.copy(), provider);
                        }
                        candidateComponents = slotComponents[slot];
                    }

                    if (provider != null && !sourcePassed) {
                        if (!FilterLogic.matchesItemInSlot(exportFilters, exportFilterMode, extracted, provider,
                                candidateComponents, filterReadCache, slot)) {
                            slotVerdicts[entry] = SLOT_REJECTED;
                            continue;
                        }
                    }
                    slotVerdicts[entry] = SLOT_PASSED;

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
                            || (!target.constraints().hasExportThreshold() && !target.constraints().hasImportThreshold()
                                    && !target.constraints().hasPerEntryAmounts())) {
                        allowedByAmount = extracted.getCount();
                    } else {
                        if (targetItemCounts.get(targetIndex) != null
                                && target.handler() instanceof DirectItemAccess direct) {
                            targetItemCounts.set(targetIndex, direct.countItems(countCandidates));
                        }
                        allowedByAmount = TransferAmountRules.allowedItems(extracted, target.constraints(),
                                sourceItemCounts, targetItemCounts.get(targetIndex));
                        if (target.constraints().hasPerEntryAmounts() && provider != null) {
                            int perEntry = TransferAmountRules.perEntryItemAmount(extracted, exportFilters,
                                    target.importFilters(), source, slot, target.handler(), importAllowedSlots,
                                    sourceItemCounts,
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
                    StackedInsertion insertion = prepareStackedInsertion(
                            target.handler(), bulkHandler, importAllowedSlots);
                    ItemStack simRemainder;
                    if (bulkHandler != null) {
                        if (bulkInsertRejections == null) {
                            bulkInsertRejections = new BulkInsertRejectionCache(
                                    (handler, stack) -> insertBulkItem(handler, stack, true));
                        }
                        simRemainder = bulkInsertRejections.simulate(bulkHandler, simulatedInsert);
                    } else if (insertion != null) {
                        simRemainder = insertion.simulate(simulatedInsert);
                    } else if (importAllowedSlots == null) {
                        if (insertRejections == null) {
                            insertRejections = new BulkInsertRejectionCache(
                                    (handler, stack) -> insertItemWithAllowedSlots(handler, null, stack, true, null));
                        }
                        simRemainder = insertRejections.simulate(target.handler(), simulatedInsert);
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

                    ItemStack uninserted = insertion != null ? insertion.commit(toMove)
                            : insertItemWithAllowedSlots(target.handler(), bulkHandler,
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
                                if (!forcedRemainder.isEmpty() && sourceLevel != null) {
                                    LOGGER.error("ITEM VOIDING PREVENTED: Could not return {} to source or fit into "
                                            + "target slot mask. Dropping at source pos {}.",
                                            forcedRemainder, sourcePos);
                                    droppedToWorld = forcedRemainder.getCount();
                                    Block.popResource(sourceLevel, sourcePos, forcedRemainder);
                                }
                            }
                        }
                    }
                    // Source changed, recheck slots
                    slotVerdicts = new byte[Math.max(slotVerdicts.length, source.getSlots())];
                    slotStacks = new ItemStack[slotVerdicts.length];
                    if (slotComponents != null) {
                        slotComponents = new CandidateComponents[Math.max(slotComponents.length, source.getSlots())];
                    }

                    int sourceLost = targetAccepted + droppedToWorld;
                    if (sourceLost > 0) {
                        insertRejections = null;
                        if (recorder != null) {
                            recorder.record(slot, targetIndex, toMove.copyWithCount(sourceLost),
                                    importAllowedSlots);
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
        return budget - remaining;
    }

    private static int exportableStock(IItemHandler source, @Nullable int[] sourceSlots,
            @Nullable boolean[] sourceAllowedSlots, ItemStack[] exportFilters, FilterMode exportFilterMode,
            @Nullable HolderLookup.Provider provider, FilterItemData.ReadCache filterReadCache,
            @Nullable ItemResource resource, @Nullable Map<Item, Integer> sourceItemCounts, int limit,
            byte[] slotVerdicts, ItemStack[] slotStacks) {
        Map<Item, Integer> keepCaps = new HashMap<>();
        long stock = 0;
        for (int entry = 0; entry < slotVerdicts.length && stock < limit; entry++) {
            int slot = sourceSlots == null ? entry : sourceSlots[entry];
            if (sourceAllowedSlots != null && (slot >= sourceAllowedSlots.length || !sourceAllowedSlots[slot])) {
                continue;
            }
            ItemStack available = source.extractItem(slot, limit, true);
            slotStacks[entry] = available;
            if (available.isEmpty() || available.is(ModTags.RESOURCE_BLACKLIST_ITEMS)
                    || resource != null && !resource.equals(new ItemResource(available))
                    || provider != null && !FilterLogic.matchesItemInSlot(exportFilters, exportFilterMode,
                            available, provider, null, filterReadCache, slot)) {
                slotVerdicts[entry] = SLOT_REJECTED;
                continue;
            }
            slotVerdicts[entry] = SLOT_PASSED;
            int count = available.getCount();
            if (sourceItemCounts != null && provider != null) {
                // Export keep amounts per item
                int cap = keepCaps.computeIfAbsent(available.getItem(), item -> TransferAmountRules.perEntryItemAmount(
                        available, exportFilters, null, source, slot, null, null, sourceItemCounts, null, provider,
                        null, filterReadCache));
                if (cap >= 0) {
                    count = Math.min(count, cap);
                    keepCaps.put(available.getItem(), cap - count);
                }
            }
            stock += count;
        }
        return (int) Math.min(stock, limit);
    }

    public static int commitSingleMove(IItemHandler source, IItemHandler target,
            @Nullable IItemHandler bulkTarget, TransferPlan.ItemMove move, LogisticsNodeEntity sourceNode) {
        ThreadGuard.requireServerThread();

        if (move.amount() <= 0 || !(source instanceof DirectItemAccess)
                && (move.sourceSlot() < 0 || move.sourceSlot() >= source.getSlots())) {
            return 0;
        }

        boolean[] targetSlotMask = move.targetSlotMask();
        if (targetSlotMask != null
                && (bulkTarget != null || targetSlotMask.length != target.getSlots())) {
            return 0;
        }

        ItemStack expected = move.resource().toStack(move.amount());
        ItemStack available = source instanceof DirectItemAccess direct
                ? direct.extract(expected, move.amount(), true)
                : source.extractItem(move.sourceSlot(), move.amount(), true);
        if (available.isEmpty()) {
            return 0;
        }
        if (!ItemStackCompat.isSameItemSameComponents(available, expected)) {
            return 0;
        }

        StackedInsertion insertion = prepareStackedInsertion(target, bulkTarget, targetSlotMask);
        ItemStack simulatedInsert = available.copyWithCount(available.getCount());
        ItemStack simRemainder = insertion != null ? insertion.simulate(simulatedInsert)
                : insertItemWithAllowedSlots(target, bulkTarget, simulatedInsert, true, targetSlotMask);
        int acceptable = available.getCount() - simRemainder.getCount();
        if (acceptable <= 0) {
            return 0;
        }

        ItemStack toMove = source instanceof DirectItemAccess direct
                ? direct.extract(expected, acceptable, false)
                : source.extractItem(move.sourceSlot(), acceptable, false);
        if (toMove.isEmpty()) {
            return 0;
        }

        ItemStack uninserted = insertion != null ? insertion.commit(toMove)
                : insertItemWithAllowedSlots(target, bulkTarget, toMove, false, targetSlotMask);
        int accepted = toMove.getCount() - uninserted.getCount();

        if (!uninserted.isEmpty()) {
            ItemStack stillLeft = source instanceof DirectItemAccess direct ? direct.insert(uninserted, false)
                    : source.insertItem(move.sourceSlot(), uninserted, false);
            for (int fallback = 0; fallback < source.getSlots() && !stillLeft.isEmpty(); fallback++) {
                stillLeft = source.insertItem(fallback, stillLeft, false);
            }
            if (!stillLeft.isEmpty()) {
                ItemStack forcedRemainder = insertItemWithAllowedSlots(target, bulkTarget, stillLeft, false,
                        targetSlotMask);
                accepted += stillLeft.getCount() - forcedRemainder.getCount();
                if (!forcedRemainder.isEmpty()) {
                    LOGGER.error("ITEM VOIDING PREVENTED: could not return {} to source or target. Dropping at {}.",
                            forcedRemainder, sourceNode.getAttachedPos());
                    Block.popResource(sourceNode.level(), sourceNode.getAttachedPos(), forcedRemainder);
                    accepted += forcedRemainder.getCount();
                }
            }
        }

        return accepted;
    }

    private static @Nullable StackedInsertion prepareStackedInsertion(IItemHandler handler,
            @Nullable IItemHandler bulkHandler, @Nullable boolean[] allowedSlots) {
        return bulkHandler == null && allowedSlots == null && handler.getSlots() > 64
                && !(handler instanceof SnapshotItemHandler) && !(handler instanceof DirectItemAccess)
                ? new StackedInsertion(handler) : null;
    }

    private static boolean[] importMask(Map<ItemKey, boolean[]> masks, ItemTransferTarget target,
            ItemStack candidate, HolderLookup.Provider provider, @Nullable CandidateComponents candidateComponents,
            @Nullable FilterItemData.ReadCache filterReadCache) {
        ItemKey key = new ItemKey(candidate.getItem(), candidate.getTag());
        if (!masks.containsKey(key))
            masks.put(key, computeImportAllowedSlots(target.handler(), target.importFilters(),
                    target.importFilterMode(), candidate, provider, candidateComponents, filterReadCache));
        boolean[] mask = masks.get(key);
        // importCap mutates its copy
        return mask == null ? null : mask.clone();
    }

    static boolean[] computeImportAllowedSlots(IItemHandler handler, ItemStack[] importFilters,
            FilterMode importFilterMode, ItemStack candidate, HolderLookup.Provider provider,
            @Nullable CandidateComponents candidateComponents, @Nullable FilterItemData.ReadCache filterReadCache) {
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
            if (handler instanceof DirectItemAccess direct) {
                return direct.insert(stack, simulate);
            }
            if (bulkHandler != null) {
                return insertBulkItem(bulkHandler, stack, simulate);
            }
            if (handler instanceof SnapshotItemHandler snapshot) {
                // Forge stacking reads item caps
                return stack.isStackable() || handler.getSlots() < 2 ? snapshot.insertStacked(stack, simulate)
                        : insertUnstackableItem(handler, stack, simulate);
            }
            // Belt segments show passing items
            return stack.isStackable() || handler.getSlots() < 2
                    ? ItemHandlerHelper.insertItemStacked(handler, stack, simulate)
                    : insertUnstackableItem(handler, stack, simulate);
        }
        if (handler instanceof IItemHandlerModifiable modifiable && !(handler instanceof SnapshotItemHandler)) {
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

    private static ItemStack insertUnstackableItem(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack remaining = stack;
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            ItemStack slotStack = handler.getStackInSlot(slot);
            if (!slotStack.isEmpty()) {
                int limit = handler.getSlotLimit(slot);
                // Plain slot already full
                if (slotStack.getCount() >= limit && limit <= slotStack.getMaxStackSize()
                        && ItemStackCompat.isSameItemSameComponents(slotStack, remaining)) {
                    continue;
                }
            }
            remaining = handler.insertItem(slot, remaining, simulate);
        }
        return remaining;
    }

    private static ItemStack insertBulkItem(IItemHandler handler, ItemStack stack, boolean simulate) {
        if (handler instanceof SnapshotItemHandler snapshot) {
            return snapshot.insertBulkItem(stack, simulate);
        }
        return DirectStorageHandlers.insertItem(handler, stack, simulate);
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
        TransferAmountRules.Constraints amountConstraints = TransferAmountRules.collect(exportFilters, importFilters,
                filterReadCache);

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

            int allowedByAmount = TransferAmountRules.allowedFluids(source, target, simulated, amountConstraints);
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
                if (ChannelTelemetry.isRecording()) {
                    ChannelTelemetry.recordMoved(new FlowResource.Fluid(drained), filled);
                }
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
            case IGNORED -> true;
            case HIGH -> signalStrength > 0;
            case LOW -> signalStrength == 0;
        };
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
