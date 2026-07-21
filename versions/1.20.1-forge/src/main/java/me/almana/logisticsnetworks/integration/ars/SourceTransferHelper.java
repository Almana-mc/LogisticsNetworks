package me.almana.logisticsnetworks.integration.ars;

import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class SourceTransferHelper {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SourceTransferHelper() {
    }

    @Nullable
    public static ISourceTile getSourceTile(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ISourceTile sourceTile) {
            return sourceTile;
        }
        return null;
    }

    public static boolean hasSourceTile(ServerLevel level, BlockPos pos) {
        return getSourceTile(level, pos) != null;
    }

    public static int transferBetween(ServerLevel sourceLevel, BlockPos sourcePos,
            ServerLevel targetLevel, BlockPos targetPos, int limit) {
        ISourceTile source = getSourceTile(sourceLevel, sourcePos);
        if (source == null) {
            if (Config.debugMode)
                LOGGER.debug("[Source] No source tile at {}", sourcePos);
            return 0;
        }
        ISourceTile target = getSourceTile(targetLevel, targetPos);
        if (target == null) {
            if (Config.debugMode)
                LOGGER.debug("[Source] No target tile at {}", targetPos);
            return 0;
        }

        if (!target.canAcceptSource()) {
            if (Config.debugMode)
                LOGGER.debug("[Source] Target at {} cannot accept source", targetPos);
            return 0;
        }

        int available = source.getSource();
        if (available <= 0)
            return 0;

        int targetSpace = target.getMaxSource() - target.getSource();
        if (targetSpace <= 0)
            return 0;

        int toMove = Math.min(limit, Math.min(available, targetSpace));
        toMove = Math.min(toMove, source.getTransferRate());
        toMove = Math.min(toMove, target.getTransferRate());

        if (toMove <= 0)
            return 0;

        source.removeSource(toMove);
        target.addSource(toMove);

        if (Config.debugMode)
            LOGGER.debug("[Source] Transferred {} source from {} to {}", toMove, sourcePos, targetPos);

        return toMove;
    }
}
