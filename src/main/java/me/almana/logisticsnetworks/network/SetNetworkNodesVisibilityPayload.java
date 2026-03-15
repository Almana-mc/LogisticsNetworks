package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record SetNetworkNodesVisibilityPayload(UUID networkId, boolean visible) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetNetworkNodesVisibilityPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_network_nodes_visibility"));

    public static final StreamCodec<FriendlyByteBuf, SetNetworkNodesVisibilityPayload> STREAM_CODEC = StreamCodec
            .of(SetNetworkNodesVisibilityPayload::write, SetNetworkNodesVisibilityPayload::read);

    public static SetNetworkNodesVisibilityPayload read(FriendlyByteBuf buf) {
        return new SetNetworkNodesVisibilityPayload(buf.readUUID(), buf.readBoolean());
    }

    public static void write(FriendlyByteBuf buf, SetNetworkNodesVisibilityPayload payload) {
        buf.writeUUID(payload.networkId);
        buf.writeBoolean(payload.visible);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
