package me.almana.logisticsnetworks.integration.storage;

import me.almana.logisticsnetworks.logic.async.ThreadGuard;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DirectStorageHandlers {
    private DirectStorageHandlers() {
    }

    public static IItemHandler exportItems(StorageEndpoint endpoint) {
        return exportItems(endpoint, new DirectStorageReads(true));
    }

    public static IItemHandler exportItems(StorageEndpoint endpoint, DirectStorageReads reads) {
        ThreadGuard.requireServerThread();
        return new DirectItemHandler(endpoint, reads, true);
    }

    public static IItemHandler importItems(StorageEndpoint endpoint) {
        return importItems(endpoint, new DirectStorageReads(true));
    }

    public static IItemHandler importItems(StorageEndpoint endpoint, DirectStorageReads reads) {
        ThreadGuard.requireServerThread();
        return new DirectItemHandler(endpoint, reads, false);
    }

    public static IFluidHandler exportFluids(StorageEndpoint endpoint) {
        return new DirectFluidHandler(endpoint, true);
    }

    public static IFluidHandler importFluids(StorageEndpoint endpoint) {
        return new DirectFluidHandler(endpoint, false);
    }

    public static boolean isDirect(Object handler) {
        return handler instanceof DirectItemAccess || handler instanceof DirectFluidHandler;
    }

    public static int snapshotView(Object handler) {
        return handler instanceof DirectItemHandler direct ? (direct.exporting() ? 1 : 2) : 0;
    }

    public static ItemStack insertItem(IItemHandler handler, ItemStack stack, boolean simulate) {
        return handler instanceof DirectItemAccess direct ? direct.insert(stack, simulate) : stack;
    }

    public static List<StorageEndpoint.StoredItem> exports(IItemHandler handler) {
        return ((DirectItemHandler) handler).exports();
    }

    public static StorageEndpoint endpoint(IItemHandler handler) {
        return ((DirectItemHandler) handler).endpoint();
    }

    public static boolean shareNetwork(Object first, Object second) {
        Object identity = networkIdentity(first);
        return identity != null && identity == networkIdentity(second);
    }

    @Nullable
    public static Object networkIdentity(Object handler) {
        if (handler instanceof DirectItemHandler direct) return direct.endpoint().networkIdentity();
        if (handler instanceof DirectFluidHandler direct) return direct.endpoint().networkIdentity();
        return null;
    }
}
