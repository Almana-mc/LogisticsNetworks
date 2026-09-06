package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.VirtualFilterType;
import me.almana.logisticsnetworks.logic.TransferCapabilityCache;
import me.almana.logisticsnetworks.logic.TransferEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.ExecutionException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SnapshotsTest extends SnapshotFixture {
    @Test
    void identitySharedAcrossNodesAndSideLabelsCountsOnce() {
        var level = level();
        var live = inventory(8);
        var table = new ItemEndpointTable();
        var budget = new Snapshots.OccupiedSlotBudget(1);
        int first = table.capture(node(level, 1, live), Direction.UP, live, budget);
        int second = table.capture(node(level, 2, live), Direction.DOWN, live, budget, true);
        assertEquals(first, second);
        assertEquals(1, table.endpoints().size());
        assertNotNull(table.endpoints().getFirst().bulkSlotLimits());
    }

    @Test
    void stablePhysicalSideSharesReacquiredWrapperButDifferentHandlersStayDistinct() {
        var level = level();
        var table = new ItemEndpointTable();
        var first = inventory(8);
        var second = inventory(8);
        var budget = new Snapshots.OccupiedSlotBudget(2);
        assertEquals(0, table.capture(node(level, 1, first), Direction.UP, first, budget));
        assertEquals(0, table.capture(node(level, 1, second), Direction.UP, second, budget));
        assertEquals(0, table.capture(node(level, 2, second), Direction.DOWN, second, budget));
        assertEquals(1, table.capture(node(level, 3, inventory(8)), Direction.UP, inventory(8), budget));
    }

    @Test
    void immutableFiltersAndIntentDescriptorsSurviveExternalMutation() {
        var filter = VirtualFilterType.SMALL.createStack();
        FilterItemData.setEntry(filter, 0, IRON.toStack(), PlannerDifferentialTest.provider());
        var filters = new ItemStack[]{filter};
        var snapshot = PlannerDifferentialTest.snapshot(inventory(8), List.of(inventory(0)), 8, false, filters, filters);
        var unit = snapshot.units().getFirst();
        FilterItemData.setEntryStock(filter, 0, 4);
        filters[0] = ItemStack.EMPTY;
        unit.exportFilters()[0].setCount(0);
        unit.targets().getFirst().importFilters()[0].setCount(0);
        assertFalse(unit.exportFilters()[0].isEmpty());
        assertFalse(unit.targets().getFirst().importFilters()[0].isEmpty());
        assertFalse(FilterItemData.hasAnyAmountEntries(unit.exportFilters()[0], FilterItemData.createReadCache()));
        var stack = IRON.toStack();
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("original"));
        boolean[] mask = {false, true};
        var move = new TransferPlan.MoveIntent(0, 0, ItemResource.of(stack), 8, mask);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("changed"));
        mask[0] = true;
        move.targetSlotMask()[1] = false;
        assertArrayEquals(new boolean[]{false, true}, move.targetSlotMask());
        assertEquals("original", move.resource().toStack().getHoverName().getString());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.units().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.endpoints().clear());
    }

    @Test
    void workerCannotCaptureAndServerCannotPlan() throws Exception {
        var live = spy(inventory(8));
        var snapshot = PlannerDifferentialTest.snapshot(live, List.of(inventory(0)), 8, false,
                PlannerDifferentialTest.NONE, PlannerDifferentialTest.NONE);
        clearInvocations(live);
        assertThrows(IllegalStateException.class, () -> NetworkPlanner.plan(snapshot));
        var failure = assertThrows(ExecutionException.class,
                () -> PlannerDifferentialTest.worker(() -> Snapshots.captureItems(live)));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        PlannerDifferentialTest.worker(() -> NetworkPlanner.plan(snapshot));
        verifyNoInteractions(live);
    }

    @Test
    void generationAdvancesOnEveryCacheInvalidationOnly() {
        var network = new LogisticsNetwork(UUID.randomUUID());
        assertEquals(0, network.getGeneration());
        network.markCacheDirty();
        network.markCacheDirty();
        network.markDirty();
        network.clearCacheDirty();
        assertEquals(2, network.getGeneration());
    }

    @Test
    void capturePreservesChannelOrderModeChangesAndServerOwnedState() throws Exception {
        var fixture = captureFixture();
        try (var engine = mockStatic(TransferEngine.class, CALLS_REAL_METHODS)) {
            engine.when(() -> TransferEngine.prepareNetwork(fixture.network, fixture.server)).thenReturn(fixture.context);
            var first = Snapshots.captureNetwork(fixture.network, fixture.server, 11, 1).snapshot();
            assertEquals(List.of(0, 1), first.units().stream().map(NetworkSnapshot.ChannelUnit::channelIndex).toList());
            assertEquals(2, first.endpoints().size());
            assertFalse(first.units().getFirst().roundRobin());
            fixture.export.setDistributionMode(DistributionMode.ROUND_ROBIN);
            fixture.network.markCacheDirty();
            var second = Snapshots.captureNetwork(fixture.network, fixture.server, 12, 1).snapshot();
            assertTrue(second.units().getFirst().roundRobin());
            assertFalse(first.units().getFirst().roundRobin());
            assertEquals(first.generation() + 1, second.generation());
            clearInvocations(fixture.source, fixture.target, fixture.level);
            assertFalse(PlannerDifferentialTest.worker(() -> NetworkPlanner.plan(second)).channels().isEmpty());
            verifyNoInteractions(fixture.source, fixture.target, fixture.level);
        }
    }

    @Test
    void captureRetainsPriorityAndDistancePreferenceOrdering() {
        var fixture = captureFixture();
        var near = node(fixture.level, 3, inventory(0));
        when(near.distanceToSqr(anyDouble(), anyDouble(), anyDouble())).thenReturn(4.0);
        when(fixture.target.distanceToSqr(anyDouble(), anyDouble(), anyDouble())).thenReturn(100.0);
        var original = fixture.context.itemImports()[0].getFirst();
        fixture.context.itemImports()[0] = List.of(original,
                new TransferEngine.ImportTarget(near, original.channel(), 0));
        try (var engine = mockStatic(TransferEngine.class, CALLS_REAL_METHODS)) {
            engine.when(() -> TransferEngine.prepareNetwork(fixture.network, fixture.server)).thenReturn(fixture.context);
            for (var mode : List.of(DistributionMode.PRIORITY, DistributionMode.NEAREST_FIRST, DistributionMode.FARTHEST_FIRST)) {
                fixture.export.setDistributionMode(mode);
                var captured = Snapshots.captureNetwork(fixture.network, fixture.server, 1, 2).snapshot();
                var targets = captured.units().getFirst().targets();
                assertEquals(mode == DistributionMode.NEAREST_FIRST ? near.getUUID() : fixture.target.getUUID(),
                        targets.getFirst().nodeId());
                assertEquals(List.of(original.node().getUUID(), near.getUUID()),
                        fixture.context.itemImports()[0].stream().map(ref -> ref.node().getUUID()).toList());
            }
        }
    }

    @Test
    void captureReturnsExplicitUnavailableAndOccupiedLimitResults() {
        var fixture = captureFixture();
        try (var engine = mockStatic(TransferEngine.class, CALLS_REAL_METHODS)) {
            engine.when(() -> TransferEngine.prepareNetwork(fixture.network, fixture.server)).thenReturn(fixture.context);
            var exceeded = Snapshots.captureNetwork(fixture.network, fixture.server, 1, 0);
            assertEquals(Snapshots.CaptureStatus.OCCUPIED_SLOT_LIMIT_EXCEEDED, exceeded.status());
            assertNull(exceeded.snapshot());
            engine.when(() -> TransferEngine.prepareNetwork(fixture.network, fixture.server)).thenReturn(null);
            var unavailable = Snapshots.captureNetwork(fixture.network, fixture.server, 1, 2);
            assertEquals(Snapshots.CaptureStatus.UNAVAILABLE, unavailable.status());
            assertNull(unavailable.snapshot());
        }
    }

    @Test
    void cooldownWakeRequiresImportTargets() {
        assertEquals(3, Snapshots.earlierItemWakeDelta(8, 3));
        assertEquals(8, Snapshots.earlierItemWakeDelta(8, 0));
        assertEquals(Long.MAX_VALUE, Snapshots.itemWakeDelta(Long.MAX_VALUE, List.of(), 3));
    }

    private record CaptureFixture(LogisticsNetwork network, MinecraftServer server, ServerLevel level,
            LogisticsNodeEntity source, LogisticsNodeEntity target, ChannelData export,
            TransferEngine.NetworkContext context) {}

    @SuppressWarnings("unchecked")
    private static CaptureFixture captureFixture() {
        var level = level();
        var source = node(level, 1, inventory(32));
        var target = node(level, 2, inventory(0));
        var exports = new ChannelData(true);
        exports.setMode(ChannelMode.EXPORT);
        var imports = new ChannelData(true);
        when(source.getChannel(0)).thenReturn(exports);
        when(source.getChannel(1)).thenReturn(exports);
        List<TransferEngine.ImportTarget>[] targets = new List[9];
        Arrays.fill(targets, List.of());
        targets[0] = List.of(new TransferEngine.ImportTarget(target, imports, 0));
        targets[1] = List.of(new TransferEngine.ImportTarget(target, imports, 1));
        var server = mock(MinecraftServer.class);
        when(server.overworld()).thenReturn(level);
        var context = new TransferEngine.NetworkContext(List.of(source), Map.of(), targets, Map.of(), Map.of());
        return new CaptureFixture(new LogisticsNetwork(UUID.randomUUID()), server, level, source, target, exports, context);
    }

    private static ServerLevel level() {
        var level = mock(ServerLevel.class);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.getGameTime()).thenReturn(100L);
        when(level.isLoaded(any())).thenReturn(true);
        when(level.getBlockState(any())).thenReturn(Blocks.STONE.defaultBlockState());
        when(level.registryAccess()).thenReturn(PlannerDifferentialTest.provider());
        return level;
    }

    private static LogisticsNodeEntity node(ServerLevel level, int x, ResourceHandler<ItemResource> handler) {
        var node = mock(LogisticsNodeEntity.class);
        when(node.level()).thenReturn(level);
        when(node.getAttachedPos()).thenReturn(new BlockPos(x, 0, 0));
        when(node.getUUID()).thenReturn(UUID.randomUUID());
        when(node.isValidNode()).thenReturn(true);
        var caps = mock(TransferCapabilityCache.class);
        when(caps.findItemHandler(any())).thenReturn(handler);
        when(node.capabilities()).thenReturn(caps);
        return node;
    }
}
