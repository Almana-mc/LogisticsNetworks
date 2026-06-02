package me.almana.logisticsnetworks.datagen;

import java.util.concurrent.CompletableFuture;
import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.registration.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

public class ModBlockTagProvider extends BlockTagsProvider {
    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, Logisticsnetworks.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(ModTags.NODE_BLACKLIST_BLOCKS);
        tag(ModTags.NODE_COMPATIBILITY_BLACKLIST_BLOCKS);
    }
}
