package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.integration.curios.CuriosCompat;
import me.almana.logisticsnetworks.util.ItemDataUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class WornWrench {
    private static final String KEY_DRAWN = "wrench_drawn";

    private WornWrench() {
    }

    public static boolean isDrawn(ItemStack stack) {
        return ItemDataUtil.peekCustomData(stack).getBoolean(KEY_DRAWN);
    }

    public static void setDrawn(ItemStack stack, boolean drawn) {
        ItemDataUtil.updateCustomData(stack, custom -> custom.putBoolean(KEY_DRAWN, drawn));
    }

    public static ItemStack drawn(Player player) {
        if (player.isHolding(stack -> stack.getItem() instanceof WrenchItem)) {
            return ItemStack.EMPTY;
        }
        ItemStack worn = CuriosCompat.wornWrench(player);
        return isDrawn(worn) ? worn : ItemStack.EMPTY;
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
