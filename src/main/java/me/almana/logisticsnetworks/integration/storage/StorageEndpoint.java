package me.almana.logisticsnetworks.integration.storage;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface StorageEndpoint {

    record StoredItem(ItemStack stack, long amount) {
    }

    record StoredFluid(FluidStack stack, long amount) {
    }

    Object networkIdentity();

    Object endpointIdentity();

    boolean isValid();

    default List<StoredItem> exportableItems() {
        return exportableItems(false);
    }

    List<StoredItem> exportableItems(boolean fresh);

    Map<Item, Long> itemCounts(Set<Item> items, boolean fresh);

    default List<StoredFluid> exportableFluids() {
        return exportableFluids(false);
    }

    List<StoredFluid> exportableFluids(boolean fresh);

    long fluidCount(FluidStack stack, boolean fresh);

    boolean canExportItem(ItemStack stack);

    boolean canExportFluid(FluidStack stack);

    long insertItem(ItemStack stack, long amount, boolean simulate);

    long extractItem(ItemStack stack, long amount, boolean simulate);

    long insertFluid(FluidStack stack, long amount, boolean simulate);

    long extractFluid(FluidStack stack, long amount, boolean simulate);
}
