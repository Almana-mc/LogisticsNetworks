package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetFilterEntryEnchantedPayload(int entryIndex, boolean enabled, boolean value) implements CustomPacketPayload {

    public static final Type<SetFilterEntryEnchantedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_filter_entry_enchanted"));

    public static final StreamCodec<FriendlyByteBuf, SetFilterEntryEnchantedPayload> STREAM_CODEC = StreamCodec
            .of(SetFilterEntryEnchantedPayload::write, SetFilterEntryEnchantedPayload::read);

    private static SetFilterEntryEnchantedPayload read(FriendlyByteBuf buf) {
        return new SetFilterEntryEnchantedPayload(buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }

    private static void write(FriendlyByteBuf buf, SetFilterEntryEnchantedPayload payload) {
        buf.writeVarInt(payload.entryIndex);
        buf.writeBoolean(payload.enabled);
        buf.writeBoolean(payload.value);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
