package me.almana.logisticsnetworks.integration.storage;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

public interface StorageEndpoint {

    record StoredItem(ItemStack stack, long amount, long bufferedAmount) {
        public StoredItem(ItemStack stack, long amount) {
            this(stack, amount, 0);
        }
    }

    record StoredFluid(FluidStack stack, long amount) {
    }

    Object networkIdentity();

    Object endpointIdentity();

    boolean isValid();

    List<StoredItem> exportableItems();

    // Interface slots, not network
    Object2LongMap<Item> itemCounts();

    List<StoredFluid> exportableFluids();

    long fluidCount(FluidStack stack);

    boolean canExportItem(ItemStack stack);

    boolean canExportFluid(FluidStack stack);

    long insertItem(ItemStack stack, long amount, boolean simulate);

    long extractItem(ItemStack stack, long amount, boolean simulate);

    long insertFluid(FluidStack stack, long amount, boolean simulate);

    long extractFluid(FluidStack stack, long amount, boolean simulate);

    default long insertSource(long amount, boolean simulate) {
        return 0;
    }

    default long extractSource(long amount, boolean simulate) {
        return 0;
    }

    default boolean storesChemicals() {
        return false;
    }

    default List<ChemicalStack> exportableChemicals() {
        return List.of();
    }

    default long insertChemical(ChemicalStack stack, long amount, boolean simulate) {
        return 0;
    }

    default long extractChemical(ChemicalStack stack, long amount, boolean simulate) {
        return 0;
    }

    default long chemicalCount(ChemicalStack stack) {
        return 0;
    }
}
