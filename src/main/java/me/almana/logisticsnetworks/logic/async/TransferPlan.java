package me.almana.logisticsnetworks.logic.async;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record TransferPlan(
        UUID networkId,
        long generation,
        long runtimeId,
        boolean failed,
        long itemWakeDelta,
        List<ChannelMoves> channels) {

    public record ChannelMoves(
            UUID sourceNodeId,
            int channelIndex,
            List<TargetRef> targets,
            List<ItemMove> moves) {
    }

    public record TargetRef(UUID nodeId, int channelIndex, boolean bulk) {
    }

    public record ItemMove(
            int sourceSlot,
            int targetIndex,
            Item expectedItem,
            DataComponentMap expectedComponents,
            int amount,
            @Nullable boolean[] targetSlotMask) {
    }
}
