package me.almana.logisticsnetworks.integration.refinedstorage;

import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.mekanism.ChemicalResource;
import mekanism.api.chemical.ChemicalStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

final class RefinedStorageChemicals {

    private static Boolean loaded = null;

    private RefinedStorageChemicals() {
    }

    static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("refinedstorage_mekanism_integration");
        }
        return loaded;
    }

    @Nullable
    static ResourceKey resource(ChemicalStack stack) {
        return isLoaded() && !stack.isEmpty() ? Resources.of(stack) : null;
    }

    @Nullable
    static ChemicalStack stack(ResourceKey key, long amount) {
        return isLoaded() && amount > 0 ? Resources.stack(key, amount) : null;
    }

    // defers loading integration classes
    private static final class Resources {
        static ResourceKey of(ChemicalStack stack) {
            return ChemicalResource.ofChemicalStack(stack);
        }

        @Nullable
        static ChemicalStack stack(ResourceKey key, long amount) {
            return key instanceof ChemicalResource chemical ? new ChemicalStack(chemical.chemical(), amount) : null;
        }
    }
}
