package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import java.util.List;

public final class TransferCapabilityCache {
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

    public void reset() {
        Arrays.fill(items, null);
        Arrays.fill(fluids, null);
        Arrays.fill(energy, null);
    }

    public ResourceHandler<ItemResource> findItemHandler(@Nullable Direction dir) {
        if (!(node.level() instanceof ServerLevel level)) {
            return null;
        }
        if (dir != null) {
            return itemSide(level, dir);
        }
        List<ResourceHandler<ItemResource>> found = new ArrayList<>(6);
        for (Direction side : Direction.values()) {
            ResourceHandler<ItemResource> handler = itemSide(level, side);
            if (handler != null && !containsIdentity(found, handler)) {
                found.add(handler);
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        return found.size() == 1 ? found.get(0) : new CombinedResourceHandler<>(found);
    }

    public ResourceHandler<FluidResource> findFluidHandler(@Nullable Direction dir) {
        if (!(node.level() instanceof ServerLevel level)) {
            return null;
        }
        if (dir != null) {
            return fluidSide(level, dir);
        }
        List<ResourceHandler<FluidResource>> found = new ArrayList<>(6);
        for (Direction side : Direction.values()) {
            ResourceHandler<FluidResource> handler = fluidSide(level, side);
            if (handler != null && !containsIdentity(found, handler)) {
                found.add(handler);
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        return found.size() == 1 ? found.get(0) : new CombinedResourceHandler<>(found);
    }

    public EnergyHandler findEnergyHandler(@Nullable Direction dir) {
        if (!(node.level() instanceof ServerLevel level)) {
            return null;
        }
        if (dir != null) {
            return energySide(level, dir);
        }
        List<EnergyHandler> found = new ArrayList<>(6);
        for (Direction side : Direction.values()) {
            EnergyHandler handler = energySide(level, side);
            if (handler != null && !containsIdentity(found, handler)) {
                found.add(handler);
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        return found.size() == 1 ? found.get(0) : new CombinedEnergyHandler(found.toArray(EnergyHandler[]::new));
    }

    private ResourceHandler<ItemResource> itemSide(ServerLevel level, Direction dir) {
        int i = dir.ordinal();
        BlockCapabilityCache<ResourceHandler<ItemResource>, Direction> cache = items[i];
        if (cache == null) {
            cache = BlockCapabilityCache.create(Capabilities.Item.BLOCK, level, node.getAttachedPos(), dir,
                    () -> !node.isRemoved(), () -> {
                    });
            items[i] = cache;
        }
        return cache.getCapability();
    }

    private ResourceHandler<FluidResource> fluidSide(ServerLevel level, Direction dir) {
        int i = dir.ordinal();
        BlockCapabilityCache<ResourceHandler<FluidResource>, Direction> cache = fluids[i];
        if (cache == null) {
            cache = BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, level, node.getAttachedPos(), dir,
                    () -> !node.isRemoved(), () -> {
                    });
            fluids[i] = cache;
        }
        return cache.getCapability();
    }

    private EnergyHandler energySide(ServerLevel level, Direction dir) {
        int i = dir.ordinal();
        BlockCapabilityCache<EnergyHandler, Direction> cache = energy[i];
        if (cache == null) {
            cache = BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level, node.getAttachedPos(), dir,
                    () -> !node.isRemoved(), () -> {
                    });
            energy[i] = cache;
        }
        return cache.getCapability();
    }

    private static <T> boolean containsIdentity(List<T> values, T candidate) {
        for (T value : values) {
            if (value == candidate) {
                return true;
            }
        }
        return false;
    }
}
