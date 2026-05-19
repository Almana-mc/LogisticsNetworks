package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.integration.mekanism.ChemicalTransferHelper;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
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
    private final Map<Long, Object> chemicals = new HashMap<>();

    IItemHandler findItemHandler(ServerLevel level, BlockPos pos, @Nullable Direction dir) {
        if (dir != null) return getItemHandler(level, pos, dir);
        if (level.getBlockState(pos).getBlock() instanceof ChestBlock) {
            return getItemHandler(level, pos, Direction.UP);
        }
        List<IItemHandler> found = new ArrayList<>(6);
        for (Direction side : Direction.values()) {
            IItemHandler handler = getItemHandler(level, pos, side);
            if (handler != null && !containsIdentity(found, handler)) {
                found.add(handler);
            }
        }
        if (found.isEmpty()) return null;
        return found.size() == 1 ? found.get(0) : new CombinedItemHandler(found.toArray(IItemHandler[]::new));
    }

    IFluidHandler findFluidHandler(ServerLevel level, BlockPos pos, @Nullable Direction dir) {
        if (dir != null) return getFluidHandler(level, pos, dir);
        List<IFluidHandler> found = new ArrayList<>(6);
        for (Direction side : Direction.values()) {
            IFluidHandler handler = getFluidHandler(level, pos, side);
            if (handler != null && !containsIdentity(found, handler)) {
                found.add(handler);
            }
        }
        if (found.isEmpty()) return null;
        return found.size() == 1 ? found.get(0) : new CombinedFluidHandler(found.toArray(IFluidHandler[]::new));
    }

    IEnergyStorage findEnergyHandler(ServerLevel level, BlockPos pos, @Nullable Direction dir) {
        if (dir != null) return getEnergyHandler(level, pos, dir);
        List<IEnergyStorage> found = new ArrayList<>(6);
        for (Direction side : Direction.values()) {
            IEnergyStorage handler = getEnergyHandler(level, pos, side);
            if (handler != null && !containsIdentity(found, handler)) {
                found.add(handler);
            }
        }
        if (found.isEmpty()) return null;
        return found.size() == 1 ? found.get(0) : new CombinedEnergyHandler(found.toArray(IEnergyStorage[]::new));
    }

    IChemicalHandler findChemicalHandler(ServerLevel level, BlockPos pos, @Nullable Direction dir) {
        long key = key(level, pos, dir == null ? Direction.DOWN : dir);
        if (dir == null) key ^= 0x1L << 62;
        Object cached = chemicals.get(key);
        if (cached == ABSENT) return null;
        if (cached != null) return (IChemicalHandler) cached;
        IChemicalHandler handler = ChemicalTransferHelper.getHandler(level, pos, dir);
        chemicals.put(key, handler != null ? handler : ABSENT);
        return handler;
    }

    private IItemHandler getItemHandler(ServerLevel level, BlockPos pos, Direction dir) {
        long key = key(level, pos, dir);
        Object cached = items.get(key);
        if (cached == ABSENT) return null;
        if (cached != null) return (IItemHandler) cached;
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir);
        items.put(key, handler != null ? handler : ABSENT);
        return handler;
    }

    private IFluidHandler getFluidHandler(ServerLevel level, BlockPos pos, Direction dir) {
        long key = key(level, pos, dir);
        Object cached = fluids.get(key);
        if (cached == ABSENT) return null;
        if (cached != null) return (IFluidHandler) cached;
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, dir);
        fluids.put(key, handler != null ? handler : ABSENT);
        return handler;
    }

    private IEnergyStorage getEnergyHandler(ServerLevel level, BlockPos pos, Direction dir) {
        long key = key(level, pos, dir);
        Object cached = energy.get(key);
        if (cached == ABSENT) return null;
        if (cached != null) return (IEnergyStorage) cached;
        IEnergyStorage handler = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, dir);
        energy.put(key, handler != null ? handler : ABSENT);
        return handler;
    }

    private static long key(ServerLevel level, BlockPos pos, Direction dir) {
        long packed = pos.asLong();
        packed ^= ((long) dir.ordinal()) << 58;
        packed ^= ((long) level.dimension().location().hashCode()) << 32;
        return packed;
    }

    private static <T> boolean containsIdentity(List<T> values, T candidate) {
        for (T value : values) {
            if (value == candidate) return true;
        }
        return false;
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
            int index = handlerIndex(slot);
            return handlers[index].getStackInSlot(slot - slotOffsets[index]);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= totalSlots) return stack;
            int index = handlerIndex(slot);
            return handlers[index].insertItem(slot - slotOffsets[index], stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= totalSlots) return ItemStack.EMPTY;
            int index = handlerIndex(slot);
            return handlers[index].extractItem(slot - slotOffsets[index], amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot < 0 || slot >= totalSlots) return 0;
            int index = handlerIndex(slot);
            return handlers[index].getSlotLimit(slot - slotOffsets[index]);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < 0 || slot >= totalSlots) return false;
            int index = handlerIndex(slot);
            return handlers[index].isItemValid(slot - slotOffsets[index], stack);
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
            int index = handlerIndex(tank);
            return handlers[index].getFluidInTank(tank - tankOffsets[index]);
        }

        @Override
        public int getTankCapacity(int tank) {
            if (tank < 0 || tank >= totalTanks) return 0;
            int index = handlerIndex(tank);
            return handlers[index].getTankCapacity(tank - tankOffsets[index]);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            if (tank < 0 || tank >= totalTanks) return false;
            int index = handlerIndex(tank);
            return handlers[index].isFluidValid(tank - tankOffsets[index], stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            int remaining = resource.getAmount();
            int filled = 0;
            for (IFluidHandler handler : handlers) {
                if (remaining <= 0) break;
                int accepted = handler.fill(resource.copyWithAmount(remaining), action);
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
            for (IFluidHandler handler : handlers) {
                if (remaining <= 0) break;
                FluidStack drained = handler.drain(resource.copyWithAmount(remaining), action);
                if (drained.isEmpty()) continue;
                if (template.isEmpty()) template = drained;
                totalAmount += drained.getAmount();
                remaining -= drained.getAmount();
            }
            return template.isEmpty() ? FluidStack.EMPTY : template.copyWithAmount(totalAmount);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) return FluidStack.EMPTY;
            int remaining = maxDrain;
            int totalAmount = 0;
            FluidStack template = FluidStack.EMPTY;
            for (IFluidHandler handler : handlers) {
                if (remaining <= 0) break;
                FluidStack drained = template.isEmpty()
                        ? handler.drain(remaining, action)
                        : handler.drain(template.copyWithAmount(remaining), action);
                if (drained.isEmpty()) continue;
                if (template.isEmpty()) template = drained;
                totalAmount += drained.getAmount();
                remaining -= drained.getAmount();
            }
            return template.isEmpty() ? FluidStack.EMPTY : template.copyWithAmount(totalAmount);
        }
    }
}
