package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.data.IoSide;
import me.almana.logisticsnetworks.integration.storage.DirectStorageHandlers;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record RecipeEndpoint(@Nullable TransferEngine.ItemTransferTarget items,
        Map<IoSide, TransferEngine.ItemTransferTarget> sidedItems,
        @Nullable TransferEngine.FluidTransferTarget fluids,
        Map<IoSide, TransferEngine.FluidTransferTarget> sidedFluids) {

    // Enable when 26.1.2 is supported.
    /*
    Trailing components: @Nullable Chemicals chemicals, Map<IoSide, Chemicals> sidedChemicals

    public record Chemicals(IChemicalHandler handler, ItemStack[] filters, FilterMode mode) {
    }

    @Nullable
    public Chemicals chemicals(Optional<IoSide> side) {
        return side.isEmpty() ? chemicals : sidedChemicals.get(side.get());
    }

    List<Chemicals> chemicalTargets() {
        return all(chemicals, sidedChemicals);
    }
    */

    @Nullable
    TransferEngine.ItemTransferTarget items(Optional<IoSide> side) {
        return side.isEmpty() ? items : sidedItems.get(side.get());
    }

    @Nullable
    TransferEngine.FluidTransferTarget fluids(Optional<IoSide> side) {
        return side.isEmpty() ? fluids : sidedFluids.get(side.get());
    }

    List<TransferEngine.ItemTransferTarget> itemTargets() {
        return all(items, sidedItems);
    }

    List<TransferEngine.FluidTransferTarget> fluidTargets() {
        return all(fluids, sidedFluids);
    }

    boolean isEmpty() {
        // Enable when 26.1.2 is supported.
        /*
        return !has(items, sidedItems) && !has(fluids, sidedFluids) && !has(chemicals, sidedChemicals);
        */
        return !has(items, sidedItems) && !has(fluids, sidedFluids);
    }

    boolean serves(Set<RecipeCardData.Kind> kinds) {
        // Enable when 26.1.2 is supported.
        /*
        return (has(items, sidedItems) || !kinds.contains(RecipeCardData.Kind.ITEM))
                && (has(fluids, sidedFluids) || !kinds.contains(RecipeCardData.Kind.FLUID))
                && (has(chemicals, sidedChemicals) || !kinds.contains(RecipeCardData.Kind.CHEMICAL));
        */
        return (has(items, sidedItems) || !kinds.contains(RecipeCardData.Kind.ITEM))
                && (has(fluids, sidedFluids) || !kinds.contains(RecipeCardData.Kind.FLUID))
                && !kinds.contains(RecipeCardData.Kind.CHEMICAL);
    }

    // Shared network capacity: commit each
    boolean direct() {
        return itemTargets().stream().anyMatch(to -> DirectStorageHandlers.isDirect(to.handler()))
                || fluidTargets().stream().anyMatch(to -> DirectStorageHandlers.isDirect(to.handler()));
    }

    private static boolean has(@Nullable Object main, Map<IoSide, ?> sided) {
        return main != null || !sided.isEmpty();
    }

    private static <T> List<T> all(@Nullable T main, Map<IoSide, T> sided) {
        List<T> all = new ArrayList<>(sided.values());
        if (main != null) all.add(main);
        return all;
    }
}
