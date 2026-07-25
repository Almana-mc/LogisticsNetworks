package me.almana.logisticsnetworks.datagen;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.data.event.GatherDataEvent;

public final class ModDataGenerators {

    private ModDataGenerators() {
    }

    public static void gather(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        if (event.includeClient()) {
            generator.addProvider(true, new ModModelProvider(output, event.getExistingFileHelper()));
        }
        if (event.includeServer()) {
            generator.addProvider(true, new ModRecipeProvider(output));
            generator.addProvider(true, ModBlockLootProvider.create(output, event.getLookupProvider()));
            ModBlockTagProvider blocks = generator.addProvider(true,
                    new ModBlockTagProvider(output, event.getLookupProvider(), event.getExistingFileHelper()));
            generator.addProvider(true, new ModItemTagProvider(output, event.getLookupProvider(),
                    blocks.contentsGetter(), event.getExistingFileHelper()));
            generator.addProvider(true,
                    new ModFluidTagProvider(output, event.getLookupProvider(), event.getExistingFileHelper()));
        }
    }
}
