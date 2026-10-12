package me.almana.logisticsnetworks.menu;

import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.item.RecipeCardItem;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

public class RecipeCardMenu extends AbstractContainerMenu {

    public static final int INV_X = 8;
    public static final int INV_Y = 172;

    @Nullable
    private final InteractionHand hand;
    @Nullable
    private final LogisticsNodeEntity node;
    private final int entityId;
    private final int channel;
    private final int slot;
    private RecipeCardData data;

    private RecipeCardMenu(int containerId, Inventory inventory, @Nullable InteractionHand hand,
            @Nullable LogisticsNodeEntity node, int entityId, int channel, int slot, RecipeCardData data) {
        super(Registration.RECIPE_CARD_MENU.get(), containerId);
        this.hand = hand;
        this.node = node;
        this.entityId = entityId;
        this.channel = channel;
        this.slot = slot;
        this.data = data;
        int locked = hand == InteractionHand.MAIN_HAND ? inventory.selected : -1;
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
                RecipeCardData.STREAM_CODEC.decode(buf));
    }

    public static void openHand(ServerPlayer player, InteractionHand hand) {
        open(player, hand, null, -1, -1, -1, RecipeCardData.of(player.getItemInHand(hand)));
    }

    public static void openNode(ServerPlayer player, LogisticsNodeEntity node, int channel, int slot) {
        open(player, null, node, node.getId(), channel, slot,
                RecipeCardData.of(node.getChannel(channel).getFilterItem(slot)));
    }

    private static void open(ServerPlayer player, @Nullable InteractionHand hand, @Nullable LogisticsNodeEntity node,
            int entityId, int channel, int slot, RecipeCardData data) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, ignored) -> new RecipeCardMenu(id, inventory, hand, node, entityId, channel, slot, data),
                Component.translatable("gui.logisticsnetworks.recipe_card.title")), buf -> {
                    buf.writeVarInt(hand == null ? -1 : hand.ordinal());
                    buf.writeVarInt(entityId);
                    buf.writeVarInt(channel);
                    buf.writeVarInt(slot);
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
        data = change.apply(data).sanitized();
        PacketDistributor.sendToServer(new SetRecipeCardPayload(data));
    }

    public boolean isNodeTarget() {
        return entityId >= 0;
    }

    @Nullable
    public InteractionHand hand() {
        return hand;
    }

    public int entityId() {
        return entityId;
    }

    public int channel() {
        return channel;
    }

    public int slot() {
        return slot;
    }

    @Override
    public boolean stillValid(Player player) {
        if (hand != null) return RecipeCardItem.is(player.getItemInHand(hand));
        return node == null || node.isAlive();
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
