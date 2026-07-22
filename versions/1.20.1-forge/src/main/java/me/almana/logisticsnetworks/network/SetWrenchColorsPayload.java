package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record SetWrenchColorsPayload(int handOrdinal, boolean reset, int caseRgb, int screenRgb)
        implements CustomPacketPayload {

    public static final Type<SetWrenchColorsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "set_wrench_colors"));

    public static final StreamCodec<FriendlyByteBuf, SetWrenchColorsPayload> STREAM_CODEC = StreamCodec
            .of(SetWrenchColorsPayload::write, SetWrenchColorsPayload::read);

    public static SetWrenchColorsPayload read(FriendlyByteBuf buf) {
        return new SetWrenchColorsPayload(buf.readVarInt(), buf.readBoolean(), buf.readInt(), buf.readInt());
    }

    public static void write(FriendlyByteBuf buf, SetWrenchColorsPayload payload) {
        buf.writeVarInt(payload.handOrdinal);
        buf.writeBoolean(payload.reset);
        buf.writeInt(payload.caseRgb);
        buf.writeInt(payload.screenRgb);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
