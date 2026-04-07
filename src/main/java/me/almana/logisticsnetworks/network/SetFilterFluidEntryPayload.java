package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.ByteBufCodecs;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
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

