package me.almana.logisticsnetworks.integration.refinedstorage;

import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.impl.node.iface.InterfaceNetworkNode;
import com.refinedmods.refinedstorage.api.network.node.NetworkNode;
import com.refinedmods.refinedstorage.common.controller.ControllerBlockEntity;
import com.refinedmods.refinedstorage.neoforge.api.RefinedStorageNeoForgeApi;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class RefinedStorageAdapter implements StorageAdapter {

    public static final RefinedStorageAdapter INSTANCE = new RefinedStorageAdapter();

    private RefinedStorageAdapter() {
    }

    @Override
    public StorageBackend backend() {
        return StorageBackend.REFINED_STORAGE;
    }

    @Override
    public boolean detects(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ControllerBlockEntity;
    }

    @Nullable
    @Override
    public StorageAccess resolve(ServerLevel level, StorageLink link) {
        return RefinedStorageAccess.resolve(level, link);
    }

    @Override
    public InterfaceStorageResolution resolveInterface(ServerLevel level, BlockPos pos,
            @Nullable Direction direction) {
        var capability = RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability();
        var provider = level.getCapability(capability, pos, null);
        if (provider == null) return InterfaceStorageResolution.unsupported();
        for (var container : provider.getContainers()) {
            NetworkNode node = container.getNode();
            if (!(node instanceof InterfaceNetworkNode interfaceNode)) continue;
            Network network = interfaceNode.getNetwork();
            if (!interfaceNode.isActive() || network == null) return InterfaceStorageResolution.unavailable();
            return InterfaceStorageResolution.available(
                    new RefinedStorageInterfaceEndpoint(interfaceNode, network));
        }
        return InterfaceStorageResolution.unsupported();
    }

    @Override
    public boolean isPattern(ItemStack stack, Level level) {
        return RefinedStoragePatternReader.isPattern(stack, level);
    }

    @Override
    public List<LinkedStorage.PatternEntry> readPatternInputs(ItemStack stack, Level level) {
        return RefinedStoragePatternReader.readInputs(stack, level);
    }

    @Override
    public List<LinkedStorage.PatternEntry> readPatternOutputs(ItemStack stack, Level level) {
        return RefinedStoragePatternReader.readOutputs(stack, level);
    }

    @Override
    public void registerWrenchLinkable() {
    }
}
