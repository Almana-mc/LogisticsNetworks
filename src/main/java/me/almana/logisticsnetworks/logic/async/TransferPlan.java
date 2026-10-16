package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.integration.storage.ItemResource;
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
            int sourceBinding,
            List<TargetRef> targets,
            List<ItemMove> moves) {
    }

    public record TargetRef(UUID nodeId, int channelIndex, boolean bulk, int binding) {
    }

    public record ItemMove(
            int sourceSlot,
            int targetIndex,
            ItemResource resource,
            int amount,
            @Nullable boolean[] targetSlotMask) {
        public Item expectedItem() {
            return resource.item();
        }

        public ItemMove withAmount(int count, @Nullable boolean[] mask) {
            return new ItemMove(sourceSlot, targetIndex, resource, count, mask);
        }
    }
}
