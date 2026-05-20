package me.almana.logisticsnetworks.integration.ae2;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.features.GridLinkables;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class AE2StorageHelper {

    private AE2StorageHelper() {
    }

    @Nullable
    static IGrid resolveGrid(ServerLevel callerLevel, GlobalPos linkPos) {
        ServerLevel targetLevel = callerLevel.getServer().getLevel(linkPos.dimension());
        if (targetLevel == null) return null;
        IInWorldGridNodeHost host = targetLevel.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, linkPos.pos());
        if (host == null) return null;

        IGridNode gridNode = null;
        for (Direction direction : Direction.values()) {
            gridNode = host.getGridNode(direction);
            if (gridNode != null && gridNode.isActive()) break;
        }
        if (gridNode == null || !gridNode.isActive()) return null;
        return gridNode.getGrid();
    }

    static boolean isGridAccessible(ServerLevel callerLevel, GlobalPos linkPos) {
        return resolveGrid(callerLevel, linkPos) != null;
    }

    static boolean isGridHost(Level level, BlockPos pos) {
        return level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, pos) != null;
    }

    static long countAvailable(ServerLevel callerLevel, GlobalPos linkPos, ItemStack pattern) {
        IGrid grid = resolveGrid(callerLevel, linkPos);
        if (grid == null || pattern.isEmpty()) return 0;
        MEStorage storage = grid.getStorageService().getInventory();
        long total = 0;

        for (AEItemKey key : matchingItemKeys(storage, pattern)) {
            long found = storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
            total = saturatingAdd(total, found);
        }
        return total;
    }

    static int extractItems(ServerLevel callerLevel, GlobalPos linkPos, ItemStack pattern, int amount, ServerPlayer player) {
        IGrid grid = resolveGrid(callerLevel, linkPos);
        if (grid == null || pattern.isEmpty() || amount <= 0) return 0;
        MEStorage storage = grid.getStorageService().getInventory();
        IActionSource source = IActionSource.ofPlayer(player);
        long remaining = amount;
        long extracted = 0;

        for (AEItemKey key : matchingItemKeys(storage, pattern)) {
            if (remaining <= 0) break;
            long moved = storage.extract(key, remaining, Actionable.MODULATE, source);
            remaining -= moved;
            extracted += moved;
        }
        return (int) Math.min(extracted, Integer.MAX_VALUE);
    }

    static void registerWrenchLinkable() {
        GridLinkables.register(Registration.WRENCH.get(), AE2LinkHandler.INSTANCE);
    }

    private static List<AEItemKey> matchingItemKeys(MEStorage storage, ItemStack pattern) {
        List<AEItemKey> keys = new ArrayList<>();
        for (Object2LongMap.Entry<appeng.api.stacks.AEKey> entry : storage.getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey itemKey
                    && ItemStack.isSameItem(itemKey.toStack(), pattern)) {
                keys.add(itemKey);
            }
        }
        return keys;
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        return result < 0 ? Long.MAX_VALUE : result;
    }
}
