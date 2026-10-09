package me.almana.logisticsnetworks.datagen;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.block.TwoCellBlock;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.client.color.item.CustomModelDataSource;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ModModelProvider extends ModelProvider {
    public ModModelProvider(PackOutput output) {
        super(output, LogisticsNetworks.MOD_ID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Item[] flatItems = {
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
                Registration.NETWORK_UPGRADE.get(),
                Registration.PATTERN_SETTER.get()
        };
        for (Item item : flatItems) {
            itemModels.generateFlatItem(item, ModelTemplates.FLAT_ITEM);
        }

        Identifier wrenchModel = ModelTemplates.THREE_LAYERED_ITEM.create(
                ModelLocationUtils.getModelLocation(Registration.WRENCH.get()),
                TextureMapping.layered(
                        new Material(Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "item/wrench_case")),
                        new Material(Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "item/wrench_screen")),
                        new Material(Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "item/wrench_base"))),
                itemModels.modelOutput);
        itemModels.itemModelOutput.accept(Registration.WRENCH.get(),
                ItemModelUtils.tintedModel(wrenchModel,
                        new CustomModelDataSource(0, WrenchItem.DEFAULT_CASE_COLOR),
                        new CustomModelDataSource(1, WrenchItem.DEFAULT_SCREEN_COLOR)));

        twoCell(blockModels, itemModels, Registration.COMPUTER_BLOCK.get(), "desktop");
        twoCell(blockModels, itemModels, Registration.SERVER_RACK_BLOCK.get(), "server_rack");

        Identifier node = Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "item/logistics_node");
        itemModels.itemModelOutput.accept(Registration.LOGISTICS_NODE_ITEM.get(), ItemModelUtils.plainModel(node));
    }

    private static void twoCell(BlockModelGenerators blockModels, ItemModelGenerators itemModels, Block block,
            String model) {
        MultiVariant main = BlockModelGenerators.plainVariant(
                Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "block/" + model));
        MultiVariant extension = BlockModelGenerators.plainVariant(
                Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "block/" + model + "_extension"));
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block).with(
                PropertyDispatch.initial(TwoCellBlock.MAIN, TwoCellBlock.FACING)
                        .generate((isMain, facing) -> isMain ? main.with(switch (facing) {
                            case WEST -> BlockModelGenerators.Y_ROT_90;
                            case NORTH -> BlockModelGenerators.Y_ROT_180;
                            case EAST -> BlockModelGenerators.Y_ROT_270;
                            default -> BlockModelGenerators.NOP;
                        }) : extension)));
        itemModels.itemModelOutput.accept(block.asItem(),
                ItemModelUtils.plainModel(ModelLocationUtils.getModelLocation(block.asItem())));
    }
}
