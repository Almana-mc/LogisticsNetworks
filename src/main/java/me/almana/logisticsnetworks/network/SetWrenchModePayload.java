package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.item.WrenchItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetWrenchModePayload(InteractionHand hand, WrenchItem.Mode mode) implements CustomPacketPayload {
    public static final Type<SetWrenchModePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "set_wrench_mode"));
    public static final StreamCodec<FriendlyByteBuf, SetWrenchModePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeEnum(payload.hand());
                buffer.writeEnum(payload.mode());
            }, buffer -> new SetWrenchModePayload(buffer.readEnum(InteractionHand.class), buffer.readEnum(WrenchItem.Mode.class)));

    public static void handle(SetWrenchModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var held = player.getItemInHand(payload.hand());
            if (!(held.getItem() instanceof WrenchItem)) return;
            WrenchItem.setMode(held, payload.mode());
            player.getInventory().setChanged();
            player.displayClientMessage(WrenchItem.getModeChangedMessage(payload.mode()), true);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
