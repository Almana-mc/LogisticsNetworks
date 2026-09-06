package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.logic.NetworkScheduler;
import me.almana.logisticsnetworks.logic.TransferEngine;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchedulerPipelineTest extends SnapshotFixture {
    boolean async;
    boolean ticking;
    int workers;

    @BeforeEach
    void setupRuntime() {
        async = Config.asyncPlanning;
        ticking = Config.networkTickingEnabled;
        workers = Config.asyncWorkerThreads;
        Config.asyncPlanning = true;
        Config.networkTickingEnabled = true;
        Config.asyncWorkerThreads = 1;
        AsyncTransferRuntime.stop();
    }

    @AfterEach
    void cleanupRuntime() {
        AsyncTransferRuntime.stop();
        Config.asyncPlanning = async;
        Config.networkTickingEnabled = ticking;
        Config.asyncWorkerThreads = workers;
    }

    @Test
    void schedulerCommitsItemsAndNonItemsOnceAndRetainsTopologyCadence() throws Exception {
        var items = inventory(20);
        var target = inventory(0);
        var energy = new SimpleEnergyHandler(100, 100, 100, 100);
        var receiver = new SimpleEnergyHandler(100);
        try (var f = new CommitFixture(items, target)) {
            var registry = install(f);
            var export = energy(f, energy, receiver);
            pre(f);
            assertEquals(0, target.getAmountAsInt(0));
            awaitCompleted();
            post(f);
            assertEquals(8, target.getAmountAsInt(0));
            assertEquals(12, items.getAmountAsInt(0));
            assertEquals(8, receiver.getAmountAsLong());
            assertEquals(8, export.getTelemetry().drainFlow());
            assertEquals(8, f.export.getTelemetry().drainFlow());
            f.engine.verify(() -> TransferEngine.processSynchronousNonItems(f.network, f.server), times(1));
            f.engine.verify(() -> TransferEngine.processNetwork(f.network, f.server), never());
            verify(f.server, times(1)).getPlayerList();
            when(f.level.getGameTime()).thenReturn(101L);
            post(f);
            verify(f.server, times(1)).getPlayerList();
        }
    }

    @Test
    void disablingBetweenCaptureAndCommitStopsAllTransfersAndResumesRetainedWork() throws Exception {
        var items = inventory(20);
        var target = inventory(0);
        var energy = new SimpleEnergyHandler(100, 100, 100, 100);
        var receiver = new SimpleEnergyHandler(100);
        try (var f = new CommitFixture(items, target)) {
            var registry = install(f);
            energy(f, energy, receiver);
            pre(f);
            var first = AsyncTransferRuntime.get();
            awaitCompleted();
            registry.wakeNetwork(f.network.getId());
            Config.networkTickingEnabled = false;
            post(f);
            pre(f);
            assertNull(AsyncTransferRuntime.get());
            assertNull(first.pollCompleted());
            assertEquals(20, items.getAmountAsInt(0));
            assertEquals(0, target.getAmountAsInt(0));
            assertEquals(0, receiver.getAmountAsLong());
            verify(f.server, never()).getPlayerList();
            assertDoesNotThrow(ThreadGuard::requireServerThread);
            Config.networkTickingEnabled = true;
            pre(f);
            assertNotEquals(first.runtimeId(), AsyncTransferRuntime.get().runtimeId());
            awaitCompleted();
            post(f);
            assertEquals(8, target.getAmountAsInt(0));
            assertEquals(8, receiver.getAmountAsLong());
        }
    }

    @Test
    void staleCompletionRetriesWithoutMovingItemsOrRunningNonItems() throws Exception {
        var target = inventory(0);
        try (var f = new CommitFixture(inventory(20), target)) {
            var registry = install(f);
            pre(f);
            awaitCompleted();
            registry.invalidateNetwork(f.network.getId());
            post(f);
            assertEquals(0, target.getAmountAsInt(0));
            f.engine.verify(() -> TransferEngine.processSynchronousNonItems(f.network, f.server), never());
            awaitCompleted();
            post(f);
            assertEquals(8, target.getAmountAsInt(0));
        }
    }

    @Test
    void emptyAttemptRetainsBackoffAndDoesNotCreateAnotherImmediateItemAttempt() throws Exception {
        try (var f = new CommitFixture(inventory(0), inventory(0))) {
            install(f);
            pre(f);
            awaitCompleted();
            post(f);
            assertEquals(100, f.lastExecution[0]);
            verify(f.source).setBackoffTicks(eq(0), floatThat(value -> value > 0));
            assertNull(AsyncTransferRuntime.get().pollCompleted());
        }
    }

    @Test
    void shutdownDrainsRuntimeAndClearsOnlyAtServerLifecycleBoundary() throws Exception {
        try (var f = new CommitFixture(inventory(20), inventory(0))) {
            var registry = install(f);
            pre(f);
            var first = AsyncTransferRuntime.get();
            awaitCompleted();
            LogisticsNetworks.onServerStopping(new ServerStoppingEvent(f.server));
            assertNull(AsyncTransferRuntime.get());
            assertNull(first.pollCompleted());
            assertThrows(IllegalStateException.class, ThreadGuard::requireServerThread);
            ThreadGuard.markServerThread();
            registry.refreshAsyncPlanning();
            registry.dispatchDirty(f.server);
            assertNull(AsyncTransferRuntime.get().pollCompleted());
        }
    }

    @Test
    void captureInvalidatedBeforeFailureDoesNotSpendFailureBudget() throws Exception {
        try (var f = new CommitFixture(inventory(20), inventory(0))) {
            var registry = install(f);
            f.engine.when(() -> TransferEngine.prepareNetwork(f.network, f.server)).thenAnswer(call -> {
                registry.invalidateNetwork(f.network.getId());
                throw new IllegalStateException("capture invalidated");
            });
            for (int attempt = 0; attempt < 4; attempt++) pre(f);
            f.engine.verify(() -> TransferEngine.prepareNetwork(f.network, f.server), times(4));
        }
    }

    @Test
    void occupiedLimitChoosesOneSynchronousRecoveryAfterCapture() throws Exception {
        int limit = Config.asyncMaxOccupiedSlots;
        Config.asyncMaxOccupiedSlots = 1;
        var source = inventory(20);
        var target = inventory(1);
        try (var f = new CommitFixture(source, target)) {
            install(f);
            pre(f);
            assertEquals(1, target.getAmountAsInt(0));
            assertNull(AsyncTransferRuntime.get().pollCompleted());
            post(f);
            assertEquals(9, target.getAmountAsInt(0));
            assertEquals(12, source.getAmountAsInt(0));
            f.engine.verify(() -> TransferEngine.processNetwork(f.network, f.server), times(1));
            f.engine.verify(() -> TransferEngine.processSynchronousNonItems(f.network, f.server), never());
        } finally {
            Config.asyncMaxOccupiedSlots = limit;
        }
    }

    @Test
    void blockedRedstoneCreatesNoCooldownWakeAndSignalWakePreservesGeneration() throws Exception {
        var target = inventory(0);
        try (var f = new CommitFixture(inventory(20), target)) {
            var registry = install(f);
            f.export.setRedstoneMode(RedstoneMode.HIGH);
            pre(f);
            awaitCompleted();
            post(f);
            when(f.level.getGameTime()).thenReturn(200L);
            pre(f);
            assertNull(AsyncTransferRuntime.get().pollCompleted());
            assertEquals(0, target.getAmountAsInt(0));
            when(f.level.getBestNeighborSignal(f.source.getAttachedPos())).thenReturn(15);
            var active = new TransferEngine.NetworkContext(f.context.sortedNodes(),
                    Map.of(f.source.getUUID(), 15), f.context.itemImports(), f.context.dimensionalCache(), f.context.tierCache());
            f.engine.when(() -> TransferEngine.prepareNetwork(f.network, f.server)).thenReturn(active);
            long generation = f.network.getGeneration();
            registry.wakeNetwork(f.network.getId());
            assertEquals(generation, f.network.getGeneration());
            pre(f);
            awaitCompleted();
            post(f);
            assertEquals(8, target.getAmountAsInt(0));
        }
    }

    @Test
    void unloadedDestinationPreventsCommitAndRecoveredReplacementResumes() throws Exception {
        var source = inventory(20);
        var target = inventory(0);
        try (var f = new CommitFixture(source, target)) {
            var registry = install(f);
            pre(f);
            awaitCompleted();
            when(f.level.isLoaded(f.targets.getFirst().getAttachedPos())).thenReturn(false);
            post(f);
            assertEquals(0, target.getAmountAsInt(0));
            assertEquals(20, source.getAmountAsInt(0));
            var replacement = inventory(0);
            when(f.targets.getFirst().capabilities().findItemHandler(any())).thenReturn(replacement);
            when(f.level.isLoaded(f.targets.getFirst().getAttachedPos())).thenReturn(true);
            when(f.level.getGameTime()).thenReturn(140L);
            registry.wakeNetwork(f.network.getId());
            pre(f);
            awaitCompleted();
            post(f);
            assertEquals(8, replacement.getAmountAsInt(0));
            assertEquals(0, target.getAmountAsInt(0));
        }
    }

    @SuppressWarnings("unchecked")
    private static NetworkRegistry install(CommitFixture f) throws Exception {
        var registry = spy(new NetworkRegistry());
        var telemetry = mock(me.almana.logisticsnetworks.logic.TelemetryManager.class);
        when(telemetry.isActive(f.network.getId())).thenReturn(true);
        doReturn(telemetry).when(registry).getTelemetryManager();
        var players = mock(net.minecraft.server.players.PlayerList.class);
        when(players.getPlayers()).thenReturn(List.of());
        when(f.server.getPlayerList()).thenReturn(players);
        var networks = NetworkRegistry.class.getDeclaredField("networks");
        networks.setAccessible(true);
        ((Map<UUID, LogisticsNetwork>) networks.get(registry)).put(f.network.getId(), f.network);
        f.registries.when(() -> NetworkRegistry.get(f.level)).thenReturn(registry);
        f.network.clearCacheDirty();
        doNothing().when(f.network).rebuildCache(registry);
        registry.wakeNetwork(f.network.getId());
        return registry;
    }

    @SuppressWarnings("unchecked")
    private static ChannelData energy(CommitFixture f, SimpleEnergyHandler source, SimpleEnergyHandler target) {
        var export = CommitFixture.channel(ChannelMode.EXPORT);
        export.setType(ChannelType.ENERGY);
        var input = CommitFixture.channel(ChannelMode.IMPORT);
        input.setType(ChannelType.ENERGY);
        when(f.source.getChannel(1)).thenReturn(export);
        when(f.targets.getFirst().getChannel(1)).thenReturn(input);
        when(f.source.capabilities().findEnergyHandler(any())).thenReturn(source);
        when(f.targets.getFirst().capabilities().findEnergyHandler(any())).thenReturn(target);
        List<NodeRef>[] refs = new List[9];
        Arrays.fill(refs, List.of());
        refs[1] = List.of(new NodeRef(f.targets.getFirst().getUUID(), new BlockPos(2, 0, 0), 0));
        when(f.network.getEnergyImports()).thenReturn(refs);
        return export;
    }

    private static void pre(CommitFixture f) {
        NetworkScheduler.onServerTickPre(new ServerTickEvent.Pre(() -> false, f.server));
    }

    private static void post(CommitFixture f) {
        NetworkScheduler.onServerTickPost(new ServerTickEvent.Post(() -> false, f.server));
    }

    private static void awaitCompleted() throws Exception {
        var field = AsyncTransferRuntime.class.getDeclaredField("completed");
        field.setAccessible(true);
        var queue = (Queue<?>) field.get(AsyncTransferRuntime.get());
        long start = System.nanoTime();
        while (queue.isEmpty() && System.nanoTime() - start < 5_000_000_000L) Thread.sleep(1);
        assertFalse(queue.isEmpty());
    }
}
