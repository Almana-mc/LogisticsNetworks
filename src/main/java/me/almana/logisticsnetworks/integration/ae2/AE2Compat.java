package me.almana.logisticsnetworks.integration.ae2;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.Collections;
import java.util.List;

public final class AE2Compat {

    private static final String AE2_MOD_ID = "ae2";
    private static Boolean loaded = null;

    private AE2Compat() {
    }

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(AE2_MOD_ID);
        }
        return loaded;
    }

    public record PatternEntry(ItemStack item, int amount) {
    }

    public static List<PatternEntry> readPatternInputs(ItemStack pattern) {
        if (!isLoaded()) return Collections.emptyList();
        return AE2PatternHelper.readInputs(pattern);
    }

    public static List<PatternEntry> readPatternOutputs(ItemStack pattern) {
        if (!isLoaded()) return Collections.emptyList();
        return AE2PatternHelper.readOutputs(pattern);
    }

    public static boolean isPattern(ItemStack stack) {
        if (!isLoaded()) return false;
        return AE2PatternHelper.isPattern(stack);
    }
}
