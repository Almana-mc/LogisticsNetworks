package me.almana.logisticsnetworks.logic.async;

import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OccupiedSlotValveTest extends SnapshotFixture {
    @Test
    void sparseHundredThousandSlotsRetainsOnlyOccupiedEntries() {
        var live = new ItemStacksResourceHandler(100_000);
        live.set(99_999, ItemResource.of(Items.DIAMOND), 1);
        var endpoint = Snapshots.captureItems(live, new Snapshots.OccupiedSlotBudget(1));
        assertArrayEquals(new int[]{99_999}, endpoint.occupiedSlots());
        assertEquals(ItemResource.of(Items.DIAMOND), new SnapshotItemHandler(endpoint).getResource(99_999));
    }
    @Test
    void sharedBudgetRejectsFirstOccupiedSlotBeyondLimit() {
        var budget = new Snapshots.OccupiedSlotBudget(2);
        Snapshots.captureItems(inventory(1), budget);
        Snapshots.captureItems(inventory(1), budget);
        assertThrows(Snapshots.OccupiedSlotLimitExceeded.class, () -> Snapshots.captureItems(inventory(1), budget));
    }
    @Test
    void emptyInventoryFitsZeroBudget() {
        assertEquals(0, Snapshots.captureItems(new ItemStacksResourceHandler(100_000),
                new Snapshots.OccupiedSlotBudget(0)).occupiedSlots().length);
    }
}
