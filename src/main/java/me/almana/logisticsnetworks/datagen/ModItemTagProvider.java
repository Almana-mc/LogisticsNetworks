package me.almana.logisticsnetworks.datagen;

import java.util.concurrent.CompletableFuture;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.ItemTagsProvider;

public class ModItemTagProvider extends ItemTagsProvider {
    public ModItemTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, LogisticsNetworks.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(ModTags.FILTERS).add(
                Registration.SMALL_FILTER.get(),
                Registration.MEDIUM_FILTER.get(),
                Registration.BIG_FILTER.get(),
                Registration.MOD_FILTER.get(),
                Registration.NAME_FILTER.get(),
                Registration.AMOUNT_FILTER.get(),
                Registration.DURABILITY_FILTER.get(),
                Registration.NBT_FILTER.get(),
                Registration.SLOT_FILTER.get(),
                Registration.TAG_FILTER.get());

        tag(ModTags.UPGRADES).add(
                Registration.IRON_UPGRADE.get(),
                Registration.GOLD_UPGRADE.get(),
                Registration.DIAMOND_UPGRADE.get(),
                Registration.NETHERITE_UPGRADE.get(),
                Registration.DIMENSIONAL_UPGRADE.get(),
                Registration.MEKANISM_CHEMICAL_UPGRADE.get(),
                Registration.ARS_SOURCE_UPGRADE.get());

        tag(ModTags.RESOURCE_BLACKLIST_ITEMS);
    }
}
