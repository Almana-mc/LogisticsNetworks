package me.almana.logisticsnetworks.integration.refinedstorage;

import com.refinedmods.refinedstorage.api.autocrafting.calculation.CancellationToken;
import com.refinedmods.refinedstorage.api.autocrafting.status.TaskStatus;
import com.refinedmods.refinedstorage.api.autocrafting.status.TaskStatusListener;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskId;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.api.storage.PlayerActor;
import me.almana.logisticsnetworks.integration.storage.CraftingBatch;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import me.almana.logisticsnetworks.integration.storage.StorageAction;
import me.almana.logisticsnetworks.integration.storage.StorageFailure;
import me.almana.logisticsnetworks.integration.storage.StorageInventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class RefinedStorageCraftingBatch implements CraftingBatch, TaskStatusListener {

    private final RefinedStorageAccess access;
    private final ServerPlayer player;
    private final List<LinkedStorage.ItemRequirement> requirements;
    private final Listener listener;
    private final List<ItemStack> held = new ArrayList<>();
    private int requirementIndex;
    private TaskId taskId;
    private boolean returning;
    private boolean finished;

    private RefinedStorageCraftingBatch(RefinedStorageAccess access, ServerPlayer player,
                                        List<LinkedStorage.ItemRequirement> requirements, Listener listener) {
        this.access = access;
        this.player = player;
        this.requirements = StorageInventory.combine(requirements);
        this.listener = listener;
    }

    @Nullable
    static RefinedStorageCraftingBatch start(RefinedStorageAccess access, ServerPlayer player,
                                             List<LinkedStorage.ItemRequirement> requirements, Listener listener) {
        RefinedStorageCraftingBatch batch = new RefinedStorageCraftingBatch(access, player, requirements, listener);
        access.autocrafting().addListener(batch);
        if (!batch.startNext()) return null;
        return batch;
    }

    @Override
    public void tick() {
        if (finished || returning) return;
        if (!access.isValid()) {
            fail(StorageFailure.of(StorageFailure.Reason.NETWORK_UNAVAILABLE));
        } else if (!access.allows(player, StorageAction.AUTOCRAFT)
                || !access.allows(player, StorageAction.EXTRACT)) {
            fail(StorageFailure.of(StorageFailure.Reason.PERMISSION_DENIED));
        }
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public long held(ItemStack pattern) {
        return StorageInventory.count(held, pattern);
    }

    @Override
    public List<ItemStack> take(ItemStack pattern, int amount) {
        return StorageInventory.reserve(held, pattern, amount);
    }

    @Override
    public void cancel() {
        if (finished) return;
        returning = true;
        if (taskId != null) access.autocrafting().cancel(taskId);
        listener.failed(StorageFailure.of(StorageFailure.Reason.CANCELED));
        returnHeld();
    }

    @Override
    public void taskStatusChanged(TaskStatus status) {
    }

    @Override
    public void taskRemoved(TaskId id) {
        if (finished || returning || !id.equals(taskId)) return;
        taskId = null;
        LinkedStorage.ItemRequirement requirement = requirements.get(requirementIndex);
        List<ItemStack> extracted = access.extract(requirement.stack(), requirement.count(), player);
        held.addAll(extracted);
        if (StorageInventory.count(extracted, requirement.stack()) < requirement.count()) {
            fail(StorageFailure.of(StorageFailure.Reason.SUBMISSION));
            return;
        }
        requirementIndex++;
        startNext();
    }

    @Override
    public void taskAdded(TaskStatus status) {
    }

    private boolean startNext() {
        if (requirementIndex >= requirements.size()) {
            if (!listener.complete(this)) {
                fail(StorageFailure.of(StorageFailure.Reason.TARGET_INVALID));
                return false;
            }
            returning = !held.isEmpty();
            if (returning) returnHeld();
            else finish();
            return true;
        }
        LinkedStorage.ItemRequirement requirement = requirements.get(requirementIndex);
        ResourceKey resource = access.resource(requirement.stack());
        if (resource == null || !access.isCraftable(requirement.stack())) {
            fail(StorageFailure.of(StorageFailure.Reason.NO_PATTERN));
            return false;
        }
        try {
            taskId = access.autocrafting().startTask(resource, requirement.count(),
                    new PlayerActor(player), false, CancellationToken.NONE).orElse(null);
        } catch (RuntimeException exception) {
            fail(StorageFailure.of(StorageFailure.Reason.SUBMISSION));
            return false;
        }
        if (taskId == null) {
            fail(StorageFailure.of(StorageFailure.Reason.MISSING_ITEMS));
            return false;
        }
        return true;
    }

    private void fail(StorageFailure failure) {
        if (finished || returning) return;
        returning = true;
        if (taskId != null) access.autocrafting().cancel(taskId);
        taskId = null;
        listener.failed(failure);
        returnHeld();
    }

    private void returnHeld() {
        StorageInventory.returnToStorageOrPlayer(access, player, held);
        held.clear();
        finish();
    }

    private void finish() {
        access.autocrafting().removeListener(this);
        finished = true;
    }
}
