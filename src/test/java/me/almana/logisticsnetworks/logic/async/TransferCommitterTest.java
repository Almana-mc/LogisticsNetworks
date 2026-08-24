package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.data.LogisticsNetwork;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferCommitterTest {

    @BeforeEach
    void markServerThread() {
        ThreadGuard.markServerThread();
    }

    @AfterEach
    void clearServerThread() {
        ThreadGuard.clearServerThread();
    }

    @Test
    void detectsEverySharedHandlerIdentity() {
        ItemStackHandler source = new ItemStackHandler(1);
        ItemStackHandler sourceBulk = new ItemStackHandler(1);
        ItemStackHandler target = new ItemStackHandler(1);
        ItemStackHandler targetBulk = new ItemStackHandler(1);

        assertTrue(TransferCommitter.sharesItemHandler(source, sourceBulk, source, targetBulk));
        assertTrue(TransferCommitter.sharesItemHandler(source, sourceBulk, target, source));
        assertTrue(TransferCommitter.sharesItemHandler(source, sourceBulk, sourceBulk, targetBulk));
        assertTrue(TransferCommitter.sharesItemHandler(source, sourceBulk, target, sourceBulk));
        assertFalse(TransferCommitter.sharesItemHandler(source, sourceBulk, target, targetBulk));
    }

    @Test
    void emptyItemCommitRetainsThePlannedWake() {
        LogisticsNetwork network = new LogisticsNetwork(UUID.randomUUID());
        TransferPlan plan = new TransferPlan(
                network.getId(), network.getGeneration(), 2L, false, 12L, List.of());

        TransferCommitter.ItemCommitResult result = assertDoesNotThrow(
                () -> TransferCommitter.commitItems(plan, network, null, null, 2L));

        assertEquals(0, result.moved());
        assertEquals(12L, result.wakeDelta());
    }
}
