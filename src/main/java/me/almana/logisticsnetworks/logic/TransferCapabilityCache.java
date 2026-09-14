package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.storage.DirectStorageHandlers;
import me.almana.logisticsnetworks.integration.storage.DirectStorageReads;
import me.almana.logisticsnetworks.integration.storage.InterfaceStorageResolution;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import me.almana.logisticsnetworks.integration.storage.StorageEndpoint;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
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

    public TransferCapabilityCache(LogisticsNodeEntity node) {
        this.node = node;
    }

    public static StorageOperation storageOperation(boolean fresh) {
        return new StorageOperation(fresh);
    }

    public static final class StorageOperation implements AutoCloseable {
        private final StorageOperation previous;
        private final DirectStorageReads reads;
        private final Map<Object, ResourceHandler<ItemResource>[]> handlers = new IdentityHashMap<>();

        private StorageOperation(boolean fresh) {
            previous = STORAGE_OPERATION.get();
            reads = new DirectStorageReads(fresh);
            STORAGE_OPERATION.set(this);
        }

        @SuppressWarnings("unchecked")
        private ResourceHandler<ItemResource> itemHandler(StorageEndpoint endpoint, boolean exporting) {
            ResourceHandler<ItemResource>[] views = handlers.computeIfAbsent(
                    endpoint.endpointIdentity(), ignored -> new ResourceHandler[2]);
            int index = exporting ? 0 : 1;
            ResourceHandler<ItemResource> existing = views[index];
            if (existing == null || DirectStorageHandlers.networkIdentity(existing) != endpoint.networkIdentity()) {
                views[index] = exporting ? DirectStorageHandlers.exportItems(endpoint, reads)
                        : DirectStorageHandlers.importItems(endpoint, reads);
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
    public ResourceHandler<ItemResource> findItemHandler(@Nullable Direction direction) {
        return findItemHandler(direction, false, false);
    }

    @Nullable
    public ResourceHandler<ItemResource> findItemExportHandler(@Nullable Direction direction,
            boolean directInterfaces) {
        return findItemHandler(direction, directInterfaces, true);
    }

    @Nullable
    public ResourceHandler<ItemResource> findItemImportHandler(@Nullable Direction direction,
            boolean directInterfaces) {
        return findItemHandler(direction, directInterfaces, false);
    }

    @Nullable
    private ResourceHandler<ItemResource> findItemHandler(@Nullable Direction direction,
            boolean directInterfaces, boolean exporting) {
        if (!(node.level() instanceof ServerLevel level)) return null;
        InterfaceStorageResolution resolution = LinkedStorage.resolveInterface(
                level, node.getAttachedPos(), direction);
        if (resolution.status() == InterfaceStorageResolution.Status.AVAILABLE && directInterfaces) {
            StorageOperation operation = STORAGE_OPERATION.get();
            if (operation != null) return operation.itemHandler(resolution.endpoint(), exporting);
            return exporting
                    ? DirectStorageHandlers.exportItems(resolution.endpoint())
                    : DirectStorageHandlers.importItems(resolution.endpoint());
        }
        if (resolution.status() == InterfaceStorageResolution.Status.UNAVAILABLE) return null;
        return findItemCapability(level, direction);
    }

    @Nullable
    public ResourceHandler<FluidResource> findFluidHandler(@Nullable Direction direction) {
        return findFluidHandler(direction, false, false);
    }

    @Nullable
    public ResourceHandler<FluidResource> findFluidExportHandler(@Nullable Direction direction,
            boolean directInterfaces) {
        return findFluidHandler(direction, directInterfaces, true);
    }

    @Nullable
    public ResourceHandler<FluidResource> findFluidImportHandler(@Nullable Direction direction,
            boolean directInterfaces) {
        return findFluidHandler(direction, directInterfaces, false);
    }

    @Nullable
    private ResourceHandler<FluidResource> findFluidHandler(@Nullable Direction direction,
            boolean directInterfaces, boolean exporting) {
        if (!(node.level() instanceof ServerLevel level)) return null;
        InterfaceStorageResolution resolution = LinkedStorage.resolveInterface(
                level, node.getAttachedPos(), direction);
        if (resolution.status() == InterfaceStorageResolution.Status.AVAILABLE && directInterfaces) {
            return exporting
                    ? DirectStorageHandlers.exportFluids(resolution.endpoint())
                    : DirectStorageHandlers.importFluids(resolution.endpoint());
        }
        if (resolution.status() == InterfaceStorageResolution.Status.UNAVAILABLE) return null;
        return findFluidCapability(level, direction);
    }

    @Nullable
    public EnergyHandler findEnergyHandler(@Nullable Direction direction) {
        if (!(node.level() instanceof ServerLevel level)) return null;
        if (direction != null) return energySide(level, direction);
        List<EnergyHandler> found = new ArrayList<>(6);
        for (Direction side : Direction.values()) {
            EnergyHandler handler = energySide(level, side);
            if (handler != null && !containsIdentity(found, handler)) found.add(handler);
        }
        if (found.isEmpty()) return null;
        return found.size() == 1 ? found.get(0) : new CombinedEnergyHandler(found.toArray(EnergyHandler[]::new));
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

    private static <T> boolean containsIdentity(List<T> values, T candidate) {
        for (T value : values) {
            if (value == candidate) return true;
        }
        return false;
    }
}
