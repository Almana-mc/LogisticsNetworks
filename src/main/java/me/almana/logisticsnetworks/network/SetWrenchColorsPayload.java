package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SetWrenchColorsPayload(int handOrdinal, boolean reset, int caseRgb, int screenRgb)
        implements CustomPacketPayload {

    public static final Type<SetWrenchColorsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "set_wrench_colors"));

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
