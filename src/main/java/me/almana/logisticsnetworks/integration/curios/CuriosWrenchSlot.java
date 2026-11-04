package me.almana.logisticsnetworks.integration.curios;

import me.almana.logisticsnetworks.item.WrenchItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

final class CuriosWrenchSlot {
    private static final String SLOT = "logistics_wrench";

    private CuriosWrenchSlot() {
    }

    static ItemStack wrench(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(inventory -> inventory.findCurio(SLOT, 0))
                .map(SlotResult::stack)
                .filter(stack -> stack.getItem() instanceof WrenchItem)
                .orElse(ItemStack.EMPTY);
    }
}
