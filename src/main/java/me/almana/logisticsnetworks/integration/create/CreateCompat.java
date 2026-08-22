package me.almana.logisticsnetworks.integration.create;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import net.neoforged.fml.ModList;

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
}
