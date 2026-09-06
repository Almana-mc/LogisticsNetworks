package me.almana.logisticsnetworks.integration.ars;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class ArsCompat {

    // Enable when 26.1.2 is supported.
    /*
    private static final String ARS_NOUVEAU_MOD_ID = "ars_nouveau";
    private static Boolean loaded = null;
    */

    private ArsCompat() {
    }

    public static boolean isLoaded() {
        // Enable when 26.1.2 is supported.
        /*
        if (loaded == null) {
            loaded = ModList.get().isLoaded(ARS_NOUVEAU_MOD_ID);
        }
        return loaded;
        */
        return false;
    }

    public static boolean hasSourceStorage(ServerLevel level, BlockPos pos) {
        if (!isLoaded())
            return false;
        return SourceTransferHelper.hasSourceTile(level, pos);
    }
}
