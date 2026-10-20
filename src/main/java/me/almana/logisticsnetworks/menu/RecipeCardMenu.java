package me.almana.logisticsnetworks.menu;

import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.NodeClipboardConfig;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.VirtualFilterType;
import me.almana.logisticsnetworks.item.RecipeCardItem;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.network.ServerPayloadHandler;
import me.almana.logisticsnetworks.network.SetRecipeCardPayload;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

public class RecipeCardMenu extends AbstractContainerMenu {

    public static final int INV_X = 8;
    public static final int INV_Y = 172;
    public static final int ID_BACK = 0;

    @Nullable
    private final InteractionHand hand;
    @Nullable
    private final LogisticsNodeEntity node;
    private final int entityId;
    private final int channel;
    private final int filterSlot;
    private final int entry;
    private final int locked;
    private RecipeCardData data;

    private RecipeCardMenu(int containerId, Inventory inventory, @Nullable InteractionHand hand,
            @Nullable LogisticsNodeEntity node, int entityId, int channel, int filterSlot, int entry,
            RecipeCardData data) {
        super(Registration.RECIPE_CARD_MENU.get(), containerId);
        this.hand = hand;
        this.node = node;
        this.entityId = entityId;
        this.channel = channel;
        this.filterSlot = filterSlot;
        this.entry = entry;
        this.data = data;
        this.locked = hand == InteractionHand.MAIN_HAND ? inventory.selected : -1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(col == locked ? new LockedSlot(inventory, col, INV_X + col * 18, INV_Y + 58)
                    : new Slot(inventory, col, INV_X + col * 18, INV_Y + 58));
        }
    }

    public RecipeCardMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, readHand(buf), null, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), RecipeCardData.STREAM_CODEC.decode(buf));
    }

    public static void openHand(ServerPlayer player, InteractionHand hand) {
        open(player, hand, null, -1, -1, -1, RecipeCardData.of(player.getItemInHand(hand)));
    }

    public static boolean openEntry(ServerPlayer player, FilterMenu filter, int entry) {
        ItemStack card = FilterItemData.getEntry(filter.getOpenedStack(), entry);
        if (!RecipeCardItem.is(card) || !filter.isBoundFilter() || filter.getGraphContext() != null) return false;
        LogisticsNodeEntity source = filter.getNodeSource();
        open(player, source == null ? filter.getHand() : null, source, filter.getNodeChannel(),
                filter.getNodeFilterSlot(), entry, RecipeCardData.of(card));
        return true;
    }

    private static void open(ServerPlayer player, @Nullable InteractionHand hand, @Nullable LogisticsNodeEntity node,
            int channel, int filterSlot, int entry, RecipeCardData data) {
        int entityId = node == null ? -1 : node.getId();
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, ignored) -> new RecipeCardMenu(id, inventory, hand, node, entityId, channel,
                        filterSlot, entry, data),
                Component.translatable("gui.logisticsnetworks.recipe_card.title")), buf -> {
                    buf.writeVarInt(hand == null ? -1 : hand.ordinal());
                    buf.writeVarInt(entityId);
                    buf.writeVarInt(channel);
                    buf.writeVarInt(filterSlot);
                    buf.writeVarInt(entry);
                    RecipeCardData.STREAM_CODEC.encode(buf, data);
                });
    }

    @Nullable
    private static InteractionHand readHand(RegistryFriendlyByteBuf buf) {
        int ordinal = buf.readVarInt();
        return ordinal < 0 ? null : InteractionHand.values()[ordinal];
    }

    public RecipeCardData data() {
        return data;
    }

    public void setData(RecipeCardData value) {
        data = value;
    }

    // Client side only
    public void edit(UnaryOperator<RecipeCardData> change) {
        RecipeCardData next = change.apply(data).sanitized();
        if (next.equals(data)) return;
        data = next;
        PacketDistributor.sendToServer(new SetRecipeCardPayload(data));
    }

    // Server side only
    public ItemStack card(Player player) {
        if (!isEntryTarget()) return player.getItemInHand(hand);
        if (node != null) return FilterItemData.getEntry(node.getChannel(channel).getFilterItem(filterSlot), entry);
        NodeClipboardConfig clipboard = WrenchItem.getClipboard(player.getItemInHand(hand), player.registryAccess());
        return clipboard == null ? ItemStack.EMPTY
                : FilterItemData.getEntry(clipboard.getFilterItem(channel, filterSlot), entry);
    }

    // Server side only
    public void store(Player player, ItemStack card) {
        if (!isEntryTarget()) {
            player.setItemInHand(hand, card);
        } else if (node != null) {
            ChannelData target = node.getChannel(channel);
            target.setFilterItem(filterSlot, withEntry(target.getFilterItem(filterSlot), card));
            ServerPayloadHandler.sendChannelSyncToViewers(node, channel, target);
            ServerPayloadHandler.propagateToLabelGroup(node, channel);
            ServerPayloadHandler.invalidateNetwork(node);
        } else {
            ItemStack wrench = player.getItemInHand(hand);
            NodeClipboardConfig clipboard = WrenchItem.getClipboard(wrench, player.registryAccess());
            if (clipboard == null) return;
            clipboard.setFilterItem(channel, filterSlot,
                    withEntry(clipboard.getFilterItem(channel, filterSlot), card));
            WrenchItem.setClipboard(wrench, clipboard, player.registryAccess());
        }
    }

    private ItemStack withEntry(ItemStack filter, ItemStack card) {
        ItemStack copy = filter.copy();
        FilterItemData.setEntry(copy, entry, card);
        return copy;
    }

    public boolean isEntryTarget() {
        return channel >= 0;
    }

    public boolean isNodeTarget() {
        return entityId >= 0;
    }

    public int entityId() {
        return entityId;
    }

    public int channel() {
        return channel;
    }

    public int filterSlot() {
        return filterSlot;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!isEntryTarget()) return RecipeCardItem.is(player.getItemInHand(hand));
        if (isNodeTarget()) return node == null || node.isAlive() && node.isOwnedBy(player)
                && RecipeCardItem.is(card(player));
        return player.getItemInHand(hand).getItem() instanceof WrenchItem;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != ID_BACK || !isEntryTarget() || isNodeTarget() || !(player instanceof ServerPlayer serverPlayer))
            return false;
        NodeClipboardConfig clipboard = WrenchItem.getClipboard(player.getItemInHand(hand), player.registryAccess());
        if (clipboard == null || !FilterMenu.openClipboard(serverPlayer, clipboard, hand, channel, filterSlot,
                VirtualFilterType.EXISTING)) {
            serverPlayer.closeContainer();
        }
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Hotkeys can't move edited card
        if (clickType == ClickType.SWAP && (button == locked || hand == InteractionHand.OFF_HAND && button == 40)) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private static class LockedSlot extends Slot {
        LockedSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
