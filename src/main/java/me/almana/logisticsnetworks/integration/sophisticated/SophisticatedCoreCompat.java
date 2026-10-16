package me.almana.logisticsnetworks.integration.sophisticated;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public final class SophisticatedCoreCompat {
    private static final boolean LOADED = ModList.get().isLoaded("sophisticatedcore");

    private SophisticatedCoreCompat() {
    }

    public static boolean isSidedWrapper(IItemHandler handler) {
        return LOADED && SophisticatedCoreInsertion.isSidedWrapper(handler);
    }

    public static boolean isBulkHandler(@Nullable IItemHandler handler) {
        return LOADED && SophisticatedCoreInsertion.isBulkHandler(handler);
    }

    public static ItemStack insertItem(IItemHandler handler, ItemStack stack, boolean simulate) {
        return SophisticatedCoreInsertion.insertItem(handler, stack, simulate);
    }
}
