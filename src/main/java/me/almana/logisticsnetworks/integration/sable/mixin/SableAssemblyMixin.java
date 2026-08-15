package me.almana.logisticsnetworks.integration.sable.mixin;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.api.SubLevelAssemblyHelper")
public abstract class SableAssemblyMixin {

    @Inject(method = "moveOtherStuff", at = @At("HEAD"))
    private static void logisticsnetworks$moveNodes(ServerLevel level, @Coerce Object transform,
            Iterable<BlockPos> blocks, @Coerce Object bounds, CallbackInfo callback) {
        Set<BlockPos> movedBlocks = new HashSet<>();
        blocks.forEach(movedBlocks::add);
        if (movedBlocks.isEmpty()) return;

        AABB searchBounds = bounds(movedBlocks).inflate(1.0);
        for (LogisticsNodeEntity node : level.getEntitiesOfClass(LogisticsNodeEntity.class, searchBounds)) {
            if (!movedBlocks.contains(node.getAttachedPos())) continue;
            moveNode(node, transform);
        }
    }

    private static AABB bounds(Set<BlockPos> blocks) {
        BlockPos first = blocks.iterator().next();
        int minX = first.getX();
        int minY = first.getY();
        int minZ = first.getZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;

        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    private static void moveNode(LogisticsNodeEntity node, Object transform) {
        try {
            Method applyPosition = transform.getClass().getMethod("apply", Vec3.class);
            Method applyBlock = transform.getClass().getMethod("apply", BlockPos.class);
            Vec3 position = (Vec3) applyPosition.invoke(transform, node.position());
            BlockPos attachedPos = (BlockPos) applyBlock.invoke(transform, node.getAttachedPos());
            node.moveAttachment(attachedPos, position);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Unsupported Sable assembly transform", exception);
        }
    }
}
