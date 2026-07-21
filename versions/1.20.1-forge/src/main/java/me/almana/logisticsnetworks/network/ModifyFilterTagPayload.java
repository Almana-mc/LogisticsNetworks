package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.ByteBufCodecs;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ModifyFilterTagPayload(
                String tag,
                boolean remove) implements CustomPacketPayload {

        public static final Type<ModifyFilterTagPayload> TYPE = new Type<>(
                        ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "modify_filter_tag"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ModifyFilterTagPayload> STREAM_CODEC = StreamCodec
                        .composite(
                                        ByteBufCodecs.STRING_UTF8,
                                        ModifyFilterTagPayload::tag,
                                        ByteBufCodecs.BOOL,
                                        ModifyFilterTagPayload::remove,
                                        ModifyFilterTagPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
                return TYPE;
        }
}

