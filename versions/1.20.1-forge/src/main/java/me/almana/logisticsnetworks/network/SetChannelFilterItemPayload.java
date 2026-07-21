package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
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
                buf.readItem());
    }

    public static void write(RegistryFriendlyByteBuf buf, SetChannelFilterItemPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeVarInt(payload.channelIndex);
        buf.writeVarInt(payload.filterSlot);
        buf.writeItem(payload.filterItem);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

