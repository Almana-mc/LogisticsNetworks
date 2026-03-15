package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RequestOpenNodeSettingsPayload(UUID networkId, UUID nodeId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestOpenNodeSettingsPayload> TYPE = new CustomPacketPayload.Type<>(
            new ResourceLocation(Logisticsnetworks.MOD_ID, "request_open_node_settings"));

    public static final StreamCodec<FriendlyByteBuf, RequestOpenNodeSettingsPayload> STREAM_CODEC = StreamCodec
            .of(RequestOpenNodeSettingsPayload::write, RequestOpenNodeSettingsPayload::read);

    public static RequestOpenNodeSettingsPayload read(FriendlyByteBuf buf) {
        UUID networkId = buf.readUUID();
        UUID nodeId = buf.readUUID();
        return new RequestOpenNodeSettingsPayload(networkId, nodeId);
    }

    public static void write(FriendlyByteBuf buf, RequestOpenNodeSettingsPayload payload) {
        buf.writeUUID(payload.networkId);
        buf.writeUUID(payload.nodeId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
