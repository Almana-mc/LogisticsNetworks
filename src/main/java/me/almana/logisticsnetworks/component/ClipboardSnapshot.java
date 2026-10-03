package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.DistributionMode;
import me.almana.logisticsnetworks.data.FilterMode;
import me.almana.logisticsnetworks.data.RedstoneMode;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public record ClipboardSnapshot(
        List<ChannelState> channels,
        List<FilterSlot> filters,
        List<ItemSlot> upgrades,
        Optional<UUID> networkId,
        Optional<String> networkName,
        boolean renderVisible,
        String nodeLabel) {

    public static final MapCodec<ClipboardSnapshot> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ChannelState.LIST_CODEC.lenientOptionalFieldOf("channels", List.of())
                    .forGetter(ClipboardSnapshot::channels),
            ComponentCodecs.lenientList(FilterSlot.CODEC).lenientOptionalFieldOf("filters", List.of())
                    .forGetter(ClipboardSnapshot::filters),
            ComponentCodecs.lenientList(ItemSlot.CODEC).lenientOptionalFieldOf("upgrades", List.of())
                    .forGetter(ClipboardSnapshot::upgrades),
            UUIDUtil.CODEC.lenientOptionalFieldOf("network_id").forGetter(ClipboardSnapshot::networkId),
            Codec.STRING.lenientOptionalFieldOf("network_name").forGetter(ClipboardSnapshot::networkName),
            Codec.BOOL.lenientOptionalFieldOf("render_visible", true).forGetter(ClipboardSnapshot::renderVisible),
            Codec.STRING.lenientOptionalFieldOf("node_label", "").forGetter(ClipboardSnapshot::nodeLabel)
    ).apply(instance, ClipboardSnapshot::new));
    public static final Codec<ClipboardSnapshot> CODEC = MAP_CODEC.codec();

    public ClipboardSnapshot {
        channels = List.copyOf(channels);
        filters = List.copyOf(filters);
        upgrades = List.copyOf(upgrades);
        networkId = networkId == null ? Optional.empty() : networkId;
        networkName = networkName == null ? Optional.empty() : networkName.filter(value -> !value.isBlank());
        nodeLabel = nodeLabel == null ? "" : nodeLabel;
    }

    public record ChannelState(
            boolean enabled,
            ChannelMode mode,
            ChannelType type,
            int batchSize,
            int tickDelay,
            Optional<Direction> direction,
            RedstoneMode redstoneMode,
            DistributionMode distributionMode,
            FilterMode filterMode,
            int priority,
            String name,
            boolean resourceRoundRobin) {

        public static final ChannelState DEFAULT = new ChannelState(false, ChannelMode.IMPORT, ChannelType.ITEM, 8,
                20, Optional.of(Direction.UP), RedstoneMode.IGNORED, DistributionMode.PRIORITY, FilterMode.MATCH_ANY,
                0, "", false);
        public static final Codec<Optional<Direction>> DIRECTION = Codec.STRING.xmap(ChannelState::parseDirection,
                direction -> direction.map(Direction::getName).orElse("all"));
        public static final MapCodec<ChannelState> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.BOOL.lenientOptionalFieldOf("enabled", false).forGetter(ChannelState::enabled),
                ChannelMode.CODEC.lenientOptionalFieldOf("mode", ChannelMode.IMPORT).forGetter(ChannelState::mode),
                ChannelType.CODEC.fieldOf("type").forGetter(ChannelState::type),
                Codec.INT.lenientOptionalFieldOf("batch_size", 8).forGetter(ChannelState::batchSize),
                Codec.INT.lenientOptionalFieldOf("tick_delay", 20).forGetter(ChannelState::tickDelay),
                DIRECTION.lenientOptionalFieldOf("direction", Optional.empty()).forGetter(ChannelState::direction),
                Codec.STRING.lenientOptionalFieldOf("redstone_mode", "ignored")
                        .forGetter(state -> state.redstoneMode.name().toLowerCase(Locale.ROOT)),
                DistributionMode.CODEC.lenientOptionalFieldOf("distribution_mode", DistributionMode.PRIORITY)
                        .forGetter(ChannelState::distributionMode),
                FilterMode.CODEC.lenientOptionalFieldOf("filter_mode", FilterMode.MATCH_ANY)
                        .forGetter(ChannelState::filterMode),
                Codec.INT.lenientOptionalFieldOf("priority", 0).forGetter(ChannelState::priority),
                Codec.STRING.lenientOptionalFieldOf("name", "").forGetter(ChannelState::name),
                Codec.BOOL.lenientOptionalFieldOf("resource_round_robin", false)
                        .forGetter(ChannelState::resourceRoundRobin)
        ).apply(instance, ChannelState::fromSerialized));
        public static final Codec<ChannelState> CODEC = MAP_CODEC.codec();
        public static final Codec<List<ChannelState>> LIST_CODEC =
                ComponentCodecs.lenient(CODEC, () -> DEFAULT).listOf();

        public ChannelState {
            mode = mode == null ? ChannelMode.IMPORT : mode;
            type = type == null ? ChannelType.ITEM : type;
            batchSize = Math.max(1, batchSize);
            tickDelay = Math.max(1, tickDelay);
            direction = direction == null ? Optional.empty() : direction;
            redstoneMode = redstoneMode == null ? RedstoneMode.IGNORED : redstoneMode;
            distributionMode = distributionMode == null ? DistributionMode.PRIORITY : distributionMode;
            filterMode = filterMode == null ? FilterMode.MATCH_ANY : filterMode;
            priority = Math.max(-99, Math.min(99, priority));
            name = name == null ? "" : name;
        }

        public static ChannelState fromSerialized(boolean enabled, ChannelMode mode, ChannelType type,
                int batchSize, int tickDelay, Optional<Direction> direction, String redstoneMode,
                DistributionMode distributionMode, FilterMode filterMode, int priority, String name,
                boolean resourceRoundRobin) {
            return new ChannelState(
                    enabled && !RedstoneMode.disablesChannel(redstoneMode),
                    mode,
                    type,
                    batchSize,
                    tickDelay,
                    direction,
                    RedstoneMode.fromSerialized(redstoneMode),
                    distributionMode,
                    filterMode,
                    priority,
                    name,
                    resourceRoundRobin);
        }

        private static Optional<Direction> parseDirection(String name) {
            if ("all".equals(name)) {
                return Optional.empty();
            }
            Direction direction = Direction.byName(name);
            return Optional.of(direction == null ? Direction.UP : direction);
        }
    }

    public record FilterSlot(int channel, int slot, StackSnapshot stack) {
        public static final Codec<FilterSlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("channel").forGetter(FilterSlot::channel),
                Codec.INT.fieldOf("slot").forGetter(FilterSlot::slot),
                StackSnapshot.CODEC.fieldOf("stack").forGetter(FilterSlot::stack)
        ).apply(instance, FilterSlot::new));
    }

    public record ItemSlot(int slot, StackSnapshot stack) {
        public static final Codec<ItemSlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("slot").forGetter(ItemSlot::slot),
                StackSnapshot.CODEC.fieldOf("stack").forGetter(ItemSlot::stack)
        ).apply(instance, ItemSlot::new));
    }
}
