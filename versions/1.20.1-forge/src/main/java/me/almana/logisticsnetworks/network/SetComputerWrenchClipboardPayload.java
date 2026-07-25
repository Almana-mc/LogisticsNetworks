package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetComputerWrenchClipboardPayload(CompoundTag clipboardTag) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetComputerWrenchClipboardPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "set_computer_wrench_clipboard"));

    public static final StreamCodec<FriendlyByteBuf, SetComputerWrenchClipboardPayload> STREAM_CODEC = StreamCodec
            .of(SetComputerWrenchClipboardPayload::write, SetComputerWrenchClipboardPayload::read);

    public static SetComputerWrenchClipboardPayload read(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new SetComputerWrenchClipboardPayload(tag == null ? new CompoundTag() : tag);
    }

    public static void write(FriendlyByteBuf buf, SetComputerWrenchClipboardPayload payload) {
        buf.writeNbt(payload.clipboardTag);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
