package me.almana.logisticsnetworks.menu;

import me.almana.logisticsnetworks.data.NodeClipboardConfig;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import me.almana.logisticsnetworks.integration.storage.StorageAccess;
import me.almana.logisticsnetworks.integration.storage.StorageAction;
import me.almana.logisticsnetworks.integration.storage.StorageInventory;
import me.almana.logisticsnetworks.integration.storage.StorageLink;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.logic.NodePlacementHelper;
import me.almana.logisticsnetworks.network.SyncMassPlacementChoicesPayload;
import me.almana.logisticsnetworks.network.SyncMassPlacementRequirementsPayload;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class MassPlacementMenu extends AbstractContainerMenu {

    public static final int ID_PLACE_NODES = 0;
    public static final int ID_CLEAR_SELECTION = 1;
    public static final int ID_SELECT_BLOCK_BASE = 100;

    private static final int DATA_SELECTED = 0;
    private static final int DATA_NODES_REQUIRED = 1;
    private static final int DATA_UPGRADES_REQUIRED = 2;
    private static final int DATA_FILTERS_REQUIRED = 3;
    private static final int DATA_CAN_PLACE = 4;
    private static final int DATA_PENDING = 5;
    private static final int DATA_SIZE = 6;

    private final InteractionHand hand;
    private final Player player;
    private final int lockedSlot;
    private final ContainerData data = new SimpleContainerData(DATA_SIZE);
    private final UUID supplyRequestId = UUID.randomUUID();
    private String lastChoicesKey = "";
    private String lastRequirementsKey = "";

    public MassPlacementMenu(int containerId, Inventory playerInventory, InteractionHand hand) {
        super(Registration.MASS_PLACEMENT_MENU.get(), containerId);
        this.hand = hand;
        this.player = playerInventory.player;
        this.lockedSlot = hand == InteractionHand.MAIN_HAND ? playerInventory.selected : -1;
        addDataSlots(data);
        refreshState();
    }

    public MassPlacementMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        super(Registration.MASS_PLACEMENT_MENU.get(), containerId);
        int handOrdinal = buf.readVarInt();
        this.hand = handOrdinal == InteractionHand.OFF_HAND.ordinal() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        this.player = playerInventory.player;
        this.lockedSlot = hand == InteractionHand.MAIN_HAND ? playerInventory.selected : -1;
        addDataSlots(data);
    }

    public int getSelectedCount() {
        return data.get(DATA_SELECTED);
    }

    public int getNodesRequired() {
        return data.get(DATA_NODES_REQUIRED);
    }

    public int getUpgradesRequired() {
        return data.get(DATA_UPGRADES_REQUIRED);
    }

    public int getFiltersRequired() {
        return data.get(DATA_FILTERS_REQUIRED);
    }

    public boolean canPlace() {
        return data.get(DATA_CAN_PLACE) == 1;
    }

    public boolean isPending() {
        return data.get(DATA_PENDING) == 1;
    }

    @Override
    public boolean stillValid(Player player) {
        ItemStack stack = getWrenchStack();
        return !stack.isEmpty() && stack.getItem() instanceof WrenchItem;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().isClientSide) {
            return false;
        }
        if (LinkedStorage.isSupplyPending(supplyRequestId)) return false;

        if (id == ID_PLACE_NODES) {
            boolean placed = placeSelectedNodes();
            refreshState();
            sendBlockChoices();
            sendRequirements();
            broadcastChanges();
            return placed;
        }

        if (id == ID_CLEAR_SELECTION) {
            ItemStack wrenchStack = getWrenchStack();
            if (!wrenchStack.isEmpty() && wrenchStack.getItem() instanceof WrenchItem) {
                WrenchItem.clearMassSelections(wrenchStack);
                player.getInventory().setChanged();
                player.displayClientMessage(
                        Component.translatable("message.logisticsnetworks.mass_placement.cleared"), true);
                refreshState();
                sendBlockChoices();
                sendRequirements();
                broadcastChanges();
            }
            return true;
        }

        if (id >= ID_SELECT_BLOCK_BASE) {
            ItemStack wrenchStack = getWrenchStack();
            int choiceIndex = id - ID_SELECT_BLOCK_BASE;
            List<WrenchItem.MassPlacementBlockChoice> choices =
                    WrenchItem.getMassPlacementBlockChoices(player.level(), wrenchStack);
            if (choiceIndex >= 0 && choiceIndex < choices.size()) {
                WrenchItem.setMassSelectedBlock(wrenchStack, choices.get(choiceIndex).blockId());
                player.getInventory().setChanged();
                refreshState();
                sendBlockChoices();
                sendRequirements();
                broadcastChanges();
                return true;
            }
        }

        return false;
    }

    @Override
    public void broadcastChanges() {
        if (!player.level().isClientSide) {
            refreshState();
            sendBlockChoices();
            sendRequirements();
        }
        super.broadcastChanges();
    }

    private void refreshState() {
        ItemStack wrenchStack = getWrenchStack();
        if (wrenchStack.isEmpty() || !(wrenchStack.getItem() instanceof WrenchItem)) {
            clearData();
            return;
        }

        WrenchItem.MassSelectionArea area = WrenchItem.getMassSelectionArea(wrenchStack, player.level().dimension());
        int selectedCount = area == null ? 0 : area.volume();
        List<WrenchItem.MassSelectionTarget> targets = WrenchItem.getMassPlacementTargets(player.level(), wrenchStack);
        int nodeCount = targets.size();

        NodeClipboardConfig clipboard = WrenchItem.getClipboard(wrenchStack, player.registryAccess());
        boolean clipboardPresent = clipboard != null && !clipboard.isEffectivelyEmpty();
        boolean clipboardValid = !clipboardPresent || clipboard.isStructurallyValid();
        boolean hasBlockSelection = WrenchItem.getMassSelectedBlock(wrenchStack) != null;

        int upgradesRequired = clipboardPresent && clipboardValid ? clipboard.getTotalUpgradeCount() * nodeCount : 0;
        int filtersRequired = clipboardPresent && clipboardValid ? clipboard.getTotalFilterCount() * nodeCount : 0;

        boolean creative = player.isCreative();
        int protectedSlot = findProtectedSlot(player.getInventory(), wrenchStack);
        List<Requirement> requirements = buildRequirements(nodeCount, clipboardPresent && clipboardValid ? clipboard : null);
        boolean pending = LinkedStorage.isSupplyPending(supplyRequestId);
        boolean hasItems = creative || hasSupplyPath(player.getInventory(), requirements, protectedSlot);
        boolean canPlace = area != null && area.isComplete() && hasBlockSelection && nodeCount > 0
                && clipboardValid && hasItems && !pending;

        data.set(DATA_SELECTED, selectedCount);
        data.set(DATA_NODES_REQUIRED, nodeCount);
        data.set(DATA_UPGRADES_REQUIRED, upgradesRequired);
        data.set(DATA_FILTERS_REQUIRED, filtersRequired);
        data.set(DATA_CAN_PLACE, canPlace ? 1 : 0);
        data.set(DATA_PENDING, pending ? 1 : 0);
    }

    private void clearData() {
        data.set(DATA_SELECTED, 0);
        data.set(DATA_NODES_REQUIRED, 0);
        data.set(DATA_UPGRADES_REQUIRED, 0);
        data.set(DATA_FILTERS_REQUIRED, 0);
        data.set(DATA_CAN_PLACE, 0);
        data.set(DATA_PENDING, 0);
    }

    private boolean placeSelectedNodes() {
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level)) {
            return false;
        }

        ItemStack wrenchStack = getWrenchStack();
        if (wrenchStack.isEmpty() || !(wrenchStack.getItem() instanceof WrenchItem)) {
            return false;
        }

        WrenchItem.MassSelectionArea area = WrenchItem.getMassSelectionArea(wrenchStack, player.level().dimension());
        if (area == null || !area.isComplete()) {
            player.displayClientMessage(Component.translatable("message.logisticsnetworks.mass_placement.none_selected"),
                    true);
            return false;
        }

        if (WrenchItem.getMassSelectedBlock(wrenchStack) == null) {
            player.displayClientMessage(
                    Component.translatable("message.logisticsnetworks.mass_placement.no_block_selected"), true);
            return false;
        }

        List<WrenchItem.MassSelectionTarget> validTargets = WrenchItem.getMassPlacementTargets(level, wrenchStack);
        if (validTargets.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.logisticsnetworks.mass_placement.invalid_targets"), true);
            return false;
        }

        NodeClipboardConfig clipboard = WrenchItem.getClipboard(wrenchStack, player.registryAccess());
        boolean clipboardPresent = clipboard != null && !clipboard.isEffectivelyEmpty();
        if (clipboardPresent && !clipboard.isStructurallyValid()) {
            player.displayClientMessage(Component.translatable("message.logisticsnetworks.clipboard.invalid"), true);
            return false;
        }

        int nodeCount = validTargets.size();
        boolean creative = player.isCreative();
        ItemStack requestWrench = wrenchStack;

        if (creative) {
            return commitPlacement(level, requestWrench, clipboardPresent ? clipboard : null, validTargets);
        }

        int protectedSlot = findProtectedSlot(player.getInventory(), wrenchStack);
        List<Requirement> requirements = buildRequirements(nodeCount, clipboardPresent ? clipboard : null);
        StorageLink storageLink = getStorageLink();
        if (hasInventoryRequirements(player.getInventory(), requirements, protectedSlot)) {
            consumeInventoryRequirements(player.getInventory(), requirements, protectedSlot);
            boolean committed = commitPlacement(level, requestWrench,
                    clipboardPresent ? clipboard : null, validTargets);
            if (!committed) returnInventoryRequirements(
                    player.getInventory(), requirements, serverPlayer);
            return committed;
        }
        if (storageLink == null) {
            reportMassFailure(serverPlayer, formatMissingInventory(
                    player.getInventory(), requirements, protectedSlot));
            return false;
        }

        List<LinkedStorage.ItemRequirement> requested = requirements.stream()
                .map(requirement -> new LinkedStorage.ItemRequirement(requirement.stack, requirement.count))
                .toList();
        Component subject = Component.translatable(
                "message.logisticsnetworks.storage.subject.mass_placement", nodeCount);
        LinkedStorage.SupplyRequestResult result = LinkedStorage.requestSupply(
                supplyRequestId, serverPlayer, storageLink, requested, protectedSlot, subject,
                new LinkedStorage.SupplyOperation() {
                    @Override
                    public boolean stillValid() {
                        return validateSnapshot(level, requestWrench, validTargets);
                    }

                    @Override
                    public boolean commit() {
                        return commitPlacement(level, requestWrench,
                                clipboardPresent ? clipboard : null, validTargets);
                    }

                    @Override
                    public void failed(Component detail) {
                        reportMassFailure(serverPlayer, detail);
                    }
                });
        if (result == LinkedStorage.SupplyRequestResult.QUEUED) {
            player.displayClientMessage(
                    Component.translatable("message.logisticsnetworks.mass_placement.queued"), true);
            refreshState();
            sendRequirements();
            broadcastChanges();
            return true;
        }
        return result == LinkedStorage.SupplyRequestResult.COMPLETED;
    }

    private boolean validateSnapshot(ServerLevel level, ItemStack wrenchStack,
                                     List<WrenchItem.MassSelectionTarget> targets) {
        if (getWrenchStack() != wrenchStack) return false;
        List<WrenchItem.MassSelectionTarget> current = WrenchItem.getMassPlacementTargets(level, wrenchStack);
        if (!current.equals(targets)) return false;
        for (WrenchItem.MassSelectionTarget target : targets) {
            if (NodePlacementHelper.validatePlacement(level, target.pos())
                    != NodePlacementHelper.ValidationResult.OK) return false;
        }
        return true;
    }

    private boolean commitPlacement(ServerLevel level, ItemStack wrenchStack, NodeClipboardConfig clipboard,
                                    List<WrenchItem.MassSelectionTarget> targets) {
        if (!validateSnapshot(level, wrenchStack, targets)) return false;
        List<LogisticsNodeEntity> placed = new ArrayList<>();
        for (WrenchItem.MassSelectionTarget target : targets) {
            LogisticsNodeEntity node = NodePlacementHelper.placeNode(level, target.pos(), player.getUUID());
            if (node == null || clipboard != null
                    && clipboard.applyToNodeWithoutInventory(node) != NodeClipboardConfig.PasteResult.SUCCESS) {
                if (node != null) node.discard();
                placed.forEach(LogisticsNodeEntity::discard);
                return false;
            }
            placed.add(node);
        }
        WrenchItem.clearMassSelections(wrenchStack);
        player.getInventory().setChanged();
        player.displayClientMessage(
                Component.translatable("message.logisticsnetworks.mass_placement.placed", placed.size()), true);
        return true;
    }

    private List<Requirement> buildRequirements(int nodeCount, NodeClipboardConfig clipboard) {
        List<Requirement> requirements = new ArrayList<>();
        if (nodeCount <= 0) {
            return requirements;
        }

        addRequirement(requirements, new ItemStack(Registration.LOGISTICS_NODE_ITEM.get()), nodeCount);

        if (clipboard == null) {
            return requirements;
        }

        for (NodeClipboardConfig.RequiredItem required : clipboard.getRequiredItemsPreview()) {
            addRequirement(requirements, required.stack(), required.count() * nodeCount);
        }

        return requirements;
    }

    private void addRequirement(List<Requirement> requirements, ItemStack stack, int count) {
        if (stack.isEmpty() || count <= 0) {
            return;
        }

        for (Requirement requirement : requirements) {
            if (ItemStack.isSameItem(requirement.stack, stack)) {
                requirement.count += count;
                return;
            }
        }

        requirements.add(new Requirement(stack.copyWithCount(1), count));
    }

    @Nullable
    private StorageLink getStorageLink() {
        ItemStack wrenchStack = getWrenchStack();
        return WrenchItem.getStorageLink(wrenchStack);
    }

    private boolean hasSupplyPath(Inventory inventory, List<Requirement> requirements, int protectedSlot) {
        StorageLink storageLink = getStorageLink();
        ServerLevel level = player.level() instanceof ServerLevel sl ? sl : null;
        StorageAccess access = storageLink == null || level == null ? null
                : LinkedStorage.resolve(level, storageLink);
        if (access != null && !access.allows((ServerPlayer) player, StorageAction.EXTRACT)) access = null;
        for (Requirement requirement : requirements) {
            int inventoryCount = StorageInventory.count(inventory, requirement.stack, protectedSlot);
            if (inventoryCount >= requirement.count) continue;
            if (access == null) return false;
            long stored = access.count(requirement.stack);
            if (inventoryCount + stored >= requirement.count) continue;
            if (!access.allows((ServerPlayer) player, StorageAction.AUTOCRAFT)
                    || !access.isCraftable(requirement.stack)) return false;
        }
        return true;
    }

    private boolean hasInventoryRequirements(Inventory inventory, List<Requirement> requirements,
                                             int protectedSlot) {
        for (Requirement requirement : requirements) {
            if (StorageInventory.count(inventory, requirement.stack, protectedSlot) < requirement.count) {
                return false;
            }
        }
        return true;
    }

    private void consumeInventoryRequirements(Inventory inventory, List<Requirement> requirements,
                                              int protectedSlot) {
        for (Requirement requirement : requirements) {
            StorageInventory.reserve(inventory, requirement.stack, requirement.count, protectedSlot);
        }
        inventory.setChanged();
    }

    private void returnInventoryRequirements(Inventory inventory, List<Requirement> requirements,
                                             ServerPlayer serverPlayer) {
        for (Requirement requirement : requirements) {
            ItemStack returned = requirement.stack.copyWithCount(requirement.count);
            inventory.add(returned);
            if (!returned.isEmpty()) serverPlayer.drop(returned, false);
        }
        inventory.setChanged();
    }

    private void reportMassFailure(ServerPlayer player, Component detail) {
        player.displayClientMessage(
                Component.translatable("message.logisticsnetworks.mass_placement.failed"), true);
        player.sendSystemMessage(detail);
    }

    private Component formatMissingInventory(Inventory inventory, List<Requirement> requirements,
                                             int protectedSlot) {
        Component missing = Component.empty();
        int entries = 0;
        for (Requirement requirement : requirements) {
            int count = requirement.count
                    - StorageInventory.count(inventory, requirement.stack, protectedSlot);
            if (count <= 0) continue;
            if (entries++ > 0) missing = missing.copy().append(", ");
            missing = missing.copy().append(Component.literal(count + "x "))
                    .append(requirement.stack.getHoverName());
        }
        return Component.translatable("message.logisticsnetworks.mass_placement.missing_detail", missing);
    }

    private int findProtectedSlot(Inventory inventory, ItemStack protectedStack) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot) == protectedStack) {
                return slot;
            }
        }
        return lockedSlot;
    }

    private ItemStack getWrenchStack() {
        if (hand == InteractionHand.OFF_HAND) {
            return player.getOffhandItem();
        }
        return lockedSlot >= 0 ? player.getInventory().getItem(lockedSlot) : player.getMainHandItem();
    }

    private void sendBlockChoices() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ItemStack wrenchStack = getWrenchStack();
        List<WrenchItem.MassPlacementBlockChoice> choices =
                WrenchItem.getMassPlacementBlockChoices(player.level(), wrenchStack);
        String key = choices.stream()
                .map(choice -> choice.blockId() + ":" + choice.targetCount() + ":" + choice.selected())
                .collect(Collectors.joining("|"));
        if (key.equals(lastChoicesKey)) {
            return;
        }

        lastChoicesKey = key;
        List<SyncMassPlacementChoicesPayload.BlockChoice> payloadChoices = new ArrayList<>(choices.size());
        for (WrenchItem.MassPlacementBlockChoice choice : choices) {
            payloadChoices.add(new SyncMassPlacementChoicesPayload.BlockChoice(
                    choice.blockId(), choice.name().getString(), choice.targetCount(), choice.selected()));
        }
        PacketDistributor.sendToPlayer(serverPlayer,
                new SyncMassPlacementChoicesPayload(containerId, payloadChoices, WrenchItem.getMaxMassNodes()));
    }

    private void sendRequirements() {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ItemStack wrenchStack = getWrenchStack();
        List<SyncMassPlacementRequirementsPayload.Requirement> payloadRequirements = new ArrayList<>();
        if (!wrenchStack.isEmpty() && wrenchStack.getItem() instanceof WrenchItem) {
            NodeClipboardConfig clipboard = WrenchItem.getClipboard(wrenchStack, player.registryAccess());
            boolean usableClipboard = clipboard != null && !clipboard.isEffectivelyEmpty()
                    && clipboard.isStructurallyValid();
            int protectedSlot = findProtectedSlot(player.getInventory(), wrenchStack);
            StorageLink storageLink = getStorageLink();
            StorageAccess access = storageLink == null ? null
                    : LinkedStorage.resolve(serverPlayer.serverLevel(), storageLink);
            if (access != null && !access.allows(serverPlayer, StorageAction.EXTRACT)) access = null;
            for (Requirement requirement : buildRequirements(
                    getNodesRequired(), usableClipboard ? clipboard : null)) {
                int inventory = StorageInventory.count(
                        player.getInventory(), requirement.stack, protectedSlot);
                long stored = access == null ? 0 : access.count(requirement.stack);
                boolean craftable = access != null && inventory + stored < requirement.count
                        && access.allows(serverPlayer, StorageAction.AUTOCRAFT)
                        && access.isCraftable(requirement.stack);
                boolean storageUsed = inventory < requirement.count && access != null
                        && (inventory + stored >= requirement.count || craftable);
                boolean missing = inventory + stored < requirement.count && !craftable;
                payloadRequirements.add(new SyncMassPlacementRequirementsPayload.Requirement(
                        requirement.stack.copyWithCount(1), requirement.count, inventory,
                        stored, craftable, storageUsed, missing));
            }
        }
        boolean pending = LinkedStorage.isSupplyPending(supplyRequestId);
        String key = pending + ":" + payloadRequirements.stream()
                .map(entry -> entry.item().getItem() + ":" + entry.required() + ":" + entry.inventory()
                        + ":" + entry.storageStored() + ":" + entry.storageCraftable())
                .collect(Collectors.joining("|"));
        if (key.equals(lastRequirementsKey)) return;
        lastRequirementsKey = key;
        PacketDistributor.sendToPlayer(serverPlayer,
                new SyncMassPlacementRequirementsPayload(containerId, pending, payloadRequirements));
    }

    private static class Requirement {
        private final ItemStack stack;
        private int count;

        private Requirement(ItemStack stack, int count) {
            this.stack = stack;
            this.count = count;
        }
    }
}
