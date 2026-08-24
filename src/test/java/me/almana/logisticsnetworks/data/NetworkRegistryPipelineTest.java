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
        clearQueue(registry);
        UUID nodeId = UUID.randomUUID();
        long generation = network.getGeneration();

        registry.addNodeToNetwork(network.getId(), nodeId);

        assertEquals(generation + 1L, network.getGeneration());
        assertTrue(network.getNodeUuids().contains(nodeId));
        assertTrue(isQueued(registry, network.getId()));

        clearQueue(registry);
        registry.removeNodeFromNetwork(network.getId(), nodeId);

        assertEquals(generation + 2L, network.getGeneration());
        assertFalse(network.getNodeUuids().contains(nodeId));
        assertTrue(isQueued(registry, network.getId()));
    }

    @Test
    void rejectedSubmissionRetriesOnTheNextTickWithoutDegradedRecovery() {
        UUID id = UUID.randomUUID();
        state.markDirty(id);
        assertTrue(state.beginDispatch(id));

        dispatcher.dispatchCapture(id, capturedNetwork(id), 10L, () -> false);

        state.promoteDueWakes(10L, ignored -> true);
        assertFalse(state.beginDispatch(id));
        state.promoteDueWakes(11L, ignored -> true);
        assertTrue(state.beginDispatch(id));
        assertTrue(state.takeSynchronousFallbacks().isEmpty());
    }

    @Test
    void unavailableCaptureRetriesAfterTwentyTicksWithoutDegradedRecovery() {
        NetworkSnapshot empty = new NetworkSnapshot(
                UUID.randomUUID(), 1L, 2L, 3L, 12L, RegistryAccess.EMPTY, List.of());
        NetworkSnapshot withItems = new NetworkSnapshot(
                UUID.randomUUID(), 1L, 2L, 3L, Long.MAX_VALUE, RegistryAccess.EMPTY,
                List.of(new NetworkSnapshot.ChannelUnit(
                        UUID.randomUUID(), 0, 1, new ItemStack[0], FilterMode.MATCH_ANY,
                        new NetworkSnapshot.ItemEndpoint(0, new int[0], new ItemStack[0], 64, new int[0]),
                        List.of())));

        assertSame(NetworkDispatcher.CaptureDisposition.DEFER,
                NetworkDispatcher.captureDisposition(Snapshots.NetworkCapture.unavailable()));
        assertSame(NetworkDispatcher.CaptureDisposition.ASYNC,
                NetworkDispatcher.captureDisposition(Snapshots.NetworkCapture.captured(empty)));
        assertSame(NetworkDispatcher.CaptureDisposition.ASYNC,
                NetworkDispatcher.captureDisposition(Snapshots.NetworkCapture.captured(withItems)));

        UUID id = UUID.randomUUID();
        state.markDirty(id);
        assertTrue(state.beginDispatch(id));
        state.retryAt(id, 20L);

        state.promoteDueWakes(19L, ignored -> true);
        assertFalse(state.beginDispatch(id));
        state.promoteDueWakes(20L, ignored -> true);
        assertTrue(state.beginDispatch(id));
        assertTrue(state.takeSynchronousFallbacks().isEmpty());
    }

    @Test
    void dispatcherDefersUnavailableCaptureWithoutDegradedRecovery() {
        UUID id = UUID.randomUUID();
        state.markDirty(id);
        assertTrue(state.beginDispatch(id));

        dispatcher.dispatchCapture(id, Snapshots.NetworkCapture.unavailable(), 10L, () -> false);

        assertTrue(state.takeSynchronousFallbacks().isEmpty());
        state.promoteDueWakes(29L, ignored -> true);
        assertFalse(state.beginDispatch(id));
        state.promoteDueWakes(30L, ignored -> true);
        assertTrue(state.beginDispatch(id));
    }

    @Test
    void wrongRuntimePlanRequeuesWithoutDegradedRecovery() {
        UUID id = UUID.randomUUID();
        state.markDirty(id);
        assertTrue(state.beginDispatch(id));

        state.retryCurrent(id);

        assertTrue(state.beginDispatch(id));
        state.finishDispatch(id);
        assertFalse(state.beginDispatch(id));
        assertTrue(state.takeSynchronousFallbacks().isEmpty());
    }

    @Test
    void dispatcherRequeuesStalePlanWithoutDegradedRecovery() {
        LogisticsNetwork network = new LogisticsNetwork(UUID.randomUUID());
        TransferPlan stale = new TransferPlan(
                network.getId(), network.getGeneration() + 1L, 7L, false, Long.MAX_VALUE, List.of());
        state.markDirty(network.getId());
        assertTrue(state.beginDispatch(network.getId()));

        assertFalse(dispatcher.prepareCompletedPlan(stale, network, 7L));

        assertTrue(state.beginDispatch(network.getId()));
        assertTrue(state.takeSynchronousFallbacks().isEmpty());
    }

    @Test
    void dispatcherRequeuesWrongRuntimePlanWithoutDegradedRecovery() {
        LogisticsNetwork network = new LogisticsNetwork(UUID.randomUUID());
        TransferPlan wrongRuntime = new TransferPlan(
                network.getId(), network.getGeneration(), 8L, false, Long.MAX_VALUE, List.of());
        state.markDirty(network.getId());
        assertTrue(state.beginDispatch(network.getId()));

        assertFalse(dispatcher.prepareCompletedPlan(wrongRuntime, network, 7L));

        assertTrue(state.beginDispatch(network.getId()));
        assertTrue(state.takeSynchronousFallbacks().isEmpty());
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
    void explicitDirtyCancelsDelayedRetries() {
        UUID id = UUID.randomUUID();
        state.markDirty(id);
        assertTrue(state.beginDispatch(id));
        state.markDirty(id);
        state.retryAt(id, 11L);
        assertTrue(state.beginDispatch(id));
        state.finishDispatch(id);
        state.promoteDueWakes(11L, ignored -> true);
        assertFalse(state.beginDispatch(id));

        state.markDirty(id);
        assertTrue(state.beginDispatch(id));
        state.retryAt(id, 20L);
        state.markDirty(id);
        assertTrue(state.beginDispatch(id));
        state.finishDispatch(id);
        state.promoteDueWakes(20L, ignored -> true);
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

    private static boolean isQueued(NetworkRegistry registry, UUID networkId) {
        return dispatchState(registry).dirtySnapshot().contains(networkId);
    }

    private static Snapshots.NetworkCapture capturedNetwork(UUID id) {
        return Snapshots.NetworkCapture.captured(new NetworkSnapshot(
                id, 1L, 2L, 3L, Long.MAX_VALUE, RegistryAccess.EMPTY,
                List.of(new NetworkSnapshot.ChannelUnit(
                        UUID.randomUUID(), 0, 1, new ItemStack[0], FilterMode.MATCH_ANY,
                        new NetworkSnapshot.ItemEndpoint(
                                0, new int[0], new ItemStack[0], 64, new int[0]),
                        List.of()))));
    }

    private static void clearQueue(NetworkRegistry registry) {
        dispatchState(registry).takeAllPendingNetworks();
    }

    private static NetworkDispatchState dispatchState(NetworkRegistry registry) {
        try {
            Field dispatcherField = NetworkRegistry.class.getDeclaredField("dispatcher");
            dispatcherField.setAccessible(true);
            Field stateField = NetworkDispatcher.class.getDeclaredField("state");
            stateField.setAccessible(true);
            return (NetworkDispatchState) stateField.get(dispatcherField.get(registry));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
