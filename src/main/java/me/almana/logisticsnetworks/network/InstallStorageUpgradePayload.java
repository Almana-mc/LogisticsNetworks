package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record InstallStorageUpgradePayload(int containerId, int entityId, int preferredSlot,
                                           ResourceLocation upgradeId) implements CustomPacketPayload {

    public static final Type<InstallStorageUpgradePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "install_storage_upgrade"));
    public static final StreamCodec<FriendlyByteBuf, InstallStorageUpgradePayload> STREAM_CODEC = StreamCodec.of(
            InstallStorageUpgradePayload::write, InstallStorageUpgradePayload::read);

    private static InstallStorageUpgradePayload read(FriendlyByteBuf buf) {
        return new InstallStorageUpgradePayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readResourceLocation());
    }

    private static void write(FriendlyByteBuf buf, InstallStorageUpgradePayload payload) {
        buf.writeVarInt(payload.containerId());
        buf.writeVarInt(payload.entityId());
        buf.writeVarInt(payload.preferredSlot());
        buf.writeResourceLocation(payload.upgradeId());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
