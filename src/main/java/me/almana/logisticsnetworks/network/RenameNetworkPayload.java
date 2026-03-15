package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RenameNetworkPayload(UUID networkId, String newName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RenameNetworkPayload> TYPE = new CustomPacketPayload.Type<>(
            new ResourceLocation(Logisticsnetworks.MOD_ID, "rename_network"));

    public static final StreamCodec<FriendlyByteBuf, RenameNetworkPayload> STREAM_CODEC = StreamCodec
            .of(RenameNetworkPayload::write, RenameNetworkPayload::read);

    public static RenameNetworkPayload read(FriendlyByteBuf buf) {
        UUID networkId = buf.readUUID();
        String newName = buf.readUtf(64);
        return new RenameNetworkPayload(networkId, newName);
    }

    public static void write(FriendlyByteBuf buf, RenameNetworkPayload payload) {
        buf.writeUUID(payload.networkId);
        buf.writeUtf(payload.newName, 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
