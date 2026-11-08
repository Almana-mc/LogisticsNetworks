package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.integration.storage.DirectStorageHandlers;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public record RecipeEndpoint(@Nullable TransferEngine.ItemTransferTarget items,
        @Nullable TransferEngine.FluidTransferTarget fluids) {

    // Enable when 26.1.2 is supported.
    /*
    Third component: @Nullable Chemicals chemicals

    public record Chemicals(IChemicalHandler handler, ItemStack[] filters, FilterMode mode) {
    }
    */

    boolean serves(Set<RecipeCardData.Kind> kinds) {
        // Enable when 26.1.2 is supported.
        /*
        return (items != null || !kinds.contains(RecipeCardData.Kind.ITEM))
                && (fluids != null || !kinds.contains(RecipeCardData.Kind.FLUID))
                && (chemicals != null || !kinds.contains(RecipeCardData.Kind.CHEMICAL));
        */
        return (items != null || !kinds.contains(RecipeCardData.Kind.ITEM))
                && (fluids != null || !kinds.contains(RecipeCardData.Kind.FLUID))
                && !kinds.contains(RecipeCardData.Kind.CHEMICAL);
    }

    // Shared network capacity: commit each
    boolean direct() {
        return items != null && DirectStorageHandlers.isDirect(items.handler())
                || fluids != null && DirectStorageHandlers.isDirect(fluids.handler());
    }
}
