package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record SetClipboardSettingsPayload(CompoundTag clipboardTag) implements CustomPacketPayload {

    public static final Type<SetClipboardSettingsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_clipboard_settings"));

    public static final StreamCodec<FriendlyByteBuf, SetClipboardSettingsPayload> STREAM_CODEC = StreamCodec
            .of(SetClipboardSettingsPayload::write, SetClipboardSettingsPayload::read);

    private static SetClipboardSettingsPayload read(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new SetClipboardSettingsPayload(tag == null ? new CompoundTag() : tag);
    }

    private static void write(FriendlyByteBuf buf, SetClipboardSettingsPayload payload) {
        buf.writeNbt(payload.clipboardTag);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
