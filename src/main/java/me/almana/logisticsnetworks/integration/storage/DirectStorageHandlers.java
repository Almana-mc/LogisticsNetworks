package me.almana.logisticsnetworks.integration.storage;

import me.almana.logisticsnetworks.logic.async.ThreadGuard;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DirectStorageHandlers {
    private DirectStorageHandlers() {
    }

    public static ResourceHandler<ItemResource> exportItems(StorageEndpoint endpoint) {
        return exportItems(endpoint, new DirectStorageReads(true));
    }

    public static ResourceHandler<ItemResource> exportItems(StorageEndpoint endpoint, DirectStorageReads reads) {
        ThreadGuard.requireServerThread();
        return new DirectItemHandler(endpoint, reads, true);
    }

    public static ResourceHandler<ItemResource> importItems(StorageEndpoint endpoint) {
        return importItems(endpoint, new DirectStorageReads(true));
    }

    public static ResourceHandler<ItemResource> importItems(StorageEndpoint endpoint, DirectStorageReads reads) {
        ThreadGuard.requireServerThread();
        return new DirectItemHandler(endpoint, reads, false);
    }

    public static ResourceHandler<FluidResource> exportFluids(StorageEndpoint endpoint) {
        return new DirectFluidHandler(endpoint, true);
    }

    public static ResourceHandler<FluidResource> importFluids(StorageEndpoint endpoint) {
        return new DirectFluidHandler(endpoint, false);
    }

    public static boolean isDirect(Object handler) {
        return handler instanceof DirectItemAccess || handler instanceof DirectFluidHandler;
    }

    public static int snapshotView(Object handler) {
        return handler instanceof DirectItemHandler direct ? direct.exporting() ? 1 : 2 : 0;
    }

    public static List<StorageEndpoint.StoredItem> exports(ResourceHandler<ItemResource> handler) {
        return ((DirectItemHandler) handler).exports();
    }

    public static StorageEndpoint endpoint(ResourceHandler<ItemResource> handler) {
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
