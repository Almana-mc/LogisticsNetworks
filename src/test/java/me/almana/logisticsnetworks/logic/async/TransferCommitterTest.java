package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.VirtualFilterType;
import me.almana.logisticsnetworks.logic.TransferEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransferCommitterTest extends SnapshotFixture {
    @Test
    void acceptsCapturedPlanAndRecordsActualTelemetryOnly() throws Exception {
        var source = inventory(20);
        var target = inventory(0);
        try (var f = new CommitFixture(source, target)) {
            var plan = f.plan();
            target.set(0, IRON, 61);
            var result = f.commit(plan);
            assertEquals(8, result.planned());
            assertEquals(3, result.committed());
            assertEquals(0, result.recovered());
            assertEquals(3, result.moved());
            assertEquals(3, f.export.getTelemetry().drainFlow());
            assertEquals(0, result.wakeDelta());
            assertEquals(100, f.lastExecution[0]);
            assertEquals(17, source.getAmountAsInt(0));
        }
    }

    @Test
    void recoversOnlyPlannedShortfallIntoCurrentlyResolvedTargets() throws Exception {
        var source = inventory(20);
        var first = inventory(0);
        var second = inventory(0);
        try (var f = new CommitFixture(source, first, second)) {
            var plan = f.plan();
            first.set(0, IRON, 61);
            var result = f.commit(plan);
            assertEquals(3, result.committed());
            assertEquals(5, result.recovered());
            assertEquals(8, result.moved());
            assertEquals(5, second.getAmountAsInt(0));
            assertEquals(12, source.getAmountAsInt(0));
            assertEquals(8, f.export.getTelemetry().drainFlow());
        }
    }

    @Test
    void staleRuntimeGenerationIdentityAndFailedPlansDoNotTouchWorld() {
        var network = new LogisticsNetwork(UUID.randomUUID());
        for (var plan : List.of(
                new TransferPlan(network.getId(), 0, 41, false, 3, List.of()),
                new TransferPlan(network.getId(), 1, 42, false, 3, List.of()),
                new TransferPlan(UUID.randomUUID(), 0, 42, false, 3, List.of()),
                new TransferPlan(network.getId(), 0, 42, true, 3, List.of()))) {
            assertEquals(0, TransferCommitter.commitItems(plan, network, null, 42).moved());
        }
        assertEquals(3, TransferCommitter.commitItems(new TransferPlan(network.getId(), 0, 42, false, 3, List.of()),
                network, null, 42).wakeDelta());
    }

    @Test
    void changedSourceBindingModeTypeLoadingAndMembershipCannotReplay() throws Exception {
        for (int mutation = 0; mutation < 7; mutation++) {
            var source = inventory(20);
            var target = inventory(0);
            try (var f = new CommitFixture(source, target)) {
                var plan = f.plan();
                switch (mutation) {
                    case 0 -> f.export.setIoDirection(Direction.DOWN);
                    case 1 -> when(f.source.getAttachedPos()).thenReturn(new BlockPos(9, 0, 0));
                    case 2 -> f.export.setDistributionMode(DistributionMode.ROUND_ROBIN);
                    case 3 -> f.export.setType(ChannelType.FLUID);
                    case 4 -> f.export.setMode(ChannelMode.IMPORT);
                    case 5 -> when(f.level.isLoaded(f.source.getAttachedPos())).thenReturn(false);
                    case 6 -> f.network.removeNode(f.source.getUUID());
                }
                assertEquals(0, f.commit(plan).moved(), "mutation " + mutation);
                assertEquals(20, source.getAmountAsInt(0));
                assertEquals(0, target.getAmountAsInt(0));
            }
        }
    }

    @Test
    void currentSourceAndTargetFiltersBlockStaleIntentsAndRecovery() throws Exception {
        for (boolean sourceFilter : List.of(true, false)) {
            var source = inventory(20);
            var target = inventory(0);
            try (var f = new CommitFixture(source, target)) {
                var plan = f.plan();
                var filter = VirtualFilterType.SMALL.createStack();
                FilterItemData.setEntry(filter, 0, new ItemStack(Items.GOLD_INGOT), PlannerDifferentialTest.provider());
                (sourceFilter ? f.export : f.imports.getFirst()).setFilterItem(0, filter);
                assertEquals(0, f.commit(plan).moved());
                assertEquals(20, source.getAmountAsInt(0));
                assertEquals(0, target.getAmountAsInt(0));
                assertEquals(20, f.commit(plan).wakeDelta());
            }
        }
    }

    @Test
    void changedMaskUsesCurrentMappedSlotDuringRecovery() throws Exception {
        var source = inventory(20);
        var target = inventory(0, 0);
        try (var f = new CommitFixture(source, target)) {
            var filter = VirtualFilterType.SMALL.createStack();
            FilterItemData.setEntry(filter, 0, IRON.toStack(), PlannerDifferentialTest.provider());
            FilterItemData.setEntrySlotMapping(filter, 0, new int[]{0});
            f.imports.getFirst().setFilterItem(0, filter);
            var plan = f.plan();
            FilterItemData.setEntrySlotMapping(filter, 0, new int[]{1});
            f.imports.getFirst().setFilterItem(0, filter);
            var result = f.commit(plan);
            assertEquals(0, result.committed());
            assertEquals(8, result.recovered());
            assertEquals(0, target.getAmountAsInt(0));
            assertEquals(8, target.getAmountAsInt(1));
        }
    }

    @Test
    void unavailableWrongModeAndSharedStorageTargetsCannotReceiveRecovery() throws Exception {
        for (int mutation = 0; mutation < 5; mutation++) {
            var source = inventory(20);
            var target = inventory(0);
            try (var f = new CommitFixture(source, target)) {
                var plan = f.plan();
                var node = f.targets.getFirst();
                switch (mutation) {
                    case 0 -> f.imports.getFirst().setEnabled(false);
                    case 1 -> f.imports.getFirst().setMode(ChannelMode.EXPORT);
                    case 2 -> when(f.level.isLoaded(node.getAttachedPos())).thenReturn(false);
                    case 3 -> doReturn(f.source.getAttachedPos()).when(node).getAttachedPos();
                    case 4 -> when(node.capabilities().findItemHandler(any())).thenReturn(source);
                }
                assertEquals(0, f.commit(plan).moved());
                assertEquals(20, source.getAmountAsInt(0));
                assertEquals(0, target.getAmountAsInt(0));
            }
        }
    }

    @Test
    void channelBatchReductionBoundsReplayedAndRecoveredTotal() throws Exception {
        var source = inventory(20);
        var target = inventory(0);
        try (var f = new CommitFixture(source, target)) {
            var plan = f.plan();
            f.export.setBatchSize(3);
            assertEquals(3, f.commit(plan).moved());
            assertEquals(17, source.getAmountAsInt(0));
        }
    }

    @Test
    void generationChangeDuringCommitStopsLaterIntentsAndRecovery() throws Exception {
        var source = inventory(8, 8);
        var first = spy(inventory(0));
        var second = inventory(0);
        try (var f = new CommitFixture(source, first, second)) {
            f.export.setBatchSize(16);
            var plan = f.plan();
            doAnswer(call -> {
                Object accepted = first.insert(0, call.getArgument(0), Math.min(4, (int) call.getArgument(1)), call.getArgument(2));
                f.network.markCacheDirty();
                return accepted;
            }).when(first).insert(any(ItemResource.class), anyInt(), any());
            assertEquals(4, f.commit(plan).moved());
            assertEquals(4, first.getAmountAsInt(0));
            assertEquals(0, second.getAmountAsInt(0));
            assertEquals(4, f.export.getTelemetry().drainFlow());
        }
    }

    @Test
    void emptyNominalPlanDoesNotPretendToDiscoverEveryLiveOverrideOpportunity() throws Exception {
        var source = inventory(20);
        var target = inventory(64);
        try (var f = new CommitFixture(source, target)) {
            var plan = f.plan();
            target.set(0, ItemResource.EMPTY, 0);
            var result = f.commit(plan);
            assertEquals(0, result.planned());
            assertEquals(0, result.moved());
            assertEquals(20, source.getAmountAsInt(0));
        }
    }

    @Test
    void recoverySharesRemainingBatchAcrossAliasesOfOneHandler() throws Exception {
        var source = inventory(16);
        var shared = inventory(4);
        var other = inventory(0);
        try (var f = new CommitFixture(source, shared, shared, other)) {
            f.export.setDistributionMode(DistributionMode.ROUND_ROBIN);
            f.export.setBatchSize(12);
            var filter = VirtualFilterType.SMALL.createStack();
            FilterItemData.setEntry(filter, 0, IRON.toStack(), PlannerDifferentialTest.provider());
            FilterItemData.setEntryBatch(filter, 0, 6);
            f.export.setFilterItem(0, filter);
            var plan = f.plan();
            var totals = new IdentityHashMap<net.neoforged.neoforge.transfer.ResourceHandler<ItemResource>,
                    Map<net.minecraft.world.item.Item, Integer>>();
            totals.put(shared, Map.of(Items.IRON_INGOT, 4));
            assertEquals(8, TransferCommitter.recoverItemChannel(plan.channels().getFirst(), f.network, f.server,
                    8, 4, Map.of(Items.IRON_INGOT, 4), totals));
            assertEquals(6, shared.getAmountAsInt(0));
            assertEquals(6, other.getAmountAsInt(0));
            assertEquals(8, source.getAmountAsInt(0));
        }
    }

    @Test
    void generationChangeDuringPartialRecoveryStopsLaterSlotsAndDestinations() throws Exception {
        for (boolean roundRobin : new boolean[]{false, true}) {
            var source = inventory(8, 8);
            var original = inventory(0);
            var recovery = spy(inventory(64));
            var later = spy(inventory(64));
            try (var f = new CommitFixture(source, original, recovery, later)) {
                f.export.setBatchSize(16);
                if (roundRobin) f.export.setDistributionMode(DistributionMode.ROUND_ROBIN);
                var plan = f.plan();
                original.set(0, IRON, 64);
                recovery.set(0, ItemResource.EMPTY, 0);
                later.set(0, ItemResource.EMPTY, 0);
                doAnswer(call -> {
                    int accepted = recovery.insert(0, call.getArgument(0),
                            Math.min(4, (int) call.getArgument(1)), call.getArgument(2));
                    if (accepted > 0) f.network.markCacheDirty();
                    return accepted;
                }).when(recovery).insert(any(ItemResource.class), anyInt(), any());
                var result = f.commit(plan);
                assertEquals(16, result.planned());
                assertEquals(0, result.committed());
                assertEquals(4, result.recovered());
                assertEquals(4, result.moved());
                assertEquals(4, recovery.getAmountAsInt(0));
                assertEquals(0, later.getAmountAsInt(0));
                assertEquals(4, source.getAmountAsInt(0));
                assertEquals(8, source.getAmountAsInt(1));
                assertEquals(4, f.export.getTelemetry().drainFlow());
                assertEquals(0, result.wakeDelta());
                verify(recovery, times(1)).insert(any(ItemResource.class), anyInt(), any());
                verify(later, never()).insert(any(ItemResource.class), anyInt(), any());
            }
        }
    }

    @Test
    void recoverySharesCurrentStockAcrossAliasesOfOneHandler() throws Exception {
        var source = inventory(16);
        var shared = inventory(4);
        var other = inventory(0);
        try (var f = new CommitFixture(source, shared, shared, other)) {
            f.export.setDistributionMode(DistributionMode.ROUND_ROBIN);
            var filter = VirtualFilterType.SMALL.createStack();
            FilterItemData.setEntry(filter, 0, IRON.toStack(), PlannerDifferentialTest.provider());
            FilterItemData.setEntryStock(filter, 0, 6);
            f.imports.get(0).setFilterItem(0, filter);
            f.imports.get(1).setFilterItem(0, filter);
            var plan = f.plan();
            assertEquals(8, TransferCommitter.recoverItemChannel(plan.channels().getFirst(), f.network, f.server,
                    8, 0, Map.of(), new IdentityHashMap<>()));
            assertEquals(6, shared.getAmountAsInt(0));
            assertEquals(6, other.getAmountAsInt(0));
            assertEquals(8, source.getAmountAsInt(0));
        }
    }

    @Test
    void priorityRecoveryCannotReuseCommittedItemBatchForAnotherItemsShortfall() throws Exception {
        var source = inventory(20, 0);
        source.set(1, ItemResource.of(Items.GOLD_INGOT), 5);
        var target = inventory(0, 0);
        try (var f = new CommitFixture(source, target)) {
            f.export.setBatchSize(10);
            var filter = VirtualFilterType.SMALL.createStack();
            FilterItemData.setEntry(filter, 0, IRON.toStack(), PlannerDifferentialTest.provider());
            FilterItemData.setEntry(filter, 1, new ItemStack(Items.GOLD_INGOT), PlannerDifferentialTest.provider());
            FilterItemData.setEntryBatch(filter, 0, 5);
            FilterItemData.setEntryBatch(filter, 1, 5);
            f.export.setFilterItem(0, filter);
            var plan = f.plan();
            source.set(1, IRON, 5);
            var result = f.commit(plan);
            assertEquals(10, result.planned());
            assertEquals(5, result.committed());
            assertEquals(0, result.recovered());
            assertEquals(5, target.getAmountAsInt(0));
            assertEquals(0, target.getAmountAsInt(1));
        }
    }

    @Test
    void malformedChannelAndTargetIndexesDoNotCreateRecoveryWork() throws Exception {
        var source = inventory(20);
        var target = inventory(0);
        try (var f = new CommitFixture(source, target)) {
            var plan = f.plan();
            var original = plan.channels().getFirst();
            var invalid = new TransferPlan.ChannelMoves(original.sourceNodeId(), 0, original.targets(),
                    List.of(new TransferPlan.MoveIntent(0, -1, IRON, Integer.MAX_VALUE, null)),
                    original.sourceBinding(), original.distributionMode());
            var result = f.commit(replace(plan, List.of(invalid)));
            assertEquals(0, result.planned());
            assertEquals(0, result.moved());
            assertEquals(20, source.getAmountAsInt(0));
        }
    }

    @Test
    void duplicateChannelsCannotMultiplyBatchAllowance() throws Exception {
        var source = inventory(20);
        var target = inventory(0);
        try (var f = new CommitFixture(source, target)) {
            var plan = f.plan();
            assertEquals(8, f.commit(replace(plan, List.of(plan.channels().getFirst(), plan.channels().getFirst()))).moved());
            assertEquals(12, source.getAmountAsInt(0));
        }
    }

    @Test
    void roundRobinRecoveryRetainsEachDestinationBatchAndUnusedDestinationAllowance() throws Exception {
        var source = inventory(40);
        var first = spy(inventory(0));
        var second = inventory(0);
        var third = inventory(0);
        var fourth = inventory(64);
        doAnswer(call -> {
            int request = call.getArgument(1);
            if (second.getAmountAsInt(0) == 0) request = Math.min(request, Math.max(0, 4 - first.getAmountAsInt(0)));
            return first.insert(0, call.getArgument(0), request, call.getArgument(2));
        }).when(first).insert(any(ItemResource.class), anyInt(), any());
        try (var f = new CommitFixture(source, first, second, third, fourth)) {
            f.export.setDistributionMode(DistributionMode.ROUND_ROBIN);
            f.export.setBatchSize(18);
            var filter = VirtualFilterType.SMALL.createStack();
            FilterItemData.setEntry(filter, 0, IRON.toStack(), PlannerDifferentialTest.provider());
            FilterItemData.setEntryBatch(filter, 0, 6);
            f.export.setFilterItem(0, filter);
            var plan = f.plan();
            third.set(0, IRON, 64);
            fourth.set(0, ItemResource.EMPTY, 0);
            var result = f.commit(plan);
            assertEquals(18, result.planned());
            assertEquals(11, result.committed());
            assertEquals(7, result.recovered());
            assertEquals(6, first.getAmountAsInt(0));
            assertEquals(6, second.getAmountAsInt(0));
            assertEquals(6, fourth.getAmountAsInt(0));
            assertEquals(22, source.getAmountAsInt(0));
        }
    }

    static TransferPlan replace(TransferPlan plan, List<TransferPlan.ChannelMoves> channels) {
        return new TransferPlan(plan.networkId(), plan.generation(), plan.runtimeId(), plan.failed(), plan.itemWakeDelta(), channels);
    }
}
