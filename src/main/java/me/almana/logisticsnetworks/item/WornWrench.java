package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import me.almana.logisticsnetworks.integration.curios.CuriosCompat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class WornWrench {
    private WornWrench() {
    }

    public static ItemStack drawn(Player player) {
        if (player.isHolding(stack -> stack.getItem() instanceof WrenchItem)) {
            return ItemStack.EMPTY;
        }
        ItemStack worn = CuriosCompat.wornWrench(player);
        return worn.getOrDefault(LogisticsDataComponents.WRENCH_DRAWN, false) ? worn : ItemStack.EMPTY;
    }

    public static ItemStack orDrawn(Player player, ItemStack held) {
        return held.getItem() instanceof WrenchItem ? held : drawn(player);
    }

    public static ItemStack held(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof WrenchItem) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof WrenchItem ? off : drawn(player);
    }
}
