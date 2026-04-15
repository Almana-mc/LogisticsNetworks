package me.almana.logisticsnetworks.menu;

import me.almana.logisticsnetworks.data.NodeClipboardConfig;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.ae2.AE2Compat;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.logic.NodePlacementHelper;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.GlobalPos;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MassPlacementMenu extends AbstractContainerMenu {

    public static final int ID_PLACE_NODES = 0;
    public static final int ID_CLEAR_SELECTION = 1;

    private static final int DATA_SELECTED = 0;
    private static final int DATA_NODES_REQUIRED = 1;
    private static final int DATA_UPGRADES_REQUIRED = 2;
    private static final int DATA_FILTERS_REQUIRED = 3;
    private static final int DATA_CAN_PLACE = 4;
    private static final int DATA_SIZE = 5;

    private final InteractionHand hand;
    private final Player player;
    private final int lockedSlot;
    private final ContainerData data = new SimpleContainerData(DATA_SIZE);

    public record RequirementView(Component name, int required, int available, int fromAE2, boolean missing) {
    }

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

    public List<RequirementView> getRequirementViews() {
        ItemStack wrenchStack = getWrenchStack();
        if (wrenchStack.isEmpty() || !(wrenchStack.getItem() instanceof WrenchItem)) {
            return List.of();
        }

        int nodeCount = getNodesRequired();
        if (nodeCount <= 0) {
            return List.of();
        }

        NodeClipboardConfig clipboard = WrenchItem.getClipboard(wrenchStack, player.registryAccess());
        boolean clipboardPresent = clipboard != null && !clipboard.isEffectivelyEmpty();
        boolean clipboardValid = !clipboardPresent || clipboard.isStructurallyValid();

        List<Requirement> requirements = buildRequirements(nodeCount, clipboardPresent && clipboardValid ? clipboard : null);
        int protectedSlot = findProtectedSlot(player.getInventory(), wrenchStack);
        List<RequirementView> views = new ArrayList<>(requirements.size());

        GlobalPos ae2Link = getAE2Link();
        for (Requirement requirement : requirements) {
            int invCount = AE2Compat.countInInventory(player.getInventory(), requirement.stack, protectedSlot);
            int ae2Count = 0;
            if (invCount < requirement.count && ae2Link != null && player.level() instanceof ServerLevel serverLevel) {
                ae2Count = (int) Math.min(AE2Compat.countAvailable(serverLevel, ae2Link, requirement.stack),
                        Integer.MAX_VALUE);
            }
            int available = invCount + ae2Count;
            boolean missing = available < requirement.count;
            views.add(new RequirementView(requirement.stack.getHoverName().copy(), requirement.count, available, ae2Count, missing));
        }

        return views;
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

        if (id == ID_PLACE_NODES) {
            boolean placed = placeSelectedNodes();
            refreshState();
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
                broadcastChanges();
            }
            return true;
        }

        return false;
    }

    @Override
    public void broadcastChanges() {
        if (!player.level().isClientSide) {
            refreshState();
        }
        super.broadcastChanges();
    }

    private void refreshState() {
        ItemStack wrenchStack = getWrenchStack();
        if (wrenchStack.isEmpty() || !(wrenchStack.getItem() instanceof WrenchItem)) {
            clearData();
            return;
        }

        List<WrenchItem.MassSelectionTarget> selected = WrenchItem.getMassSelections(wrenchStack, player.level().dimension());
        int selectedCount = selected.size();

        List<WrenchItem.MassSelectionTarget> validTargets = getValidTargets(selected);
        int nodeCount = validTargets.size();

        NodeClipboardConfig clipboard = WrenchItem.getClipboard(wrenchStack, player.registryAccess());
        boolean clipboardPresent = clipboard != null && !clipboard.isEffectivelyEmpty();
        boolean clipboardValid = !clipboardPresent || clipboard.isStructurallyValid();

        int upgradesRequired = clipboardPresent && clipboardValid ? clipboard.getTotalUpgradeCount() * nodeCount : 0;
        int filtersRequired = clipboardPresent && clipboardValid ? clipboard.getTotalFilterCount() * nodeCount : 0;

        boolean creative = player.isCreative();
        int protectedSlot = findProtectedSlot(player.getInventory(), wrenchStack);
        List<Requirement> requirements = buildRequirements(nodeCount, clipboardPresent && clipboardValid ? clipboard : null);
        boolean hasItems = creative || hasCombinedRequirements(player.getInventory(), requirements, protectedSlot);
        boolean canPlace = selectedCount > 0 && selectedCount == nodeCount && clipboardValid && hasItems;

        data.set(DATA_SELECTED, selectedCount);
        data.set(DATA_NODES_REQUIRED, nodeCount);
        data.set(DATA_UPGRADES_REQUIRED, upgradesRequired);
        data.set(DATA_FILTERS_REQUIRED, filtersRequired);
        data.set(DATA_CAN_PLACE, canPlace ? 1 : 0);
    }

    private void clearData() {
        data.set(DATA_SELECTED, 0);
        data.set(DATA_NODES_REQUIRED, 0);
        data.set(DATA_UPGRADES_REQUIRED, 0);
        data.set(DATA_FILTERS_REQUIRED, 0);
        data.set(DATA_CAN_PLACE, 0);
    }

    private boolean placeSelectedNodes() {
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level)) {
            return false;
        }

        ItemStack wrenchStack = getWrenchStack();
        if (wrenchStack.isEmpty() || !(wrenchStack.getItem() instanceof WrenchItem)) {
            return false;
        }

        List<WrenchItem.MassSelectionTarget> selected = WrenchItem.getMassSelections(wrenchStack, player.level().dimension());
        if (selected.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.logisticsnetworks.mass_placement.none_selected"),
                    true);
            return false;
        }

        List<WrenchItem.MassSelectionTarget> validTargets = getValidTargets(selected);
        if (validTargets.size() != selected.size()) {
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

        if (!creative) {
            int protectedSlot = findProtectedSlot(player.getInventory(), wrenchStack);
            List<Requirement> requirements = buildRequirements(nodeCount, clipboardPresent ? clipboard : null);

            if (!hasCombinedRequirements(player.getInventory(), requirements, protectedSlot)) {
                player.displayClientMessage(
                        Component.translatable("message.logisticsnetworks.mass_placement.missing_items"), true);
                return false;
            }

            consumeCombinedRequirements(player.getInventory(), requirements, protectedSlot, serverPlayer);
        }

        int placedCount = 0;
        List<WrenchItem.MassSelectionTarget> placedTargets = new ArrayList<>();

        for (WrenchItem.MassSelectionTarget target : validTargets) {
            LogisticsNodeEntity node = NodePlacementHelper.placeNode(level, target.pos(), player.getUUID());
            if (node == null) {
                continue;
            }

            if (clipboardPresent) {
                clipboard.applyToNodeWithoutInventory(node);
            }

            placedTargets.add(target);
            placedCount++;
        }

        WrenchItem.removeMassSelections(wrenchStack, placedTargets);
        player.getInventory().setChanged();

        if (placedCount > 0) {
            player.displayClientMessage(
                    Component.translatable("message.logisticsnetworks.mass_placement.placed", placedCount), true);
            return true;
        }

        player.displayClientMessage(Component.translatable("message.logisticsnetworks.mass_placement.failed"), true);
        return false;
    }

    private List<WrenchItem.MassSelectionTarget> getValidTargets(List<WrenchItem.MassSelectionTarget> selected) {
        boolean creative = player.isCreative();
        List<WrenchItem.MassSelectionTarget> validTargets = new ArrayList<>();
        for (WrenchItem.MassSelectionTarget target : selected) {
            NodePlacementHelper.ValidationResult validation = NodePlacementHelper.validatePlacement(player.level(), target.pos(), creative);
            if (validation == NodePlacementHelper.ValidationResult.OK) {
                validTargets.add(target);
            }
        }
        return validTargets;
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
    private GlobalPos getAE2Link() {
        ItemStack wrenchStack = getWrenchStack();
        if (!AE2Compat.isLoaded() || !WrenchItem.hasAE2Link(wrenchStack)) return null;
        return WrenchItem.getAE2LinkPos(wrenchStack);
    }

    private boolean hasCombinedRequirements(Inventory inventory, List<Requirement> requirements, int protectedSlot) {
        GlobalPos ae2Link = getAE2Link();
        ServerLevel level = player.level() instanceof ServerLevel sl ? sl : null;
        for (Requirement requirement : requirements) {
            if (!AE2Compat.hasCombinedStock(inventory, requirement.stack, requirement.count, protectedSlot, ae2Link, level)) {
                return false;
            }
        }
        return true;
    }

    private void consumeCombinedRequirements(Inventory inventory, List<Requirement> requirements, int protectedSlot, ServerPlayer serverPlayer) {
        GlobalPos ae2Link = getAE2Link();
        for (Requirement requirement : requirements) {
            AE2Compat.consumeCombined(inventory, requirement.stack, requirement.count, protectedSlot, ae2Link, serverPlayer);
        }
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

    private static class Requirement {
        private final ItemStack stack;
        private int count;

        private Requirement(ItemStack stack, int count) {
            this.stack = stack;
            this.count = count;
        }
    }
}
