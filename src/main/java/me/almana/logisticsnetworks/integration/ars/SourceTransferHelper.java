package me.almana.logisticsnetworks.integration.ars;

import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import me.almana.logisticsnetworks.integration.storage.StorageEndpoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public final class SourceTransferHelper {

    public interface SourceEnd {
        int extract(int amount, boolean simulate);

        int receive(int amount, boolean simulate);

        Object identity();
    }

    private record CapEnd(ISourceCap cap, Object identity) implements SourceEnd {
        @Override
        public int extract(int amount, boolean simulate) {
            return cap.extractSource(Math.min(amount, cap.getMaxExtract()), simulate);
        }

        @Override
        public int receive(int amount, boolean simulate) {
            return cap.receiveSource(Math.min(amount, cap.getMaxReceive()), simulate);
        }
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
        ISourceCap cap = level.getCapability(CapabilityRegistry.SOURCE_CAPABILITY, pos, side);
        if (cap != null) return new CapEnd(cap, identity(level, pos, cap));
        if (level.getBlockEntity(pos) instanceof ISourceTile tile) return new TileEnd(tile, identity(level, pos, tile));
        return null;
    }

    public static boolean hasSourceTile(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ISourceTile) return true;
        if (level.getCapability(CapabilityRegistry.SOURCE_CAPABILITY, pos, null) != null) return true;
        for (Direction side : Direction.values()) {
            if (level.getCapability(CapabilityRegistry.SOURCE_CAPABILITY, pos, side) != null) return true;
        }
        return false;
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
