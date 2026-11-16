package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetFilterEntryTagPayload(int entryIndex, String tagId, int kindOrdinal) implements CustomPacketPayload {

    public static final Type<SetFilterEntryTagPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_filter_entry_tag"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterEntryTagPayload> STREAM_CODEC = StreamCodec
            .of(SetFilterEntryTagPayload::write, SetFilterEntryTagPayload::read);

    public SetFilterEntryTagPayload(int entryIndex, String tagId, FilterTargetType kind) {
        this(entryIndex, tagId, kind.ordinal());
    }

    public static SetFilterEntryTagPayload clear(int entryIndex) {
        return new SetFilterEntryTagPayload(entryIndex, "", 0);
    }

    public static SetFilterEntryTagPayload read(RegistryFriendlyByteBuf buf) {
        int entryIndex = buf.readVarInt();
        String tagId = buf.readUtf();
        int kindOrdinal = buf.readVarInt();
        return new SetFilterEntryTagPayload(entryIndex, tagId, kindOrdinal);
    }

    public static void write(RegistryFriendlyByteBuf buf, SetFilterEntryTagPayload payload) {
        buf.writeVarInt(payload.entryIndex);
        buf.writeUtf(payload.tagId);
        buf.writeVarInt(payload.kindOrdinal);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
