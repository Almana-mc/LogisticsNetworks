package me.almana.logisticsnetworks.logic;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SophisticatedTransferTest {
    @BeforeAll
    static void bootstrap() {
        TransferParityTest.bootstrap();
        SophisticatedInventoryFixture.loadConfig();
    }

    @Test
    void bulkInsertionHonorsMemorizedSlotBeforeOrdinaryEmptySlot() throws Exception {
        var target = new SophisticatedInventoryFixture(2, 64);
        target.memory.setFilter(1, new ItemStack(Items.IRON_INGOT));
        var source = TransferParityTest.inventory(8);
        assertEquals(8, TransferParityTest.move(source, List.of(target.inventory), false));
        assertEquals(0, target.inventory.getAmountAsInt(0));
        assertEquals(8, target.inventory.getAmountAsInt(1));
        assertEquals(0, source.getAmountAsInt(0));
    }

    @Test
    void repeatedRejectedCandidateUsesOneBulkAttempt() throws Exception {
        var target = new SophisticatedInventoryFixture(1, 64);
        target.inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 64));
        var source = TransferParityTest.inventory(4, 4);
        assertEquals(0, TransferParityTest.move(source, List.of(target.inventory), false));
        assertEquals(1, target.inventory.bulkCalls);
        assertEquals(4, source.getAmountAsInt(0));
        assertEquals(4, source.getAmountAsInt(1));
    }

    @Test
    void genericHandlerKeepsDefaultDistribution() throws Exception {
        var source = TransferParityTest.inventory(8);
        var target = TransferParityTest.inventory(0, 60);
        assertEquals(8, TransferParityTest.move(source, List.of(target), false));
        assertEquals(8, target.getAmountAsInt(0));
        assertEquals(60, target.getAmountAsInt(1));
    }

    @Test
    void rejectedCandidateIncludesExactCountAndComponents() throws Exception {
        var target = new SophisticatedInventoryFixture(1, 64);
        target.inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 64));
        var named = new ItemStack(Items.IRON_INGOT, 4);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("distinct"));
        var source = new ItemStacksResourceHandler(NonNullList.of(ItemStack.EMPTY,
                new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.IRON_INGOT, 3), named));
        assertEquals(0, TransferParityTest.move(source, List.of(target.inventory), false));
        assertEquals(3, target.inventory.bulkCalls);
        assertEquals(4, source.getAmountAsInt(2));
    }

    @Test
    void acceptedMutationInvalidatesEarlierRejections() throws Exception {
        var target = new SophisticatedInventoryFixture(2, 64);
        target.inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 64));
        target.inventory.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 63));
        var source = new ItemStacksResourceHandler(NonNullList.of(ItemStack.EMPTY,
                new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.GOLD_INGOT, 1),
                new ItemStack(Items.IRON_INGOT, 4)));
        assertEquals(1, TransferParityTest.move(source, List.of(target.inventory), false));
        assertEquals(3, target.inventory.bulkCalls);
        assertEquals(64, target.inventory.getAmountAsInt(1));
        assertEquals(4, source.getAmountAsInt(0));
        assertEquals(4, source.getAmountAsInt(2));
    }

    @Test
    void rejectionCacheDoesNotOutliveMove() throws Exception {
        var target = new SophisticatedInventoryFixture(1, 64);
        target.inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 64));
        var source = TransferParityTest.inventory(4, 4);
        assertEquals(0, TransferParityTest.move(source, List.of(target.inventory), false));
        target.inventory.setStackInSlot(0, ItemStack.EMPTY);
        assertEquals(8, TransferParityTest.move(source, List.of(target.inventory), false));
        assertEquals(8, target.inventory.getAmountAsInt(0));
    }

    @Test
    void partialAcceptanceExtractsOnlyAcceptedQuantity() throws Exception {
        var target = new SophisticatedInventoryFixture(1, 64);
        target.inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 61));
        var source = TransferParityTest.inventory(8);
        assertEquals(3, TransferParityTest.move(source, List.of(target.inventory), false));
        assertEquals(64, target.inventory.getAmountAsInt(0));
        assertEquals(5, source.getAmountAsInt(0));
    }
}
