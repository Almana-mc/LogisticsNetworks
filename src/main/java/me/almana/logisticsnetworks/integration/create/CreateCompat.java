// Enable when 26.1.2 is supported.
/*
package me.almana.logisticsnetworks.integration.create;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.joml.Quaternionf;
import org.jetbrains.annotations.Nullable;

public final class CreateCompat {
    private static final boolean LOADED = ModList.get().isLoaded("create");

    private CreateCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static void tickMountedNode(LogisticsNodeEntity node) {
        if (LOADED) {
            CreateNodeAttachment.tick(node);
        }
    }

    @Nullable
    public static IItemHandler findMountedItemHandler(LogisticsNodeEntity node) {
        return LOADED && node.isMountedOnCreate() ? CreateMountedStorage.findItemHandler(node) : null;
    }

    @Nullable
    public static IFluidHandler findMountedFluidHandler(LogisticsNodeEntity node) {
        return LOADED && node.isMountedOnCreate() ? CreateMountedStorage.findFluidHandler(node) : null;
    }

    public static boolean isResolved(LogisticsNodeEntity node) {
        return !node.isMountedOnCreate() || LOADED && CreateNodeAttachment.isResolved(node);
    }

    @Nullable
    public static NodeRenderContext getRenderContext(LogisticsNodeEntity node, float partialTick) {
        if (node.isMountedOnCreate()) {
            return LOADED ? CreateNodeAttachment.getRenderContext(node, partialTick) : null;
        }
        return new NodeRenderContext(node.getPosition(partialTick), new Quaternionf(), node.level(),
                node.getAttachedPos(), NodeAttachmentKey.world(node.getAttachedPos()));
    }

    public static BlockState getAttachedBlockState(LogisticsNodeEntity node) {
        if (!node.isMountedOnCreate()) {
            return node.level().getBlockState(node.getAttachedPos());
        }
        if (!LOADED) {
            return Blocks.AIR.defaultBlockState();
        }
        return CreateNodeAttachment.getAttachedBlockState(node);
    }
}
*/
