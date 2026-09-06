package me.almana.logisticsnetworks.integration.sophisticated;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

public final class SophisticatedCoreCompat {
    private static final boolean LOADED = ModList.get().isLoaded("sophisticatedcore");

    private SophisticatedCoreCompat() {
    }

    public static boolean isBulkHandler(ResourceHandler<ItemResource> handler) {
        return LOADED && SophisticatedCoreInsertion.isBulkHandler(handler);
    }
}
