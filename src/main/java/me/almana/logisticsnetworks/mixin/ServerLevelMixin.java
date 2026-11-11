package me.almana.logisticsnetworks.mixin;


import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.almana.logisticsnetworks.data.INodeTracker;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

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
}
