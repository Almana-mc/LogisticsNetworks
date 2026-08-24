package me.almana.logisticsnetworks.data;

import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.logic.async.AsyncTransferRuntime;
import me.almana.logisticsnetworks.logic.async.NetworkSnapshot;
import me.almana.logisticsnetworks.logic.async.Snapshots;
import me.almana.logisticsnetworks.logic.async.TransferPlan;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkRegistryPipelineTest {

    private NetworkDispatcher dispatcher;
    private NetworkDispatchState state;
    private int previousWorkerThreads;

    @BeforeEach
    void setUp() {
        previousWorkerThreads = Config.asyncWorkerThreads;
        Config.asyncWorkerThreads = 1;
        AsyncTransferRuntime.stop();
        state = new NetworkDispatchState();
        dispatcher = new NetworkDispatcher(state);
    }

    @AfterEach
    void tearDown() {
        AsyncTransferRuntime.stop();
        Config.asyncWorkerThreads = previousWorkerThreads;
    }

    @Test
    void dirtyAgainWaitsForTheInFlightPlanToFinish() {
        UUID id = UUID.randomUUID();
        state.markDirty(id);

        assertTrue(state.beginDispatch(id));
        state.markDirty(id);
        assertFalse(state.beginDispatch(id));

        state.finishDispatch(id);

        assertTrue(state.beginDispatch(id));
    }

    @Test
    void inventoryWakePreservesGeneration() {
        NetworkRegistry registry = new NetworkRegistry();
        LogisticsNetwork network = registry.createNetwork();
        long generation = network.getGeneration();

        registry.wakeNetwork(network.getId());

        assertEquals(generation, network.getGeneration());
    }

    @Test
    void invalidationAdvancesGenerationOnce() {
        NetworkRegistry registry = new NetworkRegistry();
        LogisticsNetwork network = registry.createNetwork();
        long generation = network.getGeneration();

        registry.invalidateNetwork(network.getId());

        assertEquals(generation + 1L, network.getGeneration());
    }

    @Test
    void membershipChangesAdvanceGenerationOnce() {
        NetworkRegistry registry = new NetworkRegistry();
        LogisticsNetwork network = registry.createNetwork();
        registry.addNodeToNetwork(network.getId(), UUID.randomUUID());
        UUID nodeId = UUID.randomUUID();
        long generation = network.getGeneration();

        registry.addNodeToNetwork(network.getId(), nodeId);

        assertEquals(generation + 1L, network.getGeneration());
        assertTrue(network.getNodeUuids().contains(nodeId));
        assertTrue(isQueued(registry, network.getId()));

        registry.removeNodeFromNetwork(network.getId(), nodeId);

        assertEquals(generation + 2L, network.getGeneration());
        assertFalse(network.getNodeUuids().contains(nodeId));
        assertTrue(isQueued(registry, network.getId()));
    }

    @Test
    void rejectedSubmissionFallsBackSynchronously() {
        UUID id = UUID.randomUUID();
        state.markDirty(id);
        assertTrue(state.beginDispatch(id));

        state.fallbackSynchronously(id);

        assertTrue(state.takeSynchronousFallbacks().contains(id));
        state.markDirty(id);
        assertTrue(state.beginDispatch(id));
    }

    @Test
    void unavailableAndItemlessSnapshotsStaySynchronous() {
        NetworkSnapshot empty = new NetworkSnapshot(
                UUID.randomUUID(), 1L, 2L, 3L, RegistryAccess.EMPTY, List.of());
        NetworkSnapshot withItems = new NetworkSnapshot(
                UUID.randomUUID(), 1L, 2L, 3L, RegistryAccess.EMPTY,
                List.of(new NetworkSnapshot.ChannelUnit(
                        UUID.randomUUID(), 0, 1, new ItemStack[0], FilterMode.MATCH_ANY,
                        new NetworkSnapshot.ItemEndpoint(0, new int[0], new ItemStack[0], 64, new int[0]),
                        List.of())));

        assertSame(NetworkDispatcher.CaptureDisposition.SYNCHRONOUS,
                NetworkDispatcher.captureDisposition(Snapshots.NetworkCapture.unavailable()));
        assertSame(NetworkDispatcher.CaptureDisposition.SYNCHRONOUS,
                NetworkDispatcher.captureDisposition(Snapshots.NetworkCapture.captured(empty)));
        assertSame(NetworkDispatcher.CaptureDisposition.ASYNC,
                NetworkDispatcher.captureDisposition(Snapshots.NetworkCapture.captured(withItems)));
    }

    @Test
    void staleFailedAndWrongRuntimePlansRequireFallback() {
        LogisticsNetwork network = new LogisticsNetwork(UUID.randomUUID());
        long runtimeId = 7L;
        TransferPlan current = plan(network, runtimeId, false);
        TransferPlan stale = new TransferPlan(
                network.getId(), network.getGeneration() + 1L, runtimeId, false, List.of());
        TransferPlan wrongRuntime = new TransferPlan(
                network.getId(), network.getGeneration(), runtimeId + 1L, false, List.of());
        TransferPlan failed = plan(network, runtimeId, true);

        assertFalse(NetworkDispatcher.requiresSynchronousFallback(current, network, runtimeId));
        assertTrue(NetworkDispatcher.requiresSynchronousFallback(stale, network, runtimeId));
        assertTrue(NetworkDispatcher.requiresSynchronousFallback(wrongRuntime, network, runtimeId));
        assertTrue(NetworkDispatcher.requiresSynchronousFallback(failed, network, runtimeId));
    }

    @Test
    void disablingAndReenablingResetsAsyncStateWithoutLosingIds() {
        dispatcher.refreshAsyncMode(true);
        AsyncTransferRuntime first = AsyncTransferRuntime.get();
        assertNotNull(first);

        UUID inFlight = UUID.randomUUID();
        state.markDirty(inFlight);
        assertTrue(state.beginDispatch(inFlight));
        state.markDirty(inFlight);
        UUID fallback = UUID.randomUUID();
        state.markDirty(fallback);
        assertTrue(state.beginDispatch(fallback));
        state.fallbackSynchronously(fallback);

        dispatcher.refreshAsyncMode(false);

        assertNull(AsyncTransferRuntime.get());
        assertTrue(state.dirtySnapshot().containsAll(Set.of(inFlight, fallback)));

        dispatcher.refreshAsyncMode(true);
        AsyncTransferRuntime second = AsyncTransferRuntime.get();

        assertNotNull(second);
        assertNotEquals(first.runtimeId(), second.runtimeId());
        assertTrue(state.beginDispatch(inFlight));
        assertTrue(state.beginDispatch(fallback));
    }

    @Test
    void repeatedModeChecksDoNotRestartTheRuntime() {
        dispatcher.refreshAsyncMode(true);
        AsyncTransferRuntime runtime = AsyncTransferRuntime.get();
        assertNotNull(runtime);

        dispatcher.refreshAsyncMode(true);

        assertSame(runtime, AsyncTransferRuntime.get());
    }

    @Test
    void stableDisabledModeStopsUnexpectedPublishedRuntime() {
        dispatcher.refreshAsyncMode(false);
        AsyncTransferRuntime.start();
        assertNotNull(AsyncTransferRuntime.get());

        dispatcher.refreshAsyncMode(false);

        assertNull(AsyncTransferRuntime.get());
    }

    @Test
    void replacedRuntimeRequeuesItsInFlightNetwork() {
        dispatcher.refreshAsyncMode(true);
        UUID id = UUID.randomUUID();
        state.markDirty(id);
        assertTrue(state.beginDispatch(id));

        AsyncTransferRuntime.start();
        dispatcher.refreshAsyncMode(true);

        assertTrue(state.beginDispatch(id));
    }

    @Test
    void deletingANetworkClearsEverySchedulingState() {
        UUID id = UUID.randomUUID();
        state.markDirty(id);
        state.scheduleWake(id, 5L);
        assertTrue(state.beginDispatch(id));
        state.markDirty(id);
        state.fallbackSynchronously(id);

        state.delete(id);
        state.promoteDueWakes(5L, ignored -> true);

        assertFalse(state.beginDispatch(id));
        assertFalse(state.takeSynchronousFallbacks().contains(id));
    }

    @Test
    void explicitRedirtyIsNotDuplicatedByAnOldWake() {
        UUID id = UUID.randomUUID();
        state.markDirty(id);
        assertTrue(state.beginDispatch(id));
        state.markDirty(id);
        state.finishDispatch(id);

        state.scheduleResult(id, 10L, 5L);
        assertTrue(state.beginDispatch(id));
        state.promoteDueWakes(15L, ignored -> true);
        state.finishDispatch(id);

        assertFalse(state.beginDispatch(id));
    }

    @Test
    void dueWakesPromoteOnceAtTheEarliestScheduledTick() {
        UUID id = UUID.randomUUID();
        state.scheduleWake(id, 10L);
        state.scheduleWake(id, 12L);

        state.promoteDueWakes(9L, ignored -> true);
        assertFalse(state.beginDispatch(id));

        state.promoteDueWakes(10L, ignored -> true);
        assertTrue(state.beginDispatch(id));
        state.finishDispatch(id);

        state.promoteDueWakes(12L, ignored -> true);
        assertFalse(state.beginDispatch(id));
    }

    private static TransferPlan plan(LogisticsNetwork network, long runtimeId, boolean failed) {
        return new TransferPlan(
                network.getId(), network.getGeneration(), runtimeId, failed, List.of());
    }

    private static boolean isQueued(NetworkRegistry registry, UUID networkId) {
        try {
            Field dispatcherField = NetworkRegistry.class.getDeclaredField("dispatcher");
            dispatcherField.setAccessible(true);
            Field stateField = NetworkDispatcher.class.getDeclaredField("state");
            stateField.setAccessible(true);
            NetworkDispatchState state = (NetworkDispatchState) stateField.get(dispatcherField.get(registry));
            return state.dirtySnapshot().contains(networkId);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
