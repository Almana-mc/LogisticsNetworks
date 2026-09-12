package me.almana.logisticsnetworks.integration.storage;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.logic.NodePlacementHelper;
import me.almana.logisticsnetworks.network.SyncQueuedNodePlacementPayload;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class StorageNodePlacementRequests {

    private static final Map<QueueKey, PlacementGroup> GROUPS = new LinkedHashMap<>();
    private static final Map<UUID, List<Notice>> NOTICES = new LinkedHashMap<>();

    private StorageNodePlacementRequests() {
    }

    static LinkedStorage.NodePlacementRequestResult request(ServerPlayer player, GlobalPos target,
                                                             StorageLink link, boolean renderVisible) {
        if (isQueued(target)) return LinkedStorage.NodePlacementRequestResult.DUPLICATE;
        StorageAccess access = LinkedStorage.resolve(player.serverLevel(), link);
        ItemStack nodeStack = Registration.LOGISTICS_NODE_ITEM.get().getDefaultInstance();
        if (access == null) {
            sendFailure(player, link.backend(), 1, StorageFailure.of(StorageFailure.Reason.NETWORK_UNAVAILABLE));
            return LinkedStorage.NodePlacementRequestResult.UNAVAILABLE;
        }
        if (!access.allows(player, StorageAction.AUTOCRAFT)
                || !access.allows(player, StorageAction.EXTRACT)) {
            sendFailure(player, link.backend(), 1, StorageFailure.of(StorageFailure.Reason.PERMISSION_DENIED));
            return LinkedStorage.NodePlacementRequestResult.UNAVAILABLE;
        }
        if (!access.isCraftable(nodeStack)) {
            sendFailure(player, link.backend(), 1, StorageFailure.of(StorageFailure.Reason.NO_PATTERN));
            return LinkedStorage.NodePlacementRequestResult.UNAVAILABLE;
        }

        QueueKey queueKey = new QueueKey(player.getUUID(), link);
        PlacementGroup group = GROUPS.computeIfAbsent(queueKey,
                ignored -> new PlacementGroup(player.getServer(), player.getUUID(), access));
        if (!group.add(target, renderVisible)) return LinkedStorage.NodePlacementRequestResult.DUPLICATE;
        group.syncOutline(target, true);
        return LinkedStorage.NodePlacementRequestResult.QUEUED;
    }

    static boolean isQueued(GlobalPos target) {
        return GROUPS.values().stream().anyMatch(group -> group.queue.contains(target));
    }

    static void tick() {
        for (PlacementGroup group : new ArrayList<>(GROUPS.values())) group.tick();
        GROUPS.entrySet().removeIf(entry -> entry.getValue().isFinished());
    }

    static void stop() {
        for (PlacementGroup group : GROUPS.values()) group.cancel();
        GROUPS.clear();
        NOTICES.clear();
    }

    static void restore(ServerPlayer player) {
        List<Notice> notices = NOTICES.remove(player.getUUID());
        if (notices != null) {
            for (Notice notice : notices) {
                player.displayClientMessage(notice.hud(), true);
                player.sendSystemMessage(notice.detail());
            }
        }
        for (PlacementGroup group : GROUPS.values()) {
            if (group.playerId.equals(player.getUUID())) group.restoreOutlines();
        }
    }

    private static void sendFailure(ServerPlayer player, StorageBackend backend,
                                    int count, StorageFailure failure) {
        Component subject = Component.translatable("message.logisticsnetworks.storage.subject.nodes", count);
        player.sendSystemMessage(failure.detail(backend, subject));
    }

    private record QueueKey(UUID playerId, StorageLink link) {
    }

    private record Notice(Component hud, Component detail) {
    }

    private static final class PlacementGroup implements CraftingBatch.Listener {
        private final MinecraftServer server;
        private final UUID playerId;
        private final StorageAccess access;
        private final PlacementQueue<GlobalPos> queue = new PlacementQueue<>();
        private final Map<GlobalPos, Boolean> visibility = new LinkedHashMap<>();
        private List<GlobalPos> activeTargets = List.of();
        private CraftingBatch batch;

        private PlacementGroup(MinecraftServer server, UUID playerId, StorageAccess access) {
            this.server = server;
            this.playerId = playerId;
            this.access = access;
        }

        private boolean add(GlobalPos target, boolean renderVisible) {
            if (!queue.add(target)) return false;
            visibility.put(target, renderVisible);
            return true;
        }

        private void tick() {
            if (batch != null) {
                batch.tick();
                if (batch.isFinished()) batch = null;
            }
            if (batch != null) return;
            activeTargets = queue.startBatch();
            if (activeTargets.isEmpty()) return;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                failed(StorageFailure.of(StorageFailure.Reason.CANCELED));
                return;
            }
            ItemStack nodeStack = Registration.LOGISTICS_NODE_ITEM.get().getDefaultInstance();
            batch = access.startCrafting(player,
                    List.of(new LinkedStorage.ItemRequirement(nodeStack, activeTargets.size())), this);
        }

        @Override
        public boolean complete(CraftingBatch completed) {
            ItemStack nodeStack = Registration.LOGISTICS_NODE_ITEM.get().getDefaultInstance();
            int placed = 0;
            int returned = 0;
            for (GlobalPos target : activeTargets) {
                ServerLevel level = server.getLevel(target.dimension());
                boolean valid = level != null && level.hasChunkAt(target.pos())
                        && NodePlacementHelper.validatePlacement(level, target.pos())
                        == NodePlacementHelper.ValidationResult.OK;
                if (valid) {
                    LogisticsNodeEntity node = NodePlacementHelper.placeNode(level, target.pos(), playerId);
                    List<ItemStack> crafted = node == null ? List.of() : completed.take(nodeStack, 1);
                    if (node != null && StorageInventory.count(crafted) == 1) {
                        node.setRenderVisible(visibility.getOrDefault(target, true));
                        level.playSound(null, target.pos(), SoundEvents.METAL_PLACE,
                                SoundSource.BLOCKS, 1.0F, 1.0F);
                        placed++;
                    } else if (node != null) {
                        node.discard();
                        returned++;
                    } else {
                        returned++;
                    }
                } else {
                    returned++;
                }
                finishTarget(target);
            }
            Component hud = Component.translatable("message.logisticsnetworks.wrench.place_node.crafted_batch", placed);
            Component detail = returned > 0
                    ? StorageFailure.of(StorageFailure.Reason.TARGET_INVALID).detail(access.link().backend(),
                    Component.translatable("message.logisticsnetworks.storage.subject.nodes", returned))
                    : hud;
            notifyPlayer(new Notice(hud, detail), returned > 0);
            activeTargets = List.of();
            return true;
        }

        @Override
        public void failed(StorageFailure failure) {
            int count = activeTargets.size();
            for (GlobalPos target : activeTargets) finishTarget(target);
            Component hud = Component.translatable("message.logisticsnetworks.wrench.place_node.crafting_failed");
            Component subject = Component.translatable("message.logisticsnetworks.storage.subject.nodes", count);
            notifyPlayer(new Notice(hud, failure.detail(access.link().backend(), subject)), true);
            activeTargets = List.of();
        }

        private void finishTarget(GlobalPos target) {
            queue.complete(target);
            visibility.remove(target);
            syncOutline(target, false);
        }

        private void cancel() {
            if (batch != null) batch.cancel();
            for (GlobalPos target : new ArrayList<>(visibility.keySet())) finishTarget(target);
        }

        private boolean isFinished() {
            return batch == null && queue.isEmpty();
        }

        private void restoreOutlines() {
            for (GlobalPos target : visibility.keySet()) syncOutline(target, true);
        }

        private void notifyPlayer(Notice notice, boolean detailed) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.displayClientMessage(notice.hud(), true);
                if (detailed) player.sendSystemMessage(notice.detail());
            } else {
                NOTICES.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(notice);
            }
        }

        private void syncOutline(GlobalPos target, boolean queued) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                PacketDistributor.sendToPlayer(player, new SyncQueuedNodePlacementPayload(
                        target.dimension().location(), target.pos(), queued));
            }
        }
    }
}
