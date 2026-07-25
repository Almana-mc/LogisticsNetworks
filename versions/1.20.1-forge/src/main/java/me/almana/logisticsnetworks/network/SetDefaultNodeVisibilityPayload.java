package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.FriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetDefaultNodeVisibilityPayload(boolean visible) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetDefaultNodeVisibilityPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "set_default_node_visibility"));

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
