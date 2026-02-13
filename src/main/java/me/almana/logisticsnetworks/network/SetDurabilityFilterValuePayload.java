package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetDurabilityFilterValuePayload(int value) implements CustomPacketPayload {

        public static final Type<SetDurabilityFilterValuePayload> TYPE = new Type<>(
                        ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_durability_filter_value"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SetDurabilityFilterValuePayload> STREAM_CODEC = StreamCodec
                        .composite(
                                        ByteBufCodecs.VAR_INT,
                                        SetDurabilityFilterValuePayload::value,
                                        SetDurabilityFilterValuePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
                return TYPE;
        }
}
