package me.almana.logisticsnetworks.client.lnet;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

final class LnetPaths {
    private LnetPaths() {
    }

    static Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }
}
