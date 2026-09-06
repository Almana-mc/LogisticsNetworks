package me.almana.logisticsnetworks.logic.async;

import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.UUID;

public record TransferPlan(UUID networkId, long generation, long runtimeId, boolean failed,
        long itemWakeDelta, List<ChannelMoves> channels) {
    public TransferPlan {
        channels = List.copyOf(channels);
    }

    public record ChannelMoves(UUID sourceNodeId, int channelIndex,
            List<TargetRef> targets, List<MoveIntent> moves) {
        public ChannelMoves {
            targets = List.copyOf(targets);
            moves = List.copyOf(moves);
        }
    }

    public record TargetRef(UUID nodeId, int channelIndex, boolean bulk) {
    }

    public record MoveIntent(int sourceSlot, int targetIndex, ItemResource resource,
            int amount, @Nullable boolean[] targetSlotMask) {
        public MoveIntent {
            targetSlotMask = targetSlotMask == null ? null : targetSlotMask.clone();
        }

        @Override
        public boolean[] targetSlotMask() {
            return targetSlotMask == null ? null : targetSlotMask.clone();
        }
    }
}
