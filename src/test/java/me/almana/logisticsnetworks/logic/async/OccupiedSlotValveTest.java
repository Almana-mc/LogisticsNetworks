package me.almana.logisticsnetworks.logic.async;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OccupiedSlotValveTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ThreadGuard.markServerThread();
    }

    @Test
    void sparseHandlerCountsOnlyOccupiedEntries() {
        ItemStackHandler handler = new ItemStackHandler(100_000);
        handler.setStackInSlot(99_999, new ItemStack(Items.DIAMOND));
        Snapshots.OccupiedSlotBudget budget = new Snapshots.OccupiedSlotBudget(1);

        NetworkSnapshot.ItemEndpoint endpoint = Snapshots.captureItems(handler, budget);

        assertEquals(1, endpoint.occupiedSlots().length);
    }

    @Test
    void sourceAndTargetEntriesShareTheSameThreshold() {
        ItemStackHandler source = occupiedHandler(Items.IRON_INGOT);
        ItemStackHandler target = occupiedHandler(Items.DIAMOND);
        Snapshots.OccupiedSlotBudget budget = new Snapshots.OccupiedSlotBudget(2);

        assertEquals(1, Snapshots.captureItems(source, budget).occupiedSlots().length);
        assertEquals(1, Snapshots.captureItems(target, budget).occupiedSlots().length);
    }

    @Test
    void firstEntryPastThresholdAbortsCapture() {
        ItemStackHandler source = occupiedHandler(Items.IRON_INGOT);
        ItemStackHandler target = occupiedHandler(Items.DIAMOND);
        ItemStackHandler overflow = occupiedHandler(Items.GOLD_INGOT);
        Snapshots.OccupiedSlotBudget budget = new Snapshots.OccupiedSlotBudget(2);
        Snapshots.captureItems(source, budget);
        Snapshots.captureItems(target, budget);

        assertThrows(Snapshots.OccupiedSlotLimitExceeded.class,
                () -> Snapshots.captureItems(overflow, budget));
    }

    @Test
    void emptySlotsDoNotCrossZeroThreshold() {
        Snapshots.OccupiedSlotBudget budget = new Snapshots.OccupiedSlotBudget(0);

        NetworkSnapshot.ItemEndpoint endpoint = Snapshots.captureItems(
                new ItemStackHandler(100_000), budget);

        assertEquals(0, endpoint.occupiedSlots().length);
    }

    private static ItemStackHandler occupiedHandler(net.minecraft.world.item.Item item) {
        ItemStackHandler handler = new ItemStackHandler(32);
        handler.setStackInSlot(17, new ItemStack(item));
        return handler;
    }
}
