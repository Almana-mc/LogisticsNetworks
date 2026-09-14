package me.almana.logisticsnetworks.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.google.common.collect.ImmutableSet;
import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.integration.storage.CraftingBatch;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import me.almana.logisticsnetworks.integration.storage.StorageFailure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

final class AE2StorageCraftingBatch implements CraftingBatch, ICraftingRequester {

    private static final Logger LOGGER = LogUtils.getLogger();

    private record Requirement(AEItemKey key, long amount) {
    }

    private final IGrid grid;
    private final IGridNode gridNode;
    private final IActionSource source;
    private final ServerPlayer player;
    private final List<Requirement> requirements;
    private final Map<AEItemKey, Long> held = new LinkedHashMap<>();
    private final Listener listener;
    private int requirementIndex;
    private int submitDelay;
    private Future<ICraftingPlan> calculation;
    private ICraftingPlan plan;
    private ICraftingLink link;
    private boolean returning;
    private boolean finished;

    private AE2StorageCraftingBatch(AE2StorageAccess access, ServerPlayer player,
                                    List<LinkedStorage.ItemRequirement> requirements, Listener listener) {
        this.grid = access.grid();
        this.gridNode = access.node();
        this.source = IActionSource.ofPlayer(player, this);
        this.player = player;
        this.requirements = combine(requirements);
        this.listener = listener;
    }

    static AE2StorageCraftingBatch start(AE2StorageAccess access, ServerPlayer player,
                                         List<LinkedStorage.ItemRequirement> requirements, Listener listener) {
        AE2StorageCraftingBatch batch = new AE2StorageCraftingBatch(access, player, requirements, listener);
        if (batch.requirements.size() != requirements.size()) {
            listener.failed(StorageFailure.of(StorageFailure.Reason.NO_PATTERN));
            return null;
        }
        batch.startNext();
        return batch;
    }

    @Override
    public void tick() {
        if (finished) return;
        if (returning) {
            returnHeld();
            return;
        }
        if (!gridNode.isActive() || gridNode.getGrid() != grid) {
            fail(StorageFailure.of(StorageFailure.Reason.NETWORK_UNAVAILABLE));
            return;
        }
        if (link != null) {
            if (link.isCanceled() || link.isDone()) jobStateChange(link);
            return;
        }
        if (calculation != null) {
            finishCalculation();
            return;
        }
        if (plan != null) submitPlan();
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public long held(ItemStack pattern) {
        long total = 0;
        for (Map.Entry<AEItemKey, Long> entry : held.entrySet()) {
            if (ItemStack.isSameItem(entry.getKey().toStack(), pattern)) {
                total = saturatingAdd(total, entry.getValue());
            }
        }
        return total;
    }

    @Override
    public List<ItemStack> take(ItemStack pattern, int amount) {
        List<ItemStack> taken = new ArrayList<>();
        int remaining = amount;
        for (AEItemKey key : new ArrayList<>(held.keySet())) {
            if (remaining <= 0 || !ItemStack.isSameItem(key.toStack(), pattern)) continue;
            long available = held.getOrDefault(key, 0L);
            long moved = Math.min(available, remaining);
            addStacks(taken, key, moved);
            remaining -= (int) moved;
            if (moved == available) held.remove(key);
            else held.put(key, available - moved);
        }
        return taken;
    }

    @Override
    public void cancel() {
        if (finished) return;
        fail(StorageFailure.of(StorageFailure.Reason.CANCELED));
        returnHeld();
    }

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return link == null ? ImmutableSet.of() : ImmutableSet.of(link);
    }

    @Override
    public long insertCraftedItems(ICraftingLink craftingLink, AEKey what, long amount, Actionable mode) {
        if (finished || returning || amount <= 0 || !(what instanceof AEItemKey itemKey)
                || requirementIndex >= requirements.size()) return 0;
        Requirement requirement = requirements.get(requirementIndex);
        if (!requirement.key().equals(itemKey)) return 0;
        long remaining = requirement.amount() - held.getOrDefault(itemKey, 0L);
        long accepted = Math.min(amount, Math.max(remaining, 0));
        if (mode == Actionable.MODULATE && accepted > 0) {
            held.merge(itemKey, accepted, AE2StorageCraftingBatch::saturatingAdd);
        }
        return accepted;
    }

    @Override
    public void jobStateChange(ICraftingLink craftingLink) {
        if (finished || returning || craftingLink != link) return;
        link = null;
        if (craftingLink.isCanceled()) {
            fail(StorageFailure.of(StorageFailure.Reason.CANCELED));
            return;
        }
        Requirement requirement = requirements.get(requirementIndex);
        if (held.getOrDefault(requirement.key(), 0L) < requirement.amount()) {
            fail(StorageFailure.of(StorageFailure.Reason.SUBMISSION));
            return;
        }
        requirementIndex++;
        startNext();
    }

