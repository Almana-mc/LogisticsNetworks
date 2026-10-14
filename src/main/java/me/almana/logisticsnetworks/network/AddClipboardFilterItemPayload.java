package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record AddClipboardFilterItemPayload(int filterSlot, ItemStack item) implements CustomPacketPayload {

    public static final Type<AddClipboardFilterItemPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "add_clipboard_filter_item"));

    public static final StreamCodec<FriendlyByteBuf, AddClipboardFilterItemPayload> STREAM_CODEC = StreamCodec
            .of(AddClipboardFilterItemPayload::write, AddClipboardFilterItemPayload::read);

    private static AddClipboardFilterItemPayload read(FriendlyByteBuf buf) {
        return new AddClipboardFilterItemPayload(buf.readVarInt(), buf.readItem());
    }

    private static void write(FriendlyByteBuf buf, AddClipboardFilterItemPayload payload) {
        buf.writeVarInt(payload.filterSlot);
        buf.writeItem(payload.item);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
