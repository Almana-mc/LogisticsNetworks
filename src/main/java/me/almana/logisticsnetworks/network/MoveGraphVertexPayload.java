package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record MoveGraphVertexPayload(UUID networkId, String key, float x, float y) implements CustomPacketPayload {
    public static final Type<MoveGraphVertexPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "move_graph_vertex"));
    public static final StreamCodec<FriendlyByteBuf, MoveGraphVertexPayload> STREAM_CODEC =
            StreamCodec.of(MoveGraphVertexPayload::write, MoveGraphVertexPayload::read);

    public static MoveGraphVertexPayload read(FriendlyByteBuf buf) {
        return new MoveGraphVertexPayload(buf.readUUID(), buf.readUtf(256), buf.readFloat(), buf.readFloat());
    }

    public static void write(FriendlyByteBuf buf, MoveGraphVertexPayload payload) {
        buf.writeUUID(payload.networkId);
        buf.writeUtf(payload.key, 256);
        buf.writeFloat(payload.x);
        buf.writeFloat(payload.y);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

