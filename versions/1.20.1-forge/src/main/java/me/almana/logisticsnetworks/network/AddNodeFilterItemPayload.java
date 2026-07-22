package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record AddNodeFilterItemPayload(
        int entityId,
        int channel,
        int filterSlot,
        ItemStack item) implements CustomPacketPayload {

    public static final Type<AddNodeFilterItemPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "add_node_filter_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AddNodeFilterItemPayload> STREAM_CODEC = StreamCodec
            .of(AddNodeFilterItemPayload::write, AddNodeFilterItemPayload::read);

    public static AddNodeFilterItemPayload read(RegistryFriendlyByteBuf buf) {
        return new AddNodeFilterItemPayload(
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readItem());
    }

    public static void write(RegistryFriendlyByteBuf buf, AddNodeFilterItemPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeVarInt(payload.channel);
        buf.writeVarInt(payload.filterSlot);
        buf.writeItem(payload.item);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
