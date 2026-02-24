package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MassSelectConnectedPayload(int handOrdinal, BlockPos pos) implements CustomPacketPayload {

    public static final Type<MassSelectConnectedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "mass_select_connected"));

    public static final StreamCodec<FriendlyByteBuf, MassSelectConnectedPayload> STREAM_CODEC = StreamCodec
            .of(MassSelectConnectedPayload::write, MassSelectConnectedPayload::read);

    public static MassSelectConnectedPayload read(FriendlyByteBuf buf) {
        return new MassSelectConnectedPayload(buf.readVarInt(), buf.readBlockPos());
    }

    public static void write(FriendlyByteBuf buf, MassSelectConnectedPayload payload) {
        buf.writeVarInt(payload.handOrdinal);
        buf.writeBlockPos(payload.pos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
