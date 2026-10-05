package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.NetworkColors;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

public record WrenchFlow(boolean enabled, int types, int channels, Optional<UUID> network, boolean offscreen,
                         Style style, List<Integer> colors, double thickness, double speed, double opacity,
                         boolean pulses, double pulseSpacing, double pulseLength, boolean throughBlocks) {

    public static final int ALL_TYPES = 0b11111;
    public static final int ALL_CHANNELS = 0b111111111;
    public static final List<Integer> DEFAULT_COLORS = List.of(0xB87D1F, 0x1C94AC, 0xB43D3D, 0x2E944F, 0x7D49B8);
    public static final Range THICKNESS = new Range(1, 20);
    public static final Range SPEED = new Range(0.1, 60);
    public static final Range OPACITY = new Range(0.05, 1);
    public static final Range PULSE_SPACING = new Range(0.5, 32);
    public static final Range PULSE_LENGTH = new Range(0.1, 8);
    public static final WrenchFlow DEFAULT = new WrenchFlow(true, ALL_TYPES, ALL_CHANNELS, Optional.empty(), true,
            Style.ROUTED, DEFAULT_COLORS, 6, 3, 0.95, true, 3, 0.6, true);

    public static final Codec<WrenchFlow> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("enabled", DEFAULT.enabled).forGetter(WrenchFlow::enabled),
            Codec.INT.optionalFieldOf("types", ALL_TYPES).forGetter(WrenchFlow::types),
            Codec.INT.optionalFieldOf("channels", ALL_CHANNELS).forGetter(WrenchFlow::channels),
            UUIDUtil.CODEC.optionalFieldOf("network").forGetter(WrenchFlow::network),
            Codec.BOOL.optionalFieldOf("offscreen", DEFAULT.offscreen).forGetter(WrenchFlow::offscreen),
            Style.CODEC.optionalFieldOf("style", DEFAULT.style).forGetter(WrenchFlow::style),
            Codec.INT.listOf().optionalFieldOf("colors", DEFAULT_COLORS).forGetter(WrenchFlow::colors),
            Codec.DOUBLE.optionalFieldOf("thickness", DEFAULT.thickness).forGetter(WrenchFlow::thickness),
            Codec.DOUBLE.optionalFieldOf("speed", DEFAULT.speed).forGetter(WrenchFlow::speed),
            Codec.DOUBLE.optionalFieldOf("opacity", DEFAULT.opacity).forGetter(WrenchFlow::opacity),
            Codec.BOOL.optionalFieldOf("pulses", DEFAULT.pulses).forGetter(WrenchFlow::pulses),
            Codec.DOUBLE.optionalFieldOf("pulse_spacing", DEFAULT.pulseSpacing).forGetter(WrenchFlow::pulseSpacing),
            Codec.DOUBLE.optionalFieldOf("pulse_length", DEFAULT.pulseLength).forGetter(WrenchFlow::pulseLength),
            Codec.BOOL.optionalFieldOf("through_blocks", DEFAULT.throughBlocks).forGetter(WrenchFlow::throughBlocks)
    ).apply(instance, WrenchFlow::new));

    public static final StreamCodec<FriendlyByteBuf, WrenchFlow> STREAM_CODEC = StreamCodec.of(
            WrenchFlow::write, WrenchFlow::read);

    public WrenchFlow {
        types &= ALL_TYPES;
        channels &= ALL_CHANNELS;
        List<Integer> given = colors;
        colors = IntStream.range(0, DEFAULT_COLORS.size())
                .mapToObj(i -> NetworkColors.mask(i < given.size() ? given.get(i) : DEFAULT_COLORS.get(i)))
                .toList();
        thickness = THICKNESS.clamp(thickness);
        speed = SPEED.clamp(speed);
        opacity = OPACITY.clamp(opacity);
        pulseSpacing = PULSE_SPACING.clamp(pulseSpacing);
        pulseLength = PULSE_LENGTH.clamp(pulseLength);
    }

    public boolean showsType(ChannelType type) {
        return (types >> type.ordinal() & 1) != 0;
    }

    public boolean showsChannel(int channel) {
        return (channels >> channel & 1) != 0;
    }

    public boolean showsNetwork(UUID id) {
        return network.map(id::equals).orElse(true);
    }

    public int color(ChannelType type) {
        return colors.get(type.ordinal());
    }

    private static void write(FriendlyByteBuf buffer, WrenchFlow flow) {
        buffer.writeBoolean(flow.enabled);
        buffer.writeVarInt(flow.types);
        buffer.writeVarInt(flow.channels);
        buffer.writeOptional(flow.network, (buf, id) -> buf.writeUUID(id));
        buffer.writeBoolean(flow.offscreen);
        buffer.writeEnum(flow.style);
        flow.colors.forEach(buffer::writeInt);
        buffer.writeDouble(flow.thickness);
        buffer.writeDouble(flow.speed);
        buffer.writeDouble(flow.opacity);
        buffer.writeBoolean(flow.pulses);
        buffer.writeDouble(flow.pulseSpacing);
        buffer.writeDouble(flow.pulseLength);
        buffer.writeBoolean(flow.throughBlocks);
    }

    private static WrenchFlow read(FriendlyByteBuf buffer) {
        return new WrenchFlow(buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readOptional(buf -> buf.readUUID()), buffer.readBoolean(), buffer.readEnum(Style.class),
                IntStream.range(0, DEFAULT_COLORS.size()).mapToObj(i -> buffer.readInt()).toList(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readBoolean(),
                buffer.readDouble(), buffer.readDouble(), buffer.readBoolean());
    }

    public enum Style {
        ROUTED,
        DIRECT;

        public static final Codec<Style> CODEC = ComponentCodecs.enumCodec(values(), ROUTED);

        public Style next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public record Range(double min, double max) {
        public boolean contains(double value) {
            return value >= min && value <= max;
        }

        double clamp(double value) {
            return contains(value) ? value : value > max ? max : min;
        }
    }
}
