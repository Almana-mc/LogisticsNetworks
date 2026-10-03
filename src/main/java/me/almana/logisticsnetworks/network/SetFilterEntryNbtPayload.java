package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.filter.NbtPath;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetFilterEntryNbtPayload(int slot, int action, NbtPath path, String operator, int ruleIndex, String value)
        implements CustomPacketPayload {

    public static final int ACTION_ADD = 0;
    public static final int ACTION_REMOVE = 1;
    public static final int ACTION_TOGGLE_MATCH = 2;
    public static final int ACTION_CLEAR = 3;
    public static final int ACTION_SET_VALUE = 4;
    public static final int ACTION_SET_RAW = 5;
    public static final int ACTION_SET_STRICT = 6;

    public static SetFilterEntryNbtPayload add(int slot, NbtPath path, String operator, String fallbackValue) {
        return new SetFilterEntryNbtPayload(slot, ACTION_ADD, path, operator, -1, fallbackValue);
    }

    public static SetFilterEntryNbtPayload remove(int slot, int ruleIndex) {
        return new SetFilterEntryNbtPayload(slot, ACTION_REMOVE, NbtPath.EMPTY, "=", ruleIndex, "");
    }

    public static SetFilterEntryNbtPayload toggleMatch(int slot) {
        return new SetFilterEntryNbtPayload(slot, ACTION_TOGGLE_MATCH, NbtPath.EMPTY, "=", -1, "");
    }

    public static SetFilterEntryNbtPayload clear(int slot) {
        return new SetFilterEntryNbtPayload(slot, ACTION_CLEAR, NbtPath.EMPTY, "=", -1, "");
    }

    public static SetFilterEntryNbtPayload setValue(int slot, int ruleIndex, String value) {
        return new SetFilterEntryNbtPayload(slot, ACTION_SET_VALUE, NbtPath.EMPTY, "=", ruleIndex, value);
    }

    public static SetFilterEntryNbtPayload setRaw(int slot, String rawSnbt) {
        return new SetFilterEntryNbtPayload(slot, ACTION_SET_RAW, NbtPath.EMPTY, "=", -1, rawSnbt);
    }

    public static SetFilterEntryNbtPayload setStrict(int slot, boolean strict) {
        return new SetFilterEntryNbtPayload(slot, ACTION_SET_STRICT, NbtPath.EMPTY, "=", -1, Boolean.toString(strict));
    }

    public static final Type<SetFilterEntryNbtPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "set_filter_entry_nbt"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterEntryNbtPayload> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.VAR_INT,
                    SetFilterEntryNbtPayload::slot,
                    ByteBufCodecs.VAR_INT,
                    SetFilterEntryNbtPayload::action,
                    NbtPath.STREAM_CODEC,
                    SetFilterEntryNbtPayload::path,
                    ByteBufCodecs.STRING_UTF8,
                    SetFilterEntryNbtPayload::operator,
                    ByteBufCodecs.VAR_INT,
                    SetFilterEntryNbtPayload::ruleIndex,
                    ByteBufCodecs.STRING_UTF8,
                    SetFilterEntryNbtPayload::value,
                    SetFilterEntryNbtPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
