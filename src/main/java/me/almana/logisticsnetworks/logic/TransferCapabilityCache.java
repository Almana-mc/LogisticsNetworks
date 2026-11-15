package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.IoSide;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.integration.create.CreateCompat;
import me.almana.logisticsnetworks.integration.mekanism.DirectChemicalHandler;
import me.almana.logisticsnetworks.integration.storage.DirectStorageHandlers;
import me.almana.logisticsnetworks.integration.storage.InterfaceStorageResolution;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import me.almana.logisticsnetworks.integration.storage.StorageEndpoint;
import me.almana.logisticsnetworks.integration.sophisticated.SophisticatedCoreCompat;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

// One per network pass only
public final class TransferCapabilityCache {
    private final TransferEngine.CapCache handlers;
    private final Map<Object, IItemHandler[]> views = new IdentityHashMap<>();
    private final FilterItemData.ReadCache filters = FilterItemData.createReadCache();

    TransferCapabilityCache(TransferEngine.CapCache handlers) {
        this.handlers = handlers;
    }

    public static TransferCapabilityCache create() {
        return new TransferEngine.CapCache().interfaces;
    }

    public FilterItemData.ReadCache filterCache() {
        return filters;
    }

    static InterfaceStorageResolution resolveInterface(LogisticsNodeEntity node, @Nullable Direction direction) {
        if (!NodeUpgradeData.hasNetworkUpgrade(node)) return InterfaceStorageResolution.unsupported();
        return LinkedStorage.resolveInterface((ServerLevel) node.level(), node.getAttachedPos(), direction);
    }

    @Nullable
    public IItemHandler findItemExportHandler(LogisticsNodeEntity node, IoSide side, boolean direct) {
        return findItemHandler(node, side, direct, true);
    }

    @Nullable
    public IItemHandler findItemImportHandler(LogisticsNodeEntity node, IoSide side, boolean direct) {
        return findItemHandler(node, side, direct, false);
    }

    @Nullable
    public IItemHandler findBulkItemHandler(LogisticsNodeEntity node, IItemHandler sidedHandler) {
        if (DirectStorageHandlers.isDirect(sidedHandler)) return sidedHandler;
        if (node.isMountedOnCreate()) return null;
        if (!SophisticatedCoreCompat.isSidedWrapper(sidedHandler)) return null;
        IItemHandler unsidedHandler = unsidedItemHandler((ServerLevel) node.level(), node.getAttachedPos());
        return SophisticatedCoreCompat.isBulkHandler(unsidedHandler) ? unsidedHandler : null;
    }

    @Nullable
    private static IItemHandler unsidedItemHandler(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity == null ? null
                : blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
    }

    @Nullable
    IFluidHandler findFluidExportHandler(LogisticsNodeEntity node, IoSide side) {
        return findFluidHandler(node, side, true);
    }

    @Nullable
    IFluidHandler findFluidImportHandler(LogisticsNodeEntity node, IoSide side) {
        return findFluidHandler(node, side, false);
    }

    @Nullable
    @SuppressWarnings("rawtypes")
    IChemicalHandler findChemicalHandler(ServerLevel level, BlockPos pos, IoSide side) {
        return handlers.findChemicalHandler(level, pos, side);
    }

    @Nullable
    @SuppressWarnings("rawtypes")
    public IChemicalHandler findChemicalHandler(LogisticsNodeEntity node, IoSide side, boolean exporting) {
        ServerLevel level = (ServerLevel) node.level();
        if (side == IoSide.NONE || node.isMountedOnCreate()) return findChemicalHandler(level, node.getAttachedPos(), side);
        InterfaceStorageResolution resolution = resolveInterface(node, side.direction());
        if (resolution.status() == InterfaceStorageResolution.Status.UNAVAILABLE) return null;
        if (resolution.status() == InterfaceStorageResolution.Status.AVAILABLE && resolution.endpoint().storesChemicals())
            return DirectChemicalHandler.of(resolution.endpoint(), exporting);
        return findChemicalHandler(level, node.getAttachedPos(), side);
    }

    @Nullable
    private IItemHandler findItemHandler(LogisticsNodeEntity node, IoSide side,
            boolean direct, boolean exporting) {
        if (node.isMountedOnCreate()) {
            return CreateCompat.findMountedItemHandler(node);
        }
        ServerLevel level = (ServerLevel) node.level();
        BlockPos pos = node.getAttachedPos();
        if (side == IoSide.NONE) return handlers.findItemHandler(level, pos, side);
        Direction direction = side.direction();
        InterfaceStorageResolution resolution = resolveInterface(node, direction);
        if (resolution.status() == InterfaceStorageResolution.Status.AVAILABLE && direct) {
            IItemHandler buffer = exporting ? interfaceBuffer(level, pos, direction, resolution.endpoint()) : null;
            return itemView(resolution.endpoint(), buffer, exporting);
        }
        if (resolution.status() == InterfaceStorageResolution.Status.UNAVAILABLE) return null;
        return handlers.findItemHandler(level, pos, side);
    }

    @Nullable
    private IFluidHandler findFluidHandler(LogisticsNodeEntity node, IoSide side,
            boolean exporting) {
        if (node.isMountedOnCreate()) {
            return CreateCompat.findMountedFluidHandler(node);
        }
        if (side == IoSide.NONE) return handlers.findFluidHandler((ServerLevel) node.level(), node.getAttachedPos(), side);
        InterfaceStorageResolution resolution = resolveInterface(node, side.direction());
        if (resolution.status() == InterfaceStorageResolution.Status.AVAILABLE) {
            return exporting ? DirectStorageHandlers.exportFluids(resolution.endpoint())
                    : DirectStorageHandlers.importFluids(resolution.endpoint());
        }
        if (resolution.status() == InterfaceStorageResolution.Status.UNAVAILABLE) return null;
        return handlers.findFluidHandler((ServerLevel) node.level(), node.getAttachedPos(), side);
    }

    private IItemHandler itemView(StorageEndpoint endpoint, @Nullable IItemHandler buffer, boolean exporting) {
        IItemHandler[] pair = views.computeIfAbsent(endpoint.endpointIdentity(), ignored -> new IItemHandler[2]);
        int index = exporting ? 0 : 1;
        IItemHandler existing = pair[index];
        if (existing == null || DirectStorageHandlers.networkIdentity(existing) != endpoint.networkIdentity()) {
            pair[index] = exporting ? DirectStorageHandlers.exportItems(endpoint, buffer)
                    : DirectStorageHandlers.importItems(endpoint);
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
