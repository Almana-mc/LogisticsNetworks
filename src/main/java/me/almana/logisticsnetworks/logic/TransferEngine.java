package me.almana.logisticsnetworks.logic;

import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.CandidateComponents;
import me.almana.logisticsnetworks.integration.ars.ArsCompat;
import me.almana.logisticsnetworks.integration.ars.SourceTransferHelper;
import me.almana.logisticsnetworks.integration.create.CreateCompat;
import me.almana.logisticsnetworks.integration.mekanism.ChemicalTransferHelper;
import me.almana.logisticsnetworks.integration.mekanism.DirectChemicalHandler;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.integration.sophisticated.SophisticatedCoreCompat;
import me.almana.logisticsnetworks.integration.storage.DirectItemAccess;
import me.almana.logisticsnetworks.integration.storage.DirectStorageHandlers;
import me.almana.logisticsnetworks.integration.storage.InterfaceStorageResolution;
import me.almana.logisticsnetworks.integration.storage.ItemResource;
import me.almana.logisticsnetworks.item.RecipeCardItem;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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

    private record CapKey(ResourceKey<Level> dim, long pos, int side) {
    }

    private static CapKey capKey(ServerLevel level, BlockPos pos, @Nullable Direction dir) {
        return new CapKey(level.dimension(), pos.asLong(), dir == null ? IoSide.NONE.ordinal() : dir.ordinal());
    }

    static final class CapCache {
        private final Map<CapKey, Object> items = new HashMap<>();
        private final Map<CapKey, Object> fluids = new HashMap<>();
        private final Map<CapKey, Object> energy = new HashMap<>();
        private final Map<CapKey, Object> chemicals = new HashMap<>();
        private static final Object ABSENT = new Object();
        final TransferCapabilityCache interfaces = new TransferCapabilityCache(this);

        IItemHandler getItemHandler(ServerLevel level, BlockPos pos, @Nullable Direction dir) {
            CapKey key = capKey(level, pos, dir);
            Object cached = items.get(key);
            if (cached == ABSENT) return null;
            if (cached != null) return (IItemHandler) cached;
            BlockEntity be = level.getBlockEntity(pos);
            IItemHandler handler = be != null ? be.getCapability(ForgeCapabilities.ITEM_HANDLER, dir).orElse(null) : null;
            items.put(key, handler != null ? handler : ABSENT);
            return handler;
        }

        IFluidHandler getFluidHandler(ServerLevel level, BlockPos pos, @Nullable Direction dir) {
            CapKey key = capKey(level, pos, dir);
            Object cached = fluids.get(key);
            if (cached == ABSENT) return null;
            if (cached != null) return (IFluidHandler) cached;
            BlockEntity be = level.getBlockEntity(pos);
            IFluidHandler handler = be != null ? be.getCapability(ForgeCapabilities.FLUID_HANDLER, dir).orElse(null) : null;
            fluids.put(key, handler != null ? handler : ABSENT);
            return handler;
        }

        IEnergyStorage getEnergyHandler(ServerLevel level, BlockPos pos, @Nullable Direction dir) {
            CapKey key = capKey(level, pos, dir);
            Object cached = energy.get(key);
            if (cached == ABSENT) return null;
            if (cached != null) return (IEnergyStorage) cached;
            BlockEntity be = level.getBlockEntity(pos);
            IEnergyStorage handler = be != null ? be.getCapability(ForgeCapabilities.ENERGY, dir).orElse(null) : null;
            energy.put(key, handler != null ? handler : ABSENT);
            return handler;
        }

        IItemHandler findItemHandler(ServerLevel level, BlockPos pos, IoSide side) {
            Direction dir = side.direction();
            if (dir != null) return getItemHandler(level, pos, dir);
            // Mekanism null side read-only
            if (side == IoSide.NONE) return MekanismCompat.isMekanismBlock(level, pos) ? null : getItemHandler(level, pos, null);
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

        IFluidHandler findFluidHandler(ServerLevel level, BlockPos pos, IoSide side) {
            Direction dir = side.direction();
            if (dir != null) return getFluidHandler(level, pos, dir);
            if (side == IoSide.NONE) return MekanismCompat.isMekanismBlock(level, pos) ? null : getFluidHandler(level, pos, null);
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

        IEnergyStorage findEnergyHandler(ServerLevel level, BlockPos pos, IoSide side) {
            Direction dir = side.direction();
            if (dir != null) return getEnergyHandler(level, pos, dir);
            if (side == IoSide.NONE) return MekanismCompat.isMekanismBlock(level, pos) ? null : getEnergyHandler(level, pos, null);
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
        IChemicalHandler findChemicalHandler(ServerLevel level, BlockPos pos, IoSide side) {
            Direction dir = side.direction();
            CapKey key = new CapKey(level.dimension(), pos.asLong(), side.ordinal());
            Object cached = chemicals.get(key);
            if (cached == ABSENT) return null;
            if (cached != null) return (IChemicalHandler) cached;
            IChemicalHandler handler = side == IoSide.NONE ? ChemicalTransferHelper.getUnsidedHandler(level, pos)
                    : ChemicalTransferHelper.getHandler(level, pos, dir);
            chemicals.put(key, handler != null ? handler : ABSENT);
            return handler;
        }
    }

    public record ImportTarget(LogisticsNodeEntity node, ChannelData channel, int channelIndex) {
    }

    public record ItemTransferTarget(IItemHandler handler, @Nullable IItemHandler bulkHandler,
            ItemStack[] importFilters,
            FilterMode importFilterMode, TransferAmountRules.Constraints constraints, boolean hasItemNbtFilter,
            boolean[] allowedSlots, boolean hasImportSlotMapping, @Nullable EntryFaces<ItemTransferTarget> faces) {
        public ItemTransferTarget(IItemHandler handler, @Nullable IItemHandler bulkHandler, ItemStack[] importFilters,
                FilterMode importFilterMode, TransferAmountRules.Constraints constraints, boolean hasItemNbtFilter,
                boolean[] allowedSlots, boolean hasImportSlotMapping) {
            this(handler, bulkHandler, importFilters, importFilterMode, constraints, hasItemNbtFilter, allowedSlots,
                    hasImportSlotMapping, null);
        }
    }

    public record ResolvedItemTargets(List<ItemTransferTarget> targets, List<ImportTarget> refs, int status) {
        public static final int OK = 0;
        public static final int NO_REACHABLE = -1;
        public static final int PAUSED = -2;
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
        void record(int sourceSlot, int targetIndex, ItemStack moved, boolean[] targetSlotMask, @Nullable IoSide face);
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
            if (!canRunChannel(sourceNode, channel) || !CreateCompat.isResolved(sourceNode))
                continue;
            if (!isRedstoneActive(channel.getRedstoneMode(), redstoneSignal))
                continue;

            int types = NodeUpgradeData.effectiveTypes(sourceNode, channel);
            boolean cards = RecipeCardItem.anyIn(channel.getFilterItems());
            ChannelTelemetry.startRecording(telemetryActive ? channel.getTelemetry() : null);
            if (cards) {
                long delta = recipeLane(sourceNode, sourceLevel, channel, i, itemImports[i], fluidImports[i],
                        chemicalImports[i], sourceTier, gameTime, dimensionalCache, capCache.interfaces,
                        telemetryActive);
                if (delta < minWakeDelta) minWakeDelta = delta;
            }

            for (ChannelType type : ChannelType.ORDER) {
                if (!type.in(types))
                    continue;
                List<ImportTarget> typed = importsFor(type, i, itemImports, fluidImports, energyImports,
                        chemicalImports, sourceImports);
                if (typed.isEmpty() || cards && !RecipeChannel.runsLoose(channel, type))
                    continue;
                if (type == ChannelType.ENERGY && !canExtractEnergy(sourceNode, sourceLevel, channel, capCache)) {
                    minWakeDelta = Math.min(minWakeDelta, Config.backoffMaxTicks);
                    continue;
                }
                if (sourceNode.isMountedOnCreate() && !hasMountedStorage(sourceNode, channel, type, capCache.interfaces))
                    continue;

                int lane = type.ordinal();
                long cooldown = cooldownRemaining(sourceNode, channel, i, lane, sourceTier, gameTime);
                if (cooldown > 0) {
                    if (cooldown < minWakeDelta) minWakeDelta = cooldown;
                    continue;
                }
                if (type == ChannelType.ITEM && !includeItemTransfers && !cards)
                    continue;

                // Item targets order on resolve
                if (type != ChannelType.ITEM)
                    typed = orderTargets(typed, channel, sourceNode, type);
                int batch = NodeUpgradeData.effectiveBatch(channel, type, sourceTier);
                int result = switch (type) {
                    case ITEM -> transferItems(sourceNode, sourceLevel, channel, i, typed, batch, dimensionalCache,
                            capCache.interfaces, telemetryActive);
                    case FLUID -> transferFluids(sourceNode, sourceLevel, channel, typed, batch, dimensionalCache,
                            capCache);
                    case ENERGY -> transferEnergy(sourceNode, sourceLevel, channel, typed, batch, dimensionalCache,
                            capCache);
                    case CHEMICAL -> transferChemicals(sourceNode, sourceLevel, channel, typed, batch,
                            dimensionalCache, capCache);
                    case SOURCE -> transferSource(sourceNode, sourceLevel, channel, typed, batch,
                            dimensionalCache);
                };
                if (result < 0)
                    continue;

                long wakeDelta = finishChannelAttempt(sourceNode, channel, i, lane, result, gameTime, sourceTier,
                        telemetryActive);
                if (wakeDelta < minWakeDelta) minWakeDelta = wakeDelta;
            }
            ChannelTelemetry.startRecording(null);
        }

        return minWakeDelta;
    }

    private static long recipeLane(LogisticsNodeEntity sourceNode, ServerLevel level, ChannelData channel, int index,
            List<ImportTarget> items, List<ImportTarget> fluids, List<ImportTarget> chemicals, int tier,
            long gameTime, Map<UUID, Boolean> dimensionalCache, TransferCapabilityCache capCache,
            boolean telemetryActive) {
        if (items.isEmpty() && fluids.isEmpty() && chemicals.isEmpty())
            return Long.MAX_VALUE;
        long cooldown = cooldownRemaining(sourceNode, channel, index, ChannelData.RECIPE_LANE, tier, gameTime);
        if (cooldown > 0)
            return cooldown;
        int sets = RecipeChannel.transfer(sourceNode, level, channel, RecipeChannel.receivers(items, fluids, chemicals),
                NodeUpgradeData.recipeBudget(channel), tier, dimensionalCache, capCache, telemetryActive);
        if (sets < 0)
            return Long.MAX_VALUE;
        return finishChannelAttempt(sourceNode, channel, index, ChannelData.RECIPE_LANE, sets, gameTime, tier,
                telemetryActive);
    }

    private static List<ImportTarget> importsFor(ChannelType type, int channel, List<ImportTarget>[] items,
            List<ImportTarget>[] fluids, List<ImportTarget>[] energy, List<ImportTarget>[] chemicals,
            List<ImportTarget>[] source) {
        return switch (type) {
            case ITEM -> items[channel];
            case FLUID -> fluids[channel];
            case ENERGY -> energy[channel];
            case CHEMICAL -> chemicals[channel];
            case SOURCE -> source[channel];
        };
    }

    private static boolean canExtractEnergy(LogisticsNodeEntity node, ServerLevel level, ChannelData channel,
            CapCache capCache) {
        var handler = capCache.findEnergyHandler(level, node.getAttachedPos(), channel.getIoSide());
        return handler != null && handler.canExtract();
    }

    private static boolean hasMountedStorage(LogisticsNodeEntity node, ChannelData channel, ChannelType type,
            TransferCapabilityCache capCache) {
        return switch (type) {
            case ITEM -> capCache.findItemExportHandler(node, channel.getIoSide(), true) != null;
            case FLUID -> capCache.findFluidExportHandler(node, channel.getIoSide()) != null;
            default -> false;
        };
    }

    public static long finishChannelAttempt(LogisticsNodeEntity node, ChannelData channel, int index, int lane,
            int result, long gameTime, int tier, boolean telemetryActive) {
        if (telemetryActive && result > 0 && lane < ChannelData.RECIPE_LANE) {
            channel.getTelemetry().record(ChannelType.values()[lane], result);
        }

        updateBackoff(node, channel, index, lane, result > 0, gameTime, tier);
        if (result > 0) {
            return 0L;
        }
        return Math.max(1L, cooldownRemaining(node, channel, index, lane, tier, gameTime));
    }

    public static long cooldownRemaining(LogisticsNodeEntity node, ChannelData channel, int index, int lane,
            int tier, long gameTime) {
        long lastRun = node.getLastExecution(index, lane);
        long configuredDelay = configuredDelay(channel, lane, tier);
        float backoff = node.getBackoffTicks(index, lane);
        long effectiveDelay = backoffEnabled(lane) ? Math.max(configuredDelay, (long) backoff) : configuredDelay;
        long elapsed = gameTime - lastRun;
        return Math.max(0L, effectiveDelay - elapsed);
    }

    private static int configuredDelay(ChannelData channel, int lane, int tier) {
        return lane == ChannelType.ENERGY.ordinal() ? 1
                : Math.max(channel.getTickDelay(), NodeUpgradeData.getMinTickDelay(tier));
    }

    // Recipe lane follows item backoff
    private static boolean backoffEnabled(int lane) {
        return Config.backoffEnabled[lane == ChannelData.RECIPE_LANE ? ChannelType.ITEM.ordinal() : lane];
    }

    private static void updateBackoff(LogisticsNodeEntity node, ChannelData channel, int index, int lane,
            boolean success, long gameTime, int tier) {
        node.setLastExecution(index, lane, gameTime);
        int configuredDelay = configuredDelay(channel, lane, tier);

        if (success) {
            float curBackoff = node.getBackoffTicks(index, lane);
            if (curBackoff > configuredDelay) {
                node.setBackoffTicks(index, lane, Math.max(configuredDelay, curBackoff / BACKOFF_DECAY_DIVISOR));
            }
        } else if (backoffEnabled(lane)) {
            float maxBackoff = lane == ChannelType.ENERGY.ordinal()
                    ? BACKOFF_MAX_TICKS_ENERGY : (float) Config.backoffMaxTicks;
            float curBackoff = Math.max(node.getBackoffTicks(index, lane), configuredDelay);
            if (curBackoff <= configuredDelay) {
                float nextThreshold = (configuredDelay + 1.05f) / BACKOFF_MULTIPLIER;
                node.setBackoffTicks(index, lane,
                        Math.min(maxBackoff, Math.max(configuredDelay + 0.1f, nextThreshold)));
            } else {
                node.setBackoffTicks(index, lane, Math.min(maxBackoff, curBackoff * BACKOFF_MULTIPLIER));
            }
        }
    }

    static List<ImportTarget> orderTargets(List<ImportTarget> targets, ChannelData exportChannel,
            LogisticsNodeEntity sourceNode, ChannelType type) {
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
                return PriorityRobin.rotate(targets, exportChannel.getRobinCursor(type),
                        t -> t.channel().getPriority(), t -> t.node().getUUID());
            }
            default -> {
                return targets;
            }
        }
    }

    private static void markServed(ChannelData exportChannel, ChannelType type, ImportTarget target) {
        exportChannel.setRobinCursor(type,
                new PriorityRobin.Cursor(target.channel().getPriority(), target.node().getUUID()));
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
        if (!sourceNode.isMountedOnCreate() && !sourceLevel.isLoaded(sourcePos))
            return new ItemResourceOrder.Result(null, -1);
        ItemStack[] exportFilters = exportChannel.getFilterItems();
        FilterItemData.ReadCache filterReadCache = FilterItemData.createReadCache();
        boolean directSource = !FilterLogic.hasConfiguredSlotMapping(exportFilters, filterReadCache);
        IItemHandler sourceHandler = capCache.findItemExportHandler(
                sourceNode, exportChannel.getIoSide(), directSource);
        if (sourceHandler == null)
            return ItemResourceOrder.EMPTY;

        boolean[] sourceAllowedSlots = null;

        ResolvedItemTargets resolved = resolveItemTargets(sourceNode, sourceLevel, exportChannel, targets,
                sourceHandler, dimensionalCache, capCache, filterReadCache);
        if (resolved.status() == ResolvedItemTargets.NO_REACHABLE
                || resolved.status() == ResolvedItemTargets.PAUSED) {
            return new ItemResourceOrder.Result(null, -1);
        }
        if (resolved.targets().isEmpty()) {
            return ItemResourceOrder.EMPTY;
        }

        DistributionMode distribution = exportChannel.getDistributionMode();
        MoveRecorder served = distribution == DistributionMode.PRIORITY_ROBIN
                ? (slot, index, moved, mask, face) -> markServed(exportChannel, ChannelType.ITEM,
                        resolved.refs().get(index))
                : null;
        MoveRecorder recorder = telemetryActive ? withTelemetry(served, exportChannel.getTelemetry()) : served;
        ItemResourceOrder.Result result = executeItemOperation(sourceHandler, resolved.targets(),
                batchLimit, exportFilters, exportChannel.getFilterMode(),
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
        return (slot, index, moved, mask, face) -> {
            telemetry.recordResource(new FlowResource.Item(moved), moved.getCount());
            if (next != null) {
                next.record(slot, index, moved, mask, face);
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
                || channel.getMode() != ChannelMode.EXPORT || !channel.hasType(ChannelType.ITEM)
                || !canRunChannel(sourceNode, channel) || !CreateCompat.isResolved(sourceNode)
                || !isRedstoneActive(channel.getRedstoneMode(), signal)
                || RecipeCardItem.anyIn(channel.getFilterItems())) {
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
        int currentBatch = NodeUpgradeData.effectiveBatch(channel, ChannelType.ITEM, tier);
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

        targets = orderTargets(targets, exportChannel, sourceNode, ChannelType.ITEM);
        BlockPos sourcePos = sourceNode.getAttachedPos();
        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        boolean anyReachable = false;
        boolean hasUsableTarget = false;
        boolean hasUnavailableMountedTarget = false;
        boolean hasStationaryTarget = false;
        List<ItemTransferTarget> reachableTargets = new ArrayList<>(targets.size());
        List<ImportTarget> resolvedRefs = new ArrayList<>(targets.size());
        ItemStack[] exportFilters = exportChannel.getFilterItems();
        boolean exportSides = FilterLogic.hasConfiguredEntrySides(exportFilters, filterReadCache);

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

            ItemStack[] importFilters = target.channel.getFilterItems();
            boolean hasImportSlotMapping = FilterLogic.hasConfiguredSlotMapping(importFilters, filterReadCache);
            IItemHandler targetHandler = capCache.findItemImportHandler(
                    target.node, target.channel.getIoSide(), !hasImportSlotMapping);
            if (targetHandler == null) {
                hasUnavailableMountedTarget |= target.node.isMountedOnCreate();
                continue;
            }
            hasUsableTarget = true;
            if (sourceHandler == targetHandler || DirectStorageHandlers.shareNetwork(sourceHandler, targetHandler))
                continue;

            boolean[] targetAllowedSlots = null;
            IItemHandler bulkHandler = hasImportSlotMapping
                    ? null
                    : capCache.findBulkItemHandler(target.node, targetHandler);
            TransferAmountRules.Constraints constraints = TransferAmountRules.collect(exportFilters, importFilters,
                    filterReadCache);
            boolean hasItemNbtFilter = FilterLogic.hasConfiguredItemNbtFilter(importFilters, filterReadCache);
            boolean importSides = FilterLogic.hasConfiguredEntrySides(importFilters, filterReadCache);

            reachableTargets.add(new ItemTransferTarget(
                    targetHandler,
                    bulkHandler,
                    importFilters,
                    target.channel.getFilterMode(),
                    constraints,
                    hasItemNbtFilter,
                    targetAllowedSlots,
                    hasImportSlotMapping,
                    exportSides || importSides ? new EntryFaces<>(target.channel.getIoSide(), importSides,
                            exportSides, side -> itemFace(target, side, sourceHandler, capCache, constraints,
                                    hasItemNbtFilter, hasImportSlotMapping)) : null));
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

    @Nullable
    private static ItemTransferTarget itemFace(ImportTarget target, IoSide side, IItemHandler sourceHandler,
            TransferCapabilityCache capCache, TransferAmountRules.Constraints constraints, boolean hasItemNbtFilter,
            boolean hasImportSlotMapping) {
        IItemHandler handler = capCache.findItemImportHandler(target.node, side, !hasImportSlotMapping);
        if (handler == null || handler == sourceHandler || DirectStorageHandlers.shareNetwork(sourceHandler, handler))
            return null;
        return new ItemTransferTarget(handler,
                hasImportSlotMapping ? null : capCache.findBulkItemHandler(target.node, handler),
                target.channel.getFilterItems(), target.channel.getFilterMode(), constraints, hasItemNbtFilter, null,
                hasImportSlotMapping);
    }

    private record FluidTarget(IFluidHandler handler, ImportTarget ref, @Nullable EntryFaces<IFluidHandler> faces) {
    }

    public record FluidTransferTarget(IFluidHandler handler, ItemStack[] filters, FilterMode mode) {
    }

    private static int transferFluids(LogisticsNodeEntity sourceNode, ServerLevel sourceLevel,
            ChannelData exportChannel, List<ImportTarget> targets, int batchLimitMb,
            Map<UUID, Boolean> dimensionalCache, CapCache capCache) {

        BlockPos sourcePos = sourceNode.getAttachedPos();
        if (!sourceNode.isMountedOnCreate() && !sourceLevel.isLoaded(sourcePos))
            return -1;
        IFluidHandler sourceHandler = capCache.interfaces.findFluidExportHandler(sourceNode,
                exportChannel.getIoSide());
        if (sourceHandler == null)
            return 0;

        List<FluidTarget> resolved = new ArrayList<>(targets.size());
        FilterItemData.ReadCache cache = FilterItemData.createReadCache();
        boolean exportSides = FilterLogic.hasConfiguredEntrySides(exportChannel.getFilterItems(), cache);
        if (!resolveFluidTargets(sourceNode, sourceHandler, targets, dimensionalCache, capCache, exportSides, cache,
                resolved))
            return -1;
        HolderLookup.Provider provider = sourceLevel.registryAccess();
        if (exportChannel.canRotateResources())
            return rotateFluids(sourceHandler, resolved, exportChannel, batchLimitMb, provider, cache);
        if (exportChannel.getDistributionMode() == DistributionMode.ROUND_ROBIN)
            return dealFluids(sourceHandler, resolved, batchLimitMb, exportChannel, provider, cache);
        return pushFluids(sourceHandler, resolved, exportChannel, batchLimitMb, FluidStack.EMPTY, provider, cache);
    }

    // False if unreachable or paused
    private static boolean resolveFluidTargets(LogisticsNodeEntity sourceNode, IFluidHandler sourceHandler,
            List<ImportTarget> targets, Map<UUID, Boolean> dimensionalCache, CapCache capCache,
            boolean exportSides, FilterItemData.ReadCache cache, List<FluidTarget> out) {
        boolean sourceDimensional = dimensionalCache.getOrDefault(sourceNode.getUUID(), false);
        boolean anyReachable = false;
        boolean hasUsableTarget = false;
        boolean hasUnavailableMountedTarget = false;
        boolean hasStationaryTarget = false;
        for (ImportTarget target : targets) {
            if (target.node.getUUID().equals(sourceNode.getUUID()) || !target.node.isValidNode()
                    || !canReach(sourceNode, target.node, sourceDimensional, dimensionalCache))
                continue;
            anyReachable = true;
            hasStationaryTarget |= !target.node.isMountedOnCreate();
            ServerLevel targetLevel = (ServerLevel) target.node.level();
            BlockPos targetPos = target.node.getAttachedPos();
            if (!target.node.isMountedOnCreate() && !targetLevel.isLoaded(targetPos))
                continue;
            IFluidHandler handler = capCache.interfaces.findFluidImportHandler(target.node,
                    target.channel.getIoSide());
            if (handler == null) {
                hasUnavailableMountedTarget |= target.node.isMountedOnCreate();
                continue;
            }
            hasUsableTarget = true;
            if (handler == sourceHandler || DirectStorageHandlers.shareNetwork(sourceHandler, handler))
                continue;
            boolean importSides = FilterLogic.hasConfiguredEntrySides(target.channel.getFilterItems(), cache);
            out.add(new FluidTarget(handler, target, exportSides || importSides
                    ? new EntryFaces<>(target.channel.getIoSide(), importSides, exportSides,
                            side -> fluidFace(target, side, sourceHandler, capCache))
                    : null));
        }
        return anyReachable && !shouldPauseForUnavailableMountedTargets(hasUsableTarget,
                hasUnavailableMountedTarget, hasStationaryTarget);
    }

    @Nullable
    private static IFluidHandler fluidFace(ImportTarget target, IoSide side, IFluidHandler sourceHandler,
            CapCache capCache) {
        IFluidHandler handler = capCache.interfaces.findFluidImportHandler(target.node, side);
        return handler == null || handler == sourceHandler || DirectStorageHandlers.shareNetwork(sourceHandler, handler)
                ? null : handler;
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
                    target.ref().channel.getFilterMode(), provider, cache, resource, target.faces());
            remaining -= filled;
            if (filled > 0 && robin) {
                markServed(exportChannel, ChannelType.FLUID, target.ref());
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
                        provider, cache, resource, targets.get(i).faces()));
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
        IEnergyStorage sourceHandler = capCache.findEnergyHandler(sourceLevel, sourcePos, exportChannel.getIoSide());
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

            IEnergyStorage targetHandler = capCache.findEnergyHandler(targetLevel, targetPos, target.channel.getIoSide());
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
                    markServed(exportChannel, ChannelType.ENERGY, target);
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
        IChemicalHandler sourceHandler = capCache.interfaces.findChemicalHandler(sourceNode,
                exportChannel.getIoSide(), true);
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
        @SuppressWarnings("rawtypes")
        List<EntryFaces<IChemicalHandler>> equalFaces = equalHandlers == null ? null : new ArrayList<>();
        boolean robin = exportChannel.getDistributionMode() == DistributionMode.PRIORITY_ROBIN;
        boolean exportSides = FilterLogic.hasConfiguredEntrySides(exportChannel.getFilterItems(), filterReadCache);

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
            IChemicalHandler targetHandler = capCache.interfaces.findChemicalHandler(target.node(),
                    target.channel().getIoSide(), false);
            if (targetHandler == null || DirectChemicalHandler.shareNetwork(sourceHandler, targetHandler))
                continue;
            boolean importSides = FilterLogic.hasConfiguredEntrySides(target.channel().getFilterItems(),
                    filterReadCache);
            @SuppressWarnings("rawtypes")
            EntryFaces<IChemicalHandler> faces = exportSides || importSides
                    ? new EntryFaces<>(target.channel().getIoSide(), importSides, exportSides,
                            side -> chemicalFace(target, side, sourceHandler, capCache))
                    : null;

            if (equalHandlers != null) {
                equalHandlers.add(targetHandler);
                equalChannels.add(target.channel());
                equalFaces.add(faces);
                continue;
            }
            long moved = ChemicalTransferHelper.transferBetween(
                    sourceHandler, targetHandler, remaining,
                    exportChannel.getFilterItems(), exportChannel.getFilterMode(),
                    target.channel().getFilterItems(), target.channel().getFilterMode(),
                    filterReadCache, null, faces);
            if (Config.debugMode)
                LOGGER.debug("[Chemical] Transfer {} -> {}: moved={}, batch={}",
                        sourcePos, targetPos, moved, remaining);
            if (moved > 0) {
                remaining -= (int) moved;
                if (robin) {
                    markServed(exportChannel, ChannelType.CHEMICAL, target);
                    break;
                }
            }
        }

        if (Config.debugMode && !anyReachable)
            LOGGER.debug("[Chemical] No reachable targets for {}", sourcePos);
        if (!anyReachable)
            return -1;
        if (equalHandlers != null)
            return dealChemicals(sourceHandler, equalHandlers, equalChannels, equalFaces, batchLimit, exportChannel,
                    filterReadCache);
        return batchLimit - remaining;
    }

    @SuppressWarnings("rawtypes")
    @Nullable
    private static IChemicalHandler chemicalFace(ImportTarget target, IoSide side, IChemicalHandler sourceHandler,
            CapCache capCache) {
        IChemicalHandler handler = capCache.interfaces.findChemicalHandler(target.node(), side, false);
        return handler == null || DirectChemicalHandler.shareNetwork(sourceHandler, handler) ? null : handler;
    }

    @SuppressWarnings("rawtypes")
    private static int dealChemicals(IChemicalHandler source, List<IChemicalHandler> targets,
            List<ChannelData> channels, List<EntryFaces<IChemicalHandler>> faces, int limit, ChannelData exportChannel,
            FilterItemData.ReadCache cache) {
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
                            channels.get(i).getFilterItems(), channels.get(i).getFilterMode(), cache, stock.getKey(),
                            faces.get(i)));
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

        SourceTransferHelper.SourceEnd from = sourceEnd(sourceNode, exportChannel.getIoSide());
        boolean robin = exportChannel.getDistributionMode() == DistributionMode.PRIORITY_ROBIN;
        List<SourceTransferHelper.SourceEnd> equalTargets =
                exportChannel.getDistributionMode() == DistributionMode.ROUND_ROBIN ? new ArrayList<>() : null;
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
            if (from == null || !targetLevel.isLoaded(targetPos))
                continue;
            SourceTransferHelper.SourceEnd to = sourceEnd(target.node(), target.channel().getIoSide());
            if (to == null)
                continue;
            if (equalTargets != null) {
                equalTargets.add(to);
                continue;
            }

            int moved = SourceTransferHelper.transfer(from, to, remaining);
            if (Config.debugMode)
                LOGGER.debug("[Source] Transfer {} -> {}: moved={}, batch={}",
                        sourcePos, targetPos, moved, batchLimit);
            if (moved > 0) {
                remaining -= moved;
                if (robin) {
                    markServed(exportChannel, ChannelType.SOURCE, target);
                    break;
                }
            }
        }

        if (!anyReachable)
            return -1;
        if (equalTargets != null) {
            return equalTargets.isEmpty() ? 0 : (int) EvenSplit.deal(equalTargets.size(), from.extract(remaining, true),
                    (i, share) -> SourceTransferHelper.transfer(from, equalTargets.get(i), (int) share));
        }
        return batchLimit - remaining;
    }

    @Nullable
    static SourceTransferHelper.SourceEnd sourceEnd(LogisticsNodeEntity node, IoSide side) {
        Direction direction = side.direction();
        if (side != IoSide.NONE) {
            InterfaceStorageResolution resolution = TransferCapabilityCache.resolveInterface(node, direction);
            if (resolution.status() == InterfaceStorageResolution.Status.AVAILABLE)
                return SourceTransferHelper.grid(resolution.endpoint());
            if (resolution.status() == InterfaceStorageResolution.Status.UNAVAILABLE)
                return null;
        }
        return SourceTransferHelper.resolve((ServerLevel) node.level(), node.getAttachedPos(), direction);
    }

    static boolean canReach(LogisticsNodeEntity source, LogisticsNodeEntity target, boolean sourceDim,
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
                MoveRecorder single = (slot, ignored, moved, mask, face) -> recorder.record(slot, index, moved, mask,
                        face);
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
        var sourceItemCounts = sourceNeedsCounts
                ? TransferAmountRules.countItems(source, countCandidates) : null;
        var batchMoved = anyAmountConstraints && !roundRobin
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
        Map<ItemTransferTarget, Map<ItemKey, boolean[]>> importMasks = new IdentityHashMap<>();
        Map<ItemTransferTarget, Map<Item, Integer>> faceItemCounts = null;

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
                boolean movedForTarget = false;
                int targetRemaining = roundRobin ? share : remaining;

                int slotCount = sourceSlots == null ? source.getSlots() : sourceSlots.length;
                if (slotVerdicts.length < slotCount) {
                    // Source grew, recheck slots
                    slotVerdicts = new byte[slotCount];
                    slotStacks = new ItemStack[slotCount];
                    if (slotComponents != null) {
                        slotComponents = new CandidateComponents[Math.max(slotComponents.length, slotCount)];
                    }
                }
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
                            || resource != null && !resource.matches(extracted))) {
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

                    ItemTransferTarget face = target;
                    IoSide side = null;
                    if (target.faces() != null && provider != null) {
                        side = target.faces().imports() ? FilterLogic.itemSide(target.importFilters(), extracted,
                                provider, candidateComponents, filterReadCache, -1) : null;
                        if (side == null && target.faces().exports()) {
                            side = FilterLogic.itemSide(exportFilters, extracted, provider, candidateComponents,
                                    filterReadCache, slot);
                        }
                        face = target.faces().face(side, target);
                        if (face == null) {
                            continue;
                        }
                    }
                    IItemHandler targetHandler = face.handler();
                    IItemHandler bulkHandler = face.bulkHandler();

                    boolean[] importAllowedSlots = target.allowedSlots();
                    if (provider != null) {
                        if (target.hasImportSlotMapping()) {
                            importAllowedSlots = importMask(importMasks.computeIfAbsent(face, f -> new HashMap<>()),
                                    face, extracted, provider, candidateComponents, filterReadCache);
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
                        Map<Item, Integer> targetCounts = targetItemCounts.get(targetIndex);
                        if (targetCounts != null && face != target) {
                            if (faceItemCounts == null) {
                                faceItemCounts = new IdentityHashMap<>();
                            }
                            targetCounts = faceItemCounts.computeIfAbsent(face,
                                    f -> TransferAmountRules.countItems(f.handler(), countCandidates));
                        }
                        if (targetCounts != null && targetHandler instanceof DirectItemAccess direct) {
                            targetCounts = direct.countItems(countCandidates);
                            if (face == target) {
                                targetItemCounts.set(targetIndex, targetCounts);
                            } else {
                                faceItemCounts.put(face, targetCounts);
                            }
                        }
                        allowedByAmount = TransferAmountRules.allowedItems(extracted, target.constraints(),
                                sourceItemCounts, targetCounts);
                        if (target.constraints().hasPerEntryAmounts() && provider != null) {
                            int perEntry = TransferAmountRules.perEntryItemAmount(extracted, exportFilters,
                                    target.importFilters(), source, slot, targetHandler, importAllowedSlots,
                                    sourceItemCounts,
                                    targetCounts, provider, candidateComponents,
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
                            targetHandler, bulkHandler, importAllowedSlots);
                    ItemStack simRemainder;
                    if (bulkHandler != null) {
                        if (bulkInsertRejections == null) {
                            bulkInsertRejections = new BulkInsertRejectionCache(
                                    (handler, stack) -> insertBulkItem(handler, stack, true));
                        }
                        simRemainder = bulkInsertRejections.simulate(bulkHandler, simulatedInsert);
                    } else if (importAllowedSlots == null) {
                        if (insertRejections == null) {
                            insertRejections = new BulkInsertRejectionCache(
                                    (handler, stack) -> insertItemWithAllowedSlots(handler, null, stack, true, null));
                        }
                        simRemainder = insertion == null
                                ? insertRejections.simulate(targetHandler, simulatedInsert)
                                : insertRejections.simulate(targetHandler, simulatedInsert,
                                        (handler, stack) -> insertion.simulate(stack));
                    } else {
                        simRemainder = insertItemWithAllowedSlots(targetHandler, null,
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
                            : insertItemWithAllowedSlots(targetHandler, bulkHandler,
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
                                ItemStack forcedRemainder = insertItemWithAllowedSlots(targetHandler,
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
                                    dropAtSource(sourceLevel, sourcePos, forcedRemainder);
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
                                    importAllowedSlots, face == target ? null : side);
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
                            Map<Item, Integer> tgtCache = face == target ? targetItemCounts.get(targetIndex)
                                    : faceItemCounts == null ? null : faceItemCounts.get(face);
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
                    || resource != null && !resource.matches(available)
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
                    dropAtSource(sourceNode.level(), sourceNode.getAttachedPos(), forcedRemainder);
                    accepted += forcedRemainder.getCount();
                }
            }
        }

        return accepted;
    }

    // popResource obeys doTileDrops
    static void dropAtSource(Level level, BlockPos pos, ItemStack stack) {
        double halfHeight = EntityType.ITEM.getHeight() / 2.0;
        ItemEntity item = new ItemEntity(level,
                pos.getX() + 0.5 + Mth.nextDouble(level.random, -0.25, 0.25),
                pos.getY() + 0.5 + Mth.nextDouble(level.random, -0.25, 0.25) - halfHeight,
                pos.getZ() + 0.5 + Mth.nextDouble(level.random, -0.25, 0.25), stack);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
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

    static ItemStack insertItemWithAllowedSlots(IItemHandler handler, @Nullable IItemHandler bulkHandler,
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
        if (DirectStorageHandlers.isDirect(handler)) {
            return DirectStorageHandlers.insertItem(handler, stack, simulate);
        }
        return SophisticatedCoreCompat.insertItem(handler, stack, simulate);
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
            HolderLookup.Provider provider, @Nullable FilterItemData.ReadCache filterReadCache, FluidStack required,
            @Nullable EntryFaces<IFluidHandler> faces) {

        int remaining = limitMb;
        TransferAmountRules.Constraints amountConstraints = TransferAmountRules.collect(exportFilters, importFilters,
                filterReadCache);
        IFluidHandler channelTarget = target;

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
            if (faces != null) {
                IoSide side = faces.imports() ? FilterLogic.fluidSide(importFilters, simulated, filterReadCache) : null;
                if (side == null && faces.exports())
                    side = FilterLogic.fluidSide(exportFilters, simulated, filterReadCache);
                target = faces.face(side, channelTarget);
                if (target == null)
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

    public static boolean canRunChannel(LogisticsNodeEntity node, ChannelData channel) {
        return !node.isMountedOnCreate() || channel.getRedstoneMode() != RedstoneMode.HIGH;
    }

    static boolean shouldPauseForUnavailableMountedTargets(boolean hasUsableTarget,
            boolean hasUnavailableMountedTarget, boolean hasStationaryTarget) {
        return !hasUsableTarget && hasUnavailableMountedTarget && !hasStationaryTarget;
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
