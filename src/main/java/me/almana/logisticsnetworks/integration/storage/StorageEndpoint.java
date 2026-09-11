package me.almana.logisticsnetworks.integration.storage;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

public interface StorageEndpoint {

    record StoredItem(ItemStack stack, long amount) {
    }

    record StoredFluid(FluidStack stack, long amount) {
    }

    Object networkIdentity();

    boolean isValid();

    List<StoredItem> exportableItems();

    List<StoredItem> storedItems();

    List<StoredFluid> exportableFluids();

    List<StoredFluid> storedFluids();

    long insertItem(ItemStack stack, long amount, boolean simulate);

    long extractItem(ItemStack stack, long amount, boolean simulate);

    long insertFluid(FluidStack stack, long amount, boolean simulate);

    long extractFluid(FluidStack stack, long amount, boolean simulate);
}
