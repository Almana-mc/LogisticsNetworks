package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ToggleNodeVisibilityPayload(
        int entityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleNodeVisibilityPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "toggle_visibility"));

    public static final StreamCodec<FriendlyByteBuf, ToggleNodeVisibilityPayload> STREAM_CODEC = StreamCodec
            .of(ToggleNodeVisibilityPayload::write, ToggleNodeVisibilityPayload::read);

    public static ToggleNodeVisibilityPayload read(FriendlyByteBuf buf) {
        return new ToggleNodeVisibilityPayload(buf.readVarInt());
    }

    public static void write(FriendlyByteBuf buf, ToggleNodeVisibilityPayload payload) {
        buf.writeVarInt(payload.entityId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
