package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.ByteBufCodecs;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ModifyFilterModPayload(
                String modId,
                boolean remove) implements CustomPacketPayload {

        public static final Type<ModifyFilterModPayload> TYPE = new Type<>(
                        ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "modify_filter_mod"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ModifyFilterModPayload> STREAM_CODEC = StreamCodec
                        .composite(
                                        ByteBufCodecs.STRING_UTF8,
                                        ModifyFilterModPayload::modId,
                                        ByteBufCodecs.BOOL,
                                        ModifyFilterModPayload::remove,
                                        ModifyFilterModPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
                return TYPE;
        }
}

