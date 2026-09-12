package me.almana.logisticsnetworks.menu;

import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import me.almana.logisticsnetworks.integration.storage.StorageLink;
import me.almana.logisticsnetworks.logic.LabelUpgradeSync;
import me.almana.logisticsnetworks.network.SyncNetworkListPayload;
import me.almana.logisticsnetworks.network.GraphPayloadHandler;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NodeMenu extends AbstractContainerMenu {

    private static final int PLAYER_INV_X = 47;
    private static final int PLAYER_INV_Y = 218;

    // Grid Layout constants
    private static final int FILTER_GRID_X = 168;

    private static final int UPGRADE_GRID_Y = 118;
    private static final int UPGRADE_SLOTS = LogisticsNodeEntity.UPGRADE_SLOT_COUNT;
    private static final int GRID_STEP = 19;

    private final LogisticsNodeEntity node;
    private final int nodeId;
    private final Inventory playerInventory;
    private final List<ItemStack> openingUpgrades;
    @Nullable
    private final StorageLink preferredStorageLink;
    private int storageUpgradeAccess;
    private boolean remoteAccess;
    private int selectedChannel = 0;
    private boolean nodeSlotsActive = true;

    private final Container upgradeContainer;

    // Server-side
    public NodeMenu(int containerId, Inventory playerInv, LogisticsNodeEntity node) {
        this(containerId, playerInv, node, null);
    }

    public NodeMenu(int containerId, Inventory playerInv, LogisticsNodeEntity node,
                    @Nullable StorageLink preferredStorageLink) {
        this(Registration.NODE_MENU.get(), containerId, playerInv, node, 0, preferredStorageLink);
    }

    protected NodeMenu(MenuType<?> type, int containerId, Inventory playerInv,
            LogisticsNodeEntity node, int selectedChannel) {
        this(type, containerId, playerInv, node, selectedChannel, null);
    }

    protected NodeMenu(MenuType<?> type, int containerId, Inventory playerInv,
            LogisticsNodeEntity node, int selectedChannel, @Nullable StorageLink preferredStorageLink) {
        super(type, containerId);
        this.node = node;
        this.nodeId = node == null ? -1 : node.getId();
        this.playerInventory = playerInv;
        this.preferredStorageLink = preferredStorageLink;
        this.selectedChannel = Math.clamp(selectedChannel, 0, LogisticsNodeEntity.CHANNEL_COUNT - 1);
        this.upgradeContainer = new UpgradeItemsContainer();
        this.openingUpgrades = node == null ? List.of() : LabelUpgradeSync.snapshotUpgrades(node);

        layoutNodeSlots();
        layoutPlayerSlots(playerInv);
        addStorageAccessData();
    }

    // Client-side
    public NodeMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(Registration.NODE_MENU.get(), containerId);
        NodeMenuSync.ClientNodeState state = NodeMenuSync.read(buf, playerInv.player);
        this.nodeId = state.entityId();
        this.selectedChannel = state.selectedChannel();
        this.node = state.node();
        this.playerInventory = playerInv;
        this.preferredStorageLink = null;

        this.upgradeContainer = new UpgradeItemsContainer();
        this.openingUpgrades = node == null ? List.of() : LabelUpgradeSync.snapshotUpgrades(node);

        layoutNodeSlots();
        layoutPlayerSlots(playerInv);
        addStorageAccessData();
    }

    // Slot Layout

    private void layoutNodeSlots() {
        // 2x2 Upgrade Grid
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                int index = r * 2 + c;
                addSlot(new UpgradeSlot(upgradeContainer, index,
                        FILTER_GRID_X + c * GRID_STEP,
                        UPGRADE_GRID_Y + r * GRID_STEP));
            }
        }
    }

    private void layoutPlayerSlots(Inventory inv) {
        // Main Inventory (rows)
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(playerSlot(inv, c + r * 9 + 9, PLAYER_INV_X + c * 18, PLAYER_INV_Y + r * 18));
            }
        }
        // Hotbar
        for (int c = 0; c < 9; c++) {
            addSlot(playerSlot(inv, c, PLAYER_INV_X + c * 18, PLAYER_INV_Y + 58));
        }
    }

    private Slot playerSlot(Inventory inventory, int index, int x, int y) {
        return new Slot(inventory, index, x, y) {
            @Override
            public boolean isActive() {
                return hasVisibleInventory();
            }
        };
    }

    protected boolean hasVisibleInventory() {
        return true;
    }

    public LogisticsNodeEntity getNode() {
        return node;
    }

    public int getNodeId() {
        return nodeId;
    }

    public int getSelectedChannel() {
        return selectedChannel;
    }

    public boolean hasStorageUpgradeAccess() {
        return storageUpgradeAccess != 0;
    }

    @Nullable
    public StorageLink getAccessibleStorageLink(ServerPlayer player) {
        return LinkedStorage.findAccessibleLink(player, preferredStorageLink);
    }

    public List<ItemStack> getInstalledUpgrades() {
        List<ItemStack> installed = new ArrayList<>(UPGRADE_SLOTS);
        for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
            installed.add(upgradeContainer.getItem(slot));
        }
        return installed;
    }

    public boolean canEditNode(Player player) {
        return node != null && node.isValidNode() && node.isOwnedBy(player)
                && stillValid(player) && hasAvailableNode();
    }

    public void setSelectedChannel(int channelIndex) {
        this.selectedChannel = Math.max(0, Math.min(8, channelIndex));
        broadcastChanges();
    }

    /**
     * Toggle filter and upgrade slot activity to hide them on the network selection
     * page.
     */
    public void setNodeSlotsVisible(boolean visible) {
        this.nodeSlotsActive = visible;
    }

    public void setRemoteAccess(boolean remote) {
        this.remoteAccess = remote;
    }

    @Override
    public boolean stillValid(Player player) {
        if (node == null || !node.isAlive()) return false;
        return remoteAccess || player.distanceToSqr(node) < 64.0;
    }

    public void sendNetworkListToClient(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level))
            return;

        NetworkRegistry registry = NetworkRegistry.get(level);
        Collection<LogisticsNetwork> networks;
        if (player.hasPermissions(2)) {
            networks = registry.getAllNetworks().values();
        } else {
            networks = registry.getNetworksForPlayer(player.getUUID());
        }

        List<SyncNetworkListPayload.NetworkEntry> entries = new ArrayList<>(networks.size());
        for (LogisticsNetwork net : networks) {
            entries.add(new SyncNetworkListPayload.NetworkEntry(
                    net.getId(),
                    net.getName(),
                    net.getNodeUuids().size(),
                    false,
                    net.getColor()));
        }

        PacketDistributor.sendToPlayer(player, new SyncNetworkListPayload(entries));
    }

    private void addStorageAccessData() {
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                if (playerInventory.player instanceof ServerPlayer player) {
                    return getAccessibleStorageLink(player) == null ? 0 : 1;
                }
                return storageUpgradeAccess;
            }

            @Override
            public void set(int value) {
                storageUpgradeAccess = value;
            }
        });
    }

    private void markDirty() {
        if (node != null && node.getNetworkId() != null && node.level() instanceof ServerLevel level) {
            NetworkRegistry.get(level).invalidateNetwork(node.getNetworkId());
            GraphPayloadHandler.broadcast(level.getServer(), node.getNetworkId());
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!nodeSlotsActive || !hasAvailableNode() || isUpgradeEditPending()) return ItemStack.EMPTY;
        Slot fromSlot = slots.get(index);
        if (fromSlot == null || !fromSlot.hasItem())
            return ItemStack.EMPTY;

        ItemStack fromStack = fromSlot.getItem();
        ItemStack copy = fromStack.copy();

        int nodeSlotCount = UPGRADE_SLOTS;

        if (index < nodeSlotCount) {
            if (!moveItemStackTo(fromStack, nodeSlotCount, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!fromStack.is(ModTags.UPGRADES)) {
                return ItemStack.EMPTY;
            }

            ItemStack single = fromStack.copyWithCount(1);
            if (!moveItemStackTo(single, 0, nodeSlotCount, false)) {
                return ItemStack.EMPTY;
            }
            fromStack.shrink(1);
            if (fromStack.isEmpty()) {
                fromSlot.set(ItemStack.EMPTY);
            } else {
                fromSlot.setChanged();
            }
            return ItemStack.EMPTY;
        }

        if (fromStack.isEmpty()) {
            fromSlot.set(ItemStack.EMPTY);
        } else {
            fromSlot.setChanged();
        }

        return copy;
    }

    private class UpgradeItemsContainer extends AbstractProxyContainer {
        UpgradeItemsContainer() {
            super(UPGRADE_SLOTS);
        }

        @Override
        public ItemStack getItem(int slot) {
            return (node != null) ? node.getUpgradeItem(slot) : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (node != null) {
                node.setUpgradeItem(slot, stack);
                markDirty();
            }
        }
    }

    private abstract class AbstractProxyContainer implements Container {
        final int size;

        AbstractProxyContainer(int size) {
            this.size = size;
        }

        @Override
        public int getContainerSize() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack stack = getItem(slot);
            if (!stack.isEmpty())
                setItem(slot, ItemStack.EMPTY);
            return stack;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 1);
        }

        @Override
        public void setChanged() {
            markDirty();
        }

        @Override
        public boolean stillValid(Player player) {
            return NodeMenu.this.stillValid(player);
        }

        @Override
        public void clearContent() {
        }
    }

    private class UpgradeSlot extends Slot {
        UpgradeSlot(Container c, int i, int x, int y) {
            super(c, i, x, y);
        }

        @Override
        public boolean isActive() {
            return nodeSlotsActive && hasAvailableNode();
        }

        @Override
        public boolean mayPickup(Player player) {
            return isActive() && !isUpgradeEditPending();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!isActive() || isUpgradeEditPending()
                    || stack.isEmpty() || !stack.is(ModTags.UPGRADES)) {
                return false;
            }
            for (int i = 0; i < UPGRADE_SLOTS; i++) {
                if (i == getContainerSlot())
                    continue;
                ItemStack existing = upgradeContainer.getItem(i);
                if (!existing.isEmpty() && ItemStack.isSameItem(existing, stack)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    protected boolean hasAvailableNode() {
        return node != null;
    }

    private boolean isUpgradeEditPending() {
        return node != null && (LabelUpgradeSync.isPending(node)
                || LinkedStorage.isUpgradeCraftingPending(node));
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer serverPlayer && node != null && node.isAlive()) {
            LabelUpgradeSync.synchronizeMenuClose(
                    serverPlayer, node, openingUpgrades, getAccessibleStorageLink(serverPlayer));
        }
    }
}
