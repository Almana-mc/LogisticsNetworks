package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestStorageUpgradeCatalogPayload(int containerId, int entityId, int preferredSlot)
        implements CustomPacketPayload {

    public static final Type<RequestStorageUpgradeCatalogPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "request_storage_upgrade_catalog"));
    public static final StreamCodec<FriendlyByteBuf, RequestStorageUpgradeCatalogPayload> STREAM_CODEC =
            StreamCodec.of(RequestStorageUpgradeCatalogPayload::write, RequestStorageUpgradeCatalogPayload::read);

    private static RequestStorageUpgradeCatalogPayload read(FriendlyByteBuf buf) {
        return new RequestStorageUpgradeCatalogPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    private static void write(FriendlyByteBuf buf, RequestStorageUpgradeCatalogPayload payload) {
        buf.writeVarInt(payload.containerId());
        buf.writeVarInt(payload.entityId());
        buf.writeVarInt(payload.preferredSlot());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
