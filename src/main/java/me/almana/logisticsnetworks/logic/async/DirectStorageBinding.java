package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.integration.storage.DirectStorageHandlers;
import me.almana.logisticsnetworks.integration.storage.StorageEndpoint;
import net.neoforged.neoforge.items.IItemHandler;

public record DirectStorageBinding(StorageEndpoint endpoint) {
    public boolean matches(IItemHandler handler) {
        ThreadGuard.requireServerThread();
        if (DirectStorageHandlers.snapshotView(handler) == 0 || !endpoint.isValid()) return false;
        StorageEndpoint current = DirectStorageHandlers.endpoint(handler);
        return current.isValid() && endpoint.networkIdentity() == current.networkIdentity()
                && endpoint.endpointIdentity() == current.endpointIdentity();
    }
}
