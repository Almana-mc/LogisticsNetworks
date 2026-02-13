package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
