package me.almana.logisticsnetworks.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record ServerRackConfig(Optional<UUID> left, Optional<UUID> right, List<Row> rows) {

    public static final int ROWS = 3;

    public record Row(int left, int right, boolean linked) {

        private static final Codec<Integer> CHANNEL = Codec.intRange(0, LogisticsNodeEntity.CHANNEL_COUNT - 1);

        static final Codec<Row> CODEC = RecordCodecBuilder.create(i -> i.group(
                CHANNEL.fieldOf("left").forGetter(Row::left),
                CHANNEL.fieldOf("right").forGetter(Row::right),
                Codec.BOOL.fieldOf("linked").forGetter(Row::linked)).apply(i, Row::new));

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

    public static final Codec<ServerRackConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.optionalFieldOf("left").forGetter(ServerRackConfig::left),
            UUIDUtil.CODEC.optionalFieldOf("right").forGetter(ServerRackConfig::right),
            Row.CODEC.listOf(ROWS, ROWS).fieldOf("rows").forGetter(ServerRackConfig::rows))
            .apply(i, ServerRackConfig::new));

    public static final StreamCodec<ByteBuf, ServerRackConfig> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

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
}
