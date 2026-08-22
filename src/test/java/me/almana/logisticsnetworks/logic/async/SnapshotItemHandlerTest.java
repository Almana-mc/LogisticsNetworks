package me.almana.logisticsnetworks.logic.async;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotItemHandlerTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static NetworkSnapshot.ItemEndpoint endpoint(int totalSlots, int[] slots, ItemStack[] stacks) {
        int[] limits = new int[slots.length];
        java.util.Arrays.fill(limits, 64);
        return new NetworkSnapshot.ItemEndpoint(totalSlots, slots, stacks, 64, limits);
    }

    @Test
    void unoccupiedSlotsReadEmpty() {
        IItemHandler handler = new SnapshotItemHandler(
                endpoint(10000, new int[] {7}, new ItemStack[] {new ItemStack(Items.IRON_INGOT, 5)}));

        assertEquals(10000, handler.getSlots());
        assertTrue(handler.getStackInSlot(0).isEmpty());
        assertTrue(handler.getStackInSlot(9999).isEmpty());
        assertEquals(5, handler.getStackInSlot(7).getCount());
    }

    @Test
    void extractMatchesItemStackHandler() {
        ItemStackHandler real = new ItemStackHandler(3);
        real.setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 20));

        IItemHandler snap = new SnapshotItemHandler(
                endpoint(3, new int[] {1}, new ItemStack[] {new ItemStack(Items.IRON_INGOT, 20)}));

        assertEquals(real.extractItem(1, 8, true).getCount(), snap.extractItem(1, 8, true).getCount());
        assertEquals(real.extractItem(1, 8, false).getCount(), snap.extractItem(1, 8, false).getCount());
        assertEquals(real.getStackInSlot(1).getCount(), snap.getStackInSlot(1).getCount());
    }

    @Test
    void insertMatchesItemStackHandler() {
        ItemStackHandler real = new ItemStackHandler(3);
        real.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 60));

        IItemHandler snap = new SnapshotItemHandler(
                endpoint(3, new int[] {0}, new ItemStack[] {new ItemStack(Items.IRON_INGOT, 60)}));

        ItemStack toInsert = new ItemStack(Items.IRON_INGOT, 10);
        assertEquals(real.insertItem(0, toInsert, false).getCount(),
                snap.insertItem(0, toInsert, false).getCount());
        assertEquals(real.getStackInSlot(0).getCount(), snap.getStackInSlot(0).getCount());
    }

    @Test
    void mismatchedComponentsRejected() {
        IItemHandler snap = new SnapshotItemHandler(
                endpoint(2, new int[] {0}, new ItemStack[] {new ItemStack(Items.IRON_INGOT, 10)}));

        ItemStack diamond = new ItemStack(Items.DIAMOND, 4);
        assertEquals(4, snap.insertItem(0, diamond, false).getCount());
    }

    @Test
    void insertIntoEmptySlotMaterialisesEntry() {
        IItemHandler snap = new SnapshotItemHandler(endpoint(4, new int[0], new ItemStack[0]));

        assertTrue(snap.insertItem(2, new ItemStack(Items.IRON_INGOT, 12), false).isEmpty());
        assertEquals(12, snap.getStackInSlot(2).getCount());
    }
}
