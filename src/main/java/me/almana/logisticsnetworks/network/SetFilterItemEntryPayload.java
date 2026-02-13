package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record SetFilterItemEntryPayload(
        int slot,
        ItemStack itemStack) implements CustomPacketPayload {

    public static final Type<SetFilterItemEntryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_filter_item_entry"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterItemEntryPayload> STREAM_CODEC = StreamCodec
            .of(SetFilterItemEntryPayload::write, SetFilterItemEntryPayload::read);

    public static SetFilterItemEntryPayload read(RegistryFriendlyByteBuf buf) {
        return new SetFilterItemEntryPayload(
                buf.readVarInt(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
    }

    public static void write(RegistryFriendlyByteBuf buf, SetFilterItemEntryPayload payload) {
        buf.writeVarInt(payload.slot());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.itemStack());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
