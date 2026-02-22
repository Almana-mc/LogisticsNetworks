package me.almana.logisticsnetworks.data;

import net.minecraft.core.BlockPos;

import java.util.UUID;

public record NodeRef(UUID nodeId, BlockPos pos) {
}
