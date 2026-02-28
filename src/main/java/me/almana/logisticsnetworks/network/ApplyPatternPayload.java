package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
