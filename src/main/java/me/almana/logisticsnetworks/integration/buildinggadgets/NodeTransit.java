package me.almana.logisticsnetworks.integration.buildinggadgets;

import java.util.HashMap;
import java.util.Map;
import me.almana.logisticsnetworks.component.ComponentCodecs;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.NodeClipboardConfig;
import me.almana.logisticsnetworks.entity.NodeState;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Rotation;

public final class NodeTransit {
    public static final String KEY_NODE = "logisticsnetworks:node";
    public static final String KEY_NODES = "logisticsnetworks:nodes";
    public static final String KIND_MOVE = "move";
    public static final String KIND_COPY = "copy";
    public static final int MAX_COPY_BYTES = 512 * 1024;

    private NodeTransit() {
    }

    public static ListTag writeNodes(Map<Long, CompoundTag> nodes) {
        ListTag list = new ListTag();
        nodes.forEach((pos, config) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("p", pos);
            entry.put("c", config);
            list.add(entry);
        });
        return list;
    }

    public static Map<Long, CompoundTag> readNodes(ListTag list) {
        Map<Long, CompoundTag> nodes = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            nodes.put(entry.getLong("p"), entry.getCompound("c"));
        }
        return nodes;
    }

    public static boolean fits(Iterable<CompoundTag> configs) {
        long total = 0;
        for (CompoundTag config : configs) {
            total += config.sizeInBytes();
        }
        return total <= MAX_COPY_BYTES;
    }

    public static CompoundTag rotateCopy(CompoundTag config, HolderLookup.Provider registries) {
        NodeClipboardConfig clipboard = NodeClipboardConfig.load(config, registries);
        if (clipboard == null) return config.copy();
        for (int channel = 0; channel < clipboard.getChannelCount(); channel++) {
            Direction direction = clipboard.getChannelDirection(channel);
            if (direction != null) clipboard.setChannelDirection(channel, Rotation.CLOCKWISE_90.rotate(direction));
        }
        return clipboard.save(registries);
    }

    public static CompoundTag rotateMove(CompoundTag payload, HolderLookup.Provider registries) {
        CompoundTag rotated = payload.copy();
        ComponentCodecs.parse(NodeState.CODEC, registries, payload.getCompound("state")).ifPresent(state -> {
            for (ChannelData channel : state.channels()) {
                Direction direction = channel.getIoDirection();
                if (direction != null) channel.setIoDirection(Rotation.CLOCKWISE_90.rotate(direction));
            }
            rotated.put("state", ComponentCodecs.encode(NodeState.CODEC, registries, state));
        });
        return rotated;
    }
}
