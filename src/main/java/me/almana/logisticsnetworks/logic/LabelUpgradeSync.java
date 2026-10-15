package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.LabelUpgradeTemplate;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.data.graph.GraphPosition;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import me.almana.logisticsnetworks.integration.storage.StorageAccess;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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

    public static void establishLabelTemplate(ServerPlayer player, LogisticsNetwork network,
                                              LogisticsNodeEntity authority, @Nullable StorageLink storageLink) {
        String label = authority.getNodeLabel();
        if (label.isBlank() || network.getLabelTemplate(label) != null) return;
        long revision = Math.max(1, authority.getLabelRevision());
        LabelUpgradeTemplate template = new LabelUpgradeTemplate(revision, player.getUUID(), storageLink,
                snapshotUpgrades(authority), snapshotChannels(authority));
        authority.setLabelRevision(revision);
        network.setLabelTemplate(label, template);
        NetworkRegistry.get(player.serverLevel()).setDirty();
    }

    public static void refreshTemplateChannel(LogisticsNetwork network, LogisticsNodeEntity source, int index,
                                              List<LogisticsNodeEntity> updated) {
        String label = source.getNodeLabel();
        LabelUpgradeTemplate template = network.getLabelTemplate(label);
        if (template == null) return;
        List<ChannelData> channels = template.channels();
        List<ChannelData> current = snapshotChannels(source);
        if (index >= channels.size() || channels.get(index).sameSettings(current.get(index))) return;
        long previous = template.revision();
        if (source.getLabelRevision() == previous && sameUpgrades(source, template.upgrades())) channels = current;
        else channels.get(index).copyFrom(current.get(index));
        long revision = previous + 1;
        network.setLabelTemplate(label, new LabelUpgradeTemplate(revision, template.playerId(),
                template.storageLink(), template.upgrades(), channels));
        if (source.getLabelRevision() == previous) source.setLabelRevision(revision);
        for (LogisticsNodeEntity node : updated) {
            if (node.getLabelRevision() == previous) node.setLabelRevision(revision);
        }
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
                                            List<LogisticsNodeEntity> targets, UUID sourceId, String label,
                                            @Nullable StorageLink storageLink) {
        return synchronizeLabels(player, network, targets, sourceId, label, storageLink, () -> true, ignored -> {});
    }

    public static boolean synchronizeLabels(ServerPlayer player, LogisticsNetwork network,
                                            List<LogisticsNodeEntity> targets, UUID sourceId, String label,
                                            @Nullable StorageLink storageLink, BooleanSupplier expectationValid,
                                            Consumer<Boolean> completion) {
        if (targets.isEmpty() || new HashSet<>(targets).size() != targets.size()
                || targets.stream().anyMatch(LabelUpgradeSync::isPending)) {
            completion.accept(false);
            return false;
        }
        if (label.isEmpty()) {
            clearLabels(player, network, targets);
            completion.accept(true);
            return true;
        }
        BulkSync sync = BulkSync.create(player, network, targets, sourceId, label, storageLink,
                expectationValid, completion);
        if (sync == null) {
            completion.accept(false);
            return false;
        }
        return sync.start();
    }

    public static void synchronizeOnLoad(LogisticsNodeEntity node) {
        LogisticsNetwork network = isPending(node) ? null : labelNetwork(node);
        LabelUpgradeTemplate template = network == null ? null : network.getLabelTemplate(node.getNodeLabel());
        if (template == null || node.getLabelRevision() >= template.revision()) return;
        UpgradeChanges changes = getUpgradeChanges(node, template.upgrades());
        ServerPlayer player = node.getServer().getPlayerList().getPlayer(template.playerId());
        if (player == null) {
            // Owner offline: item-free only
            if (changes.required().isEmpty() && changes.returned().isEmpty()) {
                applyLoadedTemplate(null, node, template, List.of());
            }
            return;
        }
        if (hasInventory(player.getInventory(), changes.required())) {
            consumeInventory(player.getInventory(), changes.required());
            applyLoadedTemplate(player, node, template, changes.returned());
        } else if (template.storageLink() == null) {
            reportFailure(player, node.getNodeLabel(), missing(changes.required()));
        } else {
            supplyOnLoad(player, node, template, changes);
        }
    }

    private static void supplyOnLoad(ServerPlayer player, LogisticsNodeEntity node, LabelUpgradeTemplate template,
                                     UpgradeChanges changes) {
        UUID networkId = node.getNetworkId();
        String label = node.getNodeLabel();
        List<ItemStack> current = snapshotUpgrades(node);
        PENDING_NODES.add(node.getUUID());
        LinkedStorage.requestSupply(UUID.randomUUID(), player, template.storageLink(),
                toRequirements(changes.required()), -1, labelSubject(label, 1), new LinkedStorage.SupplyOperation() {
                    @Override
                    public boolean stillValid() {
                        return validLoadedTarget(node, template, networkId, label, current);
                    }

                    @Override
                    public boolean commit() {
                        applyLoadedTemplate(player, node, template, changes.returned());
                        PENDING_NODES.remove(node.getUUID());
                        return true;
                    }

                    @Override
                    public void failed(Component detail) {
                        reportFailure(player, label, detail);
                        PENDING_NODES.remove(node.getUUID());
                    }
                });
    }

    private static boolean synchronizeLoaded(ServerPlayer player, LogisticsNodeEntity source,
                                             List<ItemStack> original, @Nullable StorageLink storageLink,
                                             boolean returnSourceToStorage) {
        // Player already saved or dead
        boolean absent = player.hasDisconnected() || !player.isAlive();
        LogisticsNetwork network = absent || sameUpgrades(source, original) || isPending(source)
                ? null : labelNetwork(source);
        if (network == null || isTemplateCurrent(network, source)) {
            ServerPayloadHandler.invalidateNetwork(source);
            return false;
        }
        new LoadedSync(player, network, source, original, storageLink, returnSourceToStorage).start();
        return true;
    }

    private static void clearLabels(ServerPlayer player, LogisticsNetwork network,
                                    List<LogisticsNodeEntity> targets) {
        Map<UUID, GraphPosition> splitPositions = bulkUnlabelPositions(network, targets);
        for (LogisticsNodeEntity target : targets) {
            target.setNodeLabel("");
            GraphPosition position = splitPositions.get(target.getUUID());
            if (position != null) network.setGraphPosition("node:" + target.getUUID(), position);
        }
        markChanged(player.serverLevel(), network);
        GraphPayloadHandler.broadcast(player.getServer(), network.getId());
        GraphPayloadHandler.refreshTable(player, network.getId());
    }

    private static LabelUpgradeTemplate freshTemplate(ServerPlayer player, LogisticsNetwork network, String label,
                                                      @Nullable LabelUpgradeTemplate storedTemplate,
                                                      @Nullable LogisticsNodeEntity authority,
                                                      @Nullable StorageLink storageLink) {
        if (authority == null || storedTemplate != null
                && authority.getLabelRevision() == storedTemplate.revision()
                && sameUpgrades(authority, storedTemplate.upgrades())
                && sameChannels(authority, storedTemplate.channels())) {
            return storedTemplate;
        }
        StorageLink templateLink = storedTemplate != null && storedTemplate.storageLink() != null
                ? storedTemplate.storageLink() : storageLink;
        long revision = Math.max(nextRevision(network, label), authority.getLabelRevision() + 1);
        return new LabelUpgradeTemplate(revision, player.getUUID(), templateLink,
                snapshotUpgrades(authority), snapshotChannels(authority));
    }

    private static void commitLabel(ServerPlayer player, LogisticsNetwork network,
                                    List<LogisticsNodeEntity> targets, String label,
                                    LabelUpgradeTemplate template, @Nullable LogisticsNodeEntity authority,
                                    Map<UUID, UpgradeChanges> changes) {
        boolean refreshesAuthority = authority != null && label.equals(authority.getNodeLabel())
                && template != network.getLabelTemplate(label);
        GraphPosition labelPosition = bulkLabelPosition(network, targets, label);
        for (LogisticsNodeEntity target : targets) {
            GraphPayloadHandler.preserveLabelPosition(target, label);
            target.setNodeLabel(label);
            applyTemplate(target, template);
            if (authority != null) target.setRenderVisible(authority.isRenderVisible());
            target.setLabelRevision(template.revision());
            returnItems(player, changes.get(target.getUUID()).returned(), null);
        }
        if (refreshesAuthority) authority.setLabelRevision(template.revision());
        if (labelPosition != null) network.setGraphPosition("label:" + label, labelPosition);
        network.setLabelTemplate(label, template);
        markChanged(player.serverLevel(), network);
        GraphPayloadHandler.broadcast(player.getServer(), network.getId());
        GraphPayloadHandler.refreshTable(player, network.getId());
        player.displayClientMessage(Component.translatable(
                "message.logisticsnetworks.label.synced", targets.size()), true);
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

    private static boolean validBulkTargets(ServerPlayer player, LogisticsNetwork network, String label,
                                            @Nullable LabelUpgradeTemplate storedTemplate,
                                            List<LogisticsNodeEntity> targets,
                                            Map<LogisticsNodeEntity, BulkNodeState> snapshots, Set<UUID> pending) {
        if (!PENDING_NODES.containsAll(pending) || network.getLabelTemplate(label) != storedTemplate
                || !targets.stream().allMatch(target -> target.isOwnedBy(player))) return false;
        for (Map.Entry<LogisticsNodeEntity, BulkNodeState> entry : snapshots.entrySet()) {
            LogisticsNodeEntity node = entry.getKey();
            if (findNode(player.getServer(), node.getUUID()) != node || !node.isValidNode()
                    || !network.getId().equals(node.getNetworkId())
                    || !network.getNodeUuids().contains(node.getUUID())
                    || !entry.getValue().matches(node)) return false;
        }
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
                    || !Objects.equals(networkId, target.getNetworkId())
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

    private static void applyLoadedTemplate(@Nullable ServerPlayer player, LogisticsNodeEntity node,
                                            LabelUpgradeTemplate template, List<ItemStack> returned) {
        applyTemplate(node, template);
        node.setLabelRevision(template.revision());
        NetworkRegistry registry = NetworkRegistry.get((ServerLevel) node.level());
        registry.setDirty();
        registry.invalidateNetwork(node.getNetworkId());
        if (player == null) return;
        returnItems(player, returned, null);
        player.displayClientMessage(Component.translatable(
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
            target.copyFrom(channels.get(index));
            ServerPayloadHandler.clampChannelToUpgradeLimits(node, target);
            ServerPayloadHandler.sendChannelSyncToViewers(node, index, target);
        }
    }

    private static void markChanged(ServerLevel level, LogisticsNetwork network) {
        NetworkRegistry registry = NetworkRegistry.get(level);
        registry.setDirty();
        registry.invalidateNetwork(network.getId());
    }

    @Nullable
    private static LogisticsNetwork labelNetwork(LogisticsNodeEntity node) {
        if (!(node.level() instanceof ServerLevel level) || node.getNetworkId() == null
                || node.getNodeLabel().isBlank()) return null;
        return NetworkRegistry.get(level).getNetwork(node.getNetworkId());
    }

    private static boolean isTemplateCurrent(LogisticsNetwork network, LogisticsNodeEntity source) {
        LabelUpgradeTemplate template = network.getLabelTemplate(source.getNodeLabel());
        return template != null && source.getLabelRevision() == template.revision()
                && sameUpgrades(source, template.upgrades());
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

    private static boolean sameChannels(LogisticsNodeEntity node, List<ChannelData> expected) {
        for (int index = 0; index < LogisticsNodeEntity.CHANNEL_COUNT; index++) {
            if (index >= expected.size() || !node.getChannel(index).sameSettings(expected.get(index))) {
                return false;
            }
        }
        return true;
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
            copy.copyFrom(node.getChannel(index));
            ServerPayloadHandler.clampChannelToUpgradeLimits(node, copy);
            channels.add(copy);
        }
        return channels;
    }

    private static List<Requirement> collectChanges(List<LogisticsNodeEntity> targets, List<ItemStack> desired,
                                                    Map<UUID, UpgradeChanges> changes) {
        List<Requirement> required = new ArrayList<>();
        for (LogisticsNodeEntity target : targets) {
            UpgradeChanges targetChanges = getUpgradeChanges(target, desired);
            changes.put(target.getUUID(), targetChanges);
            mergeRequirements(required, targetChanges.required());
        }
        return required;
    }

    private static UpgradeChanges getUpgradeChanges(LogisticsNodeEntity target, List<ItemStack> desired) {
        boolean[] reused = new boolean[LogisticsNodeEntity.UPGRADE_SLOT_COUNT];
        List<Requirement> required = new ArrayList<>();
        for (ItemStack expected : desired) {
            if (expected.isEmpty()) continue;
            int reusable = findReusable(snapshotUpgrades(target), expected, reused);
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

    private static int findReusable(List<ItemStack> current, ItemStack expected, boolean[] reused) {
        for (int slot = 0; slot < current.size(); slot++) {
            if (!reused[slot] && ItemStack.isSameItem(current.get(slot), expected)) return slot;
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

    private static boolean hasInventory(Inventory inventory, List<Requirement> requirements) {
        for (Requirement requirement : requirements) {
            if (StorageInventory.count(inventory, requirement.stack(), -1) < requirement.count()) return false;
        }
        return true;
    }

    private static void consumeInventory(Inventory inventory, List<Requirement> requirements) {
        for (Requirement requirement : requirements) {
            StorageInventory.reserve(inventory, requirement.stack(), requirement.count(), -1);
        }
        inventory.setChanged();
    }

    private static List<ItemStack> reserveRollbackItems(ServerPlayer player, LogisticsNodeEntity source,
                                                        List<ItemStack> original, @Nullable StorageLink storageLink) {
        StorageAccess access = storageLink == null ? null : LinkedStorage.resolve(player.serverLevel(), storageLink);
        List<ItemStack> reserved = new ArrayList<>();
        for (Requirement requirement : getUpgradeChanges(source, original).required()) {
            List<ItemStack> fromInventory = StorageInventory.reserve(
                    player.getInventory(), requirement.stack(), requirement.count(), -1);
            reserved.addAll(fromInventory);
            int remaining = requirement.count() - StorageInventory.count(fromInventory);
            if (remaining > 0 && access != null) reserved.addAll(access.extract(requirement.stack(), remaining, player));
        }
        player.getInventory().setChanged();
        return reserved;
    }

    private static void rollbackSource(ServerPlayer player, LogisticsNodeEntity source,
                                       List<ItemStack> original, List<ItemStack> escrow,
                                       @Nullable StorageLink returnToStorage) {
        if (source.isRemoved()) {
            // Broken node dropped its upgrades
            returnItems(player, escrow, returnToStorage);
            return;
        }
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
        returnItems(player, escrow.stream().filter(stack -> !stack.isEmpty()).toList(), returnToStorage);
        if (incomplete) {
            player.sendSystemMessage(Component.translatable("message.logisticsnetworks.label.rollback_incomplete"));
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

    private static Component missing(List<Requirement> requirements) {
        return Component.translatable("message.logisticsnetworks.label.missing_upgrades",
                formatRequirements(requirements));
    }

    private static Component formatRequirements(List<Requirement> requirements) {
        Component result = Component.empty();
        for (int index = 0; index < requirements.size(); index++) {
            Requirement requirement = requirements.get(index);
            if (index > 0) result = result.copy().append(", ");
            result = result.copy().append(Component.literal(requirement.count() + "x "))
                    .append(requirement.stack().getHoverName());
        }
        return result;
    }

    private static void reportFailure(ServerPlayer player, String label, Component detail) {
        player.displayClientMessage(Component.translatable(
                "message.logisticsnetworks.label.sync_failed", label), true);
        player.sendSystemMessage(detail);
    }

    private record Requirement(ItemStack stack, int count) {
    }

    private record UpgradeChanges(List<Requirement> required, List<ItemStack> returned) {
    }

    private record BulkNodeState(String label, long revision, List<ItemStack> upgrades,
                                 List<ChannelData> channels) {
        static BulkNodeState of(LogisticsNodeEntity node) {
            return new BulkNodeState(node.getNodeLabel(), node.getLabelRevision(), snapshotUpgrades(node),
                    snapshotChannels(node));
        }

        boolean matches(LogisticsNodeEntity node) {
            return label.equals(node.getNodeLabel()) && revision == node.getLabelRevision()
                    && sameUpgrades(node, upgrades) && sameChannels(node, channels);
        }
    }

    // Menu close label sync
    private static final class LoadedSync implements LinkedStorage.SupplyOperation {
        private final ServerPlayer player;
        private final LogisticsNetwork network;
        private final LogisticsNodeEntity source;
        private final List<ItemStack> original;
        @Nullable
        private final StorageLink storageLink;
        private final boolean returnSourceToStorage;
        private final String label;
        private final UUID networkId;
        private final List<LogisticsNodeEntity> targets;
        private final List<ItemStack> desired;
        private final List<ChannelData> channels;
        private final Map<UUID, UpgradeChanges> changes = new LinkedHashMap<>();
        private final Map<UUID, List<ItemStack>> targetSnapshots = new LinkedHashMap<>();
        private final Set<UUID> pending = new HashSet<>();
        private List<ItemStack> escrow = List.of();

        private LoadedSync(ServerPlayer player, LogisticsNetwork network, LogisticsNodeEntity source,
                           List<ItemStack> original, @Nullable StorageLink storageLink,
                           boolean returnSourceToStorage) {
            this.player = player;
            this.network = network;
            this.source = source;
            this.original = original;
            this.storageLink = storageLink;
            this.returnSourceToStorage = returnSourceToStorage;
            this.label = source.getNodeLabel();
            this.networkId = source.getNetworkId();
            this.targets = findLoadedTargets(player.getServer(), network, source);
            this.desired = snapshotUpgrades(source);
            this.channels = snapshotChannels(source);
        }

        private void start() {
            List<Requirement> required = collectChanges(targets, desired, changes);
            targets.forEach(target -> targetSnapshots.put(target.getUUID(), snapshotUpgrades(target)));
            escrow = reserveRollbackItems(player, source, original, storageLink);
            pending.add(source.getUUID());
            targets.forEach(target -> pending.add(target.getUUID()));
            PENDING_NODES.addAll(pending);
            if (hasInventory(player.getInventory(), required)) {
                consumeInventory(player.getInventory(), required);
                commit();
            } else if (storageLink == null) {
                failed(missing(required));
            } else {
                LinkedStorage.requestSupply(UUID.randomUUID(), player, storageLink, toRequirements(required), -1,
                        labelSubject(label, targets.size() + 1), this);
            }
        }

        @Override
        public boolean stillValid() {
            return validLoadedGroup(player.getServer(), source, desired, targets, targetSnapshots, networkId, label);
        }

        @Override
        public boolean commit() {
            long revision = nextRevision(network, label);
            LabelUpgradeTemplate template = new LabelUpgradeTemplate(revision, player.getUUID(), storageLink,
                    desired, channels);
            for (LogisticsNodeEntity target : targets) {
                applyTemplate(target, template);
                returnItems(player, changes.get(target.getUUID()).returned(), null);
                target.setLabelRevision(revision);
            }
            applyTemplate(source, template);
            source.setLabelRevision(revision);
            network.setLabelTemplate(label, template);
            returnItems(player, escrow, null);
            markChanged(player.serverLevel(), network);
            player.displayClientMessage(Component.translatable(
                    "message.logisticsnetworks.label.synced", targets.size() + 1), true);
            PENDING_NODES.removeAll(pending);
            return true;
        }

        @Override
        public void failed(Component detail) {
            rollbackSource(player, source, original, escrow, returnSourceToStorage ? storageLink : null);
            reportFailure(player, label, detail);
            PENDING_NODES.removeAll(pending);
        }
    }

    // Bulk label apply
    private static final class BulkSync implements LinkedStorage.SupplyOperation {
        private final ServerPlayer player;
        private final LogisticsNetwork network;
        private final List<LogisticsNodeEntity> targets;
        private final String label;
        @Nullable
        private final LabelUpgradeTemplate storedTemplate;
        @Nullable
        private final LogisticsNodeEntity authority;
        private final LabelUpgradeTemplate template;
        @Nullable
        private final StorageLink supplyLink;
        private final BooleanSupplier expectationValid;
        private final Consumer<Boolean> completion;
        private final Map<UUID, UpgradeChanges> changes = new LinkedHashMap<>();
        private final Map<LogisticsNodeEntity, BulkNodeState> snapshots = new LinkedHashMap<>();
        private final Set<UUID> pending = new HashSet<>();
        private boolean finished;

        private BulkSync(ServerPlayer player, LogisticsNetwork network, List<LogisticsNodeEntity> targets,
                         String label, @Nullable LabelUpgradeTemplate storedTemplate,
                         @Nullable LogisticsNodeEntity authority, LabelUpgradeTemplate template,
                         @Nullable StorageLink storageLink, BooleanSupplier expectationValid,
                         Consumer<Boolean> completion) {
            this.player = player;
            this.network = network;
            this.targets = targets;
            this.label = label;
            this.storedTemplate = storedTemplate;
            this.authority = authority;
            this.template = template;
            this.supplyLink = template.storageLink() != null ? template.storageLink() : storageLink;
            this.expectationValid = expectationValid;
            this.completion = completion;
        }

        @Nullable
        private static BulkSync create(ServerPlayer player, LogisticsNetwork network,
                                       List<LogisticsNodeEntity> targets, UUID sourceId, String label,
                                       @Nullable StorageLink storageLink, BooleanSupplier expectationValid,
                                       Consumer<Boolean> completion) {
            LabelUpgradeTemplate stored = network.getLabelTemplate(label);
            LogisticsNodeEntity authority = findLabelAuthority(player.getServer(), network, label, stored);
            if (stored == null && authority == null) {
                authority = targets.stream().filter(node -> node.getUUID().equals(sourceId)).findFirst().orElse(null);
            }
            if (stored == null && authority == null) return null;
            LabelUpgradeTemplate template = freshTemplate(player, network, label, stored, authority, storageLink);
            return new BulkSync(player, network, targets, label, stored, authority, template, storageLink,
                    expectationValid, completion);
        }

        private boolean start() {
            List<Requirement> required = collectChanges(targets, template.upgrades(), changes);
            targets.forEach(target -> snapshots.put(target, BulkNodeState.of(target)));
            if (authority != null) snapshots.putIfAbsent(authority, BulkNodeState.of(authority));
            snapshots.keySet().forEach(node -> pending.add(node.getUUID()));
            PENDING_NODES.addAll(pending);
            if (required.isEmpty()) {
                if (stillValid()) commit();
                else finish(false);
                return true;
            }
            if (hasInventory(player.getInventory(), required)) {
                consumeInventory(player.getInventory(), required);
                commit();
                return true;
            }
            if (supplyLink == null) {
                failed(missing(required));
                return false;
            }
            UUID requestId = UUID.randomUUID();
            LinkedStorage.requestSupply(requestId, player, supplyLink, toRequirements(required), -1,
                    labelSubject(label, targets.size()), this);
            if (!LinkedStorage.isSupplyPending(requestId)) finish(false);
            return true;
        }

        @Override
        public boolean stillValid() {
            return expectationValid.getAsBoolean()
                    && validBulkTargets(player, network, label, storedTemplate, targets, snapshots, pending);
        }

        @Override
        public boolean commit() {
            commitLabel(player, network, targets, label, template, authority, changes);
            finish(true);
            return true;
        }

        @Override
        public void failed(Component detail) {
            reportFailure(player, label, detail);
            finish(false);
        }

        private void finish(boolean success) {
            PENDING_NODES.removeAll(pending);
            if (finished) return;
            finished = true;
            completion.accept(success);
        }
    }
}
