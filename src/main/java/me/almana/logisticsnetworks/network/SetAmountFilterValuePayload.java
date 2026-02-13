package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetAmountFilterValuePayload(int amount) implements CustomPacketPayload {

        public static final Type<SetAmountFilterValuePayload> TYPE = new Type<>(
                        ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_amount_filter_value"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SetAmountFilterValuePayload> STREAM_CODEC = StreamCodec
                        .composite(
                                        ByteBufCodecs.VAR_INT,
                                        SetAmountFilterValuePayload::amount,
                                        SetAmountFilterValuePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
                return TYPE;
        }
}