    @Override
    public IGridNode getActionableNode() {
        return gridNode;
    }

    private void startNext() {
        if (requirementIndex >= requirements.size()) {
            boolean committed = listener.complete(this);
            if (!committed) {
                fail(StorageFailure.of(StorageFailure.Reason.TARGET_INVALID));
                return;
            }
            returning = !held.isEmpty();
            if (returning) returnHeld();
            else finished = true;
            return;
        }
        Requirement requirement = requirements.get(requirementIndex);
        try {
            calculation = grid.getCraftingService().beginCraftingCalculation(
                    gridNode.getLevel(), () -> source, requirement.key(), requirement.amount(),
                    CalculationStrategy.REPORT_MISSING_ITEMS);
        } catch (RuntimeException exception) {
            LOGGER.error("AE2 crafting calculation failed", exception);
            fail(StorageFailure.of(StorageFailure.Reason.SUBMISSION));
        }
    }

    private void finishCalculation() {
        if (!calculation.isDone()) return;
        try {
            ICraftingPlan completed = calculation.get();
            calculation = null;
            if (completed.simulation()) {
                fail(AE2CraftingFailure.fromPlan(completed).toStorageFailure());
                return;
            }
            plan = completed;
            submitPlan();
        } catch (Exception exception) {
            LOGGER.error("AE2 crafting calculation failed", exception);
            fail(StorageFailure.of(StorageFailure.Reason.SUBMISSION));
        }
    }

    private void submitPlan() {
        if (submitDelay > 0) {
            submitDelay--;
            return;
        }
        ICraftingSubmitResult result;
        try {
            result = grid.getCraftingService().submitJob(plan, this, null, false, source);
        } catch (RuntimeException exception) {
            LOGGER.error("AE2 crafting submission failed", exception);
            fail(StorageFailure.of(StorageFailure.Reason.SUBMISSION));
            return;
        }
        if (result.successful() && result.link() != null) {
            plan = null;
            link = result.link();
            return;
        }
        StorageFailure failure = AE2CraftingFailure.fromSubmit(result).toStorageFailure();
        if (failure.retryable()) submitDelay = 20;
        else fail(failure);
    }

    private void fail(StorageFailure failure) {
        if (finished || returning) return;
        returning = true;
        if (calculation != null) calculation.cancel(true);
        if (link != null && !link.isDone() && !link.isCanceled()) link.cancel();
        calculation = null;
        plan = null;
        link = null;
        listener.failed(failure);
        returnHeld();
    }

    private void returnHeld() {
        for (Map.Entry<AEItemKey, Long> entry : new ArrayList<>(held.entrySet())) {
            long inserted = grid.getStorageService().getInventory().insert(
                    entry.getKey(), entry.getValue(), Actionable.MODULATE, source);
            long remaining = entry.getValue() - inserted;
            if (remaining > 0) returnToPlayer(entry.getKey(), remaining);
            held.remove(entry.getKey());
        }
        if (held.isEmpty()) finished = true;
    }

    private void returnToPlayer(AEItemKey key, long amount) {
        while (amount > 0) {
            ItemStack returned = key.toStack((int) Math.min(amount, key.toStack().getMaxStackSize()));
            int count = returned.getCount();
            player.getInventory().add(returned);
            if (!returned.isEmpty()) player.drop(returned, false);
            amount -= count;
        }
        player.getInventory().setChanged();
    }

    private static List<Requirement> combine(List<LinkedStorage.ItemRequirement> source) {
        Map<AEItemKey, Long> combined = new LinkedHashMap<>();
        for (LinkedStorage.ItemRequirement requirement : source) {
            AEItemKey key = AEItemKey.of(requirement.stack());
            if (key != null && requirement.count() > 0) {
                combined.merge(key, (long) requirement.count(), AE2StorageCraftingBatch::saturatingAdd);
            }
        }
        List<Requirement> result = new ArrayList<>(combined.size());
        combined.forEach((key, amount) -> result.add(new Requirement(key, amount)));
        return result;
    }

    private static void addStacks(List<ItemStack> target, AEItemKey key, long amount) {
        while (amount > 0) {
            ItemStack unit = key.toStack();
            int count = (int) Math.min(amount, unit.getMaxStackSize());
            target.add(key.toStack(count));
            amount -= count;
        }
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        return result < 0 ? Long.MAX_VALUE : result;
    }
}
