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
    private static final Long2ObjectOpenHashMap<LogisticsNodeEntity> logisticsNetworks$NODES = new Long2ObjectOpenHashMap<>();

    @Override
    public void logisticsNetworks$registerNode(@NotNull LogisticsNodeEntity node) {
        logisticsNetworks$NODES.put(node.blockPosition().asLong(), node);
    }

    @Override
    public void logisticsNetworks$removeNode(@NotNull BlockPos pos) {
        logisticsNetworks$NODES.remove(pos.asLong());
    }

    @Override
    public @Nullable LogisticsNodeEntity logisticsNetworks$getNode(@NotNull BlockPos pos) {
        return logisticsNetworks$NODES.getOrDefault(pos.asLong(), null);
    }
}
