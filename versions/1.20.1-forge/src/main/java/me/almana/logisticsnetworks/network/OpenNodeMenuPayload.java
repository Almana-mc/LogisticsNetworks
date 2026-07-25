package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.FriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenNodeMenuPayload(int entityId, int selectedChannel) implements CustomPacketPayload {

    public static final Type<OpenNodeMenuPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "open_node_menu"));

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
