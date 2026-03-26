package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CopyPasteConnectedPayload(int handOrdinal, BlockPos pos) implements CustomPacketPayload {

    public static final Type<CopyPasteConnectedPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "copy_paste_connected"));

    public static final StreamCodec<FriendlyByteBuf, CopyPasteConnectedPayload> STREAM_CODEC = StreamCodec
            .of(CopyPasteConnectedPayload::write, CopyPasteConnectedPayload::read);

    public static CopyPasteConnectedPayload read(FriendlyByteBuf buf) {
        return new CopyPasteConnectedPayload(buf.readVarInt(), buf.readBlockPos());
    }

    public static void write(FriendlyByteBuf buf, CopyPasteConnectedPayload payload) {
        buf.writeVarInt(payload.handOrdinal);
        buf.writeBlockPos(payload.pos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
