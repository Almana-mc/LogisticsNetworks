package me.almana.logisticsnetworks.mixin;


import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.almana.logisticsnetworks.data.INodeTracker;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.event.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.redstone.Orientation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class ServerLevelMixin implements INodeTracker {
    @Unique
    private final Long2ObjectOpenHashMap<LogisticsNodeEntity> logisticsNetworks$nodes = new Long2ObjectOpenHashMap<>();

    @Override
    public void logisticsNetworks$registerNode(@NotNull LogisticsNodeEntity node) {
        logisticsNetworks$nodes.put(node.getAttachedPos().asLong(), node);
    }

    @Override
    public void logisticsNetworks$removeNode(@NotNull LogisticsNodeEntity node) {
        logisticsNetworks$nodes.remove(node.getAttachedPos().asLong(), node);
    }

    @Override
    public @Nullable LogisticsNodeEntity logisticsNetworks$getNode(@NotNull BlockPos pos) {
        return logisticsNetworks$nodes.getOrDefault(pos.asLong(), null);
    }

    // 3-arg path skips NeighborNotify
    @Inject(method = "updateNeighborsAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/redstone/Orientation;)V", at = @At("HEAD"))
    private void logisticsNetworks$refreshAround(BlockPos pos, Block block, @Nullable Orientation orientation,
            CallbackInfo ci) {
        long origin = pos.asLong();
        for (Direction side : Direction.values()) {
            LogisticsNodeEntity node = logisticsNetworks$nodes.get(BlockPos.offset(origin, side));
            if (node == null) continue;
            node.forgetSignal();
            EventHandler.wakeNode(NetworkRegistry.get((ServerLevel) (Object) this), node, pos);
        }
    }
}
