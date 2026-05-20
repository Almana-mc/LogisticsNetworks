package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SetDefaultNodeVisibilityPayload(boolean visible) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetDefaultNodeVisibilityPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_default_node_visibility"));

    public static final StreamCodec<FriendlyByteBuf, SetDefaultNodeVisibilityPayload> STREAM_CODEC =
            StreamCodec.of(SetDefaultNodeVisibilityPayload::write, SetDefaultNodeVisibilityPayload::read);

    public static SetDefaultNodeVisibilityPayload read(FriendlyByteBuf buf) {
        return new SetDefaultNodeVisibilityPayload(buf.readBoolean());
    }

    public static void write(FriendlyByteBuf buf, SetDefaultNodeVisibilityPayload payload) {
        buf.writeBoolean(payload.visible);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
