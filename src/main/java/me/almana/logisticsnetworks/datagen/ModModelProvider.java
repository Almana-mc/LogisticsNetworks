package me.almana.logisticsnetworks.datagen;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public class ModModelProvider extends ModelProvider {
    public ModModelProvider(PackOutput output) {
        super(output, LogisticsNetworks.MOD_ID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Item[] flatItems = {
                Registration.WRENCH.get(),
                Registration.SMALL_FILTER.get(),
                Registration.MEDIUM_FILTER.get(),
                Registration.BIG_FILTER.get(),
                Registration.MOD_FILTER.get(),
                Registration.NAME_FILTER.get(),
                Registration.IRON_UPGRADE.get(),
                Registration.GOLD_UPGRADE.get(),
                Registration.DIAMOND_UPGRADE.get(),
                Registration.NETHERITE_UPGRADE.get(),
                Registration.DIMENSIONAL_UPGRADE.get(),
                Registration.MEKANISM_CHEMICAL_UPGRADE.get(),
                Registration.ARS_SOURCE_UPGRADE.get(),
                Registration.PATTERN_SETTER.get()
        };
        for (Item item : flatItems) {
            itemModels.generateFlatItem(item, ModelTemplates.FLAT_ITEM);
        }

        Identifier laptop = Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "block/laptop");
        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(Registration.COMPUTER_BLOCK.get(), BlockModelGenerators.plainVariant(laptop))
                        .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        itemModels.itemModelOutput.accept(Registration.COMPUTER_ITEM.get(), ItemModelUtils.plainModel(laptop));

        Identifier node = Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "item/logistics_node");
        itemModels.itemModelOutput.accept(Registration.LOGISTICS_NODE_ITEM.get(), ItemModelUtils.plainModel(node));
    }
}
