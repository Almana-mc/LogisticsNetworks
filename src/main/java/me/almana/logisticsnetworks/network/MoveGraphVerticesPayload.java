package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.data.graph.GraphPosition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record MoveGraphVerticesPayload(UUID networkId, Map<String, GraphPosition> positions)
        implements CustomPacketPayload {
    public static final int MAX_VERTICES = 512;
    public static final Type<MoveGraphVerticesPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "move_graph_vertices"));
    public static final StreamCodec<FriendlyByteBuf, MoveGraphVerticesPayload> STREAM_CODEC = StreamCodec.of(
            MoveGraphVerticesPayload::write, MoveGraphVerticesPayload::read);

    public MoveGraphVerticesPayload {
        positions = Map.copyOf(positions);
    }

    private static MoveGraphVerticesPayload read(FriendlyByteBuf buf) {
        UUID networkId = buf.readUUID();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_VERTICES) throw new IllegalArgumentException("Invalid graph vertex count");
        Map<String, GraphPosition> positions = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            positions.put(buf.readUtf(128), new GraphPosition(buf.readFloat(), buf.readFloat()));
        }
        return new MoveGraphVerticesPayload(networkId, positions);
    }

    private static void write(FriendlyByteBuf buf, MoveGraphVerticesPayload payload) {
        if (payload.positions().size() > MAX_VERTICES) throw new IllegalArgumentException("Too many graph vertices");
        buf.writeUUID(payload.networkId());
        buf.writeVarInt(payload.positions().size());
        payload.positions().forEach((key, position) -> {
            buf.writeUtf(key, 128);
            buf.writeFloat(position.x());
            buf.writeFloat(position.y());
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
