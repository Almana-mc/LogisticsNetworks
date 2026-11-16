package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.item.WornWrench;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import me.almana.logisticsnetworks.network.payload.IPayloadContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public record UseWornWrenchPayload() implements CustomPacketPayload {
    public static final UseWornWrenchPayload INSTANCE = new UseWornWrenchPayload();
    public static final Type<UseWornWrenchPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "use_worn_wrench"));
    public static final StreamCodec<FriendlyByteBuf, UseWornWrenchPayload> STREAM_CODEC = StreamCodec
            .of((buf, payload) -> {
            }, buf -> INSTANCE);

    public static void handle(UseWornWrenchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || player.isSpectator()) return;
            ItemStack wrench = WornWrench.drawn(player);
            if (wrench.getItem() instanceof WrenchItem item) {
                item.useAir(player.level(), player, InteractionHand.MAIN_HAND, wrench);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
