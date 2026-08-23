package me.almana.logisticsnetworks.data;

import me.almana.logisticsnetworks.logic.async.NetworkSnapshot;
import me.almana.logisticsnetworks.logic.async.ThreadGuard;
import me.almana.logisticsnetworks.logic.async.TransferPlan;
import net.minecraft.core.RegistryAccess;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkRegistryPipelineTest {

    @BeforeAll
    static void markServerThread() {
        ThreadGuard.markServerThread();
    }

    @Test
    void plannerRunsOutsideServerThread() {
        NetworkSnapshot snapshot = snapshot(List.of());

        TransferPlan plan = NetworkRegistry.planOffThread(snapshot);

        assertEquals(snapshot.networkId(), plan.networkId());
        assertTrue(plan.channels().isEmpty());
    }

    @Test
    void plannerFailureReturnsFallbackSignal() {
        assertNull(NetworkRegistry.planOffThread(snapshot(null)));
    }

    @Test
    void interruptionReturnsFallbackSignalAndStaysInterrupted() {
        Thread.currentThread().interrupt();
        try {
            assertNull(NetworkRegistry.planOffThread(snapshot(List.of())));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private static NetworkSnapshot snapshot(List<NetworkSnapshot.ChannelUnit> units) {
        return new NetworkSnapshot(UUID.randomUUID(), 3L, 0L, 20L, RegistryAccess.EMPTY, units);
    }
}
