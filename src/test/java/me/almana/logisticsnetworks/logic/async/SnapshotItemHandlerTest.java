package me.almana.logisticsnetworks.logic.async;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
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
        return endpoint(totalSlots, slots, stacks, 64, limits);
    }

    private static NetworkSnapshot.ItemEndpoint endpoint(
            int totalSlots, int[] slots, ItemStack[] stacks, int defaultSlotLimit, int[] limits) {
        return new NetworkSnapshot.ItemEndpoint(totalSlots, slots, stacks, defaultSlotLimit, limits);
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
    void overstackedExtractionMatchesItemStackHandler() {
        ItemStackHandler real = new ItemStackHandler(1);
        real.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 100));

        IItemHandler snap = new SnapshotItemHandler(
                endpoint(1, new int[] {0}, new ItemStack[] {new ItemStack(Items.IRON_INGOT, 100)}));

        assertEquals(real.extractItem(0, 100, false).getCount(), snap.extractItem(0, 100, false).getCount());
        assertEquals(real.getStackInSlot(0).getCount(), snap.getStackInSlot(0).getCount());
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
        ItemStack stored = new ItemStack(Items.IRON_INGOT, 10);
        stored.set(DataComponents.CUSTOM_NAME, Component.literal("stored"));
        IItemHandler snap = new SnapshotItemHandler(
                endpoint(2, new int[] {0}, new ItemStack[] {stored}));

        ItemStack inserted = new ItemStack(Items.IRON_INGOT, 4);
        inserted.set(DataComponents.CUSTOM_NAME, Component.literal("inserted"));
        assertEquals(4, snap.insertItem(0, inserted, false).getCount());
    }

    @Test
    void occupiedSlotLimitCapsInsertion() {
        IItemHandler snap = new SnapshotItemHandler(endpoint(
                2,
                new int[] {0},
                new ItemStack[] {new ItemStack(Items.IRON_INGOT, 5)},
                64,
                new int[] {7}));

        assertEquals(18, snap.insertItem(0, new ItemStack(Items.IRON_INGOT, 20), false).getCount());
        assertEquals(7, snap.getStackInSlot(0).getCount());
    }

    @Test
    void defaultSlotLimitCapsEmptySlotInsertion() {
        IItemHandler snap = new SnapshotItemHandler(endpoint(
                2,
                new int[0],
                new ItemStack[0],
                12,
                new int[0]));

        assertEquals(8, snap.insertItem(1, new ItemStack(Items.IRON_INGOT, 20), false).getCount());
        assertEquals(12, snap.getStackInSlot(1).getCount());
    }

    @Test
    void simulatedInsertDoesNotChangeState() {
        IItemHandler snap = new SnapshotItemHandler(
                endpoint(1, new int[] {0}, new ItemStack[] {new ItemStack(Items.IRON_INGOT, 60)}));

        assertTrue(snap.insertItem(0, new ItemStack(Items.IRON_INGOT, 4), true).isEmpty());
        assertEquals(60, snap.getStackInSlot(0).getCount());
    }

    @Test
    void insertIntoEmptySlotMaterialisesEntry() {
        IItemHandler snap = new SnapshotItemHandler(endpoint(4, new int[0], new ItemStack[0]));

        assertTrue(snap.insertItem(2, new ItemStack(Items.IRON_INGOT, 12), false).isEmpty());
        assertEquals(12, snap.getStackInSlot(2).getCount());
    }
}
