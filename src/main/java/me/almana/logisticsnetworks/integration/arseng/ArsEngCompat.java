package me.almana.logisticsnetworks.integration.arseng;

import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;

public final class ArsEngCompat {

    private ArsEngCompat() {
    }

    public static boolean isLoaded() {
        return false;
    }

    @Nullable
    public static AEKey sourceKey() {
        return null;
    }

    // Enable when 26.1.2 is supported.
    /*
    private static Boolean loaded = null;

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
    */
}
