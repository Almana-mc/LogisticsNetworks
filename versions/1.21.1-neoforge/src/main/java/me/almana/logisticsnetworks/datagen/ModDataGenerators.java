package me.almana.logisticsnetworks.datagen;

import net.neoforged.neoforge.data.event.GatherDataEvent;

public final class ModDataGenerators {

    private ModDataGenerators() {
    }

    public static void gather(GatherDataEvent event) {
        if (event.includeClient()) {
            event.addProvider(new ModModelProvider(event.getGenerator().getPackOutput(), event.getExistingFileHelper()));
        }
        if (event.includeServer()) {
            event.createProvider(ModRecipeProvider::new);
            event.createProvider(ModBlockLootProvider::create);
            event.createBlockAndItemTags(ModBlockTagProvider::new, ModItemTagProvider::new);
            event.createProvider(ModFluidTagProvider::new);
        }
    }
}
