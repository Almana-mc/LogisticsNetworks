package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record SyncClipboardPayload(CompoundTag clipboardTag) implements CustomPacketPayload {

    public static final Type<SyncClipboardPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "sync_clipboard"));

    public static final StreamCodec<FriendlyByteBuf, SyncClipboardPayload> STREAM_CODEC = StreamCodec
            .of((buf, payload) -> buf.writeNbt(payload.clipboardTag), buf -> new SyncClipboardPayload(buf.readNbt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
