package me.almana.logisticsnetworks.data;

import me.almana.logisticsnetworks.logic.async.NetworkSnapshot;
import me.almana.logisticsnetworks.logic.async.Snapshots;
import me.almana.logisticsnetworks.logic.async.TransferPlan;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkFailureIsolationTest {

    private static final long CURRENT_GENERATION = 1L;
    private static final long CURRENT_RUNTIME = 2L;

    private NetworkDispatchState state;
    private NetworkDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        state = new NetworkDispatchState();
        dispatcher = new NetworkDispatcher(state);
    }

    @Test
    void thirdConsecutiveWorkerFailurePinsAsyncOnce() {
        UUID id = UUID.randomUUID();

        assertFalse(fail(id));
        assertFalse(state.isAsyncDisabled(id));
        assertFalse(fail(id));
        assertFalse(state.isAsyncDisabled(id));
        assertTrue(fail(id));
        assertTrue(state.isAsyncDisabled(id));

        assertFalse(state.finishWorkerPlan(
                failedPlan(id), CURRENT_GENERATION, CURRENT_RUNTIME));
    }

    @Test
    void successfulWorkerPlanResetsConsecutiveFailures() {
        UUID id = UUID.randomUUID();
        assertFalse(fail(id));
        assertFalse(fail(id));

        state.takeSynchronousFallbacks();
        state.markDirty(id);
        assertTrue(state.beginDispatch(id));
        assertFalse(state.finishWorkerPlan(
                successfulPlan(id), CURRENT_GENERATION, CURRENT_RUNTIME));

        assertFalse(fail(id));
        assertFalse(fail(id));
        assertFalse(state.isAsyncDisabled(id));
        assertTrue(fail(id));
    }

    @Test
    void workerFailureStateIsIsolatedPerNetwork() {
        UUID broken = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();

        fail(broken);
        fail(broken);
        assertTrue(fail(broken));
        assertFalse(fail(healthy));

        assertTrue(state.isAsyncDisabled(broken));
        assertFalse(state.isAsyncDisabled(healthy));
    }

    @Test
    void staleGenerationFailuresDoNotAdvanceTheFailureBudget() {
        UUID id = UUID.randomUUID();
        TransferPlan stale = new TransferPlan(
                id, CURRENT_GENERATION + 1L, CURRENT_RUNTIME, true, List.of());

        assertFalse(fail(stale));
        assertFalse(fail(stale));
        assertFalse(fail(stale));
        assertFalse(state.isAsyncDisabled(id));

        assertFalse(fail(id));
        assertFalse(fail(id));
        assertTrue(fail(id));
    }

    @Test
    void wrongRuntimeFailuresDoNotAdvanceTheFailureBudget() {
        UUID id = UUID.randomUUID();
        TransferPlan wrongRuntime = new TransferPlan(
                id, CURRENT_GENERATION, CURRENT_RUNTIME + 1L, true, List.of());

        assertFalse(fail(wrongRuntime));
        assertFalse(fail(wrongRuntime));
        assertFalse(fail(wrongRuntime));
        assertFalse(state.isAsyncDisabled(id));

        assertFalse(fail(id));
        assertFalse(fail(id));
        assertTrue(fail(id));
    }

    @Test
    void dispatcherExceptionRetriesBeforePermanentFallback() {
        LogisticsNetwork network = new LogisticsNetwork(UUID.randomUUID());
        UUID id = network.getId();
        for (int attempt = 0; attempt < 2; attempt++) {
            state.markDirty(id);
            assertTrue(state.beginDispatch(id));

            dispatcher.recordDispatchException(network, CURRENT_RUNTIME);

            assertTrue(state.takeSynchronousFallbacks().isEmpty());
            assertTrue(state.beginDispatch(id));
            state.finishDispatch(id);
        }

        state.markDirty(id);
        assertTrue(state.beginDispatch(id));
        dispatcher.recordDispatchException(network, CURRENT_RUNTIME);

        assertTrue(state.takeSynchronousFallbacks().contains(id));
    }

    @Test
    void disabledNetworksRemainEligibleForSynchronousScheduling() {
        UUID id = UUID.randomUUID();
        assertTrue(state.disableForOccupiedSlots(id));
        assertFalse(state.disableForOccupiedSlots(id));

        state.markDirty(id);
        assertFalse(state.beginDispatch(id));
        assertEquals(Set.of(id), state.takeSynchronousFallbacks());

        state.scheduleResult(id, 10L, 0L);
        assertEquals(Set.of(id), state.takeSynchronousFallbacks());

        state.scheduleResult(id, 10L, 5L);
        state.promoteDueWakes(14L, ignored -> true);
        assertTrue(state.takeSynchronousFallbacks().isEmpty());
        state.promoteDueWakes(15L, ignored -> true);
        assertEquals(Set.of(id), state.takeSynchronousFallbacks());
    }

    @Test
    void failedPlanClearsInFlightAndQueuesOneSynchronousRecovery() {
        UUID id = UUID.randomUUID();
        state.markDirty(id);
        assertTrue(state.beginDispatch(id));

        assertFalse(state.finishWorkerPlan(
                failedPlan(id), CURRENT_GENERATION, CURRENT_RUNTIME));
        state.fallbackSynchronously(id);

        assertEquals(Set.of(id), state.takeSynchronousFallbacks());
        assertTrue(state.takeSynchronousFallbacks().isEmpty());
    }

    @Test
    void stalePlanRequeuesWithoutDegradedRecovery() {
        UUID id = UUID.randomUUID();
        state.markDirty(id);
        assertTrue(state.beginDispatch(id));
        state.markDirty(id);

        state.retryCurrent(id);

        assertTrue(state.beginDispatch(id));
        state.finishDispatch(id);
        assertFalse(state.beginDispatch(id));
        assertFalse(state.takeSynchronousFallbacks().contains(id));
    }

    @Test
    void deletionClearsFailureDisableAndSchedulingState() {
        UUID id = UUID.randomUUID();
        fail(id);
        state.takeSynchronousFallbacks();
        state.markDirty(id);
        assertTrue(state.beginDispatch(id));
        state.markDirty(id);
        state.scheduleWake(id, 12L);
        assertTrue(state.disableForOccupiedSlots(id));

        state.delete(id);
        state.promoteDueWakes(12L, ignored -> true);

        assertFalse(state.beginDispatch(id));
        assertTrue(state.takeSynchronousFallbacks().isEmpty());
        assertFalse(fail(id));
        assertFalse(fail(id));
        assertTrue(fail(id));
    }

    @Test
    void transientCaptureOutcomesDeferWhileOccupiedLimitRecoversSynchronously() {
        NetworkSnapshot itemless = new NetworkSnapshot(
                UUID.randomUUID(), 1L, 2L, 3L, RegistryAccess.EMPTY, List.of());
        NetworkSnapshot withItems = new NetworkSnapshot(
                UUID.randomUUID(), 1L, 2L, 3L, RegistryAccess.EMPTY,
                List.of(new NetworkSnapshot.ChannelUnit(
                        UUID.randomUUID(), 0, 1, new ItemStack[0], FilterMode.MATCH_ANY,
                        new NetworkSnapshot.ItemEndpoint(
                                0, new int[0], new ItemStack[0], 64, new int[0]),
                        List.of())));

        assertEquals(NetworkDispatcher.CaptureDisposition.DEFER,
                NetworkDispatcher.captureDisposition(Snapshots.NetworkCapture.unavailable()));
        assertEquals(NetworkDispatcher.CaptureDisposition.DISABLE_ASYNC,
                NetworkDispatcher.captureDisposition(Snapshots.NetworkCapture.occupiedLimitExceeded()));
        assertEquals(NetworkDispatcher.CaptureDisposition.DEFER,
                NetworkDispatcher.captureDisposition(Snapshots.NetworkCapture.captured(itemless)));
        assertEquals(NetworkDispatcher.CaptureDisposition.ASYNC,
                NetworkDispatcher.captureDisposition(Snapshots.NetworkCapture.captured(withItems)));
    }

    @Test
    void dispatchReasonsCountIndependently() {
        AsyncDispatchStats stats = new AsyncDispatchStats();
        UUID stale = UUID.randomUUID();
        UUID rejected = UUID.randomUUID();

        stats.record(AsyncDispatchReason.STALE_GENERATION, stale);
        stats.record(AsyncDispatchReason.STALE_GENERATION, stale);
        stats.record(AsyncDispatchReason.QUEUE_REJECTED, rejected);

        assertEquals(2L, stats.count(AsyncDispatchReason.STALE_GENERATION));
        assertEquals(1L, stats.count(AsyncDispatchReason.QUEUE_REJECTED));
        assertEquals(stale, stats.latestNetwork(AsyncDispatchReason.STALE_GENERATION));
        assertEquals(rejected, stats.latestNetwork(AsyncDispatchReason.QUEUE_REJECTED));
    }

    @Test
    void dispatchSummaryIncludesOnlyNonZeroReasonsAtTheInterval() {
        AsyncDispatchStats stats = new AsyncDispatchStats();
        UUID unavailable = UUID.randomUUID();

        stats.record(AsyncDispatchReason.CAPTURE_UNAVAILABLE, unavailable);

        assertEquals("CAPTURE_UNAVAILABLE=1 (" + unavailable + ")", stats.summary(0L));
        assertNull(stats.summary(1_199L));
        assertEquals("CAPTURE_UNAVAILABLE=1 (" + unavailable + ")", stats.summary(1_200L));
    }

    @Test
    void rejectedPlanReasonsDistinguishRuntimeAndGenerationMismatches() {
        UUID id = UUID.randomUUID();
        LogisticsNetwork network = new LogisticsNetwork(id);
        TransferPlan stale = new TransferPlan(
                id, network.getGeneration() + 1L, CURRENT_RUNTIME, true, List.of());
        TransferPlan wrongRuntime = new TransferPlan(
                id, network.getGeneration(), CURRENT_RUNTIME + 1L, true, List.of());

        assertEquals(AsyncDispatchReason.STALE_GENERATION,
                NetworkDispatcher.rejectedPlanReason(stale, network, CURRENT_RUNTIME));
        assertEquals(AsyncDispatchReason.WRONG_RUNTIME,
                NetworkDispatcher.rejectedPlanReason(wrongRuntime, network, CURRENT_RUNTIME));
    }

    private boolean fail(UUID id) {
        return fail(failedPlan(id));
    }

    private boolean fail(TransferPlan plan) {
        UUID id = plan.networkId();
        state.takeSynchronousFallbacks();
        state.markDirty(id);
        assertTrue(state.beginDispatch(id));
        boolean newlyDisabled = state.finishWorkerPlan(
                plan, CURRENT_GENERATION, CURRENT_RUNTIME);
        state.fallbackSynchronously(id);
        return newlyDisabled;
    }

    private static TransferPlan failedPlan(UUID id) {
        return new TransferPlan(id, CURRENT_GENERATION, CURRENT_RUNTIME, true, List.of());
    }

    private static TransferPlan successfulPlan(UUID id) {
        return new TransferPlan(id, CURRENT_GENERATION, CURRENT_RUNTIME, false, List.of());
    }
}
