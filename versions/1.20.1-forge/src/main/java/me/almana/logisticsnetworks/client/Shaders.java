package me.almana.logisticsnetworks.client;

import net.minecraftforge.fml.ModList;

public final class Shaders {

    private static final boolean OCULUS_LOADED = ModList.get().isLoaded("oculus");

    private Shaders() {
    }

    public static boolean shadersActive() {
        return OCULUS_LOADED && IrisHook.shaderPackInUse();
    }
}
