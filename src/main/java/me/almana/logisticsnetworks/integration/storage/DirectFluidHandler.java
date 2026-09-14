package me.almana.logisticsnetworks.integration.storage;

import me.almana.logisticsnetworks.logic.async.ThreadGuard;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DirectFluidHandler implements ResourceHandler<FluidResource> {
    private final StorageEndpoint endpoint;
    private final boolean exporting;
    private final ChangeJournal journal = new ChangeJournal();
    private List<StorageEndpoint.StoredFluid> exports;
    private Map<FluidResource, Long> changes = new LinkedHashMap<>();

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
        FluidResource resource = FluidResource.of(candidate);
        return DirectStorageReads.clamp(add(endpoint.fluidCount(candidate, true),
                changes.getOrDefault(resource, 0L)));
    }

    @Override
    public int size() {
        return Math.max(1, exports().size());
    }

    @Override
    public FluidResource getResource(int index) {
        return index >= 0 && index < exports().size()
                ? FluidResource.of(exports().get(index).stack()) : FluidResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        if (index < 0 || index >= exports().size()) return 0;
        StorageEndpoint.StoredFluid stored = exports().get(index);
        return add(stored.amount(), changes.getOrDefault(FluidResource.of(stored.stack()), 0L));
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return index >= 0 && index < size() && !resource.isEmpty() ? Integer.MAX_VALUE : 0;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return index >= 0 && index < size() && !resource.isEmpty() && endpoint.isValid();
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return index >= 0 && index < size() ? insert(resource, amount, transaction) : 0;
    }

    @Override
    public int insert(FluidResource resource, int amount, TransactionContext transaction) {
        ThreadGuard.requireServerThread();
        if (amount <= 0 || resource.isEmpty() || !endpoint.isValid()) return 0;
        long current = changes.getOrDefault(resource, 0L);
        int accepted = acceptedInsert(resource, amount, current);
        if (accepted > 0) move(resource, accepted, transaction);
        return accepted;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (index < 0 || index >= exports().size() || !resource.equals(getResource(index))) return 0;
        return extract(resource, amount, transaction);
    }

    @Override
    public int extract(FluidResource resource, int amount, TransactionContext transaction) {
        ThreadGuard.requireServerThread();
        FluidStack stack = resource.toStack(1);
        if (!exporting || amount <= 0 || resource.isEmpty() || !endpoint.isValid()
                || !endpoint.canExportFluid(stack)) return 0;
        long current = changes.getOrDefault(resource, 0L);
        int accepted = acceptedExtract(resource, amount, current);
        if (accepted > 0) move(resource, -accepted, transaction);
        return accepted;
    }

    private void move(FluidResource resource, int delta, TransactionContext transaction) {
        journal.updateSnapshots(transaction);
        changes.merge(resource, (long) delta, Long::sum);
    }

    private int acceptedInsert(FluidResource resource, int amount, long current) {
        int cancelled = (int) Math.min(amount, Math.max(0, -current));
        int remaining = amount - cancelled;
        if (remaining == 0) return cancelled;
        long pending = Math.max(0, current);
        long accepted = endpoint.insertFluid(resource.toStack(1), pending + remaining, true) - pending;
        return cancelled + clampRequest(accepted, remaining);
    }

    private int acceptedExtract(FluidResource resource, int amount, long current) {
        int cancelled = (int) Math.min(amount, Math.max(0, current));
        int remaining = amount - cancelled;
        if (remaining == 0) return cancelled;
        long pending = Math.max(0, -current);
        long accepted = endpoint.extractFluid(resource.toStack(1), pending + remaining, true) - pending;
        return cancelled + clampRequest(accepted, remaining);
    }

    private final class ChangeJournal extends SnapshotJournal<Map<FluidResource, Long>> {
        @Override
        protected Map<FluidResource, Long> createSnapshot() {
            return new LinkedHashMap<>(changes);
        }

        @Override
        protected void revertToSnapshot(Map<FluidResource, Long> snapshot) {
            changes = snapshot;
        }

        @Override
        protected void onRootCommit(Map<FluidResource, Long> originalState) {
            Map<FluidResource, Long> committed = changes;
            changes = new LinkedHashMap<>();
            for (Map.Entry<FluidResource, Long> entry : committed.entrySet()) {
                if (entry.getValue() > 0) {
                    endpoint.insertFluid(entry.getKey().toStack(1), entry.getValue(), false);
                } else if (entry.getValue() < 0) {
                    endpoint.extractFluid(entry.getKey().toStack(1), -entry.getValue(), false);
                }
            }
        }
    }

    private static long add(long amount, long delta) {
        return delta > 0 && amount > Long.MAX_VALUE - delta ? Long.MAX_VALUE : Math.max(0, amount + delta);
    }

    private static int clampRequest(long amount, int request) {
        return (int) Math.min(Math.max(0, amount), request);
    }
}
