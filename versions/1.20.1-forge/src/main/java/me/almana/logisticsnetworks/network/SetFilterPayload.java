package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record SetFilterPayload(
        int entityId,
        int channelIndex,
        int filterSlot,
        ItemStack filterItem) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetFilterPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_filter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterPayload> STREAM_CODEC = StreamCodec
            .of(SetFilterPayload::write, SetFilterPayload::read);

    public static SetFilterPayload read(RegistryFriendlyByteBuf buf) {
        return new SetFilterPayload(
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readItem());
    }

    public static void write(RegistryFriendlyByteBuf buf, SetFilterPayload payload) {
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

