package me.almana.logisticsnetworks.datagen;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModModelProvider extends BlockStateProvider {

    public ModModelProvider(PackOutput output, ExistingFileHelper files) {
        super(output, LogisticsNetworks.MOD_ID, files);
    }

    @Override
    protected void registerStatesAndModels() {
        ModelFile laptop = models().getExistingFile(modLoc("block/laptop"));
        horizontalBlock(Registration.COMPUTER_BLOCK.get(), laptop);
        simpleBlockItem(Registration.COMPUTER_BLOCK.get(), laptop);

        itemModels().getBuilder("wrench")
                .parent(new ModelFile.UncheckedModelFile("item/generated"))
                .texture("layer0", modLoc("item/wrench_case"))
                .texture("layer1", modLoc("item/wrench_screen"));

        itemModels().basicItem(Registration.SMALL_FILTER.get());
        itemModels().basicItem(Registration.MEDIUM_FILTER.get());
        itemModels().basicItem(Registration.BIG_FILTER.get());
        itemModels().basicItem(Registration.MOD_FILTER.get());
        itemModels().basicItem(Registration.NAME_FILTER.get());
        legacyFilter("amount_filter");
        legacyFilter("durability_filter");
        legacyFilter("nbt_filter");
        legacyFilter("slot_filter");
        legacyFilter("tag_filter");
        itemModels().basicItem(Registration.IRON_UPGRADE.get());
        itemModels().basicItem(Registration.GOLD_UPGRADE.get());
        itemModels().basicItem(Registration.DIAMOND_UPGRADE.get());
        itemModels().basicItem(Registration.NETHERITE_UPGRADE.get());
        itemModels().basicItem(Registration.DIMENSIONAL_UPGRADE.get());
        itemModels().basicItem(Registration.MEKANISM_CHEMICAL_UPGRADE.get());
        itemModels().basicItem(Registration.ARS_SOURCE_UPGRADE.get());
        itemModels().basicItem(Registration.PATTERN_SETTER.get());
    }

    private void legacyFilter(String name) {
        itemModels().getBuilder(name)
                .parent(new ModelFile.UncheckedModelFile("item/generated"))
                .texture("layer0", modLoc("item/small_filter"));
    }
}
