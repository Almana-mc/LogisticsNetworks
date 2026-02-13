package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetFilterFluidEntryPayload(
                int slot,
                String fluidId) implements CustomPacketPayload {

        public static final Type<SetFilterFluidEntryPayload> TYPE = new Type<>(
                        ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_filter_fluid_entry"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterFluidEntryPayload> STREAM_CODEC = StreamCodec
                        .composite(
                                        ByteBufCodecs.VAR_INT,
                                        SetFilterFluidEntryPayload::slot,
                                        ByteBufCodecs.STRING_UTF8,
                                        SetFilterFluidEntryPayload::fluidId,
                                        SetFilterFluidEntryPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
                return TYPE;
        }
}
