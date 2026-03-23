package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ModifyFilterNbtPayload(
                int actionOrdinal,
                int ruleIndex,
                String path) implements CustomPacketPayload {

        public enum Action {
                ADD_RULE,
                TOGGLE_RULE,
                REMOVE_RULE,
                CYCLE_OPERATOR;

                public static Action fromOrdinal(int ordinal) {
                        Action[] values = values();
                        if (ordinal < 0 || ordinal >= values.length)
                                return ADD_RULE;
                        return values[ordinal];
                }
        }

        public static final Type<ModifyFilterNbtPayload> TYPE = new Type<>(
                        ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "modify_filter_nbt"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ModifyFilterNbtPayload> STREAM_CODEC = StreamCodec
                        .composite(
                                        ByteBufCodecs.VAR_INT,
                                        ModifyFilterNbtPayload::actionOrdinal,
                                        ByteBufCodecs.VAR_INT,
                                        ModifyFilterNbtPayload::ruleIndex,
                                        ByteBufCodecs.STRING_UTF8,
                                        ModifyFilterNbtPayload::path,
                                        ModifyFilterNbtPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
                return TYPE;
        }
}
