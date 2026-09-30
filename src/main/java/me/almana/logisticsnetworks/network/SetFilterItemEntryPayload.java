package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record SetFilterItemEntryPayload(
        int slot,
        int action,
        ItemStack itemStack) implements CustomPacketPayload {

    public static final int ACTION_SET = 0;
    public static final int ACTION_CLEAR_ITEM = 1;
    public static final int ACTION_CLEAR_ENTRY = 2;

    public static SetFilterItemEntryPayload set(int slot, ItemStack itemStack) {
        return new SetFilterItemEntryPayload(slot, ACTION_SET, itemStack);
    }

    public static SetFilterItemEntryPayload clearItem(int slot) {
        return new SetFilterItemEntryPayload(slot, ACTION_CLEAR_ITEM, ItemStack.EMPTY);
    }

    public static SetFilterItemEntryPayload clearEntry(int slot) {
        return new SetFilterItemEntryPayload(slot, ACTION_CLEAR_ENTRY, ItemStack.EMPTY);
    }

    public static final Type<SetFilterItemEntryPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "set_filter_item_entry"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterItemEntryPayload> STREAM_CODEC = StreamCodec
            .of(SetFilterItemEntryPayload::write, SetFilterItemEntryPayload::read);

    public static SetFilterItemEntryPayload read(RegistryFriendlyByteBuf buf) {
        return new SetFilterItemEntryPayload(
                buf.readVarInt(),
                buf.readVarInt(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
    }

    public static void write(RegistryFriendlyByteBuf buf, SetFilterItemEntryPayload payload) {
        buf.writeVarInt(payload.slot());
        buf.writeVarInt(payload.action());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.itemStack());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
