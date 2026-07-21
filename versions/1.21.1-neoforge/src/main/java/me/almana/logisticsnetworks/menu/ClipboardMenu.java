package me.almana.logisticsnetworks.menu;

import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.DistributionMode;
import me.almana.logisticsnetworks.data.FilterMode;
import me.almana.logisticsnetworks.data.NodeClipboardConfig;
import me.almana.logisticsnetworks.data.RedstoneMode;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ClipboardMenu extends AbstractContainerMenu {

    public static final int ID_SELECT_CHANNEL_BASE = 0;
    public static final int ID_SELECT_CHANNEL_MAX = ID_SELECT_CHANNEL_BASE + LogisticsNodeEntity.CHANNEL_COUNT - 1;

    public static final int ID_TOGGLE_ENABLED = 100;
    public static final int ID_MODE_NEXT = 101;
    public static final int ID_MODE_PREV = 102;
    public static final int ID_TYPE_NEXT = 103;
    public static final int ID_TYPE_PREV = 104;
    public static final int ID_DIRECTION_NEXT = 105;
    public static final int ID_DIRECTION_PREV = 106;
    public static final int ID_REDSTONE_NEXT = 107;
    public static final int ID_REDSTONE_PREV = 108;
    public static final int ID_DISTRIBUTION_NEXT = 109;
    public static final int ID_DISTRIBUTION_PREV = 110;
    public static final int ID_FILTER_MODE_NEXT = 111;
    public static final int ID_FILTER_MODE_PREV = 112;
    public static final int ID_PRIORITY_INC = 113;
    public static final int ID_PRIORITY_DEC = 114;
    public static final int ID_BATCH_INC = 115;
    public static final int ID_BATCH_DEC = 116;
    public static final int ID_DELAY_INC = 117;
    public static final int ID_DELAY_DEC = 118;
    public static final int ID_CLEAR_CLIPBOARD = 119;

    private static final int FILTER_SLOTS = 9;
    private static final int UPGRADE_SLOTS = LogisticsNodeEntity.UPGRADE_SLOT_COUNT;
    private static final int VISUAL_SLOTS = FILTER_SLOTS + UPGRADE_SLOTS;

    private static final int DATA_SELECTED_CHANNEL = 0;
    private static final int DATA_ENABLED = 1;
    private static final int DATA_MODE = 2;
    private static final int DATA_TYPE = 3;
    private static final int DATA_DIRECTION = 4;
    private static final int DATA_REDSTONE = 5;
    private static final int DATA_DISTRIBUTION = 6;
    private static final int DATA_FILTER_MODE = 7;
    private static final int DATA_PRIORITY = 8;
    private static final int DATA_BATCH = 9;
    private static final int DATA_DELAY = 10;
    private static final int DATA_SIZE = 11;

    private final InteractionHand hand;
    private final int lockedSlot;
    private final Player player;
    private final NodeClipboardConfig clipboard;

    private final ContainerData data = new SimpleContainerData(DATA_SIZE);
    private final Container filterContainer = new FilterVisualContainer();
    private final Container upgradeContainer = new UpgradeVisualContainer();

    public ClipboardMenu(int containerId, Inventory playerInventory, InteractionHand hand) {
        super(Registration.CLIPBOARD_MENU.get(), containerId);
        this.hand = hand;
        this.player = playerInventory.player;
        this.lockedSlot = hand == InteractionHand.MAIN_HAND ? playerInventory.selected : -1;

        ItemStack wrenchStack = getWrenchStack();
        NodeClipboardConfig loaded = WrenchItem.getClipboard(wrenchStack, player.registryAccess());
        this.clipboard = loaded == null ? NodeClipboardConfig.createEmpty() : loaded;

        layoutSlots(playerInventory);
        addDataSlots(data);
        syncDataFromClipboard();
    }

    public ClipboardMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        super(Registration.CLIPBOARD_MENU.get(), containerId);
        int handOrdinal = buf.readVarInt();
        this.hand = handOrdinal == InteractionHand.OFF_HAND.ordinal() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        this.player = playerInventory.player;
        this.lockedSlot = hand == InteractionHand.MAIN_HAND ? playerInventory.selected : -1;
        this.clipboard = NodeClipboardConfig.createEmpty();

        layoutSlots(playerInventory);
        addDataSlots(data);
    }

    private void layoutSlots(Inventory inventory) {
        int filterStartX = 170;
        int filterStartY = 52;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            int row = i / 3;
            int col = i % 3;
            addSlot(new VisualSlot(filterContainer, i, filterStartX + col * 19, filterStartY + row * 19));
        }

        int upgradeStartX = 170;
        int upgradeStartY = 130;
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            int row = i / 2;
            int col = i % 2;
            addSlot(new VisualSlot(upgradeContainer, i, upgradeStartX + col * 19, upgradeStartY + row * 19));
        }

        int playerX = 32;
        int playerY = 194;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new PlayerSlot(inventory, col + row * 9 + 9, playerX + col * 18, playerY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new PlayerSlot(inventory, col, playerX + col * 18, playerY + 58));
        }
    }

    public int getSelectedChannel() {
        return data.get(DATA_SELECTED_CHANNEL);
    }

    public boolean isChannelEnabled() {
        return data.get(DATA_ENABLED) == 1;
    }

    public ChannelMode getChannelMode() {
        return ChannelMode.values()[clampOrdinal(data.get(DATA_MODE), ChannelMode.values().length)];
    }

    public ChannelType getChannelType() {
        return ChannelType.values()[clampOrdinal(data.get(DATA_TYPE), ChannelType.values().length)];
    }

    public Direction getDirection() {
        return Direction.values()[clampOrdinal(data.get(DATA_DIRECTION), Direction.values().length)];
    }

    public RedstoneMode getRedstoneMode() {
        return RedstoneMode.values()[clampOrdinal(data.get(DATA_REDSTONE), RedstoneMode.values().length)];
    }

    public DistributionMode getDistributionMode() {
        return DistributionMode.values()[clampOrdinal(data.get(DATA_DISTRIBUTION), DistributionMode.values().length)];
    }

    public FilterMode getFilterMode() {
        return FilterMode.values()[clampOrdinal(data.get(DATA_FILTER_MODE), FilterMode.values().length)];
    }

    public int getPriority() {
        return data.get(DATA_PRIORITY);
    }

    public int getBatch() {
        return data.get(DATA_BATCH);
    }

    public int getDelay() {
        return data.get(DATA_DELAY);
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
        if (slotId >= 0 && slotId < VISUAL_SLOTS) {
            if (clickType == ClickType.PICKUP) {
                applyVisualSlotClick(slotId);
            }
            return;
        }

        if (clickType == ClickType.QUICK_MOVE) {
            return;
        }

        super.clicked(slotId, dragType, clickType, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().isClientSide) {
            return false;
        }

        int selected = getSelectedChannel();
        if (id >= ID_SELECT_CHANNEL_BASE && id <= ID_SELECT_CHANNEL_MAX) {
            data.set(DATA_SELECTED_CHANNEL, id - ID_SELECT_CHANNEL_BASE);
            syncDataFromClipboard();
            broadcastChanges();
            return true;
        }

        switch (id) {
            case ID_TOGGLE_ENABLED -> clipboard.setChannelEnabled(selected, !clipboard.isChannelEnabled(selected));
            case ID_MODE_NEXT -> clipboard.setChannelMode(selected, cycleEnum(clipboard.getChannelMode(selected), 1));
            case ID_MODE_PREV -> clipboard.setChannelMode(selected, cycleEnum(clipboard.getChannelMode(selected), -1));
            case ID_TYPE_NEXT -> clipboard.setChannelType(selected, cycleEnum(clipboard.getChannelType(selected), 1));
            case ID_TYPE_PREV -> clipboard.setChannelType(selected, cycleEnum(clipboard.getChannelType(selected), -1));
            case ID_DIRECTION_NEXT ->
                clipboard.setChannelDirection(selected, cycleEnum(clipboard.getChannelDirection(selected), 1));
            case ID_DIRECTION_PREV ->
                clipboard.setChannelDirection(selected, cycleEnum(clipboard.getChannelDirection(selected), -1));
            case ID_REDSTONE_NEXT ->
                clipboard.setChannelRedstoneMode(selected, cycleEnum(clipboard.getChannelRedstoneMode(selected), 1));
            case ID_REDSTONE_PREV ->
                clipboard.setChannelRedstoneMode(selected, cycleEnum(clipboard.getChannelRedstoneMode(selected), -1));
            case ID_DISTRIBUTION_NEXT -> clipboard.setChannelDistributionMode(selected,
                    cycleEnum(clipboard.getChannelDistributionMode(selected), 1));
            case ID_DISTRIBUTION_PREV -> clipboard.setChannelDistributionMode(selected,
                    cycleEnum(clipboard.getChannelDistributionMode(selected), -1));
            case ID_FILTER_MODE_NEXT ->
                clipboard.setChannelFilterMode(selected, cycleEnum(clipboard.getChannelFilterMode(selected), 1));
            case ID_FILTER_MODE_PREV ->
                clipboard.setChannelFilterMode(selected, cycleEnum(clipboard.getChannelFilterMode(selected), -1));
            case ID_PRIORITY_INC -> clipboard.setChannelPriority(selected, clipboard.getChannelPriority(selected) + 1);
            case ID_PRIORITY_DEC -> clipboard.setChannelPriority(selected, clipboard.getChannelPriority(selected) - 1);
            case ID_BATCH_INC -> clipboard.setChannelBatchSize(selected, clipboard.getChannelBatchSize(selected) + 1);
            case ID_BATCH_DEC -> clipboard.setChannelBatchSize(selected, clipboard.getChannelBatchSize(selected) - 1);
            case ID_DELAY_INC -> clipboard.setChannelTickDelay(selected, clipboard.getChannelTickDelay(selected) + 1);
            case ID_DELAY_DEC -> clipboard.setChannelTickDelay(selected, clipboard.getChannelTickDelay(selected) - 1);
            case ID_CLEAR_CLIPBOARD -> {
                clipboard.clear();
                player.displayClientMessage(net.minecraft.network.chat.Component
                        .translatable("message.logisticsnetworks.clipboard.cleared"), true);
            }
            default -> {
                return false;
            }
        }

        syncDataFromClipboard();
        broadcastChanges();
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            WrenchItem.setClipboard(getWrenchStack(), clipboard, player.registryAccess());
        }
    }

    private void applyVisualSlotClick(int slotId) {
        ItemStack carried = getCarried();
        int selectedChannel = getSelectedChannel();

        if (slotId < FILTER_SLOTS) {
            if (carried.isEmpty()) {
                clipboard.setFilterItem(selectedChannel, slotId, ItemStack.EMPTY);
            } else if (carried.is(ModTags.FILTERS)) {
                clipboard.setFilterItem(selectedChannel, slotId, carried.copyWithCount(1));
            }
        } else {
            int upgradeSlot = slotId - FILTER_SLOTS;
            if (carried.isEmpty()) {
                clipboard.setUpgradeItem(upgradeSlot, ItemStack.EMPTY);
            } else if (carried.is(ModTags.UPGRADES)) {
                clipboard.setUpgradeItem(upgradeSlot, carried.copyWithCount(1));
            }
        }

        syncDataFromClipboard();
        broadcastChanges();
    }

    private void syncDataFromClipboard() {
        int channel = getSelectedChannel();
        if (channel < 0 || channel >= LogisticsNodeEntity.CHANNEL_COUNT) {
            channel = 0;
            data.set(DATA_SELECTED_CHANNEL, 0);
        }

        data.set(DATA_ENABLED, clipboard.isChannelEnabled(channel) ? 1 : 0);
        data.set(DATA_MODE, clipboard.getChannelMode(channel).ordinal());
        data.set(DATA_TYPE, clipboard.getChannelType(channel).ordinal());
        data.set(DATA_DIRECTION, clipboard.getChannelDirection(channel).ordinal());
        data.set(DATA_REDSTONE, clipboard.getChannelRedstoneMode(channel).ordinal());
        data.set(DATA_DISTRIBUTION, clipboard.getChannelDistributionMode(channel).ordinal());
        data.set(DATA_FILTER_MODE, clipboard.getChannelFilterMode(channel).ordinal());
        data.set(DATA_PRIORITY, clipboard.getChannelPriority(channel));
        data.set(DATA_BATCH, clipboard.getChannelBatchSize(channel));
        data.set(DATA_DELAY, clipboard.getChannelTickDelay(channel));
    }

    private ItemStack getWrenchStack() {
        if (hand == InteractionHand.OFF_HAND) {
            return player.getOffhandItem();
        }
        return lockedSlot >= 0 ? player.getInventory().getItem(lockedSlot) : player.getMainHandItem();
    }

    private static int clampOrdinal(int ordinal, int length) {
        if (length <= 0) {
            return 0;
        }
        if (ordinal < 0) {
            return 0;
        }
        if (ordinal >= length) {
            return length - 1;
        }
        return ordinal;
    }

    private static <T extends Enum<T>> T cycleEnum(T current, int direction) {
        T[] values = current.getDeclaringClass().getEnumConstants();
        int next = (current.ordinal() + direction + values.length) % values.length;
        return values[next];
    }

    private class FilterVisualContainer extends AbstractVisualContainer {
        FilterVisualContainer() {
            super(FILTER_SLOTS);
        }

        @Override
        public ItemStack getItem(int slot) {
            return clipboard.getFilterItem(getSelectedChannel(), slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            clipboard.setFilterItem(getSelectedChannel(), slot, stack);
            setChanged();
        }
    }

    private class UpgradeVisualContainer extends AbstractVisualContainer {
        UpgradeVisualContainer() {
            super(UPGRADE_SLOTS);
        }

        @Override
        public ItemStack getItem(int slot) {
            return clipboard.getUpgradeItem(slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            clipboard.setUpgradeItem(slot, stack);
            setChanged();
        }
    }

    private abstract class AbstractVisualContainer implements Container {
        private final int size;

        AbstractVisualContainer(int size) {
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
            setItem(slot, ItemStack.EMPTY);
            return stack;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 1);
        }

        @Override
        public void setChanged() {
            syncDataFromClipboard();
        }

        @Override
        public boolean stillValid(Player player) {
            return ClipboardMenu.this.stillValid(player);
        }

        @Override
        public void clearContent() {
        }
    }

    private static class VisualSlot extends Slot {
        VisualSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
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
