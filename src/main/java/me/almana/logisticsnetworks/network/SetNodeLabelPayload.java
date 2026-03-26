package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SetNodeLabelPayload(int entityId, String label) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetNodeLabelPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_node_label"));

    public static final StreamCodec<FriendlyByteBuf, SetNodeLabelPayload> STREAM_CODEC = StreamCodec
            .of(SetNodeLabelPayload::write, SetNodeLabelPayload::read);

    public static SetNodeLabelPayload read(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        String label = buf.readUtf(64);
        return new SetNodeLabelPayload(entityId, label);
    }

    public static void write(FriendlyByteBuf buf, SetNodeLabelPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeUtf(payload.label, 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
