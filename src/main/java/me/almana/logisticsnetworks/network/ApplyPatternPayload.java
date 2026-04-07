package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.ByteBufCodecs;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ApplyPatternPayload(boolean useOutputs, int multiplier) implements CustomPacketPayload {

    public static final Type<ApplyPatternPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "apply_pattern"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ApplyPatternPayload> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.BOOL,
                    ApplyPatternPayload::useOutputs,
                    ByteBufCodecs.VAR_INT,
                    ApplyPatternPayload::multiplier,
                    ApplyPatternPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
