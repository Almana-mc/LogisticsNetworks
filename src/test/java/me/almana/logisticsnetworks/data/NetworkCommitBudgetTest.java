package me.almana.logisticsnetworks.data;

import me.almana.logisticsnetworks.logic.async.TransferPlan;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class NetworkCommitBudgetTest {

    @Test
    void emptyQueueCommitsNothing() {
        Queue<TransferPlan> queued = new ArrayDeque<>();
        List<TransferPlan> committed = new ArrayList<>();

        NetworkDispatcher.drainCompleted(
                queued::poll, committed::add, () -> 0L, () -> false, 1);

        assertEquals(List.of(), committed);
    }

    @Test
    void alreadyExpiredBudgetStillCommitsOnePlan() {
        TransferPlan first = plan();
        TransferPlan second = plan();
        Queue<TransferPlan> queued = new ArrayDeque<>(List.of(first, second));
        List<TransferPlan> committed = new ArrayList<>();
        LongSupplier time = LongStream.of(0L, 1_001L).iterator()::nextLong;

        NetworkDispatcher.drainCompleted(
                queued::poll, committed::add, time, () -> false, 1);

        assertEquals(List.of(first), committed);
        assertSame(second, queued.peek());
    }

    @Test
    void expiredBudgetStopsAfterCommitAndLeavesFifoRemainder() {
        TransferPlan first = plan();
        TransferPlan second = plan();
        TransferPlan third = plan();
        Queue<TransferPlan> queued = new ArrayDeque<>(List.of(first, second, third));
        List<TransferPlan> committed = new ArrayList<>();
        LongSupplier time = LongStream.of(0L, 500L, 1_000L).iterator()::nextLong;

        NetworkDispatcher.drainCompleted(
                queued::poll, committed::add, time, () -> false, 1);

        assertEquals(List.of(first, second), committed);
        assertEquals(List.of(third), List.copyOf(queued));
    }

    @Test
    void spareTimeContinuesPastBudgetUsingTheLiveSupplier() {
        TransferPlan first = plan();
        TransferPlan second = plan();
        TransferPlan third = plan();
        Queue<TransferPlan> queued = new ArrayDeque<>(List.of(first, second, third));
        List<TransferPlan> committed = new ArrayList<>();
        LongSupplier time = LongStream.of(0L, 1_000L, 2_000L).iterator()::nextLong;
        AtomicInteger checks = new AtomicInteger();

        NetworkDispatcher.drainCompleted(
                queued::poll, committed::add, time,
                () -> checks.getAndIncrement() == 0, 1);

        assertEquals(List.of(first, second), committed);
        assertSame(third, queued.peek());
        assertEquals(2, checks.get());
    }

    private static TransferPlan plan() {
        return new TransferPlan(UUID.randomUUID(), 1L, 2L, false, List.of());
    }
}
