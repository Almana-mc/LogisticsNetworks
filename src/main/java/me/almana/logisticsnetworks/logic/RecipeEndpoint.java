package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.data.FilterMode;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public record RecipeEndpoint(@Nullable TransferEngine.ItemTransferTarget items,
        @Nullable TransferEngine.FluidTransferTarget fluids, @Nullable Chemicals chemicals) {

    public record Chemicals(IChemicalHandler handler, ItemStack[] filters, FilterMode mode) {
    }

    boolean serves(Set<RecipeCardData.Kind> kinds) {
        return (items != null || !kinds.contains(RecipeCardData.Kind.ITEM))
                && (fluids != null || !kinds.contains(RecipeCardData.Kind.FLUID))
                && (chemicals != null || !kinds.contains(RecipeCardData.Kind.CHEMICAL));
    }
}
