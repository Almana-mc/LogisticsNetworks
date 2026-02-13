package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
