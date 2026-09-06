package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.data.FilterMode;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.VirtualFilterType;
import me.almana.logisticsnetworks.logic.FilterLogic;
import me.almana.logisticsnetworks.logic.TransferAmountRules;
import me.almana.logisticsnetworks.logic.TransferEngine;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import static org.junit.jupiter.api.Assertions.*;

class PlannerDifferentialTest extends SnapshotFixture {
    static final ItemStack[] NONE = new ItemStack[0];
    static RegistryAccess provider() { return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY); }

    @Test
    void exactParityAcrossModesAmountsComponentsAndSourceSlots() throws Exception {
        for (boolean robin : new boolean[]{false, true}) {
            for (int limit : new int[]{7, 64, 256}) {
                for (int[] counts : new int[][]{{64}, {2, 6}, {128, 0, 32}}) {
                    var source = inventory(counts);
                    var special = new ItemStack(Items.DIAMOND, 9);
                    special.set(DataComponents.CUSTOM_NAME, Component.literal("owned metadata"));
                    source.set(0, ItemResource.of(special), special.getCount());
                    compare(source, List.of(inventory(0, 60), inventory(63, 0), inventory(0, 0)),
                            limit, robin, NONE, NONE);
                }
            }
        }
    }

    @Test
    void sixtyFourDividesThirteenThirteenThirteenThirteenTwelve() throws Exception {
        var targets = List.of(inventory(0), inventory(0), inventory(0), inventory(0), inventory(0));
        var plan = compare(inventory(64), targets, 64, true, NONE, NONE);
        int[] moved = new int[5];
        for (var move : plan.moves()) moved[move.targetIndex()] += move.amount();
        assertArrayEquals(new int[]{13, 13, 13, 13, 12}, moved);
    }

    @Test
    void overstackedSourceDrainsThroughBoundedExtractions() throws Exception {
        for (boolean robin : new boolean[]{false, true}) {
            var plan = compare(inventory(128), List.of(inventory(0, 0)), 256, robin, NONE, NONE);
            assertEquals(128, plan.moves().stream().mapToInt(TransferPlan.MoveIntent::amount).sum());
            assertTrue(plan.moves().stream().allMatch(move -> move.amount() <= 64));
        }
    }

    @Test
    void priorityBreakAndRoundRobinContinueAcrossSlots() throws Exception {
        for (boolean robin : new boolean[]{false, true}) {
            var targets = List.of(inventory(0), inventory(0));
            var plan = compare(inventory(2, 2, 4), targets, 8, robin, NONE, NONE);
            int[] moved = new int[2];
            for (var move : plan.moves()) moved[move.targetIndex()] += move.amount();
            assertArrayEquals(robin ? new int[]{4, 4} : new int[]{6, 2}, moved);
        }
    }

    @Test
    void slotMappingsStockAndBatchRemainExact() throws Exception {
        var exports = VirtualFilterType.SMALL.createStack();
        var imports = VirtualFilterType.SMALL.createStack();
        FilterItemData.setEntry(exports, 0, IRON.toStack(), provider());
        FilterItemData.setEntryStock(exports, 0, 3);
        FilterItemData.setEntryBatch(exports, 0, 5);
        FilterItemData.setEntrySlotMapping(exports, 0, new int[]{1});
        FilterItemData.setEntry(imports, 0, IRON.toStack(), provider());
        FilterItemData.setEntryStock(imports, 0, 9);
        FilterItemData.setEntrySlotMapping(imports, 0, new int[]{1});
        for (boolean robin : new boolean[]{false, true}) {
            var plan = compare(inventory(16, 16), List.of(inventory(0, 7), inventory(0, 0)),
                    32, robin, new ItemStack[]{exports}, new ItemStack[]{imports});
            assertFalse(plan.moves().isEmpty());
            for (var move : plan.moves()) {
                assertEquals(1, move.sourceSlot());
                assertArrayEquals(new boolean[]{false, true}, move.targetSlotMask());
            }
        }
    }

    @Test
    void sharedEndpointAcrossChannelsConservesCapacity() throws Exception {
        var source = inventory(128);
        var target = inventory(0);
        var snapshot = snapshot(source, List.of(target), 64, false, NONE, NONE);
        var first = snapshot.units().getFirst();
        var second = new NetworkSnapshot.ChannelUnit(first.sourceNodeId(), 1, 64, NONE,
                FilterMode.MATCH_ALL, 0, false, first.targets());
        var shared = new NetworkSnapshot(snapshot.networkId(), 4, 12, 100, 7, provider(),
                snapshot.endpoints(), List.of(first, second));
        var plan = worker(() -> NetworkPlanner.plan(shared));
        assertEquals(64, plan.channels().getFirst().moves().stream().mapToInt(TransferPlan.MoveIntent::amount).sum());
        assertTrue(plan.channels().get(1).moves().isEmpty());
        assertEquals(4, plan.generation());
        assertEquals(12, plan.runtimeId());
        assertEquals(7, plan.itemWakeDelta());
        assertEquals(plan, worker(() -> NetworkPlanner.plan(shared)));
    }

    @Test
    void differentialAssertionsRejectCorruptIntents() throws Exception {
        var snapshot = snapshot(inventory(8), List.of(inventory(0), inventory(0)), 8, false, NONE, NONE);
        var plan = worker(() -> NetworkPlanner.plan(snapshot)).channels().getFirst();
        var move = plan.moves().getFirst();
        for (var corrupt : List.of(
                new TransferPlan.MoveIntent(0, 0, ItemResource.of(Items.DIAMOND), 8, null),
                new TransferPlan.MoveIntent(0, 0, IRON, 9, null),
                new TransferPlan.MoveIntent(0, 0, IRON, 8, new boolean[]{false}),
                new TransferPlan.MoveIntent(0, 0, IRON, 8, new boolean[]{true, false}))) {
            assertThrows(AssertionError.class, () -> replay(inventory(8), List.of(inventory(0), inventory(0)), List.of(corrupt)));
        }
        var renamed = IRON.toStack();
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("wrong"));
        assertThrows(AssertionError.class, () -> replay(inventory(8), List.of(inventory(0)),
                List.of(new TransferPlan.MoveIntent(0, 0, ItemResource.of(renamed), 8, null))));
        var targets = List.of(inventory(0), inventory(0));
        replay(inventory(8), targets, List.of(new TransferPlan.MoveIntent(0, 1, move.resource(), 8, null)));
        assertThrows(AssertionError.class, () -> assertEquals(8, targets.getFirst().getAmountAsInt(0)));
    }

    static TransferPlan.ChannelMoves compare(ItemStacksResourceHandler source,
            List<ItemStacksResourceHandler> targets, int limit, boolean robin,
            ItemStack[] exports, ItemStack[] imports) throws Exception {
        var snapshot = snapshot(source, targets, limit, robin, exports, imports);
        var copiedSource = copy(source);
        var copiedTargets = targets.stream().map(PlannerDifferentialTest::copy).toList();
        var cache = FilterItemData.createReadCache();
        var engineTargets = targets.stream().map(target -> new TransferEngine.ItemTransferTarget(target, imports,
                FilterMode.MATCH_ALL, TransferAmountRules.collect(exports, imports, cache),
                FilterLogic.hasConfiguredItemNbtFilter(imports, cache), null,
                FilterLogic.hasConfiguredSlotMapping(imports, cache))).toList();
        int moved = TransferEngine.executeMove(source, engineTargets, limit, exports, FilterMode.MATCH_ALL,
                null, provider(), robin, cache, null);
        var plan = worker(() -> NetworkPlanner.plan(snapshot)).channels().getFirst();
        assertEquals(moved, plan.moves().stream().mapToInt(TransferPlan.MoveIntent::amount).sum());
        replay(copiedSource, copiedTargets, plan.moves());
        assertInventory(source, copiedSource);
        for (int i = 0; i < targets.size(); i++) assertInventory(targets.get(i), copiedTargets.get(i));
        return plan;
    }

    static NetworkSnapshot snapshot(ItemStacksResourceHandler source, List<ItemStacksResourceHandler> targets,
            int limit, boolean robin, ItemStack[] exports, ItemStack[] imports) {
        var endpoints = new ArrayList<NetworkSnapshot.ItemEndpoint>();
        endpoints.add(Snapshots.captureItems(source));
        var refs = new ArrayList<NetworkSnapshot.TargetUnit>();
        for (var target : targets) {
            refs.add(new NetworkSnapshot.TargetUnit(UUID.randomUUID(), 0, imports, FilterMode.MATCH_ALL,
                    FilterLogic.hasConfiguredSlotMapping(imports, FilterItemData.createReadCache()), false, endpoints.size()));
            endpoints.add(Snapshots.captureItems(target));
        }
        var unit = new NetworkSnapshot.ChannelUnit(UUID.randomUUID(), 0, limit, exports, FilterMode.MATCH_ALL, 0, robin, refs);
        return new NetworkSnapshot(UUID.randomUUID(), 0, 0, 0, Long.MAX_VALUE, provider(), endpoints, List.of(unit));
    }

    static void replay(ResourceHandler<ItemResource> source, List<? extends ResourceHandler<ItemResource>> targets,
            List<TransferPlan.MoveIntent> moves) {
        for (var move : moves) {
            assertTrue(move.sourceSlot() >= 0 && move.sourceSlot() < source.size());
            assertTrue(move.targetIndex() >= 0 && move.targetIndex() < targets.size());
            assertTrue(move.amount() > 0);
            assertEquals(source.getResource(move.sourceSlot()), move.resource());
            var target = targets.get(move.targetIndex());
            var mask = move.targetSlotMask();
            if (mask != null) assertEquals(target.size(), mask.length);
            try (var tx = Transaction.openRoot()) {
                assertEquals(move.amount(), source.extract(move.sourceSlot(), move.resource(), move.amount(), tx));
                int remaining = move.amount();
                if (mask == null) remaining -= target.insert(move.resource(), remaining, tx);
                else for (int pass = 0; pass < 2; pass++) {
                    for (int slot = 0; slot < target.size() && remaining > 0; slot++) {
                        if (mask[slot] && target.getResource(slot).isEmpty() == (pass == 1))
                            remaining -= target.insert(slot, move.resource(), remaining, tx);
                    }
                }
                assertEquals(0, remaining);
                tx.commit();
            }
        }
    }

    static ItemStacksResourceHandler copy(ItemStacksResourceHandler source) {
        var copy = new ItemStacksResourceHandler(source.size());
        for (int i = 0; i < source.size(); i++) copy.set(i, source.getResource(i), source.getAmountAsInt(i));
        return copy;
    }

    static void assertInventory(ResourceHandler<ItemResource> expected, ResourceHandler<ItemResource> actual) {
        assertEquals(expected.size(), actual.size());
        for (int slot = 0; slot < expected.size(); slot++) {
            assertEquals(expected.getResource(slot), actual.getResource(slot), "resource in slot " + slot);
            assertEquals(expected.getAmountAsInt(slot), actual.getAmountAsInt(slot), "amount in slot " + slot);
        }
    }

    static <T> T worker(java.util.concurrent.Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        new Thread(task, "planner-test").start();
        return task.get();
    }
}
