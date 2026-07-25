package me.almana.logisticsnetworks.datagen;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.registration.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public class ModBlockTagProvider extends BlockTagsProvider {

    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
            ExistingFileHelper files) {
        super(output, lookup, LogisticsNetworks.MOD_ID, files);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(ModTags.NODE_BLACKLIST_BLOCKS);
        tag(ModTags.NODE_COMPATIBILITY_BLACKLIST_BLOCKS);
    }
}
