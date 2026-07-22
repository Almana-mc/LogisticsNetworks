package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.filter.VirtualFilterType;
import me.almana.logisticsnetworks.network.codec.ByteBufCodecs;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenNodeFilterPayload(int entityId, int channel, int filterSlot,
        VirtualFilterType requestedType) implements CustomPacketPayload {

    public static final Type<OpenNodeFilterPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "open_node_filter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenNodeFilterPayload> STREAM_CODEC = StreamCodec
            .of(
                    (buf, payload) -> {
                        ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
                        ByteBufCodecs.VAR_INT.encode(buf, payload.channel);
                        ByteBufCodecs.VAR_INT.encode(buf, payload.filterSlot);
                        ByteBufCodecs.VAR_INT.encode(buf, payload.requestedType.ordinal());
                    },
                    buf -> new OpenNodeFilterPayload(
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            VirtualFilterType.fromOrdinal(ByteBufCodecs.VAR_INT.decode(buf))));

    public OpenNodeFilterPayload(int entityId, int channel, int filterSlot) {
        this(entityId, channel, filterSlot, VirtualFilterType.EXISTING);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
