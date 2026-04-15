package me.almana.logisticsnetworks.integration.ae2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

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

    public static boolean isGridHost(Level level, BlockPos pos) {
        if (!isLoaded()) return false;
        return AE2StorageHelper.isGridHost(level, pos);
    }

    public static boolean isGridAccessible(ServerLevel level, GlobalPos linkPos) {
        if (!isLoaded()) return false;
        return AE2StorageHelper.isGridAccessible(level, linkPos);
    }

    public static long countAvailable(ServerLevel level, GlobalPos linkPos, ItemStack pattern) {
        if (!isLoaded()) return 0;
        return AE2StorageHelper.countAvailable(level, linkPos, pattern);
    }

    public static int extractItems(ServerLevel level, GlobalPos linkPos, ItemStack pattern, int amount, ServerPlayer player) {
        if (!isLoaded()) return 0;
        return AE2StorageHelper.extractItems(level, linkPos, pattern, amount, player);
    }

    public static void registerLinkable() {
        if (!isLoaded()) return;
        AE2StorageHelper.registerWrenchLinkable();
    }

    public static int countInInventory(Inventory inventory, ItemStack pattern, int protectedSlot) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot == protectedSlot) continue;
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && ItemStack.isSameItem(stack, pattern)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static int consumeFromInventory(Inventory inventory, ItemStack pattern, int amount, int protectedSlot) {
        int remaining = amount;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            if (slot == protectedSlot) continue;
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !ItemStack.isSameItem(stack, pattern)) continue;
            int consume = Math.min(remaining, stack.getCount());
            stack.shrink(consume);
            if (stack.isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
            } else {
                inventory.setItem(slot, stack);
            }
            remaining -= consume;
        }
        return remaining;
    }

    public static boolean hasCombinedStock(Inventory inventory, ItemStack pattern, int needed,
                                           int protectedSlot, @Nullable GlobalPos ae2Link,
                                           @Nullable ServerLevel level) {
        int invCount = countInInventory(inventory, pattern, protectedSlot);
        if (invCount >= needed) return true;
        if (ae2Link == null || level == null) return false;
        return invCount + countAvailable(level, ae2Link, pattern) >= needed;
    }

    public static void consumeCombined(Inventory inventory, ItemStack pattern, int amount,
                                       int protectedSlot, @Nullable GlobalPos ae2Link,
                                       @Nullable ServerPlayer player) {
        int remaining = consumeFromInventory(inventory, pattern, amount, protectedSlot);
        if (remaining > 0 && ae2Link != null && player != null && player.level() instanceof ServerLevel sl) {
            extractItems(sl, ae2Link, pattern, remaining, player);
        }
    }
}
