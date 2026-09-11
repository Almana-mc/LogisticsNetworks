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

    public static int count(List<ItemStack> stacks) {
        int total = 0;
        for (ItemStack stack : stacks) total = Math.addExact(total, stack.getCount());
        return total;
    }

    public static int count(List<ItemStack> stacks, ItemStack pattern) {
        int total = 0;
        for (ItemStack stack : stacks) {
            if (ItemStack.isSameItem(stack, pattern)) total = Math.addExact(total, stack.getCount());
        }
        return total;
    }

    public static List<ItemStack> reserve(List<ItemStack> stacks, ItemStack pattern, int amount) {
        List<ItemStack> reserved = new ArrayList<>();
        int remaining = amount;
        for (int index = 0; index < stacks.size() && remaining > 0; index++) {
            ItemStack stack = stacks.get(index);
            if (!ItemStack.isSameItem(stack, pattern)) continue;
            int taken = Math.min(remaining, stack.getCount());
            reserved.add(stack.copyWithCount(taken));
            stack.shrink(taken);
            if (stack.isEmpty()) {
                stacks.remove(index);
                index--;
            }
            remaining -= taken;
        }
        return reserved;
    }

    public static void returnToPlayer(ServerPlayer player, List<ItemStack> stacks) {
        Inventory inventory = player.getInventory();
        for (ItemStack stack : stacks) {
            ItemStack returned = stack.copy();
            inventory.add(returned);
            if (!returned.isEmpty()) player.drop(returned, false);
        }
        inventory.setChanged();
    }

    public static void returnToStorageOrPlayer(StorageAccess access, ServerPlayer player, List<ItemStack> stacks) {
        List<ItemStack> leftovers = new ArrayList<>();
        for (ItemStack stack : stacks) {
            long inserted = access.insert(stack, stack.getCount(), player);
            int remaining = stack.getCount() - (int) Math.min(inserted, stack.getCount());
            if (remaining > 0) leftovers.add(stack.copyWithCount(remaining));
        }
        returnToPlayer(player, leftovers);
    }

    public static List<LinkedStorage.ItemRequirement> combine(
            List<LinkedStorage.ItemRequirement> requirements) {
        List<LinkedStorage.ItemRequirement> combined = new ArrayList<>();
        for (LinkedStorage.ItemRequirement requirement : requirements) {
            if (requirement.stack().isEmpty() || requirement.count() <= 0) continue;
            int match = -1;
            for (int index = 0; index < combined.size(); index++) {
                if (ItemStack.isSameItem(combined.get(index).stack(), requirement.stack())) {
                    match = index;
                    break;
                }
            }
            if (match < 0) {
                combined.add(new LinkedStorage.ItemRequirement(
                        requirement.stack().copyWithCount(1), requirement.count()));
            } else {
                LinkedStorage.ItemRequirement existing = combined.get(match);
                combined.set(match, new LinkedStorage.ItemRequirement(existing.stack(),
                        Math.addExact(existing.count(), requirement.count())));
            }
        }
        return combined;
    }
}
