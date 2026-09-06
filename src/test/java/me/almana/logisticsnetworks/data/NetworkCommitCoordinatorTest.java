package me.almana.logisticsnetworks.data;

import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.logic.TransferEngine;
import me.almana.logisticsnetworks.logic.async.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NetworkCommitCoordinatorTest {
    @Test
    @SuppressWarnings("unchecked")
    void actualCoordinatorSkipsRejectedFrontPlansAndCommitsFirstCurrentPlan() throws Exception {
        boolean async = Config.asyncPlanning;
        boolean ticking = Config.networkTickingEnabled;
        int budget = Config.asyncCommitBudgetUs;
        Config.asyncPlanning = true;
        Config.networkTickingEnabled = true;
        Config.asyncCommitBudgetUs = 0;
        ThreadGuard.markServerThread();
        var registry = new NetworkRegistry();
        var stale = registry.createNetwork();
        var failed = registry.createNetwork();
        var current = registry.createNetwork();
        var remaining = registry.createNetwork();
        var server = mock(MinecraftServer.class);
        var level = mock(ServerLevel.class);
        when(server.overworld()).thenReturn(level);
        when(level.getGameTime()).thenReturn(100L);
        try (var engine = mockStatic(TransferEngine.class)) {
            registry.refreshAsyncPlanning();
            var runtime = AsyncTransferRuntime.get();
            var field = AsyncTransferRuntime.class.getDeclaredField("completed");
            field.setAccessible(true);
            var queue = (Queue<TransferPlan>) field.get(runtime);
            queue.add(new TransferPlan(stale.getId(), stale.getGeneration() + 1, runtime.runtimeId(), false, 0, List.of()));
            queue.add(new TransferPlan(failed.getId(), failed.getGeneration(), runtime.runtimeId(), true, 0, List.of()));
            queue.add(new TransferPlan(current.getId(), current.getGeneration(), runtime.runtimeId(), false, 0, List.of()));
            var last = new TransferPlan(remaining.getId(), remaining.getGeneration(), runtime.runtimeId(), false, 0, List.of());
            queue.add(last);
            registry.commitCompleted(server, () -> false);
            assertEquals(List.of(last), List.copyOf(queue));
            engine.verify(() -> TransferEngine.processSynchronousNonItems(current, server), times(1));
            engine.verifyNoMoreInteractions();
            assertFalse(NetworkReloadTest.state(registry).isAsyncDisabled(failed.getId()));
        } finally {
            AsyncTransferRuntime.stop();
            ThreadGuard.clearServerThread();
            Config.asyncPlanning = async;
            Config.networkTickingEnabled = ticking;
            Config.asyncCommitBudgetUs = budget;
        }
    }

    @Test
    void degradedRecoveryProcessesOneNetworkPerCallInFifoOrder() throws Exception {
        boolean ticking = Config.networkTickingEnabled;
        Config.networkTickingEnabled = true;
        var registry = new NetworkRegistry();
        var first = registry.createNetwork();
        var second = registry.createNetwork();
        var state = NetworkReloadTest.state(registry);
        state.disableForOccupiedSlots(first.getId());
        state.disableForOccupiedSlots(second.getId());
        var server = mock(MinecraftServer.class);
        var level = mock(ServerLevel.class);
        when(server.overworld()).thenReturn(level);
        try (var engine = mockStatic(TransferEngine.class)) {
            registry.processDegradedRecovery(server);
            engine.verify(() -> TransferEngine.processNetwork(first, server), times(1));
            engine.verify(() -> TransferEngine.processNetwork(second, server), never());
            registry.processDegradedRecovery(server);
            engine.verify(() -> TransferEngine.processNetwork(second, server), times(1));
            registry.processDegradedRecovery(server);
            engine.verify(() -> TransferEngine.processNetwork(first, server), times(2));
        } finally {
            Config.networkTickingEnabled = ticking;
        }
    }
}
