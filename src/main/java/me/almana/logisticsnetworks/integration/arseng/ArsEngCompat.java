package me.almana.logisticsnetworks.integration.arseng;

import appeng.api.stacks.AEKey;
import gripe._90.arseng.me.key.SourceKey;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

public final class ArsEngCompat {

    private static Boolean loaded = null;

    private ArsEngCompat() {
    }

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("arseng");
        }
        return loaded;
    }

    @Nullable
    public static AEKey sourceKey() {
        return isLoaded() ? Key.SOURCE : null;
    }

    // defers loading arseng classes
    private static final class Key {
        static final AEKey SOURCE = SourceKey.KEY;
    }
}
