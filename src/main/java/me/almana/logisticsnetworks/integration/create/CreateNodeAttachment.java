package me.almana.logisticsnetworks.integration.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ContraptionHandler;
import com.simibubi.create.content.contraptions.StructureTransform;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.UUID;

public final class CreateNodeAttachment {
    private CreateNodeAttachment() {
    }

    static BlockPos localPosition(BlockPos attachedPos, BlockPos anchor) {
        return attachedPos.subtract(anchor);
    }

    static Direction transformDirection(Direction direction, StructureTransform transform) {
        return transform.rotateFacing(transform.mirrorFacing(direction));
    }

    public static void bindNodes(Contraption contraption, AbstractContraptionEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        AABB search = contraption.bounds.move(Vec3.atLowerCornerOf(contraption.anchor)).inflate(1.0);
        for (LogisticsNodeEntity node : level.getEntitiesOfClass(LogisticsNodeEntity.class, search)) {
            BlockPos localPos = localPosition(node.getAttachedPos(), contraption.anchor);
            if (!node.isMountedOnCreate() && contraption.getBlocks().containsKey(localPos)) {
                node.mountOnCreate(entity.getUUID(), localPos);
                tick(node);
            }
        }
    }

    public static void tick(LogisticsNodeEntity node) {
        AbstractContraptionEntity entity = findContraption(node);
        if (entity == null || !entity.getContraption().getBlocks().containsKey(node.getCreateLocalPos())) {
            return;
        }
        Vec3 position = entity.toGlobalVector(Vec3.atBottomCenterOf(node.getCreateLocalPos()), 0.0F);
        node.updateMountedPosition(position);
    }

    public static void dismountNodes(Contraption contraption, Level level, StructureTransform transform) {
        AbstractContraptionEntity entity = contraption.entity;
        if (entity == null) {
            return;
        }
        for (LogisticsNodeEntity node : level.getEntitiesOfClass(LogisticsNodeEntity.class,
                entity.getBoundingBox().inflate(2.0))) {
            if (!entity.getUUID().equals(node.getCreateContraptionId())) {
                continue;
            }
            BlockPos target = transform.apply(node.getCreateLocalPos());
            node.dismountFromCreate(target, Vec3.atBottomCenterOf(target),
                    direction -> transformDirection(direction, transform));
        }
    }

    @Nullable
    static AbstractContraptionEntity findContraption(LogisticsNodeEntity node) {
        UUID contraptionId = node.getCreateContraptionId();
        if (contraptionId == null) {
            return null;
        }
        if (node.level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(contraptionId);
            return entity instanceof AbstractContraptionEntity contraption && contraption.isAlive()
                    ? contraption
                    : null;
        }
        for (WeakReference<AbstractContraptionEntity> reference
                : ContraptionHandler.loadedContraptions.get(node.level()).values()) {
            AbstractContraptionEntity contraption = reference.get();
            if (contraption != null && contraption.isAlive() && contraptionId.equals(contraption.getUUID())) {
                return contraption;
            }
        }
        return null;
    }

    public static boolean isResolved(LogisticsNodeEntity node) {
        AbstractContraptionEntity entity = findContraption(node);
        return entity != null && entity.getContraption().getBlocks().containsKey(node.getCreateLocalPos());
    }
}
