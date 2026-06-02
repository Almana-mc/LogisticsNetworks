package me.almana.logisticsnetworks.datagen;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

public final class ModBlockLootProvider {
    private ModBlockLootProvider() {}

    public static LootTableProvider create(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        return new LootTableProvider(output, Set.of(), List.of(
                new LootTableProvider.SubProviderEntry(ComputerLoot::new, LootContextParamSets.BLOCK)
        ), registries);
    }

    static final class ComputerLoot extends BlockLootSubProvider {
        ComputerLoot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
        }

        @Override
        protected void generate() {
            dropSelf(Registration.COMPUTER_BLOCK.get());
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return List.of(Registration.COMPUTER_BLOCK.get());
        }
    }
}
