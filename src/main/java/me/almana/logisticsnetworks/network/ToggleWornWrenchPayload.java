package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.integration.curios.CuriosCompat;
import me.almana.logisticsnetworks.item.WornWrench;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import me.almana.logisticsnetworks.network.payload.IPayloadContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public record ToggleWornWrenchPayload() implements CustomPacketPayload {
    public static final ToggleWornWrenchPayload INSTANCE = new ToggleWornWrenchPayload();
    public static final Type<ToggleWornWrenchPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "toggle_worn_wrench"));
    public static final StreamCodec<FriendlyByteBuf, ToggleWornWrenchPayload> STREAM_CODEC = StreamCodec
            .of((buf, payload) -> {
            }, buf -> INSTANCE);

    public static void handle(ToggleWornWrenchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ItemStack worn = CuriosCompat.wornWrench(player);
            if (worn.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.logisticsnetworks.wrench.worn.none"), true);
                return;
            }
            boolean drawn = !WornWrench.isDrawn(worn);
            WornWrench.setDrawn(worn, drawn);
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
