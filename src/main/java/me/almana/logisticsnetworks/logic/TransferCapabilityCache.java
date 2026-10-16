package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.storage.DirectStorageHandlers;
import me.almana.logisticsnetworks.integration.storage.DirectStorageReads;
import me.almana.logisticsnetworks.integration.storage.InterfaceStorageResolution;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import me.almana.logisticsnetworks.integration.storage.StorageEndpoint;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

// One per network pass only
public final class TransferCapabilityCache {
    private final TransferEngine.CapCache handlers;
    private final DirectStorageReads reads = new DirectStorageReads(true);
    private final Map<Object, IItemHandler[]> views = new IdentityHashMap<>();

    TransferCapabilityCache(TransferEngine.CapCache handlers) {
        this.handlers = handlers;
    }

    public static TransferCapabilityCache create() {
        return new TransferEngine.CapCache().interfaces;
    }

    static InterfaceStorageResolution resolveInterface(LogisticsNodeEntity node, @Nullable Direction direction) {
        if (!NodeUpgradeData.hasNetworkUpgrade(node)) return InterfaceStorageResolution.unsupported();
        return LinkedStorage.resolveInterface((ServerLevel) node.level(), node.getAttachedPos(), direction);
    }

    @Nullable
    public IItemHandler findItemExportHandler(LogisticsNodeEntity node, @Nullable Direction direction, boolean direct) {
        return findItemHandler(node, direction, direct, true);
    }

    @Nullable
    public IItemHandler findItemImportHandler(LogisticsNodeEntity node, @Nullable Direction direction, boolean direct) {
        return findItemHandler(node, direction, direct, false);
    }

    @Nullable
    public IItemHandler findBulkItemHandler(LogisticsNodeEntity node, IItemHandler sidedHandler) {
        if (DirectStorageHandlers.isDirect(sidedHandler)) return sidedHandler;
        return null;
    }

    @Nullable
    IFluidHandler findFluidExportHandler(LogisticsNodeEntity node, @Nullable Direction direction) {
        return findFluidHandler(node, direction, true);
    }

    @Nullable
    IFluidHandler findFluidImportHandler(LogisticsNodeEntity node, @Nullable Direction direction) {
        return findFluidHandler(node, direction, false);
    }

    @Nullable
    private IItemHandler findItemHandler(LogisticsNodeEntity node, @Nullable Direction direction,
            boolean direct, boolean exporting) {
        ServerLevel level = (ServerLevel) node.level();
        BlockPos pos = node.getAttachedPos();
        InterfaceStorageResolution resolution = resolveInterface(node, direction);
        if (resolution.status() == InterfaceStorageResolution.Status.AVAILABLE && direct) {
            IItemHandler buffer = exporting ? interfaceBuffer(level, pos, direction, resolution.endpoint()) : null;
            return itemView(resolution.endpoint(), buffer, exporting);
        }
        if (resolution.status() == InterfaceStorageResolution.Status.UNAVAILABLE) return null;
        return handlers.findItemHandler(level, pos, direction);
    }

    @Nullable
    private IFluidHandler findFluidHandler(LogisticsNodeEntity node, @Nullable Direction direction,
            boolean exporting) {
        InterfaceStorageResolution resolution = resolveInterface(node, direction);
        if (resolution.status() == InterfaceStorageResolution.Status.AVAILABLE) {
            return exporting ? DirectStorageHandlers.exportFluids(resolution.endpoint())
                    : DirectStorageHandlers.importFluids(resolution.endpoint());
        }
        if (resolution.status() == InterfaceStorageResolution.Status.UNAVAILABLE) return null;
        return handlers.findFluidHandler((ServerLevel) node.level(), node.getAttachedPos(), direction);
    }

    private IItemHandler itemView(StorageEndpoint endpoint, @Nullable IItemHandler buffer, boolean exporting) {
        IItemHandler[] pair = views.computeIfAbsent(endpoint.endpointIdentity(), ignored -> new IItemHandler[2]);
        int index = exporting ? 0 : 1;
        IItemHandler existing = pair[index];
        if (existing == null || DirectStorageHandlers.networkIdentity(existing) != endpoint.networkIdentity()) {
            pair[index] = exporting ? DirectStorageHandlers.exportItems(endpoint, buffer, reads)
                    : DirectStorageHandlers.importItems(endpoint, reads);
        }
        return pair[index];
    }

    // The interface's own slots
    @Nullable
    private IItemHandler interfaceBuffer(ServerLevel level, BlockPos pos, @Nullable Direction direction,
            StorageEndpoint endpoint) {
        if (direction != null) return handlers.getItemHandler(level, pos, direction);
        for (Direction side : Direction.values()) {
            InterfaceStorageResolution resolution = LinkedStorage.resolveInterface(level, pos, side);
            if (resolution.status() == InterfaceStorageResolution.Status.AVAILABLE
                    && resolution.endpoint().endpointIdentity() == endpoint.endpointIdentity()) {
                return handlers.getItemHandler(level, pos, side);
            }
        }
        return null;
    }
}
