package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.data.FilterMode;
import me.almana.logisticsnetworks.data.IoSide;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record RecipeEndpoint(@Nullable TransferEngine.ItemTransferTarget items,
        Map<IoSide, TransferEngine.ItemTransferTarget> sidedItems,
        @Nullable TransferEngine.FluidTransferTarget fluids,
        Map<IoSide, TransferEngine.FluidTransferTarget> sidedFluids,
        @Nullable Chemicals chemicals, Map<IoSide, Chemicals> sidedChemicals) {

    public record Chemicals(IChemicalHandler handler, ItemStack[] filters, FilterMode mode) {
    }

    @Nullable
    TransferEngine.ItemTransferTarget items(Optional<IoSide> side) {
        return side.isEmpty() ? items : sidedItems.get(side.get());
    }

    @Nullable
    TransferEngine.FluidTransferTarget fluids(Optional<IoSide> side) {
        return side.isEmpty() ? fluids : sidedFluids.get(side.get());
    }

    @Nullable
    public Chemicals chemicals(Optional<IoSide> side) {
        return side.isEmpty() ? chemicals : sidedChemicals.get(side.get());
    }

    List<TransferEngine.ItemTransferTarget> itemTargets() {
        return all(items, sidedItems);
    }

    List<TransferEngine.FluidTransferTarget> fluidTargets() {
        return all(fluids, sidedFluids);
    }

    List<Chemicals> chemicalTargets() {
        return all(chemicals, sidedChemicals);
    }

    boolean isEmpty() {
        return !has(items, sidedItems) && !has(fluids, sidedFluids) && !has(chemicals, sidedChemicals);
    }

    boolean serves(Set<RecipeCardData.Kind> kinds) {
        return (has(items, sidedItems) || !kinds.contains(RecipeCardData.Kind.ITEM))
                && (has(fluids, sidedFluids) || !kinds.contains(RecipeCardData.Kind.FLUID))
                && (has(chemicals, sidedChemicals) || !kinds.contains(RecipeCardData.Kind.CHEMICAL));
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
