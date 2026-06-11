package me.almana.logisticsnetworks.client;

import net.neoforged.fml.ModList;

public final class Shaders {

    private static final boolean IRIS_LOADED = ModList.get().isLoaded("iris");

    private Shaders() {
    }

    public static boolean shadersActive() {
        return IRIS_LOADED && IrisHook.shaderPackInUse();
    }
}
