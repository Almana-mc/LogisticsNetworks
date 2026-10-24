package me.almana.logisticsnetworks.integration.appmek;

import appeng.api.stacks.AEKey;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.chemical.ChemicalStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

public final class AppMekCompat {

    private static Boolean loaded = null;

    private AppMekCompat() {
    }

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("appmek");
        }
        return loaded;
    }

    @Nullable
    public static AEKey key(ChemicalStack stack) {
        return isLoaded() && !stack.isEmpty() ? Keys.of(stack) : null;
    }

    @Nullable
    public static ChemicalStack stack(AEKey key, long amount) {
        return isLoaded() && amount > 0 ? Keys.stack(key, amount) : null;
    }

    // defers loading appmek classes
    private static final class Keys {
        static AEKey of(ChemicalStack stack) {
            return MekanismKey.of(stack);
        }

        @Nullable
        static ChemicalStack stack(AEKey key, long amount) {
            return key instanceof MekanismKey chemical ? chemical.withAmount(amount) : null;
        }
    }
}
