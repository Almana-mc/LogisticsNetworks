package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
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

    public static final Codec<ClipboardSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ChannelState.CODEC.listOf().fieldOf("channels").forGetter(ClipboardSnapshot::channels),
            FilterSlot.CODEC.listOf().optionalFieldOf("filters", List.of()).forGetter(ClipboardSnapshot::filters),
            ItemSlot.CODEC.listOf().optionalFieldOf("upgrades", List.of()).forGetter(ClipboardSnapshot::upgrades),
            UUIDUtil.CODEC.optionalFieldOf("network_id").forGetter(ClipboardSnapshot::networkId),
            Codec.STRING.optionalFieldOf("network_name").forGetter(ClipboardSnapshot::networkName),
            Codec.BOOL.optionalFieldOf("render_visible", true).forGetter(ClipboardSnapshot::renderVisible),
            Codec.STRING.optionalFieldOf("node_label", "").forGetter(ClipboardSnapshot::nodeLabel)
    ).apply(instance, ClipboardSnapshot::new));

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
            String name) {

        private static final Codec<ChannelMode> MODE_CODEC = enumCodec(ChannelMode.values(), ChannelMode.IMPORT);
        private static final Codec<ChannelType> TYPE_CODEC = enumCodec(ChannelType.values(), ChannelType.ITEM);
        private static final Codec<RedstoneMode> REDSTONE_CODEC = enumCodec(RedstoneMode.values(), RedstoneMode.ALWAYS_ON);
        private static final Codec<DistributionMode> DISTRIBUTION_CODEC = enumCodec(
                DistributionMode.values(), DistributionMode.PRIORITY);
        private static final Codec<FilterMode> FILTER_CODEC = enumCodec(FilterMode.values(), FilterMode.MATCH_ANY);
        public static final Codec<ChannelState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.fieldOf("enabled").forGetter(ChannelState::enabled),
                MODE_CODEC.fieldOf("mode").forGetter(ChannelState::mode),
                TYPE_CODEC.fieldOf("type").forGetter(ChannelState::type),
                Codec.INT.fieldOf("batch_size").forGetter(ChannelState::batchSize),
                Codec.INT.fieldOf("tick_delay").forGetter(ChannelState::tickDelay),
                Direction.CODEC.optionalFieldOf("direction").forGetter(ChannelState::direction),
                REDSTONE_CODEC.fieldOf("redstone_mode").forGetter(ChannelState::redstoneMode),
                DISTRIBUTION_CODEC.fieldOf("distribution_mode").forGetter(ChannelState::distributionMode),
                FILTER_CODEC.fieldOf("filter_mode").forGetter(ChannelState::filterMode),
                Codec.INT.fieldOf("priority").forGetter(ChannelState::priority),
                Codec.STRING.optionalFieldOf("name", "").forGetter(ChannelState::name)
        ).apply(instance, ChannelState::new));

        public ChannelState {
            mode = mode == null ? ChannelMode.IMPORT : mode;
            type = type == null ? ChannelType.ITEM : type;
            batchSize = Math.max(1, batchSize);
            tickDelay = Math.max(1, tickDelay);
            direction = direction == null ? Optional.empty() : direction;
            redstoneMode = redstoneMode == null ? RedstoneMode.ALWAYS_ON : redstoneMode;
            distributionMode = distributionMode == null ? DistributionMode.PRIORITY : distributionMode;
            filterMode = filterMode == null ? FilterMode.MATCH_ANY : filterMode;
            priority = Math.max(-99, Math.min(99, priority));
            name = name == null ? "" : name;
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

    private static <E extends Enum<E>> Codec<E> enumCodec(E[] values, E fallback) {
        return Codec.STRING.xmap(value -> {
            for (E candidate : values) {
                if (candidate.name().equalsIgnoreCase(value)) {
                    return candidate;
                }
            }
            return fallback;
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }
}
