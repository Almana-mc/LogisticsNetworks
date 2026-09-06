package me.almana.logisticsnetworks.logic;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.p3pp3rf1y.sophisticatedcore.inventory.ItemStackKey;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IInsertResponseUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IOverflowResponseUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.stack.StackUpgradeItem;
import net.p3pp3rf1y.sophisticatedcore.upgrades.voiding.VoidType;
import net.p3pp3rf1y.sophisticatedcore.upgrades.voiding.VoidUpgradeItem;
import net.p3pp3rf1y.sophisticatedcore.upgrades.voiding.VoidUpgradeWrapper;
import net.p3pp3rf1y.sophisticatedstorage.init.ModItems;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SophisticatedTransactionTest {
    @BeforeAll
    static void bootstrap() {
        SophisticatedTransferTest.bootstrap();
    }

    @Test
    void abortedBulkAcceptanceRestoresTrackerPersistenceAndObservers() throws Exception {
        var target = new SophisticatedInventoryFixture(2, 64);
        target.inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 60));
        target.saves = 0;
        target.changes = 0;
        Set<ItemStackKey> observed = new HashSet<>(target.inventory.getTrackedStacks());
        target.inventory.registerTrackingListeners(observed::add, observed::remove, () -> {}, () -> {});
        try (var tx = Transaction.openRoot()) {
            assertTrue(SophisticatedBoundaryTest.insert(target.inventory, new ItemStack(Items.IRON_INGOT, 8), tx, null).isEmpty());
            assertEquals(64, target.inventory.getAmountAsInt(0));
            assertEquals(4, target.inventory.getAmountAsInt(1));
        }
        assertEquals(60, target.inventory.getAmountAsInt(0));
        assertEquals(0, target.inventory.getAmountAsInt(1));
        assertEquals(Set.of(0), target.inventory.getSlotTracker().getPartialSlots(ItemStackKey.of(new ItemStack(Items.IRON_INGOT))));
        assertEquals(Set.of(1), target.inventory.getSlotTracker().getEmptySlots());
        assertEquals(target.inventory.getTrackedStacks(), observed);
        assertEquals(60, target.contents.inventory().stacks().get(0).getCount());
        assertEquals(0, target.saves);
        assertEquals(0, target.changes);
        assertEquals(8, TransferParityTest.move(TransferParityTest.inventory(8), List.of(target.inventory), false));
        assertEquals(64, target.inventory.getAmountAsInt(0));
        assertEquals(4, target.inventory.getAmountAsInt(1));
    }

    @Test
    void nestedCommittedInsertStillRollsBackWithParent() throws Exception {
        var target = new SophisticatedInventoryFixture(1, 64);
        Set<ItemStackKey> observed = new HashSet<>();
        var empty = new java.util.concurrent.atomic.AtomicBoolean(true);
        target.inventory.registerTrackingListeners(observed::add, observed::remove,
                () -> empty.set(true), () -> empty.set(false));
        try (var root = Transaction.openRoot()) {
            try (var nested = Transaction.open(root)) {
                assertTrue(SophisticatedBoundaryTest.insert(target.inventory, new ItemStack(Items.GOLD_INGOT, 64), nested, null).isEmpty());
                nested.commit();
            }
            assertFalse(target.inventory.hasEmptySlots());
            assertFalse(empty.get());
            assertEquals(1, observed.size());
        }
        assertTrue(target.inventory.hasEmptySlots());
        assertTrue(target.inventory.getTrackedStacks().isEmpty());
        assertEquals(0, target.inventory.getAmountAsInt(0));
        assertEquals(0, target.saves);
        assertTrue(empty.get());
        assertTrue(observed.isEmpty());
    }

    @Test
    void changedSourceExtractionRollsBackCoreContentsAndTracker() throws Exception {
        var target = new SophisticatedInventoryFixture(1, 64);
        var source = changingSource();
        assertEquals(0, TransferParityTest.move(source, List.of(target.inventory), false));
        assertEquals(8, source.getAmountAsInt(0));
        assertEquals(0, target.inventory.getAmountAsInt(0));
        assertTrue(target.inventory.getTrackedStacks().isEmpty());
        assertTrue(target.inventory.hasEmptySlots());
        assertEquals(0, target.saves);
        assertEquals(0, target.changes);
    }

    @Test
    void registeredStackUpgradeAcceptsBeyondVanillaStackCapacity() throws Exception {
        var target = new SophisticatedInventoryFixture(1, 64);
        when(target.wrapper.getBaseStackSizeMultiplier()).thenReturn(1);
        var upgrade = StackUpgradeItem.TYPE.create(target.wrapper, new ItemStack(ModItems.STACK_UPGRADE_TIER_1.get()), stack -> {});
        when(target.upgrades.getTypeWrappers(StackUpgradeItem.TYPE)).thenReturn(List.of(upgrade));
        target.inventory.setBaseSlotLimit(StackUpgradeItem.getInventorySlotLimit(target.wrapper));
        assertEquals(128, target.inventory.getBaseSlotLimit());
        target.inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 125));
        var source = TransferParityTest.inventory(8);
        assertEquals(3, TransferParityTest.move(source, List.of(target.inventory), false));
        assertEquals(128, target.inventory.getAmountAsInt(0));
        assertEquals(5, source.getAmountAsInt(0));
        target.inventory.setBaseSlotLimit(256);
        assertEquals(5, TransferParityTest.move(source, List.of(target.inventory), false));
        assertEquals(133, target.inventory.getAmountAsInt(0));
    }

    @Test
    void registeredVoidOverflowAcceptsFullStorageWithoutSimulationMutation() throws Exception {
        var target = new SophisticatedInventoryFixture(1, 64);
        target.inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 64));
        var upgrade = addVoidUpgrade(target);
        upgrade.setVoidType(VoidType.STORAGE_OVERFLOW);
        var upgradeBefore = upgrade.getUpgradeStack().copy();
        target.saves = 0;
        target.changes = 0;
        try (var tx = Transaction.openRoot()) {
            assertTrue(SophisticatedBoundaryTest.insert(target.inventory, new ItemStack(Items.IRON_INGOT, 8), tx, null).isEmpty());
        }
        assertTrue(ItemStack.matches(upgradeBefore, upgrade.getUpgradeStack()));
        assertEquals(64, target.inventory.getAmountAsInt(0));
        assertEquals(0, target.saves);
        assertEquals(0, target.changes);
        var source = TransferParityTest.inventory(8);
        assertEquals(8, TransferParityTest.move(source, List.of(target.inventory), false));
        assertEquals(0, source.getAmountAsInt(0));
        assertEquals(64, target.inventory.getAmountAsInt(0));
    }

    @Test
    void voidAcceptanceCannotConsumeUnextractableSource() throws Exception {
        var target = new SophisticatedInventoryFixture(1, 64);
        var upgrade = addVoidUpgrade(target);
        upgrade.setVoidType(VoidType.ALWAYS);
        var source = changingSource();
        assertEquals(0, TransferParityTest.move(source, List.of(target.inventory), false));
        assertEquals(8, source.getAmountAsInt(0));
        assertEquals(0, target.inventory.getAmountAsInt(0));
        assertTrue(target.inventory.getTrackedStacks().isEmpty());
    }

    private static VoidUpgradeWrapper addVoidUpgrade(SophisticatedInventoryFixture target) {
        var upgrade = new VoidUpgradeWrapper(target.wrapper, new ItemStack(ModItems.VOID_UPGRADE.get()), stack -> {});
        upgrade.getFilterLogic().setAllowList(true);
        upgrade.getFilterLogic().getFilterHandler().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        when(target.upgrades.getWrappersThatImplementFromMainStorage(IInsertResponseUpgrade.class)).thenReturn(List.of(upgrade));
        when(target.upgrades.getWrappersThatImplementFromMainStorage(IOverflowResponseUpgrade.class)).thenReturn(List.of(upgrade));
        when(target.upgrades.getTypeWrappers(VoidUpgradeItem.TYPE)).thenReturn(List.of(upgrade));
        target.inventory.setBaseSlotLimit(target.inventory.getBaseSlotLimit());
        return upgrade;
    }

    private static net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler changingSource() {
        return new net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler(
                net.minecraft.core.NonNullList.of(ItemStack.EMPTY, new ItemStack(Items.IRON_INGOT, 8))) {
            private int attempts;

            @Override
            public int extract(int slot, ItemResource resource, int amount, TransactionContext transaction) {
                return ++attempts % 2 == 0 ? 0 : super.extract(slot, resource, amount, transaction);
            }
        };
    }
}
