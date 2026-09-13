package me.almana.logisticsnetworks.logic.async;

import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.Config;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class AsyncTransferRuntime {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_PENDING = 256;
    private static final AtomicLong RUNTIME_IDS = new AtomicLong();
    private static final AtomicLong WORKER_IDS = new AtomicLong();

    @Nullable
    private static volatile AsyncTransferRuntime instance;

    private final long runtimeId;
    private final ThreadPoolExecutor executor;
    private final Queue<TransferPlan> completed = new ConcurrentLinkedQueue<>();
    private final Map<UUID, List<DirectStorageBinding>> bindings = new HashMap<>();

    private AsyncTransferRuntime(long runtimeId, int workers) {
        this.runtimeId = runtimeId;
        executor = new ThreadPoolExecutor(
                workers,
                workers,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(MAX_PENDING),
                runnable -> {
                    Thread thread = new Thread(
                            runnable, "LogisticsNetworks-Worker-" + WORKER_IDS.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    public static synchronized void start() {
        stop();
        int configured = Config.asyncWorkerThreads;
        int workers = configured > 0
                ? configured
                : Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() - 2));
        instance = new AsyncTransferRuntime(RUNTIME_IDS.incrementAndGet(), workers);
        LOGGER.info("Async transfer runtime started with {} worker threads", workers);
    }

    public static synchronized void stop() {
        AsyncTransferRuntime current = instance;
        instance = null;
        if (current != null) {
            current.executor.shutdownNow();
            current.completed.clear();
            current.bindings.clear();
            LOGGER.info("Async transfer runtime stopped");
        }
    }

    @Nullable
    public static AsyncTransferRuntime get() {
        return instance;
    }

    public long runtimeId() {
        return runtimeId;
    }

    public boolean submit(NetworkSnapshot snapshot) {
        return submit(snapshot, List.of());
    }

    public boolean submit(NetworkSnapshot snapshot, List<DirectStorageBinding> capturedBindings) {
        ThreadGuard.requireServerThread();
        bindings.put(snapshot.networkId(), capturedBindings);
        try {
            executor.execute(() -> plan(snapshot));
            return true;
        } catch (RejectedExecutionException exception) {
            bindings.remove(snapshot.networkId());
            return false;
        }
    }

    public List<DirectStorageBinding> takeBindings(UUID network) {
        ThreadGuard.requireServerThread();
        List<DirectStorageBinding> result = bindings.remove(network);
        return result == null ? List.of() : result;
    }

    @Nullable
    public TransferPlan pollCompleted() {
        return completed.poll();
    }

    private void plan(NetworkSnapshot snapshot) {
        try {
            completed.offer(NetworkPlanner.plan(snapshot));
        } catch (Throwable throwable) {
            LOGGER.error("Failed to plan network {}", snapshot.networkId(), throwable);
            completed.offer(new TransferPlan(
                    snapshot.networkId(), snapshot.generation(), snapshot.runtimeId(),
                    true, Long.MAX_VALUE, List.of()));
        }
    }
}
