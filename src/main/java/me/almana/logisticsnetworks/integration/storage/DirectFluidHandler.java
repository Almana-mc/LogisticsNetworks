package me.almana.logisticsnetworks.integration.storage;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.List;

public final class DirectFluidHandler implements IFluidHandler {
    private final StorageEndpoint endpoint;
    private final boolean exporting;
    private List<StorageEndpoint.StoredFluid> exports;

    DirectFluidHandler(StorageEndpoint endpoint, boolean exporting) {
        this.endpoint = endpoint;
        this.exporting = exporting;
    }

    StorageEndpoint endpoint() {
        return endpoint;
    }

    private List<StorageEndpoint.StoredFluid> exports() {
        if (exports == null) exports = exporting ? endpoint.exportableFluids(true) : List.of();
        return exports;
    }

    public int count(FluidStack candidate) {
        if (!exporting) return DirectStorageReads.clamp(endpoint.fluidCount(candidate, true));
        for (var entry : endpoint.exportableFluids(true)) {
            if (entry.stack().isFluidEqual(candidate)) return DirectStorageReads.clamp(entry.amount());
        }
        return 0;
    }

    @Override
    public int getTanks() {
        return Math.max(1, exports().size());
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        if (tank < 0 || tank >= exports().size() || !endpoint.isValid()) return FluidStack.EMPTY;
        var stored = exports().get(tank);
        return new FluidStack(stored.stack(), DirectStorageReads.clamp(stored.amount()));
    }

    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return !stack.isEmpty() && endpoint.isValid();
    }

    @Override
    public int fill(FluidStack stack, FluidAction action) {
        if (stack.isEmpty() || !endpoint.isValid()) return 0;
        return Math.min(stack.getAmount(), DirectStorageReads.clamp(endpoint.insertFluid(stack, stack.getAmount(), action.simulate())));
    }

    @Override
    public FluidStack drain(FluidStack stack, FluidAction action) {
        if (!exporting || stack.isEmpty() || !endpoint.isValid() || !endpoint.canExportFluid(stack)) return FluidStack.EMPTY;
        int moved = Math.min(stack.getAmount(), DirectStorageReads.clamp(endpoint.extractFluid(stack, stack.getAmount(), action.simulate())));
        return moved == 0 ? FluidStack.EMPTY : new FluidStack(stack, moved);
    }

    @Override
    public FluidStack drain(int amount, FluidAction action) {
        if (amount <= 0 || exports().isEmpty()) return FluidStack.EMPTY;
        return drain(new FluidStack(exports().get(0).stack(), amount), action);
    }
}
