package me.almana.logisticsnetworks.integration.storage;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class StorageSupplyRequests {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Request> ACTIVE = new LinkedHashMap<>();

    private StorageSupplyRequests() {
    }

    static LinkedStorage.SupplyRequestResult request(UUID requestId, ServerPlayer player, StorageLink link,
                                                      List<LinkedStorage.ItemRequirement> requested,
                                                      int protectedSlot, Component subject,
                                                      LinkedStorage.SupplyOperation operation) {
        if (ACTIVE.containsKey(requestId)) return LinkedStorage.SupplyRequestResult.DUPLICATE;
        StorageAccess access = LinkedStorage.resolve(player.serverLevel(), link);
        if (access == null) {
            operation.failed(StorageFailure.of(StorageFailure.Reason.NETWORK_UNAVAILABLE)
                    .detail(link.backend(), subject));
            return LinkedStorage.SupplyRequestResult.UNAVAILABLE;
        }
        if (!access.allows(player, StorageAction.EXTRACT)) {
            operation.failed(StorageFailure.of(StorageFailure.Reason.PERMISSION_DENIED)
                    .detail(link.backend(), subject));
            return LinkedStorage.SupplyRequestResult.UNAVAILABLE;
        }

        Request request = new Request(player, access, StorageInventory.combine(requested),
                protectedSlot, subject, operation);
        List<LinkedStorage.ItemRequirement> crafting = request.reserve();
        if (request.failure != null) {
            request.fail(request.failure);
            return LinkedStorage.SupplyRequestResult.UNAVAILABLE;
        }
        if (crafting.isEmpty()) {
            request.completeWithoutCrafting();
            return request.succeeded
                    ? LinkedStorage.SupplyRequestResult.COMPLETED
                    : LinkedStorage.SupplyRequestResult.UNAVAILABLE;
        }
        if (!access.allows(player, StorageAction.AUTOCRAFT)) {
            request.fail(StorageFailure.of(StorageFailure.Reason.PERMISSION_DENIED));
            return LinkedStorage.SupplyRequestResult.UNAVAILABLE;
        }
        request.batch = access.startCrafting(player, crafting, request);
        if (request.batch == null) {
            request.rollback();
            return LinkedStorage.SupplyRequestResult.UNAVAILABLE;
        }
        ACTIVE.put(requestId, request);
        return LinkedStorage.SupplyRequestResult.QUEUED;
    }

    static boolean isPending(UUID requestId) {
        return ACTIVE.containsKey(requestId);
    }

    static void tick() {
        for (Request request : new ArrayList<>(ACTIVE.values())) request.tick();
        ACTIVE.entrySet().removeIf(entry -> entry.getValue().finished());
    }

    static void stop() {
        for (Request request : ACTIVE.values()) request.cancel();
        ACTIVE.clear();
    }

    private static final class Request implements CraftingBatch.Listener {
        private final ServerPlayer player;
        private final StorageAccess access;
        private final List<LinkedStorage.ItemRequirement> requirements;
        private final int protectedSlot;
        private final Component subject;
        private final LinkedStorage.SupplyOperation operation;
        private final List<ItemStack> inventoryReserved = new ArrayList<>();
        private final List<ItemStack> storageReserved = new ArrayList<>();
        private final List<ItemStack> craftedReserved = new ArrayList<>();
        private CraftingBatch batch;
        private StorageFailure failure;
        private boolean succeeded;
        private boolean notified;

        private Request(ServerPlayer player, StorageAccess access,
                        List<LinkedStorage.ItemRequirement> requirements, int protectedSlot,
                        Component subject, LinkedStorage.SupplyOperation operation) {
            this.player = player;
            this.access = access;
            this.requirements = requirements;
            this.protectedSlot = protectedSlot;
            this.subject = subject;
            this.operation = operation;
        }

        private List<LinkedStorage.ItemRequirement> reserve() {
            if (!operation.stillValid()) {
                failure = StorageFailure.of(StorageFailure.Reason.TARGET_INVALID);
                return List.of();
            }
            List<LinkedStorage.ItemRequirement> crafting = new ArrayList<>();
            for (LinkedStorage.ItemRequirement requirement : requirements) {
                List<ItemStack> fromInventory = StorageInventory.reserve(
                        player.getInventory(), requirement.stack(), requirement.count(), protectedSlot);
                inventoryReserved.addAll(fromInventory);
                int remaining = requirement.count() - StorageInventory.count(fromInventory);
                List<ItemStack> fromStorage = access.extract(requirement.stack(), remaining, player);
                storageReserved.addAll(fromStorage);
                remaining -= StorageInventory.count(fromStorage);
                if (remaining <= 0) continue;
                if (!access.isCraftable(requirement.stack())) {
                    failure = StorageFailure.of(StorageFailure.Reason.NO_PATTERN);
                    rollback();
                    return List.of();
                }
                crafting.add(new LinkedStorage.ItemRequirement(requirement.stack(), remaining));
            }
            return crafting;
        }

        private void tick() {
            if (batch != null) batch.tick();
        }

        private boolean finished() {
            return batch == null || batch.isFinished();
        }

        private void cancel() {
            if (batch != null) batch.cancel();
            else rollback();
        }

        private void completeWithoutCrafting() {
            if (!operation.stillValid()) {
                fail(StorageFailure.of(StorageFailure.Reason.TARGET_INVALID));
                return;
            }
            try {
                if (operation.commit()) {
                    succeeded = true;
                    clearReserved();
                    return;
                }
                failure = StorageFailure.of(StorageFailure.Reason.TARGET_INVALID);
            } catch (RuntimeException exception) {
                LOGGER.error("Storage supply operation failed", exception);
                failure = StorageFailure.of(StorageFailure.Reason.SUBMISSION);
            }
            fail(failure);
        }

        @Override
        public boolean complete(CraftingBatch completed) {
            if (!operation.stillValid()) {
                failure = StorageFailure.of(StorageFailure.Reason.TARGET_INVALID);
                rollback();
                return false;
            }
            for (LinkedStorage.ItemRequirement requirement : requirements) {
                int reserved = matchingReserved(requirement.stack());
                int needed = Math.max(0, requirement.count() - reserved);
                List<ItemStack> crafted = completed.take(requirement.stack(), needed);
                craftedReserved.addAll(crafted);
                if (StorageInventory.count(crafted) != needed) {
                    failure = StorageFailure.of(StorageFailure.Reason.SUBMISSION);
                    rollback();
                    return false;
                }
            }
            try {
                if (operation.commit()) {
                    succeeded = true;
                    clearReserved();
                    return true;
                }
                failure = StorageFailure.of(StorageFailure.Reason.TARGET_INVALID);
            } catch (RuntimeException exception) {
                LOGGER.error("Storage supply operation failed", exception);
                failure = StorageFailure.of(StorageFailure.Reason.SUBMISSION);
            }
            rollback();
            return false;
        }

        @Override
        public void failed(StorageFailure failure) {
            fail(this.failure == null ? failure : this.failure);
        }

        private int matchingReserved(ItemStack pattern) {
            return StorageInventory.count(inventoryReserved, pattern)
                    + StorageInventory.count(storageReserved, pattern);
        }

        private void fail(StorageFailure failure) {
            if (notified) return;
            rollback();
            notified = true;
            operation.failed(failure.detail(access.link().backend(), subject));
        }

        private void rollback() {
            StorageInventory.returnToPlayer(player, inventoryReserved);
            StorageInventory.returnToStorageOrPlayer(access, player, storageReserved);
            StorageInventory.returnToStorageOrPlayer(access, player, craftedReserved);
            clearReserved();
        }

        private void clearReserved() {
            inventoryReserved.clear();
            storageReserved.clear();
            craftedReserved.clear();
        }
    }
}
