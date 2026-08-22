package me.almana.logisticsnetworks.logic.async;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.FutureTask;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void occupiedSlotLimitsStayAlignedWithSlots() {
        ItemStackHandler handler = handlerWithLimits(64, 17, 64, 43);
        handler.setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 5));
        handler.setStackInSlot(3, new ItemStack(Items.DIAMOND, 2));

        NetworkSnapshot.ItemEndpoint endpoint = Snapshots.captureItems(handler);

        assertArrayEquals(new int[] {1, 3}, endpoint.occupiedSlots());
        assertArrayEquals(new int[] {17, 43}, endpoint.occupiedSlotLimits());
    }

    @Test
    void firstEmptySlotSuppliesDefaultLimit() {
        ItemStackHandler handler = handlerWithLimits(7, 19, 31);
        handler.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 5));

        NetworkSnapshot.ItemEndpoint endpoint = Snapshots.captureItems(handler);

        assertEquals(19, endpoint.defaultSlotLimit());
    }

    @Test
    void fullHandlerUsesSlotZeroLimitAsDefault() {
        ItemStackHandler handler = handlerWithLimits(11, 23);
        handler.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 5));
        handler.setStackInSlot(1, new ItemStack(Items.DIAMOND, 2));

        NetworkSnapshot.ItemEndpoint endpoint = Snapshots.captureItems(handler);

        assertEquals(11, endpoint.defaultSlotLimit());
    }

    @Test
    void zeroSlotHandlerUsesDefaultLimit() {
        NetworkSnapshot.ItemEndpoint endpoint = Snapshots.captureItems(new ItemStackHandler(0));

        assertEquals(64, endpoint.defaultSlotLimit());
    }

    @Test
    void offServerThreadRejectedBeforeHandlerAccess() throws Exception {
        AccessTrackingHandler handler = new AccessTrackingHandler();
        FutureTask<Void> task = new FutureTask<>(() -> {
            assertThrows(IllegalStateException.class, () -> Snapshots.captureItems(handler));
            return null;
        });
        Thread worker = new Thread(task);

        worker.start();
        worker.join();
        task.get();

        assertEquals(0, handler.reads);
    }

    @Test
    void filterArrayCopiedElementWise() {
        ItemStack[] filters = {new ItemStack(Items.IRON_INGOT, 1), ItemStack.EMPTY};

        ItemStack[] copied = Snapshots.copyFilters(filters);

        assertNotSame(filters[0], copied[0]);
        assertEquals(1, copied[0].getCount());
        assertSame(ItemStack.EMPTY, copied[1]);
    }

    private static ItemStackHandler handlerWithLimits(int... limits) {
        return new ItemStackHandler(limits.length) {
            @Override
            public int getSlotLimit(int slot) {
                return limits[slot];
            }
        };
    }

    private static final class AccessTrackingHandler extends ItemStackHandler {

        private int reads;

        private AccessTrackingHandler() {
            super(1);
        }

        @Override
        public int getSlots() {
            reads++;
            return super.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            reads++;
            return super.getStackInSlot(slot);
        }

        @Override
        public int getSlotLimit(int slot) {
            reads++;
            return super.getSlotLimit(slot);
        }
    }
}
