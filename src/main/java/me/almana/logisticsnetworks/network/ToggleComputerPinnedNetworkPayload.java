package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ToggleComputerPinnedNetworkPayload(BlockPos computerPos, UUID networkId) implements CustomPacketPayload {

    public static final Type<ToggleComputerPinnedNetworkPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "toggle_computer_pinned_network"));

    public static final StreamCodec<FriendlyByteBuf, ToggleComputerPinnedNetworkPayload> STREAM_CODEC = StreamCodec
            .of(ToggleComputerPinnedNetworkPayload::write, ToggleComputerPinnedNetworkPayload::read);

    public static ToggleComputerPinnedNetworkPayload read(FriendlyByteBuf buf) {
        return new ToggleComputerPinnedNetworkPayload(buf.readBlockPos(), buf.readUUID());
    }

    public static void write(FriendlyByteBuf buf, ToggleComputerPinnedNetworkPayload payload) {
        buf.writeBlockPos(payload.computerPos());
        buf.writeUUID(payload.networkId());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
