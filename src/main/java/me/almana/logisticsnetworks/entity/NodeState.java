package me.almana.logisticsnetworks.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.almana.logisticsnetworks.component.ComponentCodecs;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.NetworkColors;
import me.almana.logisticsnetworks.data.SlotStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.Util;
import net.neoforged.neoforge.common.util.NeoForgeExtraCodecs;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record NodeState(BlockPos attachedPos, boolean valid, Optional<UUID> networkId, String networkName,
        int networkColor, boolean renderVisible, Optional<UUID> owner, String nodeLabel, long labelRevision,
        boolean highlighted, Optional<UUID> createContraptionId, BlockPos createLocalPos,
        List<ChannelData> channels, List<SlotStack> upgrades) {

    private static final String KEY = "logisticsnetworks:node";
    private static final Codec<BlockPos> PACKED_POS = Codec.LONG.xmap(BlockPos::of, BlockPos::asLong);
    private static final Codec<List<ChannelData>> LEGACY_CHANNELS = Codec.unboundedMap(Codec.STRING,
            ComponentCodecs.lenient(ChannelData.CODEC, ChannelData::new)).xmap(
            map -> IntStream.range(0, LogisticsNodeEntity.CHANNEL_COUNT)
                    .mapToObj(index -> map.getOrDefault("Channel" + index, new ChannelData()))
                    .toList(),
            channels -> IntStream.range(0, channels.size()).boxed()
                    .collect(Collectors.toMap(index -> "Channel" + index, channels::get)));

    private static final MapCodec<NodeState> FIELDS = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BlockPos.CODEC.lenientOptionalFieldOf("attached_pos", BlockPos.ZERO).forGetter(NodeState::attachedPos),
            Codec.BOOL.lenientOptionalFieldOf("valid", false).forGetter(NodeState::valid),
            UUIDUtil.CODEC.lenientOptionalFieldOf("network_id").forGetter(NodeState::networkId),
            Codec.STRING.lenientOptionalFieldOf("network_name", "").forGetter(NodeState::networkName),
            Codec.INT.lenientOptionalFieldOf("network_color", NetworkColors.DEFAULT)
                    .forGetter(NodeState::networkColor),
            Codec.BOOL.lenientOptionalFieldOf("render_visible", true).forGetter(NodeState::renderVisible),
            owner("owner", UUIDUtil.CODEC).forGetter(NodeState::owner),
            Codec.STRING.lenientOptionalFieldOf("node_label", "").forGetter(NodeState::nodeLabel),
            Codec.LONG.lenientOptionalFieldOf("label_revision", 0L).forGetter(NodeState::labelRevision),
            Codec.BOOL.lenientOptionalFieldOf("highlighted", false).forGetter(NodeState::highlighted),
            UUIDUtil.CODEC.lenientOptionalFieldOf("create_contraption_id")
                    .forGetter(NodeState::createContraptionId),
            BlockPos.CODEC.lenientOptionalFieldOf("create_local_pos", BlockPos.ZERO)
                    .forGetter(NodeState::createLocalPos),
            ChannelData.LIST_CODEC.lenientOptionalFieldOf("channels", List.of()).forGetter(NodeState::channels),
            SlotStack.LIST_CODEC.lenientOptionalFieldOf("upgrades", List.of()).forGetter(NodeState::upgrades)
    ).apply(instance, NodeState::new));
    private static final MapCodec<NodeState> LEGACY_FIELDS = RecordCodecBuilder.mapCodec(instance -> instance.group(
            PACKED_POS.lenientOptionalFieldOf("AttachedPos", BlockPos.ZERO).forGetter(NodeState::attachedPos),
            Codec.BOOL.lenientOptionalFieldOf("Valid", false).forGetter(NodeState::valid),
            UUIDUtil.LENIENT_CODEC.lenientOptionalFieldOf("NetworkId").forGetter(NodeState::networkId),
            Codec.STRING.lenientOptionalFieldOf("NetworkName", "").forGetter(NodeState::networkName),
            Codec.INT.lenientOptionalFieldOf("NetworkColor", NetworkColors.DEFAULT)
                    .forGetter(NodeState::networkColor),
            Codec.BOOL.lenientOptionalFieldOf("RenderVisible", true).forGetter(NodeState::renderVisible),
            owner("OwnerUUID", UUIDUtil.LENIENT_CODEC).forGetter(NodeState::owner),
            Codec.STRING.lenientOptionalFieldOf("NodeLabel", "").forGetter(NodeState::nodeLabel),
            Codec.LONG.lenientOptionalFieldOf("LabelRevision", 0L).forGetter(NodeState::labelRevision),
            Codec.BOOL.lenientOptionalFieldOf("Highlighted", false).forGetter(NodeState::highlighted),
            UUIDUtil.LENIENT_CODEC.lenientOptionalFieldOf("CreateContraptionId")
                    .forGetter(NodeState::createContraptionId),
            PACKED_POS.lenientOptionalFieldOf("CreateLocalPos", BlockPos.ZERO).forGetter(NodeState::createLocalPos),
            LEGACY_CHANNELS.lenientOptionalFieldOf("Channels", List.of()).forGetter(NodeState::channels),
            SlotStack.LEGACY_LIST_CODEC.lenientOptionalFieldOf("Upgrades", List.of()).forGetter(NodeState::upgrades)
    ).apply(instance, NodeState::new));

    public static final MapCodec<NodeState> MAP_CODEC =
            NeoForgeExtraCodecs.mapWithAlternative(FIELDS.codec().fieldOf(KEY), LEGACY_FIELDS);
    public static final Codec<NodeState> CODEC = MAP_CODEC.codec();

    public NodeState {
        labelRevision = Math.max(0, labelRevision);
        createLocalPos = createContraptionId.isPresent() ? createLocalPos : BlockPos.ZERO;
    }

    // Malformed owner locks to nobody
    private static MapCodec<Optional<UUID>> owner(String key, Codec<UUID> codec) {
        return ComponentCodecs.lenient(codec.xmap(Optional::of, Optional::get),
                () -> Optional.of(Util.NIL_UUID)).optionalFieldOf(key, Optional.empty());
    }
}
