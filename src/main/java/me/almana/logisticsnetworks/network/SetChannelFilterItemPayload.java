package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record SetChannelFilterItemPayload(
        int entityId,
        int channelIndex,
        int filterSlot,
        ItemStack filterItem) implements CustomPacketPayload {

    public static final Type<SetChannelFilterItemPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_channel_filter_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetChannelFilterItemPayload> STREAM_CODEC = StreamCodec
            .of(SetChannelFilterItemPayload::write, SetChannelFilterItemPayload::read);

    public static SetChannelFilterItemPayload read(RegistryFriendlyByteBuf buf) {
        return new SetChannelFilterItemPayload(
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
    }

    public static void write(RegistryFriendlyByteBuf buf, SetChannelFilterItemPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeVarInt(payload.channelIndex);
        buf.writeVarInt(payload.filterSlot);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.filterItem);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
