package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.data.ServerRackConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record UpdateServerRackPayload(BlockPos rackPos, ServerRackConfig config) implements CustomPacketPayload {

    public static final Type<UpdateServerRackPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "update_server_rack"));

    public static final StreamCodec<FriendlyByteBuf, UpdateServerRackPayload> STREAM_CODEC = StreamCodec
            .of(UpdateServerRackPayload::write, UpdateServerRackPayload::read);

    public static UpdateServerRackPayload read(FriendlyByteBuf buf) {
        return new UpdateServerRackPayload(buf.readBlockPos(), ServerRackConfig.STREAM_CODEC.decode(buf));
    }

    public static void write(FriendlyByteBuf buf, UpdateServerRackPayload payload) {
        buf.writeBlockPos(payload.rackPos);
        ServerRackConfig.STREAM_CODEC.encode(buf, payload.config);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
