package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ReturnToComputerPayload(UUID networkId) implements CustomPacketPayload {
    public static final Type<ReturnToComputerPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "return_to_computer"));
    public static final StreamCodec<FriendlyByteBuf, ReturnToComputerPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> buf.writeUUID(payload.networkId()),
                    buf -> new ReturnToComputerPayload(buf.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
