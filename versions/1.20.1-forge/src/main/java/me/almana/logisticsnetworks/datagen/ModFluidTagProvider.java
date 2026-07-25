package me.almana.logisticsnetworks.datagen;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.registration.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public class ModFluidTagProvider extends IntrinsicHolderTagsProvider<Fluid> {

    public ModFluidTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
            ExistingFileHelper files) {
        super(output, Registries.FLUID, lookup, fluid -> fluid.builtInRegistryHolder().key(),
                LogisticsNetworks.MOD_ID, files);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(ModTags.RESOURCE_BLACKLIST_FLUIDS);
    }
}
