package me.almana.logisticsnetworks.integration.curios;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class CuriosCompat {
    private static final boolean LOADED = ModList.get().isLoaded("curios");

    private CuriosCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static ItemStack wornWrench(Player player) {
        return LOADED ? CuriosWrenchSlot.wrench(player) : ItemStack.EMPTY;
    }
}
