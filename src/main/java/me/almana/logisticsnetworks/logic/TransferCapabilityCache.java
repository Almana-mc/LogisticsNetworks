package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.IoSide;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.integration.storage.DirectStorageHandlers;
import me.almana.logisticsnetworks.integration.storage.InterfaceStorageResolution;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import me.almana.logisticsnetworks.integration.storage.StorageEndpoint;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class TransferCapabilityCache {
    private static final ThreadLocal<StorageOperation> STORAGE_OPERATION = new ThreadLocal<>();

    private final LogisticsNodeEntity node;

    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<ResourceHandler<ItemResource>, Direction>[] items = new BlockCapabilityCache[6];
    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<ResourceHandler<FluidResource>, Direction>[] fluids = new BlockCapabilityCache[6];
    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<EnergyHandler, Direction>[] energy = new BlockCapabilityCache[6];
    // Memoised combine while faces match
    private static final EnergyHandler[] NO_ENERGY = new EnergyHandler[0];
    private EnergyHandler[] combinedEnergyOf = NO_ENERGY;
    private EnergyHandler combinedEnergy;

    public TransferCapabilityCache(LogisticsNodeEntity node) {
        this.node = node;
    }

    public static StorageOperation storageOperation() {
        return new StorageOperation();
    }

    public static final class StorageOperation implements AutoCloseable {
        private final StorageOperation previous;
        private final Map<Object, ResourceHandler<ItemResource>[]> handlers = new IdentityHashMap<>();

        private StorageOperation() {
            previous = STORAGE_OPERATION.get();
            STORAGE_OPERATION.set(this);
        }

        @SuppressWarnings("unchecked")
        private ResourceHandler<ItemResource> itemHandler(StorageEndpoint endpoint,
                @Nullable ResourceHandler<ItemResource> buffer, boolean exporting) {
            ResourceHandler<ItemResource>[] views = handlers.computeIfAbsent(
                    endpoint.endpointIdentity(), ignored -> new ResourceHandler[2]);
            int index = exporting ? 0 : 1;
            ResourceHandler<ItemResource> existing = views[index];
            if (existing == null || DirectStorageHandlers.networkIdentity(existing) != endpoint.networkIdentity()) {
                views[index] = exporting ? DirectStorageHandlers.exportItems(endpoint, buffer)
                        : DirectStorageHandlers.importItems(endpoint);
            }
            return views[index];
        }

        @Override
        public void close() {
            if (previous == null) STORAGE_OPERATION.remove();
            else STORAGE_OPERATION.set(previous);
            handlers.clear();
        }
    }

    public void reset() {
        Arrays.fill(items, null);
        Arrays.fill(fluids, null);
        Arrays.fill(energy, null);
    }

    @Nullable
    public ResourceHandler<ItemResource> findItemHandler(IoSide side) {
        return findItemHandler(side, false, false);
    }

    @Nullable
    public ResourceHandler<ItemResource> findItemExportHandler(IoSide side, boolean directInterfaces) {
        return findItemHandler(side, directInterfaces, true);
    }

    @Nullable
    public ResourceHandler<ItemResource> findItemImportHandler(IoSide side, boolean directInterfaces) {
        return findItemHandler(side, directInterfaces, false);
    }

    @Nullable
    private ResourceHandler<ItemResource> findItemHandler(IoSide side, boolean directInterfaces,
            boolean exporting) {
        if (side == IoSide.NONE) return unsided(Capabilities.Item.BLOCK);
        return findItemHandler(side.direction(), directInterfaces, exporting);
    }

    @Nullable
    private ResourceHandler<ItemResource> findItemHandler(@Nullable Direction direction,
            boolean directInterfaces, boolean exporting) {
        if (!(node.level() instanceof ServerLevel level)) return null;
        InterfaceStorageResolution resolution = resolveInterface(level, direction);
        if (resolution.status() == InterfaceStorageResolution.Status.AVAILABLE && directInterfaces) {
            ResourceHandler<ItemResource> buffer = exporting
                    ? findInterfaceBuffer(level, direction, resolution.endpoint())
                    : null;
            StorageOperation operation = STORAGE_OPERATION.get();
            if (operation != null) return operation.itemHandler(resolution.endpoint(), buffer, exporting);
            return exporting
                    ? DirectStorageHandlers.exportItems(resolution.endpoint(), buffer)
                    : DirectStorageHandlers.importItems(resolution.endpoint());
        }
        if (resolution.status() == InterfaceStorageResolution.Status.UNAVAILABLE) return null;
        return findItemCapability(level, direction);
    }

    InterfaceStorageResolution resolveInterface(ServerLevel level, @Nullable Direction direction) {
        if (!NodeUpgradeData.hasNetworkUpgrade(node)) return InterfaceStorageResolution.unsupported();
        return LinkedStorage.resolveInterface(level, node.getAttachedPos(), direction);
    }

    @Nullable
    private ResourceHandler<ItemResource> findInterfaceBuffer(ServerLevel level, @Nullable Direction direction,
            StorageEndpoint endpoint) {
        if (direction != null) return itemSide(level, direction);
        for (Direction side : Direction.values()) {
            InterfaceStorageResolution resolution = LinkedStorage.resolveInterface(level, node.getAttachedPos(), side);
            if (resolution.status() == InterfaceStorageResolution.Status.AVAILABLE
                    && resolution.endpoint().endpointIdentity() == endpoint.endpointIdentity()) {
                return itemSide(level, side);
            }
        }
        return null;
    }

    @Nullable
    public ResourceHandler<FluidResource> findFluidHandler(IoSide side) {
        return findFluidHandler(side, false, false);
    }

    @Nullable
    public ResourceHandler<FluidResource> findFluidExportHandler(IoSide side, boolean directInterfaces) {
        return findFluidHandler(side, directInterfaces, true);
    }

    @Nullable
    public ResourceHandler<FluidResource> findFluidImportHandler(IoSide side, boolean directInterfaces) {
        return findFluidHandler(side, directInterfaces, false);
    }

    @Nullable
    private ResourceHandler<FluidResource> findFluidHandler(IoSide side, boolean directInterfaces,
            boolean exporting) {
        if (side == IoSide.NONE) return unsided(Capabilities.Fluid.BLOCK);
        return findFluidHandler(side.direction(), directInterfaces, exporting);
    }

    @Nullable
    private ResourceHandler<FluidResource> findFluidHandler(@Nullable Direction direction,
            boolean directInterfaces, boolean exporting) {
        if (!(node.level() instanceof ServerLevel level)) return null;
        InterfaceStorageResolution resolution = resolveInterface(level, direction);
        if (resolution.status() == InterfaceStorageResolution.Status.AVAILABLE && directInterfaces) {
            return exporting
                    ? DirectStorageHandlers.exportFluids(resolution.endpoint())
                    : DirectStorageHandlers.importFluids(resolution.endpoint());
        }
        if (resolution.status() == InterfaceStorageResolution.Status.UNAVAILABLE) return null;
        return findFluidCapability(level, direction);
    }

    @Nullable
    public EnergyHandler findEnergyHandler(IoSide side) {
        if (side == IoSide.NONE) return unsided(Capabilities.Energy.BLOCK);
        return findEnergyHandler(side.direction());
    }

    @Nullable
    private EnergyHandler findEnergyHandler(@Nullable Direction direction) {
        if (!(node.level() instanceof ServerLevel level)) return null;
        if (direction != null) return energySide(level, direction);
        List<EnergyHandler> found = new ArrayList<>(6);
        for (Direction side : Direction.values()) {
            EnergyHandler handler = energySide(level, side);
            if (handler != null && !containsIdentity(found, handler)) found.add(handler);
        }
        if (found.size() <= 1) {
            combinedEnergyOf = NO_ENERGY;
            combinedEnergy = null;
            return found.isEmpty() ? null : found.get(0);
        }
        if (!sameHandlers(found, combinedEnergyOf)) {
            combinedEnergyOf = found.toArray(EnergyHandler[]::new);
            combinedEnergy = new CombinedEnergyHandler(combinedEnergyOf);
        }
        return combinedEnergy;
    }

    @Nullable
    private ResourceHandler<ItemResource> findItemCapability(ServerLevel level, @Nullable Direction direction) {
        if (direction != null) return itemSide(level, direction);
        if (level.getBlockState(node.getAttachedPos()).getBlock() instanceof ChestBlock) {
            return itemSide(level, Direction.UP);
        }
        List<ResourceHandler<ItemResource>> found = new ArrayList<>(6);
        for (Direction side : Direction.values()) {
            ResourceHandler<ItemResource> handler = itemSide(level, side);
            if (handler != null && !containsIdentity(found, handler)) found.add(handler);
        }
        if (found.isEmpty()) return null;
        return found.size() == 1 ? found.get(0) : new CombinedResourceHandler<>(found);
    }

    @Nullable
    private ResourceHandler<FluidResource> findFluidCapability(ServerLevel level, @Nullable Direction direction) {
        if (direction != null) return fluidSide(level, direction);
        List<ResourceHandler<FluidResource>> found = new ArrayList<>(6);
        for (Direction side : Direction.values()) {
            ResourceHandler<FluidResource> handler = fluidSide(level, side);
            if (handler != null && !containsIdentity(found, handler)) found.add(handler);
        }
        if (found.isEmpty()) return null;
        return found.size() == 1 ? found.get(0) : new CombinedResourceHandler<>(found);
    }

    // Mekanism null side read-only
    @Nullable
    private <T> T unsided(BlockCapability<T, @Nullable Direction> capability) {
        if (!(node.level() instanceof ServerLevel level)) return null;
        BlockPos pos = node.getAttachedPos();
        if (MekanismCompat.isMekanismBlock(level, pos)) return null;
        return level.getCapability(capability, pos, null);
    }

    private ResourceHandler<ItemResource> itemSide(ServerLevel level, Direction direction) {
        int index = direction.ordinal();
        BlockCapabilityCache<ResourceHandler<ItemResource>, Direction> cache = items[index];
        if (cache == null) {
            cache = BlockCapabilityCache.create(Capabilities.Item.BLOCK, level, node.getAttachedPos(), direction,
                    () -> !node.isRemoved(), () -> {
                    });
            items[index] = cache;
        }
        return cache.getCapability();
    }

    private ResourceHandler<FluidResource> fluidSide(ServerLevel level, Direction direction) {
        int index = direction.ordinal();
        BlockCapabilityCache<ResourceHandler<FluidResource>, Direction> cache = fluids[index];
        if (cache == null) {
            cache = BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, level, node.getAttachedPos(), direction,
                    () -> !node.isRemoved(), () -> {
                    });
            fluids[index] = cache;
        }
        return cache.getCapability();
    }

    private EnergyHandler energySide(ServerLevel level, Direction direction) {
        int index = direction.ordinal();
        BlockCapabilityCache<EnergyHandler, Direction> cache = energy[index];
        if (cache == null) {
            cache = BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level, node.getAttachedPos(), direction,
                    () -> !node.isRemoved(), () -> {
                    });
            energy[index] = cache;
        }
        return cache.getCapability();
    }

    // Enable when 26.1.2 is supported.
    /*
    @Nullable
    private IChemicalHandler findChemicalHandler(ServerLevel level, IoSide side) {
        if (side == IoSide.NONE) return ChemicalTransferHelper.getUnsidedHandler(level, node.getAttachedPos());
        return ChemicalTransferHelper.getHandler(level, node.getAttachedPos(), side.direction());
    }

    @Nullable
    public IChemicalHandler findChemicalHandler(IoSide side, boolean exporting) {
        ServerLevel level = (ServerLevel) node.level();
        if (side == IoSide.NONE || node.isMountedOnCreate()) return findChemicalHandler(level, side);
        InterfaceStorageResolution resolution = resolveInterface(level, side.direction());
        if (resolution.status() == InterfaceStorageResolution.Status.UNAVAILABLE) return null;
        if (resolution.status() == InterfaceStorageResolution.Status.AVAILABLE && resolution.endpoint().storesChemicals())
            return DirectChemicalHandler.of(resolution.endpoint(), exporting);
        return findChemicalHandler(level, side);
    }
    */

    private static <T> boolean containsIdentity(List<T> values, T candidate) {
        for (T value : values) {
            if (value == candidate) return true;
        }
        return false;
    }

    private static boolean sameHandlers(List<EnergyHandler> found, EnergyHandler[] previous) {
        if (found.size() != previous.length) return false;
        for (int i = 0; i < previous.length; i++) {
            if (found.get(i) != previous[i]) return false;
        }
        return true;
    }
}
