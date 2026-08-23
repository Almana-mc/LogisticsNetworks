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
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkFailureIsolationTest {

    private NetworkDispatchState state;

    @BeforeEach
    void setUp() {
        state = new NetworkDispatchState();
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

        assertFalse(state.finishWorkerPlan(failedPlan(id)));
    }

    @Test
    void successfulWorkerPlanResetsConsecutiveFailures() {
        UUID id = UUID.randomUUID();
        assertFalse(fail(id));
        assertFalse(fail(id));

        state.takeSynchronousFallbacks();
        state.markDirty(id);
        assertTrue(state.beginDispatch(id));
        assertFalse(state.finishWorkerPlan(successfulPlan(id)));

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

        assertFalse(state.finishWorkerPlan(failedPlan(id)));
        state.fallbackSynchronously(id);

        assertEquals(Set.of(id), state.takeSynchronousFallbacks());
        assertTrue(state.takeSynchronousFallbacks().isEmpty());
    }

    @Test
    void submissionFallbackDoesNotConsumeWorkerFailureBudget() {
        UUID id = UUID.randomUUID();
        state.markDirty(id);
        assertTrue(state.beginDispatch(id));

        state.fallbackSynchronously(id);

        assertEquals(Set.of(id), state.takeSynchronousFallbacks());
        assertFalse(fail(id));
        assertFalse(fail(id));
        assertTrue(fail(id));
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
    void temporaryCaptureFailureAndOccupiedLimitHaveDifferentDisposition() {
        NetworkSnapshot itemless = new NetworkSnapshot(
                UUID.randomUUID(), 1L, 2L, 3L, RegistryAccess.EMPTY, List.of());
        NetworkSnapshot withItems = new NetworkSnapshot(
                UUID.randomUUID(), 1L, 2L, 3L, RegistryAccess.EMPTY,
                List.of(new NetworkSnapshot.ChannelUnit(
                        UUID.randomUUID(), 0, 1, new ItemStack[0], FilterMode.MATCH_ANY,
                        new NetworkSnapshot.ItemEndpoint(
                                0, new int[0], new ItemStack[0], 64, new int[0]),
                        List.of())));

        assertEquals(NetworkDispatcher.CaptureDisposition.SYNCHRONOUS,
                NetworkDispatcher.captureDisposition(Snapshots.NetworkCapture.unavailable()));
        assertEquals(NetworkDispatcher.CaptureDisposition.DISABLE_ASYNC,
                NetworkDispatcher.captureDisposition(Snapshots.NetworkCapture.occupiedLimitExceeded()));
        assertEquals(NetworkDispatcher.CaptureDisposition.SYNCHRONOUS,
                NetworkDispatcher.captureDisposition(Snapshots.NetworkCapture.captured(itemless)));
        assertEquals(NetworkDispatcher.CaptureDisposition.ASYNC,
                NetworkDispatcher.captureDisposition(Snapshots.NetworkCapture.captured(withItems)));
    }

    private boolean fail(UUID id) {
        state.takeSynchronousFallbacks();
        state.markDirty(id);
        assertTrue(state.beginDispatch(id));
        boolean newlyDisabled = state.finishWorkerPlan(failedPlan(id));
        state.fallbackSynchronously(id);
        return newlyDisabled;
    }

    private static TransferPlan failedPlan(UUID id) {
        return new TransferPlan(id, 1L, 2L, true, List.of());
    }

    private static TransferPlan successfulPlan(UUID id) {
        return new TransferPlan(id, 1L, 2L, false, List.of());
    }
}
