package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record SyncOpenFilterPayload(int containerId, ItemStack filter) implements CustomPacketPayload {
    public static final Type<SyncOpenFilterPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "sync_open_filter"));
    public static final StreamCodec<FriendlyByteBuf, SyncOpenFilterPayload> STREAM_CODEC = StreamCodec
            .of((buf, payload) -> {
                buf.writeVarInt(payload.containerId());
                buf.writeItem(payload.filter());
            }, buf -> new SyncOpenFilterPayload(buf.readVarInt(), buf.readItem()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
