package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetFilterEntryNbtPayload(int entryIndex, String key, boolean matchValue, String value, String op) implements CustomPacketPayload {

    public SetFilterEntryNbtPayload(int entryIndex, String key, boolean matchValue) {
        this(entryIndex, key, matchValue, "", "");
    }

    public SetFilterEntryNbtPayload(int entryIndex, String key, boolean matchValue, String value) {
        this(entryIndex, key, matchValue, value, "");
    }

    public static final Type<SetFilterEntryNbtPayload> TYPE = new Type<>(
            new ResourceLocation(Logisticsnetworks.MOD_ID, "set_filter_entry_nbt"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterEntryNbtPayload> STREAM_CODEC = StreamCodec
            .of(SetFilterEntryNbtPayload::write, SetFilterEntryNbtPayload::read);

    public static SetFilterEntryNbtPayload read(RegistryFriendlyByteBuf buf) {
        int entryIndex = buf.readVarInt();
        String key = buf.readUtf();
        boolean matchValue = buf.readBoolean();
        String value = buf.readUtf();
        String op = buf.readUtf();
        return new SetFilterEntryNbtPayload(entryIndex, key, matchValue, value, op);
    }

    public static void write(RegistryFriendlyByteBuf buf, SetFilterEntryNbtPayload payload) {
        buf.writeVarInt(payload.entryIndex);
        buf.writeUtf(payload.key);
        buf.writeBoolean(payload.matchValue);
        buf.writeUtf(payload.value);
        buf.writeUtf(payload.op);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
