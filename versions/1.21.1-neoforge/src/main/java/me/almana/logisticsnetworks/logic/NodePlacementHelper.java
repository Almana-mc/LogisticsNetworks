package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.ars.ArsCompat;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.List;
import java.util.UUID;

public final class NodePlacementHelper {

    public enum ValidationResult {
        OK,
        AIR,
        BLACKLISTED,
        NO_STORAGE_CAPABILITY,
        NODE_ALREADY_EXISTS
    }

    private NodePlacementHelper() {
    }

    public static ValidationResult validatePlacement(Level level, BlockPos pos) {
        return validatePlacement(level, pos, false);
    }

    public static ValidationResult validatePlacement(Level level, BlockPos pos, boolean creative) {
        if (level.isEmptyBlock(pos)) {
            return ValidationResult.AIR;
        }

        BlockState state = level.getBlockState(pos);
        if (state.is(ModTags.NODE_BLACKLIST_BLOCKS) || state.is(ModTags.NODE_COMPATIBILITY_BLACKLIST_BLOCKS)) {
            return ValidationResult.BLACKLISTED;
        }

        if (hasNodeAttached(level, pos)) {
            return ValidationResult.NODE_ALREADY_EXISTS;
        }

        if (!creative && !hasAnyStorageCapability(level, pos)) {
            return ValidationResult.NO_STORAGE_CAPABILITY;
        }

        return ValidationResult.OK;
    }

    public static boolean hasAnyStorageCapability(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        Direction[] directions = { null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
                Direction.WEST, Direction.EAST };

        for (Direction direction : directions) {
            if (serverLevel.getCapability(Capabilities.ItemHandler.BLOCK, pos, direction) != null) {
                return true;
            }
            if (serverLevel.getCapability(Capabilities.FluidHandler.BLOCK, pos, direction) != null) {
                return true;
            }
            if (serverLevel.getCapability(Capabilities.EnergyStorage.BLOCK, pos, direction) != null) {
                return true;
            }
        }

        if (MekanismCompat.hasChemicalStorage(serverLevel, pos)) {
            return true;
        }

        return ArsCompat.hasSourceStorage(serverLevel, pos);
    }

    public static boolean hasNodeAttached(Level level, BlockPos pos) {
        List<LogisticsNodeEntity> existingNodes = level.getEntitiesOfClass(LogisticsNodeEntity.class,
                new AABB(pos).inflate(0.1));
        for (LogisticsNodeEntity node : existingNodes) {
            if (node.getAttachedPos().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    public static LogisticsNodeEntity placeNode(Level level, BlockPos pos) {
        return placeNode(level, pos, null);
    }

    public static LogisticsNodeEntity placeNode(Level level, BlockPos pos, UUID ownerUuid) {
        LogisticsNodeEntity node = Registration.LOGISTICS_NODE.get().create(level);
        if (node == null) {
            return null;
        }
        node.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        node.setAttachedPos(pos);
        node.setValid(true);
        node.setOwnerUUID(ownerUuid);

        if (!level.addFreshEntity(node)) {
            return null;
        }

        return node;
    }
}
