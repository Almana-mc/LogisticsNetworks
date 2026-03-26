package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record ToggleNetworkLabelHighlightPayload(UUID networkId, String label) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleNetworkLabelHighlightPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "toggle_network_label_highlight"));

    public static final StreamCodec<FriendlyByteBuf, ToggleNetworkLabelHighlightPayload> STREAM_CODEC = StreamCodec
            .of(ToggleNetworkLabelHighlightPayload::write, ToggleNetworkLabelHighlightPayload::read);

    public static ToggleNetworkLabelHighlightPayload read(FriendlyByteBuf buf) {
        return new ToggleNetworkLabelHighlightPayload(buf.readUUID(), buf.readUtf(64));
    }

    public static void write(FriendlyByteBuf buf, ToggleNetworkLabelHighlightPayload payload) {
        buf.writeUUID(payload.networkId());
        buf.writeUtf(payload.label(), 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
