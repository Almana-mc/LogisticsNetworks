package me.almana.logisticsnetworks.menu;

import net.minecraft.world.entity.player.Inventory;

final class MassPlacementInventoryAccess {

    private MassPlacementInventoryAccess() {
    }

    static int selectedSlot(Inventory inventory) {
        //? if <26 {
        /*return inventory.selected;
        *///?} else {
        return inventory.getSelectedSlot();
        //?}
    }
}
