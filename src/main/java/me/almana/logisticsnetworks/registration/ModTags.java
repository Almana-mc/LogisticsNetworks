package me.almana.logisticsnetworks.registration;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

public final class ModTags {

    public static final TagKey<Item> FILTERS = itemTag("filters");
    public static final TagKey<Item> UPGRADES = itemTag("upgrades");
    public static final TagKey<Item> RESOURCE_BLACKLIST_ITEMS = itemTag("blacklist/item");

    public static final TagKey<Block> NODE_COMPATIBILITY_BLACKLIST_BLOCKS = blockTag("compatibility_blacklist");
    public static final TagKey<Block> NODE_BLACKLIST_BLOCKS = blockTag("blacklist");

    public static final TagKey<BlockEntityType<?>> NODE_COMPATIBILITY_BLACKLIST_BLOCK_ENTITIES = blockEntityTag(
            "compatibility_blacklist");
    public static final TagKey<BlockEntityType<?>> NODE_BLACKLIST_BLOCK_ENTITIES = blockEntityTag("blacklist");

    public static final TagKey<Fluid> RESOURCE_BLACKLIST_FLUIDS = fluidTag("blacklist/fluid");

    private ModTags() {
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, id(path));
    }

    private static TagKey<Block> blockTag(String path) {
        return TagKey.create(Registries.BLOCK, id(path));
    }

    private static TagKey<BlockEntityType<?>> blockEntityTag(String path) {
        return TagKey.create(Registries.BLOCK_ENTITY_TYPE, id(path));
    }

    private static TagKey<Fluid> fluidTag(String path) {
        return TagKey.create(Registries.FLUID, id(path));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, path);
    }
}
