package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import me.almana.logisticsnetworks.integration.curios.CuriosCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ToggleWornWrenchPayload() implements CustomPacketPayload {
    public static final ToggleWornWrenchPayload INSTANCE = new ToggleWornWrenchPayload();
    public static final Type<ToggleWornWrenchPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "toggle_worn_wrench"));
    public static final StreamCodec<FriendlyByteBuf, ToggleWornWrenchPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static void handle(ToggleWornWrenchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ItemStack worn = CuriosCompat.wornWrench(player);
            if (worn.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.logisticsnetworks.wrench.worn.none"), true);
                return;
            }
            boolean drawn = !worn.getOrDefault(LogisticsDataComponents.WRENCH_DRAWN, false);
            worn.set(LogisticsDataComponents.WRENCH_DRAWN, drawn);
            player.displayClientMessage(Component.translatable(drawn
                    ? "message.logisticsnetworks.wrench.worn.drawn"
                    : "message.logisticsnetworks.wrench.worn.stowed"), true);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
