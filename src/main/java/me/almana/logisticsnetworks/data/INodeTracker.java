package me.almana.logisticsnetworks.data;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface INodeTracker {
    void logisticsNetworks$registerNode(@NotNull LogisticsNodeEntity node);
    void logisticsNetworks$removeNode(@NotNull BlockPos pos);
    @Nullable LogisticsNodeEntity logisticsNetworks$getNode(@NotNull BlockPos pos);
}
