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

class PlannerDifferentialTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ThreadGuard.markServerThread();
    }

    private record Scenario(ItemStackHandler source, List<ItemStackHandler> targets, int batchLimit) {
    }

    private static Scenario scenario(int batchLimit, ItemStack[] sourceStacks, int targetCount, int targetSlots) {
        ItemStackHandler source = new ItemStackHandler(Math.max(sourceStacks.length, 27));
        for (int i = 0; i < sourceStacks.length; i++) {
            source.setStackInSlot(i, sourceStacks[i]);
        }
        List<ItemStackHandler> targets = new ArrayList<>();
        for (int i = 0; i < targetCount; i++) {
            targets.add(new ItemStackHandler(targetSlots));
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
        List<NetworkSnapshot.TargetUnit> targetUnits = new ArrayList<>();
        for (int i = 0; i < s.targets().size(); i++) {
            targetUnits.add(new NetworkSnapshot.TargetUnit(
                    UUID.randomUUID(), 0, noFilters, FilterMode.MATCH_ANY, false, false,
                    Snapshots.captureItems(s.targets().get(i))));
        }

        NetworkSnapshot.ChannelUnit unit = new NetworkSnapshot.ChannelUnit(
                UUID.randomUUID(), 0, s.batchLimit(), noFilters, FilterMode.MATCH_ANY,
                Snapshots.captureItems(s.source()), targetUnits);

        NetworkSnapshot snapshot = new NetworkSnapshot(
                UUID.randomUUID(), 0L, 0L, 0L, RegistryAccess.EMPTY, List.of(unit));

        TransferPlan.ChannelMoves planned = planOnWorker(unit, snapshot);

        for (TransferPlan.ItemMove move : planned.moves()) {
            ItemStack extracted = s.source().extractItem(move.sourceSlot(), move.amount(), false);
            ItemStack leftover = s.targets().get(move.targetIndex()).insertItem(0, extracted, false);
            for (int slot = 1; slot < s.targets().get(move.targetIndex()).getSlots() && !leftover.isEmpty(); slot++) {
                leftover = s.targets().get(move.targetIndex()).insertItem(slot, leftover, false);
            }
        }

        return describe(s.source(), s.targets());
    }

    private static TransferPlan.ChannelMoves planOnWorker(NetworkSnapshot.ChannelUnit unit,
            NetworkSnapshot snapshot) {
        FutureTask<TransferPlan.ChannelMoves> task = new FutureTask<>(() -> ItemPlanner.plan(unit, snapshot));
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

    private static void assertIdentical(int batchLimit, ItemStack[] stacks, int targetCount, int targetSlots) {
        assertEquals(
                runSync(scenario(batchLimit, stacks, targetCount, targetSlots)),
                runPlanned(scenario(batchLimit, stacks, targetCount, targetSlots)));
    }

    @Test
    void singleTargetSingleStack() {
        assertIdentical(64, new ItemStack[] {new ItemStack(Items.IRON_INGOT, 64)}, 1, 27);
    }

    @Test
    void multipleTargetsSplitBatch() {
        assertIdentical(64, new ItemStack[] {new ItemStack(Items.IRON_INGOT, 64)}, 5, 27);
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
