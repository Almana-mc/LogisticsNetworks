package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.ByteBufCodecs;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenFilterInSlotPayload(int slotIndex) implements CustomPacketPayload {

    public static final Type<OpenFilterInSlotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "open_filter_in_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFilterInSlotPayload> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.VAR_INT,
                    OpenFilterInSlotPayload::slotIndex,
                    OpenFilterInSlotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
