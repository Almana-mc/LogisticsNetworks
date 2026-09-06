package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.integration.sophisticated.SophisticatedCoreCompat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;
import net.p3pp3rf1y.sophisticatedcore.inventory.FilteredItemHandler;
import net.p3pp3rf1y.sophisticatedstorage.block.ContentsFilteredItemHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SophisticatedBoundaryTest {
    @BeforeAll
    static void bootstrap() {
        SophisticatedTransferTest.bootstrap();
    }

    @Test
    void acquiredInputFilterRemainsAuthoritative() throws Exception {
        var target = new SophisticatedInventoryFixture(2, 64);
        var filter = new net.p3pp3rf1y.sophisticatedcore.upgrades.FilterLogic(
                new ItemStack(Items.PAPER), stack -> {}, 1, ModCoreDataComponents.FILTER_ATTRIBUTES);
        filter.setAllowList(true);
        filter.getFilterHandler().setStackInSlot(0, new ItemStack(Items.GOLD_INGOT));
        var acquired = new FilteredItemHandler.Modifiable(target.inventory, List.of(filter), List.of());
        assertTrue(SophisticatedCoreCompat.isBulkHandler(acquired));
        assertEquals(0, TransferParityTest.move(TransferParityTest.inventory(8), List.of(acquired), false));
        assertTrue(target.inventory.getTrackedStacks().isEmpty());
        try (var tx = Transaction.openRoot()) {
            assertEquals(8, insert(acquired, new ItemStack(Items.IRON_INGOT, 8), tx, new boolean[]{false, true}).getCount());
            assertTrue(insert(acquired, new ItemStack(Items.GOLD_INGOT, 8), tx, new boolean[]{false, true}).isEmpty());
            tx.commit();
        }
        assertEquals(ItemResource.of(Items.GOLD_INGOT), target.inventory.getResource(1));
        assertEquals(0, target.inventory.getAmountAsInt(0));
    }

    @Test
    void explicitMaskCannotUseMemorizedSlotOutsideMask() throws Exception {
        var target = new SophisticatedInventoryFixture(2, 64);
        target.memory.setFilter(1, new ItemStack(Items.IRON_INGOT));
        try (var tx = Transaction.openRoot()) {
            assertTrue(insert(target.inventory, new ItemStack(Items.IRON_INGOT, 8), tx, new boolean[]{true, false}).isEmpty());
            tx.commit();
        }
        assertEquals(0, target.inventory.bulkCalls);
        assertEquals(8, target.inventory.getAmountAsInt(0));
        assertEquals(0, target.inventory.getAmountAsInt(1));
    }

    @Test
    void lockedWrapperRequiresKnownContentsAndMatchingMaskedMemory() throws Exception {
        var target = new SophisticatedInventoryFixture(2, 64);
        var locked = new ContentsFilteredItemHandler(() -> target.inventory, target.inventory::getSlotTracker,
                () -> target.memory, true);
        assertTrue(SophisticatedCoreCompat.isBulkHandler(locked));
        assertEquals(0, TransferParityTest.move(TransferParityTest.inventory(8), List.of(locked), false));
        target.memory.setFilter(1, new ItemStack(Items.IRON_INGOT));
        try (var tx = Transaction.openRoot()) {
            assertEquals(8, insert(locked, new ItemStack(Items.IRON_INGOT, 8), tx, new boolean[]{true, false}).getCount());
            assertTrue(insert(locked, new ItemStack(Items.IRON_INGOT, 8), tx, new boolean[]{false, true}).isEmpty());
            tx.commit();
        }
        assertEquals(0, target.inventory.getAmountAsInt(0));
        assertEquals(8, target.inventory.getAmountAsInt(1));
    }

    @Test
    void currentControllerUsesWholeHandlerEvenWithoutTrackedInterface() throws Exception {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var controller = new net.p3pp3rf1y.sophisticatedstorage.block.ControllerBlockEntity(
                net.minecraft.core.BlockPos.ZERO,
                net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks.CONTROLLER.get().defaultBlockState()) {
            @Override
            public int insert(ItemResource resource, int amount, TransactionContext transaction) {
                attempts.incrementAndGet();
                return super.insert(resource, amount, transaction);
            }
        };
        assertTrue(SophisticatedCoreCompat.isBulkHandler(controller));
        assertEquals(0, TransferParityTest.move(TransferParityTest.inventory(4, 4), List.of(controller), false));
        assertEquals(1, attempts.get());
    }

    @Test
    void capabilityCacheKeepsSelectedSideAndItsLockedWrapper() throws Exception {
        var target = new SophisticatedInventoryFixture(1, 64);
        var locked = new ContentsFilteredItemHandler(() -> target.inventory, target.inventory::getSlotTracker,
                () -> target.memory, true);
        var level = mock(net.minecraft.server.level.ServerLevel.class);
        var node = mock(me.almana.logisticsnetworks.entity.LogisticsNodeEntity.class);
        var position = new net.minecraft.core.BlockPos(4, 5, 6);
        var side = net.minecraft.core.Direction.NORTH;
        var capability = net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK;
        when(node.level()).thenReturn(level);
        when(node.getAttachedPos()).thenReturn(position);
        when(level.isLoaded(position)).thenReturn(true);
        when(level.getCapability(capability, position, side)).thenReturn(locked);
        when(level.getCapability(capability, position, null)).thenReturn(target.inventory);
        var cache = new TransferCapabilityCache(node);
        var acquired = cache.findItemHandler(side);
        assertSame(locked, acquired);
        var source = TransferParityTest.inventory(8);
        assertEquals(0, TransferParityTest.move(source, List.of(acquired), false));
        assertEquals(8, source.getAmountAsInt(0));
        assertEquals(0, target.inventory.getAmountAsInt(0));
        verify(level, never()).getCapability(capability, position, null);
        target.memory.setFilter(0, new ItemStack(Items.IRON_INGOT));
        assertSame(locked, cache.findItemHandler(side));
        assertEquals(8, TransferParityTest.move(source, List.of(acquired), false));
        assertEquals(8, target.inventory.getAmountAsInt(0));
    }

    static ItemStack insert(ResourceHandler<ItemResource> handler, ItemStack stack, TransactionContext transaction,
            boolean[] mask) throws Exception {
        var method = TransferEngine.class.getDeclaredMethod("insertItemWithAllowedSlots", ResourceHandler.class,
                ItemStack.class, TransactionContext.class, boolean[].class);
        method.setAccessible(true);
        return (ItemStack) method.invoke(null, handler, stack, transaction, mask);
    }
}
