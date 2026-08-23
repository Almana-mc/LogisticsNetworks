package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.data.FilterMode;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkPlannerTest {

    @Test
    void retainsAttemptedChannelWhenNothingCanMove() throws Exception {
        NetworkSnapshot.ItemEndpoint endpoint = new NetworkSnapshot.ItemEndpoint(
                1, new int[0], new ItemStack[0], 64, new int[0]);
        NetworkSnapshot.ChannelUnit channel = new NetworkSnapshot.ChannelUnit(
                UUID.randomUUID(), 2, 64, new ItemStack[0], FilterMode.MATCH_ANY, endpoint, List.of());
        NetworkSnapshot snapshot = new NetworkSnapshot(
                UUID.randomUUID(), 4L, 0L, 30L, RegistryAccess.EMPTY, List.of(channel));

        TransferPlan plan = planOnWorker(snapshot);

        assertEquals(1, plan.channels().size());
        assertTrue(plan.channels().getFirst().moves().isEmpty());
    }

    private static TransferPlan planOnWorker(NetworkSnapshot snapshot)
            throws InterruptedException, ExecutionException {
        FutureTask<TransferPlan> task = new FutureTask<>(() -> NetworkPlanner.plan(snapshot));
        Thread worker = new Thread(task);
        worker.start();
        return task.get();
    }
}
