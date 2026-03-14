package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RequestOpenNodeSettingsPayload(UUID networkId, UUID nodeId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestOpenNodeSettingsPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "request_open_node_settings"));

    public static final StreamCodec<FriendlyByteBuf, RequestOpenNodeSettingsPayload> STREAM_CODEC = StreamCodec
            .of(RequestOpenNodeSettingsPayload::write, RequestOpenNodeSettingsPayload::read);

    public static RequestOpenNodeSettingsPayload read(FriendlyByteBuf buf) {
        return new RequestOpenNodeSettingsPayload(buf.readUUID(), buf.readUUID());
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
