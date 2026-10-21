package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.DistributionMode;
import me.almana.logisticsnetworks.data.FilterMode;
import me.almana.logisticsnetworks.data.RedstoneMode;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
            ComponentCodecs.quietLenientList(FilterSlot.CODEC, LogisticsNodeEntity.CHANNEL_COUNT * ChannelData.FILTER_SIZE)
                    .lenientOptionalFieldOf("filters", List.of())
                    .forGetter(ClipboardSnapshot::filters),
            ComponentCodecs.quietLenientList(ItemSlot.CODEC, LogisticsNodeEntity.UPGRADE_SLOT_COUNT)
                    .lenientOptionalFieldOf("upgrades", List.of())
                    .forGetter(ClipboardSnapshot::upgrades),
            UUIDUtil.CODEC.lenientOptionalFieldOf("network_id").forGetter(ClipboardSnapshot::networkId),
            Codec.STRING.lenientOptionalFieldOf("network_name").forGetter(ClipboardSnapshot::networkName),
            Codec.BOOL.lenientOptionalFieldOf("render_visible", true).forGetter(ClipboardSnapshot::renderVisible),
            Codec.STRING.lenientOptionalFieldOf("node_label", "").forGetter(ClipboardSnapshot::nodeLabel)
    ).apply(instance, ClipboardSnapshot::new));
    public static final Codec<ClipboardSnapshot> CODEC = MAP_CODEC.codec();
    public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardSnapshot> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

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
            int types,
            List<Integer> batches,
            int recipeSets,
            int tickDelay,
            Optional<Direction> direction,
            RedstoneMode redstoneMode,
            DistributionMode distributionMode,
            FilterMode filterMode,
            int priority,
            String name,
            boolean resourceRoundRobin) {

        public static final List<Integer> MAX_BATCHES = List.of(0, 0, 0, 0, 0);
        public static final ChannelState DEFAULT = new ChannelState(false, ChannelMode.IMPORT, ChannelType.ALL,
                MAX_BATCHES, 0, 20, Optional.of(Direction.UP), RedstoneMode.IGNORED, DistributionMode.PRIORITY,
                FilterMode.MATCH_ANY, 0, "", false);
        public static final Codec<Optional<Direction>> DIRECTION = Codec.STRING.xmap(ChannelState::parseDirection,
                direction -> direction.map(Direction::getName).orElse("all"));
        private static final Codec<Map<ChannelType, Integer>> BATCHES =
                Codec.unboundedMap(ChannelType.CODEC, Codec.INT);
        public static final MapCodec<ChannelState> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.BOOL.lenientOptionalFieldOf("enabled", false).forGetter(ChannelState::enabled),
                ChannelMode.CODEC.lenientOptionalFieldOf("mode", ChannelMode.IMPORT).forGetter(ChannelState::mode),
                ChannelType.CODEC.fieldOf("type").forGetter(state -> ChannelType.first(state.types)),
                ChannelType.CODEC.listOf().lenientOptionalFieldOf("types")
                        .forGetter(state -> Optional.of(ChannelType.listOf(state.types))),
                Codec.INT.lenientOptionalFieldOf("batch_size").forGetter(state -> Optional.empty()),
                BATCHES.lenientOptionalFieldOf("batches").forGetter(state -> Optional.of(batchMap(state.batches))),
                Codec.INT.lenientOptionalFieldOf("recipe_sets", 0).forGetter(ChannelState::recipeSets),
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
                ComponentCodecs.quietLenient(CODEC, () -> DEFAULT).listOf(0, LogisticsNodeEntity.CHANNEL_COUNT);

        public ChannelState {
            mode = mode == null ? ChannelMode.IMPORT : mode;
            types &= ChannelType.ALL;
            batches = batches == null || batches.size() != MAX_BATCHES.size()
                    ? MAX_BATCHES : batches.stream().map(value -> Math.max(0, value)).toList();
            recipeSets = Math.max(0, recipeSets);
            tickDelay = Math.max(1, tickDelay);
            direction = direction == null ? Optional.empty() : direction;
            redstoneMode = redstoneMode == null ? RedstoneMode.IGNORED : redstoneMode;
            distributionMode = distributionMode == null ? DistributionMode.PRIORITY : distributionMode;
            filterMode = filterMode == null ? FilterMode.MATCH_ANY : filterMode;
            priority = Math.max(-99, Math.min(99, priority));
            name = name == null ? "" : name;
        }

        public static ChannelState fromSerialized(boolean enabled, ChannelMode mode, ChannelType type,
                Optional<List<ChannelType>> types, Optional<Integer> batchSize,
                Optional<Map<ChannelType, Integer>> batches, int recipeSets, int tickDelay,
                Optional<Direction> direction, String redstoneMode, DistributionMode distributionMode,
                FilterMode filterMode, int priority, String name, boolean resourceRoundRobin) {
            int mask = types.map(ChannelType::maskOf).orElse(type.bit());
            List<Integer> values = batches.map(ChannelState::batchList)
                    .orElseGet(() -> legacyBatches(type, batchSize.orElse(8)));
            return new ChannelState(
                    enabled && !RedstoneMode.disablesChannel(redstoneMode),
                    mode,
                    mask,
                    values,
                    recipeSets,
                    tickDelay,
                    direction,
                    RedstoneMode.fromSerialized(redstoneMode),
                    distributionMode,
                    filterMode,
                    priority,
                    name,
                    resourceRoundRobin);
        }

        public static ChannelState legacy(boolean enabled, ChannelMode mode, ChannelType type, int batchSize,
                int tickDelay, Optional<Direction> direction, String redstoneMode,
                DistributionMode distributionMode, FilterMode filterMode, int priority, String name,
                boolean resourceRoundRobin) {
            return fromSerialized(enabled, mode, type, Optional.empty(), Optional.of(batchSize), Optional.empty(),
                    0, tickDelay, direction, redstoneMode, distributionMode, filterMode, priority, name,
                    resourceRoundRobin);
        }

        public ChannelType primaryType() {
            return ChannelType.first(types);
        }

        public int primaryBatch() {
            return batches.get(primaryType().ordinal());
        }

        private static Map<ChannelType, Integer> batchMap(List<Integer> batches) {
            Map<ChannelType, Integer> map = new EnumMap<>(ChannelType.class);
            for (ChannelType type : ChannelType.values()) {
                if (batches.get(type.ordinal()) > 0) map.put(type, batches.get(type.ordinal()));
            }
            return map;
        }

        private static List<Integer> batchList(Map<ChannelType, Integer> map) {
            List<Integer> list = new ArrayList<>(MAX_BATCHES.size());
            for (ChannelType type : ChannelType.values()) {
                list.add(Math.max(0, map.getOrDefault(type, 0)));
            }
            return List.copyOf(list);
        }

        private static List<Integer> legacyBatches(ChannelType type, int batch) {
            List<Integer> list = new ArrayList<>(MAX_BATCHES);
            list.set(type.ordinal(), Math.max(1, batch));
            return List.copyOf(list);
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
