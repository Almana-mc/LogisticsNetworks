package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetFilterEntryNbtPayload(int slot, int action, String path, String operator, int ruleIndex, String value)
        implements CustomPacketPayload {

    public static final int ACTION_ADD = 0;
    public static final int ACTION_REMOVE = 1;
    public static final int ACTION_TOGGLE_MATCH = 2;
    public static final int ACTION_CLEAR = 3;
    public static final int ACTION_SET_VALUE = 4;
    public static final int ACTION_SET_RAW = 5;

    public static SetFilterEntryNbtPayload add(int slot, String path, String operator, String fallbackValue) {
        return new SetFilterEntryNbtPayload(slot, ACTION_ADD, path, operator, -1, fallbackValue);
    }

    public static SetFilterEntryNbtPayload remove(int slot, int ruleIndex) {
        return new SetFilterEntryNbtPayload(slot, ACTION_REMOVE, "", "=", ruleIndex, "");
    }

    public static SetFilterEntryNbtPayload toggleMatch(int slot) {
        return new SetFilterEntryNbtPayload(slot, ACTION_TOGGLE_MATCH, "", "=", -1, "");
    }

    public static SetFilterEntryNbtPayload clear(int slot) {
        return new SetFilterEntryNbtPayload(slot, ACTION_CLEAR, "", "=", -1, "");
    }

    public static SetFilterEntryNbtPayload setValue(int slot, int ruleIndex, String value) {
        return new SetFilterEntryNbtPayload(slot, ACTION_SET_VALUE, "", "=", ruleIndex, value);
    }

    public static SetFilterEntryNbtPayload setRaw(int slot, String rawSnbt) {
        return new SetFilterEntryNbtPayload(slot, ACTION_SET_RAW, "", "=", -1, rawSnbt);
    }

    public static final Type<SetFilterEntryNbtPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "set_filter_entry_nbt"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterEntryNbtPayload> STREAM_CODEC = StreamCodec
            .of(SetFilterEntryNbtPayload::write, SetFilterEntryNbtPayload::read);

    public static SetFilterEntryNbtPayload read(RegistryFriendlyByteBuf buf) {
        int slot = buf.readVarInt();
        int action = buf.readVarInt();
        String path = buf.readUtf();
        String operator = buf.readUtf();
        int ruleIndex = buf.readVarInt();
        String value = buf.readUtf();
        return new SetFilterEntryNbtPayload(slot, action, path, operator, ruleIndex, value);
    }

    public static void write(RegistryFriendlyByteBuf buf, SetFilterEntryNbtPayload payload) {
        buf.writeVarInt(payload.slot);
        buf.writeVarInt(payload.action);
        buf.writeUtf(payload.path);
        buf.writeUtf(payload.operator);
        buf.writeVarInt(payload.ruleIndex);
        buf.writeUtf(payload.value);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
