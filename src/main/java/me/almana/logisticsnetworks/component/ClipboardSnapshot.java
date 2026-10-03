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
            String name,
            boolean resourceRoundRobin) {

        public static final Codec<ChannelState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.fieldOf("enabled").forGetter(ChannelState::enabled),
                ChannelMode.CODEC.fieldOf("mode").forGetter(ChannelState::mode),
                ChannelType.CODEC.fieldOf("type").forGetter(ChannelState::type),
                Codec.INT.fieldOf("batch_size").forGetter(ChannelState::batchSize),
                Codec.INT.fieldOf("tick_delay").forGetter(ChannelState::tickDelay),
                Direction.CODEC.optionalFieldOf("direction").forGetter(ChannelState::direction),
                Codec.STRING.optionalFieldOf("redstone_mode")
                        .forGetter(state -> Optional.of(state.redstoneMode.name().toLowerCase(Locale.ROOT))),
                DistributionMode.CODEC.fieldOf("distribution_mode").forGetter(ChannelState::distributionMode),
                FilterMode.CODEC.fieldOf("filter_mode").forGetter(ChannelState::filterMode),
                Codec.INT.fieldOf("priority").forGetter(ChannelState::priority),
                Codec.STRING.optionalFieldOf("name", "").forGetter(ChannelState::name),
                Codec.BOOL.optionalFieldOf("resource_round_robin", false).forGetter(ChannelState::resourceRoundRobin)
        ).apply(instance, ChannelState::fromSerialized));

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

        private static ChannelState fromSerialized(boolean enabled, ChannelMode mode, ChannelType type,
                int batchSize, int tickDelay, Optional<Direction> direction, Optional<String> redstoneMode,
                DistributionMode distributionMode, FilterMode filterMode, int priority, String name,
                boolean resourceRoundRobin) {
            String savedRedstoneMode = redstoneMode.orElse("ignored");
            return new ChannelState(
                    enabled && !RedstoneMode.disablesChannel(savedRedstoneMode),
                    mode,
                    type,
                    batchSize,
                    tickDelay,
                    direction,
                    RedstoneMode.fromSerialized(savedRedstoneMode),
                    distributionMode,
                    filterMode,
                    priority,
                    name,
                    resourceRoundRobin);
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
