package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record ReturnToComputerPayload(UUID networkId) implements CustomPacketPayload {
    public static final Type<ReturnToComputerPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "return_to_computer"));
    public static final StreamCodec<FriendlyByteBuf, ReturnToComputerPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> buf.writeUUID(payload.networkId()),
                    buf -> new ReturnToComputerPayload(buf.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
