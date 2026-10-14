package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record MoveGraphVertexPayload(UUID networkId, String key, float x, float y) implements CustomPacketPayload {
    public static final Type<MoveGraphVertexPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "move_graph_vertex"));
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
