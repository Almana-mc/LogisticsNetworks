package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.ByteBufCodecs;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ModifyFilterNbtPayload(
                String path,
                boolean remove) implements CustomPacketPayload {

        public static final Type<ModifyFilterNbtPayload> TYPE = new Type<>(
                        ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "modify_filter_nbt"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ModifyFilterNbtPayload> STREAM_CODEC = StreamCodec
                        .composite(
                                        ByteBufCodecs.STRING_UTF8,
                                        ModifyFilterNbtPayload::path,
                                        ByteBufCodecs.BOOL,
                                        ModifyFilterNbtPayload::remove,
                                        ModifyFilterNbtPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
                return TYPE;
        }
}

