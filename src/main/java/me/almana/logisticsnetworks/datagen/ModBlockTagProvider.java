package me.almana.logisticsnetworks.datagen;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.registration.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

public class ModBlockTagProvider extends BlockTagsProvider {
    private static final List<String> FLUX_BLOCKS = List.of("flux_block", "flux_plug", "flux_point",
            "flux_controller", "basic_flux_storage", "herculean_flux_storage", "gargantuan_flux_storage");

    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, LogisticsNetworks.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(ModTags.NODE_BLACKLIST_BLOCKS);
        tag(ModTags.NODE_COMPATIBILITY_BLACKLIST_BLOCKS);
        var blacklist = getOrCreateRawBuilder(ModTags.NODE_COMPATIBILITY_BLACKLIST_BLOCKS);
        for (String block : FLUX_BLOCKS) {
            blacklist.addOptionalElement(Identifier.fromNamespaceAndPath("fluxnetworks", block));
        }
    }
}
