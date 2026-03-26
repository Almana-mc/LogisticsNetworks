package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ModifyFilterModPayload(
                String modId,
                boolean remove) implements CustomPacketPayload {

        public static final Type<ModifyFilterModPayload> TYPE = new Type<>(
                        Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "modify_filter_mod"));

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
