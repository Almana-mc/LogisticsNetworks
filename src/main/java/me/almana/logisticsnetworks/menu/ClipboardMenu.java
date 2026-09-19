package me.almana.logisticsnetworks.menu;

import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.data.NodeClipboardConfig;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.VirtualFilterType;
import me.almana.logisticsnetworks.integration.ars.ArsCompat;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.logic.NodeAccessPolicy;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class ClipboardMenu extends AbstractContainerMenu {

    public static final int ID_SELECT_CHANNEL_BASE = 0;
    public static final int ID_SELECT_CHANNEL_MAX = ID_SELECT_CHANNEL_BASE + LogisticsNodeEntity.CHANNEL_COUNT - 1;
    public static final int ID_OPEN_FILTER_BASE = 100;
    private static final int FILTER_ACTIONS = VirtualFilterType.values().length;

    private static final int UPGRADE_SLOTS = LogisticsNodeEntity.UPGRADE_SLOT_COUNT;
    private static final int DATA_SELECTED_CHANNEL = 0;

    private final InteractionHand hand;
    private final int lockedSlot;
    private final Player player;
    private final ContainerData data = new SimpleContainerData(1);
    private final Container upgradeContainer = new UpgradeVisualContainer();
    private NodeClipboardConfig clipboard;
    private boolean upgradeSlotsActive = true;

    public ClipboardMenu(int containerId, Inventory inventory, InteractionHand hand) {
        this(containerId, inventory, hand, loadClipboard(inventory.player, hand), 0);
    }

    private ClipboardMenu(int containerId, Inventory inventory, InteractionHand hand,
            NodeClipboardConfig clipboard, int selectedChannel) {
        super(Registration.CLIPBOARD_MENU.get(), containerId);
        this.hand = hand;
        this.player = inventory.player;
        this.lockedSlot = hand == InteractionHand.MAIN_HAND ? inventory.selected : -1;
        this.clipboard = clipboard;
        data.set(DATA_SELECTED_CHANNEL, Math.clamp(selectedChannel, 0, LogisticsNodeEntity.CHANNEL_COUNT - 1));
        layoutSlots(inventory);
        addDataSlots(data);
    }

    public ClipboardMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        super(Registration.CLIPBOARD_MENU.get(), containerId);
        int handOrdinal = buf.readVarInt();
        this.hand = handOrdinal == InteractionHand.OFF_HAND.ordinal()
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        this.player = inventory.player;
        this.lockedSlot = hand == InteractionHand.MAIN_HAND ? inventory.selected : -1;
        data.set(DATA_SELECTED_CHANNEL,
                Math.clamp(buf.readVarInt(), 0, LogisticsNodeEntity.CHANNEL_COUNT - 1));
        NodeClipboardConfig loaded = NodeClipboardConfig.load(buf.readNbt(), player.registryAccess());
        this.clipboard = loaded == null ? NodeClipboardConfig.createEmpty() : loaded;
        layoutSlots(inventory);
        addDataSlots(data);
    }

    public static void open(ServerPlayer player, InteractionHand hand, int selectedChannel) {
        NodeClipboardConfig clipboard = loadClipboard(player, hand);
        normalize(clipboard);
        int selected = Math.clamp(selectedChannel, 0, LogisticsNodeEntity.CHANNEL_COUNT - 1);
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, ignored) -> new ClipboardMenu(id, inventory, hand, clipboard, selected),
                Component.translatable("gui.logisticsnetworks.clipboard")), buf -> {
                    buf.writeVarInt(hand.ordinal());
                    buf.writeVarInt(selected);
                    buf.writeNbt(clipboard.save(player.registryAccess()));
                });
        NodeMenu.sendAvailableNetworkListToClient(player);
    }

    private static NodeClipboardConfig loadClipboard(Player player, InteractionHand hand) {
        NodeClipboardConfig loaded = WrenchItem.getClipboard(player.getItemInHand(hand), player.registryAccess());
        return loaded == null ? NodeClipboardConfig.createEmpty() : loaded;
    }

    private void layoutSlots(Inventory inventory) {
        for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
            addSlot(new VisualSlot(upgradeContainer, slot,
                    168 + (slot % 2) * 19, 118 + (slot / 2) * 19));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new PlayerSlot(inventory, col + row * 9 + 9,
                        47 + col * 18, 218 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new PlayerSlot(inventory, col, 47 + col * 18, 276));
        }
    }

    public NodeClipboardConfig getClipboard() {
        return clipboard;
    }

    public InteractionHand getHand() {
        return hand;
    }

    public int getSelectedChannel() {
        return data.get(DATA_SELECTED_CHANNEL);
    }

    public void setSelectedChannel(int channel) {
        data.set(DATA_SELECTED_CHANNEL, Math.clamp(channel, 0, LogisticsNodeEntity.CHANNEL_COUNT - 1));
    }

    public void setUpgradeSlotsVisible(boolean visible) {
        upgradeSlotsActive = visible;
    }

    public static int filterButtonId(int slot, VirtualFilterType type) {
        return ID_OPEN_FILTER_BASE + slot * FILTER_ACTIONS + type.ordinal();
    }

    @Override
    public boolean stillValid(Player player) {
        ItemStack stack = getWrenchStack();
        return !stack.isEmpty() && stack.getItem() instanceof WrenchItem;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof VisualSlot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < UPGRADE_SLOTS) {
            if (clickType == ClickType.PICKUP) applyUpgradeClick(slotId);
            return;
        }
        if (clickType != ClickType.QUICK_MOVE) super.clicked(slotId, dragType, clickType, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().isClientSide) return false;
        if (id >= ID_SELECT_CHANNEL_BASE && id <= ID_SELECT_CHANNEL_MAX) {
            setSelectedChannel(id - ID_SELECT_CHANNEL_BASE);
            broadcastChanges();
            return true;
        }
        int action = id - ID_OPEN_FILTER_BASE;
        if (action < 0) return false;
        int slot = action / FILTER_ACTIONS;
        int typeOrdinal = action % FILTER_ACTIONS;
        if (slot >= clipboard.getFilterSlotCount()) return false;
        VirtualFilterType type = VirtualFilterType.fromOrdinal(typeOrdinal);
        return player instanceof ServerPlayer serverPlayer
                && FilterMenu.openClipboard(serverPlayer, clipboard, hand, getSelectedChannel(), slot, type);
    }

    public boolean replaceClipboard(NodeClipboardConfig next, ServerPlayer player) {
        if (!stillValid(player) || !next.isStructurallyValid()) return false;
        UUID networkId = next.getNetworkId();
        if (networkId != null) {
            LogisticsNetwork network = NetworkRegistry.get(player.serverLevel()).getNetwork(networkId);
            if (network == null || !(NodeAccessPolicy.canAccess(network.getOwnerUuid(), player.getUUID())
                    || player.hasPermissions(2))) return false;
            next.setNetworkTarget(networkId, network.getName());
            for (int channel = 0; channel < LogisticsNodeEntity.CHANNEL_COUNT; channel++) {
                next.setChannelName(channel, network.getChannelName(channel));
            }
        } else {
            next.setNetworkTarget(null, next.getNetworkName());
        }
        normalize(next);
        clipboard = next;
        saveClipboard();
        broadcastChanges();
        return true;
    }

    private static void normalize(NodeClipboardConfig config) {
        config.setNetworkTarget(config.getNetworkId(), config.getNetworkName());
        config.setNodeLabel(config.getNodeLabel());
        boolean chemical = MekanismCompat.isLoaded() && hasUpgrade(config, Registration.MEKANISM_CHEMICAL_UPGRADE.get());
        boolean source = ArsCompat.isLoaded() && hasUpgrade(config, Registration.ARS_SOURCE_UPGRADE.get());
        for (int channel = 0; channel < LogisticsNodeEntity.CHANNEL_COUNT; channel++) {
            config.setChannelName(channel, config.getChannelName(channel));
            ChannelType type = config.getChannelType(channel);
            if ((type == ChannelType.CHEMICAL && !chemical) || (type == ChannelType.SOURCE && !source)) {
                config.setChannelType(channel, ChannelType.ITEM);
            }
            config.setChannelBatchSize(channel, config.getChannelBatchSize(channel));
            config.setChannelTickDelay(channel, config.getChannelTickDelay(channel));
        }
    }

    private static boolean hasUpgrade(NodeClipboardConfig config, net.minecraft.world.item.Item upgrade) {
        for (int slot = 0; slot < config.getUpgradeSlotCount(); slot++) {
            if (config.getUpgradeItem(slot).is(upgrade)) return true;
        }
        return false;
    }

    public void saveClipboard() {
        if (!player.level().isClientSide) {
            WrenchItem.setClipboard(getWrenchStack(), clipboard, player.registryAccess());
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        saveClipboard();
    }

    private void applyUpgradeClick(int slot) {
        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            clipboard.setUpgradeItem(slot, ItemStack.EMPTY);
        } else if (carried.is(ModTags.UPGRADES)) {
            clipboard.setUpgradeItem(slot, carried);
        }
        normalize(clipboard);
        saveClipboard();
        broadcastChanges();
    }

    private ItemStack getWrenchStack() {
        if (hand == InteractionHand.OFF_HAND) return player.getOffhandItem();
        return lockedSlot >= 0 ? player.getInventory().getItem(lockedSlot) : player.getMainHandItem();
    }

    private class UpgradeVisualContainer implements Container {
        @Override
        public int getContainerSize() {
            return UPGRADE_SLOTS;
        }

        @Override
        public boolean isEmpty() {
            return clipboard.getTotalUpgradeCount() == 0;
        }

        @Override
        public ItemStack getItem(int slot) {
            return clipboard.getUpgradeItem(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack stack = getItem(slot);
            clipboard.setUpgradeItem(slot, ItemStack.EMPTY);
            return stack;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 1);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            clipboard.setUpgradeItem(slot, stack);
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return ClipboardMenu.this.stillValid(player);
        }

        @Override
        public void clearContent() {
            for (int slot = 0; slot < UPGRADE_SLOTS; slot++) clipboard.setUpgradeItem(slot, ItemStack.EMPTY);
        }
    }

    private class VisualSlot extends Slot {
        VisualSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean isActive() {
            return upgradeSlotsActive;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private class PlayerSlot extends Slot {
        private final int index;

        PlayerSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
            this.index = index;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return index != lockedSlot;
        }

        @Override
        public boolean mayPickup(Player player) {
            return index != lockedSlot;
        }
    }
}
