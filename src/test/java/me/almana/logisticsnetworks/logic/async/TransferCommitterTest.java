package me.almana.logisticsnetworks.logic.async;

import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferCommitterTest {

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
}
