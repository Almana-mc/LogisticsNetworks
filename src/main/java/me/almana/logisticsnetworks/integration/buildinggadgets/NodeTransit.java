package me.almana.logisticsnetworks.integration.buildinggadgets;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Direction;
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
            CompoundTag entry = list.getCompoundOrEmpty(i);
            nodes.put(entry.getLongOr("p", 0L), entry.getCompoundOrEmpty("c"));
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

    public static CompoundTag rotateCopy(CompoundTag config) {
        CompoundTag rotated = config.copy();
        ListTag channels = rotated.getListOrEmpty("channels");
        for (int i = 0; i < channels.size(); i++) {
            rotateKey(channels.getCompoundOrEmpty(i), "io");
        }
        return rotated;
    }

    public static CompoundTag rotateMove(CompoundTag payload) {
        CompoundTag rotated = payload.copy();
        CompoundTag channels = rotated.getCompoundOrEmpty("state").getCompoundOrEmpty("Channels");
        for (String key : channels.keySet()) {
            rotateKey(channels.getCompoundOrEmpty(key), "IoDirection");
        }
        return rotated;
    }

    private static void rotateKey(CompoundTag tag, String key) {
        Direction direction = Direction.byName(tag.getStringOr(key, ""));
        if (direction != null && direction.getAxis().isHorizontal()) {
            tag.putString(key, Rotation.CLOCKWISE_90.rotate(direction).getName());
        }
    }
}
