package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.data.FilterMode;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.logic.TransferAmountRules;
import me.almana.logisticsnetworks.logic.TransferEngine;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerDifferentialTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ThreadGuard.markServerThread();
    }

    private record Scenario(ItemStackHandler source, List<ItemStackHandler> targets, int batchLimit) {
    }

    private static Scenario scenario(int batchLimit, ItemStack[] sourceStacks, int targetCount, int targetSlots,
            ItemStack... targetStacks) {
        assertTrue(targetStacks.length == 0 || targetStacks.length == targetCount,
                "Target seeds must match targets");
        ItemStackHandler source = new ItemStackHandler(Math.max(sourceStacks.length, 27));
        for (int i = 0; i < sourceStacks.length; i++) {
            source.setStackInSlot(i, sourceStacks[i].copy());
        }
        List<ItemStackHandler> targets = new ArrayList<>();
        for (int i = 0; i < targetCount; i++) {
            ItemStackHandler target = new ItemStackHandler(targetSlots);
            if (targetStacks.length > 0) {
                target.setStackInSlot(0, targetStacks[i].copy());
            }
            targets.add(target);
        }
        return new Scenario(source, targets, batchLimit);
    }

    private static List<String> runSync(Scenario s) {
        List<TransferEngine.ItemTransferTarget> engineTargets = new ArrayList<>();
        FilterItemData.ReadCache cache = FilterItemData.createReadCache();
        ItemStack[] noFilters = new ItemStack[0];

        for (ItemStackHandler target : s.targets()) {
            engineTargets.add(new TransferEngine.ItemTransferTarget(
                    target, null, noFilters, FilterMode.MATCH_ANY,
                    TransferAmountRules.collect(noFilters, noFilters, cache),
                    false, null, false));
        }

        TransferEngine.executeMove(s.source(), engineTargets, s.batchLimit(),
                noFilters, FilterMode.MATCH_ANY, null,
                RegistryAccess.EMPTY, null, null, cache, null);

        return describe(s.source(), s.targets());
    }

    private static List<String> runPlanned(Scenario s) {
        ItemStack[] noFilters = new ItemStack[0];
        List<NetworkSnapshot.ItemEndpoint> endpoints = new ArrayList<>();
        endpoints.add(Snapshots.captureItems(s.source()));
        List<NetworkSnapshot.TargetUnit> targetUnits = new ArrayList<>();
        for (int i = 0; i < s.targets().size(); i++) {
            int endpoint = endpoints.size();
            endpoints.add(Snapshots.captureItems(s.targets().get(i)));
            targetUnits.add(new NetworkSnapshot.TargetUnit(
                    UUID.randomUUID(), 0, noFilters, FilterMode.MATCH_ANY, false, false,
                    endpoint));
        }

        NetworkSnapshot.ChannelUnit unit = new NetworkSnapshot.ChannelUnit(
                UUID.randomUUID(), 0, s.batchLimit(), noFilters, FilterMode.MATCH_ANY,
                0, targetUnits);

        NetworkSnapshot snapshot = new NetworkSnapshot(
                UUID.randomUUID(), 0L, 0L, 0L, Long.MAX_VALUE,
                RegistryAccess.EMPTY, endpoints, List.of(unit));

        TransferPlan.ChannelMoves planned = planOnWorker(unit, snapshot);

        for (TransferPlan.ItemMove move : planned.moves()) {
            int sourceSlot = move.sourceSlot();
            int targetIndex = move.targetIndex();
            int amount = move.amount();
            assertTrue(sourceSlot >= 0 && sourceSlot < s.source().getSlots(),
                    "Intent source slot out of range");
            assertTrue(targetIndex >= 0 && targetIndex < s.targets().size(),
                    "Intent target index out of range");
            assertTrue(amount > 0, "Intent amount must be positive");

            ItemStack sourceStack = s.source().getStackInSlot(sourceSlot);
            assertEquals(move.expectedItem(), sourceStack.getItem(), "Intent item mismatch");
            assertEquals(move.expectedComponents(), sourceStack.getComponents(), "Intent components mismatch");

            ItemStack extracted = s.source().extractItem(sourceSlot, amount, false);
            assertEquals(amount, extracted.getCount(), "Intent extraction incomplete");

            ItemStackHandler target = s.targets().get(targetIndex);
            boolean[] targetSlotMask = move.targetSlotMask();
            if (targetSlotMask != null) {
                assertEquals(target.getSlots(), targetSlotMask.length, "Intent slot mask length mismatch");
            }

            ItemStack leftover = extracted;
            for (int slot = 0; slot < target.getSlots() && !leftover.isEmpty(); slot++) {
                if (targetSlotMask == null || targetSlotMask[slot]) {
                    leftover = target.insertItem(slot, leftover, false);
                }
            }
            assertTrue(leftover.isEmpty(), "Intent insertion incomplete");
        }

        return describe(s.source(), s.targets());
    }

    private static TransferPlan.ChannelMoves planOnWorker(NetworkSnapshot.ChannelUnit unit,
            NetworkSnapshot snapshot) {
        FutureTask<TransferPlan.ChannelMoves> task = new FutureTask<>(() -> {
            List<SnapshotItemHandler> endpoints = snapshot.endpoints().stream()
                    .map(SnapshotItemHandler::new)
                    .toList();
            return ItemPlanner.plan(unit, snapshot, endpoints);
        });
        Thread worker = new Thread(task);
        worker.start();
        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    private static List<String> describe(IItemHandler source, List<ItemStackHandler> targets) {
        List<String> out = new ArrayList<>();
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                out.add("src[" + slot + "]=" + stack.getItem() + "x" + stack.getCount());
            }
        }
        for (int t = 0; t < targets.size(); t++) {
            for (int slot = 0; slot < targets.get(t).getSlots(); slot++) {
                ItemStack stack = targets.get(t).getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    out.add("tgt" + t + "[" + slot + "]=" + stack.getItem() + "x" + stack.getCount());
                }
            }
        }
        return out;
    }

    private static void assertIdentical(int batchLimit, ItemStack[] stacks, int targetCount, int targetSlots,
            ItemStack... targetStacks) {
        assertEquals(
                runSync(scenario(batchLimit, stacks, targetCount, targetSlots, targetStacks)),
                runPlanned(scenario(batchLimit, stacks, targetCount, targetSlots, targetStacks)));
    }

    @Test
    void singleTargetSingleStack() {
        assertIdentical(64, new ItemStack[] {new ItemStack(Items.IRON_INGOT, 64)}, 1, 27);
    }

    @Test
    void multipleTargetsSplitBatch() {
        assertIdentical(64, new ItemStack[] {new ItemStack(Items.IRON_INGOT, 64)}, 5, 1,
                new ItemStack(Items.IRON_INGOT, 51),
                new ItemStack(Items.IRON_INGOT, 51),
                new ItemStack(Items.IRON_INGOT, 51),
                new ItemStack(Items.IRON_INGOT, 51),
                new ItemStack(Items.IRON_INGOT, 51));
    }

    @Test
    void multipleSourceSlots() {
        assertIdentical(256, new ItemStack[] {
                new ItemStack(Items.IRON_INGOT, 64),
                new ItemStack(Items.DIAMOND, 32),
                new ItemStack(Items.IRON_INGOT, 10)}, 3, 27);
    }

    @Test
    void batchLimitSmallerThanAvailable() {
        assertIdentical(7, new ItemStack[] {new ItemStack(Items.IRON_INGOT, 64)}, 2, 27);
    }

    @Test
    void targetTooSmallForEverything() {
        assertIdentical(640, new ItemStack[] {
                new ItemStack(Items.IRON_INGOT, 64),
                new ItemStack(Items.IRON_INGOT, 64)}, 1, 1);
    }

    @Test
    void emptySourceMovesNothing() {
        assertIdentical(64, new ItemStack[0], 2, 27);
    }
}
