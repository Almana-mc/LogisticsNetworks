package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetFilterEntryAmountPayload(int entryIndex, int batch, int stock) implements CustomPacketPayload {

    public static final Type<SetFilterEntryAmountPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_filter_entry_amount"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterEntryAmountPayload> STREAM_CODEC = StreamCodec
            .of(SetFilterEntryAmountPayload::write, SetFilterEntryAmountPayload::read);

    public static SetFilterEntryAmountPayload read(RegistryFriendlyByteBuf buf) {
        int entryIndex = buf.readVarInt();
        int batch = buf.readVarInt();
        int stock = buf.readVarInt();
        return new SetFilterEntryAmountPayload(entryIndex, batch, stock);
    }

    public static void write(RegistryFriendlyByteBuf buf, SetFilterEntryAmountPayload payload) {
        buf.writeVarInt(payload.entryIndex);
        buf.writeVarInt(payload.batch);
        buf.writeVarInt(payload.stock);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
