package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenNodeMenuPayload(int entityId, int selectedChannel) implements CustomPacketPayload {

    public static final Type<OpenNodeMenuPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "open_node_menu"));

    public static final StreamCodec<FriendlyByteBuf, OpenNodeMenuPayload> STREAM_CODEC = StreamCodec
            .of(OpenNodeMenuPayload::write, OpenNodeMenuPayload::read);

    private static OpenNodeMenuPayload read(FriendlyByteBuf buf) {
        return new OpenNodeMenuPayload(buf.readVarInt(), buf.readVarInt());
    }

    private static void write(FriendlyByteBuf buf, OpenNodeMenuPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeVarInt(payload.selectedChannel);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
