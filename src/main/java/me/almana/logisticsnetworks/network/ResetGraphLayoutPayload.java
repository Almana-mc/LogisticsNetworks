package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ResetGraphLayoutPayload(UUID networkId) implements CustomPacketPayload {
    public static final Type<ResetGraphLayoutPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "reset_graph_layout"));
    public static final StreamCodec<FriendlyByteBuf, ResetGraphLayoutPayload> STREAM_CODEC =
            StreamCodec.of(ResetGraphLayoutPayload::write, ResetGraphLayoutPayload::read);

    public static ResetGraphLayoutPayload read(FriendlyByteBuf buf) {
        return new ResetGraphLayoutPayload(buf.readUUID());
    }

    public static void write(FriendlyByteBuf buf, ResetGraphLayoutPayload payload) {
        buf.writeUUID(payload.networkId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
