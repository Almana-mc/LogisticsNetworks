package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.LabelUpgradeTemplate;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.storage.StorageInventory;
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
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

// Inventory only until Phase 8
public final class LabelUpgradeSync {

    private LabelUpgradeSync() {
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
                                              LogisticsNodeEntity authority) {
        String label = authority.getNodeLabel();
        if (label.isBlank() || network.getLabelTemplate(label) != null) return;
        long revision = Math.max(1, authority.getLabelRevision());
        LabelUpgradeTemplate template = new LabelUpgradeTemplate(revision, player.getUUID(),
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
                template.upgrades(), channels));
        if (source.getLabelRevision() == previous) source.setLabelRevision(revision);
        for (LogisticsNodeEntity node : updated) {
            if (node.getLabelRevision() == previous) node.setLabelRevision(revision);
        }
    }

    public static void synchronizeMenuClose(ServerPlayer player, LogisticsNodeEntity source,
                                            List<ItemStack> original) {
        // Player already saved or dead
        boolean absent = player.hasDisconnected() || !player.isAlive();
        LogisticsNetwork network = absent || sameUpgrades(source, original) ? null : labelNetwork(source);
        if (network == null || isTemplateCurrent(network, source)) {
            ServerPayloadHandler.invalidateNetwork(source);
            return;
        }
        List<LogisticsNodeEntity> targets = findLoadedTargets(player.getServer(), network, source);
        List<ItemStack> desired = snapshotUpgrades(source);
        Map<UUID, UpgradeChanges> changes = new LinkedHashMap<>();
        List<Requirement> required = collectChanges(targets, desired, changes);
        List<ItemStack> escrow = reserveRollbackItems(player, source, original);
        if (!hasInventory(player.getInventory(), required)) {
            rollbackSource(player, source, original, escrow);
            reportFailure(player, source.getNodeLabel(), missing(required));
            return;
        }
        consumeInventory(player.getInventory(), required);
        LabelUpgradeTemplate template = new LabelUpgradeTemplate(nextRevision(network, source.getNodeLabel()),
                player.getUUID(), desired, snapshotChannels(source));
        for (LogisticsNodeEntity target : targets) {
            applyTemplate(target, template);
            returnItems(player, changes.get(target.getUUID()).returned());
            target.setLabelRevision(template.revision());
        }
        applyTemplate(source, template);
        source.setLabelRevision(template.revision());
        network.setLabelTemplate(source.getNodeLabel(), template);
        returnItems(player, escrow);
        markChanged(player.serverLevel(), network);
        player.displayClientMessage(Component.translatable(
                "message.logisticsnetworks.label.synced", targets.size() + 1), true);
    }

    public static boolean synchronizeLabels(ServerPlayer player, LogisticsNetwork network,
                                            List<LogisticsNodeEntity> targets, UUID sourceId, String label) {
        return synchronizeLabels(player, network, targets, sourceId, label, () -> true, ignored -> {});
    }

    public static boolean synchronizeLabels(ServerPlayer player, LogisticsNetwork network,
                                            List<LogisticsNodeEntity> targets, UUID sourceId, String label,
                                            BooleanSupplier expectationValid, Consumer<Boolean> completion) {
        boolean applied = applyLabel(player, network, targets, sourceId, label, expectationValid);
        completion.accept(applied);
        return applied;
    }

    public static void synchronizeOnLoad(LogisticsNodeEntity node) {
        LogisticsNetwork network = labelNetwork(node);
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
        if (!hasInventory(player.getInventory(), changes.required())) {
            reportFailure(player, node.getNodeLabel(), missing(changes.required()));
            return;
        }
        consumeInventory(player.getInventory(), changes.required());
        applyLoadedTemplate(player, node, template, changes.returned());
    }

    private static boolean applyLabel(ServerPlayer player, LogisticsNetwork network,
                                      List<LogisticsNodeEntity> targets, UUID sourceId, String label,
                                      BooleanSupplier expectationValid) {
        if (targets.isEmpty() || new HashSet<>(targets).size() != targets.size()) return false;
        if (label.isEmpty()) {
            clearLabels(player, network, targets);
            return true;
        }
        LabelUpgradeTemplate storedTemplate = network.getLabelTemplate(label);
        LogisticsNodeEntity authority = findLabelAuthority(player.getServer(), network, label, storedTemplate);
        if (storedTemplate == null && authority == null) {
            authority = targets.stream().filter(node -> node.getUUID().equals(sourceId)).findFirst().orElse(null);
        }
        if (storedTemplate == null && authority == null) return false;
        LabelUpgradeTemplate template = freshTemplate(player, network, label, storedTemplate, authority);
        Map<UUID, UpgradeChanges> changes = new LinkedHashMap<>();
        List<Requirement> required = collectChanges(targets, template.upgrades(), changes);
        if (required.isEmpty()
                && !(expectationValid.getAsBoolean() && validTargets(player, network, targets, authority))) {
            return false;
        }
        if (!hasInventory(player.getInventory(), required)) {
            reportFailure(player, label, missing(required));
            return false;
        }
        consumeInventory(player.getInventory(), required);
        commitLabel(player, network, targets, label, template, authority, changes);
        return true;
    }

    private static void clearLabels(ServerPlayer player, LogisticsNetwork network,
                                    List<LogisticsNodeEntity> targets) {
        targets.forEach(target -> target.setNodeLabel(""));
        markChanged(player.serverLevel(), network);
        GraphPayloadHandler.broadcast(player.getServer(), network.getId());
    }

    private static LabelUpgradeTemplate freshTemplate(ServerPlayer player, LogisticsNetwork network, String label,
                                                      @Nullable LabelUpgradeTemplate storedTemplate,
                                                      @Nullable LogisticsNodeEntity authority) {
        if (authority == null || storedTemplate != null
                && authority.getLabelRevision() == storedTemplate.revision()
                && sameUpgrades(authority, storedTemplate.upgrades())
                && sameChannels(authority, storedTemplate.channels())) {
            return storedTemplate;
        }
        long revision = Math.max(nextRevision(network, label), authority.getLabelRevision() + 1);
        return new LabelUpgradeTemplate(revision, player.getUUID(),
                snapshotUpgrades(authority), snapshotChannels(authority));
    }

    private static void commitLabel(ServerPlayer player, LogisticsNetwork network,
                                    List<LogisticsNodeEntity> targets, String label,
                                    LabelUpgradeTemplate template, @Nullable LogisticsNodeEntity authority,
                                    Map<UUID, UpgradeChanges> changes) {
        boolean refreshesAuthority = authority != null && label.equals(authority.getNodeLabel())
                && template != network.getLabelTemplate(label);
        for (LogisticsNodeEntity target : targets) {
            target.setNodeLabel(label);
            applyTemplate(target, template);
            if (authority != null) target.setRenderVisible(authority.isRenderVisible());
            target.setLabelRevision(template.revision());
            returnItems(player, changes.get(target.getUUID()).returned());
        }
        if (refreshesAuthority) authority.setLabelRevision(template.revision());
        network.setLabelTemplate(label, template);
        markChanged(player.serverLevel(), network);
        GraphPayloadHandler.broadcast(player.getServer(), network.getId());
        player.displayClientMessage(Component.translatable(
                "message.logisticsnetworks.label.synced", targets.size()), true);
    }

    private static boolean validTargets(ServerPlayer player, LogisticsNetwork network,
                                        List<LogisticsNodeEntity> targets,
                                        @Nullable LogisticsNodeEntity authority) {
        MinecraftServer server = player.getServer();
        for (LogisticsNodeEntity target : targets) {
            if (findNode(server, target.getUUID()) != target || !target.isOwnedBy(player)
                    || !network.getId().equals(target.getNetworkId())
                    || !network.getNodeUuids().contains(target.getUUID())) return false;
        }
        return authority == null || findNode(server, authority.getUUID()) == authority
                && authority.isValidNode()
                && network.getId().equals(authority.getNetworkId())
                && network.getNodeUuids().contains(authority.getUUID());
    }

    private static void applyLoadedTemplate(@Nullable ServerPlayer player, LogisticsNodeEntity node,
                                            LabelUpgradeTemplate template, List<ItemStack> returned) {
        applyTemplate(node, template);
        node.setLabelRevision(template.revision());
        NetworkRegistry registry = NetworkRegistry.get((ServerLevel) node.level());
        registry.setDirty();
        registry.invalidateNetwork(node.getNetworkId());
        if (player == null) return;
        returnItems(player, returned);
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
            dropReplacedFilters(node, target, channels.get(index));
            target.copyFrom(channels.get(index));
            ServerPayloadHandler.clampChannelToUpgradeLimits(node, target);
            ServerPayloadHandler.sendChannelSyncToViewers(node, index, target);
        }
    }

    // Physical filters until Phase 4
    private static void dropReplacedFilters(LogisticsNodeEntity node, ChannelData current, ChannelData next) {
        for (int slot = 0; slot < ChannelData.FILTER_SIZE; slot++) {
            ItemStack old = current.getFilterItem(slot);
            if (!old.isEmpty() && !ItemStack.matches(old, next.getFilterItem(slot))) {
                node.spawnAtLocation(old.copy());
            }
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
                                                        List<ItemStack> original) {
        List<ItemStack> reserved = new ArrayList<>();
        for (Requirement requirement : getUpgradeChanges(source, original).required()) {
            reserved.addAll(StorageInventory.reserve(
                    player.getInventory(), requirement.stack(), requirement.count(), -1));
        }
        player.getInventory().setChanged();
        return reserved;
    }

    private static void rollbackSource(ServerPlayer player, LogisticsNodeEntity source,
                                       List<ItemStack> original, List<ItemStack> escrow) {
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
        returnItems(player, rollback.returned());
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

    private static void returnItems(ServerPlayer player, List<ItemStack> items) {
        StorageInventory.returnToPlayer(player, items);
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
}
