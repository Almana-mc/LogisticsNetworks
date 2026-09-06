package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.data.DistributionMode;
import me.almana.logisticsnetworks.data.FilterMode;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AsyncTransferRuntimeTest {
    int workers;

    @BeforeEach
    void setup() {
        workers = Config.asyncWorkerThreads;
        Config.asyncWorkerThreads = 1;
        ThreadGuard.markServerThread();
        AsyncTransferRuntime.stop();
    }

    @AfterEach
    void cleanup() {
        AsyncTransferRuntime.stop();
        ThreadGuard.clearServerThread();
        Config.asyncWorkerThreads = workers;
    }

    @Test
    void realWorkerPlansOwnedSnapshotAndPreservesIdentity() throws Exception {
        AsyncTransferRuntime.start();
        var runtime = AsyncTransferRuntime.get();
        var snapshot = empty(runtime.runtimeId());
        assertTrue(runtime.submit(snapshot));
        var plan = await(runtime);
        assertEquals(snapshot.networkId(), plan.networkId());
        assertEquals(snapshot.generation(), plan.generation());
        assertEquals(snapshot.runtimeId(), plan.runtimeId());
        assertEquals(19, plan.itemWakeDelta());
        assertFalse(plan.failed());
    }

    @Test
    void plannerExceptionReturnsFailedIdentityWithoutEscapingWorker() throws Exception {
        AsyncTransferRuntime.start();
        var runtime = AsyncTransferRuntime.get();
        var snapshot = new NetworkSnapshot(UUID.randomUUID(), 27, runtime.runtimeId(), 100, 19,
                RegistryAccess.EMPTY, List.of(), List.of(new NetworkSnapshot.ChannelUnit(UUID.randomUUID(),
                0, 8, new ItemStack[0], FilterMode.MATCH_ANY, 0, false, List.of(), null, DistributionMode.PRIORITY)));
        assertTrue(runtime.submit(snapshot));
        var plan = await(runtime);
        assertTrue(plan.failed());
        assertEquals(snapshot.networkId(), plan.networkId());
        assertEquals(27, plan.generation());
        assertEquals(runtime.runtimeId(), plan.runtimeId());
        assertTrue(plan.channels().isEmpty());
    }

    @Test
    void boundedQueueRejectsAt256AndShutdownDrainsPendingWork() throws Exception {
        AsyncTransferRuntime.start();
        var runtime = AsyncTransferRuntime.get();
        var executor = executor(runtime);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        executor.execute(() -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        try {
            for (int i = 0; i < 256; i++) assertTrue(runtime.submit(empty(runtime.runtimeId())));
            assertFalse(runtime.submit(empty(runtime.runtimeId())));
            assertEquals(256, executor.getQueue().size());
            AsyncTransferRuntime.stop();
            assertTrue(executor.getQueue().isEmpty());
            assertFalse(runtime.submit(empty(runtime.runtimeId())));
        } finally {
            release.countDown();
        }
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertNull(runtime.pollCompleted());
        assertDoesNotThrow(ThreadGuard::requireServerThread);
    }

    @Test
    void autoAndExplicitWorkerCountsRetainExactPolicyAndDaemonNames() throws Exception {
        for (int count : new int[]{0, 1, 7, 16}) {
            Config.asyncWorkerThreads = count;
            AsyncTransferRuntime.start();
            var executor = executor(AsyncTransferRuntime.get());
            int expected = count == 0 ? Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() - 2)) : count;
            assertEquals(expected, executor.getCorePoolSize());
            var worker = executor.submit(Thread::currentThread).get(5, TimeUnit.SECONDS);
            assertTrue(worker.isDaemon());
            assertTrue(worker.getName().matches("LogisticsNetworks-Worker-\\d+"));
            AsyncTransferRuntime.stop();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void replacementRejectsOldSubmissionAndLateCompletion() throws Exception {
        AsyncTransferRuntime.start();
        var first = AsyncTransferRuntime.get();
        var snapshot = empty(first.runtimeId());
        AsyncTransferRuntime.start();
        var second = AsyncTransferRuntime.get();
        assertNotEquals(first.runtimeId(), second.runtimeId());
        assertFalse(first.submit(snapshot));
        var plan = AsyncTransferRuntime.class.getDeclaredMethod("plan", NetworkSnapshot.class);
        plan.setAccessible(true);
        var lateWorker = new Thread(() -> {
            try { plan.invoke(first, snapshot); } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
        lateWorker.start();
        lateWorker.join(5000);
        assertFalse(lateWorker.isAlive());
        assertNull(first.pollCompleted());
        assertNull(second.pollCompleted());
    }

    @Test
    void tomlDefaultsKeysAndRangesMatchSource() {
        assertEquals(List.of("async", "asyncPlanning"), Config.asyncPlanningSpec.getPath());
        assertTrue(Config.asyncPlanningSpec.getDefault());
        assertEquals(0, Config.asyncWorkerThreadsSpec.getDefault());
        assertEquals(2000, Config.asyncCommitBudgetUsSpec.getDefault());
        assertEquals(200000, Config.asyncMaxOccupiedSlotsSpec.getDefault());
        range(Config.asyncWorkerThreadsSpec, "asyncWorkerThreads", 0, 16);
        range(Config.asyncCommitBudgetUsSpec, "asyncCommitBudgetUs", 100, 50000);
        range(Config.asyncMaxOccupiedSlotsSpec, "asyncMaxOccupiedSlots", 1000, 5000000);
    }

    private static void range(net.neoforged.neoforge.common.ModConfigSpec.IntValue value, String key, int min, int max) {
        assertEquals(List.of("async", key), value.getPath());
        assertTrue(value.getSpec().test(min));
        assertTrue(value.getSpec().test(max));
        assertFalse(value.getSpec().test(min - 1));
        assertFalse(value.getSpec().test(max + 1));
    }

    private static ThreadPoolExecutor executor(AsyncTransferRuntime runtime) throws Exception {
        var field = AsyncTransferRuntime.class.getDeclaredField("executor");
        field.setAccessible(true);
        return (ThreadPoolExecutor) field.get(runtime);
    }

    private static NetworkSnapshot empty(long runtimeId) {
        return new NetworkSnapshot(UUID.randomUUID(), 17, runtimeId, 100, 19, RegistryAccess.EMPTY, List.of(), List.of());
    }

    private static TransferPlan await(AsyncTransferRuntime runtime) throws InterruptedException {
        long start = System.nanoTime();
        TransferPlan plan;
        while ((plan = runtime.pollCompleted()) == null && System.nanoTime() - start < 5_000_000_000L) Thread.sleep(1);
        assertNotNull(plan);
        return plan;
    }
}
