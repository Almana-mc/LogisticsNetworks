package me.almana.logisticsnetworks.datagen;

import java.util.concurrent.CompletableFuture;
import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.registration.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider;
import net.minecraft.world.level.material.Fluid;

public class ModFluidTagProvider extends IntrinsicHolderTagsProvider<Fluid> {
    public ModFluidTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, Registries.FLUID, lookup, fluid -> fluid.builtInRegistryHolder().key(), Logisticsnetworks.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(ModTags.RESOURCE_BLACKLIST_FLUIDS);
    }
}
