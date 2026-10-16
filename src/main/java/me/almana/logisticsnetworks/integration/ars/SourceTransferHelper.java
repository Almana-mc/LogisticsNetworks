package me.almana.logisticsnetworks.integration.ars;

import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import me.almana.logisticsnetworks.integration.arseng.ArsEngCompat;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import me.almana.logisticsnetworks.integration.storage.StorageEndpoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class SourceTransferHelper {

    public interface SourceEnd {
        int extract(int amount, boolean simulate);

        int receive(int amount, boolean simulate);

        Object identity();
    }

    private record TileEnd(ISourceTile tile, Object identity, boolean capability) implements SourceEnd {
        @Override
        public int extract(int amount, boolean simulate) {
            int before = tile.getSource();
            int moved = Math.max(0, Math.min(amount, Math.min(before, tile.getTransferRate())));
            if (simulate || moved <= 0) return moved;
            tile.removeSource(moved);
            // capability sinks may ignore removal
            return capability ? Math.max(0, before - tile.getSource()) : moved;
        }

        @Override
        public int receive(int amount, boolean simulate) {
            if (!tile.canAcceptSource()) return 0;
            int before = tile.getSource();
            int space = tile.getMaxSource() - before;
            int moved = Math.max(0, Math.min(amount, Math.min(space, tile.getTransferRate())));
            if (simulate || moved <= 0) return moved;
            tile.addSource(moved);
            // clamping tiles store less
            return capability ? moved : Math.max(0, Math.min(moved, tile.getSource() - before));
        }
    }

    private record GridEnd(StorageEndpoint endpoint) implements SourceEnd {
        @Override
        public int extract(int amount, boolean simulate) {
            return (int) endpoint.extractSource(amount, simulate);
        }

        @Override
        public int receive(int amount, boolean simulate) {
            return (int) endpoint.insertSource(amount, simulate);
        }

        @Override
        public Object identity() {
            return endpoint.networkIdentity();
        }
    }

    private SourceTransferHelper() {
    }

    public static SourceEnd grid(StorageEndpoint endpoint) {
        return new GridEnd(endpoint);
    }

    @Nullable
    public static SourceEnd resolve(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        SourceEnd jar = ArsEngCompat.jarEnd(blockEntity);
        if (jar != null) return jar;
        ISourceTile tile = sourceTile(blockEntity, side);
        if (tile == null) return null;
        if (blockEntity instanceof ISourceTile) return new TileEnd(tile, identity(level, pos, tile), false);
        if (ArsEngCompat.isTunnel(tile)) return null;
        SourceEnd storage = ArsEngCompat.storageEnd(tile, level, pos);
        return storage != null ? storage : new TileEnd(tile, identity(level, pos, tile), true);
    }

    public static boolean hasSourceTile(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (sourceTile(blockEntity, null) != null) return true;
        for (Direction side : Direction.values()) {
            if (sourceTile(blockEntity, side) != null) return true;
        }
        return false;
    }

    // Ars 4 lacks source capability
    @Nullable
    private static ISourceTile sourceTile(@Nullable BlockEntity blockEntity, @Nullable Direction side) {
        if (blockEntity == null) return null;
        ISourceTile capability = ArsEngCompat.sourceTile(blockEntity, side);
        if (capability != null) return capability;
        return blockEntity instanceof ISourceTile tile ? tile : null;
    }

    public static int transfer(SourceEnd from, SourceEnd to, int limit) {
        if (from.identity() == to.identity()) return 0;
        int offered = from.extract(limit, true);
        if (offered <= 0) return 0;
        int accepted = to.receive(offered, true);
        if (accepted <= 0) return 0;
        int moved = from.extract(accepted, false);
        int rejected = moved - to.receive(moved, false);
        // refund what target refused
        if (rejected > 0) from.receive(rejected, false);
        return moved - rejected;
    }

    public static Object identity(ServerLevel level, BlockPos pos, Object fallback) {
        Object network = LinkedStorage.networkIdentity(level, pos);
        return network != null ? network : fallback;
    }
}
