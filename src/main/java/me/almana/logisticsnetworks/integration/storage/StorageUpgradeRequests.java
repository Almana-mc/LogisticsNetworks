package me.almana.logisticsnetworks.integration.storage;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.logic.LabelUpgradeSync;
import me.almana.logisticsnetworks.network.ServerPayloadHandler;
import me.almana.logisticsnetworks.registration.ModTags;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class StorageUpgradeRequests {

    private static final List<UpgradeRequest> REQUESTS = new ArrayList<>();
    private static final Map<UUID, List<Notice>> NOTICES = new HashMap<>();

    private StorageUpgradeRequests() {
    }

    static List<LinkedStorage.UpgradeEntry> list(ServerPlayer player, StorageLink link,
                                                  List<ItemStack> installed) {
        StorageAccess access = LinkedStorage.resolve(player.serverLevel(), link);
        if (access == null || !access.allows(player, StorageAction.EXTRACT)) return List.of();
        Set<Item> installedItems = new HashSet<>();
        for (ItemStack stack : installed) {
            if (!stack.isEmpty()) installedItems.add(stack.getItem());
        }
        Map<Item, Long> stored = access.countTagged(ModTags.UPGRADES);
        Set<Item> craftable = access.allows(player, StorageAction.AUTOCRAFT)
                ? access.craftableTagged(ModTags.UPGRADES) : Set.of();
        List<LinkedStorage.UpgradeEntry> entries = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(ModTags.UPGRADES).ifPresent(upgrades -> {
            for (Holder<Item> holder : upgrades) {
                Item item = holder.value();
                long count = stored.getOrDefault(item, 0L);
                boolean canCraft = craftable.contains(item);
                if (!installedItems.contains(item) && (count > 0 || canCraft)) {
                    entries.add(new LinkedStorage.UpgradeEntry(item.getDefaultInstance(), count, canCraft));
                }
            }
        });
        entries.sort(Comparator.comparing((LinkedStorage.UpgradeEntry entry) -> entry.stored() == 0)
                .thenComparing(entry -> BuiltInRegistries.ITEM.getKey(entry.item().getItem()).toString()));
        return entries;
    }

    static LinkedStorage.UpgradeInstallResult install(ServerPlayer player, LogisticsNodeEntity node,
                                                       StorageLink link, int preferredSlot, Item upgrade) {
        for (UpgradeRequest request : REQUESTS) {
            if (!request.finished() && request.nodeId.equals(node.getUUID())) {
                return LinkedStorage.UpgradeInstallResult.PENDING;
            }
        }
        List<ItemStack> installed = installedUpgrades(node);
        int slot = LinkedStorage.findUpgradeSlot(installed, preferredSlot, upgrade);
        if (slot < 0) {
            return containsUpgrade(installed, upgrade)
                    ? LinkedStorage.UpgradeInstallResult.DUPLICATE
                    : LinkedStorage.UpgradeInstallResult.NO_SLOT;
        }
        StorageAccess access = LinkedStorage.resolve(player.serverLevel(), link);
        if (access == null) {
            sendImmediateFailure(player, node, upgrade, link.backend(),
                    StorageFailure.of(StorageFailure.Reason.NETWORK_UNAVAILABLE));
            return LinkedStorage.UpgradeInstallResult.UNAVAILABLE;
        }
        if (!access.allows(player, StorageAction.EXTRACT)) {
            sendImmediateFailure(player, node, upgrade, link.backend(),
                    StorageFailure.of(StorageFailure.Reason.PERMISSION_DENIED));
            return LinkedStorage.UpgradeInstallResult.UNAVAILABLE;
        }
        ItemStack extracted = access.extractOne(upgrade, player);
        if (!extracted.isEmpty()) {
            node.setUpgradeItem(slot, extracted);
            ServerPayloadHandler.invalidateNetwork(node);
            return LinkedStorage.UpgradeInstallResult.INSTALLED;
        }
        if (!access.allows(player, StorageAction.AUTOCRAFT)) {
            sendImmediateFailure(player, node, upgrade, link.backend(),
                    StorageFailure.of(StorageFailure.Reason.PERMISSION_DENIED));
            return LinkedStorage.UpgradeInstallResult.UNAVAILABLE;
        }
        if (!access.isCraftable(upgrade.getDefaultInstance())) {
            sendImmediateFailure(player, node, upgrade, link.backend(),
                    StorageFailure.of(StorageFailure.Reason.NO_PATTERN));
            return LinkedStorage.UpgradeInstallResult.UNAVAILABLE;
        }
        UpgradeRequest request = new UpgradeRequest(player, node, access, preferredSlot, upgrade);
        request.batch = access.startCrafting(player,
                List.of(new LinkedStorage.ItemRequirement(upgrade.getDefaultInstance(), 1)), request);
        if (request.batch == null) return LinkedStorage.UpgradeInstallResult.UNAVAILABLE;
        REQUESTS.add(request);
        return LinkedStorage.UpgradeInstallResult.CRAFTING;
    }

    static boolean isPending(LogisticsNodeEntity node) {
        return REQUESTS.stream().anyMatch(request -> !request.finished() && request.nodeId.equals(node.getUUID()));
    }

    static void tick(MinecraftServer server) {
        for (UpgradeRequest request : new ArrayList<>(REQUESTS)) request.tick();
        REQUESTS.removeIf(UpgradeRequest::finished);
    }

    static void stop() {
        for (UpgradeRequest request : REQUESTS) request.cancel();
        REQUESTS.clear();
        NOTICES.clear();
    }

    static void deliverNotices(ServerPlayer player) {
        List<Notice> notices = NOTICES.remove(player.getUUID());
        if (notices == null) return;
        for (Notice notice : notices) {
            player.displayClientMessage(notice.hud(), true);
            player.sendSystemMessage(notice.detail());
        }
    }

    private static List<ItemStack> installedUpgrades(LogisticsNodeEntity node) {
        return LabelUpgradeSync.snapshotUpgrades(node);
    }

    private static boolean containsUpgrade(List<ItemStack> installed, Item upgrade) {
        return installed.stream().anyMatch(stack -> !stack.isEmpty() && stack.is(upgrade));
    }

    private static void sendImmediateFailure(ServerPlayer player, LogisticsNodeEntity node, Item upgrade,
                                             StorageBackend backend, StorageFailure failure) {
        player.sendSystemMessage(failure.detail(backend, upgradeSubject(upgrade, node)));
    }

    private static Component upgradeSubject(Item upgrade, LogisticsNodeEntity node) {
        Component nodeName = node.getNodeLabel().isBlank()
                ? Component.literal(node.blockPosition().toShortString())
                : Component.literal(node.getNodeLabel());
        return Component.translatable("message.logisticsnetworks.storage.subject.upgrade",
                upgrade.getDescription(), nodeName);
    }

    private record Notice(Component hud, Component detail) {
    }

    private static final class UpgradeRequest implements CraftingBatch.Listener {
        private final MinecraftServer server;
        private final UUID playerId;
        private final UUID nodeId;
        private final StorageAccess access;
        private final int preferredSlot;
        private final Item upgrade;
        private final List<ItemStack> original;
        private CraftingBatch batch;

        private UpgradeRequest(ServerPlayer player, LogisticsNodeEntity node, StorageAccess access,
                               int preferredSlot, Item upgrade) {
            this.server = player.getServer();
            this.playerId = player.getUUID();
            this.nodeId = node.getUUID();
            this.access = access;
            this.preferredSlot = preferredSlot;
            this.upgrade = upgrade;
            this.original = LabelUpgradeSync.snapshotUpgrades(node);
        }

        private void tick() {
            if (batch != null) batch.tick();
        }

        private boolean finished() {
            return batch == null || batch.isFinished();
        }

        private void cancel() {
            if (batch != null) batch.cancel();
        }

        @Override
        public boolean complete(CraftingBatch completed) {
            LogisticsNodeEntity node = findNode();
            if (node == null) return false;
            int slot = LinkedStorage.findUpgradeSlot(installedUpgrades(node), preferredSlot, upgrade);
            if (slot < 0) return false;
            List<ItemStack> crafted = completed.take(upgrade.getDefaultInstance(), 1);
            if (StorageInventory.count(crafted) != 1) return false;
            node.setUpgradeItem(slot, crafted.getFirst().copyWithCount(1));
            ServerPayloadHandler.invalidateNetwork(node);
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                boolean labelSync = LabelUpgradeSync.synchronizeCraftedUpgrade(
                        player, node, original, access.link());
                if (!labelSync) {
                    player.displayClientMessage(Component.translatable(
                            "message.logisticsnetworks.storage.upgrade.complete",
                            upgrade.getDescription(), nodeName(node)), true);
                }
            }
            return true;
        }

        @Override
        public void failed(StorageFailure failure) {
            LogisticsNodeEntity node = findNode();
            Component subject = node == null
                    ? Component.translatable("message.logisticsnetworks.storage.subject.upgrade_unknown",
                    upgrade.getDescription())
                    : upgradeSubject(upgrade, node);
            Notice notice = new Notice(
                    Component.translatable("message.logisticsnetworks.storage.upgrade.failed",
                            upgrade.getDescription(), node == null ? "?" : nodeName(node)),
                    failure.detail(access.link().backend(), subject));
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.displayClientMessage(notice.hud(), true);
                player.sendSystemMessage(notice.detail());
            } else {
                NOTICES.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(notice);
            }
        }

        private LogisticsNodeEntity findNode() {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(nodeId);
                if (entity instanceof LogisticsNodeEntity node && node.isAlive() && node.isValidNode()) return node;
            }
            return null;
        }

        private Component nodeName(LogisticsNodeEntity node) {
            return node.getNodeLabel().isBlank()
                    ? Component.literal(node.blockPosition().toShortString())
                    : Component.literal(node.getNodeLabel());
        }
    }
}
