package me.almana.logisticsnetworks.integration.ae2;

// 26.1 AE2 API pending
/*
import appeng.api.config.Actionable;
import appeng.api.features.GridLinkables;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

final class AE2StorageHelper {

    private AE2StorageHelper() {
    }

    @Nullable
    static IGrid resolveGrid(ServerLevel callerLevel, GlobalPos linkPos) {
        ServerLevel targetLevel = callerLevel.getServer().getLevel(linkPos.dimension());
        if (targetLevel == null) return null;
        BlockEntity be = targetLevel.getBlockEntity(linkPos.pos());
        if (!(be instanceof IInWorldGridNodeHost host)) return null;
        IGridNode gridNode = host.getGridNode(null);
        if (gridNode == null) {
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                gridNode = host.getGridNode(dir);
                if (gridNode != null) break;
            }
        }
        if (gridNode == null || !gridNode.isActive()) return null;
        return gridNode.getGrid();
    }

    static boolean isGridAccessible(ServerLevel callerLevel, GlobalPos linkPos) {
        return resolveGrid(callerLevel, linkPos) != null;
    }

    static boolean isGridHost(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof IInWorldGridNodeHost;
    }

    static long countAvailable(ServerLevel callerLevel, GlobalPos linkPos, ItemStack pattern) {
        IGrid grid = resolveGrid(callerLevel, linkPos);
        if (grid == null) return 0;
        AEItemKey key = AEItemKey.of(pattern);
        if (key == null) return 0;
        MEStorage storage = grid.getStorageService().getInventory();
        return storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    static int extractItems(ServerLevel callerLevel, GlobalPos linkPos, ItemStack pattern, int amount, ServerPlayer player) {
        IGrid grid = resolveGrid(callerLevel, linkPos);
        if (grid == null) return 0;
        AEItemKey key = AEItemKey.of(pattern);
        if (key == null) return 0;
        MEStorage storage = grid.getStorageService().getInventory();
        long extracted = storage.extract(key, amount, Actionable.MODULATE, IActionSource.ofPlayer(player));
        return (int) Math.min(extracted, Integer.MAX_VALUE);
    }

    static void registerWrenchLinkable() {
        GridLinkables.register(Registration.WRENCH.get(), AE2LinkHandler.INSTANCE);
    }
}
*/
