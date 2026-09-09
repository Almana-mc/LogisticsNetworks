package me.almana.logisticsnetworks.data.graph;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.UUID;

public record GraphNode(UUID nodeId, String label, String blockName, BlockPos attachedPos,
        Identifier dimension, boolean dimensional, List<GraphChannel> channels) {

    public GraphNode {
        channels = List.copyOf(channels);
    }
}
