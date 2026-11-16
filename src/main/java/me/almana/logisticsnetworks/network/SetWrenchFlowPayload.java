package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.component.WrenchFlow;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record SetWrenchFlowPayload(int handOrdinal, WrenchFlow flow) implements CustomPacketPayload {

    public static final Type<SetWrenchFlowPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_wrench_flow"));
    public static final StreamCodec<FriendlyByteBuf, SetWrenchFlowPayload> STREAM_CODEC = StreamCodec.of(
            SetWrenchFlowPayload::write, SetWrenchFlowPayload::read);

    public static SetWrenchFlowPayload read(FriendlyByteBuf buffer) {
        return new SetWrenchFlowPayload(buffer.readVarInt(), WrenchFlow.STREAM_CODEC.decode(buffer));
    }

    public static void write(FriendlyByteBuf buffer, SetWrenchFlowPayload payload) {
        buffer.writeVarInt(payload.handOrdinal);
        WrenchFlow.STREAM_CODEC.encode(buffer, payload.flow);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
