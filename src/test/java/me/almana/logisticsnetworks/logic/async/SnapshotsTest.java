package me.almana.logisticsnetworks.logic.async;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class SnapshotsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ThreadGuard.markServerThread();
    }

    @Test
    void onlyOccupiedSlotsCaptured() {
        ItemStackHandler handler = new ItemStackHandler(1000);
        handler.setStackInSlot(3, new ItemStack(Items.IRON_INGOT, 5));
        handler.setStackInSlot(900, new ItemStack(Items.DIAMOND, 2));

        NetworkSnapshot.ItemEndpoint endpoint = Snapshots.captureItems(handler);

        assertEquals(1000, endpoint.totalSlots());
        assertEquals(2, endpoint.occupiedSlots().length);
        assertEquals(3, endpoint.occupiedSlots()[0]);
        assertEquals(900, endpoint.occupiedSlots()[1]);
    }

    @Test
    void capturedStacksAreOwnedCopies() {
        ItemStackHandler handler = new ItemStackHandler(4);
        handler.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 5));

        NetworkSnapshot.ItemEndpoint endpoint = Snapshots.captureItems(handler);

        assertNotSame(handler.getStackInSlot(0), endpoint.occupiedStacks()[0]);

        handler.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 64));
        assertEquals(5, endpoint.occupiedStacks()[0].getCount());
    }

    @Test
    void emptyHandlerCaptured() {
        NetworkSnapshot.ItemEndpoint endpoint = Snapshots.captureItems(new ItemStackHandler(27));

        assertEquals(27, endpoint.totalSlots());
        assertEquals(0, endpoint.occupiedSlots().length);
    }

    @Test
    void filterArrayCopiedElementWise() {
        ItemStack[] filters = {new ItemStack(Items.IRON_INGOT, 1), ItemStack.EMPTY};

        ItemStack[] copied = Snapshots.copyFilters(filters);

        assertNotSame(filters[0], copied[0]);
        assertEquals(1, copied[0].getCount());
    }
}
