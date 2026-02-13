package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

public record AssignNetworkPayload(
        int entityId,
        Optional<UUID> networkId,
        String newNetworkName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AssignNetworkPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "assign_network"));

    public static final StreamCodec<FriendlyByteBuf, AssignNetworkPayload> STREAM_CODEC = StreamCodec
            .of(AssignNetworkPayload::write, AssignNetworkPayload::read);

    public static AssignNetworkPayload read(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        boolean hasExisting = buf.readBoolean();
        Optional<UUID> networkId = hasExisting ? Optional.of(buf.readUUID()) : Optional.empty();
        String name = buf.readUtf(64);
        return new AssignNetworkPayload(entityId, networkId, name);
    }

    public static void write(FriendlyByteBuf buf, AssignNetworkPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeBoolean(payload.networkId.isPresent());
        payload.networkId.ifPresent(buf::writeUUID);
        buf.writeUtf(payload.newNetworkName, 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
