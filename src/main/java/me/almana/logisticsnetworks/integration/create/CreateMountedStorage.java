package me.almana.logisticsnetworks.integration.create;

import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorage;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

final class CreateMountedStorage {
    private CreateMountedStorage() {
    }

    @Nullable
    static IItemHandler findItemHandler(LogisticsNodeEntity node) {
        AbstractContraptionEntity entity = CreateNodeAttachment.findContraption(node);
        if (entity == null) {
            return null;
        }
        MountedItemStorage storage = entity.getContraption().getStorage().getAllItemStorages()
                .get(node.getCreateLocalPos());
        return storage;
    }

    @Nullable
    static IFluidHandler findFluidHandler(LogisticsNodeEntity node) {
        AbstractContraptionEntity entity = CreateNodeAttachment.findContraption(node);
        if (entity == null) {
            return null;
        }
        MountedFluidStorage storage = entity.getContraption().getStorage().getFluids().storages
                .get(node.getCreateLocalPos());
        return storage;
    }
}
