package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public record SyncClipboardPayload(byte[] clipboardBytes, boolean first, boolean more) implements CustomPacketPayload {

    public static final Type<SyncClipboardPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "sync_clipboard"));

    public static final StreamCodec<FriendlyByteBuf, SyncClipboardPayload> STREAM_CODEC = StreamCodec
            .of((buf, payload) -> {
                buf.writeByteArray(payload.clipboardBytes);
                buf.writeBoolean(payload.first);
                buf.writeBoolean(payload.more);
            }, buf -> new SyncClipboardPayload(buf.readByteArray(), buf.readBoolean(), buf.readBoolean()));

    public static void send(ServerPlayer player, CompoundTag clipboardTag) {
        List<byte[]> parts = NetworkHandler.split(clipboardTag);
        for (int i = 0; i < parts.size(); i++) {
            NetworkHandler.sendToPlayer(player, new SyncClipboardPayload(parts.get(i), i == 0, i < parts.size() - 1));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
