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

    private record TileEnd(ISourceTile tile, Object identity) implements SourceEnd {
        @Override
        public int extract(int amount, boolean simulate) {
            int moved = Math.max(0, Math.min(amount, Math.min(tile.getSource(), tile.getTransferRate())));
            if (!simulate && moved > 0) tile.removeSource(moved);
            return moved;
        }

        @Override
        public int receive(int amount, boolean simulate) {
            if (!tile.canAcceptSource()) return 0;
            int space = tile.getMaxSource() - tile.getSource();
            int moved = Math.max(0, Math.min(amount, Math.min(space, tile.getTransferRate())));
            if (!simulate && moved > 0) tile.addSource(moved);
            return moved;
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
        ISourceTile tile = sourceTile(level.getBlockEntity(pos), side);
        return tile == null ? null : new TileEnd(tile, identity(level, pos, tile));
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

    private static Object identity(ServerLevel level, BlockPos pos, Object fallback) {
        Object network = LinkedStorage.networkIdentity(level, pos);
        return network != null ? network : fallback;
    }
}
