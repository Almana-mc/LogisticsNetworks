package me.almana.logisticsnetworks.integration.storage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class StorageInventory {

    private StorageInventory() {
    }

    public static int count(Container inventory, ItemStack pattern, int protectedSlot) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot == protectedSlot) continue;
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && ItemStack.isSameItem(stack, pattern)) {
                total = Math.addExact(total, stack.getCount());
            }
        }
        return total;
    }

    public static List<ItemStack> reserve(Container inventory, ItemStack pattern, int amount, int protectedSlot) {
        List<ItemStack> reserved = new ArrayList<>();
        int remaining = amount;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            if (slot == protectedSlot) continue;
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !ItemStack.isSameItem(stack, pattern)) continue;
            int taken = Math.min(remaining, stack.getCount());
            reserved.add(stack.copyWithCount(taken));
            stack.shrink(taken);
            if (stack.isEmpty()) inventory.setItem(slot, ItemStack.EMPTY);
            else inventory.setItem(slot, stack);
            remaining -= taken;
        }
        inventory.setChanged();
        return reserved;
    }

    public static void returnToPlayer(ServerPlayer player, List<ItemStack> stacks) {
        Inventory inventory = player.getInventory();
        // Mirrors AbstractContainerMenu.removed
        boolean absent = !player.isAlive() || player.hasDisconnected();
        for (ItemStack stack : stacks) {
            ItemStack returned = stack.copy();
            if (!absent) inventory.add(returned);
            if (!returned.isEmpty()) player.drop(returned, false);
        }
        inventory.setChanged();
    }
}
