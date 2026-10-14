package me.almana.logisticsnetworks.menu;

import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.logic.LabelUpgradeSync;
import me.almana.logisticsnetworks.logic.NodeAccessPolicy;
import me.almana.logisticsnetworks.network.NetworkHandler;
import me.almana.logisticsnetworks.network.ServerPayloadHandler;
import me.almana.logisticsnetworks.network.SyncNetworkListPayload;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
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
    @Nullable
    private GraphMenuContext returnContext;
    private boolean remoteAccess;
    private int selectedChannel;
    private boolean nodeSlotsActive = true;

    private final Container upgradeContainer;
    private final List<ItemStack> openingUpgrades;

    // Server-side
    public NodeMenu(int containerId, Inventory playerInv, LogisticsNodeEntity node, int selectedChannel) {
        this(Registration.NODE_MENU.get(), containerId, playerInv, node, selectedChannel);
    }

    protected NodeMenu(MenuType<?> type, int containerId, Inventory playerInv, @Nullable LogisticsNodeEntity node,
            int selectedChannel) {
        super(type, containerId);
        this.node = node;
        this.nodeId = node == null ? -1 : node.getId();
        this.selectedChannel = selectedChannel;
        this.upgradeContainer = new UpgradeItemsContainer();
        this.openingUpgrades = node == null ? List.of() : LabelUpgradeSync.snapshotUpgrades(node);

        layoutNodeSlots();
        layoutPlayerSlots(playerInv);
    }

    // Client-side
    public NodeMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(Registration.NODE_MENU.get(), containerId);
        NodeMenuSync.ClientNodeState state = NodeMenuSync.read(buf, playerInv.player);
        this.node = state.node();
        this.nodeId = state.entityId();
        this.selectedChannel = state.selectedChannel();
        this.returnContext = buf.readableBytes() > 0 && buf.readBoolean() ? GraphMenuContext.read(buf) : null;
        this.upgradeContainer = new UpgradeItemsContainer();
        this.openingUpgrades = List.of();

        layoutNodeSlots();
        layoutPlayerSlots(playerInv);
    }

    public static void open(ServerPlayer player, LogisticsNodeEntity node, int selectedChannel, boolean remote) {
        open(player, node, selectedChannel, remote, null);
    }

    public static void open(ServerPlayer player, LogisticsNodeEntity node, int selectedChannel, boolean remote,
            @Nullable GraphMenuContext context) {
        NodeMenuSync.openScreen(player, new SimpleMenuProvider((id, inv, p) -> {
            NodeMenu menu = new NodeMenu(id, inv, node, selectedChannel);
            menu.setRemoteAccess(remote);
            menu.setReturnContext(context);
            return menu;
        }, Component.translatable("gui.logisticsnetworks.node_config")), node, selectedChannel, buf -> {}, buf -> {
            buf.writeBoolean(context != null);
            if (context != null) context.write(buf);
        });
        if (player.containerMenu instanceof NodeMenu menu) {
            menu.sendNetworkListToClient(player);
        }
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

    protected boolean hasAvailableNode() {
        return node != null;
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

    @Nullable
    public GraphMenuContext getReturnContext() {
        return returnContext;
    }

    public void setReturnContext(@Nullable GraphMenuContext returnContext) {
        this.returnContext = returnContext;
    }

    public void setSelectedChannel(int channelIndex) {
        this.selectedChannel = Math.max(0, Math.min(8, channelIndex));
        broadcastChanges();
    }

    public void setNodeSlotsVisible(boolean visible) {
        this.nodeSlotsActive = visible;
    }

    public void setRemoteAccess(boolean remote) {
        this.remoteAccess = remote;
    }

    @Override
    public boolean stillValid(Player player) {
        if (returnContext != null) return returnContext.canEdit(player, node);
        if (node == null || !node.isAlive()) return false;
        if (!player.level().isClientSide() && !node.isOwnedBy(player)) return false;
        return remoteAccess || player.distanceToSqr(node) < 64.0;
    }

    public void sendNetworkListToClient(ServerPlayer player) {
        if (node != null && node.getNetworkId() != null) {
            NetworkRegistry.get(player.serverLevel()).stampCreatedAt(node.getNetworkId());
        }
        sendAvailableNetworkListToClient(player);
    }

    public static void sendAvailableNetworkListToClient(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level))
            return;

        NetworkRegistry registry = NetworkRegistry.get(level);
        Collection<LogisticsNetwork> networks = registry.getVisibleNetworks(player);

        List<SyncNetworkListPayload.NetworkEntry> entries = new ArrayList<>(networks.size());
        for (LogisticsNetwork net : networks) {
            entries.add(new SyncNetworkListPayload.NetworkEntry(
                    net.getId(),
                    net.getName(),
                    net.getNodeUuids().size(),
                    false,
                    NodeAccessPolicy.canDelete(net.getOwnerUuid(), player),
                    net.getColor(),
                    net.getCreatedAt()));
        }

        NetworkHandler.sendToPlayer(player, new SyncNetworkListPayload(entries));
    }

    private void markDirty() {
        if (node != null) ServerPayloadHandler.invalidateNetwork(node);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot fromSlot = slots.get(index);
        if (!nodeSlotsActive || !hasAvailableNode() || !fromSlot.hasItem())
            return ItemStack.EMPTY;

        ItemStack fromStack = fromSlot.getItem();
        if (index < UPGRADE_SLOTS) {
            ItemStack copy = fromStack.copy();
            ItemStack remainder = fromStack.copy();
            if (!moveItemStackTo(remainder, UPGRADE_SLOTS, slots.size(), true))
                return ItemStack.EMPTY;
            fromSlot.set(remainder);
            return copy;
        }
        if (!fromStack.is(ModTags.UPGRADES))
            return ItemStack.EMPTY;

        ItemStack single = fromStack.copyWithCount(1);
        if (!moveItemStackTo(single, 0, UPGRADE_SLOTS, false))
            return ItemStack.EMPTY;
        fromStack.shrink(1);
        if (fromStack.isEmpty()) {
            fromSlot.set(ItemStack.EMPTY);
        } else {
            fromSlot.setChanged();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer serverPlayer && node != null && node.isAlive()
                && node.isOwnedBy(serverPlayer)) {
            LabelUpgradeSync.synchronizeMenuClose(serverPlayer, node, openingUpgrades);
        }
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
        public boolean mayPlace(ItemStack stack) {
            if (stack.isEmpty() || !stack.is(ModTags.UPGRADES)) {
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
}
