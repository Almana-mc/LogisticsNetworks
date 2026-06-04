package me.almana.logisticsnetworks.datagen;

import net.neoforged.neoforge.data.event.GatherDataEvent;

public final class ModDataGenerators {
    private ModDataGenerators() {}

    public static void gather(GatherDataEvent.Client event) {
        event.createProvider(ModModelProvider::new);
    }

    public static void gatherServer(GatherDataEvent.Server event) {
        event.createProvider(ModRecipeProvider.Runner::new);
        event.createProvider(ModBlockLootProvider::create);
        event.createProvider(ModBlockTagProvider::new);
        event.createProvider(ModItemTagProvider::new);
        event.createProvider(ModFluidTagProvider::new);
    }
}
