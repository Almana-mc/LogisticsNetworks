package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CycleWrenchModePayload(int handOrdinal, boolean forward) implements CustomPacketPayload {

    public static final Type<CycleWrenchModePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "cycle_wrench_mode"));

    public static final StreamCodec<FriendlyByteBuf, CycleWrenchModePayload> STREAM_CODEC = StreamCodec
            .of(CycleWrenchModePayload::write, CycleWrenchModePayload::read);

    public static CycleWrenchModePayload read(FriendlyByteBuf buf) {
        return new CycleWrenchModePayload(buf.readVarInt(), buf.readBoolean());
    }

    public static void write(FriendlyByteBuf buf, CycleWrenchModePayload payload) {
        buf.writeVarInt(payload.handOrdinal);
        buf.writeBoolean(payload.forward);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
