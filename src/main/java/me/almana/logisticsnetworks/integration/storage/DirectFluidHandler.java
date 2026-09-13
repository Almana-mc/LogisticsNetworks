package me.almana.logisticsnetworks.integration.storage;

import me.almana.logisticsnetworks.logic.async.ThreadGuard;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

public final class DirectFluidHandler implements IFluidHandler {
    private final StorageEndpoint endpoint;
    private final boolean exporting;
    private List<StorageEndpoint.StoredFluid> exports;

    DirectFluidHandler(StorageEndpoint endpoint, boolean exporting) {
        ThreadGuard.requireServerThread();
        this.endpoint = endpoint;
        this.exporting = exporting;
    }

    StorageEndpoint endpoint() {
        return endpoint;
    }

    private List<StorageEndpoint.StoredFluid> exports() {
        ThreadGuard.requireServerThread();
        if (exports == null) exports = exporting ? endpoint.exportableFluids(true) : List.of();
        return exports;
    }

    public int count(FluidStack candidate) {
        ThreadGuard.requireServerThread();
        if (!exporting) return DirectStorageReads.clamp(endpoint.fluidCount(candidate, true));
        for (var entry : endpoint.exportableFluids(true)) {
            if (FluidStack.isSameFluidSameComponents(entry.stack(), candidate)) return DirectStorageReads.clamp(entry.amount());
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
        return stored.stack().copyWithAmount(DirectStorageReads.clamp(stored.amount()));
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
        ThreadGuard.requireServerThread();
        if (stack.isEmpty() || !endpoint.isValid()) return 0;
        return Math.min(stack.getAmount(), DirectStorageReads.clamp(endpoint.insertFluid(stack, stack.getAmount(), action.simulate())));
    }

    @Override
    public FluidStack drain(FluidStack stack, FluidAction action) {
        ThreadGuard.requireServerThread();
        if (!exporting || stack.isEmpty() || !endpoint.isValid() || !endpoint.canExportFluid(stack)) return FluidStack.EMPTY;
        int moved = Math.min(stack.getAmount(), DirectStorageReads.clamp(endpoint.extractFluid(stack, stack.getAmount(), action.simulate())));
        return moved == 0 ? FluidStack.EMPTY : stack.copyWithAmount(moved);
    }

    @Override
    public FluidStack drain(int amount, FluidAction action) {
        if (amount <= 0 || exports().isEmpty()) return FluidStack.EMPTY;
        return drain(exports().getFirst().stack().copyWithAmount(amount), action);
    }
}
