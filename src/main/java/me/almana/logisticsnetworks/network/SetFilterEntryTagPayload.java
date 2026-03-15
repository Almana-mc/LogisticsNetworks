package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetFilterEntryTagPayload(int entryIndex, String tagId) implements CustomPacketPayload {

    public static final Type<SetFilterEntryTagPayload> TYPE = new Type<>(
            new ResourceLocation(Logisticsnetworks.MOD_ID, "set_filter_entry_tag"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterEntryTagPayload> STREAM_CODEC = StreamCodec
            .of(SetFilterEntryTagPayload::write, SetFilterEntryTagPayload::read);

    public static SetFilterEntryTagPayload read(RegistryFriendlyByteBuf buf) {
        int entryIndex = buf.readVarInt();
        String tagId = buf.readUtf();
        return new SetFilterEntryTagPayload(entryIndex, tagId);
    }

    public static void write(RegistryFriendlyByteBuf buf, SetFilterEntryTagPayload payload) {
        buf.writeVarInt(payload.entryIndex);
        buf.writeUtf(payload.tagId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
