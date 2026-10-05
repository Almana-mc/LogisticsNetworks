package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.component.WrenchFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetWrenchFlowPayload(int handOrdinal, WrenchFlow flow) implements CustomPacketPayload {

    public static final Type<SetWrenchFlowPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "set_wrench_flow"));
    public static final StreamCodec<FriendlyByteBuf, SetWrenchFlowPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetWrenchFlowPayload::handOrdinal,
            WrenchFlow.STREAM_CODEC, SetWrenchFlowPayload::flow,
            SetWrenchFlowPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
