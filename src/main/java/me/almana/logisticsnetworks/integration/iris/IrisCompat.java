package me.almana.logisticsnetworks.integration.iris;

import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraftforge.fml.ModList;

public final class IrisCompat {
    private static final boolean LOADED = ModList.get().isLoaded("oculus");

    private IrisCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static boolean isRenderingShadowPass() {
        return LOADED && IrisAccess.isRenderingShadowPass();
    }

    public static boolean isShaderPackInUse() {
        return LOADED && IrisAccess.isShaderPackInUse();
    }

    private static final class IrisAccess {
        private static boolean isRenderingShadowPass() {
            return IrisApi.getInstance().isRenderingShadowPass();
        }

        private static boolean isShaderPackInUse() {
            return IrisApi.getInstance().isShaderPackInUse();
        }
    }
}
