package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record ResetGraphLayoutPayload(UUID networkId) implements CustomPacketPayload {
    public static final Type<ResetGraphLayoutPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "reset_graph_layout"));
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

