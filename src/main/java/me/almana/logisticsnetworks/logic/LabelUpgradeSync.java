package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.LabelUpgradeTemplate;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.data.graph.GraphPosition;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import me.almana.logisticsnetworks.integration.storage.StorageInventory;
import me.almana.logisticsnetworks.integration.storage.StorageLink;
import me.almana.logisticsnetworks.network.GraphPayloadHandler;
import me.almana.logisticsnetworks.network.ServerPayloadHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LabelUpgradeSync {

    private static final Set<UUID> PENDING_NODES = new HashSet<>();

    private LabelUpgradeSync() {
    }

    public static boolean isPending(LogisticsNodeEntity node) {
        return PENDING_NODES.contains(node.getUUID());
    }

    public static List<ItemStack> snapshotUpgrades(LogisticsNodeEntity node) {
        List<ItemStack> snapshot = new ArrayList<>(LogisticsNodeEntity.UPGRADE_SLOT_COUNT);
        for (int slot = 0; slot < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; slot++) {
            ItemStack stack = node.getUpgradeItem(slot);
            snapshot.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }
        return snapshot;
    }

    public static void synchronizeMenuClose(ServerPlayer player, LogisticsNodeEntity source,
                                            List<ItemStack> original, @Nullable StorageLink storageLink) {
        synchronizeLoaded(player, source, original, storageLink, false);
    }

    public static boolean synchronizeCraftedUpgrade(ServerPlayer player, LogisticsNodeEntity source,
                                                     List<ItemStack> original, StorageLink storageLink) {
        return synchronizeLoaded(player, source, original, storageLink, true);
    }

    public static boolean synchronizeLabels(ServerPlayer player, LogisticsNetwork network,
                                            List<LogisticsNodeEntity> targets, UUID sourceId,
                                            String label, @Nullable StorageLink storageLink) {
        if (targets.isEmpty() || targets.stream().anyMatch(node -> PENDING_NODES.contains(node.getUUID()))) {
            return false;
        }
        if (label.isEmpty()) {
            Map<UUID, GraphPosition> splitPositions = bulkUnlabelPositions(network, targets);
            for (LogisticsNodeEntity target : targets) {
                target.setNodeLabel("");
                GraphPosition position = splitPositions.get(target.getUUID());
                if (position != null) network.setGraphPosition("node:" + target.getUUID(), position);
            }
            NetworkRegistry.get(player.level()).setDirty();
            NetworkRegistry.get(player.level()).invalidateNetwork(network.getId());
            GraphPayloadHandler.broadcast(player.level().getServer(), network.getId());
            GraphPayloadHandler.refreshTable(player, network.getId());
            return true;
        }

        LabelUpgradeTemplate storedTemplate = network.getLabelTemplate(label);
        LogisticsNodeEntity authority = findLabelAuthority(player.level().getServer(), network, label, storedTemplate);
        if (storedTemplate == null && authority == null) {
            authority = targets.stream().filter(node -> node.getUUID().equals(sourceId)).findFirst().orElse(null);
        }
        if (storedTemplate == null && authority == null) return false;

        LabelUpgradeTemplate template = storedTemplate;
        if (authority != null && (storedTemplate == null
                || authority.getLabelRevision() != storedTemplate.revision()
                || !sameUpgrades(authority, storedTemplate.upgrades())
                || !sameChannels(authority, storedTemplate.channels()))) {
            StorageLink templateLink = storedTemplate != null && storedTemplate.storageLink() != null
                    ? storedTemplate.storageLink() : storageLink;
            long revision = Math.max(nextRevision(network, label), authority.getLabelRevision() + 1);
            template = new LabelUpgradeTemplate(revision, player.getUUID(), templateLink,
                    snapshotUpgrades(authority), snapshotChannels(authority));
        }

        Map<UUID, BulkNodeState> snapshots = new LinkedHashMap<>();
        Map<UUID, UpgradeChanges> changes = new LinkedHashMap<>();
        List<Requirement> required = new ArrayList<>();
        for (LogisticsNodeEntity target : targets) {
            snapshots.put(target.getUUID(), new BulkNodeState(target.getNodeLabel(), target.getLabelRevision(),
                    snapshotUpgrades(target), snapshotChannels(target)));
            UpgradeChanges targetChanges = getUpgradeChanges(target, template.upgrades());
            changes.put(target.getUUID(), targetChanges);
            mergeRequirements(required, targetChanges.required());
        }

        LabelUpgradeTemplate appliedTemplate = template;
        LogisticsNodeEntity labelAuthority = authority;
        BulkNodeState authoritySnapshot = authority == null ? null : new BulkNodeState(authority.getNodeLabel(),
                authority.getLabelRevision(), snapshotUpgrades(authority), snapshotChannels(authority));
        boolean refreshesAuthority = authority != null && label.equals(authority.getNodeLabel())
                && appliedTemplate != storedTemplate;
        GraphPosition labelPosition = bulkLabelPosition(network, targets, label);
        StorageLink supplyLink = template.storageLink() != null ? template.storageLink() : storageLink;
        Set<UUID> pending = new HashSet<>(snapshots.keySet());
        if (authority != null) pending.add(authority.getUUID());
        PENDING_NODES.addAll(pending);
        Runnable release = () -> PENDING_NODES.removeAll(pending);
        Runnable commit = () -> {
            for (LogisticsNodeEntity target : targets) {
                GraphPayloadHandler.preserveLabelPosition(target, label);
                target.setNodeLabel(label);
                applyTemplate(target, appliedTemplate);
                target.setLabelRevision(appliedTemplate.revision());
                returnItems(player, changes.get(target.getUUID()).returned(), null);
            }
            if (refreshesAuthority) labelAuthority.setLabelRevision(appliedTemplate.revision());
            if (labelPosition != null) network.setGraphPosition("label:" + label, labelPosition);
            network.setLabelTemplate(label, appliedTemplate);
            NetworkRegistry registry = NetworkRegistry.get(player.level());
            registry.setDirty();
            registry.invalidateNetwork(network.getId());
            GraphPayloadHandler.broadcast(player.level().getServer(), network.getId());
            GraphPayloadHandler.refreshTable(player, network.getId());
            player.sendSystemMessage(Component.translatable(
                    "message.logisticsnetworks.label.synced", targets.size()), true);
            release.run();
        };
        java.util.function.BooleanSupplier stillValid = () -> validBulkTargets(
                player, network, label, storedTemplate, targets, snapshots, pending,
                labelAuthority, authoritySnapshot);
        List<LinkedStorage.ItemRequirement> requirements = toRequirements(required);
        if (requirements.isEmpty()) {
            if (stillValid.getAsBoolean()) commit.run();
            else release.run();
            return true;
        }
        if (hasInventory(player.getInventory(), requirements, -1)) {
            consumeInventory(player.getInventory(), requirements, -1);
            commit.run();
            return true;
        }
        if (supplyLink == null) {
            reportFailure(player, label, Component.translatable(
                    "message.logisticsnetworks.label.missing_upgrades", formatRequirements(requirements)));
            release.run();
            return false;
        }

        UUID requestId = UUID.randomUUID();
        Component subject = labelSubject(label, targets.size());
        LinkedStorage.requestSupply(requestId, player, supplyLink, requirements, -1, subject,
                new LinkedStorage.SupplyOperation() {
                    @Override
                    public boolean stillValid() {
                        return stillValid.getAsBoolean();
                    }

                    @Override
                    public boolean commit() {
                        commit.run();
                        return true;
                    }

                    @Override
                    public void failed(Component detail) {
                        reportFailure(player, label, detail);
                        release.run();
                    }
                });
        return true;
    }

    public static void synchronizeOnLoad(LogisticsNodeEntity node) {
        if (!(node.level() instanceof ServerLevel level) || node.getNetworkId() == null
                || node.getNodeLabel().isBlank() || PENDING_NODES.contains(node.getUUID())) return;
        LogisticsNetwork network = NetworkRegistry.get(level).getNetwork(node.getNetworkId());
        if (network == null) return;
        LabelUpgradeTemplate template = network.getLabelTemplate(node.getNodeLabel());
        if (template == null || node.getLabelRevision() >= template.revision()) return;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(template.playerId());
        if (player == null) return;
        UUID expectedNetworkId = node.getNetworkId();
        String expectedLabel = node.getNodeLabel();

        List<ItemStack> current = snapshotUpgrades(node);
        UpgradeChanges changes = getUpgradeChanges(node, template.upgrades());
        List<LinkedStorage.ItemRequirement> requirements = toRequirements(changes.required());
        PENDING_NODES.add(node.getUUID());
        Runnable release = () -> PENDING_NODES.remove(node.getUUID());
        if (requirements.isEmpty()) {
            applyLoadedTemplate(player, node, template, changes.returned());
            release.run();
            return;
        }

        int protectedSlot = -1;
        if (hasInventory(player.getInventory(), requirements, protectedSlot)) {
            consumeInventory(player.getInventory(), requirements, protectedSlot);
            applyLoadedTemplate(player, node, template, changes.returned());
            release.run();
            return;
        }
        StorageLink link = template.storageLink();
        if (link == null) {
            reportFailure(player, node.getNodeLabel(), Component.translatable(
                    "message.logisticsnetworks.label.missing_upgrades", formatRequirements(requirements)));
            release.run();
            return;
        }

        UUID requestId = UUID.randomUUID();
        Component subject = labelSubject(expectedLabel, 1);
        LinkedStorage.requestSupply(requestId, player, link, requirements, protectedSlot, subject,
                new LinkedStorage.SupplyOperation() {
                    @Override
                    public boolean stillValid() {
                        return validLoadedTarget(node, template, expectedNetworkId, expectedLabel, current);
                    }

                    @Override
                    public boolean commit() {
                        applyLoadedTemplate(player, node, template, changes.returned());
                        release.run();
                        return true;
                    }

                    @Override
                    public void failed(Component detail) {
                        reportFailure(player, expectedLabel, detail);
                        release.run();
                    }
                });
    }

    private static boolean synchronizeLoaded(ServerPlayer player, LogisticsNodeEntity source,
                                             List<ItemStack> original, @Nullable StorageLink storageLink,
                                             boolean returnSourceToStorage) {
        if (sameUpgrades(source, original)) {
            ServerPayloadHandler.invalidateNetwork(source);
            return false;
        }
        if (!(source.level() instanceof ServerLevel level) || source.getNetworkId() == null
                || source.getNodeLabel().isBlank() || PENDING_NODES.contains(source.getUUID())) {
            ServerPayloadHandler.invalidateNetwork(source);
            return false;
        }
        LogisticsNetwork network = NetworkRegistry.get(level).getNetwork(source.getNetworkId());
        if (network == null) return false;
        UUID expectedNetworkId = source.getNetworkId();
        String expectedLabel = source.getNodeLabel();
        LabelUpgradeTemplate currentTemplate = network.getLabelTemplate(expectedLabel);
        if (currentTemplate != null && source.getLabelRevision() == currentTemplate.revision()
                && sameUpgrades(source, currentTemplate.upgrades())) {
            ServerPayloadHandler.invalidateNetwork(source);
            return false;
        }
        List<LogisticsNodeEntity> targets = findLoadedTargets(level.getServer(), network, source);
        List<ItemStack> desired = snapshotUpgrades(source);
        List<ChannelData> channels = snapshotChannels(source);
        Map<UUID, UpgradeChanges> changes = new LinkedHashMap<>();
        Map<UUID, List<ItemStack>> targetSnapshots = new LinkedHashMap<>();
        List<Requirement> required = new ArrayList<>();
        for (LogisticsNodeEntity target : targets) {
            targetSnapshots.put(target.getUUID(), snapshotUpgrades(target));
            UpgradeChanges targetChanges = getUpgradeChanges(target, desired);
            changes.put(target.getUUID(), targetChanges);
            mergeRequirements(required, targetChanges.required());
        }
        List<ItemStack> rollbackEscrow = reserveRollbackItems(player, source, original, storageLink);
        List<LinkedStorage.ItemRequirement> requirements = toRequirements(required);
        Set<UUID> pending = new HashSet<>();
        pending.add(source.getUUID());
        targets.forEach(target -> pending.add(target.getUUID()));
        PENDING_NODES.addAll(pending);
        Runnable release = () -> PENDING_NODES.removeAll(pending);

        Runnable commit = () -> {
            long revision = nextRevision(network, expectedLabel);
            LabelUpgradeTemplate template = new LabelUpgradeTemplate(
                    revision, player.getUUID(), storageLink, desired, channels);
            for (LogisticsNodeEntity target : targets) {
                applyTemplate(target, template);
                returnItems(player, changes.get(target.getUUID()).returned(), null);
                target.setLabelRevision(revision);
            }
            applyTemplate(source, template);
            source.setLabelRevision(revision);
            network.setLabelTemplate(expectedLabel, template);
            returnItems(player, rollbackEscrow, null);
            NetworkRegistry registry = NetworkRegistry.get(level);
            registry.setDirty();
            registry.invalidateNetwork(expectedNetworkId);
            player.sendSystemMessage(Component.translatable(
                    "message.logisticsnetworks.label.synced", targets.size() + 1), true);
            release.run();
        };
        java.util.function.Consumer<Component> fail = detail -> {
            rollbackSource(player, source, original, rollbackEscrow,
                    returnSourceToStorage ? storageLink : null);
            reportFailure(player, expectedLabel, detail);
            release.run();
        };

        if (requirements.isEmpty()) {
            commit.run();
            return true;
        }
        int protectedSlot = -1;
        if (hasInventory(player.getInventory(), requirements, protectedSlot)) {
            consumeInventory(player.getInventory(), requirements, protectedSlot);
            commit.run();
            return true;
        }
        if (storageLink == null) {
            fail.accept(Component.translatable(
                    "message.logisticsnetworks.label.missing_upgrades", formatRequirements(requirements)));
            return true;
        }

        UUID requestId = UUID.randomUUID();
        Component subject = labelSubject(expectedLabel, targets.size() + 1);
        LinkedStorage.requestSupply(requestId, player, storageLink, requirements, protectedSlot, subject,
                new LinkedStorage.SupplyOperation() {
                    @Override
                    public boolean stillValid() {
                        return validLoadedGroup(level.getServer(), source, desired, targets,
                                targetSnapshots, expectedNetworkId, expectedLabel);
                    }

                    @Override
                    public boolean commit() {
                        commit.run();
                        return true;
                    }

                    @Override
                    public void failed(Component detail) {
                        fail.accept(detail);
                    }
                });
        return true;
    }

    private static boolean validLoadedGroup(MinecraftServer server, LogisticsNodeEntity source,
                                            List<ItemStack> desired, List<LogisticsNodeEntity> targets,
                                            Map<UUID, List<ItemStack>> targetSnapshots,
                                            UUID networkId, String label) {
        if (!source.isAlive() || !sameUpgrades(source, desired)
                || !label.equals(source.getNodeLabel())
                || !networkId.equals(source.getNetworkId())) return false;
        for (LogisticsNodeEntity target : targets) {
            LogisticsNodeEntity loaded = findNode(server, target.getUUID());
            if (loaded != target || !label.equals(target.getNodeLabel())
                    || !java.util.Objects.equals(networkId, target.getNetworkId())
                    || !sameUpgrades(target, targetSnapshots.get(target.getUUID()))) return false;
        }
        return true;
    }

    private static boolean validLoadedTarget(LogisticsNodeEntity node, LabelUpgradeTemplate template,
                                             UUID networkId, String label, List<ItemStack> current) {
        return node.isAlive() && !node.getNodeLabel().isBlank()
                && label.equals(node.getNodeLabel()) && networkId.equals(node.getNetworkId())
                && node.getLabelRevision() < template.revision() && sameUpgrades(node, current);
    }

    private static void applyLoadedTemplate(ServerPlayer player, LogisticsNodeEntity node,
                                            LabelUpgradeTemplate template, List<ItemStack> returned) {
        applyTemplate(node, template);
        node.setLabelRevision(template.revision());
        returnItems(player, returned, null);
        if (node.level() instanceof ServerLevel level && node.getNetworkId() != null) {
            NetworkRegistry registry = NetworkRegistry.get(level);
            registry.setDirty();
            registry.invalidateNetwork(node.getNetworkId());
        }
        player.sendSystemMessage(Component.translatable(
                "message.logisticsnetworks.label.loaded_synced", node.getNodeLabel()), true);
    }

    private static void applyTemplate(LogisticsNodeEntity node, LabelUpgradeTemplate template) {
        List<ItemStack> upgrades = template.upgrades();
        for (int slot = 0; slot < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; slot++) {
            node.setUpgradeItem(slot, slot < upgrades.size() ? upgrades.get(slot) : ItemStack.EMPTY);
        }
        List<ChannelData> channels = template.channels();
        for (int index = 0; index < Math.min(channels.size(), LogisticsNodeEntity.CHANNEL_COUNT); index++) {
            ChannelData target = node.getChannel(index);
            if (target == null) continue;
            target.copyFrom(channels.get(index));
            ServerPayloadHandler.clampChannelToUpgradeLimits(node, target);
            ServerPayloadHandler.sendChannelSyncToViewers(node, index, target);
        }
    }

    private static List<LogisticsNodeEntity> findLoadedTargets(MinecraftServer server,
                                                                LogisticsNetwork network,
                                                                LogisticsNodeEntity source) {
        List<LogisticsNodeEntity> targets = new ArrayList<>();
        String label = source.getNodeLabel();
        for (UUID nodeId : network.getNodeUuids()) {
            if (nodeId.equals(source.getUUID())) continue;
            LogisticsNodeEntity node = findNode(server, nodeId);
            if (node != null && node.isValidNode() && label.equals(node.getNodeLabel())) targets.add(node);
        }
        return targets;
    }

    @Nullable
    private static LogisticsNodeEntity findLabelAuthority(MinecraftServer server, LogisticsNetwork network,
                                                           String label,
                                                           @Nullable LabelUpgradeTemplate storedTemplate) {
        LogisticsNodeEntity authority = null;
        long minimumRevision = storedTemplate == null ? Long.MIN_VALUE : storedTemplate.revision();
        for (UUID nodeId : network.getNodeUuids()) {
            LogisticsNodeEntity node = findNode(server, nodeId);
            if (node == null || !node.isValidNode() || !label.equals(node.getNodeLabel())
                    || node.getLabelRevision() < minimumRevision) continue;
            if (authority == null || node.getLabelRevision() > authority.getLabelRevision()
                    || node.getLabelRevision() == authority.getLabelRevision()
                    && node.getUUID().compareTo(authority.getUUID()) < 0) authority = node;
        }
        return authority;
    }

    private static boolean validBulkTargets(ServerPlayer player, LogisticsNetwork network,
                                            String label, @Nullable LabelUpgradeTemplate storedTemplate,
                                            List<LogisticsNodeEntity> targets,
                                            Map<UUID, BulkNodeState> snapshots, Set<UUID> pending,
                                            @Nullable LogisticsNodeEntity authority,
                                            @Nullable BulkNodeState authoritySnapshot) {
        LabelUpgradeTemplate currentTemplate = network.getLabelTemplate(label);
        if (!PENDING_NODES.containsAll(pending) || currentTemplate != storedTemplate
                || currentTemplate != null && currentTemplate.revision() != storedTemplate.revision()) return false;
        for (LogisticsNodeEntity target : targets) {
            BulkNodeState state = snapshots.get(target.getUUID());
            if (findNode(player.level().getServer(), target.getUUID()) != target || !target.isOwnedBy(player)
                    || !network.getId().equals(target.getNetworkId())
                    || !network.getNodeUuids().contains(target.getUUID())
                    || !state.label().equals(target.getNodeLabel())
                    || state.revision() != target.getLabelRevision()
                    || !sameUpgrades(target, state.upgrades())
                    || !sameChannels(target, state.channels())) return false;
        }
        return authority == null || findNode(player.level().getServer(), authority.getUUID()) == authority
                && authority.isValidNode()
                && network.getId().equals(authority.getNetworkId())
                && network.getNodeUuids().contains(authority.getUUID())
                && authoritySnapshot.label().equals(authority.getNodeLabel())
                && authoritySnapshot.revision() == authority.getLabelRevision()
                && sameUpgrades(authority, authoritySnapshot.upgrades())
                && sameChannels(authority, authoritySnapshot.channels());
    }

    private static boolean sameChannels(LogisticsNodeEntity node, List<ChannelData> expected) {
        if (!(node.level() instanceof ServerLevel level)) return false;
        for (int index = 0; index < LogisticsNodeEntity.CHANNEL_COUNT; index++) {
            ChannelData actual = node.getChannel(index);
            if (actual == null || index >= expected.size()
                    || !actual.save(level.registryAccess()).equals(expected.get(index).save(level.registryAccess()))) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static GraphPosition bulkLabelPosition(LogisticsNetwork network,
                                                   List<LogisticsNodeEntity> targets, String label) {
        GraphPosition existing = network.getGraphPositions().get("label:" + label);
        if (existing != null) return existing;
        Set<String> keys = new HashSet<>();
        float x = 0;
        float y = 0;
        int count = 0;
        for (LogisticsNodeEntity target : targets) {
            String key = target.getNodeLabel().isEmpty()
                    ? "node:" + target.getUUID() : "label:" + target.getNodeLabel();
            GraphPosition position = network.getGraphPositions().get(key);
            if (position != null && keys.add(key)) {
                x += position.x();
                y += position.y();
                count++;
            }
        }
        return count == 0 ? null : new GraphPosition(x / count, y / count);
    }

    private static Map<UUID, GraphPosition> bulkUnlabelPositions(LogisticsNetwork network,
                                                                 List<LogisticsNodeEntity> targets) {
        Map<UUID, GraphPosition> positions = new LinkedHashMap<>();
        Map<String, Integer> splitIndexes = new LinkedHashMap<>();
        for (LogisticsNodeEntity target : targets) {
            String oldKey = target.getNodeLabel().isEmpty()
                    ? "node:" + target.getUUID() : "label:" + target.getNodeLabel();
            GraphPosition oldPosition = network.getGraphPositions().get(oldKey);
            if (oldPosition == null) continue;
            if (target.getNodeLabel().isEmpty()) {
                positions.put(target.getUUID(), oldPosition);
                continue;
            }
            int index = splitIndexes.merge(oldKey, 1, Integer::sum) - 1;
            double angle = index * Math.PI / 3.0;
            float distance = 46.0f * (1 + index / 6);
            positions.put(target.getUUID(), new GraphPosition(
                    oldPosition.x() + (float) Math.cos(angle) * distance,
                    oldPosition.y() + (float) Math.sin(angle) * distance));
        }
        return positions;
    }

    @Nullable
    private static LogisticsNodeEntity findNode(MinecraftServer server, UUID nodeId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(nodeId);
            if (entity instanceof LogisticsNodeEntity node) return node;
        }
        return null;
    }

    private static List<ChannelData> snapshotChannels(LogisticsNodeEntity node) {
        List<ChannelData> channels = new ArrayList<>(LogisticsNodeEntity.CHANNEL_COUNT);
        for (int index = 0; index < LogisticsNodeEntity.CHANNEL_COUNT; index++) {
            ChannelData copy = new ChannelData();
            ChannelData source = node.getChannel(index);
            if (source != null) {
                copy.copyFrom(source);
                ServerPayloadHandler.clampChannelToUpgradeLimits(node, copy);
            }
            channels.add(copy);
        }
        return channels;
    }

    private static UpgradeChanges getUpgradeChanges(LogisticsNodeEntity target, List<ItemStack> desired) {
        boolean[] reused = new boolean[LogisticsNodeEntity.UPGRADE_SLOT_COUNT];
        List<Requirement> required = new ArrayList<>();
        for (ItemStack expected : desired) {
            if (expected.isEmpty()) continue;
            int reusable = findReusable(target, expected, reused);
            if (reusable >= 0) reused[reusable] = true;
            else addRequirement(required, expected, 1);
        }
        List<ItemStack> returned = new ArrayList<>();
        for (int slot = 0; slot < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; slot++) {
            ItemStack current = target.getUpgradeItem(slot);
            if (!current.isEmpty() && !reused[slot]) returned.add(current.copyWithCount(1));
        }
        return new UpgradeChanges(required, returned);
    }

    private static int findReusable(LogisticsNodeEntity target, ItemStack expected, boolean[] reused) {
        for (int slot = 0; slot < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; slot++) {
            if (!reused[slot] && ItemStack.isSameItem(target.getUpgradeItem(slot), expected)) return slot;
        }
        return -1;
    }

    private static void mergeRequirements(List<Requirement> target, List<Requirement> additions) {
        for (Requirement requirement : additions) addRequirement(target, requirement.stack(), requirement.count());
    }

    private static void addRequirement(List<Requirement> requirements, ItemStack stack, int count) {
        for (int index = 0; index < requirements.size(); index++) {
            Requirement existing = requirements.get(index);
            if (ItemStack.isSameItem(existing.stack(), stack)) {
                requirements.set(index, new Requirement(existing.stack(), existing.count() + count));
                return;
            }
        }
        requirements.add(new Requirement(stack.copyWithCount(1), count));
    }

    private static List<LinkedStorage.ItemRequirement> toRequirements(List<Requirement> requirements) {
        return requirements.stream()
                .map(requirement -> new LinkedStorage.ItemRequirement(requirement.stack(), requirement.count()))
                .toList();
    }

    private static boolean hasInventory(Inventory inventory, List<LinkedStorage.ItemRequirement> requirements,
                                        int protectedSlot) {
        for (LinkedStorage.ItemRequirement requirement : requirements) {
            if (StorageInventory.count(inventory, requirement.stack(), protectedSlot) < requirement.count()) {
                return false;
            }
        }
        return true;
    }

    private static void consumeInventory(Inventory inventory, List<LinkedStorage.ItemRequirement> requirements,
                                         int protectedSlot) {
        for (LinkedStorage.ItemRequirement requirement : requirements) {
            StorageInventory.reserve(inventory, requirement.stack(), requirement.count(), protectedSlot);
        }
        inventory.setChanged();
    }

    private static List<ItemStack> reserveRollbackItems(ServerPlayer player, LogisticsNodeEntity source,
                                                        List<ItemStack> original, @Nullable StorageLink storageLink) {
        UpgradeChanges rollback = getUpgradeChanges(source, original);
        List<ItemStack> reserved = new ArrayList<>();
        for (Requirement requirement : rollback.required()) {
            List<ItemStack> inventory = StorageInventory.reserve(
                    player.getInventory(), requirement.stack(), requirement.count(), -1);
            reserved.addAll(inventory);
            int remaining = requirement.count() - StorageInventory.count(inventory);
            if (remaining > 0 && storageLink != null) {
                var access = LinkedStorage.resolve(player.level(), storageLink);
                if (access != null) reserved.addAll(access.extract(requirement.stack(), remaining, player));
            }
        }
        player.getInventory().setChanged();
        return reserved;
    }

    private static void rollbackSource(ServerPlayer player, LogisticsNodeEntity source,
                                       List<ItemStack> original, List<ItemStack> escrow,
                                       @Nullable StorageLink returnToStorage) {
        UpgradeChanges rollback = getUpgradeChanges(source, original);
        List<ItemStack> current = snapshotUpgrades(source);
        boolean[] reused = new boolean[LogisticsNodeEntity.UPGRADE_SLOT_COUNT];
        boolean incomplete = false;
        for (int slot = 0; slot < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; slot++) {
            ItemStack expected = slot < original.size() ? original.get(slot) : ItemStack.EMPTY;
            ItemStack restored = ItemStack.EMPTY;
            if (!expected.isEmpty()) {
                int reusable = findReusable(current, expected, reused);
                if (reusable >= 0) {
                    reused[reusable] = true;
                    restored = current.get(reusable).copyWithCount(1);
                } else {
                    restored = takeEscrow(escrow, expected);
                    if (restored.isEmpty()) incomplete = true;
                }
            }
            source.setUpgradeItem(slot, restored);
        }
        returnItems(player, rollback.returned(), returnToStorage);
        if (incomplete) {
            player.sendSystemMessage(Component.translatable(
                    "message.logisticsnetworks.label.rollback_incomplete"));
        }
        ServerPayloadHandler.invalidateNetwork(source);
    }

    private static ItemStack takeEscrow(List<ItemStack> escrow, ItemStack expected) {
        for (ItemStack stack : escrow) {
            if (stack.isEmpty() || !ItemStack.isSameItem(stack, expected)) continue;
            stack.shrink(1);
            return expected.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    private static int findReusable(List<ItemStack> current, ItemStack expected, boolean[] reused) {
        for (int slot = 0; slot < current.size(); slot++) {
            if (!reused[slot] && ItemStack.isSameItem(current.get(slot), expected)) return slot;
        }
        return -1;
    }

    private static void returnItems(ServerPlayer player, List<ItemStack> items,
                                    @Nullable StorageLink returnToStorage) {
        LinkedStorage.returnItems(player, returnToStorage, items);
    }

    private static boolean sameUpgrades(LogisticsNodeEntity node, List<ItemStack> expected) {
        for (int slot = 0; slot < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; slot++) {
            ItemStack actual = node.getUpgradeItem(slot);
            ItemStack wanted = slot < expected.size() ? expected.get(slot) : ItemStack.EMPTY;
            if (actual.isEmpty() != wanted.isEmpty()) return false;
            if (!actual.isEmpty() && !ItemStack.isSameItem(actual, wanted)) return false;
        }
        return true;
    }

    private static long nextRevision(LogisticsNetwork network, String label) {
        LabelUpgradeTemplate current = network.getLabelTemplate(label);
        return current == null ? 1 : current.revision() + 1;
    }

    private static Component labelSubject(String label, int count) {
        return Component.translatable("message.logisticsnetworks.storage.subject.label", label, count);
    }

    private static Component formatRequirements(List<LinkedStorage.ItemRequirement> requirements) {
        Component result = Component.empty();
        for (int index = 0; index < requirements.size(); index++) {
            LinkedStorage.ItemRequirement requirement = requirements.get(index);
            if (index > 0) result = result.copy().append(", ");
            result = result.copy().append(Component.literal(requirement.count() + "x "))
                    .append(requirement.stack().getHoverName());
        }
        return result;
    }

    private static void reportFailure(ServerPlayer player, String label, Component detail) {
        player.sendSystemMessage(Component.translatable(
                "message.logisticsnetworks.label.sync_failed", label), true);
        player.sendSystemMessage(detail);
    }

    private record Requirement(ItemStack stack, int count) {
    }

    private record UpgradeChanges(List<Requirement> required, List<ItemStack> returned) {
    }

    private record BulkNodeState(String label, long revision, List<ItemStack> upgrades,
                                 List<ChannelData> channels) {
    }
}
