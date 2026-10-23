package me.almana.logisticsnetworks.integration.ae2;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.parts.IPartHost;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.helpers.InterfaceLogicHost;
import me.almana.logisticsnetworks.integration.storage.InterfaceStorageResolution;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import me.almana.logisticsnetworks.integration.storage.StorageAccess;
import me.almana.logisticsnetworks.integration.storage.StorageAdapter;
import me.almana.logisticsnetworks.integration.storage.StorageBackend;
import me.almana.logisticsnetworks.integration.storage.StorageLink;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class AE2StorageAdapter implements StorageAdapter {

    public static final AE2StorageAdapter INSTANCE = new AE2StorageAdapter();

    private AE2StorageAdapter() {
    }

    @Override
    public StorageBackend backend() {
        return StorageBackend.AE2;
    }

    @Override
    public boolean detects(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ControllerBlockEntity;
    }

    @Nullable
    @Override
    public StorageAccess resolve(ServerLevel level, StorageLink link) {
        return AE2StorageAccess.resolve(level, link);
    }

    @Override
    public InterfaceStorageResolution resolveInterface(ServerLevel level, BlockPos pos,
            @Nullable Direction direction) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        InterfaceLogicHost host = interfaceHost(blockEntity, direction);
        if (!(host instanceof IActionHost actionHost)) return InterfaceStorageResolution.unsupported();

        IGridNode node = actionHost.getActionableNode();
        if (node == null || !node.isActive() || node.getGrid() == null) {
            return InterfaceStorageResolution.unavailable();
        }
        return InterfaceStorageResolution.available(new AE2InterfaceEndpoint(host, actionHost, node));
    }

    @Nullable
    @Override
    public Object networkIdentity(ServerLevel level, BlockPos pos) {
        IGridNode node = AE2StorageAccess.findNode(level.getBlockEntity(pos));
        return node != null && node.isActive() ? node.getGrid() : null;
    }

    @Override
    public boolean isPattern(ItemStack stack, Level level) {
        return AE2PatternReader.isPattern(stack);
    }

    @Override
    public List<LinkedStorage.PatternEntry> readPatternInputs(ItemStack stack, Level level) {
        return AE2PatternReader.readInputs(stack);
    }

    @Override
    public List<LinkedStorage.PatternEntry> readPatternOutputs(ItemStack stack, Level level) {
        return AE2PatternReader.readOutputs(stack);
    }

    @Override
    public void registerWrenchLinkable() {
        AE2StorageAccess.registerWrenchLinkable();
    }

    @Nullable
    private static InterfaceLogicHost interfaceHost(@Nullable BlockEntity blockEntity,
            @Nullable Direction direction) {
        if (blockEntity instanceof InterfaceLogicHost host) return host;
        if (!(blockEntity instanceof IPartHost partHost)) return null;
        if (direction != null) {
            return partHost.getPart(direction) instanceof InterfaceLogicHost host ? host : null;
        }
        for (Direction side : Direction.values()) {
            if (partHost.getPart(side) instanceof InterfaceLogicHost host) return host;
        }
        return null;
    }
}
