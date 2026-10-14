package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import java.util.Optional;

import java.util.UUID;

public record RequestOpenGraphPayload(BlockPos computerPos, ResourceLocation computerDimension, UUID networkId, Optional<UUID> nodeId, int selectedChannel) implements CustomPacketPayload {
    public static final Type<RequestOpenGraphPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "request_open_graph"));
    public static final StreamCodec<FriendlyByteBuf, RequestOpenGraphPayload> STREAM_CODEC =
            StreamCodec.of(RequestOpenGraphPayload::write, RequestOpenGraphPayload::read);

    public static RequestOpenGraphPayload read(FriendlyByteBuf buf) {
        return new RequestOpenGraphPayload(buf.readBlockPos(), buf.readResourceLocation(), buf.readUUID(), buf.readBoolean() ? Optional.of(buf.readUUID()) : Optional.empty(), buf.readVarInt());
    }

    public static void write(FriendlyByteBuf buf, RequestOpenGraphPayload payload) {
        buf.writeBlockPos(payload.computerPos);
        buf.writeResourceLocation(payload.computerDimension);
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
