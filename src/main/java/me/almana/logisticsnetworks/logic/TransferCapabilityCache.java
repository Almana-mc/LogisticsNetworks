package me.almana.logisticsnetworks.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TransferCapabilityCache {
    private static final Object ABSENT = new Object();

    private final Map<Long, Object> items = new HashMap<>();
    private final Map<Long, Object> fluids = new HashMap<>();
    private final Map<Long, Object> energy = new HashMap<>();

    private static long key(ServerLevel level, BlockPos pos, Direction dir) {
        long packed = pos.asLong();
        packed ^= ((long) dir.ordinal()) << 58;
        packed ^= ((long) level.dimension().identifier().hashCode()) << 32;
        return packed;
    }

    ResourceHandler<ItemResource> findItemHandler(ServerLevel level, BlockPos pos, @Nullable Direction dir) {
        if (dir != null) {
            return getItemHandler(level, pos, dir);
        }
        List<ResourceHandler<ItemResource>> found = new ArrayList<>(6);
        for (Direction side : Direction.values()) {
            ResourceHandler<ItemResource> handler = getItemHandler(level, pos, side);
            if (handler != null && !containsIdentity(found, handler)) {
                found.add(handler);
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        return found.size() == 1 ? found.get(0) : new CombinedResourceHandler<>(found);
    }

    ResourceHandler<FluidResource> findFluidHandler(ServerLevel level, BlockPos pos, @Nullable Direction dir) {
        if (dir != null) {
            return getFluidHandler(level, pos, dir);
        }
        List<ResourceHandler<FluidResource>> found = new ArrayList<>(6);
        for (Direction side : Direction.values()) {
            ResourceHandler<FluidResource> handler = getFluidHandler(level, pos, side);
            if (handler != null && !containsIdentity(found, handler)) {
                found.add(handler);
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        return found.size() == 1 ? found.get(0) : new CombinedResourceHandler<>(found);
    }

    EnergyHandler findEnergyHandler(ServerLevel level, BlockPos pos, @Nullable Direction dir) {
        if (dir != null) {
            return getEnergyHandler(level, pos, dir);
        }
        List<EnergyHandler> found = new ArrayList<>(6);
        for (Direction side : Direction.values()) {
            EnergyHandler handler = getEnergyHandler(level, pos, side);
            if (handler != null && !containsIdentity(found, handler)) {
                found.add(handler);
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        return found.size() == 1 ? found.get(0) : new CombinedEnergyHandler(found.toArray(EnergyHandler[]::new));
    }

    @SuppressWarnings("unchecked")
    private ResourceHandler<ItemResource> getItemHandler(ServerLevel level, BlockPos pos, Direction dir) {
        long key = key(level, pos, dir);
        Object cached = items.get(key);
        if (cached == ABSENT) {
            return null;
        }
        if (cached != null) {
            return (ResourceHandler<ItemResource>) cached;
        }
        ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, pos, dir);
        items.put(key, handler != null ? handler : ABSENT);
        return handler;
    }

    @SuppressWarnings("unchecked")
    private ResourceHandler<FluidResource> getFluidHandler(ServerLevel level, BlockPos pos, Direction dir) {
        long key = key(level, pos, dir);
        Object cached = fluids.get(key);
        if (cached == ABSENT) {
            return null;
        }
        if (cached != null) {
            return (ResourceHandler<FluidResource>) cached;
        }
        ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK, pos, dir);
        fluids.put(key, handler != null ? handler : ABSENT);
        return handler;
    }

    private EnergyHandler getEnergyHandler(ServerLevel level, BlockPos pos, Direction dir) {
        long key = key(level, pos, dir);
        Object cached = energy.get(key);
        if (cached == ABSENT) {
            return null;
        }
        if (cached != null) {
            return (EnergyHandler) cached;
        }
        EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, pos, dir);
        energy.put(key, handler != null ? handler : ABSENT);
        return handler;
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
