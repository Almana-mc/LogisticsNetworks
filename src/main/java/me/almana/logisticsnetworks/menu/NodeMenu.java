package me.almana.logisticsnetworks.menu;

import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.network.SyncNetworkListPayload;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class NodeMenu extends AbstractContainerMenu {

    private static final int PLAYER_INV_X = 47;
    private static final int PLAYER_INV_Y = 176;

    // Grid Layout constants
    private static final int FILTER_GRID_X = 168;
    private static final int FILTER_GRID_Y = 50;
    private static final int FILTER_SLOTS = 9;

    private static final int UPGRADE_GRID_Y = 119;
    private static final int UPGRADE_SLOTS = LogisticsNodeEntity.UPGRADE_SLOT_COUNT;
    private static final int GRID_STEP = 19;

    private final LogisticsNodeEntity node;
    private int selectedChannel = 0;

    private final Container filterContainer;
    private final Container upgradeContainer;

    // Server-side
    public NodeMenu(int containerId, Inventory playerInv, LogisticsNodeEntity node) {
        super(Registration.NODE_MENU.get(), containerId);
        this.node = node;
        this.filterContainer = new FilterItemsContainer();
        this.upgradeContainer = new UpgradeItemsContainer();

        layoutNodeSlots();
        layoutPlayerSlots(playerInv);
    }

    // Client-side
    public NodeMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(Registration.NODE_MENU.get(), containerId);
        int entityId = buf.readVarInt();
        Entity entity = playerInv.player.level().getEntity(entityId);
        this.node = (entity instanceof LogisticsNodeEntity n) ? n : null;

        this.filterContainer = new FilterItemsContainer();
        this.upgradeContainer = new UpgradeItemsContainer();

        if (this.node != null) {
            readInitialData(buf, playerInv.player.level().registryAccess());
        }

        layoutNodeSlots();
        layoutPlayerSlots(playerInv);
    }

    private void readInitialData(FriendlyByteBuf buf, HolderLookup.Provider provider) {
        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            CompoundTag tag = buf.readNbt();
            if (tag != null) {
                node.getChannel(i).load(tag, provider);
            }
        }
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            CompoundTag tag = buf.readNbt();
            if (tag != null) {
                node.setUpgradeItem(i, ItemStack.parseOptional(provider, tag));
            }
        }
    }

    // Slot Layout

    private void layoutNodeSlots() {
        // 3x3 Filter Grid
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int index = r * 3 + c;
                addSlot(new FilterSlot(filterContainer, index,
                        FILTER_GRID_X + c * GRID_STEP,
                        FILTER_GRID_Y + r * GRID_STEP));
            }
        }

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
                addSlot(new Slot(inv, c + r * 9 + 9, PLAYER_INV_X + c * 18, PLAYER_INV_Y + r * 18));
            }
        }
        // Hotbar
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inv, c, PLAYER_INV_X + c * 18, PLAYER_INV_Y + 58));
        }
    }

    public LogisticsNodeEntity getNode() {
        return node;
    }

    public int getSelectedChannel() {
        return selectedChannel;
    }

    public void setSelectedChannel(int channelIndex) {
        this.selectedChannel = Math.max(0, Math.min(8, channelIndex));
        broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return node != null && node.isAlive() && player.distanceToSqr(node) < 64.0;
    }

    public void sendNetworkListToClient(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level))
            return;

        var networks = NetworkRegistry.get(level).getAllNetworks().values();
        List<SyncNetworkListPayload.NetworkEntry> entries = new ArrayList<>(networks.size());

        for (LogisticsNetwork net : networks) {
            entries.add(new SyncNetworkListPayload.NetworkEntry(net.getId(), net.getName(), net.getNodeUuids().size()));
        }

        PacketDistributor.sendToPlayer(player, new SyncNetworkListPayload(entries));
    }

    private void markDirty() {
        if (node != null && node.getNetworkId() != null && node.level() instanceof ServerLevel level) {
            NetworkRegistry.get(level).markNetworkDirty(node.getNetworkId());
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot fromSlot = slots.get(index);
        if (fromSlot == null || !fromSlot.hasItem())
            return ItemStack.EMPTY;

        ItemStack fromStack = fromSlot.getItem();
        ItemStack copy = fromStack.copy();

        int nodeSlotCount = FILTER_SLOTS + UPGRADE_SLOTS;

        if (index < nodeSlotCount) {
            if (!moveItemStackTo(fromStack, nodeSlotCount, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (fromStack.is(ModTags.FILTERS)) {
                if (!moveItemStackTo(fromStack, 0, FILTER_SLOTS, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (fromStack.is(ModTags.UPGRADES)) {
                if (!moveItemStackTo(fromStack, FILTER_SLOTS, nodeSlotCount, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (fromStack.isEmpty()) {
            fromSlot.set(ItemStack.EMPTY);
        } else {
            fromSlot.setChanged();
        }

        return copy;
    }

    private class FilterItemsContainer extends AbstractProxyContainer {
        FilterItemsContainer() {
            super(FILTER_SLOTS);
        }

        @Override
        public ItemStack getItem(int slot) {
            return (node != null) ? node.getChannel(selectedChannel).getFilterItem(slot) : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (node != null) {
                node.getChannel(selectedChannel).setFilterItem(slot, stack.copyWithCount(1));
                markDirty();
            }
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

    private static class FilterSlot extends Slot {
        FilterSlot(Container c, int i, int x, int y) {
            super(c, i, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !stack.isEmpty() && stack.is(ModTags.FILTERS);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private static class UpgradeSlot extends Slot {
        UpgradeSlot(Container c, int i, int x, int y) {
            super(c, i, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !stack.isEmpty() && stack.is(ModTags.UPGRADES);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
