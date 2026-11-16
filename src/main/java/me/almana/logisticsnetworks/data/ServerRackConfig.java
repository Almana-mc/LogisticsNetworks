package me.almana.logisticsnetworks.data;

import io.netty.handler.codec.DecoderException;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record ServerRackConfig(Optional<UUID> left, Optional<UUID> right, List<Row> rows) {

    public static final int ROWS = 3;
    private static final String KEY_LEFT = "left";
    private static final String KEY_RIGHT = "right";
    private static final String KEY_ROWS = "rows";
    private static final String KEY_LINKED = "linked";

    public record Row(int left, int right, boolean linked) {

        public int channel(boolean leftSide) {
            return leftSide ? left : right;
        }

        public Row withChannel(boolean leftSide, int channel) {
            return leftSide ? new Row(channel, right, linked) : new Row(left, channel, linked);
        }

        public Row toggled() {
            return new Row(left, right, !linked);
        }
    }

    public static final ServerRackConfig EMPTY = new ServerRackConfig(Optional.empty(), Optional.empty(),
            List.of(new Row(0, 0, false), new Row(1, 1, false), new Row(2, 2, false)));

    public static final StreamCodec<FriendlyByteBuf, ServerRackConfig> STREAM_CODEC = StreamCodec
            .of(ServerRackConfig::write, ServerRackConfig::read);

    public boolean bridges() {
        return left.isPresent() && right.isPresent() && !left.equals(right);
    }

    public ServerRackConfig withSide(boolean leftSide, Optional<UUID> network) {
        return leftSide ? new ServerRackConfig(network, right, rows) : new ServerRackConfig(left, network, rows);
    }

    public ServerRackConfig withRow(int index, Row row) {
        List<Row> next = new ArrayList<>(rows);
        next.set(index, row);
        return new ServerRackConfig(left, right, List.copyOf(next));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        left.ifPresent(id -> tag.putUUID(KEY_LEFT, id));
        right.ifPresent(id -> tag.putUUID(KEY_RIGHT, id));
        ListTag list = new ListTag();
        for (Row row : rows) {
            CompoundTag entry = new CompoundTag();
            entry.putInt(KEY_LEFT, row.left());
            entry.putInt(KEY_RIGHT, row.right());
            entry.putBoolean(KEY_LINKED, row.linked());
            list.add(entry);
        }
        tag.put(KEY_ROWS, list);
        return tag;
    }

    // Same layout as 1.21.1 codec
    public static Optional<ServerRackConfig> load(CompoundTag tag) {
        ListTag list = tag.getList(KEY_ROWS, Tag.TAG_COMPOUND);
        if (list.size() != ROWS) {
            return Optional.empty();
        }
        List<Row> rows = new ArrayList<>(ROWS);
        for (int i = 0; i < ROWS; i++) {
            CompoundTag entry = list.getCompound(i);
            int leftChannel = entry.getInt(KEY_LEFT);
            int rightChannel = entry.getInt(KEY_RIGHT);
            if (!isChannel(leftChannel) || !isChannel(rightChannel)) {
                return Optional.empty();
            }
            rows.add(new Row(leftChannel, rightChannel, entry.getBoolean(KEY_LINKED)));
        }
        return Optional.of(new ServerRackConfig(uuid(tag, KEY_LEFT), uuid(tag, KEY_RIGHT), List.copyOf(rows)));
    }

    private static Optional<UUID> uuid(CompoundTag tag, String key) {
        return tag.hasUUID(key) ? Optional.of(tag.getUUID(key)) : Optional.empty();
    }

    private static boolean isChannel(int channel) {
        return channel >= 0 && channel < LogisticsNodeEntity.CHANNEL_COUNT;
    }

    private static void write(FriendlyByteBuf buf, ServerRackConfig config) {
        buf.writeOptional(config.left, FriendlyByteBuf::writeUUID);
        buf.writeOptional(config.right, FriendlyByteBuf::writeUUID);
        for (Row row : config.rows) {
            buf.writeVarInt(row.left());
            buf.writeVarInt(row.right());
            buf.writeBoolean(row.linked());
        }
    }

    private static ServerRackConfig read(FriendlyByteBuf buf) {
        Optional<UUID> left = buf.readOptional(FriendlyByteBuf::readUUID);
        Optional<UUID> right = buf.readOptional(FriendlyByteBuf::readUUID);
        List<Row> rows = new ArrayList<>(ROWS);
        for (int i = 0; i < ROWS; i++) {
            rows.add(new Row(readChannel(buf), readChannel(buf), buf.readBoolean()));
        }
        return new ServerRackConfig(left, right, List.copyOf(rows));
    }

    private static int readChannel(FriendlyByteBuf buf) {
        int channel = buf.readVarInt();
        if (!isChannel(channel)) {
            throw new DecoderException("Server rack channel out of range: " + channel);
        }
        return channel;
    }
}
