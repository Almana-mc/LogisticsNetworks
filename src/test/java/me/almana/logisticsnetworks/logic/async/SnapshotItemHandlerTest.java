package me.almana.logisticsnetworks.logic.async;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotItemHandlerTest extends SnapshotFixture {
    @Test
    void sparseCaptureOwnsStacksAndArrays() {
        var live = inventory(128, 0);
        var endpoint = Snapshots.captureItems(live);
        endpoint.occupiedStacks()[0].setCount(1);
        endpoint.occupiedSlots()[0] = 1;
        endpoint.occupiedSlotLimits()[0] = 1;
        var handler = new SnapshotItemHandler(endpoint);
        assertEquals(128, handler.getAmountAsInt(0));
        try (var tx = Transaction.openRoot()) {
            assertEquals(64, handler.extract(0, IRON, 100, tx));
            tx.commit();
        }
        assertEquals(64, handler.getAmountAsInt(0));
        assertEquals(128, live.getAmountAsInt(0));
        assertEquals(128, new SnapshotItemHandler(endpoint).getAmountAsInt(0));
    }

    @Test
    void nestedCommitStillRollsBackWithParent() {
        var handler = new SnapshotItemHandler(Snapshots.captureItems(inventory(8, 0)));
        try (var root = Transaction.openRoot()) {
            try (var child = Transaction.open(root)) {
                assertEquals(3, handler.extract(0, IRON, 3, child));
                assertEquals(3, handler.insert(1, IRON, 3, child));
                child.commit();
            }
            assertEquals(5, handler.getAmountAsInt(0));
        }
        assertEquals(8, handler.getAmountAsInt(0));
        assertEquals(0, handler.getAmountAsInt(1));
    }

    @Test
    void bulkUsesCapturedLimitsAndOrdinaryUsesWholeDefaultOrder() {
        var endpoint = new NetworkSnapshot.ItemEndpoint(2, new int[]{1},
                new ItemStack[]{new ItemStack(Items.IRON_INGOT, 60)}, 64, new int[]{64}, null);
        var ordinary = new SnapshotItemHandler(endpoint);
        try (var tx = Transaction.openRoot()) {
            assertEquals(8, ordinary.insert(IRON, 8, tx));
            tx.commit();
        }
        assertEquals(8, ordinary.getAmountAsInt(0));
        assertEquals(60, ordinary.getAmountAsInt(1));
        var bulk = new SnapshotItemHandler(new NetworkSnapshot.ItemEndpoint(2, new int[]{1},
                new ItemStack[]{new ItemStack(Items.IRON_INGOT, 60)}, 64, new int[]{64}, new int[]{128,128}));
        try (var tx = Transaction.openRoot()) {
            assertEquals(100, bulk.insert(IRON, 100, tx));
            tx.commit();
        }
        assertEquals(128, bulk.getAmountAsInt(1));
        assertEquals(32, bulk.getAmountAsInt(0));
    }
}
