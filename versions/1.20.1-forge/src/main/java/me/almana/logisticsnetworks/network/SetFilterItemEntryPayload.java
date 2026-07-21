package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
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
                buf.readItem());
    }

    public static void write(RegistryFriendlyByteBuf buf, SetFilterItemEntryPayload payload) {
        buf.writeVarInt(payload.slot());
        buf.writeItem(payload.itemStack());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

