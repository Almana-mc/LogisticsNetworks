package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import java.util.Optional;

import java.util.UUID;

public record RequestOpenGraphPayload(BlockPos computerPos, Identifier computerDimension, UUID networkId, Optional<UUID> nodeId, int selectedChannel) implements CustomPacketPayload {
    public static final Type<RequestOpenGraphPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "request_open_graph"));
    public static final StreamCodec<FriendlyByteBuf, RequestOpenGraphPayload> STREAM_CODEC =
            StreamCodec.of(RequestOpenGraphPayload::write, RequestOpenGraphPayload::read);

    public static RequestOpenGraphPayload read(FriendlyByteBuf buf) {
        return new RequestOpenGraphPayload(buf.readBlockPos(), buf.readIdentifier(), buf.readUUID(), buf.readBoolean() ? Optional.of(buf.readUUID()) : Optional.empty(), buf.readVarInt());
    }

    public static void write(FriendlyByteBuf buf, RequestOpenGraphPayload payload) {
        buf.writeBlockPos(payload.computerPos);
        buf.writeIdentifier(payload.computerDimension);
        buf.writeUUID(payload.networkId);
        buf.writeBoolean(payload.nodeId.isPresent());
        payload.nodeId.ifPresent(buf::writeUUID);
        buf.writeVarInt(payload.selectedChannel);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

